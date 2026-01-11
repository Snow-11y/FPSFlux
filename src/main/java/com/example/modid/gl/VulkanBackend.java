package com.example.modid.gl;

import com.example.modid.gl.mapping.VulkanCallMapperX;
import com.example.modid.gl.vulkan.VulkanContext;
import com.example.modid.gl.vulkan.VulkanManager;

import java.nio.ByteBuffer;

/**
 * VulkanBackend - GPUBackend implementation for Vulkan.
 * 
 * <p>Delegates to VulkanManager and VulkanCallMapperX.</p>
 */
public final class VulkanBackend implements GPUBackend {
    
    private static VulkanBackend INSTANCE;
    
    public static VulkanBackend get() {
        if (INSTANCE == null) {
            INSTANCE = new VulkanBackend();
        }
        return INSTANCE;
    }
    
    private VulkanBackend() {}
    
    /**
     * Initialize backend.
     */
    public boolean initialize() {
        return VulkanManager.init();
    }
    
    private VulkanManager mgr() {
        return VulkanManager.getFast();
    }
    
    @Override
    public Type getType() { return Type.VULKAN; }
    
    @Override
    public String getVersionString() {
        return mgr() != null ? mgr().getVersionString() : "Unknown";
    }
    
    @Override
    public boolean isInitialized() {
        return VulkanManager.isInitialized();
    }
    
    // ========================================================================
    // BUFFER OPERATIONS
    // ========================================================================
    
    @Override
    public long createBuffer(long size, int usage, int memoryFlags) {
        // Translate to Vulkan usage/memory flags
        int vkUsage = 0;
        if ((usage & BufferUsage.VERTEX) != 0) vkUsage |= 0x00000080; // VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
        if ((usage & BufferUsage.INDEX) != 0) vkUsage |= 0x00000040;  // VK_BUFFER_USAGE_INDEX_BUFFER_BIT
        if ((usage & BufferUsage.UNIFORM) != 0) vkUsage |= 0x00000010; // VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
        if ((usage & BufferUsage.STORAGE) != 0) vkUsage |= 0x00000020; // VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
        if ((usage & BufferUsage.INDIRECT) != 0) vkUsage |= 0x00000100; // VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT
        if ((usage & BufferUsage.TRANSFER_SRC) != 0) vkUsage |= 0x00000001;
        if ((usage & BufferUsage.TRANSFER_DST) != 0) vkUsage |= 0x00000002;
        
        // Use VulkanManager's buffer creation
        return mgr().genBuffer();
    }
    
    @Override
    public void destroyBuffer(long buffer) {
        mgr().deleteBuffer(buffer);
    }
    
    @Override
    public void bufferUpload(long buffer, long offset, ByteBuffer data) {
        // Bind and upload
        mgr().bindBuffer(0x8892, buffer); // GL_ARRAY_BUFFER
        mgr().bufferSubData(0x8892, offset, data);
    }
    
    @Override
    public ByteBuffer mapBuffer(long buffer, long offset, long size) {
        mgr().bindBuffer(0x8892, buffer);
        return mgr().mapBufferRange(0x8892, offset, size, 0x0002); // GL_MAP_WRITE_BIT
    }
    
    @Override
    public void unmapBuffer(long buffer) {
        mgr().bindBuffer(0x8892, buffer);
        mgr().unmapBuffer(0x8892);
    }
    
    @Override
    public long getBufferDeviceAddress(long buffer) {
        return mgr().getBufferDeviceAddress(buffer);
    }
    
    // ========================================================================
    // TEXTURE OPERATIONS
    // ========================================================================
    
    @Override
    public long createTexture2D(int width, int height, int format, int mipLevels) {
        return mgr().genTexture();
    }
    
    @Override
    public void destroyTexture(long texture) {
        mgr().deleteTexture(texture);
    }
    
    @Override
    public void textureUpload(long texture, int level, int x, int y, int width, int height, ByteBuffer data) {
        mgr().bindTexture(0x0DE1, texture); // GL_TEXTURE_2D
        VulkanCallMapperX.texSubImage2D(0x0DE1, level, x, y, width, height, 0x1908, 0x1401, data);
    }
    
    // ========================================================================
    // SHADER OPERATIONS
    // ========================================================================
    
    @Override
    public long createShader(int stage, String source) {
        int glType = translateStageToGL(stage);
        long shader = mgr().createShader(glType);
        mgr().shaderSource(shader, source);
        mgr().compileShader(shader);
        return shader;
    }
    
    private int translateStageToGL(int stage) {
        if ((stage & ShaderStage.VERTEX) != 0) return 0x8B31;
        if ((stage & ShaderStage.FRAGMENT) != 0) return 0x8B30;
        if ((stage & ShaderStage.GEOMETRY) != 0) return 0x8DD9;
        if ((stage & ShaderStage.TESS_CONTROL) != 0) return 0x8E88;
        if ((stage & ShaderStage.TESS_EVAL) != 0) return 0x8E87;
        if ((stage & ShaderStage.COMPUTE) != 0) return 0x91B9;
        return 0x8B31;
    }
    
    @Override
    public long createShaderFromSPIRV(int stage, ByteBuffer spirv) {
        // VulkanCallMapperX handles SPIR-V directly
        int glType = translateStageToGL(stage);
        return VulkanCallMapperX.createShaderFromSPIRV(glType, spirv);
    }
    
    @Override
    public void destroyShader(long shader) {
        mgr().deleteShader(shader);
    }
    
    @Override
    public long createProgram(long... shaders) {
        long program = mgr().createProgram();
        for (long shader : shaders) {
            mgr().attachShader(program, shader);
        }
        mgr().linkProgram(program);
        return program;
    }
    
