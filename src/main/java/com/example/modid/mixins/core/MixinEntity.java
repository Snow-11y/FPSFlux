package com.example.modid.mixins.core;

import com.example.modid.bridge.BridgeMixinInterface;
import com.example.modid.bridge.MinecraftECSBridge;
import com.example.modid.ecs.Entity;
import com.example.modid.mixins.util.MixinHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MixinEntity - Core entity extension implementing BridgeMixinInterface.
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Stores ECS slot and entity handle directly in MC entity</li>
 *   <li>Hooks entity lifecycle events (death, teleport)</li>
 *   <li>Provides fast O(1) access to ECS data</li>
 *   <li>Thread-safe field access for render thread</li>
 * </ul>
 */
@Mixin(net.minecraft.entity.Entity.class)
public abstract class MixinEntity implements BridgeMixinInterface {

    @Unique
    private static final Logger fpsflux$LOGGER = Logger.getLogger("FPSFlux-MixinEntity");

    // ========================================================================
    // SHADOWS
    // ========================================================================

    @Shadow public int entityId;
    @Shadow public World worldObj;
    @Shadow public double posX;
    @Shadow public double posY;
    @Shadow public double posZ;
    @Shadow public double prevPosX;
    @Shadow public double prevPosY;
    @Shadow public double prevPosZ;
    @Shadow public float rotationYaw;
    @Shadow public float rotationPitch;
    @Shadow public double motionX;
    @Shadow public double motionY;
    @Shadow public double motionZ;
    @Shadow public boolean isDead;

    // ========================================================================
    // BRIDGE INTERFACE FIELDS (Volatile for cross-thread visibility)
    // ========================================================================

    /**
     * Slot index in the bridge's entity array.
     * -1 means not registered.
     */
    @Unique
    private volatile int fpsflux$bridgeSlot = -1;

    /**
     * Direct reference to ECS entity.
     * Null means not registered.
     */
    @Unique
    private volatile Entity fpsflux$ecsEntity = null;

    /**
     * Internal state flags.
     * Uses volatile for cross-thread visibility.
     */
    @Unique
    private volatile int fpsflux$flags = 0;

    /**
     * Cached LOD level to avoid recalculation.
     */
    @Unique
    private int fpsflux$lodLevel = MixinHelper.LOD_FULL;

    /**
     * Last tick when LOD was calculated.
     */
    @Unique
    private long fpsflux$lodCalculationTick = 0;

    // ========================================================================
    // BRIDGE INTERFACE IMPLEMENTATION
    // ========================================================================

    @Override
    public void fpsflux$setBridgeSlot(int slot) {
        this.fpsflux$bridgeSlot = slot;
        if (slot >= 0) {
            fpsflux$setFlag(FLAG_REGISTERED);
        } else {
            fpsflux$clearFlag(FLAG_REGISTERED);
        }
    }

    @Override
    public int fpsflux$getBridgeSlot() {
        return this.fpsflux$bridgeSlot;
    }

    @Override
    public void fpsflux$setEcsEntity(Entity entity) {
        this.fpsflux$ecsEntity = entity;
    }

    @Override
    public Entity fpsflux$getEcsEntity() {
        return this.fpsflux$ecsEntity;
    }

    @Override
    public void fpsflux$setFlags(int flags) {
        this.fpsflux$flags = flags;
    }

    @Override
    public int fpsflux$getFlags() {
        return this.fpsflux$flags;
    }

    // ========================================================================
    // LIFECYCLE HOOKS
    // ========================================================================

    /**
     * Hook entity death to clean up ECS resources.
     */
    @Inject(
        method = "setDead",
        at = @At("HEAD")
    )
    private void fpsflux$onSetDead(CallbackInfo ci) {
        fpsflux$unregisterFromBridge("setDead");
    }

