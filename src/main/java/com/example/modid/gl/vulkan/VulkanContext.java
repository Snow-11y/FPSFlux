package com.example.modid.gl.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Complete Vulkan Context - Full initialization
 */
public class VulkanContext {
    
    // Core Vulkan objects
    public VkInstance instance;
    public long surface;
    public VkPhysicalDevice physicalDevice;
    public VkDevice device;
    
    // Queue handles
    public VkQueue graphicsQueue;
    public VkQueue presentQueue;
    public int graphicsQueueFamily = -1;
    public int presentQueueFamily = -1;
    
    // Swapchain
    public long swapchain = VK_NULL_HANDLE;
    public List<Long> swapchainImages = new ArrayList<>();
    public List<Long> swapchainImageViews = new ArrayList<>();
    public List<Long> swapchainFramebuffers = new ArrayList<>();
    public int swapchainImageFormat;
    public VkExtent2D swapchainExtent;
    public int currentImageIndex = 0;
    
    // Render pass
    public long renderPass = VK_NULL_HANDLE;
    
    // Descriptor management
    public long descriptorSetLayout = VK_NULL_HANDLE;
    public long descriptorPool = VK_NULL_HANDLE;
    public List<Long> descriptorSets = new ArrayList<>();
    
    // Command pools and buffers
    public long commandPool = VK_NULL_HANDLE;
    public VkCommandBuffer[] commandBuffers;
    
    // Synchronization
    public long imageAvailableSemaphore = VK_NULL_HANDLE;
    public long renderFinishedSemaphore = VK_NULL_HANDLE;
    public long inFlightFence = VK_NULL_HANDLE;
    
    // Depth buffer
    public long depthImage = VK_NULL_HANDLE;
    public long depthImageMemory = VK_NULL_HANDLE;
    public long depthImageView = VK_NULL_HANDLE;
    
    // Configuration
    private static final boolean ENABLE_VALIDATION = true;
    private static final String[] VALIDATION_LAYERS = {"VK_LAYER_KHRONOS_validation"};
    private static final String[] DEVICE_EXTENSIONS = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    
    private long windowHandle; // GLFW window
    
    /**
     * Initialize complete Vulkan context
     */
    public void initialize(long glfwWindow) {
        this.windowHandle = glfwWindow;
        
        try (MemoryStack stack = stackPush()) {
            createInstance(stack);
            createSurface(stack);
            pickPhysicalDevice(stack);
            createLogicalDevice(stack);
            createSwapchain(stack);
            createImageViews(stack);
            createRenderPass(stack);
            createDepthResources(stack);
            createFramebuffers(stack);
            createCommandPool(stack);
            createCommandBuffers(stack);
            createDescriptorSetLayout(stack);
            createDescriptorPool(stack);
            createDescriptorSets(stack);
            createSyncObjects(stack);
            
            System.out.println("[VulkanContext] Initialization complete");
        } catch (Exception e) {
            System.err.println("[VulkanContext] Initialization failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Vulkan initialization failed", e);
        }
    }
    
    // ========================================================================
    // INSTANCE CREATION
    // ========================================================================
    
    private void createInstance(MemoryStack stack) {
        // Application info
        VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
            .pApplicationName(stack.UTF8Safe("FPSFlux Minecraft"))
            .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
            .pEngineName(stack.UTF8Safe("FPSFlux GL-to-Vulkan"))
            .engineVersion(VK_MAKE_VERSION(1, 0, 0))
            .apiVersion(VK_API_VERSION_1_0);
        
        // Get required extensions from GLFW
        PointerBuffer glfwExtensions = glfwGetRequiredInstanceExtensions();
        if (glfwExtensions == null) {
            throw new RuntimeException("GLFW required extensions not available");
        }
        
        PointerBuffer extensions;
        if (ENABLE_VALIDATION) {
            extensions = stack.mallocPointer(glfwExtensions.remaining() + 1);
            extensions.put(glfwExtensions);
            extensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
            extensions.flip();
        } else {
            extensions = glfwExtensions;
        }
        
        // Create instance
        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
            .pApplicationInfo(appInfo)
            .ppEnabledExtensionNames(extensions);
        
        // Enable validation layers if requested
        if (ENABLE_VALIDATION && checkValidationLayerSupport(stack)) {
            createInfo.ppEnabledLayerNames(asPointerBuffer(stack, VALIDATION_LAYERS));
            System.out.println("[VulkanContext] Validation layers enabled");
        }
        
        PointerBuffer pInstance = stack.mallocPointer(1);
        int result = vkCreateInstance(createInfo, null, pInstance);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create Vulkan instance: " + result);
        }
        
