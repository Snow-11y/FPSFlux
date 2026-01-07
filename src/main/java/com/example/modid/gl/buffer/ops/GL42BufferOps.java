package com.example.modid.gl.buffer.ops;

import org.lwjgl.opengl.GL42;

/**
 * GL 4.2 - Base Instance Rendering
 * 
 * Optimizations over 4.0:
 * - glDrawArraysInstancedBaseInstance for efficient instancing
 * - Atomic counters for lock-free parallel shader operations
 * - Image load/store for arbitrary buffer access in shaders
 * - Compressed texture formats for reduced VRAM usage
 */
public class GL42BufferOps extends GL40BufferOps {
    
    @Override
    public String getName() {
        return "GL 4.2 Base Instance";
    }
}
