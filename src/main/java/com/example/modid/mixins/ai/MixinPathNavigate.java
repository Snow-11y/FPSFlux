package com.example.modid.mixins.ai;

import com.example.modid.bridge.BridgeMixinInterface;
import com.example.modid.mixins.util.MixinHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityMoveHelper;
import net.minecraft.pathfinding.PathNavigate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MixinPathNavigate - Pathfinding optimization via LOD culling.
 *
 * <h2>Optimizations:</h2>
 * <ul>
 *   <li>Skip pathfinding for culled entities</li>
 *   <li>Reduce pathfinding frequency for distant entities</li>
 *   <li>Use simplified paths for MINIMAL LOD</li>
 * </ul>
 */
@Mixin(PathNavigate.class)
public abstract class MixinPathNavigate {

    @Shadow @Final protected EntityLiving theEntity;
    @Shadow protected int totalTicks;

    @Unique
    private long fpsflux$lastPathfindTick = 0;

    // ========================================================================
    // PATHFINDING CULLING
    // ========================================================================

    /**
     * Intercept path updates for LOD-based culling.
     */
    @Inject(
        method = "onUpdateNavigation",
        at = @At("HEAD"),
        cancellable = true
    )
    private void fpsflux$onUpdateNavigation(CallbackInfo ci) {
        if (!(theEntity instanceof BridgeMixinInterface ext)) return;
        if (!ext.fpsflux$isRegistered()) return;

        // Skip if AI is culled
        if (ext.fpsflux$isAICulled()) {
            ci.cancel();
            return;
        }

        // Check LOD for pathfinding frequency
        if (theEntity instanceof MixinEntityLiving mixinLiving) {
            int lod = mixinLiving.fpsflux$getCurrentLOD();
            
            // Reduce pathfinding frequency based on LOD
            int pathfindInterval = switch (lod) {
                case MixinHelper.LOD_FULL -> 1;      // Every tick
                case MixinHelper.LOD_REDUCED -> 4;   // Every 4 ticks
                case MixinHelper.LOD_MINIMAL -> 10;  // Every 10 ticks
                default -> 1;
            };

            if ((totalTicks % pathfindInterval) != 0) {
                ci.cancel();
            }
        }
    }

    /**
     * Optimize path-to-entity calculations.
     */
    @Inject(
        method = "tryMoveToEntityLiving",
        at = @At("HEAD"),
        cancellable = true
    )
    private void fpsflux$onTryMoveToEntity(EntityLiving target, double speed, CallbackInfoReturnable<Boolean> cir) {
        if (!(theEntity instanceof BridgeMixinInterface ext)) return;
        if (!ext.fpsflux$isRegistered()) return;

        // Skip expensive pathfinding if AI is culled
        if (ext.fpsflux$isAICulled()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Optimize path-to-position calculations.
     */
    @Inject(
        method = "tryMoveToXYZ",
        at = @At("HEAD"),
        cancellable = true
    )
    private void fpsflux$onTryMoveToXYZ(double x, double y, double z, double speed, CallbackInfoReturnable<Boolean> cir) {
        if (!(theEntity instanceof BridgeMixinInterface ext)) return;
        if (!ext.fpsflux$isRegistered()) return;

        if (ext.fpsflux$isAICulled()) {
            cir.setReturnValue(false);
        }
    }
}
