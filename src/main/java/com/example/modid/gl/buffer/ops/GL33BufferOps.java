package com.example.modid.gl.buffer.ops;

import org.lwjgl.opengl.GL33;

/**
 * GL 3.3 - Instanced Rendering
 * 
 * Optimizations over 3.1:
 * - glDrawArraysInstanced / glDrawElementsInstanced
 * - Render same geometry multiple times with different transforms
 * - Massive reduction in draw calls (1000+ chunks -> 1 instanced call)
 * - Vertex attribute divisors for per-instance data
 */
public class GL33BufferOps extends GL31BufferOps {
    
    // Buffer operations inherit from 3.1
    // Main benefit is instanced drawing support for renderer
    
    @Override
    public String getName() {
        return "GL 3.3 Instanced Rendering";
    }
}
