package com.example.modid.gl.vulkan;

import com.example.modid.FPSFlux;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRDynamicRendering.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.KHRSynchronization2.*;
import static org.lwjgl.vulkan.KHRTimelineSemaphore.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.*;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.*;
import static org.lwjgl.vulkan.KHRMaintenance4.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.lwjgl.vulkan.VK12.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Complete Vulkan Context - Full initialization with Vulkan 1.0-1.4 support
 * 
 * Fixed to work properly with VulkanCallMapperX
 */
public class VulkanContext {
    
    // ========================================================================
    // VERSION CONSTANTS
    // ========================================================================
    
    public static final int VULKAN_1_0 = VK_MAKE_VERSION(1, 0, 0);
    public static final int VULKAN_1_1 = VK_MAKE_VERSION(1, 1, 0);
    public static final int VULKAN_1_2 = VK_MAKE_VERSION(1, 2, 0);
    public static final int VULKAN_1_3 = VK_MAKE_VERSION(1, 3, 0);
    public static final int VULKAN_1_4 = VK_MAKE_VERSION(1, 4, 0);
    
    // ========================================================================
    // CORE VULKAN OBJECTS
    // ========================================================================
    
    public VkInstance instance;
    public long surface;
    public VkPhysicalDevice physicalDevice;
    public VkDevice device;
    
    // ========================================================================
    // VERSION AND FEATURES
    // ========================================================================
    
    public int vulkanVersion = VULKAN_1_0;
    public int instanceVersion = VULKAN_1_0;
    public int deviceApiVersion = VULKAN_1_0;
    
    // Feature support flags
    private boolean timelineSemaphoresSupported = false;
    private boolean bufferDeviceAddressSupported = false;
    private boolean descriptorIndexingSupported = false;
    private boolean dynamicRenderingSupported = false;
    private boolean synchronization2Supported = false;
    private boolean maintenance4Supported = false;
    private boolean maintenance5Supported = false;
    
    // Extension tracking
    private final Set<String> enabledInstanceExtensions = new HashSet<>();
    private final Set<String> enabledDeviceExtensions = new HashSet<>();
    private final Set<String> availableDeviceExtensions = new HashSet<>();
    
    // Physical device features
    private VkPhysicalDeviceFeatures physicalDeviceFeatures;
    private VkPhysicalDeviceProperties physicalDeviceProperties;
    private VkPhysicalDeviceMemoryProperties memoryProperties;
    
    // ========================================================================
    // QUEUE HANDLES
    // ========================================================================
    
    public VkQueue graphicsQueue;
    public VkQueue presentQueue;
    public VkQueue computeQueue;
    public VkQueue transferQueue;
    public int graphicsQueueFamily = -1;
    public int presentQueueFamily = -1;
    public int computeQueueFamily = -1;
    public int transferQueueFamily = -1;
    
    // ========================================================================
    // SWAPCHAIN
    // ========================================================================
    
    public long swapchain = VK_NULL_HANDLE;
    public List<Long> swapchainImages = new ArrayList<>();
    public List<Long> swapchainImageViews = new ArrayList<>();
    public List<Long> swapchainFramebuffers = new ArrayList<>();
    public int swapchainImageFormat;
    public VkExtent2D swapchainExtent;
    public int currentImageIndex = 0;
    private boolean swapchainNeedsRecreation = false;
    
    // ========================================================================
    // RENDER PASS
    // ========================================================================
    
    public long renderPass = VK_NULL_HANDLE;
    
    // ========================================================================
    // PIPELINE CACHE
    // ========================================================================
    
    public long pipelineCache = VK_NULL_HANDLE;
    
    // ========================================================================
    // DESCRIPTOR MANAGEMENT
    // ========================================================================
    
    public long descriptorSetLayout = VK_NULL_HANDLE;
    public long descriptorPool = VK_NULL_HANDLE;
    public List<Long> descriptorSets = new ArrayList<>();
    
    // ========================================================================
    // COMMAND POOLS AND BUFFERS
    // ========================================================================
    
    public long commandPool = VK_NULL_HANDLE;
    public long transferCommandPool = VK_NULL_HANDLE;
    public VkCommandBuffer[] commandBuffers;
    
    // ========================================================================
    // SYNCHRONIZATION
    // ========================================================================
    
    public long imageAvailableSemaphore = VK_NULL_HANDLE;
    public long renderFinishedSemaphore = VK_NULL_HANDLE;
    public long inFlightFence = VK_NULL_HANDLE;
    
    // Multiple frames in flight support
    public static final int MAX_FRAMES_IN_FLIGHT = 2;
    public long[] imageAvailableSemaphores;
    public long[] renderFinishedSemaphores;
    public long[] inFlightFences;
    public int currentFrame = 0;
    
    // ========================================================================
    // DEPTH BUFFER
    // ========================================================================
    
    public long depthImage = VK_NULL_HANDLE;
    public long depthImageMemory = VK_NULL_HANDLE;
    public long depthImageView = VK_NULL_HANDLE;
    public int depthFormat;
    
    // ========================================================================
    // CONFIGURATION
    // ========================================================================
    
    private static final boolean ENABLE_VALIDATION = true;
    private static final String[] VALIDATION_LAYERS = {"VK_LAYER_KHRONOS_validation"};
    
    // Required device extensions
    private static final String[] REQUIRED_DEVICE_EXTENSIONS = {
        VK_KHR_SWAPCHAIN_EXTENSION_NAME
    };
    
    // Optional device extensions for features
    private static final String[] OPTIONAL_DEVICE_EXTENSIONS = {
        VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME,
        VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME,
        VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME,
        VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME,
        VK_KHR_SYNCHRONIZATION_2_EXTENSION_NAME,
        VK_KHR_MAINTENANCE_4_EXTENSION_NAME,
        "VK_KHR_maintenance5"
    };
    
    private long windowHandle;
    private boolean initialized = false;
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    /**
     * Initialize complete Vulkan context
     */
    public void initialize(long glfwWindow) {
        if (initialized) {
            FPSFlux.LOGGER.warn("[VulkanContext] Already initialized");
            return;
        }
        
        this.windowHandle = glfwWindow;
        
        try {
            FPSFlux.LOGGER.info("[VulkanContext] Starting Vulkan initialization...");
            
            // Detect available Vulkan version
            detectInstanceVersion();
            
            try (MemoryStack stack = stackPush()) {
                createInstance(stack);
                createSurface(stack);
                pickPhysicalDevice(stack);
                detectDeviceFeatures(stack);
                createLogicalDevice(stack);
                createSwapchain(stack);
                createImageViews(stack);
                createRenderPass(stack);
                createDepthResources(stack);
                createFramebuffers(stack);
                createCommandPool(stack);
                createCommandBuffers(stack);
                createPipelineCache(stack);
                createSyncObjects(stack);
            }
            
            initialized = true;
            
            FPSFlux.LOGGER.info("[VulkanContext] Initialization complete");
            FPSFlux.LOGGER.info("[VulkanContext] Vulkan Version: {}.{}.{}", 
                VK_VERSION_MAJOR(vulkanVersion),
                VK_VERSION_MINOR(vulkanVersion),
                VK_VERSION_PATCH(vulkanVersion));
            logFeatureSupport();
            
        } catch (Exception e) {
            FPSFlux.LOGGER.error("[VulkanContext] Initialization failed: {}", e.getMessage());
            e.printStackTrace();
            cleanup();
            throw new RuntimeException("Vulkan initialization failed", e);
        }
    }
    
