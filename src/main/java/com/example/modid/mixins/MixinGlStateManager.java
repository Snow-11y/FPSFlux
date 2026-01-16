package com.example.modid.mixins;

import com.example.modid.gl.OpenGLManager;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Comprehensive GlStateManager mixin that routes all OpenGL calls through
 * an optimized state management system with caching and redundant call elimination.
 * 
 * @author FPSFlux
 * @version 2.0
 */
@Mixin(GlStateManager.class)
public abstract class MixinGlStateManager {

    // ========================================================================
    // GL CONSTANTS (avoiding magic numbers)
    // ========================================================================
    
    @Unique private static final int GL_TEXTURE_2D = 0x0DE1;
    @Unique private static final int GL_DEPTH_TEST = 0x0B71;
    @Unique private static final int GL_ALPHA_TEST = 0x0BC0;
    @Unique private static final int GL_BLEND = 0x0BE2;
    @Unique private static final int GL_CULL_FACE = 0x0B44;
    @Unique private static final int GL_POLYGON_OFFSET_FILL = 0x8037;
    @Unique private static final int GL_LIGHTING = 0x0B50;
    @Unique private static final int GL_FOG = 0x0B60;
    @Unique private static final int GL_COLOR_MATERIAL = 0x0B57;
    @Unique private static final int GL_NORMALIZE = 0x0BA1;
    @Unique private static final int GL_RESCALE_NORMAL = 0x803A;
    @Unique private static final int GL_SCISSOR_TEST = 0x0C11;
    @Unique private static final int GL_STENCIL_TEST = 0x0B90;
    @Unique private static final int GL_LINE_SMOOTH = 0x0B20;
    @Unique private static final int GL_POLYGON_SMOOTH = 0x0B41;
    @Unique private static final int GL_TEXTURE0 = 0x84C0;
    
    // Matrix modes
    @Unique private static final int GL_MODELVIEW = 0x1700;
    @Unique private static final int GL_PROJECTION = 0x1701;
    @Unique private static final int GL_TEXTURE_MATRIX = 0x1702;
    
    // ========================================================================
    // LOCAL STATE CACHE (for ultra-high-frequency calls)
    // ========================================================================
    
    @Unique private static float cachedRed = 1.0f;
    @Unique private static float cachedGreen = 1.0f;
    @Unique private static float cachedBlue = 1.0f;
    @Unique private static float cachedAlpha = 1.0f;
    @Unique private static boolean colorStateDirty = true;
    
    @Unique private static int cachedAlphaFunc = GL11.GL_ALWAYS;
    @Unique private static float cachedAlphaRef = 0.0f;
    
    @Unique private static int cachedCullMode = GL11.GL_BACK;
    @Unique private static int cachedShadeModel = GL11.GL_SMOOTH;
    
    @Unique private static int cachedMatrixMode = GL_MODELVIEW;
    
    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    @Unique
    private static boolean isReady() {
        return OpenGLManager.isInitialized();
    }
    
    @Unique
    private static void enable(int cap) {
        if (isReady()) {
            OpenGLManager.getFast().enable(cap);
        } else {
            GL11.glEnable(cap);
        }
    }
    
