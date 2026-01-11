package com.example.modid.ecs;

import com.example.modid.FPSFlux;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SystemScheduler - Manages system execution with parallel processing support.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Priority-based ordering</li>
 *   <li>Parallel execution of independent systems</li>
 *   <li>Dependency tracking</li>
 *   <li>Performance profiling</li>
 * </ul>
 */
public final class SystemScheduler {
    
    /**
     * Execution stage.
     */
    public enum Stage {
        PRE_UPDATE,
        UPDATE,
        POST_UPDATE,
        RENDER_PREPARE,
        RENDER
    }
    
    /**
     * System registration with metadata.
     */
    private static final class SystemEntry {
        final System system;
        final Stage stage;
        final Set<String> dependencies;
        final Set<String> dependents;
        volatile boolean completed;
        
        SystemEntry(System system, Stage stage) {
            this.system = system;
            this.stage = stage;
            this.dependencies = new HashSet<>();
            this.dependents = new HashSet<>();
            this.completed = false;
        }
    }
    
    private final Map<String, SystemEntry> systems = new LinkedHashMap<>();
    private final Map<Stage, List<SystemEntry>> stageOrder = new EnumMap<>(Stage.class);
    private final ExecutorService executor;
    private final int parallelism;
    
    private volatile boolean orderDirty = true;
    
