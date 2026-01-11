package com.example.modid.ecs;

import com.example.modid.FPSFlux;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * World - Main ECS world container.
 * 
 * <p>Manages entities, archetypes, and coordinates system execution.</p>
 */
public final class World {
    
    /** World name for debugging */
    public final String name;
    
    // Entity management
    private final AtomicInteger nextEntityIndex = new AtomicInteger(1);
    private final int[] entityGenerations;
    private final int[] entityArchetypes; // Maps entity index to archetype ID
    private final Queue<Integer> recycledIndices = new LinkedList<>();
    private int maxEntityIndex = 0;
    
    // Archetype management
    private final Map<Long, Archetype> archetypesByMask = new ConcurrentHashMap<>();
    private final List<Archetype> archetypeList = new ArrayList<>();
    private final AtomicInteger nextArchetypeId = new AtomicInteger(0);
    
    // System scheduling
    private final SystemScheduler scheduler;
    
    // Component registry reference
    private final ComponentRegistry registry;
    
    // Statistics
    private long totalEntitiesCreated = 0;
    private long totalEntitiesDestroyed = 0;
    
    /**
     * Create a new world.
     */
    public World(String name, int maxEntities, int parallelism) {
        this.name = name;
        this.entityGenerations = new int[maxEntities];
        this.entityArchetypes = new int[maxEntities];
        Arrays.fill(entityArchetypes, -1);
        
        this.scheduler = new SystemScheduler(parallelism);
        this.registry = ComponentRegistry.get();
    }
    
    /**
     * Create with default settings.
     */
    public World(String name) {
        this(name, 1_000_000, Runtime.getRuntime().availableProcessors() - 1);
    }
    
    // ========================================================================
    // ENTITY MANAGEMENT
    // ========================================================================
    
    /**
     * Create a new entity.
     */
    public Entity createEntity() {
        int index;
        int generation;
        
        synchronized (recycledIndices) {
            if (!recycledIndices.isEmpty()) {
                index = recycledIndices.poll();
                generation = ++entityGenerations[index];
            } else {
                index = nextEntityIndex.getAndIncrement();
                if (index >= entityGenerations.length) {
                    throw new RuntimeException("Entity limit reached: " + entityGenerations.length);
                }
                generation = entityGenerations[index] = 1;
                maxEntityIndex = Math.max(maxEntityIndex, index);
            }
        }
        
        totalEntitiesCreated++;
        return new Entity(index, generation);
    }
    
    /**
     * Create entity with components.
     */
    @SafeVarargs
    public final Entity createEntity(Class<?>... componentTypes) {
        Entity entity = createEntity();
        
        // Find or create archetype
        long mask = 0;
        for (Class<?> type : componentTypes) {
            ComponentRegistry.ComponentType ct = registry.getType(type);
            mask |= (1L << ct.id);
        }
        
        Archetype archetype = getOrCreateArchetype(mask);
        archetype.addEntity(entity);
        entityArchetypes[entity.index] = archetype.id;
        
        return entity;
    }
    
    /**
     * Destroy an entity.
     */
    public void destroyEntity(Entity entity) {
        if (!isValid(entity)) return;
        
        // Remove from archetype
        int archetypeId = entityArchetypes[entity.index];
        if (archetypeId >= 0 && archetypeId < archetypeList.size()) {
            archetypeList.get(archetypeId).removeEntity(entity);
        }
        
        // Invalidate
        entityArchetypes[entity.index] = -1;
        entityGenerations[entity.index]++;
        
        synchronized (recycledIndices) {
            recycledIndices.add(entity.index);
        }
        
        totalEntitiesDestroyed++;
    }
    
    /**
     * Check if entity is valid.
     */
    public boolean isValid(Entity entity) {
        return entity.index > 0 && 
               entity.index < entityGenerations.length &&
               entityGenerations[entity.index] == entity.generation;
    }
    
    /**
     * Get entity count.
     */
    public int getEntityCount() {
        return (int) (totalEntitiesCreated - totalEntitiesDestroyed);
    }
    
    // ========================================================================
    // COMPONENT MANAGEMENT
    // ========================================================================
    
