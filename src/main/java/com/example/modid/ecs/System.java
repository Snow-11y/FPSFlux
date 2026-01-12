package com.example.modid.ecs;

import com.example.modid.FPSFlux;

import java.lang.annotation.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/**
 * System - Advanced base class for ECS systems with modern Java 21+ features.
 *
 * <p>Core Features:</p>
 * <ul>
 *   <li>Lifecycle hooks with state management</li>
 *   <li>Automatic component mask configuration via annotations</li>
 *   <li>Lock-free performance metrics</li>
 *   <li>Async/parallel execution support</li>
 *   <li>Resource management with AutoCloseable</li>
 *   <li>Dependency specification for ordering</li>
 *   <li>Change detection integration</li>
 *   <li>Debug/profiling support</li>
 * </ul>
 *
 * @author Enhanced ECS Framework
 * @version 2.0.0
 * @since Java 21
 */
public abstract class System implements AutoCloseable {

    // ========================================================================
    // ANNOTATIONS
    // ========================================================================

    /**
     * Declare required components for this system.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface RequireComponents {
        Class<?>[] value();
    }

    /**
     * Declare excluded components for this system.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ExcludeComponents {
        Class<?>[] value();
    }

    /**
     * Declare system dependencies (must run after).
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface DependsOn {
        String[] value();
    }

    /**
     * Declare system priority.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Priority {
        int value() default 0;
    }

    /**
     * Mark system as thread-safe for parallel execution.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ThreadSafe {}

    /**
     * Mark system as read-only (doesn't modify components).
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ReadOnly {}

    // ========================================================================
    // ENUMS
    // ========================================================================

    /**
     * System lifecycle state.
     */
    public enum State {
        CREATED,
        INITIALIZING,
        READY,
        RUNNING,
        PAUSED,
        SHUTTING_DOWN,
        SHUTDOWN,
        ERROR
    }

    /**
     * Execution mode for the system.
     */
    public enum ExecutionMode {
        /** Execute on main thread sequentially */
        SEQUENTIAL,
        /** Execute archetypes in parallel */
        PARALLEL_ARCHETYPES,
        /** Execute entities in parallel (within archetype) */
        PARALLEL_ENTITIES,
        /** Fully parallel execution */
        FULLY_PARALLEL,
        /** Execute asynchronously, don't wait */
        ASYNC
    }

    // ========================================================================
    // RECORDS
    // ========================================================================

    /**
     * System descriptor containing metadata.
     */
    public record Descriptor(
        String name,
        Class<? extends System> systemClass,
        long requiredMask,
        long excludedMask,
        int priority,
        String[] dependencies,
        boolean threadSafe,
        boolean readOnly,
        ExecutionMode executionMode
    ) {
        public static Descriptor from(System system) {
            Class<? extends System> clazz = system.getClass();
            
            // Process annotations
            String[] deps = clazz.isAnnotationPresent(DependsOn.class)
                ? clazz.getAnnotation(DependsOn.class).value()
                : new String[0];
            
            int prio = clazz.isAnnotationPresent(Priority.class)
                ? clazz.getAnnotation(Priority.class).value()
                : system.priority;
            
            boolean safe = clazz.isAnnotationPresent(ThreadSafe.class);
            boolean ro = clazz.isAnnotationPresent(ReadOnly.class);

            return new Descriptor(
                system.name,
                clazz,
                system.requiredMask,
                system.excludedMask,
                prio,
                deps,
                safe,
                ro,
                system.executionMode
            );
        }
    }

    /**
     * Execution context passed to system during update.
     */
    public record ExecutionContext(
        World world,
        float deltaTime,
        long frameNumber,
        Instant frameStart,
        ExecutorService executor,
        Map<String, Object> frameData
    ) {
        /**
         * Get typed frame data.
         */
        @SuppressWarnings("unchecked")
        public <T> T getData(String key, T defaultValue) {
            Object value = frameData.get(key);
            return value != null ? (T) value : defaultValue;
        }

        /**
         * Set frame data.
         */
        public void setData(String key, Object value) {
            frameData.put(key, value);
        }

        /**
         * Get elapsed time since frame start.
         */
        public Duration elapsed() {
            return Duration.between(frameStart, Instant.now());
        }
    }

