package com.example.modid.ecs;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Archetype - Defines a unique combination of component types.
 * 
 * <p>Entities with the same component types share an archetype.
 * This enables efficient batch processing and cache-friendly memory access.</p>
 */
public final class Archetype {
    
    /** Unique archetype ID */
    public final int id;
    
    /** Sorted array of component type IDs */
    private final int[] componentTypeIds;
    
    /** Bitmask for fast component checking */
    private final long componentMask;
    
    /** Hash for archetype lookup */
    private final int hash;
    
    /** Component arrays (SoA storage) */
    private final Map<Integer, ComponentArray> componentArrays;
    
    /** Entities in this archetype */
    private final List<Entity> entities;
    private final Map<Integer, Integer> entityIndexToArchetypeIndex;
    
    /**
     * Create archetype from component types.
     */
    Archetype(int id, ComponentRegistry.ComponentType... types) {
        this.id = id;
        this.componentTypeIds = new int[types.length];
        this.componentArrays = new HashMap<>();
        this.entities = new ArrayList<>();
        this.entityIndexToArchetypeIndex = new HashMap<>();
        
        long mask = 0;
        for (int i = 0; i < types.length; i++) {
            componentTypeIds[i] = types[i].id;
            mask |= (1L << types[i].id);
            componentArrays.put(types[i].id, new ComponentArray(types[i], 64));
        }
        
        // Sort for consistent comparison
        Arrays.sort(componentTypeIds);
        
        this.componentMask = mask;
        this.hash = Arrays.hashCode(componentTypeIds);
    }
    
    /**
     * Create archetype from component type IDs.
     */
    Archetype(int id, int[] typeIds, ComponentRegistry registry) {
        this.id = id;
        this.componentTypeIds = typeIds.clone();
        Arrays.sort(this.componentTypeIds);
        
        this.componentArrays = new HashMap<>();
        this.entities = new ArrayList<>();
        this.entityIndexToArchetypeIndex = new HashMap<>();
        
        long mask = 0;
        for (int typeId : componentTypeIds) {
            mask |= (1L << typeId);
            ComponentRegistry.ComponentType type = registry.getType(typeId);
            componentArrays.put(typeId, new ComponentArray(type, 64));
        }
        
        this.componentMask = mask;
        this.hash = Arrays.hashCode(componentTypeIds);
    }
    
    /**
     * Check if archetype has component type.
     */
    public boolean hasComponent(int typeId) {
        return (componentMask & (1L << typeId)) != 0;
    }
    
    /**
     * Check if archetype has all specified components.
     */
    public boolean hasAllComponents(long mask) {
        return (componentMask & mask) == mask;
    }
    
    /**
     * Check if archetype has any of specified components.
     */
    public boolean hasAnyComponent(long mask) {
        return (componentMask & mask) != 0;
    }
    
    /**
     * Get component mask.
     */
    public long getComponentMask() {
        return componentMask;
    }
    
    /**
     * Add entity to this archetype.
     */
    public int addEntity(Entity entity) {
        int archetypeIndex = entities.size();
        entities.add(entity);
        entityIndexToArchetypeIndex.put(entity.index, archetypeIndex);
        return archetypeIndex;
    }
    
    /**
     * Remove entity from this archetype.
     */
    public void removeEntity(Entity entity) {
        Integer archetypeIndex = entityIndexToArchetypeIndex.remove(entity.index);
        if (archetypeIndex == null) return;
        
        // Swap with last entity
        int lastIndex = entities.size() - 1;
        if (archetypeIndex != lastIndex) {
            Entity lastEntity = entities.get(lastIndex);
            entities.set(archetypeIndex, lastEntity);
            entityIndexToArchetypeIndex.put(lastEntity.index, archetypeIndex);
            
            // Swap component data too
            for (ComponentArray array : componentArrays.values()) {
                // Component arrays handle their own swap-remove
            }
        }
        
        entities.remove(lastIndex);
        
        // Remove component data
        for (ComponentArray array : componentArrays.values()) {
            array.remove(entity.index);
        }
    }
    
    /**
     * Set component data for entity.
     */
    public void setComponent(int entityIndex, int typeId, ByteBuffer data) {
        ComponentArray array = componentArrays.get(typeId);
        if (array != null) {
            array.add(entityIndex, data);
        }
    }
    
    /**
     * Get component data for entity.
     */
    public ByteBuffer getComponent(int entityIndex, int typeId) {
        ComponentArray array = componentArrays.get(typeId);
        return array != null ? array.get(entityIndex) : null;
    }
    
    /**
     * Get component array for type.
     */
    public ComponentArray getComponentArray(int typeId) {
        return componentArrays.get(typeId);
    }
    
    /**
     * Get all component arrays.
     */
    public Collection<ComponentArray> getComponentArrays() {
        return componentArrays.values();
    }
    
    /**
     * Get entity count.
     */
    public int getEntityCount() {
        return entities.size();
    }
    
    /**
     * Get entities.
     */
    public List<Entity> getEntities() {
        return Collections.unmodifiableList(entities);
    }
    
    /**
     * Get component type IDs.
     */
    public int[] getComponentTypeIds() {
        return componentTypeIds.clone();
    }
    
    /**
     * Sync all component arrays to GPU.
     */
    public void syncToGpu() {
        for (ComponentArray array : componentArrays.values()) {
            array.syncToGpu();
        }
    }
    
    @Override
    public int hashCode() {
        return hash;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Archetype other)) return false;
        return Arrays.equals(componentTypeIds, other.componentTypeIds);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Archetype[id=");
        sb.append(id).append(", components={");
        for (int i = 0; i < componentTypeIds.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(componentTypeIds[i]);
        }
        sb.append("}, entities=").append(entities.size()).append("]");
        return sb.toString();
    }
}
