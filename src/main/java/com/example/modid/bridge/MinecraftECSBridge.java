package com.example.modid.bridge;

import com.example.modid.ecs.*;
import com.example.modid.ecs.System;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * MinecraftECSBridge - High-performance bridge between Minecraft's OOP entities
 * and a Data-Oriented ECS framework.
 *
 * <h2>Architecture Features:</h2>
 * <ul>
 *   <li><b>Foreign Memory Mapping:</b> Zero-copy access via Panama FFM where possible</li>
 *   <li><b>Lifecycle Synchronization:</b> Atomic state tracking with proper memory ordering</li>
 *   <li><b>Thread Confinement:</b> Managed transition between MC Main Thread and ECS Virtual Thread Pool</li>
 *   <li><b>Double Buffering:</b> State interpolation for smooth rendering during async physics</li>
 *   <li><b>Circuit Breaker:</b> Automatic degradation on repeated failures</li>
 * </ul>
 *
 * <h2>Thread Safety Guarantees:</h2>
 * <ul>
 *   <li>Entity registration/deregistration is lock-free using CAS operations</li>
 *   <li>Memory segments are confined to their owning arena's thread scope</li>
 *   <li>State transitions use atomic operations with proper happens-before ordering</li>
 * </ul>
 *
 * @author Enhanced ECS Framework
 * @version 4.0.0-J21
 */
public final class MinecraftECSBridge implements AutoCloseable {

    // ========================================================================
    // LOGGING & DIAGNOSTICS
    // ========================================================================
    
    private static final Logger LOGGER = Logger.getLogger(MinecraftECSBridge.class.getName());

    // ========================================================================
    // SINGLETON (Thread-Safe Lazy Initialization via Holder Pattern)
    // ========================================================================

    private static final class InstanceHolder {
        private static final MinecraftECSBridge INSTANCE = new MinecraftECSBridge();
    }

    public static MinecraftECSBridge getInstance() {
        return InstanceHolder.INSTANCE;
    }

    // ========================================================================
    // BRIDGE STATE MACHINE
    // ========================================================================

    /**
     * Represents the lifecycle states of the bridge.
     */
    public enum BridgeState {
        UNINITIALIZED,
        INITIALIZING,
        RUNNING,
        PAUSED,
        SHUTTING_DOWN,
        TERMINATED
    }

    private final AtomicReference<BridgeState> state = new AtomicReference<>(BridgeState.UNINITIALIZED);

    // ========================================================================
    // CORE STATE
    // ========================================================================

    private volatile com.example.modid.ecs.World ecsWorld;
    
