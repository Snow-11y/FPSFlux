package com.example.modid.gl.mapping;

import com.example.modid.FPSFlux;
import com.example.modid.gl.vulkan.VulkanContext;
import com.example.modid.gl.vulkan.VulkanState;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;

/**
 * VulkanCallMapperX - OpenGL → Vulkan Translation Layer
 * 
 * Translates GL-style calls to Vulkan commands.
 * Includes:
 * - SPIR-V shader compilation via shaderc
 * - Proper vertex input state
 * - Descriptor set management
 * - Pipeline caching
 * - Fence synchronization
 */
public class VulkanCallMapperX {
    
    private static VulkanContext ctx;
    private static VulkanState state;
    private static VkCommandBuffer currentCommandBuffer;
    private static boolean recordingCommands = false;
    private static boolean initialized = false;
    
    // Shaderc compiler (reusable)
    private static long shadercCompiler = 0;
    
    // Pipeline cache: state hash -> pipeline handle
    private static final Map<PipelineStateKey, Long> pipelineCache = new HashMap<>();
    
    // Descriptor pool and sets
    private static long descriptorPool = VK_NULL_HANDLE;
    private static long[] descriptorSets;
    private static int currentDescriptorSetIndex = 0;
    private static final int MAX_DESCRIPTOR_SETS = 16;
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    public static void initialize(VulkanContext context) {
        if (initialized) return;
        
        ctx = context;
        state = new VulkanState();
        
        // Initialize shaderc compiler
        shadercCompiler = Shaderc.shaderc_compiler_initialize();
        if (shadercCompiler == 0) {
            throw new RuntimeException("Failed to initialize shaderc compiler");
        }
        
        // Create descriptor pool
        createDescriptorPool();
        
        // Allocate descriptor sets
        allocateDescriptorSets();
        
        initialized = true;
        FPSFlux.LOGGER.info("[VulkanCallMapperX] Initialized successfully");
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    public static void shutdown() {
        if (!initialized) return;
        
        // Wait for GPU to finish
        if (ctx != null && ctx.device != null) {
            vkDeviceWaitIdle(ctx.device);
        }
        
        // Destroy cached pipelines
        for (Long pipeline : pipelineCache.values()) {
            if (pipeline != VK_NULL_HANDLE) {
                vkDestroyPipeline(ctx.device, pipeline, null);
            }
        }
        pipelineCache.clear();
        
        // Destroy descriptor pool (also frees descriptor sets)
        if (descriptorPool != VK_NULL_HANDLE) {
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
    
    private static void createDescriptorPool() {
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2);
        poolSizes.get(0)
            .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(MAX_DESCRIPTOR_SETS);
        poolSizes.get(1)
            .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(MAX_DESCRIPTOR_SETS * 8); // 8 texture units per set
        
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
            .pPoolSizes(poolSizes)
            .maxSets(MAX_DESCRIPTOR_SETS);
        
        LongBuffer pDescriptorPool = LongBuffer.allocate(1);
        int result = vkCreateDescriptorPool(ctx.device, poolInfo, null, pDescriptorPool);
        if (result != VK_SUCCESS) {
            poolSizes.free();
            poolInfo.free();
            throw new RuntimeException("Failed to create descriptor pool: " + result);
        }
        
        descriptorPool = pDescriptorPool.get(0);
        
        poolSizes.free();
        poolInfo.free();
    }
    
    private static void allocateDescriptorSets() {
        descriptorSets = new long[MAX_DESCRIPTOR_SETS];
        
        // Need descriptor set layout from context
        if (ctx.descriptorSetLayout == VK_NULL_HANDLE) {
            createDescriptorSetLayout();
        }
        
        LongBuffer layouts = LongBuffer.allocate(MAX_DESCRIPTOR_SETS);
        for (int i = 0; i < MAX_DESCRIPTOR_SETS; i++) {
            layouts.put(i, ctx.descriptorSetLayout);
        }
        
        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .descriptorPool(descriptorPool)
            .pSetLayouts(layouts);
        
        LongBuffer pDescriptorSets = LongBuffer.allocate(MAX_DESCRIPTOR_SETS);
        int result = vkAllocateDescriptorSets(ctx.device, allocInfo, pDescriptorSets);
        if (result != VK_SUCCESS) {
            allocInfo.free();
            throw new RuntimeException("Failed to allocate descriptor sets: " + result);
        }
        
        for (int i = 0; i < MAX_DESCRIPTOR_SETS; i++) {
            descriptorSets[i] = pDescriptorSets.get(i);
        }
        
        allocInfo.free();
    }
    
    private static void createDescriptorSetLayout() {
        // Bindings: 0 = UBO, 1-8 = texture samplers
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(9);
        
        // Uniform buffer binding
        bindings.get(0)
            .binding(0)
            .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(1)
            .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
        
        // Texture sampler bindings (8 texture units)
        for (int i = 1; i <= 8; i++) {
            bindings.get(i)
                .binding(i)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        }
        
        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
            .pBindings(bindings);
        
        LongBuffer pDescriptorSetLayout = LongBuffer.allocate(1);
        int result = vkCreateDescriptorSetLayout(ctx.device, layoutInfo, null, pDescriptorSetLayout);
        if (result != VK_SUCCESS) {
            bindings.free();
            layoutInfo.free();
            throw new RuntimeException("Failed to create descriptor set layout: " + result);
        }
        
        ctx.descriptorSetLayout = pDescriptorSetLayout.get(0);
        
        bindings.free();
        layoutInfo.free();
    }
    
    // ========================================================================
    // SHADER COMPILATION (SPIR-V)
    // ========================================================================
    
    /**
     * Compile GLSL to SPIR-V using shaderc
     */
    private static ByteBuffer compileGLSLtoSPIRV(String source, int shaderType) {
        checkInitialized();
        
        if (shadercCompiler == 0) {
            throw new RuntimeException("Shaderc compiler not initialized");
        }
        
        // Determine shader kind
        int kind;
        String shaderName;
        if (shaderType == 0x8B31) { // GL_VERTEX_SHADER
            kind = Shaderc.shaderc_vertex_shader;
            shaderName = "vertex.glsl";
        } else if (shaderType == 0x8B30) { // GL_FRAGMENT_SHADER
            kind = Shaderc.shaderc_fragment_shader;
            shaderName = "fragment.glsl";
        } else if (shaderType == 0x8DD9) { // GL_GEOMETRY_SHADER
            kind = Shaderc.shaderc_geometry_shader;
            shaderName = "geometry.glsl";
        } else if (shaderType == 0x8E88) { // GL_TESS_CONTROL_SHADER
            kind = Shaderc.shaderc_tess_control_shader;
            shaderName = "tess_control.glsl";
        } else if (shaderType == 0x8E87) { // GL_TESS_EVALUATION_SHADER
            kind = Shaderc.shaderc_tess_evaluation_shader;
            shaderName = "tess_eval.glsl";
        } else if (shaderType == 0x91B9) { // GL_COMPUTE_SHADER
            kind = Shaderc.shaderc_compute_shader;
            shaderName = "compute.glsl";
        } else {
            throw new RuntimeException("Unknown shader type: " + shaderType);
        }
        
        // Compile options
        long options = Shaderc.shaderc_compile_options_initialize();
        Shaderc.shaderc_compile_options_set_target_env(
            options, 
            Shaderc.shaderc_target_env_vulkan, 
            Shaderc.shaderc_env_version_vulkan_1_0
        );
        Shaderc.shaderc_compile_options_set_optimization_level(
            options, 
            Shaderc.shaderc_optimization_level_performance
        );
        
        // Compile
        long result = Shaderc.shaderc_compile_into_spv(
            shadercCompiler,
            source,
            kind,
            shaderName,
            "main",
            options
        );
        
        // Check status
        int status = Shaderc.shaderc_result_get_compilation_status(result);
        if (status != Shaderc.shaderc_compilation_status_success) {
            String errorMessage = Shaderc.shaderc_result_get_error_message(result);
            Shaderc.shaderc_result_release(result);
            Shaderc.shaderc_compile_options_release(options);
            throw new RuntimeException("Shader compilation failed: " + errorMessage);
        }
        
        // Get SPIR-V bytes
        ByteBuffer spirvTemp = Shaderc.shaderc_result_get_bytes(result);
        
        // Copy to our own buffer (result buffer gets freed)
        ByteBuffer spirv = ByteBuffer.allocateDirect(spirvTemp.remaining());
        spirv.put(spirvTemp);
        spirv.flip();
        
        // Cleanup
        Shaderc.shaderc_result_release(result);
        Shaderc.shaderc_compile_options_release(options);
        
        FPSFlux.LOGGER.debug("[VulkanCallMapperX] Compiled {} to SPIR-V ({} bytes)", 
            shaderName, spirv.remaining());
        
        return spirv;
    }
    
    // ========================================================================
    // VERTEX INPUT STATE BUILDING
    // ========================================================================
    
    /**
     * Build vertex input state from current VulkanState tracking
     */
    private static VertexInputStateInfo buildVertexInputState() {
        int enabledCount = state.getEnabledAttributeCount();
        if (enabledCount == 0) {
            return new VertexInputStateInfo(null, null);
        }
        
        // Calculate stride from attributes
        int stride = state.getCurrentVertexStride();
        
        VkVertexInputBindingDescription.Buffer bindings = VkVertexInputBindingDescription.calloc(1);
        bindings.get(0)
            .binding(0)
            .stride(stride)
            .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
        
        VkVertexInputAttributeDescription.Buffer attributes = 
            VkVertexInputAttributeDescription.calloc(enabledCount);
        
        int attrIdx = 0;
        for (int i = 0; i < 16; i++) { // Max 16 vertex attributes
            if (state.isVertexAttribArrayEnabled(i)) {
                VulkanState.VertexAttrib attr = state.getVertexAttrib(i);
                if (attr != null) {
                    attributes.get(attrIdx)
                        .binding(0)
                        .location(i)
                        .format(translateVertexFormatToVulkan(attr.size, attr.type, attr.normalized))
                        .offset((int) attr.pointer);
                    attrIdx++;
                }
            }
        }
        
        return new VertexInputStateInfo(bindings, attributes);
    }
    
    private static int translateVertexFormatToVulkan(int size, int type, boolean normalized) {
        // GL_FLOAT
        if (type == 0x1406) {
            return switch (size) {
                case 1 -> VK_FORMAT_R32_SFLOAT;
                case 2 -> VK_FORMAT_R32G32_SFLOAT;
                case 3 -> VK_FORMAT_R32G32B32_SFLOAT;
                case 4 -> VK_FORMAT_R32G32B32A32_SFLOAT;
                default -> throw new RuntimeException("Invalid float vertex size: " + size);
            };
        }
        
        // GL_UNSIGNED_BYTE
        if (type == 0x1401) {
            if (normalized) {
                return switch (size) {
                    case 1 -> VK_FORMAT_R8_UNORM;
                    case 2 -> VK_FORMAT_R8G8_UNORM;
                    case 3 -> VK_FORMAT_R8G8B8_UNORM;
                    case 4 -> VK_FORMAT_R8G8B8A8_UNORM;
                    default -> throw new RuntimeException("Invalid ubyte vertex size: " + size);
                };
            } else {
                return switch (size) {
                    case 1 -> VK_FORMAT_R8_UINT;
                    case 2 -> VK_FORMAT_R8G8_UINT;
                    case 3 -> VK_FORMAT_R8G8B8_UINT;
                    case 4 -> VK_FORMAT_R8G8B8A8_UINT;
                    default -> throw new RuntimeException("Invalid ubyte vertex size: " + size);
                };
            }
        }
        
        // GL_BYTE
        if (type == 0x1400) {
            if (normalized) {
                return switch (size) {
                    case 1 -> VK_FORMAT_R8_SNORM;
                    case 2 -> VK_FORMAT_R8G8_SNORM;
                    case 3 -> VK_FORMAT_R8G8B8_SNORM;
                    case 4 -> VK_FORMAT_R8G8B8A8_SNORM;
                    default -> throw new RuntimeException("Invalid byte vertex size: " + size);
                };
            } else {
                return switch (size) {
                    case 1 -> VK_FORMAT_R8_SINT;
                    case 2 -> VK_FORMAT_R8G8_SINT;
                    case 3 -> VK_FORMAT_R8G8B8_SINT;
                    case 4 -> VK_FORMAT_R8G8B8A8_SINT;
                    default -> throw new RuntimeException("Invalid byte vertex size: " + size);
                };
            }
        }
        
        // GL_SHORT
        if (type == 0x1402) {
            if (normalized) {
                return switch (size) {
                    case 1 -> VK_FORMAT_R16_SNORM;
                    case 2 -> VK_FORMAT_R16G16_SNORM;
                    case 3 -> VK_FORMAT_R16G16B16_SNORM;
                    case 4 -> VK_FORMAT_R16G16B16A16_SNORM;
                    default -> throw new RuntimeException("Invalid short vertex size: " + size);
                };
            } else {
                return switch (size) {
                    case 1 -> VK_FORMAT_R16_SINT;
                    case 2 -> VK_FORMAT_R16G16_SINT;
                    case 3 -> VK_FORMAT_R16G16B16_SINT;
                    case 4 -> VK_FORMAT_R16G16B16A16_SINT;
                    default -> throw new RuntimeException("Invalid short vertex size: " + size);
                };
            }
        }
        
        // GL_UNSIGNED_SHORT
        if (type == 0x1403) {
            if (normalized) {
                return switch (size) {
                    case 1 -> VK_FORMAT_R16_UNORM;
                    case 2 -> VK_FORMAT_R16G16_UNORM;
                    case 3 -> VK_FORMAT_R16G16B16_UNORM;
                    case 4 -> VK_FORMAT_R16G16B16A16_UNORM;
                    default -> throw new RuntimeException("Invalid ushort vertex size: " + size);
                };
            } else {
                return switch (size) {
                    case 1 -> VK_FORMAT_R16_UINT;
                    case 2 -> VK_FORMAT_R16G16_UINT;
                    case 3 -> VK_FORMAT_R16G16B16_UINT;
                    case 4 -> VK_FORMAT_R16G16B16A16_UINT;
                    default -> throw new RuntimeException("Invalid ushort vertex size: " + size);
                };
            }
        }
        
        // GL_INT
        if (type == 0x1404) {
            return switch (size) {
                case 1 -> VK_FORMAT_R32_SINT;
                case 2 -> VK_FORMAT_R32G32_SINT;
                case 3 -> VK_FORMAT_R32G32B32_SINT;
                case 4 -> VK_FORMAT_R32G32B32A32_SINT;
                default -> throw new RuntimeException("Invalid int vertex size: " + size);
            };
        }
        
        // GL_UNSIGNED_INT
        if (type == 0x1405) {
            return switch (size) {
                case 1 -> VK_FORMAT_R32_UINT;
                case 2 -> VK_FORMAT_R32G32_UINT;
                case 3 -> VK_FORMAT_R32G32B32_UINT;
                case 4 -> VK_FORMAT_R32G32B32A32_UINT;
                default -> throw new RuntimeException("Invalid uint vertex size: " + size);
            };
        }
        
        // GL_HALF_FLOAT
        if (type == 0x140B) {
            return switch (size) {
                case 1 -> VK_FORMAT_R16_SFLOAT;
                case 2 -> VK_FORMAT_R16G16_SFLOAT;
                case 3 -> VK_FORMAT_R16G16B16_SFLOAT;
                case 4 -> VK_FORMAT_R16G16B16A16_SFLOAT;
                default -> throw new RuntimeException("Invalid half vertex size: " + size);
            };
        }
        
        // GL_DOUBLE
        if (type == 0x140A) {
            return switch (size) {
                case 1 -> VK_FORMAT_R64_SFLOAT;
                case 2 -> VK_FORMAT_R64G64_SFLOAT;
                case 3 -> VK_FORMAT_R64G64B64_SFLOAT;
                case 4 -> VK_FORMAT_R64G64B64A64_SFLOAT;
                default -> throw new RuntimeException("Invalid double vertex size: " + size);
            };
        }
        
        throw new RuntimeException("Unsupported vertex type: 0x" + Integer.toHexString(type));
    }
    
    // Helper class for vertex input state
    private static class VertexInputStateInfo {
        final VkVertexInputBindingDescription.Buffer bindings;
        final VkVertexInputAttributeDescription.Buffer attributes;
        
        VertexInputStateInfo(VkVertexInputBindingDescription.Buffer bindings,
                            VkVertexInputAttributeDescription.Buffer attributes) {
            this.bindings = bindings;
            this.attributes = attributes;
        }
        
        void free() {
            if (bindings != null) bindings.free();
            if (attributes != null) attributes.free();
        }
    }
    
    // ========================================================================
    // PIPELINE CACHING
    // ========================================================================
    
    /**
     * Key for pipeline cache based on current GL state
     */
    private static class PipelineStateKey {
        final long program;
        final int primitiveTopology;
        final boolean blendEnabled;
        final int blendSrcRGB, blendDstRGB;
        final int blendSrcAlpha, blendDstAlpha;
        final boolean depthTestEnabled;
        final boolean depthWriteEnabled;
        final int depthFunc;
        final boolean cullFaceEnabled;
        final int cullFaceMode;
        final int frontFace;
        final int polygonMode;
        final int vertexInputHash;
        
        PipelineStateKey(long program, int topology, VulkanState state) {
            this.program = program;
            this.primitiveTopology = topology;
            this.blendEnabled = state.isBlendEnabled();
            this.blendSrcRGB = state.getBlendSrcRGB();
            this.blendDstRGB = state.getBlendDstRGB();
            this.blendSrcAlpha = state.getBlendSrcAlpha();
            this.blendDstAlpha = state.getBlendDstAlpha();
            this.depthTestEnabled = state.isDepthTestEnabled();
            this.depthWriteEnabled = state.isDepthWriteEnabled();
            this.depthFunc = state.getDepthFunc();
            this.cullFaceEnabled = state.isCullFaceEnabled();
            this.cullFaceMode = state.getCullFaceMode();
            this.frontFace = state.getFrontFace();
            this.polygonMode = state.getPolygonMode();
            this.vertexInputHash = state.getVertexInputHash();
        }
        
        @Override
        public int hashCode() {
            int result = Long.hashCode(program);
            result = 31 * result + primitiveTopology;
            result = 31 * result + (blendEnabled ? 1 : 0);
            result = 31 * result + blendSrcRGB;
            result = 31 * result + blendDstRGB;
            result = 31 * result + blendSrcAlpha;
            result = 31 * result + blendDstAlpha;
            result = 31 * result + (depthTestEnabled ? 1 : 0);
            result = 31 * result + (depthWriteEnabled ? 1 : 0);
            result = 31 * result + depthFunc;
            result = 31 * result + (cullFaceEnabled ? 1 : 0);
            result = 31 * result + cullFaceMode;
            result = 31 * result + frontFace;
            result = 31 * result + polygonMode;
            result = 31 * result + vertexInputHash;
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
                && depthTestEnabled == other.depthTestEnabled
                && depthWriteEnabled == other.depthWriteEnabled
                && depthFunc == other.depthFunc
                && cullFaceEnabled == other.cullFaceEnabled
                && cullFaceMode == other.cullFaceMode
                && frontFace == other.frontFace
                && polygonMode == other.polygonMode
                && vertexInputHash == other.vertexInputHash;
        }
    }
    
    /**
     * Get or create pipeline for current state
     */
    private static long getOrCreatePipeline(int glMode) {
        long program = state.currentProgram;
        if (program == 0) {
            throw new RuntimeException("No program bound");
        }
        
        int topology = translatePrimitiveTopology(glMode);
        PipelineStateKey key = new PipelineStateKey(program, topology, state);
        
        Long cached = pipelineCache.get(key);
        if (cached != null) {
            return cached;
        }
        
        // Create new pipeline
        long pipeline = createPipelineForState(key);
        pipelineCache.put(key, pipeline);
        
        FPSFlux.LOGGER.debug("[VulkanCallMapperX] Created new pipeline, cache size: {}", 
            pipelineCache.size());
        
        return pipeline;
    }
    
    private static long createPipelineForState(PipelineStateKey key) {
        VulkanState.ProgramObject progObj = state.getProgram(key.program);
        if (progObj == null || !progObj.linked) {
            throw new RuntimeException("Program not linked: " + key.program);
        }
        
        // Get shader modules
        VulkanState.ShaderObject vertShader = null;
        VulkanState.ShaderObject fragShader = null;
        
        for (long shaderId : progObj.attachedShaders) {
            VulkanState.ShaderObject shader = state.getShader(shaderId);
            if (shader.type == 0x8B31) { // GL_VERTEX_SHADER
                vertShader = shader;
            } else if (shader.type == 0x8B30) { // GL_FRAGMENT_SHADER
                fragShader = shader;
            }
        }
        
        if (vertShader == null || fragShader == null) {
            throw new RuntimeException("Program must have vertex and fragment shaders");
        }
        
        // Shader stages
        ByteBuffer entryPoint = ByteBuffer.allocateDirect(5);
        entryPoint.put("main".getBytes()).put((byte) 0).flip();
        
        VkPipelineShaderStageCreateInfo.Buffer shaderStages = 
            VkPipelineShaderStageCreateInfo.calloc(2);
        
        shaderStages.get(0)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(VK_SHADER_STAGE_VERTEX_BIT)
            .module(vertShader.module)
            .pName(entryPoint);
        
        shaderStages.get(1)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
            .module(fragShader.module)
            .pName(entryPoint);
        
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
                .primitiveRestartEnable(false);
        
        // Viewport state (dynamic)
        VkPipelineViewportStateCreateInfo viewportState = 
            VkPipelineViewportStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .viewportCount(1)
                .scissorCount(1);
        
        // Rasterization
        VkPipelineRasterizationStateCreateInfo rasterizer = 
            VkPipelineRasterizationStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(translatePolygonMode(key.polygonMode))
                .lineWidth(1.0f)
                .cullMode(key.cullFaceEnabled ? translateCullMode(key.cullFaceMode) : VK_CULL_MODE_NONE)
                .frontFace(translateFrontFace(key.frontFace))
                .depthBiasEnable(false);
        
        // Multisampling
        VkPipelineMultisampleStateCreateInfo multisampling = 
            VkPipelineMultisampleStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .sampleShadingEnable(false)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
        
        // Depth stencil
        VkPipelineDepthStencilStateCreateInfo depthStencil = 
            VkPipelineDepthStencilStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                .depthTestEnable(key.depthTestEnabled)
                .depthWriteEnable(key.depthWriteEnabled)
                .depthCompareOp(translateDepthFunc(key.depthFunc))
                .depthBoundsTestEnable(false)
                .stencilTestEnable(false);
        
        // Color blending
        VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = 
            VkPipelineColorBlendAttachmentState.calloc(1);
        
        colorBlendAttachment.get(0)
            .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | 
                           VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
            .blendEnable(key.blendEnabled);
        
        if (key.blendEnabled) {
            colorBlendAttachment.get(0)
                .srcColorBlendFactor(translateBlendFactor(key.blendSrcRGB))
                .dstColorBlendFactor(translateBlendFactor(key.blendDstRGB))
                .colorBlendOp(VK_BLEND_OP_ADD)
                .srcAlphaBlendFactor(translateBlendFactor(key.blendSrcAlpha))
                .dstAlphaBlendFactor(translateBlendFactor(key.blendDstAlpha))
                .alphaBlendOp(VK_BLEND_OP_ADD);
        }
        
        VkPipelineColorBlendStateCreateInfo colorBlending = 
            VkPipelineColorBlendStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .logicOpEnable(false)
                .pAttachments(colorBlendAttachment);
        
        // Dynamic state
        IntBuffer dynamicStates = IntBuffer.allocate(2);
        dynamicStates.put(VK_DYNAMIC_STATE_VIEWPORT);
        dynamicStates.put(VK_DYNAMIC_STATE_SCISSOR);
        dynamicStates.flip();
        
        VkPipelineDynamicStateCreateInfo dynamicState = 
            VkPipelineDynamicStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                .pDynamicStates(dynamicStates);
        
        // Pipeline layout (reuse from program or create)
        long pipelineLayout = progObj.pipelineLayout;
        if (pipelineLayout == VK_NULL_HANDLE) {
            VkPipelineLayoutCreateInfo pipelineLayoutInfo = 
                VkPipelineLayoutCreateInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(LongBuffer.wrap(new long[]{ctx.descriptorSetLayout}));
            
            LongBuffer pPipelineLayout = LongBuffer.allocate(1);
            vkCreatePipelineLayout(ctx.device, pipelineLayoutInfo, null, pPipelineLayout);
            pipelineLayout = pPipelineLayout.get(0);
            progObj.pipelineLayout = pipelineLayout;
            
            pipelineLayoutInfo.free();
        }
        
        // Create graphics pipeline
        VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = 
            VkGraphicsPipelineCreateInfo.calloc(1);
        
        pipelineInfo.get(0)
            .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
            .pStages(shaderStages)
            .pVertexInputState(vertexInputInfo)
            .pInputAssemblyState(inputAssembly)
            .pViewportState(viewportState)
            .pRasterizationState(rasterizer)
            .pMultisampleState(multisampling)
            .pDepthStencilState(depthStencil)
            .pColorBlendState(colorBlending)
            .pDynamicState(dynamicState)
            .layout(pipelineLayout)
            .renderPass(ctx.renderPass)
            .subpass(0);
        
        LongBuffer pPipeline = LongBuffer.allocate(1);
        int result = vkCreateGraphicsPipelines(ctx.device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
        
        // Cleanup
        shaderStages.free();
        vertexInputInfo.free();
        vertexInput.free();
        inputAssembly.free();
        viewportState.free();
        rasterizer.free();
        multisampling.free();
        depthStencil.free();
        colorBlendAttachment.free();
        colorBlending.free();
        dynamicState.free();
        pipelineInfo.free();
        
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create graphics pipeline: " + result);
        }
        
        return pPipeline.get(0);
    }
    
    // ========================================================================
    // FRAME MANAGEMENT (with proper synchronization)
    // ========================================================================
    
    private static void beginFrame() {
        // Wait for previous frame to finish
        vkWaitForFences(ctx.device, new long[]{ctx.inFlightFence}, true, Long.MAX_VALUE);
        
        // Acquire next swapchain image
        IntBuffer pImageIndex = IntBuffer.allocate(1);
        int result = vkAcquireNextImageKHR(ctx.device, ctx.swapchain, Long.MAX_VALUE, 
            ctx.imageAvailableSemaphore, VK_NULL_HANDLE, pImageIndex);
        
        if (result == VK_ERROR_OUT_OF_DATE_KHR) {
            // Swapchain needs recreation
            ctx.recreateSwapchain();
            return;
        } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
            throw new RuntimeException("Failed to acquire swapchain image: " + result);
        }
        
        ctx.currentImageIndex = pImageIndex.get(0);
        
        // Reset fence
        vkResetFences(ctx.device, new long[]{ctx.inFlightFence});
        
        // Reset and begin command buffer
        currentCommandBuffer = ctx.getCurrentCommandBuffer();
        vkResetCommandBuffer(currentCommandBuffer, 0);
        
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        
        vkBeginCommandBuffer(currentCommandBuffer, beginInfo);
        
        // Begin render pass
        VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
            .renderPass(ctx.renderPass)
            .framebuffer(ctx.getCurrentFramebuffer());

        renderPassInfo.renderArea().offset().set(0, 0);
        renderPassInfo.renderArea().extent().set(ctx.swapchainExtent.width(), ctx.swapchainExtent.height());
        
        VkClearValue.Buffer clearValues = VkClearValue.calloc(2);
        float[] clearColor = state.getClearColor();
        clearValues.get(0).color()
            .float32(0, clearColor[0])
            .float32(1, clearColor[1])
            .float32(2, clearColor[2])
            .float32(3, clearColor[3]);
        clearValues.get(1).depthStencil().set(state.getClearDepth(), 0);
        renderPassInfo.pClearValues(clearValues);
        
        vkCmdBeginRenderPass(currentCommandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        
        // Set viewport and scissor
        VkViewport.Buffer viewport = VkViewport.calloc(1)
            .x(0.0f)
            .y(0.0f)
            .width(ctx.swapchainExtent.width())
            .height(ctx.swapchainExtent.height())
            .minDepth(0.0f)
            .maxDepth(1.0f);
        vkCmdSetViewport(currentCommandBuffer, 0, viewport);
        
        VkRect2D.Buffer scissor = VkRect2D.calloc(1);
        scissor.offset().set(0, 0);
        scissor.extent().set(ctx.swapchainExtent.width(), ctx.swapchainExtent.height());
        vkCmdSetScissor(currentCommandBuffer, 0, scissor);
        
        recordingCommands = true;
        
        // Rotate descriptor set
        currentDescriptorSetIndex = (currentDescriptorSetIndex + 1) % MAX_DESCRIPTOR_SETS;
        
        beginInfo.free();
        renderPassInfo.free();
        clearValues.free();
        viewport.free();
        scissor.free();
    }
    
    public static void endFrame() {
        if (!recordingCommands) return;
        
        vkCmdEndRenderPass(currentCommandBuffer);
        
        int result = vkEndCommandBuffer(currentCommandBuffer);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to end command buffer: " + result);
        }
        
        // Submit
        VkSubmitInfo submitInfo = VkSubmitInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
        
        LongBuffer waitSemaphores = LongBuffer.wrap(new long[]{ctx.imageAvailableSemaphore});
        IntBuffer waitStages = IntBuffer.wrap(new int[]{VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT});
        submitInfo.pWaitSemaphores(waitSemaphores);
        submitInfo.pWaitDstStageMask(waitStages);
        
        submitInfo.pCommandBuffers(currentCommandBuffer);
        
        LongBuffer signalSemaphores = LongBuffer.wrap(new long[]{ctx.renderFinishedSemaphore});
        submitInfo.pSignalSemaphores(signalSemaphores);
        
        result = vkQueueSubmit(ctx.graphicsQueue, submitInfo, ctx.inFlightFence);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to submit draw command buffer: " + result);
        }
        
        // Present
        VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc()
            .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            .pWaitSemaphores(signalSemaphores);
        
        LongBuffer swapchains = LongBuffer.wrap(new long[]{ctx.swapchain});
        IntBuffer imageIndices = IntBuffer.wrap(new int[]{ctx.currentImageIndex});
        presentInfo.pSwapchains(swapchains);
        presentInfo.pImageIndices(imageIndices);
        
        result = vkQueuePresentKHR(ctx.presentQueue, presentInfo);
        
        if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
            ctx.recreateSwapchain();
        } else if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to present: " + result);
        }
        
        recordingCommands = false;
        
        submitInfo.free();
        presentInfo.free();
    }
    
    // ========================================================================
    // TEXTURE OPERATIONS
    // ========================================================================
    
    public static long genTexture() {
        checkInitialized();
        
        VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .imageType(VK_IMAGE_TYPE_2D)
            .format(VK_FORMAT_R8G8B8A8_UNORM)
            .mipLevels(1)
            .arrayLayers(1)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .tiling(VK_IMAGE_TILING_OPTIMAL)
            .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        
        imageInfo.extent().width(1).height(1).depth(1);
        
        LongBuffer pImage = LongBuffer.allocate(1);
        int result = vkCreateImage(ctx.device, imageInfo, null, pImage);
        if (result != VK_SUCCESS) {
            imageInfo.free();
            throw new RuntimeException("Failed to create Vulkan image: " + result);
        }
        
        long image = pImage.get(0);
        
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetImageMemoryRequirements(ctx.device, image, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
        
        LongBuffer pMemory = LongBuffer.allocate(1);
        result = vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        if (result != VK_SUCCESS) {
            imageInfo.free();
            memReqs.free();
            allocInfo.free();
            throw new RuntimeException("Failed to allocate image memory: " + result);
        }
        
        long memory = pMemory.get(0);
        vkBindImageMemory(ctx.device, image, memory, 0);
        
        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(image)
            .viewType(VK_IMAGE_VIEW_TYPE_2D)
            .format(VK_FORMAT_R8G8B8A8_UNORM);
        
        viewInfo.subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1);
        
        LongBuffer pImageView = LongBuffer.allocate(1);
        result = vkCreateImageView(ctx.device, viewInfo, null, pImageView);
        if (result != VK_SUCCESS) {
            imageInfo.free();
            memReqs.free();
            allocInfo.free();
            viewInfo.free();
            throw new RuntimeException("Failed to create image view: " + result);
        }
        
        long imageView = pImageView.get(0);
        
        // Create default sampler
        long sampler = createDefaultSampler();
        
        long textureId = state.registerTexture(image, memory, imageView, sampler);
        
        imageInfo.free();
        memReqs.free();
        allocInfo.free();
        viewInfo.free();
        
        return textureId;
    }
    
    private static long createDefaultSampler() {
        VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
            .magFilter(VK_FILTER_LINEAR)
            .minFilter(VK_FILTER_LINEAR)
            .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .anisotropyEnable(false)
            .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
            .unnormalizedCoordinates(false)
            .compareEnable(false)
            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
            .mipLodBias(0.0f)
            .minLod(0.0f)
            .maxLod(0.0f);
        
        LongBuffer pSampler = LongBuffer.allocate(1);
        int result = vkCreateSampler(ctx.device, samplerInfo, null, pSampler);
        
        samplerInfo.free();
        
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create sampler: " + result);
        }
        
        return pSampler.get(0);
    }
    
    public static void bindTexture(int target, long texture) {
        checkInitialized();
        
        if (texture == 0) {
            state.unbindTexture(state.activeTextureUnit);
            return;
        }
        
        VulkanState.TextureObject texObj = state.getTexture(texture);
        if (texObj == null) {
            throw new RuntimeException("Invalid texture ID: " + texture);
        }
        
        state.bindTexture(state.activeTextureUnit, texture);
        updateTextureDescriptor(state.activeTextureUnit, texObj.imageView, texObj.sampler);
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
        
        // Wait for GPU to finish using this texture
        vkDeviceWaitIdle(ctx.device);
        
        if (texObj.sampler != VK_NULL_HANDLE) {
            vkDestroySampler(ctx.device, texObj.sampler, null);
        }
        if (texObj.imageView != VK_NULL_HANDLE) {
            vkDestroyImageView(ctx.device, texObj.imageView, null);
        }
        if (texObj.image != VK_NULL_HANDLE) {
            vkDestroyImage(ctx.device, texObj.image, null);
        }
        if (texObj.memory != VK_NULL_HANDLE) {
            vkFreeMemory(ctx.device, texObj.memory, null);
        }
        
        state.unregisterTexture(texture);
    }
    
    // ========================================================================
    // BUFFER OPERATIONS
    // ========================================================================
    
    public static long genBuffer() {
        checkInitialized();
        return state.registerBuffer(VK_NULL_HANDLE, VK_NULL_HANDLE, 0);
    }
    
    public static void bindBuffer(int target, long buffer) {
        checkInitialized();
        state.bindBuffer(target, buffer);
    }
    
    public static void bufferData(int target, long size, ByteBuffer data, int usage) {
        checkInitialized();
        
        long bufferId = state.getBoundBuffer(target);
        if (bufferId == 0) {
            throw new RuntimeException("No buffer bound to target: " + target);
        }
        
        VulkanState.BufferObject bufObj = state.getBuffer(bufferId);
        
        // Destroy old buffer if exists
        if (bufObj.buffer != VK_NULL_HANDLE) {
            vkDeviceWaitIdle(ctx.device);
            vkDestroyBuffer(ctx.device, bufObj.buffer, null);
            if (bufObj.memory != VK_NULL_HANDLE) {
                vkFreeMemory(ctx.device, bufObj.memory, null);
            }
        }
        
        // Create new buffer
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(size)
            .usage(translateBufferUsage(target) | VK_BUFFER_USAGE_TRANSFER_DST_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        
        LongBuffer pBuffer = LongBuffer.allocate(1);
        vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
        long buffer = pBuffer.get(0);
        
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(ctx.device, buffer, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
        
        LongBuffer pMemory = LongBuffer.allocate(1);
        vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        long memory = pMemory.get(0);
        
        vkBindBufferMemory(ctx.device, buffer, memory, 0);
        
        // Upload data if provided
        if (data != null) {
            uploadBufferData(buffer, size, data);
        }
        
        bufObj.buffer = buffer;
        bufObj.memory = memory;
        bufObj.size = size;
        
        bufferInfo.free();
        memReqs.free();
        allocInfo.free();
    }
    
    public static void deleteBuffer(long buffer) {
        checkInitialized();
        
        if (buffer == 0) return;
        
        VulkanState.BufferObject bufObj = state.getBuffer(buffer);
        if (bufObj == null) return;
        
        vkDeviceWaitIdle(ctx.device);
        
        if (bufObj.buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(ctx.device, bufObj.buffer, null);
        }
        if (bufObj.memory != VK_NULL_HANDLE) {
            vkFreeMemory(ctx.device, bufObj.memory, null);
        }
        
        state.unregisterBuffer(buffer);
    }
    
    private static void uploadBufferData(long dstBuffer, long size, ByteBuffer data) {
        // Create staging buffer
        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
            .size(size)
            .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        
        LongBuffer pBuffer = LongBuffer.allocate(1);
        vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
        long stagingBuffer = pBuffer.get(0);
        
        VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(ctx.device, stagingBuffer, memReqs);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memReqs.size())
            .memoryTypeIndex(ctx.findMemoryType(
                memReqs.memoryTypeBits(),
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            ));
        
        LongBuffer pMemory = LongBuffer.allocate(1);
        vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
        long stagingMemory = pMemory.get(0);
        
        vkBindBufferMemory(ctx.device, stagingBuffer, stagingMemory, 0);
        
        // Map and copy
        ByteBuffer mapped = ctx.mapMemory(stagingMemory, 0, size);
        mapped.put(data);
        data.rewind();
        vkUnmapMemory(ctx.device, stagingMemory);
        
        // Copy staging to device
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        
        VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1)
            .srcOffset(0)
            .dstOffset(0)
            .size(size);
        
        vkCmdCopyBuffer(cmdBuffer, stagingBuffer, dstBuffer, copyRegion);
        
        ctx.endSingleTimeCommands(cmdBuffer);
        
        // Cleanup
        vkDestroyBuffer(ctx.device, stagingBuffer, null);
        vkFreeMemory(ctx.device, stagingMemory, null);
        bufferInfo.free();
        memReqs.free();
        allocInfo.free();
        copyRegion.free();
    }
    
    // ========================================================================
    // DRAW CALLS
    // ========================================================================
    
    public static void drawArrays(int mode, int first, int count) {
        checkInitialized();
        
        if (!recordingCommands) {
            beginFrame();
        }
        
        // Get or create pipeline for current state
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        // Bind vertex buffers
        long vertexBuffer = state.getBoundBuffer(0x8892); // GL_ARRAY_BUFFER
        if (vertexBuffer != 0) {
            VulkanState.BufferObject bufObj = state.getBuffer(vertexBuffer);
            if (bufObj.buffer != VK_NULL_HANDLE) {
                LongBuffer pBuffers = LongBuffer.wrap(new long[]{bufObj.buffer});
                LongBuffer pOffsets = LongBuffer.wrap(new long[]{0});
                vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
            }
        }
        
        // Bind descriptor sets
        bindDescriptorSets();
        
        // Draw
        vkCmdDraw(currentCommandBuffer, count, 1, first, 0);
    }
    
    public static void drawElements(int mode, int count, int type, long indices) {
        checkInitialized();
        
        if (!recordingCommands) {
            beginFrame();
        }
        
        long pipeline = getOrCreatePipeline(mode);
        vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        
        // Bind vertex buffer
        long vertexBuffer = state.getBoundBuffer(0x8892);
        if (vertexBuffer != 0) {
            VulkanState.BufferObject bufObj = state.getBuffer(vertexBuffer);
            if (bufObj.buffer != VK_NULL_HANDLE) {
                LongBuffer pBuffers = LongBuffer.wrap(new long[]{bufObj.buffer});
                LongBuffer pOffsets = LongBuffer.wrap(new long[]{0});
                vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
            }
        }
        
        // Bind index buffer
        long indexBuffer = state.getBoundBuffer(0x8893); // GL_ELEMENT_ARRAY_BUFFER
        if (indexBuffer != 0) {
            VulkanState.BufferObject bufObj = state.getBuffer(indexBuffer);
            int indexType = (type == 0x1405) ? VK_INDEX_TYPE_UINT32 : VK_INDEX_TYPE_UINT16;
            vkCmdBindIndexBuffer(currentCommandBuffer, bufObj.buffer, indices, indexType);
        }
        
        bindDescriptorSets();
        
        vkCmdDrawIndexed(currentCommandBuffer, count, 1, 0, 0, 0);
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
        if (shaderObj == null) {
            throw new RuntimeException("Invalid shader: " + shader);
        }
        
        // Compile to SPIR-V
        ByteBuffer spirv = compileGLSLtoSPIRV(shaderObj.source, shaderObj.type);
        
        // Create shader module
        VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
            .pCode(spirv);
        
        LongBuffer pShaderModule = LongBuffer.allocate(1);
        int result = vkCreateShaderModule(ctx.device, createInfo, null, pShaderModule);
        
        createInfo.free();
        
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create shader module: " + result);
        }
        
        shaderObj.spirv = spirv;
        shaderObj.module = pShaderModule.get(0);
        shaderObj.compiled = true;
    }
    
    public static void deleteShader(long shader) {
        checkInitialized();
        
        if (shader == 0) return;
        
        VulkanState.ShaderObject shaderObj = state.getShader(shader);
        if (shaderObj == null) return;
        
        if (shaderObj.module != VK_NULL_HANDLE) {
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
    
    public static void linkProgram(long program) {
        checkInitialized();
        
        VulkanState.ProgramObject progObj = state.getProgram(program);
        if (progObj == null) {
            throw new RuntimeException("Invalid program: " + program);
        }
        
        // Just mark as linked - actual pipeline creation happens on draw
        progObj.linked = true;
    }
    
    public static void deleteProgram(long program) {
        checkInitialized();
        
        if (program == 0) return;
        
        VulkanState.ProgramObject progObj = state.getProgram(program);
        if (progObj == null) return;
        
        vkDeviceWaitIdle(ctx.device);
        
        if (progObj.pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(ctx.device, progObj.pipelineLayout, null);
        }
        
        // Note: Pipelines using this program are cached and will be invalid
        // A proper implementation would track and remove them
        
        state.unregisterProgram(program);
    }
    
    public static void useProgram(long program) {
        checkInitialized();
        state.useProgram(program);
    }
    
    // ========================================================================
    // STATE MANAGEMENT
    // ========================================================================
    
    public static void enable(int cap) {
        checkInitialized();
        state.enable(cap);
    }
    
    public static void disable(int cap) {
        checkInitialized();
        state.disable(cap);
    }
    
    public static void blendFunc(int sfactor, int dfactor) {
        checkInitialized();
        state.setBlendFunc(sfactor, dfactor);
    }
    
    public static void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        checkInitialized();
        state.setBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    
    public static void depthFunc(int func) {
        checkInitialized();
        state.setDepthFunc(func);
    }
    
    public static void depthMask(boolean flag) {
        checkInitialized();
        state.setDepthMask(flag);
    }
    
    public static void cullFace(int mode) {
        checkInitialized();
        state.setCullFace(mode);
    }
    
    public static void frontFace(int mode) {
        checkInitialized();
        state.setFrontFace(mode);
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
    
    public static void viewport(int x, int y, int width, int height) {
        checkInitialized();
        state.setViewport(x, y, width, height);
    }
    
    public static void scissor(int x, int y, int width, int height) {
        checkInitialized();
        state.setScissor(x, y, width, height);
    }
    
    // ========================================================================
    // VERTEX ATTRIBUTES
    // ========================================================================
    
    public static void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        checkInitialized();
        state.setVertexAttribPointer(index, size, type, normalized, stride, pointer);
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
    
    public static void bindVertexArray(long vao) {
        checkInitialized();
        state.bindVertexArray(vao);
    }
    
    public static void deleteVertexArray(long vao) {
        checkInitialized();
        state.deleteVertexArray(vao);
    }
    
    // ========================================================================
    // UNIFORMS
    // ========================================================================
    
    public static int getUniformLocation(long program, String name) {
        checkInitialized();
        return state.getUniformLocation(program, name);
    }
    
    public static void uniform1i(int location, int v0) {
        checkInitialized();
        state.setUniform(location, v0);
    }
    
    public static void uniform1f(int location, float v0) {
        checkInitialized();
        state.setUniform(location, v0);
    }
    
    public static void uniform2f(int location, float v0, float v1) {
        checkInitialized();
        state.setUniform(location, v0, v1);
    }
    
    public static void uniform3f(int location, float v0, float v1, float v2) {
        checkInitialized();
        state.setUniform(location, v0, v1, v2);
    }
    
    public static void uniform4f(int location, float v0, float v1, float v2, float v3) {
        checkInitialized();
        state.setUniform(location, v0, v1, v2, v3);
    }
    
    public static void uniformMatrix4fv(int location, boolean transpose, float[] value) {
        checkInitialized();
        state.setUniformMatrix4(location, transpose, value);
    }
    
    // ========================================================================
    // HELPER METHODS
    // ========================================================================
    
    private static void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("VulkanCallMapperX not initialized");
        }
    }
    
    private static void bindDescriptorSets() {
        long descriptorSet = descriptorSets[currentDescriptorSetIndex];
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        
        if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE && descriptorSet != VK_NULL_HANDLE) {
            LongBuffer pDescriptorSets = LongBuffer.wrap(new long[]{descriptorSet});
            vkCmdBindDescriptorSets(
                currentCommandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                prog.pipelineLayout,
                0,
                pDescriptorSets,
                null
            );
        }
    }
    
    private static void updateTextureDescriptor(int unit, long imageView, long sampler) {
        if (imageView == VK_NULL_HANDLE || sampler == VK_NULL_HANDLE) return;
        
        long descriptorSet = descriptorSets[currentDescriptorSetIndex];
        
        VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1);
        imageInfo.get(0)
            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .imageView(imageView)
            .sampler(sampler);
        
        VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1);
        descriptorWrite.get(0)
            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            .dstSet(descriptorSet)
            .dstBinding(unit + 1) // +1 because binding 0 is UBO
            .dstArrayElement(0)
            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .pImageInfo(imageInfo);
        
        vkUpdateDescriptorSets(ctx.device, descriptorWrite, null);
        
        imageInfo.free();
        descriptorWrite.free();
    }
    
    private static int translatePrimitiveTopology(int glMode) {
        return switch (glMode) {
            case 0x0000 -> VK_PRIMITIVE_TOPOLOGY_POINT_LIST;         // GL_POINTS
            case 0x0001 -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST;          // GL_LINES
            case 0x0002 -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST;          // GL_LINE_LOOP (approx)
            case 0x0003 -> VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;         // GL_LINE_STRIP
            case 0x0004 -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;      // GL_TRIANGLES
            case 0x0005 -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;     // GL_TRIANGLE_STRIP
            case 0x0006 -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;       // GL_TRIANGLE_FAN
            case 0x0007 -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST_WITH_ADJACENCY; // GL_QUADS (approx)
            default -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        };
    }
    
    private static int translateDepthFunc(int glFunc) {
        return switch (glFunc) {
            case 0x0200 -> VK_COMPARE_OP_NEVER;           // GL_NEVER
            case 0x0201 -> VK_COMPARE_OP_LESS;            // GL_LESS
            case 0x0202 -> VK_COMPARE_OP_EQUAL;           // GL_EQUAL
            case 0x0203 -> VK_COMPARE_OP_LESS_OR_EQUAL;   // GL_LEQUAL
            case 0x0204 -> VK_COMPARE_OP_GREATER;         // GL_GREATER
            case 0x0205 -> VK_COMPARE_OP_NOT_EQUAL;       // GL_NOTEQUAL
            case 0x0206 -> VK_COMPARE_OP_GREATER_OR_EQUAL; // GL_GEQUAL
            case 0x0207 -> VK_COMPARE_OP_ALWAYS;          // GL_ALWAYS
            default -> VK_COMPARE_OP_LESS;
        };
    }
    
    private static int translateBlendFactor(int glFactor) {
        return switch (glFactor) {
            case 0 -> VK_BLEND_FACTOR_ZERO;                      // GL_ZERO
            case 1 -> VK_BLEND_FACTOR_ONE;                       // GL_ONE
            case 0x0300 -> VK_BLEND_FACTOR_SRC_COLOR;            // GL_SRC_COLOR
            case 0x0301 -> VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;  // GL_ONE_MINUS_SRC_COLOR
            case 0x0302 -> VK_BLEND_FACTOR_SRC_ALPHA;            // GL_SRC_ALPHA
            case 0x0303 -> VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;  // GL_ONE_MINUS_SRC_ALPHA
            case 0x0304 -> VK_BLEND_FACTOR_DST_ALPHA;            // GL_DST_ALPHA
            case 0x0305 -> VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;  // GL_ONE_MINUS_DST_ALPHA
            case 0x0306 -> VK_BLEND_FACTOR_DST_COLOR;            // GL_DST_COLOR
            case 0x0307 -> VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;  // GL_ONE_MINUS_DST_COLOR
            case 0x0308 -> VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;   // GL_SRC_ALPHA_SATURATE
            default -> VK_BLEND_FACTOR_ONE;
        };
    }
    
    private static int translateCullMode(int glMode) {
        return switch (glMode) {
            case 0x0404 -> VK_CULL_MODE_FRONT_BIT;               // GL_FRONT
            case 0x0405 -> VK_CULL_MODE_BACK_BIT;                // GL_BACK
            case 0x0408 -> VK_CULL_MODE_FRONT_AND_BACK;          // GL_FRONT_AND_BACK
            default -> VK_CULL_MODE_BACK_BIT;
        };
    }
    
    private static int translateFrontFace(int glMode) {
        return switch (glMode) {
            case 0x0900 -> VK_FRONT_FACE_CLOCKWISE;              // GL_CW
            case 0x0901 -> VK_FRONT_FACE_COUNTER_CLOCKWISE;      // GL_CCW
            default -> VK_FRONT_FACE_COUNTER_CLOCKWISE;
        };
    }
    
    private static int translatePolygonMode(int glMode) {
        return switch (glMode) {
            case 0x1B00 -> VK_POLYGON_MODE_POINT;                // GL_POINT
            case 0x1B01 -> VK_POLYGON_MODE_LINE;                 // GL_LINE
            case 0x1B02 -> VK_POLYGON_MODE_FILL;                 // GL_FILL
            default -> VK_POLYGON_MODE_FILL;
        };
    }
    
    private static int translateBufferUsage(int glTarget) {
        return switch (glTarget) {
            case 0x8892 -> VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;    // GL_ARRAY_BUFFER
            case 0x8893 -> VK_BUFFER_USAGE_INDEX_BUFFER_BIT;     // GL_ELEMENT_ARRAY_BUFFER
            case 0x8A11 -> VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;   // GL_UNIFORM_BUFFER
            default -> VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        };
    }
    
    // ========================================================================
    // DEBUG
    // ========================================================================
    
    public static String getStatusReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VulkanCallMapperX Status ===\n");
        sb.append("Initialized: ").append(initialized).append("\n");
        sb.append("Recording: ").append(recordingCommands).append("\n");
        sb.append("Pipeline Cache: ").append(pipelineCache.size()).append(" entries\n");
        sb.append("Descriptor Sets: ").append(MAX_DESCRIPTOR_SETS).append("\n");
        if (state != null) {
            sb.append("Active Texture Unit: ").append(state.activeTextureUnit).append("\n");
            sb.append("Current Program: ").append(state.currentProgram).append("\n");
        }
        return sb.toString();
    };
}