    @Unique
    private static void disable(int cap) {
        if (isReady()) {
            OpenGLManager.getFast().disable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    // ========================================================================
    // TEXTURE MANAGEMENT
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Route texture binding through optimized cache
     */
    @Overwrite
    public static void bindTexture(int texture) {
        if (isReady()) {
            OpenGLManager.getFast().bindTexture(GL_TEXTURE_2D, texture);
        } else {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
    }

    /**
     * @author FPSFlux
     * @reason Cached active texture unit switching
     */
    @Overwrite
    public static void setActiveTexture(int texture) {
        if (isReady()) {
            OpenGLManager.getFast().activeTexture(texture);
        } else {
            GL13.glActiveTexture(texture);
        }
    }

    /**
     * @author FPSFlux
     * @reason Proper texture deletion with cache invalidation
     */
    @Overwrite
    public static void deleteTexture(int texture) {
        GL11.glDeleteTextures(texture);
        if (isReady()) {
            OpenGLManager.getFast().invalidateTextureBindings();
        }
    }

    /**
     * @author FPSFlux
     * @reason Cached GL_TEXTURE_2D enable
     */
    @Overwrite
    public static void enableTexture2D() {
        enable(GL_TEXTURE_2D);
    }

    /**
     * @author FPSFlux
     * @reason Cached GL_TEXTURE_2D disable
     */
    @Overwrite
    public static void disableTexture2D() {
        disable(GL_TEXTURE_2D);
    }

    /**
     * @author FPSFlux
     * @reason Texture parameter caching
     */
    @Overwrite
    public static void glTexParameteri(int target, int pname, int param) {
        GL11.glTexParameteri(target, pname, param);
    }

    /**
     * @author FPSFlux
     * @reason Direct texture parameter call
     */
    @Overwrite
    public static void glTexParameterf(int target, int pname, float param) {
        GL11.glTexParameterf(target, pname, param);
    }

    /**
     * @author FPSFlux
     * @reason Texture image specification
     */
    @Overwrite
    public static void glTexImage2D(int target, int level, int internalFormat, int width, int height, 
                                     int border, int format, int type, IntBuffer pixels) {
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    /**
     * @author FPSFlux
     * @reason Texture subimage update
     */
    @Overwrite
    public static void glTexSubImage2D(int target, int level, int xOffset, int yOffset, 
                                        int width, int height, int format, int type, IntBuffer pixels) {
        GL11.glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
    }

    /**
     * @author FPSFlux
     * @reason Copy pixels from framebuffer to texture
     */
    @Overwrite
    public static void glCopyTexSubImage2D(int target, int level, int xOffset, int yOffset, 
                                            int x, int y, int width, int height) {
        GL11.glCopyTexSubImage2D(target, level, xOffset, yOffset, x, y, width, height);
    }

    /**
     * @author FPSFlux
     * @reason Get texture level parameter
     */
    @Overwrite
    public static void glGetTexLevelParameteriv(int target, int level, int pname, IntBuffer params) {
        GL11.glGetTexLevelParameter(target, level, pname, params);
    }

    /**
     * @author FPSFlux
     * @reason Texture generation with tracking
     */
    @Overwrite
    public static int generateTexture() {
        return GL11.glGenTextures();
    }

    // ========================================================================
    // DEPTH TESTING
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached depth test enable
     */
    @Overwrite
    public static void enableDepth() {
        enable(GL_DEPTH_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Cached depth test disable
     */
    @Overwrite
    public static void disableDepth() {
        disable(GL_DEPTH_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Cached depth function
     */
    @Overwrite
    public static void depthFunc(int func) {
        if (isReady()) {
            OpenGLManager.getFast().depthFunc(func);
        } else {
            GL11.glDepthFunc(func);
        }
    }

    /**
     * @author FPSFlux
     * @reason Cached depth mask
     */
    @Overwrite
    public static void depthMask(boolean flagIn) {
        if (isReady()) {
            OpenGLManager.getFast().depthMask(flagIn);
        } else {
            GL11.glDepthMask(flagIn);
        }
    }

    /**
     * @author FPSFlux
     * @reason Clear depth setting
     */
    @Overwrite
    public static void clearDepth(double depth) {
        GL11.glClearDepth(depth);
    }

    // ========================================================================
    // ALPHA TESTING
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached alpha test enable
     */
    @Overwrite
    public static void enableAlpha() {
        enable(GL_ALPHA_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Cached alpha test disable
     */
    @Overwrite
    public static void disableAlpha() {
        disable(GL_ALPHA_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Cached alpha function with state tracking
     */
    @Overwrite
    public static void alphaFunc(int func, float ref) {
        if (func != cachedAlphaFunc || ref != cachedAlphaRef) {
            cachedAlphaFunc = func;
            cachedAlphaRef = ref;
            GL11.glAlphaFunc(func, ref);
        }
    }

    // ========================================================================
    // BLENDING
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached blend enable
     */
    @Overwrite
    public static void enableBlend() {
        enable(GL_BLEND);
    }

    /**
     * @author FPSFlux
     * @reason Cached blend disable
     */
    @Overwrite
    public static void disableBlend() {
        disable(GL_BLEND);
    }

    /**
     * @author FPSFlux
     * @reason Cached blend function
     */
    @Overwrite
    public static void blendFunc(int srcFactor, int dstFactor) {
        if (isReady()) {
            OpenGLManager.getFast().blendFunc(srcFactor, dstFactor);
        } else {
            GL11.glBlendFunc(srcFactor, dstFactor);
        }
    }

    /**
     * @author FPSFlux
     * @reason Cached separate blend function
     */
    @Overwrite
    public static void tryBlendFuncSeparate(int srcFactor, int dstFactor, int srcFactorAlpha, int dstFactorAlpha) {
        if (isReady()) {
            OpenGLManager.getFast().blendFuncSeparate(srcFactor, dstFactor, srcFactorAlpha, dstFactorAlpha);
        } else {
            GL14.glBlendFuncSeparate(srcFactor, dstFactor, srcFactorAlpha, dstFactorAlpha);
        }
    }

    /**
     * @author FPSFlux
     * @reason Blend equation support
     */
    @Overwrite
    public static void blendEquation(int mode) {
        GL14.glBlendEquation(mode);
    }

    // ========================================================================
    // CULLING
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached cull face enable
     */
    @Overwrite
    public static void enableCull() {
        enable(GL_CULL_FACE);
    }

    /**
     * @author FPSFlux
     * @reason Cached cull face disable
     */
    @Overwrite
    public static void disableCull() {
        disable(GL_CULL_FACE);
    }

    /**
     * @author FPSFlux
     * @reason Cached cull face mode
     */
    @Overwrite
    public static void cullFace(int mode) {
        if (mode != cachedCullMode) {
            cachedCullMode = mode;
            GL11.glCullFace(mode);
        }
    }

    // ========================================================================
    // POLYGON OFFSET
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached polygon offset enable
     */
    @Overwrite
    public static void enablePolygonOffset() {
        enable(GL_POLYGON_OFFSET_FILL);
    }

    /**
     * @author FPSFlux
     * @reason Cached polygon offset disable
     */
    @Overwrite
    public static void disablePolygonOffset() {
        disable(GL_POLYGON_OFFSET_FILL);
    }

    /**
     * @author FPSFlux
     * @reason Cached polygon offset values
     */
    @Overwrite
    public static void doPolygonOffset(float factor, float units) {
        if (isReady()) {
            OpenGLManager.getFast().polygonOffset(factor, units);
        } else {
            GL11.glPolygonOffset(factor, units);
        }
    }

    // ========================================================================
    // VIEWPORT & SCISSOR
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached viewport
     */
    @Overwrite
    public static void viewport(int x, int y, int width, int height) {
        if (isReady()) {
            OpenGLManager.getFast().viewport(x, y, width, height);
        } else {
            GL11.glViewport(x, y, width, height);
        }
    }

    /**
     * @author FPSFlux
     * @reason Scissor test enable
     */
    @Overwrite
    public static void enableScissorTest() {
        enable(GL_SCISSOR_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Scissor test disable
     */
    @Overwrite
    public static void disableScissorTest() {
        disable(GL_SCISSOR_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Scissor box definition
     */
    @Overwrite
    public static void scissor(int x, int y, int width, int height) {
        GL11.glScissor(x, y, width, height);
    }

    // ========================================================================
    // COLOR MASK & CLEAR
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached color mask
     */
    @Overwrite
    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        if (isReady()) {
            OpenGLManager.getFast().colorMask(red, green, blue, alpha);
        } else {
            GL11.glColorMask(red, green, blue, alpha);
        }
    }

    /**
     * @author FPSFlux
     * @reason Cached clear color
     */
    @Overwrite
    public static void clearColor(float red, float green, float blue, float alpha) {
        if (isReady()) {
            OpenGLManager.getFast().clearColor(red, green, blue, alpha);
        } else {
            GL11.glClearColor(red, green, blue, alpha);
        }
    }

    /**
     * @author FPSFlux
     * @reason Direct clear call (no caching beneficial)
     */
    @Overwrite
    public static void clear(int mask) {
        GL11.glClear(mask);
    }

    // ========================================================================
    // LIGHTING (Fixed Function Pipeline)
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached lighting enable
     */
    @Overwrite
    public static void enableLighting() {
        enable(GL_LIGHTING);
    }

    /**
     * @author FPSFlux
     * @reason Cached lighting disable
     */
    @Overwrite
    public static void disableLighting() {
        disable(GL_LIGHTING);
    }

    /**
     * @author FPSFlux
     * @reason Enable individual light
     */
    @Overwrite
    public static void enableLight(int light) {
        enable(GL11.GL_LIGHT0 + light);
    }

    /**
     * @author FPSFlux
     * @reason Disable individual light
     */
    @Overwrite
    public static void disableLight(int light) {
        disable(GL11.GL_LIGHT0 + light);
    }

    /**
     * @author FPSFlux
     * @reason Light parameter setting
     */
    @Overwrite
    public static void glLight(int light, int pname, FloatBuffer params) {
        GL11.glLight(light, pname, params);
    }

    /**
     * @author FPSFlux
     * @reason Light model setting
     */
    @Overwrite
    public static void glLightModel(int pname, FloatBuffer params) {
        GL11.glLightModel(pname, params);
    }

    /**
     * @author FPSFlux
     * @reason Enable color material
     */
    @Overwrite
    public static void enableColorMaterial() {
        enable(GL_COLOR_MATERIAL);
    }

    /**
     * @author FPSFlux
     * @reason Disable color material
     */
    @Overwrite
    public static void disableColorMaterial() {
        disable(GL_COLOR_MATERIAL);
    }

    /**
     * @author FPSFlux
     * @reason Color material mode
     */
    @Overwrite
    public static void colorMaterial(int face, int mode) {
        GL11.glColorMaterial(face, mode);
    }

    // ========================================================================
    // NORMALS
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Enable normalize
     */
    @Overwrite
    public static void enableNormalize() {
        enable(GL_NORMALIZE);
    }

    /**
     * @author FPSFlux
     * @reason Disable normalize
     */
    @Overwrite
    public static void disableNormalize() {
        disable(GL_NORMALIZE);
    }

    /**
     * @author FPSFlux
     * @reason Enable rescale normal (OpenGL 1.2+)
     */
    @Overwrite
    public static void enableRescaleNormal() {
        enable(GL_RESCALE_NORMAL);
    }

    /**
     * @author FPSFlux
     * @reason Disable rescale normal
     */
    @Overwrite
    public static void disableRescaleNormal() {
        disable(GL_RESCALE_NORMAL);
    }

    // ========================================================================
    // FOG
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Enable fog
     */
    @Overwrite
    public static void enableFog() {
        enable(GL_FOG);
    }

    /**
     * @author FPSFlux
     * @reason Disable fog
     */
    @Overwrite
    public static void disableFog() {
        disable(GL_FOG);
    }

    /**
     * @author FPSFlux
     * @reason Set fog mode
     */
    @Overwrite
    public static void setFog(int mode) {
        GL11.glFogi(GL11.GL_FOG_MODE, mode);
    }

    /**
     * @author FPSFlux
     * @reason Set fog density
     */
    @Overwrite
    public static void setFogDensity(float density) {
        GL11.glFogf(GL11.GL_FOG_DENSITY, density);
    }

    /**
     * @author FPSFlux
     * @reason Set fog start distance
     */
    @Overwrite
    public static void setFogStart(float start) {
        GL11.glFogf(GL11.GL_FOG_START, start);
    }

    /**
     * @author FPSFlux
     * @reason Set fog end distance
     */
    @Overwrite
    public static void setFogEnd(float end) {
        GL11.glFogf(GL11.GL_FOG_END, end);
    }

    /**
     * @author FPSFlux
     * @reason Set fog color
     */
    @Overwrite
    public static void glFog(int pname, FloatBuffer params) {
        GL11.glFog(pname, params);
    }

    /**
     * @author FPSFlux
     * @reason Set fog integer parameter
     */
    @Overwrite
    public static void glFogi(int pname, int param) {
        GL11.glFogi(pname, param);
    }

    // ========================================================================
    // COLOR (High Frequency - Locally Cached)
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Ultra-high frequency call with local caching
     */
    @Overwrite
    public static void color(float colorRed, float colorGreen, float colorBlue, float colorAlpha) {
        if (colorRed != cachedRed || colorGreen != cachedGreen || 
            colorBlue != cachedBlue || colorAlpha != cachedAlpha) {
            cachedRed = colorRed;
            cachedGreen = colorGreen;
            cachedBlue = colorBlue;
            cachedAlpha = colorAlpha;
            colorStateDirty = true;
            GL11.glColor4f(colorRed, colorGreen, colorBlue, colorAlpha);
        }
    }

    /**
     * @author FPSFlux
     * @reason RGB color with alpha = 1
     */
    @Overwrite
    public static void color(float colorRed, float colorGreen, float colorBlue) {
        color(colorRed, colorGreen, colorBlue, 1.0f);
    }

    /**
     * @author FPSFlux
     * @reason Reset color to white with caching
     */
    @Overwrite
    public static void resetColor() {
        if (colorStateDirty || cachedRed != 1.0f || cachedGreen != 1.0f || 
            cachedBlue != 1.0f || cachedAlpha != 1.0f) {
            cachedRed = 1.0f;
            cachedGreen = 1.0f;
            cachedBlue = 1.0f;
            cachedAlpha = 1.0f;
            colorStateDirty = false;
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    // ========================================================================
    // SHADE MODEL
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached shade model
     */
    @Overwrite
    public static void shadeModel(int mode) {
        if (mode != cachedShadeModel) {
            cachedShadeModel = mode;
            GL11.glShadeModel(mode);
        }
    }

    // ========================================================================
    // LINE & POINT
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Enable line smoothing
     */
    @Overwrite
    public static void enableLineSmooth() {
        enable(GL_LINE_SMOOTH);
    }

    /**
     * @author FPSFlux
     * @reason Disable line smoothing
     */
    @Overwrite
    public static void disableLineSmooth() {
        disable(GL_LINE_SMOOTH);
    }

    /**
     * @author FPSFlux
     * @reason Set line width
     */
    @Overwrite
    public static void glLineWidth(float width) {
        GL11.glLineWidth(width);
    }

    /**
     * @author FPSFlux
     * @reason Set polygon mode
     */
    @Overwrite
    public static void glPolygonMode(int face, int mode) {
        GL11.glPolygonMode(face, mode);
    }

    // ========================================================================
    // MATRIX OPERATIONS
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached matrix mode
     */
    @Overwrite
    public static void matrixMode(int mode) {
        if (mode != cachedMatrixMode) {
            cachedMatrixMode = mode;
            GL11.glMatrixMode(mode);
        }
    }

    /**
     * @author FPSFlux
     * @reason Load identity matrix
     */
    @Overwrite
    public static void loadIdentity() {
        GL11.glLoadIdentity();
    }

    /**
     * @author FPSFlux
     * @reason Push matrix stack
     */
    @Overwrite
    public static void pushMatrix() {
        GL11.glPushMatrix();
    }

    /**
     * @author FPSFlux
     * @reason Pop matrix stack
     */
    @Overwrite
    public static void popMatrix() {
        GL11.glPopMatrix();
    }

    /**
     * @author FPSFlux
     * @reason Orthographic projection
     */
    @Overwrite
    public static void ortho(double left, double right, double bottom, double top, double zNear, double zFar) {
        GL11.glOrtho(left, right, bottom, top, zNear, zFar);
    }

    /**
     * @author FPSFlux
     * @reason Rotation around axis
     */
    @Overwrite
    public static void rotate(float angle, float x, float y, float z) {
        GL11.glRotatef(angle, x, y, z);
    }

    /**
     * @author FPSFlux
     * @reason Scale transformation
     */
    @Overwrite
    public static void scale(float x, float y, float z) {
        GL11.glScalef(x, y, z);
    }

    /**
     * @author FPSFlux
     * @reason Scale transformation (double precision)
     */
    @Overwrite
    public static void scale(double x, double y, double z) {
        GL11.glScaled(x, y, z);
    }

    /**
     * @author FPSFlux
     * @reason Translation
     */
    @Overwrite
    public static void translate(float x, float y, float z) {
        GL11.glTranslatef(x, y, z);
    }

    /**
     * @author FPSFlux
     * @reason Translation (double precision)
     */
    @Overwrite
    public static void translate(double x, double y, double z) {
        GL11.glTranslated(x, y, z);
    }

    /**
     * @author FPSFlux
     * @reason Multiply by matrix
     */
    @Overwrite
    public static void multMatrix(FloatBuffer matrix) {
        GL11.glMultMatrix(matrix);
    }

    /**
     * @author FPSFlux
     * @reason Get current matrix
     */
    @Overwrite
    public static void getFloat(int pname, FloatBuffer params) {
        GL11.glGetFloat(pname, params);
    }

    // ========================================================================
    // TEXTURE ENVIRONMENT
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Set texture environment mode
     */
    @Overwrite
    public static void glTexEnvi(int target, int pname, int param) {
        GL11.glTexEnvi(target, pname, param);
    }

    /**
     * @author FPSFlux
     * @reason Set texture environment float parameter
     */
    @Overwrite
    public static void glTexEnvf(int target, int pname, float param) {
        GL11.glTexEnvf(target, pname, param);
    }

    /**
     * @author FPSFlux
     * @reason Set texture environment with float buffer
     */
    @Overwrite
    public static void glTexEnv(int target, int pname, FloatBuffer params) {
        GL11.glTexEnv(target, pname, params);
    }

    // ========================================================================
    // SHADER PROGRAM MANAGEMENT
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached shader program binding
     */
    @Overwrite
    public static void glUseProgram(int program) {
        if (isReady()) {
            OpenGLManager.getFast().useProgram(program);
        } else {
            GL20.glUseProgram(program);
        }
    }

    /**
     * @author FPSFlux
     * @reason Get uniform location
     */
    @Overwrite
    public static int glGetUniformLocation(int program, CharSequence name) {
        return GL20.glGetUniformLocation(program, name);
    }

    /**
     * @author FPSFlux
     * @reason Set integer uniform
     */
    @Overwrite
    public static void glUniform1i(int location, int v0) {
        GL20.glUniform1(location, v0);
    }

    /**
     * @author FPSFlux
     * @reason Set float uniform
     */
    @Overwrite
    public static void glUniform1f(int location, float v0) {
        GL20.glUniform1f(location, v0);
    }

    /**
     * @author FPSFlux
     * @reason Set vec2 uniform
     */
    @Overwrite
    public static void glUniform2f(int location, float v0, float v1) {
        GL20.glUniform2f(location, v0, v1);
    }

    /**
     * @author FPSFlux
     * @reason Set vec3 uniform
     */
    @Overwrite
    public static void glUniform3f(int location, float v0, float v1, float v2) {
        GL20.glUniform3f(location, v0, v1, v2);
    }

    /**
     * @author FPSFlux
     * @reason Set vec4 uniform
     */
    @Overwrite
    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        GL20.glUniform4f(location, v0, v1, v2, v3);
    }

    /**
     * @author FPSFlux
     * @reason Set matrix4 uniform
     */
    @Overwrite
    public static void glUniformMatrix4(int location, boolean transpose, FloatBuffer matrix) {
        GL20.glUniformMatrix4(location, transpose, matrix);
    }

    /**
     * @author FPSFlux
     * @reason Get attribute location
     */
    @Overwrite
    public static int glGetAttribLocation(int program, CharSequence name) {
        return GL20.glGetAttribLocation(program, name);
    }

    // ========================================================================
    // FRAMEBUFFER OPERATIONS
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached framebuffer binding
     */
    @Overwrite
    public static void glBindFramebuffer(int target, int framebuffer) {
        if (isReady()) {
            OpenGLManager.getFast().bindFramebuffer(target, framebuffer);
        } else {
            GL30.glBindFramebuffer(target, framebuffer);
        }
    }

    /**
     * @author FPSFlux
     * @reason Delete framebuffer
     */
    @Overwrite
    public static void glDeleteFramebuffers(int framebuffer) {
        GL30.glDeleteFramebuffers(framebuffer);
    }

    /**
     * @author FPSFlux
     * @reason Generate framebuffer
     */
    @Overwrite
    public static int glGenFramebuffers() {
        return GL30.glGenFramebuffers();
    }

    /**
     * @author FPSFlux
     * @reason Check framebuffer status
     */
    @Overwrite
    public static int glCheckFramebufferStatus(int target) {
        return GL30.glCheckFramebufferStatus(target);
    }

    /**
     * @author FPSFlux
     * @reason Attach texture to framebuffer
     */
    @Overwrite
    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    // ========================================================================
    // BUFFER OBJECT MANAGEMENT
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached buffer binding
     */
    @Overwrite
    public static void glBindBuffer(int target, int buffer) {
        if (isReady()) {
            OpenGLManager.getFast().bindBuffer(target, buffer);
        } else {
            GL15.glBindBuffer(target, buffer);
        }
    }

    /**
     * @author FPSFlux
     * @reason Generate buffer
     */
    @Overwrite
    public static int glGenBuffers() {
        return GL15.glGenBuffers();
    }

    /**
     * @author FPSFlux
     * @reason Delete buffer
     */
    @Overwrite
    public static void glDeleteBuffers(int buffer) {
        if (isReady()) {
            OpenGLManager.getFast().deleteBuffer(buffer);
        }
        GL15.glDeleteBuffers(buffer);
    }

    /**
     * @author FPSFlux
     * @reason Buffer data upload
     */
    @Overwrite
    public static void glBufferData(int target, java.nio.ByteBuffer data, int usage) {
        GL15.glBufferData(target, data, usage);
    }

    // ========================================================================
    // VERTEX ARRAY OBJECTS
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Cached VAO binding
     */
    @Overwrite
    public static void glBindVertexArray(int array) {
        if (isReady()) {
            OpenGLManager.getFast().bindVertexArray(array);
        } else {
            GL30.glBindVertexArray(array);
        }
    }

    /**
     * @author FPSFlux
     * @reason Generate VAO
     */
    @Overwrite
    public static int glGenVertexArrays() {
        return GL30.glGenVertexArrays();
    }

    /**
     * @author FPSFlux
     * @reason Delete VAO
     */
    @Overwrite
    public static void glDeleteVertexArrays(int array) {
        GL30.glDeleteVertexArrays(array);
    }

    // ========================================================================
    // VERTEX ATTRIBUTES
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Enable vertex attribute
     */
    @Overwrite
    public static void glEnableVertexAttribArray(int index) {
        GL20.glEnableVertexAttribArray(index);
    }

    /**
     * @author FPSFlux
     * @reason Disable vertex attribute
     */
    @Overwrite
    public static void glDisableVertexAttribArray(int index) {
        GL20.glDisableVertexAttribArray(index);
    }

    /**
     * @author FPSFlux
     * @reason Define vertex attribute pointer
     */
    @Overwrite
    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    // ========================================================================
    // STENCIL OPERATIONS
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Enable stencil test
     */
    @Overwrite
    public static void enableStencilTest() {
        enable(GL_STENCIL_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Disable stencil test
     */
    @Overwrite
    public static void disableStencilTest() {
        disable(GL_STENCIL_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Set stencil function
     */
    @Overwrite
    public static void stencilFunc(int func, int ref, int mask) {
        GL11.glStencilFunc(func, ref, mask);
    }

    /**
     * @author FPSFlux
     * @reason Set stencil mask
     */
    @Overwrite
    public static void stencilMask(int mask) {
        GL11.glStencilMask(mask);
    }

    /**
     * @author FPSFlux
     * @reason Set stencil operation
     */
    @Overwrite
    public static void stencilOp(int sfail, int dpfail, int dppass) {
        GL11.glStencilOp(sfail, dpfail, dppass);
    }

    /**
     * @author FPSFlux
     * @reason Clear stencil buffer value
     */
    @Overwrite
    public static void clearStencil(int s) {
        GL11.glClearStencil(s);
    }

    // ========================================================================
    // READING PIXELS
    // ========================================================================

    /**
     * @author FPSFlux
     * @reason Read pixels from framebuffer
     */
    @Overwrite
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }

    /**
     * @author FPSFlux
     * @reason Get error state
     */
    @Overwrite
    public static int glGetError() {
        return GL11.glGetError();
    }

    /**
     * @author FPSFlux
     * @reason Get string parameter
     */
    @Overwrite
    public static String glGetString(int name) {
        return GL11.glGetString(name);
    }

    /**
     * @author FPSFlux
     * @reason Get integer parameter
     */
    @Overwrite
    public static void glGetInteger(int pname, IntBuffer params) {
        GL11.glGetInteger(pname, params);
    }

    // ========================================================================
    // STATE RESET
    // ========================================================================

    /**
     * Resets all locally cached state. Call when GL context changes.
     */
    @Unique
    public static void resetLocalCache() {
        cachedRed = cachedGreen = cachedBlue = cachedAlpha = 1.0f;
        colorStateDirty = true;
        cachedAlphaFunc = GL11.GL_ALWAYS;
        cachedAlphaRef = 0.0f;
        cachedCullMode = GL11.GL_BACK;
        cachedShadeModel = GL11.GL_SMOOTH;
        cachedMatrixMode = GL_MODELVIEW;
    }
}