    /**
     * Add component to entity.
     */
    public <T> void addComponent(Entity entity, Class<T> componentClass, ByteBuffer data) {
        if (!isValid(entity)) return;
        
        ComponentRegistry.ComponentType type = registry.getType(componentClass);
        
        // Get current archetype
        int currentArchetypeId = entityArchetypes[entity.index];
        long currentMask = 0;
        
        if (currentArchetypeId >= 0) {
            Archetype currentArchetype = archetypeList.get(currentArchetypeId);
            currentMask = currentArchetype.getComponentMask();
            
            // Already has component?
            if (currentArchetype.hasComponent(type.id)) {
                currentArchetype.setComponent(entity.index, type.id, data);
                return;
            }
            
            // Remove from current archetype
            currentArchetype.removeEntity(entity);
        }
        
        // Find or create new archetype
        long newMask = currentMask | (1L << type.id);
        Archetype newArchetype = getOrCreateArchetype(newMask);
        
        // Add to new archetype
        newArchetype.addEntity(entity);
        newArchetype.setComponent(entity.index, type.id, data);
        entityArchetypes[entity.index] = newArchetype.id;
        
        // Copy existing component data
        if (currentArchetypeId >= 0) {
            Archetype oldArchetype = archetypeList.get(currentArchetypeId);
            for (int typeId : oldArchetype.getComponentTypeIds()) {
                if (typeId != type.id) {
                    ByteBuffer oldData = oldArchetype.getComponent(entity.index, typeId);
                    if (oldData != null) {
                        newArchetype.setComponent(entity.index, typeId, oldData);
                    }
                }
            }
        }
    }
    
    /**
     * Add component with float data.
     */
    public void addComponent(Entity entity, Class<?> componentClass, float... values) {
        ByteBuffer data = ByteBuffer.allocate(values.length * 4).order(java.nio.ByteOrder.nativeOrder());
        for (float v : values) data.putFloat(v);
        data.flip();
        addComponent(entity, componentClass, data);
    }
    
    /**
     * Remove component from entity.
     */
    public void removeComponent(Entity entity, Class<?> componentClass) {
        if (!isValid(entity)) return;
        
        ComponentRegistry.ComponentType type = registry.getType(componentClass);
        int currentArchetypeId = entityArchetypes[entity.index];
        
        if (currentArchetypeId < 0) return;
        
        Archetype currentArchetype = archetypeList.get(currentArchetypeId);
        if (!currentArchetype.hasComponent(type.id)) return;
        
        // Calculate new mask
        long newMask = currentArchetype.getComponentMask() & ~(1L << type.id);
        
        if (newMask == 0) {
            // No components left - just remove from archetype
            currentArchetype.removeEntity(entity);
            entityArchetypes[entity.index] = -1;
            return;
        }
        
        // Find or create new archetype
        Archetype newArchetype = getOrCreateArchetype(newMask);
        
        // Copy component data (except removed)
        for (int typeId : currentArchetype.getComponentTypeIds()) {
            if (typeId != type.id) {
                ByteBuffer data = currentArchetype.getComponent(entity.index, typeId);
                if (data != null) {
                    newArchetype.setComponent(entity.index, typeId, data);
                }
            }
        }
        
        // Move entity
        currentArchetype.removeEntity(entity);
        newArchetype.addEntity(entity);
        entityArchetypes[entity.index] = newArchetype.id;
    }
    
    /**
     * Check if entity has component.
     */
    public boolean hasComponent(Entity entity, Class<?> componentClass) {
        if (!isValid(entity)) return false;
        
        int archetypeId = entityArchetypes[entity.index];
        if (archetypeId < 0) return false;
        
        ComponentRegistry.ComponentType type = registry.getType(componentClass);
        return archetypeList.get(archetypeId).hasComponent(type.id);
    }
    
    /**
     * Get component data.
     */
    public ByteBuffer getComponent(Entity entity, Class<?> componentClass) {
        if (!isValid(entity)) return null;
        
        int archetypeId = entityArchetypes[entity.index];
        if (archetypeId < 0) return null;
        
        ComponentRegistry.ComponentType type = registry.getType(componentClass);
        return archetypeList.get(archetypeId).getComponent(entity.index, type.id);
    }
    