        instance = new VkInstance(pInstance.get(0), createInfo);
        System.out.println("[VulkanContext] Vulkan instance created");
    }
    
    private boolean checkValidationLayerSupport(MemoryStack stack) {
        IntBuffer layerCount = stack.ints(0);
        vkEnumerateInstanceLayerProperties(layerCount, null);
        
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
        System.out.println("[VulkanContext] Window surface created");
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
        
        // Pick first suitable device
        for (int i = 0; i < pDevices.capacity(); i++) {
            VkPhysicalDevice device = new VkPhysicalDevice(pDevices.get(i), instance);
            if (isDeviceSuitable(device, stack)) {
                physicalDevice = device;
                
                VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.malloc(stack);
                vkGetPhysicalDeviceProperties(device, props);
                System.out.println("[VulkanContext] Selected GPU: " + props.deviceNameString());
                return;
            }
        }
        
        throw new RuntimeException("No suitable GPU found");
    }
    
    private boolean isDeviceSuitable(VkPhysicalDevice device, MemoryStack stack) {
        // Find queue families
        QueueFamilyIndices indices = findQueueFamilies(device, stack);
        
        // Check extension support
        boolean extensionsSupported = checkDeviceExtensionSupport(device, stack);
        
        // Check swapchain support
        boolean swapchainAdequate = false;
        if (extensionsSupported) {
            SwapchainSupportDetails details = querySwapchainSupport(device, stack);
            swapchainAdequate = details.formats.capacity() > 0 && details.presentModes.capacity() > 0;
        }
        
        // Check features
        VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.malloc(stack);
        vkGetPhysicalDeviceFeatures(device, features);
        
        return indices.isComplete() && extensionsSupported && swapchainAdequate && features.samplerAnisotropy();
    }
    
    private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device, MemoryStack stack) {
        QueueFamilyIndices indices = new QueueFamilyIndices();
        
        IntBuffer queueFamilyCount = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);
        
        VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.malloc(queueFamilyCount.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);
        
        IntBuffer presentSupport = stack.ints(VK_FALSE);
        
        for (int i = 0; i < queueFamilies.capacity(); i++) {
            VkQueueFamilyProperties queueFamily = queueFamilies.get(i);
            
            if ((queueFamily.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                indices.graphicsFamily = i;
            }
            
            vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, presentSupport);
            if (presentSupport.get(0) == VK_TRUE) {
                indices.presentFamily = i;
            }
            
            if (indices.isComplete()) break;
        }
        
        return indices;
    }
    
    private boolean checkDeviceExtensionSupport(VkPhysicalDevice device, MemoryStack stack) {
        IntBuffer extensionCount = stack.ints(0);
        vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, null);
        
        VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.malloc(extensionCount.get(0), stack);
        vkEnumerateDeviceExtensionProperties(device, (String) null, extensionCount, availableExtensions);
        
        for (String required : DEVICE_EXTENSIONS) {
            boolean found = false;
            for (int i = 0; i < availableExtensions.capacity(); i++) {
                if (required.equals(availableExtensions.get(i).extensionNameString())) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
    
    // ========================================================================
    // LOGICAL DEVICE CREATION
    // ========================================================================
    
    private void createLogicalDevice(MemoryStack stack) {
        QueueFamilyIndices indices = findQueueFamilies(physicalDevice, stack);
        graphicsQueueFamily = indices.graphicsFamily;
        presentQueueFamily = indices.presentFamily;
        
        int[] uniqueQueueFamilies = indices.graphicsFamily == indices.presentFamily ?
            new int[]{indices.graphicsFamily} :
            new int[]{indices.graphicsFamily, indices.presentFamily};
        
        VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(uniqueQueueFamilies.length, stack);
        
        for (int i = 0; i < uniqueQueueFamilies.length; i++) {
            queueCreateInfos.get(i)
                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(uniqueQueueFamilies[i])
                .pQueuePriorities(stack.floats(1.0f));
        }
        
        VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack)
            .samplerAnisotropy(true);
        
        VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
            .pQueueCreateInfos(queueCreateInfos)
            .pEnabledFeatures(deviceFeatures)
            .ppEnabledExtensionNames(asPointerBuffer(stack, DEVICE_EXTENSIONS));
        
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
        vkGetDeviceQueue(device, indices.graphicsFamily, 0, pQueue);
        graphicsQueue = new VkQueue(pQueue.get(0), device);
        
        vkGetDeviceQueue(device, indices.presentFamily, 0, pQueue);
        presentQueue = new VkQueue(pQueue.get(0), device);
        
        System.out.println("[VulkanContext] Logical device created");
    }
    
    // ========================================================================
    // SWAPCHAIN CREATION
    // ========================================================================
    
    private void createSwapchain(MemoryStack stack) {
        SwapchainSupportDetails details = querySwapchainSupport(physicalDevice, stack);
        
        VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(details.formats);
        int presentMode = chooseSwapPresentMode(details.presentModes);
        VkExtent2D extent = chooseSwapExtent(details.capabilities, stack);
        
        IntBuffer imageCount = stack.ints(details.capabilities.minImageCount() + 1);
        if (details.capabilities.maxImageCount() > 0 && imageCount.get(0) > details.capabilities.maxImageCount()) {
            imageCount.put(0, details.capabilities.maxImageCount());
        }
        
        VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
            .surface(surface)
            .minImageCount(imageCount.get(0))
            .imageFormat(surfaceFormat.format())
            .imageColorSpace(surfaceFormat.colorSpace())
            .imageExtent(extent)
            .imageArrayLayers(1)
            .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        
        QueueFamilyIndices indices = findQueueFamilies(physicalDevice, stack);
        
        if (indices.graphicsFamily != indices.presentFamily) {
            createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                .pQueueFamilyIndices(stack.ints(indices.graphicsFamily, indices.presentFamily));
        } else {
            createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
        }
        
        createInfo
            .preTransform(details.capabilities.currentTransform())
            .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
            .presentMode(presentMode)
            .clipped(true)
            .oldSwapchain(VK_NULL_HANDLE);
        
        LongBuffer pSwapchain = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateSwapchainKHR(device, createInfo, null, pSwapchain);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create swapchain: " + result);
        }
        
        swapchain = pSwapchain.get(0);
        swapchainImageFormat = surfaceFormat.format();
        swapchainExtent = VkExtent2D.create().set(extent);
        
        // Get swapchain images
        vkGetSwapchainImagesKHR(device, swapchain, imageCount, null);
        LongBuffer pSwapchainImages = stack.mallocLong(imageCount.get(0));
        vkGetSwapchainImagesKHR(device, swapchain, imageCount, pSwapchainImages);
        
        swapchainImages.clear();
        for (int i = 0; i < pSwapchainImages.capacity(); i++) {
            swapchainImages.add(pSwapchainImages.get(i));
        }
        
        System.out.println("[VulkanContext] Swapchain created with " + swapchainImages.size() + " images");
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
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
        return formats.get(0);
    }
    
    private int chooseSwapPresentMode(IntBuffer presentModes) {
        for (int i = 0; i < presentModes.capacity(); i++) {
            if (presentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                return VK_PRESENT_MODE_MAILBOX_KHR;
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR;
    }
    
    private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return capabilities.currentExtent();
        }
        
        IntBuffer width = stack.ints(0);
        IntBuffer height = stack.ints(0);
        
        // Get framebuffer size from GLFW
        org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(windowHandle, width, height);
        
        VkExtent2D actualExtent = VkExtent2D.malloc(stack).set(width.get(0), height.get(0));
        
        VkExtent2D minExtent = capabilities.minImageExtent();
        VkExtent2D maxExtent = capabilities.maxImageExtent();
        
        actualExtent.width(clamp(actualExtent.width(), minExtent.width(), maxExtent.width()));
        actualExtent.height(clamp(actualExtent.height(), minExtent.height(), maxExtent.height()));
        
        return actualExtent;
    }
    
    // ========================================================================
    // IMAGE VIEWS
    // ========================================================================
    
    private void createImageViews(MemoryStack stack) {
        swapchainImageViews.clear();
        
        for (long image : swapchainImages) {
            long imageView = createImageView(image, swapchainImageFormat, VK_IMAGE_ASPECT_COLOR_BIT, stack);
            swapchainImageViews.add(imageView);
        }
        
        System.out.println("[VulkanContext] Created " + swapchainImageViews.size() + " image views");
    }
    
    private long createImageView(long image, int format, int aspectFlags, MemoryStack stack) {
        VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(image)
            .viewType(VK_IMAGE_VIEW_TYPE_2D)
            .format(format);
        
        viewInfo.subresourceRange()
            .aspectMask(aspectFlags)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1);
        
        LongBuffer pImageView = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateImageView(device, viewInfo, null, pImageView);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create image view: " + result);
        }
        
        return pImageView.get(0);
    }
    
    // ========================================================================
    // RENDER PASS
    // ========================================================================
    
    private void createRenderPass(MemoryStack stack) {
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
            .format(findDepthFormat(stack))
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
        System.out.println("[VulkanContext] Render pass created");
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
        int depthFormat = findDepthFormat(stack);
        
        ImageCreateInfo imageInfo = new ImageCreateInfo();
        imageInfo.width = swapchainExtent.width();
        imageInfo.height = swapchainExtent.height();
        imageInfo.format = depthFormat;
        imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
        imageInfo.usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
        imageInfo.properties = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
        
        long[] result = createImage(imageInfo, stack);
        depthImage = result[0];
        depthImageMemory = result[1];
        
        depthImageView = createImageView(depthImage, depthFormat, VK_IMAGE_ASPECT_DEPTH_BIT, stack);
        
        System.out.println("[VulkanContext] Depth resources created");
    }
    
    private long[] createImage(ImageCreateInfo info, MemoryStack stack) {
        VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .imageType(VK_IMAGE_TYPE_2D)
            .format(info.format)
            .mipLevels(1)
            .arrayLayers(1)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .tiling(info.tiling)
            .usage(info.usage)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        
        imageInfo.extent().width(info.width).height(info.height).depth(1);
        
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
            .memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), info.properties, stack));
        
        LongBuffer pMemory = stack.longs(VK_NULL_HANDLE);
        result = vkAllocateMemory(device, allocInfo, null, pMemory);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate image memory: " + result);
        }
        
        long memory = pMemory.get(0);
        vkBindImageMemory(device, image, memory, 0);
        
        return new long[]{image, memory};
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
        
        System.out.println("[VulkanContext] Created " + swapchainFramebuffers.size() + " framebuffers");
    }
    
    // ========================================================================
    // COMMAND POOL AND BUFFERS
    // ========================================================================
    
    private void createCommandPool(MemoryStack stack) {
        QueueFamilyIndices queueFamilyIndices = findQueueFamilies(physicalDevice, stack);
        
        VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
            .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
            .queueFamilyIndex(queueFamilyIndices.graphicsFamily);
        
        LongBuffer pCommandPool = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateCommandPool(device, poolInfo, null, pCommandPool);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create command pool: " + result);
        }
        
        commandPool = pCommandPool.get(0);
        System.out.println("[VulkanContext] Command pool created");
    }
    
    private void createCommandBuffers(MemoryStack stack) {
        int imageCount = swapchainImages.size();
        commandBuffers = new VkCommandBuffer[imageCount];
        
        VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            .commandPool(commandPool)
            .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            .commandBufferCount(imageCount);
        
        PointerBuffer pCommandBuffers = stack.mallocPointer(imageCount);
        int result = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate command buffers: " + result);
        }
        
        for (int i = 0; i < imageCount; i++) {
            commandBuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), device);
        }
        
        System.out.println("[VulkanContext] Created " + imageCount + " command buffers");
    }
    
    // ========================================================================
    // DESCRIPTOR SETS
    // ========================================================================
    
    private void createDescriptorSetLayout(MemoryStack stack) {
        // Create layout for texture samplers
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
        
        // Binding 0: Texture sampler
        bindings.get(0)
            .binding(0)
            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(1)
            .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
        
        // Binding 1: Uniform buffer
        bindings.get(1)
            .binding(1)
            .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(1)
            .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
        
        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
            .pBindings(bindings);
        
        LongBuffer pDescriptorSetLayout = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateDescriptorSetLayout(device, layoutInfo, null, pDescriptorSetLayout);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create descriptor set layout: " + result);
        }
        
        descriptorSetLayout = pDescriptorSetLayout.get(0);
        System.out.println("[VulkanContext] Descriptor set layout created");
    }
    
    private void createDescriptorPool(MemoryStack stack) {
        VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
        
        poolSizes.get(0)
            .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(swapchainImages.size() * 10); // 10 textures per frame
        
        poolSizes.get(1)
            .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(swapchainImages.size());
        
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
            .pPoolSizes(poolSizes)
            .maxSets(swapchainImages.size());
        
        LongBuffer pDescriptorPool = stack.longs(VK_NULL_HANDLE);
        int result = vkCreateDescriptorPool(device, poolInfo, null, pDescriptorPool);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create descriptor pool: " + result);
        }
        
        descriptorPool = pDescriptorPool.get(0);
        System.out.println("[VulkanContext] Descriptor pool created");
    }
    
    private void createDescriptorSets(MemoryStack stack) {
        LongBuffer layouts = stack.mallocLong(swapchainImages.size());
        for (int i = 0; i < layouts.capacity(); i++) {
            layouts.put(i, descriptorSetLayout);
        }
        
        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .descriptorPool(descriptorPool)
            .pSetLayouts(layouts);
        
        LongBuffer pDescriptorSets = stack.mallocLong(swapchainImages.size());
        int result = vkAllocateDescriptorSets(device, allocInfo, pDescriptorSets);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to allocate descriptor sets: " + result);
        }
        
        descriptorSets.clear();
        for (int i = 0; i < pDescriptorSets.capacity(); i++) {
            descriptorSets.add(pDescriptorSets.get(i));
        }
        
        System.out.println("[VulkanContext] Created " + descriptorSets.size() + " descriptor sets");
    }
    
    // ========================================================================
    // SYNCHRONIZATION
    // ========================================================================
    
    private void createSyncObjects(MemoryStack stack) {
        VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        
        VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
            .flags(VK_FENCE_CREATE_SIGNALED_BIT);
        
        LongBuffer pSemaphore = stack.longs(VK_NULL_HANDLE);
        LongBuffer pFence = stack.longs(VK_NULL_HANDLE);
        
        int result = vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create semaphore: " + result);
        }
        imageAvailableSemaphore = pSemaphore.get(0);
        
        result = vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create semaphore: " + result);
        }
        renderFinishedSemaphore = pSemaphore.get(0);
        
        result = vkCreateFence(device, fenceInfo, null, pFence);
        if (result != VK_SUCCESS) {
            throw new RuntimeException("Failed to create fence: " + result);
        }
        inFlightFence = pFence.get(0);
        
        System.out.println("[VulkanContext] Synchronization objects created");
    }
    
    // ========================================================================
    // UTILITY FUNCTIONS
    // ========================================================================
    
    public int findMemoryType(int typeFilter, int properties, MemoryStack stack) {
        VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.malloc(stack);
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);
        
        for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
            if ((typeFilter & (1 << i)) != 0 && 
                (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
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
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            
            submitInfo.pCommandBuffers(stack.pointers(commandBuffer));
            
            vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE);
            vkQueueWaitIdle(graphicsQueue);
            
            vkFreeCommandBuffers(device, commandPool, commandBuffer);
        }
    }
    
    public ByteBuffer mapMemory(long memory, long offset, long size) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, memory, offset, size, 0, pData);
            return memByteBuffer(pData.get(0), (int)size);
        }
    }
    
    // ========================================================================
    // CLEANUP
    // ========================================================================
    
    public void cleanup() {
        vkDeviceWaitIdle(device);
        
        // Cleanup synchronization objects
        if (imageAvailableSemaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(device, imageAvailableSemaphore, null);
        }
        if (renderFinishedSemaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(device, renderFinishedSemaphore, null);
        }
        if (inFlightFence != VK_NULL_HANDLE) {
            vkDestroyFence(device, inFlightFence, null);
        }
        
        // Cleanup command pool
        if (commandPool != VK_NULL_HANDLE) {
            vkDestroyCommandPool(device, commandPool, null);
        }
        
        // Cleanup descriptor pool
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, descriptorPool, null);
        }
        
        // Cleanup descriptor set layout
        if (descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        }
        
        // Cleanup framebuffers
        for (long framebuffer : swapchainFramebuffers) {
            vkDestroyFramebuffer(device, framebuffer, null);
        }
        
        // Cleanup depth resources
        if (depthImageView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, depthImageView, null);
        }
        if (depthImage != VK_NULL_HANDLE) {
            vkDestroyImage(device, depthImage, null);
        }
        if (depthImageMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, depthImageMemory, null);
        }
        
        // Cleanup render pass
        if (renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, renderPass, null);
        }
        
        // Cleanup image views
        for (long imageView : swapchainImageViews) {
            vkDestroyImageView(device, imageView, null);
        }
        
        // Cleanup swapchain
        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchain, null);
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
        
        System.out.println("[VulkanContext] Cleanup complete");
    }
    
    // ========================================================================
    // HELPER CLASSES
    // ========================================================================
    
    private static class QueueFamilyIndices {
        int graphicsFamily = -1;
        int presentFamily = -1;
        
        boolean isComplete() {
            return graphicsFamily >= 0 && presentFamily >= 0;
        }
    }
    
    private static class SwapchainSupportDetails {
        VkSurfaceCapabilitiesKHR capabilities;
        VkSurfaceFormatKHR.Buffer formats;
        IntBuffer presentModes;
    }
    
    private static class ImageCreateInfo {
        int width;
        int height;
        int format;
        int tiling;
        int usage;
        int properties;
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
}
```

---

## VulkanCapabilities - Complete Detection

**`com/example/modid/gl/VulkanCapabilities.java`** (Update with actual detection)

```java
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
```

---

## Safe Initialization in VulkanManager

**Update `com/example/modid/gl/VulkanManager.java`:**

```java
package com.example.modid.gl;

