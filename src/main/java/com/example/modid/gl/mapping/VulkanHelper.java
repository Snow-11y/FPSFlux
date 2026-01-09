package com.example.modid.gl.mapping;

import com.example.modid.FPSFlux;
import com.example.modid.gl.vulkan.VulkanContext;
import com.example.modid.gl.vulkan.VulkanState;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.vulkan.VK12.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * VulkanHelper - Utility class for VulkanCallMapperX
 * 
 * Provides:
 * - Safe resource creation and cleanup
 * - Image/texture creation and upload
 * - Buffer management with staging
 * - Descriptor set updates
 * - Image layout transitions
 * - Mipmap generation
 * - Format conversions (GL <-> VK)
 * - Shader preprocessing (GLSL GL -> Vulkan)
 * - Error handling utilities
 * - FBO/RBO emulation support
 * - Memory allocation tracking
 */
public class VulkanHelper {
    
    // ========================================================================
    // CONSTANTS
    // ========================================================================
    
    private static final long STAGING_BUFFER_SIZE = 64 * 1024 * 1024; // 64MB staging buffer
    private static final int MAX_MIP_LEVELS = 16;
    
    // ========================================================================
    // CONTEXT REFERENCES
    // ========================================================================
    
    private static VulkanContext ctx;
    private static VulkanState state;
    
    // ========================================================================
    // STAGING BUFFER POOL
    // ========================================================================
    
    private static final ConcurrentLinkedQueue<StagingBuffer> stagingBufferPool = new ConcurrentLinkedQueue<>();
    private static final List<StagingBuffer> activeStagingBuffers = Collections.synchronizedList(new ArrayList<>());
    
    public static class StagingBuffer {
        public long buffer;
        public long memory;
        public long size;
        public ByteBuffer mapped;
        public long fence;
        public boolean inUse;
    }
    
    // ========================================================================
    // RESOURCE TRACKING
    // ========================================================================
    
    private static final Set<Long> allocatedBuffers = Collections.synchronizedSet(new HashSet<>());
    private static final Set<Long> allocatedImages = Collections.synchronizedSet(new HashSet<>());
    private static final Set<Long> allocatedImageViews = Collections.synchronizedSet(new HashSet<>());
    private static final Set<Long> allocatedSamplers = Collections.synchronizedSet(new HashSet<>());
    private static final Set<Long> allocatedMemory = Collections.synchronizedSet(new HashSet<>());
    private static final Set<Long> allocatedDescriptorSets = Collections.synchronizedSet(new HashSet<>());
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    public static void initialize(VulkanContext context, VulkanState vulkanState) {
        ctx = context;
        state = vulkanState;
        
        // Pre-allocate staging buffers
        for (int i = 0; i < 3; i++) {
            StagingBuffer sb = createStagingBuffer(STAGING_BUFFER_SIZE);
            if (sb != null) {
                stagingBufferPool.add(sb);
            }
        }
        
        FPSFlux.LOGGER.info("[VulkanHelper] Initialized with {} staging buffers", stagingBufferPool.size());
    }
    
    public static void shutdown() {
        // Wait for GPU
        if (ctx != null && ctx.device != null) {
            vkDeviceWaitIdle(ctx.device);
        }
        
        // Free staging buffers
        for (StagingBuffer sb : stagingBufferPool) {
            destroyStagingBuffer(sb);
        }
        stagingBufferPool.clear();
        
        for (StagingBuffer sb : activeStagingBuffers) {
            destroyStagingBuffer(sb);
        }
        activeStagingBuffers.clear();
        
        // Log leaked resources
        if (!allocatedBuffers.isEmpty()) {
            FPSFlux.LOGGER.warn("[VulkanHelper] {} buffers were not freed", allocatedBuffers.size());
        }
        if (!allocatedImages.isEmpty()) {
            FPSFlux.LOGGER.warn("[VulkanHelper] {} images were not freed", allocatedImages.size());
        }
        if (!allocatedImageViews.isEmpty()) {
            FPSFlux.LOGGER.warn("[VulkanHelper] {} image views were not freed", allocatedImageViews.size());
        }
        if (!allocatedSamplers.isEmpty()) {
            FPSFlux.LOGGER.warn("[VulkanHelper] {} samplers were not freed", allocatedSamplers.size());
        }
        if (!allocatedMemory.isEmpty()) {
            FPSFlux.LOGGER.warn("[VulkanHelper] {} memory allocations were not freed", allocatedMemory.size());
        }
        
        ctx = null;
        state = null;
        
        FPSFlux.LOGGER.info("[VulkanHelper] Shutdown complete");
    }
    
    // ========================================================================
    // STAGING BUFFER MANAGEMENT
    // ========================================================================
    
    private static StagingBuffer createStagingBuffer(long size) {
        try (MemoryStack stack = stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size)
                .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            
            LongBuffer pBuffer = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
            if (result != VK_SUCCESS) {
                FPSFlux.LOGGER.error("[VulkanHelper] Failed to create staging buffer: {}", result);
                return null;
            }
            
            long buffer = pBuffer.get(0);
            
            VkMemoryRequirements memReqs = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(ctx.device, buffer, memReqs);
            
            int memoryType = ctx.findMemoryType(memReqs.memoryTypeBits(),
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(memoryType);
            
            LongBuffer pMemory = stack.longs(VK_NULL_HANDLE);
            result = vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
            if (result != VK_SUCCESS) {
                vkDestroyBuffer(ctx.device, buffer, null);
                FPSFlux.LOGGER.error("[VulkanHelper] Failed to allocate staging memory: {}", result);
                return null;
            }
            
            long memory = pMemory.get(0);
            vkBindBufferMemory(ctx.device, buffer, memory, 0);
            
            // Map persistently
            PointerBuffer pData = stack.mallocPointer(1);
            result = vkMapMemory(ctx.device, memory, 0, size, 0, pData);
            if (result != VK_SUCCESS) {
                vkDestroyBuffer(ctx.device, buffer, null);
                vkFreeMemory(ctx.device, memory, null);
                FPSFlux.LOGGER.error("[VulkanHelper] Failed to map staging memory: {}", result);
                return null;
            }
            
            // Create fence
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .flags(VK_FENCE_CREATE_SIGNALED_BIT);
            
            LongBuffer pFence = stack.longs(VK_NULL_HANDLE);
            vkCreateFence(ctx.device, fenceInfo, null, pFence);
            
            StagingBuffer sb = new StagingBuffer();
            sb.buffer = buffer;
            sb.memory = memory;
            sb.size = size;
            sb.mapped = memByteBuffer(pData.get(0), (int) size);
            sb.fence = pFence.get(0);
            sb.inUse = false;
            
            return sb;
        }
    }
    
    private static void destroyStagingBuffer(StagingBuffer sb) {
        if (sb == null) return;
        
        if (sb.fence != VK_NULL_HANDLE) {
            vkDestroyFence(ctx.device, sb.fence, null);
        }
        if (sb.memory != VK_NULL_HANDLE) {
            vkUnmapMemory(ctx.device, sb.memory);
            vkFreeMemory(ctx.device, sb.memory, null);
        }
        if (sb.buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(ctx.device, sb.buffer, null);
        }
    }
    
    public static StagingBuffer acquireStagingBuffer(long requiredSize) {
        // Try to get from pool
        StagingBuffer sb = stagingBufferPool.poll();
        if (sb != null && sb.size >= requiredSize) {
            // Wait for fence
            try (MemoryStack stack = stackPush()) {
                LongBuffer pFence = stack.longs(sb.fence);
                vkWaitForFences(ctx.device, pFence, true, Long.MAX_VALUE);
                vkResetFences(ctx.device, pFence);
            }
            sb.inUse = true;
            activeStagingBuffers.add(sb);
            return sb;
        }
        
        // Return to pool if too small
        if (sb != null) {
            stagingBufferPool.add(sb);
        }
        
        // Create new larger buffer
        long newSize = Math.max(requiredSize, STAGING_BUFFER_SIZE);
        sb = createStagingBuffer(newSize);
        if (sb != null) {
            sb.inUse = true;
            activeStagingBuffers.add(sb);
        }
        return sb;
    }
    
