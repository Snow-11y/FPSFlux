package com.example.modid.gl;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.opengl.*;

public class GLCapabilities {
    private static boolean initialized = false;
    
    // Version support
    public static boolean GL15 = false;
    public static boolean GL20 = false;
    public static boolean GL30 = false;
    public static boolean GL31 = false;
    public static boolean GL33 = false;
    public static boolean GL40 = false;
    public static boolean GL42 = false;
    public static boolean GL43 = false;
    public static boolean GL44 = false;
    public static boolean GL45 = false;
    public static boolean GL46 = false;
    
    // Feature flags
    public static boolean hasVBO = false;
    public static boolean hasVAO = false;
    public static boolean hasInstancing = false;
    public static boolean hasBaseInstance = false;
    public static boolean hasMultiDrawIndirect = false;
    public static boolean hasDSA = false;
    public static boolean hasPersistentMapping = false;
    public static boolean hasComputeShaders = false;
    public static boolean hasSSBO = false;
    
    // Performance hints
    public static int maxTextureUnits = 0;
    public static int maxVertexAttribs = 0;
    public static long maxBufferSize = 0;
    
    // Wrapper/compatibility detection
    public static boolean isGLWrapper = false;
    public static String wrapperName = "Native";
    public static String glVersion = "";
    public static String glRenderer = "";
    public static String glVendor = "";
    
    // Detected GL level
    public static int detectedMajor = 1;
    public static int detectedMinor = 1;
    
    public static void detect() {
        if (initialized) return;
        
        try {
            // Get context capabilities
            ContextCapabilities caps = GLContext.getCapabilities();
            
            // Get GL strings for wrapper detection
            glVersion = GL11.glGetString(GL11.GL_VERSION);
            glRenderer = GL11.glGetString(GL11.GL_RENDERER);
            glVendor = GL11.glGetString(GL11.GL_VENDOR);
            
            // Detect GL wrappers
            detectWrapper();
            
            // Parse version
            parseVersion(glVersion);
            
            // Detect version support (conservative if wrapper detected)
            if (isGLWrapper) {
                detectVersionsConservative(caps);
            } else {
                detectVersionsNative(caps);
            }
            
            // Feature detection
            detectFeatures(caps);
            
            // Query limits
            queryLimits();
            
            initialized = true;
            
        } catch (Exception e) {
            System.err.println("[FPSFlux] Failed to detect GL capabilities: " + e.getMessage());
            e.printStackTrace();
            
            // Ultra-safe fallback
            GL15 = true;
            hasVBO = true;
            detectedMajor = 1;
            detectedMinor = 5;
            initialized = true;
        }
    }
    
    private static void detectWrapper() {
        String versionLower = glVersion.toLowerCase();
        String rendererLower = glRenderer.toLowerCase();
        String vendorLower = glVendor.toLowerCase();
        
        // Detect gl4es (ARM/mobile wrapper)
        if (versionLower.contains("gl4es") || 
            rendererLower.contains("gl4es") ||
            vendorLower.contains("gl4es")) {
            isGLWrapper = true;
            wrapperName = "gl4es";
            return;
        }
        
        // Detect ANGLE (Windows D3D wrapper)
        if (rendererLower.contains("angle") || 
            rendererLower.contains("direct3d")) {
            isGLWrapper = true;
            wrapperName = "ANGLE (D3D)";
            return;
        }
        
        // Detect Mesa software renderer
        if (rendererLower.contains("llvmpipe") || 
            rendererLower.contains("softpipe") ||
            rendererLower.contains("software rasterizer")) {
            isGLWrapper = true;
            wrapperName = "Mesa Software";
            return;
        }
        
        // Detect Zink (Vulkan-to-OpenGL)
        if (rendererLower.contains("zink")) {
            isGLWrapper = true;
            wrapperName = "Zink (Vulkan)";
            return;
        }
        
        // Detect VirGL (virtualized GL)
        if (rendererLower.contains("virgl")) {
            isGLWrapper = true;
            wrapperName = "VirGL (Virtual)";
            return;
        }
        
        wrapperName = "Native";
    }
    
