package com.example.modid.gl.buffer.ops;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * GLBufferOps11 - OpenGL 1.1 Pipeline
 * 
 * OpenGL 1.1 added texture objects but still no VBOs.
 * All buffer operations throw UnsupportedOperationException.
 * 
 * F3 Display Color: Gray (#666666) - Legacy/Unsupported
 */
public final class GLBufferOps11 {

    public static final int VERSION_CODE = 11;
    public static final int DISPLAY_COLOR = 0x666666;
    public static final String VERSION_NAME = "OpenGL 1.1 (No VBO)";

    private static final String ERR_MSG = "OpenGL 1.1 does not support Vertex Buffer Objects. " +
            "Minimum GL 1.5 required for VBO operations.";

    public GLBufferOps11() {
        System.err.println("[GLBufferOps11] WARNING: GL 1.1 pipeline selected. VBOs not available!");
    }

    public int genBuffer() {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public int[] genBuffers(int count) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public void deleteBuffer(int buffer) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public void deleteBuffers(int[] buffers) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public void bindBuffer(int target, int buffer) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public void bufferData(int target, long size, int usage) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public void bufferData(int target, ByteBuffer data, int usage) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public void bufferData(int target, FloatBuffer data, int usage) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public void bufferData(int target, IntBuffer data, int usage) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public void bufferSubData(int target, long offset, ByteBuffer data) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public void bufferSubData(int target, long offset, FloatBuffer data) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public void bufferSubData(int target, long offset, IntBuffer data) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public ByteBuffer mapBuffer(int target, int access) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public boolean unmapBuffer(int target) {
        throw new UnsupportedOperationException(ERR_MSG);
    }

    public void shutdown() {}
}
