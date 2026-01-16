package com.example.modid.mixins.core;

import com.example.modid.bridge.MinecraftECSBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MixinMinecraft - Core game loop integration.
 *
 * <h2>Injection Points:</h2>
 * <ul>
 *   <li>startGame: Initialize ECS bridge</li>
 *   <li>runTick: Drive ECS tick processing</li>
 *   <li>runGameLoop: Handle render tick</li>
 *   <li>shutdownMinecraftApplet: Cleanup bridge</li>
 * </ul>
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Unique
    private static final Logger fpsflux$LOGGER = Logger.getLogger("FPSFlux-MixinMinecraft");

    // ========================================================================
    // SHADOWS
    // ========================================================================

    @Shadow public WorldClient theWorld;
    @Shadow @Final public Profiler mcProfiler;
    @Shadow public boolean isGamePaused;
    @Shadow private float renderPartialTicksPaused;

    @Shadow public abstract float getRenderPartialTicks();

    // ========================================================================
    // LIFECYCLE STATE
    // ========================================================================

    @Unique
    private boolean fpsflux$initialized = false;

    @Unique
    private long fpsflux$lastTickTime = 0L;

    @Unique
    private float fpsflux$accumulatedTime = 0.0f;

    // Fixed timestep for physics (50 Hz = 20ms per tick, matching MC)
    @Unique
    private static final float FIXED_TIMESTEP = 0.02f;

    @Unique
    private static final float MAX_FRAME_TIME = 0.25f; // Prevent spiral of death

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize ECS bridge after Minecraft finishes startup.
     * Placed at RETURN to ensure all MC systems are ready.
     */
    @Inject(
        method = "startGame",
        at = @At("RETURN")
    )
    private void fpsflux$onStartGame(CallbackInfo ci) {
        if (fpsflux$initialized) return;

        try {
            fpsflux$LOGGER.info("[FPSFlux] Initializing ECS Bridge...");
            
            MinecraftECSBridge bridge = MinecraftECSBridge.getInstance();
            bridge.initialize();
            
            fpsflux$initialized = true;
            fpsflux$lastTickTime = System.nanoTime();
            
            fpsflux$LOGGER.info("[FPSFlux] ECS Bridge initialized successfully");
            
        } catch (Exception e) {
            fpsflux$LOGGER.log(Level.SEVERE, "[FPSFlux] Failed to initialize ECS Bridge", e);
            // Don't crash the game, but log the error
        }
    }

    // ========================================================================
    // TICK PROCESSING
    // ========================================================================

    /**
     * Drive ECS tick at the start of MC's tick loop.
     * Uses fixed timestep accumulator for consistent physics.
     */
    @Inject(
        method = "runTick",
        at = @At("HEAD")
    )
    private void fpsflux$onTickStart(CallbackInfo ci) {
        if (!fpsflux$initialized || isGamePaused) return;

        MinecraftECSBridge bridge = fpsflux$getBridgeSafe();
        if (bridge == null) return;

        mcProfiler.startSection("fpsflux_tick");

        try {
            // Calculate delta time
            long now = System.nanoTime();
            float deltaTime = (now - fpsflux$lastTickTime) / 1_000_000_000.0f;
            fpsflux$lastTickTime = now;

            // Clamp to prevent spiral of death
            deltaTime = Math.min(deltaTime, MAX_FRAME_TIME);

            // Accumulate time
            fpsflux$accumulatedTime += deltaTime;

            // Process fixed timesteps
            int steps = 0;
            while (fpsflux$accumulatedTime >= FIXED_TIMESTEP && steps < 4) {
                bridge.onClientTick(FIXED_TIMESTEP);
                fpsflux$accumulatedTime -= FIXED_TIMESTEP;
                steps++;
            }

            // If we're still behind, drain the accumulator to prevent spiral
            if (fpsflux$accumulatedTime > FIXED_TIMESTEP * 2) {
                fpsflux$LOGGER.fine("[FPSFlux] Draining time accumulator: " + fpsflux$accumulatedTime);
                fpsflux$accumulatedTime = 0;
            }

        } catch (Exception e) {
            fpsflux$LOGGER.log(Level.WARNING, "[FPSFlux] Tick processing error", e);
        } finally {
            mcProfiler.endSection();
        }
    }

    /**
     * Post-tick hook for any cleanup or state validation.
     */
    @Inject(
        method = "runTick",
        at = @At("RETURN")
    )
    private void fpsflux$onTickEnd(CallbackInfo ci) {
        if (!fpsflux$initialized) return;

        // Optional: Validate state, process deferred operations
        // This runs after MC has finished its tick
    }

    // ========================================================================
    // RENDER TICK
    // ========================================================================

    /**
     * Hook into the render loop for interpolation updates.
     * This ensures smooth visuals between physics ticks.
     */
    @Inject(
        method = "runGameLoop",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;updateCameraAndRender(FJ)V",
            shift = At.Shift.BEFORE
        )
    )
    private void fpsflux$onPreRender(CallbackInfo ci) {
        if (!fpsflux$initialized) return;

        MinecraftECSBridge bridge = fpsflux$getBridgeSafe();
        if (bridge == null) return;

        float partialTicks = isGamePaused ? renderPartialTicksPaused : getRenderPartialTicks();

        mcProfiler.startSection("fpsflux_render");
        try {
            bridge.onRenderTick(partialTicks);
        } catch (Exception e) {
            fpsflux$LOGGER.log(Level.FINE, "[FPSFlux] Render tick error", e);
        } finally {
            mcProfiler.endSection();
        }
    }

    // ========================================================================
    // WORLD LOADING/UNLOADING
    // ========================================================================

    /**
     * Handle world changes (loading new world / disconnecting).
     */
    @Inject(
        method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
        at = @At("HEAD")
    )
    private void fpsflux$onWorldLoad(WorldClient world, String message, CallbackInfo ci) {
        if (!fpsflux$initialized) return;

        MinecraftECSBridge bridge = fpsflux$getBridgeSafe();
        if (bridge == null) return;

        if (world == null) {
            // World is being unloaded
            fpsflux$LOGGER.info("[FPSFlux] World unloading, pausing bridge");
            bridge.pause();
        } else {
            // New world is loading
            fpsflux$LOGGER.info("[FPSFlux] World loading, resuming bridge");
            bridge.resume();
        }
    }

    // ========================================================================
    // SHUTDOWN
    // ========================================================================

    /**
     * Clean up ECS bridge on game shutdown.
     */
    @Inject(
        method = "shutdownMinecraftApplet",
        at = @At("HEAD")
    )
    private void fpsflux$onShutdown(CallbackInfo ci) {
        if (!fpsflux$initialized) return;

        fpsflux$LOGGER.info("[FPSFlux] Shutting down ECS Bridge...");

        try {
            MinecraftECSBridge bridge = MinecraftECSBridge.getInstance();
            bridge.close();
        } catch (Exception e) {
            fpsflux$LOGGER.log(Level.WARNING, "[FPSFlux] Error during bridge shutdown", e);
        }

        fpsflux$initialized = false;
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

    @Unique
    private MinecraftECSBridge fpsflux$getBridgeSafe() {
        try {
            MinecraftECSBridge bridge = MinecraftECSBridge.getInstance();
            return bridge.isRunning() ? bridge : null;
        } catch (Exception e) {
            return null;
        }
    }
}
