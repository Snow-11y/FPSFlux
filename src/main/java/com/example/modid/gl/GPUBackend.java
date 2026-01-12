package com.example.modid.gl;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * GPUBackend - Unified GPU abstraction interface with modern Java 21+ features.
 *
 * <p>Provides a common API that works with both OpenGL and Vulkan backends,
 * enabling the ECS, Render Graph, and GPU-Driven rendering systems to be
 * backend-agnostic.</p>
 *
 * <p>Core Features:</p>
 * <ul>
 *   <li>Unified buffer, texture, and shader management</li>
 *   <li>GPU-driven rendering with indirect draws</li>
 *   <li>Compute shader dispatch</li>
 *   <li>Render pass abstraction</li>
 *   <li>Memory barrier synchronization</li>
 *   <li>Capability querying</li>
 *   <li>Resource lifecycle management</li>
 *   <li>Profiling and debug support</li>
 *   <li>Async operation support</li>
 * </ul>
 *
 * @author Enhanced GPU Framework
 * @version 2.0.0
 * @since Java 21
 */
public interface GPUBackend extends AutoCloseable {

    // ========================================================================
    // BACKEND INFO
    // ========================================================================

    /**
     * Backend type.
     */
    enum Type {
        OPENGL("OpenGL", true, false),
        VULKAN("Vulkan", true, true),
        METAL("Metal", false, true),
        DIRECTX12("DirectX 12", false, true),
        WEBGPU("WebGPU", true, true);

        private final String displayName;
        private final boolean crossPlatform;
        private final boolean modernApi;

        Type(String displayName, boolean crossPlatform, boolean modernApi) {
            this.displayName = displayName;
            this.crossPlatform = crossPlatform;
            this.modernApi = modernApi;
        }

        public String displayName() { return displayName; }
        public boolean isCrossPlatform() { return crossPlatform; }
        public boolean isModernApi() { return modernApi; }
    }

    /**
     * Get backend type.
     */
    Type getType();

    /**
     * Get version string (e.g., "OpenGL 4.6" or "Vulkan 1.3").
     */
    String getVersionString();

    /**
     * Check if backend is initialized and ready.
     */
    boolean isInitialized();

    /**
     * Get backend info record.
     */
    default BackendInfo getBackendInfo() {
        return new BackendInfo(
            getType(),
            getVersionString(),
            isInitialized(),
            getCapabilities()
        );
    }

    /**
     * Backend information record.
     */
    record BackendInfo(
        Type type,
        String version,
        boolean initialized,
        Capabilities capabilities
    ) {}

    // ========================================================================
    // BUFFER OPERATIONS
    // ========================================================================

    /**
     * Buffer usage flags - can be combined with bitwise OR.
     */
    interface BufferUsage {
        int VERTEX          = 1 << 0;   // Vertex buffer
        int INDEX           = 1 << 1;   // Index buffer
        int UNIFORM         = 1 << 2;   // Uniform/constant buffer
        int STORAGE         = 1 << 3;   // Shader storage buffer (SSBO)
        int INDIRECT        = 1 << 4;   // Indirect draw/dispatch commands
        int TRANSFER_SRC    = 1 << 5;   // Source for copy operations
        int TRANSFER_DST    = 1 << 6;   // Destination for copy operations
        int QUERY_RESULT    = 1 << 7;   // Query result buffer
        int ACCELERATION    = 1 << 8;   // Acceleration structure (ray tracing)
        int SHADER_BINDING  = 1 << 9;   // Shader binding table (ray tracing)
        int CONDITIONAL     = 1 << 10;  // Conditional rendering
        int TRANSFORM_FEEDBACK = 1 << 11; // Transform feedback

        // Common combinations
        int VERTEX_INDEX = VERTEX | INDEX;
        int STAGING = TRANSFER_SRC | TRANSFER_DST;
        int GPU_ONLY = STORAGE | INDIRECT;
    }

    /**
     * Memory property flags - can be combined with bitwise OR.
     */
    interface MemoryFlags {
        int DEVICE_LOCAL    = 1 << 0;   // GPU-only memory, fastest for GPU access
        int HOST_VISIBLE    = 1 << 1;   // CPU can read/write
        int HOST_COHERENT   = 1 << 2;   // No explicit flush/invalidate needed
        int HOST_CACHED     = 1 << 3;   // Cached on CPU side (faster reads)
        int PERSISTENT      = 1 << 4;   // Keep mapped permanently
        int LAZILY_ALLOCATED = 1 << 5;  // Memory allocated on first use
        int PROTECTED       = 1 << 6;   // Protected/secure memory
        int TRANSIENT       = 1 << 7;   // Short-lived, streaming data

        // Common combinations
        int GPU_OPTIMAL = DEVICE_LOCAL;
        int CPU_TO_GPU = HOST_VISIBLE | HOST_COHERENT;
        int GPU_TO_CPU = HOST_VISIBLE | HOST_CACHED;
        int STAGING = HOST_VISIBLE | HOST_COHERENT | TRANSIENT;
        int PERSISTENT_MAPPED = HOST_VISIBLE | HOST_COHERENT | PERSISTENT;
    }

    /**
     * Create a GPU buffer.
     *
     * @param size        Size in bytes
     * @param usage       Usage flags from {@link BufferUsage}
     * @param memoryFlags Memory property flags from {@link MemoryFlags}
     * @return Buffer handle (0 on failure)
     */
    long createBuffer(long size, int usage, int memoryFlags);

    /**
     * Create a buffer with debug name.
     */
    default long createBuffer(long size, int usage, int memoryFlags, String debugName) {
        return createBuffer(size, usage, memoryFlags);
    }