    public static void releaseStagingBuffer(StagingBuffer sb) {
        if (sb == null) return;
        sb.inUse = false;
        activeStagingBuffers.remove(sb);
        stagingBufferPool.add(sb);
    }
    
    // ========================================================================
    // BUFFER CREATION
    // ========================================================================
    
    public static class BufferAllocation {
        public long buffer;
        public long memory;
        public long size;
        public ByteBuffer mapped; // null if not host visible
    }
    
    /**
     * Create a Vulkan buffer with memory allocation
     */
    public static BufferAllocation createBuffer(long size, int usage, int memoryProperties) {
        checkContext();
        
        try (MemoryStack stack = stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size)
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            
            LongBuffer pBuffer = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create buffer: " + translateVkResult(result));
            }
            
            long buffer = pBuffer.get(0);
            allocatedBuffers.add(buffer);
            
            VkMemoryRequirements memReqs = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(ctx.device, buffer, memReqs);
            
            int memoryType = ctx.findMemoryType(memReqs.memoryTypeBits(), memoryProperties);
            
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(memoryType);
            
            LongBuffer pMemory = stack.longs(VK_NULL_HANDLE);
            result = vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
            if (result != VK_SUCCESS) {
                vkDestroyBuffer(ctx.device, buffer, null);
                allocatedBuffers.remove(buffer);
                throw new RuntimeException("Failed to allocate buffer memory: " + translateVkResult(result));
            }
            
            long memory = pMemory.get(0);
            allocatedMemory.add(memory);
            
            vkBindBufferMemory(ctx.device, buffer, memory, 0);
            
            BufferAllocation alloc = new BufferAllocation();
            alloc.buffer = buffer;
            alloc.memory = memory;
            alloc.size = size;
            
