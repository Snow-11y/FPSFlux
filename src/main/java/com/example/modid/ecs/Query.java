package com.example.modid.ecs;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Query - Fluent API for querying entities by component.
 * 
 * <p>Example usage:</p>
 * <pre>
 * Query.create(world)
 *     .with(Position.class)
 *     .with(Velocity.class)
 *     .without(Static.class)
 *     .forEach((entity, components) -> {
 *         // Process entity
 *     });
 * </pre>
 */
public final class Query {
    
    private final World world;
    private long requiredMask = 0;
    private long excludedMask = 0;
    private List<Class<?>> requiredTypes = new ArrayList<>();
    
    private Query(World world) {
        this.world = world;
    }
    
    /**
     * Create a new query for the world.
     */
    public static Query create(World world) {
        return new Query(world);
    }
    
    /**
     * Require a component type.
     */
    public Query with(Class<?> componentClass) {
        ComponentRegistry.ComponentType type = ComponentRegistry.get().getType(componentClass);
        requiredMask |= (1L << type.id);
        requiredTypes.add(componentClass);
        return this;
    }
    
    /**
     * Exclude entities with a component type.
     */
    public Query without(Class<?> componentClass) {
        ComponentRegistry.ComponentType type = ComponentRegistry.get().getType(componentClass);
        excludedMask |= (1L << type.id);
        return this;
    }
    
    /**
     * Get matching archetypes.
     */
    public List<Archetype> getArchetypes() {
        return world.queryArchetypes(requiredMask, excludedMask);
    }
    
    /**
     * Iterate over all matching entities.
     */
    public void forEach(Consumer<Entity> consumer) {
        for (Archetype archetype : getArchetypes()) {
            for (Entity entity : archetype.getEntities()) {
                consumer.accept(entity);
            }
        }
    }
    
    /**
     * Iterate with component data map.
     */
    public void forEach(BiConsumer<Entity, Map<Class<?>, ByteBuffer>> consumer) {
        for (Archetype archetype : getArchetypes()) {
            for (Entity entity : archetype.getEntities()) {
                Map<Class<?>, ByteBuffer> components = new HashMap<>();
                for (Class<?> type : requiredTypes) {
                    ComponentRegistry.ComponentType ct = ComponentRegistry.get().getType(type);
                    ByteBuffer data = archetype.getComponent(entity.index, ct.id);
                    if (data != null) {
                        components.put(type, data);
                    }
                }
                consumer.accept(entity, components);
            }
        }
    }
    
    /**
     * Iterate with single component (common case).
     */
    public <T> void forEachWith(Class<T> componentClass, BiConsumer<Entity, ByteBuffer> consumer) {
        ComponentRegistry.ComponentType type = ComponentRegistry.get().getType(componentClass);
        
        for (Archetype archetype : getArchetypes()) {
            ComponentArray array = archetype.getComponentArray(type.id);
            if (array == null) continue;
            
            array.forEach((entityIndex, data) -> {
                // Reconstruct entity (simplified - would need generation lookup)
                Entity entity = new Entity(entityIndex, 1);
                consumer.accept(entity, data);
            });
        }
    }
    
    /**
     * Iterate over component arrays directly (fastest, for batch processing).
     */
    public void forEachArchetype(Consumer<Archetype> consumer) {
        for (Archetype archetype : getArchetypes()) {
            consumer.accept(archetype);
        }
    }
    
    /**
     * Count matching entities.
     */
    public int count() {
        int total = 0;
        for (Archetype archetype : getArchetypes()) {
            total += archetype.getEntityCount();
        }
        return total;
    }
    
    /**
     * Check if any entities match.
     */
    public boolean any() {
        for (Archetype archetype : getArchetypes()) {
            if (archetype.getEntityCount() > 0) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get first matching entity.
     */
    public Optional<Entity> first() {
        for (Archetype archetype : getArchetypes()) {
            List<Entity> entities = archetype.getEntities();
            if (!entities.isEmpty()) {
                return Optional.of(entities.get(0));
            }
        }
        return Optional.empty();
    }
}
