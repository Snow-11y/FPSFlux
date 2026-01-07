package com.example.modid.gl.buffer.ops;

import org.lwjgl.opengl.*;

/**
 * OpenGL ES 3.1 - Adds compute shaders
 * 
 * ES 3.1 additions over 3.0:
 * - Compute shaders (GPU-side culling, sorting)
 * - Shader storage buffer objects (SSBO)
 * - Indirect drawing
 * - Separate shader programs
 * 
 * Still no:
 * - Geometry shaders
 * - Tessellation
 * - CopyBufferSubData (use SSBO workaround)
 */
public class GLES31BufferOps extends GLES30BufferOps {
    
    @Override
    public int resizeBuffer(int oldVbo, long oldSize, long newSize, int usage) {
        // ES 3.1 still doesn't have CopyBufferSubData
        // But we can use compute shader as workaround if needed
        
        // For now, fall back to parent ES 3.0 implementation
        return super.resizeBuffer(oldVbo, oldSize, newSize, usage);
    }
    
    @Override
    public String getName() {
        return "OpenGL ES 3.1 (Compute Shaders)";
    }
}
