package com.example.modid.gl;

import java.nio.ByteBuffer;

/**
 * GPUBackend - Unified GPU abstraction interface.
 * 
 * <p>Provides a common API that works with both OpenGL and Vulkan backends.</p>
 * <p>The ECS, Render Graph, and GPU-Driven systems use this interface.</p>
 */
public interface GPUBackend {
    
    // ========================================================================
    // BACKEND INFO
    // ========================================================================
    
    enum Type { OPENGL, VULKAN }
    
    Type getType();
    String getVersionString();
    boolean isInitialized();
    
    // ========================================================================
    // BUFFER OPERATIONS
    // ========================================================================
    
    /**
     * Buffer usage hints.
     */
    interface BufferUsage {
        int VERTEX = 1 << 0;
        int INDEX = 1 << 1;
        int UNIFORM = 1 << 2;
        int STORAGE = 1 << 3;
        int INDIRECT = 1 << 4;
        int TRANSFER_SRC = 1 << 5;
        int TRANSFER_DST = 1 << 6;
    }
    
    /**
     * Memory flags.
     */
    interface MemoryFlags {
        int DEVICE_LOCAL = 1 << 0;      // GPU-only, fastest
        int HOST_VISIBLE = 1 << 1;      // CPU can access
        int HOST_COHERENT = 1 << 2;     // No flush needed
        int HOST_CACHED = 1 << 3;       // Cached on CPU side
        int PERSISTENT = 1 << 4;        // Keep mapped permanently
    }
    
    /**
     * Create a buffer.
     */
    long createBuffer(long size, int usage, int memoryFlags);
    
    /**
     * Destroy a buffer.
     */
    void destroyBuffer(long buffer);
    
    /**
     * Upload data to buffer.
     */
    void bufferUpload(long buffer, long offset, ByteBuffer data);
    
    /**
     * Map buffer for CPU access.
     */
    ByteBuffer mapBuffer(long buffer, long offset, long size);
    
    /**
     * Unmap buffer.
     */
    void unmapBuffer(long buffer);
    
    /**
     * Get buffer device address (for bindless, Vulkan 1.2+ / GL with extensions).
     */
    long getBufferDeviceAddress(long buffer);
    
    // ========================================================================
    // TEXTURE OPERATIONS
    // ========================================================================
    
    /**
     * Texture format.
     */
    interface TextureFormat {
        int RGBA8 = 1;
        int RGBA16F = 2;
        int RGBA32F = 3;
        int DEPTH24_STENCIL8 = 4;
        int DEPTH32F = 5;
        int R32UI = 6;
        int RG32F = 7;
    }
    
    /**
     * Create a 2D texture.
     */
    long createTexture2D(int width, int height, int format, int mipLevels);
    
    /**
     * Destroy texture.
     */
    void destroyTexture(long texture);
    
    /**
     * Upload texture data.
     */
    void textureUpload(long texture, int level, int x, int y, int width, int height, ByteBuffer data);
    
    // ========================================================================
    // SHADER OPERATIONS
    // ========================================================================
    
    /**
     * Shader stage.
     */
    interface ShaderStage {
        int VERTEX = 1 << 0;
        int FRAGMENT = 1 << 1;
        int GEOMETRY = 1 << 2;
        int TESS_CONTROL = 1 << 3;
        int TESS_EVAL = 1 << 4;
        int COMPUTE = 1 << 5;
        int TASK = 1 << 6;      // Mesh shader
        int MESH = 1 << 7;      // Mesh shader
    }
    
    /**
     * Create shader from GLSL source (will be compiled to SPIR-V for Vulkan).
     */
    long createShader(int stage, String source);
    
    /**
     * Create shader from SPIR-V binary.
     */
    long createShaderFromSPIRV(int stage, ByteBuffer spirv);
    
    /**
     * Destroy shader.
     */
    void destroyShader(long shader);
    
    /**
     * Create shader program/pipeline.
     */
    long createProgram(long... shaders);
    
    /**
     * Destroy program.
     */
    void destroyProgram(long program);
    
    // ========================================================================
    // DRAW OPERATIONS
    // ========================================================================
    
    /**
     * Bind vertex buffer.
     */
    void bindVertexBuffer(int binding, long buffer, long offset, int stride);
    
    /**
     * Bind index buffer.
     */
    void bindIndexBuffer(long buffer, long offset, boolean is32Bit);
    
    /**
     * Bind program/pipeline.
     */
    void bindProgram(long program);
    
    /**
     * Draw indexed.
     */
    void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance);
    
    /**
     * Draw indexed indirect.
     */
    void drawIndexedIndirect(long buffer, long offset, int drawCount, int stride);
    
    /**
     * Draw indexed indirect with count (GPU-driven).
     */
    void drawIndexedIndirectCount(long commandBuffer, long commandOffset,
                                   long countBuffer, long countOffset,
                                   int maxDrawCount, int stride);
    
    // ========================================================================
    // COMPUTE OPERATIONS
    // ========================================================================
    
    /**
     * Bind compute program.
     */
    void bindComputeProgram(long program);
    
    /**
     * Dispatch compute.
     */
    void dispatchCompute(int groupsX, int groupsY, int groupsZ);
    
    /**
     * Dispatch compute indirect.
     */
    void dispatchComputeIndirect(long buffer, long offset);
    
    // ========================================================================
    // SYNCHRONIZATION
    // ========================================================================
    
    /**
     * Memory barrier types.
     */
    interface BarrierType {
        int VERTEX_ATTRIB = 1 << 0;
        int INDEX_READ = 1 << 1;
        int UNIFORM_READ = 1 << 2;
        int TEXTURE_FETCH = 1 << 3;
        int SHADER_IMAGE = 1 << 4;
        int INDIRECT_COMMAND = 1 << 5;
        int BUFFER_UPDATE = 1 << 6;
        int SHADER_STORAGE = 1 << 7;
        int ALL = 0xFFFFFFFF;
    }
    
    /**
     * Insert memory barrier.
     */
    void memoryBarrier(int barrierBits);
    
    /**
     * Wait for GPU to finish all work.
     */
    void finish();
    
    // ========================================================================
    // RENDER PASS (for Render Graph)
    // ========================================================================
    
    /**
     * Begin render pass / dynamic rendering.
     */
    void beginRenderPass(RenderPassInfo info);
    
    /**
     * End render pass.
     */
    void endRenderPass();
    
    /**
     * Render pass description.
     */
    class RenderPassInfo {
        public String name;
        public long[] colorAttachments;
        public long depthAttachment;
        public int x, y, width, height;
        public float[] clearColor;
        public float clearDepth;
        public int clearStencil;
        public boolean clearOnLoad;
        public boolean storeResults;
    }
    
    // ========================================================================
    // FRAME MANAGEMENT
    // ========================================================================
    
    void beginFrame();
    void endFrame();
    int getCurrentFrameIndex();
    
    // ========================================================================
    // CAPABILITIES
    // ========================================================================
    
    boolean supportsMultiDrawIndirect();
    boolean supportsIndirectCount();
    boolean supportsComputeShaders();
    boolean supportsMeshShaders();
    boolean supportsBufferDeviceAddress();
    boolean supportsPersistentMapping();
    int getMaxComputeWorkGroupSize();
    int getMaxDrawIndirectCount();
}
