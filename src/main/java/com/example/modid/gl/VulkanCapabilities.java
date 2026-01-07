package com.example.modid.gl;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Detect Vulkan support and version via LWJGL 3 Vulkan bindings
 * No external JAR needed - LWJGL 3.3.3+ includes Vulkan support
 */
public class VulkanCapabilities {
    private static boolean initialized = false;
    
    public static boolean isSupported = false;
    public static boolean hasVulkan10 = false;
    public static boolean hasVulkan11 = false;
    public static boolean hasVulkan12 = false;
    public static boolean hasVulkan13 = false;
    public static boolean hasVulkan14 = false;
    
    public static String vulkanVersion = "Not Supported";
    public static String deviceName = "Unknown";
    public static int detectedMajor = 0;
    public static int detectedMinor = 0;
    public static int detectedPatch = 0;
    
    public static void detect() {
        if (initialized) return;
        
        try {
            // Check if Vulkan library is loadable
            if (!checkVulkanAvailable()) {
                isSupported = false;
                initialized = true;
                return;
            }
            
            try (MemoryStack stack = MemoryStack.stackPush()) {
                // Create minimal instance for device query
                VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8Safe("FPSFlux"))
                    .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                    .pEngineName(stack.UTF8Safe("FPSFlux"))
                    .engineVersion(VK_MAKE_VERSION(1, 0, 0))
                    .apiVersion(VK_API_VERSION_1_0);
                
                VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo);
                
                PointerBuffer pInstance = stack.mallocPointer(1);
                int result = vkCreateInstance(createInfo, null, pInstance);
                
                if (result != VK_SUCCESS) {
                    System.err.println("[FPSFlux] vkCreateInstance failed: " + result);
                    isSupported = false;
                    initialized = true;
                    return;
                }
                
                VkInstance instance = new VkInstance(pInstance.get(0), createInfo);
                
                try {
                    // Enumerate physical devices
                    IntBuffer deviceCount = stack.ints(0);
                    vkEnumeratePhysicalDevices(instance, deviceCount, null);
                    
                    if (deviceCount.get(0) == 0) {
                        System.err.println("[FPSFlux] No Vulkan physical devices found");
                        isSupported = false;
                        return;
                    }
                    
                    PointerBuffer pDevices = stack.mallocPointer(deviceCount.get(0));
                    vkEnumeratePhysicalDevices(instance, deviceCount, pDevices);
                    
                    // Use first device (primary GPU)
                    VkPhysicalDevice physicalDevice = new VkPhysicalDevice(pDevices.get(0), instance);
                    
                    // Query device properties
                    VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
                    vkGetPhysicalDeviceProperties(physicalDevice, properties);
                    
                    deviceName = properties.deviceNameString();
                    int apiVersion = properties.apiVersion();
                    
                    // Parse version
                    detectedMajor = VK_VERSION_MAJOR(apiVersion);
                    detectedMinor = VK_VERSION_MINOR(apiVersion);
                    detectedPatch = VK_VERSION_PATCH(apiVersion);
                    vulkanVersion = detectedMajor + "." + detectedMinor + "." + detectedPatch;
                    
                    // Set version flags
                    isSupported = true;
                    hasVulkan10 = true;
                    hasVulkan11 = detectedMajor > 1 || (detectedMajor == 1 && detectedMinor >= 1);
                    hasVulkan12 = detectedMajor > 1 || (detectedMajor == 1 && detectedMinor >= 2);
                    hasVulkan13 = detectedMajor > 1 || (detectedMajor == 1 && detectedMinor >= 3);
                    hasVulkan14 = detectedMajor > 1 || (detectedMajor == 1 && detectedMinor >= 4);
                    
                    System.out.println("[FPSFlux] Vulkan detected: " + vulkanVersion + " on " + deviceName);
                    
                } finally {
                    // Always cleanup instance
                    vkDestroyInstance(instance, null);
                }
            }
            
        } catch (Throwable e) {
            System.err.println("[FPSFlux] Vulkan detection failed: " + e.getMessage());
            isSupported = false;
        }
        
        initialized = true;
    }
    
    private static boolean checkVulkanAvailable() {
        try {
            // Attempt to call a Vulkan function - will throw if unavailable
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer count = stack.ints(0);
                int result = vkEnumerateInstanceExtensionProperties((CharSequence) null, count, null);
                return result == VK_SUCCESS || result == VK_INCOMPLETE;
            }
        } catch (Throwable t) {
            // Vulkan library not available or not loadable
            return false;
        }
    }
    
    public static String getReport() {
        if (!initialized) detect();
        
        StringBuilder sb = new StringBuilder();
        sb.append("Vulkan: ").append(isSupported ? "Supported" : "Not Supported");
        sb.append(" | Version: ").append(vulkanVersion);
        sb.append(" | Device: ").append(deviceName);
        
        if (isSupported) {
            sb.append("\nFeatures: ");
            sb.append("VK1.0:").append(hasVulkan10).append(" ");
            sb.append("VK1.1:").append(hasVulkan11).append(" ");
            sb.append("VK1.2:").append(hasVulkan12).append(" ");
            sb.append("VK1.3:").append(hasVulkan13).append(" ");
            sb.append("VK1.4:").append(hasVulkan14);
        }
        
        return sb.toString();
    }
}
