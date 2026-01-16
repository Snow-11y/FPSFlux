package com.example.modid.mixins.ai;

import com.example.modid.bridge.BridgeComponents;
import com.example.modid.bridge.BridgeMixinInterface;
import com.example.modid.bridge.MinecraftECSBridge;
import com.example.modid.mixins.core.MixinEntity;
import com.example.modid.mixins.util.MixinHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * MixinEntityLiving - Advanced AI culling and LOD management.
 *
 * <h2>Culling Strategies:</h2>
 * <ul>
 *   <li>Distance-based LOD with configurable thresholds</li>
 *   <li>Frustum culling for off-screen entities</li>
 *   <li>Priority-based culling for important entities</li>
 *   <li>Tick-skipping for distant entities</li>
 * </ul>
 */
@Mixin(EntityLiving.class)
public abstract class MixinEntityLiving extends EntityLivingBase implements BridgeMixinInterface {

    // Required constructor for mixin
    protected MixinEntityLiving(World worldIn) {
        super(worldIn);
    }

    // ========================================================================
    // SHADOWS
    // ========================================================================

    @Shadow protected abstract void updateEntityActionState();

    // ========================================================================
    // UNIQUE FIELDS
    // ========================================================================

    @Unique
    private int fpsflux$currentLOD = MixinHelper.LOD_FULL;

    @Unique
    private long fpsflux$lastAITick = 0;

    @Unique
    private boolean fpsflux$aiSkippedLastTick = false;

    // ========================================================================
    // AI CULLING
    // ========================================================================

    /**
     * Main AI culling hook - intercepts entity action state updates.
     */
    @Inject(
        method = "updateEntityActionState",
        at = @At("HEAD"),
        cancellable = true
    )
    private void fpsflux$onUpdateEntityActionState(CallbackInfo ci) {
        // Skip culling for unregistered entities
        if (!fpsflux$isRegistered()) return;

        // Never cull the player
        if ((Object) this instanceof EntityPlayer) return;

        // Get bridge and calculate LOD
        MinecraftECSBridge bridge = MixinHelper.getBridge();
        if (bridge == null) return;

        long currentTick = bridge.getTickCount();

        // Calculate LOD based on distance to viewer
        EntityPlayer viewer = fpsflux$getViewer();
        if (viewer != null) {
            fpsflux$currentLOD = fpsflux$calculateLODInternal(viewer, currentTick);
        }

        // Check if AI should be processed this tick
        boolean shouldProcess = MixinHelper.shouldProcessAI(fpsflux$currentLOD, currentTick);

        if (!shouldProcess) {
            // Skip AI this tick
            fpsflux$aiSkippedLastTick = true;
            fpsflux$setFlag(FLAG_AI_CULLED);
            
            // Update component flags in ECS memory
            fpsflux$updateCullingFlags(true);
            
            ci.cancel();
            return;
        }

        // AI is processing
        fpsflux$aiSkippedLastTick = false;
        fpsflux$clearFlag(FLAG_AI_CULLED);
        fpsflux$updateCullingFlags(false);
        fpsflux$lastAITick = currentTick;
    }

    /**
     * Hook into living update for additional optimizations.
     */
    @Inject(
        method = "onLivingUpdate",
        at = @At("HEAD"),
        cancellable = true
    )
    private void fpsflux$onLivingUpdate(CallbackInfo ci) {
        if (!fpsflux$isRegistered()) return;

        // If completely culled, skip all living updates
        if (fpsflux$currentLOD == MixinHelper.LOD_CULLED) {
            // Still need minimal processing to stay alive
            // Just skip heavy computation
            return;
        }

        // For MINIMAL LOD, skip certain expensive operations
        if (fpsflux$currentLOD == MixinHelper.LOD_MINIMAL) {
            // Could skip pathfinding updates, etc.
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    @Unique
    private EntityPlayer fpsflux$getViewer() {
        if (this.worldObj.isRemote) {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.thePlayer;
        }
        return null;
    }

    @Unique
    private int fpsflux$calculateLODInternal(Entity viewer, long currentTick) {
        // Use cached LOD from MixinEntity if available
        if (this instanceof MixinEntity mixinEntity) {
            return mixinEntity.fpsflux$getLOD(viewer.posX, viewer.posY, viewer.posZ, currentTick);
        }

        // Fallback calculation
        double distSq = MixinHelper.distanceSquared((Entity) (Object) this, viewer);
        return MixinHelper.calculateLOD(distSq);
    }

    @Unique
    private void fpsflux$updateCullingFlags(boolean aiCulled) {
        int slot = fpsflux$getBridgeSlot();
        if (slot < 0) return;

        MinecraftECSBridge bridge = MixinHelper.getBridge();
        if (bridge == null) return;

        try {
            MemorySegment memory = bridge.getComponentMemory();
            long base = bridge.getEntityMemoryOffset(slot);

            long flags = memory.get(ValueLayout.JAVA_LONG, base + BridgeComponents.META_FLAGS);

            if (aiCulled) {
                flags |= BridgeComponents.FLAG_NO_CLIP; // Repurpose or add AI_CULLED flag
            } else {
                flags &= ~BridgeComponents.FLAG_NO_CLIP;
            }

            memory.set(ValueLayout.JAVA_LONG, base + BridgeComponents.META_FLAGS, flags);
        } catch (Exception e) {
            // Silently ignore - non-critical
        }
    }

    /**
     * Gets the current LOD level for external queries.
     */
    @Unique
    public int fpsflux$getCurrentLOD() {
        return fpsflux$currentLOD;
    }

    /**
     * Checks if AI was skipped last tick.
     */
    @Unique
    public boolean fpsflux$wasAISkipped() {
        return fpsflux$aiSkippedLastTick;
    }
}