    // ========================================================================
    // ARCHETYPE MANAGEMENT
    // ========================================================================
    
    /**
     * Get or create archetype for component mask.
     */
    private Archetype getOrCreateArchetype(long mask) {
        return archetypesByMask.computeIfAbsent(mask, m -> {
            // Build type ID array from mask
            List<Integer> typeIds = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                if ((m & (1L << i)) != 0) {
                    typeIds.add(i);
                }
            }
            
            int[] typeIdArray = typeIds.stream().mapToInt(Integer::intValue).toArray();
            int id = nextArchetypeId.getAndIncrement();
            Archetype archetype = new Archetype(id, typeIdArray, registry);
            
            // Ensure list is large enough
            while (archetypeList.size() <= id) {
                archetypeList.add(null);
            }
            archetypeList.set(id, archetype);
            
            return archetype;
        });
    }
    
    /**
     * Get all archetypes.
     */
    public List<Archetype> getArchetypes() {
        return Collections.unmodifiableList(archetypeList.stream()
            .filter(Objects::nonNull)
            .toList());
    }
    
    /**
     * Get archetypes matching a component query.
     */
    public List<Archetype> queryArchetypes(long requiredMask, long excludedMask) {
        List<Archetype> result = new ArrayList<>();
        for (Archetype archetype : archetypeList) {
            if (archetype == null) continue;
            long mask = archetype.getComponentMask();
            if ((mask & requiredMask) == requiredMask && (mask & excludedMask) == 0) {
                result.add(archetype);
            }
        }
        return result;
    }
    
    // ========================================================================
    // SYSTEM MANAGEMENT
    // ========================================================================
    
    /**
     * Register a system.
     */
    public void registerSystem(System system, SystemScheduler.Stage stage) {
        scheduler.register(system, stage);
    }
    
    /**
     * Register system with dependencies.
     */
    public void registerSystem(System system, SystemScheduler.Stage stage, String... dependencies) {
        scheduler.register(system, stage, dependencies);
    }
    
    /**
     * Initialize all systems.
     */
    public void initialize() {
        scheduler.initialize(this);
        FPSFlux.LOGGER.info("[ECS] World '{}' initialized with {} archetypes", name, archetypeList.size());
    }
    
    /**
     * Update world (execute all systems).
     */
    public void update(float deltaTime) {
        scheduler.executeAll(this, deltaTime);
    }
    
    /**
     * Execute specific stage.
     */
    public void executeStage(SystemScheduler.Stage stage, float deltaTime) {
        scheduler.executeStage(this, stage, deltaTime);
    }
    
    /**
     * Sync all component data to GPU.
     */
    public void syncToGpu() {
        for (Archetype archetype : archetypeList) {
            if (archetype != null) {
                archetype.syncToGpu();
            }
        }
    }
    
    /**
     * Shutdown world.
     */
    public void shutdown() {
        scheduler.shutdown(this);
        FPSFlux.LOGGER.info("[ECS] World '{}' shutdown. Created: {}, Destroyed: {}",
            name, totalEntitiesCreated, totalEntitiesDestroyed);
    }
    
    /**
     * Get scheduler.
     */
    public SystemScheduler getScheduler() {
        return scheduler;
    }
    
    /**
     * Get component registry.
     */
    public ComponentRegistry getRegistry() {
        return registry;
    }
    
    /**
     * Get world statistics.
     */
    public String getStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== World '").append(name).append("' Stats ===\n");
        sb.append("Entities: ").append(getEntityCount()).append(" active\n");
        sb.append("  Created: ").append(totalEntitiesCreated).append("\n");
        sb.append("  Destroyed: ").append(totalEntitiesDestroyed).append("\n");
        sb.append("  Recycled pool: ").append(recycledIndices.size()).append("\n");
        sb.append("Archetypes: ").append(archetypesByMask.size()).append("\n");
        
        for (Archetype archetype : getArchetypes()) {
            sb.append("  ").append(archetype).append("\n");
        }
        
        sb.append("\n").append(scheduler.getPerformanceReport());
        
        return sb.toString();
    }
}
