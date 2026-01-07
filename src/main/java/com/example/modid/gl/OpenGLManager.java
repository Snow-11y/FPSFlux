package com.example.modid.gl;

import com.example.modid.FPSFlux;
import com.example.modid.gl.buffer.BufferOps;
import com.example.modid.gl.buffer.ops.*;
import com.example.modid.gl.state.GLStateCache;

/**
 * OpenGLManager - Backend translation layer for OpenGL
 * 
 * This does NOT replace the renderer. It sits between Minecraft's GL calls
 * and the actual OpenGL driver, translating legacy calls to modern equivalents.
 * 
 * Flow: Minecraft Renderer → OpenGLManager → Modern GL Backend → GPU
 * 
 * Compatible with: Vanilla, OptiFine, Sodium/Celerita, Shaders, any renderer
 */
public class OpenGLManager {
    private static BufferOps activeBackend = null;
    private static boolean initialized = false;
    private static boolean enabled = false;
    private static String backendName = "None";
    
    // Config defaults
    private static boolean forceBackend = false;
    private static String forcedBackendVersion = "GL20"; // Safest default
    
    public static void initialize() {
        if (initialized) return;
        
        FPSFlux.LOGGER.info("[OpenGLManager] Initializing backend translation layer...");
        
        // Detect what's available
        GLCapabilities.detect();
        
        // Evaluate compatibility (GPU + wrapper support)
        CompatibilityLayer.CompatibilityLevel compatLevel = CompatibilityLayer.evaluate();
        
        FPSFlux.LOGGER.info("[OpenGLManager] {}", GLCapabilities.getReport());
        FPSFlux.LOGGER.info("[OpenGLManager] Compatibility: {}", compatLevel);
        
        // Select backend based on config or auto-detect
        if (forceBackend) {
            activeBackend = selectForcedBackend();
        } else {
            activeBackend = selectOptimalBackend(compatLevel);
        }
        
        if (activeBackend != null) {
            enabled = true;
            backendName = activeBackend.getName();
            GLStateCache.reset();
            FPSFlux.LOGGER.info("[OpenGLManager] Active Backend: {}", backendName);
        } else {
            enabled = false;
            backendName = "Disabled";
            FPSFlux.LOGGER.warn("[OpenGLManager] No compatible backend found - running in passthrough mode");
        }
        
        initialized = true;
    }
    
    private static BufferOps selectOptimalBackend(CompatibilityLayer.CompatibilityLevel compatLevel) {
        // Auto-detect best backend for this system
        
        switch (compatLevel) {
            case OPTIMAL:
                return selectDesktopBackend();
                
            case DEGRADED:
                return selectWrapperBackend();
                
            case MINIMAL:
                return selectLegacyBackend();
                
            default:
                return null;
        }
    }
    
    private static BufferOps selectDesktopBackend() {
        // Native desktop GPU - prefer newest
        
        if (GLCapabilities.GL46) return new GL46BufferOps();
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) return new GL45BufferOps();
        if (GLCapabilities.GL44 && GLCapabilities.hasPersistentMapping) return new GL44BufferOps();
        if (GLCapabilities.GL43) return new GL43BufferOps();
        if (GLCapabilities.GL42) return new GL42BufferOps();
        if (GLCapabilities.GL40) return new GL40BufferOps();
        if (GLCapabilities.GL33) return new GL33BufferOps();
        if (GLCapabilities.GL31) return new GL31BufferOps();
        if (GLCapabilities.GL30) return new GL30BufferOps();
        if (GLCapabilities.GL20) return new GL20BufferOps();
        if (GLCapabilities.GL15) return new GL15BufferOps();
        
