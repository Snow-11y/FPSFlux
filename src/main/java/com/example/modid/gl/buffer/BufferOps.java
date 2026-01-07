package com.example.modid.gl.buffer.ops;

import java.nio.ByteBuffer;

public interface BufferOps {
    int createBuffer(long size, int usage);
    void uploadData(int buffer, long offset, ByteBuffer data);
    int resizeBuffer(int oldBuffer, long oldSize, long newSize, int usage);
    ByteBuffer mapBuffer(int buffer, long size, int access);
    void unmapBuffer(int buffer);
    void deleteBuffer(int buffer);
    String getName();
}
