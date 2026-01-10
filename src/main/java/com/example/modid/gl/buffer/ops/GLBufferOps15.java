package com.example.modid.gl.buffer.ops;

import com.example.modid.gl.mapping.OpenGLCallMapper;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * GLBufferOps15 - OpenGL 1.5 Pipeline
 * 
 * FIRST VERSION WITH VERTEX BUFFER OBJECTS!
 * Core features: glGenBuffers, glDeleteBuffers, glBindBuffer, 
 *                glBufferData, glBufferSubData, glMapBuffer, glUnmapBuffer
 * 
 * Optimizations:
 * - Thread-local IntBuffer for single-buffer gen/delete (zero allocation after warmup)
 * - Direct mapper calls (no abstraction overhead)
 * 
 * F3 Display Color: Bronze (#CD7F32) - First VBO Era
 */
public final class GLBufferOps15 {

    public static final int VERSION_CODE = 15;
    public static final int DISPLAY_COLOR = 0xCD7F32;
    public static final String VERSION_NAME = "OpenGL 1.5";

    // Thread-local buffer for single gen/delete operations - avoids allocation
    private static final ThreadLocal<IntBuffer> SINGLE_INT_BUFFER = ThreadLocal.withInitial(() -> {
        return ByteBuffer.allocateDirect(4).asIntBuffer();
    });

    public GLBufferOps15() {
        // Warm up the thread-local buffer on construction
        SINGLE_INT_BUFFER.get();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Buffer Generation
    // ─────────────────────────────────────────────────────────────────────────────

    public int genBuffer() {
        IntBuffer buf = SINGLE_INT_BUFFER.get();
        buf.clear();
        OpenGLCallMapper.glGenBuffers(buf);
        return buf.get(0);
    }

    public int[] genBuffers(int count) {
        if (count <= 0) return new int[0];
        if (count == 1) return new int[] { genBuffer() };
        
        // Batch generation - allocate result array (unavoidable for batch)
        IntBuffer buf = ByteBuffer.allocateDirect(count * 4).asIntBuffer();
        OpenGLCallMapper.glGenBuffers(buf);
        int[] result = new int[count];
        buf.get(result);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Buffer Deletion
    // ─────────────────────────────────────────────────────────────────────────────

    public void deleteBuffer(int buffer) {
        if (buffer == 0) return;
        IntBuffer buf = SINGLE_INT_BUFFER.get();
        buf.clear();
        buf.put(0, buffer);
        OpenGLCallMapper.glDeleteBuffers(buf);
    }

    public void deleteBuffers(int[] buffers) {
        if (buffers == null || buffers.length == 0) return;
        if (buffers.length == 1) {
            deleteBuffer(buffers[0]);
            return;
        }
        
        IntBuffer buf = ByteBuffer.allocateDirect(buffers.length * 4).asIntBuffer();
        buf.put(buffers);
        buf.flip();
        OpenGLCallMapper.glDeleteBuffers(buf);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Buffer Binding
    // ─────────────────────────────────────────────────────────────────────────────

    public void bindBuffer(int target, int buffer) {
        OpenGLCallMapper.glBindBuffer(target, buffer);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Buffer Data Upload
    // ─────────────────────────────────────────────────────────────────────────────

    public void bufferData(int target, long size, int usage) {
        OpenGLCallMapper.glBufferData(target, size, usage);
    }

    public void bufferData(int target, ByteBuffer data, int usage) {
        OpenGLCallMapper.glBufferData(target, data, usage);
    }

    public void bufferData(int target, FloatBuffer data, int usage) {
        OpenGLCallMapper.glBufferData(target, data, usage);
    }

    public void bufferData(int target, IntBuffer data, int usage) {
        OpenGLCallMapper.glBufferData(target, data, usage);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Buffer Sub-Data Update
    // ─────────────────────────────────────────────────────────────────────────────

    public void bufferSubData(int target, long offset, ByteBuffer data) {
        OpenGLCallMapper.glBufferSubData(target, offset, data);
    }

    public void bufferSubData(int target, long offset, FloatBuffer data) {
        OpenGLCallMapper.glBufferSubData(target, offset, data);
    }

    public void bufferSubData(int target, long offset, IntBuffer data) {
        OpenGLCallMapper.glBufferSubData(target, offset, data);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Buffer Mapping (GL 1.5 style - whole buffer only)
    // ─────────────────────────────────────────────────────────────────────────────

    public ByteBuffer mapBuffer(int target, int access) {
        return OpenGLCallMapper.glMapBuffer(target, access);
    }

    public ByteBuffer mapBuffer(int target, int access, long length) {
        // GL 1.5 mapBuffer doesn't use length, but some LWJGL versions need it
        return OpenGLCallMapper.glMapBuffer(target, access, length, null);
    }

    public boolean unmapBuffer(int target) {
        return OpenGLCallMapper.glUnmapBuffer(target);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Query
    // ─────────────────────────────────────────────────────────────────────────────

    public int getBufferParameteri(int target, int pname) {
        return OpenGLCallMapper.glGetBufferParameteri(target, pname);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────────

    public void shutdown() {
        // ThreadLocal will be cleaned up by GC when thread dies
        // No explicit cleanup needed for thread-local direct buffers
    }
}