    /**
     * Hook entity removal for cleanup.
     */
    @Inject(
        method = "onRemovedFromWorld",
        at = @At("HEAD"),
        remap = false, // Forge method
        require = 0    // Optional - may not exist
    )
    private void fpsflux$onRemovedFromWorld(CallbackInfo ci) {
        fpsflux$unregisterFromBridge("onRemovedFromWorld");
    }

    /**
     * Common unregistration logic.
     */
    @Unique
    private void fpsflux$unregisterFromBridge(String source) {
        if (fpsflux$bridgeSlot < 0) return;

        try {
            MinecraftECSBridge bridge = MixinHelper.getBridge();
            if (bridge != null) {
                bridge.unregisterEntity((net.minecraft.entity.Entity) (Object) this);
            }
        } catch (Exception e) {
            fpsflux$LOGGER.log(Level.FINE, "[FPSFlux] Error during unregister from " + source, e);
        } finally {
            fpsflux$bridgeSlot = -1;
            fpsflux$ecsEntity = null;
            fpsflux$flags = 0;
        }
    }

    // ========================================================================
    // POSITION CHANGE DETECTION
    // ========================================================================

    /**
     * Detect significant position changes (teleports).
     */
    @Inject(
        method = "setPosition",
        at = @At("HEAD")
    )
    private void fpsflux$onSetPosition(double x, double y, double z, CallbackInfo ci) {
        if (fpsflux$bridgeSlot < 0) return;

        // Check for teleport (large position change)
        double dx = x - posX;
        double dy = y - posY;
        double dz = z - posZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        // If distance > 8 blocks, consider it a teleport
        if (distSq > 64.0) {
            fpsflux$setFlag(FLAG_TELEPORTED);
            fpsflux$setFlag(FLAG_DIRTY_INBOUND);
        }
    }

    /**
     * Detect position changes via setPositionAndRotation.
     */
    @Inject(
        method = "setPositionAndRotation",
        at = @At("HEAD")
    )
    private void fpsflux$onSetPositionAndRotation(double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        if (fpsflux$bridgeSlot < 0) return;

        double dx = x - posX;
        double dy = y - posY;
        double dz = z - posZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > 64.0) {
            fpsflux$setFlag(FLAG_TELEPORTED);
            fpsflux$setFlag(FLAG_DIRTY_INBOUND);
        }
    }

    // ========================================================================
    // MOVEMENT INTEGRATION
    // ========================================================================

    /**
     * Optional: Intercept vanilla movement when ECS controls physics.
     */
    @Inject(
        method = "moveEntity",
        at = @At("HEAD"),
        cancellable = true
    )
    private void fpsflux$onMoveEntity(double x, double y, double z, CallbackInfo ci) {
        // If ECS manages physics for this entity, we might want to skip vanilla movement
        if (fpsflux$hasFlag(FLAG_ECS_PHYSICS)) {
            // Option 1: Cancel vanilla movement entirely
            // ci.cancel();
            
            // Option 2: Let vanilla handle collision, then override result
            // (handled in post-move hook)
        }
    }

    // ========================================================================
    // LOD MANAGEMENT
    // ========================================================================

    /**
     * Gets cached LOD level, recalculating if stale.
     *
     * @param viewerX viewer X position
     * @param viewerY viewer Y position
     * @param viewerZ viewer Z position
     * @param currentTick current game tick
     * @return LOD level
     */
    @Unique
    public int fpsflux$getLOD(double viewerX, double viewerY, double viewerZ, long currentTick) {
        // Recalculate LOD every 10 ticks
        if (currentTick - fpsflux$lodCalculationTick >= 10) {
            double distSq = MixinHelper.distanceSquared(
                (net.minecraft.entity.Entity) (Object) this, 
                viewerX, viewerY, viewerZ
            );
            fpsflux$lodLevel = MixinHelper.calculateLOD(distSq);
            fpsflux$lodCalculationTick = currentTick;
        }
        return fpsflux$lodLevel;
    }

    /**
     * Force LOD recalculation next access.
     */
    @Unique
    public void fpsflux$invalidateLOD() {
        fpsflux$lodCalculationTick = 0;
    }
}
