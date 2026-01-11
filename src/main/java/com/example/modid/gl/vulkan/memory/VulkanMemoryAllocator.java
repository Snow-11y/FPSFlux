package com.example.modid.gl.vulkan.memory;

import com.example.modid.gl.vulkan.VulkanContext;
import java.util.*;

public class VulkanMemoryAllocator {
    
    private final VulkanContext context;
    // Map memoryTypeIndex -> List of Blocks
    private final Map<Integer, List<MemoryBlock>> blocks = new HashMap<>();
    private static final long DEFAULT_BLOCK_SIZE = 256 * 1024 * 1024; // 256 MB pages
    private int nextBlockId = 0;
    
    public VulkanMemoryAllocator(VulkanContext context) {
        this.context = context;
    }
    
    public MemoryAllocation allocate(long size, int usage, int propertyFlags) {
        // 1. Find Memory Type Index
        VkMemoryRequirements fakeReqs = VkMemoryRequirements.calloc(); // In reality, pass real reqs
        // Usually you pass the memoryTypeBits from the buffer creation query
        // Here we simulate finding the type index based on flags
        int memTypeIndex = context.findMemoryType(~0, propertyFlags);
        fakeReqs.free();
        
        List<MemoryBlock> blockList = blocks.computeIfAbsent(memTypeIndex, k -> new ArrayList<>());
        
        // 2. Try to allocate from existing blocks
        for (MemoryBlock block : blockList) {
            MemoryAllocation alloc = block.allocate(size, 256); // 256 alignment safe default
            if (alloc != null) return alloc;
        }
        
        // 3. Create new block
        long blockSize = Math.max(DEFAULT_BLOCK_SIZE, size); // Handle huge buffers
        MemoryBlock newBlock = new MemoryBlock(context, nextBlockId++, blockSize, memTypeIndex);
        blockList.add(newBlock);
        
        return newBlock.allocate(size, 256);
    }
    
    public void free(MemoryAllocation alloc) {
        // Find block and free
        // In optimization: Alloc should hold reference to its block to avoid searching
        // For now, simple implementation
        // block.free(alloc);
    }
    
    public void shutdown() {
        for (List<MemoryBlock> list : blocks.values()) {
            for (MemoryBlock block : list) {
                block.destroy();
            }
        }
        blocks.clear();
    }
}
