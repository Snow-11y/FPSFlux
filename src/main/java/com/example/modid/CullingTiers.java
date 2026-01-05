package com.lo.fpsflux;

public enum CullingTier {
    FULL(0, 32 * 32),           // 0-32 blocks squared
    MINIMAL(32 * 32, 64 * 64),  // 32-64 blocks squared
    MODERATE(64 * 64, 128 * 128), // 64-128 blocks squared
    AGGRESSIVE(128 * 128, Double.MAX_VALUE); // 128+ blocks squared
    
    private final double minDistSq;
    private final double maxDistSq;
    
    CullingTier(double minDistSq, double maxDistSq) {
        this.minDistSq = minDistSq;
        this.maxDistSq = maxDistSq;
    }
    
    public static CullingTier fromDistanceSquared(double distSq) {
        for (CullingTier tier : values()) {
            if (distSq >= tier.minDistSq && distSq < tier.maxDistSq) {
                return tier;
            }
        }
        return AGGRESSIVE;
    }
    
    public boolean shouldTickAI() {
        return this == FULL;
    }
    
    public boolean shouldTickPathfinding() {
        return this == FULL || this == MINIMAL;
    }
    
    public int getPhysicsInterval() {
        return switch(this) {
            case FULL, MINIMAL -> 1;      // Every tick
            case MODERATE -> 2;            // Every 2 ticks
            case AGGRESSIVE -> 10;         // Every 10 ticks
        };
    }
}