    @Override
    public void destroyProgram(long program) {
        mgr().deleteProgram(program);
    }
    
    // ========================================================================
    // DRAW OPERATIONS
    // ========================================================================
    
    @Override
    public void bindVertexBuffer(int binding, long buffer, long offset, int stride) {
        VulkanCallMapperX.bindVertexBufferSlot(binding, buffer, offset);
    }
    
    @Override
    public void bindIndexBuffer(long buffer, long offset, boolean is32Bit) {
        mgr().bindBuffer(0x8893, buffer); // GL_ELEMENT_ARRAY_BUFFER
    }
    
    @Override
    public void bindProgram(long program) {
        mgr().useProgram(program);
    }
    
    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        VulkanCallMapperX.drawElementsInstancedBaseVertexBaseInstance(
            0x0004, indexCount, 0x1405, firstIndex * 4L, instanceCount, vertexOffset, firstInstance);
    }
    
    @Override
    public void drawIndexedIndirect(long buffer, long offset, int drawCount, int stride) {
        mgr().multiDrawElementsIndirect(0x0004, 0x1405, buffer, offset, drawCount, stride);
    }
    
    @Override
    public void drawIndexedIndirectCount(long commandBuffer, long commandOffset,
                                          long countBuffer, long countOffset,
                                          int maxDrawCount, int stride) {
        mgr().multiDrawElementsIndirectCount(0x0004, 0x1405, commandBuffer, commandOffset,
            countBuffer, countOffset, maxDrawCount, stride);
    }
    
    // ========================================================================
    // COMPUTE OPERATIONS
    // ========================================================================
    
    @Override
    public void bindComputeProgram(long program) {
        mgr().useProgram(program);
    }
    
    @Override
    public void dispatchCompute(int groupsX, int groupsY, int groupsZ) {
        mgr().dispatchCompute(groupsX, groupsY, groupsZ);
    }
    
    @Override
    public void dispatchComputeIndirect(long buffer, long offset) {
        mgr().dispatchComputeIndirect(buffer, offset);
    }
    
    // ========================================================================
    // SYNCHRONIZATION
    // ========================================================================
    
    @Override
    public void memoryBarrier(int barrierBits) {
        int glBarriers = translateBarriers(barrierBits);
        VulkanCallMapperX.memoryBarrier(glBarriers);
    }
    
    private int translateBarriers(int bits) {
        int gl = 0;
        if ((bits & BarrierType.VERTEX_ATTRIB) != 0) gl |= 0x00000001;
        if ((bits & BarrierType.INDEX_READ) != 0) gl |= 0x00000002;
        if ((bits & BarrierType.UNIFORM_READ) != 0) gl |= 0x00000004;
        if ((bits & BarrierType.TEXTURE_FETCH) != 0) gl |= 0x00000008;
        if ((bits & BarrierType.SHADER_IMAGE) != 0) gl |= 0x00000020;
        if ((bits & BarrierType.INDIRECT_COMMAND) != 0) gl |= 0x00000080;
        if ((bits & BarrierType.BUFFER_UPDATE) != 0) gl |= 0x00000400;
        if ((bits & BarrierType.SHADER_STORAGE) != 0) gl |= 0x00002000;
        if (bits == BarrierType.ALL) gl = 0xFFFFFFFF;
        return gl;
    }
    
    @Override
    public void finish() {
        mgr().finish();
    }
    
    // ========================================================================
    // RENDER PASS
    // ========================================================================
    
    @Override
    public void beginRenderPass(RenderPassInfo info) {
        VulkanManager.RenderPassDesc desc = new VulkanManager.RenderPassDesc(
            info.name,
            info.colorAttachments != null ? info.colorAttachments.length : 0,
            info.depthAttachment != 0,
            false,
            info.clearOnLoad ? 0 : 1, // VK_ATTACHMENT_LOAD_OP_CLEAR/LOAD
            info.storeResults ? 0 : 1, // VK_ATTACHMENT_STORE_OP_STORE/DONT_CARE
            info.clearColor != null ? new int[] {
                (int)(info.clearColor[0] * 255),
                (int)(info.clearColor[1] * 255),
                (int)(info.clearColor[2] * 255),
                (int)(info.clearColor[3] * 255)
            } : null,
            info.clearDepth,
            info.clearStencil
        );
        
        mgr().beginRenderPass(desc, 0, info.x, info.y, info.width, info.height);
    }
    
    @Override
    public void endRenderPass() {
        mgr().endRenderPass();
    }
    
    // ========================================================================
    // FRAME MANAGEMENT
    // ========================================================================
    
    @Override
    public void beginFrame() {
        mgr().beginFrame();
    }
    
    @Override
    public void endFrame() {
        mgr().endFrame();
    }
    
    @Override
    public int getCurrentFrameIndex() {
        return mgr().getCurrentFrameIndex();
    }
    
    // ========================================================================
    // CAPABILITIES
    // ========================================================================
    
    @Override
    public boolean supportsMultiDrawIndirect() { return mgr().hasMultiDrawIndirect(); }
    
    @Override
    public boolean supportsIndirectCount() { return mgr().hasIndirectCount(); }
    
    @Override
    public boolean supportsComputeShaders() { return true; } // Always in Vulkan
    
    @Override
    public boolean supportsMeshShaders() { return mgr().hasMeshShaders(); }
    
    @Override
    public boolean supportsBufferDeviceAddress() { return mgr().hasBufferDeviceAddress(); }
    
    @Override
    public boolean supportsPersistentMapping() { return true; } // Always in Vulkan
    
    @Override
    public int getMaxComputeWorkGroupSize() { return 1024; } // Typical Vulkan limit
    
    @Override
    public int getMaxDrawIndirectCount() { return 65536; }
}