// ========================================================================
// TEXTURE UPLOAD OPERATIONS (add to VulkanCallMapperX.java)
// ========================================================================

/**
 * GL: glTexImage2D(target, level, internalFormat, width, height, border, format, type, data)
 * VK: Create/recreate image with new dimensions and upload data
 */
public static void texImage2D(int target, int level, int internalFormat,
                               int width, int height, int border,
                               int format, int type, ByteBuffer data) {
    checkInitialized();
    
    long texture = state.getBoundTexture(state.activeTextureUnit);
    if (texture == 0) {
        throw new RuntimeException("No texture bound");
    }
    
    VulkanState.TextureObject texObj = state.getTexture(texture);
    int vkFormat = translateFormatToVulkan(internalFormat);
    
    // Destroy old image if exists and size changed
    if (texObj.image != VK_NULL_HANDLE) {
        if (texObj.width != width || texObj.height != height || texObj.format != vkFormat) {
            vkDeviceWaitIdle(ctx.device);
            
            if (texObj.imageView != VK_NULL_HANDLE) {
                vkDestroyImageView(ctx.device, texObj.imageView, null);
            }
            vkDestroyImage(ctx.device, texObj.image, null);
            if (texObj.memory != VK_NULL_HANDLE) {
                vkFreeMemory(ctx.device, texObj.memory, null);
            }
            
            texObj.image = VK_NULL_HANDLE;
            texObj.imageView = VK_NULL_HANDLE;
            texObj.memory = VK_NULL_HANDLE;
        }
    }
    
    // Create new image if needed
    if (texObj.image == VK_NULL_HANDLE) {
        createTextureImage(texObj, width, height, vkFormat);
    }
    
    // Upload data if provided
    if (data != null && data.remaining() > 0) {
        uploadTextureData(texObj.image, width, height, format, type, data);
    }
    
    texObj.width = width;
    texObj.height = height;
    texObj.format = vkFormat;
}

