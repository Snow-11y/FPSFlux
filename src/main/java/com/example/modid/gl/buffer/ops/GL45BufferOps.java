package com.example.modid.gl.buffer.ops;

import com.example.modid.gl.GLCapabilities;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;

/**
 * GL 4.5+ Direct State Access - eliminates binding overhead entirely.
 * 
 * All operations reference buffers by ID, no binding required.
 * Combines with persistent mapping for ultimate performance.
 */
public class GL45BufferOps extends GL44BufferOps {
    
    @Override
    public int createBuffer(long size, int usage) {
        if (!GLCapabilities.hasDSA) {
            return super.createBuffer(size, usage);
        }
        
        // DSA: create without binding
        int buffer = GL45.glCreateBuffers();
        
        int flags = GL44.GL_MAP_WRITE_BIT | 
                   GL44.GL_MAP_PERSISTENT_BIT | 
                   GL44.GL_MAP_COHERENT_BIT;
        
        GL45.glNamedBufferStorage(buffer, size, flags);
        
        return buffer;
    }
    
    @Override
    public void uploadData(int buffer, long offset, ByteBuffer data) {
        if (GLCapabilities.hasDSA) {
            // DSA: upload without binding
            GL45.glNamedBufferSubData(buffer, offset, data);
        } else {
            super.uploadData(buffer, offset, data);
        }
    }
    
    @Override
    public ByteBuffer mapBuffer(int buffer, long size, int access) {
        if (!GLCapabilities.hasDSA) {
            return super.mapBuffer(buffer, size, access);
        }
        
        int flags = GL30.GL_MAP_WRITE_BIT | 
                   GL44.GL_MAP_PERSISTENT_BIT | 
                   GL44.GL_MAP_COHERENT_BIT;
        
        // DSA: map without binding
        return GL45.glMapNamedBufferRange(buffer, 0, size, flags, null);
    }
    
    @Override
    public int resizeBuffer(int oldBuffer, long oldSize, long newSize, int usage) {
        int newBuffer = createBuffer(newSize, usage);
        
        // DSA copy - cleanest possible
        GL45.glCopyNamedBufferSubData(oldBuffer, newBuffer, 0, 0, oldSize);
        
        deleteBuffer(oldBuffer);
        return newBuffer;
    }
    
    @Override
    public String getName() {
        return "GL 4.5 DSA + Persistent Mapping";
    }
}
