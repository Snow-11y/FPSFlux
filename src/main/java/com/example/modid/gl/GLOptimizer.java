package com.example.modid.gl;

import com.example.modid.gl.buffer.ops.*;
import com.example.modid.gl.state.GLStateCache;

public class GLOptimizer {
    private static BufferOps bufferOps = null;
    private static boolean initialized = false;
    
    public static void initialize() {
        if (initialized) return;
        
        // Detect capabilities
        GLCapabilities.detect();
        
        // Select best buffer implementation
        bufferOps = selectBufferImplementation();
        
        // Reset state cache
        GLStateCache.reset();
        
        initialized = true;
        
        System.out.println("[FPSFlux] GL Optimizer initialized:");
        System.out.println("  " + GLCapabilities.getReport());
        System.out.println("  Buffer Strategy: " + bufferOps.getName());
    }
    
    private static BufferOps selectBufferImplementation() {
        // Pick best available implementation
        if (GLCapabilities.GL45 && GLCapabilities.hasDSA && GLCapabilities.hasPersistentMapping) {
            return new GL45BufferOps();
        }
        if (GLCapabilities.GL44 && GLCapabilities.hasPersistentMapping) {
            return new GL44BufferOps();
        }
        if (GLCapabilities.GL31) {
            return new GL31BufferOps();
        }
        if (GLCapabilities.GL15) {
            return new GL15BufferOps();
        }
        
        throw new RuntimeException("GPU does not support OpenGL 1.5 - cannot run FPSFlux");
    }
    
    public static BufferOps getBufferOps() {
        if (!initialized) initialize();
        return bufferOps;
    }
    
    public static void printStats() {
        System.out.printf("[FPSFlux] State cache efficiency: %.1f%% calls skipped%n",
            GLStateCache.getSkipPercentage());
        GLStateCache.resetMetrics();
    }
}