/**
 * GL: glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, data)
 * VK: Upload partial texture data
 */
public static void texSubImage2D(int target, int level, int xoffset, int yoffset,
                                  int width, int height, int format, int type,
                                  ByteBuffer data) {
    checkInitialized();
    
    long texture = state.getBoundTexture(state.activeTextureUnit);
    if (texture == 0) {
        throw new RuntimeException("No texture bound");
    }
    
    VulkanState.TextureObject texObj = state.getTexture(texture);
    if (texObj.image == VK_NULL_HANDLE) {
        throw new RuntimeException("Texture image not created - call texImage2D first");
    }
    
    if (data != null && data.remaining() > 0) {
        uploadTextureDataRegion(texObj.image, xoffset, yoffset, width, height, format, type, data);
    }
}

/**
 * GL: glTexParameteri(target, pname, param)
 * VK: Update sampler settings (recreate sampler)
 */
public static void texParameteri(int target, int pname, int param) {
    checkInitialized();
    
    long texture = state.getBoundTexture(state.activeTextureUnit);
    if (texture == 0) return;
    
    VulkanState.TextureObject texObj = state.getTexture(texture);
    
    // Store parameter
    texObj.setParameter(pname, param);
    
    // Recreate sampler with new parameters
    if (texObj.sampler != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(ctx.device);
        vkDestroySampler(ctx.device, texObj.sampler, null);
    }
    
    texObj.sampler = createSamplerForTexture(texObj);
}

