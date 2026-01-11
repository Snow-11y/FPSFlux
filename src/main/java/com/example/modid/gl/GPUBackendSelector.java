package com.example.modid.gl;

import com.example.modid.FPSFlux;

/**
 * GPUBackendSelector - Selects and initializes the appropriate GPU backend.
 */
public final class GPUBackendSelector {
    
    public enum PreferredBackend {
        AUTO,       // Auto-detect best option
        OPENGL,     // Force OpenGL
        VULKAN      // Force Vulkan
    }
    
    private static GPUBackend activeBackend;
    
    /**
     * Initialize GPU backend.
     */
    public static GPUBackend initialize(PreferredBackend preferred) {
        if (activeBackend != null) {
            return activeBackend;
        }
        
        switch (preferred) {
            case VULKAN:
                activeBackend = tryVulkan();
                if (activeBackend == null) {
                    FPSFlux.LOGGER.warn("[GPU] Vulkan requested but unavailable, falling back to OpenGL");
                    activeBackend = tryOpenGL();
                }
                break;
                
            case OPENGL:
                activeBackend = tryOpenGL();
                break;
                
            case AUTO:
            default:
                // Try Vulkan first (better performance), fall back to OpenGL
                activeBackend = tryVulkan();
                if (activeBackend == null) {
                    activeBackend = tryOpenGL();
                }
                break;
        }
        
        if (activeBackend == null) {
            throw new RuntimeException("Failed to initialize any GPU backend");
        }
        
        FPSFlux.LOGGER.info("[GPU] Using {} backend: {}", 
            activeBackend.getType(), activeBackend.getVersionString());
        
        return activeBackend;
    }
    
    private static GPUBackend tryVulkan() {
        try {
            VulkanBackend vulkan = VulkanBackend.get();
            if (vulkan.initialize()) {
                return vulkan;
            }
        } catch (Exception e) {
            FPSFlux.LOGGER.debug("[GPU] Vulkan initialization failed: {}", e.getMessage());
        }
        return null;
    }
    
    private static GPUBackend tryOpenGL() {
        try {
            OpenGLBackend opengl = OpenGLBackend.get();
            if (opengl.initialize()) {
                return opengl;
            }
        } catch (Exception e) {
            FPSFlux.LOGGER.debug("[GPU] OpenGL initialization failed: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get active backend.
     */
    public static GPUBackend get() {
        if (activeBackend == null) {
            throw new IllegalStateException("GPU backend not initialized");
        }
        return activeBackend;
    }
    
    /**
     * Check if initialized.
     */
    public static boolean isInitialized() {
        return activeBackend != null;
    }
    
    /**
     * Get backend type.
     */
    public static GPUBackend.Type getType() {
        return activeBackend != null ? activeBackend.getType() : null;
    }
}