    /**
     * Destroy a buffer and free its memory.
     */
    void destroyBuffer(long buffer);

    /**
     * Upload data to buffer from ByteBuffer.
     *
     * @param buffer Buffer handle
     * @param offset Offset in buffer (bytes)
     * @param data   Data to upload
     */
    void bufferUpload(long buffer, long offset, ByteBuffer data);

    /**
     * Upload data to buffer from MemorySegment.
     */
    default void bufferUpload(long buffer, long offset, MemorySegment data) {
        bufferUpload(buffer, offset, data.asByteBuffer());
    }

    /**
     * Upload data asynchronously.
     */
    default CompletableFuture<Void> bufferUploadAsync(long buffer, long offset, ByteBuffer data) {
        return CompletableFuture.runAsync(() -> bufferUpload(buffer, offset, data));
    }

    /**
     * Map buffer for CPU access.
     *
     * @param buffer Buffer handle
     * @param offset Offset to start mapping
     * @param size   Size to map
     * @return Mapped ByteBuffer, or null on failure
     */
    ByteBuffer mapBuffer(long buffer, long offset, long size);

    /**
     * Map buffer as MemorySegment.
     */
    default MemorySegment mapBufferSegment(long buffer, long offset, long size) {
        ByteBuffer mapped = mapBuffer(buffer, offset, size);
        return mapped != null ? MemorySegment.ofBuffer(mapped) : null;
    }

    /**
     * Unmap a previously mapped buffer.
     */
    void unmapBuffer(long buffer);

    /**
     * Get buffer device address for bindless access.
     * Requires {@link Capabilities#bufferDeviceAddress()}.
     *
     * @return Device address, or 0 if not supported
     */
    long getBufferDeviceAddress(long buffer);