/**
 * GL: glTexParameterf(target, pname, param)
 * VK: Update sampler settings
 */
public static void texParameterf(int target, int pname, float param) {
    checkInitialized();
    
    long texture = state.getBoundTexture(state.activeTextureUnit);
    if (texture == 0) return;
    
    VulkanState.TextureObject texObj = state.getTexture(texture);
    texObj.setParameterf(pname, param);
    
    if (texObj.sampler != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(ctx.device);
        vkDestroySampler(ctx.device, texObj.sampler, null);
    }
    
    texObj.sampler = createSamplerForTexture(texObj);
}

/**
 * GL: glGenerateMipmap(target)
 * VK: Generate mipmaps using vkCmdBlitImage
 */
public static void generateMipmap(int target) {
    checkInitialized();
    
    long texture = state.getBoundTexture(state.activeTextureUnit);
    if (texture == 0) return;
    
    VulkanState.TextureObject texObj = state.getTexture(texture);
    if (texObj.image == VK_NULL_HANDLE) return;
    
    // Calculate mip levels
    int mipLevels = (int) Math.floor(Math.log(Math.max(texObj.width, texObj.height)) / Math.log(2)) + 1;
    
    if (mipLevels <= 1) return;
    
    // Need to recreate image with mipmap storage
    // This is complex - for now, just mark as needing mipmaps
    texObj.mipLevels = mipLevels;
    texObj.needsMipmapGeneration = true;
    
    // TODO: Full mipmap generation requires:
    // 1. Recreate image with mipLevels > 1
    // 2. Transition each level and blit from previous level
    FPSFlux.LOGGER.warn("[VulkanCallMapperX] generateMipmap not fully implemented");
}

/**
 * GL: glCopyTexImage2D
 * VK: Copy from framebuffer to texture
 */
