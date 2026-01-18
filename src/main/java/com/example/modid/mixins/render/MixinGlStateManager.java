package com.example.modid.mixins.render;

import com.example.modid.patcher.UniversalPatcher;
import com.example.modid.patcher.UniversalPatcher.SafePathRegistry;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for GlStateManager - The State Router
 * Routes GL state calls through UniversalPatcher to sync with Vulkan
 */
@Mixin(GlStateManager.class)
public abstract class MixinGlStateManager {

    // ═══════════════════════════════════════════════════════════════════════════
    // CAPABILITY STATE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Route Alpha Test Enable to Vulkan
     */
    @Overwrite
    public static void enableAlpha() {
        UniversalPatcher.execute(SafePathRegistry.Operation.ENABLE_CAPABILITY, 
            SafePathRegistry.GL_ALPHA_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Route Alpha Test Disable to Vulkan
     */
    @Overwrite
    public static void disableAlpha() {
        UniversalPatcher.execute(SafePathRegistry.Operation.DISABLE_CAPABILITY,
            SafePathRegistry.GL_ALPHA_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Route Blend Enable to Vulkan
     */
    @Overwrite
    public static void enableBlend() {
        UniversalPatcher.execute(SafePathRegistry.Operation.ENABLE_CAPABILITY,
            SafePathRegistry.GL_BLEND);
    }

    /**
     * @author FPSFlux
     * @reason Route Blend Disable to Vulkan
     */
    @Overwrite
    public static void disableBlend() {
        UniversalPatcher.execute(SafePathRegistry.Operation.DISABLE_CAPABILITY,
            SafePathRegistry.GL_BLEND);
    }

    /**
     * @author FPSFlux
     * @reason Route Depth Test Enable to Vulkan
     */
    @Overwrite
    public static void enableDepth() {
        UniversalPatcher.execute(SafePathRegistry.Operation.ENABLE_CAPABILITY,
            SafePathRegistry.GL_DEPTH_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Route Depth Test Disable to Vulkan
     */
    @Overwrite
    public static void disableDepth() {
        UniversalPatcher.execute(SafePathRegistry.Operation.DISABLE_CAPABILITY,
            SafePathRegistry.GL_DEPTH_TEST);
    }

    /**
     * @author FPSFlux
     * @reason Route Cull Face Enable to Vulkan
     */
    @Overwrite
    public static void enableCull() {
        UniversalPatcher.execute(SafePathRegistry.Operation.ENABLE_CAPABILITY,
            SafePathRegistry.GL_CULL_FACE);
    }

    /**
     * @author FPSFlux
     * @reason Route Cull Face Disable to Vulkan
     */
    @Overwrite
    public static void disableCull() {
        UniversalPatcher.execute(SafePathRegistry.Operation.DISABLE_CAPABILITY,
            SafePathRegistry.GL_CULL_FACE);
    }

    /**
     * @author FPSFlux
     * @reason Route Fog Enable to Vulkan
     */
    @Overwrite
    public static void enableFog() {
        UniversalPatcher.execute(SafePathRegistry.Operation.ENABLE_CAPABILITY,
            SafePathRegistry.GL_FOG);
    }

    /**
     * @author FPSFlux
     * @reason Route Fog Disable to Vulkan
     */
    @Overwrite
    public static void disableFog() {
        UniversalPatcher.execute(SafePathRegistry.Operation.DISABLE_CAPABILITY,
            SafePathRegistry.GL_FOG);
    }

    /**
     * @author FPSFlux
     * @reason Route Lighting Enable to Vulkan
     */
    @Overwrite
    public static void enableLighting() {
        UniversalPatcher.execute(SafePathRegistry.Operation.ENABLE_CAPABILITY,
            SafePathRegistry.GL_LIGHTING);
    }

    /**
     * @author FPSFlux
     * @reason Route Lighting Disable to Vulkan
     */
    @Overwrite
    public static void disableLighting() {
        UniversalPatcher.execute(SafePathRegistry.Operation.DISABLE_CAPABILITY,
            SafePathRegistry.GL_LIGHTING);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BLEND FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Route Blend Func to Vulkan
     */
    @Overwrite
    public static void blendFunc(int srcFactor, int dstFactor) {
        UniversalPatcher.execute(SafePathRegistry.Operation.BLEND_FUNC, srcFactor, dstFactor);
    }

    /**
     * @author FPSFlux
     * @reason Route Blend Func Separate to Vulkan
     */
    @Overwrite
    public static void tryBlendFuncSeparate(int srcFactor, int dstFactor, 
                                            int srcFactorAlpha, int dstFactorAlpha) {
        UniversalPatcher.execute(SafePathRegistry.Operation.BLEND_FUNC_SEPARATE,
            srcFactor, dstFactor, srcFactorAlpha, dstFactorAlpha);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEPTH FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Route Depth Func to Vulkan
     */
    @Overwrite
    public static void depthFunc(int depthFunc) {
        UniversalPatcher.execute(SafePathRegistry.Operation.DEPTH_FUNC, depthFunc);
    }

    /**
     * @author FPSFlux
     * @reason Route Depth Mask to Vulkan
     */
    @Overwrite
    public static void depthMask(boolean flagIn) {
        UniversalPatcher.execute(SafePathRegistry.Operation.DEPTH_MASK, flagIn ? 1 : 0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COLOR OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Route Color Mask to Vulkan
     */
    @Overwrite
    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        UniversalPatcher.execute(SafePathRegistry.Operation.COLOR_MASK,
            red ? 1 : 0, green ? 1 : 0, blue ? 1 : 0, alpha ? 1 : 0);
    }

    /**
     * @author FPSFlux
     * @reason Route Clear Color to Vulkan
     */
    @Overwrite
    public static void clearColor(float red, float green, float blue, float alpha) {
        UniversalPatcher.executeFloat(SafePathRegistry.Operation.CLEAR_COLOR,
            red, green, blue, alpha);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXTURE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Route Active Texture to Vulkan
     */
    @Overwrite
    public static void setActiveTexture(int texture) {
        UniversalPatcher.execute(SafePathRegistry.Operation.ACTIVE_TEXTURE, texture);
    }

    /**
     * @author FPSFlux
     * @reason Route Bind Texture to Vulkan
     */
    @Overwrite
    public static void bindTexture(int texture) {
        UniversalPatcher.execute(SafePathRegistry.Operation.BIND_TEXTURE, 
            0x0DE1, // GL_TEXTURE_2D
            texture);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VIEWPORT/SCISSOR
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Route Viewport to Vulkan
     */
    @Overwrite
    public static void viewport(int x, int y, int width, int height) {
        UniversalPatcher.execute(SafePathRegistry.Operation.VIEWPORT, x, y, width, height);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POLYGON OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Route Cull Face Mode to Vulkan
     */
    @Overwrite
    public static void cullFace(int mode) {
        UniversalPatcher.execute(SafePathRegistry.Operation.CULL_FACE, mode);
    }

    /**
     * @author FPSFlux
     * @reason Route Polygon Offset to Vulkan
     */
    @Overwrite
    public static void doPolygonOffset(float factor, float units) {
        UniversalPatcher.executeFloat(SafePathRegistry.Operation.POLYGON_OFFSET, factor, units);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLEAR OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Route Clear to Vulkan
     */
    @Overwrite
    public static void clear(int mask) {
        UniversalPatcher.execute(SafePathRegistry.Operation.CLEAR, mask);
    }
}