    /**
     * Create scheduler with specified parallelism.
     */
    public SystemScheduler(int parallelism) {
        this.parallelism = parallelism;
        this.executor = Executors.newFixedThreadPool(parallelism, r -> {
            Thread t = new Thread(r, "ECS-Worker-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
        
        for (Stage stage : Stage.values()) {
            stageOrder.put(stage, new ArrayList<>());
        }
    }
    
    /**
     * Create scheduler with default parallelism (CPU cores - 1).
     */
    public SystemScheduler() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }
    
    /**
     * Register a system.
     */
    public void register(System system, Stage stage) {
        SystemEntry entry = new SystemEntry(system, stage);
        systems.put(system.name, entry);
        orderDirty = true;
    }
    
    /**
     * Register system with dependencies.
     */
    public void register(System system, Stage stage, String... dependencies) {
        SystemEntry entry = new SystemEntry(system, stage);
        entry.dependencies.addAll(Arrays.asList(dependencies));
        systems.put(system.name, entry);
        orderDirty = true;
    }
    
    /**
     * Add dependency between systems.
     */
    public void addDependency(String systemName, String dependsOn) {
        SystemEntry entry = systems.get(systemName);
        SystemEntry depEntry = systems.get(dependsOn);
        
        if (entry != null && depEntry != null) {
            entry.dependencies.add(dependsOn);
            depEntry.dependents.add(systemName);
            orderDirty = true;
        }
    }
    
    /**
     * Unregister a system.
     */
    public void unregister(String systemName) {
        SystemEntry entry = systems.remove(systemName);
        if (entry != null) {
            // Remove from dependents
            for (String dep : entry.dependencies) {
                SystemEntry depEntry = systems.get(dep);
                if (depEntry != null) {
                    depEntry.dependents.remove(systemName);
                }
            }
            orderDirty = true;
        }
    }
    
    /**
     * Initialize all systems.
     */
    public void initialize(World world) {
        rebuildOrder();
        
        for (Stage stage : Stage.values()) {
            for (SystemEntry entry : stageOrder.get(stage)) {
                try {
                    entry.system.initialize(world);
                } catch (Exception e) {
                    FPSFlux.LOGGER.error("[ECS] Failed to initialize system {}: {}", 
                        entry.system.name, e.getMessage());
                }
            }
        }
    }
    
    /**
     * Execute a specific stage.
     */
    public void executeStage(World world, Stage stage, float deltaTime) {
        if (orderDirty) {
            rebuildOrder();
        }
        
        List<SystemEntry> stageSystems = stageOrder.get(stage);
        if (stageSystems.isEmpty()) return;
        
        // Reset completion flags
        for (SystemEntry entry : stageSystems) {
            entry.completed = false;
        }
        
        // Execute systems respecting dependencies
        if (parallelism > 1 && stageSystems.size() > 1) {
            executeParallel(world, stageSystems, deltaTime);
        } else {
            executeSequential(world, stageSystems, deltaTime);
        }
    }
    
    /**
     * Execute all stages in order.
     */
    public void executeAll(World world, float deltaTime) {
        for (Stage stage : Stage.values()) {
            executeStage(world, stage, deltaTime);
        }
    }
    
    /**
     * Sequential execution.
     */
    private void executeSequential(World world, List<SystemEntry> entries, float deltaTime) {
        for (SystemEntry entry : entries) {
            if (!entry.system.enabled) {
                entry.completed = true;
                continue;
            }
            
            executeSystem(world, entry, deltaTime);
        }
    }
    
    /**
     * Parallel execution with dependency respect.
     */
    private void executeParallel(World world, List<SystemEntry> entries, float deltaTime) {
        CountDownLatch latch = new CountDownLatch(entries.size());
        AtomicInteger completed = new AtomicInteger(0);
        
        // Submit all systems that have no unmet dependencies
        for (SystemEntry entry : entries) {
            if (!entry.system.enabled) {
                entry.completed = true;
                latch.countDown();
                completed.incrementAndGet();
                continue;
            }
            
            submitWhenReady(world, entry, deltaTime, latch, entries);
        }
        
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            FPSFlux.LOGGER.warn("[ECS] System execution interrupted");
            Thread.currentThread().interrupt();
        }
    }
    
    private void submitWhenReady(World world, SystemEntry entry, float deltaTime,
                                  CountDownLatch latch, List<SystemEntry> allEntries) {
        executor.submit(() -> {
            // Wait for dependencies
            while (!entry.dependencies.isEmpty()) {
                boolean allDepsComplete = true;
                for (String dep : entry.dependencies) {
                    SystemEntry depEntry = systems.get(dep);
                    if (depEntry != null && !depEntry.completed) {
                        allDepsComplete = false;
                        break;
                    }
                }
                
                if (allDepsComplete) break;
                
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            
            executeSystem(world, entry, deltaTime);
            entry.completed = true;
            latch.countDown();
        });
    }
    
    /**
     * Execute a single system.
     */
    private void executeSystem(World world, SystemEntry entry, float deltaTime) {
        System system = entry.system;
        long startTime = java.lang.System.nanoTime();
        
        try {
            system.onBeforeUpdate(world, deltaTime);
            
            // Find matching archetypes
            for (Archetype archetype : world.getArchetypes()) {
                if (system.matchesArchetype(archetype)) {
                    system.update(world, archetype, deltaTime);
                }
            }
            
            system.onAfterUpdate(world, deltaTime);
            
        } catch (Exception e) {
            FPSFlux.LOGGER.error("[ECS] System {} failed: {}", system.name, e.getMessage());
            e.printStackTrace();
        }
        
        long endTime = java.lang.System.nanoTime();
        system.lastExecutionTimeNanos = endTime - startTime;
        system.totalExecutionTimeNanos += system.lastExecutionTimeNanos;
        system.executionCount++;
        
        entry.completed = true;
    }
    
    /**
     * Rebuild execution order based on priorities and dependencies.
     */
    private void rebuildOrder() {
        for (Stage stage : Stage.values()) {
            stageOrder.get(stage).clear();
        }
        
        // Group by stage
        for (SystemEntry entry : systems.values()) {
            stageOrder.get(entry.stage).add(entry);
        }
        
        // Sort each stage by priority
        for (Stage stage : Stage.values()) {
            List<SystemEntry> stageSystems = stageOrder.get(stage);
            stageSystems.sort(Comparator.comparingInt(e -> e.system.priority));
        }
        
        // Topological sort within each stage for dependencies
        for (Stage stage : Stage.values()) {
            List<SystemEntry> sorted = topologicalSort(stageOrder.get(stage));
            stageOrder.put(stage, sorted);
        }
        
        orderDirty = false;
    }
    
    /**
     * Topological sort for dependency ordering.
     */
    private List<SystemEntry> topologicalSort(List<SystemEntry> entries) {
        List<SystemEntry> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        for (SystemEntry entry : entries) {
            if (!visited.contains(entry.system.name)) {
                topologicalSortVisit(entry, entries, visited, visiting, result);
            }
        }
        
        return result;
    }
    
    private void topologicalSortVisit(SystemEntry entry, List<SystemEntry> entries,
                                       Set<String> visited, Set<String> visiting,
                                       List<SystemEntry> result) {
        if (visiting.contains(entry.system.name)) {
            FPSFlux.LOGGER.warn("[ECS] Circular dependency detected involving: {}", entry.system.name);
            return;
        }
        
        if (visited.contains(entry.system.name)) {
            return;
        }
        
        visiting.add(entry.system.name);
        
        for (String depName : entry.dependencies) {
            SystemEntry depEntry = systems.get(depName);
            if (depEntry != null && entries.contains(depEntry)) {
                topologicalSortVisit(depEntry, entries, visited, visiting, result);
            }
        }
        
        visiting.remove(entry.system.name);
        visited.add(entry.system.name);
        result.add(entry);
    }
    
    /**
     * Shutdown all systems.
     */
    public void shutdown(World world) {
        // Shutdown in reverse order
        List<Stage> reverseStages = Arrays.asList(Stage.values());
        Collections.reverse(reverseStages);
        
        for (Stage stage : reverseStages) {
            List<SystemEntry> stageSystems = new ArrayList<>(stageOrder.get(stage));
            Collections.reverse(stageSystems);
            
            for (SystemEntry entry : stageSystems) {
                try {
                    entry.system.shutdown(world);
                } catch (Exception e) {
                    FPSFlux.LOGGER.error("[ECS] Failed to shutdown system {}: {}",
                        entry.system.name, e.getMessage());
                }
            }
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
    
    /**
     * Get system by name.
     */
    public System getSystem(String name) {
        SystemEntry entry = systems.get(name);
        return entry != null ? entry.system : null;
    }
    
    /**
     * Get all systems in a stage.
     */
    public List<System> getSystemsInStage(Stage stage) {
        return stageOrder.get(stage).stream()
            .map(e -> e.system)
            .toList();
    }
    
    /**
     * Get performance report.
     */
    public String getPerformanceReport() {
        StringBuilder sb = new StringBuilder("=== ECS System Performance ===\n");
        
        for (Stage stage : Stage.values()) {
            List<SystemEntry> stageSystems = stageOrder.get(stage);
            if (stageSystems.isEmpty()) continue;
            
            sb.append("\n").append(stage).append(":\n");
            for (SystemEntry entry : stageSystems) {
                System s = entry.system;
                sb.append(String.format("  %-30s: %.3f ms (avg: %.3f ms, count: %d)%s\n",
                    s.name,
                    s.getLastExecutionTimeMs(),
                    s.getAverageExecutionTimeMs(),
                    s.executionCount,
                    s.enabled ? "" : " [DISABLED]"));
            }
        }
        
        return sb.toString();
    }
}