public static void copyTexImage2D(int target, int level, int internalFormat,
                                   int x, int y, int width, int height, int border) {
    checkInitialized();
    
    long texture = state.getBoundTexture(state.activeTextureUnit);
    if (texture == 0) {
        throw new RuntimeException("No texture bound");
    }
    
    VulkanState.TextureObject texObj = state.getTexture(texture);
    int vkFormat = translateFormatToVulkan(internalFormat);
    
    // Ensure texture exists with correct size
    if (texObj.image == VK_NULL_HANDLE || 
        texObj.width != width || texObj.height != height) {
        
        if (texObj.image != VK_NULL_HANDLE) {
            vkDeviceWaitIdle(ctx.device);
            vkDestroyImageView(ctx.device, texObj.imageView, null);
            vkDestroyImage(ctx.device, texObj.image, null);
            vkFreeMemory(ctx.device, texObj.memory, null);
        }
        
        createTextureImage(texObj, width, height, vkFormat);
        texObj.width = width;
        texObj.height = height;
        texObj.format = vkFormat;
    }
    
    // Copy from current framebuffer
    VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
    
    // Transition texture to transfer dst
    transitionImageLayout(cmdBuffer, texObj.image, 
        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    
    // Get current framebuffer image
    long srcImage = ctx.getCurrentSwapchainImage();
    
    // Transition src to transfer src
    transitionImageLayout(cmdBuffer, srcImage,
        VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
    
    // Copy region
    VkImageCopy.Buffer region = VkImageCopy.calloc(1);
    region.get(0).srcSubresource()
        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        .mipLevel(0)
        .baseArrayLayer(0)
        .layerCount(1);
    region.get(0).srcOffset().set(x, y, 0);
    region.get(0).dstSubresource()
        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        .mipLevel(0)
        .baseArrayLayer(0)
        .layerCount(1);
    region.get(0).dstOffset().set(0, 0, 0);
    region.get(0).extent().set(width, height, 1);
    
    vkCmdCopyImage(cmdBuffer, srcImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        texObj.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
    
    // Transition back
    transitionImageLayout(cmdBuffer, srcImage,
        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
    transitionImageLayout(cmdBuffer, texObj.image,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    
    ctx.endSingleTimeCommands(cmdBuffer);
    
    region.free();
}

// ========================================================================
// GPU-SIDE EXECUTION (add to VulkanCallMapperX.java)
// ========================================================================

// Additional imports needed
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// ========================================================================
// GPU COMMAND EXECUTION SYSTEM
// ========================================================================

// Command types for deferred execution
private static enum CommandType {
    DRAW_ARRAYS,
    DRAW_ELEMENTS,
    DRAW_ARRAYS_INSTANCED,
    DRAW_ELEMENTS_INSTANCED,
    DISPATCH_COMPUTE,
    COPY_BUFFER,
    COPY_IMAGE,
    BLIT_IMAGE,
    CLEAR_COLOR,
    CLEAR_DEPTH,
    MEMORY_BARRIER,
    PUSH_CONSTANTS
}

// Deferred command structure
private static class GPUCommand {
    CommandType type;
    int mode;
    int first;
    int count;
    int instanceCount;
    int baseInstance;
    int indexType;
    long indices;
    long srcBuffer;
    long dstBuffer;
    long srcImage;
    long dstImage;
    int srcOffsetX, srcOffsetY, srcOffsetZ;
    int dstOffsetX, dstOffsetY, dstOffsetZ;
    int width, height, depth;
    float[] clearColor;
    float clearDepth;
    int clearStencil;
    long pipeline;
    long descriptorSet;
    long[] vertexBuffers;
    long[] vertexOffsets;
    long indexBuffer;
    long indexOffset;
    byte[] pushConstantData;
    int pushConstantOffset;
    int pushConstantStageFlags;
    int groupCountX, groupCountY, groupCountZ;
}

// Command queue for batching
private static final ConcurrentLinkedQueue<GPUCommand> commandQueue = new ConcurrentLinkedQueue<>();
private static final int MAX_COMMANDS_PER_BATCH = 1024;
private static final AtomicBoolean gpuBusy = new AtomicBoolean(false);
private static final AtomicLong frameNumber = new AtomicLong(0);

// Multiple command buffers for parallel recording
private static final int NUM_COMMAND_BUFFERS = 3;
private static VkCommandBuffer[] commandBuffers;
private static long[] commandBufferFences;
private static int currentCommandBufferIndex = 0;

// Secondary command buffers for parallel recording
private static final int NUM_SECONDARY_BUFFERS = 8;
private static VkCommandBuffer[] secondaryCommandBuffers;
private static final AtomicBoolean[] secondaryBufferInUse = new AtomicBoolean[NUM_SECONDARY_BUFFERS];

// GPU timeline semaphore for synchronization (Vulkan 1.2+)
private static long timelineSemaphore = VK_NULL_HANDLE;
private static final AtomicLong timelineValue = new AtomicLong(0);

// ========================================================================
// INITIALIZATION
// ========================================================================

/**
 * Initialize GPU execution system
 */
private static void initializeGPUExecution() {
    // Create command buffers
    commandBuffers = new VkCommandBuffer[NUM_COMMAND_BUFFERS];
    commandBufferFences = new long[NUM_COMMAND_BUFFERS];
    
    VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
        .commandPool(ctx.commandPool)
        .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
        .commandBufferCount(NUM_COMMAND_BUFFERS);
    
    PointerBuffer pCommandBuffers = PointerBuffer.allocateDirect(NUM_COMMAND_BUFFERS);
    vkAllocateCommandBuffers(ctx.device, allocInfo, pCommandBuffers);
    
    for (int i = 0; i < NUM_COMMAND_BUFFERS; i++) {
        commandBuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), ctx.device);
        
        // Create fence for each command buffer
        VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
            .flags(VK_FENCE_CREATE_SIGNALED_BIT);
        
        LongBuffer pFence = LongBuffer.allocate(1);
        vkCreateFence(ctx.device, fenceInfo, null, pFence);
        commandBufferFences[i] = pFence.get(0);
        
        fenceInfo.free();
    }
    
    allocInfo.free();
    
    // Create secondary command buffers for parallel recording
    secondaryCommandBuffers = new VkCommandBuffer[NUM_SECONDARY_BUFFERS];
    
    VkCommandBufferAllocateInfo secondaryAllocInfo = VkCommandBufferAllocateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
        .commandPool(ctx.commandPool)
        .level(VK_COMMAND_BUFFER_LEVEL_SECONDARY)
        .commandBufferCount(NUM_SECONDARY_BUFFERS);
    
    PointerBuffer pSecondaryBuffers = PointerBuffer.allocateDirect(NUM_SECONDARY_BUFFERS);
    vkAllocateCommandBuffers(ctx.device, secondaryAllocInfo, pSecondaryBuffers);
    
    for (int i = 0; i < NUM_SECONDARY_BUFFERS; i++) {
        secondaryCommandBuffers[i] = new VkCommandBuffer(pSecondaryBuffers.get(i), ctx.device);
        secondaryBufferInUse[i] = new AtomicBoolean(false);
    }
    
    secondaryAllocInfo.free();
    
    // Create timeline semaphore if Vulkan 1.2+ available
    if (ctx.vulkanVersion >= VK_API_VERSION_1_2) {
        createTimelineSemaphore();
    }
    
    FPSFlux.LOGGER.info("[VulkanCallMapperX] GPU execution system initialized");
}

/**
 * Create timeline semaphore for fine-grained synchronization
 */
private static void createTimelineSemaphore() {
    VkSemaphoreTypeCreateInfo typeInfo = VkSemaphoreTypeCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO)
        .semaphoreType(VK_SEMAPHORE_TYPE_TIMELINE)
        .initialValue(0);
    
    VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
        .pNext(typeInfo.address());
    
    LongBuffer pSemaphore = LongBuffer.allocate(1);
    int result = vkCreateSemaphore(ctx.device, semaphoreInfo, null, pSemaphore);
    
    if (result == VK_SUCCESS) {
        timelineSemaphore = pSemaphore.get(0);
        FPSFlux.LOGGER.info("[VulkanCallMapperX] Timeline semaphore created");
    }
    
    typeInfo.free();
    semaphoreInfo.free();
}

// ========================================================================
// COMMAND RECORDING
// ========================================================================

/**
 * Get next available command buffer
 */
private static VkCommandBuffer acquireCommandBuffer() {
    int index = currentCommandBufferIndex;
    long fence = commandBufferFences[index];
    
    // Wait for this command buffer to be available
    vkWaitForFences(ctx.device, new long[]{fence}, true, Long.MAX_VALUE);
    vkResetFences(ctx.device, new long[]{fence});
    
    // Reset command buffer
    vkResetCommandBuffer(commandBuffers[index], 0);
    
    currentCommandBufferIndex = (currentCommandBufferIndex + 1) % NUM_COMMAND_BUFFERS;
    
    return commandBuffers[index];
}

/**
 * Get a secondary command buffer for parallel recording
 */
private static VkCommandBuffer acquireSecondaryCommandBuffer() {
    for (int i = 0; i < NUM_SECONDARY_BUFFERS; i++) {
        if (secondaryBufferInUse[i].compareAndSet(false, true)) {
            vkResetCommandBuffer(secondaryCommandBuffers[i], 0);
            return secondaryCommandBuffers[i];
        }
    }
    
    // All buffers in use, wait for first one
    FPSFlux.LOGGER.warn("[VulkanCallMapperX] All secondary buffers in use, blocking");
    while (!secondaryBufferInUse[0].compareAndSet(false, true)) {
        Thread.yield();
    }
    vkResetCommandBuffer(secondaryCommandBuffers[0], 0);
    return secondaryCommandBuffers[0];
}

/**
 * Release secondary command buffer
 */
private static void releaseSecondaryCommandBuffer(VkCommandBuffer buffer) {
    for (int i = 0; i < NUM_SECONDARY_BUFFERS; i++) {
        if (secondaryCommandBuffers[i] == buffer) {
            secondaryBufferInUse[i].set(false);
            return;
        }
    }
}

/**
 * Begin recording secondary command buffer
 */
private static void beginSecondaryCommandBuffer(VkCommandBuffer cmdBuffer, long renderPass, long framebuffer) {
    VkCommandBufferInheritanceInfo inheritanceInfo = VkCommandBufferInheritanceInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO)
        .renderPass(renderPass)
        .subpass(0)
        .framebuffer(framebuffer);
    
    VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
        .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT | 
               VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT)
        .pInheritanceInfo(inheritanceInfo);
    
    vkBeginCommandBuffer(cmdBuffer, beginInfo);
    
    inheritanceInfo.free();
    beginInfo.free();
}

// ========================================================================
// DEFERRED COMMAND EXECUTION
// ========================================================================

/**
 * Queue a draw arrays command for deferred execution
 */
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
    
    // Capture current vertex buffer bindings
    long vbo = state.getBoundBuffer(0x8892); // GL_ARRAY_BUFFER
    if (vbo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(vbo);
        cmd.vertexBuffers = new long[]{bufObj.buffer};
        cmd.vertexOffsets = new long[]{0};
    }
    
    commandQueue.add(cmd);
    
    // Auto-flush if queue is full
    if (commandQueue.size() >= MAX_COMMANDS_PER_BATCH) {
        flushCommands();
    }
}

/**
 * Queue a draw elements command for deferred execution
 */
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
    
    // Capture current buffer bindings
    long vbo = state.getBoundBuffer(0x8892);
    if (vbo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(vbo);
        cmd.vertexBuffers = new long[]{bufObj.buffer};
        cmd.vertexOffsets = new long[]{0};
    }
    
    long ibo = state.getBoundBuffer(0x8893); // GL_ELEMENT_ARRAY_BUFFER
    if (ibo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(ibo);
        cmd.indexBuffer = bufObj.buffer;
        cmd.indexOffset = indices;
    }
    
    commandQueue.add(cmd);
    
    if (commandQueue.size() >= MAX_COMMANDS_PER_BATCH) {
        flushCommands();
    }
}

/**
 * Queue instanced draw arrays
 */
public static void queueDrawArraysInstanced(int mode, int first, int count, int instanceCount) {
    checkInitialized();
    
    GPUCommand cmd = new GPUCommand();
    cmd.type = CommandType.DRAW_ARRAYS_INSTANCED;
    cmd.mode = mode;
    cmd.first = first;
    cmd.count = count;
    cmd.instanceCount = instanceCount;
    cmd.baseInstance = 0;
    cmd.pipeline = getOrCreatePipeline(mode);
    cmd.descriptorSet = descriptorSets[currentDescriptorSetIndex];
    
    long vbo = state.getBoundBuffer(0x8892);
    if (vbo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(vbo);
        cmd.vertexBuffers = new long[]{bufObj.buffer};
        cmd.vertexOffsets = new long[]{0};
    }
    
    commandQueue.add(cmd);
    
    if (commandQueue.size() >= MAX_COMMANDS_PER_BATCH) {
        flushCommands();
    }
}

/**
 * Queue instanced draw elements
 */
public static void queueDrawElementsInstanced(int mode, int count, int type, long indices, int instanceCount) {
    checkInitialized();
    
    GPUCommand cmd = new GPUCommand();
    cmd.type = CommandType.DRAW_ELEMENTS_INSTANCED;
    cmd.mode = mode;
    cmd.count = count;
    cmd.indexType = (type == 0x1405) ? VK_INDEX_TYPE_UINT32 : VK_INDEX_TYPE_UINT16;
    cmd.indices = indices;
    cmd.instanceCount = instanceCount;
    cmd.baseInstance = 0;
    cmd.pipeline = getOrCreatePipeline(mode);
    cmd.descriptorSet = descriptorSets[currentDescriptorSetIndex];
    
    long vbo = state.getBoundBuffer(0x8892);
    if (vbo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(vbo);
        cmd.vertexBuffers = new long[]{bufObj.buffer};
        cmd.vertexOffsets = new long[]{0};
    }
    
    long ibo = state.getBoundBuffer(0x8893);
    if (ibo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(ibo);
        cmd.indexBuffer = bufObj.buffer;
        cmd.indexOffset = indices;
    }
    
    commandQueue.add(cmd);
    
    if (commandQueue.size() >= MAX_COMMANDS_PER_BATCH) {
        flushCommands();
    }
}

/**
 * Queue compute shader dispatch
 */
public static void queueDispatchCompute(int groupCountX, int groupCountY, int groupCountZ) {
    checkInitialized();
    
    GPUCommand cmd = new GPUCommand();
    cmd.type = CommandType.DISPATCH_COMPUTE;
    cmd.groupCountX = groupCountX;
    cmd.groupCountY = groupCountY;
    cmd.groupCountZ = groupCountZ;
    cmd.pipeline = state.getCurrentComputePipeline();
    cmd.descriptorSet = descriptorSets[currentDescriptorSetIndex];
    
    commandQueue.add(cmd);
    
    if (commandQueue.size() >= MAX_COMMANDS_PER_BATCH) {
        flushCommands();
    }
}

/**
 * Queue buffer copy
 */
public static void queueCopyBuffer(long srcBuffer, long dstBuffer, long srcOffset, long dstOffset, long size) {
    checkInitialized();
    
    GPUCommand cmd = new GPUCommand();
    cmd.type = CommandType.COPY_BUFFER;
    cmd.srcBuffer = srcBuffer;
    cmd.dstBuffer = dstBuffer;
    cmd.srcOffsetX = (int) srcOffset;
    cmd.dstOffsetX = (int) dstOffset;
    cmd.width = (int) size;
    
    commandQueue.add(cmd);
}

/**
 * Queue image copy
 */
public static void queueCopyImage(long srcImage, long dstImage, 
                                   int srcX, int srcY, int dstX, int dstY,
                                   int width, int height) {
    checkInitialized();
    
    GPUCommand cmd = new GPUCommand();
    cmd.type = CommandType.COPY_IMAGE;
    cmd.srcImage = srcImage;
    cmd.dstImage = dstImage;
    cmd.srcOffsetX = srcX;
    cmd.srcOffsetY = srcY;
    cmd.dstOffsetX = dstX;
    cmd.dstOffsetY = dstY;
    cmd.width = width;
    cmd.height = height;
    cmd.depth = 1;
    
    commandQueue.add(cmd);
}

/**
 * Queue image blit (with scaling)
 */
public static void queueBlitImage(long srcImage, long dstImage,
                                   int srcX0, int srcY0, int srcX1, int srcY1,
                                   int dstX0, int dstY0, int dstX1, int dstY1,
                                   int filter) {
    checkInitialized();
    
    GPUCommand cmd = new GPUCommand();
    cmd.type = CommandType.BLIT_IMAGE;
    cmd.srcImage = srcImage;
    cmd.dstImage = dstImage;
    cmd.srcOffsetX = srcX0;
    cmd.srcOffsetY = srcY0;
    cmd.width = srcX1; // Store as second offset
    cmd.height = srcY1;
    cmd.dstOffsetX = dstX0;
    cmd.dstOffsetY = dstY0;
    cmd.depth = dstX1;
    cmd.mode = dstY1;
    cmd.indexType = filter;
    
    commandQueue.add(cmd);
}

