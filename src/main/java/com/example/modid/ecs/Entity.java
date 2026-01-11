package com.example.modid.ecs;

/**
 * Entity - Lightweight entity handle.
 * 
 * <p>An entity is just an ID. All data is stored in archetypes.</p>
 * <p>Uses generation counter for safe handle recycling.</p>
 */
public final class Entity {
    
    /** Invalid entity constant */
    public static final Entity NULL = new Entity(0, 0);
    
    /** Entity index (lower 32 bits when packed) */
    public final int index;
    
    /** Generation counter for validity checking (upper 32 bits when packed) */
    public final int generation;
    
    public Entity(int index, int generation) {
        this.index = index;
        this.generation = generation;
    }
    
    /**
     * Pack entity into 64-bit handle.
     */
    public long pack() {
        return ((long) generation << 32) | (index & 0xFFFFFFFFL);
    }
    
    /**
     * Unpack entity from 64-bit handle.
     */
    public static Entity unpack(long packed) {
        int index = (int) (packed & 0xFFFFFFFFL);
        int generation = (int) (packed >>> 32);
        return new Entity(index, generation);
    }
    
    /**
     * Check if entity is valid (non-null).
     */
    public boolean isValid() {
        return index != 0 || generation != 0;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Entity other)) return false;
        return index == other.index && generation == other.generation;
    }
    
    @Override
    public int hashCode() {
        return 31 * index + generation;
    }
    
    @Override
    public String toString() {
        return "Entity[" + index + ":" + generation + "]";
    }
}
