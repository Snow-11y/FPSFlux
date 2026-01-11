package com.example.modid.gl.vulkan.meshlet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * MeshletData - Represents a cluster of geometry.
 * Memory Layout (Optimized for std430/scalar packing):
 * - vec3 center (12 bytes)
 * - float radius (4 bytes)
 * - uint vertexOffset (4 bytes)
 * - uint indexOffset (4 bytes)
 * - uint vertexCount (4 bytes)
 * - uint triangleCount (4 bytes)
 * Total: 32 bytes (perfect cache alignment)
 */
public class MeshletData {
    public static final int SIZE_BYTES = 32;
    
    public float centerX, centerY, centerZ;
    public float radius;
    public int vertexOffset;
    public int indexOffset;
    public int vertexCount;
    public int triangleCount;
    
    public void write(ByteBuffer buffer) {
        buffer.putFloat(centerX).putFloat(centerY).putFloat(centerZ);
        buffer.putFloat(radius);
        buffer.putInt(vertexOffset);
        buffer.putInt(indexOffset);
        buffer.putInt(vertexCount);
        buffer.putInt(triangleCount);
    }
}
