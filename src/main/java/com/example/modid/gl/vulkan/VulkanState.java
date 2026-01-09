package com.example.modid.gl.vulkan;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * VulkanState - Complete OpenGL state tracking for Vulkan translation
 * 
 * Tracks all OpenGL state needed for pipeline creation and rendering:
 * - Texture bindings and parameters
 * - Buffer bindings (including UBO, SSBO, etc.)
 * - Shader and program state
 * - Vertex attribute configuration
 * - Blend, depth, stencil, cull state
 * - Viewport, scissor
 * - Uniforms and push constants
 * - VAO state
 */
public class VulkanState {
    
    // ========================================================================
    // GL CONSTANTS (for reference)
    // ========================================================================
    
    // Buffer targets
    public static final int GL_ARRAY_BUFFER = 0x8892;
    public static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    public static final int GL_UNIFORM_BUFFER = 0x8A11;
    public static final int GL_SHADER_STORAGE_BUFFER = 0x90D2;
    public static final int GL_DRAW_INDIRECT_BUFFER = 0x8F3F;
    public static final int GL_DISPATCH_INDIRECT_BUFFER = 0x90EE;
    public static final int GL_COPY_READ_BUFFER = 0x8F36;
    public static final int GL_COPY_WRITE_BUFFER = 0x8F37;
    public static final int GL_TRANSFORM_FEEDBACK_BUFFER = 0x8C8E;
    public static final int GL_TEXTURE_BUFFER = 0x8C2A;
    public static final int GL_PIXEL_PACK_BUFFER = 0x88EB;
    public static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC;
    
    // Shader types
    public static final int GL_VERTEX_SHADER = 0x8B31;
    public static final int GL_FRAGMENT_SHADER = 0x8B30;
    public static final int GL_GEOMETRY_SHADER = 0x8DD9;
    public static final int GL_TESS_CONTROL_SHADER = 0x8E88;
    public static final int GL_TESS_EVALUATION_SHADER = 0x8E87;
    public static final int GL_COMPUTE_SHADER = 0x91B9;
    
    // Capabilities
    public static final int GL_BLEND = 0x0BE2;
    public static final int GL_DEPTH_TEST = 0x0B71;
    public static final int GL_STENCIL_TEST = 0x0B90;
    public static final int GL_CULL_FACE = 0x0B44;
    public static final int GL_SCISSOR_TEST = 0x0C11;
    public static final int GL_POLYGON_OFFSET_FILL = 0x8037;
    public static final int GL_POLYGON_OFFSET_LINE = 0x2A02;
    public static final int GL_POLYGON_OFFSET_POINT = 0x2A01;
    public static final int GL_MULTISAMPLE = 0x809D;
    public static final int GL_SAMPLE_ALPHA_TO_COVERAGE = 0x809E;
    public static final int GL_SAMPLE_ALPHA_TO_ONE = 0x809F;
    public static final int GL_SAMPLE_COVERAGE = 0x80A0;
    public static final int GL_SAMPLE_SHADING = 0x8C36;
    public static final int GL_PROGRAM_POINT_SIZE = 0x8642;
    public static final int GL_DEPTH_CLAMP = 0x864F;
    public static final int GL_PRIMITIVE_RESTART = 0x8F9D;
    public static final int GL_PRIMITIVE_RESTART_FIXED_INDEX = 0x8D69;
    public static final int GL_RASTERIZER_DISCARD = 0x8C89;
    public static final int GL_COLOR_LOGIC_OP = 0x0BF2;
    public static final int GL_LINE_SMOOTH = 0x0B20;
    public static final int GL_POLYGON_SMOOTH = 0x0B41;
    public static final int GL_FRAMEBUFFER_SRGB = 0x8DB9;
    public static final int GL_DITHER = 0x0BD0;
    
    // ========================================================================
    // TEXTURE STATE
    // ========================================================================
    
    public int activeTextureUnit = 0;
    private final Map<Integer, Long> boundTextures = new HashMap<>(); // unit -> textureId
    private final Map<Long, TextureObject> textures = new HashMap<>();
    private long nextTextureId = 1;
    
    public static class TextureObject {
        public long image;
        public long memory;
        public long imageView;
        public long sampler;
        public int width;
        public int height;
        public int depth = 1;
        public int mipLevels = 1;
        public int arrayLayers = 1;
        public int format;
        public int target; // GL_TEXTURE_2D, etc.
        
        // Sampler parameters
        private final Map<Integer, Integer> intParams = new HashMap<>();
        private final Map<Integer, Float> floatParams = new HashMap<>();
        
        public TextureObject() {
            // Default parameters
            intParams.put(0x2800, 0x2601); // GL_TEXTURE_MAG_FILTER = GL_LINEAR
            intParams.put(0x2801, 0x2703); // GL_TEXTURE_MIN_FILTER = GL_LINEAR_MIPMAP_LINEAR
            intParams.put(0x2802, 0x2901); // GL_TEXTURE_WRAP_S = GL_REPEAT
            intParams.put(0x2803, 0x2901); // GL_TEXTURE_WRAP_T = GL_REPEAT
            intParams.put(0x8072, 0x2901); // GL_TEXTURE_WRAP_R = GL_REPEAT
            intParams.put(0x884C, 0x0); // GL_TEXTURE_COMPARE_MODE = GL_NONE
            intParams.put(0x884D, 0x0203); // GL_TEXTURE_COMPARE_FUNC = GL_LEQUAL
            floatParams.put(0x84FE, 1.0f); // GL_TEXTURE_MAX_ANISOTROPY = 1.0
            floatParams.put(0x813A, 0.0f); // GL_TEXTURE_MIN_LOD = 0.0
            floatParams.put(0x813B, 1000.0f); // GL_TEXTURE_MAX_LOD = 1000.0
            floatParams.put(0x8501, 0.0f); // GL_TEXTURE_LOD_BIAS = 0.0
        }
        
        public void setParameter(int pname, int value) {
            intParams.put(pname, value);
        }
        
        public int getParameteri(int pname) {
            return intParams.getOrDefault(pname, 0);
        }
        
        public void setParameterf(int pname, float value) {
            floatParams.put(pname, value);
        }
        
        public float getParameterf(int pname) {
            return floatParams.getOrDefault(pname, 0.0f);
        }
    }
    
