package com.example.modid.gl.buffer.ops;

import org.lwjgl.opengl.GL46;

/**
 * GL 4.6 - Cutting Edge
 * 
 * Optimizations over 4.5:
 * - SPIR-V shader support (precompiled shaders, faster load times)
 * - Polygon offset clamp for better z-fighting control
 * - Anisotropic filtering as core feature
 * - Multi-vendor extensions standardized
 * 
 * This is bleeding edge - not all GPUs support it yet
 * But when they do, it's the fastest path available
 */
public class GL46BufferOps extends GL45BufferOps {
    
    // Buffer operations same as 4.5 (DSA is the peak for buffers)
    // Main benefits are shader compilation and texture features
    
    @Override
    public String getName() {
        return "GL 4.6 Latest";
    }
}
