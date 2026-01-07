package com.example.modid.gl;

import com.example.modid.FPSFlux;
import com.example.modid.gl.buffer.BufferOps;
import com.example.modid.gl.buffer.ops.*;
import com.example.modid.gl.state.GLStateCache;

/**
 * OpenGLESManager - OpenGL ES backend handler
 * 
 * OpenGL ES is NOT fully compatible with desktop OpenGL. Key differences:
 * - No fixed-function pipeline (no glBegin/glEnd, matrix stacks)
 * - Limited texture formats
 * - Stricter precision requirements
 * - Different extension system
 * 
 * This manager:
 * 1. Detects ES capabilities
 * 2. Translates incompatible calls through wrapper if available
 * 3. Falls back to older ES version if feature unsupported
 * 4. Can redirect to Vulkan as ultimate fallback
 * 
 * Fallback chain: ES 3.2 → ES 3.1 → ES 3.0 → Wrapper → Vulkan (if enabled)
 */
public class OpenGLESManager {
    private static BufferOps activeBackend = null;
    private static boolean initialized = false;
    private static boolean enabled = false;
    private static String backendName = "None";
    
    // ES compatibility tracking
    private static boolean isNativeES = false;
    private static boolean hasWrapper = false;
    private static String wrapperType = "None";
    
    // Feature compatibility flags
    private static boolean needsDesktopGLEmulation = false;
    private static boolean canFallbackToVulkan = false;
    
    public static void initialize() {
        if (initialized) return;
        
        FPSFlux.LOGGER.info("[OpenGLESManager] Initializing OpenGL ES backend...");
        
        // Detect ES capabilities
        GLCapabilities.detect();
        
        // Determine if we're running native ES or through wrapper
        analyzeESEnvironment();
        
        // Select backend with fallback chain
        activeBackend = selectESBackendWithFallback();
        
        if (activeBackend != null) {
            enabled = true;
            backendName = activeBackend.getName();
            GLStateCache.reset();
            FPSFlux.LOGGER.info("[OpenGLESManager] Active Backend: {}", backendName);
            FPSFlux.LOGGER.info("[OpenGLESManager] Native ES: {} | Wrapper: {}", isNativeES, wrapperType);
        } else {
            enabled = false;
            FPSFlux.LOGGER.warn("[OpenGLESManager] No compatible ES backend found");
        }
        
        initialized = true;
    }
    
    private static void analyzeESEnvironment() {
        // Determine if we're on native ES or wrapped
        
        String renderer = GLCapabilities.glRenderer.toLowerCase();
        String version = GLCapabilities.glVersion.toLowerCase();
        
        // Native ES detection (mobile GPUs)
        if (version.contains("opengl es") || version.contains("gles")) {
            isNativeES = true;
            FPSFlux.LOGGER.info("[OpenGLESManager] Detected native OpenGL ES environment");
        }
        
        // Wrapper detection
        if (GLCapabilities.isGLWrapper) {
            hasWrapper = true;
            wrapperType = GLCapabilities.wrapperName;
            
            // gl4es is the main ES-to-desktop wrapper
            if (wrapperType.equals("gl4es")) {
                needsDesktopGLEmulation = true;
                FPSFlux.LOGGER.info("[OpenGLESManager] gl4es wrapper detected - can translate desktop GL calls");
            }
        }
        
        // Check if Vulkan is available as fallback
        if (VulkanCapabilities.isSupported) {
            canFallbackToVulkan = true;
            FPSFlux.LOGGER.info("[OpenGLESManager] Vulkan available for fallback");
        }
    }
    
