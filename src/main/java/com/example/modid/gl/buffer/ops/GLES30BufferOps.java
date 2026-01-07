package com.example.modid.gl.buffer.ops;

import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;

/**
 * OpenGL ES 3.0 - Mobile baseline
 * 
 * ES 3.0 features:
 * - VAO support (like desktop GL 3.0)
 * - MapBufferRange for efficient updates
 * - Transform feedback
 * - Uniform buffer objects
 * 
 * Key differences from desktop:
 * - No fixed-function pipeline
 * - Must use shaders for everything
 * - Stricter about precision qualifiers
 */
public class GLES30BufferOps implements BufferOps {
    
    @Override
    public int createBuffer(long size, int usage) {
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, size, usage);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return vbo;
    }
    
    @Override
    public void uploadData(int buffer, long offset, ByteBuffer data) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offset, data);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
    
    @Override
    public int resizeBuffer(int oldVbo, long oldSize, long newSize, int usage) {
        // ES 3.0 has MapBufferRange but no CopyBufferSubData
        // Use map/unmap strategy similar to desktop GL 1.5
        
        int tempBuffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tempBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, oldSize, GL15.GL_STREAM_COPY);
        
        // Map with ES 3.0 MapBufferRange
        int flags = GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_READ_BIT;
        ByteBuffer tempData = GL30.glMapBufferRange(
            GL15.GL_ARRAY_BUFFER,
            0,
            oldSize,
            flags,
            null
        );
        
        // Copy from old buffer
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldVbo);
        GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, tempData);
        
        // Resize original buffer
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, newSize, usage);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, tempData);
        
        // Cleanup
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tempBuffer);
        GL30.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glDeleteBuffers(tempBuffer);
        
        return oldVbo;
    }
    
    @Override
    public ByteBuffer mapBuffer(int buffer, long size, int access) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        
        // ES 3.0 requires explicit flags
        int flags = (access == GL15.GL_READ_WRITE) ?
            (GL30.GL_MAP_READ_BIT | GL30.GL_MAP_WRITE_BIT) :
            (access == GL15.GL_WRITE_ONLY ? GL30.GL_MAP_WRITE_BIT : GL30.GL_MAP_READ_BIT);
        
        return GL30.glMapBufferRange(GL15.GL_ARRAY_BUFFER, 0, size, flags, null);
    }
    
    @Override
    public void unmapBuffer(int buffer) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL30.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
    
    @Override
    public void deleteBuffer(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }
    
    @Override
    public String getName() {
        return "OpenGL ES 3.0";
    }
}
