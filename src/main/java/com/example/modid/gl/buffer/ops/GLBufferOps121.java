package com.example.modid.gl.buffer.ops;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * GLBufferOps121 - OpenGL 1.2.1 Pipeline
 * 
 * OpenGL 1.2.1 = 1.2 + ARB_imaging subset. Still no VBOs.
 * Used as fallback for GL 1.3/1.4 which also lack core VBOs.
 * 
 * F3 Display Color: Orange-Gray (#888866) - Legacy Transitional
 */
public final class GLBufferOps121 {

    public static final int VERSION_CODE = 121;
    public static final int DISPLAY_COLOR = 0x888866;
    public static final String VERSION_NAME = "OpenGL 1.2.1 (No VBO)";

    private static final String ERR_MSG = "OpenGL 1.2.1/1.3/1.4 does not support core Vertex Buffer Objects. " +
            "Minimum GL 1.5 required. Some hardware may support ARB_vertex_buffer_object extension.";

    public GLBufferOps121() {
        System.err.println("[GLBufferOps121] WARNING: GL 1.2.1 pipeline selected. VBOs not available!");
    }

    public int genBuffer() { throw new UnsupportedOperationException(ERR_MSG); }
    public int[] genBuffers(int count) { throw new UnsupportedOperationException(ERR_MSG); }
    public void deleteBuffer(int buffer) { throw new UnsupportedOperationException(ERR_MSG); }
    public void deleteBuffers(int[] buffers) { throw new UnsupportedOperationException(ERR_MSG); }
    public void bindBuffer(int target, int buffer) { throw new UnsupportedOperationException(ERR_MSG); }
    public void bufferData(int target, long size, int usage) { throw new UnsupportedOperationException(ERR_MSG); }
    public void bufferData(int target, ByteBuffer data, int usage) { throw new UnsupportedOperationException(ERR_MSG); }
    public void bufferData(int target, FloatBuffer data, int usage) { throw new UnsupportedOperationException(ERR_MSG); }
    public void bufferData(int target, IntBuffer data, int usage) { throw new UnsupportedOperationException(ERR_MSG); }
    public void bufferSubData(int target, long offset, ByteBuffer data) { throw new UnsupportedOperationException(ERR_MSG); }
    public void bufferSubData(int target, long offset, FloatBuffer data) { throw new UnsupportedOperationException(ERR_MSG); }
    public void bufferSubData(int target, long offset, IntBuffer data) { throw new UnsupportedOperationException(ERR_MSG); }
    public ByteBuffer mapBuffer(int target, int access) { throw new UnsupportedOperationException(ERR_MSG); }
    public boolean unmapBuffer(int target) { throw new UnsupportedOperationException(ERR_MSG); }
    public void shutdown() {}
}
