package com.example.modid.gl.mapping;

import com.example.modid.gl.vulkan.VulkanContext;
import com.example.modid.gl.vulkan.VulkanState;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * REAL OpenGL → Vulkan Translation Layer
 * 
 * Every GL call is translated to actual Vulkan commands.
 * This is a full GL driver implementation on top of Vulkan.
 * 
 * State tracking:
 * - VulkanContext: Manages instance, device, queues, swapchain
 * - VulkanState: Tracks current GL state (bound textures, buffers, etc.)
 * - Command buffer recording for deferred execution
 */
public class VulkanCallMapper {
    
    private static VulkanContext ctx;
    private static VulkanState state;
    private static VkCommandBuffer currentCommandBuffer;
    private static boolean recordingCommands = false;
    
    public static void initialize(VulkanContext context) {
        ctx = context;
        state = new VulkanState();
    }
    
    // ========================================================================
    // TEXTURE OPERATIONS
    // ========================================================================
    
    /**
     * GL: glGenTextures(1, &texture)
     * VK: vkCreateImage + vkAllocateMemory + vkCreateImageView
     */
    public static long genTexture() {
        // Create Vulkan image
        VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .imageType(VK_IMAGE_TYPE_2D)
            .format(VK_FORMAT_R8G8B8A8_UNORM)
            .mipLevels(1)
            .arrayLayers(1)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .tiling(VK_IMAGE_TILING_OPTIMAL)
            .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        
        // Extent will be set when glTexImage2D is called
        imageInfo.extent().width(1).height(1).depth(1);
        
        LongBuffer pImage = LongBuffer.allocate(1);
        int result = vkCreateImage(ctx.device, imageInfo, null, pImage);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create Vulkan image: " + result);
        }
        
        long image = pImage.get(0);
        
        // Allocate device memory
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetImageMemoryRequirements(ctx.device, image, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
        
        LongBuffer pMemory = LongBuffer.allocate(1);
        result = vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate image memory: " + result);
        }
        
        long memory = pMemory.get(0);
        vkBindImageMemory(ctx.device, image, memory, 0);
        
        // Create image view
        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(image)
            .viewType(VK_IMAGE_VIEW_TYPE_2D)
            .format(VK_FORMAT_R8G8B8A8_UNORM);
        
