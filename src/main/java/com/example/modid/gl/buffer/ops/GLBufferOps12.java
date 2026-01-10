package com.example.modid.gl.buffer.ops;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * GLBufferOps12 - OpenGL 1.2 Pipeline
 * 
 * OpenGL 1.2 added 3D textures, BGRA, packed pixels - still no VBOs.
 * All buffer operations throw UnsupportedOperationException.
 * 
 * F3 Display Color: Light Gray (#777777) - Legacy/Unsupported
 */
public final class GLBufferOps12 {

    public static final int VERSION_CODE = 12;
    public static final int DISPLAY_COLOR = 0x777777;
    public static final String VERSION_NAME = "OpenGL 1.2 (No VBO)";

    private static final String ERR_MSG = "OpenGL 1.2 does not support Vertex Buffer Objects. " +
            "Minimum GL 1.5 required.";

    public GLBufferOps12() {
        System.err.println("[GLBufferOps12] WARNING: GL 1.2 pipeline selected. VBOs not available!");
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
