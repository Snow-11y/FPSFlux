package com.example.modid.gl.vulkan.memory;

/**
 * MemoryAllocation - Represents a slice of VRAM.
 */
public class MemoryAllocation {
    public final long memoryHandle;   // The underlying large VkDeviceMemory block
    public final long offset;         // Offset within that block
    public final long size;           // Size of this specific allocation
    public final ByteBuffer mappedPtr;// If host-visible, this points to the data
    public final int blockId;         // ID of the parent block
    
    public MemoryAllocation(long memoryHandle, long offset, long size, ByteBuffer mappedPtr, int blockId) {
        this.memoryHandle = memoryHandle;
        this.offset = offset;
        this.size = size;
        this.mappedPtr = mappedPtr;
        this.blockId = blockId;
    }
    
    /**
     * Get the device address for this allocation + an internal offset.
     * Useful for Bindless Descriptors.
     */
    public long getDeviceAddress() {
        // Note: requires buffer object to query address, usually handled by wrapper
        return offset; 
    }
}