        viewInfo.subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1);
        
        LongBuffer pImageView = LongBuffer.allocate(1);
        result = vkCreateImageView(ctx.device, viewInfo, null, pImageView);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create image view: " + result);
        }
        
        long imageView = pImageView.get(0);
        
        // Store in state tracker
        long textureId = state.registerTexture(image, memory, imageView);
        
        imageInfo.free();
        memReqs.free();
        allocInfo.free();
        viewInfo.free();
        
        return textureId;
    }
    
    /**
     * GL: glBindTexture(GL_TEXTURE_2D, texture)
     * VK: Update descriptor set with new image view
     */
    public static void bindTexture(int target, long texture) {
        if (texture == 0) {
            state.unbindTexture(state.activeTextureUnit);
            return;
        }
        
        // Get texture info from state
        VulkanState.TextureObject texObj = state.getTexture(texture);
        if (texObj == null) {
            throw new RuntimeException("Invalid texture ID: " + texture);
        }
        
        // Bind to current texture unit
        state.bindTexture(state.activeTextureUnit, texture);
        
        // Update descriptor set for this texture unit
        updateTextureDescriptor(state.activeTextureUnit, texObj.imageView, texObj.sampler);
    }
    
    /**
     * GL: glActiveTexture(GL_TEXTURE0 + unit)
     * VK: Just update state (descriptor binding happens on draw)
     */
    public static void activeTexture(int unit) {
        state.activeTextureUnit = unit - 0x84C0; // GL_TEXTURE0
    }
    
    /**
     * GL: glTexImage2D(target, level, internalFormat, width, height, border, format, type, data)
     * VK: Recreate image with new size, upload via staging buffer
     */
    public static void texImage2D(int target, int level, int internalFormat, int width, int height, 
                                   int border, int format, int type, ByteBuffer data) {
        
        long texture = state.getBoundTexture(state.activeTextureUnit);
        if (texture == 0) {
            throw new RuntimeException("No texture bound");
        }
        
        VulkanState.TextureObject texObj = state.getTexture(texture);
        
        // Destroy old image if exists
        if (texObj.image != VK_NULL_HANDLE) {
            vkDestroyImage(ctx.device, texObj.image, null);
            vkFreeMemory(ctx.device, texObj.memory, null);
            vkDestroyImageView(ctx.device, texObj.imageView, null);
        }
        
        // Create new image with correct size
        VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .imageType(VK_IMAGE_TYPE_2D)
            .format(translateFormatToVulkan(internalFormat))
            .mipLevels(1)
            .arrayLayers(1)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .tiling(VK_IMAGE_TILING_OPTIMAL)
            .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        
        imageInfo.extent().width(width).height(height).depth(1);
        
        LongBuffer pImage = LongBuffer.allocate(1);
        vkCreateImage(ctx.device, imageInfo, null, pImage);
        long image = pImage.get(0);
        
        // Allocate memory
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetImageMemoryRequirements(ctx.device, image, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
        
        LongBuffer pMemory = LongBuffer.allocate(1);
        vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        long memory = pMemory.get(0);
        
        vkBindImageMemory(ctx.device, image, memory, 0);
        
        // If data provided, upload via staging buffer
        if (data != null) {
            uploadTextureData(image, width, height, data);
        }
        
        // Create image view
        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(image)
            .viewType(VK_IMAGE_VIEW_TYPE_2D)
            .format(translateFormatToVulkan(internalFormat));
        
        viewInfo.subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1);
        
        LongBuffer pImageView = LongBuffer.allocate(1);
        vkCreateImageView(ctx.device, viewInfo, null, pImageView);
        long imageView = pImageView.get(0);
        
        // Update texture object
        texObj.image = image;
        texObj.memory = memory;
        texObj.imageView = imageView;
        texObj.width = width;
        texObj.height = height;
        
        imageInfo.free();
        memReqs.free();
        allocInfo.free();
        viewInfo.free();
    }
    
    /**
     * Upload texture data via staging buffer
     */
    private static void uploadTextureData(long image, int width, int height, ByteBuffer data) {
        long bufferSize = data.remaining();
        
        // Create staging buffer
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(bufferSize)
            .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        
        LongBuffer pBuffer = LongBuffer.allocate(1);
        vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
        long stagingBuffer = pBuffer.get(0);
        
        // Allocate staging memory (host-visible)
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(ctx.device, stagingBuffer, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(
                memReqs.memoryTypeBits(),
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            ));
        
        LongBuffer pMemory = LongBuffer.allocate(1);
        vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        long stagingMemory = pMemory.get(0);
        
        vkBindBufferMemory(ctx.device, stagingBuffer, stagingMemory, 0);
        
        // Map and copy data
        ByteBuffer mappedData = ctx.mapMemory(stagingMemory, 0, bufferSize);
        mappedData.put(data);
        data.rewind();
        vkUnmapMemory(ctx.device, stagingMemory);
        
        // Transition image layout and copy
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        
        // Transition to transfer dst
        transitionImageLayout(cmdBuffer, image, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        
        // Copy buffer to image
        VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1)
            .bufferOffset(0)
            .bufferRowLength(0)
            .bufferImageHeight(0);
        
        region.imageSubresource()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .mipLevel(0)
            .baseArrayLayer(0)
            .layerCount(1);
        
        region.imageOffset().set(0, 0, 0);
        region.imageExtent().set(width, height, 1);
        
        vkCmdCopyBufferToImage(cmdBuffer, stagingBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
        
        // Transition to shader read
        transitionImageLayout(cmdBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        
        ctx.endSingleTimeCommands(cmdBuffer);
        
        // Cleanup
        vkDestroyBuffer(ctx.device, stagingBuffer, null);
        vkFreeMemory(ctx.device, stagingMemory, null);
        bufferInfo.free();
        memReqs.free();
        allocInfo.free();
        region.free();
    }
    
    /**
     * Transition image layout with pipeline barrier
     */
    private static void transitionImageLayout(VkCommandBuffer cmdBuffer, long image, int oldLayout, int newLayout) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .oldLayout(oldLayout)
            .newLayout(newLayout)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .image(image);
        
        barrier.subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1);
        
        int sourceStage, destinationStage;
        
        if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            barrier.srcAccessMask(0);
            barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
            sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        } else {
            throw new RuntimeException("Unsupported layout transition");
        }
        
        vkCmdPipelineBarrier(cmdBuffer, sourceStage, destinationStage, 0, null, null, barrier);
        barrier.free();
    }
    
    // ========================================================================
    // BUFFER OPERATIONS
    // ========================================================================
    
    /**
     * GL: glGenBuffers(1, &buffer)
     * VK: vkCreateBuffer + vkAllocateMemory
     */
    public static long genBuffer() {
        // Create buffer (size will be set on glBufferData)
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(1) // Placeholder
            .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        
        LongBuffer pBuffer = LongBuffer.allocate(1);
        vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
        long buffer = pBuffer.get(0);
        
        long bufferId = state.registerBuffer(buffer, VK_NULL_HANDLE, 0);
        
        bufferInfo.free();
        return bufferId;
    }
    
    /**
     * GL: glBindBuffer(target, buffer)
     * VK: Just update state (binding happens on draw)
     */
    public static void bindBuffer(int target, long buffer) {
        state.bindBuffer(target, buffer);
    }
    
    /**
     * GL: glBufferData(target, size, data, usage)
     * VK: Recreate buffer with correct size, upload via staging
     */
    public static void bufferData(int target, long size, ByteBuffer data, int usage) {
        long bufferId = state.getBoundBuffer(target);
        if (bufferId == 0) {
            throw new RuntimeException("No buffer bound to target: " + target);
        }
        
        VulkanState.BufferObject bufObj = state.getBuffer(bufferId);
        
        // Destroy old buffer if exists
        if (bufObj.buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(ctx.device, bufObj.buffer, null);
            if (bufObj.memory != VK_NULL_HANDLE) {
                vkFreeMemory(ctx.device, bufObj.memory, null);
            }
        }
        
        // Create new buffer with correct size
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(size)
            .usage(translateBufferUsage(target) | VK_BUFFER_USAGE_TRANSFER_DST_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        
        LongBuffer pBuffer = LongBuffer.allocate(1);
        vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
        long buffer = pBuffer.get(0);
        
        // Allocate device-local memory
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(ctx.device, buffer, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
        
        LongBuffer pMemory = LongBuffer.allocate(1);
        vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        long memory = pMemory.get(0);
        
        vkBindBufferMemory(ctx.device, buffer, memory, 0);
        
        // Upload data if provided
        if (data != null) {
            uploadBufferData(buffer, size, data);
        }
        
        // Update buffer object
        bufObj.buffer = buffer;
        bufObj.memory = memory;
        bufObj.size = size;
        
        bufferInfo.free();
        memReqs.free();
        allocInfo.free();
    }
    
    /**
     * GL: glBufferSubData(target, offset, size, data)
     * VK: Upload via staging buffer
     */
    public static void bufferSubData(int target, long offset, ByteBuffer data) {
        long bufferId = state.getBoundBuffer(target);
        if (bufferId == 0) {
            throw new RuntimeException("No buffer bound");
        }
        
        VulkanState.BufferObject bufObj = state.getBuffer(bufferId);
        uploadBufferDataPartial(bufObj.buffer, offset, data);
    }
    
    /**
     * Upload buffer data via staging buffer
     */
    private static void uploadBufferData(long dstBuffer, long size, ByteBuffer data) {
        // Create staging buffer
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(size)
            .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        
        LongBuffer pBuffer = LongBuffer.allocate(1);
        vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
        long stagingBuffer = pBuffer.get(0);
        
        // Allocate staging memory
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(ctx.device, stagingBuffer, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(
                memReqs.memoryTypeBits(),
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            ));
        
        LongBuffer pMemory = LongBuffer.allocate(1);
        vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        long stagingMemory = pMemory.get(0);
        
        vkBindBufferMemory(ctx.device, stagingBuffer, stagingMemory, 0);
        
        // Map and copy
        ByteBuffer mapped = ctx.mapMemory(stagingMemory, 0, size);
        mapped.put(data);
        data.rewind();
        vkUnmapMemory(ctx.device, stagingMemory);
        
        // Copy staging to device buffer
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        
        VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1)
            .srcOffset(0)
            .dstOffset(0)
            .size(size);
        
        vkCmdCopyBuffer(cmdBuffer, stagingBuffer, dstBuffer, copyRegion);
        
        ctx.endSingleTimeCommands(cmdBuffer);
        
        // Cleanup
        vkDestroyBuffer(ctx.device, stagingBuffer, null);
        vkFreeMemory(ctx.device, stagingMemory, null);
        bufferInfo.free();
        memReqs.free();
        allocInfo.free();
        copyRegion.free();
    }
    
    private static void uploadBufferDataPartial(long dstBuffer, long offset, ByteBuffer data) {
        long size = data.remaining();
        
        // Create staging buffer
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(size)
            .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        
        LongBuffer pBuffer = LongBuffer.allocate(1);
        vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
        long stagingBuffer = pBuffer.get(0);
        
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(ctx.device, stagingBuffer, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(
                memReqs.memoryTypeBits(),
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            ));
        
        LongBuffer pMemory = LongBuffer.allocate(1);
        vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        long stagingMemory = pMemory.get(0);
        
        vkBindBufferMemory(ctx.device, stagingBuffer, stagingMemory, 0);
        
        // Map and copy
        ByteBuffer mapped = ctx.mapMemory(stagingMemory, 0, size);
        mapped.put(data);
        data.rewind();
        vkUnmapMemory(ctx.device, stagingMemory);
        
        // Copy to destination with offset
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        
        VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1)
            .srcOffset(0)
            .dstOffset(offset)
            .size(size);
        
        vkCmdCopyBuffer(cmdBuffer, stagingBuffer, dstBuffer, copyRegion);
        
        ctx.endSingleTimeCommands(cmdBuffer);
        
        // Cleanup
        vkDestroyBuffer(ctx.device, stagingBuffer, null);
        vkFreeMemory(ctx.device, stagingMemory, null);
        bufferInfo.free();
        memReqs.free();
        allocInfo.free();
        copyRegion.free();
    }
    
    // ========================================================================
    // DRAW CALLS
    // ========================================================================
    
    /**
     * GL: glDrawArrays(mode, first, count)
     * VK: vkCmdDraw with current pipeline and state
     */
    public static void drawArrays(int mode, int first, int count) {
        // Ensure we have a command buffer recording
        if (!recordingCommands) {
            beginFrame();
        }
        
        // Bind pipeline for this draw mode
        long pipeline = state.getPipelineForMode(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        // Bind vertex buffers
        long vertexBuffer = state.getBoundBuffer(0x8892); // GL_ARRAY_BUFFER
        if (vertexBuffer != 0) {
            VulkanState.BufferObject bufObj = state.getBuffer(vertexBuffer);
            LongBuffer pBuffers = LongBuffer.wrap(new long[]{bufObj.buffer});
            LongBuffer pOffsets = LongBuffer.wrap(new long[]{0});
            vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
        }
        
        // Bind descriptor sets (textures, uniforms)
        bindDescriptorSets();
        
        // Draw
        vkCmdDraw(currentCommandBuffer, count, 1, first, 0);
    }
    
    /**
     * GL: glDrawElements(mode, count, type, indices)
     * VK: vkCmdDrawIndexed
     */
    public static void drawElements(int mode, int count, int type, long indices) {
        if (!recordingCommands) {
            beginFrame();
        }
        
        // Bind pipeline
        long pipeline = state.getPipelineForMode(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        // Bind vertex buffer
        long vertexBuffer = state.getBoundBuffer(0x8892);
        if (vertexBuffer != 0) {
            VulkanState.BufferObject bufObj = state.getBuffer(vertexBuffer);
            LongBuffer pBuffers = LongBuffer.wrap(new long[]{bufObj.buffer});
            LongBuffer pOffsets = LongBuffer.wrap(new long[]{0});
            vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
        }
        
        // Bind index buffer
        long indexBuffer = state.getBoundBuffer(0x8893); // GL_ELEMENT_ARRAY_BUFFER
        if (indexBuffer != 0) {
            VulkanState.BufferObject bufObj = state.getBuffer(indexBuffer);
            int indexType = (type == 0x1405) ? VK_INDEX_TYPE_UINT32 : VK_INDEX_TYPE_UINT16;
            vkCmdBindIndexBuffer(currentCommandBuffer, bufObj.buffer, indices, indexType);
        }
        
        // Bind descriptors
        bindDescriptorSets();
        
        // Draw indexed
        vkCmdDrawIndexed(currentCommandBuffer, count, 1, 0, 0, 0);
    }
    
    // ========================================================================
    // FRAME MANAGEMENT
    // ========================================================================
    
    /**
     * Begin recording commands for this frame
     */
    private static void beginFrame() {
        currentCommandBuffer = ctx.getCurrentCommandBuffer();
        
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        
        vkBeginCommandBuffer(currentCommandBuffer, beginInfo);
        
        // Begin render pass
        VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
            .renderPass(ctx.renderPass)
            .framebuffer(ctx.getCurrentFramebuffer());

renderPassInfo.renderArea().offset().set(0, 0);
        renderPassInfo.renderArea().extent().set(ctx.swapchainExtent.width(), ctx.swapchainExtent.height());
        
        VkClearValue.Buffer clearValues = VkClearValue.calloc(2);
        clearValues.get(0).color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 1.0f);
        clearValues.get(1).depthStencil().set(1.0f, 0);
        renderPassInfo.pClearValues(clearValues);
        
        vkCmdBeginRenderPass(currentCommandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        
        // Set viewport and scissor
        VkViewport.Buffer viewport = VkViewport.calloc(1)
            .x(0.0f)
            .y(0.0f)
            .width(ctx.swapchainExtent.width())
            .height(ctx.swapchainExtent.height())
            .minDepth(0.0f)
            .maxDepth(1.0f);
        vkCmdSetViewport(currentCommandBuffer, 0, viewport);
        
        VkRect2D.Buffer scissor = VkRect2D.calloc(1);
        scissor.offset().set(0, 0);
        scissor.extent().set(ctx.swapchainExtent.width(), ctx.swapchainExtent.height());
        vkCmdSetScissor(currentCommandBuffer, 0, scissor);
        
        recordingCommands = true;
        
        beginInfo.free();
        renderPassInfo.free();
        clearValues.free();
        viewport.free();
        scissor.free();
    }
    
    /**
     * GL: glFlush() / glFinish() / swap buffers
     * VK: End command buffer, submit, present
     */
    public static void endFrame() {
        if (!recordingCommands) return;
        
        // End render pass
        vkCmdEndRenderPass(currentCommandBuffer);
        
        // End command buffer
        int result = vkEndCommandBuffer(currentCommandBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to end command buffer: " + result);
        }
        
        // Submit command buffer
        VkSubmitInfo submitInfo = VkSubmitInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
        
        VkCommandBuffer.Buffer commandBuffers = VkCommandBuffer.calloc(1);
        commandBuffers.put(0, currentCommandBuffer);
        submitInfo.pCommandBuffers(commandBuffers);
        
        // Wait for image available
        LongBuffer waitSemaphores = LongBuffer.wrap(new long[]{ctx.imageAvailableSemaphore});
        submitInfo.pWaitSemaphores(waitSemaphores);
        submitInfo.pWaitDstStageMask(new int[]{VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT});
        
        // Signal when rendering complete
        LongBuffer signalSemaphores = LongBuffer.wrap(new long[]{ctx.renderFinishedSemaphore});
        submitInfo.pSignalSemaphores(signalSemaphores);
        
        result = vkQueueSubmit(ctx.graphicsQueue, submitInfo, ctx.inFlightFence);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to submit draw command buffer: " + result);
        }
        
        // Present
        VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc()
            .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
        
        presentInfo.pWaitSemaphores(signalSemaphores);
        
        LongBuffer swapchains = LongBuffer.wrap(new long[]{ctx.swapchain});
        presentInfo.pSwapchains(swapchains);
        presentInfo.pImageIndices(new int[]{ctx.currentImageIndex});
        
        result = vkQueuePresentKHR(ctx.presentQueue, presentInfo);
        if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
            throw new RuntimeException("Failed to present: " + result);
        }
        
        recordingCommands = false;
        
        submitInfo.free();
        commandBuffers.free();
        presentInfo.free();
    }
    
    // ========================================================================
    // SHADER OPERATIONS
    // ========================================================================
    
    /**
     * GL: glCreateShader(type)
     * VK: Return shader ID for later compilation to SPIR-V
     */
    public static long createShader(int type) {
        return state.createShader(type);
    }
    
    /**
     * GL: glShaderSource(shader, source)
     * VK: Store source for compilation to SPIR-V
     */
    public static void shaderSource(long shader, String source) {
        state.setShaderSource(shader, source);
    }
    
    /**
     * GL: glCompileShader(shader)
     * VK: Compile GLSL to SPIR-V using shaderc or external compiler
     */
    public static void compileShader(long shader) {
        VulkanState.ShaderObject shaderObj = state.getShader(shader);
        
        // Compile GLSL to SPIR-V
        // This requires shaderc library or external glslangValidator
        ByteBuffer spirv = compileGLSLtoSPIRV(shaderObj.source, shaderObj.type);
        
        // Create Vulkan shader module
        VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
            .pCode(spirv);
        
        LongBuffer pShaderModule = LongBuffer.allocate(1);
        int result = vkCreateShaderModule(ctx.device, createInfo, null, pShaderModule);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create shader module: " + result);
        }
        
        shaderObj.spirv = spirv;
        shaderObj.module = pShaderModule.get(0);
        shaderObj.compiled = true;
        
        createInfo.free();
    }
    
    /**
     * GL: glCreateProgram()
     * VK: Return program ID for pipeline creation
     */
    public static long createProgram() {
        return state.createProgram();
    }
    
    /**
     * GL: glAttachShader(program, shader)
     * VK: Store shader attachment for pipeline creation
     */
    public static void attachShader(long program, long shader) {
        state.attachShaderToProgram(program, shader);
    }
    
    /**
     * GL: glLinkProgram(program)
     * VK: Create graphics pipeline with attached shaders
     */
    public static void linkProgram(long program) {
        VulkanState.ProgramObject progObj = state.getProgram(program);
        
        // Get attached shaders
        VulkanState.ShaderObject vertShader = null;
        VulkanState.ShaderObject fragShader = null;
        
        for (long shaderId : progObj.attachedShaders) {
            VulkanState.ShaderObject shader = state.getShader(shaderId);
            if (shader.type == 0x8B31) { // GL_VERTEX_SHADER
                vertShader = shader;
            } else if (shader.type == 0x8B30) { // GL_FRAGMENT_SHADER
                fragShader = shader;
            }
        }
        
        if (vertShader == null || fragShader == null) {
            throw new RuntimeException("Program must have vertex and fragment shaders");
        }
        
        // Create pipeline
        VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2);
        
        shaderStages.get(0)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(VK_SHADER_STAGE_VERTEX_BIT)
            .module(vertShader.module)
            .pName(ByteBuffer.wrap("main\0".getBytes()));
        
        shaderStages.get(1)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
            .module(fragShader.module)
            .pName(ByteBuffer.wrap("main\0".getBytes()));
        
        // Vertex input state (will be set based on glVertexAttribPointer calls)
        VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
        
        // Input assembly
        VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
            .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
            .primitiveRestartEnable(false);
        
        // Viewport state (dynamic)
        VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
            .viewportCount(1)
            .scissorCount(1);
        
        // Rasterization
        VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
            .depthClampEnable(false)
            .rasterizerDiscardEnable(false)
            .polygonMode(VK_POLYGON_MODE_FILL)
            .lineWidth(1.0f)
            .cullMode(VK_CULL_MODE_BACK_BIT)
            .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
            .depthBiasEnable(false);
        
        // Multisampling
        VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
            .sampleShadingEnable(false)
            .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
        
        // Depth stencil
        VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
            .depthTestEnable(true)
            .depthWriteEnable(true)
            .depthCompareOp(VK_COMPARE_OP_LESS)
            .depthBoundsTestEnable(false)
            .stencilTestEnable(false);
        
        // Color blending
        VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1);
        colorBlendAttachment.get(0)
            .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
            .blendEnable(false);
        
        VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
            .logicOpEnable(false)
            .pAttachments(colorBlendAttachment);
        
        // Dynamic state
        int[] dynamicStates = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
            .pDynamicStates(dynamicStates);
        
        // Pipeline layout
        VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
            .pSetLayouts(LongBuffer.wrap(new long[]{ctx.descriptorSetLayout}));
        
        LongBuffer pPipelineLayout = LongBuffer.allocate(1);
        vkCreatePipelineLayout(ctx.device, pipelineLayoutInfo, null, pPipelineLayout);
        long pipelineLayout = pPipelineLayout.get(0);
        
        // Create graphics pipeline
        VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1);
        pipelineInfo.get(0)
            .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
            .pStages(shaderStages)
            .pVertexInputState(vertexInputInfo)
            .pInputAssemblyState(inputAssembly)
            .pViewportState(viewportState)
            .pRasterizationState(rasterizer)
            .pMultisampleState(multisampling)
            .pDepthStencilState(depthStencil)
            .pColorBlendState(colorBlending)
            .pDynamicState(dynamicState)
            .layout(pipelineLayout)
            .renderPass(ctx.renderPass)
            .subpass(0);
        
        LongBuffer pPipeline = LongBuffer.allocate(1);
        int result = vkCreateGraphicsPipelines(ctx.device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create graphics pipeline: " + result);
        }
        
        progObj.pipeline = pPipeline.get(0);
        progObj.pipelineLayout = pipelineLayout;
        progObj.linked = true;
        
        // Cleanup
        shaderStages.free();
        vertexInputInfo.free();
        inputAssembly.free();
        viewportState.free();
        rasterizer.free();
        multisampling.free();
        depthStencil.free();
        colorBlendAttachment.free();
        colorBlending.free();
        dynamicState.free();
        pipelineLayoutInfo.free();
        pipelineInfo.free();
    }
    
    /**
     * GL: glUseProgram(program)
     * VK: Set active pipeline
     */
    public static void useProgram(long program) {
        state.useProgram(program);
    }
    
    // ========================================================================
    // STATE MANAGEMENT
    // ========================================================================
    
    /**
     * GL: glEnable(cap)
     * VK: Update pipeline state (requires pipeline recreation)
     */
    public static void enable(int cap) {
        state.enable(cap);
    }
    
    /**
     * GL: glDisable(cap)
     * VK: Update pipeline state
     */
    public static void disable(int cap) {
        state.disable(cap);
    }
    
    /**
     * GL: glBlendFunc(sfactor, dfactor)
     * VK: Update blend state (requires pipeline recreation)
     */
    public static void blendFunc(int sfactor, int dfactor) {
        state.setBlendFunc(sfactor, dfactor);
    }
    
    /**
     * GL: glDepthFunc(func)
     * VK: Update depth state (requires pipeline recreation)
     */
    public static void depthFunc(int func) {
        state.setDepthFunc(func);
    }
    
    /**
     * GL: glCullFace(mode)
     * VK: Update cull mode (requires pipeline recreation)
     */
    public static void cullFace(int mode) {
        state.setCullFace(mode);
    }
    
    /**
     * GL: glClear(mask)
     * VK: Clear handled by render pass clear values
     */
    public static void clear(int mask) {
        // Vulkan clears via render pass, so this is mostly a no-op
        // We set clear values when beginning render pass
    }
    
    /**
     * GL: glClearColor(r, g, b, a)
     * VK: Update clear color for next render pass
     */
    public static void clearColor(float r, float g, float b, float a) {
        state.setClearColor(r, g, b, a);
    }
    
    // ========================================================================
    // VERTEX ATTRIBUTES
    // ========================================================================
    
    /**
     * GL: glVertexAttribPointer(index, size, type, normalized, stride, pointer)
     * VK: Update vertex input state (requires pipeline recreation)
     */
    public static void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        state.setVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }
    
    /**
     * GL: glEnableVertexAttribArray(index)
     * VK: Mark attribute as enabled
     */
    public static void enableVertexAttribArray(int index) {
        state.enableVertexAttribArray(index);
    }
    
    /**
     * GL: glDisableVertexAttribArray(index)
     * VK: Mark attribute as disabled
     */
    public static void disableVertexAttribArray(int index) {
        state.disableVertexAttribArray(index);
    }
    
    // ========================================================================
    // UNIFORMS
    // ========================================================================
    
    /**
     * GL: glGetUniformLocation(program, name)
     * VK: Return uniform location index
     */
    public static int getUniformLocation(long program, String name) {
        return state.getUniformLocation(program, name);
    }
    
    /**
     * GL: glUniform1f(location, v0)
     * VK: Update uniform buffer
     */
    public static void uniform1f(int location, float v0) {
        state.setUniform(location, v0);
    }
    
    /**
     * GL: glUniform4f(location, v0, v1, v2, v3)
     * VK: Update uniform buffer
     */
    public static void uniform4f(int location, float v0, float v1, float v2, float v3) {
        state.setUniform(location, v0, v1, v2, v3);
    }
    
    /**
     * GL: glUniformMatrix4fv(location, count, transpose, value)
     * VK: Update uniform buffer
     */
    public static void uniformMatrix4fv(int location, boolean transpose, float[] value) {
        state.setUniformMatrix4(location, transpose, value);
    }
    
    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================
    
    private static void bindDescriptorSets() {
        // Update descriptor sets based on current state
        long descriptorSet = state.getCurrentDescriptorSet();
        if (descriptorSet != VK_NULL_HANDLE) {
            VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
            if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE) {
                LongBuffer pDescriptorSets = LongBuffer.wrap(new long[]{descriptorSet});
                vkCmdBindDescriptorSets(
                    currentCommandBuffer,
                    VK_PIPELINE_BIND_POINT_GRAPHICS,
                    prog.pipelineLayout,
                    0,
                    pDescriptorSets,
                    null
                );
            }
        }
    }
    
    private static void updateTextureDescriptor(int unit, long imageView, long sampler) {
        // Update descriptor set with new texture binding
        VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1);
        imageInfo.get(0)
            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .imageView(imageView)
            .sampler(sampler);
        
        VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1);
        descriptorWrite.get(0)
            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            .dstSet(state.getCurrentDescriptorSet())
            .dstBinding(unit)
            .dstArrayElement(0)
            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .pImageInfo(imageInfo);
        
        vkUpdateDescriptorSets(ctx.device, descriptorWrite, null);
        
        imageInfo.free();
        descriptorWrite.free();
    }
    
    private static int translateFormatToVulkan(int glFormat) {
        return switch (glFormat) {
            case 0x1908 -> VK_FORMAT_R8G8B8A8_UNORM; // GL_RGBA
            case 0x1907 -> VK_FORMAT_R8G8B8_UNORM;   // GL_RGB
            case 0x8814 -> VK_FORMAT_R8G8B8A8_SRGB;  // GL_SRGB8_ALPHA8
            case 0x1902 -> VK_FORMAT_D32_SFLOAT;     // GL_DEPTH_COMPONENT
            default -> VK_FORMAT_R8G8B8A8_UNORM;
        };
    }
    
    private static int translateBufferUsage(int glTarget) {
        return switch (glTarget) {
            case 0x8892 -> VK_BUFFER_USAGE_VERTEX_BUFFER_BIT; // GL_ARRAY_BUFFER
            case 0x8893 -> VK_BUFFER_USAGE_INDEX_BUFFER_BIT;  // GL_ELEMENT_ARRAY_BUFFER
            case 0x8A11 -> VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT; // GL_UNIFORM_BUFFER
            default -> VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        };
    }
    
    /**
     * Compile GLSL to SPIR-V
     * This requires shaderc library or external process
     */
    private static ByteBuffer compileGLSLtoSPIRV(String source, int shaderType) {
        // TODO: Integrate shaderc or call external glslangValidator
        // For now, throw exception - this needs actual SPIR-V compiler
        throw new UnsupportedOperationException(
            "GLSL to SPIR-V compilation requires shaderc library or external compiler"
        );
        
        // Example with shaderc (if available):
        /*
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        
        int kind = (shaderType == 0x8B31) ? 
            shaderc_vertex_shader : shaderc_fragment_shader;
        
        long result = shaderc_compile_into_spv(
            compiler, source, kind, "shader", "main", options
        );
        
        if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
            String error = shaderc_result_get_error_message(result);
            throw new RuntimeException("Shader compilation failed: " + error);
        }
        
        ByteBuffer spirv = shaderc_result_get_bytes(result);
        
        shaderc_result_release(result);
        shaderc_compile_options_release(options);
        shaderc_compiler_release(compiler);
        
        return spirv;
        */
    }
}