            // Map if host visible
            if ((memoryProperties & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0) {
                PointerBuffer pData = stack.mallocPointer(1);
                result = vkMapMemory(ctx.device, memory, 0, size, 0, pData);
                if (result == VK_SUCCESS) {
                    alloc.mapped = memByteBuffer(pData.get(0), (int) size);
                }
            }
            
            return alloc;
        }
    }
    
    /**
     * Create a buffer and upload data using staging
     */
    public static BufferAllocation createBufferWithData(ByteBuffer data, int usage) {
        checkContext();
        
        long size = data.remaining();
        
        // Create device-local buffer
        BufferAllocation alloc = createBuffer(size, 
            usage | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        
        // Upload via staging
        uploadBufferData(alloc.buffer, 0, data);
        
        return alloc;
    }
    
    /**
     * Upload data to a buffer using staging buffer
     */
    public static void uploadBufferData(long dstBuffer, long offset, ByteBuffer data) {
        checkContext();
        
        long size = data.remaining();
        StagingBuffer staging = acquireStagingBuffer(size);
        
        if (staging == null) {
            throw new RuntimeException("Failed to acquire staging buffer");
        }
        
        try {
            // Copy to staging
            staging.mapped.clear();
            staging.mapped.put(data);
            data.rewind();
            
            // Copy to device
            VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
            
            try (MemoryStack stack = stackPush()) {
                VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0)
                    .dstOffset(offset)
                    .size(size);
                
                vkCmdCopyBuffer(cmdBuffer, staging.buffer, dstBuffer, copyRegion);
            }
            
            // Submit with fence
            vkEndCommandBuffer(cmdBuffer);
            
            try (MemoryStack stack = stackPush()) {
                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(cmdBuffer));
                
                vkQueueSubmit(ctx.graphicsQueue, submitInfo, staging.fence);
            }
            
            // Wait and cleanup
            try (MemoryStack stack = stackPush()) {
                LongBuffer pFence = stack.longs(staging.fence);
                vkWaitForFences(ctx.device, pFence, true, Long.MAX_VALUE);
            }
            
            vkFreeCommandBuffers(ctx.device, ctx.commandPool, cmdBuffer);
            
        } finally {
            releaseStagingBuffer(staging);
        }
    }
    
    /**
     * Destroy a buffer and its memory
     */
    public static void destroyBuffer(BufferAllocation alloc) {
        if (alloc == null) return;
        checkContext();
        
        if (alloc.mapped != null && alloc.memory != VK_NULL_HANDLE) {
            vkUnmapMemory(ctx.device, alloc.memory);
        }
        if (alloc.buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(ctx.device, alloc.buffer, null);
            allocatedBuffers.remove(alloc.buffer);
        }
        if (alloc.memory != VK_NULL_HANDLE) {
            vkFreeMemory(ctx.device, alloc.memory, null);
            allocatedMemory.remove(alloc.memory);
        }
    }
    
    // ========================================================================
    // IMAGE/TEXTURE CREATION
    // ========================================================================
    
    public static class ImageAllocation {
        public long image;
        public long memory;
        public long imageView;
        public int width;
        public int height;
        public int depth;
        public int mipLevels;
        public int arrayLayers;
        public int format;
        public int currentLayout;
    }
    
    /**
     * Create a 2D image with memory
     */
    public static ImageAllocation createImage2D(int width, int height, int format, int usage, 
                                                 int mipLevels, int samples) {
        checkContext();
        
        if (mipLevels <= 0) {
            mipLevels = calculateMipLevels(width, height);
        }
        
        try (MemoryStack stack = stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK_IMAGE_TYPE_2D)
                .format(format)
                .mipLevels(mipLevels)
                .arrayLayers(1)
                .samples(samples)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            
            imageInfo.extent().width(width).height(height).depth(1);
            
            LongBuffer pImage = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateImage(ctx.device, imageInfo, null, pImage);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create image: " + translateVkResult(result));
            }
            
            long image = pImage.get(0);
            allocatedImages.add(image);
            
            VkMemoryRequirements memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(ctx.device, image, memReqs);
            
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(), 
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            
            LongBuffer pMemory = stack.longs(VK_NULL_HANDLE);
            result = vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
            if (result != VK_SUCCESS) {
                vkDestroyImage(ctx.device, image, null);
                allocatedImages.remove(image);
                throw new RuntimeException("Failed to allocate image memory: " + translateVkResult(result));
            }
            
            long memory = pMemory.get(0);
            allocatedMemory.add(memory);
            
            vkBindImageMemory(ctx.device, image, memory, 0);
            
            // Create image view
            int aspectMask = isDepthFormat(format) ? 
                (hasStencilComponent(format) ? VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT 
                                             : VK_IMAGE_ASPECT_DEPTH_BIT)
                : VK_IMAGE_ASPECT_COLOR_BIT;
            
            long imageView = createImageView(image, format, aspectMask, mipLevels, 1, VK_IMAGE_VIEW_TYPE_2D);
            
            ImageAllocation alloc = new ImageAllocation();
            alloc.image = image;
            alloc.memory = memory;
            alloc.imageView = imageView;
            alloc.width = width;
            alloc.height = height;
            alloc.depth = 1;
            alloc.mipLevels = mipLevels;
            alloc.arrayLayers = 1;
            alloc.format = format;
            alloc.currentLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            
            return alloc;
        }
    }
    
    /**
     * Create a 2D texture from pixel data
     */
    public static ImageAllocation createTexture2D(int width, int height, int format, 
                                                   ByteBuffer pixels, boolean generateMipmaps) {
        checkContext();
        
        int mipLevels = generateMipmaps ? calculateMipLevels(width, height) : 1;
        
        // Create image
        int usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
        if (generateMipmaps) {
            usage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
        }
        
        ImageAllocation alloc = createImage2D(width, height, format, usage, mipLevels, VK_SAMPLE_COUNT_1_BIT);
        
        // Upload pixel data
        if (pixels != null && pixels.remaining() > 0) {
            uploadImageData(alloc, pixels, 0, 0, width, height, 0, 0);
            
            // Generate mipmaps or transition to shader read
            if (generateMipmaps && mipLevels > 1) {
                generateMipmaps(alloc);
            } else {
                transitionImageLayout(alloc, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, 
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
            }
        }
        
        return alloc;
    }
    
    /**
     * Create a cube map texture
     */
    public static ImageAllocation createTextureCube(int size, int format, ByteBuffer[] faces, 
                                                     boolean generateMipmaps) {
        checkContext();
        
        int mipLevels = generateMipmaps ? calculateMipLevels(size, size) : 1;
        
        try (MemoryStack stack = stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .flags(VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT)
                .imageType(VK_IMAGE_TYPE_2D)
                .format(format)
                .mipLevels(mipLevels)
                .arrayLayers(6)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT |
                       (generateMipmaps ? VK_IMAGE_USAGE_TRANSFER_SRC_BIT : 0))
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            
            imageInfo.extent().width(size).height(size).depth(1);
            
            LongBuffer pImage = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateImage(ctx.device, imageInfo, null, pImage);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create cube map: " + translateVkResult(result));
            }
            
            long image = pImage.get(0);
            allocatedImages.add(image);
            
            VkMemoryRequirements memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(ctx.device, image, memReqs);
            
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(), 
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            
            LongBuffer pMemory = stack.longs(VK_NULL_HANDLE);
            result = vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
            if (result != VK_SUCCESS) {
                vkDestroyImage(ctx.device, image, null);
                allocatedImages.remove(image);
                throw new RuntimeException("Failed to allocate cube map memory: " + translateVkResult(result));
            }
            
            long memory = pMemory.get(0);
            allocatedMemory.add(memory);
            
            vkBindImageMemory(ctx.device, image, memory, 0);
            
            // Create cube map image view
            long imageView = createImageView(image, format, VK_IMAGE_ASPECT_COLOR_BIT, 
                mipLevels, 6, VK_IMAGE_VIEW_TYPE_CUBE);
            
            ImageAllocation alloc = new ImageAllocation();
            alloc.image = image;
            alloc.memory = memory;
            alloc.imageView = imageView;
            alloc.width = size;
            alloc.height = size;
            alloc.depth = 1;
            alloc.mipLevels = mipLevels;
            alloc.arrayLayers = 6;
            alloc.format = format;
            alloc.currentLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            
            // Upload faces
            if (faces != null) {
                for (int i = 0; i < Math.min(faces.length, 6); i++) {
                    if (faces[i] != null) {
                        uploadImageData(alloc, faces[i], 0, 0, size, size, 0, i);
                    }
                }
                
                // Transition to shader read
                transitionImageLayout(alloc, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
            }
            
            return alloc;
        }
    }
    
    /**
     * Upload pixel data to an image
     */
    public static void uploadImageData(ImageAllocation alloc, ByteBuffer pixels, 
                                        int offsetX, int offsetY, int width, int height,
                                        int mipLevel, int arrayLayer) {
        checkContext();
        
        long size = pixels.remaining();
        StagingBuffer staging = acquireStagingBuffer(size);
        
        if (staging == null) {
            throw new RuntimeException("Failed to acquire staging buffer for image upload");
        }
        
        try {
            // Copy to staging
            staging.mapped.clear();
            staging.mapped.put(pixels);
            pixels.rewind();
            
            VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
            
            // Transition to transfer dst
            transitionImageLayoutCmd(cmdBuffer, alloc.image, alloc.format,
                alloc.currentLayout, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                mipLevel, 1, arrayLayer, 1);
            
            // Copy buffer to image
            try (MemoryStack stack = stackPush()) {
                VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
                region.get(0)
                    .bufferOffset(0)
                    .bufferRowLength(0)
                    .bufferImageHeight(0);
                
                region.get(0).imageSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(mipLevel)
                    .baseArrayLayer(arrayLayer)
                    .layerCount(1);
                
                region.get(0).imageOffset().set(offsetX, offsetY, 0);
                region.get(0).imageExtent().set(width, height, 1);
                
                vkCmdCopyBufferToImage(cmdBuffer, staging.buffer, alloc.image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
            }
            
            alloc.currentLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            
            // Submit
            vkEndCommandBuffer(cmdBuffer);
            
            try (MemoryStack stack = stackPush()) {
                VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(cmdBuffer));
                
                vkQueueSubmit(ctx.graphicsQueue, submitInfo, staging.fence);
            }
            
            try (MemoryStack stack = stackPush()) {
                LongBuffer pFence = stack.longs(staging.fence);
                vkWaitForFences(ctx.device, pFence, true, Long.MAX_VALUE);
            }
            
            vkFreeCommandBuffers(ctx.device, ctx.commandPool, cmdBuffer);
            
        } finally {
            releaseStagingBuffer(staging);
        }
    }
    
    /**
     * Generate mipmaps for an image
     */
    public static void generateMipmaps(ImageAllocation alloc) {
        checkContext();
        
        if (alloc.mipLevels <= 1) return;
        
        // Check if format supports linear filtering
        try (MemoryStack stack = stackPush()) {
            VkFormatProperties formatProps = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(ctx.physicalDevice, alloc.format, formatProps);
            
            if ((formatProps.optimalTilingFeatures() & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) == 0) {
                FPSFlux.LOGGER.warn("[VulkanHelper] Format does not support linear filtering for mipmaps");
                transitionImageLayout(alloc, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT);
                return;
            }
        }
        
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .image(alloc.image)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
            
            barrier.get(0).subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseArrayLayer(0)
                .layerCount(alloc.arrayLayers)
                .levelCount(1);
            
            int mipWidth = alloc.width;
            int mipHeight = alloc.height;
            
            for (int i = 1; i < alloc.mipLevels; i++) {
                // Transition previous mip to transfer src
                barrier.get(0).subresourceRange().baseMipLevel(i - 1);
                barrier.get(0)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
                
                vkCmdPipelineBarrier(cmdBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                    null, null, barrier);
                
                // Blit
                VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
                blit.get(0).srcOffsets(0).set(0, 0, 0);
                blit.get(0).srcOffsets(1).set(mipWidth, mipHeight, 1);
                blit.get(0).srcSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(i - 1)
                    .baseArrayLayer(0)
                    .layerCount(alloc.arrayLayers);
                
                int newWidth = mipWidth > 1 ? mipWidth / 2 : 1;
                int newHeight = mipHeight > 1 ? mipHeight / 2 : 1;
                
                blit.get(0).dstOffsets(0).set(0, 0, 0);
                blit.get(0).dstOffsets(1).set(newWidth, newHeight, 1);
                blit.get(0).dstSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(i)
                    .baseArrayLayer(0)
                    .layerCount(alloc.arrayLayers);
                
                vkCmdBlitImage(cmdBuffer,
                    alloc.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    alloc.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    blit, VK_FILTER_LINEAR);
                
                // Transition to shader read
                barrier.get(0)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                
                vkCmdPipelineBarrier(cmdBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0,
                    null, null, barrier);
                
                mipWidth = newWidth;
                mipHeight = newHeight;
            }
            
            // Transition last mip
            barrier.get(0).subresourceRange().baseMipLevel(alloc.mipLevels - 1);
            barrier.get(0)
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
            
            vkCmdPipelineBarrier(cmdBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0,
                null, null, barrier);
        }
        
        ctx.endSingleTimeCommands(cmdBuffer);
        alloc.currentLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    }
    
    /**
     * Create an image view
     */
    public static long createImageView(long image, int format, int aspectMask, 
                                        int mipLevels, int arrayLayers, int viewType) {
        checkContext();
        
        try (MemoryStack stack = stackPush()) {
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(image)
                .viewType(viewType)
                .format(format);
            
            viewInfo.subresourceRange()
                .aspectMask(aspectMask)
                .baseMipLevel(0)
                .levelCount(mipLevels)
                .baseArrayLayer(0)
                .layerCount(arrayLayers);
            
            LongBuffer pImageView = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateImageView(ctx.device, viewInfo, null, pImageView);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create image view: " + translateVkResult(result));
            }
            
            long imageView = pImageView.get(0);
            allocatedImageViews.add(imageView);
            return imageView;
        }
    }
    
    /**
     * Destroy an image and its resources
     */
    public static void destroyImage(ImageAllocation alloc) {
        if (alloc == null) return;
        checkContext();
        
        if (alloc.imageView != VK_NULL_HANDLE) {
            vkDestroyImageView(ctx.device, alloc.imageView, null);
            allocatedImageViews.remove(alloc.imageView);
        }
        if (alloc.image != VK_NULL_HANDLE) {
            vkDestroyImage(ctx.device, alloc.image, null);
            allocatedImages.remove(alloc.image);
        }
        if (alloc.memory != VK_NULL_HANDLE) {
            vkFreeMemory(ctx.device, alloc.memory, null);
            allocatedMemory.remove(alloc.memory);
        }
    }
    
    // ========================================================================
    // IMAGE LAYOUT TRANSITIONS
    // ========================================================================
    
    /**
     * Transition image layout
     */
    public static void transitionImageLayout(ImageAllocation alloc, int newLayout,
                                              int srcStage, int dstStage,
                                              int srcAccess, int dstAccess) {
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        
        transitionImageLayoutCmd(cmdBuffer, alloc.image, alloc.format,
            alloc.currentLayout, newLayout, 0, alloc.mipLevels, 0, alloc.arrayLayers);
        
        ctx.endSingleTimeCommands(cmdBuffer);
        alloc.currentLayout = newLayout;
    }
    
    /**
     * Transition image layout in a command buffer
     */
    public static void transitionImageLayoutCmd(VkCommandBuffer cmdBuffer, long image, int format,
                                                 int oldLayout, int newLayout,
                                                 int baseMipLevel, int levelCount,
                                                 int baseArrayLayer, int layerCount) {
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image);
            
            int aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            if (isDepthFormat(format)) {
                aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
                if (hasStencilComponent(format)) {
                    aspectMask |= VK_IMAGE_ASPECT_STENCIL_BIT;
                }
            }
            
            barrier.get(0).subresourceRange()
                .aspectMask(aspectMask)
                .baseMipLevel(baseMipLevel)
                .levelCount(levelCount)
                .baseArrayLayer(baseArrayLayer)
                .layerCount(layerCount);
            
            int srcStage, dstStage, srcAccess, dstAccess;
            
            // Determine access masks and stages based on layouts
            if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                srcAccess = 0;
                dstAccess = VK_ACCESS_TRANSFER_WRITE_BIT;
                srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && 
                       newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                srcAccess = VK_ACCESS_TRANSFER_WRITE_BIT;
                dstAccess = VK_ACCESS_SHADER_READ_BIT;
                srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && 
                       newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                srcAccess = 0;
                dstAccess = VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | 
                           VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                dstStage = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && 
                       newLayout == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
                srcAccess = 0;
                dstAccess = VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
                srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                dstStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL && 
                       newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                srcAccess = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
                dstAccess = VK_ACCESS_SHADER_READ_BIT;
                srcStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL && 
                       newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                srcAccess = VK_ACCESS_SHADER_READ_BIT;
                dstAccess = VK_ACCESS_TRANSFER_WRITE_BIT;
                srcStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else {
                // Generic fallback
                srcAccess = VK_ACCESS_MEMORY_WRITE_BIT;
                dstAccess = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
                srcStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                dstStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
            }
            
            barrier.get(0)
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess);
            
            vkCmdPipelineBarrier(cmdBuffer, srcStage, dstStage, 0, null, null, barrier);
        }
    }
    
    // ========================================================================
    // SAMPLER CREATION
    // ========================================================================
    
    /**
     * Create a sampler with specified parameters
     */
    public static long createSampler(int magFilter, int minFilter, int mipmapMode,
                                      int addressModeU, int addressModeV, int addressModeW,
                                      float maxAnisotropy, boolean compareEnable, int compareOp,
                                      float minLod, float maxLod, float mipLodBias) {
        checkContext();
        
        try (MemoryStack stack = stackPush()) {
            boolean anisotropyEnabled = maxAnisotropy > 1.0f && ctx.supportsAnisotropy();
            
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(magFilter)
                .minFilter(minFilter)
                .mipmapMode(mipmapMode)
                .addressModeU(addressModeU)
                .addressModeV(addressModeV)
                .addressModeW(addressModeW)
                .anisotropyEnable(anisotropyEnabled)
                .maxAnisotropy(anisotropyEnabled ? Math.min(maxAnisotropy, ctx.getMaxAnisotropy()) : 1.0f)
                .compareEnable(compareEnable)
                .compareOp(compareOp)
                .minLod(minLod)
                .maxLod(maxLod)
                .mipLodBias(mipLodBias)
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                .unnormalizedCoordinates(false);
            
            LongBuffer pSampler = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateSampler(ctx.device, samplerInfo, null, pSampler);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create sampler: " + translateVkResult(result));
            }
            
            long sampler = pSampler.get(0);
            allocatedSamplers.add(sampler);
            return sampler;
        }
    }
    
    /**
     * Create a sampler from OpenGL texture parameters
     */
    public static long createSamplerFromGLParams(VulkanState.TextureObject texObj) {
        int magFilter = translateGLFilter(texObj.getParameteri(0x2800)); // GL_TEXTURE_MAG_FILTER
        int minFilter = translateGLFilter(texObj.getParameteri(0x2801)); // GL_TEXTURE_MIN_FILTER
        int mipmapMode = translateGLMipmapMode(texObj.getParameteri(0x2801));
        int addressModeU = translateGLWrap(texObj.getParameteri(0x2802)); // GL_TEXTURE_WRAP_S
        int addressModeV = translateGLWrap(texObj.getParameteri(0x2803)); // GL_TEXTURE_WRAP_T
        int addressModeW = translateGLWrap(texObj.getParameteri(0x8072)); // GL_TEXTURE_WRAP_R
        
        float maxAniso = texObj.getParameterf(0x84FE); // GL_TEXTURE_MAX_ANISOTROPY
        
        boolean compareEnable = texObj.getParameteri(0x884C) != 0; // GL_TEXTURE_COMPARE_MODE
        int compareOp = translateGLCompareFunc(texObj.getParameteri(0x884D)); // GL_TEXTURE_COMPARE_FUNC
        
        float minLod = texObj.getParameterf(0x813A); // GL_TEXTURE_MIN_LOD
        float maxLod = texObj.getParameterf(0x813B); // GL_TEXTURE_MAX_LOD
        float lodBias = texObj.getParameterf(0x8501); // GL_TEXTURE_LOD_BIAS
        
        return createSampler(magFilter, minFilter, mipmapMode,
            addressModeU, addressModeV, addressModeW,
            maxAniso, compareEnable, compareOp,
            minLod, maxLod, lodBias);
    }
    
    /**
     * Destroy a sampler
     */
    public static void destroySampler(long sampler) {
        if (sampler == VK_NULL_HANDLE) return;
        checkContext();
        
        vkDestroySampler(ctx.device, sampler, null);
        allocatedSamplers.remove(sampler);
    }
    
    // ========================================================================
    // DESCRIPTOR SET UPDATES
    // ========================================================================
    
    /**
     * Update a descriptor set with a uniform buffer
     */
    public static void updateDescriptorSetUniformBuffer(long descriptorSet, int binding,
                                                         long buffer, long offset, long range) {
        checkContext();
        
        try (MemoryStack stack = stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(buffer)
                .offset(offset)
                .range(range);
            
            VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(binding)
                .dstArrayElement(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .pBufferInfo(bufferInfo);
            
            vkUpdateDescriptorSets(ctx.device, descriptorWrite, null);
        }
    }
    
    /**
     * Update a descriptor set with a storage buffer
     */
    public static void updateDescriptorSetStorageBuffer(long descriptorSet, int binding,
                                                         long buffer, long offset, long range) {
        checkContext();
        
        try (MemoryStack stack = stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(buffer)
                .offset(offset)
                .range(range);
            
            VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(binding)
                .dstArrayElement(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .pBufferInfo(bufferInfo);
            
            vkUpdateDescriptorSets(ctx.device, descriptorWrite, null);
        }
    }
    
    /**
     * Update a descriptor set with a combined image sampler
     */
    public static void updateDescriptorSetTexture(long descriptorSet, int binding,
                                                   long imageView, long sampler, int imageLayout) {
        checkContext();
        
        try (MemoryStack stack = stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                .imageLayout(imageLayout)
                .imageView(imageView)
                .sampler(sampler);
            
            VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(binding)
                .dstArrayElement(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .pImageInfo(imageInfo);
            
            vkUpdateDescriptorSets(ctx.device, descriptorWrite, null);
        }
    }
    
    /**
     * Update multiple textures in a descriptor set
     */
    public static void updateDescriptorSetTextures(long descriptorSet, int baseBinding,
                                                    long[] imageViews, long[] samplers, int imageLayout) {
        checkContext();
        
        if (imageViews.length != samplers.length) {
            throw new IllegalArgumentException("imageViews and samplers must have same length");
        }
        
        try (MemoryStack stack = stackPush()) {
            VkWriteDescriptorSet.Buffer descriptorWrites = 
                VkWriteDescriptorSet.calloc(imageViews.length, stack);
            
            for (int i = 0; i < imageViews.length; i++) {
                VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(imageLayout)
                    .imageView(imageViews[i])
                    .sampler(samplers[i]);
                
                descriptorWrites.get(i)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(baseBinding + i)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(imageInfo);
            }
            
            vkUpdateDescriptorSets(ctx.device, descriptorWrites, null);
        }
    }
    
    // ========================================================================
    // FORMAT CONVERSION (GL -> VK)
    // ========================================================================
    
    /**
     * Convert OpenGL internal format to Vulkan format
     */
    public static int translateGLInternalFormat(int glFormat) {
        return switch (glFormat) {
            // Unsigned normalized
            case 0x8051 -> VK_FORMAT_R8G8B8_UNORM;         // GL_RGB8
            case 0x8058 -> VK_FORMAT_R8G8B8A8_UNORM;       // GL_RGBA8
            case 0x8C41 -> VK_FORMAT_R8G8B8_SRGB;          // GL_SRGB8
            case 0x8C43 -> VK_FORMAT_R8G8B8A8_SRGB;        // GL_SRGB8_ALPHA8
            case 0x8229 -> VK_FORMAT_R8_UNORM;             // GL_R8
            case 0x822B -> VK_FORMAT_R8G8_UNORM;           // GL_RG8
            case 0x8D62 -> VK_FORMAT_R16_UNORM;            // GL_R16
            case 0x8D64 -> VK_FORMAT_R16G16_UNORM;         // GL_RG16
            case 0x8D70 -> VK_FORMAT_R16G16B16_UNORM;      // GL_RGB16
            case 0x805B -> VK_FORMAT_R16G16B16A16_UNORM;   // GL_RGBA16
            
            // Signed normalized
            case 0x8F94 -> VK_FORMAT_R8_SNORM;             // GL_R8_SNORM
            case 0x8F95 -> VK_FORMAT_R8G8_SNORM;           // GL_RG8_SNORM
            case 0x8F96 -> VK_FORMAT_R8G8B8_SNORM;         // GL_RGB8_SNORM
            case 0x8F97 -> VK_FORMAT_R8G8B8A8_SNORM;       // GL_RGBA8_SNORM
            case 0x8F98 -> VK_FORMAT_R16_SNORM;            // GL_R16_SNORM
            case 0x8F99 -> VK_FORMAT_R16G16_SNORM;         // GL_RG16_SNORM
            case 0x8F9A -> VK_FORMAT_R16G16B16_SNORM;      // GL_RGB16_SNORM
            case 0x8F9B -> VK_FORMAT_R16G16B16A16_SNORM;   // GL_RGBA16_SNORM
            
            // Floating point
            case 0x822D -> VK_FORMAT_R16_SFLOAT;           // GL_R16F
            case 0x822F -> VK_FORMAT_R16G16_SFLOAT;        // GL_RG16F
            case 0x881B -> VK_FORMAT_R16G16B16_SFLOAT;     // GL_RGB16F
            case 0x881A -> VK_FORMAT_R16G16B16A16_SFLOAT;  // GL_RGBA16F
            case 0x822E -> VK_FORMAT_R32_SFLOAT;           // GL_R32F
            case 0x8230 -> VK_FORMAT_R32G32_SFLOAT;        // GL_RG32F
            case 0x8815 -> VK_FORMAT_R32G32B32_SFLOAT;     // GL_RGB32F
            case 0x8814 -> VK_FORMAT_R32G32B32A32_SFLOAT;  // GL_RGBA32F
            
            // Integer
            case 0x8231 -> VK_FORMAT_R8_SINT;              // GL_R8I
            case 0x8233 -> VK_FORMAT_R8G8_SINT;            // GL_RG8I
            case 0x8D8F -> VK_FORMAT_R8G8B8_SINT;          // GL_RGB8I
            case 0x8D8E -> VK_FORMAT_R8G8B8A8_SINT;        // GL_RGBA8I
            case 0x8232 -> VK_FORMAT_R8_UINT;              // GL_R8UI
            case 0x8234 -> VK_FORMAT_R8G8_UINT;            // GL_RG8UI
            case 0x8D90 -> VK_FORMAT_R8G8B8_UINT;          // GL_RGB8UI
            case 0x8D7C -> VK_FORMAT_R8G8B8A8_UINT;        // GL_RGBA8UI
            case 0x8235 -> VK_FORMAT_R16_SINT;             // GL_R16I
            case 0x8237 -> VK_FORMAT_R16G16_SINT;          // GL_RG16I
            case 0x8D89 -> VK_FORMAT_R16G16B16_SINT;       // GL_RGB16I
            case 0x8D88 -> VK_FORMAT_R16G16B16A16_SINT;    // GL_RGBA16I
            case 0x8236 -> VK_FORMAT_R16_UINT;             // GL_R16UI
            case 0x8238 -> VK_FORMAT_R16G16_UINT;          // GL_RG16UI
            case 0x8D7B -> VK_FORMAT_R16G16B16_UINT;       // GL_RGB16UI
            case 0x8D76 -> VK_FORMAT_R16G16B16A16_UINT;    // GL_RGBA16UI
            case 0x8235 -> VK_FORMAT_R32_SINT;             // GL_R32I
            case 0x823B -> VK_FORMAT_R32G32_SINT;          // GL_RG32I
            case 0x8D83 -> VK_FORMAT_R32G32B32_SINT;       // GL_RGB32I
            case 0x8D82 -> VK_FORMAT_R32G32B32A32_SINT;    // GL_RGBA32I
            case 0x8236 -> VK_FORMAT_R32_UINT;             // GL_R32UI
            case 0x823C -> VK_FORMAT_R32G32_UINT;          // GL_RG32UI
            case 0x8D71 -> VK_FORMAT_R32G32B32_UINT;       // GL_RGB32UI
            case 0x8D70 -> VK_FORMAT_R32G32B32A32_UINT;    // GL_RGBA32UI
            
            // Packed
            case 0x8C3A -> VK_FORMAT_B10G11R11_UFLOAT_PACK32; // GL_R11F_G11F_B10F
            case 0x8C3D -> VK_FORMAT_E5B9G9R9_UFLOAT_PACK32;  // GL_RGB9_E5
            case 0x8D9F -> VK_FORMAT_A2B10G10R10_SNORM_PACK32; // GL_RGB10_A2
            case 0x906F -> VK_FORMAT_A2B10G10R10_UINT_PACK32;  // GL_RGB10_A2UI
            
            // Depth/stencil
            case 0x81A5 -> VK_FORMAT_D16_UNORM;            // GL_DEPTH_COMPONENT16
            case 0x81A6 -> VK_FORMAT_D24_UNORM_S8_UINT;    // GL_DEPTH_COMPONENT24
            case 0x81A7 -> VK_FORMAT_D32_SFLOAT;           // GL_DEPTH_COMPONENT32
            case 0x8CAC -> VK_FORMAT_D32_SFLOAT;           // GL_DEPTH_COMPONENT32F
            case 0x88F0 -> VK_FORMAT_D24_UNORM_S8_UINT;    // GL_DEPTH24_STENCIL8
            case 0x8CAD -> VK_FORMAT_D32_SFLOAT_S8_UINT;   // GL_DEPTH32F_STENCIL8
            case 0x8D48 -> VK_FORMAT_S8_UINT;              // GL_STENCIL_INDEX8
            
            // Compressed
            case 0x83F0 -> VK_FORMAT_BC1_RGB_UNORM_BLOCK;  // GL_COMPRESSED_RGB_S3TC_DXT1_EXT
            case 0x83F1 -> VK_FORMAT_BC1_RGBA_UNORM_BLOCK; // GL_COMPRESSED_RGBA_S3TC_DXT1_EXT
            case 0x83F2 -> VK_FORMAT_BC2_UNORM_BLOCK;      // GL_COMPRESSED_RGBA_S3TC_DXT3_EXT
            case 0x83F3 -> VK_FORMAT_BC3_UNORM_BLOCK;      // GL_COMPRESSED_RGBA_S3TC_DXT5_EXT
            case 0x8C4C -> VK_FORMAT_BC1_RGB_SRGB_BLOCK;   // GL_COMPRESSED_SRGB_S3TC_DXT1_EXT
            case 0x8C4D -> VK_FORMAT_BC1_RGBA_SRGB_BLOCK;  // GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT1_EXT
            case 0x8C4E -> VK_FORMAT_BC2_SRGB_BLOCK;       // GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT3_EXT
            case 0x8C4F -> VK_FORMAT_BC3_SRGB_BLOCK;       // GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT
            case 0x8DBB -> VK_FORMAT_BC4_UNORM_BLOCK;      // GL_COMPRESSED_RED_RGTC1
            case 0x8DBC -> VK_FORMAT_BC4_SNORM_BLOCK;      // GL_COMPRESSED_SIGNED_RED_RGTC1
            case 0x8DBD -> VK_FORMAT_BC5_UNORM_BLOCK;      // GL_COMPRESSED_RG_RGTC2
            case 0x8DBE -> VK_FORMAT_BC5_SNORM_BLOCK;      // GL_COMPRESSED_SIGNED_RG_RGTC2
            case 0x8E8C -> VK_FORMAT_BC6H_UFLOAT_BLOCK;    // GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT
            case 0x8E8D -> VK_FORMAT_BC6H_SFLOAT_BLOCK;    // GL_COMPRESSED_RGB_BPTC_SIGNED_FLOAT
            case 0x8E8E -> VK_FORMAT_BC7_UNORM_BLOCK;      // GL_COMPRESSED_RGBA_BPTC_UNORM
            case 0x8E8F -> VK_FORMAT_BC7_SRGB_BLOCK;       // GL_COMPRESSED_SRGB_ALPHA_BPTC_UNORM
            
            // Legacy/simple formats
            case 0x1907 -> VK_FORMAT_R8G8B8_UNORM;         // GL_RGB
            case 0x1908 -> VK_FORMAT_R8G8B8A8_UNORM;       // GL_RGBA
            case 0x80E1 -> VK_FORMAT_B8G8R8A8_UNORM;       // GL_BGRA
            case 0x1903 -> VK_FORMAT_R8_UNORM;             // GL_RED
            case 0x8227 -> VK_FORMAT_R8G8_UNORM;           // GL_RG
            
            default -> VK_FORMAT_R8G8B8A8_UNORM;
        };
    }
    
    /**
     * Translate GL filter to VK filter
     */
    public static int translateGLFilter(int glFilter) {
        return switch (glFilter) {
            case 0x2600 -> VK_FILTER_NEAREST;  // GL_NEAREST
            case 0x2601 -> VK_FILTER_LINEAR;   // GL_LINEAR
            case 0x2700 -> VK_FILTER_NEAREST;  // GL_NEAREST_MIPMAP_NEAREST
            case 0x2701 -> VK_FILTER_LINEAR;   // GL_LINEAR_MIPMAP_NEAREST
            case 0x2702 -> VK_FILTER_NEAREST;  // GL_NEAREST_MIPMAP_LINEAR
            case 0x2703 -> VK_FILTER_LINEAR;   // GL_LINEAR_MIPMAP_LINEAR
            default -> VK_FILTER_LINEAR;
        };
    }
    
    /**
     * Translate GL filter to VK mipmap mode
     */
    public static int translateGLMipmapMode(int glFilter) {
        return switch (glFilter) {
            case 0x2700, 0x2701 -> VK_SAMPLER_MIPMAP_MODE_NEAREST; // MIPMAP_NEAREST variants
            case 0x2702, 0x2703 -> VK_SAMPLER_MIPMAP_MODE_LINEAR;  // MIPMAP_LINEAR variants
            default -> VK_SAMPLER_MIPMAP_MODE_LINEAR;
        };
    }
    
    /**
     * Translate GL wrap mode to VK address mode
     */
    public static int translateGLWrap(int glWrap) {
        return switch (glWrap) {
            case 0x2901 -> VK_SAMPLER_ADDRESS_MODE_REPEAT;           // GL_REPEAT
            case 0x812F -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;    // GL_CLAMP_TO_EDGE
            case 0x812D -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER;  // GL_CLAMP_TO_BORDER
            case 0x8370 -> VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;  // GL_MIRRORED_REPEAT
            case 0x8742 -> VK_SAMPLER_ADDRESS_MODE_MIRROR_CLAMP_TO_EDGE; // GL_MIRROR_CLAMP_TO_EDGE
            default -> VK_SAMPLER_ADDRESS_MODE_REPEAT;
        };
    }
    
    /**
     * Translate GL compare function to VK compare op
     */
    public static int translateGLCompareFunc(int glFunc) {
        return switch (glFunc) {
            case 0x0200 -> VK_COMPARE_OP_NEVER;          // GL_NEVER
            case 0x0201 -> VK_COMPARE_OP_LESS;           // GL_LESS
            case 0x0202 -> VK_COMPARE_OP_EQUAL;          // GL_EQUAL
            case 0x0203 -> VK_COMPARE_OP_LESS_OR_EQUAL;  // GL_LEQUAL
            case 0x0204 -> VK_COMPARE_OP_GREATER;        // GL_GREATER
            case 0x0205 -> VK_COMPARE_OP_NOT_EQUAL;      // GL_NOTEQUAL
            case 0x0206 -> VK_COMPARE_OP_GREATER_OR_EQUAL; // GL_GEQUAL
            case 0x0207 -> VK_COMPARE_OP_ALWAYS;         // GL_ALWAYS
            default -> VK_COMPARE_OP_LESS;
        };
    }
    
    // ========================================================================
    // SHADER PREPROCESSING
    // ========================================================================
    
    /**
     * Preprocess GLSL shader source for Vulkan compatibility
     */
    public static String preprocessGLSLForVulkan(String source, int shaderType) {
        StringBuilder result = new StringBuilder();
        
        // Determine shader stage for qualifiers
        String stage = switch (shaderType) {
            case 0x8B31 -> "vertex";
            case 0x8B30 -> "fragment";
            case 0x8DD9 -> "geometry";
            case 0x8E88 -> "tesscontrol";
            case 0x8E87 -> "tesseval";
            case 0x91B9 -> "compute";
            default -> "vertex";
        };
        
        // Add Vulkan-specific header
        result.append("#version 450\n");
        result.append("#extension GL_ARB_separate_shader_objects : enable\n");
        result.append("#extension GL_ARB_shading_language_420pack : enable\n");
        result.append("\n");
        
        String[] lines = source.split("\n");
        int locationCounter = 0;
        int bindingCounter = 0;
        int setCounter = 0;
        
        Map<String, Integer> uniformLocations = new HashMap<>();
        Map<String, Integer> uniformBindings = new HashMap<>();
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            // Skip existing version directive
            if (trimmed.startsWith("#version")) {
                continue;
            }
            
            // Skip incompatible extensions
            if (trimmed.startsWith("#extension") && 
                (trimmed.contains("GL_ARB_explicit_attrib_location") ||
                 trimmed.contains("GL_ARB_explicit_uniform_location"))) {
                continue;
            }
            
            // Handle in/out without layout qualifiers
            if ((trimmed.startsWith("in ") || trimmed.startsWith("out ")) &&
                !trimmed.contains("layout")) {
                result.append("layout(location = ").append(locationCounter++).append(") ").append(line).append("\n");
                continue;
            }
            
            // Handle uniform blocks
            if (trimmed.startsWith("uniform ") && trimmed.contains("{")) {
                // This is a uniform block
                result.append("layout(std140, set = 0, binding = ").append(bindingCounter++).append(") ").append(line).append("\n");
                continue;
            }
            
            // Handle standalone uniforms (convert to push constants or UBO)
            if (trimmed.startsWith("uniform ") && !trimmed.contains("{")) {
                // For simplicity, leave as-is but add layout
                // In real implementation, you'd collect these into a UBO
                result.append("layout(binding = ").append(bindingCounter++).append(") ").append(line).append("\n");
                continue;
            }
            
            // Handle sampler uniforms
            if (trimmed.contains("sampler") && trimmed.startsWith("uniform")) {
                result.append("layout(set = 0, binding = ").append(bindingCounter++).append(") ").append(line).append("\n");
                continue;
            }
            
            // Replace gl_FragColor with custom output
            if (trimmed.contains("gl_FragColor")) {
                line = line.replace("gl_FragColor", "outColor");
                // Add output declaration if needed
                if (!source.contains("out vec4 outColor")) {
                    result.insert(result.indexOf("\n") + 1, "layout(location = 0) out vec4 outColor;\n");
                }
            }
            
            // Replace gl_FragData[n] with custom outputs
            Pattern fragDataPattern = Pattern.compile("gl_FragData\\[(\\d+)\\]");
            Matcher matcher = fragDataPattern.matcher(line);
            while (matcher.find()) {
                int index = Integer.parseInt(matcher.group(1));
                line = line.replace(matcher.group(), "fragData" + index);
            }
            
            // Replace texture2D/texture3D/textureCube with texture()
            line = line.replace("texture2D(", "texture(");
            line = line.replace("texture3D(", "texture(");
            line = line.replace("textureCube(", "texture(");
            line = line.replace("texture2DLod(", "textureLod(");
            line = line.replace("shadow2D(", "texture(");
            
            result.append(line).append("\n");
        }
        
        return result.toString();
    }
    
    // ========================================================================
    // FRAMEBUFFER OBJECTS (FBO) EMULATION
    // ========================================================================
    
    public static class FramebufferObject {
        public long framebuffer;
        public long renderPass;
        public List<ImageAllocation> colorAttachments = new ArrayList<>();
        public ImageAllocation depthStencilAttachment;
        public int width;
        public int height;
        public boolean complete;
    }
    
    /**
     * Create a framebuffer with color and optional depth attachments
     */
    public static FramebufferObject createFramebuffer(int width, int height, 
                                                       int[] colorFormats, 
                                                       boolean hasDepth, int depthFormat) {
        checkContext();
        
        FramebufferObject fbo = new FramebufferObject();
        fbo.width = width;
        fbo.height = height;
        
        List<Long> attachmentViews = new ArrayList<>();
        
        // Create color attachments
        for (int format : colorFormats) {
            int vkFormat = translateGLInternalFormat(format);
            ImageAllocation colorImg = createImage2D(width, height, vkFormat,
                VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                1, VK_SAMPLE_COUNT_1_BIT);
            
            // Transition to color attachment
            transitionImageLayout(colorImg, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
            
            fbo.colorAttachments.add(colorImg);
            attachmentViews.add(colorImg.imageView);
        }
        
        // Create depth attachment
        if (hasDepth) {
            int vkDepthFormat = depthFormat != 0 ? translateGLInternalFormat(depthFormat) : VK_FORMAT_D24_UNORM_S8_UINT;
            ImageAllocation depthImg = createImage2D(width, height, vkDepthFormat,
                VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                1, VK_SAMPLE_COUNT_1_BIT);
            
            transitionImageLayout(depthImg, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
                0, VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            
            fbo.depthStencilAttachment = depthImg;
            attachmentViews.add(depthImg.imageView);
        }
        
        // Create render pass
        fbo.renderPass = createRenderPassForFBO(fbo);
        
        // Create framebuffer
        try (MemoryStack stack = stackPush()) {
            LongBuffer pAttachments = stack.mallocLong(attachmentViews.size());
            for (int i = 0; i < attachmentViews.size(); i++) {
                pAttachments.put(i, attachmentViews.get(i));
            }
            
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                .renderPass(fbo.renderPass)
                .pAttachments(pAttachments)
                .width(width)
                .height(height)
                .layers(1);
            
            LongBuffer pFramebuffer = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateFramebuffer(ctx.device, framebufferInfo, null, pFramebuffer);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create framebuffer: " + translateVkResult(result));
            }
            
            fbo.framebuffer = pFramebuffer.get(0);
        }
        
        fbo.complete = true;
        return fbo;
    }
    
    private static long createRenderPassForFBO(FramebufferObject fbo) {
        try (MemoryStack stack = stackPush()) {
            int attachmentCount = fbo.colorAttachments.size() + (fbo.depthStencilAttachment != null ? 1 : 0);
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(attachmentCount, stack);
            
            int attachmentIdx = 0;
            
            // Color attachments
            for (ImageAllocation colorImg : fbo.colorAttachments) {
                attachments.get(attachmentIdx++)
                    .format(colorImg.format)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            }
            
            // Depth attachment
            if (fbo.depthStencilAttachment != null) {
                attachments.get(attachmentIdx++)
                    .format(fbo.depthStencilAttachment.format)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }
            
            // Subpass
            VkAttachmentReference.Buffer colorRefs = VkAttachmentReference.calloc(fbo.colorAttachments.size(), stack);
            for (int i = 0; i < fbo.colorAttachments.size(); i++) {
                colorRefs.get(i)
                    .attachment(i)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            }
            
            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(fbo.colorAttachments.size())
                .pColorAttachments(colorRefs);
            
            if (fbo.depthStencilAttachment != null) {
                VkAttachmentReference depthRef = VkAttachmentReference.calloc(stack)
                    .attachment(fbo.colorAttachments.size())
                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
                subpass.pDepthStencilAttachment(depthRef);
            }
            
            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachments)
                .pSubpasses(subpass);
            
            LongBuffer pRenderPass = stack.longs(VK_NULL_HANDLE);
            int result = vkCreateRenderPass(ctx.device, renderPassInfo, null, pRenderPass);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create FBO render pass: " + translateVkResult(result));
            }
            
            return pRenderPass.get(0);
        }
    }
    
    /**
     * Destroy a framebuffer object
     */
    public static void destroyFramebuffer(FramebufferObject fbo) {
        if (fbo == null) return;
        checkContext();
        
        if (fbo.framebuffer != VK_NULL_HANDLE) {
            vkDestroyFramebuffer(ctx.device, fbo.framebuffer, null);
        }
        if (fbo.renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(ctx.device, fbo.renderPass, null);
        }
        
        for (ImageAllocation colorImg : fbo.colorAttachments) {
            destroyImage(colorImg);
        }
        
        if (fbo.depthStencilAttachment != null) {
            destroyImage(fbo.depthStencilAttachment);
        }
    }
    
    // ========================================================================
    // UTILITY METHODS
    // ========================================================================
    
    private static void checkContext() {
        if (ctx == null || ctx.device == null) {
            throw new IllegalStateException("VulkanHelper not initialized or context is null");
        }
    }
    
    public static int calculateMipLevels(int width, int height) {
        return (int) Math.floor(Math.log(Math.max(width, height)) / Math.log(2)) + 1;
    }
    
    public static boolean isDepthFormat(int format) {
        return format == VK_FORMAT_D16_UNORM ||
               format == VK_FORMAT_D16_UNORM_S8_UINT ||
               format == VK_FORMAT_D24_UNORM_S8_UINT ||
               format == VK_FORMAT_D32_SFLOAT ||
               format == VK_FORMAT_D32_SFLOAT_S8_UINT ||
               format == VK_FORMAT_X8_D24_UNORM_PACK32;
    }
    
    public static boolean hasStencilComponent(int format) {
        return format == VK_FORMAT_D16_UNORM_S8_UINT ||
               format == VK_FORMAT_D24_UNORM_S8_UINT ||
               format == VK_FORMAT_D32_SFLOAT_S8_UINT ||
               format == VK_FORMAT_S8_UINT;
    }
    
    public static int getBytesPerPixel(int format) {
        return switch (format) {
            case VK_FORMAT_R8_UNORM, VK_FORMAT_R8_SNORM, VK_FORMAT_R8_UINT, VK_FORMAT_R8_SINT -> 1;
            case VK_FORMAT_R8G8_UNORM, VK_FORMAT_R8G8_SNORM, VK_FORMAT_R16_UNORM, VK_FORMAT_R16_SFLOAT -> 2;
            case VK_FORMAT_R8G8B8_UNORM, VK_FORMAT_R8G8B8_SNORM, VK_FORMAT_R8G8B8_SRGB -> 3;
            case VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R8G8B8A8_SNORM, VK_FORMAT_R8G8B8A8_SRGB,
                 VK_FORMAT_B8G8R8A8_UNORM, VK_FORMAT_B8G8R8A8_SRGB,
                 VK_FORMAT_R16G16_UNORM, VK_FORMAT_R16G16_SFLOAT, VK_FORMAT_R32_SFLOAT -> 4;
            case VK_FORMAT_R16G16B16_UNORM, VK_FORMAT_R16G16B16_SFLOAT -> 6;
            case VK_FORMAT_R16G16B16A16_UNORM, VK_FORMAT_R16G16B16A16_SFLOAT, 
                 VK_FORMAT_R32G32_SFLOAT -> 8;
            case VK_FORMAT_R32G32B32_SFLOAT -> 12;
            case VK_FORMAT_R32G32B32A32_SFLOAT -> 16;
            default -> 4;
        };
    }
    
    /**
     * Translate VkResult to human-readable string
     */
    public static String translateVkResult(int result) {
        return switch (result) {
            case VK_SUCCESS -> "VK_SUCCESS";
            case VK_NOT_READY -> "VK_NOT_READY";
            case VK_TIMEOUT -> "VK_TIMEOUT";
            case VK_EVENT_SET -> "VK_EVENT_SET";
            case VK_EVENT_RESET -> "VK_EVENT_RESET";
            case VK_INCOMPLETE -> "VK_INCOMPLETE";
            case VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY";
            case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
            case VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
            case VK_ERROR_DEVICE_LOST -> "VK_ERROR_DEVICE_LOST";
            case VK_ERROR_MEMORY_MAP_FAILED -> "VK_ERROR_MEMORY_MAP_FAILED";
            case VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT";
            case VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
            case VK_ERROR_FEATURE_NOT_PRESENT -> "VK_ERROR_FEATURE_NOT_PRESENT";
            case VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER";
            case VK_ERROR_TOO_MANY_OBJECTS -> "VK_ERROR_TOO_MANY_OBJECTS";
            case VK_ERROR_FORMAT_NOT_SUPPORTED -> "VK_ERROR_FORMAT_NOT_SUPPORTED";
            case VK_ERROR_FRAGMENTED_POOL -> "VK_ERROR_FRAGMENTED_POOL";
            case VK_ERROR_UNKNOWN -> "VK_ERROR_UNKNOWN";
            case -1000069000 -> "VK_ERROR_OUT_OF_POOL_MEMORY";
            case -1000072003 -> "VK_ERROR_INVALID_EXTERNAL_HANDLE";
            case -1000161000 -> "VK_ERROR_FRAGMENTATION";
            case -1000174001 -> "VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS";
            case -1000001004 -> "VK_ERROR_SURFACE_LOST_KHR";
            case -1000001003 -> "VK_ERROR_NATIVE_WINDOW_IN_USE_KHR";
            case -1000001004 -> "VK_SUBOPTIMAL_KHR";
            case -1000001004 -> "VK_ERROR_OUT_OF_DATE_KHR";
            default -> "VK_UNKNOWN_ERROR (" + result + ")";
        };
    }
    
    /**
     * Check if a Vulkan call succeeded, throw exception if not
     */
    public static void checkVkResult(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new RuntimeException(operation + " failed: " + translateVkResult(result));
        }
    }
    
    // ========================================================================
    // DEBUG UTILITIES
    // ========================================================================
    
    public static String getResourceReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VulkanHelper Resource Report ===\n");
        sb.append("Tracked Resources:\n");
        sb.append("  Buffers: ").append(allocatedBuffers.size()).append("\n");
        sb.append("  Images: ").append(allocatedImages.size()).append("\n");
        sb.append("  Image Views: ").append(allocatedImageViews.size()).append("\n");
        sb.append("  Samplers: ").append(allocatedSamplers.size()).append("\n");
        sb.append("  Memory Allocations: ").append(allocatedMemory.size()).append("\n");
        sb.append("  Descriptor Sets: ").append(allocatedDescriptorSets.size()).append("\n");
        sb.append("\nStaging Buffers:\n");
        sb.append("  Pool Size: ").append(stagingBufferPool.size()).append("\n");
        sb.append("  Active: ").append(activeStagingBuffers.size()).append("\n");
        return sb.toString();
    }
}