import com.example.modid.FPSFlux;
import com.example.modid.gl.mapping.VulkanCallMapper;
import com.example.modid.gl.vulkan.VulkanContext;

/**
 * VulkanManager - Safe initialization with error handling
 */
public class VulkanManager {
    private static boolean initialized = false;
    private static boolean enabled = false;
    private static String activePath = "Disabled";
    
    private static VulkanContext context = null;
    private static boolean vulkanEnabled = false; // Config flag
    
    public static void initialize() {
        if (initialized) return;
        
        if (!vulkanEnabled) {
            FPSFlux.LOGGER.info("[VulkanManager] Vulkan disabled in config");
            initialized = true;
            return;
        }
        
        FPSFlux.LOGGER.info("[VulkanManager] Initializing Vulkan backend...");
        
        try {
            // Detect Vulkan support
            VulkanCapabilities.detect();
            
            if (!VulkanCapabilities.isSupported) {
                FPSFlux.LOGGER.warn("[VulkanManager] Vulkan not supported on this system");
                enabled = false;
                initialized = true;
                return;
            }
            
            // Determine version
            if (VulkanCapabilities.hasVulkan14) {
                activePath = "Vulkan 1.4";
            } else if (VulkanCapabilities.hasVulkan13) {
                activePath = "Vulkan 1.3";
            } else if (VulkanCapabilities.hasVulkan12) {
                activePath = "Vulkan 1.2";
            } else if (VulkanCapabilities.hasVulkan11) {
                activePath = "Vulkan 1.1";
            } else if (VulkanCapabilities.hasVulkan10) {
                activePath = "Vulkan 1.0";
            }
            
            // Initialize Vulkan context
            // NOTE: This requires a GLFW window handle
            // For Minecraft, we'd need to hook into the window creation
            // For now, mark as available but don't fully initialize
            
            enabled = true;
            FPSFlux.LOGGER.info("[VulkanManager] Vulkan detected: {}", activePath);
            FPSFlux.LOGGER.warn("[VulkanManager] Full initialization requires window handle integration");
            
        } catch (Exception e) {
            FPSFlux.LOGGER.error("[VulkanManager] Initialization failed: {}", e.getMessage());
            e.printStackTrace();
            enabled = false;
        }
        
        initialized = true;
    }
    