    /**
     * Copy data between buffers (GPU-side).
     */
    default void copyBuffer(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {
        // Default implementation via map/unmap - backends should override
        ByteBuffer src = mapBuffer(srcBuffer, srcOffset, size);
        if (src != null) {
            bufferUpload(dstBuffer, dstOffset, src);
            unmapBuffer(srcBuffer);
        }
    }

    /**
     * Fill buffer with a value.
     */
    default void fillBuffer(long buffer, long offset, long size, int value) {
        // Default implementation - backends should override for efficiency
        ByteBuffer data = ByteBuffer.allocate((int) size);
        while (data.hasRemaining()) {
            data.putInt(value);
        }
        data.flip();
        bufferUpload(buffer, offset, data);
    }

    // ========================================================================
    // TEXTURE OPERATIONS
    // ========================================================================

    /**
     * Texture formats.
     */
    interface TextureFormat {
        // Unsigned normalized
        int R8              = 1;
        int RG8             = 2;
        int RGB8            = 3;
        int RGBA8           = 4;
        int SRGB8_ALPHA8    = 5;

        // Signed normalized
        int R8_SNORM        = 10;
        int RG8_SNORM       = 11;
        int RGBA8_SNORM     = 12;

        // Unsigned integer
        int R8UI            = 20;
        int RG8UI           = 21;
        int RGBA8UI         = 22;
        int R16UI           = 23;
        int RG16UI          = 24;
        int RGBA16UI        = 25;
        int R32UI           = 26;
        int RG32UI          = 27;
        int RGBA32UI        = 28;

        // Signed integer
        int R8I             = 30;
        int RG8I            = 31;
        int RGBA8I          = 32;
        int R16I            = 33;
        int R32I            = 34;
        int RGBA32I         = 35;

        // Float
        int R16F            = 40;
        int RG16F           = 41;
        int RGBA16F         = 42;
        int R32F            = 43;
        int RG32F           = 44;
        int RGB32F          = 45;
        int RGBA32F         = 46;
        int R11F_G11F_B10F  = 47;

        // Depth/stencil
        int DEPTH16         = 50;
        int DEPTH24         = 51;
        int DEPTH32F        = 52;
        int DEPTH24_STENCIL8 = 53;
        int DEPTH32F_STENCIL8 = 54;
        int STENCIL8        = 55;

        // Compressed
        int BC1_RGB         = 60;  // DXT1
        int BC1_RGBA        = 61;
        int BC2_RGBA        = 62;  // DXT3
        int BC3_RGBA        = 63;  // DXT5
        int BC4_R           = 64;
        int BC5_RG          = 65;
        int BC6H_RGB        = 66;  // HDR
        int BC7_RGBA        = 67;
        int ETC2_RGB8       = 70;
        int ETC2_RGBA8      = 71;
        int ASTC_4x4        = 80;
        int ASTC_8x8        = 81;
    }

    /**
     * Texture types.
     */
    enum TextureType {
        TEXTURE_1D,
        TEXTURE_2D,
        TEXTURE_3D,
        TEXTURE_CUBE,
        TEXTURE_1D_ARRAY,
        TEXTURE_2D_ARRAY,
        TEXTURE_CUBE_ARRAY,
        TEXTURE_2D_MULTISAMPLE,
        TEXTURE_2D_MULTISAMPLE_ARRAY
    }

    /**
     * Sampler filter modes.
     */
    enum Filter {
        NEAREST,
        LINEAR,
        NEAREST_MIPMAP_NEAREST,
        LINEAR_MIPMAP_NEAREST,
        NEAREST_MIPMAP_LINEAR,
        LINEAR_MIPMAP_LINEAR
    }

    /**
     * Sampler address modes.
     */
    enum AddressMode {
        REPEAT,
        MIRRORED_REPEAT,
        CLAMP_TO_EDGE,
        CLAMP_TO_BORDER,
        MIRROR_CLAMP_TO_EDGE
    }

    /**
     * Create a 2D texture.
     *
     * @param width     Width in pixels
     * @param height    Height in pixels
     * @param format    Texture format from {@link TextureFormat}
     * @param mipLevels Number of mip levels (1 for no mipmaps)
     * @return Texture handle (0 on failure)
     */
    long createTexture2D(int width, int height, int format, int mipLevels);

    /**
     * Create a 2D texture with debug name.
     */
    default long createTexture2D(int width, int height, int format, int mipLevels, String debugName) {
        return createTexture2D(width, height, format, mipLevels);
    }

    /**
     * Create texture with full options.
     */
    default long createTexture(TextureInfo info) {
        // Default implementation for 2D textures
        if (info.type() == TextureType.TEXTURE_2D) {
            return createTexture2D(info.width(), info.height(), info.format(), info.mipLevels());
        }
        throw new UnsupportedOperationException("Texture type not supported: " + info.type());
    }

    /**
     * Destroy a texture and free its memory.
     */
    void destroyTexture(long texture);

    /**
     * Upload data to a texture region.
     *
     * @param texture Texture handle
     * @param level   Mip level
     * @param x       X offset
     * @param y       Y offset
     * @param width   Width of region
     * @param height  Height of region
     * @param data    Pixel data
     */
    void textureUpload(long texture, int level, int x, int y, int width, int height, ByteBuffer data);

    /**
     * Upload asynchronously.
     */
    default CompletableFuture<Void> textureUploadAsync(
            long texture, int level, int x, int y, int width, int height, ByteBuffer data) {
        return CompletableFuture.runAsync(() -> textureUpload(texture, level, x, y, width, height, data));
    }

    /**
     * Generate mipmaps for texture.
     */
    default void generateMipmaps(long texture) {
        // Backend-specific implementation
    }

    /**
     * Create a sampler.
     */
    default long createSampler(SamplerInfo info) {
        return 0; // Backend-specific
    }

    /**
     * Destroy a sampler.
     */
    default void destroySampler(long sampler) {
        // Backend-specific
    }

    /**
     * Texture creation info.
     */
    record TextureInfo(
        TextureType type,
        int width,
        int height,
        int depth,
        int format,
        int mipLevels,
        int arrayLayers,
        int samples,
        int usage,
        String debugName
    ) {
        public static TextureInfo texture2D(int width, int height, int format, int mipLevels) {
            return new TextureInfo(TextureType.TEXTURE_2D, width, height, 1, format, mipLevels, 1, 1, 0, null);
        }

        public static TextureInfo texture2D(int width, int height, int format, int mipLevels, String debugName) {
            return new TextureInfo(TextureType.TEXTURE_2D, width, height, 1, format, mipLevels, 1, 1, 0, debugName);
        }

        public static TextureInfo texture3D(int width, int height, int depth, int format, int mipLevels) {
            return new TextureInfo(TextureType.TEXTURE_3D, width, height, depth, format, mipLevels, 1, 1, 0, null);
        }

        public static TextureInfo textureCube(int size, int format, int mipLevels) {
            return new TextureInfo(TextureType.TEXTURE_CUBE, size, size, 1, format, mipLevels, 6, 1, 0, null);
        }

        public static TextureInfo texture2DArray(int width, int height, int layers, int format, int mipLevels) {
            return new TextureInfo(TextureType.TEXTURE_2D_ARRAY, width, height, 1, format, mipLevels, layers, 1, 0, null);
        }

        public static TextureInfo texture2DMS(int width, int height, int format, int samples) {
            return new TextureInfo(TextureType.TEXTURE_2D_MULTISAMPLE, width, height, 1, format, 1, 1, samples, 0, null);
        }
    }

    /**
     * Sampler creation info.
     */
    record SamplerInfo(
        Filter minFilter,
        Filter magFilter,
        Filter mipFilter,
        AddressMode addressU,
        AddressMode addressV,
        AddressMode addressW,
        float mipLodBias,
        float maxAnisotropy,
        boolean compareEnable,
        CompareOp compareOp,
        float minLod,
        float maxLod,
        float[] borderColor
    ) {
        public static SamplerInfo linear() {
            return new SamplerInfo(
                Filter.LINEAR, Filter.LINEAR, Filter.LINEAR_MIPMAP_LINEAR,
                AddressMode.REPEAT, AddressMode.REPEAT, AddressMode.REPEAT,
                0, 1, false, CompareOp.NEVER, 0, 1000, null
            );
        }

        public static SamplerInfo nearest() {
            return new SamplerInfo(
                Filter.NEAREST, Filter.NEAREST, Filter.NEAREST_MIPMAP_NEAREST,
                AddressMode.REPEAT, AddressMode.REPEAT, AddressMode.REPEAT,
                0, 1, false, CompareOp.NEVER, 0, 1000, null
            );
        }

        public static SamplerInfo anisotropic(float maxAnisotropy) {
            return new SamplerInfo(
                Filter.LINEAR, Filter.LINEAR, Filter.LINEAR_MIPMAP_LINEAR,
                AddressMode.REPEAT, AddressMode.REPEAT, AddressMode.REPEAT,
                0, maxAnisotropy, false, CompareOp.NEVER, 0, 1000, null
            );
        }

        public static SamplerInfo shadow() {
            return new SamplerInfo(
                Filter.LINEAR, Filter.LINEAR, Filter.NEAREST,
                AddressMode.CLAMP_TO_BORDER, AddressMode.CLAMP_TO_BORDER, AddressMode.CLAMP_TO_BORDER,
                0, 1, true, CompareOp.LESS_OR_EQUAL, 0, 1000, new float[]{1, 1, 1, 1}
            );
        }
    }

    // ========================================================================
    // SHADER OPERATIONS
    // ========================================================================

    /**
     * Shader stage flags.
     */
    interface ShaderStage {
        int VERTEX          = 1 << 0;
        int FRAGMENT        = 1 << 1;
        int GEOMETRY        = 1 << 2;
        int TESS_CONTROL    = 1 << 3;
        int TESS_EVAL       = 1 << 4;
        int COMPUTE         = 1 << 5;
        int TASK            = 1 << 6;   // Mesh shading
        int MESH            = 1 << 7;   // Mesh shading
        int RAYGEN          = 1 << 8;   // Ray tracing
        int ANY_HIT         = 1 << 9;   // Ray tracing
        int CLOSEST_HIT     = 1 << 10;  // Ray tracing
        int MISS            = 1 << 11;  // Ray tracing
        int INTERSECTION    = 1 << 12;  // Ray tracing
        int CALLABLE        = 1 << 13;  // Ray tracing

        int ALL_GRAPHICS = VERTEX | FRAGMENT | GEOMETRY | TESS_CONTROL | TESS_EVAL;
        int ALL_MESH = TASK | MESH | FRAGMENT;
        int ALL_RAY_TRACING = RAYGEN | ANY_HIT | CLOSEST_HIT | MISS | INTERSECTION | CALLABLE;
    }

    /**
     * Create shader from GLSL source.
     *
     * @param stage  Shader stage from {@link ShaderStage}
     * @param source GLSL source code
     * @return Shader handle (0 on failure)
     */
    long createShader(int stage, String source);

    /**
     * Create shader with debug name.
     */
    default long createShader(int stage, String source, String debugName) {
        return createShader(stage, source);
    }

    /**
     * Create shader from SPIR-V binary.
     *
     * @param stage Shader stage
     * @param spirv SPIR-V bytecode
     * @return Shader handle (0 on failure)
     */
    long createShaderFromSPIRV(int stage, ByteBuffer spirv);

    /**
     * Create shader from SPIR-V with specialization constants.
     */
    default long createShaderFromSPIRV(int stage, ByteBuffer spirv, String entryPoint,
                                       Map<Integer, Object> specializationConstants) {
        return createShaderFromSPIRV(stage, spirv);
    }

    /**
     * Destroy a shader module.
     */
    void destroyShader(long shader);

    /**
     * Create shader program/pipeline from shader modules.
     *
     * @param shaders Shader handles to link together
     * @return Program handle (0 on failure)
     */
    long createProgram(long... shaders);

    /**
     * Create program with debug name.
     */
    default long createProgram(String debugName, long... shaders) {
        return createProgram(shaders);
    }

    /**
     * Destroy a shader program/pipeline.
     */
    void destroyProgram(long program);

    /**
     * Get uniform/descriptor location.
     */
    default int getUniformLocation(long program, String name) {
        return -1;
    }

    /**
     * Set uniform value (for OpenGL compatibility).
     */
    default void setUniform(long program, int location, float value) {}
    default void setUniform(long program, int location, int value) {}
    default void setUniform(long program, int location, float x, float y, float z) {}
    default void setUniform(long program, int location, float x, float y, float z, float w) {}
    default void setUniformMatrix4(long program, int location, boolean transpose, FloatBuffer matrix) {}

    // ========================================================================
    // PIPELINE STATE
    // ========================================================================

    /**
     * Comparison operators.
     */
    enum CompareOp {
        NEVER, LESS, EQUAL, LESS_OR_EQUAL,
        GREATER, NOT_EQUAL, GREATER_OR_EQUAL, ALWAYS
    }

    /**
     * Blend factors.
     */
    enum BlendFactor {
        ZERO, ONE,
        SRC_COLOR, ONE_MINUS_SRC_COLOR,
        DST_COLOR, ONE_MINUS_DST_COLOR,
        SRC_ALPHA, ONE_MINUS_SRC_ALPHA,
        DST_ALPHA, ONE_MINUS_DST_ALPHA,
        CONSTANT_COLOR, ONE_MINUS_CONSTANT_COLOR,
        CONSTANT_ALPHA, ONE_MINUS_CONSTANT_ALPHA,
        SRC_ALPHA_SATURATE,
        SRC1_COLOR, ONE_MINUS_SRC1_COLOR,
        SRC1_ALPHA, ONE_MINUS_SRC1_ALPHA
    }

    /**
     * Blend operations.
     */
    enum BlendOp {
        ADD, SUBTRACT, REVERSE_SUBTRACT, MIN, MAX
    }

    /**
     * Cull modes.
     */
    enum CullMode {
        NONE, FRONT, BACK, FRONT_AND_BACK
    }

    /**
     * Front face winding.
     */
    enum FrontFace {
        COUNTER_CLOCKWISE, CLOCKWISE
    }

    /**
     * Polygon modes.
     */
    enum PolygonMode {
        FILL, LINE, POINT
    }

    /**
     * Primitive topology.
     */
    enum Topology {
        POINT_LIST,
        LINE_LIST,
        LINE_STRIP,
        TRIANGLE_LIST,
        TRIANGLE_STRIP,
        TRIANGLE_FAN,
        LINE_LIST_WITH_ADJACENCY,
        LINE_STRIP_WITH_ADJACENCY,
        TRIANGLE_LIST_WITH_ADJACENCY,
        TRIANGLE_STRIP_WITH_ADJACENCY,
        PATCH_LIST
    }

    /**
     * Set depth test state.
     */
    default void setDepthTest(boolean enable, boolean writeEnable, CompareOp compareOp) {}

    /**
     * Set blend state.
     */
    default void setBlendState(boolean enable, BlendFactor srcFactor, BlendFactor dstFactor, BlendOp op) {}

    /**
     * Set cull mode.
     */
    default void setCullMode(CullMode mode, FrontFace frontFace) {}

    /**
     * Set polygon mode.
     */
    default void setPolygonMode(PolygonMode mode) {}

    /**
     * Set scissor rect.
     */
    default void setScissor(int x, int y, int width, int height) {}

    /**
     * Set viewport.
     */
    default void setViewport(int x, int y, int width, int height, float minDepth, float maxDepth) {}

    /**
     * Set line width.
     */
    default void setLineWidth(float width) {}

    // ========================================================================
    // DRAW OPERATIONS
    // ========================================================================

    /**
     * Bind vertex buffer to binding slot.
     *
     * @param binding Buffer binding index
     * @param buffer  Buffer handle
     * @param offset  Offset in buffer
     * @param stride  Vertex stride in bytes
     */
    void bindVertexBuffer(int binding, long buffer, long offset, int stride);

    /**
     * Bind multiple vertex buffers.
     */
    default void bindVertexBuffers(int firstBinding, long[] buffers, long[] offsets, int[] strides) {
        for (int i = 0; i < buffers.length; i++) {
            bindVertexBuffer(firstBinding + i, buffers[i], offsets[i], strides[i]);
        }
    }

    /**
     * Bind index buffer.
     *
     * @param buffer  Buffer handle
     * @param offset  Offset in buffer
     * @param is32Bit true for 32-bit indices, false for 16-bit
     */
    void bindIndexBuffer(long buffer, long offset, boolean is32Bit);

    /**
     * Bind shader program/pipeline.
     */
    void bindProgram(long program);

    /**
     * Draw indexed primitives.
     *
     * @param indexCount    Number of indices
     * @param instanceCount Number of instances
     * @param firstIndex    First index offset
     * @param vertexOffset  Added to each index
     * @param firstInstance First instance ID
     */
    void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance);

