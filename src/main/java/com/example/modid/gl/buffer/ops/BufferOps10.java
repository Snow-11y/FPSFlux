package com.example.modid.gl.buffer.ops;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * GLBufferOps10 - OpenGL 1.0 Pipeline
 * 
 * OpenGL 1.0 does NOT support Vertex Buffer Objects.
 * All buffer operations throw UnsupportedOperationException.
 * 
 * This pipeline exists for completeness and proper error messaging.
 * Any hardware running GL 1.0 is 25+ years old and cannot run modern Minecraft.
 * 
 * F3 Display Color: Dark Gray (#555555) - Legacy/Unsupported
 */
public final class GLBufferOps10 {

    public static final int VERSION_CODE = 10;
    public static final int DISPLAY_COLOR = 0x555555;
    public static final String VERSION_NAME = "OpenGL 1.0 (No VBO)";

    private static final String ERR_MSG = "OpenGL 1.0 does not support Vertex Buffer Objects. " +
            "Minimum GL 1.5 required for VBO operations. Please update your graphics drivers.";

    public GLBufferOps10() {
        System.err.println("[GLBufferOps10] WARNING: GL 1.0 pipeline selected. VBOs not available!");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Required methods - all throw
    // ─────────────────────────────────────────────────────────────────────────────

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

    public void shutdown() {
        // Nothing to clean up
    }
}