    /**
     * Performance metrics snapshot.
     */
    public record Metrics(
        String systemName,
        long executionCount,
        long totalTimeNanos,
        long lastTimeNanos,
        long minTimeNanos,
        long maxTimeNanos,
        int entitiesProcessed,
        int archetypesProcessed,
        double avgTimeMs,
        double avgEntitiesPerSecond
    ) {
        public static Metrics from(System system) {
            long count = system.executionCount.get();
            long total = system.totalExecutionTimeNanos.get();
            double avgMs = count > 0 ? (total / count) / 1_000_000.0 : 0;
            double avgEps = count > 0 && total > 0 
                ? (system.totalEntitiesProcessed.get() / (total / 1_000_000_000.0))
                : 0;

            return new Metrics(
                system.name,
                count,
                total,
                system.lastExecutionTimeNanos.get(),
                system.minExecutionTimeNanos.get(),
                system.maxExecutionTimeNanos.get(),
                system.totalEntitiesProcessed.intValue(),
                system.totalArchetypesProcessed.intValue(),
                avgMs,
                avgEps
            );
        }
    }

    // ========================================================================
    // CORE STATE
    // ========================================================================

    /** System name for debugging and dependency resolution */
    public final String name;

    /** System unique identifier */
    public final UUID id;

    /** Priority for execution ordering (lower = earlier) */
    public volatile int priority;

    /** Whether system is enabled */
    private volatile boolean enabled = true;

    /** Current system state */
    private volatile State state = State.CREATED;

    /** Execution mode */
    protected volatile ExecutionMode executionMode = ExecutionMode.SEQUENTIAL;

    // Component masks
    protected volatile long requiredMask = 0;
    protected volatile long excludedMask = 0;
    protected volatile long optionalMask = 0;
    protected volatile long writeMask = 0;  // Components this system writes to

    // Dependencies
    protected final Set<String> dependencies = ConcurrentHashMap.newKeySet();

    // Performance tracking with lock-free counters
    protected final AtomicLong lastExecutionTimeNanos = new AtomicLong(0);
    protected final AtomicLong totalExecutionTimeNanos = new AtomicLong(0);
    protected final AtomicLong minExecutionTimeNanos = new AtomicLong(Long.MAX_VALUE);
    protected final AtomicLong maxExecutionTimeNanos = new AtomicLong(0);
    protected final AtomicLong executionCount = new AtomicLong(0);
    protected final LongAdder totalEntitiesProcessed = new LongAdder();
    protected final LongAdder totalArchetypesProcessed = new LongAdder();

    // Error tracking
    protected final AtomicReference<Throwable> lastError = new AtomicReference<>();
    protected final AtomicLong errorCount = new AtomicLong(0);

    // Change detection
    protected final AtomicLong lastProcessedVersion = new AtomicLong(0);

    // Resources managed by this system
    private final List<AutoCloseable> managedResources = new CopyOnWriteArrayList<>();

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    protected System(String name) {
        this.name = Objects.requireNonNull(name, "System name cannot be null");
        this.id = UUID.randomUUID();
        this.priority = 0;
        
        processAnnotations();
    }

    protected System(String name, int priority) {
        this(name);
        this.priority = priority;
    }

    /**
     * Process class annotations to configure system.
     */
    private void processAnnotations() {
        Class<? extends System> clazz = getClass();
        ComponentRegistry registry = ComponentRegistry.get();

        // RequireComponents
        if (clazz.isAnnotationPresent(RequireComponents.class)) {
            for (Class<?> comp : clazz.getAnnotation(RequireComponents.class).value()) {
                ComponentRegistry.ComponentType type = registry.getType(comp);
                requiredMask |= (1L << type.id);
            }
        }

        // ExcludeComponents
        if (clazz.isAnnotationPresent(ExcludeComponents.class)) {
            for (Class<?> comp : clazz.getAnnotation(ExcludeComponents.class).value()) {
                ComponentRegistry.ComponentType type = registry.getType(comp);
                excludedMask |= (1L << type.id);
            }
        }

        // DependsOn
        if (clazz.isAnnotationPresent(DependsOn.class)) {
            Collections.addAll(dependencies, clazz.getAnnotation(DependsOn.class).value());
        }

        // Priority
        if (clazz.isAnnotationPresent(Priority.class)) {
            priority = clazz.getAnnotation(Priority.class).value();
        }

        // ThreadSafe implies parallel capable
        if (clazz.isAnnotationPresent(ThreadSafe.class)) {
            executionMode = ExecutionMode.PARALLEL_ARCHETYPES;
        }
    }