        return null;
    }
    
    private static BufferOps selectWrapperBackend() {
        // Wrapper detected (gl4es, ANGLE, Zink) - be conservative
        
        if (GLCapabilities.wrapperName.equals("gl4es")) {
            // gl4es on mobile - check for OpenGL ES support first
            if (GLCapabilities.hasGLES32) return new GLES32BufferOps();
            if (GLCapabilities.hasGLES31) return new GLES31BufferOps();
            if (GLCapabilities.hasGLES30) return new GLES30BufferOps();
            
            // Fall back to conservative desktop GL
            if (GLCapabilities.GL30) return new GL30BufferOps();
            if (GLCapabilities.GL20) return new GL20BufferOps();
        }
        
        if (GLCapabilities.wrapperName.contains("Zink")) {
            // Zink (Vulkan-to-GL) - supports up to GL 4.6 but with caveats
            // Prefer GL 4.3 path for stability
            if (GLCapabilities.GL43) return new GL43BufferOps();
            if (GLCapabilities.GL33) return new GL33BufferOps();
        }
        
        if (GLCapabilities.wrapperName.contains("ANGLE")) {
            // ANGLE (D3D-to-GL) - usually supports GL 3.x well
            if (GLCapabilities.GL33) return new GL33BufferOps();
            if (GLCapabilities.GL30) return new GL30BufferOps();
        }
        
        // Generic wrapper fallback
        if (GLCapabilities.GL31) return new GL31BufferOps();
        if (GLCapabilities.GL20) return new GL20BufferOps();
        if (GLCapabilities.GL15) return new GL15BufferOps();
        
        return null;
    }
    
    private static BufferOps selectLegacyBackend() {
        // Very old GPU or limited wrapper
        if (GLCapabilities.GL20) return new GL20BufferOps();
        if (GLCapabilities.GL15) return new GL15BufferOps();
        return null;
    }
    
    private static BufferOps selectForcedBackend() {
        // User forced a specific backend via config
        FPSFlux.LOGGER.info("[OpenGLManager] Forcing backend: {}", forcedBackendVersion);
        
        return switch (forcedBackendVersion.toUpperCase()) {
            case "GL46" -> GLCapabilities.GL46 ? new GL46BufferOps() : null;
            case "GL45" -> GLCapabilities.GL45 ? new GL45BufferOps() : null;
            case "GL44" -> GLCapabilities.GL44 ? new GL44BufferOps() : null;
            case "GL43" -> GLCapabilities.GL43 ? new GL43BufferOps() : null;
            case "GL42" -> GLCapabilities.GL42 ? new GL42BufferOps() : null;
            case "GL40" -> GLCapabilities.GL40 ? new GL40BufferOps() : null;
            case "GL33" -> GLCapabilities.GL33 ? new GL33BufferOps() : null;
            case "GL31" -> GLCapabilities.GL31 ? new GL31BufferOps() : null;
            case "GL30" -> GLCapabilities.GL30 ? new GL30BufferOps() : null;
            case "GL20" -> GLCapabilities.GL20 ? new GL20BufferOps() : null;
            case "GL15" -> GLCapabilities.GL15 ? new GL15BufferOps() : null;
            case "GLES32" -> GLCapabilities.hasGLES32 ? new GLES32BufferOps() : null;
            case "GLES31" -> GLCapabilities.hasGLES31 ? new GLES31BufferOps() : null;
            case "GLES30" -> GLCapabilities.hasGLES30 ? new GLES30BufferOps() : null;
            default -> {
                FPSFlux.LOGGER.error("[OpenGLManager] Unknown forced backend: {}", forcedBackendVersion);
                yield null;
            }
        };
    }
    
    // Public API
    
    public static BufferOps getBackend() {
        if (!initialized) initialize();
        return activeBackend;
    }
    
    public static boolean isEnabled() {
        if (!initialized) initialize();
        return enabled;
    }
    
    public static String getBackendName() {
        if (!initialized) initialize();
        return backendName;
    }
    
    public static void setForcedBackend(String version) {
        forcedBackendVersion = version;
        forceBackend = true;
    }
    
    public static void disableForcedBackend() {
        forceBackend = false;
    }
    
    public static String getDetailedReport() {
        if (!initialized) initialize();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== OpenGLManager Status ===\n");
        sb.append("Backend: ").append(backendName).append("\n");
        sb.append("Status: ").append(enabled ? "ACTIVE" : "DISABLED").append("\n");
        sb.append("Mode: ").append(forceBackend ? "FORCED" : "AUTO").append("\n");
        sb.append("\n").append(GLCapabilities.getReport()).append("\n");
        
        if (enabled) {
            sb.append("\nState Cache Efficiency: ")
              .append(String.format("%.1f%%", GLStateCache.getSkipPercentage()))
              .append(" calls skipped\n");
        }
        
        return sb.toString();
    }
}