    /**
     * Draw indexed primitives (single instance).
     */
    default void drawIndexed(int indexCount, int firstIndex, int vertexOffset) {
        drawIndexed(indexCount, 1, firstIndex, vertexOffset, 0);
    }

    /**
     * Draw non-indexed primitives.
     */
    default void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        // Backend-specific implementation
    }

    /**
     * Draw non-indexed primitives (single instance).
     */
    default void draw(int vertexCount, int firstVertex) {
        draw(vertexCount, 1, firstVertex, 0);
    }

    /**
     * Draw indexed indirect (multiple draws from buffer).
     *
     * @param buffer    Command buffer handle
     * @param offset    Offset in command buffer
     * @param drawCount Number of draws
     * @param stride    Stride between commands
     */
    void drawIndexedIndirect(long buffer, long offset, int drawCount, int stride);

    /**
     * Draw indexed indirect with GPU-determined count.
     * Requires {@link Capabilities#indirectCount()}.
     *
     * @param commandBuffer Buffer containing draw commands
     * @param commandOffset Offset to first command
     * @param countBuffer   Buffer containing draw count
     * @param countOffset   Offset to count value
     * @param maxDrawCount  Maximum draws (for bounds checking)
     * @param stride        Stride between commands
     */
    void drawIndexedIndirectCount(long commandBuffer, long commandOffset,
                                   long countBuffer, long countOffset,
                                   int maxDrawCount, int stride);

    /**
     * Draw mesh tasks (mesh shader path).
     * Requires {@link Capabilities#meshShaders()}.
     */
    default void drawMeshTasks(int groupCountX, int groupCountY, int groupCountZ) {
        throw new UnsupportedOperationException("Mesh shaders not supported");
    }

    /**
     * Draw mesh tasks indirect.
     */
    default void drawMeshTasksIndirect(long buffer, long offset, int drawCount, int stride) {
        throw new UnsupportedOperationException("Mesh shaders not supported");
    }

    /**
     * Indirect draw command structure (matches VkDrawIndexedIndirectCommand).
     */
    record DrawIndexedIndirectCommand(
        int indexCount,
        int instanceCount,
        int firstIndex,
        int vertexOffset,
        int firstInstance
    ) {
        public static final int SIZE_BYTES = 20;

        public void writeTo(ByteBuffer buffer) {
            buffer.putInt(indexCount);
            buffer.putInt(instanceCount);
            buffer.putInt(firstIndex);
            buffer.putInt(vertexOffset);
            buffer.putInt(firstInstance);
        }
    }

    // ========================================================================
    // COMPUTE OPERATIONS
    // ========================================================================

    /**
     * Bind compute program/pipeline.
     */
    void bindComputeProgram(long program);

    /**
     * Dispatch compute shader.
     *
     * @param groupsX Number of work groups in X
     * @param groupsY Number of work groups in Y
     * @param groupsZ Number of work groups in Z
     */
    void dispatchCompute(int groupsX, int groupsY, int groupsZ);

    /**
     * Dispatch compute with automatic group calculation.
     */
    default void dispatchCompute(int totalX, int totalY, int totalZ, int localX, int localY, int localZ) {
        int groupsX = (totalX + localX - 1) / localX;
        int groupsY = (totalY + localY - 1) / localY;
        int groupsZ = (totalZ + localZ - 1) / localZ;
        dispatchCompute(groupsX, groupsY, groupsZ);
    }

    /**
     * Dispatch compute indirect (command from buffer).
     *
     * @param buffer Indirect command buffer
     * @param offset Offset in buffer
     */
    void dispatchComputeIndirect(long buffer, long offset);

    /**
     * Dispatch compute indirect command structure.
     */
    record DispatchIndirectCommand(int groupCountX, int groupCountY, int groupCountZ) {
        public static final int SIZE_BYTES = 12;

        public void writeTo(ByteBuffer buffer) {
            buffer.putInt(groupCountX);
            buffer.putInt(groupCountY);
            buffer.putInt(groupCountZ);
        }
    }

    // ========================================================================
    // DESCRIPTOR / BINDING OPERATIONS
    // ========================================================================

    /**
     * Bind uniform buffer.
     */
    default void bindUniformBuffer(int binding, long buffer, long offset, long size) {}

    /**
     * Bind shader storage buffer.
     */
    default void bindStorageBuffer(int binding, long buffer, long offset, long size) {}

    /**
     * Bind texture/sampler.
     */
    default void bindTexture(int binding, long texture, long sampler) {}

    /**
     * Bind image for compute access.
     */
    default void bindImage(int binding, long texture, int level, boolean layered, int layer, int access, int format) {}

    /**
     * Push constants/uniforms.
     */
    default void pushConstants(int stageFlags, int offset, ByteBuffer data) {}

    /**
     * Push constants from MemorySegment.
     */
    default void pushConstants(int stageFlags, int offset, MemorySegment data) {
        pushConstants(stageFlags, offset, data.asByteBuffer());
    }

    // ========================================================================
    // SYNCHRONIZATION
    // ========================================================================

    /**
     * Memory barrier type flags.
     */
    interface BarrierType {
        int VERTEX_ATTRIB       = 1 << 0;   // Vertex attribute reads
        int INDEX_READ          = 1 << 1;   // Index buffer reads
        int UNIFORM_READ        = 1 << 2;   // Uniform buffer reads
        int TEXTURE_FETCH       = 1 << 3;   // Texture sampling
        int SHADER_IMAGE        = 1 << 4;   // Image load/store
        int INDIRECT_COMMAND    = 1 << 5;   // Indirect draw/dispatch reads
        int BUFFER_UPDATE       = 1 << 6;   // Buffer update operations
        int SHADER_STORAGE      = 1 << 7;   // SSBO access
        int FRAMEBUFFER         = 1 << 8;   // Framebuffer read/write
        int TRANSFORM_FEEDBACK  = 1 << 9;   // Transform feedback writes
        int ATOMIC_COUNTER      = 1 << 10;  // Atomic counter access
        int PIXEL_BUFFER        = 1 << 11;  // Pixel buffer operations
        int TEXTURE_UPDATE      = 1 << 12;  // Texture updates
        int CLIENT_MAPPED       = 1 << 13;  // Client-mapped buffer operations

        int ALL = 0xFFFFFFFF;

        // Common combinations
        int COMPUTE_TO_DRAW = VERTEX_ATTRIB | INDEX_READ | INDIRECT_COMMAND | SHADER_STORAGE;
        int COMPUTE_TO_COMPUTE = SHADER_STORAGE | UNIFORM_READ;
        int DRAW_TO_COMPUTE = SHADER_STORAGE | FRAMEBUFFER;
    }

    /**
     * Insert memory barrier.
     *
     * @param barrierBits Flags from {@link BarrierType}
     */
    void memoryBarrier(int barrierBits);

    /**
     * Barrier between compute and draw operations.
     */
    default void computeToDrawBarrier() {
        memoryBarrier(BarrierType.COMPUTE_TO_DRAW);
    }

    /**
     * Barrier between compute operations.
     */
    default void computeToComputeBarrier() {
        memoryBarrier(BarrierType.COMPUTE_TO_COMPUTE);
    }

    /**
     * Full barrier (all operations).
     */
    default void fullBarrier() {
        memoryBarrier(BarrierType.ALL);
    }

    /**
     * Wait for GPU to finish all pending work.
     */
    void finish();

    /**
     * Flush commands without waiting.
     */
    default void flush() {
        // Backend-specific
    }

    /**
     * Create a fence for synchronization.
     */
    default long createFence(boolean signaled) {
        return 0;
    }

    /**
     * Wait for fence to be signaled.
     */
    default boolean waitFence(long fence, long timeoutNanos) {
        return true;
    }

    /**
     * Reset fence to unsignaled state.
     */
    default void resetFence(long fence) {}

    /**
     * Destroy a fence.
     */
    default void destroyFence(long fence) {}

    /**
     * Create a semaphore.
     */
    default long createSemaphore() {
        return 0;
    }

    /**
     * Destroy a semaphore.
     */
    default void destroySemaphore(long semaphore) {}

    // ========================================================================
    // RENDER PASS
    // ========================================================================

    /**
     * Begin render pass / dynamic rendering.
     */
    void beginRenderPass(RenderPassInfo info);

    /**
     * End current render pass.
     */
    void endRenderPass();

    /**
     * Next subpass (for multi-subpass render passes).
     */
    default void nextSubpass() {}

    /**
     * Render pass information.
     */
    record RenderPassInfo(
        String name,
        long[] colorAttachments,
        long[] colorResolveAttachments,
        long depthAttachment,
        long depthResolveAttachment,
        int x,
        int y,
        int width,
        int height,
        float[] clearColor,
        float clearDepth,
        int clearStencil,
        boolean clearOnLoad,
        boolean storeResults,
        int layers,
        int viewMask
    ) {
        /**
         * Simple constructor for common case.
         */
        public RenderPassInfo(String name, int x, int y, int width, int height) {
            this(name, null, null, 0, 0, x, y, width, height,
                new float[]{0, 0, 0, 1}, 1.0f, 0, true, true, 1, 0);
        }

        /**
         * Builder for RenderPassInfo.
         */
        public static Builder builder(String name) {
            return new Builder(name);
        }

        public static final class Builder {
            private final String name;
            private long[] colorAttachments;
            private long[] colorResolveAttachments;
            private long depthAttachment;
            private long depthResolveAttachment;
            private int x, y, width, height;
            private float[] clearColor = {0, 0, 0, 1};
            private float clearDepth = 1.0f;
            private int clearStencil = 0;
            private boolean clearOnLoad = true;
            private boolean storeResults = true;
            private int layers = 1;
            private int viewMask = 0;

            private Builder(String name) { this.name = name; }

            public Builder colorAttachments(long... attachments) {
                this.colorAttachments = attachments;
                return this;
            }
            public Builder colorResolveAttachments(long... attachments) {
                this.colorResolveAttachments = attachments;
                return this;
            }
            public Builder depthAttachment(long attachment) {
                this.depthAttachment = attachment;
                return this;
            }
            public Builder depthResolveAttachment(long attachment) {
                this.depthResolveAttachment = attachment;
                return this;
            }
            public Builder area(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
                return this;
            }
            public Builder clearColor(float r, float g, float b, float a) {
                this.clearColor = new float[]{r, g, b, a};
                return this;
            }
            public Builder clearDepth(float depth) {
                this.clearDepth = depth;
                return this;
            }
            public Builder clearStencil(int stencil) {
                this.clearStencil = stencil;
                return this;
            }
            public Builder clearOnLoad(boolean clear) {
                this.clearOnLoad = clear;
                return this;
            }
            public Builder storeResults(boolean store) {
                this.storeResults = store;
                return this;
            }
            public Builder layers(int layers) {
                this.layers = layers;
                return this;
            }
            public Builder viewMask(int mask) {
                this.viewMask = mask;
                return this;
            }

            public RenderPassInfo build() {
                return new RenderPassInfo(
                    name, colorAttachments, colorResolveAttachments,
                    depthAttachment, depthResolveAttachment,
                    x, y, width, height, clearColor, clearDepth, clearStencil,
                    clearOnLoad, storeResults, layers, viewMask
                );
            }
        }
    }

    // ========================================================================
    // FRAME MANAGEMENT
    // ========================================================================

    /**
     * Begin a new frame. Call at start of rendering.
     */
    void beginFrame();

    /**
     * End current frame. Call after all rendering.
     */
    void endFrame();

    /**
     * Get current frame index (0 to framesInFlight-1).
     */
    int getCurrentFrameIndex();

    /**
     * Get total frames rendered.
     */
    default long getFrameCount() {
        return 0;
    }

    /**
     * Get frames per second (averaged).
     */
    default float getFPS() {
        return 0;
    }

    /**
     * Get frame time in milliseconds.
     */
    default float getFrameTimeMs() {
        return 0;
    }

    // ========================================================================
    // PROFILING & DEBUG
    // ========================================================================

    /**
     * Push debug group for GPU profiling.
     */
    default void pushDebugGroup(String name) {}

    /**
     * Push debug group with color.
     */
    default void pushDebugGroup(String name, int color) {
        pushDebugGroup(name);
    }

    /**
     * Pop debug group.
     */
    default void popDebugGroup() {}

    /**
     * Insert debug label/marker.
     */
    default void insertDebugLabel(String name) {}

    /**
     * Set debug name for object.
     */
    default void setDebugName(long handle, String name) {}

    /**
     * Begin GPU timer query.
     */
    default void beginTimerQuery(String name) {}

    /**
     * End GPU timer query.
     */
    default void endTimerQuery() {}

    /**
     * Get timer query results.
     */
    default List<TimerResult> getTimerResults() {
        return List.of();
    }

    /**
     * Timer query result.
     */
    record TimerResult(String name, double elapsedMs) {}

    /**
     * Scoped debug group (auto-closes).
     */
    default AutoCloseable debugScope(String name) {
        pushDebugGroup(name);
        return this::popDebugGroup;
    }

    /**
     * Execute action within debug group.
     */
    default void withDebugGroup(String name, Runnable action) {
        pushDebugGroup(name);
        try {
            action.run();
        } finally {
            popDebugGroup();
        }
    }

    // ========================================================================
    // CAPABILITIES
    // ========================================================================

    /**
     * Get backend capabilities.
     */
    default Capabilities getCapabilities() {
        return new Capabilities(
            supportsMultiDrawIndirect(),
            supportsIndirectCount(),
            supportsComputeShaders(),
            supportsMeshShaders(),
            supportsBufferDeviceAddress(),
            supportsPersistentMapping(),
            false, // ray tracing
            false, // bindless textures
            false, // sparse textures
            false, // sparse buffers
            false, // timeline semaphores
            getMaxComputeWorkGroupSize(),
            getMaxDrawIndirectCount(),
            0, 0, 0, 0
        );
    }

    /**
     * Backend capabilities record.
     */
    record Capabilities(
        boolean multiDrawIndirect,
        boolean indirectCount,
        boolean computeShaders,
        boolean meshShaders,
        boolean bufferDeviceAddress,
        boolean persistentMapping,
        boolean rayTracing,
        boolean bindlessTextures,
        boolean sparseTextures,
        boolean sparseBuffers,
        boolean timelineSemaphores,
        int maxComputeWorkGroupSize,
        int maxDrawIndirectCount,
        int maxTextureSize,
        int maxUniformBufferSize,
        int maxStorageBufferSize,
        long maxMemoryAllocation
    ) {
        public static final Capabilities EMPTY = new Capabilities(
            false, false, false, false, false, false, false, false, false, false, false,
            0, 0, 0, 0, 0, 0
        );

        /**
         * Check if GPU-driven rendering is fully supported.
         */
        public boolean supportsGpuDriven() {
            return multiDrawIndirect && indirectCount && computeShaders;
        }

        /**
         * Check if modern mesh shading is supported.
         */
        public boolean supportsMeshShading() {
            return meshShaders;
        }

        /**
         * Check if ray tracing is supported.
         */
        public boolean supportsRayTracing() {
            return rayTracing;
        }
    }

    boolean supportsMultiDrawIndirect();
    boolean supportsIndirectCount();
    boolean supportsComputeShaders();
    boolean supportsMeshShaders();
    boolean supportsBufferDeviceAddress();
    boolean supportsPersistentMapping();
    int getMaxComputeWorkGroupSize();
    int getMaxDrawIndirectCount();

    // ========================================================================
    // STATISTICS
    // ========================================================================

    /**
     * Get backend statistics.
     */
    default Statistics getStatistics() {
        return Statistics.EMPTY;
    }

    /**
     * Reset statistics counters.
     */
    default void resetStatistics() {}

    /**
     * Backend statistics.
     */
    record Statistics(
        long drawCalls,
        long dispatchCalls,
        long bufferUploads,
        long textureUploads,
        long pipelineBinds,
        long memoryBarriers,
        long totalBufferMemory,
        long totalTextureMemory,
        int bufferCount,
        int textureCount,
        int shaderCount,
        int pipelineCount,
        long frameCount,
        Duration uptime
    ) {
        public static final Statistics EMPTY = new Statistics(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Duration.ZERO
        );

        public String format() {
            return String.format("""
                GPU Backend Statistics:
                  Draw calls: %,d
                  Dispatch calls: %,d
                  Buffer uploads: %,d
                  Texture uploads: %,d
                  Pipeline binds: %,d
                  Memory barriers: %,d
                  Buffer memory: %.2f MB (%d buffers)
                  Texture memory: %.2f MB (%d textures)
                  Shaders: %d, Pipelines: %d
                  Frames: %,d
                  Uptime: %s
                """,
                drawCalls, dispatchCalls, bufferUploads, textureUploads,
                pipelineBinds, memoryBarriers,
                totalBufferMemory / (1024.0 * 1024.0), bufferCount,
                totalTextureMemory / (1024.0 * 1024.0), textureCount,
                shaderCount, pipelineCount, frameCount, uptime);
        }
    }

    // ========================================================================
    // MEMORY MANAGEMENT
    // ========================================================================

    /**
     * Get memory budget info.
     */
    default MemoryBudget getMemoryBudget() {
        return MemoryBudget.UNKNOWN;
    }

    /**
     * Memory budget information.
     */
    record MemoryBudget(
        long deviceLocalTotal,
        long deviceLocalUsed,
        long hostVisibleTotal,
        long hostVisibleUsed
    ) {
        public static final MemoryBudget UNKNOWN = new MemoryBudget(0, 0, 0, 0);

        public long deviceLocalAvailable() {
            return deviceLocalTotal - deviceLocalUsed;
        }

        public long hostVisibleAvailable() {
            return hostVisibleTotal - hostVisibleUsed;
        }

        public float deviceLocalUsagePercent() {
            return deviceLocalTotal > 0 ? (float) deviceLocalUsed / deviceLocalTotal * 100 : 0;
        }

        public float hostVisibleUsagePercent() {
            return hostVisibleTotal > 0 ? (float) hostVisibleUsed / hostVisibleTotal * 100 : 0;
        }
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    /**
     * Shutdown and cleanup resources.
     */
    @Override
    default void close() {
        // Backend-specific cleanup
    }

    // ========================================================================
    // FACTORY
    // ========================================================================

    /**
     * Get the active backend instance.
     */
    static GPUBackend get() {
        // Return current active backend (set by initialization)
        return GPUBackendRegistry.getActive();
    }

    /**
     * Create backend of specified type.
     */
    static GPUBackend create(Type type) {
        return switch (type) {
            case OPENGL -> OpenGLBackend.get();
            case VULKAN -> VulkanBackend.get();
            default -> throw new UnsupportedOperationException("Backend not supported: " + type);
        };
    }

    /**
     * Create best available backend.
     */
    static GPUBackend createBest() {
        // Prefer Vulkan if available
        try {
            GPUBackend vulkan = VulkanBackend.get();
            if (vulkan.isInitialized() || ((VulkanBackend) vulkan).initialize()) {
                return vulkan;
            }
        } catch (Exception ignored) {}

        // Fall back to OpenGL
        GPUBackend opengl = OpenGLBackend.get();
        if (opengl.isInitialized() || ((OpenGLBackend) opengl).initialize()) {
            return opengl;
        }

        throw new RuntimeException("No GPU backend available");
    }
}

/**
 * Registry for managing active GPU backend.
 */
final class GPUBackendRegistry {
    private static volatile GPUBackend active;

    static GPUBackend getActive() {
        if (active == null) {
            throw new IllegalStateException("No GPU backend initialized");
        }
        return active;
    }

    static void setActive(GPUBackend backend) {
        active = backend;
    }

    private GPUBackendRegistry() {}
}