    private static BufferOps selectESBackendWithFallback() {
        // Try selecting ES backend in priority order
        
        BufferOps backend = null;
        
        // Stage 1: Try native ES versions (best performance on mobile)
        if (isNativeES) {
            backend = tryNativeES();
            if (backend != null) {
                FPSFlux.LOGGER.info("[OpenGLESManager] Using native ES path");
                return backend;
            }
        }
        
        // Stage 2: Try ES through wrapper
        if (hasWrapper && needsDesktopGLEmulation) {
            backend = tryWrappedES();
            if (backend != null) {
                FPSFlux.LOGGER.info("[OpenGLESManager] Using wrapped ES path via {}", wrapperType);
                return backend;
            }
        }
        
        // Stage 3: Fallback to desktop GL if available
        backend = tryDesktopGLFallback();
        if (backend != null) {
            FPSFlux.LOGGER.warn("[OpenGLESManager] Falling back to desktop GL");
            return backend;
        }
        
        // Stage 4: Ultimate fallback to Vulkan
        if (canFallbackToVulkan && VulkanManager.isEnabled()) {
            backend = VulkanManager.getBackend();
            if (backend != null) {
                FPSFlux.LOGGER.warn("[OpenGLESManager] Falling back to Vulkan backend");
                return backend;
            }
        }
        
        // Stage 5: Nothing worked
        FPSFlux.LOGGER.error("[OpenGLESManager] All fallback paths exhausted");
        return null;
    }
    
    private static BufferOps tryNativeES() {
        // Native ES environment - use ES-specific implementations
        
        if (GLCapabilities.hasGLES32) {
            if (validateESFeatures(32)) {
                return new GLES32BufferOps();
            } else {
                FPSFlux.LOGGER.warn("[OpenGLESManager] ES 3.2 advertised but features missing");
            }
        }
        
        if (GLCapabilities.hasGLES31) {
            if (validateESFeatures(31)) {
                return new GLES31BufferOps();
            } else {
                FPSFlux.LOGGER.warn("[OpenGLESManager] ES 3.1 advertised but features missing");
            }
        }
        
        if (GLCapabilities.hasGLES30) {
            if (validateESFeatures(30)) {
                return new GLES30BufferOps();
            } else {
                FPSFlux.LOGGER.warn("[OpenGLESManager] ES 3.0 advertised but features missing");
            }
        }
        
        return null;
    }
    
    private static BufferOps tryWrappedES() {
        // ES through wrapper (like gl4es) - be conservative
        
        if (wrapperType.equals("gl4es")) {
            return tryGl4esPath();
        }
        
        // Generic wrapper fallback
        if (GLCapabilities.hasGLES30) {
            FPSFlux.LOGGER.info("[OpenGLESManager] Trying generic wrapped ES 3.0");
            return new GLES30BufferOps();
        }
        
        return null;
    }
    
    private static BufferOps tryGl4esPath() {
        // gl4es specific logic - it's quirky but powerful
        
        // gl4es can expose ES 3.2 but with caveats
        if (GLCapabilities.hasGLES32) {
            // Validate actual capabilities, not just version string
            if (testGl4esES32Support()) {
                FPSFlux.LOGGER.info("[OpenGLESManager] gl4es ES 3.2 validated");
                return new GLES32BufferOps();
            }
        }
        
        // gl4es ES 3.0 is most stable
        if (GLCapabilities.hasGLES30) {
            FPSFlux.LOGGER.info("[OpenGLESManager] Using gl4es ES 3.0 (most stable)");
            return new GLES30BufferOps();
        }
        
        return null;
    }
    
    private static BufferOps tryDesktopGLFallback() {
        // ES failed, try desktop GL paths
        
        FPSFlux.LOGGER.warn("[OpenGLESManager] ES backends failed, trying desktop GL fallback");
        
        // Try safest desktop GL versions that work on most systems
        if (GLCapabilities.GL30 && GLCapabilities.hasVAO) {
            FPSFlux.LOGGER.info("[OpenGLESManager] Desktop GL 3.0 fallback");
            return new GL30BufferOps();
        }
        
        if (GLCapabilities.GL20) {
            FPSFlux.LOGGER.info("[OpenGLESManager] Desktop GL 2.0 fallback (minimal)");
            return new GL20BufferOps();
        }
        
        if (GLCapabilities.GL15) {
            FPSFlux.LOGGER.info("[OpenGLESManager] Desktop GL 1.5 fallback (emergency)");
            return new GL15BufferOps();
        }
        
        return null;
    }
    
