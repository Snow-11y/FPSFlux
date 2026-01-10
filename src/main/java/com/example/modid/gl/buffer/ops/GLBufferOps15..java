package com.example.modid.gl.buffer.ops;

import com.example.modid.gl.mapping.OpenGLCallMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * OpenGL 1.5 Pipeline - FIRST VBO SUPPORT
 * 
 * Features: glGenBuffers, glDeleteBuffers, glBindBuffer, glBufferData,
 *           glBufferSubData, glMapBuffer, glUnmapBuffer, glGetBufferParameteriv
 * 
 * Optimizations:
 * - Pooled direct IntBuffer (4 bytes) for single ops - ZERO allocation
 * - Batch buffer pool for multi-ops
 * - No per-call object creation
 * 
 * Display Color: Bronze (#CD7F32)
 */
public final class GLBufferOps15 {
    public static final int VERSION = 15;
    public static final int COLOR = 0xCD7F32;
    public static final String NAME = "GL 1.5";

    // ═══════════════════════════════════════════════════════════════════════════
    // BUFFER POOL - Zero allocation for hot paths
    // ═══════════════════════════════════════════════════════════════════════════
    
    // Single-int buffer per thread (4 bytes, reused forever)
    private static final ThreadLocal<IntBuffer> INT1 = ThreadLocal.withInitial(() -> 
        ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer());
    
    // Small batch buffer pool (up to 64 buffers = 256 bytes)
    private static final ThreadLocal<IntBuffer> INT64 = ThreadLocal.withInitial(() -> 
        ByteBuffer.allocateDirect(256).order(ByteOrder.nativeOrder()).asIntBuffer());
    
    // Query buffer
    private static final ThreadLocal<IntBuffer> QUERY = ThreadLocal.withInitial(() -> 
        ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer());

    // Track if shutdown was called
    private volatile boolean shutdown;

    public GLBufferOps15() {
        // Warm up thread-locals on render thread
        INT1.get();
        INT64.get();
        QUERY.get();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUFFER GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    public int genBuffer() {
        IntBuffer b = INT1.get();
        b.clear();
        OpenGLCallMapper.glGenBuffers(b);
        return b.get(0);
    }

    public int[] genBuffers(int count) {
        if (count <= 0) return new int[0];
        if (count == 1) return new int[] { genBuffer() };
        
        // Use pooled buffer if fits, else allocate (rare for large batches)
        IntBuffer b;
        if (count <= 64) {
            b = INT64.get();
            b.clear();
            b.limit(count);
        } else {
            b = ByteBuffer.allocateDirect(count << 2)
                .order(ByteOrder.nativeOrder()).asIntBuffer();
        }
        
        OpenGLCallMapper.glGenBuffers(b);
        int[] result = new int[count];
        b.rewind();
        b.get(result);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUFFER DELETION
    // ═══════════════════════════════════════════════════════════════════════════

    public void deleteBuffer(int buffer) {
        if (buffer == 0) return;
        IntBuffer b = INT1.get();
        b.clear();
        b.put(0, buffer);
        b.limit(1);
        OpenGLCallMapper.glDeleteBuffers(b);
    }

    public void deleteBuffers(int[] buffers) {
        if (buffers == null || buffers.length == 0) return;
        
        int len = buffers.length;
        if (len == 1) {
            deleteBuffer(buffers[0]);
            return;
        }
        
        IntBuffer b;
        if (len <= 64) {
            b = INT64.get();
            b.clear();
        } else {
            b = ByteBuffer.allocateDirect(len << 2)
                .order(ByteOrder.nativeOrder()).asIntBuffer();
        }
        
        b.put(buffers);
        b.flip();
        OpenGLCallMapper.glDeleteBuffers(b);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUFFER BINDING
    // ═══════════════════════════════════════════════════════════════════════════

    public void bindBuffer(int target, int buffer) {
        OpenGLCallMapper.glBindBuffer(target, buffer);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUFFER DATA UPLOAD
    // ═══════════════════════════════════════════════════════════════════════════

    public void bufferData(int target, long size, int usage) {
        OpenGLCallMapper.glBufferData(target, size, usage);
    }

    public void bufferData(int target, ByteBuffer data, int usage) {
        if (data == null) {
            OpenGLCallMapper.glBufferData(target, 0L, usage);
            return;
        }
        OpenGLCallMapper.glBufferData(target, data, usage);
    }

    public void bufferData(int target, FloatBuffer data, int usage) {
        if (data == null) {
            OpenGLCallMapper.glBufferData(target, 0L, usage);
            return;
        }
        OpenGLCallMapper.glBufferData(target, data, usage);
    }

    public void bufferData(int target, IntBuffer data, int usage) {
        if (data == null) {
            OpenGLCallMapper.glBufferData(target, 0L, usage);
            return;
        }
        OpenGLCallMapper.glBufferData(target, data, usage);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUFFER SUB-DATA UPDATE
    // ═══════════════════════════════════════════════════════════════════════════

    public void bufferSubData(int target, long offset, ByteBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        OpenGLCallMapper.glBufferSubData(target, offset, data);
    }

    public void bufferSubData(int target, long offset, FloatBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        OpenGLCallMapper.glBufferSubData(target, offset, data);
    }

    public void bufferSubData(int target, long offset, IntBuffer data) {
        if (data == null || !data.hasRemaining()) return;
        OpenGLCallMapper.glBufferSubData(target, offset, data);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUFFER MAPPING (GL 1.5 style - whole buffer)
    // ═══════════════════════════════════════════════════════════════════════════

    public ByteBuffer mapBuffer(int target, int access) {
        return OpenGLCallMapper.glMapBuffer(target, access);
    }

    public ByteBuffer mapBuffer(int target, int access, long length) {
        return OpenGLCallMapper.glMapBuffer(target, access, length, null);
    }

    public boolean unmapBuffer(int target) {
        return OpenGLCallMapper.glUnmapBuffer(target);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUFFER QUERY
    // ═══════════════════════════════════════════════════════════════════════════

    public int getBufferParameteri(int target, int pname) {
        IntBuffer b = QUERY.get();
        b.clear();
        OpenGLCallMapper.glGetBufferParameteriv(target, pname, b);
        return b.get(0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SHUTDOWN - Clean ThreadLocals to prevent leaks
    // ═══════════════════════════════════════════════════════════════════════════

    public void shutdown() {
        if (shutdown) return;
        shutdown = true;
        
        // Remove thread-locals to allow GC of direct buffers
        try { INT1.remove(); } catch (Throwable ignored) {}
        try { INT64.remove(); } catch (Throwable ignored) {}
        try { QUERY.remove(); } catch (Throwable ignored) {}
    }
}