    private static void parseVersion(String version) {
        try {
            String[] parts = version.split("\\s+")[0].split("\\.");
            detectedMajor = Integer.parseInt(parts[0]);
            detectedMinor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        } catch (Exception e) {
            // Fallback to minimal
            detectedMajor = 1;
            detectedMinor = 1;
        }
    }
    
    private static void detectVersionsNative(ContextCapabilities caps) {
        // Native GPU - trust reported versions
        GL15 = detectedMajor > 1 || (detectedMajor == 1 && detectedMinor >= 5);
        GL20 = detectedMajor >= 2;
        GL30 = detectedMajor >= 3;
        GL31 = detectedMajor > 3 || (detectedMajor == 3 && detectedMinor >= 1);
        GL33 = detectedMajor > 3 || (detectedMajor == 3 && detectedMinor >= 3);
        GL40 = detectedMajor >= 4;
        GL42 = detectedMajor > 4 || (detectedMajor == 4 && detectedMinor >= 2);
        GL43 = detectedMajor > 4 || (detectedMajor == 4 && detectedMinor >= 3);
        GL44 = detectedMajor > 4 || (detectedMajor == 4 && detectedMinor >= 4);
        GL45 = detectedMajor > 4 || (detectedMajor == 4 && detectedMinor >= 5);
        GL46 = detectedMajor > 4 || (detectedMajor == 4 && detectedMinor >= 6);
    }
    
    private static void detectVersionsConservative(ContextCapabilities caps) {
        // Wrapper detected - verify each feature individually
        // Don't trust version string, test actual capabilities
        
        GL15 = testGL15(caps);
        GL20 = testGL20(caps);
        GL30 = testGL30(caps);
        GL31 = testGL31(caps);
        GL33 = testGL33(caps);
        GL40 = testGL40(caps);
        GL42 = testGL42(caps);
        GL43 = testGL43(caps);
        GL44 = testGL44(caps);
        GL45 = testGL45(caps);
        GL46 = testGL46(caps);
    }
    
    private static boolean testGL15(ContextCapabilities caps) {
        return caps.GL_ARB_vertex_buffer_object || caps.OpenGL15;
    }
    
    private static boolean testGL20(ContextCapabilities caps) {
        return (caps.GL_ARB_shader_objects && 
                caps.GL_ARB_vertex_shader && 
                caps.GL_ARB_fragment_shader) || caps.OpenGL20;
    }
    
    private static boolean testGL30(ContextCapabilities caps) {
        return (caps.GL_ARB_vertex_array_object && 
                caps.GL_ARB_framebuffer_object && 
                caps.GL_ARB_map_buffer_range) || caps.OpenGL30;
    }
    
    private static boolean testGL31(ContextCapabilities caps) {
        return (caps.GL_ARB_copy_buffer && 
                caps.GL_ARB_uniform_buffer_object) || caps.OpenGL31;
    }
    
    private static boolean testGL33(ContextCapabilities caps) {
        return (caps.GL_ARB_instanced_arrays && 
                caps.GL_ARB_blend_func_extended) || caps.OpenGL33;
    }
    
    private static boolean testGL40(ContextCapabilities caps) {
        return (caps.GL_ARB_tessellation_shader && 
                caps.GL_ARB_transform_feedback2) || caps.OpenGL40;
    }
    
    private static boolean testGL42(ContextCapabilities caps) {
        return (caps.GL_ARB_base_instance && 
                caps.GL_ARB_texture_storage) || caps.OpenGL42;
    }
    
    private static boolean testGL43(ContextCapabilities caps) {
        return (caps.GL_ARB_multi_draw_indirect && 
                caps.GL_ARB_compute_shader && 
                caps.GL_ARB_shader_storage_buffer_object) || caps.OpenGL43;
    }
    
