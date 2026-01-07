package com.example.modid.gl;

import com.example.modid.FPSFlux;
import com.example.modid.gl.buffer.BufferOps;
import com.example.modid.gl.vulkan.*;

/**
 * VulkanManager - Vulkan backend translation layer
 * 
 * Translates OpenGL-style calls from renderers into Vulkan commands.
 * Vulkan 1.0+ is significantly faster than OpenGL on mobile/modern GPUs.
 * 
 * Flow: Minecraft Renderer (GL calls) → VulkanManager → Vulkan API → GPU
 * 
 * DISABLED BY DEFAULT - requires explicit config enable
 */
public class VulkanManager {
    private static BufferOps activeBackend = null;
    private static boolean initialized = false;
    private static boolean enabled = false;
    private static String backendName = "None";
    
    // Vulkan requires explicit enable
    private static boolean vulkanEnabled = false;
    private static String forcedVulkanVersion = "VK10";
    
    public static void initialize() {
        if (initialized) return;
        
        if (!vulkanEnabled) {
            FPSFlux.LOGGER.info("[VulkanManager] Vulkan backend disabled in config");
            initialized = true;
            return;
        }
        
        FPSFlux.LOGGER.info("[VulkanManager] Initializing Vulkan backend...");
        
        // Detect Vulkan support
        VulkanCapabilities.detect();
        
        if (!VulkanCapabilities.isSupported) {
            FPSFlux.LOGGER.warn("[VulkanManager] Vulkan not supported on this system");
            enabled = false;
            initialized = true;
            return;
        }
        
        // Select Vulkan version
        activeBackend = selectVulkanBackend();
        
        if (activeBackend != null) {
            enabled = true;
            backendName = activeBackend.getName();
            FPSFlux.LOGGER.info("[VulkanManager] Active Backend: {}", backendName);
        } else {
            enabled = false;
            FPSFlux.LOGGER.error("[VulkanManager] Failed to initialize Vulkan backend");
        }
        
        initialized = true;
    }
    
    private static BufferOps selectVulkanBackend() {
        // Select highest supported Vulkan version
        
        if (VulkanCapabilities.hasVulkan14) return new Vulkan14BufferOps();
        if (VulkanCapabilities.hasVulkan13) return new Vulkan13BufferOps();
        if (VulkanCapabilities.hasVulkan12) return new Vulkan12BufferOps();
        if (VulkanCapabilities.hasVulkan11) return new Vulkan11BufferOps();
        if (VulkanCapabilities.hasVulkan10) return new VulkanBufferOps();
        
        return null;
    }
    
    // Public API
    
    public static void enable() {
        vulkanEnabled = true;
    }
    
    public static void disable() {
        vulkanEnabled = false;
        enabled = false;
    }
    
    public static boolean isEnabled() {
        if (!initialized) initialize();
        return enabled;
    }
    
    public static BufferOps getBackend() {
        if (!initialized) initialize();
        return activeBackend;
    }
    
    public static String getBackendName() {
        if (!initialized) initialize();
        return backendName;
    }
    
    public static String getDetailedReport() {
        if (!initialized) initialize();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== VulkanManager Status ===\n");
        sb.append("Backend: ").append(backendName).append("\n");
        sb.append("Status: ").append(enabled ? "ACTIVE" : "DISABLED").append("\n");
        
        if (VulkanCapabilities.isSupported) {
            sb.append("\n").append(VulkanCapabilities.getReport()).append("\n");
        } else {
            sb.append("\nVulkan not supported on this system\n");
        }
        
        return sb.toString();
    }
}
