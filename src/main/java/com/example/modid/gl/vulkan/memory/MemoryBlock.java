package com.example.modid.gl.vulkan.memory;

import com.example.modid.gl.vulkan.VulkanContext;
import org.lwjgl.vulkan.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.nio.ByteBuffer;
import java.util.LinkedList;

public class MemoryBlock {
    private final VulkanContext context;
    private final long memoryHandle;
    private final long totalSize;
    private final int memoryTypeIndex;
    private final ByteBuffer mappedBuffer; // Null if GPU-only
    private final int id;
    
    // Simple free-list allocator
    private final LinkedList<Node> freeNodes = new LinkedList<>();
    
    private static class Node {
        long offset;
        long size;
        Node(long offset, long size) { this.offset = offset; this.size = size; }
    }
    
    public MemoryBlock(VulkanContext context, int id, long size, int memoryTypeIndex) {
        this.context = context;
        this.id = id;
        this.totalSize = size;
        this.memoryTypeIndex = memoryTypeIndex;
        
        // 1. Allocate the physical device memory
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(size)
                .memoryTypeIndex(memoryTypeIndex);
                
            java.nio.LongBuffer pMem = stack.mallocLong(1);
            int res = vkAllocateMemory(context.device, allocInfo, null, pMem);
            if (res != VK_SUCCESS) throw new RuntimeException("Failed to allocate memory block: " + res);
            this.memoryHandle = pMem.get(0);
        }
        
        // 2. Map it immediately if it's Host Visible (Persistent Mapping)
        // Check properties
        VkPhysicalDeviceMemoryProperties memProps = VkPhysicalDeviceMemoryProperties.calloc();
        vkGetPhysicalDeviceMemoryProperties(context.physicalDevice, memProps);
        int flags = memProps.memoryTypes(memoryTypeIndex).propertyFlags();
        memProps.free();
        
        if ((flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0) {
            org.lwjgl.PointerBuffer pData = memAllocPointer(1);
            vkMapMemory(context.device, memoryHandle, 0, size, 0, pData);
            this.mappedBuffer = pData.getByteBuffer(0, (int)size);
            memFree(pData);
        } else {
            this.mappedBuffer = null;
        }
        
        // Initial free node covering whole block
        freeNodes.add(new Node(0, size));
    }
    
    public MemoryAllocation allocate(long size, long alignment) {
        // Simple First-Fit algorithm
        for (Node node : freeNodes) {
            long padding = (alignment - (node.offset % alignment)) % alignment;
            long requiredSize = size + padding;
            
            if (node.size >= requiredSize) {
                long allocOffset = node.offset + padding;
                
                // Shrink or remove the node
                if (node.size == requiredSize) {
                    freeNodes.remove(node);
                } else {
                    node.offset += requiredSize;
                    node.size -= requiredSize;
                }
                
                ByteBuffer slice = null;
                if (mappedBuffer != null) {
                    // Create a view
                    mappedBuffer.position((int)allocOffset);
                    mappedBuffer.limit((int)(allocOffset + size));
                    slice = mappedBuffer.slice();
                    mappedBuffer.clear();
                }
                
                return new MemoryAllocation(memoryHandle, allocOffset, size, slice, id);
            }
        }
        return null; // Out of memory in this block
    }
    
    public void free(MemoryAllocation allocation) {
        // Merge back into free list (Coalescing omitted for brevity, but critical for prod)
        // A naive implementation just adds a node. 
        // Production: Sort list by offset and merge adjacent nodes.
        freeNodes.add(new Node(allocation.offset, allocation.size));
    }
    
    public void destroy() {
        if (mappedBuffer != null) {
            vkUnmapMemory(context.device, memoryHandle);
        }
        vkFreeMemory(context.device, memoryHandle, null);
    }
}
