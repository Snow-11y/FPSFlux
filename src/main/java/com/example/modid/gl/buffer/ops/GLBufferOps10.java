package com.example.modid.gl.buffer.ops;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * OpenGL 1.0 Pipeline - NO VBO SUPPORT
 * 
 * Display Color: Dark Gray (#555555)
 */
public final class GLBufferOps10 {
    public static final int VERSION = 10;
    public static final int COLOR = 0x555555;
    public static final String NAME = "Snowy OpenGL 1.0";
    
    private static final UnsupportedOperationException NO_VBO = 
        new UnsupportedOperationException("GL 1.0: No VBO support. Requires GL 1.5+");

    public int genBuffer() { throw NO_VBO; }
    public int[] genBuffers(int n) { throw NO_VBO; }
    public void deleteBuffer(int b) { throw NO_VBO; }
    public void deleteBuffers(int[] b) { throw NO_VBO; }
    public void bindBuffer(int t, int b) { throw NO_VBO; }
    public void bufferData(int t, long s, int u) { throw NO_VBO; }
    public void bufferData(int t, ByteBuffer d, int u) { throw NO_VBO; }
    public void bufferData(int t, FloatBuffer d, int u) { throw NO_VBO; }
    public void bufferData(int t, IntBuffer d, int u) { throw NO_VBO; }
    public void bufferSubData(int t, long o, ByteBuffer d) { throw NO_VBO; }
    public void bufferSubData(int t, long o, FloatBuffer d) { throw NO_VBO; }
    public void bufferSubData(int t, long o, IntBuffer d) { throw NO_VBO; }
    public ByteBuffer mapBuffer(int t, int a) { throw NO_VBO; }
    public ByteBuffer mapBuffer(int t, int a, long l) { throw NO_VBO; }
    public boolean unmapBuffer(int t) { throw NO_VBO; }
    public int getBufferParameteri(int t, int p) { throw NO_VBO; }
    public void shutdown() {}
}