/**
 * Queue push constants update
 */
public static void queuePushConstants(int stageFlags, int offset, byte[] data) {
    checkInitialized();
    
    GPUCommand cmd = new GPUCommand();
    cmd.type = CommandType.PUSH_CONSTANTS;
    cmd.pushConstantStageFlags = stageFlags;
    cmd.pushConstantOffset = offset;
    cmd.pushConstantData = data.clone();
    
    commandQueue.add(cmd);
}

/**
 * Queue memory barrier
 */
public static void queueMemoryBarrier(int srcAccessMask, int dstAccessMask,
                                       int srcStageMask, int dstStageMask) {
    checkInitialized();
    
    GPUCommand cmd = new GPUCommand();
    cmd.type = CommandType.MEMORY_BARRIER;
    cmd.srcOffsetX = srcAccessMask;
    cmd.srcOffsetY = dstAccessMask;
    cmd.dstOffsetX = srcStageMask;
    cmd.dstOffsetY = dstStageMask;
    
    commandQueue.add(cmd);
}

// ========================================================================
// COMMAND FLUSHING AND EXECUTION
// ========================================================================

/**
 * Flush all queued commands to GPU
 */
public static void flushCommands() {
    if (commandQueue.isEmpty()) return;
    
    checkInitialized();
    
    if (!recordingCommands) {
        beginFrame();
    }
    
    // Process all queued commands
    GPUCommand cmd;
    while ((cmd = commandQueue.poll()) != null) {
        executeCommand(cmd);
    }
}

/**
 * Execute a single GPU command
 */
private static void executeCommand(GPUCommand cmd) {
    switch (cmd.type) {
        case DRAW_ARRAYS -> executeDrawArrays(cmd);
        case DRAW_ELEMENTS -> executeDrawElements(cmd);
        case DRAW_ARRAYS_INSTANCED -> executeDrawArraysInstanced(cmd);
        case DRAW_ELEMENTS_INSTANCED -> executeDrawElementsInstanced(cmd);
        case DISPATCH_COMPUTE -> executeDispatchCompute(cmd);
        case COPY_BUFFER -> executeCopyBuffer(cmd);
        case COPY_IMAGE -> executeCopyImage(cmd);
        case BLIT_IMAGE -> executeBlitImage(cmd);
        case MEMORY_BARRIER -> executeMemoryBarrier(cmd);
        case PUSH_CONSTANTS -> executePushConstants(cmd);
    }
}

private static void executeDrawArrays(GPUCommand cmd) {
    vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipeline);
    
    if (cmd.vertexBuffers != null && cmd.vertexBuffers.length > 0) {
        LongBuffer pBuffers = LongBuffer.wrap(cmd.vertexBuffers);
        LongBuffer pOffsets = LongBuffer.wrap(cmd.vertexOffsets);
        vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
    }
    
    if (cmd.descriptorSet != VK_NULL_HANDLE) {
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE) {
            LongBuffer pDescriptorSets = LongBuffer.wrap(new long[]{cmd.descriptorSet});
            vkCmdBindDescriptorSets(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                prog.pipelineLayout, 0, pDescriptorSets, null);
        }
    }
    
    vkCmdDraw(currentCommandBuffer, cmd.count, 1, cmd.first, 0);
}

private static void executeDrawElements(GPUCommand cmd) {
    vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipeline);
    
    if (cmd.vertexBuffers != null && cmd.vertexBuffers.length > 0) {
        LongBuffer pBuffers = LongBuffer.wrap(cmd.vertexBuffers);
        LongBuffer pOffsets = LongBuffer.wrap(cmd.vertexOffsets);
        vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
    }
    
    if (cmd.indexBuffer != VK_NULL_HANDLE) {
        vkCmdBindIndexBuffer(currentCommandBuffer, cmd.indexBuffer, cmd.indexOffset, cmd.indexType);
    }
    
    if (cmd.descriptorSet != VK_NULL_HANDLE) {
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE) {
            LongBuffer pDescriptorSets = LongBuffer.wrap(new long[]{cmd.descriptorSet});
            vkCmdBindDescriptorSets(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                prog.pipelineLayout, 0, pDescriptorSets, null);
        }
    }
    
    vkCmdDrawIndexed(currentCommandBuffer, cmd.count, 1, 0, 0, 0);
}

private static void executeDrawArraysInstanced(GPUCommand cmd) {
    vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipeline);
    
    if (cmd.vertexBuffers != null && cmd.vertexBuffers.length > 0) {
        LongBuffer pBuffers = LongBuffer.wrap(cmd.vertexBuffers);
        LongBuffer pOffsets = LongBuffer.wrap(cmd.vertexOffsets);
        vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
    }
    
    if (cmd.descriptorSet != VK_NULL_HANDLE) {
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE) {
            LongBuffer pDescriptorSets = LongBuffer.wrap(new long[]{cmd.descriptorSet});
            vkCmdBindDescriptorSets(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                prog.pipelineLayout, 0, pDescriptorSets, null);
        }
    }
    
    vkCmdDraw(currentCommandBuffer, cmd.count, cmd.instanceCount, cmd.first, cmd.baseInstance);
}

private static void executeDrawElementsInstanced(GPUCommand cmd) {
    vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, cmd.pipeline);
    
    if (cmd.vertexBuffers != null && cmd.vertexBuffers.length > 0) {
        LongBuffer pBuffers = LongBuffer.wrap(cmd.vertexBuffers);
        LongBuffer pOffsets = LongBuffer.wrap(cmd.vertexOffsets);
        vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
    }
    
    if (cmd.indexBuffer != VK_NULL_HANDLE) {
        vkCmdBindIndexBuffer(currentCommandBuffer, cmd.indexBuffer, cmd.indexOffset, cmd.indexType);
    }
    
    if (cmd.descriptorSet != VK_NULL_HANDLE) {
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE) {
            LongBuffer pDescriptorSets = LongBuffer.wrap(new long[]{cmd.descriptorSet});
            vkCmdBindDescriptorSets(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                prog.pipelineLayout, 0, pDescriptorSets, null);
        }
    }
    
    vkCmdDrawIndexed(currentCommandBuffer, cmd.count, cmd.instanceCount, 0, 0, cmd.baseInstance);
}

private static void executeDispatchCompute(GPUCommand cmd) {
    if (cmd.pipeline == VK_NULL_HANDLE) {
        FPSFlux.LOGGER.warn("[VulkanCallMapperX] No compute pipeline bound");
        return;
    }
    
    vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, cmd.pipeline);
    
    if (cmd.descriptorSet != VK_NULL_HANDLE) {
        VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
        if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE) {
            LongBuffer pDescriptorSets = LongBuffer.wrap(new long[]{cmd.descriptorSet});
            vkCmdBindDescriptorSets(currentCommandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                prog.pipelineLayout, 0, pDescriptorSets, null);
        }
    }
    
    vkCmdDispatch(currentCommandBuffer, cmd.groupCountX, cmd.groupCountY, cmd.groupCountZ);
}

private static void executeCopyBuffer(GPUCommand cmd) {
    VkBufferCopy.Buffer region = VkBufferCopy.calloc(1)
        .srcOffset(cmd.srcOffsetX)
        .dstOffset(cmd.dstOffsetX)
        .size(cmd.width);
    
    vkCmdCopyBuffer(currentCommandBuffer, cmd.srcBuffer, cmd.dstBuffer, region);
    
    region.free();
}

private static void executeCopyImage(GPUCommand cmd) {
    VkImageCopy.Buffer region = VkImageCopy.calloc(1);
    
    region.get(0).srcSubresource()
        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        .mipLevel(0)
        .baseArrayLayer(0)
        .layerCount(1);
    region.get(0).srcOffset().set(cmd.srcOffsetX, cmd.srcOffsetY, cmd.srcOffsetZ);
    
    region.get(0).dstSubresource()
        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        .mipLevel(0)
        .baseArrayLayer(0)
        .layerCount(1);
    region.get(0).dstOffset().set(cmd.dstOffsetX, cmd.dstOffsetY, cmd.dstOffsetZ);
    
    region.get(0).extent().set(cmd.width, cmd.height, cmd.depth);
    
    vkCmdCopyImage(currentCommandBuffer, 
        cmd.srcImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        cmd.dstImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        region);
    
    region.free();
}

private static void executeBlitImage(GPUCommand cmd) {
    VkImageBlit.Buffer region = VkImageBlit.calloc(1);
    
    region.get(0).srcSubresource()
        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        .mipLevel(0)
        .baseArrayLayer(0)
        .layerCount(1);
    region.get(0).srcOffsets(0).set(cmd.srcOffsetX, cmd.srcOffsetY, 0);
    region.get(0).srcOffsets(1).set(cmd.width, cmd.height, 1); // srcX1, srcY1
    
    region.get(0).dstSubresource()
        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        .mipLevel(0)
        .baseArrayLayer(0)
        .layerCount(1);
    region.get(0).dstOffsets(0).set(cmd.dstOffsetX, cmd.dstOffsetY, 0);
    region.get(0).dstOffsets(1).set(cmd.depth, cmd.mode, 1); // dstX1, dstY1
    
    int filter = (cmd.indexType == 0x2601) ? VK_FILTER_LINEAR : VK_FILTER_NEAREST; // GL_LINEAR
    
    vkCmdBlitImage(currentCommandBuffer,
        cmd.srcImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
        cmd.dstImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        region, filter);
    
    region.free();
}

private static void executeMemoryBarrier(GPUCommand cmd) {
    VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1)
        .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
        .srcAccessMask(cmd.srcOffsetX)
        .dstAccessMask(cmd.srcOffsetY);
    
    vkCmdPipelineBarrier(currentCommandBuffer,
        cmd.dstOffsetX, cmd.dstOffsetY, // srcStage, dstStage
        0, barrier, null, null);
    
    barrier.free();
}

private static void executePushConstants(GPUCommand cmd) {
    VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
    if (prog == null || prog.pipelineLayout == VK_NULL_HANDLE) return;
    
    ByteBuffer data = ByteBuffer.allocateDirect(cmd.pushConstantData.length);
    data.put(cmd.pushConstantData);
    data.flip();
    
    vkCmdPushConstants(currentCommandBuffer, prog.pipelineLayout,
        cmd.pushConstantStageFlags, cmd.pushConstantOffset, data);
}

// ========================================================================
// INDIRECT DRAWING (GPU-driven)
// ========================================================================

/**
 * GL: glDrawArraysIndirect(mode, indirect)
 * VK: vkCmdDrawIndirect - GPU reads draw parameters from buffer
 */
public static void drawArraysIndirect(int mode, long indirectBuffer, long offset) {
    checkInitialized();
    
    if (!recordingCommands) {
        beginFrame();
    }
    
    long pipeline = getOrCreatePipeline(mode);
    vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
    
    // Bind vertex buffers
    long vbo = state.getBoundBuffer(0x8892);
    if (vbo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(vbo);
        if (bufObj.buffer != VK_NULL_HANDLE) {
            LongBuffer pBuffers = LongBuffer.wrap(new long[]{bufObj.buffer});
            LongBuffer pOffsets = LongBuffer.wrap(new long[]{0});
            vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
        }
    }
    
    bindDescriptorSets();
    
    // Get indirect buffer
    VulkanState.BufferObject indirectBufObj = state.getBuffer(indirectBuffer);
    if (indirectBufObj == null || indirectBufObj.buffer == VK_NULL_HANDLE) {
        throw new RuntimeException("Invalid indirect buffer");
    }
    
    // Draw indirect (GPU reads VkDrawIndirectCommand from buffer)
    vkCmdDrawIndirect(currentCommandBuffer, indirectBufObj.buffer, offset, 1, 0);
}

/**
 * GL: glDrawElementsIndirect(mode, type, indirect)
 * VK: vkCmdDrawIndexedIndirect
 */
public static void drawElementsIndirect(int mode, int type, long indirectBuffer, long offset) {
    checkInitialized();
    
    if (!recordingCommands) {
        beginFrame();
    }
    
    long pipeline = getOrCreatePipeline(mode);
    vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
    
    // Bind vertex buffer
    long vbo = state.getBoundBuffer(0x8892);
    if (vbo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(vbo);
        if (bufObj.buffer != VK_NULL_HANDLE) {
            LongBuffer pBuffers = LongBuffer.wrap(new long[]{bufObj.buffer});
            LongBuffer pOffsets = LongBuffer.wrap(new long[]{0});
            vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
        }
    }
    
    // Bind index buffer
    long ibo = state.getBoundBuffer(0x8893);
    if (ibo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(ibo);
        int indexType = (type == 0x1405) ? VK_INDEX_TYPE_UINT32 : VK_INDEX_TYPE_UINT16;
        vkCmdBindIndexBuffer(currentCommandBuffer, bufObj.buffer, 0, indexType);
    }
    
    bindDescriptorSets();
    
    // Get indirect buffer
    VulkanState.BufferObject indirectBufObj = state.getBuffer(indirectBuffer);
    if (indirectBufObj == null || indirectBufObj.buffer == VK_NULL_HANDLE) {
        throw new RuntimeException("Invalid indirect buffer");
    }
    
    // Draw indexed indirect
    vkCmdDrawIndexedIndirect(currentCommandBuffer, indirectBufObj.buffer, offset, 1, 0);
}

/**
 * GL: glMultiDrawArraysIndirect(mode, indirect, drawcount, stride)
 * VK: vkCmdDrawIndirect with count > 1
 */
public static void multiDrawArraysIndirect(int mode, long indirectBuffer, long offset, int drawCount, int stride) {
    checkInitialized();
    
    if (!recordingCommands) {
        beginFrame();
    }
    
    long pipeline = getOrCreatePipeline(mode);
    vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
    
    long vbo = state.getBoundBuffer(0x8892);
    if (vbo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(vbo);
        if (bufObj.buffer != VK_NULL_HANDLE) {
            LongBuffer pBuffers = LongBuffer.wrap(new long[]{bufObj.buffer});
            LongBuffer pOffsets = LongBuffer.wrap(new long[]{0});
            vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
        }
    }
    
    bindDescriptorSets();
    
    VulkanState.BufferObject indirectBufObj = state.getBuffer(indirectBuffer);
    if (indirectBufObj == null || indirectBufObj.buffer == VK_NULL_HANDLE) {
        throw new RuntimeException("Invalid indirect buffer");
    }
    
    // Multi-draw indirect
    vkCmdDrawIndirect(currentCommandBuffer, indirectBufObj.buffer, offset, drawCount, stride);
}

