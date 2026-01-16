package com.example.modid.bridge;

import com.example.modid.ecs.Archetype;
import com.example.modid.ecs.SnowySystem;
import com.example.modid.ecs.World;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * InterpolationSystem - Handles smooth transform interpolation for rendering.
 */
public final class InterpolationSystem extends System {

    private final MinecraftECSBridge bridge;
    private volatile float currentInterpolationFactor = 0.0f;

    public InterpolationSystem(MinecraftECSBridge bridge) {
        super("Bridge_Interpolation");
        this.bridge = bridge;
    }

    @Override
    public void update(World world, float partialTicks) {
        this.currentInterpolationFactor = Math.clamp(partialTicks, 0.0f, 1.0f);
        // Actual interpolation is performed on-demand via getInterpolatedTransform()
    }

    @Override
    public void update(World world, Archetype archetype, float deltaTime) {
        // Not used
    }

    public float getCurrentInterpolationFactor() {
        return currentInterpolationFactor;
    }
}
