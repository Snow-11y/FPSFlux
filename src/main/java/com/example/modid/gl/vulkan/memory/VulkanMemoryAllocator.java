package com.example.modid.gl.vulkan.memory;

import com.example.modid.gl.vulkan.VulkanContext;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.vulkan.VK12.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.*;

/**
 * Production-grade Vulkan Memory Allocator.
 * 
 * Features:
 * - Automatic memory type selection based on usage
 * - Multiple allocation strategies per pool
 * - Dedicated allocations for large/optimal resources
 * - Linear (bump) allocators for per-frame staging
 * - Buffer device address support for bindless
 * - Memory defragmentation
 * - Statistics and debugging
 * - Thread-safe operations
 */
public class VulkanMemoryAllocator {
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════
    
    public static class Config {
        public long defaultBlockSize = 256 * 1024 * 1024L;     // 256 MB
        public long smallBlockSize = 64 * 1024 * 1024L;        // 64 MB for small allocs
        public long largeBlockSize = 512 * 1024 * 1024L;       // 512 MB for large pools
        public long dedicatedThreshold = 64 * 1024 * 1024L;    // 64 MB -> dedicated
        public long smallAllocationThreshold = 4 * 1024L;       // 4 KB
        public long linearBlockSize = 32 * 1024 * 1024L;       // 32 MB per-frame staging
        public int maxFramesInFlight = 3;
        public boolean enableDeviceAddress = true;
        public boolean enableDefragmentation = true;
        public boolean enableDebugMarkers = false;
        public MemoryBlock.AllocationStrategy defaultStrategy = MemoryBlock.AllocationStrategy.BEST_FIT;
        
        public Config() {}
        
        public static Config highPerformance() {
            Config c = new Config();
            c.defaultBlockSize = 512 * 1024 * 1024L;
            c.linearBlockSize = 64 * 1024 * 1024L;
            return c;
        }
        