/**
 * GL: glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride)
 * VK: vkCmdDrawIndexedIndirect with count > 1
 */
public static void multiDrawElementsIndirect(int mode, int type, long indirectBuffer, long offset, 
                                              int drawCount, int stride) {
    checkInitialized();
    
    if (!recordingCommands) {
        beginFrame();
    }
    
    long pipeline = getOrCreatePipeline(mode);
    vkCmdBindPipeline(currentCommandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
    
    long vbo = state.getBoundBuffer(0x8892);
    if (vbo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(vbo);
        if (bufObj.buffer != VK_NULL_HANDLE) {
            LongBuffer pBuffers = LongBuffer.wrap(new long[]{bufObj.buffer});
            LongBuffer pOffsets = LongBuffer.wrap(new long[]{0});
            vkCmdBindVertexBuffers(currentCommandBuffer, 0, pBuffers, pOffsets);
        }
    }
    
    long ibo = state.getBoundBuffer(0x8893);
    if (ibo != 0) {
        VulkanState.BufferObject bufObj = state.getBuffer(ibo);
        int indexType = (type == 0x1405) ? VK_INDEX_TYPE_UINT32 : VK_INDEX_TYPE_UINT16;
        vkCmdBindIndexBuffer(currentCommandBuffer, bufObj.buffer, 0, indexType);
    }
    
    bindDescriptorSets();
    
    VulkanState.BufferObject indirectBufObj = state.getBuffer(indirectBuffer);
    if (indirectBufObj == null || indirectBufObj.buffer == VK_NULL_HANDLE) {
        throw new RuntimeException("Invalid indirect buffer");
    }
    
    // Multi-draw indexed indirect
    vkCmdDrawIndexedIndirect(currentCommandBuffer, indirectBufObj.buffer, offset, drawCount, stride);
}

// ========================================================================
// COMPUTE SHADER DISPATCH
// ========================================================================

/**
 * GL: glDispatchCompute(num_groups_x, num_groups_y, num_groups_z)
 * VK: vkCmdDispatch
 */
public static void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
    checkInitialized();
    
    // Compute dispatch doesn't need render pass
    VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
    
    long computePipeline = state.getCurrentComputePipeline();
    if (computePipeline == VK_NULL_HANDLE) {
        ctx.endSingleTimeCommands(cmdBuffer);
        throw new RuntimeException("No compute pipeline bound");
    }
    
    vkCmdBindPipeline(cmdBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
    
    // Bind descriptor sets
    VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
    if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE) {
        long descriptorSet = descriptorSets[currentDescriptorSetIndex];
        LongBuffer pDescriptorSets = LongBuffer.wrap(new long[]{descriptorSet});
        vkCmdBindDescriptorSets(cmdBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
            prog.pipelineLayout, 0, pDescriptorSets, null);
    }
    
    vkCmdDispatch(cmdBuffer, numGroupsX, numGroupsY, numGroupsZ);
    
    ctx.endSingleTimeCommands(cmdBuffer);
}

/**
 * GL: glDispatchComputeIndirect(indirect)
 * VK: vkCmdDispatchIndirect
 */
public static void dispatchComputeIndirect(long indirectBuffer, long offset) {
    checkInitialized();
    
    VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
    
    long computePipeline = state.getCurrentComputePipeline();
    if (computePipeline == VK_NULL_HANDLE) {
        ctx.endSingleTimeCommands(cmdBuffer);
        throw new RuntimeException("No compute pipeline bound");
    }
    
    vkCmdBindPipeline(cmdBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
    
    VulkanState.ProgramObject prog = state.getProgram(state.currentProgram);
    if (prog != null && prog.pipelineLayout != VK_NULL_HANDLE) {
        long descriptorSet = descriptorSets[currentDescriptorSetIndex];
        LongBuffer pDescriptorSets = LongBuffer.wrap(new long[]{descriptorSet});
        vkCmdBindDescriptorSets(cmdBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
            prog.pipelineLayout, 0, pDescriptorSets, null);
    }
    
    VulkanState.BufferObject indirectBufObj = state.getBuffer(indirectBuffer);
    if (indirectBufObj == null || indirectBufObj.buffer == VK_NULL_HANDLE) {
        ctx.endSingleTimeCommands(cmdBuffer);
        throw new RuntimeException("Invalid indirect buffer");
    }
    
    vkCmdDispatchIndirect(cmdBuffer, indirectBufObj.buffer, offset);
    
    ctx.endSingleTimeCommands(cmdBuffer);
}

// ========================================================================
// MEMORY BARRIERS
// ========================================================================

/**
 * GL: glMemoryBarrier(barriers)
 * VK: vkCmdPipelineBarrier
 */
public static void memoryBarrier(int barriers) {
    checkInitialized();
    
    if (!recordingCommands) {
        // Need to execute immediately outside of render pass
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        insertMemoryBarrier(cmdBuffer, barriers);
        ctx.endSingleTimeCommands(cmdBuffer);
    } else {
        // End render pass, insert barrier, restart render pass
        // This is expensive but necessary for correctness
        vkCmdEndRenderPass(currentCommandBuffer);
        insertMemoryBarrier(currentCommandBuffer, barriers);
        
        // Restart render pass
        VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
            .renderPass(ctx.renderPass)
            .framebuffer(ctx.getCurrentFramebuffer());
        
        renderPassInfo.renderArea().offset().set(0, 0);
        renderPassInfo.renderArea().extent().set(ctx.swapchainExtent.width(), ctx.swapchainExtent.height());
        
        // No clear values since we're continuing
        vkCmdBeginRenderPass(currentCommandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        
        renderPassInfo.free();
    }
}

private static void insertMemoryBarrier(VkCommandBuffer cmdBuffer, int glBarriers) {
    int srcStage = 0;
    int dstStage = 0;
    int srcAccess = 0;
    int dstAccess = 0;
    
    // Translate GL barrier bits to Vulkan
    if ((glBarriers & 0x00000001) != 0) { // GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT
        srcStage |= VK_PIPELINE_STAGE_VERTEX_INPUT_BIT;
        dstStage |= VK_PIPELINE_STAGE_VERTEX_INPUT_BIT;
        srcAccess |= VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT;
        dstAccess |= VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT;
    }
    if ((glBarriers & 0x00000002) != 0) { // GL_ELEMENT_ARRAY_BARRIER_BIT
        srcStage |= VK_PIPELINE_STAGE_VERTEX_INPUT_BIT;
        dstStage |= VK_PIPELINE_STAGE_VERTEX_INPUT_BIT;
        srcAccess |= VK_ACCESS_INDEX_READ_BIT;
        dstAccess |= VK_ACCESS_INDEX_READ_BIT;
    }
    if ((glBarriers & 0x00000004) != 0) { // GL_UNIFORM_BARRIER_BIT
        srcStage |= VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        dstStage |= VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        srcAccess |= VK_ACCESS_UNIFORM_READ_BIT;
        dstAccess |= VK_ACCESS_UNIFORM_READ_BIT;
    }
    if ((glBarriers & 0x00000008) != 0) { // GL_TEXTURE_FETCH_BARRIER_BIT
        srcStage |= VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        dstStage |= VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        srcAccess |= VK_ACCESS_SHADER_READ_BIT;
        dstAccess |= VK_ACCESS_SHADER_READ_BIT;
    }
    if ((glBarriers & 0x00000020) != 0) { // GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
        srcStage |= VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
        dstStage |= VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
        srcAccess |= VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
        dstAccess |= VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
    }
    if ((glBarriers & 0x00000080) != 0) { // GL_COMMAND_BARRIER_BIT
        srcStage |= VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT;
        dstStage |= VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT;
        srcAccess |= VK_ACCESS_INDIRECT_COMMAND_READ_BIT;
        dstAccess |= VK_ACCESS_INDIRECT_COMMAND_READ_BIT;
    }
    if ((glBarriers & 0x00000400) != 0) { // GL_BUFFER_UPDATE_BARRIER_BIT
        srcStage |= VK_PIPELINE_STAGE_TRANSFER_BIT;
        dstStage |= VK_PIPELINE_STAGE_TRANSFER_BIT;
        srcAccess |= VK_ACCESS_TRANSFER_WRITE_BIT;
        dstAccess |= VK_ACCESS_TRANSFER_READ_BIT;
    }
    if ((glBarriers & 0x00000800) != 0) { // GL_FRAMEBUFFER_BARRIER_BIT
        srcStage |= VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dstStage |= VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        srcAccess |= VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        dstAccess |= VK_ACCESS_COLOR_ATTACHMENT_READ_BIT;
    }
    if ((glBarriers & 0x00002000) != 0) { // GL_SHADER_STORAGE_BARRIER_BIT
        srcStage |= VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
        dstStage |= VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
        srcAccess |= VK_ACCESS_SHADER_WRITE_BIT;
        dstAccess |= VK_ACCESS_SHADER_READ_BIT;
    }
    if ((glBarriers & 0xFFFFFFFF) != 0 && glBarriers == 0xFFFFFFFF) { // GL_ALL_BARRIER_BITS
        srcStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        dstStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        srcAccess = VK_ACCESS_MEMORY_WRITE_BIT;
        dstAccess = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
    }
    
    if (srcStage == 0) srcStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
    if (dstStage == 0) dstStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
    
    VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1)
        .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
        .srcAccessMask(srcAccess)
        .dstAccessMask(dstAccess);
    
    vkCmdPipelineBarrier(cmdBuffer, srcStage, dstStage, 0, barrier, null, null);
    
    barrier.free();
}

// ========================================================================
// ASYNC / FENCE OPERATIONS
// ========================================================================

/**
 * GL: glFenceSync(condition, flags)
 * VK: Create timeline semaphore signal
 */
public static long fenceSync(int condition, int flags) {
    checkInitialized();
    
    if (timelineSemaphore == VK_NULL_HANDLE) {
        // Fallback to fence-based sync
        VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
        
        LongBuffer pFence = LongBuffer.allocate(1);
        vkCreateFence(ctx.device, fenceInfo, null, pFence);
        fenceInfo.free();
        
        long fence = pFence.get(0);
        
        // Submit an empty command buffer to signal the fence
        VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
        ctx.endSingleTimeCommandsWithFence(cmdBuffer, fence);
        
        return fence;
    }
    
    // Use timeline semaphore
    long signalValue = timelineValue.incrementAndGet();
    
    // Signal the timeline semaphore
    VkSemaphoreSignalInfo signalInfo = VkSemaphoreSignalInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_SEMAPHORE_SIGNAL_INFO)
        .semaphore(timelineSemaphore)
        .value(signalValue);
    
    vkSignalSemaphore(ctx.device, signalInfo);
    signalInfo.free();
    
    // Return encoded sync object (high bit = timeline, low 63 bits = value)
    return 0x8000000000000000L | signalValue;
}

/**
 * GL: glClientWaitSync(sync, flags, timeout)
 * VK: Wait on fence or timeline semaphore
 */
public static int clientWaitSync(long sync, int flags, long timeout) {
    checkInitialized();
    
    if ((sync & 0x8000000000000000L) != 0) {
        // Timeline semaphore
        long waitValue = sync & 0x7FFFFFFFFFFFFFFFL;
        
        VkSemaphoreWaitInfo waitInfo = VkSemaphoreWaitInfo.calloc()
            .sType(VK_STRUCTURE_TYPE_SEMAPHORE_WAIT_INFO)
            .pSemaphores(LongBuffer.wrap(new long[]{timelineSemaphore}))
            .pValues(LongBuffer.wrap(new long[]{waitValue}));
        
        int result = vkWaitSemaphores(ctx.device, waitInfo, timeout);
        waitInfo.free();
        
        if (result == VK_SUCCESS) {
            return 0x911A; // GL_ALREADY_SIGNALED
        } else if (result == VK_TIMEOUT) {
            return 0x911B; // GL_TIMEOUT_EXPIRED
        } else {
            return 0x911D; // GL_WAIT_FAILED
        }
    } else {
        // Fence
        int result = vkWaitForFences(ctx.device, new long[]{sync}, true, timeout);
        
        if (result == VK_SUCCESS) {
            return 0x911A; // GL_ALREADY_SIGNALED
        } else if (result == VK_TIMEOUT) {
            return 0x911B; // GL_TIMEOUT_EXPIRED
        } else {
            return 0x911D; // GL_WAIT_FAILED
        }
    }
}

/**
 * GL: glWaitSync(sync, flags, timeout)
 * VK: GPU-side wait (insert into command stream)
 */
