package com.example.modid.gl.buffer.ops;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/**
 * GL 2.0 - Adds GLSL shader support
 * 
 * Optimizations over 1.5:
 * - Vertex attributes instead of fixed-function
 * - Can use vertex shaders for transform optimizations
 * - Better precision for calculations
 */
public class GL20BufferOps extends GL15BufferOps {
    
    // Buffer operations same as GL15, but renderer can use shaders
    // This class exists for future shader-based optimizations
    
    @Override
    public String getName() {
        return "GL 2.0 VBO + Shaders";
    }
}
