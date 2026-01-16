package com.example.modid.bridge;

import com.example.modid.ecs.*;
import com.example.modid.ecs.System;
import net.minecraft.entity.Entity;

import java.lang.foreign.MemorySegment;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Synchronization systems for bidirectional state transfer between
 * Minecraft entities (heap) and ECS components (off-heap).
 */
public final class SyncSystems {
    
    private static final Logger LOGGER = Logger.getLogger(SyncSystems.class.getName());

    private SyncSystems() {}

    // ========================================================================
    // INBOUND SYNC SYSTEM
    // ========================================================================

    /**
     * Reads state from Minecraft entities and writes to ECS components.
     * Ensures ECS has the latest inputs, teleports, and external changes.
     */
    @System.Phase(System.ExecutionPhase.PRE_UPDATE)
    @System.ThreadSafe(strategy = System.ParallelStrategy.ENTITIES)
    @System.RequireComponents({
        BridgeComponents.Transform.class, 
        BridgeComponents.Velocity.class, 
        BridgeComponents.Metadata.class
    })
    public static final class EntityInboundSyncSystem extends System {

        private final MinecraftECSBridge bridge;
        private final AtomicLong syncedEntityCount = new AtomicLong(0);
        private final AtomicLong skippedEntityCount = new AtomicLong(0);

        // Threshold for detecting external teleports (blocks squared)
        private static final double TELEPORT_THRESHOLD_SQ = 64.0; // 8 blocks

        public EntityInboundSyncSystem(MinecraftECSBridge bridge) {
            super("InboundStateSync");
            this.bridge = bridge;
        }

        @Override
        public void update(World world, Archetype archetype, float deltaTime) {
            ComponentArray<BridgeComponents.Transform> transformArray = 
                archetype.getComponentArray(BridgeComponents.Transform.class);
            ComponentArray<BridgeComponents.Velocity> velocityArray = 
                archetype.getComponentArray(BridgeComponents.Velocity.class);
            ComponentArray<BridgeComponents.Metadata> metadataArray = 
                archetype.getComponentArray(BridgeComponents.Metadata.class);

            archetype.forEachEntityIndexed((index, ecsEntity) -> {
                MemorySegment metaSeg = metadataArray.getSegment(index);
                int mcId = BridgeComponents.Metadata.getEntityId(metaSeg);

                Optional<Entity> mcEntityOpt = bridge.getMinecraftEntity(mcId);
                
                if (mcEntityOpt.isEmpty()) {
                    skippedEntityCount.incrementAndGet();
                    return; // Entity was garbage collected or removed
                }

                Entity mcEntity = mcEntityOpt.get();
                MemorySegment transSeg = transformArray.getMutableSegment(index);
                MemorySegment velSeg = velocityArray.getMutableSegment(index);

                // Check for external teleport (large position change)
                double dx = mcEntity.posX - BridgeComponents.Transform.getX(transSeg);
                double dy = mcEntity.posY - BridgeComponents.Transform.getY(transSeg);
                double dz = mcEntity.posZ - BridgeComponents.Transform.getZ(transSeg);
                double distSq = dx * dx + dy * dy + dz * dz;

                if (distSq > TELEPORT_THRESHOLD_SQ) {
                    BridgeComponents.Metadata.setFlag(metaSeg, BridgeComponents.Metadata.FLAG_EXTERNAL_TELEPORT);
                }

                // Sync Position
                BridgeComponents.Transform.setX(transSeg, mcEntity.posX);
                BridgeComponents.Transform.setY(transSeg, mcEntity.posY);
                BridgeComponents.Transform.setZ(transSeg, mcEntity.posZ);
                BridgeComponents.Transform.setYaw(transSeg, mcEntity.rotationYaw);
                BridgeComponents.Transform.setPitch(transSeg, mcEntity.rotationPitch);

                // Sync Velocity
                BridgeComponents.Velocity.setX(velSeg, mcEntity.motionX);
                BridgeComponents.Velocity.setY(velSeg, mcEntity.motionY);
                BridgeComponents.Velocity.setZ(velSeg, mcEntity.motionZ);

                syncedEntityCount.incrementAndGet();
            });
        }

        @Override
        protected void onBeforeUpdate(World world, float deltaTime) {
            syncedEntityCount.set(0);
            skippedEntityCount.set(0);
        }

        public long getSyncedEntityCount() {
            return syncedEntityCount.get();
        }
    }

    // ========================================================================
    // OUTBOUND SYNC SYSTEM
    // ========================================================================

    /**
     * Writes calculated ECS state back to Minecraft entities.
     * Uses dirty flags to minimize unnecessary writes.
     */
    @System.Phase(System.ExecutionPhase.POST_UPDATE)
    @System.ThreadSafe(strategy = System.ParallelStrategy.ENTITIES)
    @System.RequireComponents({
        BridgeComponents.Transform.class, 
        BridgeComponents.Velocity.class, 
        BridgeComponents.Metadata.class,
        BridgeComponents.PreviousTransform.class
    })
    public static final class EntityOutboundSyncSystem extends System {

