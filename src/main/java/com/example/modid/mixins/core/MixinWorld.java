package com.example.modid.mixins.core;

import com.example.modid.bridge.BridgeMixinInterface;
import com.example.modid.bridge.MinecraftECSBridge;
import com.example.modid.mixins.util.MixinHelper;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MixinWorld - World-level entity lifecycle management.
 *
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Register entities when they spawn/join</li>
 *   <li>Unregister entities when they're removed</li>
 *   <li>Handle chunk loading/unloading</li>
 *   <li>Batch entity processing hooks</li>
 * </ul>
 */
@Mixin(World.class)
public abstract class MixinWorld {

    @Unique
    private static final Logger fpsflux$LOGGER = Logger.getLogger("FPSFlux-MixinWorld");

    // ========================================================================
    // SHADOWS
    // ========================================================================

    @Shadow public boolean isRemote;
    @Shadow @Final public Profiler theProfiler;
    @Shadow @Final public List<Entity> loadedEntityList;

    // ========================================================================
    // ENTITY SPAWN/JOIN
    // ========================================================================

    /**
     * Register entity with bridge when spawned in world.
     */
    @Inject(
        method = "spawnEntityInWorld",
        at = @At("RETURN")
    )
    private void fpsflux$onSpawnEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // Only process if spawn was successful
        if (!cir.getReturnValue()) return;

        fpsflux$registerEntity(entity, "spawnEntityInWorld");
    }

    /**
     * Register entity when joining via other means (e.g., loading from NBT).
     */
    @Inject(
        method = "onEntityAdded",
        at = @At("RETURN"),
        remap = false, // Forge method
        require = 0
    )
    private void fpsflux$onEntityAdded(Entity entity, CallbackInfo ci) {
        fpsflux$registerEntity(entity, "onEntityAdded");
    }

    /**
     * Common registration logic.
     */
    @Unique
    private void fpsflux$registerEntity(Entity entity, String source) {
        if (!isRemote) return; // Client-side only
        if (entity == null) return;

        // Skip if already registered
        if (entity instanceof BridgeMixinInterface ext) {
            if (ext.fpsflux$isRegistered()) return;
        }

        MinecraftECSBridge bridge = MixinHelper.getBridge();
        if (bridge == null) return;

        try {
            theProfiler.startSection("fpsflux_register");
            int slot = bridge.registerEntity(entity);
            
            if (slot >= 0) {
                fpsflux$LOGGER.log(Level.FINE, 
                    "[FPSFlux] Registered entity {0} (ID: {1}) in slot {2} via {3}",
                    new Object[]{entity.getClass().getSimpleName(), entity.getEntityId(), slot, source}
                );
            }
        } catch (Exception e) {
            fpsflux$LOGGER.log(Level.WARNING, 
                "[FPSFlux] Failed to register entity " + entity.getEntityId(), e);
        } finally {
            theProfiler.endSection();
        }
    }

    // ========================================================================
    // ENTITY REMOVAL
    // ========================================================================

    /**
     * Unregister entity when explicitly removed from world.
     */
    @Inject(
        method = "removeEntity",
        at = @At("HEAD")
    )
    private void fpsflux$onRemoveEntity(Entity entity, CallbackInfo ci) {
        fpsflux$unregisterEntity(entity, "removeEntity");
    }

    /**
     * Unregister entity when removed via other means.
     */
    @Inject(
        method = "onEntityRemoved",
        at = @At("HEAD"),
        remap = false, // Forge method
        require = 0
    )
    private void fpsflux$onEntityRemoved(Entity entity, CallbackInfo ci) {
        fpsflux$unregisterEntity(entity, "onEntityRemoved");
    }

    /**
     * Handle removeEntityDangerously for thorough cleanup.
     */
    @Inject(
        method = "removeEntityDangerously",
        at = @At("HEAD")
    )
    private void fpsflux$onRemoveEntityDangerously(Entity entity, CallbackInfo ci) {
        fpsflux$unregisterEntity(entity, "removeEntityDangerously");
    }

    /**
     * Common unregistration logic.
     */
    @Unique
    private void fpsflux$unregisterEntity(Entity entity, String source) {
        if (!isRemote) return;
        if (entity == null) return;

        // Skip if not registered
        if (entity instanceof BridgeMixinInterface ext) {
            if (!ext.fpsflux$isRegistered()) return;
        } else {
            return;
        }

        MinecraftECSBridge bridge = MixinHelper.getBridge();
        if (bridge == null) return;

        try {
            theProfiler.startSection("fpsflux_unregister");
            bridge.unregisterEntity(entity);
            
            fpsflux$LOGGER.log(Level.FINE,
                "[FPSFlux] Unregistered entity {0} (ID: {1}) via {2}",
                new Object[]{entity.getClass().getSimpleName(), entity.getEntityId(), source}
            );
        } catch (Exception e) {
            fpsflux$LOGGER.log(Level.WARNING,
                "[FPSFlux] Failed to unregister entity " + entity.getEntityId(), e);
        } finally {
            theProfiler.endSection();
        }
    }

    // ========================================================================
    // BATCH UPDATE HOOKS
    // ========================================================================

    /**
     * Pre-tick hook for entity updates.
     * Can be used to prepare ECS state before vanilla updates.
     */
    @Inject(
        method = "updateEntities",
        at = @At("HEAD")
    )
    private void fpsflux$onPreUpdateEntities(CallbackInfo ci) {
        if (!isRemote) return;

        theProfiler.startSection("fpsflux_preUpdate");
        // Any pre-update preparation
        theProfiler.endSection();
    }

    /**
     * Post-tick hook for entity updates.
     * Can be used to sync ECS state after vanilla updates.
     */
    @Inject(
        method = "updateEntities",
        at = @At("RETURN")
    )
    private void fpsflux$onPostUpdateEntities(CallbackInfo ci) {
        if (!isRemote) return;

        theProfiler.startSection("fpsflux_postUpdate");
        // Any post-update synchronization
        theProfiler.endSection();
    }
}