    // ========================================================================
    // COMPONENT MASK CONFIGURATION
    // ========================================================================

    /**
     * Require a component type for this system.
     */
    protected final System require(Class<?> componentClass) {
        ComponentRegistry.ComponentType type = ComponentRegistry.get().getType(componentClass);
        requiredMask |= (1L << type.id);
        return this;
    }

    /**
     * Require multiple component types.
     */
    @SafeVarargs
    protected final System require(Class<?>... componentClasses) {
        for (Class<?> clazz : componentClasses) {
            require(clazz);
        }
        return this;
    }

    /**
     * Exclude entities with a component type.
     */
    protected final System exclude(Class<?> componentClass) {
        ComponentRegistry.ComponentType type = ComponentRegistry.get().getType(componentClass);
        excludedMask |= (1L << type.id);
        return this;
    }

    /**
     * Exclude multiple component types.
     */
    @SafeVarargs
    protected final System exclude(Class<?>... componentClasses) {
        for (Class<?> clazz : componentClasses) {
            exclude(clazz);
        }
        return this;
    }

    /**
     * Mark component as optional (system runs even if not present).
     */
    protected final System optional(Class<?> componentClass) {
        ComponentRegistry.ComponentType type = ComponentRegistry.get().getType(componentClass);
        optionalMask |= (1L << type.id);
        return this;
    }

    /**
     * Mark component as written by this system (for dependency analysis).
     */
    protected final System writes(Class<?> componentClass) {
        ComponentRegistry.ComponentType type = ComponentRegistry.get().getType(componentClass);
        writeMask |= (1L << type.id);
        return this;
    }

    /**
     * Add runtime dependency.
     */
    protected final System dependsOn(String systemName) {
        dependencies.add(systemName);
        return this;
    }

    // ========================================================================
    // ARCHETYPE MATCHING
    // ========================================================================

    /**
     * Check if system should process an archetype.
     */
    public boolean matchesArchetype(Archetype archetype) {
        if (archetype == null) return false;
        long mask = archetype.getComponentMask();
        return (mask & requiredMask) == requiredMask && (mask & excludedMask) == 0;
    }

    /**
     * Get all matching archetypes from world.
     */
    public List<Archetype> getMatchingArchetypes(World world) {
        return world.queryArchetypes(requiredMask, excludedMask, optionalMask);
    }

    // ========================================================================
    // LIFECYCLE HOOKS
    // ========================================================================

    /**
     * Initialize system. Called once when world starts.
     */
    public void initialize(World world) {
        state = State.INITIALIZING;
        onInitialize(world);
        state = State.READY;
    }

    /**
     * Override for custom initialization.
     */
    protected void onInitialize(World world) {
        // Override in subclass
    }

    /**
     * Called before update loop each frame.
     */
    public void onBeforeUpdate(World world, float deltaTime) {
        // Override in subclass
    }

    /**
     * Called before update with full execution context.
     */
    public void onBeforeUpdate(ExecutionContext context) {
        onBeforeUpdate(context.world(), context.deltaTime());
    }

    /**
     * Main update method - called for each matching archetype.
     */
    public abstract void update(World world, Archetype archetype, float deltaTime);

    /**
     * Update with full execution context.
     */
    public void update(ExecutionContext context, Archetype archetype) {
        update(context.world(), archetype, context.deltaTime());
    }

    /**
     * Called after update loop each frame.
     */
    public void onAfterUpdate(World world, float deltaTime) {
        // Override in subclass
    }

    /**
     * Called after update with full execution context.
     */
    public void onAfterUpdate(ExecutionContext context) {
        onAfterUpdate(context.world(), context.deltaTime());
    }

    /**
     * Cleanup system. Called when world shuts down.
     */
    public void shutdown(World world) {
        state = State.SHUTTING_DOWN;
        onShutdown(world);
        closeManagedResources();
        state = State.SHUTDOWN;
    }

    /**
     * Override for custom shutdown.
     */
    protected void onShutdown(World world) {
        // Override in subclass
    }

