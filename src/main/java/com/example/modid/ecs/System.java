package com.example.modid.ecs;

/**
 * System - Base class for ECS systems.
 * 
 * <p>Systems contain logic that operates on entities with specific component combinations.</p>
 */
public abstract class System {
    
    /** System name for debugging */
    public final String name;
    
    /** Priority for execution ordering (lower = earlier) */
    public int priority = 0;
    
    /** Whether system is enabled */
    public boolean enabled = true;
    
    /** Component mask for filtering entities */
    protected long requiredMask = 0;
    protected long excludedMask = 0;
    
    /** Performance tracking */
    protected long lastExecutionTimeNanos = 0;
    protected long totalExecutionTimeNanos = 0;
    protected long executionCount = 0;
    
    protected System(String name) {
        this.name = name;
    }
    
    /**
     * Require a component type for this system.
     */
    protected void require(Class<?> componentClass) {
        ComponentRegistry.ComponentType type = ComponentRegistry.get().getType(componentClass);
        requiredMask |= (1L << type.id);
    }
    
    /**
     * Exclude entities with a component type.
     */
    protected void exclude(Class<?> componentClass) {
        ComponentRegistry.ComponentType type = ComponentRegistry.get().getType(componentClass);
        excludedMask |= (1L << type.id);
    }
    
    /**
     * Check if system should process an archetype.
     */
    public boolean matchesArchetype(Archetype archetype) {
        long mask = archetype.getComponentMask();
        return (mask & requiredMask) == requiredMask && (mask & excludedMask) == 0;
    }
    
    /**
     * Initialize system.
     */
    public void initialize(World world) {
        // Override in subclass
    }
    
    /**
     * Called before update loop.
     */
    public void onBeforeUpdate(World world, float deltaTime) {
        // Override in subclass
    }
    
    /**
     * Main update method - called for each matching archetype.
     */
    public abstract void update(World world, Archetype archetype, float deltaTime);
    
    /**
     * Called after update loop.
     */
    public void onAfterUpdate(World world, float deltaTime) {
        // Override in subclass
    }
    
    /**
     * Cleanup system.
     */
    public void shutdown(World world) {
        // Override in subclass
    }
    
    /**
     * Get average execution time in milliseconds.
     */
    public double getAverageExecutionTimeMs() {
        if (executionCount == 0) return 0;
        return (totalExecutionTimeNanos / executionCount) / 1_000_000.0;
    }
    
    /**
     * Get last execution time in milliseconds.
     */
    public double getLastExecutionTimeMs() {
        return lastExecutionTimeNanos / 1_000_000.0;
    }
}