    // Texture methods
    public long registerTexture(long image, long memory, long imageView, long sampler) {
        TextureObject tex = new TextureObject();
        tex.image = image;
        tex.memory = memory;
        tex.imageView = imageView;
        tex.sampler = sampler;
        long id = nextTextureId++;
        textures.put(id, tex);
        return id;
    }
    
    public TextureObject getTexture(long id) {
        return textures.get(id);
    }
    
    public void unregisterTexture(long id) {
        textures.remove(id);
        // Remove from all bindings
        boundTextures.values().removeIf(v -> v == id);
    }
    
    public void bindTexture(int unit, long texture) {
        boundTextures.put(unit, texture);
    }
    
    public void unbindTexture(int unit) {
        boundTextures.remove(unit);
    }
    
    public long getBoundTexture(int unit) {
        return boundTextures.getOrDefault(unit, 0L);
    }
    
    public Map<Integer, Long> getAllBoundTextures() {
        return new HashMap<>(boundTextures);
    }
    
    // ========================================================================
    // BUFFER STATE
    // ========================================================================
    
    private final Map<Integer, Long> boundBuffers = new HashMap<>(); // target -> bufferId
    private final Map<Long, BufferObject> buffers = new HashMap<>();
    private long nextBufferId = 1;
    
    // Indexed buffer bindings (for UBO, SSBO)
    private final Map<Integer, Map<Integer, IndexedBufferBinding>> indexedBindings = new HashMap<>();
    
    // Vertex buffer bindings (separate from VBO bindings)
    private final Map<Integer, Long> boundVertexBuffers = new HashMap<>(); // binding -> buffer
    private final Map<Integer, Long> vertexBufferOffsets = new HashMap<>(); // binding -> offset
    private final Map<Integer, Integer> vertexBindingStrides = new HashMap<>(); // binding -> stride
    private final Map<Integer, Integer> vertexBindingDivisors = new HashMap<>(); // binding -> divisor
    
    public static class BufferObject {
        public long buffer;
        public long memory;
        public long size;
        public int usage;
        public boolean mapped = false;
        public ByteBuffer mappedBuffer = null;
    }
    
    public static class IndexedBufferBinding {
        public long buffer;
        public long offset;
        public long size;
    }
    
    // Buffer methods
    public long registerBuffer(long buffer, long memory, long size) {
        BufferObject buf = new BufferObject();
        buf.buffer = buffer;
        buf.memory = memory;
        buf.size = size;
        long id = nextBufferId++;
        buffers.put(id, buf);
        return id;
    }
    
    public BufferObject getBuffer(long id) {
        return buffers.get(id);
    }
    
    public void unregisterBuffer(long id) {
        buffers.remove(id);
        boundBuffers.values().removeIf(v -> v == id);
        boundVertexBuffers.values().removeIf(v -> v == id);
    }
    
    public void bindBuffer(int target, long buffer) {
        if (buffer == 0) {
            boundBuffers.remove(target);
        } else {
            boundBuffers.put(target, buffer);
        }
    }
    
    public long getBoundBuffer(int target) {
        return boundBuffers.getOrDefault(target, 0L);
    }
    
    public void bindBufferBase(int target, int index, long buffer) {
        Map<Integer, IndexedBufferBinding> targetBindings = 
            indexedBindings.computeIfAbsent(target, k -> new HashMap<>());
        
        if (buffer == 0) {
            targetBindings.remove(index);
        } else {
            IndexedBufferBinding binding = new IndexedBufferBinding();
            binding.buffer = buffer;
            binding.offset = 0;
            BufferObject bufObj = buffers.get(buffer);
            binding.size = bufObj != null ? bufObj.size : 0;
            targetBindings.put(index, binding);
        }
    }
    
    public void bindBufferRange(int target, int index, long buffer, long offset, long size) {
        Map<Integer, IndexedBufferBinding> targetBindings = 
            indexedBindings.computeIfAbsent(target, k -> new HashMap<>());
        
        if (buffer == 0) {
            targetBindings.remove(index);
        } else {
            IndexedBufferBinding binding = new IndexedBufferBinding();
            binding.buffer = buffer;
            binding.offset = offset;
            binding.size = size;
            targetBindings.put(index, binding);
        }
    }
    
    public IndexedBufferBinding getIndexedBufferBinding(int target, int index) {
        Map<Integer, IndexedBufferBinding> targetBindings = indexedBindings.get(target);
        return targetBindings != null ? targetBindings.get(index) : null;
    }
    
    // Vertex buffer binding methods
    public void bindVertexBuffer(int binding, long buffer, long offset, int stride) {
        boundVertexBuffers.put(binding, buffer);
        vertexBufferOffsets.put(binding, offset);
        vertexBindingStrides.put(binding, stride);
    }
    
    public long getBoundVertexBuffer(int binding) {
        return boundVertexBuffers.getOrDefault(binding, 0L);
    }
    
    public long getVertexBufferOffset(int binding) {
        return vertexBufferOffsets.getOrDefault(binding, 0L);
    }
    
    public int getVertexBindingStride(int binding) {
        return vertexBindingStrides.getOrDefault(binding, 0);
    }
    
    public int getVertexBindingDivisor(int binding) {
        return vertexBindingDivisors.getOrDefault(binding, 0);
    }
    
    public void setVertexBindingDivisor(int binding, int divisor) {
        vertexBindingDivisors.put(binding, divisor);
    }
    
    public int getVertexBindingCount() {
        int maxBinding = 0;
        for (int binding : boundVertexBuffers.keySet()) {
            maxBinding = Math.max(maxBinding, binding + 1);
        }
        for (VertexAttrib attr : vertexAttribs.values()) {
            if (attr.enabled) {
                maxBinding = Math.max(maxBinding, attr.binding + 1);
            }
        }
        return maxBinding;
    }
    
    // ========================================================================
    // SHADER STATE
    // ========================================================================
    
    private final Map<Long, ShaderObject> shaders = new HashMap<>();
    private long nextShaderId = 1;
    
    public static class ShaderObject {
        public int type;
        public String source;
        public ByteBuffer spirv;
        public long module;
        public boolean compiled = false;
        public boolean compileStatus = false;
        public String infoLog = "";
        public boolean deleteRequested = false;
        public int refCount = 0;
    }
    
