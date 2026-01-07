package com.example.modid.gl.buffer.ops;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

/**
 * GL 3.1 - Dedicated Copy Buffers
 * 
 * Optimizations over 3.0:
 * - glCopyBufferSubData for GPU-to-GPU copies (no CPU roundtrip)
 * - Uniform Buffer Objects for shared shader data
 * - Texture Buffer Objects for large data arrays
 */
public class GL31BufferOps extends GL30BufferOps {
    
    @Override
    public int resizeBuffer(int oldVbo, long oldSize, long newSize, int usage) {
        // Use dedicated copy buffers - MUCH faster than map/unmap
        
        int newVbo = GL15.glGenBuffers();
        
        // Bind new buffer to COPY_WRITE
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, newVbo);
        GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER, newSize, usage);
        
        // Bind old buffer to COPY_READ
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, oldVbo);
        
        // GPU-side copy - no CPU involvement!
        GL31.glCopyBufferSubData(
            GL31.GL_COPY_READ_BUFFER,
            GL31.GL_COPY_WRITE_BUFFER,
            0, // src offset
            0, // dst offset
            oldSize
        );
        
        // Cleanup
        GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
        GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        GL15.glDeleteBuffers(oldVbo);
        
        return newVbo;
    }
    
    @Override
    public String getName() {
        return "GL 3.1 Copy Buffers";
    }
}
