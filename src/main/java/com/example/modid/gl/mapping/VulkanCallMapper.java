package com.example.modid.gl.mapping;

import com.example.modid.FPSFlux;
import com.example.modid.gl.vulkan.VulkanContext;
import com.example.modid.gl.vulkan.VulkanState;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.vulkan.VK12.*;
import static org.lwjgl.vulkan.VK13.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.KHRDynamicRendering.*;
import static org.lwjgl.vulkan.KHRSynchronization2.*;
import static org.lwjgl.vulkan.KHRTimelineSemaphore.*;

/**
 * VulkanCallMapperX - Complete OpenGL → Vulkan Translation Layer
 * 
 * Supports Vulkan 1.0 through 1.4 with feature detection and fallbacks.
 * 
 * Version-specific features:
 * - Vulkan 1.0: Base functionality
 * - Vulkan 1.1: Subgroup operations, multiview
 * - Vulkan 1.2: Timeline semaphores, buffer device address, descriptor indexing
 * - Vulkan 1.3: Dynamic rendering, synchronization2, maintenance4
 * - Vulkan 1.4: Push descriptors improvements, maintenance5
 */
public class VulkanCallMapperX {
    
    // ========================================================================
    // VERSION CONSTANTS
    // ========================================================================
    
    public static final int VULKAN_1_0 = VK_MAKE_VERSION(1, 0, 0);
    public static final int VULKAN_1_1 = VK_MAKE_VERSION(1, 1, 0);
    public static final int VULKAN_1_2 = VK_MAKE_VERSION(1, 2, 0);
    public static final int VULKAN_1_3 = VK_MAKE_VERSION(1, 3, 0);
    public static final int VULKAN_1_4 = VK_MAKE_VERSION(1, 4, 0);
    
    // ========================================================================
    // CORE STATE
    // ========================================================================
    
    private static VulkanContext ctx;
    private static VulkanState state;
    private static VkCommandBuffer currentCommandBuffer;
    private static boolean recordingCommands = false;
    private static boolean initialized = false;
    
    // Vulkan version and features
    private static int vulkanVersion = VULKAN_1_0;
    private static boolean supportsTimelineSemaphores = false;
    private static boolean supportsDynamicRendering = false;
    private static boolean supportsSynchronization2 = false;
    private static boolean supportsBufferDeviceAddress = false;
    private static boolean supportsDescriptorIndexing = false;
    private static boolean supportsMaintenance4 = false;
    private static boolean supportsMaintenance5 = false;
    
    // Shaderc compiler
    private static long shadercCompiler = 0;
    
    // Pipeline cache
    private static final Map<PipelineStateKey, Long> pipelineCache = new HashMap<>();
    private static final Map<Long, Long> computePipelineCache = new HashMap<>();
    
    // Descriptor management
    private static long descriptorPool = VK_NULL_HANDLE;
    private static long[] descriptorSets;
    private static int currentDescriptorSetIndex = 0;
    private static final int MAX_DESCRIPTOR_SETS = 32;
    private static final int MAX_TEXTURES_PER_SET = 16;
    
    // Command batching
    private static final ConcurrentLinkedQueue<GPUCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private static final int MAX_COMMANDS_PER_BATCH = 2048;
    
    // Multiple command buffers for frames in flight
    private static final int MAX_FRAMES_IN_FLIGHT = 3;
    private static VkCommandBuffer[] commandBufferArray;
    private static long[] commandBufferFences;
    private static int currentFrameIndex = 0;
    
    // Timeline semaphore (Vulkan 1.2+)
    private static long timelineSemaphore = VK_NULL_HANDLE;
    private static final AtomicLong timelineValue = new AtomicLong(0);
    
    // Uniform buffer for push constants fallback
    private static long uniformBuffer = VK_NULL_HANDLE;
    private static long uniformBufferMemory = VK_NULL_HANDLE;
    private static ByteBuffer uniformBufferMapped = null;
    private static final int UNIFORM_BUFFER_SIZE = 65536; // 64KB
    
    // ========================================================================
    // GPU COMMAND TYPES
    // ========================================================================
    
    private enum CommandType {
        // Draw commands
        DRAW_ARRAYS,
        DRAW_ELEMENTS,
        DRAW_ARRAYS_INSTANCED,
        DRAW_ELEMENTS_INSTANCED,
        DRAW_ARRAYS_INDIRECT,
        DRAW_ELEMENTS_INDIRECT,
        MULTI_DRAW_ARRAYS_INDIRECT,
        MULTI_DRAW_ELEMENTS_INDIRECT,
        DRAW_ARRAYS_INDIRECT_COUNT,      // Vulkan 1.2+
        DRAW_ELEMENTS_INDIRECT_COUNT,    // Vulkan 1.2+
        
        // Compute commands
        DISPATCH_COMPUTE,
        DISPATCH_COMPUTE_INDIRECT,
        
        // Transfer commands
        COPY_BUFFER,
        COPY_BUFFER_TO_IMAGE,
        COPY_IMAGE_TO_BUFFER,
        COPY_IMAGE,
        BLIT_IMAGE,
        RESOLVE_IMAGE,
        FILL_BUFFER,
        UPDATE_BUFFER,
        CLEAR_COLOR_IMAGE,
        CLEAR_DEPTH_STENCIL_IMAGE,
        
        // Synchronization commands
        MEMORY_BARRIER,
        BUFFER_BARRIER,
        IMAGE_BARRIER,
        PIPELINE_BARRIER,
        
        // Other commands
        PUSH_CONSTANTS,
        SET_VIEWPORT,
        SET_SCISSOR,
        SET_LINE_WIDTH,
        SET_DEPTH_BIAS,
        SET_BLEND_CONSTANTS,
        SET_DEPTH_BOUNDS,
        SET_STENCIL_COMPARE_MASK,
        SET_STENCIL_WRITE_MASK,
        SET_STENCIL_REFERENCE,
        
        // Vulkan 1.3+ dynamic state
        SET_CULL_MODE,
        SET_FRONT_FACE,
        SET_PRIMITIVE_TOPOLOGY,
        SET_DEPTH_TEST_ENABLE,
        SET_DEPTH_WRITE_ENABLE,
        SET_DEPTH_COMPARE_OP,
        SET_DEPTH_BOUNDS_TEST_ENABLE,
        SET_STENCIL_TEST_ENABLE,
        SET_STENCIL_OP,
        SET_RASTERIZER_DISCARD_ENABLE,
        SET_DEPTH_BIAS_ENABLE,
        SET_PRIMITIVE_RESTART_ENABLE
    }
    
    // ========================================================================
    // GPU COMMAND STRUCTURE
    // ========================================================================
    
    private static class GPUCommand {
        CommandType type;
        
        // Draw parameters
        int mode;
        int first;
        int count;
        int instanceCount;
        int baseInstance;
        int baseVertex;
        int indexType;
        long indices;
        
        // Indirect parameters
        long indirectBuffer;
        long indirectOffset;
        int drawCount;
        int stride;
        long countBuffer;        // For indirect count
        long countBufferOffset;
        int maxDrawCount;
        
        // Compute parameters
        int groupCountX, groupCountY, groupCountZ;
        
        // Transfer parameters
        long srcBuffer;
        long dstBuffer;
        long srcImage;
        long dstImage;
        long srcOffset;
        long dstOffset;
        long size;
        int srcOffsetX, srcOffsetY, srcOffsetZ;
        int dstOffsetX, dstOffsetY, dstOffsetZ;
        int width, height, depth;
        int srcMipLevel, dstMipLevel;
        int srcArrayLayer, dstArrayLayer;
        int layerCount;
        int srcImageLayout, dstImageLayout;
        int filter;
        int data; // For fill buffer
        
        // Barrier parameters
        int srcAccessMask, dstAccessMask;
        int srcStageMask, dstStageMask;
        int oldLayout, newLayout;
        int srcQueueFamily, dstQueueFamily;
        long barrierBuffer;
        long barrierImage;
        int aspectMask;
        int baseMipLevel, levelCount;
        int baseArrayLayer;
        
        // Pipeline state
        long pipeline;
        long pipelineLayout;
        long descriptorSet;
        long[] vertexBuffers;
        long[] vertexOffsets;
        long indexBuffer;
        long indexOffset;
        
        // Push constants
        byte[] pushConstantData;
        int pushConstantOffset;
        int pushConstantStageFlags;
        
        // Dynamic state
        float viewportX, viewportY, viewportWidth, viewportHeight;
        float viewportMinDepth, viewportMaxDepth;
        int scissorX, scissorY, scissorWidth, scissorHeight;
        float lineWidth;
        float depthBiasConstant, depthBiasClamp, depthBiasSlope;
        float[] blendConstants;
        float depthBoundsMin, depthBoundsMax;
        int stencilFaceMask;
        int stencilCompareMask, stencilWriteMask, stencilReference;
        int stencilFailOp, stencilPassOp, stencilDepthFailOp, stencilCompareOp;
        boolean boolValue; // For enable/disable operations
        int intValue;      // For topology, compare op, etc.
    }
    
    // ========================================================================
    // PIPELINE STATE KEY
    // ========================================================================
    
    private static class PipelineStateKey {
        final long program;
        final int primitiveTopology;
        final boolean blendEnabled;
        final int blendSrcRGB, blendDstRGB;
        final int blendSrcAlpha, blendDstAlpha;
        final int blendOpRGB, blendOpAlpha;
        final boolean depthTestEnabled;
        final boolean depthWriteEnabled;
        final int depthFunc;
        final boolean depthBoundsTestEnabled;
        final boolean stencilTestEnabled;
        final boolean cullFaceEnabled;
        final int cullFaceMode;
        final int frontFace;
        final int polygonMode;
        final boolean primitiveRestartEnabled;
        final boolean rasterizerDiscardEnabled;
        final boolean depthClampEnabled;
        final boolean depthBiasEnabled;
        final int sampleCount;
        final boolean sampleShadingEnabled;
        final boolean alphaToCoverageEnabled;
        final boolean alphaToOneEnabled;
        final int colorWriteMask;
        final boolean logicOpEnabled;
        final int logicOp;
        final int vertexInputHash;
        final int renderPassHash;
        
        PipelineStateKey(long program, int topology, VulkanState state, int renderPassHash) {
            this.program = program;
            this.primitiveTopology = topology;
            this.blendEnabled = state.isBlendEnabled();
            this.blendSrcRGB = state.getBlendSrcRGB();
            this.blendDstRGB = state.getBlendDstRGB();
            this.blendSrcAlpha = state.getBlendSrcAlpha();
            this.blendDstAlpha = state.getBlendDstAlpha();
            this.blendOpRGB = state.getBlendOpRGB();
            this.blendOpAlpha = state.getBlendOpAlpha();
            this.depthTestEnabled = state.isDepthTestEnabled();
            this.depthWriteEnabled = state.isDepthWriteEnabled();
            this.depthFunc = state.getDepthFunc();
            this.depthBoundsTestEnabled = state.isDepthBoundsTestEnabled();
            this.stencilTestEnabled = state.isStencilTestEnabled();
            this.cullFaceEnabled = state.isCullFaceEnabled();
            this.cullFaceMode = state.getCullFaceMode();
            this.frontFace = state.getFrontFace();
            this.polygonMode = state.getPolygonMode();
            this.primitiveRestartEnabled = state.isPrimitiveRestartEnabled();
            this.rasterizerDiscardEnabled = state.isRasterizerDiscardEnabled();
            this.depthClampEnabled = state.isDepthClampEnabled();
            this.depthBiasEnabled = state.isDepthBiasEnabled();
            this.sampleCount = state.getSampleCount();
            this.sampleShadingEnabled = state.isSampleShadingEnabled();
            this.alphaToCoverageEnabled = state.isAlphaToCoverageEnabled();
            this.alphaToOneEnabled = state.isAlphaToOneEnabled();
            this.colorWriteMask = state.getColorWriteMask();
            this.logicOpEnabled = state.isLogicOpEnabled();
            this.logicOp = state.getLogicOp();
            this.vertexInputHash = state.getVertexInputHash();
            this.renderPassHash = renderPassHash;
        }
        
