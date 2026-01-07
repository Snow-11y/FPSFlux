package com.example.modid.gl.buffer.ops;

/**
 * GL 4.0 - Tessellation Shaders & Transform Feedback
 * 
 * Optimizations over 3.3:
 * - Tessellation for dynamic LOD (generate detail on GPU)
 * - Transform feedback to capture shader output to buffers
 * - GPU-side particle systems and procedural geometry
 * - Double precision for large world coordinates
 */
public class GL40BufferOps extends GL33BufferOps {
    
    @Override
    public String getName() {
        return "GL 4.0 Tessellation";
    }
}
