package com.example.modid.gl;

import com.example.modid.gl.buffer.ops.*;
import com.example.modid.gl.state.GLStateCache;

public class GLOptimizer {
    private static BufferOps bufferOps = null;
    private static boolean initialized = false;
    private static boolean enabled = false;
    private static CompatibilityLayer.CompatibilityLevel compatLevel;
    
    public static void initialize() {
        if (initialized) return;
        
        System.out.println("[FPSFlux] Initializing GL Optimizer...");
        
        // Detect capabilities
        GLCapabilities.detect();
        
        // Evaluate compatibility
        compatLevel = CompatibilityLayer.evaluate();
        
        // Print detailed report
        System.out.println("[FPSFlux] === OpenGL Capability Report ===");
        System.out.println(GLCapabilities.getReport());
        System.out.println("[FPSFlux] Compatibility: " + compatLevel);
        System.out.println("[FPSFlux] Message: " + CompatibilityLayer.getMessage());
        
        // Select buffer implementation based on compatibility
        if (compatLevel == CompatibilityLayer.CompatibilityLevel.UNSUPPORTED) {
            System.err.println("[FPSFlux] GPU/Renderer combination unsupported. Mod disabled.");
            bufferOps = new GL15BufferOps(); // Dummy fallback to prevent crashes
            enabled = false;
        } else {
            bufferOps = selectBufferImplementation();
            enabled = true;
            
            // Reset state cache
            GLStateCache.reset();
            
            System.out.println("[FPSFlux] Selected: " + bufferOps.getName());
        }
        
        initialized = true;
    }
    
    private static BufferOps selectBufferImplementation() {
        // Selection logic with fallbacks for each compatibility level
        
        switch (compatLevel) {
            case OPTIMAL:
                return selectOptimalPath();
                
            case DEGRADED:
                return selectDegradedPath();
                
            case MINIMAL:
                return selectMinimalPath();
                
            default:
                return new GL15BufferOps();
        }
    }
    
    private static BufferOps selectOptimalPath() {
        // Native GPU with modern features - pick absolute best
        
        if (GLCapabilities.GL46) {
            return new GL46BufferOps();
        }
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA) {
            return new GL45BufferOps();
        }
        if (GLCapabilities.GL44 && GLCapabilities.hasPersistentMapping) {
            return new GL44BufferOps();
        }
        if (GLCapabilities.GL43 && GLCapabilities.hasMultiDrawIndirect) {
            return new GL43BufferOps();
        }
        if (GLCapabilities.GL42 && GLCapabilities.hasBaseInstance) {
            return new GL42BufferOps();
        }
        if (GLCapabilities.GL40) {
            return new GL40BufferOps();
        }
        if (GLCapabilities.GL33 && GLCapabilities.hasInstancing) {
            return new GL33BufferOps();
        }
        
        // Shouldn't reach here in OPTIMAL level, but fallback anyway
        return new GL31BufferOps();
    }
    
    private static BufferOps selectDegradedPath() {
        // Wrapper or limited GPU - avoid risky features
        
        // Never use persistent mapping or DSA on wrappers
        if (GLCapabilities.GL33 && GLCapabilities.hasInstancing) {
            System.out.println("[FPSFlux] Using GL 3.3 path (instancing safe for wrappers)");
            return new GL33BufferOps();
        }
        if (GLCapabilities.GL31) {
            System.out.println("[FPSFlux] Using GL 3.1 path (copy buffers)");
            return new GL31BufferOps();
        }
        if (GLCapabilities.GL30 && GLCapabilities.hasVAO) {
            System.out.println("[FPSFlux] Using GL 3.0 path (VAO)");
            return new GL30BufferOps();
        }
        if (GLCapabilities.GL20) {
            System.out.println("[FPSFlux] Using GL 2.0 path (shaders)");
            return new GL20BufferOps();
        }
        
        // Absolute fallback
        System.out.println("[FPSFlux] Using GL 1.5 path (basic VBO)");
        return new GL15BufferOps();
    }
    
    private static BufferOps selectMinimalPath() {
        // Very old GPU - safest possible path
        
        if (GLCapabilities.GL20) {
            System.out.println("[FPSFlux] Minimal mode: GL 2.0 (shaders available)");
            return new GL20BufferOps();
        }
        
        System.out.println("[FPSFlux] Minimal mode: GL 1.5 (VBO only)");
        return new GL15BufferOps();
    }
    
    public static BufferOps getBufferOps() {
        if (!initialized) initialize();
        return bufferOps;
    }
    
    public static boolean isEnabled() {
        if (!initialized) initialize();
        return enabled;
    }
    
    public static CompatibilityLayer.CompatibilityLevel getCompatibilityLevel() {
        if (!initialized) initialize();
        return compatLevel;
    }
    
    public static void printStats() {
        if (!enabled) {
            System.out.println("[FPSFlux] Optimizer disabled - no stats available");
            return;
        }
        
        System.out.printf("[FPSFlux] === Performance Stats ===%n");
        System.out.printf("Buffer Implementation: %s%n", bufferOps.getName());
        System.out.printf("State Cache Efficiency: %.1f%% calls skipped%n", 
            GLStateCache.getSkipPercentage());
        
        GLStateCache.resetMetrics();
    }
    
    public static String getDetailedReport() {
        if (!initialized) initialize();
        
        StringBuilder report = new StringBuilder();
        report.append("=== FPSFlux GL Optimizer Report ===\n");
        report.append("\nOpenGL Information:\n");
        report.append(GLCapabilities.getReport()).append("\n");
        report.append("\nCompatibility Level: ").append(compatLevel).append("\n");
        report.append("Status: ").append(enabled ? "ENABLED" : "DISABLED").append("\n");
        
        if (enabled) {
            report.append("Active Implementation: ").append(bufferOps.getName()).append("\n");
            report.append("State Cache Hit Rate: ").append(
                String.format("%.1f%%", GLStateCache.getSkipPercentage())
            ).append("\n");
        } else {
            report.append("Reason: ").append(CompatibilityLayer.getMessage()).append("\n");
        }
        
        return report.toString();
    }
}