        private final MinecraftECSBridge bridge;
        private final AtomicLong lastProcessedVersion = new AtomicLong(0);
        private final AtomicLong writtenEntityCount = new AtomicLong(0);

        public EntityOutboundSyncSystem(MinecraftECSBridge bridge) {
            super("OutboundStateSync");
            this.bridge = bridge;
        }

        @Override
        public void update(World world, Archetype archetype, float deltaTime) {
            ComponentArray<BridgeComponents.Transform> transformArray = 
                archetype.getComponentArray(BridgeComponents.Transform.class);
            ComponentArray<BridgeComponents.Velocity> velocityArray = 
                archetype.getComponentArray(BridgeComponents.Velocity.class);
            ComponentArray<BridgeComponents.Metadata> metadataArray = 
                archetype.getComponentArray(BridgeComponents.Metadata.class);
            ComponentArray<BridgeComponents.PreviousTransform> prevTransformArray =
                archetype.getComponentArray(BridgeComponents.PreviousTransform.class);

            long versionThreshold = lastProcessedVersion.get();

            archetype.forEachEntityIndexed((index, ecsEntity) -> {
                MemorySegment metaSeg = metadataArray.getSegment(index);
                
                // Get and clear dirty flags atomically
                int flags = BridgeComponents.Metadata.getAndClearFlags(metaSeg);
                
                boolean transformDirty = (flags & BridgeComponents.Metadata.FLAG_TRANSFORM_DIRTY) != 0;
                boolean velocityDirty = (flags & BridgeComponents.Metadata.FLAG_VELOCITY_DIRTY) != 0;

                // Also check component version for change detection
                if (!transformDirty) {
                    transformDirty = transformArray.hasChangedSince(index, versionThreshold);
                }
                if (!velocityDirty) {
                    velocityDirty = velocityArray.hasChangedSince(index, versionThreshold);
                }

                if (!transformDirty && !velocityDirty) {
                    return; // No changes, skip this entity
                }

                int mcId = BridgeComponents.Metadata.getEntityId(metaSeg);
                Optional<Entity> mcEntityOpt = bridge.getMinecraftEntity(mcId);

                if (mcEntityOpt.isEmpty()) {
                    return;
                }

                Entity mcEntity = mcEntityOpt.get();

                // Store current transform as previous (for next tick's interpolation)
                MemorySegment transSeg = transformArray.getSegment(index);
                MemorySegment prevSeg = prevTransformArray.getMutableSegment(index);
                BridgeComponents.Transform.copy(transSeg, prevSeg);

                // Write back to MC entity
                if (transformDirty) {
                    mcEntity.posX = BridgeComponents.Transform.getX(transSeg);
                    mcEntity.posY = BridgeComponents.Transform.getY(transSeg);
                    mcEntity.posZ = BridgeComponents.Transform.getZ(transSeg);
                    mcEntity.rotationYaw = BridgeComponents.Transform.getYaw(transSeg);
                    mcEntity.rotationPitch = BridgeComponents.Transform.getPitch(transSeg);
                    
                    // Update bounding box (required for collision)
                    mcEntity.setPositionAndRotation(
                        mcEntity.posX, mcEntity.posY, mcEntity.posZ,
                        mcEntity.rotationYaw, mcEntity.rotationPitch
                    );
                }

                if (velocityDirty) {
                    MemorySegment velSeg = velocityArray.getSegment(index);
                    mcEntity.motionX = BridgeComponents.Velocity.getX(velSeg);
                    mcEntity.motionY = BridgeComponents.Velocity.getY(velSeg);
                    mcEntity.motionZ = BridgeComponents.Velocity.getZ(velSeg);
                }

                writtenEntityCount.incrementAndGet();
            });
        }

        @Override
        protected void onAfterUpdate(World world, float deltaTime) {
            lastProcessedVersion.set(world.getStatistics().componentOperations());
        }
    }

    // ========================================================================
    // INTERPOLATION SYSTEM
    // ========================================================================

    /**
     * Interpolates entity positions for smooth rendering between physics ticks.
     */
    @System.Phase(System.ExecutionPhase.RENDER)
    @System.ThreadSafe(strategy = System.ParallelStrategy.SINGLE)
    public static final class InterpolationSystem extends System {

        private final InterpolationBuffer buffer;

        public InterpolationSystem(InterpolationBuffer buffer) {
            super("Interpolation");
            this.buffer = buffer;
        }

        @Override
        public void update(World world, float partialTicks) {
            // Interpolation happens per-entity when queried during rendering
            buffer.setInterpolationFactor(partialTicks);
        }
    }
}
