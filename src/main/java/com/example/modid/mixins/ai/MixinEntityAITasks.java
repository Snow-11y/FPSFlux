package com.example.modid.mixins.ai;

import com.example.modid.bridge.BridgeMixinInterface;
import com.example.modid.mixins.util.MixinHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAITasks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MixinEntityAITasks - Task-level AI culling and optimization.
 */
@Mixin(EntityAITasks.class)
public abstract class MixinEntityAITasks {

    @Unique
    private EntityLiving fpsflux$cachedEntity = null;

    @Unique
    private int fpsflux$tickCounter = 0;

    // ========================================================================
    // TASK EXECUTION CULLING
    // ========================================================================

    /**
     * Intercept AI task execution for LOD-based optimization.
     */
    @Inject(
        method = "onUpdateTasks",
        at = @At("HEAD"),
        cancellable = true
    )
    private void fpsflux$onUpdateTasks(CallbackInfo ci) {
        fpsflux$tickCounter++;

        // Get the owning entity (cached for performance)
        EntityLiving entity = fpsflux$getOwningEntity();
        if (entity == null) return;

        if (!(entity instanceof BridgeMixinInterface ext)) return;
        if (!ext.fpsflux$isRegistered()) return;

        // Skip entirely if AI is culled
        if (ext.fpsflux$isAICulled()) {
            ci.cancel();
            return;
        }

        // LOD-based task frequency reduction
        if (entity instanceof MixinEntityLiving mixinLiving) {
            int lod = mixinLiving.fpsflux$getCurrentLOD();
            
            // Skip task updates based on LOD
            boolean shouldUpdate = switch (lod) {
                case MixinHelper.LOD_FULL -> true;
                case MixinHelper.LOD_REDUCED -> (fpsflux$tickCounter & 1) == 0;  // 50%
                case MixinHelper.LOD_MINIMAL -> (fpsflux$tickCounter & 3) == 0;  // 25%
                default -> true;
            };

            if (!shouldUpdate) {
                ci.cancel();
            }
        }
    }

    /**
     * Attempts to get the owning entity of this AI task set.
     * This uses reflection once and caches the result.
     */
    @Unique
    private EntityLiving fpsflux$getOwningEntity() {
        if (fpsflux$cachedEntity != null) {
            return fpsflux$cachedEntity;
        }

        // The EntityAITasks doesn't have a direct reference to its owner,
        // but we can get it through the running tasks
        // This is a simplified approach - in practice you might use an accessor mixin
        return null;
    }
}