    // Performance Metrics (using VarHandle for non-contended atomic access)
    private static final VarHandle LAST_TICK_TIME;
    private static final VarHandle FRAME_ACCUMULATOR;
    private static final VarHandle TICK_COUNT;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            LAST_TICK_TIME = lookup.findVarHandle(MinecraftECSBridge.class, "lastTickTimeNanos", long.class);
            FRAME_ACCUMULATOR = lookup.findVarHandle(MinecraftECSBridge.class, "frameAccumulatorNanos", long.class);
            TICK_COUNT = lookup.findVarHandle(MinecraftECSBridge.class, "tickCount", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    @SuppressWarnings("unused") // Accessed via VarHandle
    private volatile long lastTickTimeNanos = 0;
    @SuppressWarnings("unused")
    private volatile long frameAccumulatorNanos = 0;
    @SuppressWarnings("unused")
    private volatile long tickCount = 0;

    // ========================================================================
    // ENTITY MAPPING (Lock-Free, High Concurrency)
    // ========================================================================

    /**
     * Represents a tracked entity with its associated resources.
     * Using a record for immutability and clear data semantics.
     */
    public record TrackedEntity(
        com.example.modid.ecs.Entity ecsEntity,
        MemorySegment componentMemory,
        Arena entityArena,
        long creationTick
    ) implements AutoCloseable {
        
        public TrackedEntity {
            Objects.requireNonNull(ecsEntity, "ecsEntity cannot be null");
            Objects.requireNonNull(componentMemory, "componentMemory cannot be null");
            Objects.requireNonNull(entityArena, "entityArena cannot be null");
        }
        
        @Override
        public void close() {
            if (entityArena.scope().isAlive()) {
                entityArena.close();
            }
        }
    }

    // Primary mapping: MC Entity ID -> Tracked Entity (with all resources)
    private final ConcurrentHashMap<Integer, TrackedEntity> entityRegistry = new ConcurrentHashMap<>(4096);
    
    // Weak reference cache for MC entities (allows GC, rebuilt on access)
    private final ConcurrentHashMap<Integer, java.lang.ref.WeakReference<Entity>> mcEntityCache = 
        new ConcurrentHashMap<>(4096);

    // ========================================================================
    // CIRCUIT BREAKER (Prevents Cascading Failures)
    // ========================================================================

    private final CircuitBreaker circuitBreaker = new CircuitBreaker(
        5,      // failure threshold
        30_000, // reset timeout ms
        "ECS-Bridge"
    );

    // ========================================================================
    // INTERPOLATION DOUBLE BUFFER
    // ========================================================================

    private final InterpolationBuffer interpolationBuffer = new InterpolationBuffer();

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    private MinecraftECSBridge() {
        // Register shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                close();
            } catch (Exception e) {
                LOGGER.severe("Error during shutdown: " + e.getMessage());
            }
        }, "ECS-Bridge-Shutdown"));
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initializes the ECS world and registers core bridge systems.
     * Must be called during Minecraft post-initialization phase (main thread).
     *
     * @throws IllegalStateException if already initialized or in invalid state
     */
    public void initialize() {
        if (!state.compareAndSet(BridgeState.UNINITIALIZED, BridgeState.INITIALIZING)) {
            BridgeState current = state.get();
            if (current == BridgeState.RUNNING) {
                LOGGER.fine("Bridge already initialized, skipping");
                return;
            }
            throw new IllegalStateException("Cannot initialize from state: " + current);
        }

        try {
            LOGGER.info("[ECS-Bridge] Initializing High-Performance Bridge Layer...");

            // 1. Configure the ECS World with validated parameters
            int parallelism = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
            
            com.example.modid.ecs.World.Config config = com.example.modid.ecs.World.Config.builder("Minecraft-Client-World")
                .maxEntities(100_000)
                .parallelism(parallelism)
                .useVirtualThreads(true)
                .useOffHeapStorage(true)
                .enableChangeDetection(true)
                .memoryBudgetMB(256) // Limit memory usage
                .build();

            this.ecsWorld = new com.example.modid.ecs.World(config);

            // 2. Register Synchronization Systems in correct order
            registerCoreSystems();

            // 3. Initialize the World
            ecsWorld.initialize();
            
            // 4. Transition to running state
            state.set(BridgeState.RUNNING);
            
            LOGGER.info("[ECS-Bridge] Initialization Complete. World ID: " + 
                       java.lang.System.identityHashCode(ecsWorld));

        } catch (Exception e) {
            state.set(BridgeState.TERMINATED);
            LOGGER.severe("[ECS-Bridge] Initialization failed: " + e.getMessage());
            throw new RuntimeException("ECS Bridge initialization failed", e);
        }
    }

    private void registerCoreSystems() {
        // PRE_UPDATE: Read from MC entities into ECS components
        ecsWorld.registerSystem(
            new EntityInboundSyncSystem(this), 
            SystemScheduler.Stage.PRE_UPDATE
        );
        
        // UPDATE: Heavy computation systems would be registered here by users
        // e.g., BoidFlockingSystem, PathfindingSystem, PhysicsSystem
        
        // POST_UPDATE: Write ECS results back to MC entities
        ecsWorld.registerSystem(
            new EntityOutboundSyncSystem(this), 
            SystemScheduler.Stage.POST_UPDATE
        );
        
        // RENDER: Interpolation for smooth visuals
        ecsWorld.registerSystem(
            new InterpolationSystem(interpolationBuffer), 
            SystemScheduler.Stage.RENDER
        );
    }

    // ========================================================================
    // GAME LOOP HOOKS
    // ========================================================================

    /**
     * Main tick method, called from MixinMinecraft on the client thread.
     * Delegates processing to the ECS scheduler.
     *
     * @param partialTicks the partial tick time for interpolation
     */
    public void onClientTick(float partialTicks) {
        if (!isOperational()) return;

        // Circuit breaker check
        if (!circuitBreaker.allowRequest()) {
            LOGGER.warning("[ECS-Bridge] Circuit breaker OPEN - skipping tick");
            return;
        }

        long now = java.lang.System.nanoTime();
        long last = (long) LAST_TICK_TIME.getAndSet(this, now);
        
        // Skip first tick (no delta available)
        if (last == 0) return;

        float deltaTime = (now - last) / 1_000_000_000.0f;
        
        // Clamp delta to prevent spiral of death
        deltaTime = Math.min(deltaTime, 0.1f); // Max 100ms step

        try {
            // 1. Process deferred commands (entity creation/destruction)
            ecsWorld.getCommandBuffer().execute(ecsWorld);

            // 2. Store previous state for interpolation
            interpolationBuffer.captureState(ecsWorld);

            // 3. Execute ECS update pipeline
            ecsWorld.update(deltaTime);
            
            // 4. Track metrics
            TICK_COUNT.getAndAdd(this, 1L);
            circuitBreaker.recordSuccess();

        } catch (Exception e) {
            circuitBreaker.recordFailure();
            LOGGER.severe("[ECS-Bridge] Tick failure: " + e.getMessage());
            
            if (circuitBreaker.isOpen()) {
                LOGGER.severe("[ECS-Bridge] Circuit breaker opened due to repeated failures");
            }
        }
    }

    /**
     * Called during the render phase for state interpolation.
     *
     * @param partialTicks interpolation factor [0, 1)
     */
    public void onRenderTick(float partialTicks) {
        if (!isOperational()) return;

        try {
            ecsWorld.executeStage(SystemScheduler.Stage.RENDER, partialTicks);
        } catch (Exception e) {
            LOGGER.warning("[ECS-Bridge] Render tick error: " + e.getMessage());
        }
    }

    // ========================================================================
    // ENTITY LIFECYCLE HOOKS
    // ========================================================================

    /**
     * Called when an entity joins the world.
     * Creates a corresponding ECS entity with all required components.
     *
     * @param mcEntity the Minecraft entity that joined
     * @throws NullPointerException if mcEntity is null
     */
    public void onEntityJoin(Entity mcEntity) {
        Objects.requireNonNull(mcEntity, "mcEntity cannot be null");
        
        if (!isOperational()) return;

        int entityId = mcEntity.getEntityId();

        // Atomic registration using computeIfAbsent (lock-free for existing keys)
        TrackedEntity tracked = entityRegistry.computeIfAbsent(entityId, id -> {
            try {
                return createTrackedEntity(mcEntity);
            } catch (Exception e) {
                LOGGER.severe("Failed to create tracked entity for " + id + ": " + e.getMessage());
                return null;
            }
        });

        if (tracked == null) {
            LOGGER.warning("Entity registration failed for ID: " + entityId);
            return;
        }

        // Update weak reference cache
        mcEntityCache.put(entityId, new java.lang.ref.WeakReference<>(mcEntity));

        // Link MC entity to ECS entity via mixin interface
        if (mcEntity instanceof IMixinEntityExtension ext) {
            ext.fpsFlux$setEcsHandle(tracked.ecsEntity());
        }
    }

    /**
     * Called when an entity leaves the world.
     * Schedules ECS entity destruction and cleans up resources.
     *
     * @param mcEntity the Minecraft entity that left
     */
    public void onEntityLeave(Entity mcEntity) {
        if (mcEntity == null || !isOperational()) return;

        int entityId = mcEntity.getEntityId();
        
        // Atomically remove and get the tracked entity
        TrackedEntity tracked = entityRegistry.remove(entityId);
        mcEntityCache.remove(entityId);

        if (tracked != null) {
            // Clear the ECS handle on the MC entity
            if (mcEntity instanceof IMixinEntityExtension ext) {
                ext.fpsFlux$setEcsHandle(null);
            }
            
            // Schedule deferred destruction (thread-safe)
            ecsWorld.destroyEntityDeferred(tracked.ecsEntity());
            
            // Close the entity's memory arena (releases off-heap memory)
            tracked.close();
        }
    }

    // ========================================================================
    // TRACKED ENTITY CREATION
    // ========================================================================

    private TrackedEntity createTrackedEntity(Entity mcEntity) {
        // Each entity gets its own confined arena for proper cleanup
        Arena entityArena = Arena.ofConfined();
        
        try {
            // Calculate total memory needed for all components
            long totalSize = BridgeComponents.Transform.LAYOUT.byteSize() +
                            BridgeComponents.Velocity.LAYOUT.byteSize() +
                            BridgeComponents.Metadata.LAYOUT.byteSize() +
                            BridgeComponents.PreviousTransform.LAYOUT.byteSize();
            
            // Allocate a single contiguous block (better cache locality)
            MemorySegment componentMemory = entityArena.allocate(totalSize, 64);
            
            // Create ECS entity
            com.example.modid.ecs.Entity ecsEntity = ecsWorld.createEntity();
            
            // Initialize all components
            initializeComponents(ecsEntity, mcEntity, componentMemory);
            
            long currentTick = (long) TICK_COUNT.get(this);
            
            return new TrackedEntity(ecsEntity, componentMemory, entityArena, currentTick);
            
        } catch (Exception e) {
            // Cleanup on failure
            entityArena.close();
            throw e;
        }
    }

    private void initializeComponents(
            com.example.modid.ecs.Entity ecsEntity, 
            Entity mcEntity,
            MemorySegment memory) {
        
        long offset = 0;
        
        // 1. Transform Component
        MemorySegment transformSeg = memory.asSlice(offset, BridgeComponents.Transform.LAYOUT.byteSize());
        BridgeComponents.Transform.initialize(transformSeg, mcEntity);
        ecsWorld.addComponent(ecsEntity, BridgeComponents.Transform.class, transformSeg);
        offset += BridgeComponents.Transform.LAYOUT.byteSize();
        
        // 2. Velocity Component
        MemorySegment velocitySeg = memory.asSlice(offset, BridgeComponents.Velocity.LAYOUT.byteSize());
        BridgeComponents.Velocity.initialize(velocitySeg, mcEntity);
        ecsWorld.addComponent(ecsEntity, BridgeComponents.Velocity.class, velocitySeg);
        offset += BridgeComponents.Velocity.LAYOUT.byteSize();
        
        // 3. Metadata Component
        MemorySegment metadataSeg = memory.asSlice(offset, BridgeComponents.Metadata.LAYOUT.byteSize());
        BridgeComponents.Metadata.initialize(metadataSeg, mcEntity.getEntityId());
        ecsWorld.addComponent(ecsEntity, BridgeComponents.Metadata.class, metadataSeg);
        offset += BridgeComponents.Metadata.LAYOUT.byteSize();
        
        // 4. Previous Transform (for interpolation)
        MemorySegment prevTransformSeg = memory.asSlice(offset, BridgeComponents.PreviousTransform.LAYOUT.byteSize());
        BridgeComponents.PreviousTransform.initialize(prevTransformSeg, mcEntity);
        ecsWorld.addComponent(ecsEntity, BridgeComponents.PreviousTransform.class, prevTransformSeg);
    }

    // ========================================================================
    // ACCESSORS
    // ========================================================================

    /**
     * Gets the ECS entity for a Minecraft entity ID.
     *
     * @param mcEntityId the Minecraft entity ID
     * @return Optional containing the ECS entity, or empty if not found
     */
    public Optional<com.example.modid.ecs.Entity> getEcsEntity(int mcEntityId) {
        TrackedEntity tracked = entityRegistry.get(mcEntityId);
        return tracked != null ? Optional.of(tracked.ecsEntity()) : Optional.empty();
    }

    /**
     * Gets the Minecraft entity for an ID, if still alive.
     *
     * @param mcEntityId the Minecraft entity ID
     * @return Optional containing the MC entity, or empty if not found/collected
     */
    public Optional<Entity> getMinecraftEntity(int mcEntityId) {
        var ref = mcEntityCache.get(mcEntityId);
        if (ref == null) return Optional.empty();
        
        Entity entity = ref.get();
        if (entity == null) {
            // Reference was collected, clean up
            mcEntityCache.remove(mcEntityId);
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    /**
     * @return true if the bridge is operational (initialized and running)
     */
    public boolean isOperational() {
        BridgeState s = state.get();
        return s == BridgeState.RUNNING || s == BridgeState.PAUSED;
    }

    /**
     * @return current bridge state
     */
    public BridgeState getState() {
        return state.get();
    }

    /**
     * @return the underlying ECS world, or null if not initialized
     */
    public com.example.modid.ecs.World getEcsWorld() {
        return ecsWorld;
    }

    // ========================================================================
    // METRICS
    // ========================================================================

    public record BridgeMetrics(
        long tickCount,
        int trackedEntities,
        long lastTickDurationNanos,
        boolean circuitBreakerOpen,
        BridgeState state
    ) {}

    public BridgeMetrics getMetrics() {
        return new BridgeMetrics(
            (long) TICK_COUNT.get(this),
            entityRegistry.size(),
            (long) FRAME_ACCUMULATOR.get(this),
            circuitBreaker.isOpen(),
            state.get()
        );
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    /**
     * Pauses ECS processing while keeping state intact.
     */
    public void pause() {
        state.compareAndSet(BridgeState.RUNNING, BridgeState.PAUSED);
    }

    /**
     * Resumes ECS processing after a pause.
     */
    public void resume() {
        state.compareAndSet(BridgeState.PAUSED, BridgeState.RUNNING);
    }

    @Override
    public void close() {
        BridgeState previous = state.getAndSet(BridgeState.SHUTTING_DOWN);
        if (previous == BridgeState.SHUTTING_DOWN || previous == BridgeState.TERMINATED) {
            return;
        }

        LOGGER.info("[ECS-Bridge] Shutting down...");

        try {
            // 1. Shutdown ECS world
            if (ecsWorld != null) {
                ecsWorld.shutdown();
            }

            // 2. Close all entity arenas (releases off-heap memory)
            entityRegistry.values().forEach(TrackedEntity::close);
            entityRegistry.clear();
            mcEntityCache.clear();

            LOGGER.info("[ECS-Bridge] Shutdown complete");

        } finally {
            state.set(BridgeState.TERMINATED);
        }
    }

    // ========================================================================
    // MIXIN INTERFACE
    // ========================================================================

    /**
     * Interface to be injected into net.minecraft.entity.Entity via Mixin.
     * Provides bidirectional linking between MC entities and ECS entities.
     */
    public interface IMixinEntityExtension {
        void fpsFlux$setEcsHandle(com.example.modid.ecs.Entity entity);
        com.example.modid.ecs.Entity fpsFlux$getEcsHandle();
        
        default boolean fpsFlux$hasEcsHandle() {
            return fpsFlux$getEcsHandle() != null;
        }
    }
}