    private static boolean testGL44(ContextCapabilities caps) {
        return (caps.GL_ARB_buffer_storage && 
                caps.GL_ARB_clear_texture) || caps.OpenGL44;
    }
    
    private static boolean testGL45(ContextCapabilities caps) {
        return (caps.GL_ARB_direct_state_access && 
                caps.GL_ARB_clip_control) || caps.OpenGL45;
    }
    
    private static boolean testGL46(ContextCapabilities caps) {
        return (caps.GL_ARB_spirv_extensions && 
                caps.GL_ARB_polygon_offset_clamp) || caps.OpenGL46;
    }
    
    private static void detectFeatures(ContextCapabilities caps) {
        hasVBO = GL15 && caps.GL_ARB_vertex_buffer_object;
        hasVAO = GL30 && caps.GL_ARB_vertex_array_object;
        hasInstancing = GL33 && caps.GL_ARB_instanced_arrays;
        hasBaseInstance = GL42 && caps.GL_ARB_base_instance;
        hasMultiDrawIndirect = GL43 && caps.GL_ARB_multi_draw_indirect;
        hasDSA = GL45 && caps.GL_ARB_direct_state_access;
        hasPersistentMapping = GL44 && caps.GL_ARB_buffer_storage;
        hasComputeShaders = GL43 && caps.GL_ARB_compute_shader;
        hasSSBO = GL43 && caps.GL_ARB_shader_storage_buffer_object;
        
        // Additional safety: disable advanced features on wrappers
        if (isGLWrapper) {
            // gl4es typically doesn't support these reliably
            if (wrapperName.equals("gl4es")) {
                hasPersistentMapping = false;
                hasMultiDrawIndirect = false;
                hasComputeShaders = false;
                hasSSBO = false;
                hasDSA = false; // gl4es DSA is buggy
            }
            
            // Mesa software is slow, disable expensive features
            if (wrapperName.contains("Mesa Software")) {
                hasMultiDrawIndirect = false;
                hasComputeShaders = false;
                hasPersistentMapping = false;
            }
        }
    }
    
    private static void queryLimits() {
        try {
            maxTextureUnits = GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
        } catch (Exception e) {
            maxTextureUnits = 2; // Minimum safe value
        }
        
        if (GL20) {
            try {
                maxVertexAttribs = GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS);
            } catch (Exception e) {
                maxVertexAttribs = 8;
            }
        }
    }
    
    public static String getReport() {
        if (!initialized) detect();
        
        StringBuilder sb = new StringBuilder();
        sb.append("OpenGL ").append(glVersion).append("\n");
        sb.append("Renderer: ").append(glRenderer).append("\n");
        sb.append("Vendor: ").append(glVendor).append("\n");
        sb.append("Wrapper: ").append(wrapperName).append("\n");
        sb.append("Detected: GL ").append(detectedMajor).append(".").append(detectedMinor).append("\n");
        sb.append("Features: ");
        sb.append("VBO:").append(hasVBO).append(" ");
        sb.append("VAO:").append(hasVAO).append(" ");
        sb.append("Instancing:").append(hasInstancing).append(" ");
        sb.append("DSA:").append(hasDSA).append(" ");
        sb.append("PersistentMap:").append(hasPersistentMapping).append(" ");
        sb.append("MultiDrawIndirect:").append(hasMultiDrawIndirect);
        
        return sb.toString();
    }
    
    public static int getMaxSupportedVersion() {
        if (!initialized) detect();
        
        if (GL46) return 46;
        if (GL45) return 45;
        if (GL44) return 44;
        if (GL43) return 43;
        if (GL42) return 42;
        if (GL40) return 40;
        if (GL33) return 33;
        if (GL31) return 31;
        if (GL30) return 30;
        if (GL20) return 20;
        if (GL15) return 15;
        return 11; // Absolute minimum
    }
}