public static void waitSync(long sync, int flags, long timeout) {
    checkInitialized();
    
    // This is a GPU-side wait, not CPU-side
    // In Vulkan, this is handled via semaphore dependencies in queue submission
    // For now, we'll insert a pipeline barrier as approximation
    
    if (!recordingCommands) {
        beginFrame();
    }
    
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

/**
 * GL: glDeleteSync(sync)
 * VK: Destroy fence or decrement timeline reference
 */
public static void deleteSync(long sync) {
    checkInitialized();
    
    if ((sync & 0x8000000000000000L) != 0) {
        // Timeline semaphore - nothing to delete per-sync
        // The semaphore is shared and destroyed on shutdown
    } else {
        // Fence
        vkDestroyFence(ctx.device, sync, null);
    }
}

/**
 * GL: glFinish()
 * VK: Wait for all GPU operations to complete
 */
public static void finish() {
    checkInitialized();
    
    // Flush any pending commands
    if (recordingCommands) {
        endFrame();
    }
    flushCommands();
    
    // Wait for device idle
    vkDeviceWaitIdle(ctx.device);
}

/**
 * GL: glFlush()
 * VK: Submit pending commands (don't wait)
 */
public static void flush() {
    checkInitialized();
    flushCommands();
}

// ========================================================================
// CLEANUP
// ========================================================================

/**
 * Shutdown GPU execution system
 */
private static void shutdownGPUExecution() {
    // Wait for all work to complete
    vkDeviceWaitIdle(ctx.device);
    
    // Destroy command buffer fences
    if (commandBufferFences != null) {
        for (long fence : commandBufferFences) {
            if (fence != VK_NULL_HANDLE) {
                vkDestroyFence(ctx.device, fence, null);
            }
        }
    }
    
    // Destroy timeline semaphore
    if (timelineSemaphore != VK_NULL_HANDLE) {
        vkDestroySemaphore(ctx.device, timelineSemaphore, null);
        timelineSemaphore = VK_NULL_HANDLE;
    }
    
    // Clear command queue
    commandQueue.clear();
    
    FPSFlux.LOGGER.info("[VulkanCallMapperX] GPU execution system shutdown");
}

// ========================================================================
// TEXTURE HELPER METHODS
// ========================================================================

/**
 * Create Vulkan image for texture
 */
private static void createTextureImage(VulkanState.TextureObject texObj, 
                                        int width, int height, int vkFormat) {
    VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
        .imageType(VK_IMAGE_TYPE_2D)
        .format(vkFormat)
        .mipLevels(1)
        .arrayLayers(1)
        .samples(VK_SAMPLE_COUNT_1_BIT)
        .tiling(VK_IMAGE_TILING_OPTIMAL)
        .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
    
    imageInfo.extent().width(width).height(height).depth(1);
    
    LongBuffer pImage = LongBuffer.allocate(1);
    int result = vkCreateImage(ctx.device, imageInfo, null, pImage);
    if (result != VK_SUCCESS) {
        imageInfo.free();
        throw new RuntimeException("Failed to create image: " + result);
    }
    
    long image = pImage.get(0);
    
    // Allocate memory
    VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
    vkGetImageMemoryRequirements(ctx.device, image, memReqs);
    
    VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
        .allocationSize(memReqs.size())
        .memoryTypeIndex(ctx.findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
    
    LongBuffer pMemory = LongBuffer.allocate(1);
    result = vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
    if (result != VK_SUCCESS) {
        vkDestroyImage(ctx.device, image, null);
        imageInfo.free();
        memReqs.free();
        allocInfo.free();
        throw new RuntimeException("Failed to allocate image memory: " + result);
    }
    
    long memory = pMemory.get(0);
    vkBindImageMemory(ctx.device, image, memory, 0);
    
    // Create image view
    VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
        .image(image)
        .viewType(VK_IMAGE_VIEW_TYPE_2D)
        .format(vkFormat);
    
    viewInfo.subresourceRange()
        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        .baseMipLevel(0)
        .levelCount(1)
        .baseArrayLayer(0)
        .layerCount(1);
    
    LongBuffer pImageView = LongBuffer.allocate(1);
    result = vkCreateImageView(ctx.device, viewInfo, null, pImageView);
    if (result != VK_SUCCESS) {
        vkDestroyImage(ctx.device, image, null);
        vkFreeMemory(ctx.device, memory, null);
        imageInfo.free();
        memReqs.free();
        allocInfo.free();
        viewInfo.free();
        throw new RuntimeException("Failed to create image view: " + result);
    }
    
    texObj.image = image;
    texObj.memory = memory;
    texObj.imageView = pImageView.get(0);
    
    imageInfo.free();
    memReqs.free();
    allocInfo.free();
    viewInfo.free();
}

/**
 * Upload full texture data via staging buffer
 */
private static void uploadTextureData(long image, int width, int height,
                                       int format, int type, ByteBuffer data) {
    long bufferSize = data.remaining();
    
    // Create staging buffer
    VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
        .size(bufferSize)
        .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
        .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
    
    LongBuffer pBuffer = LongBuffer.allocate(1);
    vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
    long stagingBuffer = pBuffer.get(0);
    
    VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
    vkGetBufferMemoryRequirements(ctx.device, stagingBuffer, memReqs);
    
    VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
        .allocationSize(memReqs.size())
        .memoryTypeIndex(ctx.findMemoryType(
            memReqs.memoryTypeBits(),
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        ));
    
    LongBuffer pMemory = LongBuffer.allocate(1);
    vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
    long stagingMemory = pMemory.get(0);
    
    vkBindBufferMemory(ctx.device, stagingBuffer, stagingMemory, 0);
    
    // Map and copy data
    ByteBuffer mapped = ctx.mapMemory(stagingMemory, 0, bufferSize);
    mapped.put(data);
    data.rewind();
    vkUnmapMemory(ctx.device, stagingMemory);
    
    // Transition and copy
    VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
    
    transitionImageLayout(cmdBuffer, image,
        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    
    VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1)
        .bufferOffset(0)
        .bufferRowLength(0)
        .bufferImageHeight(0);
    
    region.imageSubresource()
        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        .mipLevel(0)
        .baseArrayLayer(0)
        .layerCount(1);
    
    region.imageOffset().set(0, 0, 0);
    region.imageExtent().set(width, height, 1);
    
    vkCmdCopyBufferToImage(cmdBuffer, stagingBuffer, image,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
    
    transitionImageLayout(cmdBuffer, image,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    
    ctx.endSingleTimeCommands(cmdBuffer);
    
    // Cleanup
    vkDestroyBuffer(ctx.device, stagingBuffer, null);
    vkFreeMemory(ctx.device, stagingMemory, null);
    
    bufferInfo.free();
    memReqs.free();
    allocInfo.free();
    region.free();
}

/**
 * Upload partial texture data (for texSubImage2D)
 */
private static void uploadTextureDataRegion(long image, int xoffset, int yoffset,
                                             int width, int height,
                                             int format, int type, ByteBuffer data) {
    long bufferSize = data.remaining();
    
    // Create staging buffer
    VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
        .size(bufferSize)
        .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
        .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
    
    LongBuffer pBuffer = LongBuffer.allocate(1);
    vkCreateBuffer(ctx.device, bufferInfo, null, pBuffer);
    long stagingBuffer = pBuffer.get(0);
    
    VkMemoryRequirements memReqs = VkMemoryRequirements.calloc();
    vkGetBufferMemoryRequirements(ctx.device, stagingBuffer, memReqs);
    
    VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
        .allocationSize(memReqs.size())
        .memoryTypeIndex(ctx.findMemoryType(
            memReqs.memoryTypeBits(),
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        ));
    
    LongBuffer pMemory = LongBuffer.allocate(1);
    vkAllocateMemory(ctx.device, allocInfo, null, pMemory);
    long stagingMemory = pMemory.get(0);
    
    vkBindBufferMemory(ctx.device, stagingBuffer, stagingMemory, 0);
    
    // Map and copy
    ByteBuffer mapped = ctx.mapMemory(stagingMemory, 0, bufferSize);
    mapped.put(data);
    data.rewind();
    vkUnmapMemory(ctx.device, stagingMemory);
    
    // Transition and copy
    VkCommandBuffer cmdBuffer = ctx.beginSingleTimeCommands();
    
    // Transition to transfer dst (preserve existing data)
    transitionImageLayout(cmdBuffer, image,
        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    
    VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1)
        .bufferOffset(0)
        .bufferRowLength(0)
        .bufferImageHeight(0);
    
    region.imageSubresource()
        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        .mipLevel(0)
        .baseArrayLayer(0)
        .layerCount(1);
    
    region.imageOffset().set(xoffset, yoffset, 0);
    region.imageExtent().set(width, height, 1);
    
    vkCmdCopyBufferToImage(cmdBuffer, stagingBuffer, image,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
    
    transitionImageLayout(cmdBuffer, image,
        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    
    ctx.endSingleTimeCommands(cmdBuffer);
    
    // Cleanup
    vkDestroyBuffer(ctx.device, stagingBuffer, null);
    vkFreeMemory(ctx.device, stagingMemory, null);
    
    bufferInfo.free();
    memReqs.free();
    allocInfo.free();
    region.free();
}

/**
 * Transition image layout with pipeline barrier
 */
private static void transitionImageLayout(VkCommandBuffer cmdBuffer, long image,
                                           int oldLayout, int newLayout) {
    VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1)
        .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
        .oldLayout(oldLayout)
        .newLayout(newLayout)
        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
        .image(image);
    
    barrier.subresourceRange()
        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        .baseMipLevel(0)
        .levelCount(1)
        .baseArrayLayer(0)
        .layerCount(1);
    
    int sourceStage, destinationStage;
    
    if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && 
        newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
        barrier.srcAccessMask(0);
        barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
        sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        
    } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && 
               newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
        barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
        sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        
    } else if (oldLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL && 
               newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
        barrier.srcAccessMask(VK_ACCESS_SHADER_READ_BIT);
        barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
        sourceStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        
    } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL && 
               newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        barrier.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
        barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
        sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        
    } else if (oldLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL && 
               newLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
        barrier.srcAccessMask(VK_ACCESS_SHADER_READ_BIT);
        barrier.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
        sourceStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        
    } else if (oldLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR && 
               newLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
        barrier.srcAccessMask(VK_ACCESS_MEMORY_READ_BIT);
        barrier.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
        sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        
    } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL && 
               newLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) {
        barrier.srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
        barrier.dstAccessMask(VK_ACCESS_MEMORY_READ_BIT);
        sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        destinationStage = VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
        
    } else {
        barrier.free();
        throw new RuntimeException("Unsupported layout transition: " + oldLayout + " -> " + newLayout);
    }
    
    vkCmdPipelineBarrier(cmdBuffer, sourceStage, destinationStage, 0, null, null, barrier);
    barrier.free();
}

/**
 * Create sampler based on texture parameters
 */
private static long createSamplerForTexture(VulkanState.TextureObject texObj) {
    int magFilter = translateFilter(texObj.getParameteri(0x2800)); // GL_TEXTURE_MAG_FILTER
    int minFilter = translateFilter(texObj.getParameteri(0x2801)); // GL_TEXTURE_MIN_FILTER
    int wrapS = translateWrap(texObj.getParameteri(0x2802));       // GL_TEXTURE_WRAP_S
    int wrapT = translateWrap(texObj.getParameteri(0x2803));       // GL_TEXTURE_WRAP_T
    int wrapR = translateWrap(texObj.getParameteri(0x8072));       // GL_TEXTURE_WRAP_R
    
    int mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;
    int minFilterGL = texObj.getParameteri(0x2801);
    if (minFilterGL == 0x2600 || minFilterGL == 0x2700) { // GL_NEAREST or GL_NEAREST_MIPMAP_NEAREST
        mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST;
    }
    
    float maxAnisotropy = texObj.getParameterf(0x84FE); // GL_TEXTURE_MAX_ANISOTROPY
    boolean anisotropyEnabled = maxAnisotropy > 1.0f && ctx.supportsAnisotropy();
    
    VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc()
        .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
        .magFilter(magFilter)
        .minFilter(minFilter)
        .addressModeU(wrapS)
        .addressModeV(wrapT)
        .addressModeW(wrapR)
        .anisotropyEnable(anisotropyEnabled)
        .maxAnisotropy(anisotropyEnabled ? maxAnisotropy : 1.0f)
        .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
        .unnormalizedCoordinates(false)
        .compareEnable(false)
        .compareOp(VK_COMPARE_OP_ALWAYS)
        .mipmapMode(mipmapMode)
        .mipLodBias(0.0f)
        .minLod(0.0f)
        .maxLod(texObj.mipLevels > 1 ? (float) texObj.mipLevels : 0.0f);
    
    LongBuffer pSampler = LongBuffer.allocate(1);
    int result = vkCreateSampler(ctx.device, samplerInfo, null, pSampler);
    
    samplerInfo.free();
    
    if (result != VK_SUCCESS) {
        throw new RuntimeException("Failed to create sampler: " + result);
    }
    
    return pSampler.get(0);
}

private static int translateFilter(int glFilter) {
    return switch (glFilter) {
        case 0x2600 -> VK_FILTER_NEAREST;               // GL_NEAREST
        case 0x2601 -> VK_FILTER_LINEAR;                // GL_LINEAR
        case 0x2700 -> VK_FILTER_NEAREST;               // GL_NEAREST_MIPMAP_NEAREST
        case 0x2701 -> VK_FILTER_LINEAR;                // GL_LINEAR_MIPMAP_NEAREST
        case 0x2702 -> VK_FILTER_NEAREST;               // GL_NEAREST_MIPMAP_LINEAR
        case 0x2703 -> VK_FILTER_LINEAR;                // GL_LINEAR_MIPMAP_LINEAR
        default -> VK_FILTER_LINEAR;
    };
}

private static int translateWrap(int glWrap) {
    return switch (glWrap) {
        case 0x2901 -> VK_SAMPLER_ADDRESS_MODE_REPEAT;           // GL_REPEAT
        case 0x812F -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;    // GL_CLAMP_TO_EDGE
        case 0x812D -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER;  // GL_CLAMP_TO_BORDER
        case 0x8370 -> VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;  // GL_MIRRORED_REPEAT
        default -> VK_SAMPLER_ADDRESS_MODE_REPEAT;
    };
}

private static int translateFormatToVulkan(int glFormat) {
    return switch (glFormat) {
        // Basic formats
        case 0x1908 -> VK_FORMAT_R8G8B8A8_UNORM;           // GL_RGBA
        case 0x1907 -> VK_FORMAT_R8G8B8_UNORM;             // GL_RGB
        case 0x1906 -> VK_FORMAT_R8_UNORM;                 // GL_ALPHA (approximation)
        case 0x1909 -> VK_FORMAT_R8_UNORM;                 // GL_LUMINANCE
        case 0x190A -> VK_FORMAT_R8G8_UNORM;               // GL_LUMINANCE_ALPHA
        
        // Sized formats
        case 0x8051 -> VK_FORMAT_R8G8B8_UNORM;             // GL_RGB8
        case 0x8058 -> VK_FORMAT_R8G8B8A8_UNORM;           // GL_RGBA8
        case 0x8C41 -> VK_FORMAT_R8G8B8_SRGB;              // GL_SRGB8
        case 0x8C43 -> VK_FORMAT_R8G8B8A8_SRGB;            // GL_SRGB8_ALPHA8
        
        // Float formats
        case 0x881A -> VK_FORMAT_R16G16B16A16_SFLOAT;      // GL_RGBA16F
        case 0x8815 -> VK_FORMAT_R32G32B32_SFLOAT;         // GL_RGB32F
        case 0x8814 -> VK_FORMAT_R32G32B32A32_SFLOAT;      // GL_RGBA32F
        
        // Depth formats
        case 0x1902 -> VK_FORMAT_D32_SFLOAT;               // GL_DEPTH_COMPONENT
        case 0x81A5 -> VK_FORMAT_D16_UNORM;                // GL_DEPTH_COMPONENT16
        case 0x81A6 -> VK_FORMAT_D24_UNORM_S8_UINT;        // GL_DEPTH_COMPONENT24
        case 0x81A7 -> VK_FORMAT_D32_SFLOAT;               // GL_DEPTH_COMPONENT32
        case 0x88F0 -> VK_FORMAT_D24_UNORM_S8_UINT;        // GL_DEPTH24_STENCIL8
        
        // Compressed formats (need extension support)
        case 0x83F0 -> VK_FORMAT_BC1_RGB_UNORM_BLOCK;      // GL_COMPRESSED_RGB_S3TC_DXT1
        case 0x83F1 -> VK_FORMAT_BC1_RGBA_UNORM_BLOCK;     // GL_COMPRESSED_RGBA_S3TC_DXT1
        case 0x83F2 -> VK_FORMAT_BC2_UNORM_BLOCK;          // GL_COMPRESSED_RGBA_S3TC_DXT3
        case 0x83F3 -> VK_FORMAT_BC3_UNORM_BLOCK;          // GL_COMPRESSED_RGBA_S3TC_DXT5
        
        default -> VK_FORMAT_R8G8B8A8_UNORM;
    }
}
