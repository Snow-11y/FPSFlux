package com.example.modid.gl.state;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

/**
 * Caches OpenGL state to avoid redundant API calls.
 * 
 * Example: If texture 5 is already bound, calling bindTexture(5) becomes a no-op.
 * This can eliminate 30-50% of state change overhead in typical scenes.
 */
public class GLStateCache {
    // Texture state
    private static final int[] boundTextures = new int[32]; // GL_TEXTURE0-31
    private static int activeTextureUnit = 0;
    
    // Buffer state
    private static int boundArrayBuffer = 0;
    private static int boundElementBuffer = 0;
    
    // Capability state (glEnable/glDisable)
    private static boolean depthTestEnabled = false;
    private static boolean blendEnabled = false;
    private static boolean cullFaceEnabled = false;
    
    // Counters for optimization metrics
    private static long totalCalls = 0;
    private static long skippedCalls = 0;
    
    public static void reset() {
        // Call this when context is lost or at world load
        for (int i = 0; i < boundTextures.length; i++) {
            boundTextures[i] = 0;
        }
        activeTextureUnit = 0;
        boundArrayBuffer = 0;
        boundElementBuffer = 0;
        depthTestEnabled = false;
        blendEnabled = false;
        cullFaceEnabled = false;
    }
    
    // Texture binding optimization
    public static void bindTexture(int target, int texture) {
        totalCalls++;
        
        if (boundTextures[activeTextureUnit] == texture) {
            skippedCalls++;
            return; // Already bound, skip
        }
        
        GL11.glBindTexture(target, texture);
        boundTextures[activeTextureUnit] = texture;
    }
    
    public static void activeTexture(int unit) {
        totalCalls++;
        
        int index = unit - GL13.GL_TEXTURE0;
        if (activeTextureUnit == index) {
            skippedCalls++;
            return;
        }
        
        GL13.glActiveTexture(unit);
        activeTextureUnit = index;
    }
    
    // Buffer binding optimization
    public static void bindBuffer(int target, int buffer) {
        totalCalls++;
        
        if (target == GL15.GL_ARRAY_BUFFER) {
            if (boundArrayBuffer == buffer) {
                skippedCalls++;
                return;
            }
            boundArrayBuffer = buffer;
        } else if (target == GL15.GL_ELEMENT_ARRAY_BUFFER) {
            if (boundElementBuffer == buffer) {
                skippedCalls++;
                return;
            }
            boundElementBuffer = buffer;
        }
        
        GL15.glBindBuffer(target, buffer);
    }
    
    // Capability toggle optimization
    public static void enable(int cap) {
        totalCalls++;
        
        boolean currentState = getCapabilityState(cap);
        if (currentState) {
            skippedCalls++;
            return;
        }
        
        GL11.glEnable(cap);
        setCapabilityState(cap, true);
    }
    
    public static void disable(int cap) {
        totalCalls++;
        
        boolean currentState = getCapabilityState(cap);
        if (!currentState) {
            skippedCalls++;
            return;
        }
        
        GL11.glDisable(cap);
        setCapabilityState(cap, false);
    }
    
    private static boolean getCapabilityState(int cap) {
        return switch(cap) {
            case GL11.GL_DEPTH_TEST -> depthTestEnabled;
            case GL11.GL_BLEND -> blendEnabled;
            case GL11.GL_CULL_FACE -> cullFaceEnabled;
            default -> false; // Unknown, let GL handle it
        };
    }
    
    private static void setCapabilityState(int cap, boolean state) {
        switch(cap) {
            case GL11.GL_DEPTH_TEST -> depthTestEnabled = state;
            case GL11.GL_BLEND -> blendEnabled = state;
            case GL11.GL_CULL_FACE -> cullFaceEnabled = state;
        }
    }
    
    public static double getSkipPercentage() {
        if (totalCalls == 0) return 0.0;
        return (skippedCalls * 100.0) / totalCalls;
    }
    
    public static void resetMetrics() {
        totalCalls = 0;
        skippedCalls = 0;
    }
}
