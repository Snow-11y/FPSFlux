package com.example.modid.gl.mapping;

import com.example.modid.gl.GLCapabilities;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Comprehensive OpenGL call mapping across all versions
 * 
 * Maps legacy calls to best available modern equivalent:
 * GL 1.1 → GL 1.5 → GL 2.0 → GL 3.0 → GL 3.1 → GL 3.3 → GL 4.0 → GL 4.2 → GL 4.3 → GL 4.5 → GL 4.6
 */
public class OpenGLCallMapper {
    
    // ========================================================================
    // TEXTURE OPERATIONS
    // ========================================================================
    
    /**
     * glBindTexture mapping
     * GL 1.1: glBindTexture(target, texture)
     * GL 4.5: glBindTextureUnit(unit, texture) [DSA - no target needed]
     */
    public static void bindTexture(int target, int texture) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            // GL 4.5 DSA: Can bind directly to specific unit
            // But requires knowing which unit - for now use legacy
            GL11.glBindTexture(target, texture);
        } else {
            // GL 1.1+: Standard bind
            GL11.glBindTexture(target, texture);
        }
    }
    
    /**
     * glActiveTexture + glBindTexture mapping
     * GL 1.1: Multi-step (glActiveTexture + glBindTexture)
     * GL 4.5: glBindTextureUnit(unit, texture) [Single call]
     */
    public static void bindTextureToUnit(int unit, int texture) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            // GL 4.5 DSA: Direct unit binding
            GL45.glBindTextureUnit(unit, texture);
        } else if (GLCapabilities.GL13) {
            // GL 1.3+: Two-step binding
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        } else {
            // GL 1.1: No multi-texturing, ignore unit
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
    }
    
    /**
     * glTexImage2D mapping
     * GL 1.1: glTexImage2D(...)
     * GL 4.2: glTexStorage2D(...) [Immutable storage, better performance]
     * GL 4.5: glTextureStorage2D(...) [DSA version]
     */
    public static void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer data) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA && level == 0) {
            // GL 4.5 DSA: Use texture storage (requires texture ID, not target)
            // For now, fall back since we'd need texture ID
            GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, data);
        } else if (GLCapabilities.GL42 && level == 0) {
            // GL 4.2: Immutable storage (only for base level)
            GL42.glTexStorage2D(target, 1, internalFormat, width, height);
            if (data != null) {
                GL11.glTexSubImage2D(target, 0, 0, 0, width, height, format, type, data);
            }
        } else {
            // GL 1.1+: Standard mutable storage
            GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, data);
        }
    }
    
    /**
     * glTexSubImage2D mapping
     * GL 1.1: glTexSubImage2D(target, ...)
     * GL 4.5: glTextureSubImage2D(texture, ...) [DSA - no binding]
     */
    public static void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer data) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            // Would need texture ID for DSA
            GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, data);
        } else {
            // GL 1.1+
            GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, data);
        }
    }
    
    /**
     * glGenerateMipmap mapping
     * GL 3.0: glGenerateMipmap(target)
     * GL 4.5: glGenerateTextureMipmap(texture) [DSA]
     */
    public static void generateMipmap(int target) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            // Would need texture ID
            GL30.glGenerateMipmap(target);
        } else if (GLCapabilities.GL30) {
            // GL 3.0+
            GL30.glGenerateMipmap(target);
        } else {
            // No automatic mipmap generation in GL < 3.0
            // Would need manual mipmap generation
        }
    }
    
    // ========================================================================
    // BUFFER OPERATIONS
    // ========================================================================
    
    /**
     * glGenBuffers mapping
     * GL 1.5: glGenBuffers()
     * GL 4.5: glCreateBuffers() [DSA - pre-initialized]
     */
    public static int genBuffer() {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            // GL 4.5 DSA: Create directly
            return GL45.glCreateBuffers();
        } else if (GLCapabilities.GL15) {
            // GL 1.5+
            return GL15.glGenBuffers();
        } else {
            throw new UnsupportedOperationException("VBO not supported");
        }
    }
    
    /**
     * glBindBuffer mapping
     * GL 1.5: glBindBuffer(target, buffer)
     * GL 4.5: No binding needed with DSA, but still supported
     */
    public static void bindBuffer(int target, int buffer) {
        // Always use standard bind (DSA operations don't need it)
        if (GLCapabilities.GL15) {
            GL15.glBindBuffer(target, buffer);
        }
    }
    
    /**
     * glBufferData mapping
     * GL 1.5: glBufferData(target, data, usage)
     * GL 4.4: glBufferStorage(target, data, flags) [Immutable, persistent mapping]
     * GL 4.5: glNamedBufferData/glNamedBufferStorage(buffer, ...) [DSA]
     */
    public static void bufferData(int target, long size, int usage) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            // GL 4.5 DSA: Would need buffer ID
            // For persistent mapping
            if (GLCapabilities.hasPersistentMapping) {
                int flags = translateUsageToStorageFlags(usage);
                GL44.glBufferStorage(target, size, flags);
            } else {
                GL15.glBufferData(target, size, usage);
            }
        } else if (GLCapabilities.GL44 && GLCapabilities.hasPersistentMapping) {
            // GL 4.4: Immutable storage
            int flags = translateUsageToStorageFlags(usage);
            GL44.glBufferStorage(target, size, flags);
        } else if (GLCapabilities.GL15) {
            // GL 1.5: Mutable storage
            GL15.glBufferData(target, size, usage);
        }
    }
    
    public static void bufferData(int target, ByteBuffer data, int usage) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            if (GLCapabilities.hasPersistentMapping) {
                int flags = translateUsageToStorageFlags(usage);
                GL44.glBufferStorage(target, data, flags);
            } else {
                GL15.glBufferData(target, data, usage);
            }
        } else if (GLCapabilities.GL44 && GLCapabilities.hasPersistentMapping) {
            int flags = translateUsageToStorageFlags(usage);
            GL44.glBufferStorage(target, data, flags);
        } else if (GLCapabilities.GL15) {
            GL15.glBufferData(target, data, usage);
        }
    }
    
    /**
     * glBufferSubData mapping
     * GL 1.5: glBufferSubData(target, offset, data)
     * GL 4.5: glNamedBufferSubData(buffer, offset, data) [DSA - no binding]
     */
    public static void bufferSubData(int target, long offset, ByteBuffer data) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            // Would need buffer ID for DSA
            GL15.glBufferSubData(target, offset, data);
        } else if (GLCapabilities.GL15) {
            GL15.glBufferSubData(target, offset, data);
        }
    }
    
    /**
     * Named buffer operations (requires buffer ID)
     */
    public static void namedBufferData(int buffer, long size, int usage) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            if (GLCapabilities.hasPersistentMapping) {
                int flags = translateUsageToStorageFlags(usage);
                GL45.glNamedBufferStorage(buffer, size, flags);
            } else {
                GL45.glNamedBufferData(buffer, size, usage);
            }
        } else {
            // Fallback: bind then use standard call
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            bufferData(GL15.GL_ARRAY_BUFFER, size, usage);
        }
    }
    
    public static void namedBufferSubData(int buffer, long offset, ByteBuffer data) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            GL45.glNamedBufferSubData(buffer, offset, data);
        } else {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offset, data);
        }
    }
    
    /**
     * glCopyBufferSubData mapping
     * GL 3.1: glCopyBufferSubData(readTarget, writeTarget, ...)
     * GL 4.5: glCopyNamedBufferSubData(readBuffer, writeBuffer, ...) [DSA]
     */
    public static void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            // Would need buffer IDs for DSA
            GL31.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
        } else if (GLCapabilities.GL31) {
            GL31.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
        } else if (GLCapabilities.GL15) {
            // Manual copy via map/unmap
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, readTarget);
            ByteBuffer src = GL15.glMapBuffer(GL15.GL_ARRAY_BUFFER, GL15.GL_READ_ONLY, size, null);
            
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, writeTarget);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, writeOffset, src);
            
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, readTarget);
            GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
        }
    }
    
    public static void copyNamedBufferSubData(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            GL45.glCopyNamedBufferSubData(readBuffer, writeBuffer, readOffset, writeOffset, size);
        } else if (GLCapabilities.GL31) {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, readBuffer);
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, writeBuffer);
            GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER, GL31.GL_COPY_WRITE_BUFFER, readOffset, writeOffset, size);
        } else {
            // Manual fallback
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, readBuffer);
            ByteBuffer src = GL15.glMapBuffer(GL15.GL_ARRAY_BUFFER, GL15.GL_READ_ONLY, size, null);
            
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, writeBuffer);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, writeOffset, src);
            
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, readBuffer);
            GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
        }
    }
    
    /**
     * glMapBuffer mapping
     * GL 1.5: glMapBuffer(target, access)
     * GL 3.0: glMapBufferRange(target, offset, length, access) [More control]
     * GL 4.5: glMapNamedBuffer/glMapNamedBufferRange(buffer, ...) [DSA]
     */
    public static ByteBuffer mapBuffer(int target, int access, long length) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            // Would need buffer ID
            return GL30.glMapBufferRange(target, 0, length, translateAccessToMapFlags(access), null);
        } else if (GLCapabilities.GL30) {
            return GL30.glMapBufferRange(target, 0, length, translateAccessToMapFlags(access), null);
        } else if (GLCapabilities.GL15) {
            return GL15.glMapBuffer(target, access, length, null);
        }
        return null;
    }
    
    public static ByteBuffer mapNamedBuffer(int buffer, int access, long length) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            return GL45.glMapNamedBufferRange(buffer, 0, length, translateAccessToMapFlags(access), null);
        } else {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            return mapBuffer(GL15.GL_ARRAY_BUFFER, access, length);
        }
    }
    
    /**
     * glUnmapBuffer mapping
     * GL 1.5: glUnmapBuffer(target)
     * GL 4.5: glUnmapNamedBuffer(buffer) [DSA]
     */
    public static void unmapBuffer(int target) {
        if (GLCapabilities.GL15) {
            GL15.glUnmapBuffer(target);
        }
    }
    
    public static void unmapNamedBuffer(int buffer) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            GL45.glUnmapNamedBuffer(buffer);
        } else {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
        }
    }
    
    // ========================================================================
    // VERTEX ARRAY OPERATIONS
    // ========================================================================
    
    /**
     * glGenVertexArrays mapping
     * GL 3.0: glGenVertexArrays()
     * GL 4.5: glCreateVertexArrays() [DSA - pre-initialized]
     */
    public static int genVertexArray() {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            return GL45.glCreateVertexArrays();
        } else if (GLCapabilities.GL30 && GLCapabilities.hasVAO) {
            return GL30.glGenVertexArrays();
        } else {
            throw new UnsupportedOperationException("VAO not supported");
        }
    }
    
    /**
     * glBindVertexArray mapping
     * GL 3.0: glBindVertexArray(array)
     */
    public static void bindVertexArray(int array) {
        if (GLCapabilities.GL30 && GLCapabilities.hasVAO) {
            GL30.glBindVertexArray(array);
        }
    }
    
    /**
     * glVertexAttribPointer mapping
     * GL 2.0: glVertexAttribPointer(index, size, type, normalized, stride, pointer)
     * GL 4.3: glVertexAttribFormat + glVertexAttribBinding [Separate format from binding]
     * GL 4.5: glVertexArrayAttribFormat + glVertexArrayAttribBinding [DSA]
     */
    public static void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        if (GLCapabilities.GL20) {
            GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
        }
    }
    
    /**
     * glEnableVertexAttribArray mapping
     * GL 2.0: glEnableVertexAttribArray(index)
     * GL 4.5: glEnableVertexArrayAttrib(vao, index) [DSA]
     */
    public static void enableVertexAttribArray(int index) {
        if (GLCapabilities.GL20) {
            GL20.glEnableVertexAttribArray(index);
        }
    }
    
    public static void disableVertexAttribArray(int index) {
        if (GLCapabilities.GL20) {
            GL20.glDisableVertexAttribArray(index);
        }
    }
    
    // ========================================================================
    // DRAW CALLS
    // ========================================================================
    
    /**
     * glDrawArrays mapping
     * GL 1.1: glDrawArrays(mode, first, count)
     * GL 4.3: glMultiDrawArraysIndirect(mode, indirect, drawcount, stride) [Batch many draws]
     */
    public static void drawArrays(int mode, int first, int count) {
        GL11.glDrawArrays(mode, first, count);
    }
    
    /**
     * glDrawElements mapping
     * GL 1.1: glDrawElements(mode, count, type, indices)
     * GL 4.3: glMultiDrawElementsIndirect(...) [Batch many draws]
     */
    public static void drawElements(int mode, int count, int type, long indices) {
        GL11.glDrawElements(mode, count, type, indices);
    }
    
    /**
     * glDrawArraysInstanced mapping
     * GL 3.1: glDrawArraysInstanced(mode, first, count, instancecount)
     * GL 4.2: glDrawArraysInstancedBaseInstance(...) [Specify base instance]
     */
    public static void drawArraysInstanced(int mode, int first, int count, int instanceCount) {
        if (GLCapabilities.GL42 && GLCapabilities.hasBaseInstance) {
            GL42.glDrawArraysInstancedBaseInstance(mode, first, count, instanceCount, 0);
        } else if (GLCapabilities.GL31 && GLCapabilities.hasInstancing) {
            GL31.glDrawArraysInstanced(mode, first, count, instanceCount);
        } else {
            // Fallback: draw multiple times (slow)
            for (int i = 0; i < instanceCount; i++) {
                GL11.glDrawArrays(mode, first, count);
            }
        }
    }
    
    /**
     * glDrawElementsInstanced mapping
     * GL 3.1: glDrawElementsInstanced(mode, count, type, indices, instancecount)
     * GL 4.2: glDrawElementsInstancedBaseInstance(...) [With base instance]
     * GL 4.2: glDrawElementsInstancedBaseVertexBaseInstance(...) [With base vertex too]
     */
    public static void drawElementsInstanced(int mode, int count, int type, long indices, int instanceCount) {
        if (GLCapabilities.GL42 && GLCapabilities.hasBaseInstance) {
            GL42.glDrawElementsInstancedBaseInstance(mode, count, type, indices, instanceCount, 0);
        } else if (GLCapabilities.GL31 && GLCapabilities.hasInstancing) {
            GL31.glDrawElementsInstanced(mode, count, type, indices, instanceCount);
        } else {
            for (int i = 0; i < instanceCount; i++) {
                GL11.glDrawElements(mode, count, type, indices);
            }
        }
    }
    
    /**
     * Multi-draw indirect mapping (GL 4.3+)
     */
    public static void multiDrawArraysIndirect(int mode, long indirect, int drawcount, int stride) {
        if (GLCapabilities.GL43 && GLCapabilities.hasMultiDrawIndirect) {
            GL43.glMultiDrawArraysIndirect(mode, indirect, drawcount, stride);
        } else {
            // Fallback: individual draws
            // Would need to parse indirect buffer manually
        }
    }
    
    // ========================================================================
    // FRAMEBUFFER OPERATIONS
    // ========================================================================
    
    /**
     * glGenFramebuffers mapping
     * GL 3.0: glGenFramebuffers()
     * GL 4.5: glCreateFramebuffers() [DSA]
     */
    public static int genFramebuffer() {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            return GL45.glCreateFramebuffers();
        } else if (GLCapabilities.GL30) {
            return GL30.glGenFramebuffers();
        } else {
            throw new UnsupportedOperationException("FBO not supported");
        }
    }
    
    /**
     * glBindFramebuffer mapping
     * GL 3.0: glBindFramebuffer(target, framebuffer)
     */
    public static void bindFramebuffer(int target, int framebuffer) {
        if (GLCapabilities.GL30) {
            GL30.glBindFramebuffer(target, framebuffer);
        }
    }
    
    /**
     * glFramebufferTexture2D mapping
     * GL 3.0: glFramebufferTexture2D(target, attachment, textarget, texture, level)
     * GL 4.5: glNamedFramebufferTexture(framebuffer, attachment, texture, level) [DSA]
     */
    public static void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            // Would need framebuffer ID
            GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
        } else if (GLCapabilities.GL30) {
            GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
        }
    }
    
    // ========================================================================
    // SHADER OPERATIONS
    // ========================================================================
    
    /**
     * glCreateShader mapping
     * GL 2.0: glCreateShader(type)
     */
    public static int createShader(int type) {
        if (GLCapabilities.GL20) {
            return GL20.glCreateShader(type);
        }
        throw new UnsupportedOperationException("Shaders not supported");
    }
    
    /**
     * glShaderSource mapping
     * GL 2.0: glShaderSource(shader, string)
     */
    public static void shaderSource(int shader, CharSequence source) {
        if (GLCapabilities.GL20) {
            GL20.glShaderSource(shader, source);
        }
    }
    
    /**
     * glCompileShader mapping
     * GL 2.0: glCompileShader(shader)
     */
    public static void compileShader(int shader) {
        if (GLCapabilities.GL20) {
            GL20.glCompileShader(shader);
        }
    }
    
    /**
     * glCreateProgram mapping
     * GL 2.0: glCreateProgram()
     */
    public static int createProgram() {
        if (GLCapabilities.GL20) {
            return GL20.glCreateProgram();
        }
        throw new UnsupportedOperationException("Shaders not supported");
    }
    
    /**
     * glAttachShader mapping
     * GL 2.0: glAttachShader(program, shader)
     */
    public static void attachShader(int program, int shader) {
        if (GLCapabilities.GL20) {
            GL20.glAttachShader(program, shader);
        }
    }
    
    /**
     * glLinkProgram mapping
     * GL 2.0: glLinkProgram(program)
     */
    public static void linkProgram(int program) {
        if (GLCapabilities.GL20) {
            GL20.glLinkProgram(program);
        }
    }
    
    /**
     * glUseProgram mapping
     * GL 2.0: glUseProgram(program)
     */
    public static void useProgram(int program) {
        if (GLCapabilities.GL20) {
            GL20.glUseProgram(program);
        }
    }
    
    /**
     * glGetUniformLocation mapping
     * GL 2.0: glGetUniformLocation(program, name)
     */
    public static int getUniformLocation(int program, CharSequence name) {
        if (GLCapabilities.GL20) {
            return GL20.glGetUniformLocation(program, name);
        }
        return -1;
    }
    
    /**
     * glUniform* mapping
     * GL 2.0: glUniform1f, glUniform2f, etc.
     * GL 4.1: glProgramUniform* [Can set uniforms without binding program]
     */
    public static void uniform1f(int location, float v0) {
        if (GLCapabilities.GL20) {
            GL20.glUniform1f(location, v0);
        }
    }
    
    public static void uniform2f(int location, float v0, float v1) {
        if (GLCapabilities.GL20) {
            GL20.glUniform2f(location, v0, v1);
        }
    }
    
    public static void uniform3f(int location, float v0, float v1, float v2) {
        if (GLCapabilities.GL20) {
            GL20.glUniform3f(location, v0, v1, v2);
        }
    }
    
    public static void uniform4f(int location, float v0, float v1, float v2, float v3) {
        if (GLCapabilities.GL20) {
            GL20.glUniform4f(location, v0, v1, v2, v3);
        }
    }
    
    public static void uniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        if (GLCapabilities.GL20) {
            GL20.glUniformMatrix4fv(location, transpose, value);
        }
    }
    
    // ========================================================================
    // STATE MANAGEMENT
    // ========================================================================
    
    /**
     * glEnable/glDisable mapping
     * GL 1.1: glEnable(cap) / glDisable(cap)
     */
    public static void enable(int cap) {
        GL11.glEnable(cap);
    }
    
    public static void disable(int cap) {
        GL11.glDisable(cap);
    }
    
    /**
     * glBlendFunc mapping
     * GL 1.1: glBlendFunc(sfactor, dfactor)
     * GL 1.4: glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha)
     * GL 4.0: glBlendFunci(...) [Per-draw-buffer blending]
     */
    public static void blendFunc(int sfactor, int dfactor) {
        GL11.glBlendFunc(sfactor, dfactor);
    }
    
    public static void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        if (GLCapabilities.GL14) {
            GL14.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
        } else {
            GL11.glBlendFunc(srcRGB, dstRGB);
        }
    }
    
    /**
     * glDepthFunc mapping
     * GL 1.1: glDepthFunc(func)
     */
    public static void depthFunc(int func) {
        GL11.glDepthFunc(func);
    }
    
    /**
     * glDepthMask mapping
     * GL 1.1: glDepthMask(flag)
     */
    public static void depthMask(boolean flag) {
        GL11.glDepthMask(flag);
    }
    
    /**
     * glCullFace mapping
     * GL 1.1: glCullFace(mode)
     */
    public static void cullFace(int mode) {
        GL11.glCullFace(mode);
    }
    
    /**
     * glPolygonMode mapping
     * GL 1.1: glPolygonMode(face, mode)
     */
    public static void polygonMode(int face, int mode) {
        GL11.glPolygonMode(face, mode);
    }
    
    // ========================================================================
    // QUERY OPERATIONS
    // ========================================================================
    
    /**
     * glGetInteger mapping
     * GL 1.1: glGetIntegerv(pname, params)
     * GL 4.5: glGetInteger*(pname) [Type-specific getters]
     */
    public static int getInteger(int pname) {
        return GL11.glGetInteger(pname);
    }
    
    public static void getIntegerv(int pname, IntBuffer params) {
        GL11.glGetIntegerv(pname, params);
    }
    
    /**
     * glGetString mapping
     * GL 1.1: glGetString(name)
     */
    public static String getString(int name) {
        return GL11.glGetString(name);
    }
    
    /**
     * glGetError mapping
     * GL 1.1: glGetError()
     */
    public static int getError() {public static int getError() {
        return GL11.glGetError();
    }
    
    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================
    
    private static int translateUsageToStorageFlags(int usage) {
        return switch (usage) {
            case GL15.GL_STATIC_DRAW -> 0;
            case GL15.GL_DYNAMIC_DRAW -> GL44.GL_DYNAMIC_STORAGE_BIT;
            case GL15.GL_STREAM_DRAW -> GL44.GL_DYNAMIC_STORAGE_BIT | GL44.GL_MAP_WRITE_BIT;
            case GL15.GL_STATIC_READ -> GL44.GL_MAP_READ_BIT;
            case GL15.GL_DYNAMIC_READ -> GL44.GL_DYNAMIC_STORAGE_BIT | GL44.GL_MAP_READ_BIT;
            case GL15.GL_STREAM_READ -> GL44.GL_DYNAMIC_STORAGE_BIT | GL44.GL_MAP_READ_BIT;
            case GL15.GL_STATIC_COPY -> 0;
            case GL15.GL_DYNAMIC_COPY -> GL44.GL_DYNAMIC_STORAGE_BIT;
            case GL15.GL_STREAM_COPY -> GL44.GL_DYNAMIC_STORAGE_BIT;
            default -> GL44.GL_DYNAMIC_STORAGE_BIT;
        };
    }
    
    private static int translateAccessToMapFlags(int access) {
        return switch (access) {
            case GL15.GL_READ_ONLY -> GL30.GL_MAP_READ_BIT;
            case GL15.GL_WRITE_ONLY -> GL30.GL_MAP_WRITE_BIT;
            case GL15.GL_READ_WRITE -> GL30.GL_MAP_READ_BIT | GL30.GL_MAP_WRITE_BIT;
            default -> GL30.GL_MAP_WRITE_BIT;
        };
    }
}