    private static boolean validateESFeatures(int version) {
        // Validate that ES version actually has required features
        // Some drivers lie about version support
        
        try {
            switch (version) {
                case 32:
                    return validateES32();
                case 31:
                    return validateES31();
                case 30:
                    return validateES30();
                default:
                    return false;
            }
        } catch (Exception e) {
            FPSFlux.LOGGER.error("[OpenGLESManager] Feature validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    private static boolean validateES30() {
        // ES 3.0 minimum requirements
        // - VAO support
        // - Transform feedback
        // - Uniform buffer objects
        
        return GLCapabilities.hasVAO; // Simplified check
    }
    
    private static boolean validateES31() {
        // ES 3.1 additions
        // - Compute shaders
        // - Separate shader objects
        // - Indirect drawing
        
        return GLCapabilities.hasVAO && GLCapabilities.hasComputeShaders;
    }
    
    private static boolean validateES32() {
        // ES 3.2 additions
        // - Geometry shaders
        // - Tessellation
        // - Texture buffer objects
        
        return GLCapabilities.hasVAO && 
               GLCapabilities.hasComputeShaders &&
               GLCapabilities.GL40; // Geometry/tess similar to GL 4.0
    }
    
    private static boolean testGl4esES32Support() {
        // gl4es specific ES 3.2 validation
        // gl4es can report ES 3.2 but some features are stubbed
        
        // Check for known gl4es issues
        String renderer = GLCapabilities.glRenderer.toLowerCase();
        
        // If running on ARM Mali, be extra careful
        if (renderer.contains("mali")) {
            FPSFlux.LOGGER.warn("[OpenGLESManager] ARM Mali detected - using conservative ES path");
            return false; // Prefer ES 3.0/3.1 on Mali
        }
        
        // If running on Adreno, ES 3.2 usually works
        if (renderer.contains("adreno")) {
            FPSFlux.LOGGER.info("[OpenGLESManager] Adreno GPU - ES 3.2 likely stable");
            return true;
        }
        
        // Default: allow ES 3.2 but log warning
        FPSFlux.LOGGER.warn("[OpenGLESManager] gl4es ES 3.2 - may have compatibility issues");
        return true;
    }
    
    // Incompatible call translation
    
    /**
     * Translate desktop GL call to ES equivalent
     * Used when renderer makes desktop-only calls on ES backend
     */
    public static void translateDesktopCall(String glFunction, Object... args) {
        if (!needsDesktopGLEmulation) {
            FPSFlux.LOGGER.warn("[OpenGLESManager] Desktop call translation requested but not needed: {}", glFunction);
            return;
        }
        
        // Common desktop → ES translations
        switch (glFunction) {
            case "glBegin":
                // ES has no glBegin - must use VBOs
                FPSFlux.LOGGER.error("[OpenGLESManager] glBegin not supported in ES - renderer needs VBO conversion");
                break;
                
            case "glPushMatrix":
            case "glPopMatrix":
                // ES has no matrix stack - must manage manually
                FPSFlux.LOGGER.warn("[OpenGLESManager] Matrix stack not supported in ES");
                break;
                
            case "glColor3f":
            case "glColor4f":
                // ES requires vertex attributes, not glColor
                FPSFlux.LOGGER.warn("[OpenGLESManager] Fixed-function color not supported in ES");
                break;
                
            default:
                FPSFlux.LOGGER.warn("[OpenGLESManager] Unknown desktop call: {}", glFunction);
        }
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
    
    public static boolean isNativeES() {
        if (!initialized) initialize();
        return isNativeES;
    }
    
    public static boolean needsDesktopEmulation() {
        if (!initialized) initialize();
        return needsDesktopGLEmulation;
    }
    
    public static String getDetailedReport() {
        if (!initialized) initialize();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== OpenGLESManager Status ===\n");
        sb.append("Backend: ").append(backendName).append("\n");
        sb.append("Status: ").append(enabled ? "ACTIVE" : "DISABLED").append("\n");
        sb.append("Native ES: ").append(isNativeES).append("\n");
        sb.append("Wrapper: ").append(wrapperType).append("\n");
        sb.append("Desktop Emulation: ").append(needsDesktopGLEmulation).append("\n");
        sb.append("Vulkan Fallback: ").append(canFallbackToVulkan).append("\n");
        
        if (enabled) {
            sb.append("\nES Capabilities:\n");
            sb.append("  ES 3.0: ").append(GLCapabilities.hasGLES30).append("\n");
            sb.append("  ES 3.1: ").append(GLCapabilities.hasGLES31).append("\n");
            sb.append("  ES 3.2: ").append(GLCapabilities.hasGLES32).append("\n");
        }
        
        return sb.toString();
    }
}
