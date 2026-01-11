package com.example.modid.ecs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.BiConsumer;

/**
 * ComponentArray - SoA (Structure of Arrays) storage for a single component type.
 * 
 * <p>Stores component data in contiguous memory for cache-efficient iteration.</p>
 * <p>Designed for direct GPU upload via persistent mapping.</p>
 */
public final class ComponentArray {
    
    private final ComponentRegistry.ComponentType type;
    private final int componentSize;
    private final int alignment;
    
    // Raw data storage
    private ByteBuffer data;
    private int capacity;
    private int count;
    
    // Entity to index mapping
    private int[] entityToIndex;
    private int[] indexToEntity;
    
    // GPU buffer for direct upload
    private long gpuBuffer;
    private ByteBuffer gpuMappedBuffer;
    private boolean gpuDirty;
    
    /**
     * Create component array with initial capacity.
     */
    public ComponentArray(ComponentRegistry.ComponentType type, int initialCapacity) {
        this.type = type;
        this.componentSize = type.sizeBytes;
        this.alignment = type.alignment;
        this.capacity = initialCapacity;
        this.count = 0;
        
        // Allocate aligned buffer
        int bufferSize = alignUp(componentSize * capacity, alignment);
        this.data = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
        
        this.entityToIndex = new int[initialCapacity];
        this.indexToEntity = new int[initialCapacity];
        
        // Initialize mappings to invalid
        java.util.Arrays.fill(entityToIndex, -1);
        java.util.Arrays.fill(indexToEntity, -1);
        
        this.gpuDirty = true;
    }
    
    /**
     * Add component data for an entity.
     * 
     * @param entityIndex entity's index
     * @param componentData raw component bytes
     */
    public void add(int entityIndex, ByteBuffer componentData) {
        ensureCapacity(count + 1);
        ensureEntityCapacity(entityIndex + 1);
        
        // Check if entity already has this component
        if (entityToIndex[entityIndex] != -1) {
            // Update existing
            int index = entityToIndex[entityIndex];
            data.position(index * componentSize);
            data.put(componentData);
        } else {
            // Add new
            int index = count;
            entityToIndex[entityIndex] = index;
            indexToEntity[index] = entityIndex;
            
            data.position(index * componentSize);
            data.put(componentData);
            
            count++;
        }
        
        componentData.rewind();
        gpuDirty = true;
    }
    
    /**
     * Add component with primitive float array data.
     */
    public void addFloats(int entityIndex, float... values) {
        ByteBuffer temp = ByteBuffer.allocate(values.length * 4).order(ByteOrder.nativeOrder());
        for (float v : values) {
            temp.putFloat(v);
        }
        temp.flip();
        add(entityIndex, temp);
    }
    
    /**
     * Add component with primitive int array data.
     */
    public void addInts(int entityIndex, int... values) {
        ByteBuffer temp = ByteBuffer.allocate(values.length * 4).order(ByteOrder.nativeOrder());
        for (int v : values) {
            temp.putInt(v);
        }
        temp.flip();
        add(entityIndex, temp);
    }
    
    /**
     * Remove component from entity.
     */
    public void remove(int entityIndex) {
        if (entityIndex >= entityToIndex.length || entityToIndex[entityIndex] == -1) {
            return; // Entity doesn't have this component
        }
        
        int removedIndex = entityToIndex[entityIndex];
        int lastIndex = count - 1;
        
        if (removedIndex != lastIndex) {
            // Swap with last element
            int lastEntityIndex = indexToEntity[lastIndex];
            
            // Copy last element data to removed position
            data.position(lastIndex * componentSize);
            ByteBuffer lastData = data.slice().limit(componentSize);
            data.position(removedIndex * componentSize);
            data.put(lastData);
            
            // Update mappings
            entityToIndex[lastEntityIndex] = removedIndex;
            indexToEntity[removedIndex] = lastEntityIndex;
        }
        
        // Clear removed entity's mapping
        entityToIndex[entityIndex] = -1;
        indexToEntity[lastIndex] = -1;
        
        count--;
        gpuDirty = true;
    }
    