    public long createShader(int type) {
        ShaderObject shader = new ShaderObject();
        shader.type = type;
        long id = nextShaderId++;
        shaders.put(id, shader);
        return id;
    }
    
    public ShaderObject getShader(long id) {
        return shaders.get(id);
    }
    
    public void setShaderSource(long id, String source) {
        ShaderObject shader = shaders.get(id);
        if (shader != null) {
            shader.source = source;
        }
    }
    
    public void unregisterShader(long id) {
        shaders.remove(id);
    }
    
    // ========================================================================
    // PROGRAM STATE
    // ========================================================================
    
    private final Map<Long, ProgramObject> programs = new HashMap<>();
    private long nextProgramId = 1;
    public long currentProgram = 0;
    
    public static class ProgramObject {
        public List<Long> attachedShaders = new ArrayList<>();
        public long pipeline;
        public long pipelineLayout;
        public long computePipeline;
        public boolean linked = false;
        public boolean linkStatus = false;
        public boolean validateStatus = false;
        public String infoLog = "";
        public int activeUniforms = 0;
        public int activeUniformMaxLength = 0;
        public int activeAttributes = 0;
        public int activeAttributeMaxLength = 0;
        public boolean deleteRequested = false;
        
        // Uniform locations
        public final Map<String, Integer> uniformLocations = new HashMap<>();
        public int nextUniformLocation = 0;
    }
    
    public long createProgram() {
        ProgramObject program = new ProgramObject();
        long id = nextProgramId++;
        programs.put(id, program);
        return id;
    }
    
    public ProgramObject getProgram(long id) {
        return programs.get(id);
    }
    
    public void attachShaderToProgram(long programId, long shaderId) {
        ProgramObject program = programs.get(programId);
        ShaderObject shader = shaders.get(shaderId);
        if (program != null && shader != null) {
            if (!program.attachedShaders.contains(shaderId)) {
                program.attachedShaders.add(shaderId);
                shader.refCount++;
            }
        }
    }
    
    public void detachShaderFromProgram(long programId, long shaderId) {
        ProgramObject program = programs.get(programId);
        ShaderObject shader = shaders.get(shaderId);
        if (program != null) {
            if (program.attachedShaders.remove(shaderId) && shader != null) {
                shader.refCount--;
                if (shader.refCount <= 0 && shader.deleteRequested) {
                    shaders.remove(shaderId);
                }
            }
        }
    }
    
    public void useProgram(long programId) {
        currentProgram = programId;
    }
    
    public void unregisterProgram(long id) {
        programs.remove(id);
        if (currentProgram == id) {
            currentProgram = 0;
        }
    }
    
    public long getPipelineForMode(int glMode) {
        ProgramObject program = programs.get(currentProgram);
        if (program != null && program.linked) {
            return program.pipeline;
        }
        return 0;
    }
    
    public long getCurrentComputePipeline() {
        ProgramObject program = programs.get(currentProgram);
        if (program != null && program.linked) {
            return program.computePipeline;
        }
        return 0;
    }
    
    // ========================================================================
    // VERTEX ATTRIBUTE STATE
    // ========================================================================
    
    private final Map<Integer, VertexAttrib> vertexAttribs = new HashMap<>();
    
    public static class VertexAttrib {
        public int size = 4;
        public int type = 0x1406; // GL_FLOAT
        public boolean normalized = false;
        public int stride = 0;
        public long offset = 0;
        public long pointer = 0; // Legacy pointer (offset in bound VBO)
        public boolean enabled = false;
        public int binding = 0;
        public int divisor = 0;
        public boolean integer = false; // For glVertexAttribIPointer
        public boolean isLong = false; // For glVertexAttribLPointer
        
        // Generic vertex attribute values (when no buffer bound)
        public float[] genericValue = {0, 0, 0, 1};
    }
    
    public void setVertexAttribPointer(int index, int size, int type, boolean normalized, 
                                        int stride, long pointer) {
        VertexAttrib attrib = vertexAttribs.computeIfAbsent(index, k -> new VertexAttrib());
        attrib.size = size;
        attrib.type = type;
        attrib.normalized = normalized;
        attrib.stride = stride;
        attrib.pointer = pointer;
        attrib.offset = pointer;
        attrib.integer = false;
        
        // Infer binding from currently bound VAO/VBO
        long boundVBO = getBoundBuffer(GL_ARRAY_BUFFER);
        if (boundVBO != 0) {
            attrib.binding = 0; // Default binding
        }
    }
    