    /**
     * Called when an error occurs during execution.
     */
    protected void onError(World world, Throwable error) {
        lastError.set(error);
        errorCount.incrementAndGet();
        FPSFlux.LOGGER.error("[ECS] Error in system '{}': {}", name, error.getMessage(), error);
    }

    // ========================================================================
    // EXECUTION CONTROL
    // ========================================================================

    /**
     * Execute system for all matching archetypes.
     */
    public final void execute(World world, float deltaTime) {
        if (!enabled || state == State.PAUSED || state == State.SHUTDOWN) return;

        state = State.RUNNING;
        long startTime = java.lang.System.nanoTime();

        try {
            onBeforeUpdate(world, deltaTime);

            List<Archetype> archetypes = getMatchingArchetypes(world);
            int entityCount = 0;

            switch (executionMode) {
                case SEQUENTIAL -> {
                    for (Archetype archetype : archetypes) {
                        update(world, archetype, deltaTime);
                        entityCount += archetype.getEntityCount();
                    }
                }
                case PARALLEL_ARCHETYPES -> {
                    entityCount = executeParallelArchetypes(world, archetypes, deltaTime);
                }
                case PARALLEL_ENTITIES -> {
                    for (Archetype archetype : archetypes) {
                        updateParallelEntities(world, archetype, deltaTime);
                        entityCount += archetype.getEntityCount();
                    }
                }
                case FULLY_PARALLEL -> {
                    entityCount = executeFullyParallel(world, archetypes, deltaTime);
                }
                case ASYNC -> {
                    executeAsync(world, archetypes, deltaTime);
                    entityCount = archetypes.stream().mapToInt(Archetype::getEntityCount).sum();
                }
            }

            onAfterUpdate(world, deltaTime);

            // Record metrics
            totalArchetypesProcessed.add(archetypes.size());
            totalEntitiesProcessed.add(entityCount);

        } catch (Throwable t) {
            state = State.ERROR;
            onError(world, t);
        }

        long duration = java.lang.System.nanoTime() - startTime;
        recordExecutionTime(duration);
        state = State.READY;
    }

    /**
     * Execute system with full context.
     */
    public final void execute(ExecutionContext context) {
        execute(context.world(), context.deltaTime());
    }

    private int executeParallelArchetypes(World world, List<Archetype> archetypes, float deltaTime) {
        return archetypes.parallelStream()
            .peek(archetype -> update(world, archetype, deltaTime))
            .mapToInt(Archetype::getEntityCount)
            .sum();
    }

    /**
     * Override for parallel entity processing within archetype.
     */
    protected void updateParallelEntities(World world, Archetype archetype, float deltaTime) {
        // Default: sequential
        update(world, archetype, deltaTime);
    }

    private int executeFullyParallel(World world, List<Archetype> archetypes, float deltaTime) {
        return archetypes.parallelStream()
            .peek(archetype -> updateParallelEntities(world, archetype, deltaTime))
            .mapToInt(Archetype::getEntityCount)
            .sum();
    }

    private void executeAsync(World world, List<Archetype> archetypes, float deltaTime) {
        CompletableFuture.runAsync(() -> {
            for (Archetype archetype : archetypes) {
                update(world, archetype, deltaTime);
            }
        });
    }

    // ========================================================================
    // STATE MANAGEMENT
    // ========================================================================

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { 
        this.enabled = enabled; 
    }

    public void enable() { 
        enabled = true; 
    }

    public void disable() { 
        enabled = false; 
    }

    public void pause() {
        if (state == State.READY || state == State.RUNNING) {
            state = State.PAUSED;
        }
    }

    public void resume() {
        if (state == State.PAUSED) {
            state = State.READY;
        }
    }

    public State getState() { 
        return state; 
    }

    public boolean isRunnable() {
        return enabled && (state == State.READY || state == State.RUNNING);
    }

    // ========================================================================
    // RESOURCE MANAGEMENT
    // ========================================================================

    /**
     * Register a resource to be automatically closed on shutdown.
     */
    protected <T extends AutoCloseable> T manage(T resource) {
        managedResources.add(resource);
        return resource;
    }

