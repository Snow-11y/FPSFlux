package com.example.modid.gl;

import com.example.modid.gl.mapping.OpenGLCallMapper;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL44.*;
import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.opengl.GL46.*;

/**
 * OpenGLBackend - GPUBackend implementation for OpenGL.
 */
public final class OpenGLBackend implements GPUBackend {
    
    private static OpenGLBackend INSTANCE;
    
    public static OpenGLBackend get() {
        if (INSTANCE == null) {
            INSTANCE = new OpenGLBackend();
        }
        return INSTANCE;
    }
    
    private boolean initialized = false;
    private String versionString;
    private int glVersion;
    
    // Capability cache
    private boolean hasMultiDrawIndirect;
    private boolean hasIndirectCount;
    private boolean hasComputeShaders;
    private boolean hasMeshShaders;
    private boolean hasBufferDeviceAddress;
    private boolean hasPersistentMapping;
    
    // Buffer tracking for mapping
    private final Map<Long, MappedBufferInfo> mappedBuffers = new HashMap<>();
    
    private static class MappedBufferInfo {
        int target;
        ByteBuffer mapped;
        long offset;
        long size;
    }
    
    private OpenGLBackend() {}
    
    /**
     * Initialize backend.
     */
    public boolean initialize() {
        if (initialized) return true;
        
        try {
            OpenGLCallMapper.initialize();
            
            versionString = OpenGLCallMapper.getString(GL_VERSION);
            glVersion = parseGLVersion(versionString);
            
            // Detect capabilities
            hasMultiDrawIndirect = glVersion >= 43 || OpenGLCallMapper.hasExtension("GL_ARB_multi_draw_indirect");
            hasIndirectCount = glVersion >= 46 || OpenGLCallMapper.hasExtension("GL_ARB_indirect_parameters");
            hasComputeShaders = glVersion >= 43 || OpenGLCallMapper.hasExtension("GL_ARB_compute_shader");
            hasMeshShaders = OpenGLCallMapper.hasExtension("GL_NV_mesh_shader");
            hasBufferDeviceAddress = OpenGLCallMapper.hasExtension("GL_NV_shader_buffer_load");
            hasPersistentMapping = glVersion >= 44 || OpenGLCallMapper.hasExtension("GL_ARB_buffer_storage");
            
            initialized = true;
            return true;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private int parseGLVersion(String version) {
        try {
            String[] parts = version.split("[\\s.]");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major * 10 + minor;
        } catch (Exception e) {
            return 33; // Default to 3.3
        }
    }
    
    @Override
    public Type getType() { return Type.OPENGL; }
    
    @Override
    public String getVersionString() { return versionString; }
    
    @Override
    public boolean isInitialized() { return initialized; }
    
    // ========================================================================
    // BUFFER OPERATIONS
    // ========================================================================
    
    @Override
    public long createBuffer(long size, int usage, int memoryFlags) {
        int glUsage = translateUsageFlags(usage, memoryFlags);
        
        int[] buffer = new int[1];
        glGenBuffers(buffer);
        long handle = buffer[0];
        
        if (hasPersistentMapping && (memoryFlags & MemoryFlags.PERSISTENT) != 0) {
            // Use immutable storage
            int storageFlags = GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
            if ((memoryFlags & MemoryFlags.HOST_VISIBLE) != 0) {
                storageFlags |= GL_MAP_READ_BIT;
            }
            
            glBindBuffer(GL_ARRAY_BUFFER, (int) handle);
            glBufferStorage(GL_ARRAY_BUFFER, size, storageFlags);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        } else {
            glBindBuffer(GL_ARRAY_BUFFER, (int) handle);
            glBufferData(GL_ARRAY_BUFFER, size, glUsage);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
        
        return handle;
    }
    
    private int translateUsageFlags(int usage, int memoryFlags) {
        boolean dynamic = (memoryFlags & MemoryFlags.HOST_VISIBLE) != 0;
        boolean read = (usage & BufferUsage.TRANSFER_SRC) != 0;
        
        if (dynamic) {
            return read ? GL_DYNAMIC_READ : GL_DYNAMIC_DRAW;
        } else {
            return read ? GL_STATIC_READ : GL_STATIC_DRAW;
        }
    }
    
    @Override
    public void destroyBuffer(long buffer) {
        int[] buf = new int[] { (int) buffer };
        glDeleteBuffers(buf);
        mappedBuffers.remove(buffer);
    }
    
    @Override
    public void bufferUpload(long buffer, long offset, ByteBuffer data) {
        glBindBuffer(GL_ARRAY_BUFFER, (int) buffer);
        glBufferSubData(GL_ARRAY_BUFFER, offset, data);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    
    @Override
    public ByteBuffer mapBuffer(long buffer, long offset, long size) {
        MappedBufferInfo info = mappedBuffers.get(buffer);
        if (info != null && info.mapped != null) {
            // Already mapped - return existing
            return info.mapped;
        }
        
        glBindBuffer(GL_ARRAY_BUFFER, (int) buffer);
        
        ByteBuffer mapped;
        if (hasPersistentMapping) {
            // Persistent coherent mapping
            mapped = glMapBufferRange(GL_ARRAY_BUFFER, offset, size,
                GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT);
        } else {
            mapped = glMapBufferRange(GL_ARRAY_BUFFER, offset, size,
                GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_RANGE_BIT);
        }
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        
        info = new MappedBufferInfo();
        info.target = GL_ARRAY_BUFFER;
        info.mapped = mapped;
        info.offset = offset;
        info.size = size;
        mappedBuffers.put(buffer, info);
        
        return mapped;
    }
    
    @Override
    public void unmapBuffer(long buffer) {
        MappedBufferInfo info = mappedBuffers.get(buffer);
        if (info == null) return;
        
        // For persistent mapping, don't actually unmap
        if (!hasPersistentMapping) {
            glBindBuffer(info.target, (int) buffer);
            glUnmapBuffer(info.target);
            glBindBuffer(info.target, 0);
        }
        
        mappedBuffers.remove(buffer);
    }
    
    @Override
    public long getBufferDeviceAddress(long buffer) {
        if (!hasBufferDeviceAddress) return 0;
        // GL_NV_shader_buffer_load extension
        // This is limited in OpenGL
        return 0;
    }
    
    // ========================================================================
    // TEXTURE OPERATIONS
    // ========================================================================
    
    @Override
    public long createTexture2D(int width, int height, int format, int mipLevels) {
        int[] tex = new int[1];
        glGenTextures(tex);
        long handle = tex[0];
        
        int glFormat = translateFormat(format);
        int glInternalFormat = translateInternalFormat(format);
        int glType = translateType(format);
        
        glBindTexture(GL_TEXTURE_2D, (int) handle);
        
        if (glVersion >= 42) {
            glTexStorage2D(GL_TEXTURE_2D, mipLevels, glInternalFormat, width, height);
        } else {
            for (int i = 0; i < mipLevels; i++) {
                int w = Math.max(1, width >> i);
                int h = Math.max(1, height >> i);
                glTexImage2D(GL_TEXTURE_2D, i, glInternalFormat, w, h, 0, glFormat, glType, (ByteBuffer) null);
            }
        }
        
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mipLevels > 1 ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        
        glBindTexture(GL_TEXTURE_2D, 0);
        
        return handle;
    }
    
    private int translateFormat(int format) {
        return switch (format) {
            case TextureFormat.RGBA8, TextureFormat.RGBA16F, TextureFormat.RGBA32F -> GL_RGBA;
            case TextureFormat.DEPTH24_STENCIL8 -> GL_DEPTH_STENCIL;
            case TextureFormat.DEPTH32F -> GL_DEPTH_COMPONENT;
            case TextureFormat.R32UI -> GL_RED_INTEGER;
            case TextureFormat.RG32F -> GL_RG;
            default -> GL_RGBA;
        };
    }
    
    private int translateInternalFormat(int format) {
        return switch (format) {
            case TextureFormat.RGBA8 -> GL_RGBA8;
            case TextureFormat.RGBA16F -> GL_RGBA16F;
            case TextureFormat.RGBA32F -> GL_RGBA32F;
            case TextureFormat.DEPTH24_STENCIL8 -> GL_DEPTH24_STENCIL8;
            case TextureFormat.DEPTH32F -> GL_DEPTH_COMPONENT32F;
            case TextureFormat.R32UI -> GL_R32UI;
            case TextureFormat.RG32F -> GL_RG32F;
            default -> GL_RGBA8;
        };
    }
    
    private int translateType(int format) {
        return switch (format) {
            case TextureFormat.RGBA8 -> GL_UNSIGNED_BYTE;
            case TextureFormat.RGBA16F, TextureFormat.RGBA32F, TextureFormat.RG32F -> GL_FLOAT;
            case TextureFormat.DEPTH24_STENCIL8 -> GL_UNSIGNED_INT_24_8;
            case TextureFormat.DEPTH32F -> GL_FLOAT;
            case TextureFormat.R32UI -> GL_UNSIGNED_INT;
            default -> GL_UNSIGNED_BYTE;
        };
    }
    
    @Override
    public void destroyTexture(long texture) {
        int[] tex = new int[] { (int) texture };
        glDeleteTextures(tex);
    }
    
    @Override
    public void textureUpload(long texture, int level, int x, int y, int width, int height, ByteBuffer data) {
        glBindTexture(GL_TEXTURE_2D, (int) texture);
        glTexSubImage2D(GL_TEXTURE_2D, level, x, y, width, height, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    // ========================================================================
    // SHADER OPERATIONS
    // ========================================================================
    
    @Override
    public long createShader(int stage, String source) {
        int glType = translateShaderStage(stage);
        int shader = glCreateShader(glType);
        glShaderSource(shader, source);
        glCompileShader(shader);
        
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new RuntimeException("Shader compilation failed: " + log);
        }
        
        return shader;
    }
    
    private int translateShaderStage(int stage) {
        if ((stage & ShaderStage.VERTEX) != 0) return GL_VERTEX_SHADER;
        if ((stage & ShaderStage.FRAGMENT) != 0) return GL_FRAGMENT_SHADER;
        if ((stage & ShaderStage.GEOMETRY) != 0) return GL_GEOMETRY_SHADER;
        if ((stage & ShaderStage.TESS_CONTROL) != 0) return GL_TESS_CONTROL_SHADER;
        if ((stage & ShaderStage.TESS_EVAL) != 0) return GL_TESS_EVALUATION_SHADER;
        if ((stage & ShaderStage.COMPUTE) != 0) return GL_COMPUTE_SHADER;
        // Mesh shaders need NV extension
        return GL_VERTEX_SHADER;
    }
    
    @Override
    public long createShaderFromSPIRV(int stage, ByteBuffer spirv) {
        if (glVersion < 46 && !OpenGLCallMapper.hasExtension("GL_ARB_gl_spirv")) {
            throw new UnsupportedOperationException("SPIR-V shaders require GL 4.6 or GL_ARB_gl_spirv");
        }
        
        int glType = translateShaderStage(stage);
        int shader = glCreateShader(glType);
        
        glShaderBinary(new int[] { shader }, GL_SHADER_BINARY_FORMAT_SPIR_V, spirv);
        glSpecializeShader(shader, "main", new int[0], new int[0]);
        
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new RuntimeException("SPIR-V shader specialization failed: " + log);
        }
        
        return shader;
    }
    
    @Override
    public void destroyShader(long shader) {
        glDeleteShader((int) shader);
    }
    
    @Override
    public long createProgram(long... shaders) {
        int program = glCreateProgram();
        
        for (long shader : shaders) {
            glAttachShader(program, (int) shader);
        }
        
        glLinkProgram(program);
        
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new RuntimeException("Program linking failed: " + log);
        }
        
        // Detach shaders after linking
        for (long shader : shaders) {
            glDetachShader(program, (int) shader);
        }
        
        return program;
    }
    
    @Override
    public void destroyProgram(long program) {
        glDeleteProgram((int) program);
    }
    
    // ========================================================================
    // DRAW OPERATIONS
    // ========================================================================
    
    @Override
    public void bindVertexBuffer(int binding, long buffer, long offset, int stride) {
        if (glVersion >= 43) {
            glBindVertexBuffer(binding, (int) buffer, offset, stride);
        } else {
            // Fallback: use VAO
            glBindBuffer(GL_ARRAY_BUFFER, (int) buffer);
        }
    }
    
    @Override
    public void bindIndexBuffer(long buffer, long offset, boolean is32Bit) {
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, (int) buffer);
        // Offset handled in draw call
    }
    
    @Override
    public void bindProgram(long program) {
        glUseProgram((int) program);
    }
    
    @Override
    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        if (glVersion >= 42) {
            glDrawElementsInstancedBaseVertexBaseInstance(
                GL_TRIANGLES, indexCount, GL_UNSIGNED_INT,
                firstIndex * 4L, instanceCount, vertexOffset, firstInstance);
        } else if (instanceCount > 1) {
            glDrawElementsInstancedBaseVertex(
                GL_TRIANGLES, indexCount, GL_UNSIGNED_INT,
                firstIndex * 4L, instanceCount, vertexOffset);
        } else {
            glDrawElementsBaseVertex(
                GL_TRIANGLES, indexCount, GL_UNSIGNED_INT,
                firstIndex * 4L, vertexOffset);
        }
    }
    
    @Override
    public void drawIndexedIndirect(long buffer, long offset, int drawCount, int stride) {
        if (!hasMultiDrawIndirect) {
            throw new UnsupportedOperationException("Multi-draw indirect not supported");
        }
        
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, (int) buffer);
        glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, offset, drawCount, stride);
    }
    
    @Override
    public void drawIndexedIndirectCount(long commandBuffer, long commandOffset,
                                          long countBuffer, long countOffset,
                                          int maxDrawCount, int stride) {
        if (!hasIndirectCount) {
            throw new UnsupportedOperationException("Indirect count not supported (requires GL 4.6)");
        }
        
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, (int) commandBuffer);
        glBindBuffer(GL_PARAMETER_BUFFER, (int) countBuffer);
        
        glMultiDrawElementsIndirectCount(
            GL_TRIANGLES, GL_UNSIGNED_INT,
            commandOffset, countOffset, maxDrawCount, stride);
    }
    
    // ========================================================================
    // COMPUTE OPERATIONS
    // ========================================================================
    
    @Override
    public void bindComputeProgram(long program) {
        glUseProgram((int) program);
    }
    
    @Override
    public void dispatchCompute(int groupsX, int groupsY, int groupsZ) {
        if (!hasComputeShaders) {
            throw new UnsupportedOperationException("Compute shaders not supported");
        }
        glDispatchCompute(groupsX, groupsY, groupsZ);
    }
    
    @Override
    public void dispatchComputeIndirect(long buffer, long offset) {
        if (!hasComputeShaders) {
            throw new UnsupportedOperationException("Compute shaders not supported");
        }
        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, (int) buffer);
        glDispatchComputeIndirect(offset);
    }
    
    // ========================================================================
    // SYNCHRONIZATION
    // ========================================================================
    
    @Override
    public void memoryBarrier(int barrierBits) {
        int glBarriers = 0;
        
        if ((barrierBits & BarrierType.VERTEX_ATTRIB) != 0) glBarriers |= GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT;
        if ((barrierBits & BarrierType.INDEX_READ) != 0) glBarriers |= GL_ELEMENT_ARRAY_BARRIER_BIT;
        if ((barrierBits & BarrierType.UNIFORM_READ) != 0) glBarriers |= GL_UNIFORM_BARRIER_BIT;
        if ((barrierBits & BarrierType.TEXTURE_FETCH) != 0) glBarriers |= GL_TEXTURE_FETCH_BARRIER_BIT;
        if ((barrierBits & BarrierType.SHADER_IMAGE) != 0) glBarriers |= GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
        if ((barrierBits & BarrierType.INDIRECT_COMMAND) != 0) glBarriers |= GL_COMMAND_BARRIER_BIT;
        if ((barrierBits & BarrierType.BUFFER_UPDATE) != 0) glBarriers |= GL_BUFFER_UPDATE_BARRIER_BIT;
        if ((barrierBits & BarrierType.SHADER_STORAGE) != 0) glBarriers |= GL_SHADER_STORAGE_BARRIER_BIT;
        if (barrierBits == BarrierType.ALL) glBarriers = GL_ALL_BARRIER_BITS;
        
        glMemoryBarrier(glBarriers);
    }
    
    @Override
    public void finish() {
        glFinish();
    }
    
    // ========================================================================
    // RENDER PASS
    // ========================================================================
    
    @Override
    public void beginRenderPass(RenderPassInfo info) {
        // OpenGL uses FBOs instead of render passes
        if (info.colorAttachments != null && info.colorAttachments.length > 0) {
            // Bind FBO with attachments
            // For simplicity, assume default framebuffer or pre-configured FBO
        }
        
        glViewport(info.x, info.y, info.width, info.height);
        
        if (info.clearOnLoad) {
            if (info.clearColor != null) {
                glClearColor(info.clearColor[0], info.clearColor[1], info.clearColor[2], info.clearColor[3]);
            }
            glClearDepth(info.clearDepth);
            glClearStencil(info.clearStencil);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        }
    }
    
    @Override
    public void endRenderPass() {
        // Nothing special for OpenGL
    }
    
    // ========================================================================
    // FRAME MANAGEMENT
    // ========================================================================
    
    private int frameIndex = 0;
    
    @Override
    public void beginFrame() {
        // OpenGL doesn't need explicit frame management
    }
    
    @Override
    public void endFrame() {
        frameIndex = (frameIndex + 1) % 3;
    }
    
    @Override
    public int getCurrentFrameIndex() {
        return frameIndex;
    }
    
    // ========================================================================
    // CAPABILITIES
    // ========================================================================
    
    @Override
    public boolean supportsMultiDrawIndirect() { return hasMultiDrawIndirect; }
    
    @Override
    public boolean supportsIndirectCount() { return hasIndirectCount; }
    
    @Override
    public boolean supportsComputeShaders() { return hasComputeShaders; }
    
    @Override
    public boolean supportsMeshShaders() { return hasMeshShaders; }
    
    @Override
    public boolean supportsBufferDeviceAddress() { return hasBufferDeviceAddress; }
    
    @Override
    public boolean supportsPersistentMapping() { return hasPersistentMapping; }
    
    @Override
    public int getMaxComputeWorkGroupSize() {
        if (!hasComputeShaders) return 0;
        return glGetIntegeri(GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0);
    }
    
    @Override
    public int getMaxDrawIndirectCount() {
        return Integer.MAX_VALUE; // No explicit limit in OpenGL
    }
}