    /**
     * Initialize with GLFW window (call after Minecraft window is created)
     */
    public static void initializeWithWindow(long windowHandle) {
        if (context != null) return;
        if (!enabled) return;
        
        try {
            context = new VulkanContext();
            context.initialize(windowHandle);
            
            // Initialize call mapper
            VulkanCallMapper.initialize(context);
            
            FPSFlux.LOGGER.info("[VulkanManager] Vulkan context fully initialized");
        } catch (Exception e) {
            FPSFlux.LOGGER.error("[VulkanManager] Context initialization failed: {}", e.getMessage());
            e.printStackTrace();
            enabled = false;
            context = null;
        }
    }
    
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
    
    public static String getActivePath() {
        if (!initialized) initialize();
        return activePath;
    }
    
    public static VulkanContext getContext() {
        return context;
    }
    
    public static String getDetailedReport() {
        if (!initialized) initialize();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== VulkanManager Status ===\n");
        sb.append("Active Path: ").append(activePath).append("\n");
        sb.append("Status: ").append(enabled ? "AVAILABLE" : "DISABLED").append("\n");
        sb.append("Context: ").append(context != null ? "INITIALIZED" : "NOT INITIALIZED").append("\n");
        
        if (VulkanCapabilities.isSupported) {
            sb.append("\n").append(VulkanCapabilities.getReport());
        } else {
            sb.append("\nVulkan not supported on this system\n");
        }
        
        return sb.toString();
    }
    
    public static void cleanup() {
        if (context != null) {
            context.cleanup();
            context = null;
        }
    }
}