    private void closeManagedResources() {
        for (AutoCloseable resource : managedResources) {
            try {
                resource.close();
            } catch (Exception e) {
                FPSFlux.LOGGER.warn("[ECS] Error closing resource in system '{}': {}", 
                    name, e.getMessage());
            }
        }
        managedResources.clear();
    }

    @Override
    public void close() {
        closeManagedResources();
    }

    // ========================================================================
    // PERFORMANCE METRICS
    // ========================================================================

    private void recordExecutionTime(long nanos) {
        lastExecutionTimeNanos.set(nanos);
        totalExecutionTimeNanos.addAndGet(nanos);
        executionCount.incrementAndGet();

        // Update min/max atomically
        long currentMin;
        do {
            currentMin = minExecutionTimeNanos.get();
        } while (nanos < currentMin && !minExecutionTimeNanos.compareAndSet(currentMin, nanos));

        long currentMax;
        do {
            currentMax = maxExecutionTimeNanos.get();
        } while (nanos > currentMax && !maxExecutionTimeNanos.compareAndSet(currentMax, nanos));
    }

    /**
     * Get average execution time in milliseconds.
     */
    public double getAverageExecutionTimeMs() {
        long count = executionCount.get();
        return count > 0 ? (totalExecutionTimeNanos.get() / count) / 1_000_000.0 : 0;
    }

    /**
     * Get last execution time in milliseconds.
     */
    public double getLastExecutionTimeMs() {
        return lastExecutionTimeNanos.get() / 1_000_000.0;
    }

    /**
     * Get min execution time in milliseconds.
     */
    public double getMinExecutionTimeMs() {
        long min = minExecutionTimeNanos.get();
        return min == Long.MAX_VALUE ? 0 : min / 1_000_000.0;
    }

    /**
     * Get max execution time in milliseconds.
     */
    public double getMaxExecutionTimeMs() {
        return maxExecutionTimeNanos.get() / 1_000_000.0;
    }

    /**
     * Get execution count.
     */
    public long getExecutionCount() {
        return executionCount.get();
    }

    /**
     * Get full metrics snapshot.
     */
    public Metrics getMetrics() {
        return Metrics.from(this);
    }

    /**
     * Reset performance metrics.
     */
    public void resetMetrics() {
        lastExecutionTimeNanos.set(0);
        totalExecutionTimeNanos.set(0);
        minExecutionTimeNanos.set(Long.MAX_VALUE);
        maxExecutionTimeNanos.set(0);
        executionCount.set(0);
        totalEntitiesProcessed.reset();
        totalArchetypesProcessed.reset();
        errorCount.set(0);
        lastError.set(null);
    }

    // ========================================================================
    // DESCRIPTOR & METADATA
    // ========================================================================

    /**
     * Get system descriptor.
     */
    public Descriptor getDescriptor() {
        return Descriptor.from(this);
    }

    /**
     * Get component masks.
     */
    public long getRequiredMask() { return requiredMask; }
    public long getExcludedMask() { return excludedMask; }
    public long getOptionalMask() { return optionalMask; }
    public long getWriteMask() { return writeMask; }

    /**
     * Get dependencies.
     */
    public Set<String> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    // ========================================================================
    // ERROR HANDLING
    // ========================================================================

    /**
     * Get last error.
     */
    public Optional<Throwable> getLastError() {
        return Optional.ofNullable(lastError.get());
    }

    /**
     * Get error count.
     */
    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * Clear error state.
     */
    public void clearErrors() {
        lastError.set(null);
        errorCount.set(0);
        if (state == State.ERROR) {
            state = State.READY;
        }
    }

    // ========================================================================
    // OBJECT METHODS
    // ========================================================================

    @Override
    public String toString() {
        return String.format("System[%s, priority=%d, state=%s, enabled=%s]",
            name, priority, state, enabled);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof System other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    // ========================================================================
    // UTILITY METHODS FOR SUBCLASSES
    // ========================================================================

    /**
     * Get component type info.
     */
    protected ComponentRegistry.ComponentType getComponentType(Class<?> componentClass) {
        return ComponentRegistry.get().getType(componentClass);
    }

    /**
     * Create a query builder for this system's masks.
     */
    protected World.QueryBuilder createQuery(World world) {
        World.QueryBuilder builder = world.query();
        // Auto-apply required components from mask
        // (would need component registry reverse lookup)
        return builder;
    }
}
