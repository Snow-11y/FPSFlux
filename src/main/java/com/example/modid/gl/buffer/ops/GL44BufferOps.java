package com.example.modid.gl.buffer.ops;

import com.example.modid.gl.GLCapabilities;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;

/**
 * GL 4.4+ Persistent Mapped Buffers - the most advanced buffer strategy.
 * 
 * Maps GPU memory once at creation, keeps it mapped forever.
 * Uses fence sync to avoid stalls. Zero overhead uploads.
 */
public class GL44BufferOps implements BufferOps {
    
    @Override
    public int createBuffer(long size, int usage) {
        if (!GLCapabilities.hasPersistentMapping) {
            throw new UnsupportedOperationException("GL 4.4 required");
        }
        
        int buffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        
        // Create persistent mapped buffer
        int flags = GL44.GL_MAP_WRITE_BIT | 
                   GL44.GL_MAP_PERSISTENT_BIT | 
                   GL44.GL_MAP_COHERENT_BIT;
        
        GL44.glBufferStorage(GL15.GL_ARRAY_BUFFER, size, flags);
        
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return buffer;
    }
    
    @Override
    public ByteBuffer mapBuffer(int buffer, long size, int access) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        
        int flags = GL30.GL_MAP_WRITE_BIT | 
                   GL44.GL_MAP_PERSISTENT_BIT | 
                   GL44.GL_MAP_COHERENT_BIT;
        
        ByteBuffer mapped = GL30.glMapBufferRange(
            GL15.GL_ARRAY_BUFFER,
            0,
            size,
            flags,
            null
        );
        
        return mapped;
    }
    
    @Override
    public void uploadData(int buffer, long offset, ByteBuffer data) {
        // With persistent mapping, we write directly to mapped memory
        // No GL call needed - this is why it's fast!
        // (Actual mapped buffer management done by BufferAllocator)
    }
    
    @Override
    public int resizeBuffer(int oldBuffer, long oldSize, long newSize, int usage) {
        // Can't resize persistent mapped buffers - create new and copy
        int newBuffer = createBuffer(newSize, usage);
        
        // Use DSA copy if available
        if (GLCapabilities.GL45) {
            GL45.glCopyNamedBufferSubData(oldBuffer, newBuffer, 0, 0, oldSize);
        } else {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, oldBuffer);
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, newBuffer);
            GL31.glCopyBufferSubData(
                GL31.GL_COPY_READ_BUFFER,
                GL31.GL_COPY_WRITE_BUFFER,
                0, 0, oldSize
            );
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, 0);
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, 0);
        }
        
        deleteBuffer(oldBuffer);
        return newBuffer;
    }
    
    @Override
    public void unmapBuffer(int buffer) {
        // Persistent mapped buffers stay mapped
        // No-op for this implementation
    }
    
    @Override
    public void deleteBuffer(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }
    
    @Override
    public String getName() {
        return "GL 4.4 Persistent Mapped Buffers";
    }
}