        @Override
        public int hashCode() {
            int result = Long.hashCode(program);
            result = 31 * result + primitiveTopology;
            result = 31 * result + (blendEnabled ? 1 : 0);
            result = 31 * result + blendSrcRGB + blendDstRGB + blendSrcAlpha + blendDstAlpha;
            result = 31 * result + blendOpRGB + blendOpAlpha;
            result = 31 * result + (depthTestEnabled ? 1 : 0);
            result = 31 * result + (depthWriteEnabled ? 1 : 0);
            result = 31 * result + depthFunc;
            result = 31 * result + (depthBoundsTestEnabled ? 1 : 0);
            result = 31 * result + (stencilTestEnabled ? 1 : 0);
            result = 31 * result + (cullFaceEnabled ? 1 : 0);
            result = 31 * result + cullFaceMode + frontFace + polygonMode;
            result = 31 * result + (primitiveRestartEnabled ? 1 : 0);
            result = 31 * result + (rasterizerDiscardEnabled ? 1 : 0);
            result = 31 * result + (depthClampEnabled ? 1 : 0);
            result = 31 * result + (depthBiasEnabled ? 1 : 0);
            result = 31 * result + sampleCount;
            result = 31 * result + (sampleShadingEnabled ? 1 : 0);
            result = 31 * result + (alphaToCoverageEnabled ? 1 : 0);
            result = 31 * result + (alphaToOneEnabled ? 1 : 0);
            result = 31 * result + colorWriteMask;
            result = 31 * result + (logicOpEnabled ? 1 : 0);
            result = 31 * result + logicOp;
            result = 31 * result + vertexInputHash;
            result = 31 * result + renderPassHash;
            return result;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PipelineStateKey other)) return false;
            return program == other.program
                && primitiveTopology == other.primitiveTopology
                && blendEnabled == other.blendEnabled
                && blendSrcRGB == other.blendSrcRGB
                && blendDstRGB == other.blendDstRGB
                && blendSrcAlpha == other.blendSrcAlpha
                && blendDstAlpha == other.blendDstAlpha
                && blendOpRGB == other.blendOpRGB
                && blendOpAlpha == other.blendOpAlpha
                && depthTestEnabled == other.depthTestEnabled
                && depthWriteEnabled == other.depthWriteEnabled
                && depthFunc == other.depthFunc
                && depthBoundsTestEnabled == other.depthBoundsTestEnabled
                && stencilTestEnabled == other.stencilTestEnabled
                && cullFaceEnabled == other.cullFaceEnabled
                && cullFaceMode == other.cullFaceMode
                && frontFace == other.frontFace
                && polygonMode == other.polygonMode
                && primitiveRestartEnabled == other.primitiveRestartEnabled
                && rasterizerDiscardEnabled == other.rasterizerDiscardEnabled
                && depthClampEnabled == other.depthClampEnabled
                && depthBiasEnabled == other.depthBiasEnabled
                && sampleCount == other.sampleCount
                && sampleShadingEnabled == other.sampleShadingEnabled
                && alphaToCoverageEnabled == other.alphaToCoverageEnabled
                && alphaToOneEnabled == other.alphaToOneEnabled
                && colorWriteMask == other.colorWriteMask
                && logicOpEnabled == other.logicOpEnabled
                && logicOp == other.logicOp
                && vertexInputHash == other.vertexInputHash
                && renderPassHash == other.renderPassHash;
        }
    }
    
    // ========================================================================
    // VERTEX INPUT STATE
    // ========================================================================
    
    private static class VertexInputStateInfo implements AutoCloseable {
        final VkVertexInputBindingDescription.Buffer bindings;
        final VkVertexInputAttributeDescription.Buffer attributes;
        
        VertexInputStateInfo(VkVertexInputBindingDescription.Buffer bindings,
                            VkVertexInputAttributeDescription.Buffer attributes) {
            this.bindings = bindings;
            this.attributes = attributes;
        }
        
        @Override
        public void close() {
            if (bindings != null) bindings.free();
            if (attributes != null) attributes.free();
        }
    }
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    public static void initialize(VulkanContext context) {
        if (initialized) return;
        
        ctx = context;
        state = new VulkanState();
        
        // Detect Vulkan version and features
        detectVulkanVersion();
        detectVulkanFeatures();
        
        // Initialize shaderc compiler
        shadercCompiler = Shaderc.shaderc_compiler_initialize();
        if (shadercCompiler == 0) {
            throw new RuntimeException("Failed to initialize shaderc compiler");
        }
        
        // Create descriptor pool and sets
        createDescriptorPool();
        allocateDescriptorSets();
        
        // Create uniform buffer
        createUniformBuffer();
        
        // Initialize GPU execution system
        initializeGPUExecution();
        
        initialized = true;
        
        FPSFlux.LOGGER.info("[VulkanCallMapperX] Initialized successfully");
        FPSFlux.LOGGER.info("[VulkanCallMapperX] Vulkan Version: {}.{}.{}", 
            VK_VERSION_MAJOR(vulkanVersion),
            VK_VERSION_MINOR(vulkanVersion),
            VK_VERSION_PATCH(vulkanVersion));
        FPSFlux.LOGGER.info("[VulkanCallMapperX] Timeline Semaphores: {}", supportsTimelineSemaphores);
        FPSFlux.LOGGER.info("[VulkanCallMapperX] Dynamic Rendering: {}", supportsDynamicRendering);
        FPSFlux.LOGGER.info("[VulkanCallMapperX] Synchronization2: {}", supportsSynchronization2);
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    private static void detectVulkanVersion() {
        vulkanVersion = ctx.vulkanVersion;
        
        // Clamp to known versions
        if (vulkanVersion >= VULKAN_1_4) {
            vulkanVersion = VULKAN_1_4;
        } else if (vulkanVersion >= VULKAN_1_3) {
            vulkanVersion = VULKAN_1_3;
        } else if (vulkanVersion >= VULKAN_1_2) {
            vulkanVersion = VULKAN_1_2;
        } else if (vulkanVersion >= VULKAN_1_1) {
            vulkanVersion = VULKAN_1_1;
        } else {
            vulkanVersion = VULKAN_1_0;
        }
    }
    
    private static void detectVulkanFeatures() {
        // Vulkan 1.2+ features
        if (vulkanVersion >= VULKAN_1_2) {
            supportsTimelineSemaphores = ctx.supportsTimelineSemaphores();
            supportsBufferDeviceAddress = ctx.supportsBufferDeviceAddress();
            supportsDescriptorIndexing = ctx.supportsDescriptorIndexing();
        }
        
        // Vulkan 1.3+ features
        if (vulkanVersion >= VULKAN_1_3) {
            supportsDynamicRendering = ctx.supportsDynamicRendering();
            supportsSynchronization2 = ctx.supportsSynchronization2();
            supportsMaintenance4 = ctx.supportsMaintenance4();
        }
        
        // Vulkan 1.4+ features
        if (vulkanVersion >= VULKAN_1_4) {
            supportsMaintenance5 = ctx.supportsMaintenance5();
        }
        
        // Check for extensions on older versions
        if (!supportsTimelineSemaphores && vulkanVersion < VULKAN_1_2) {
            supportsTimelineSemaphores = ctx.hasExtension("VK_KHR_timeline_semaphore");
        }
        if (!supportsDynamicRendering && vulkanVersion < VULKAN_1_3) {
            supportsDynamicRendering = ctx.hasExtension("VK_KHR_dynamic_rendering");
        }
        if (!supportsSynchronization2 && vulkanVersion < VULKAN_1_3) {
            supportsSynchronization2 = ctx.hasExtension("VK_KHR_synchronization2");
        }
    }
    
    private static void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("VulkanCallMapperX not initialized. Call initialize() first.");
        }
    }
    
    public static void shutdown() {
        if (!initialized) return;
        
        FPSFlux.LOGGER.info("[VulkanCallMapperX] Shutting down...");
        
        if (ctx != null && ctx.device != null) {
            vkDeviceWaitIdle(ctx.device);
        }
        
        // Shutdown GPU execution
        shutdownGPUExecution();
        
        // Destroy uniform buffer
        destroyUniformBuffer();
        
        // Destroy cached pipelines
        for (Long pipeline : pipelineCache.values()) {
            if (pipeline != VK_NULL_HANDLE && ctx != null) {
                vkDestroyPipeline(ctx.device, pipeline, null);
            }
        }
        pipelineCache.clear();
        
        for (Long pipeline : computePipelineCache.values()) {
            if (pipeline != VK_NULL_HANDLE && ctx != null) {
                vkDestroyPipeline(ctx.device, pipeline, null);
            }
        }
        computePipelineCache.clear();
        
        // Destroy descriptor pool
        if (descriptorPool != VK_NULL_HANDLE && ctx != null) {
            vkDestroyDescriptorPool(ctx.device, descriptorPool, null);
            descriptorPool = VK_NULL_HANDLE;
        }
        
        // Release shaderc compiler
        if (shadercCompiler != 0) {
            Shaderc.shaderc_compiler_release(shadercCompiler);
            shadercCompiler = 0;
        }
        
        ctx = null;
        state = null;
        recordingCommands = false;
        initialized = false;
        
        FPSFlux.LOGGER.info("[VulkanCallMapperX] Shutdown complete");
    }
    
    // ========================================================================
    // UNIFORM BUFFER
    // ========================================================================
    
    private static void createUniformBuffer() {
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(UNIFORM_BUFFER_SIZE)
            .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        
        LongBuffer pBuffer = memAllocLong(1);
        int result = vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            bufferInfo.free();
            memFree(pBuffer);
            throw new RuntimeException("Failed to create uniform buffer: " + result);
        }
        uniformBuffer = pBuffer.get(0);
        
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(ctx.device, uniformBuffer, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(),
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
        
        LongBuffer pMemory = memAllocLong(1);
        result = vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        if (result != VK_SUCCESS) {
            vkDestroyBuffer(ctx.device, uniformBuffer, null);
            bufferInfo.free();
            memReqs.free();
            allocInfo.free();
            memFree(pBuffer);
            memFree(pMemory);
            throw new RuntimeException("Failed to allocate uniform buffer memory: " + result);
        }
        uniformBufferMemory = pMemory.get(0);
        
        vkBindBufferMemory(ctx.device, uniformBuffer, uniformBufferMemory, 0);
        
        // Map the buffer persistently
        uniformBufferMapped = ctx.mapMemory(uniformBufferMemory, 0, UNIFORM_BUFFER_SIZE);
        
        bufferInfo.free();
        memReqs.free();
        allocInfo.free();
        memFree(pBuffer);
        memFree(pMemory);
    }
    
    private static void destroyUniformBuffer() {
        if (uniformBufferMapped != null && ctx != null) {
            vkUnmapMemory(ctx.device, uniformBufferMemory);
            uniformBufferMapped = null;
        }
        if (uniformBuffer != VK_NULL_HANDLE && ctx != null) {
            vkDestroyBuffer(ctx.device, uniformBuffer, null);
            uniformBuffer = VK_NULL_HANDLE;
        }
        if (uniformBufferMemory != VK_NULL_HANDLE && ctx != null) {
            vkFreeMemory(ctx.device, uniformBufferMemory, null);
            uniformBufferMemory = VK_NULL_HANDLE;
        }
    }
    
    // ========================================================================
    // VERSION QUERY
    // ========================================================================
    
    public static int getVulkanVersion() {
        return vulkanVersion;
    }
    
    public static String getVulkanVersionString() {
        int major = VK_VERSION_MAJOR(vulkanVersion);
        int minor = VK_VERSION_MINOR(vulkanVersion);
        int patch = VK_VERSION_PATCH(vulkanVersion);
        return major + "." + minor + "." + patch;
    }
    
    public static boolean supportsVulkan11() { return vulkanVersion >= VULKAN_1_1; }
    public static boolean supportsVulkan12() { return vulkanVersion >= VULKAN_1_2; }
    public static boolean supportsVulkan13() { return vulkanVersion >= VULKAN_1_3; }
    public static boolean supportsVulkan14() { return vulkanVersion >= VULKAN_1_4; }
    
    public static boolean hasTimelineSemaphores() { return supportsTimelineSemaphores; }
    public static boolean hasDynamicRendering() { return supportsDynamicRendering; }
    public static boolean hasSynchronization2() { return supportsSynchronization2; }
    public static boolean hasBufferDeviceAddress() { return supportsBufferDeviceAddress; }
    public static boolean hasDescriptorIndexing() { return supportsDescriptorIndexing; }

    // ========================================================================
    // DESCRIPTOR POOL & SETS
    // ========================================================================
    
    private static void createDescriptorPool() {
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(3);
        poolSizes.get(0)
            .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(MAX_DESCRIPTOR_SETS * 2);
        poolSizes.get(1)
            .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(MAX_DESCRIPTOR_SETS * MAX_TEXTURES_PER_SET);
        poolSizes.get(2)
            .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
            .descriptorCount(MAX_DESCRIPTOR_SETS * 4);
        
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
            .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
            .pPoolSizes(poolSizes)
            .maxSets(MAX_DESCRIPTOR_SETS);
        
        LongBuffer pPool = memAllocLong(1);
        int result = vkCreateDescriptorPool(ctx.device, poolInfo, null, pPool);
        
        if (result != VK_SUCCESS) {
            poolSizes.free();
            poolInfo.free();
            memFree(pPool);
            throw new RuntimeException("Failed to create descriptor pool: " + result);
        }
        
        descriptorPool = pPool.get(0);
        
        poolSizes.free();
        poolInfo.free();
        memFree(pPool);
    }
    
    private static void allocateDescriptorSets() {
        descriptorSets = new long[MAX_DESCRIPTOR_SETS];
        
        if (ctx.descriptorSetLayout == VK_NULL_HANDLE) {
            createDescriptorSetLayout();
        }
        
        LongBuffer layouts = memAllocLong(MAX_DESCRIPTOR_SETS);
        for (int i = 0; i < MAX_DESCRIPTOR_SETS; i++) {
            layouts.put(i, ctx.descriptorSetLayout);
        }
        
        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .descriptorPool(descriptorPool)
            .pSetLayouts(layouts);
        
        LongBuffer pSets = memAllocLong(MAX_DESCRIPTOR_SETS);
        int result = vkAllocateDescriptorSets(ctx.device, allocInfo, pSets);
        
        if (result != VK_SUCCESS) {
            allocInfo.free();
            memFree(layouts);
            memFree(pSets);
            throw new RuntimeException("Failed to allocate descriptor sets: " + result);
        }
        
        for (int i = 0; i < MAX_DESCRIPTOR_SETS; i++) {
            descriptorSets[i] = pSets.get(i);
        }
        
        allocInfo.free();
        memFree(layouts);
        memFree(pSets);
    }
    
    private static void createDescriptorSetLayout() {
        int bindingCount = 1 + MAX_TEXTURES_PER_SET + 1; // UBO + textures + SSBO
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(bindingCount);
        
        // Binding 0: Uniform buffer
        bindings.get(0)
            .binding(0)
            .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(1)
            .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT);
        
        // Bindings 1-16: Combined image samplers
        for (int i = 0; i < MAX_TEXTURES_PER_SET; i++) {
            bindings.get(1 + i)
                .binding(1 + i)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT);
        }
        
        // Binding 17: Storage buffer
        bindings.get(1 + MAX_TEXTURES_PER_SET)
            .binding(1 + MAX_TEXTURES_PER_SET)
            .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
            .descriptorCount(1)
            .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT);
        
        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
            .pBindings(bindings);
        
        LongBuffer pLayout = memAllocLong(1);
        int result = vkCreateDescriptorSetLayout(ctx.device, layoutInfo, null, pLayout);
        
        if (result != VK_SUCCESS) {
            bindings.free();
            layoutInfo.free();
            memFree(pLayout);
            throw new RuntimeException("Failed to create descriptor set layout: " + result);
        }
        
        ctx.descriptorSetLayout = pLayout.get(0);
        
        bindings.free();
        layoutInfo.free();
        memFree(pLayout);
    }
    
    // ========================================================================
    // GPU EXECUTION INITIALIZATION
    // ========================================================================
    
    private static void initializeGPUExecution() {
        // Create command buffers
        commandBufferArray = new VkCommandBuffer[MAX_FRAMES_IN_FLIGHT];
        commandBufferFences = new long[MAX_FRAMES_IN_FLIGHT];
        
        VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            .commandPool(ctx.commandPool)
            .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            .commandBufferCount(MAX_FRAMES_IN_FLIGHT);
        
        PointerBuffer pCmdBuffers = memAllocPointer(MAX_FRAMES_IN_FLIGHT);
        int result = vkAllocateCommandBuffers(ctx.device, allocInfo, pCmdBuffers);
        
        if (result != VK_SUCCESS) {
            allocInfo.free();
            memFree(pCmdBuffers);
            throw new RuntimeException("Failed to allocate command buffers: " + result);
        }
        
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            commandBufferArray[i] = new VkCommandBuffer(pCmdBuffers.get(i), ctx.device);
            
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .flags(VK_FENCE_CREATE_SIGNALED_BIT);
            
            LongBuffer pFence = memAllocLong(1);
            vkCreateFence(ctx.device, fenceInfo, null, pFence);
            commandBufferFences[i] = pFence.get(0);
            
            fenceInfo.free();
            memFree(pFence);
        }
        
        allocInfo.free();
        memFree(pCmdBuffers);
        
        // Create timeline semaphore if supported
        if (supportsTimelineSemaphores) {
            createTimelineSemaphore();
        }
        
        FPSFlux.LOGGER.info("[VulkanCallMapperX] GPU execution initialized with {} frames in flight", MAX_FRAMES_IN_FLIGHT);
    }
    
    private static void createTimelineSemaphore() {
        if (vulkanVersion >= VULKAN_1_2) {
            // Use core Vulkan 1.2 API
            VkSemaphoreTypeCreateInfo typeInfo = VkSemaphoreTypeCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO)
                .semaphoreType(VK_SEMAPHORE_TYPE_TIMELINE)
                .initialValue(0);
            
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
                .pNext(typeInfo.address());
            
            LongBuffer pSemaphore = memAllocLong(1);
            int result = vkCreateSemaphore(ctx.device, semaphoreInfo, null, pSemaphore);
            
            if (result == VK_SUCCESS) {
                timelineSemaphore = pSemaphore.get(0);
                FPSFlux.LOGGER.info("[VulkanCallMapperX] Timeline semaphore created (Vulkan 1.2+)");
            } else {
                supportsTimelineSemaphores = false;
                FPSFlux.LOGGER.warn("[VulkanCallMapperX] Failed to create timeline semaphore: {}", result);
            }
            
            typeInfo.free();
            semaphoreInfo.free();
            memFree(pSemaphore);
        } else {
            // Use extension API for Vulkan 1.0/1.1
            try {
                VkSemaphoreTypeCreateInfoKHR typeInfo = VkSemaphoreTypeCreateInfoKHR.calloc()
                    .sType(KHRTimelineSemaphore.VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO_KHR)
                    .semaphoreType(KHRTimelineSemaphore.VK_SEMAPHORE_TYPE_TIMELINE_KHR)
                    .initialValue(0);
                
                VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
                    .pNext(typeInfo.address());
                
                LongBuffer pSemaphore = memAllocLong(1);
                int result = vkCreateSemaphore(ctx.device, semaphoreInfo, null, pSemaphore);
                
                if (result == VK_SUCCESS) {
                    timelineSemaphore = pSemaphore.get(0);
                    FPSFlux.LOGGER.info("[VulkanCallMapperX] Timeline semaphore created (KHR extension)");
                } else {
                    supportsTimelineSemaphores = false;
                }
                
                typeInfo.free();
                semaphoreInfo.free();
                memFree(pSemaphore);
            } catch (Exception e) {
                supportsTimelineSemaphores = false;
                FPSFlux.LOGGER.warn("[VulkanCallMapperX] Timeline semaphore extension not available");
            }
        }
    }
    
    private static void shutdownGPUExecution() {
        if (ctx == null || ctx.device == null) return;
        
        vkDeviceWaitIdle(ctx.device);
        
        // Destroy command buffer fences
        if (commandBufferFences != null) {
            for (long fence : commandBufferFences) {
                if (fence != VK_NULL_HANDLE) {
                    vkDestroyFence(ctx.device, fence, null);
                }
            }
            commandBufferFences = null;
        }
        
        commandBufferArray = null;
        
        // Destroy timeline semaphore
        if (timelineSemaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(ctx.device, timelineSemaphore, null);
            timelineSemaphore = VK_NULL_HANDLE;
        }
        
        commandQueue.clear();
        
        FPSFlux.LOGGER.info("[VulkanCallMapperX] GPU execution shutdown");
    }
    
    // ========================================================================
    // SHADER COMPILATION (SPIR-V)
    // ========================================================================
    
    private static ByteBuffer compileGLSLtoSPIRV(String source, int shaderType) {
        checkInitialized();
        
        if (shadercCompiler == 0) {
            throw new RuntimeException("Shaderc compiler not initialized");
        }
        
        int kind;
        String shaderName;
        switch (shaderType) {
            case 0x8B31 -> { kind = Shaderc.shaderc_vertex_shader; shaderName = "vertex.glsl"; }
            case 0x8B30 -> { kind = Shaderc.shaderc_fragment_shader; shaderName = "fragment.glsl"; }
            case 0x8DD9 -> { kind = Shaderc.shaderc_geometry_shader; shaderName = "geometry.glsl"; }
            case 0x8E88 -> { kind = Shaderc.shaderc_tess_control_shader; shaderName = "tess_control.glsl"; }
            case 0x8E87 -> { kind = Shaderc.shaderc_tess_evaluation_shader; shaderName = "tess_eval.glsl"; }
            case 0x91B9 -> { kind = Shaderc.shaderc_compute_shader; shaderName = "compute.glsl"; }
            default -> throw new RuntimeException("Unknown shader type: 0x" + Integer.toHexString(shaderType));
        }
        
        long options = Shaderc.shaderc_compile_options_initialize();
        
        // Set target environment based on Vulkan version
        int targetEnv = Shaderc.shaderc_target_env_vulkan;
        int envVersion;
        if (vulkanVersion >= VULKAN_1_3) {
            envVersion = Shaderc.shaderc_env_version_vulkan_1_3;
        } else if (vulkanVersion >= VULKAN_1_2) {
            envVersion = Shaderc.shaderc_env_version_vulkan_1_2;
        } else if (vulkanVersion >= VULKAN_1_1) {
            envVersion = Shaderc.shaderc_env_version_vulkan_1_1;
        } else {
            envVersion = Shaderc.shaderc_env_version_vulkan_1_0;
        }
        
        Shaderc.shaderc_compile_options_set_target_env(options, targetEnv, envVersion);
        Shaderc.shaderc_compile_options_set_optimization_level(options, 
            Shaderc.shaderc_optimization_level_performance);
        
        // Enable Vulkan 1.1+ features if available
        if (vulkanVersion >= VULKAN_1_1) {
            Shaderc.shaderc_compile_options_set_target_spirv(options, Shaderc.shaderc_spirv_version_1_3);
        }
        
        long result = Shaderc.shaderc_compile_into_spv(
            shadercCompiler, source, kind, shaderName, "main", options
        );
        
        int status = Shaderc.shaderc_result_get_compilation_status(result);
        if (status != Shaderc.shaderc_compilation_status_success) {
            String error = Shaderc.shaderc_result_get_error_message(result);
            Shaderc.shaderc_result_release(result);
            Shaderc.shaderc_compile_options_release(options);
            throw new RuntimeException("Shader compilation failed (" + shaderName + "): " + error);
        }
        
        ByteBuffer spirvTemp = Shaderc.shaderc_result_get_bytes(result);
        if (spirvTemp == null || spirvTemp.remaining() == 0) {
            Shaderc.shaderc_result_release(result);
            Shaderc.shaderc_compile_options_release(options);
            throw new RuntimeException("Shader compilation produced no output");
        }
        
        ByteBuffer spirv = memAlloc(spirvTemp.remaining());
        spirv.put(spirvTemp);
        spirv.flip();
        
        int numWarnings = (int) Shaderc.shaderc_result_get_num_warnings(result);
        if (numWarnings > 0) {
            String warnings = Shaderc.shaderc_result_get_error_message(result);
            FPSFlux.LOGGER.warn("[VulkanCallMapperX] Shader warnings ({}): {}", shaderName, warnings);
        }
        
        Shaderc.shaderc_result_release(result);
        Shaderc.shaderc_compile_options_release(options);
        
        FPSFlux.LOGGER.debug("[VulkanCallMapperX] Compiled {} ({} bytes SPIR-V)", shaderName, spirv.remaining());
        
        return spirv;
    }
    
    // ========================================================================
    // VERTEX INPUT STATE
    // ========================================================================
    
    private static VertexInputStateInfo buildVertexInputState() {
        int enabledCount = state.getEnabledAttributeCount();
        if (enabledCount == 0) {
            return new VertexInputStateInfo(null, null);
        }
        
        // Count unique bindings
        int bindingCount = state.getVertexBindingCount();
        if (bindingCount == 0) bindingCount = 1;
        
        VkVertexInputBindingDescription.Buffer bindings = VkVertexInputBindingDescription.calloc(bindingCount);
        
        for (int i = 0; i < bindingCount; i++) {
            int stride = state.getVertexBindingStride(i);
            int divisor = state.getVertexBindingDivisor(i);
            bindings.get(i)
                .binding(i)
                .stride(stride)
                .inputRate(divisor > 0 ? VK_VERTEX_INPUT_RATE_INSTANCE : VK_VERTEX_INPUT_RATE_VERTEX);
        }
        
        VkVertexInputAttributeDescription.Buffer attributes = 
            VkVertexInputAttributeDescription.calloc(enabledCount);
        
        int attrIdx = 0;
        for (int i = 0; i < 16; i++) {
            if (state.isVertexAttribEnabled(i)) {
                VulkanState.VertexAttrib attr = state.getVertexAttrib(i);
                if (attr != null) {
                    attributes.get(attrIdx)
                        .binding(attr.binding)
                        .location(i)
                        .format(translateVertexFormat(attr.size, attr.type, attr.normalized))
                        .offset((int) attr.offset);
                    attrIdx++;
                }
            }
        }
        
        return new VertexInputStateInfo(bindings, attributes);
    }
    
    private static int translateVertexFormat(int size, int type, boolean normalized) {
        return switch (type) {
            case 0x1406 -> // GL_FLOAT
                switch (size) {
                    case 1 -> VK_FORMAT_R32_SFLOAT;
                    case 2 -> VK_FORMAT_R32G32_SFLOAT;
                    case 3 -> VK_FORMAT_R32G32B32_SFLOAT;
                    default -> VK_FORMAT_R32G32B32A32_SFLOAT;
                };
            case 0x1401 -> // GL_UNSIGNED_BYTE
                normalized ? switch (size) {
                    case 1 -> VK_FORMAT_R8_UNORM;
                    case 2 -> VK_FORMAT_R8G8_UNORM;
                    case 3 -> VK_FORMAT_R8G8B8_UNORM;
                    default -> VK_FORMAT_R8G8B8A8_UNORM;
                } : switch (size) {
                    case 1 -> VK_FORMAT_R8_UINT;
                    case 2 -> VK_FORMAT_R8G8_UINT;
                    case 3 -> VK_FORMAT_R8G8B8_UINT;
                    default -> VK_FORMAT_R8G8B8A8_UINT;
                };
            case 0x1400 -> // GL_BYTE
                normalized ? switch (size) {
                    case 1 -> VK_FORMAT_R8_SNORM;
                    case 2 -> VK_FORMAT_R8G8_SNORM;
                    case 3 -> VK_FORMAT_R8G8B8_SNORM;
                    default -> VK_FORMAT_R8G8B8A8_SNORM;
                } : switch (size) {
                    case 1 -> VK_FORMAT_R8_SINT;
                    case 2 -> VK_FORMAT_R8G8_SINT;
                    case 3 -> VK_FORMAT_R8G8B8_SINT;
                    default -> VK_FORMAT_R8G8B8A8_SINT;
                };
            case 0x1402 -> // GL_SHORT
                normalized ? switch (size) {
                    case 1 -> VK_FORMAT_R16_SNORM;
                    case 2 -> VK_FORMAT_R16G16_SNORM;
                    default -> VK_FORMAT_R16G16B16A16_SNORM;
                } : switch (size) {
                    case 1 -> VK_FORMAT_R16_SINT;
                    case 2 -> VK_FORMAT_R16G16_SINT;
                    default -> VK_FORMAT_R16G16B16A16_SINT;
                };
            case 0x1403 -> // GL_UNSIGNED_SHORT
                normalized ? switch (size) {
                    case 1 -> VK_FORMAT_R16_UNORM;
                    case 2 -> VK_FORMAT_R16G16_UNORM;
                    default -> VK_FORMAT_R16G16B16A16_UNORM;
                } : switch (size) {
                    case 1 -> VK_FORMAT_R16_UINT;
                    case 2 -> VK_FORMAT_R16G16_UINT;
                    default -> VK_FORMAT_R16G16B16A16_UINT;
                };
            case 0x1404 -> // GL_INT
                switch (size) {
                    case 1 -> VK_FORMAT_R32_SINT;
                    case 2 -> VK_FORMAT_R32G32_SINT;
                    case 3 -> VK_FORMAT_R32G32B32_SINT;
                    default -> VK_FORMAT_R32G32B32A32_SINT;
                };
            case 0x1405 -> // GL_UNSIGNED_INT
                switch (size) {
                    case 1 -> VK_FORMAT_R32_UINT;
                    case 2 -> VK_FORMAT_R32G32_UINT;
                    case 3 -> VK_FORMAT_R32G32B32_UINT;
                    default -> VK_FORMAT_R32G32B32A32_UINT;
                };
            case 0x140B -> // GL_HALF_FLOAT
                switch (size) {
                    case 1 -> VK_FORMAT_R16_SFLOAT;
                    case 2 -> VK_FORMAT_R16G16_SFLOAT;
                    default -> VK_FORMAT_R16G16B16A16_SFLOAT;
                };
            case 0x140A -> // GL_DOUBLE
                switch (size) {
                    case 1 -> VK_FORMAT_R64_SFLOAT;
                    case 2 -> VK_FORMAT_R64G64_SFLOAT;
                    case 3 -> VK_FORMAT_R64G64B64_SFLOAT;
                    default -> VK_FORMAT_R64G64B64A64_SFLOAT;
                };
            case 0x8D9F -> // GL_INT_2_10_10_10_REV
                normalized ? VK_FORMAT_A2B10G10R10_SNORM_PACK32 : VK_FORMAT_A2B10G10R10_SINT_PACK32;
            case 0x8368 -> // GL_UNSIGNED_INT_2_10_10_10_REV
                normalized ? VK_FORMAT_A2B10G10R10_UNORM_PACK32 : VK_FORMAT_A2B10G10R10_UINT_PACK32;
            case 0x8C3B -> // GL_UNSIGNED_INT_10F_11F_11F_REV
                VK_FORMAT_B10G11R11_UFLOAT_PACK32;
            default -> VK_FORMAT_R32G32B32A32_SFLOAT;
        };
    }

    // ========================================================================
    // PIPELINE MANAGEMENT
    // ========================================================================
    
    private static long getOrCreatePipeline(int glMode) {
        long program = state.currentProgram;
        if (program == 0) {
            throw new RuntimeException("No program bound");
        }
        
        int topology = translatePrimitiveTopology(glMode);
        int renderPassHash = ctx.renderPass != VK_NULL_HANDLE ? Long.hashCode(ctx.renderPass) : 0;
        PipelineStateKey key = new PipelineStateKey(program, topology, state, renderPassHash);
        
        Long cached = pipelineCache.get(key);
        if (cached != null) {
            return cached;
        }
        
        long pipeline = createPipelineForState(key);
        pipelineCache.put(key, pipeline);
        
        FPSFlux.LOGGER.debug("[VulkanCallMapperX] Created pipeline, cache size: {}", pipelineCache.size());
        
        return pipeline;
    }
    
    private static long getOrCreateComputePipeline(long program) {
        Long cached = computePipelineCache.get(program);
        if (cached != null) {
            return cached;
        }
        
        long pipeline = createComputePipeline(program);
        computePipelineCache.put(program, pipeline);
        
        return pipeline;
    }
    
    private static long createPipelineForState(PipelineStateKey key) {
        VulkanState.ProgramObject progObj = state.getProgram(key.program);
        if (progObj == null || !progObj.linked) {
            throw new RuntimeException("Program not linked: " + key.program);
        }
        
        // Find shaders
        VulkanState.ShaderObject vertShader = null;
        VulkanState.ShaderObject fragShader = null;
        VulkanState.ShaderObject geomShader = null;
        VulkanState.ShaderObject tessControlShader = null;
        VulkanState.ShaderObject tessEvalShader = null;
        
        for (long shaderId : progObj.attachedShaders) {
            VulkanState.ShaderObject shader = state.getShader(shaderId);
            if (shader != null) {
                switch (shader.type) {
                    case 0x8B31 -> vertShader = shader;
                    case 0x8B30 -> fragShader = shader;
                    case 0x8DD9 -> geomShader = shader;
                    case 0x8E88 -> tessControlShader = shader;
                    case 0x8E87 -> tessEvalShader = shader;
                }
            }
        }
        
        if (vertShader == null) {
            throw new RuntimeException("Program must have a vertex shader");
        }
        
        // Count shader stages
        int stageCount = 1; // Vertex
        if (fragShader != null) stageCount++;
        if (geomShader != null) stageCount++;
        if (tessControlShader != null) stageCount++;
        if (tessEvalShader != null) stageCount++;
        
        ByteBuffer entryPoint = memUTF8("main");
        
        VkPipelineShaderStageCreateInfo.Buffer shaderStages = 
            VkPipelineShaderStageCreateInfo.calloc(stageCount);
        
        int stageIdx = 0;
        
        // Vertex shader
        shaderStages.get(stageIdx++)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(VK_SHADER_STAGE_VERTEX_BIT)
            .module(vertShader.module)
            .pName(entryPoint);
        
        // Tessellation control shader
        if (tessControlShader != null) {
            shaderStages.get(stageIdx++)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT)
                .module(tessControlShader.module)
                .pName(entryPoint);
        }
        
        // Tessellation evaluation shader
        if (tessEvalShader != null) {
            shaderStages.get(stageIdx++)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT)
                .module(tessEvalShader.module)
                .pName(entryPoint);
        }
        
        // Geometry shader
        if (geomShader != null) {
            shaderStages.get(stageIdx++)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_GEOMETRY_BIT)
                .module(geomShader.module)
                .pName(entryPoint);
        }
        
        // Fragment shader
        if (fragShader != null) {
            shaderStages.get(stageIdx++)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragShader.module)
                .pName(entryPoint);
        }
        
        // Vertex input state
        VertexInputStateInfo vertexInput = buildVertexInputState();
        
        VkPipelineVertexInputStateCreateInfo vertexInputInfo = 
            VkPipelineVertexInputStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
        
        if (vertexInput.bindings != null) {
            vertexInputInfo.pVertexBindingDescriptions(vertexInput.bindings);
        }
        if (vertexInput.attributes != null) {
            vertexInputInfo.pVertexAttributeDescriptions(vertexInput.attributes);
        }
        
        // Input assembly
        VkPipelineInputAssemblyStateCreateInfo inputAssembly = 
            VkPipelineInputAssemblyStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(key.primitiveTopology)
                .primitiveRestartEnable(key.primitiveRestartEnabled);
        
        // Tessellation state
        VkPipelineTessellationStateCreateInfo tessellationState = null;
        if (tessControlShader != null && tessEvalShader != null) {
            tessellationState = VkPipelineTessellationStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_TESSELLATION_STATE_CREATE_INFO)
                .patchControlPoints(state.getPatchVertices());
        }
        
        // Viewport (dynamic)
        VkPipelineViewportStateCreateInfo viewportState = 
            VkPipelineViewportStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .viewportCount(1)
                .scissorCount(1);
        
        // Rasterization
        VkPipelineRasterizationStateCreateInfo rasterizer = 
            VkPipelineRasterizationStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .depthClampEnable(key.depthClampEnabled)
                .rasterizerDiscardEnable(key.rasterizerDiscardEnabled)
                .polygonMode(translatePolygonMode(key.polygonMode))
                .lineWidth(1.0f)
                .cullMode(key.cullFaceEnabled ? translateCullMode(key.cullFaceMode) : VK_CULL_MODE_NONE)
                .frontFace(translateFrontFace(key.frontFace))
                .depthBiasEnable(key.depthBiasEnabled);
        
        // Multisampling
        VkPipelineMultisampleStateCreateInfo multisampling = 
            VkPipelineMultisampleStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .sampleShadingEnable(key.sampleShadingEnabled)
                .rasterizationSamples(translateSampleCount(key.sampleCount))
                .minSampleShading(key.sampleShadingEnabled ? 0.25f : 0.0f)
                .alphaToCoverageEnable(key.alphaToCoverageEnabled)
                .alphaToOneEnable(key.alphaToOneEnabled);
        
        // Depth stencil
        VkPipelineDepthStencilStateCreateInfo depthStencil = 
            VkPipelineDepthStencilStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                .depthTestEnable(key.depthTestEnabled)
                .depthWriteEnable(key.depthWriteEnabled)
                .depthCompareOp(translateDepthFunc(key.depthFunc))
                .depthBoundsTestEnable(key.depthBoundsTestEnabled)
                .stencilTestEnable(key.stencilTestEnabled);
        
        if (key.stencilTestEnabled) {
            VulkanState.StencilState front = state.getStencilFront();
            VulkanState.StencilState back = state.getStencilBack();
            
            depthStencil.front()
                .failOp(translateStencilOp(front.failOp))
                .passOp(translateStencilOp(front.passOp))
                .depthFailOp(translateStencilOp(front.depthFailOp))
                .compareOp(translateCompareOp(front.compareOp))
                .compareMask(front.compareMask)
                .writeMask(front.writeMask)
                .reference(front.reference);
            
            depthStencil.back()
                .failOp(translateStencilOp(back.failOp))
                .passOp(translateStencilOp(back.passOp))
                .depthFailOp(translateStencilOp(back.depthFailOp))
                .compareOp(translateCompareOp(back.compareOp))
                .compareMask(back.compareMask)
                .writeMask(back.writeMask)
                .reference(back.reference);
        }
        
        // Color blending
        VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = 
            VkPipelineColorBlendAttachmentState.calloc(1);
        
        colorBlendAttachment.get(0)
            .colorWriteMask(key.colorWriteMask)
            .blendEnable(key.blendEnabled);
        
        if (key.blendEnabled) {
            colorBlendAttachment.get(0)
                .srcColorBlendFactor(translateBlendFactor(key.blendSrcRGB))
                .dstColorBlendFactor(translateBlendFactor(key.blendDstRGB))
                .colorBlendOp(translateBlendOp(key.blendOpRGB))
                .srcAlphaBlendFactor(translateBlendFactor(key.blendSrcAlpha))
                .dstAlphaBlendFactor(translateBlendFactor(key.blendDstAlpha))
                .alphaBlendOp(translateBlendOp(key.blendOpAlpha));
        }
        
        VkPipelineColorBlendStateCreateInfo colorBlending = 
            VkPipelineColorBlendStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .logicOpEnable(key.logicOpEnabled)
                .logicOp(key.logicOpEnabled ? translateLogicOp(key.logicOp) : VK_LOGIC_OP_COPY)
                .pAttachments(colorBlendAttachment);
        
        // Dynamic state - use extended dynamic state for Vulkan 1.3+
        IntBuffer dynamicStates;
        if (vulkanVersion >= VULKAN_1_3) {
            dynamicStates = memAllocInt(15);
            dynamicStates.put(VK_DYNAMIC_STATE_VIEWPORT);
            dynamicStates.put(VK_DYNAMIC_STATE_SCISSOR);
            dynamicStates.put(VK_DYNAMIC_STATE_LINE_WIDTH);
            dynamicStates.put(VK_DYNAMIC_STATE_DEPTH_BIAS);
            dynamicStates.put(VK_DYNAMIC_STATE_BLEND_CONSTANTS);
            dynamicStates.put(VK_DYNAMIC_STATE_DEPTH_BOUNDS);
            dynamicStates.put(VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK);
            dynamicStates.put(VK_DYNAMIC_STATE_STENCIL_WRITE_MASK);
            dynamicStates.put(VK_DYNAMIC_STATE_STENCIL_REFERENCE);
            dynamicStates.put(VK_DYNAMIC_STATE_CULL_MODE);
            dynamicStates.put(VK_DYNAMIC_STATE_FRONT_FACE);
            dynamicStates.put(VK_DYNAMIC_STATE_PRIMITIVE_TOPOLOGY);
            dynamicStates.put(VK_DYNAMIC_STATE_DEPTH_TEST_ENABLE);
            dynamicStates.put(VK_DYNAMIC_STATE_DEPTH_WRITE_ENABLE);
            dynamicStates.put(VK_DYNAMIC_STATE_DEPTH_COMPARE_OP);
        } else {
            dynamicStates = memAllocInt(9);
            dynamicStates.put(VK_DYNAMIC_STATE_VIEWPORT);
            dynamicStates.put(VK_DYNAMIC_STATE_SCISSOR);
            dynamicStates.put(VK_DYNAMIC_STATE_LINE_WIDTH);
            dynamicStates.put(VK_DYNAMIC_STATE_DEPTH_BIAS);
            dynamicStates.put(VK_DYNAMIC_STATE_BLEND_CONSTANTS);
            dynamicStates.put(VK_DYNAMIC_STATE_DEPTH_BOUNDS);
            dynamicStates.put(VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK);
            dynamicStates.put(VK_DYNAMIC_STATE_STENCIL_WRITE_MASK);
            dynamicStates.put(VK_DYNAMIC_STATE_STENCIL_REFERENCE);
        }
        dynamicStates.flip();
        
        VkPipelineDynamicStateCreateInfo dynamicState = 
            VkPipelineDynamicStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                .pDynamicStates(dynamicStates);
        
        // Pipeline layout
        long pipelineLayout = progObj.pipelineLayout;
        if (pipelineLayout == VK_NULL_HANDLE) {
            pipelineLayout = createPipelineLayout();
            progObj.pipelineLayout = pipelineLayout;
        }
        
        // Create pipeline
        VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = 
            VkGraphicsPipelineCreateInfo.calloc(1);
        
        pipelineInfo.get(0)
            .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
            .pStages(shaderStages)
            .pVertexInputState(vertexInputInfo)
            .pInputAssemblyState(inputAssembly)
            .pTessellationState(tessellationState)
            .pViewportState(viewportState)
            .pRasterizationState(rasterizer)
            .pMultisampleState(multisampling)
            .pDepthStencilState(depthStencil)
            .pColorBlendState(colorBlending)
            .pDynamicState(dynamicState)
            .layout(pipelineLayout)
            .renderPass(ctx.renderPass)
            .subpass(0);
        
        LongBuffer pPipeline = memAllocLong(1);
        int result = vkCreateGraphicsPipelines(ctx.device, ctx.pipelineCache, pipelineInfo, null, pPipeline);
        
        long pipeline = pPipeline.get(0);
        
        // Cleanup
        memFree(entryPoint);
        shaderStages.free();
        vertexInputInfo.free();
        vertexInput.close();
        inputAssembly.free();
        if (tessellationState != null) tessellationState.free();
        viewportState.free();
        rasterizer.free();
        multisampling.free();
        depthStencil.free();
        colorBlendAttachment.free();
        colorBlending.free();
        memFree(dynamicStates);
        dynamicState.free();
        pipelineInfo.free();
        memFree(pPipeline);
        
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create graphics pipeline: " + result);
        }
        
        return pipeline;
    }
    
    private static long createComputePipeline(long program) {
        VulkanState.ProgramObject progObj = state.getProgram(program);
        if (progObj == null || !progObj.linked) {
            throw new RuntimeException("Program not linked: " + program);
        }
        
        // Find compute shader
        VulkanState.ShaderObject computeShader = null;
        for (long shaderId : progObj.attachedShaders) {
            VulkanState.ShaderObject shader = state.getShader(shaderId);
            if (shader != null && shader.type == 0x91B9) { // GL_COMPUTE_SHADER
                computeShader = shader;
                break;
            }
        }
        
        if (computeShader == null) {
            throw new RuntimeException("Program has no compute shader");
        }
        
        ByteBuffer entryPoint = memUTF8("main");
        
        VkPipelineShaderStageCreateInfo shaderStage = VkPipelineShaderStageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(VK_SHADER_STAGE_COMPUTE_BIT)
            .module(computeShader.module)
            .pName(entryPoint);
        
        long pipelineLayout = progObj.pipelineLayout;
        if (pipelineLayout == VK_NULL_HANDLE) {
            pipelineLayout = createPipelineLayout();
            progObj.pipelineLayout = pipelineLayout;
        }
        
        VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1);
        pipelineInfo.get(0)
            .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
            .stage(shaderStage)
            .layout(pipelineLayout);
        
        LongBuffer pPipeline = memAllocLong(1);
        int result = vkCreateComputePipelines(ctx.device, ctx.pipelineCache, pipelineInfo, null, pPipeline);
        
        long pipeline = pPipeline.get(0);
        
        memFree(entryPoint);
        shaderStage.free();
        pipelineInfo.free();
        memFree(pPipeline);
        
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create compute pipeline: " + result);
        }
        
        progObj.computePipeline = pipeline;
        
        return pipeline;
    }
    
    private static long createPipelineLayout() {
        // Push constant range
        VkPushConstantRange.Buffer pushConstantRange = VkPushConstantRange.calloc(1);
        pushConstantRange.get(0)
            .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT)
            .offset(0)
            .size(256); // 256 bytes of push constants
        
        LongBuffer pSetLayouts = memAllocLong(1);
        pSetLayouts.put(0, ctx.descriptorSetLayout);
        
        VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
            .pSetLayouts(pSetLayouts)
            .pPushConstantRanges(pushConstantRange);
        
        LongBuffer pLayout = memAllocLong(1);
        int result = vkCreatePipelineLayout(ctx.device, layoutInfo, null, pLayout);
        
        long layout = pLayout.get(0);
        
        pushConstantRange.free();
        memFree(pSetLayouts);
        layoutInfo.free();
        memFree(pLayout);
        
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create pipeline layout: " + result);
        }
        
        return layout;
    }
    
    // ========================================================================
    // FRAME MANAGEMENT
    // ========================================================================
    
    private static void beginFrame() {
        if (recordingCommands) return;
        
        // Wait for previous frame using this slot
        LongBuffer pFence = memAllocLong(1);
        pFence.put(0, ctx.inFlightFence);
        vkWaitForFences(ctx.device, pFence, true, Long.MAX_VALUE);
        memFree(pFence);
        
        // Acquire next image
        IntBuffer pImageIndex = memAllocInt(1);
        int result = vkAcquireNextImageKHR(ctx.device, ctx.swapchain, Long.MAX_VALUE, 
            ctx.imageAvailableSemaphore, VK_NULL_HANDLE, pImageIndex);
        
        if (result == VK_ERROR_OUT_OF_DATE_KHR) {
            ctx.recreateSwapchain();
            memFree(pImageIndex);
            return;
        } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
            memFree(pImageIndex);
            throw new RuntimeException("Failed to acquire swapchain image: " + result);
        }
        
        ctx.currentImageIndex = pImageIndex.get(0);
        memFree(pImageIndex);
        
        // Reset fence
        pFence = memAllocLong(1);
        pFence.put(0, ctx.inFlightFence);
        vkResetFences(ctx.device, pFence);
        memFree(pFence);
        
        // Begin command buffer
        currentCommandBuffer = ctx.getCurrentCommandBuffer();
        vkResetCommandBuffer(currentCommandBuffer, 0);
        
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        
        result = vkBeginCommandBuffer(currentCommandBuffer, beginInfo);
        beginInfo.free();
        
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to begin command buffer: " + result);
        }
        
        // Begin render pass or dynamic rendering
        if (supportsDynamicRendering) {
            beginDynamicRendering();
        } else {
            beginRenderPass();
        }
        
        // Set initial dynamic state
        setInitialDynamicState();
        
        recordingCommands = true;
        currentDescriptorSetIndex = (currentDescriptorSetIndex + 1) % MAX_DESCRIPTOR_SETS;
        currentFrameIndex = (currentFrameIndex + 1) % MAX_FRAMES_IN_FLIGHT;
    }
    
    private static void beginRenderPass() {
        VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
            .renderPass(ctx.renderPass)
            .framebuffer(ctx.getCurrentFramebuffer());

        renderPassInfo.renderArea().offset().set(0, 0);
        renderPassInfo.renderArea().extent().set(ctx.swapchainExtent.width(), ctx.swapchainExtent.height());
        
        VkClearValue.Buffer clearValues = VkClearValue.calloc(2);
        float[] cc = state.getClearColor();
        clearValues.get(0).color().float32(0, cc[0]).float32(1, cc[1]).float32(2, cc[2]).float32(3, cc[3]);
        clearValues.get(1).depthStencil().set(state.getClearDepth(), state.getClearStencil());
        renderPassInfo.pClearValues(clearValues);
        
        vkCmdBeginRenderPass(currentCommandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        
        renderPassInfo.free();
        clearValues.free();
    }
    
    private static void beginDynamicRendering() {
        // Vulkan 1.3+ dynamic rendering
        VkRenderingAttachmentInfo.Buffer colorAttachment = VkRenderingAttachmentInfo.calloc(1);
        float[] cc = state.getClearColor();
        colorAttachment.get(0)
            .sType(VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO)
            .imageView(ctx.getCurrentSwapchainImageView())
            .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        colorAttachment.get(0).clearValue().color().float32(0, cc[0]).float32(1, cc[1]).float32(2, cc[2]).float32(3, cc[3]);
        
        VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO)
            .imageView(ctx.getDepthImageView())
            .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
        depthAttachment.clearValue().depthStencil().set(state.getClearDepth(), state.getClearStencil());
        
        VkRenderingInfo renderingInfo = VkRenderingInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_RENDERING_INFO)
            .layerCount(1)
            .pColorAttachments(colorAttachment)
            .pDepthAttachment(depthAttachment);
        renderingInfo.renderArea().offset().set(0, 0);
        renderingInfo.renderArea().extent().set(ctx.swapchainExtent.width(), ctx.swapchainExtent.height());
        
        vkCmdBeginRendering(currentCommandBuffer, renderingInfo);
        
        colorAttachment.free();
        depthAttachment.free();
        renderingInfo.free();
    }
    
    private static void setInitialDynamicState() {
        // Viewport
        VkViewport.Buffer viewport = VkViewport.calloc(1)
            .x(0.0f).y(0.0f)
            .width(ctx.swapchainExtent.width())
            .height(ctx.swapchainExtent.height())
            .minDepth(0.0f).maxDepth(1.0f);
        vkCmdSetViewport(currentCommandBuffer, 0, viewport);
        viewport.free();
        
        // Scissor
        VkRect2D.Buffer scissor = VkRect2D.calloc(1);
        scissor.offset().set(0, 0);
        scissor.extent().set(ctx.swapchainExtent.width(), ctx.swapchainExtent.height());
        vkCmdSetScissor(currentCommandBuffer, 0, scissor);
        scissor.free();
        
        // Line width
        vkCmdSetLineWidth(currentCommandBuffer, state.getLineWidth());
        
        // Depth bias
        if (state.isDepthBiasEnabled()) {
            vkCmdSetDepthBias(currentCommandBuffer, 
                state.getDepthBiasConstant(), 
                state.getDepthBiasClamp(), 
                state.getDepthBiasSlope());
        }
        
        // Blend constants
        float[] blendConst = state.getBlendConstants();
        vkCmdSetBlendConstants(currentCommandBuffer, blendConst);
        
        // Depth bounds
        if (state.isDepthBoundsTestEnabled()) {
            vkCmdSetDepthBounds(currentCommandBuffer, 
                state.getDepthBoundsMin(), 
                state.getDepthBoundsMax());
        }
        
        // Stencil state
        if (state.isStencilTestEnabled()) {
            VulkanState.StencilState front = state.getStencilFront();
            VulkanState.StencilState back = state.getStencilBack();
            vkCmdSetStencilCompareMask(currentCommandBuffer, VK_STENCIL_FACE_FRONT_BIT, front.compareMask);
            vkCmdSetStencilCompareMask(currentCommandBuffer, VK_STENCIL_FACE_BACK_BIT, back.compareMask);
            vkCmdSetStencilWriteMask(currentCommandBuffer, VK_STENCIL_FACE_FRONT_BIT, front.writeMask);
            vkCmdSetStencilWriteMask(currentCommandBuffer, VK_STENCIL_FACE_BACK_BIT, back.writeMask);
            vkCmdSetStencilReference(currentCommandBuffer, VK_STENCIL_FACE_FRONT_BIT, front.reference);
            vkCmdSetStencilReference(currentCommandBuffer, VK_STENCIL_FACE_BACK_BIT, back.reference);
        }
        
        // Vulkan 1.3+ extended dynamic state
        if (vulkanVersion >= VULKAN_1_3) {
            vkCmdSetCullMode(currentCommandBuffer, 
                state.isCullFaceEnabled() ? translateCullMode(state.getCullFaceMode()) : VK_CULL_MODE_NONE);
            vkCmdSetFrontFace(currentCommandBuffer, translateFrontFace(state.getFrontFace()));
            vkCmdSetDepthTestEnable(currentCommandBuffer, state.isDepthTestEnabled());
            vkCmdSetDepthWriteEnable(currentCommandBuffer, state.isDepthWriteEnabled());
            vkCmdSetDepthCompareOp(currentCommandBuffer, translateDepthFunc(state.getDepthFunc()));
        }
    }
    
    public static void endFrame() {
        if (!recordingCommands) return;
        
        // End render pass or dynamic rendering
        if (supportsDynamicRendering) {
            vkCmdEndRendering(currentCommandBuffer);
        } else {
            vkCmdEndRenderPass(currentCommandBuffer);
        }
        
        int result = vkEndCommandBuffer(currentCommandBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to end command buffer: " + result);
        }
        
        // Submit using synchronization2 if available
        if (supportsSynchronization2) {
            submitWithSync2();
        } else {
            submitTraditional();
        }
        
        recordingCommands = false;
    }
    
    private static void submitTraditional() {
        VkSubmitInfo submitInfo = VkSubmitInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
        
        LongBuffer waitSemaphores = memAllocLong(1);
        waitSemaphores.put(0, ctx.imageAvailableSemaphore);
        
        IntBuffer waitStages = memAllocInt(1);
        waitStages.put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
        
        submitInfo.pWaitSemaphores(waitSemaphores);
        submitInfo.pWaitDstStageMask(waitStages);
        
        PointerBuffer pCmdBuffers = memAllocPointer(1);
        pCmdBuffers.put(0, currentCommandBuffer.address());
        submitInfo.pCommandBuffers(pCmdBuffers);
        
        LongBuffer signalSemaphores = memAllocLong(1);
        signalSemaphores.put(0, ctx.renderFinishedSemaphore);
        submitInfo.pSignalSemaphores(signalSemaphores);
        
        int result = vkQueueSubmit(ctx.graphicsQueue, submitInfo, ctx.inFlightFence);
        
        if (result != VK_SUCCESS) {
            FPSFlux.LOGGER.error("[VulkanCallMapperX] Queue submit failed: {}", result);
        }
        
        // Present
        VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc()
            .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            .pWaitSemaphores(signalSemaphores);
        
        LongBuffer swapchains = memAllocLong(1);
        swapchains.put(0, ctx.swapchain);
        
        IntBuffer imageIndices = memAllocInt(1);
        imageIndices.put(0, ctx.currentImageIndex);
        
        presentInfo.pSwapchains(swapchains);
        presentInfo.pImageIndices(imageIndices);
        
        result = vkQueuePresentKHR(ctx.presentQueue, presentInfo);
        
        if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
            ctx.recreateSwapchain();
        }
        
        submitInfo.free();
        memFree(waitSemaphores);
        memFree(waitStages);
        memFree(pCmdBuffers);
        memFree(signalSemaphores);
        presentInfo.free();
        memFree(swapchains);
        memFree(imageIndices);
    }
    
    private static void submitWithSync2() {
        // Vulkan 1.3+ synchronization2
        VkSemaphoreSubmitInfo.Buffer waitSemaphoreInfo = VkSemaphoreSubmitInfo.calloc(1);
        waitSemaphoreInfo.get(0)
            .sType(VK_STRUCTURE_TYPE_SEMAPHORE_SUBMIT_INFO)
            .semaphore(ctx.imageAvailableSemaphore)
            .stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT);
        
        VkSemaphoreSubmitInfo.Buffer signalSemaphoreInfo = VkSemaphoreSubmitInfo.calloc(1);
        signalSemaphoreInfo.get(0)
            .sType(VK_STRUCTURE_TYPE_SEMAPHORE_SUBMIT_INFO)
            .semaphore(ctx.renderFinishedSemaphore)
            .stageMask(VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT);
        
        VkCommandBufferSubmitInfo.Buffer cmdBufferInfo = VkCommandBufferSubmitInfo.calloc(1);
        cmdBufferInfo.get(0)
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_SUBMIT_INFO)
            .commandBuffer(currentCommandBuffer);
        
        VkSubmitInfo2.Buffer submitInfo = VkSubmitInfo2.calloc(1);
        submitInfo.get(0)
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO_2)
            .pWaitSemaphoreInfos(waitSemaphoreInfo)
            .pCommandBufferInfos(cmdBufferInfo)
            .pSignalSemaphoreInfos(signalSemaphoreInfo);
        
        int result = vkQueueSubmit2(ctx.graphicsQueue, submitInfo, ctx.inFlightFence);
        
        if (result != VK_SUCCESS) {
            FPSFlux.LOGGER.error("[VulkanCallMapperX] Queue submit2 failed: {}", result);
        }
        
        // Present (same as traditional)
        LongBuffer signalSemaphores = memAllocLong(1);
        signalSemaphores.put(0, ctx.renderFinishedSemaphore);
        
        VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc()
            .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            .pWaitSemaphores(signalSemaphores);
        
        LongBuffer swapchains = memAllocLong(1);
        swapchains.put(0, ctx.swapchain);
        
        IntBuffer imageIndices = memAllocInt(1);
        imageIndices.put(0, ctx.currentImageIndex);
        
        presentInfo.pSwapchains(swapchains);
        presentInfo.pImageIndices(imageIndices);
        
        result = vkQueuePresentKHR(ctx.presentQueue, presentInfo);
        
        if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
            ctx.recreateSwapchain();
        }
        
        waitSemaphoreInfo.free();
        signalSemaphoreInfo.free();
        cmdBufferInfo.free();
        submitInfo.free();
        memFree(signalSemaphores);
        presentInfo.free();
        memFree(swapchains);
        memFree(imageIndices);
    }
    
    // ========================================================================
    // DRAW CALLS
    // ========================================================================
    
    public static void drawArrays(int mode, int first, int count) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindDescriptorSets();
        
        vkCmdDraw(currentCommandBuffer, count, 1, first, 0);
    }
    
    public static void drawElements(int mode, int count, int type, long indices) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindCurrentIndexBuffer(type, indices);
        bindDescriptorSets();
        
        vkCmdDrawIndexed(currentCommandBuffer, count, 1, 0, 0, 0);
    }
    
    public static void drawArraysInstanced(int mode, int first, int count, int instanceCount) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindDescriptorSets();
        
        vkCmdDraw(currentCommandBuffer, count, instanceCount, first, 0);
    }
    
    public static void drawElementsInstanced(int mode, int count, int type, long indices, int instanceCount) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindCurrentIndexBuffer(type, indices);
        bindDescriptorSets();
        
        vkCmdDrawIndexed(currentCommandBuffer, count, instanceCount, 0, 0, 0);
    }
    
    public static void drawArraysInstancedBaseInstance(int mode, int first, int count, 
                                                        int instanceCount, int baseInstance) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindDescriptorSets();
        
        vkCmdDraw(currentCommandBuffer, count, instanceCount, first, baseInstance);
    }
    
    public static void drawElementsInstancedBaseVertexBaseInstance(int mode, int count, int type,
                                                                    long indices, int instanceCount,
                                                                    int baseVertex, int baseInstance) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindCurrentIndexBuffer(type, indices);
        bindDescriptorSets();
        
        vkCmdDrawIndexed(currentCommandBuffer, count, instanceCount, 0, baseVertex, baseInstance);
    }

    // ========================================================================
    // INDIRECT DRAWING
    // ========================================================================
    
    public static void drawArraysIndirect(int mode, long indirectBuffer, long offset) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindDescriptorSets();
        
        VulkanState.BufferObject indirectBuf = state.getBuffer(indirectBuffer);
        if (indirectBuf != null && indirectBuf.buffer != VK_NULL_HANDLE) {
            vkCmdDrawIndirect(currentCommandBuffer, indirectBuf.buffer, offset, 1, 0);
        }
    }
    
    public static void drawElementsIndirect(int mode, int type, long indirectBuffer, long offset) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindCurrentIndexBuffer(type, 0);
        bindDescriptorSets();
        
        VulkanState.BufferObject indirectBuf = state.getBuffer(indirectBuffer);
        if (indirectBuf != null && indirectBuf.buffer != VK_NULL_HANDLE) {
            vkCmdDrawIndexedIndirect(currentCommandBuffer, indirectBuf.buffer, offset, 1, 0);
        }
    }
    
    public static void multiDrawArraysIndirect(int mode, long indirectBuffer, long offset, 
                                                int drawCount, int stride) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindDescriptorSets();
        
        VulkanState.BufferObject indirectBuf = state.getBuffer(indirectBuffer);
        if (indirectBuf != null && indirectBuf.buffer != VK_NULL_HANDLE) {
            vkCmdDrawIndirect(currentCommandBuffer, indirectBuf.buffer, offset, drawCount, stride);
        }
    }
    
    public static void multiDrawElementsIndirect(int mode, int type, long indirectBuffer, 
                                                  long offset, int drawCount, int stride) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindCurrentIndexBuffer(type, 0);
        bindDescriptorSets();
        
        VulkanState.BufferObject indirectBuf = state.getBuffer(indirectBuffer);
        if (indirectBuf != null && indirectBuf.buffer != VK_NULL_HANDLE) {
            vkCmdDrawIndexedIndirect(currentCommandBuffer, indirectBuf.buffer, offset, drawCount, stride);
        }
    }
    
    // Vulkan 1.2+ indirect count
    public static void drawArraysIndirectCount(int mode, long indirectBuffer, long indirectOffset,
                                                long countBuffer, long countOffset, int maxDrawCount, int stride) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        if (vulkanVersion < VULKAN_1_2) {
            FPSFlux.LOGGER.warn("[VulkanCallMapperX] drawArraysIndirectCount requires Vulkan 1.2+");
            return;
        }
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindDescriptorSets();
        
        VulkanState.BufferObject indirectBuf = state.getBuffer(indirectBuffer);
        VulkanState.BufferObject countBuf = state.getBuffer(countBuffer);
        
        if (indirectBuf != null && indirectBuf.buffer != VK_NULL_HANDLE &&
            countBuf != null && countBuf.buffer != VK_NULL_HANDLE) {
            vkCmdDrawIndirectCount(currentCommandBuffer, indirectBuf.buffer, indirectOffset,
                countBuf.buffer, countOffset, maxDrawCount, stride);
        }
    }
    
    public static void drawElementsIndirectCount(int mode, int type, long indirectBuffer, long indirectOffset,
                                                  long countBuffer, long countOffset, int maxDrawCount, int stride) {
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        if (vulkanVersion < VULKAN_1_2) {
            FPSFlux.LOGGER.warn("[VulkanCallMapperX] drawElementsIndirectCount requires Vulkan 1.2+");
            return;
        }
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        bindCurrentVertexBuffers();
        bindCurrentIndexBuffer(type, 0);
        bindDescriptorSets();
        
        VulkanState.BufferObject indirectBuf = state.getBuffer(indirectBuffer);
        VulkanState.BufferObject countBuf = state.getBuffer(countBuffer);
        
        if (indirectBuf != null && indirectBuf.buffer != VK_NULL_HANDLE &&
            countBuf != null && countBuf.buffer != VK_NULL_HANDLE) {
            vkCmdDrawIndexedIndirectCount(currentCommandBuffer, indirectBuf.buffer, indirectOffset,
                countBuf.buffer, countOffset, maxDrawCount, stride);
        }
    }
    
    // ========================================================================
    // DRAW HELPER METHODS
    // ========================================================================
    
    private static void bindCurrentVertexBuffers() {
        int bindingCount = state.getVertexBindingCount();
        if (bindingCount == 0) bindingCount = 1;
        
        LongBuffer pBuffers = memAllocLong(bindingCount);
        LongBuffer pOffsets = memAllocLong(bindingCount);
        
        boolean hasAny = false;
        for (int i = 0; i < bindingCount; i++) {
            long vbo = state.getBoundVertexBuffer(i);
            if (vbo != 0) {
                VulkanState.BufferObject bufObj = state.getBuffer(vbo);
                if (bufObj != null && bufObj.buffer != VK_NULL_HANDLE) {
                    pBuffers.put(i, bufObj.buffer);
                    pOffsets.put(i, state.getVertexBufferOffset(i));
                    hasAny = true;
                } else {
                    pBuffers.put(i, VK_NULL_HANDLE);
                    pOffsets.put(i, 0);
                }
            } else {
                // Fallback to GL_ARRAY_BUFFER
                long arrayBuffer = state.getBoundBuffer(0x8892);
                if (arrayBuffer != 0) {
                    VulkanState.BufferObject bufObj = state.getBuffer(arrayBuffer);
                    if (bufObj != null && bufObj.buffer != VK_NULL_HANDLE) {
                        pBuffers.put(i, bufObj.buffer);
                        pOffsets.put(i, 0);
                        hasAny = true;
                    } else {
                        pBuffers.put(i, VK_NULL_HANDLE);
                        pOffsets.put(i, 0);
                    }
                } else {
                    pBuffers.put(i, VK_NULL_HANDLE);
                    pOffsets.put(i, 0);
                }
            }
        }
        
        if (hasAny) {
            vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
        }
        
        memFree(pBuffers);
        memFree(pOffsets);
    }
    
    private static void bindCurrentIndexBuffer(int type, long offset) {
        long ibo = state.getBoundBuffer(0x8893); // GL_ELEMENT_ARRAY_BUFFER
        if (ibo != 0) {
            VulkanState.BufferObject bufObj = state.getBuffer(ibo);
            if (bufObj != null && bufObj.buffer != VK_NULL_HANDLE) {
                int indexType = (type == 0x1405) ? VK_INDEX_TYPE_UINT32 : 
                               (type == 0x1401) ? VK_INDEX_TYPE_UINT8_EXT : VK_INDEX_TYPE_UINT16;
                vkCmdBindIndexBuffer(currentCommandBuffer, bufObj.buffer, offset, indexType);
            }
        }
    }
    
    private static void bindDescriptorSets() {
        long ds = descriptorSets[currentDescriptorSetIndex];
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        
        if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE && ds != VK_NULL_HANDLE) {
            LongBuffer pSets = memAllocLong(1);
            pSets.put(0, ds);
            vkCmdBindDescriptorSets(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                prog.pipelineLayout, 0, pSets, null);
            memFree(pSets);
        }
    }
    
    private static void bindDescriptorSetsCompute() {
        long ds = descriptorSets[currentDescriptorSetIndex];
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        
        if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE && ds != VK_NULL_HANDLE) {
            LongBuffer pSets = memAllocLong(1);
            pSets.put(0, ds);
            vkCmdBindDescriptorSets(currentCommandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                prog.pipelineLayout, 0, pSets, null);
            memFree(pSets);
        }
    }
    
    // ========================================================================
    // COMMAND QUEUE (DEFERRED EXECUTION)
    // ========================================================================
    
    public static void queueDrawArrays(int mode, int first, int count) {
        checkInitialized();
        
        GPUCommand cmd = new GPUCommand();
        cmd.type = CommandType.DRAW_ARRAYS;
        cmd.mode = mode;
        cmd.first = first;
        cmd.count = count;
        cmd.instanceCount = 1;
        cmd.pipeline = getOrCreatePipeline(mode);
        cmd.descriptorSet = descriptorSets[currentDescriptorSetIndex];
        
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        if (prog != null) cmd.pipelineLayout = prog.pipelineLayout;
        
        captureVertexBufferBindings(cmd);
        
        commandQueue.add(cmd);
        if (commandQueue.size() >= MAX_COMMANDS_PER_BATCH) flushCommands();
    }
    
    public static void queueDrawElements(int mode, int count, int type, long indices) {
        checkInitialized();
        
        GPUCommand cmd = new GPUCommand();
        cmd.type = CommandType.DRAW_ELEMENTS;
        cmd.mode = mode;
        cmd.count = count;
        cmd.indexType = (type == 0x1405) ? VK_INDEX_TYPE_UINT32 : VK_INDEX_TYPE_UINT16;
        cmd.indices = indices;
        cmd.instanceCount = 1;
        cmd.pipeline = getOrCreatePipeline(mode);
        cmd.descriptorSet = descriptorSets[currentDescriptorSetIndex];
        
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        if (prog != null) cmd.pipelineLayout = prog.pipelineLayout;
        
        captureVertexBufferBindings(cmd);
        captureIndexBufferBinding(cmd, indices);
        
        commandQueue.add(cmd);
        if (commandQueue.size() >= MAX_COMMANDS_PER_BATCH) flushCommands();
    }
    
    public static void queueDrawArraysInstanced(int mode, int first, int count, int instanceCount) {
        checkInitialized();
        
        GPUCommand cmd = new GPUCommand();
        cmd.type = CommandType.DRAW_ARRAYS_INSTANCED;
        cmd.mode = mode;
        cmd.first = first;
        cmd.count = count;
        cmd.instanceCount = instanceCount;
        cmd.pipeline = getOrCreatePipeline(mode);
        cmd.descriptorSet = descriptorSets[currentDescriptorSetIndex];
        
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        if (prog != null) cmd.pipelineLayout = prog.pipelineLayout;
        
        captureVertexBufferBindings(cmd);
        
        commandQueue.add(cmd);
        if (commandQueue.size() >= MAX_COMMANDS_PER_BATCH) flushCommands();
    }
    
    public static void queueDrawElementsInstanced(int mode, int count, int type, long indices, int instanceCount) {
        checkInitialized();
        
        GPUCommand cmd = new GPUCommand();
        cmd.type = CommandType.DRAW_ELEMENTS_INSTANCED;
        cmd.mode = mode;
        cmd.count = count;
        cmd.indexType = (type == 0x1405) ? VK_INDEX_TYPE_UINT32 : VK_INDEX_TYPE_UINT16;
        cmd.indices = indices;
        cmd.instanceCount = instanceCount;
        cmd.pipeline = getOrCreatePipeline(mode);
        cmd.descriptorSet = descriptorSets[currentDescriptorSetIndex];
        
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        if (prog != null) cmd.pipelineLayout = prog.pipelineLayout;
        
        captureVertexBufferBindings(cmd);
        captureIndexBufferBinding(cmd, indices);
        
        commandQueue.add(cmd);
        if (commandQueue.size() >= MAX_COMMANDS_PER_BATCH) flushCommands();
    }
    
    public static void queueDrawArraysIndirect(int mode, long indirectBuffer, long offset, 
                                                int drawCount, int stride) {
        checkInitialized();
        
        GPUCommand cmd = new GPUCommand();
        cmd.type = CommandType.MULTI_DRAW_ARRAYS_INDIRECT;
        cmd.mode = mode;
        cmd.indirectBuffer = indirectBuffer;
        cmd.indirectOffset = offset;
        cmd.drawCount = drawCount;
        cmd.stride = stride;
        cmd.pipeline = getOrCreatePipeline(mode);
        cmd.descriptorSet = descriptorSets[currentDescriptorSetIndex];
        
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        if (prog != null) cmd.pipelineLayout = prog.pipelineLayout;
        
        captureVertexBufferBindings(cmd);
        
        commandQueue.add(cmd);
        if (commandQueue.size() >= MAX_COMMANDS_PER_BATCH) flushCommands();
    }
    
    public static void queueDispatchCompute(int groupCountX, int groupCountY, int groupCountZ) {
        checkInitialized();
        
        GPUCommand cmd = new GPUCommand();
        cmd.type = CommandType.DISPATCH_COMPUTE;
        cmd.groupCountX = groupCountX;
        cmd.groupCountY = groupCountY;
        cmd.groupCountZ = groupCountZ;
        cmd.pipeline = state.getCurrentComputePipeline();
        cmd.descriptorSet = descriptorSets[currentDescriptorSetIndex];
        
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        if (prog != null) cmd.pipelineLayout = prog.pipelineLayout;
        
        commandQueue.add(cmd);
        if (commandQueue.size() >= MAX_COMMANDS_PER_BATCH) flushCommands();
    }
    
    public static void queueCopyBuffer(long srcBuffer, long dstBuffer, long srcOffset, 
                                        long dstOffset, long size) {
        checkInitialized();
        
        GPUCommand cmd = new GPUCommand();
        cmd.type = CommandType.COPY_BUFFER;
        cmd.srcBuffer = srcBuffer;
        cmd.dstBuffer = dstBuffer;
        cmd.srcOffset = srcOffset;
        cmd.dstOffset = dstOffset;
        cmd.size = size;
        
        commandQueue.add(cmd);
    }
    
    public static void queueMemoryBarrier(int srcAccessMask, int dstAccessMask,
                                           int srcStageMask, int dstStageMask) {
        checkInitialized();
        
        GPUCommand cmd = new GPUCommand();
        cmd.type = CommandType.MEMORY_BARRIER;
        cmd.srcAccessMask = srcAccessMask;
        cmd.dstAccessMask = dstAccessMask;
        cmd.srcStageMask = srcStageMask;
        cmd.dstStageMask = dstStageMask;
        
        commandQueue.add(cmd);
    }
    
    private static void captureVertexBufferBindings(GPUCommand cmd) {
        int bindingCount = Math.max(1, state.getVertexBindingCount());
        cmd.vertexBuffers = new long[bindingCount];
        cmd.vertexOffsets = new long[bindingCount];
        
        for (int i = 0; i < bindingCount; i++) {
            long vbo = state.getBoundVertexBuffer(i);
            if (vbo == 0) vbo = state.getBoundBuffer(0x8892);
            
            if (vbo != 0) {
                VulkanState.BufferObject bufObj = state.getBuffer(vbo);
                if (bufObj != null && bufObj.buffer != VK_NULL_HANDLE) {
                    cmd.vertexBuffers[i] = bufObj.buffer;
                    cmd.vertexOffsets[i] = state.getVertexBufferOffset(i);
                }
            }
        }
    }
    
    private static void captureIndexBufferBinding(GPUCommand cmd, long offset) {
        long ibo = state.getBoundBuffer(0x8893);
        if (ibo != 0) {
            VulkanState.BufferObject bufObj = state.getBuffer(ibo);
            if (bufObj != null && bufObj.buffer != VK_NULL_HANDLE) {
                cmd.indexBuffer = bufObj.buffer;
                cmd.indexOffset = offset;
            }
        }
    }
    
    public static void flushCommands() {
        if (commandQueue.isEmpty()) return;
        
        checkInitialized();
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        GPUCommand cmd;
        while ((cmd = commandQueue.poll()) != null) {
            executeCommand(cmd);
        }
    }
    
    private static void executeCommand(GPUCommand cmd) {
        switch (cmd.type) {
            case DRAW_ARRAYS -> {
                vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipeline);
                bindVertexBuffersFromCommand(cmd);
                bindDescriptorSetFromCommand(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS);
                vkCmdDraw(currentCommandBuffer, cmd.count, cmd.instanceCount, cmd.first, cmd.baseInstance);
            }
            case DRAW_ELEMENTS -> {
                vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipeline);
                bindVertexBuffersFromCommand(cmd);
                if (cmd.indexBuffer != VK_NULL_HANDLE) {
                    vkCmdBindIndexBuffer(currentCommandBuffer, cmd.indexBuffer, cmd.indexOffset, cmd.indexType);
                }
                bindDescriptorSetFromCommand(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS);
                vkCmdDrawIndexed(currentCommandBuffer, cmd.count, cmd.instanceCount, 0, cmd.baseVertex, cmd.baseInstance);
            }
            case DRAW_ARRAYS_INSTANCED -> {
                vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipeline);
                bindVertexBuffersFromCommand(cmd);
                bindDescriptorSetFromCommand(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS);
                vkCmdDraw(currentCommandBuffer, cmd.count, cmd.instanceCount, cmd.first, cmd.baseInstance);
            }
            case DRAW_ELEMENTS_INSTANCED -> {
                vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipeline);
                bindVertexBuffersFromCommand(cmd);
                if (cmd.indexBuffer != VK_NULL_HANDLE) {
                    vkCmdBindIndexBuffer(currentCommandBuffer, cmd.indexBuffer, cmd.indexOffset, cmd.indexType);
                }
                bindDescriptorSetFromCommand(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS);
                vkCmdDrawIndexed(currentCommandBuffer, cmd.count, cmd.instanceCount, 0, cmd.baseVertex, cmd.baseInstance);
            }
            case DRAW_ARRAYS_INDIRECT, MULTI_DRAW_ARRAYS_INDIRECT -> {
                vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipeline);
                bindVertexBuffersFromCommand(cmd);
                bindDescriptorSetFromCommand(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS);
                VulkanState.BufferObject indirectBuf = state.getBuffer(cmd.indirectBuffer);
                if (indirectBuf != null && indirectBuf.buffer != VK_NULL_HANDLE) {
                    vkCmdDrawIndirect(currentCommandBuffer, indirectBuf.buffer, cmd.indirectOffset, 
                        cmd.drawCount, cmd.stride);
                }
            }
            case DRAW_ELEMENTS_INDIRECT, MULTI_DRAW_ELEMENTS_INDIRECT -> {
                vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipeline);
                bindVertexBuffersFromCommand(cmd);
                if (cmd.indexBuffer != VK_NULL_HANDLE) {
                    vkCmdBindIndexBuffer(currentCommandBuffer, cmd.indexBuffer, cmd.indexOffset, cmd.indexType);
                }
                bindDescriptorSetFromCommand(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS);
                VulkanState.BufferObject indirectBuf = state.getBuffer(cmd.indirectBuffer);
                if (indirectBuf != null && indirectBuf.buffer != VK_NULL_HANDLE) {
                    vkCmdDrawIndexedIndirect(currentCommandBuffer, indirectBuf.buffer, cmd.indirectOffset,
                        cmd.drawCount, cmd.stride);
                }
            }
            case DISPATCH_COMPUTE -> {
                if (cmd.pipeline != VK_NULL_HANDLE) {
                    vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, cmd.pipeline);
                    bindDescriptorSetFromCommand(cmd, VK_PIPELINE_BIND_POINT_COMPUTE);
                    vkCmdDispatch(currentCommandBuffer, cmd.groupCountX, cmd.groupCountY, cmd.groupCountZ);
                }
            }
            case COPY_BUFFER -> {
                VkBufferCopy.Buffer region = VkBufferCopy.calloc(1)
                    .srcOffset(cmd.srcOffset)
                    .dstOffset(cmd.dstOffset)
                    .size(cmd.size);
                vkCmdCopyBuffer(currentCommandBuffer, cmd.srcBuffer, cmd.dstBuffer, region);
                region.free();
            }
            case MEMORY_BARRIER -> {
                if (supportsSynchronization2) {
                    executeMemoryBarrierSync2(cmd);
                } else {
                    executeMemoryBarrierLegacy(cmd);
                }
            }
            case PUSH_CONSTANTS -> {
                if (cmd.pipelineLayout != VK_NULL_HANDLE && cmd.pushConstantData != null) {
                    ByteBuffer data = memAlloc(cmd.pushConstantData.length);
                    data.put(cmd.pushConstantData);
                    data.flip();
                    vkCmdPushConstants(currentCommandBuffer, cmd.pipelineLayout,
                        cmd.pushConstantStageFlags, cmd.pushConstantOffset, data);
                    memFree(data);
                }
            }
            case SET_VIEWPORT -> {
                VkViewport.Buffer viewport = VkViewport.calloc(1)
                    .x(cmd.viewportX).y(cmd.viewportY)
                    .width(cmd.viewportWidth).height(cmd.viewportHeight)
                    .minDepth(cmd.viewportMinDepth).maxDepth(cmd.viewportMaxDepth);
                vkCmdSetViewport(currentCommandBuffer, 0, viewport);
                viewport.free();
            }
            case SET_SCISSOR -> {
                VkRect2D.Buffer scissor = VkRect2D.calloc(1);
                scissor.offset().set(cmd.scissorX, cmd.scissorY);
                scissor.extent().set(cmd.scissorWidth, cmd.scissorHeight);
                vkCmdSetScissor(currentCommandBuffer, 0, scissor);
                scissor.free();
            }
            case SET_LINE_WIDTH -> {
                vkCmdSetLineWidth(currentCommandBuffer, cmd.lineWidth);
            }
            case SET_DEPTH_BIAS -> {
                vkCmdSetDepthBias(currentCommandBuffer, cmd.depthBiasConstant, 
                    cmd.depthBiasClamp, cmd.depthBiasSlope);
            }
            case SET_BLEND_CONSTANTS -> {
                if (cmd.blendConstants != null) {
                    vkCmdSetBlendConstants(currentCommandBuffer, cmd.blendConstants);
                }
            }
            case SET_DEPTH_BOUNDS -> {
                vkCmdSetDepthBounds(currentCommandBuffer, cmd.depthBoundsMin, cmd.depthBoundsMax);
            }
            case SET_STENCIL_COMPARE_MASK -> {
                vkCmdSetStencilCompareMask(currentCommandBuffer, cmd.stencilFaceMask, cmd.stencilCompareMask);
            }
            case SET_STENCIL_WRITE_MASK -> {
                vkCmdSetStencilWriteMask(currentCommandBuffer, cmd.stencilFaceMask, cmd.stencilWriteMask);
            }
            case SET_STENCIL_REFERENCE -> {
                vkCmdSetStencilReference(currentCommandBuffer, cmd.stencilFaceMask, cmd.stencilReference);
            }
            // Vulkan 1.3+ dynamic state
            case SET_CULL_MODE -> {
                if (vulkanVersion >= VULKAN_1_3) {
                    vkCmdSetCullMode(currentCommandBuffer, cmd.intValue);
                }
            }
            case SET_FRONT_FACE -> {
                if (vulkanVersion >= VULKAN_1_3) {
                    vkCmdSetFrontFace(currentCommandBuffer, cmd.intValue);
                }
            }
            case SET_PRIMITIVE_TOPOLOGY -> {
                if (vulkanVersion >= VULKAN_1_3) {
                    vkCmdSetPrimitiveTopology(currentCommandBuffer, cmd.intValue);
                }
            }
            case SET_DEPTH_TEST_ENABLE -> {
                if (vulkanVersion >= VULKAN_1_3) {
                    vkCmdSetDepthTestEnable(currentCommandBuffer, cmd.boolValue);
                }
            }
            case SET_DEPTH_WRITE_ENABLE -> {
                if (vulkanVersion >= VULKAN_1_3) {
                    vkCmdSetDepthWriteEnable(currentCommandBuffer, cmd.boolValue);
                }
            }
            case SET_DEPTH_COMPARE_OP -> {
                if (vulkanVersion >= VULKAN_1_3) {
                    vkCmdSetDepthCompareOp(currentCommandBuffer, cmd.intValue);
                }
            }
        }
    }
    
    private static void bindVertexBuffersFromCommand(GPUCommand cmd) {
        if (cmd.vertexBuffers == null || cmd.vertexBuffers.length == 0) return;
        
        LongBuffer pBuffers = memAllocLong(cmd.vertexBuffers.length);
        LongBuffer pOffsets = memAllocLong(cmd.vertexOffsets.length);
        
        for (int i = 0; i < cmd.vertexBuffers.length; i++) {
            pBuffers.put(i, cmd.vertexBuffers[i]);
            pOffsets.put(i, cmd.vertexOffsets[i]);
        }
        
        vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
        
        memFree(pBuffers);
        memFree(pOffsets);
    }
    
    private static void bindDescriptorSetFromCommand(GPUCommand cmd, int bindPoint) {
        if (cmd.descriptorSet != VK_NULL_HANDLE && cmd.pipelineLayout != VK_NULL_HANDLE) {
            LongBuffer pSets = memAllocLong(1);
            pSets.put(0, cmd.descriptorSet);
            vkCmdBindDescriptorSets(currentCommandBuffer, bindPoint, cmd.pipelineLayout, 0, pSets, null);
            memFree(pSets);
        }
    }
    
    // ========================================================================
    // COMPUTE DISPATCH
    // ========================================================================
    
    public static void dispatchCompute(int groupsX, int groupsY, int groupsZ) {
        checkInitialized();
        
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        
        long computePipeline = state.getCurrentComputePipeline();
        if (computePipeline == VK_NULL_HANDLE) {
            // Try to create one
            if (state.currentProgram != 0) {
                computePipeline = getOrCreateComputePipeline(state.currentProgram);
            }
        }
        
        if (computePipeline != VK_NULL_HANDLE) {
            vkCmdBindPipeline(cmdBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
            
            VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
            if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE) {
                long ds = descriptorSets[currentDescriptorSetIndex];
                LongBuffer pSets = memAllocLong(1);
                pSets.put(0, ds);
                vkCmdBindDescriptorSets(cmdBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                    prog.pipelineLayout, 0, pSets, null);
                memFree(pSets);
            }
            
            vkCmdDispatch(cmdBuffer, groupsX, groupsY, groupsZ);
        }
        
        ctx.endSingleTimeCommands(cmdBuffer);
    }
    
    public static void dispatchComputeIndirect(long indirectBuffer, long offset) {
        checkInitialized();
        
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        
        long computePipeline = state.getCurrentComputePipeline();
        if (computePipeline == VK_NULL_HANDLE && state.currentProgram != 0) {
            computePipeline = getOrCreateComputePipeline(state.currentProgram);
        }
        
        if (computePipeline != VK_NULL_HANDLE) {
            vkCmdBindPipeline(cmdBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
            
            VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
            if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE) {
                long ds = descriptorSets[currentDescriptorSetIndex];
                LongBuffer pSets = memAllocLong(1);
                pSets.put(0, ds);
                vkCmdBindDescriptorSets(cmdBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                    prog.pipelineLayout, 0, pSets, null);
                memFree(pSets);
            }
            
            VulkanState.BufferObject indirectBuf = state.getBuffer(indirectBuffer);
            if (indirectBuf != null && indirectBuf.buffer != VK_NULL_HANDLE) {
                vkCmdDispatchIndirect(cmdBuffer, indirectBuf.buffer, offset);
            }
        }
        
        ctx.endSingleTimeCommands(cmdBuffer);
    }

    // ========================================================================
    // SYNCHRONIZATION
    // ========================================================================
    
    public static long fenceSync(int condition, int flags) {
        checkInitialized();
        
        if (supportsTimelineSemaphores && timelineSemaphore != VK_NULL_HANDLE) {
            long signalValue = timelineValue.incrementAndGet();
            
            if (vulkanVersion >= VULKAN_1_2) {
                VkSemaphoreSignalInfo signalInfo = VkSemaphoreSignalInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_SIGNAL_INFO)
                    .semaphore(timelineSemaphore)
                    .value(signalValue);
                
                vkSignalSemaphore(ctx.device, signalInfo);
                signalInfo.free();
            }
            
            // Encode as timeline value
            return 0x8000000000000000L | signalValue;
        } else {
            // Fallback to fence
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            
            LongBuffer pFence = memAllocLong(1);
            vkCreateFence(ctx.device, fenceInfo, null, pFence);
            long fence = pFence.get(0);
            
            fenceInfo.free();
            memFree(pFence);
            
            VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
            ctx.endSingleTimeCommandsWithFence(cmdBuffer, fence);
            
            return fence;
        }
    }
    
    public static int clientWaitSync(long sync, int flags, long timeout) {
        checkInitialized();
        
        if ((sync & 0x8000000000000000L) != 0 && supportsTimelineSemaphores) {
            long waitValue = sync & 0x7FFFFFFFFFFFFFFFL;
            
            if (vulkanVersion >= VULKAN_1_2) {
                LongBuffer pSemaphores = memAllocLong(1);
                LongBuffer pValues = memAllocLong(1);
                pSemaphores.put(0, timelineSemaphore);
                pValues.put(0, waitValue);
                
                VkSemaphoreWaitInfo waitInfo = VkSemaphoreWaitInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_WAIT_INFO)
                    .pSemaphores(pSemaphores)
                    .pValues(pValues);
                
                int result = vkWaitSemaphores(ctx.device, waitInfo, timeout);
                
                waitInfo.free();
                memFree(pSemaphores);
                memFree(pValues);
                
                return switch (result) {
                    case VK_SUCCESS -> 0x911A; // GL_ALREADY_SIGNALED
                    case VK_TIMEOUT -> 0x911B; // GL_TIMEOUT_EXPIRED
                    default -> 0x911D; // GL_WAIT_FAILED
                };
            }
        }
        
        // Fence fallback
        LongBuffer pFence = memAllocLong(1);
        pFence.put(0, sync);
        int result = vkWaitForFences(ctx.device, pFence, true, timeout);
        memFree(pFence);
        
        return switch (result) {
            case VK_SUCCESS -> 0x911A;
            case VK_TIMEOUT -> 0x911B;
            default -> 0x911D;
        };
    }
    
    public static int getSyncStatus(long sync) {
        checkInitialized();
        
        if ((sync & 0x8000000000000000L) != 0 && supportsTimelineSemaphores) {
            long checkValue = sync & 0x7FFFFFFFFFFFFFFFL;
            
            if (vulkanVersion >= VULKAN_1_2) {
                LongBuffer pValue = memAllocLong(1);
                vkGetSemaphoreCounterValue(ctx.device, timelineSemaphore, pValue);
                long currentValue = pValue.get(0);
                memFree(pValue);
                
                return currentValue >= checkValue ? 0x9119 : 0x9118; // GL_SIGNALED : GL_UNSIGNALED
            }
        }
        
        // Fence fallback
        LongBuffer pFence = memAllocLong(1);
        pFence.put(0, sync);
        int result = vkGetFenceStatus(ctx.device, sync);
        memFree(pFence);
        
        return result == VK_SUCCESS ? 0x9119 : 0x9118;
    }
    
    public static void waitSync(long sync, int flags, long timeout) {
        checkInitialized();
        
        // GPU-side wait - insert barrier
        if (!recordingCommands) beginFrame();
        if (!recordingCommands) return;
        
        if (supportsSynchronization2) {
            VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1);
            barrier.get(0)
                .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER_2)
                .srcStageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                .srcAccessMask(VK_ACCESS_2_MEMORY_WRITE_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
                .dstAccessMask(VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT);
            
            VkDependencyInfo depInfo = VkDependencyInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_DEPENDENCY_INFO)
                .pMemoryBarriers(barrier);
            
            vkCmdPipelineBarrier2(currentCommandBuffer, depInfo);
            
            barrier.free();
            depInfo.free();
        } else {
            VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1)
                .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                .srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
            
            vkCmdPipelineBarrier(currentCommandBuffer,
                VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                0, barrier, null, null);
            
            barrier.free();
        }
    }
    
    public static void deleteSync(long sync) {
        checkInitialized();
        
        if ((sync & 0x8000000000000000L) == 0 && sync != VK_NULL_HANDLE) {
            vkDestroyFence(ctx.device, sync, null);
        }
    }
    
    public static void finish() {
        checkInitialized();
        if (recordingCommands) endFrame();
        flushCommands();
        vkDeviceWaitIdle(ctx.device);
    }
    
    public static void flush() {
        checkInitialized();
        flushCommands();
    }
    
    // ========================================================================
    // MEMORY BARRIERS
    // ========================================================================
    
    public static void memoryBarrier(int barriers) {
        checkInitialized();
        
        if (recordingCommands) {
            if (supportsSynchronization2) {
                insertMemoryBarrierSync2(barriers);
            } else {
                insertMemoryBarrierLegacy(barriers);
            }
        } else {
            VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
            if (supportsSynchronization2) {
                insertMemoryBarrierSync2InCmd(cmdBuffer, barriers);
            } else {
                insertMemoryBarrierLegacyInCmd(cmdBuffer, barriers);
            }
            ctx.endSingleTimeCommands(cmdBuffer);
        }
    }
    
    private static void insertMemoryBarrierSync2(int barriers) {
        long srcStage = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT;
        long dstStage = VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT;
        long srcAccess = 0;
        long dstAccess = 0;
        
        if ((barriers & 0x00000001) != 0) { // GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT
            srcAccess |= VK_ACCESS_2_VERTEX_ATTRIBUTE_READ_BIT;
            dstAccess |= VK_ACCESS_2_VERTEX_ATTRIBUTE_READ_BIT;
        }
        if ((barriers & 0x00000002) != 0) { // GL_ELEMENT_ARRAY_BARRIER_BIT
            srcAccess |= VK_ACCESS_2_INDEX_READ_BIT;
            dstAccess |= VK_ACCESS_2_INDEX_READ_BIT;
        }
        if ((barriers & 0x00000004) != 0) { // GL_UNIFORM_BARRIER_BIT
            srcAccess |= VK_ACCESS_2_UNIFORM_READ_BIT;
            dstAccess |= VK_ACCESS_2_UNIFORM_READ_BIT;
        }
        if ((barriers & 0x00000008) != 0) { // GL_TEXTURE_FETCH_BARRIER_BIT
            srcAccess |= VK_ACCESS_2_SHADER_SAMPLED_READ_BIT;
            dstAccess |= VK_ACCESS_2_SHADER_SAMPLED_READ_BIT;
        }
        if ((barriers & 0x00000020) != 0) { // GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
            srcAccess |= VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT;
            dstAccess |= VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT;
        }
        if ((barriers & 0x00000080) != 0) { // GL_COMMAND_BARRIER_BIT
            srcAccess |= VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT;
            dstAccess |= VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT;
        }
        if ((barriers & 0x00000400) != 0) { // GL_BUFFER_UPDATE_BARRIER_BIT
            srcAccess |= VK_ACCESS_2_TRANSFER_WRITE_BIT;
            dstAccess |= VK_ACCESS_2_TRANSFER_READ_BIT;
        }
        if ((barriers & 0x00002000) != 0) { // GL_SHADER_STORAGE_BARRIER_BIT
            srcAccess |= VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT;
            dstAccess |= VK_ACCESS_2_SHADER_STORAGE_READ_BIT;
        }
        if (barriers == 0xFFFFFFFF) { // GL_ALL_BARRIER_BITS
            srcAccess = VK_ACCESS_2_MEMORY_WRITE_BIT;
            dstAccess = VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT;
        }
        
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1);
        barrier.get(0)
            .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER_2)
            .srcStageMask(srcStage)
            .srcAccessMask(srcAccess)
            .dstStageMask(dstStage)
            .dstAccessMask(dstAccess);
        
        VkDependencyInfo depInfo = VkDependencyInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DEPENDENCY_INFO)
            .pMemoryBarriers(barrier);
        
        vkCmdPipelineBarrier2(currentCommandBuffer, depInfo);
        
        barrier.free();
        depInfo.free();
    }
    
    private static void insertMemoryBarrierLegacy(int barriers) {
        insertMemoryBarrierLegacyInCmd(currentCommandBuffer, barriers);
    }
    
    private static void insertMemoryBarrierSync2InCmd(VkCommandBuffer cmdBuffer, int barriers) {
        // Same as insertMemoryBarrierSync2 but uses provided cmdBuffer
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1);
        barrier.get(0)
            .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER_2)
            .srcStageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
            .srcAccessMask(VK_ACCESS_2_MEMORY_WRITE_BIT)
            .dstStageMask(VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT)
            .dstAccessMask(VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT);
        
        VkDependencyInfo depInfo = VkDependencyInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DEPENDENCY_INFO)
            .pMemoryBarriers(barrier);
        
        vkCmdPipelineBarrier2(cmdBuffer, depInfo);
        
        barrier.free();
        depInfo.free();
    }
    
    private static void insertMemoryBarrierLegacyInCmd(VkCommandBuffer cmdBuffer, int barriers) {
        int srcStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        int dstStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        int srcAccess = 0;
        int dstAccess = 0;
        
        if ((barriers & 0x00000001) != 0) {
            srcAccess |= VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT;
            dstAccess |= VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT;
        }
        if ((barriers & 0x00000002) != 0) {
            srcAccess |= VK_ACCESS_INDEX_READ_BIT;
            dstAccess |= VK_ACCESS_INDEX_READ_BIT;
        }
        if ((barriers & 0x00000004) != 0) {
            srcAccess |= VK_ACCESS_UNIFORM_READ_BIT;
            dstAccess |= VK_ACCESS_UNIFORM_READ_BIT;
        }
        if ((barriers & 0x00000008) != 0) {
            srcAccess |= VK_ACCESS_SHADER_READ_BIT;
            dstAccess |= VK_ACCESS_SHADER_READ_BIT;
        }
        if ((barriers & 0x00000020) != 0) {
            srcAccess |= VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
            dstAccess |= VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
        }
        if ((barriers & 0x00000080) != 0) {
            srcAccess |= VK_ACCESS_INDIRECT_COMMAND_READ_BIT;
            dstAccess |= VK_ACCESS_INDIRECT_COMMAND_READ_BIT;
        }
        if ((barriers & 0x00000400) != 0) {
            srcAccess |= VK_ACCESS_TRANSFER_WRITE_BIT;
            dstAccess |= VK_ACCESS_TRANSFER_READ_BIT;
        }
        if ((barriers & 0x00002000) != 0) {
            srcAccess |= VK_ACCESS_SHADER_WRITE_BIT;
            dstAccess |= VK_ACCESS_SHADER_READ_BIT;
        }
        if (barriers == 0xFFFFFFFF) {
            srcAccess = VK_ACCESS_MEMORY_WRITE_BIT;
            dstAccess = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
        }
        
        VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1)
            .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
            .srcAccessMask(srcAccess)
            .dstAccessMask(dstAccess);
        
        vkCmdPipelineBarrier(cmdBuffer, srcStage, dstStage, 0, barrier, null, null);
        barrier.free();
    }
    
    private static void executeMemoryBarrierSync2(GPUCommand cmd) {
        VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1);
        barrier.get(0)
            .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER_2)
            .srcStageMask(cmd.srcStageMask)
            .srcAccessMask(cmd.srcAccessMask)
            .dstStageMask(cmd.dstStageMask)
            .dstAccessMask(cmd.dstAccessMask);
        
        VkDependencyInfo depInfo = VkDependencyInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DEPENDENCY_INFO)
            .pMemoryBarriers(barrier);
        
        vkCmdPipelineBarrier2(currentCommandBuffer, depInfo);
        
        barrier.free();
        depInfo.free();
    }
    
    private static void executeMemoryBarrierLegacy(GPUCommand cmd) {
        VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1)
            .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
            .srcAccessMask(cmd.srcAccessMask)
            .dstAccessMask(cmd.dstAccessMask);
        
        vkCmdPipelineBarrier(currentCommandBuffer, cmd.srcStageMask, cmd.dstStageMask, 
            0, barrier, null, null);
        barrier.free();
    }
    
    // ========================================================================
    // STATE MANAGEMENT
    // ========================================================================
    
    public static void enable(int cap) { checkInitialized(); state.enable(cap); }
    public static void disable(int cap) { checkInitialized(); state.disable(cap); }
    
    public static void blendFunc(int src, int dst) { 
        checkInitialized(); 
        state.setBlendFunc(src, dst); 
    }
    
    public static void blendFuncSeparate(int srcRGB, int dstRGB, int srcA, int dstA) { 
        checkInitialized(); 
        state.setBlendFuncSeparate(srcRGB, dstRGB, srcA, dstA); 
    }
    
    public static void blendEquation(int mode) {
        checkInitialized();
        state.setBlendEquation(mode);
    }
    
    public static void blendEquationSeparate(int modeRGB, int modeAlpha) {
        checkInitialized();
        state.setBlendEquationSeparate(modeRGB, modeAlpha);
    }
    
    public static void blendColor(float r, float g, float b, float a) {
        checkInitialized();
        state.setBlendColor(r, g, b, a);
    }
    
    public static void depthFunc(int func) { 
        checkInitialized(); 
        state.setDepthFunc(func); 
    }
    
    public static void depthMask(boolean flag) { 
        checkInitialized(); 
        state.setDepthMask(flag); 
    }
    
    public static void depthRange(double near, double far) {
        checkInitialized();
        state.setDepthRange((float) near, (float) far);
    }
    
    public static void cullFace(int mode) { 
        checkInitialized(); 
        state.setCullFace(mode); 
    }
    
    public static void frontFace(int mode) { 
        checkInitialized(); 
        state.setFrontFace(mode); 
    }
    
    public static void polygonMode(int face, int mode) { 
        checkInitialized(); 
        state.setPolygonMode(face, mode); 
    }
    
    public static void polygonOffset(float factor, float units) {
        checkInitialized();
        state.setPolygonOffset(factor, units);
    }
    
    public static void lineWidth(float width) {
        checkInitialized();
        state.setLineWidth(width);
    }
    
    public static void pointSize(float size) {
        checkInitialized();
        state.setPointSize(size);
    }
    
    public static void colorMask(boolean r, boolean g, boolean b, boolean a) {
        checkInitialized();
        state.setColorMask(r, g, b, a);
    }
    
    public static void stencilFunc(int func, int ref, int mask) {
        checkInitialized();
        state.setStencilFunc(func, ref, mask);
    }
    
    public static void stencilFuncSeparate(int face, int func, int ref, int mask) {
        checkInitialized();
        state.setStencilFuncSeparate(face, func, ref, mask);
    }
    
    public static void stencilOp(int sfail, int dpfail, int dppass) {
        checkInitialized();
        state.setStencilOp(sfail, dpfail, dppass);
    }
    
    public static void stencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        checkInitialized();
        state.setStencilOpSeparate(face, sfail, dpfail, dppass);
    }
    
    public static void stencilMask(int mask) {
        checkInitialized();
        state.setStencilMask(mask);
    }
    
    public static void stencilMaskSeparate(int face, int mask) {
        checkInitialized();
        state.setStencilMaskSeparate(face, mask);
    }
    
    public static void logicOp(int op) {
        checkInitialized();
        state.setLogicOp(op);
    }
    
    public static void sampleCoverage(float value, boolean invert) {
        checkInitialized();
        state.setSampleCoverage(value, invert);
    }
    
    public static void minSampleShading(float value) {
        checkInitialized();
        state.setMinSampleShading(value);
    }
    
    public static void patchParameteri(int pname, int value) {
        checkInitialized();
        if (pname == 0x8E72) { // GL_PATCH_VERTICES
            state.setPatchVertices(value);
        }
    }
    
    public static void clear(int mask) { 
        checkInitialized(); 
        state.markClearRequested(mask); 
    }
    
    public static void clearColor(float r, float g, float b, float a) { 
        checkInitialized(); 
        state.setClearColor(r, g, b, a); 
    }
    
    public static void clearDepth(double depth) { 
        checkInitialized(); 
        state.setClearDepth((float) depth); 
    }
    
    public static void clearStencil(int s) {
        checkInitialized();
        state.setClearStencil(s);
    }
    
    public static void viewport(int x, int y, int w, int h) { 
        checkInitialized(); 
        state.setViewport(x, y, w, h); 
    }
    
    public static void scissor(int x, int y, int w, int h) { 
        checkInitialized(); 
        state.setScissor(x, y, w, h); 
    }

    // ========================================================================
    // BUFFER OPERATIONS
    // ========================================================================
    
    public static long genBuffer() {
        checkInitialized();
        return state.registerBuffer(VK_NULL_HANDLE, VK_NULL_HANDLE, 0);
    }
    
    public static void genBuffers(int n, long[] buffers) {
        checkInitialized();
        for (int i = 0; i < n; i++) {
            buffers[i] = state.registerBuffer(VK_NULL_HANDLE, VK_NULL_HANDLE, 0);
        }
    }
    
    public static void bindBuffer(int target, long buffer) {
        checkInitialized();
        state.bindBuffer(target, buffer);
    }
    
    public static void bindBufferBase(int target, int index, long buffer) {
        checkInitialized();
        state.bindBufferBase(target, index, buffer);
    }
    
    public static void bindBufferRange(int target, int index, long buffer, long offset, long size) {
        checkInitialized();
        state.bindBufferRange(target, index, buffer, offset, size);
    }
    
    public static void bufferData(int target, long size, ByteBuffer data, int usage) {
        checkInitialized();
        
        long bufferId = state.getBoundBuffer(target);
        if (bufferId == 0) throw new RuntimeException("No buffer bound to target: 0x" + Integer.toHexString(target));
        
        VulkanState.BufferObject bufObj = state.getBuffer(bufferId);
        
        // Destroy old buffer
        if (bufObj.buffer != VK_NULL_HANDLE) {
            vkDeviceWaitIdle(ctx.device);
            vkDestroyBuffer(ctx.device, bufObj.buffer, null);
            if (bufObj.memory != VK_NULL_HANDLE) {
                vkFreeMemory(ctx.device, bufObj.memory, null);
            }
        }
        
        // Create new buffer
        int vkUsage = translateBufferUsage(target);
        if (supportsBufferDeviceAddress) {
            vkUsage |= VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
        }
        
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(size)
            .usage(vkUsage | VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        
        LongBuffer pBuffer = memAllocLong(1);
        int result = vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
        if (result != VK_SUCCESS) {
            bufferInfo.free();
            memFree(pBuffer);
            throw new RuntimeException("Failed to create buffer: " + result);
        }
        long buffer = pBuffer.get(0);
        
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(ctx.device, buffer, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
        
        LongBuffer pMemory = memAllocLong(1);
        result = vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        if (result != VK_SUCCESS) {
            vkDestroyBuffer(ctx.device, buffer, null);
            bufferInfo.free();
            memReqs.free();
            allocInfo.free();
            memFree(pBuffer);
            memFree(pMemory);
            throw new RuntimeException("Failed to allocate buffer memory: " + result);
        }
        long memory = pMemory.get(0);
        
        vkBindBufferMemory(ctx.device, buffer, memory, 0);
        
        // Upload data
        if (data != null && data.remaining() > 0) {
            uploadBufferData(buffer, size, data);
        }
        
        bufObj.buffer = buffer;
        bufObj.memory = memory;
        bufObj.size = size;
        
        bufferInfo.free();
        memReqs.free();
        allocInfo.free();
        memFree(pBuffer);
        memFree(pMemory);
    }
    
    public static void bufferSubData(int target, long offset, ByteBuffer data) {
        checkInitialized();
        
        long bufferId = state.getBoundBuffer(target);
        if (bufferId == 0) throw new RuntimeException("No buffer bound");
        
        VulkanState.BufferObject bufObj = state.getBuffer(bufferId);
        if (bufObj.buffer == VK_NULL_HANDLE) throw new RuntimeException("Buffer not created");
        
        uploadBufferDataPartial(bufObj.buffer, offset, data);
    }
    
    public static void deleteBuffer(long buffer) {
        checkInitialized();
        if (buffer == 0) return;
        
        VulkanState.BufferObject bufObj = state.getBuffer(buffer);
        if (bufObj == null) return;
        
        vkDeviceWaitIdle(ctx.device);
        
        if (bufObj.buffer != VK_NULL_HANDLE) vkDestroyBuffer(ctx.device, bufObj.buffer, null);
        if (bufObj.memory != VK_NULL_HANDLE) vkFreeMemory(ctx.device, bufObj.memory, null);
        
        state.unregisterBuffer(buffer);
    }
    
    public static void deleteBuffers(int n, long[] buffers) {
        for (int i = 0; i < n; i++) {
            deleteBuffer(buffers[i]);
        }
    }
    
    public static ByteBuffer mapBuffer(int target, int access) {
        checkInitialized();
        
        long bufferId = state.getBoundBuffer(target);
        if (bufferId == 0) return null;
        
        VulkanState.BufferObject bufObj = state.getBuffer(bufferId);
        if (bufObj.buffer == VK_NULL_HANDLE) return null;
        
        return ctx.mapMemory(bufObj.memory, 0, bufObj.size);
    }
    
    public static ByteBuffer mapBufferRange(int target, long offset, long length, int access) {
        checkInitialized();
        
        long bufferId = state.getBoundBuffer(target);
        if (bufferId == 0) return null;
        
        VulkanState.BufferObject bufObj = state.getBuffer(bufferId);
        if (bufObj.buffer == VK_NULL_HANDLE) return null;
        
        return ctx.mapMemory(bufObj.memory, offset, length);
    }
    
    public static boolean unmapBuffer(int target) {
        checkInitialized();
        
        long bufferId = state.getBoundBuffer(target);
        if (bufferId == 0) return false;
        
        VulkanState.BufferObject bufObj = state.getBuffer(bufferId);
        if (bufObj.memory == VK_NULL_HANDLE) return false;
        
        vkUnmapMemory(ctx.device, bufObj.memory);
        return true;
    }
    
    public static void copyBufferSubData(int readTarget, int writeTarget, 
                                          long readOffset, long writeOffset, long size) {
        checkInitialized();
        
        long srcId = state.getBoundBuffer(readTarget);
        long dstId = state.getBoundBuffer(writeTarget);
        
        if (srcId == 0 || dstId == 0) return;
        
        VulkanState.BufferObject srcBuf = state.getBuffer(srcId);
        VulkanState.BufferObject dstBuf = state.getBuffer(dstId);
        
        if (srcBuf.buffer == VK_NULL_HANDLE || dstBuf.buffer == VK_NULL_HANDLE) return;
        
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        
        VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1)
            .srcOffset(readOffset)
            .dstOffset(writeOffset)
            .size(size);
        
        vkCmdCopyBuffer(cmdBuffer, srcBuf.buffer, dstBuf.buffer, copyRegion);
        
        ctx.endSingleTimeCommands(cmdBuffer);
        copyRegion.free();
    }
    
    private static void uploadBufferData(long dstBuffer, long size, ByteBuffer data) {
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(size)
            .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        
        LongBuffer pBuffer = memAllocLong(1);
        vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
        long stagingBuffer = pBuffer.get(0);
        
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(ctx.device, stagingBuffer, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(), 
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
        
        LongBuffer pMemory = memAllocLong(1);
        vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        long stagingMemory = pMemory.get(0);
        
        vkBindBufferMemory(ctx.device, stagingBuffer, stagingMemory, 0);
        
        ByteBuffer mapped = ctx.mapMemory(stagingMemory, 0, size);
        mapped.put(data);
        data.rewind();
        vkUnmapMemory(ctx.device, stagingMemory);
        
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1).srcOffset(0).dstOffset(0).size(size);
        vkCmdCopyBuffer(cmdBuffer, stagingBuffer, dstBuffer, copyRegion);
        ctx.endSingleTimeCommands(cmdBuffer);
        
        vkDestroyBuffer(ctx.device, stagingBuffer, null);
        vkFreeMemory(ctx.device, stagingMemory, null);
        
        bufferInfo.free();
        memReqs.free();
        allocInfo.free();
        memFree(pBuffer);
        memFree(pMemory);
        copyRegion.free();
    }
    
    private static void uploadBufferDataPartial(long dstBuffer, long offset, ByteBuffer data) {
        long size = data.remaining();
        
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(size)
            .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        
        LongBuffer pBuffer = memAllocLong(1);
        vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
        long stagingBuffer = pBuffer.get(0);
        
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(ctx.device, stagingBuffer, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(), 
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
        
        LongBuffer pMemory = memAllocLong(1);
        vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        long stagingMemory = pMemory.get(0);
        
        vkBindBufferMemory(ctx.device, stagingBuffer, stagingMemory, 0);
        
        ByteBuffer mapped = ctx.mapMemory(stagingMemory, 0, size);
        mapped.put(data);
        data.rewind();
        vkUnmapMemory(ctx.device, stagingMemory);
        
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1).srcOffset(0).dstOffset(offset).size(size);
        vkCmdCopyBuffer(cmdBuffer, stagingBuffer, dstBuffer, copyRegion);
        ctx.endSingleTimeCommands(cmdBuffer);
        
        vkDestroyBuffer(ctx.device, stagingBuffer, null);
        vkFreeMemory(ctx.device, stagingMemory, null);
        
        bufferInfo.free();
        memReqs.free();
        allocInfo.free();
        memFree(pBuffer);
        memFree(pMemory);
        copyRegion.free();
    }
    
    // ========================================================================
    // TEXTURE OPERATIONS
    // ========================================================================
    
    public static long genTexture() {
        checkInitialized();
        long sampler = createDefaultSampler();
        return state.registerTexture(VK_NULL_HANDLE, VK_NULL_HANDLE, VK_NULL_HANDLE, sampler);
    }
    
    public static void genTextures(int n, long[] textures) {
        for (int i = 0; i < n; i++) {
            textures[i] = genTexture();
        }
    }
    
    public static void bindTexture(int target, long texture) {
        checkInitialized();
        if (texture == 0) {
            state.unbindTexture(state.activeTextureUnit);
        } else {
            state.bindTexture(state.activeTextureUnit, texture);
        }
    }
    
    public static void activeTexture(int unit) {
        checkInitialized();
        state.activeTextureUnit = unit - 0x84C0; // GL_TEXTURE0
    }
    
    public static void deleteTexture(long texture) {
        checkInitialized();
        if (texture == 0) return;
        
        VulkanState.TextureObject texObj = state.getTexture(texture);
        if (texObj == null) return;
        
        vkDeviceWaitIdle(ctx.device);
        
        if (texObj.sampler != VK_NULL_HANDLE) vkDestroySampler(ctx.device, texObj.sampler, null);
        if (texObj.imageView != VK_NULL_HANDLE) vkDestroyImageView(ctx.device, texObj.imageView, null);
        if (texObj.image != VK_NULL_HANDLE) vkDestroyImage(ctx.device, texObj.image, null);
        if (texObj.memory != VK_NULL_HANDLE) vkFreeMemory(ctx.device, texObj.memory, null);
        
        state.unregisterTexture(texture);
    }
    
    public static void deleteTextures(int n, long[] textures) {
        for (int i = 0; i < n; i++) {
            deleteTexture(textures[i]);
        }
    }
    
    public static void texParameteri(int target, int pname, int param) {
        checkInitialized();
        long texture = state.getBoundTexture(state.activeTextureUnit);
        if (texture == 0) return;
        
        VulkanState.TextureObject texObj = state.getTexture(texture);
        if (texObj == null) return;
        
        texObj.setParameter(pname, param);
        recreateSamplerIfNeeded(texObj);
    }
    
    public static void texParameterf(int target, int pname, float param) {
        checkInitialized();
        long texture = state.getBoundTexture(state.activeTextureUnit);
        if (texture == 0) return;
        
        VulkanState.TextureObject texObj = state.getTexture(texture);
        if (texObj == null) return;
        
        texObj.setParameterf(pname, param);
        recreateSamplerIfNeeded(texObj);
    }
    
    private static void recreateSamplerIfNeeded(VulkanState.TextureObject texObj) {
        if (texObj.sampler != VK_NULL_HANDLE) {
            vkDeviceWaitIdle(ctx.device);
            vkDestroySampler(ctx.device, texObj.sampler, null);
        }
        texObj.sampler = createSamplerForTexture(texObj);
    }
    
    private static long createDefaultSampler() {
        VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
            .magFilter(VK_FILTER_LINEAR)
            .minFilter(VK_FILTER_LINEAR)
            .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .anisotropyEnable(ctx.supportsAnisotropy())
            .maxAnisotropy(ctx.supportsAnisotropy() ? ctx.getMaxAnisotropy() : 1.0f)
            .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
            .unnormalizedCoordinates(false)
            .compareEnable(false)
            .compareOp(VK_COMPARE_OP_ALWAYS)
            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
            .mipLodBias(0.0f)
            .minLod(0.0f)
            .maxLod(VK_LOD_CLAMP_NONE);
        
        LongBuffer pSampler = memAllocLong(1);
        vkCreateSampler(ctx.device, samplerInfo, null, pSampler);
        long sampler = pSampler.get(0);
        
        samplerInfo.free();
        memFree(pSampler);
        
        return sampler;
    }
    
    private static long createSamplerForTexture(VulkanState.TextureObject texObj) {
        int magFilter = translateFilter(texObj.getParameteri(0x2800)); // GL_TEXTURE_MAG_FILTER
        int minFilter = translateFilter(texObj.getParameteri(0x2801)); // GL_TEXTURE_MIN_FILTER
        int wrapS = translateWrap(texObj.getParameteri(0x2802));       // GL_TEXTURE_WRAP_S
        int wrapT = translateWrap(texObj.getParameteri(0x2803));       // GL_TEXTURE_WRAP_T
        int wrapR = translateWrap(texObj.getParameteri(0x8072));       // GL_TEXTURE_WRAP_R
        
        float maxAniso = texObj.getParameterf(0x84FE); // GL_TEXTURE_MAX_ANISOTROPY
        boolean anisotropyEnabled = maxAniso > 1.0f && ctx.supportsAnisotropy();
        
        VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
            .magFilter(magFilter)
            .minFilter(minFilter)
            .addressModeU(wrapS)
            .addressModeV(wrapT)
            .addressModeW(wrapR)
            .anisotropyEnable(anisotropyEnabled)
            .maxAnisotropy(anisotropyEnabled ? Math.min(maxAniso, ctx.getMaxAnisotropy()) : 1.0f)
            .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
            .unnormalizedCoordinates(false)
            .compareEnable(false)
            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
            .mipLodBias(0.0f)
            .minLod(0.0f)
            .maxLod(texObj.mipLevels > 1 ? (float) texObj.mipLevels : VK_LOD_CLAMP_NONE);
        
        LongBuffer pSampler = memAllocLong(1);
        vkCreateSampler(ctx.device, samplerInfo, null, pSampler);
        long sampler = pSampler.get(0);
        
        samplerInfo.free();
        memFree(pSampler);
        
        return sampler;
    }
    
    // ========================================================================
    // SHADER OPERATIONS
    // ========================================================================
    
    public static long createShader(int type) {
        checkInitialized();
        return state.createShader(type);
    }
    
    public static void shaderSource(long shader, String source) {
        checkInitialized();
        state.setShaderSource(shader, source);
    }
    
    public static void compileShader(long shader) {
        checkInitialized();
        
        VulkanState.ShaderObject shaderObj = state.getShader(shader);
        if (shaderObj == null) throw new RuntimeException("Invalid shader: " + shader);
        
        try {
            ByteBuffer spirv = compileGLSLtoSPIRV(shaderObj.source, shaderObj.type);
            
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(spirv);
            
            LongBuffer pModule = memAllocLong(1);
            int result = vkCreateShaderModule(ctx.device, createInfo, null, pModule);
            
            if (result == VK_SUCCESS) {
                shaderObj.spirv = spirv;
                shaderObj.module = pModule.get(0);
                shaderObj.compiled = true;
                shaderObj.compileStatus = true;
                shaderObj.infoLog = "";
            } else {
                shaderObj.compiled = false;
                shaderObj.compileStatus = false;
                shaderObj.infoLog = "Failed to create shader module: " + result;
            }
            
            createInfo.free();
            memFree(pModule);
        } catch (Exception e) {
            shaderObj.compiled = false;
            shaderObj.compileStatus = false;
            shaderObj.infoLog = e.getMessage();
        }
    }
    
    public static int getShaderiv(long shader, int pname) {
        checkInitialized();
        VulkanState.ShaderObject shaderObj = state.getShader(shader);
        if (shaderObj == null) return 0;
        
        return switch (pname) {
            case 0x8B4F -> shaderObj.compileStatus ? 1 : 0; // GL_COMPILE_STATUS
            case 0x8B81 -> shaderObj.type;                   // GL_SHADER_TYPE
            case 0x8B88 -> shaderObj.infoLog.length();       // GL_INFO_LOG_LENGTH
            case 0x8B80 -> shaderObj.source != null ? shaderObj.source.length() : 0; // GL_SHADER_SOURCE_LENGTH
            default -> 0;
        };
    }
    
    public static String getShaderInfoLog(long shader) {
        checkInitialized();
        VulkanState.ShaderObject shaderObj = state.getShader(shader);
        return shaderObj != null ? shaderObj.infoLog : "";
    }
    
    public static void deleteShader(long shader) {
        checkInitialized();
        if (shader == 0) return;
        
        VulkanState.ShaderObject shaderObj = state.getShader(shader);
        if (shaderObj != null && shaderObj.module != VK_NULL_HANDLE) {
            vkDestroyShaderModule(ctx.device, shaderObj.module, null);
        }
        state.unregisterShader(shader);
    }
    
    public static long createProgram() {
        checkInitialized();
        return state.createProgram();
    }
    
    public static void attachShader(long program, long shader) {
        checkInitialized();
        state.attachShaderToProgram(program, shader);
    }
    
    public static void detachShader(long program, long shader) {
        checkInitialized();
        state.detachShaderFromProgram(program, shader);
    }
    
    public static void linkProgram(long program) {
        checkInitialized();
        VulkanState.ProgramObject progObj = state.getProgram(program);
        if (progObj != null) {
            progObj.linked = true;
            progObj.linkStatus = true;
        }
    }
    
    public static int getProgramiv(long program, int pname) {
        checkInitialized();
        VulkanState.ProgramObject progObj = state.getProgram(program);
        if (progObj == null) return 0;
        
        return switch (pname) {
            case 0x8B82 -> progObj.linkStatus ? 1 : 0;          // GL_LINK_STATUS
            case 0x8B83 -> progObj.validateStatus ? 1 : 0;      // GL_VALIDATE_STATUS
            case 0x8B84 -> progObj.infoLog.length();            // GL_INFO_LOG_LENGTH
            case 0x8B85 -> progObj.attachedShaders.size();      // GL_ATTACHED_SHADERS
            case 0x8B86 -> progObj.activeUniforms;              // GL_ACTIVE_UNIFORMS
            case 0x8B87 -> progObj.activeUniformMaxLength;      // GL_ACTIVE_UNIFORM_MAX_LENGTH
            case 0x8B89 -> progObj.activeAttributes;            // GL_ACTIVE_ATTRIBUTES
            case 0x8B8A -> progObj.activeAttributeMaxLength;    // GL_ACTIVE_ATTRIBUTE_MAX_LENGTH
            default -> 0;
        };
    }
    
    public static String getProgramInfoLog(long program) {
        checkInitialized();
        VulkanState.ProgramObject progObj = state.getProgram(program);
        return progObj != null ? progObj.infoLog : "";
    }
    
    public static void useProgram(long program) {
        checkInitialized();
        state.useProgram(program);
    }
    
    public static void deleteProgram(long program) {
        checkInitialized();
        if (program == 0) return;
        
        VulkanState.ProgramObject progObj = state.getProgram(program);
        if (progObj != null) {
            vkDeviceWaitIdle(ctx.device);
            if (progObj.pipelineLayout != VK_NULL_HANDLE) {
                vkDestroyPipelineLayout(ctx.device, progObj.pipelineLayout, null);
            }
            if (progObj.computePipeline != VK_NULL_HANDLE) {
                vkDestroyPipeline(ctx.device, progObj.computePipeline, null);
            }
        }
        state.unregisterProgram(program);
    }
    
    // ========================================================================
    // VERTEX ATTRIBUTES
    // ========================================================================
    
    public static void vertexAttribPointer(int index, int size, int type, boolean normalized, 
                                            int stride, long pointer) {
        checkInitialized();
        state.setVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }
    
    public static void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        checkInitialized();
        state.setVertexAttribIPointer(index, size, type, stride, pointer);
    }
    
    public static void vertexAttribDivisor(int index, int divisor) {
        checkInitialized();
        state.setVertexAttribDivisor(index, divisor);
    }
    
    public static void enableVertexAttribArray(int index) {
        checkInitialized();
        state.enableVertexAttribArray(index);
    }
    
    public static void disableVertexAttribArray(int index) {
        checkInitialized();
        state.disableVertexAttribArray(index);
    }
    
    public static long genVertexArray() {
        checkInitialized();
        return state.createVertexArray();
    }
    
    public static void genVertexArrays(int n, long[] arrays) {
        for (int i = 0; i < n; i++) {
            arrays[i] = state.createVertexArray();
        }
    }
    
    public static void bindVertexArray(long vao) {
        checkInitialized();
        state.bindVertexArray(vao);
    }
    
    public static void deleteVertexArray(long vao) {
        checkInitialized();
        state.deleteVertexArray(vao);
    }
    
    public static void deleteVertexArrays(int n, long[] arrays) {
        for (int i = 0; i < n; i++) {
            state.deleteVertexArray(arrays[i]);
        }
    }
    
    // ========================================================================
    // UNIFORMS
    // ========================================================================
    
    public static int getUniformLocation(long program, String name) {
        checkInitialized();
        return state.getUniformLocation(program, name);
    }
    
    public static void uniform1i(int loc, int v) { checkInitialized(); state.setUniform(loc, v); }
    public static void uniform1f(int loc, float v) { checkInitialized(); state.setUniform(loc, v); }
    public static void uniform2i(int loc, int v0, int v1) { checkInitialized(); state.setUniform(loc, v0, v1); }
    public static void uniform2f(int loc, float v0, float v1) { checkInitialized(); state.setUniform(loc, v0, v1); }
    public static void uniform3i(int loc, int v0, int v1, int v2) { checkInitialized(); state.setUniform(loc, v0, v1, v2); }
    public static void uniform3f(int loc, float v0, float v1, float v2) { checkInitialized(); state.setUniform(loc, v0, v1, v2); }
    public static void uniform4i(int loc, int v0, int v1, int v2, int v3) { checkInitialized(); state.setUniform(loc, v0, v1, v2, v3); }
    public static void uniform4f(int loc, float v0, float v1, float v2, float v3) { checkInitialized(); state.setUniform(loc, v0, v1, v2, v3); }
    
    public static void uniformMatrix2fv(int loc, boolean transpose, float[] value) { checkInitialized(); state.setUniformMatrix2(loc, transpose, value); }
    public static void uniformMatrix3fv(int loc, boolean transpose, float[] value) { checkInitialized(); state.setUniformMatrix3(loc, transpose, value); }
    public static void uniformMatrix4fv(int loc, boolean transpose, float[] value) { checkInitialized(); state.setUniformMatrix4(loc, transpose, value); }
    
    // ========================================================================
    // TRANSLATION HELPERS
    // ========================================================================
    
    private static int translatePrimitiveTopology(int glMode) {
        return switch (glMode) {
            case 0x0000 -> VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
            case 0x0001 -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
            case 0x0002 -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST; // LINE_LOOP approximation
            case 0x0003 -> VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
            case 0x0004 -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            case 0x0005 -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
            case 0x0006 -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
            case 0x000A -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST_WITH_ADJACENCY;
            case 0x000B -> VK_PRIMITIVE_TOPOLOGY_LINE_STRIP_WITH_ADJACENCY;
            case 0x000C -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST_WITH_ADJACENCY;
            case 0x000D -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP_WITH_ADJACENCY;
            case 0x000E -> VK_PRIMITIVE_TOPOLOGY_PATCH_LIST;
            default -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        };
    }
    
    private static int translateDepthFunc(int glFunc) {
        return switch (glFunc) {
            case 0x0200 -> VK_COMPARE_OP_NEVER;
            case 0x0201 -> VK_COMPARE_OP_LESS;
            case 0x0202 -> VK_COMPARE_OP_EQUAL;
            case 0x0203 -> VK_COMPARE_OP_LESS_OR_EQUAL;
            case 0x0204 -> VK_COMPARE_OP_GREATER;
            case 0x0205 -> VK_COMPARE_OP_NOT_EQUAL;
            case 0x0206 -> VK_COMPARE_OP_GREATER_OR_EQUAL;
            case 0x0207 -> VK_COMPARE_OP_ALWAYS;
            default -> VK_COMPARE_OP_LESS;
        };
    }
    
    private static int translateCompareOp(int glOp) {
        return translateDepthFunc(glOp);
    }
    
    private static int translateBlendFactor(int glFactor) {
        return switch (glFactor) {
            case 0 -> VK_BLEND_FACTOR_ZERO;
            case 1 -> VK_BLEND_FACTOR_ONE;
            case 0x0300 -> VK_BLEND_FACTOR_SRC_COLOR;
            case 0x0301 -> VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
            case 0x0302 -> VK_BLEND_FACTOR_SRC_ALPHA;
            case 0x0303 -> VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case 0x0304 -> VK_BLEND_FACTOR_DST_ALPHA;
            case 0x0305 -> VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            case 0x0306 -> VK_BLEND_FACTOR_DST_COLOR;
            case 0x0307 -> VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
            case 0x0308 -> VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
            case 0x8001 -> VK_BLEND_FACTOR_CONSTANT_COLOR;
            case 0x8002 -> VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
            case 0x8003 -> VK_BLEND_FACTOR_CONSTANT_ALPHA;
            case 0x8004 -> VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
            case 0x88F9 -> VK_BLEND_FACTOR_SRC1_COLOR;
            case 0x88FA -> VK_BLEND_FACTOR_ONE_MINUS_SRC1_COLOR;
            case 0x88FB -> VK_BLEND_FACTOR_SRC1_ALPHA;
            case 0x88FC -> VK_BLEND_FACTOR_ONE_MINUS_SRC1_ALPHA;
            default -> VK_BLEND_FACTOR_ONE;
        };
    }
    
    private static int translateBlendOp(int glOp) {
        return switch (glOp) {
            case 0x8006 -> VK_BLEND_OP_ADD;
            case 0x800A -> VK_BLEND_OP_SUBTRACT;
            case 0x800B -> VK_BLEND_OP_REVERSE_SUBTRACT;
            case 0x8007 -> VK_BLEND_OP_MIN;
            case 0x8008 -> VK_BLEND_OP_MAX;
            default -> VK_BLEND_OP_ADD;
        };
    }
    
    private static int translateCullMode(int glMode) {
        return switch (glMode) {
            case 0x0404 -> VK_CULL_MODE_FRONT_BIT;
            case 0x0405 -> VK_CULL_MODE_BACK_BIT;
            case 0x0408 -> VK_CULL_MODE_FRONT_AND_BACK;
            default -> VK_CULL_MODE_BACK_BIT;
        };
    }
    
    private static int translateFrontFace(int glMode) {
        return switch (glMode) {
            case 0x0900 -> VK_FRONT_FACE_CLOCKWISE;
            case 0x0901 -> VK_FRONT_FACE_COUNTER_CLOCKWISE;
            default -> VK_FRONT_FACE_COUNTER_CLOCKWISE;
        };
    }
    
    private static int translatePolygonMode(int glMode) {
        return switch (glMode) {
            case 0x1B00 -> VK_POLYGON_MODE_POINT;
            case 0x1B01 -> VK_POLYGON_MODE_LINE;
            case 0x1B02 -> VK_POLYGON_MODE_FILL;
            default -> VK_POLYGON_MODE_FILL;
        };
    }
    
    private static int translateStencilOp(int glOp) {
        return switch (glOp) {
            case 0 -> VK_STENCIL_OP_KEEP;
            case 0x1E00 -> VK_STENCIL_OP_KEEP;
            case 0x0 -> VK_STENCIL_OP_ZERO;
            case 0x1E01 -> VK_STENCIL_OP_REPLACE;
            case 0x1E02 -> VK_STENCIL_OP_INCREMENT_AND_CLAMP;
            case 0x1E03 -> VK_STENCIL_OP_DECREMENT_AND_CLAMP;
            case 0x150A -> VK_STENCIL_OP_INVERT;
            case 0x8507 -> VK_STENCIL_OP_INCREMENT_AND_WRAP;
            case 0x8508 -> VK_STENCIL_OP_DECREMENT_AND_WRAP;
            default -> VK_STENCIL_OP_KEEP;
        };
    }
    
    private static int translateLogicOp(int glOp) {
        return switch (glOp) {
            case 0x1500 -> VK_LOGIC_OP_CLEAR;
            case 0x1501 -> VK_LOGIC_OP_AND;
            case 0x1502 -> VK_LOGIC_OP_AND_REVERSE;
            case 0x1503 -> VK_LOGIC_OP_COPY;
            case 0x1504 -> VK_LOGIC_OP_AND_INVERTED;
            case 0x1505 -> VK_LOGIC_OP_NO_OP;
            case 0x1506 -> VK_LOGIC_OP_XOR;
            case 0x1507 -> VK_LOGIC_OP_OR;
            case 0x1508 -> VK_LOGIC_OP_NOR;
            case 0x1509 -> VK_LOGIC_OP_EQUIVALENT;
            case 0x150A -> VK_LOGIC_OP_INVERT;
            case 0x150B -> VK_LOGIC_OP_OR_REVERSE;
            case 0x150C -> VK_LOGIC_OP_COPY_INVERTED;
            case 0x150D -> VK_LOGIC_OP_OR_INVERTED;
            case 0x150E -> VK_LOGIC_OP_NAND;
            case 0x150F -> VK_LOGIC_OP_SET;
            default -> VK_LOGIC_OP_COPY;
        };
    }
    
    private static int translateSampleCount(int count) {
        return switch (count) {
            case 1 -> VK_SAMPLE_COUNT_1_BIT;
            case 2 -> VK_SAMPLE_COUNT_2_BIT;
            case 4 -> VK_SAMPLE_COUNT_4_BIT;
            case 8 -> VK_SAMPLE_COUNT_8_BIT;
            case 16 -> VK_SAMPLE_COUNT_16_BIT;
            case 32 -> VK_SAMPLE_COUNT_32_BIT;
            case 64 -> VK_SAMPLE_COUNT_64_BIT;
            default -> VK_SAMPLE_COUNT_1_BIT;
        };
    }
    
    private static int translateBufferUsage(int target) {
        return switch (target) {
            case 0x8892 -> VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
            case 0x8893 -> VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
            case 0x8A11 -> VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
            case 0x8F36 -> VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
            case 0x90D2 -> VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            case 0x88EC -> VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            case 0x88ED -> VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
            case 0x8C8E -> VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            case 0x8C2A -> VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT;
            default -> VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        };
    }
    
    private static int translateFilter(int glFilter) {
        return switch (glFilter) {
            case 0x2600 -> VK_FILTER_NEAREST;
            case 0x2601 -> VK_FILTER_LINEAR;
            case 0x2700, 0x2702 -> VK_FILTER_NEAREST;
            case 0x2701, 0x2703 -> VK_FILTER_LINEAR;
            default -> VK_FILTER_LINEAR;
        };
    }
    
    private static int translateWrap(int glWrap) {
        return switch (glWrap) {
            case 0x2901 -> VK_SAMPLER_ADDRESS_MODE_REPEAT;
            case 0x812F -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            case 0x812D -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER;
            case 0x8370 -> VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
            case 0x8742 -> VK_SAMPLER_ADDRESS_MODE_MIRROR_CLAMP_TO_EDGE;
            default -> VK_SAMPLER_ADDRESS_MODE_REPEAT;
        };
    }
    
    // ========================================================================
    // DEBUG & STATUS
    // ========================================================================
    
    public static String getStatusReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VulkanCallMapperX Status ===\n");
        sb.append("Initialized: ").append(initialized).append("\n");
        sb.append("Vulkan Version: ").append(getVulkanVersionString()).append("\n");
        sb.append("Recording Commands: ").append(recordingCommands).append("\n");
        sb.append("Pipeline Cache: ").append(pipelineCache.size()).append(" graphics, ");
        sb.append(computePipelineCache.size()).append(" compute\n");
        sb.append("Command Queue: ").append(commandQueue.size()).append(" pending\n");
        sb.append("Current Frame: ").append(currentFrameIndex).append("/").append(MAX_FRAMES_IN_FLIGHT).append("\n");
        sb.append("\n=== Features ===\n");
        sb.append("Timeline Semaphores: ").append(supportsTimelineSemaphores).append("\n");
        sb.append("Dynamic Rendering: ").append(supportsDynamicRendering).append("\n");
        sb.append("Synchronization2: ").append(supportsSynchronization2).append("\n");
        sb.append("Buffer Device Address: ").append(supportsBufferDeviceAddress).append("\n");
        sb.append("Descriptor Indexing: ").append(supportsDescriptorIndexing).append("\n");
        sb.append("Maintenance4: ").append(supportsMaintenance4).append("\n");
        sb.append("Maintenance5: ").append(supportsMaintenance5).append("\n");
        if (state != null) {
            sb.append("\n=== State ===\n");
            sb.append("Active Texture Unit: ").append(state.activeTextureUnit).append("\n");
            sb.append("Current Program: ").append(state.currentProgram).append("\n");
            sb.append("Bound VAO: ").append(state.getCurrentVAO()).append("\n");
        }
        return sb.toString();
    }
    
    public static void logStatus() {
        FPSFlux.LOGGER.info(getStatusReport());
    }
}