        public static Config lowMemory() {
            Config c = new Config();
            c.defaultBlockSize = 64 * 1024 * 1024L;
            c.smallBlockSize = 16 * 1024 * 1024L;
            c.linearBlockSize = 8 * 1024 * 1024L;
            c.dedicatedThreshold = 32 * 1024 * 1024L;
            return c;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // MEMORY USAGE HINTS
    // ═══════════════════════════════════════════════════════════════════════
    
    public enum MemoryUsage {
        /** GPU-only, optimal for render targets and static geometry */
        GPU_ONLY,
        /** CPU writable, GPU readable - dynamic uniforms, staging */
        CPU_TO_GPU,
        /** GPU writable, CPU readable - readback buffers */
        GPU_TO_CPU,
        /** CPU cached for frequent reads */
        CPU_CACHED,
        /** Linear per-frame allocation, auto-reset */
        CPU_TO_GPU_FRAME,
        /** Lazily allocated (mobile/tiled) */
        GPU_LAZY
    }
    
    public enum AllocationFlags {
        NONE(0),
        DEDICATED(1 << 0),              // Force dedicated allocation
        NEVER_DEDICATED(1 << 1),        // Never use dedicated
        MAPPED(1 << 2),                 // Persistently mapped
        HOST_SEQUENTIAL_WRITE(1 << 3),  // Optimize for sequential writes
        HOST_RANDOM_ACCESS(1 << 4),     // Optimize for random access
        CAN_ALIAS(1 << 5),              // Can share memory with other allocations
        STRATEGY_MIN_MEMORY(1 << 6),    // Minimize memory usage
        STRATEGY_MIN_TIME(1 << 7);      // Minimize allocation time
        
        public final int bits;
        AllocationFlags(int bits) { this.bits = bits; }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // ALLOCATION REQUEST & INFO
    // ═══════════════════════════════════════════════════════════════════════
    
    public static class AllocationCreateInfo {
        public long size;
        public long alignment;
        public int memoryTypeBits = ~0;           // From VkMemoryRequirements
        public MemoryUsage usage = MemoryUsage.GPU_ONLY;
        public int flags = 0;                      // AllocationFlags
        public int requiredFlags = 0;              // VkMemoryPropertyFlags
        public int preferredFlags = 0;             // VkMemoryPropertyFlags
        public float priority = 0.5f;              // Memory priority (0-1)
        public String debugName;
        
        public AllocationCreateInfo() {}
        
        public AllocationCreateInfo size(long s) { this.size = s; return this; }
        public AllocationCreateInfo alignment(long a) { this.alignment = a; return this; }
        public AllocationCreateInfo memoryTypeBits(int bits) { this.memoryTypeBits = bits; return this; }
        public AllocationCreateInfo usage(MemoryUsage u) { this.usage = u; return this; }
        public AllocationCreateInfo flags(int f) { this.flags = f; return this; }
        public AllocationCreateInfo dedicated() { this.flags |= AllocationFlags.DEDICATED.bits; return this; }
        public AllocationCreateInfo mapped() { this.flags |= AllocationFlags.MAPPED.bits; return this; }
        public AllocationCreateInfo debugName(String n) { this.debugName = n; return this; }
        
        public static AllocationCreateInfo buffer(long size, MemoryUsage usage) {
            return new AllocationCreateInfo().size(size).usage(usage);
        }
        
        public static AllocationCreateInfo image(long size) {
            return new AllocationCreateInfo().size(size).usage(MemoryUsage.GPU_ONLY);
        }
        
        public static AllocationCreateInfo staging(long size) {
            return new AllocationCreateInfo()
                .size(size)
                .usage(MemoryUsage.CPU_TO_GPU)
                .flags(AllocationFlags.HOST_SEQUENTIAL_WRITE.bits);
        }
        
        public static AllocationCreateInfo uniform(long size) {
            return new AllocationCreateInfo()
                .size(size)
                .usage(MemoryUsage.CPU_TO_GPU)
                .mapped();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // MEMORY POOL
    // ═══════════════════════════════════════════════════════════════════════
    
    private class MemoryPool {
        final int memoryTypeIndex;
        final int propertyFlags;
        final long preferredBlockSize;
        final MemoryBlock.AllocationStrategy strategy;
        final List<MemoryBlock> blocks = new CopyOnWriteArrayList<>();
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        
        // Statistics
        final AtomicLong totalAllocated = new AtomicLong();
        final AtomicLong totalBlockMemory = new AtomicLong();
        final AtomicInteger allocationCount = new AtomicInteger();
        
        MemoryPool(int memoryTypeIndex, int propertyFlags, long blockSize, 
                   MemoryBlock.AllocationStrategy strategy) {
            this.memoryTypeIndex = memoryTypeIndex;
            this.propertyFlags = propertyFlags;
            this.preferredBlockSize = blockSize;
            this.strategy = strategy;
        }
        
        MemoryAllocation allocate(long size, long alignment, String debugName) {
            // Try existing blocks first (read lock)
            lock.readLock().lock();
            try {
                for (MemoryBlock block : blocks) {
                    MemoryAllocation alloc = block.allocate(size, alignment, debugName);
                    if (alloc != null) {
                        updateStats(alloc, true);
                        return alloc;
                    }
                }
            } finally {
                lock.readLock().unlock();
            }
            
            // Need new block (write lock)
            lock.writeLock().lock();
            try {
                // Double-check after acquiring write lock
                for (MemoryBlock block : blocks) {
                    MemoryAllocation alloc = block.allocate(size, alignment, debugName);
                    if (alloc != null) {
                        updateStats(alloc, true);
                        return alloc;
                    }
                }
                
                // Create new block
                long blockSize = calculateBlockSize(size);
                MemoryBlock newBlock = new MemoryBlock(
                    context.device,
                    context.physicalDevice,
                    nextBlockId.getAndIncrement(),
                    blockSize,
                    memoryTypeIndex,
                    strategy
                );
                blocks.add(newBlock);
                totalBlockMemory.addAndGet(blockSize);
                
                MemoryAllocation alloc = newBlock.allocate(size, alignment, debugName);
                if (alloc != null) {
                    updateStats(alloc, true);
                }
                return alloc;
                
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        void free(MemoryAllocation allocation) {
            for (MemoryBlock block : blocks) {
                if (block.getId() == allocation.getBlockId()) {
                    block.free(allocation);
                    updateStats(allocation, false);
                    return;
                }
            }
        }
        
        private void updateStats(MemoryAllocation alloc, boolean isAlloc) {
            if (isAlloc) {
                totalAllocated.addAndGet(alloc.getAlignedSize());
                allocationCount.incrementAndGet();
            } else {
                totalAllocated.addAndGet(-alloc.getAlignedSize());
                allocationCount.decrementAndGet();
            }
        }
        
        private long calculateBlockSize(long requiredSize) {
            if (requiredSize > preferredBlockSize) {
                // Round up to next power of 2
                return Long.highestOneBit(requiredSize - 1) << 1;
            }
            return preferredBlockSize;
        }
        
        void defragment() {
            lock.writeLock().lock();
            try {
                // Remove empty blocks (keep at least one)
                if (blocks.size() > 1) {
                    blocks.removeIf(block -> {
                        if (block.getAllocationCount() == 0) {
                            totalBlockMemory.addAndGet(-block.getTotalSize());
                            block.destroy();
                            return true;
                        }
                        return false;
                    });
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        void destroy() {
            lock.writeLock().lock();
            try {
                for (MemoryBlock block : blocks) {
                    block.destroy();
                }
                blocks.clear();
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        PoolStats getStats() {
            return new PoolStats(
                memoryTypeIndex,
                propertyFlags,
                blocks.size(),
                totalBlockMemory.get(),
                totalAllocated.get(),
                allocationCount.get()
            );
        }
    }
    
    public record PoolStats(
        int memoryTypeIndex,
        int propertyFlags,
        int blockCount,
        long totalMemory,
        long allocatedMemory,
        int allocationCount
    ) {}
    
    // ═══════════════════════════════════════════════════════════════════════
    // LINEAR ALLOCATOR (Per-Frame Staging)
    // ═══════════════════════════════════════════════════════════════════════
    
    private class LinearAllocator {
        final int memoryTypeIndex;
        final MemoryBlock[] frameBlocks;
        final AtomicLong[] frameOffsets;
        int currentFrame = 0;
        
        LinearAllocator(int memoryTypeIndex, int frameCount, long blockSize) {
            this.memoryTypeIndex = memoryTypeIndex;
            this.frameBlocks = new MemoryBlock[frameCount];
            this.frameOffsets = new AtomicLong[frameCount];
            
            for (int i = 0; i < frameCount; i++) {
                frameBlocks[i] = new MemoryBlock(
                    context.device,
                    context.physicalDevice,
                    nextBlockId.getAndIncrement(),
                    blockSize,
                    memoryTypeIndex,
                    MemoryBlock.AllocationStrategy.LINEAR
                );
                frameOffsets[i] = new AtomicLong(0);
            }
        }
        
        MemoryAllocation allocate(long size, long alignment, String debugName) {
            return frameBlocks[currentFrame].allocate(size, alignment, debugName);
        }
        
        void nextFrame() {
            currentFrame = (currentFrame + 1) % frameBlocks.length;
            frameBlocks[currentFrame].resetLinear();
            frameOffsets[currentFrame].set(0);
        }
        
        void destroy() {
            for (MemoryBlock block : frameBlocks) {
                if (block != null) {
                    block.destroy();
                }
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // DEDICATED ALLOCATION TRACKING
    // ═══════════════════════════════════════════════════════════════════════
    
    private static class DedicatedAllocation {
        final long memoryHandle;
        final long size;
        final int memoryTypeIndex;
        final long mappedPointer;
        final MemoryAllocation allocation;
        
        DedicatedAllocation(long memoryHandle, long size, int memoryTypeIndex, 
                           long mappedPointer, MemoryAllocation allocation) {
            this.memoryHandle = memoryHandle;
            this.size = size;
            this.memoryTypeIndex = memoryTypeIndex;
            this.mappedPointer = mappedPointer;
            this.allocation = allocation;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════
    
    private final VulkanContext context;
    private final Config config;
    
    // Memory properties
    private final VkPhysicalDeviceMemoryProperties memoryProperties;
    private final int memoryTypeCount;
    private final int memoryHeapCount;
    private final long[] heapSizes;
    private final AtomicLong[] heapUsage;
    private final int[] memoryTypeToHeap;
    
    // Pools by memory type
    private final Map<Integer, MemoryPool> pools = new ConcurrentHashMap<>();
    
    // Linear allocators for per-frame staging
    private final Map<Integer, LinearAllocator> linearAllocators = new ConcurrentHashMap<>();
    
    // Dedicated allocations
    private final Map<Long, DedicatedAllocation> dedicatedAllocations = new ConcurrentHashMap<>();
    
    // Counters
    private final AtomicInteger nextBlockId = new AtomicInteger(0);
    private final AtomicLong frameCounter = new AtomicLong(0);
    
    // Global statistics
    private final AtomicLong totalAllocatedBytes = new AtomicLong(0);
    private final AtomicLong totalAllocationCount = new AtomicLong(0);
    private final AtomicLong dedicatedAllocationBytes = new AtomicLong(0);
    private final AtomicInteger dedicatedAllocationCount = new AtomicInteger(0);
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════════
    
    public VulkanMemoryAllocator(VulkanContext context) {
        this(context, new Config());
    }
    
    public VulkanMemoryAllocator(VulkanContext context, Config config) {
        this.context = context;
        this.config = config;
        
        // Query memory properties
        this.memoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
        vkGetPhysicalDeviceMemoryProperties(context.physicalDevice, memoryProperties);
        
        this.memoryTypeCount = memoryProperties.memoryTypeCount();
        this.memoryHeapCount = memoryProperties.memoryHeapCount();
        
        this.heapSizes = new long[memoryHeapCount];
        this.heapUsage = new AtomicLong[memoryHeapCount];
        for (int i = 0; i < memoryHeapCount; i++) {
            heapSizes[i] = memoryProperties.memoryHeaps(i).size();
            heapUsage[i] = new AtomicLong(0);
        }
        
        this.memoryTypeToHeap = new int[memoryTypeCount];
        for (int i = 0; i < memoryTypeCount; i++) {
            memoryTypeToHeap[i] = memoryProperties.memoryTypes(i).heapIndex();
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // MEMORY TYPE SELECTION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Find optimal memory type for the given requirements.
     */
    public int findMemoryType(int memoryTypeBits, MemoryUsage usage) {
        int required = 0;
        int preferred = 0;
        
        switch (usage) {
            case GPU_ONLY -> {
                required = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
            }
            case CPU_TO_GPU, CPU_TO_GPU_FRAME -> {
                required = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
                preferred = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | 
                           VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            }
            case GPU_TO_CPU -> {
                required = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
                preferred = VK_MEMORY_PROPERTY_HOST_CACHED_BIT |
                           VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            }
            case CPU_CACHED -> {
                required = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                          VK_MEMORY_PROPERTY_HOST_CACHED_BIT;
                preferred = VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            }
            case GPU_LAZY -> {
                required = VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT;
                preferred = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
            }
        }
        
        return findMemoryType(memoryTypeBits, required, preferred);
    }
    
    public int findMemoryType(int memoryTypeBits, int requiredFlags, int preferredFlags) {
        int bestType = -1;
        int bestScore = -1;
        
        for (int i = 0; i < memoryTypeCount; i++) {
            // Check if this type is allowed
            if ((memoryTypeBits & (1 << i)) == 0) continue;
            
            int flags = memoryProperties.memoryTypes(i).propertyFlags();
            
            // Must have all required flags
            if ((flags & requiredFlags) != requiredFlags) continue;
            
            // Score based on preferred flags and heap availability
            int score = Integer.bitCount(flags & preferredFlags) * 100;
            
            // Prefer device-local
            if ((flags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0) {
                score += 1000;
            }
            
            // Prefer heaps with more available space
            int heapIndex = memoryTypeToHeap[i];
            long available = heapSizes[heapIndex] - heapUsage[heapIndex].get();
            score += (int) Math.min(available / (1024 * 1024), 100); // Up to 100 bonus
            
            if (score > bestScore) {
                bestScore = score;
                bestType = i;
            }
        }
        
        if (bestType == -1) {
            // Fallback: find any type with required flags
            for (int i = 0; i < memoryTypeCount; i++) {
                if ((memoryTypeBits & (1 << i)) == 0) continue;
                int flags = memoryProperties.memoryTypes(i).propertyFlags();
                if ((flags & requiredFlags) == requiredFlags) {
                    return i;
                }
            }
        }
        
        return bestType;
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // ALLOCATION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Allocate memory for any usage.
     */
    public MemoryAllocation allocate(AllocationCreateInfo info) {
        // Find memory type
        int memoryType = findMemoryType(info.memoryTypeBits, info.usage);
        if (memoryType < 0) {
            memoryType = findMemoryType(info.memoryTypeBits, info.requiredFlags, info.preferredFlags);
        }
        if (memoryType < 0) {
            throw new RuntimeException("No suitable memory type found for: " + info.debugName);
        }
        
        // Determine allocation strategy
        boolean useDedicated = (info.flags & AllocationFlags.DEDICATED.bits) != 0 ||
            (info.size >= config.dedicatedThreshold && 
             (info.flags & AllocationFlags.NEVER_DEDICATED.bits) == 0);
        
        boolean useLinear = info.usage == MemoryUsage.CPU_TO_GPU_FRAME;
        
        // Allocate
        MemoryAllocation allocation;
        
        if (useDedicated) {
            allocation = allocateDedicated(info.size, memoryType, info.debugName);
        } else if (useLinear) {
            allocation = allocateLinear(info.size, info.alignment, memoryType, info.debugName);
        } else {
            allocation = allocateFromPool(info.size, info.alignment, memoryType, info.debugName);
        }
        
        if (allocation == null) {
            throw new RuntimeException("Failed to allocate " + info.size + " bytes: " + info.debugName);
        }
        
        // Update heap usage
        int heapIndex = memoryTypeToHeap[allocation.getMemoryTypeIndex()];
        heapUsage[heapIndex].addAndGet(allocation.getAlignedSize());
        totalAllocatedBytes.addAndGet(allocation.getAlignedSize());
        totalAllocationCount.incrementAndGet();
        
        return allocation;
    }
    
    /**
     * Allocate memory for a buffer with automatic binding.
     */
    public MemoryAllocation allocateForBuffer(long buffer, MemoryUsage usage, String debugName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryRequirements memReqs = VkMemoryRequirements.malloc(stack);
            vkGetBufferMemoryRequirements(context.device, buffer, memReqs);
            
            AllocationCreateInfo info = new AllocationCreateInfo()
                .size(memReqs.size())
                .alignment(memReqs.alignment())
                .memoryTypeBits((int) memReqs.memoryTypeBits())
                .usage(usage)
                .debugName(debugName);
            
            // Check for dedicated allocation requirement
            if (context.supportsVK12()) {
                VkMemoryDedicatedRequirements dedicatedReqs = VkMemoryDedicatedRequirements.malloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_DEDICATED_REQUIREMENTS);
                
                VkMemoryRequirements2 memReqs2 = VkMemoryRequirements2.malloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_REQUIREMENTS_2)
                    .pNext(dedicatedReqs.address());
                
                VkBufferMemoryRequirementsInfo2 bufferInfo = VkBufferMemoryRequirementsInfo2.malloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_MEMORY_REQUIREMENTS_INFO_2)
                    .buffer(buffer);
                
                vkGetBufferMemoryRequirements2(context.device, bufferInfo, memReqs2);
                
                if (dedicatedReqs.requiresDedicatedAllocation()) {
                    info.flags |= AllocationFlags.DEDICATED.bits;
                }
            }
            
            MemoryAllocation allocation = allocate(info);
            
            // Bind buffer to memory
            int result = vkBindBufferMemory(context.device, buffer,
                allocation.getMemoryHandle(), allocation.getOffset());
            
            if (result != VK_SUCCESS) {
                free(allocation);
                throw new RuntimeException("Failed to bind buffer memory: " + result);
            }
            
            // Get buffer device address
            if (config.enableDeviceAddress && context.supportsBufferDeviceAddress()) {
                VkBufferDeviceAddressInfo addressInfo = VkBufferDeviceAddressInfo.malloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO)
                    .buffer(buffer);
                
                long deviceAddress = vkGetBufferDeviceAddress(context.device, addressInfo);
                allocation.setBufferInfo(buffer, deviceAddress);
            }
            
            return allocation;
        }
    }
    
    /**
     * Allocate memory for an image with automatic binding.
     */
    public MemoryAllocation allocateForImage(long image, String debugName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryRequirements memReqs = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(context.device, image, memReqs);
            
            AllocationCreateInfo info = new AllocationCreateInfo()
                .size(memReqs.size())
                .alignment(memReqs.alignment())
                .memoryTypeBits((int) memReqs.memoryTypeBits())
                .usage(MemoryUsage.GPU_ONLY)
                .debugName(debugName);
            
            // Check for dedicated allocation
            if (context.supportsVK12()) {
                VkMemoryDedicatedRequirements dedicatedReqs = VkMemoryDedicatedRequirements.malloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_DEDICATED_REQUIREMENTS);
                
                VkMemoryRequirements2 memReqs2 = VkMemoryRequirements2.malloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_REQUIREMENTS_2)
                    .pNext(dedicatedReqs.address());
                
                VkImageMemoryRequirementsInfo2 imageInfo = VkImageMemoryRequirementsInfo2.malloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_REQUIREMENTS_INFO_2)
                    .image(image);
                
                vkGetImageMemoryRequirements2(context.device, imageInfo, memReqs2);
                
                if (dedicatedReqs.requiresDedicatedAllocation() || 
                    dedicatedReqs.prefersDedicatedAllocation()) {
                    info.flags |= AllocationFlags.DEDICATED.bits;
                }
            }
            
            MemoryAllocation allocation = allocate(info);
            
            // Bind image to memory
            int result = vkBindImageMemory(context.device, image,
                allocation.getMemoryHandle(), allocation.getOffset());
            
            if (result != VK_SUCCESS) {
                free(allocation);
                throw new RuntimeException("Failed to bind image memory: " + result);
            }
            
            return allocation;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL ALLOCATION METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    private MemoryAllocation allocateFromPool(long size, long alignment, int memoryType, String debugName) {
        MemoryPool pool = getOrCreatePool(memoryType);
        return pool.allocate(size, alignment > 0 ? alignment : 256, debugName);
    }
    
    private MemoryAllocation allocateLinear(long size, long alignment, int memoryType, String debugName) {
        LinearAllocator allocator = linearAllocators.computeIfAbsent(memoryType, 
            mt -> new LinearAllocator(mt, config.maxFramesInFlight, config.linearBlockSize));
        return allocator.allocate(size, alignment > 0 ? alignment : 256, debugName);
    }
    
    private MemoryAllocation allocateDedicated(long size, int memoryType, String debugName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.malloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(size)
                .memoryTypeIndex(memoryType);
            
            // Add device address flag if needed
            if (config.enableDeviceAddress && context.supportsBufferDeviceAddress()) {
                VkMemoryAllocateFlagsInfo flagsInfo = VkMemoryAllocateFlagsInfo.malloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO)
                    .flags(VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT)
                    .deviceMask(0);
                allocInfo.pNext(flagsInfo.address());
            }
            
            // Add priority if supported
            if (context.supportsMemoryPriority()) {
                VkMemoryPriorityAllocateInfoEXT priorityInfo = VkMemoryPriorityAllocateInfoEXT.malloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_PRIORITY_ALLOCATE_INFO_EXT)
                    .priority(0.5f);
                // Chain after flags info if present
            }
            
            LongBuffer pMemory = stack.mallocLong(1);
            int result = vkAllocateMemory(context.device, allocInfo, null, pMemory);
            
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate dedicated memory: " + translateVkResult(result));
            }
            
            long memoryHandle = pMemory.get(0);
            int propertyFlags = memoryProperties.memoryTypes(memoryType).propertyFlags();
            
            // Map if host visible
            long mappedPtr = 0;
            if ((propertyFlags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0) {
                PointerBuffer pData = stack.mallocPointer(1);
                result = vkMapMemory(context.device, memoryHandle, 0, size, 0, pData);
                if (result == VK_SUCCESS) {
                    mappedPtr = pData.get(0);
                }
            }
            
            MemoryAllocation allocation = MemoryAllocation.builder()
                .memoryHandle(memoryHandle)
                .offset(0)
                .size(size)
                .alignedSize(size)
                .blockId(-1)
                .type(MemoryAllocation.AllocationType.DEDICATED)
                .memoryTypeIndex(memoryType)
                .propertyFlags(propertyFlags)
                .baseMappedPtr(mappedPtr)
                .ownsMapping(true)
                .debugName(debugName)
                .allocationFrame(frameCounter.get())
                .build();
            
            DedicatedAllocation dedicated = new DedicatedAllocation(
                memoryHandle, size, memoryType, mappedPtr, allocation);
            dedicatedAllocations.put(memoryHandle, dedicated);
            
            dedicatedAllocationBytes.addAndGet(size);
            dedicatedAllocationCount.incrementAndGet();
            
            return allocation;
        }
    }
    
    private MemoryPool getOrCreatePool(int memoryType) {
        return pools.computeIfAbsent(memoryType, mt -> {
            int flags = memoryProperties.memoryTypes(mt).propertyFlags();
            long blockSize = config.defaultBlockSize;
            
            // Use smaller blocks for host-visible memory (often limited)
            if ((flags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) == 0) {
                blockSize = config.smallBlockSize;
            }
            
            return new MemoryPool(mt, flags, blockSize, config.defaultStrategy);
        });
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // FREE
    // ═══════════════════════════════════════════════════════════════════════
    
    public void free(MemoryAllocation allocation) {
        if (allocation == null || allocation.isFreed()) return;
        
        // Update heap usage
        int heapIndex = memoryTypeToHeap[allocation.getMemoryTypeIndex()];
        heapUsage[heapIndex].addAndGet(-allocation.getAlignedSize());
        totalAllocatedBytes.addAndGet(-allocation.getAlignedSize());
        totalAllocationCount.decrementAndGet();
        
        if (allocation.isDedicated()) {
            freeDedicated(allocation);
        } else if (allocation.getType() == MemoryAllocation.AllocationType.LINEAR) {
            // Linear allocations are freed in bulk during nextFrame()
            allocation.markFreed();
        } else {
            MemoryPool pool = pools.get(allocation.getMemoryTypeIndex());
            if (pool != null) {
                pool.free(allocation);
            }
        }
    }
    
    private void freeDedicated(MemoryAllocation allocation) {
        DedicatedAllocation dedicated = dedicatedAllocations.remove(allocation.getMemoryHandle());
        if (dedicated == null) return;
        
        if (dedicated.mappedPointer != 0) {
            vkUnmapMemory(context.device, dedicated.memoryHandle);
        }
        
        vkFreeMemory(context.device, dedicated.memoryHandle, null);
        allocation.markFreed();
        
        dedicatedAllocationBytes.addAndGet(-dedicated.size);
        dedicatedAllocationCount.decrementAndGet();
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // FRAME MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Call at the start of each frame to reset per-frame allocators.
     */
    public void beginFrame() {
        frameCounter.incrementAndGet();
        
        // Reset linear allocators
        for (LinearAllocator allocator : linearAllocators.values()) {
            allocator.nextFrame();
        }
    }
    
    public long getCurrentFrame() {
        return frameCounter.get();
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // DEFRAGMENTATION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Defragment memory pools by removing empty blocks.
     */
    public void defragment() {
        if (!config.enableDefragmentation) return;
        
        for (MemoryPool pool : pools.values()) {
            pool.defragment();
        }
    }
    
    /**
     * Get defragmentation statistics.
     */
    public DefragStats getDefragmentationStats() {
        long totalFragmented = 0;
        long totalWasted = 0;
        
        for (MemoryPool pool : pools.values()) {
            for (MemoryBlock block : pool.blocks) {
                long blockFree = block.getFreeBytes();
                if (block.getAllocationCount() > 0) {
                    totalFragmented += blockFree;
                } else {
                    totalWasted += block.getTotalSize();
                }
            }
        }
        
        return new DefragStats(totalFragmented, totalWasted, 
            totalFragmented + totalWasted > 0);
    }
    
    public record DefragStats(long fragmentedBytes, long wastedBytes, boolean recommended) {}
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ═══════════════════════════════════════════════════════════════════════
    
    public AllocatorStats getStatistics() {
        List<PoolStats> poolStats = new ArrayList<>();
        for (MemoryPool pool : pools.values()) {
            poolStats.add(pool.getStats());
        }
        
        long[] heapUsed = new long[heapUsage.length];
        for (int i = 0; i < heapUsage.length; i++) {
            heapUsed[i] = heapUsage[i].get();
        }
        
        return new AllocatorStats(
            totalAllocatedBytes.get(),
            totalAllocationCount.get(),
            dedicatedAllocationBytes.get(),
            dedicatedAllocationCount.get(),
            pools.size(),
            poolStats,
            heapSizes.clone(),
            heapUsed
        );
    }
    
    public record AllocatorStats(
        long totalAllocatedBytes,
        long totalAllocationCount,
        long dedicatedBytes,
        int dedicatedCount,
        int poolCount,
        List<PoolStats> pools,
        long[] heapSizes,
        long[] heapUsage
    ) {
        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Vulkan Memory Allocator ===\n");
            sb.append(String.format("Total: %s in %d allocations\n",
                formatBytes(totalAllocatedBytes), totalAllocationCount));
            sb.append(String.format("Dedicated: %s in %d allocations\n",
                formatBytes(dedicatedBytes), dedicatedCount));
            
            sb.append("\nHeaps:\n");
            for (int i = 0; i < heapSizes.length; i++) {
                double pct = 100.0 * heapUsage[i] / heapSizes[i];
                sb.append(String.format("  %d: %s / %s (%.1f%%)\n",
                    i, formatBytes(heapUsage[i]), formatBytes(heapSizes[i]), pct));
            }
            
            sb.append("\nPools:\n");
            for (PoolStats ps : pools) {
                sb.append(String.format("  Type %d: %d blocks, %s in %d allocs\n",
                    ps.memoryTypeIndex, ps.blockCount,
                    formatBytes(ps.allocatedMemory), ps.allocationCount));
            }
            
            return sb.toString();
        }
        
        private static String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════════
    
    public void shutdown() {
        // Destroy pools
        for (MemoryPool pool : pools.values()) {
            pool.destroy();
        }
        pools.clear();
        
        // Destroy linear allocators
        for (LinearAllocator allocator : linearAllocators.values()) {
            allocator.destroy();
        }
        linearAllocators.clear();
        
        // Free dedicated allocations
        for (DedicatedAllocation dedicated : dedicatedAllocations.values()) {
            if (dedicated.mappedPointer != 0) {
                vkUnmapMemory(context.device, dedicated.memoryHandle);
            }
            vkFreeMemory(context.device, dedicated.memoryHandle, null);
        }
        dedicatedAllocations.clear();
        
        memoryProperties.free();
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════
    
    private static String translateVkResult(int result) {
        return switch (result) {
            case VK_SUCCESS -> "VK_SUCCESS";
            case VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY";
            case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
            case VK_ERROR_TOO_MANY_OBJECTS -> "VK_ERROR_TOO_MANY_OBJECTS";
            case VK_ERROR_MEMORY_MAP_FAILED -> "VK_ERROR_MEMORY_MAP_FAILED";
            default -> "VK_ERROR_" + result;
        };
    }
}
