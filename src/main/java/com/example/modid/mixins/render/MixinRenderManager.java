package com.example.modid.mixins.render;

import com.example.modid.bridge.BridgeMixinInterface;
import com.example.modid.bridge.MinecraftECSBridge;
import com.example.modid.mixins.util.MixinHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MixinRenderManager - Rendering integration with ECS interpolation.
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Smooth entity rendering via ECS interpolation</li>
 *   <li>Render culling for off-screen/distant entities</li>
 *   <li>LOD-based render quality adjustment</li>
 * </ul>
 */
@Mixin(RenderManager.class)
public abstract class MixinRenderManager {

    // ========================================================================
    // SHADOWS
    // ========================================================================

    @Shadow public double viewerPosX;
    @Shadow public double viewerPosY;
    @Shadow public double viewerPosZ;

    // ========================================================================
    // THREAD-LOCAL BACKUP ARRAYS (Zero allocation in hot path)
    // ========================================================================

    @Unique
    private static final ThreadLocal<double[]> fpsflux$positionBackup = 
        ThreadLocal.withInitial(() -> new double[5]);

    @Unique
    private static final ThreadLocal<Boolean> fpsflux$interpolationActive = 
        ThreadLocal.withInitial(() -> false);

    // ========================================================================
    // RENDER INTERCEPTION
    // ========================================================================

    /**
     * Apply interpolated position before rendering entity.
     */
    @Inject(
        method = "doRenderEntity",
        at = @At("HEAD"),
        cancellable = true
    )
    private void fpsflux$onPreRenderEntity(
            Entity entity, 
            double x, double y, double z, 
            float entityYaw, 
            float partialTicks, 
            boolean hideDebugBox, 
            CallbackInfoReturnable<Boolean> cir) {
        
        if (!(entity instanceof BridgeMixinInterface ext)) return;
        if (!ext.fpsflux$isRegistered()) return;

        // Check render culling
        if (ext.fpsflux$isRenderCulled()) {
            cir.setReturnValue(false);
            return;
        }

        // Apply interpolation
        double[] backup = fpsflux$positionBackup.get();
        boolean applied = MixinHelper.applyInterpolation(entity, partialTicks, backup);
        fpsflux$interpolationActive.set(applied);
    }

    /**
     * Restore original position after rendering.
     */
    @Inject(
        method = "doRenderEntity",
        at = @At("RETURN")
    )
    private void fpsflux$onPostRenderEntity(
            Entity entity,
            double x, double y, double z,
            float entityYaw,
            float partialTicks,
            boolean hideDebugBox,
            CallbackInfoReturnable<Boolean> cir) {
        
        if (fpsflux$interpolationActive.get()) {
            double[] backup = fpsflux$positionBackup.get();
            MixinHelper.restoreFromBackup(entity, backup);
            fpsflux$interpolationActive.set(false);
        }
    }

    /**
     * Alternative hook for renderEntityStatic if present.
     */
    @Inject(
        method = "renderEntityStatic",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void fpsflux$onRenderEntityStatic(
            Entity entity, 
            float partialTicks, 
            boolean hideDebugBox, 
            CallbackInfo ci) {
        
        if (!(entity instanceof BridgeMixinInterface ext)) return;
        if (!ext.fpsflux$isRegistered()) return;

        // Check render culling
        if (ext.fpsflux$isRenderCulled()) {
            ci.cancel();
            return;
        }

        // Apply interpolation for this render
        double[] backup = fpsflux$positionBackup.get();
        boolean applied = MixinHelper.applyInterpolation(entity, partialTicks, backup);
        fpsflux$interpolationActive.set(applied);
    }

    @Inject(
        method = "renderEntityStatic",
        at = @At("RETURN"),
        require = 0
    )
    private void fpsflux$onPostRenderEntityStatic(
            Entity entity,
            float partialTicks,
            boolean hideDebugBox,
            CallbackInfo ci) {
        
        if (fpsflux$interpolationActive.get()) {
            double[] backup = fpsflux$positionBackup.get();
            MixinHelper.restoreFromBackup(entity, backup);
            fpsflux$interpolationActive.set(false);
        }
    }
}
