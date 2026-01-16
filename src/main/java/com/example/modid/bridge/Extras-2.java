package com.example.modid.bridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Double-buffer for smooth interpolation between physics frames.
 */
public final class InterpolationBuffer {
    
    private volatile float interpolationFactor = 0.0f;
    
    // Interpolated state cache (entity ID -> interpolated transform)
    private final ConcurrentHashMap<Integer, MemorySegment> interpolatedCache = 
        new ConcurrentHashMap<>();
    private final Arena cacheArena = Arena.ofShared();

    public void setInterpolationFactor(float factor) {
        this.interpolationFactor = Math.clamp(factor, 0.0f, 1.0f);
    }

    public float getInterpolationFactor() {
        return interpolationFactor;
    }

    public void captureState(com.example.modid.ecs.World world) {
        // Capture current state as "previous" for next interpolation
        // Implementation depends on your ECS world API
    }

    public MemorySegment getInterpolatedTransform(int entityId, MemorySegment prev, MemorySegment current) {
        return interpolatedCache.computeIfAbsent(entityId, id -> {
            MemorySegment result = cacheArena.allocate(BridgeComponents.Transform.LAYOUT);
            return result;
        });
    }

    public void close() {
        interpolatedCache.clear();
        if (cacheArena.scope().isAlive()) {
            cacheArena.close();
        }
    }
}
