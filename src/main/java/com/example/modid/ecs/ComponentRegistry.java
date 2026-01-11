package com.example.modid.ecs;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ComponentRegistry - Manages component type metadata.
 * 
 * <p>Assigns unique IDs to component types and tracks their memory layout.</p>
 */
public final class ComponentRegistry {
    
    private static final ComponentRegistry INSTANCE = new ComponentRegistry();
    
    public static ComponentRegistry get() { return INSTANCE; }
    
    /**
     * Component type metadata.
     */
    public static final class ComponentType {
        public final int id;
        public final Class<?> clazz;
        public final int sizeBytes;
        public final int alignment;
        public final String name;
        
        ComponentType(int id, Class<?> clazz, int sizeBytes, int alignment) {
            this.id = id;
            this.clazz = clazz;
            this.sizeBytes = sizeBytes;
            this.alignment = alignment;
            this.name = clazz.getSimpleName();
        }
    }
    
    /** Max supported component types */
    public static final int MAX_COMPONENT_TYPES = 256;
    
    private final AtomicInteger nextComponentId = new AtomicInteger(0);
    private final Map<Class<?>, ComponentType> typesByClass = new ConcurrentHashMap<>();
    private final ComponentType[] typesById = new ComponentType[MAX_COMPONENT_TYPES];
    
    // Pre-registered primitive component sizes
    private static final Map<Class<?>, Integer> PRIMITIVE_SIZES = Map.of(
        byte.class, 1, Byte.class, 1,
        short.class, 2, Short.class, 2,
        int.class, 4, Integer.class, 4,
        long.class, 8, Long.class, 8,
        float.class, 4, Float.class, 4,
        double.class, 8, Double.class, 8,
        boolean.class, 1, Boolean.class, 1
    );
    
    private ComponentRegistry() {}
    
    /**
     * Register a component type.
     * 
     * @param clazz component class
     * @param sizeBytes size in bytes for SoA storage
     * @param alignment memory alignment requirement
     * @return component type metadata
     */
    public <T> ComponentType register(Class<T> clazz, int sizeBytes, int alignment) {
        return typesByClass.computeIfAbsent(clazz, c -> {
            int id = nextComponentId.getAndIncrement();
            if (id >= MAX_COMPONENT_TYPES) {
                throw new RuntimeException("Component type limit exceeded: " + MAX_COMPONENT_TYPES);
            }
            ComponentType type = new ComponentType(id, clazz, sizeBytes, alignment);
            typesById[id] = type;
            return type;
        });
    }
    
    /**
     * Register with auto-detected size (for simple types).
     */
    public <T> ComponentType register(Class<T> clazz) {
        Integer size = PRIMITIVE_SIZES.get(clazz);
        if (size != null) {
            return register(clazz, size, size);
        }
        // Estimate size based on fields (simplified)
        int estimatedSize = estimateSize(clazz);
        return register(clazz, estimatedSize, 4);
    }
    
    /**
     * Get component type by class.
     */
    @SuppressWarnings("unchecked")
    public <T> ComponentType getType(Class<T> clazz) {
        ComponentType type = typesByClass.get(clazz);
        if (type == null) {
            throw new IllegalArgumentException("Component type not registered: " + clazz.getName());
        }
        return type;
    }
    
    /**
     * Get component type by ID.
     */
    public ComponentType getType(int id) {
        if (id < 0 || id >= MAX_COMPONENT_TYPES || typesById[id] == null) {
            throw new IllegalArgumentException("Invalid component type ID: " + id);
        }
        return typesById[id];
    }
    
    /**
     * Check if type is registered.
     */
    public boolean isRegistered(Class<?> clazz) {
        return typesByClass.containsKey(clazz);
    }
    
    /**
     * Get all registered types.
     */
    public Collection<ComponentType> getAllTypes() {
        return Collections.unmodifiableCollection(typesByClass.values());
    }
    
    /**
     * Get number of registered types.
     */
    public int getTypeCount() {
        return nextComponentId.get();
    }
    
    /**
     * Estimate size of a class based on fields.
     */
    private int estimateSize(Class<?> clazz) {
        int size = 0;
        for (var field : clazz.getDeclaredFields()) {
            Class<?> fieldType = field.getType();
            Integer fieldSize = PRIMITIVE_SIZES.get(fieldType);
            if (fieldSize != null) {
                size += fieldSize;
            } else if (fieldType.isArray()) {
                // Reference size
                size += 8;
            } else {
                // Object reference
                size += 8;
            }
        }
        return Math.max(size, 4); // Minimum 4 bytes
    }
}