    /**
     * Check if entity has this component.
     */
    public boolean has(int entityIndex) {
        return entityIndex < entityToIndex.length && entityToIndex[entityIndex] != -1;
    }
    
    /**
     * Get component data for entity (read-only view).
     */
    public ByteBuffer get(int entityIndex) {
        if (!has(entityIndex)) {
            return null;
        }
        
        int index = entityToIndex[entityIndex];
        data.position(index * componentSize);
        return data.slice().limit(componentSize).asReadOnlyBuffer();
    }
    
    /**
     * Get raw data buffer for iteration.
     */
    public ByteBuffer getRawData() {
        data.position(0);
        data.limit(count * componentSize);
        return data.slice().asReadOnlyBuffer();
    }
    
    /**
     * Get mutable data buffer for batch updates.
     */
    public ByteBuffer getMutableData() {
        data.position(0);
        data.limit(count * componentSize);
        return data.slice();
    }
    
    /**
     * Iterate over all components with their entity indices.
     */
    public void forEach(BiConsumer<Integer, ByteBuffer> consumer) {
        for (int i = 0; i < count; i++) {
            int entityIndex = indexToEntity[i];
            data.position(i * componentSize);
            ByteBuffer componentData = data.slice().limit(componentSize);
            consumer.accept(entityIndex, componentData);
        }
    }
    
    /**
     * Get component count.
     */
    public int getCount() {
        return count;
    }
    
    /**
     * Get component type.
     */
    public ComponentRegistry.ComponentType getType() {
        return type;
    }
    
    /**
     * Get total data size in bytes.
     */
    public int getDataSize() {
        return count * componentSize;
    }
    
    /**
     * Check if GPU buffer needs update.
     */
    public boolean isGpuDirty() {
        return gpuDirty;
    }
    
    /**
     * Mark GPU as synced.
     */
    public void clearGpuDirty() {
        gpuDirty = false;
    }
    
    /**
     * Set GPU buffer for direct mapping.
     */
    public void setGpuBuffer(long buffer, ByteBuffer mapped) {
        this.gpuBuffer = buffer;
        this.gpuMappedBuffer = mapped;
    }
    
    /**
     * Get GPU buffer handle.
     */
    public long getGpuBuffer() {
        return gpuBuffer;
    }
    
    /**
     * Sync data to GPU buffer.
     */
    public void syncToGpu() {
        if (!gpuDirty || gpuMappedBuffer == null) return;
        
        data.position(0);
        gpuMappedBuffer.position(0);
        gpuMappedBuffer.put(data);
        
        gpuDirty = false;
    }
    
    private void ensureCapacity(int required) {
        if (required <= capacity) return;
        
        int newCapacity = Math.max(capacity * 2, required);
        int newSize = alignUp(componentSize * newCapacity, alignment);
        
        ByteBuffer newData = ByteBuffer.allocateDirect(newSize).order(ByteOrder.nativeOrder());
        data.position(0);
        data.limit(count * componentSize);
        newData.put(data);
        
        data = newData;
        capacity = newCapacity;
        
        // Resize index to entity array
        int[] newIndexToEntity = new int[newCapacity];
        java.util.Arrays.fill(newIndexToEntity, -1);
        System.arraycopy(indexToEntity, 0, newIndexToEntity, 0, Math.min(indexToEntity.length, newCapacity));
        indexToEntity = newIndexToEntity;
    }
    
    private void ensureEntityCapacity(int required) {
        if (required <= entityToIndex.length) return;
        
        int newSize = Math.max(entityToIndex.length * 2, required);
        int[] newEntityToIndex = new int[newSize];
        java.util.Arrays.fill(newEntityToIndex, -1);
        System.arraycopy(entityToIndex, 0, newEntityToIndex, 0, entityToIndex.length);
        entityToIndex = newEntityToIndex;
    }
    
    private static int alignUp(int value, int alignment) {
        return (value + alignment - 1) & ~(alignment - 1);
    }
}
