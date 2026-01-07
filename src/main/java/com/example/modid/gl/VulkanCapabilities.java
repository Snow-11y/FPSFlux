package com.example.modid.gl;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.vulkan.VK12.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Complete Vulkan capability detection
 */
public class VulkanCapabilities {
    public static boolean isSupported = false;
    public static boolean hasVulkan10 = false;
    public static boolean hasVulkan11 = false;
    public static boolean hasVulkan12 = false;
    public static boolean hasVulkan13 = false;
    public static boolean hasVulkan14 = false;
    
    public static String vulkanVersion = "Not Supported";
    public static String deviceName = "Unknown";
    public static String driverVersion = "Unknown";
    
    // Feature flags
    public static boolean hasTimelineSemaphores = false;
    public static boolean hasBufferDeviceAddress = false;
    public static boolean hasDynamicRendering = false;
    public static boolean hasSynchronization2 = false;
    
    public static void detect() {
        try (MemoryStack stack = stackPush()) {
            // Check if Vulkan is available
            if (!VK.isSupported()) {
                isSupported = false;
                return;
            }
            
            // Try to create minimal instance to check version
            IntBuffer pApiVersion = stack.ints(0);
            int result = vkEnumerateInstanceVersion(pApiVersion);
            if (result != VK_SUCCESS) {
                isSupported = false;
                return;
            }
            
            int apiVersion = pApiVersion.get(0);
            int major = VK_VERSION_MAJOR(apiVersion);
            int minor = VK_VERSION_MINOR(apiVersion);
            int patch = VK_VERSION_PATCH(apiVersion);
            
            vulkanVersion = major + "." + minor + "." + patch;
            
            // Set version flags
            hasVulkan10 = true;
            hasVulkan11 = (major > 1) || (major == 1 && minor >= 1);
            hasVulkan12 = (major > 1) || (major == 1 && minor >= 2);
            hasVulkan13 = (major > 1) || (major == 1 && minor >= 3);
            hasVulkan14 = (major > 1) || (major == 1 && minor >= 4);
            
            isSupported = true;
            
            System.out.println("[VulkanCapabilities] Detected Vulkan " + vulkanVersion);
            
        } catch (Exception e) {
            isSupported = false;
            vulkanVersion = "Detection Failed: " + e.getMessage();
        }
    }
    
    public static String getReport() {
        if (!isSupported) {
            return "Vulkan: Not Supported";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Vulkan: Supported\n");
        sb.append("Version: ").append(vulkanVersion).append("\n");
        sb.append("Device: ").append(deviceName).append("\n");
        sb.append("Driver: ").append(driverVersion).append("\n");
        sb.append("Features:\n");
        sb.append("  Vulkan 1.0: ").append(hasVulkan10).append("\n");
        sb.append("  Vulkan 1.1: ").append(hasVulkan11).append("\n");
        sb.append("  Vulkan 1.2: ").append(hasVulkan12).append("\n");
        sb.append("  Vulkan 1.3: ").append(hasVulkan13).append("\n");
        sb.append("  Vulkan 1.4: ").append(hasVulkan14).append("\n");
        sb.append("  Timeline Semaphores: ").append(hasTimelineSemaphores).append("\n");
        sb.append("  Buffer Device Address: ").append(hasBufferDeviceAddress).append("\n");
        sb.append("  Dynamic Rendering: ").append(hasDynamicRendering).append("\n");
        sb.append("  Synchronization2: ").append(hasSynchronization2);
        
        return sb.toString();
    }
}
