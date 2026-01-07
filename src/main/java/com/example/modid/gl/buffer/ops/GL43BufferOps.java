package com.example.modid.gl.buffer.ops;

import org.lwjgl.opengl.GL43;

/**
 * GL 4.3 - Multi-Draw Indirect & Compute Shaders
 * 
 * Optimizations over 4.2:
 * - glMultiDrawArraysIndirect: entire scene in ONE draw call
 * - Compute shaders for culling, sorting, LOD calculations on GPU
 * - Shader Storage Buffer Objects (SSBO) for massive data arrays
 * - No CPU bottleneck - GPU manages its own workload
 * 
 * This is where things get truly advanced:
 * - GPU culls invisible chunks itself
 * - GPU builds draw commands
 * - CPU just says "render" and GPU does everything
 */
public class GL43BufferOps extends GL42BufferOps {
    
    // Multi-draw indirect requires specialized setup
    // Actual draw call batching implemented in DrawCallBatcher
    
    @Override
    public String getName() {
        return "GL 4.3 Multi-Draw Indirect + Compute";
    }
}