    public void setVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        VertexAttrib attrib = vertexAttribs.computeIfAbsent(index, k -> new VertexAttrib());
        attrib.size = size;
        attrib.type = type;
        attrib.normalized = false;
        attrib.stride = stride;
        attrib.pointer = pointer;
        attrib.offset = pointer;
        attrib.integer = true;
    }
    
    public void setVertexAttribDivisor(int index, int divisor) {
        VertexAttrib attrib = vertexAttribs.computeIfAbsent(index, k -> new VertexAttrib());
        attrib.divisor = divisor;
        vertexBindingDivisors.put(attrib.binding, divisor);
    }
    
    public void setVertexAttribBinding(int attribIndex, int bindingIndex) {
        VertexAttrib attrib = vertexAttribs.computeIfAbsent(attribIndex, k -> new VertexAttrib());
        attrib.binding = bindingIndex;
    }
    
    public void setVertexAttribFormat(int attribIndex, int size, int type, boolean normalized, int relativeOffset) {
        VertexAttrib attrib = vertexAttribs.computeIfAbsent(attribIndex, k -> new VertexAttrib());
        attrib.size = size;
        attrib.type = type;
        attrib.normalized = normalized;
        attrib.offset = relativeOffset;
    }
    
    public void enableVertexAttribArray(int index) {
        VertexAttrib attrib = vertexAttribs.computeIfAbsent(index, k -> new VertexAttrib());
        attrib.enabled = true;
    }
    
    public void disableVertexAttribArray(int index) {
        VertexAttrib attrib = vertexAttribs.get(index);
        if (attrib != null) {
            attrib.enabled = false;
        }
    }
    
    public boolean isVertexAttribEnabled(int index) {
        VertexAttrib attrib = vertexAttribs.get(index);
        return attrib != null && attrib.enabled;
    }
    
    public VertexAttrib getVertexAttrib(int index) {
        return vertexAttribs.get(index);
    }
    
    public int getEnabledAttributeCount() {
        int count = 0;
        for (VertexAttrib attr : vertexAttribs.values()) {
            if (attr.enabled) count++;
        }
        return count;
    }
    
    public int getVertexInputHash() {
        int hash = 0;
        for (Map.Entry<Integer, VertexAttrib> entry : vertexAttribs.entrySet()) {
            if (entry.getValue().enabled) {
                VertexAttrib attr = entry.getValue();
                hash = 31 * hash + entry.getKey();
                hash = 31 * hash + attr.size;
                hash = 31 * hash + attr.type;
                hash = 31 * hash + (attr.normalized ? 1 : 0);
                hash = 31 * hash + attr.stride;
                hash = 31 * hash + attr.binding;
                hash = 31 * hash + Long.hashCode(attr.offset);
            }
        }
        return hash;
    }
    
    // ========================================================================
    // VAO STATE
    // ========================================================================
    
    private final Map<Long, VAOState> vertexArrays = new HashMap<>();
    private long nextVAOId = 1;
    private long currentVAO = 0;
    
    public static class VAOState {
        public final Map<Integer, VertexAttrib> attribs = new HashMap<>();
        public final Map<Integer, Long> vertexBuffers = new HashMap<>();
        public final Map<Integer, Long> vertexOffsets = new HashMap<>();
        public final Map<Integer, Integer> bindingStrides = new HashMap<>();
        public final Map<Integer, Integer> bindingDivisors = new HashMap<>();
        public long elementArrayBuffer = 0;
    }
    
    public long createVertexArray() {
        long id = nextVAOId++;
        vertexArrays.put(id, new VAOState());
        return id;
    }
    
    public void bindVertexArray(long vao) {
        // Save current state to current VAO
        if (currentVAO != 0) {
            saveVAOState(currentVAO);
        }
        
        currentVAO = vao;
        
        // Restore state from new VAO
        if (vao != 0) {
            restoreVAOState(vao);
        } else {
            // Bind default VAO (clear state)
            vertexAttribs.clear();
            boundVertexBuffers.clear();
            vertexBufferOffsets.clear();
            vertexBindingStrides.clear();
            vertexBindingDivisors.clear();
        }
    }
    
    private void saveVAOState(long vao) {
        VAOState state = vertexArrays.get(vao);
        if (state == null) return;
        
        state.attribs.clear();
        for (Map.Entry<Integer, VertexAttrib> entry : vertexAttribs.entrySet()) {
            VertexAttrib copy = new VertexAttrib();
            VertexAttrib src = entry.getValue();
            copy.size = src.size;
            copy.type = src.type;
            copy.normalized = src.normalized;
            copy.stride = src.stride;
            copy.offset = src.offset;
            copy.pointer = src.pointer;
            copy.enabled = src.enabled;
            copy.binding = src.binding;
            copy.divisor = src.divisor;
            copy.integer = src.integer;
            state.attribs.put(entry.getKey(), copy);
        }
        
        state.vertexBuffers.clear();
        state.vertexBuffers.putAll(boundVertexBuffers);
        state.vertexOffsets.clear();
        state.vertexOffsets.putAll(vertexBufferOffsets);
        state.bindingStrides.clear();
        state.bindingStrides.putAll(vertexBindingStrides);
        state.bindingDivisors.clear();
        state.bindingDivisors.putAll(vertexBindingDivisors);
        state.elementArrayBuffer = getBoundBuffer(GL_ELEMENT_ARRAY_BUFFER);
    }
    
    private void restoreVAOState(long vao) {
        VAOState state = vertexArrays.get(vao);
        if (state == null) return;
        
        vertexAttribs.clear();
        for (Map.Entry<Integer, VertexAttrib> entry : state.attribs.entrySet()) {
            VertexAttrib copy = new VertexAttrib();
            VertexAttrib src = entry.getValue();
            copy.size = src.size;
            copy.type = src.type;
            copy.normalized = src.normalized;
            copy.stride = src.stride;
            copy.offset = src.offset;
            copy.pointer = src.pointer;
            copy.enabled = src.enabled;
            copy.binding = src.binding;
            copy.divisor = src.divisor;
            copy.integer = src.integer;
            vertexAttribs.put(entry.getKey(), copy);
        }
        
        boundVertexBuffers.clear();
        boundVertexBuffers.putAll(state.vertexBuffers);
        vertexBufferOffsets.clear();
        vertexBufferOffsets.putAll(state.vertexOffsets);
        vertexBindingStrides.clear();
        vertexBindingStrides.putAll(state.bindingStrides);
        vertexBindingDivisors.clear();
        vertexBindingDivisors.putAll(state.bindingDivisors);
        
        if (state.elementArrayBuffer != 0) {
            bindBuffer(GL_ELEMENT_ARRAY_BUFFER, state.elementArrayBuffer);
        }
    }
    
    public void deleteVertexArray(long vao) {
        if (vao == currentVAO) {
            bindVertexArray(0);
        }
        vertexArrays.remove(vao);
    }
    
    public long getCurrentVAO() {
        return currentVAO;
    }
    
    // ========================================================================
    // BLEND STATE
    // ========================================================================
    
    private int blendSrcRGB = 1; // GL_ONE
    private int blendDstRGB = 0; // GL_ZERO
    private int blendSrcAlpha = 1;
    private int blendDstAlpha = 0;
    private int blendOpRGB = 0x8006; // GL_FUNC_ADD
    private int blendOpAlpha = 0x8006;
    private float[] blendColor = {0, 0, 0, 0};
    
    public void setBlendFunc(int sfactor, int dfactor) {
        blendSrcRGB = sfactor;
        blendDstRGB = dfactor;
        blendSrcAlpha = sfactor;
        blendDstAlpha = dfactor;
    }
    
    public void setBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        blendSrcRGB = srcRGB;
        blendDstRGB = dstRGB;
        blendSrcAlpha = srcAlpha;
        blendDstAlpha = dstAlpha;
    }
    
    public void setBlendEquation(int mode) {
        blendOpRGB = mode;
        blendOpAlpha = mode;
    }
    
    public void setBlendEquationSeparate(int modeRGB, int modeAlpha) {
        blendOpRGB = modeRGB;
        blendOpAlpha = modeAlpha;
    }
    
    public void setBlendColor(float r, float g, float b, float a) {
        blendColor[0] = r;
        blendColor[1] = g;
        blendColor[2] = b;
        blendColor[3] = a;
    }
    
    public boolean isBlendEnabled() { return enabledCaps.contains(GL_BLEND); }
    public int getBlendSrcRGB() { return blendSrcRGB; }
    public int getBlendDstRGB() { return blendDstRGB; }
    public int getBlendSrcAlpha() { return blendSrcAlpha; }
    public int getBlendDstAlpha() { return blendDstAlpha; }
    public int getBlendOpRGB() { return blendOpRGB; }
    public int getBlendOpAlpha() { return blendOpAlpha; }
    public float[] getBlendConstants() { return blendColor.clone(); }
    
    // Legacy compatibility
    public int getBlendSrcFactor() { return blendSrcRGB; }
    public int getBlendDstFactor() { return blendDstRGB; }
    
    // ========================================================================
    // DEPTH STATE
    // ========================================================================
    
    private int depthFunc = 0x0201; // GL_LESS
    private boolean depthWriteEnabled = true;
    private boolean depthBoundsTestEnabled = false;
    private float depthBoundsMin = 0.0f;
    private float depthBoundsMax = 1.0f;
    private boolean depthBiasEnabled = false;
    private float depthBiasConstant = 0.0f;
    private float depthBiasClamp = 0.0f;
    private float depthBiasSlope = 0.0f;
    private float depthRangeNear = 0.0f;
    private float depthRangeFar = 1.0f;
    
    public void setDepthFunc(int func) { depthFunc = func; }
    public int getDepthFunc() { return depthFunc; }
    
    public void setDepthMask(boolean flag) { depthWriteEnabled = flag; }
    public boolean isDepthWriteEnabled() { return depthWriteEnabled; }
    
    public void setDepthRange(float near, float far) {
        depthRangeNear = near;
        depthRangeFar = far;
    }
    public float getDepthRangeNear() { return depthRangeNear; }
    public float getDepthRangeFar() { return depthRangeFar; }
    
    public void setDepthBounds(float min, float max) {
        depthBoundsMin = min;
        depthBoundsMax = max;
    }
    public boolean isDepthBoundsTestEnabled() { return depthBoundsTestEnabled; }
    public float getDepthBoundsMin() { return depthBoundsMin; }
    public float getDepthBoundsMax() { return depthBoundsMax; }
    
    public void setPolygonOffset(float factor, float units) {
        depthBiasSlope = factor;
        depthBiasConstant = units;
        depthBiasEnabled = enabledCaps.contains(GL_POLYGON_OFFSET_FILL) ||
                           enabledCaps.contains(GL_POLYGON_OFFSET_LINE) ||
                           enabledCaps.contains(GL_POLYGON_OFFSET_POINT);
    }
    
    public boolean isDepthBiasEnabled() { 
        return enabledCaps.contains(GL_POLYGON_OFFSET_FILL) ||
               enabledCaps.contains(GL_POLYGON_OFFSET_LINE) ||
               enabledCaps.contains(GL_POLYGON_OFFSET_POINT);
    }
    public float getDepthBiasConstant() { return depthBiasConstant; }
    public float getDepthBiasClamp() { return depthBiasClamp; }
    public float getDepthBiasSlope() { return depthBiasSlope; }
    
    public boolean isDepthTestEnabled() { return enabledCaps.contains(GL_DEPTH_TEST); }
    public boolean isDepthClampEnabled() { return enabledCaps.contains(GL_DEPTH_CLAMP); }
    
    // ========================================================================
    // STENCIL STATE
    // ========================================================================
    
    private final StencilState stencilFront = new StencilState();
    private final StencilState stencilBack = new StencilState();
    
    public static class StencilState {
        public int failOp = 0x1E00; // GL_KEEP
        public int passOp = 0x1E00;
        public int depthFailOp = 0x1E00;
        public int compareOp = 0x0207; // GL_ALWAYS
        public int compareMask = 0xFF;
        public int writeMask = 0xFF;
        public int reference = 0;
    }
    
    public void setStencilFunc(int func, int ref, int mask) {
        stencilFront.compareOp = func;
        stencilFront.reference = ref;
        stencilFront.compareMask = mask;
        stencilBack.compareOp = func;
        stencilBack.reference = ref;
        stencilBack.compareMask = mask;
    }
    
    public void setStencilFuncSeparate(int face, int func, int ref, int mask) {
        if (face == 0x0404 || face == 0x0408) { // GL_FRONT or GL_FRONT_AND_BACK
            stencilFront.compareOp = func;
            stencilFront.reference = ref;
            stencilFront.compareMask = mask;
        }
        if (face == 0x0405 || face == 0x0408) { // GL_BACK or GL_FRONT_AND_BACK
            stencilBack.compareOp = func;
            stencilBack.reference = ref;
            stencilBack.compareMask = mask;
        }
    }
    
    public void setStencilOp(int sfail, int dpfail, int dppass) {
        stencilFront.failOp = sfail;
        stencilFront.depthFailOp = dpfail;
        stencilFront.passOp = dppass;
        stencilBack.failOp = sfail;
        stencilBack.depthFailOp = dpfail;
        stencilBack.passOp = dppass;
    }
    
    public void setStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        if (face == 0x0404 || face == 0x0408) {
            stencilFront.failOp = sfail;
            stencilFront.depthFailOp = dpfail;
            stencilFront.passOp = dppass;
        }
        if (face == 0x0405 || face == 0x0408) {
            stencilBack.failOp = sfail;
            stencilBack.depthFailOp = dpfail;
            stencilBack.passOp = dppass;
        }
    }
    
    public void setStencilMask(int mask) {
        stencilFront.writeMask = mask;
        stencilBack.writeMask = mask;
    }
    
    public void setStencilMaskSeparate(int face, int mask) {
        if (face == 0x0404 || face == 0x0408) {
            stencilFront.writeMask = mask;
        }
        if (face == 0x0405 || face == 0x0408) {
            stencilBack.writeMask = mask;
        }
    }
    
    public boolean isStencilTestEnabled() { return enabledCaps.contains(GL_STENCIL_TEST); }
    public StencilState getStencilFront() { return stencilFront; }
    public StencilState getStencilBack() { return stencilBack; }
    
    // ========================================================================
    // CULL AND POLYGON STATE
    // ========================================================================
    
    private int cullFaceMode = 0x0405; // GL_BACK
    private int frontFace = 0x0901; // GL_CCW
    private int polygonMode = 0x1B02; // GL_FILL
    private float lineWidth = 1.0f;
    private float pointSize = 1.0f;
    
    public void setCullFace(int mode) { cullFaceMode = mode; }
    public int getCullFaceMode() { return cullFaceMode; }
    public boolean isCullFaceEnabled() { return enabledCaps.contains(GL_CULL_FACE); }
    
    public void setFrontFace(int mode) { frontFace = mode; }
    public int getFrontFace() { return frontFace; }
    
    public void setPolygonMode(int face, int mode) { polygonMode = mode; }
    public int getPolygonMode() { return polygonMode; }
    
    public void setLineWidth(float width) { lineWidth = width; }
    public float getLineWidth() { return lineWidth; }
    
    public void setPointSize(float size) { pointSize = size; }
    public float getPointSize() { return pointSize; }
    
    // ========================================================================
    // COLOR MASK AND LOGIC OP
    // ========================================================================
    
    private int colorWriteMask = 0xF; // RGBA all enabled (VK_COLOR_COMPONENT_R_BIT | G | B | A)
    private int logicOp = 0x1503; // GL_COPY
    
    public void setColorMask(boolean r, boolean g, boolean b, boolean a) {
        colorWriteMask = 0;
        if (r) colorWriteMask |= 0x1; // VK_COLOR_COMPONENT_R_BIT
        if (g) colorWriteMask |= 0x2; // VK_COLOR_COMPONENT_G_BIT
        if (b) colorWriteMask |= 0x4; // VK_COLOR_COMPONENT_B_BIT
        if (a) colorWriteMask |= 0x8; // VK_COLOR_COMPONENT_A_BIT
    }
    public int getColorWriteMask() { return colorWriteMask; }
    
    public void setLogicOp(int op) { logicOp = op; }
    public int getLogicOp() { return logicOp; }
    public boolean isLogicOpEnabled() { return enabledCaps.contains(GL_COLOR_LOGIC_OP); }
    
    // ========================================================================
    // MULTISAMPLING STATE
    // ========================================================================
    
    private int sampleCount = 1;
    private float minSampleShading = 0.0f;
    private float sampleCoverageValue = 1.0f;
    private boolean sampleCoverageInvert = false;
    
    public void setSampleCount(int count) { sampleCount = count; }
    public int getSampleCount() { return sampleCount; }
    
    public void setMinSampleShading(float value) { minSampleShading = value; }
    public float getMinSampleShading() { return minSampleShading; }
    
    public void setSampleCoverage(float value, boolean invert) {
        sampleCoverageValue = value;
        sampleCoverageInvert = invert;
    }
    public float getSampleCoverageValue() { return sampleCoverageValue; }
    public boolean isSampleCoverageInvert() { return sampleCoverageInvert; }
    
    public boolean isSampleShadingEnabled() { return enabledCaps.contains(GL_SAMPLE_SHADING); }
    public boolean isAlphaToCoverageEnabled() { return enabledCaps.contains(GL_SAMPLE_ALPHA_TO_COVERAGE); }
    public boolean isAlphaToOneEnabled() { return enabledCaps.contains(GL_SAMPLE_ALPHA_TO_ONE); }
    
    // ========================================================================
    // PRIMITIVE STATE
    // ========================================================================
    
    private int patchVertices = 3;
    
    public boolean isPrimitiveRestartEnabled() { 
        return enabledCaps.contains(GL_PRIMITIVE_RESTART) || 
               enabledCaps.contains(GL_PRIMITIVE_RESTART_FIXED_INDEX); 
    }
    
    public boolean isRasterizerDiscardEnabled() { 
        return enabledCaps.contains(GL_RASTERIZER_DISCARD); 
    }
    
    public void setPatchVertices(int count) { patchVertices = count; }
    public int getPatchVertices() { return patchVertices; }
    
    // ========================================================================
    // CAPABILITY STATE
    // ========================================================================
    
    private final Set<Integer> enabledCaps = new HashSet<>();
    
    public void enable(int cap) {
        enabledCaps.add(cap);
        
        // Update derived state
        if (cap == GL_POLYGON_OFFSET_FILL || cap == GL_POLYGON_OFFSET_LINE || cap == GL_POLYGON_OFFSET_POINT) {
            depthBiasEnabled = true;
        }
    }
    
    public void disable(int cap) {
        enabledCaps.remove(cap);
        
        // Update derived state
        if (cap == GL_POLYGON_OFFSET_FILL || cap == GL_POLYGON_OFFSET_LINE || cap == GL_POLYGON_OFFSET_POINT) {
            depthBiasEnabled = enabledCaps.contains(GL_POLYGON_OFFSET_FILL) ||
                               enabledCaps.contains(GL_POLYGON_OFFSET_LINE) ||
                               enabledCaps.contains(GL_POLYGON_OFFSET_POINT);
        }
    }
    
    public boolean isEnabled(int cap) {
        return enabledCaps.contains(cap);
    }
    
    // ========================================================================
    // CLEAR STATE
    // ========================================================================
    
    private float[] clearColor = {0, 0, 0, 1};
    private float clearDepth = 1.0f;
    private int clearStencil = 0;
    private int pendingClearMask = 0;
    
    public void setClearColor(float r, float g, float b, float a) {
        clearColor[0] = r;
        clearColor[1] = g;
        clearColor[2] = b;
        clearColor[3] = a;
    }
    public float[] getClearColor() { return clearColor.clone(); }
    
    public void setClearDepth(float depth) { clearDepth = depth; }
    public float getClearDepth() { return clearDepth; }
    
    public void setClearStencil(int stencil) { clearStencil = stencil; }
    public int getClearStencil() { return clearStencil; }
    
    public void markClearRequested(int mask) { pendingClearMask |= mask; }
    public int getPendingClearMask() { return pendingClearMask; }
    public void clearPendingClear() { pendingClearMask = 0; }
    
    // ========================================================================
    // VIEWPORT AND SCISSOR STATE
    // ========================================================================
    
    private int viewportX = 0, viewportY = 0, viewportWidth = 800, viewportHeight = 600;
    private int scissorX = 0, scissorY = 0, scissorWidth = 800, scissorHeight = 600;
    
    public void setViewport(int x, int y, int width, int height) {
        viewportX = x;
        viewportY = y;
        viewportWidth = width;
        viewportHeight = height;
    }
    public int getViewportX() { return viewportX; }
    public int getViewportY() { return viewportY; }
    public int getViewportWidth() { return viewportWidth; }
    public int getViewportHeight() { return viewportHeight; }
    public int[] getViewport() { return new int[]{viewportX, viewportY, viewportWidth, viewportHeight}; }
    
    public void setScissor(int x, int y, int width, int height) {
        scissorX = x;
        scissorY = y;
        scissorWidth = width;
        scissorHeight = height;
    }
    public int getScissorX() { return scissorX; }
    public int getScissorY() { return scissorY; }
    public int getScissorWidth() { return scissorWidth; }
    public int getScissorHeight() { return scissorHeight; }
    public int[] getScissor() { return new int[]{scissorX, scissorY, scissorWidth, scissorHeight}; }
    
    public boolean isScissorTestEnabled() { return enabledCaps.contains(GL_SCISSOR_TEST); }
    
    // ========================================================================
    // UNIFORM STATE
    // ========================================================================
    
    private final Map<Integer, Object> uniforms = new HashMap<>();
    
    public int getUniformLocation(long program, String name) {
        ProgramObject prog = programs.get(program);
        if (prog == null) return -1;
        
        return prog.uniformLocations.computeIfAbsent(name, k -> prog.nextUniformLocation++);
    }
    
    // Scalar uniforms
    public void setUniform(int location, int v) {
        if (location >= 0) uniforms.put(location, v);
    }
    
    public void setUniform(int location, float v) {
        if (location >= 0) uniforms.put(location, v);
    }
    
    // Vector uniforms
    public void setUniform(int location, int v0, int v1) {
        if (location >= 0) uniforms.put(location, new int[]{v0, v1});
    }
    
    public void setUniform(int location, float v0, float v1) {
        if (location >= 0) uniforms.put(location, new float[]{v0, v1});
    }
    
    public void setUniform(int location, int v0, int v1, int v2) {
        if (location >= 0) uniforms.put(location, new int[]{v0, v1, v2});
    }
    
    public void setUniform(int location, float v0, float v1, float v2) {
        if (location >= 0) uniforms.put(location, new float[]{v0, v1, v2});
    }
    
    public void setUniform(int location, int v0, int v1, int v2, int v3) {
        if (location >= 0) uniforms.put(location, new int[]{v0, v1, v2, v3});
    }
    
    public void setUniform(int location, float v0, float v1, float v2, float v3) {
        if (location >= 0) uniforms.put(location, new float[]{v0, v1, v2, v3});
    }
    
    // Matrix uniforms
    public void setUniformMatrix2(int location, boolean transpose, float[] value) {
        if (location >= 0) {
            float[] matrix = value.clone();
            if (transpose) transposeMatrix2(matrix);
            uniforms.put(location, matrix);
        }
    }
    
    public void setUniformMatrix3(int location, boolean transpose, float[] value) {
        if (location >= 0) {
            float[] matrix = value.clone();
            if (transpose) transposeMatrix3(matrix);
            uniforms.put(location, matrix);
        }
    }
    
    public void setUniformMatrix4(int location, boolean transpose, float[] value) {
        if (location >= 0) {
            float[] matrix = value.clone();
            if (transpose) transposeMatrix4(matrix);
            uniforms.put(location, matrix);
        }
    }
    
    private void transposeMatrix2(float[] m) {
        float t = m[1]; m[1] = m[2]; m[2] = t;
    }
    
    private void transposeMatrix3(float[] m) {
        float t;
        t = m[1]; m[1] = m[3]; m[3] = t;
        t = m[2]; m[2] = m[6]; m[6] = t;
        t = m[5]; m[5] = m[7]; m[7] = t;
    }
    
    private void transposeMatrix4(float[] m) {
        float t;
        t = m[1]; m[1] = m[4]; m[4] = t;
        t = m[2]; m[2] = m[8]; m[8] = t;
        t = m[3]; m[3] = m[12]; m[12] = t;
        t = m[6]; m[6] = m[9]; m[9] = t;
        t = m[7]; m[7] = m[13]; m[13] = t;
        t = m[11]; m[11] = m[14]; m[14] = t;
    }
    
    public Object getUniform(int location) {
        return uniforms.get(location);
    }
    
    public Map<Integer, Object> getAllUniforms() {
        return new HashMap<>(uniforms);
    }
    
    public void clearUniforms() {
        uniforms.clear();
    }
    
    // ========================================================================
    // DESCRIPTOR SET STATE
    // ========================================================================
    
    private long currentDescriptorSet = 0;
    
    public long getCurrentDescriptorSet() {
        return currentDescriptorSet;
    }
    
    public void setDescriptorSet(long descriptorSet) {
        currentDescriptorSet = descriptorSet;
    }
    
    // ========================================================================
    // ACTIVE TEXTURE UNIT
    // ========================================================================
    
    public void setActiveTexture(int unit) {
        // unit is GL_TEXTURE0 + n
        activeTextureUnit = unit - 0x84C0; // GL_TEXTURE0
    }
    
    public int getActiveTextureUnit() {
        return activeTextureUnit;
    }
    
    // ========================================================================
    // STATE RESET
    // ========================================================================
    
    public void reset() {
        // Reset to default OpenGL state
        activeTextureUnit = 0;
        boundTextures.clear();
        boundBuffers.clear();
        boundVertexBuffers.clear();
        vertexBufferOffsets.clear();
        vertexBindingStrides.clear();
        vertexBindingDivisors.clear();
        indexedBindings.clear();
        vertexAttribs.clear();
        
        currentProgram = 0;
        currentVAO = 0;
        currentDescriptorSet = 0;
        
        enabledCaps.clear();
        
        // Blend
        blendSrcRGB = 1;
        blendDstRGB = 0;
        blendSrcAlpha = 1;
        blendDstAlpha = 0;
        blendOpRGB = 0x8006;
        blendOpAlpha = 0x8006;
        blendColor = new float[]{0, 0, 0, 0};
        
        // Depth
        depthFunc = 0x0201;
        depthWriteEnabled = true;
        depthBoundsTestEnabled = false;
        depthBoundsMin = 0.0f;
        depthBoundsMax = 1.0f;
        depthBiasEnabled = false;
        depthBiasConstant = 0.0f;
        depthBiasClamp = 0.0f;
        depthBiasSlope = 0.0f;
        depthRangeNear = 0.0f;
        depthRangeFar = 1.0f;
        
        // Stencil
        stencilFront.failOp = 0x1E00;
        stencilFront.passOp = 0x1E00;
        stencilFront.depthFailOp = 0x1E00;
        stencilFront.compareOp = 0x0207;
        stencilFront.compareMask = 0xFF;
        stencilFront.writeMask = 0xFF;
        stencilFront.reference = 0;
        
        stencilBack.failOp = 0x1E00;
        stencilBack.passOp = 0x1E00;
        stencilBack.depthFailOp = 0x1E00;
        stencilBack.compareOp = 0x0207;
        stencilBack.compareMask = 0xFF;
        stencilBack.writeMask = 0xFF;
        stencilBack.reference = 0;
        
        // Cull/Polygon
        cullFaceMode = 0x0405;
        frontFace = 0x0901;
        polygonMode = 0x1B02;
        lineWidth = 1.0f;
        pointSize = 1.0f;
        
        // Color/Logic
        colorWriteMask = 0xF;
        logicOp = 0x1503;
        
        // Multisample
        sampleCount = 1;
        minSampleShading = 0.0f;
        sampleCoverageValue = 1.0f;
        sampleCoverageInvert = false;
        
        // Primitive
        patchVertices = 3;
        
        // Clear
        clearColor = new float[]{0, 0, 0, 1};
        clearDepth = 1.0f;
        clearStencil = 0;
        pendingClearMask = 0;
        
        // Viewport/Scissor
        viewportX = 0;
        viewportY = 0;
        viewportWidth = 800;
        viewportHeight = 600;
        scissorX = 0;
        scissorY = 0;
        scissorWidth = 800;
        scissorHeight = 600;
        
        // Uniforms
        uniforms.clear();
    }
    
    // ========================================================================
    // DEBUG/STATUS
    // ========================================================================
    
    public String getStateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VulkanState Report ===\n");
        sb.append("Current Program: ").append(currentProgram).append("\n");
        sb.append("Current VAO: ").append(currentVAO).append("\n");
        sb.append("Active Texture Unit: ").append(activeTextureUnit).append("\n");
        
        sb.append("\nBound Textures:\n");
        for (Map.Entry<Integer, Long> entry : boundTextures.entrySet()) {
            sb.append("  Unit ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        
        sb.append("\nBound Buffers:\n");
        for (Map.Entry<Integer, Long> entry : boundBuffers.entrySet()) {
            sb.append("  Target 0x").append(Integer.toHexString(entry.getKey()))
              .append(": ").append(entry.getValue()).append("\n");
        }
        
        sb.append("\nEnabled Vertex Attributes:\n");
        for (Map.Entry<Integer, VertexAttrib> entry : vertexAttribs.entrySet()) {
            if (entry.getValue().enabled) {
                VertexAttrib attr = entry.getValue();
                sb.append("  Location ").append(entry.getKey())
                  .append(": size=").append(attr.size)
                  .append(", type=0x").append(Integer.toHexString(attr.type))
                  .append(", stride=").append(attr.stride)
                  .append(", offset=").append(attr.offset)
                  .append(", binding=").append(attr.binding)
                  .append("\n");
            }
        }
        
        sb.append("\nEnabled Capabilities:\n");
        for (int cap : enabledCaps) {
            sb.append("  0x").append(Integer.toHexString(cap)).append("\n");
        }
        
        sb.append("\nBlend State:\n");
        sb.append("  Enabled: ").append(isBlendEnabled()).append("\n");
        sb.append("  Src RGB: 0x").append(Integer.toHexString(blendSrcRGB))
          .append(", Dst RGB: 0x").append(Integer.toHexString(blendDstRGB)).append("\n");
        sb.append("  Src Alpha: 0x").append(Integer.toHexString(blendSrcAlpha))
          .append(", Dst Alpha: 0x").append(Integer.toHexString(blendDstAlpha)).append("\n");
        
        sb.append("\nDepth State:\n");
        sb.append("  Test Enabled: ").append(isDepthTestEnabled()).append("\n");
        sb.append("  Write Enabled: ").append(depthWriteEnabled).append("\n");
        sb.append("  Func: 0x").append(Integer.toHexString(depthFunc)).append("\n");
        
        sb.append("\nStencil State:\n");
        sb.append("  Test Enabled: ").append(isStencilTestEnabled()).append("\n");
        
        sb.append("\nCull State:\n");
        sb.append("  Enabled: ").append(isCullFaceEnabled()).append("\n");
        sb.append("  Mode: 0x").append(Integer.toHexString(cullFaceMode)).append("\n");
        sb.append("  Front Face: 0x").append(Integer.toHexString(frontFace)).append("\n");
        
        sb.append("\nViewport: ").append(viewportX).append(", ").append(viewportY)
          .append(", ").append(viewportWidth).append("x").append(viewportHeight).append("\n");
        sb.append("Scissor: ").append(scissorX).append(", ").append(scissorY)
          .append(", ").append(scissorWidth).append("x").append(scissorHeight).append("\n");
        
        sb.append("\nRegistered Objects:\n");
        sb.append("  Textures: ").append(textures.size()).append("\n");
        sb.append("  Buffers: ").append(buffers.size()).append("\n");
        sb.append("  Shaders: ").append(shaders.size()).append("\n");
        sb.append("  Programs: ").append(programs.size()).append("\n");
        sb.append("  VAOs: ").append(vertexArrays.size()).append("\n");
        sb.append("  Uniforms: ").append(uniforms.size()).append("\n");
        
        return sb.toString();
    }
}
