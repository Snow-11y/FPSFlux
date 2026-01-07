package com.example.modid.gl.buffer.ops;

import org.lwjgl.opengl.*;

/**
 * OpenGL ES 3.2 - Near desktop parity
 * 
 * ES 3.2 additions over 3.1:
 * - Geometry shaders
 * - Tessellation shaders
 * - Texture buffer objects
 * - Debug output
 * - CopyBufferSubData (FINALLY!)
 * 
 * This is roughly equivalent to desktop GL 4.0 feature-wise
 */
public class GLES32BufferOps extends GLES31BufferOps {
    
    @Override
    public int resizeBuffer(int oldVbo, long oldSize, long newSize, int usage) {
        // ES 3.2 has CopyBufferSubData! Use it like desktop GL 3.1+
        
        int newVbo = GL15.glGenBuffers();
        
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, newVbo);
        GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER, newSize, usage);
        
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, oldVbo);
        
        // Finally available in ES 3.2!
        GL31.glCopyBufferSubData(
            GL31.GL_COPY_READ_BUFFER,
            GL31.GL_COPY_WRITE_BUFFER,
            0, 0, oldSize
        );
        
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        GL15.glDeleteBuffers(oldVbo);
        
        return newVbo;
    }
    
    @Override
    public String getName() {
        return "OpenGL ES 3.2 (Full Features)";
    }
}