    private void detectInstanceVersion() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer pApiVersion = stack.ints(0);
            
            // vkEnumerateInstanceVersion is Vulkan 1.1+
            // If not available, assume 1.0
            try {
                int result = vkEnumerateInstanceVersion(pApiVersion);
                if (result == VK_SUCCESS) {
                    instanceVersion = pApiVersion.get(0);
                } else {
                    instanceVersion = VULKAN_1_0;
                }
            } catch (Exception e) {
                instanceVersion = VULKAN_1_0;
            }
            
            FPSFlux.LOGGER.info("[VulkanContext] Instance API version: {}.{}.{}", 
                VK_VERSION_MAJOR(instanceVersion),
                VK_VERSION_MINOR(instanceVersion),
                VK_VERSION_PATCH(instanceVersion));
        }
    }
    
    // ========================================================================
    // INSTANCE CREATION
    // ========================================================================
    
    private void createInstance(MemoryStack stack) {
        // Determine API version to request
        int requestedApiVersion;
        if (instanceVersion >= VULKAN_1_3) {
            requestedApiVersion = VK_API_VERSION_1_3;
        } else if (instanceVersion >= VULKAN_1_2) {
            requestedApiVersion = VK_API_VERSION_1_2;
        } else if (instanceVersion >= VULKAN_1_1) {
            requestedApiVersion = VK_API_VERSION_1_1;
        } else {
            requestedApiVersion = VK_API_VERSION_1_0;
        }
        
        VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
            .pApplicationName(stack.UTF8Safe("FPSFlux Minecraft"))
            .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
            .pEngineName(stack.UTF8Safe("FPSFlux GL-to-Vulkan"))
            .engineVersion(VK_MAKE_VERSION(1, 0, 0))
            .apiVersion(requestedApiVersion);
        
        // Get required extensions from GLFW
        PointerBuffer glfwExtensions = glfwGetRequiredInstanceExtensions();
        if (glfwExtensions == null) {
            throw new RuntimeException("GLFW required extensions not available");
        }
        
        // Build extension list
        List<String> extensionList = new ArrayList<>();
        for (int i = 0; i < glfwExtensions.remaining(); i++) {
            extensionList.add(memUTF8(glfwExtensions.get(i)));
        }
        
        if (ENABLE_VALIDATION) {
            extensionList.add(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
        }
        
        PointerBuffer extensions = stack.mallocPointer(extensionList.size());
        for (String ext : extensionList) {
            extensions.put(stack.UTF8(ext));
            enabledInstanceExtensions.add(ext);
        }
        extensions.flip();
        
        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
            .pApplicationInfo(appInfo)
            .ppEnabledExtensionNames(extensions);
        
        if (ENABLE_VALIDATION && checkValidationLayerSupport(stack)) {
            createInfo.ppEnabledLayerNames(asPointerBuffer(stack, VALIDATION_LAYERS));
            FPSFlux.LOGGER.info("[VulkanContext] Validation layers enabled");
        }
        
        PointerBuffer pInstance = stack.mallocPointer(1);
        int result = vkCreateInstance(createInfo, null, pInstance);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create Vulkan instance: " + result);
        }
        
        instance = new VkInstance(pInstance.get(0), createInfo);
        FPSFlux.LOGGER.info("[VulkanContext] Vulkan instance created");
    }
    
    private boolean checkValidationLayerSupport(MemoryStack stack) {
        IntBuffer layerCount = stack.ints(0);
        vkEnumerateInstanceLayerProperties(layerCount, null);
        
        if (layerCount.get(0) == 0) return false;
        
        VkLayerProperties.Buffer availableLayers = VkLayerProperties.malloc(layerCount.get(0), stack);
        vkEnumerateInstanceLayerProperties(layerCount, availableLayers);
        
        for (String layerName : VALIDATION_LAYERS) {
            boolean found = false;
            for (int i = 0; i < availableLayers.capacity(); i++) {
                if (layerName.equals(availableLayers.get(i).layerNameString())) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
    
    // ========================================================================
    // SURFACE CREATION
    // ========================================================================
    
    private void createSurface(MemoryStack stack) {
        LongBuffer pSurface = stack.longs(VK_NULL_HANDLE);
        int result = glfwCreateWindowSurface(instance, windowHandle, null, pSurface);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create window surface: " + result);
        }
        surface = pSurface.get(0);
        FPSFlux.LOGGER.info("[VulkanContext] Window surface created");
    }
    
    // ========================================================================
    // PHYSICAL DEVICE SELECTION
    // ========================================================================
    
    private void pickPhysicalDevice(MemoryStack stack) {
        IntBuffer deviceCount = stack.ints(0);
        vkEnumeratePhysicalDevices(instance, deviceCount, null);
        
        if (deviceCount.get(0) == 0) {
            throw new RuntimeException("No Vulkan-capable GPU found");
        }
        
        PointerBuffer pDevices = stack.mallocPointer(deviceCount.get(0));
        vkEnumeratePhysicalDevices(instance, deviceCount, pDevices);
        
        VkPhysicalDevice bestDevice = null;
        int bestScore = -1;
        
        for (int i = 0; i < pDevices.capacity(); i++) {
            VkPhysicalDevice device = new VkPhysicalDevice(pDevices.get(i), instance);
            int score = rateDevice(device, stack);
            if (score > bestScore) {
                bestScore = score;
                bestDevice = device;
            }
        }
        
        if (bestDevice == null || bestScore < 0) {
            throw new RuntimeException("No suitable GPU found");
        }
        
        physicalDevice = bestDevice;
        
        // Get device properties
        physicalDeviceProperties = VkPhysicalDeviceProperties.malloc();
        vkGetPhysicalDeviceProperties(physicalDevice, physicalDeviceProperties);
        
        // Get memory properties
        memoryProperties = VkPhysicalDeviceMemoryProperties.malloc();
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
        
        // Determine actual Vulkan version supported by device
        deviceApiVersion = physicalDeviceProperties.apiVersion();
        vulkanVersion = Math.min(instanceVersion, deviceApiVersion);
        
        // Enumerate available device extensions
        enumerateDeviceExtensions(stack);
        
        FPSFlux.LOGGER.info("[VulkanContext] Selected GPU: {}", physicalDeviceProperties.deviceNameString());
        FPSFlux.LOGGER.info("[VulkanContext] Device API version: {}.{}.{}", 
            VK_VERSION_MAJOR(deviceApiVersion),
            VK_VERSION_MINOR(deviceApiVersion),
            VK_VERSION_PATCH(deviceApiVersion));
    }
    
    private int rateDevice(VkPhysicalDevice device, MemoryStack stack) {
        VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.malloc(stack);
        vkGetPhysicalDeviceProperties(device, props);
        
        VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.malloc(stack);
        vkGetPhysicalDeviceFeatures(device, features);
        
        // Check queue families
        QueueFamilyIndices indices = findQueueFamilies(device, stack);
        if (!indices.isComplete()) return -1;
        
        // Check extension support
        if (!checkDeviceExtensionSupport(device, REQUIRED_DEVICE_EXTENSIONS, stack)) return -1;
        
        // Check swapchain support
        SwapchainSupportDetails details = querySwapchainSupport(device, stack);
        if (details.formats == null || details.formats.capacity() == 0 ||
            details.presentModes == null || details.presentModes.capacity() == 0) {
            return -1;
        }
        
        int score = 0;
        
        // Prefer discrete GPUs
        if (props.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
            score += 10000;
        } else if (props.deviceType() == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) {
            score += 1000;
        }
        
        // Higher API version is better
        score += VK_VERSION_MINOR(props.apiVersion()) * 100;
        
        // Anisotropic filtering support
        if (features.samplerAnisotropy()) {
            score += 50;
        }
        
        // Geometry shader support
        if (features.geometryShader()) {
            score += 20;
        }
        
        // Tessellation shader support
        if (features.tessellationShader()) {
            score += 20;
        }
        
        // Max image dimension (larger is better for textures)
        score += props.limits().maxImageDimension2D() / 1000;
        
        return score;
    }
    
    private void enumerateDeviceExtensions(MemoryStack stack) {
        IntBuffer extensionCount = stack.ints(0);
        vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, extensionCount, null);
        
        if (extensionCount.get(0) > 0) {
            VkExtensionProperties.Buffer availableExtensions = 
                VkExtensionProperties.malloc(extensionCount.get(0), stack);
            vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, extensionCount, availableExtensions);
            
            for (int i = 0; i < availableExtensions.capacity(); i++) {
                availableDeviceExtensions.add(availableExtensions.get(i).extensionNameString());
            }
        }
    }
    
    private boolean checkDeviceExtensionSupport(VkPhysicalDevice device, String[] required, MemoryStack stack) {
        IntBuffer extensionCount = stack.ints(0);
        vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, null);
        
        VkExtensionProperties.Buffer availableExtensions = 
            VkExtensionProperties.malloc(extensionCount.get(0), stack);
        vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, availableExtensions);
        
        Set<String> available = new HashSet<>();
        for (int i = 0; i < availableExtensions.capacity(); i++) {
            available.add(availableExtensions.get(i).extensionNameString());
        }
        
        for (String req : required) {
            if (!available.contains(req)) return false;
        }
        return true;
    }
    
    // ========================================================================
    // FEATURE DETECTION
    // ========================================================================
    
    private void detectDeviceFeatures(MemoryStack stack) {
        // Get basic features
        physicalDeviceFeatures = VkPhysicalDeviceFeatures.malloc();
        vkGetPhysicalDeviceFeatures(physicalDevice, physicalDeviceFeatures);
        
        // Vulkan 1.2+ features (timeline semaphores, buffer device address, descriptor indexing)
        if (vulkanVersion >= VULKAN_1_2) {
            VkPhysicalDeviceVulkan12Features features12 = VkPhysicalDeviceVulkan12Features.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES);
            
            VkPhysicalDeviceFeatures2 features2 = VkPhysicalDeviceFeatures2.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2)
                .pNext(features12.address());
            
            vkGetPhysicalDeviceFeatures2(physicalDevice, features2);
            
            timelineSemaphoresSupported = features12.timelineSemaphore();
            bufferDeviceAddressSupported = features12.bufferDeviceAddress();
            descriptorIndexingSupported = features12.descriptorIndexing();
        } else {
            // Check via extensions for Vulkan 1.0/1.1
            timelineSemaphoresSupported = hasExtension(VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME);
            bufferDeviceAddressSupported = hasExtension(VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME);
            descriptorIndexingSupported = hasExtension(VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME);
        }
        
        // Vulkan 1.3+ features (dynamic rendering, synchronization2)
        if (vulkanVersion >= VULKAN_1_3) {
            VkPhysicalDeviceVulkan13Features features13 = VkPhysicalDeviceVulkan13Features.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES);
            
            VkPhysicalDeviceFeatures2 features2 = VkPhysicalDeviceFeatures2.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2)
                .pNext(features13.address());
            
            vkGetPhysicalDeviceFeatures2(physicalDevice, features2);
            
            dynamicRenderingSupported = features13.dynamicRendering();
            synchronization2Supported = features13.synchronization2();
            maintenance4Supported = features13.maintenance4();
        } else {
            // Check via extensions
            dynamicRenderingSupported = hasExtension(VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME);
            synchronization2Supported = hasExtension(VK_KHR_SYNCHRONIZATION_2_EXTENSION_NAME);
            maintenance4Supported = hasExtension(VK_KHR_MAINTENANCE_4_EXTENSION_NAME);
        }
        
        // Vulkan 1.4 features (maintenance5)
        if (vulkanVersion >= VULKAN_1_4) {
            maintenance5Supported = true; // Core in 1.4
        } else {
            maintenance5Supported = hasExtension("VK_KHR_maintenance5");
        }
    }
    
    private void logFeatureSupport() {
        FPSFlux.LOGGER.info("[VulkanContext] Feature Support:");
        FPSFlux.LOGGER.info("  Timeline Semaphores: {}", timelineSemaphoresSupported);
        FPSFlux.LOGGER.info("  Buffer Device Address: {}", bufferDeviceAddressSupported);
        FPSFlux.LOGGER.info("  Descriptor Indexing: {}", descriptorIndexingSupported);
        FPSFlux.LOGGER.info("  Dynamic Rendering: {}", dynamicRenderingSupported);
        FPSFlux.LOGGER.info("  Synchronization2: {}", synchronization2Supported);
        FPSFlux.LOGGER.info("  Maintenance4: {}", maintenance4Supported);
        FPSFlux.LOGGER.info("  Maintenance5: {}", maintenance5Supported);
        FPSFlux.LOGGER.info("  Anisotropic Filtering: {}", physicalDeviceFeatures.samplerAnisotropy());
    }
    
    // ========================================================================
    // FEATURE QUERY METHODS (Used by VulkanCallMapperX)
    // ========================================================================
    
    public boolean supportsTimelineSemaphores() {
        return timelineSemaphoresSupported;
    }
    
    public boolean supportsBufferDeviceAddress() {
        return bufferDeviceAddressSupported;
    }
    
    public boolean supportsDescriptorIndexing() {
        return descriptorIndexingSupported;
    }
    
    public boolean supportsDynamicRendering() {
        return dynamicRenderingSupported;
    }
    
    public boolean supportsSynchronization2() {
        return synchronization2Supported;
    }
    
    public boolean supportsMaintenance4() {
        return maintenance4Supported;
    }
    
    public boolean supportsMaintenance5() {
        return maintenance5Supported;
    }
    
    public boolean hasExtension(String extensionName) {
        return availableDeviceExtensions.contains(extensionName) || 
               enabledDeviceExtensions.contains(extensionName);
    }
    
    public boolean supportsAnisotropy() {
        return physicalDeviceFeatures != null && physicalDeviceFeatures.samplerAnisotropy();
    }
    
    public float getMaxAnisotropy() {
        if (physicalDeviceProperties != null) {
            return physicalDeviceProperties.limits().maxSamplerAnisotropy();
        }
        return 1.0f;
    }
    
    // ========================================================================
    // QUEUE FAMILY INDICES
    // ========================================================================
    
    private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device, MemoryStack stack) {
        QueueFamilyIndices indices = new QueueFamilyIndices();
        
        IntBuffer queueFamilyCount = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);
        
        VkQueueFamilyProperties.Buffer queueFamilies = 
            VkQueueFamilyProperties.malloc(queueFamilyCount.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);
        
        IntBuffer presentSupport = stack.ints(VK_FALSE);
        
        for (int i = 0; i < queueFamilies.capacity(); i++) {
            VkQueueFamilyProperties queueFamily = queueFamilies.get(i);
            int flags = queueFamily.queueFlags();
            
            // Graphics queue
            if ((flags & VK_QUEUE_GRAPHICS_BIT) != 0 && indices.graphicsFamily < 0) {
                indices.graphicsFamily = i;
            }
            
            // Compute queue (prefer dedicated)
            if ((flags & VK_QUEUE_COMPUTE_BIT) != 0) {
                if (indices.computeFamily < 0 || (flags & VK_QUEUE_GRAPHICS_BIT) == 0) {
                    indices.computeFamily = i;
                }
            }
            
            // Transfer queue (prefer dedicated)
            if ((flags & VK_QUEUE_TRANSFER_BIT) != 0) {
                if (indices.transferFamily < 0 || 
                    ((flags & VK_QUEUE_GRAPHICS_BIT) == 0 && (flags & VK_QUEUE_COMPUTE_BIT) == 0)) {
                    indices.transferFamily = i;
                }
            }
            
            // Present queue
            vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, presentSupport);
            if (presentSupport.get(0) == VK_TRUE && indices.presentFamily < 0) {
                indices.presentFamily = i;
            }
        }
        
        // Fallback: use graphics queue for compute/transfer if not found
        if (indices.computeFamily < 0) indices.computeFamily = indices.graphicsFamily;
        if (indices.transferFamily < 0) indices.transferFamily = indices.graphicsFamily;
        
        return indices;
    }
    
    // ========================================================================
    // LOGICAL DEVICE CREATION
    // ========================================================================
    
    private void createLogicalDevice(MemoryStack stack) {
        QueueFamilyIndices indices = findQueueFamilies(physicalDevice, stack);
        graphicsQueueFamily = indices.graphicsFamily;
        presentQueueFamily = indices.presentFamily;
        computeQueueFamily = indices.computeFamily;
        transferQueueFamily = indices.transferFamily;
        
        // Collect unique queue families
        Set<Integer> uniqueQueueFamilies = new HashSet<>();
        uniqueQueueFamilies.add(indices.graphicsFamily);
        uniqueQueueFamilies.add(indices.presentFamily);
        if (indices.computeFamily != indices.graphicsFamily) {
            uniqueQueueFamilies.add(indices.computeFamily);
        }
        if (indices.transferFamily != indices.graphicsFamily && 
            indices.transferFamily != indices.computeFamily) {
            uniqueQueueFamilies.add(indices.transferFamily);
        }
        
        VkDeviceQueueCreateInfo.Buffer queueCreateInfos = 
            VkDeviceQueueCreateInfo.calloc(uniqueQueueFamilies.size(), stack);
        
        int i = 0;
        for (int family : uniqueQueueFamilies) {
            queueCreateInfos.get(i++)
                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(family)
                .pQueuePriorities(stack.floats(1.0f));
        }
        
        // Build extension list
        List<String> extensionList = new ArrayList<>();
        for (String ext : REQUIRED_DEVICE_EXTENSIONS) {
            extensionList.add(ext);
        }
        
        // Add optional extensions if available
        for (String ext : OPTIONAL_DEVICE_EXTENSIONS) {
            if (availableDeviceExtensions.contains(ext)) {
                extensionList.add(ext);
                FPSFlux.LOGGER.debug("[VulkanContext] Enabling optional extension: {}", ext);
            }
        }
        
        PointerBuffer extensions = stack.mallocPointer(extensionList.size());
        for (String ext : extensionList) {
            extensions.put(stack.UTF8(ext));
            enabledDeviceExtensions.add(ext);
        }
        extensions.flip();
        
        // Device features
        VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack)
            .samplerAnisotropy(physicalDeviceFeatures.samplerAnisotropy())
            .geometryShader(physicalDeviceFeatures.geometryShader())
            .tessellationShader(physicalDeviceFeatures.tessellationShader())
            .multiDrawIndirect(physicalDeviceFeatures.multiDrawIndirect())
            .drawIndirectFirstInstance(physicalDeviceFeatures.drawIndirectFirstInstance())
            .depthClamp(physicalDeviceFeatures.depthClamp())
            .depthBiasClamp(physicalDeviceFeatures.depthBiasClamp())
            .fillModeNonSolid(physicalDeviceFeatures.fillModeNonSolid())
            .wideLines(physicalDeviceFeatures.wideLines())
            .largePoints(physicalDeviceFeatures.largePoints())
            .logicOp(physicalDeviceFeatures.logicOp())
            .shaderStorageImageExtendedFormats(physicalDeviceFeatures.shaderStorageImageExtendedFormats());
        
        VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
            .pQueueCreateInfos(queueCreateInfos)
            .pEnabledFeatures(deviceFeatures)
            .ppEnabledExtensionNames(extensions);
        
        // Chain Vulkan 1.2+ features if supported
        if (vulkanVersion >= VULKAN_1_2) {
            VkPhysicalDeviceVulkan12Features features12 = VkPhysicalDeviceVulkan12Features.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES)
                .timelineSemaphore(timelineSemaphoresSupported)
                .bufferDeviceAddress(bufferDeviceAddressSupported)
                .descriptorIndexing(descriptorIndexingSupported);
            
            VkPhysicalDeviceVulkan11Features features11 = VkPhysicalDeviceVulkan11Features.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES)
                .pNext(features12.address());
            
            createInfo.pNext(features11.address());
            
            // Chain Vulkan 1.3+ features if supported
            if (vulkanVersion >= VULKAN_1_3) {
                VkPhysicalDeviceVulkan13Features features13 = VkPhysicalDeviceVulkan13Features.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES)
                    .dynamicRendering(dynamicRenderingSupported)
                    .synchronization2(synchronization2Supported)
                    .maintenance4(maintenance4Supported);
                
                features12.pNext(features13.address());
            }
        }
        
        if (ENABLE_VALIDATION) {
            createInfo.ppEnabledLayerNames(asPointerBuffer(stack, VALIDATION_LAYERS));
        }
        
        PointerBuffer pDevice = stack.pointers(VK_NULL_HANDLE);
        int result = vkCreateDevice(physicalDevice, createInfo, null, pDevice);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create logical device: " + result);
        }
        
        device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);
        
        // Get queue handles
        PointerBuffer pQueue = stack.pointers(VK_NULL_HANDLE);
        
        vkGetDeviceQueue(device, graphicsQueueFamily, 0, pQueue);
        graphicsQueue = new VkQueue(pQueue.get(0), device);
        
        vkGetDeviceQueue(device, presentQueueFamily, 0, pQueue);
        presentQueue = new VkQueue(pQueue.get(0), device);
        
        vkGetDeviceQueue(device, computeQueueFamily, 0, pQueue);
        computeQueue = new VkQueue(pQueue.get(0), device);
        
        vkGetDeviceQueue(device, transferQueueFamily, 0, pQueue);
        transferQueue = new VkQueue(pQueue.get(0), device);
        
        FPSFlux.LOGGER.info("[VulkanContext] Logical device created");
    }
    
    // ========================================================================
    // SWAPCHAIN
    // ========================================================================
    
    private void createSwapchain(MemoryStack stack) {
        SwapchainSupportDetails details = querySwapchainSupport(physicalDevice, stack);
        
        VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(details.formats);
        int presentMode = chooseSwapPresentMode(details.presentModes);
        VkExtent2D extent = chooseSwapExtent(details.capabilities, stack);
        
        int imageCount = details.capabilities.minImageCount() + 1;
        if (details.capabilities.maxImageCount() > 0 && imageCount > details.capabilities.maxImageCount()) {
            imageCount = details.capabilities.maxImageCount();
        }
        
        VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
            .surface(surface)
            .minImageCount(imageCount)
            .imageFormat(surfaceFormat.format())
            .imageColorSpace(surfaceFormat.colorSpace())
            .imageExtent(extent)
            .imageArrayLayers(1)
            .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT);
        
        if (graphicsQueueFamily != presentQueueFamily) {
            createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                .pQueueFamilyIndices(stack.ints(graphicsQueueFamily, presentQueueFamily));
        } else {
            createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
        }
        
        createInfo
            .preTransform(details.capabilities.currentTransform())
            .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
            .presentMode(presentMode)
            .clipped(true)
            .oldSwapchain(swapchain); // Use old swapchain for recreation
        
        LongBuffer pSwapchain = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateSwapchainKHR(device, createInfo, null, pSwapchain);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create swapchain: " + result);
        }
        
        // Destroy old swapchain if recreating
        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchain, null);
        }
        
        swapchain = pSwapchain.get(0);
        swapchainImageFormat = surfaceFormat.format();
        
        // Store extent (need to allocate outside stack)
        if (swapchainExtent == null) {
            swapchainExtent = VkExtent2D.malloc();
        }
        swapchainExtent.set(extent);
        
        // Get swapchain images
        IntBuffer pImageCount = stack.ints(0);
        vkGetSwapchainImagesKHR(device, swapchain, pImageCount, null);
        LongBuffer pSwapchainImages = stack.mallocLong(pImageCount.get(0));
        vkGetSwapchainImagesKHR(device, swapchain, pImageCount, pSwapchainImages);
        
        swapchainImages.clear();
        for (int i = 0; i < pSwapchainImages.capacity(); i++) {
            swapchainImages.add(pSwapchainImages.get(i));
        }
        
        swapchainNeedsRecreation = false;
        
        FPSFlux.LOGGER.info("[VulkanContext] Swapchain created: {}x{} with {} images",
            swapchainExtent.width(), swapchainExtent.height(), swapchainImages.size());
    }
    
    private SwapchainSupportDetails querySwapchainSupport(VkPhysicalDevice device, MemoryStack stack) {
        SwapchainSupportDetails details = new SwapchainSupportDetails();
        
        details.capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
        vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, details.capabilities);
        
        IntBuffer count = stack.ints(0);
        vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, null);
        
        if (count.get(0) != 0) {
            details.formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
            vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, count, details.formats);
        }
        
        vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, count, null);
        
        if (count.get(0) != 0) {
            details.presentModes = stack.mallocInt(count.get(0));
            vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, count, details.presentModes);
        }
        
        return details;
    }
    
    private VkSurfaceFormatKHR chooseSwapSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
        // Prefer SRGB for color accuracy
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB && 
                format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
        // Fallback to UNORM
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK_FORMAT_B8G8R8A8_UNORM) {
                return format;
            }
        }
        return formats.get(0);
    }
    
    private int chooseSwapPresentMode(IntBuffer presentModes) {
        // Prefer mailbox (triple buffering) for low latency
        for (int i = 0; i < presentModes.capacity(); i++) {
            if (presentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                return VK_PRESENT_MODE_MAILBOX_KHR;
            }
        }
        // Fallback to FIFO (vsync, always supported)
        return VK_PRESENT_MODE_FIFO_KHR;
    }
    
    private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return capabilities.currentExtent();
        }
        
        IntBuffer width = stack.ints(0);
        IntBuffer height = stack.ints(0);
        glfwGetFramebufferSize(windowHandle, width, height);
        
        VkExtent2D actualExtent = VkExtent2D.malloc(stack).set(width.get(0), height.get(0));
        
        actualExtent.width(clamp(actualExtent.width(), 
            capabilities.minImageExtent().width(), capabilities.maxImageExtent().width()));
        actualExtent.height(clamp(actualExtent.height(), 
            capabilities.minImageExtent().height(), capabilities.maxImageExtent().height()));
        
        return actualExtent;
    }
    
    /**
     * Recreate swapchain (call on window resize or VK_ERROR_OUT_OF_DATE_KHR)
     */
    public void recreateSwapchain() {
        // Wait for window to have valid size
        try (MemoryStack stack = stackPush()) {
            IntBuffer width = stack.ints(0);
            IntBuffer height = stack.ints(0);
            glfwGetFramebufferSize(windowHandle, width, height);
            
            while (width.get(0) == 0 || height.get(0) == 0) {
                glfwGetFramebufferSize(windowHandle, width, height);
                glfwWaitEvents();
            }
        }
        
        vkDeviceWaitIdle(device);
        
        // Cleanup old resources
        cleanupSwapchainResources();
        
        try (MemoryStack stack = stackPush()) {
            createSwapchain(stack);
            createImageViews(stack);
            createDepthResources(stack);
            createFramebuffers(stack);
        }
        
        FPSFlux.LOGGER.info("[VulkanContext] Swapchain recreated");
    }
    
    private void cleanupSwapchainResources() {
        // Cleanup framebuffers
        for (long framebuffer : swapchainFramebuffers) {
            vkDestroyFramebuffer(device, framebuffer, null);
        }
        swapchainFramebuffers.clear();
        
        // Cleanup depth resources
        if (depthImageView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, depthImageView, null);
            depthImageView = VK_NULL_HANDLE;
        }
        if (depthImage != VK_NULL_HANDLE) {
            vkDestroyImage(device, depthImage, null);
            depthImage = VK_NULL_HANDLE;
        }
        if (depthImageMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, depthImageMemory, null);
            depthImageMemory = VK_NULL_HANDLE;
        }
        
        // Cleanup image views
        for (long imageView : swapchainImageViews) {
            vkDestroyImageView(device, imageView, null);
        }
        swapchainImageViews.clear();
    }
    
    // ========================================================================
    // IMAGE VIEWS
    // ========================================================================
    
    private void createImageViews(MemoryStack stack) {
        swapchainImageViews.clear();
        
        for (long image : swapchainImages) {
            long imageView = createImageView(image, swapchainImageFormat, VK_IMAGE_ASPECT_COLOR_BIT, 1, stack);
            swapchainImageViews.add(imageView);
        }
        
        FPSFlux.LOGGER.debug("[VulkanContext] Created {} image views", swapchainImageViews.size());
    }
    
    public long createImageView(long image, int format, int aspectFlags, int mipLevels, MemoryStack stack) {
        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(image)
            .viewType(VK_IMAGE_VIEW_TYPE_2D)
            .format(format);
        
        viewInfo.subresourceRange()
            .aspectMask(aspectFlags)
            .baseMipLevel(0)
            .levelCount(mipLevels)
            .baseArrayLayer(0)
            .layerCount(1);
        
        LongBuffer pImageView = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateImageView(device, viewInfo, null, pImageView);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create image view: " + result);
        }
        
        return pImageView.get(0);
    }
    
    // Convenience overload
    private long createImageView(long image, int format, int aspectFlags, MemoryStack stack) {
        return createImageView(image, format, aspectFlags, 1, stack);
    }
    
    // ========================================================================
    // RENDER PASS
    // ========================================================================
    
    private void createRenderPass(MemoryStack stack) {
        depthFormat = findDepthFormat(stack);
        
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
        
        // Color attachment
        attachments.get(0)
            .format(swapchainImageFormat)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        
        // Depth attachment
        attachments.get(1)
            .format(depthFormat)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        
        VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack)
            .attachment(0)
            .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        
        VkAttachmentReference depthAttachmentRef = VkAttachmentReference.calloc(stack)
            .attachment(1)
            .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        
        VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
            .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
            .colorAttachmentCount(1)
            .pColorAttachments(colorAttachmentRef)
            .pDepthStencilAttachment(depthAttachmentRef);
        
        VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
            .srcSubpass(VK_SUBPASS_EXTERNAL)
            .dstSubpass(0)
            .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
            .srcAccessMask(0)
            .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
            .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
        
        VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            .pAttachments(attachments)
            .pSubpasses(subpass)
            .pDependencies(dependency);
        
        LongBuffer pRenderPass = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create render pass: " + result);
        }
        
        renderPass = pRenderPass.get(0);
        FPSFlux.LOGGER.debug("[VulkanContext] Render pass created");
    }
    
    private int findDepthFormat(MemoryStack stack) {
        return findSupportedFormat(
            new int[]{VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT},
            VK_IMAGE_TILING_OPTIMAL,
            VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT,
            stack
        );
    }
    
    private int findSupportedFormat(int[] candidates, int tiling, int features, MemoryStack stack) {
        for (int format : candidates) {
            VkFormatProperties props = VkFormatProperties.malloc(stack);
            vkGetPhysicalDeviceFormatProperties(physicalDevice, format, props);
            
            if (tiling == VK_IMAGE_TILING_LINEAR && (props.linearTilingFeatures() & features) == features) {
                return format;
            } else if (tiling == VK_IMAGE_TILING_OPTIMAL && (props.optimalTilingFeatures() & features) == features) {
                return format;
            }
        }
        throw new RuntimeException("Failed to find supported format");
    }
    
    // ========================================================================
    // DEPTH RESOURCES
    // ========================================================================
    
    private void createDepthResources(MemoryStack stack) {
        long[] result = createImage(
            swapchainExtent.width(), swapchainExtent.height(),
            1, depthFormat, VK_IMAGE_TILING_OPTIMAL,
            VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, stack
        );
        depthImage = result[0];
        depthImageMemory = result[1];
        
        depthImageView = createImageView(depthImage, depthFormat, VK_IMAGE_ASPECT_DEPTH_BIT, stack);
        
        // Transition depth image layout
        transitionImageLayout(depthImage, depthFormat, 
            VK_IMAGE_LAYOUT_UNDEFINED, 
            VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, 1);
        
        FPSFlux.LOGGER.debug("[VulkanContext] Depth resources created");
    }
    
    private long[] createImage(int width, int height, int mipLevels, int format, int tiling, 
                               int usage, int properties, MemoryStack stack) {
        VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .imageType(VK_IMAGE_TYPE_2D)
            .format(format)
            .mipLevels(mipLevels)
            .arrayLayers(1)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .tiling(tiling)
            .usage(usage)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        
        imageInfo.extent().width(width).height(height).depth(1);
        
        LongBuffer pImage = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateImage(device, imageInfo, null, pImage);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create image: " + result);
        }
        
        long image = pImage.get(0);
        
        VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc(stack);
        vkGetImageMemoryRequirements(device, image, memRequirements);
        
        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memRequirements.size())
            .memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), properties));
        
        LongBuffer pMemory = stack.longs(VK_NULL_HANDLE);
        result = vkAllocateMemory(device, allocInfo, null, pMemory);
        if (result != VK_SUCCESS) {
            vkDestroyImage(device, image, null);
            throw new RuntimeException("Failed to allocate image memory: " + result);
        }
        
        long memory = pMemory.get(0);
        vkBindImageMemory(device, image, memory, 0);
        
        return new long[]{image, memory};
    }
    
    private void transitionImageLayout(long image, int format, int oldLayout, int newLayout, int mipLevels) {
        VkCommandBuffer commandBuffer = beginSingleTimeCommands();
        
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image);
            
            barrier.subresourceRange()
                .baseMipLevel(0)
                .levelCount(mipLevels)
                .baseArrayLayer(0)
                .layerCount(1);
            
            // Set aspect mask
            if (newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                if (hasStencilComponent(format)) {
                    barrier.subresourceRange().aspectMask(
                        barrier.subresourceRange().aspectMask() | VK_IMAGE_ASPECT_STENCIL_BIT);
                }
            } else {
                barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            }
            
            int sourceStage, destinationStage;
            
            if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && 
                newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                barrier.srcAccessMask(0);
                barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && 
                       newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && 
                       newLayout == VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                barrier.srcAccessMask(0);
                barrier.dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | 
                                     VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
                sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
            } else {
                barrier.srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT);
                barrier.dstAccessMask(VK_ACCESS_MEMORY_READ_BIT);
                sourceStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                destinationStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
            }
            
            vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage, 0,
                null, null, barrier);
        }
        
        endSingleTimeCommands(commandBuffer);
    }
    
    private boolean hasStencilComponent(int format) {
        return format == VK_FORMAT_D32_SFLOAT_S8_UINT || format == VK_FORMAT_D24_UNORM_S8_UINT;
    }
    
    // ========================================================================
    // FRAMEBUFFERS
    // ========================================================================
    
    private void createFramebuffers(MemoryStack stack) {
        swapchainFramebuffers.clear();
        
        LongBuffer attachments = stack.longs(VK_NULL_HANDLE, depthImageView);
        
        VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
            .renderPass(renderPass)
            .width(swapchainExtent.width())
            .height(swapchainExtent.height())
            .layers(1);
        
        LongBuffer pFramebuffer = stack.longs(VK_NULL_HANDLE);
        
        for (long imageView : swapchainImageViews) {
            attachments.put(0, imageView);
            framebufferInfo.pAttachments(attachments);
            
            int result = vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create framebuffer: " + result);
            }
            
            swapchainFramebuffers.add(pFramebuffer.get(0));
        }
        
        FPSFlux.LOGGER.debug("[VulkanContext] Created {} framebuffers", swapchainFramebuffers.size());
    }
    
    // ========================================================================
    // COMMAND POOL AND BUFFERS
    // ========================================================================
    
    private void createCommandPool(MemoryStack stack) {
        VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
            .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
            .queueFamilyIndex(graphicsQueueFamily);
        
        LongBuffer pCommandPool = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateCommandPool(device, poolInfo, null, pCommandPool);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create command pool: " + result);
        }
        commandPool = pCommandPool.get(0);
        
        // Create transfer command pool if using dedicated transfer queue
        if (transferQueueFamily != graphicsQueueFamily) {
            poolInfo.queueFamilyIndex(transferQueueFamily);
            result = vkCreateCommandPool(device, poolInfo, null, pCommandPool);
            if (result == VK_SUCCESS) {
                transferCommandPool = pCommandPool.get(0);
            }
        } else {
            transferCommandPool = commandPool;
        }
        
        FPSFlux.LOGGER.debug("[VulkanContext] Command pool created");
    }
    
    private void createCommandBuffers(MemoryStack stack) {
        int bufferCount = swapchainImages.size();
        commandBuffers = new VkCommandBuffer[bufferCount];
        
        VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            .commandPool(commandPool)
            .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            .commandBufferCount(bufferCount);
        
        PointerBuffer pCommandBuffers = stack.mallocPointer(bufferCount);
        int result = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate command buffers: " + result);
        }
        
        for (int i = 0; i < bufferCount; i++) {
            commandBuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), device);
        }
        
        FPSFlux.LOGGER.debug("[VulkanContext] Created {} command buffers", bufferCount);
    }
    
    // ========================================================================
    // PIPELINE CACHE
    // ========================================================================
    
    private void createPipelineCache(MemoryStack stack) {
        VkPipelineCacheCreateInfo cacheInfo = VkPipelineCacheCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO);
        
        LongBuffer pCache = stack.longs(VK_NULL_HANDLE);
        int result = vkCreatePipelineCache(device, cacheInfo, null, pCache);
        if (result == VK_SUCCESS) {
            pipelineCache = pCache.get(0);
            FPSFlux.LOGGER.debug("[VulkanContext] Pipeline cache created");
        } else {
            FPSFlux.LOGGER.warn("[VulkanContext] Failed to create pipeline cache: {}", result);
        }
    }
    
    // ========================================================================
    // SYNCHRONIZATION
    // ========================================================================
    
    private void createSyncObjects(MemoryStack stack) {
        imageAvailableSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
        renderFinishedSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
        inFlightFences = new long[MAX_FRAMES_IN_FLIGHT];
        
        VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        
        VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
            .flags(VK_FENCE_CREATE_SIGNALED_BIT);
        
        LongBuffer pSemaphore = stack.longs(VK_NULL_HANDLE);
        LongBuffer pFence = stack.longs(VK_NULL_HANDLE);
        
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            int result = vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create semaphore: " + result);
            }
            imageAvailableSemaphores[i] = pSemaphore.get(0);
            
            result = vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create semaphore: " + result);
            }
            renderFinishedSemaphores[i] = pSemaphore.get(0);
            
            result = vkCreateFence(device, fenceInfo, null, pFence);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create fence: " + result);
            }
            inFlightFences[i] = pFence.get(0);
        }
        
        // Set legacy single semaphore/fence references
        imageAvailableSemaphore = imageAvailableSemaphores[0];
        renderFinishedSemaphore = renderFinishedSemaphores[0];
        inFlightFence = inFlightFences[0];
        
        FPSFlux.LOGGER.debug("[VulkanContext] Synchronization objects created");
    }
    
    // ========================================================================
    // ACCESSOR METHODS (Used by VulkanCallMapperX)
    // ========================================================================
    
    /**
     * Find suitable memory type for allocation
     */
    public int findMemoryType(int typeFilter, int properties) {
        for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
            if ((typeFilter & (1 << i)) != 0 && 
                (memoryProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                return i;
            }
        }
        throw new RuntimeException("Failed to find suitable memory type");
    }
    
    public VkCommandBuffer getCurrentCommandBuffer() {
        return commandBuffers[currentImageIndex];
    }
    
    public long getCurrentFramebuffer() {
        return swapchainFramebuffers.get(currentImageIndex);
    }
    
    public long getCurrentSwapchainImageView() {
        return swapchainImageViews.get(currentImageIndex);
    }
    
    public long getDepthImageView() {
        return depthImageView;
    }
    
    public long getCurrentSwapchainImage() {
        return swapchainImages.get(currentImageIndex);
    }
    
    // ========================================================================
    // COMMAND BUFFER UTILITIES
    // ========================================================================
    
    public VkCommandBuffer beginSingleTimeCommands() {
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandPool(commandPool)
                .commandBufferCount(1);
            
            PointerBuffer pCommandBuffer = stack.mallocPointer(1);
            vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
            VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), device);
            
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            
            vkBeginCommandBuffer(commandBuffer, beginInfo);
            
            return commandBuffer;
        }
    }
    
    public void endSingleTimeCommands(VkCommandBuffer commandBuffer) {
        vkEndCommandBuffer(commandBuffer);
        
        try (MemoryStack stack = stackPush()) {
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(commandBuffer));
            
            vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE);
            vkQueueWaitIdle(graphicsQueue);
            
            vkFreeCommandBuffers(device, commandPool, commandBuffer);
        }
    }
    
    public void endSingleTimeCommandsWithFence(VkCommandBuffer commandBuffer, long fence) {
        vkEndCommandBuffer(commandBuffer);
        
        try (MemoryStack stack = stackPush()) {
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(commandBuffer));
            
            vkQueueSubmit(graphicsQueue, submitInfo, fence);
            
            // Don't wait - let caller manage fence
        }
    }
    
    /**
     * Map device memory for CPU access
     */
    public ByteBuffer mapMemory(long memory, long offset, long size) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            int result = vkMapMemory(device, memory, offset, size, 0, pData);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to map memory: " + result);
            }
            return memByteBuffer(pData.get(0), (int) size);
        }
    }
    
    // ========================================================================
    // CLEANUP
    // ========================================================================
    
    public void cleanup() {
        if (device != null) {
            vkDeviceWaitIdle(device);
        }
        
        // Cleanup synchronization objects
        if (imageAvailableSemaphores != null) {
            for (long semaphore : imageAvailableSemaphores) {
                if (semaphore != VK_NULL_HANDLE) {
                    vkDestroySemaphore(device, semaphore, null);
                }
            }
        }
        if (renderFinishedSemaphores != null) {
            for (long semaphore : renderFinishedSemaphores) {
                if (semaphore != VK_NULL_HANDLE) {
                    vkDestroySemaphore(device, semaphore, null);
                }
            }
        }
        if (inFlightFences != null) {
            for (long fence : inFlightFences) {
                if (fence != VK_NULL_HANDLE) {
                    vkDestroyFence(device, fence, null);
                }
            }
        }
        
        // Cleanup pipeline cache
        if (pipelineCache != VK_NULL_HANDLE) {
            vkDestroyPipelineCache(device, pipelineCache, null);
        }
        
        // Cleanup command pools
        if (commandPool != VK_NULL_HANDLE) {
            vkDestroyCommandPool(device, commandPool, null);
        }
        if (transferCommandPool != VK_NULL_HANDLE && transferCommandPool != commandPool) {
            vkDestroyCommandPool(device, transferCommandPool, null);
        }
        
        // Cleanup descriptor pool
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, descriptorPool, null);
        }
        
        // Cleanup descriptor set layout
        if (descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        }
        
        // Cleanup swapchain resources
        cleanupSwapchainResources();
        
        // Cleanup render pass
        if (renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, renderPass, null);
        }
        
        // Cleanup swapchain
        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchain, null);
        }
        
        // Free extent
        if (swapchainExtent != null) {
            swapchainExtent.free();
        }
        
        // Free physical device properties
        if (physicalDeviceProperties != null) {
            physicalDeviceProperties.free();
        }
        if (physicalDeviceFeatures != null) {
            physicalDeviceFeatures.free();
        }
        if (memoryProperties != null) {
            memoryProperties.free();
        }
        
        // Cleanup device
        if (device != null) {
            vkDestroyDevice(device, null);
        }
        
        // Cleanup surface
        if (surface != VK_NULL_HANDLE) {
            vkDestroySurfaceKHR(instance, surface, null);
        }
        
        // Cleanup instance
        if (instance != null) {
            vkDestroyInstance(instance, null);
        }
        
        initialized = false;
        
        FPSFlux.LOGGER.info("[VulkanContext] Cleanup complete");
    }
    
    // ========================================================================
    // HELPER CLASSES
    // ========================================================================
    
    private static class QueueFamilyIndices {
        int graphicsFamily = -1;
        int presentFamily = -1;
        int computeFamily = -1;
        int transferFamily = -1;
        
        boolean isComplete() {
            return graphicsFamily >= 0 && presentFamily >= 0;
        }
    }
    
    private static class SwapchainSupportDetails {
        VkSurfaceCapabilitiesKHR capabilities;
        VkSurfaceFormatKHR.Buffer formats;
        IntBuffer presentModes;
    }
    
    // ========================================================================
    // UTILITY HELPERS
    // ========================================================================
    
    private static PointerBuffer asPointerBuffer(MemoryStack stack, String[] strings) {
        PointerBuffer buffer = stack.mallocPointer(strings.length);
        for (String str : strings) {
            buffer.put(stack.UTF8(str));
        }
        return buffer.rewind();
    }
    
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    // ========================================================================
    // STATUS REPORT
    // ========================================================================
    
    public String getStatusReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VulkanContext Status ===\n");
        sb.append("Initialized: ").append(initialized).append("\n");
        sb.append("Vulkan Version: ").append(VK_VERSION_MAJOR(vulkanVersion)).append(".")
          .append(VK_VERSION_MINOR(vulkanVersion)).append(".")
          .append(VK_VERSION_PATCH(vulkanVersion)).append("\n");
        
        if (physicalDeviceProperties != null) {
            sb.append("Device: ").append(physicalDeviceProperties.deviceNameString()).append("\n");
            sb.append("Driver Version: ").append(physicalDeviceProperties.driverVersion()).append("\n");
        }
        
        sb.append("\nSwapchain:\n");
        if (swapchainExtent != null) {
            sb.append("  Size: ").append(swapchainExtent.width()).append("x")
              .append(swapchainExtent.height()).append("\n");
        }
        sb.append("  Images: ").append(swapchainImages.size()).append("\n");
        sb.append("  Format: ").append(swapchainImageFormat).append("\n");
        
        sb.append("\nFeatures:\n");
        sb.append("  Timeline Semaphores: ").append(timelineSemaphoresSupported).append("\n");
        sb.append("  Buffer Device Address: ").append(bufferDeviceAddressSupported).append("\n");
        sb.append("  Descriptor Indexing: ").append(descriptorIndexingSupported).append("\n");
        sb.append("  Dynamic Rendering: ").append(dynamicRenderingSupported).append("\n");
        sb.append("  Synchronization2: ").append(synchronization2Supported).append("\n");
        sb.append("  Maintenance4: ").append(maintenance4Supported).append("\n");
        sb.append("  Maintenance5: ").append(maintenance5Supported).append("\n");
        sb.append("  Anisotropic Filtering: ").append(supportsAnisotropy()).append("\n");
        
        sb.append("\nQueues:\n");
        sb.append("  Graphics Family: ").append(graphicsQueueFamily).append("\n");
        sb.append("  Present Family: ").append(presentQueueFamily).append("\n");
        sb.append("  Compute Family: ").append(computeQueueFamily).append("\n");
        sb.append("  Transfer Family: ").append(transferQueueFamily).append("\n");
        
        sb.append("\nEnabled Device Extensions: ").append(enabledDeviceExtensions.size()).append("\n");
        for (String ext : enabledDeviceExtensions) {
            sb.append("  ").append(ext).append("\n");
        }
        
        return sb.toString();
    }
}
