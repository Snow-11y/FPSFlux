package com.example.modid.gl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UniversalCapabilities - A comprehensive graphics API capability detection and configuration system.
 * 
 * Supports:
 * - OpenGL 1.0 - 4.6
 * - OpenGL ES 1.0 - 3.2
 * - GLSL 1.10 - 4.60
 * - GLSL ES 1.00 - 3.20
 * - SPIR-V 1.0 - 1.6
 * - Vulkan 1.0 - 1.4
 * 
 * Acts as a universal control panel that validates user preferences against hardware capabilities.
 * 
 * @snowy Enhanced Graphics Capability System
 * @version 2.0.0
 */
public class UniversalCapabilities {
    
    //===========================================================================================================
    // INITIALIZATION STATE
    //===========================================================================================================
    
    private static boolean initialized = false;
    private static boolean vulkanInitialized = false;
    private static final Object initLock = new Object();
    
    //===========================================================================================================
    // CONFIGURATION - WHAT USER WANTS (Loadable from external config)
    //===========================================================================================================
    
    public static final class Config {
        // Preferred API
        public enum PreferredAPI {
            AUTO,           // Automatically select best available
            OPENGL,         // Force OpenGL
            OPENGL_ES,      // Force OpenGL ES
            VULKAN,         // Force Vulkan
            OPENGL_COMPAT,  // OpenGL Compatibility Profile
            OPENGL_CORE     // OpenGL Core Profile
        }
        
        // Rendering tier preferences
        public enum RenderTier {
            ULTRA,          // Use all available features
            HIGH,           // High quality, modern features
            MEDIUM,         // Balanced quality/compatibility
            LOW,            // Maximum compatibility
            POTATO,         // Absolute minimum (GL 1.5 level)
            CUSTOM          // User-defined feature set
        }
        
        // Feature preferences
        public enum FeatureLevel {
            REQUIRED,       // Must have this feature
            PREFERRED,      // Want this feature if available
            DISABLED,       // Explicitly disabled
            AUTO            // System decides
        }
        
        // API Preferences
        public PreferredAPI preferredAPI = PreferredAPI.AUTO;
        public RenderTier renderTier = RenderTier.AUTO;
        
        // Version requirements
        public int minGLMajor = 1;
        public int minGLMinor = 5;
        public int maxGLMajor = 4;
        public int maxGLMinor = 6;
        
        public int minGLESMajor = 2;
        public int minGLESMinor = 0;
        public int maxGLESMajor = 3;
        public int maxGLESMinor = 2;
        
        public int minVulkanMajor = 1;
        public int minVulkanMinor = 0;
        public int maxVulkanMajor = 1;
        public int maxVulkanMinor = 4;
        
        public int minGLSLMajor = 1;
        public int minGLSLMinor = 10;
        public int maxGLSLMajor = 4;
        public int maxGLSLMinor = 60;
        
        public int minSPIRVMajor = 1;
        public int minSPIRVMinor = 0;
        public int maxSPIRVMajor = 1;
        public int maxSPIRVMinor = 6;
        
        // Feature toggles
        public FeatureLevel useVBO = FeatureLevel.AUTO;
        public FeatureLevel useVAO = FeatureLevel.AUTO;
        public FeatureLevel useInstancing = FeatureLevel.AUTO;
        public FeatureLevel useBaseInstance = FeatureLevel.AUTO;
        public FeatureLevel useMultiDrawIndirect = FeatureLevel.AUTO;
        public FeatureLevel useDSA = FeatureLevel.AUTO;
        public FeatureLevel usePersistentMapping = FeatureLevel.AUTO;
        public FeatureLevel useComputeShaders = FeatureLevel.AUTO;
        public FeatureLevel useSSBO = FeatureLevel.AUTO;
        public FeatureLevel useTessellation = FeatureLevel.AUTO;
        public FeatureLevel useGeometryShaders = FeatureLevel.AUTO;
        public FeatureLevel useSPIRV = FeatureLevel.AUTO;
        public FeatureLevel useBindlessTextures = FeatureLevel.AUTO;
        public FeatureLevel useSparseTextures = FeatureLevel.AUTO;
        public FeatureLevel useMeshShaders = FeatureLevel.AUTO;
        public FeatureLevel useRayTracing = FeatureLevel.AUTO;
        
        // Performance settings
        public boolean preferBatching = true;
        public boolean preferAsyncCompute = true;
        public boolean preferAsyncTransfer = true;
        public boolean allowFallback = true;
        public boolean strictValidation = false;
        public boolean enableDebugOutput = false;
        
        // Memory settings
        public long maxBufferMemoryMB = 512;
        public long maxTextureMemoryMB = 1024;
        public int maxTextureSize = 0; // 0 = use hardware max
        public int maxAnisotropy = 16;
        
        // Workaround flags
        public boolean forceCompatibilityProfile = false;
        public boolean disableExtensions = false;
        public boolean emulateModernGL = false;
        public boolean trustDriverVersion = true;
        
        // Wrapper-specific overrides
        public Map<String, Map<String, Object>> wrapperOverrides = new HashMap<>();
        
        /**
         * Load configuration from a properties map
         */
        public void loadFromMap(Map<String, Object> properties) {
            if (properties == null) return;
            
            if (properties.containsKey("preferredAPI")) {
                try {
                    preferredAPI = PreferredAPI.valueOf(properties.get("preferredAPI").toString().toUpperCase());
                } catch (Exception ignored) {}
            }
            
            if (properties.containsKey("renderTier")) {
                try {
                    renderTier = RenderTier.valueOf(properties.get("renderTier").toString().toUpperCase());
                } catch (Exception ignored) {}
            }
            
            // Version requirements
            minGLMajor = getInt(properties, "minGLMajor", minGLMajor);
            minGLMinor = getInt(properties, "minGLMinor", minGLMinor);
            maxGLMajor = getInt(properties, "maxGLMajor", maxGLMajor);
            maxGLMinor = getInt(properties, "maxGLMinor", maxGLMinor);
            
            minGLESMajor = getInt(properties, "minGLESMajor", minGLESMajor);
            minGLESMinor = getInt(properties, "minGLESMinor", minGLESMinor);
            maxGLESMajor = getInt(properties, "maxGLESMajor", maxGLESMajor);
            maxGLESMinor = getInt(properties, "maxGLESMinor", maxGLESMinor);
            
            minVulkanMajor = getInt(properties, "minVulkanMajor", minVulkanMajor);
            minVulkanMinor = getInt(properties, "minVulkanMinor", minVulkanMinor);
            maxVulkanMajor = getInt(properties, "maxVulkanMajor", maxVulkanMajor);
            maxVulkanMinor = getInt(properties, "maxVulkanMinor", maxVulkanMinor);
            
            // Feature levels
            useVBO = getFeatureLevel(properties, "useVBO", useVBO);
            useVAO = getFeatureLevel(properties, "useVAO", useVAO);
            useInstancing = getFeatureLevel(properties, "useInstancing", useInstancing);
            useBaseInstance = getFeatureLevel(properties, "useBaseInstance", useBaseInstance);
            useMultiDrawIndirect = getFeatureLevel(properties, "useMultiDrawIndirect", useMultiDrawIndirect);
            useDSA = getFeatureLevel(properties, "useDSA", useDSA);
            usePersistentMapping = getFeatureLevel(properties, "usePersistentMapping", usePersistentMapping);
            useComputeShaders = getFeatureLevel(properties, "useComputeShaders", useComputeShaders);
            useSSBO = getFeatureLevel(properties, "useSSBO", useSSBO);
            useTessellation = getFeatureLevel(properties, "useTessellation", useTessellation);
            useGeometryShaders = getFeatureLevel(properties, "useGeometryShaders", useGeometryShaders);
            useSPIRV = getFeatureLevel(properties, "useSPIRV", useSPIRV);
            useBindlessTextures = getFeatureLevel(properties, "useBindlessTextures", useBindlessTextures);
            useSparseTextures = getFeatureLevel(properties, "useSparseTextures", useSparseTextures);
            useMeshShaders = getFeatureLevel(properties, "useMeshShaders", useMeshShaders);
            useRayTracing = getFeatureLevel(properties, "useRayTracing", useRayTracing);
            
            // Booleans
            preferBatching = getBool(properties, "preferBatching", preferBatching);
            preferAsyncCompute = getBool(properties, "preferAsyncCompute", preferAsyncCompute);
            preferAsyncTransfer = getBool(properties, "preferAsyncTransfer", preferAsyncTransfer);
            allowFallback = getBool(properties, "allowFallback", allowFallback);
            strictValidation = getBool(properties, "strictValidation", strictValidation);
            enableDebugOutput = getBool(properties, "enableDebugOutput", enableDebugOutput);
            forceCompatibilityProfile = getBool(properties, "forceCompatibilityProfile", forceCompatibilityProfile);
            disableExtensions = getBool(properties, "disableExtensions", disableExtensions);
            emulateModernGL = getBool(properties, "emulateModernGL", emulateModernGL);
            trustDriverVersion = getBool(properties, "trustDriverVersion", trustDriverVersion);
            
            // Memory
            maxBufferMemoryMB = getLong(properties, "maxBufferMemoryMB", maxBufferMemoryMB);
            maxTextureMemoryMB = getLong(properties, "maxTextureMemoryMB", maxTextureMemoryMB);
            maxTextureSize = getInt(properties, "maxTextureSize", maxTextureSize);
            maxAnisotropy = getInt(properties, "maxAnisotropy", maxAnisotropy);
        }
        
        /**
         * Export configuration to a properties map
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            
            map.put("preferredAPI", preferredAPI.name());
            map.put("renderTier", renderTier.name());
            
            map.put("minGLMajor", minGLMajor);
            map.put("minGLMinor", minGLMinor);
            map.put("maxGLMajor", maxGLMajor);
            map.put("maxGLMinor", maxGLMinor);
            
            map.put("minGLESMajor", minGLESMajor);
            map.put("minGLESMinor", minGLESMinor);
            map.put("maxGLESMajor", maxGLESMajor);
            map.put("maxGLESMinor", maxGLESMinor);
            
            map.put("minVulkanMajor", minVulkanMajor);
            map.put("minVulkanMinor", minVulkanMinor);
            map.put("maxVulkanMajor", maxVulkanMajor);
            map.put("maxVulkanMinor", maxVulkanMinor);
            
            map.put("useVBO", useVBO.name());
            map.put("useVAO", useVAO.name());
            map.put("useInstancing", useInstancing.name());
            map.put("useBaseInstance", useBaseInstance.name());
            map.put("useMultiDrawIndirect", useMultiDrawIndirect.name());
            map.put("useDSA", useDSA.name());
            map.put("usePersistentMapping", usePersistentMapping.name());
            map.put("useComputeShaders", useComputeShaders.name());
            map.put("useSSBO", useSSBO.name());
            map.put("useTessellation", useTessellation.name());
            map.put("useGeometryShaders", useGeometryShaders.name());
            map.put("useSPIRV", useSPIRV.name());
            map.put("useBindlessTextures", useBindlessTextures.name());
            map.put("useSparseTextures", useSparseTextures.name());
            map.put("useMeshShaders", useMeshShaders.name());
            map.put("useRayTracing", useRayTracing.name());
            
            map.put("preferBatching", preferBatching);
            map.put("preferAsyncCompute", preferAsyncCompute);
            map.put("preferAsyncTransfer", preferAsyncTransfer);
            map.put("allowFallback", allowFallback);
            map.put("strictValidation", strictValidation);
            map.put("enableDebugOutput", enableDebugOutput);
            
            map.put("maxBufferMemoryMB", maxBufferMemoryMB);
            map.put("maxTextureMemoryMB", maxTextureMemoryMB);
            map.put("maxTextureSize", maxTextureSize);
            map.put("maxAnisotropy", maxAnisotropy);
            
            map.put("forceCompatibilityProfile", forceCompatibilityProfile);
            map.put("disableExtensions", disableExtensions);
            map.put("emulateModernGL", emulateModernGL);
            map.put("trustDriverVersion", trustDriverVersion);
            
            return map;
        }
        
        /**
         * Apply a render tier preset
         */
        public void applyTierPreset(RenderTier tier) {
            this.renderTier = tier;
            
            switch (tier) {
                case ULTRA:
                    minGLMajor = 4; minGLMinor = 5;
                    useVBO = FeatureLevel.REQUIRED;
                    useVAO = FeatureLevel.REQUIRED;
                    useInstancing = FeatureLevel.REQUIRED;
                    useBaseInstance = FeatureLevel.REQUIRED;
                    useMultiDrawIndirect = FeatureLevel.PREFERRED;
                    useDSA = FeatureLevel.PREFERRED;
                    usePersistentMapping = FeatureLevel.PREFERRED;
                    useComputeShaders = FeatureLevel.PREFERRED;
                    useSSBO = FeatureLevel.PREFERRED;
                    useTessellation = FeatureLevel.AUTO;
                    useGeometryShaders = FeatureLevel.AUTO;
                    useSPIRV = FeatureLevel.AUTO;
                    useBindlessTextures = FeatureLevel.AUTO;
                    useMeshShaders = FeatureLevel.AUTO;
                    break;
                    
                case HIGH:
                    minGLMajor = 4; minGLMinor = 3;
                    useVBO = FeatureLevel.REQUIRED;
                    useVAO = FeatureLevel.REQUIRED;
                    useInstancing = FeatureLevel.REQUIRED;
                    useBaseInstance = FeatureLevel.PREFERRED;
                    useMultiDrawIndirect = FeatureLevel.AUTO;
                    useDSA = FeatureLevel.AUTO;
                    usePersistentMapping = FeatureLevel.AUTO;
                    useComputeShaders = FeatureLevel.AUTO;
                    useSSBO = FeatureLevel.AUTO;
                    useTessellation = FeatureLevel.DISABLED;
                    useGeometryShaders = FeatureLevel.DISABLED;
                    useSPIRV = FeatureLevel.DISABLED;
                    break;
                    
                case MEDIUM:
                    minGLMajor = 3; minGLMinor = 3;
                    useVBO = FeatureLevel.REQUIRED;
                    useVAO = FeatureLevel.REQUIRED;
                    useInstancing = FeatureLevel.PREFERRED;
                    useBaseInstance = FeatureLevel.DISABLED;
                    useMultiDrawIndirect = FeatureLevel.DISABLED;
                    useDSA = FeatureLevel.DISABLED;
                    usePersistentMapping = FeatureLevel.DISABLED;
                    useComputeShaders = FeatureLevel.DISABLED;
                    useSSBO = FeatureLevel.DISABLED;
                    break;
                    
                case LOW:
                    minGLMajor = 2; minGLMinor = 1;
                    useVBO = FeatureLevel.REQUIRED;
                    useVAO = FeatureLevel.PREFERRED;
                    useInstancing = FeatureLevel.DISABLED;
                    useBaseInstance = FeatureLevel.DISABLED;
                    useMultiDrawIndirect = FeatureLevel.DISABLED;
                    useDSA = FeatureLevel.DISABLED;
                    usePersistentMapping = FeatureLevel.DISABLED;
                    useComputeShaders = FeatureLevel.DISABLED;
                    useSSBO = FeatureLevel.DISABLED;
                    break;
                    
                case POTATO:
                    minGLMajor = 1; minGLMinor = 5;
                    useVBO = FeatureLevel.PREFERRED;
                    useVAO = FeatureLevel.DISABLED;
                    useInstancing = FeatureLevel.DISABLED;
                    useBaseInstance = FeatureLevel.DISABLED;
                    useMultiDrawIndirect = FeatureLevel.DISABLED;
                    useDSA = FeatureLevel.DISABLED;
                    usePersistentMapping = FeatureLevel.DISABLED;
                    useComputeShaders = FeatureLevel.DISABLED;
                    useSSBO = FeatureLevel.DISABLED;
                    break;
                    
                case CUSTOM:
                default:
                    // Keep current settings
                    break;
            }
        }
        
        private static int getInt(Map<String, Object> map, String key, int def) {
            Object v = map.get(key);
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof String) {
                try { return Integer.parseInt((String) v); } catch (Exception ignored) {}
            }
            return def;
        }
        
        private static long getLong(Map<String, Object> map, String key, long def) {
            Object v = map.get(key);
            if (v instanceof Number) return ((Number) v).longValue();
            if (v instanceof String) {
                try { return Long.parseLong((String) v); } catch (Exception ignored) {}
            }
            return def;
        }
        
        private static boolean getBool(Map<String, Object> map, String key, boolean def) {
            Object v = map.get(key);
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof String) return Boolean.parseBoolean((String) v);
            return def;
        }
        
        private static FeatureLevel getFeatureLevel(Map<String, Object> map, String key, FeatureLevel def) {
            Object v = map.get(key);
            if (v instanceof FeatureLevel) return (FeatureLevel) v;
            if (v instanceof String) {
                try { return FeatureLevel.valueOf(((String) v).toUpperCase()); } catch (Exception ignored) {}
            }
            return def;
        }
    }
    
    // User configuration instance
    public static final Config config = new Config();
    
    //===========================================================================================================
    // OPENGL VERSIONS - Complete 1.0 to 4.6
    //===========================================================================================================
    
    public static final class GL {
        // All OpenGL versions
        public static boolean GL10 = false;  // 1992
        public static boolean GL11 = false;  // 1997
        public static boolean GL12 = false;  // 1998
        public static boolean GL13 = false;  // 2001
        public static boolean GL14 = false;  // 2002
        public static boolean GL15 = false;  // 2003
        public static boolean GL20 = false;  // 2004
        public static boolean GL21 = false;  // 2006
        public static boolean GL30 = false;  // 2008
        public static boolean GL31 = false;  // 2009
        public static boolean GL32 = false;  // 2009
        public static boolean GL33 = false;  // 2010
        public static boolean GL40 = false;  // 2010
        public static boolean GL41 = false;  // 2010
        public static boolean GL42 = false;  // 2011
        public static boolean GL43 = false;  // 2012
        public static boolean GL44 = false;  // 2013
        public static boolean GL45 = false;  // 2014
        public static boolean GL46 = false;  // 2017
        
        // Profile flags
        public static boolean isCoreProfile = false;
        public static boolean isCompatibilityProfile = false;
        public static boolean isForwardCompatible = false;
        
        // Version info
        public static int majorVersion = 1;
        public static int minorVersion = 0;
        public static String versionString = "";
        public static String renderer = "";
        public static String vendor = "";
        public static String shadingLanguageVersion = "";
        
        // Extension set
        public static final Set<String> extensions = new HashSet<>();
        public static int extensionCount = 0;
        
        /**
         * Check if a specific GL version is supported
         */
        public static boolean isVersionSupported(int major, int minor) {
            int target = major * 10 + minor;
            int current = majorVersion * 10 + minorVersion;
            return current >= target;
        }
        
        /**
         * Get version as combined integer (e.g., 45 for GL 4.5)
         */
        public static int getVersionInt() {
            return majorVersion * 10 + minorVersion;
        }
        
        /**
         * Check extension support
         */
        public static boolean hasExtension(String name) {
            return extensions.contains(name);
        }
        
        /**
         * Check any of multiple extensions
         */
        public static boolean hasAnyExtension(String... names) {
            for (String name : names) {
                if (extensions.contains(name)) return true;
            }
            return false;
        }
        
        /**
         * Check all of multiple extensions
         */
        public static boolean hasAllExtensions(String... names) {
            for (String name : names) {
                if (!extensions.contains(name)) return false;
            }
            return true;
        }
    }
    
    //===========================================================================================================
    // OPENGL ES VERSIONS - Complete 1.0 to 3.2
    //===========================================================================================================
    
    public static final class GLES {
        // All OpenGL ES versions
        public static boolean ES10 = false;  // 2003 (OpenGL ES 1.0)
        public static boolean ES11 = false;  // 2004 (OpenGL ES 1.1)
        public static boolean ES20 = false;  // 2007 (OpenGL ES 2.0)
        public static boolean ES30 = false;  // 2012 (OpenGL ES 3.0)
        public static boolean ES31 = false;  // 2014 (OpenGL ES 3.1)
        public static boolean ES32 = false;  // 2015 (OpenGL ES 3.2)
        
        // Context info
        public static boolean isGLESContext = false;
        public static int majorVersion = 0;
        public static int minorVersion = 0;
        public static String versionString = "";
        
        // Extension set
        public static final Set<String> extensions = new HashSet<>();
        
        // Feature packs (ES 3.1 AEP, etc.)
        public static boolean hasAndroidExtensionPack = false;
        
        /**
         * Check if a specific GLES version is supported
         */
        public static boolean isVersionSupported(int major, int minor) {
            if (!isGLESContext && !Wrapper.isGLES) return false;
            int target = major * 10 + minor;
            int current = majorVersion * 10 + minorVersion;
            return current >= target;
        }
        
        /**
         * Get version as combined integer
         */
        public static int getVersionInt() {
            return majorVersion * 10 + minorVersion;
        }
        
        /**
         * Check extension support
         */
        public static boolean hasExtension(String name) {
            return extensions.contains(name);
        }
    }
    
    //===========================================================================================================
    // GLSL VERSIONS - Complete 1.10 to 4.60 + ES 1.00 to 3.20
    //===========================================================================================================
    
    public static final class GLSL {
        // Desktop GLSL versions
        public static boolean GLSL_110 = false;  // GL 2.0 - #version 110
        public static boolean GLSL_120 = false;  // GL 2.1 - #version 120
        public static boolean GLSL_130 = false;  // GL 3.0 - #version 130
        public static boolean GLSL_140 = false;  // GL 3.1 - #version 140
        public static boolean GLSL_150 = false;  // GL 3.2 - #version 150
        public static boolean GLSL_330 = false;  // GL 3.3 - #version 330
        public static boolean GLSL_400 = false;  // GL 4.0 - #version 400
        public static boolean GLSL_410 = false;  // GL 4.1 - #version 410
        public static boolean GLSL_420 = false;  // GL 4.2 - #version 420
        public static boolean GLSL_430 = false;  // GL 4.3 - #version 430
        public static boolean GLSL_440 = false;  // GL 4.4 - #version 440
        public static boolean GLSL_450 = false;  // GL 4.5 - #version 450
        public static boolean GLSL_460 = false;  // GL 4.6 - #version 460
        
        // ES GLSL versions
        public static boolean GLSL_ES_100 = false;  // GLES 2.0 - #version 100 es
        public static boolean GLSL_ES_300 = false;  // GLES 3.0 - #version 300 es
        public static boolean GLSL_ES_310 = false;  // GLES 3.1 - #version 310 es
        public static boolean GLSL_ES_320 = false;  // GLES 3.2 - #version 320 es
        
        // Version info
        public static int majorVersion = 1;
        public static int minorVersion = 10;
        public static String versionString = "";
        
        // GLSL ES version info
        public static int esMajorVersion = 0;
        public static int esMinorVersion = 0;
        
        // Feature support
        public static boolean hasExplicitUniformLocation = false;  // layout(location = n)
        public static boolean hasSSBO = false;
        public static boolean hasComputeShaders = false;
        public static boolean hasSubroutines = false;
        public static boolean hasTessellation = false;
        public static boolean hasGeometryShaders = false;
        public static boolean hasDouble = false;
        public static boolean hasInt64 = false;
        public static boolean hasFloat16 = false;
        public static boolean hasDerivatives = false;
        public static boolean hasTextureGather = false;
        public static boolean hasImageLoadStore = false;
        public static boolean hasAtomics = false;
        public static boolean hasSPIRVInput = false;
        
        /**
         * Get the #version directive string for the current GLSL version
         */
        public static String getVersionDirective(boolean core) {
            if (GLES.isGLESContext || Wrapper.isGLES) {
                if (GLSL_ES_320) return "#version 320 es";
                if (GLSL_ES_310) return "#version 310 es";
                if (GLSL_ES_300) return "#version 300 es";
                return "#version 100";
            } else {
                int version = getVersionInt();
                if (version >= 150 && core) {
                    return "#version " + version + " core";
                } else if (version >= 150) {
                    return "#version " + version + " compatibility";
                }
                return "#version " + version;
            }
        }
        
        /**
         * Get version as combined integer (e.g., 450 for GLSL 4.50)
         */
        public static int getVersionInt() {
            return majorVersion * 100 + minorVersion;
        }
        
        /**
         * Get ES version as combined integer
         */
        public static int getESVersionInt() {
            return esMajorVersion * 100 + esMinorVersion;
        }
        
        /**
         * Check if a specific GLSL version is supported
         */
        public static boolean isVersionSupported(int major, int minor) {
            int target = major * 100 + minor;
            int current = getVersionInt();
            return current >= target;
        }
    }
    
    //===========================================================================================================
    // SPIR-V VERSIONS - 1.0 to 1.6
    //===========================================================================================================
    
    public static final class SPIRV {
        // SPIR-V versions
        public static boolean SPIRV_10 = false;  // Vulkan 1.0
        public static boolean SPIRV_11 = false;  // Vulkan 1.0 + ext
        public static boolean SPIRV_12 = false;  // Vulkan 1.0 + ext
        public static boolean SPIRV_13 = false;  // Vulkan 1.1
        public static boolean SPIRV_14 = false;  // Vulkan 1.1 + ext / 1.2
        public static boolean SPIRV_15 = false;  // Vulkan 1.2
        public static boolean SPIRV_16 = false;  // Vulkan 1.3
        
        // Version info
        public static int majorVersion = 0;
        public static int minorVersion = 0;
        
        // GL SPIR-V support (ARB_gl_spirv)
        public static boolean hasGLSPIRV = false;
        public static boolean hasGLSPIRVExtensions = false;
        
        // Capability sets
        public static final Set<String> capabilities = new HashSet<>();
        public static final Set<String> extensions = new HashSet<>();
        
        // Notable capabilities
        public static boolean hasShader = false;
        public static boolean hasGeometry = false;
        public static boolean hasTessellation = false;
        public static boolean hasFloat64 = false;
        public static boolean hasInt64 = false;
        public static boolean hasInt16 = false;
        public static boolean hasInt8 = false;
        public static boolean hasFloat16 = false;
        public static boolean hasStorageImageMultisample = false;
        public static boolean hasVariablePointers = false;
        public static boolean hasRayTracingKHR = false;
        public static boolean hasMeshShadingNV = false;
        public static boolean hasMeshShadingEXT = false;
        
        /**
         * Get version as combined integer (e.g., 16 for SPIR-V 1.6)
         */
        public static int getVersionInt() {
            return majorVersion * 10 + minorVersion;
        }
        
        /**
         * Check if a specific SPIR-V version is supported
         */
        public static boolean isVersionSupported(int major, int minor) {
            int target = major * 10 + minor;
            int current = getVersionInt();
            return current >= target;
        }
    }
    
    //===========================================================================================================
    // VULKAN VERSIONS - 1.0 to 1.4
    //===========================================================================================================
    
    public static final class Vulkan {
        // Vulkan versions
        public static boolean VK10 = false;  // 2016
        public static boolean VK11 = false;  // 2018
        public static boolean VK12 = false;  // 2020
        public static boolean VK13 = false;  // 2022
        public static boolean VK14 = false;  // 2024
        
        // Availability
        public static boolean isAvailable = false;
        public static boolean isLoaded = false;
        
        // Version info
        public static int apiVersion = 0;
        public static int driverVersion = 0;
        public static int majorVersion = 0;
        public static int minorVersion = 0;
        public static int patchVersion = 0;
        
        // Device info
        public static String deviceName = "";
        public static String driverName = "";
        public static String driverInfo = "";
        public static int vendorID = 0;
        public static int deviceID = 0;
        public static int deviceType = 0; // VK_PHYSICAL_DEVICE_TYPE_*
        
        // Device types
        public static final int DEVICE_TYPE_OTHER = 0;
        public static final int DEVICE_TYPE_INTEGRATED_GPU = 1;
        public static final int DEVICE_TYPE_DISCRETE_GPU = 2;
        public static final int DEVICE_TYPE_VIRTUAL_GPU = 3;
        public static final int DEVICE_TYPE_CPU = 4;
        
        // Extension set
        public static final Set<String> instanceExtensions = new HashSet<>();
        public static final Set<String> deviceExtensions = new HashSet<>();
        
        // Queue family support
        public static boolean hasGraphics = false;
        public static boolean hasCompute = false;
        public static boolean hasTransfer = false;
        public static boolean hasSparseBinding = false;
        public static boolean hasProtected = false;
        
        // Feature support (VK 1.0 core)
        public static boolean hasRobustBufferAccess = false;
        public static boolean hasFullDrawIndexUint32 = false;
        public static boolean hasImageCubeArray = false;
        public static boolean hasIndependentBlend = false;
        public static boolean hasGeometryShader = false;
        public static boolean hasTessellationShader = false;
        public static boolean hasSampleRateShading = false;
        public static boolean hasDualSrcBlend = false;
        public static boolean hasLogicOp = false;
        public static boolean hasMultiDrawIndirect = false;
        public static boolean hasDrawIndirectFirstInstance = false;
        public static boolean hasDepthClamp = false;
        public static boolean hasDepthBiasClamp = false;
        public static boolean hasFillModeNonSolid = false;
        public static boolean hasDepthBounds = false;
        public static boolean hasWideLines = false;
        public static boolean hasLargePoints = false;
        public static boolean hasAlphaToOne = false;
        public static boolean hasMultiViewport = false;
        public static boolean hasSamplerAnisotropy = false;
        public static boolean hasTextureCompressionETC2 = false;
        public static boolean hasTextureCompressionASTC_LDR = false;
        public static boolean hasTextureCompressionBC = false;
        public static boolean hasOcclusionQueryPrecise = false;
        public static boolean hasPipelineStatisticsQuery = false;
        public static boolean hasVertexPipelineStoresAndAtomics = false;
        public static boolean hasFragmentStoresAndAtomics = false;
        public static boolean hasShaderTessellationAndGeometryPointSize = false;
        public static boolean hasShaderImageGatherExtended = false;
        public static boolean hasShaderStorageImageExtendedFormats = false;
        public static boolean hasShaderStorageImageMultisample = false;
        public static boolean hasShaderStorageImageReadWithoutFormat = false;
        public static boolean hasShaderStorageImageWriteWithoutFormat = false;
        public static boolean hasShaderUniformBufferArrayDynamicIndexing = false;
        public static boolean hasShaderSampledImageArrayDynamicIndexing = false;
        public static boolean hasShaderStorageBufferArrayDynamicIndexing = false;
        public static boolean hasShaderStorageImageArrayDynamicIndexing = false;
        public static boolean hasShaderClipDistance = false;
        public static boolean hasShaderCullDistance = false;
        public static boolean hasShaderFloat64 = false;
        public static boolean hasShaderInt64 = false;
        public static boolean hasShaderInt16 = false;
        public static boolean hasShaderResourceResidency = false;
        public static boolean hasShaderResourceMinLod = false;
        public static boolean hasSparseBinding = false;
        public static boolean hasSparseResidencyBuffer = false;
        public static boolean hasSparseResidencyImage2D = false;
        public static boolean hasSparseResidencyImage3D = false;
        public static boolean hasSparseResidency2Samples = false;
        public static boolean hasSparseResidency4Samples = false;
        public static boolean hasSparseResidency8Samples = false;
        public static boolean hasSparseResidency16Samples = false;
        public static boolean hasSparseResidencyAliased = false;
        public static boolean hasVariableMultisampleRate = false;
        public static boolean hasInheritedQueries = false;
        
        // Vulkan 1.1 features
        public static boolean hasMultiview = false;
        public static boolean hasProtectedMemory = false;
        public static boolean hasSamplerYcbcrConversion = false;
        public static boolean hasShaderDrawParameters = false;
        public static boolean hasVariablePointers = false;
        
        // Vulkan 1.2 features
        public static boolean hasSamplerMirrorClampToEdge = false;
        public static boolean hasDrawIndirectCount = false;
        public static boolean hasStorageBuffer8BitAccess = false;
        public static boolean hasUniformAndStorageBuffer8BitAccess = false;
        public static boolean hasStoragePushConstant8 = false;
        public static boolean hasShaderBufferInt64Atomics = false;
        public static boolean hasShaderSharedInt64Atomics = false;
        public static boolean hasShaderFloat16 = false;
        public static boolean hasShaderInt8 = false;
        public static boolean hasDescriptorIndexing = false;
        public static boolean hasScalarBlockLayout = false;
        public static boolean hasImagelessFramebuffer = false;
        public static boolean hasUniformBufferStandardLayout = false;
        public static boolean hasShaderSubgroupExtendedTypes = false;
        public static boolean hasSeparateDepthStencilLayouts = false;
        public static boolean hasHostQueryReset = false;
        public static boolean hasTimelineSemaphore = false;
        public static boolean hasBufferDeviceAddress = false;
        public static boolean hasVulkanMemoryModel = false;
        
        // Vulkan 1.3 features
        public static boolean hasRobustImageAccess = false;
        public static boolean hasInlineUniformBlock = false;
        public static boolean hasPipelineCreationCacheControl = false;
        public static boolean hasPrivateData = false;
        public static boolean hasShaderDemoteToHelperInvocation = false;
        public static boolean hasShaderTerminateInvocation = false;
        public static boolean hasSubgroupSizeControl = false;
        public static boolean hasComputeFullSubgroups = false;
        public static boolean hasSynchronization2 = false;
        public static boolean hasTextureCompressionASTC_HDR = false;
        public static boolean hasShaderZeroInitializeWorkgroupMemory = false;
        public static boolean hasDynamicRendering = false;
        public static boolean hasShaderIntegerDotProduct = false;
        public static boolean hasMaintenance4 = false;
        
        // Extension features
        public static boolean hasRayTracingPipeline = false;     // VK_KHR_ray_tracing_pipeline
        public static boolean hasRayQuery = false;               // VK_KHR_ray_query
        public static boolean hasAccelerationStructure = false;  // VK_KHR_acceleration_structure
        public static boolean hasMeshShader = false;             // VK_EXT_mesh_shader
        public static boolean hasFragmentDensityMap = false;     // VK_EXT_fragment_density_map
        public static boolean hasFragmentShadingRate = false;    // VK_KHR_fragment_shading_rate
        public static boolean hasSwapchain = false;              // VK_KHR_swapchain
        public static boolean hasVideoQueue = false;             // VK_KHR_video_queue
        public static boolean hasVideoDecodeH264 = false;
        public static boolean hasVideoDecodeH265 = false;
        public static boolean hasVideoDecodeAV1 = false;
        public static boolean hasVideoEncodeH264 = false;
        public static boolean hasVideoEncodeH265 = false;
        
        // Memory properties
        public static long deviceLocalMemoryBytes = 0;
        public static long hostVisibleMemoryBytes = 0;
        public static long hostCoherentMemoryBytes = 0;
        
        // Limits (selected important ones)
        public static int maxImageDimension1D = 0;
        public static int maxImageDimension2D = 0;
        public static int maxImageDimension3D = 0;
        public static int maxImageDimensionCube = 0;
        public static int maxImageArrayLayers = 0;
        public static int maxTexelBufferElements = 0;
        public static int maxUniformBufferRange = 0;
        public static int maxStorageBufferRange = 0;
        public static int maxPushConstantsSize = 0;
        public static int maxMemoryAllocationCount = 0;
        public static int maxSamplerAllocationCount = 0;
        public static int maxBoundDescriptorSets = 0;
        public static int maxPerStageDescriptorSamplers = 0;
        public static int maxPerStageDescriptorUniformBuffers = 0;
        public static int maxPerStageDescriptorStorageBuffers = 0;
        public static int maxPerStageDescriptorSampledImages = 0;
        public static int maxPerStageDescriptorStorageImages = 0;
        public static int maxPerStageResources = 0;
        public static int maxVertexInputAttributes = 0;
        public static int maxVertexInputBindings = 0;
        public static int maxVertexInputAttributeOffset = 0;
        public static int maxVertexInputBindingStride = 0;
        public static int maxVertexOutputComponents = 0;
        public static int maxFragmentInputComponents = 0;
        public static int maxFragmentOutputAttachments = 0;
        public static int maxComputeSharedMemorySize = 0;
        public static int[] maxComputeWorkGroupCount = new int[3];
        public static int maxComputeWorkGroupInvocations = 0;
        public static int[] maxComputeWorkGroupSize = new int[3];
        public static int maxDrawIndirectCount = 0;
        public static float maxSamplerAnisotropyValue = 0;
        public static int maxViewports = 0;
        public static int[] maxViewportDimensions = new int[2];
        public static int maxFramebufferWidth = 0;
        public static int maxFramebufferHeight = 0;
        public static int maxFramebufferLayers = 0;
        public static int maxColorAttachments = 0;
        
        /**
         * Get version as combined integer (e.g., 13 for Vulkan 1.3)
         */
        public static int getVersionInt() {
            return majorVersion * 10 + minorVersion;
        }
        
        /**
         * Check if a specific Vulkan version is supported
         */
        public static boolean isVersionSupported(int major, int minor) {
            if (!isAvailable) return false;
            int target = major * 10 + minor;
            int current = getVersionInt();
            return current >= target;
        }
        
        /**
         * Check instance extension support
         */
        public static boolean hasInstanceExtension(String name) {
            return instanceExtensions.contains(name);
        }
        
        /**
         * Check device extension support
         */
        public static boolean hasDeviceExtension(String name) {
            return deviceExtensions.contains(name);
        }
        
        /**
         * Get device type as string
         */
        public static String getDeviceTypeName() {
            switch (deviceType) {
                case DEVICE_TYPE_INTEGRATED_GPU: return "Integrated GPU";
                case DEVICE_TYPE_DISCRETE_GPU: return "Discrete GPU";
                case DEVICE_TYPE_VIRTUAL_GPU: return "Virtual GPU";
                case DEVICE_TYPE_CPU: return "CPU";
                default: return "Other";
            }
        }
    }
    
    //===========================================================================================================
    // WRAPPER DETECTION (gl4es, ANGLE, Mesa, Zink, VirGL, etc.)
    //===========================================================================================================
    
    public static final class Wrapper {
        public enum Type {
            NATIVE,           // Native driver
            GL4ES,            // gl4es (GLES to GL wrapper)
            ANGLE,            // ANGLE (D3D/Vulkan to GL)
            MESA_SOFTWARE,    // Mesa LLVMpipe/Softpipe
            MESA_ZINK,        // Mesa Zink (Vulkan backend)
            VIRGL,            // VirGL (Virtualized)
            DXVK,             // DXVK-OpenGL (D3D to Vulkan)
            MGL,              // MoltenGL (Metal to GL)
            APITRACE,         // API tracing layer
            RENDERDOC,        // RenderDoc capture
            WINE,             // Wine/Proton
            UNKNOWN           // Unknown wrapper
        }
        
        public static Type type = Type.NATIVE;
        public static String name = "Native";
        public static String version = "";
        public static boolean isGLES = false;
        public static boolean isEmulated = false;
        public static boolean isVirtualized = false;
        public static boolean isSoftware = false;
        public static boolean isTranslated = false;
        
        // Wrapper quirks
        public static boolean quirk_brokenDSA = false;
        public static boolean quirk_brokenPersistentMapping = false;
        public static boolean quirk_brokenMultiDrawIndirect = false;
        public static boolean quirk_brokenComputeShaders = false;
        public static boolean quirk_brokenSSBO = false;
        public static boolean quirk_brokenTessellation = false;
        public static boolean quirk_brokenGeometryShaders = false;
        public static boolean quirk_brokenSPIRV = false;
        public static boolean quirk_brokenBindlessTextures = false;
        public static boolean quirk_slowInstancing = false;
        public static boolean quirk_slowBufferOperations = false;
        public static boolean quirk_requiresVAO = false;
        public static boolean quirk_requiresCoreProfile = false;
        public static boolean quirk_noPersistentMapping = false;
        
        // Performance characteristics
        public static float performanceMultiplier = 1.0f;
        public static int recommendedBatchSize = 256;
        public static long recommendedBufferSize = 64 * 1024 * 1024; // 64MB
    }
    
    //===========================================================================================================
    // FEATURE FLAGS (Resolved capabilities: what user wants AND hardware supports)
    //===========================================================================================================
    
    public static final class Features {
        // Buffer features
        public static boolean VBO = false;
        public static boolean VAO = false;
        public static boolean UBO = false;
        public static boolean SSBO = false;
        public static boolean TBO = false;
        public static boolean PBO = false;
        public static boolean persistentMapping = false;
        public static boolean coherentMapping = false;
        public static boolean immutableStorage = false;
        public static boolean sparseBuffers = false;
        
        // Draw features
        public static boolean instancing = false;
        public static boolean baseInstance = false;
        public static boolean multiDrawIndirect = false;
        public static boolean indirectCount = false;
        public static boolean primitiveRestart = false;
        public static boolean transformFeedback = false;
        
        // Shader features
        public static boolean shaders = false;
        public static boolean geometryShaders = false;
        public static boolean tessellationShaders = false;
        public static boolean computeShaders = false;
        public static boolean meshShaders = false;
        public static boolean taskShaders = false;
        public static boolean rayTracingShaders = false;
        
        // Shader language features
        public static boolean glsl = false;
        public static boolean spirv = false;
        public static boolean glslES = false;
        public static boolean subroutines = false;
        public static boolean shaderDouble = false;
        public static boolean shaderInt64 = false;
        public static boolean shaderFloat16 = false;
        public static boolean shaderInt8 = false;
        public static boolean shaderImageLoadStore = false;
        public static boolean shaderAtomics = false;
        
        // Texture features
        public static boolean textureArrays = false;
        public static boolean textureCubeArrays = false;
        public static boolean texture3D = false;
        public static boolean multisampleTextures = false;
        public static boolean sparseTextures = false;
        public static boolean bindlessTextures = false;
        public static boolean textureStorage = false;
        public static boolean anisotropicFiltering = false;
        public static boolean textureCompressionS3TC = false;
        public static boolean textureCompressionBPTC = false;
        public static boolean textureCompressionRGTC = false;
        public static boolean textureCompressionETC2 = false;
        public static boolean textureCompressionASTC = false;
        
        // Framebuffer features
        public static boolean framebufferObject = false;
        public static boolean multipleRenderTargets = false;
        public static boolean framebufferBlit = false;
        public static boolean framebufferMultisample = false;
        public static boolean framebufferSRGB = false;
        public static boolean imagelessFramebuffer = false;
        public static boolean dynamicRendering = false;
        
        // State features
        public static boolean DSA = false;
        public static boolean separateSamplers = false;
        public static boolean programPipelines = false;
        public static boolean explicitUniformLocation = false;
        public static boolean samplerObjects = false;
        
        // Synchronization
        public static boolean sync = false;
        public static boolean timerQuery = false;
        public static boolean conditionalRendering = false;
        
        // Debug features
        public static boolean debugOutput = false;
        public static boolean debugLabels = false;
        public static boolean debugGroups = false;
        
        // Misc features
        public static boolean clipControl = false;
        public static boolean depthClamp = false;
        public static boolean polygonOffsetClamp = false;
        public static boolean viewportArrays = false;
        public static boolean scissorArrays = false;
        public static boolean conservativeRasterization = false;
        public static boolean sampleShading = false;
    }
    
    //===========================================================================================================
    // HARDWARE LIMITS
    //===========================================================================================================
    
    public static final class Limits {
        // Texture limits
        public static int maxTextureSize = 0;
        public static int maxTextureSize3D = 0;
        public static int maxTextureSizeCube = 0;
        public static int maxTextureArrayLayers = 0;
        public static int maxTextureUnits = 0;
        public static int maxTextureImageUnits = 0;
        public static int maxCombinedTextureUnits = 0;
        public static float maxTextureAnisotropy = 0;
        public static int maxTextureLODBias = 0;
        
        // Framebuffer limits
        public static int maxRenderbufferSize = 0;
        public static int maxColorAttachments = 0;
        public static int maxDrawBuffers = 0;
        public static int maxFramebufferWidth = 0;
        public static int maxFramebufferHeight = 0;
        public static int maxFramebufferLayers = 0;
        public static int maxFramebufferSamples = 0;
        public static int maxSamples = 0;
        
        // Vertex limits
        public static int maxVertexAttribs = 0;
        public static int maxVertexUniformComponents = 0;
        public static int maxVertexUniformVectors = 0;
        public static int maxVertexOutputComponents = 0;
        public static int maxVertexTextureUnits = 0;
        public static int maxVertexUniformBlocks = 0;
        
        // Fragment limits
        public static int maxFragmentUniformComponents = 0;
        public static int maxFragmentUniformVectors = 0;
        public static int maxFragmentInputComponents = 0;
        public static int maxFragmentUniformBlocks = 0;
        
        // Geometry shader limits
        public static int maxGeometryInputComponents = 0;
        public static int maxGeometryOutputComponents = 0;
        public static int maxGeometryOutputVertices = 0;
        public static int maxGeometryTotalOutputComponents = 0;
        public static int maxGeometryUniformBlocks = 0;
        
        // Tessellation limits
        public static int maxTessControlInputComponents = 0;
        public static int maxTessControlOutputComponents = 0;
        public static int maxTessControlUniformBlocks = 0;
        public static int maxTessEvaluationInputComponents = 0;
        public static int maxTessEvaluationOutputComponents = 0;
        public static int maxTessEvaluationUniformBlocks = 0;
        public static int maxPatchVertices = 0;
        public static int maxTessGenLevel = 0;
        
        // Compute shader limits
        public static int maxComputeWorkGroupInvocations = 0;
        public static int[] maxComputeWorkGroupCount = {0, 0, 0};
        public static int[] maxComputeWorkGroupSize = {0, 0, 0};
        public static int maxComputeSharedMemorySize = 0;
        public static int maxComputeUniformBlocks = 0;
        public static int maxComputeTextureImageUnits = 0;
        public static int maxComputeAtomicCounters = 0;
        public static int maxComputeAtomicCounterBuffers = 0;
        public static int maxComputeImageUniforms = 0;
        
        // Buffer limits
        public static long maxUniformBlockSize = 0;
        public static long maxSSBOSize = 0;
        public static long maxBufferSize = 0;
        public static int maxUniformBufferBindings = 0;
        public static int maxSSBOBindings = 0;
        public static int maxAtomicCounterBufferBindings = 0;
        public static int maxTransformFeedbackBuffers = 0;
        public static int maxTransformFeedbackInterleavedComponents = 0;
        public static int maxTransformFeedbackSeparateComponents = 0;
        public static int maxTransformFeedbackSeparateAttribs = 0;
        
        // Image limits
        public static int maxImageUnits = 0;
        public static int maxCombinedImageUnitsAndFragmentOutputs = 0;
        
        // Shader limits
        public static int maxUniformLocations = 0;
        public static int maxVaryingComponents = 0;
        public static int maxVaryingVectors = 0;
        public static int maxVaryingFloats = 0;
        public static int maxClipDistances = 0;
        public static int maxCullDistances = 0;
        public static int maxCombinedClipAndCullDistances = 0;
        public static int maxSubroutines = 0;
        public static int maxSubroutineUniformLocations = 0;
        
        // Draw limits
        public static int maxElementsVertices = 0;
        public static int maxElementsIndices = 0;
        public static int maxDrawIndirectCount = 0;
        
        // Viewport limits
        public static int maxViewports = 0;
        public static int[] maxViewportDims = {0, 0};
        public static float[] viewportBoundsRange = {0, 0};
        
        // Point/Line limits
        public static float[] pointSizeRange = {0, 0};
        public static float pointSizeGranularity = 0;
        public static float[] lineWidthRange = {0, 0};
        public static float lineWidthGranularity = 0;
        
        // Misc limits
        public static int maxLabelLength = 0;
        public static int maxDebugMessageLength = 0;
        public static int maxDebugLoggedMessages = 0;
        public static int maxDebugGroupStackDepth = 0;
        
        // Shader program limits
        public static int maxProgramTexelOffset = 0;
        public static int minProgramTexelOffset = 0;
        
        // Extension limits
        public static int maxSparseTextureSizeARB = 0;
        public static int maxSparseArrayTextureLayers = 0;
    }
    
    //===========================================================================================================
    // HARDWARE/GPU INFO
    //===========================================================================================================
    
    public static final class GPU {
        public enum Vendor {
            NVIDIA,
            AMD,
            INTEL,
            ARM,
            QUALCOMM,
            APPLE,
            IMAGINATION,
            SAMSUNG,
            BROADCOM,
            VIVANTE,
            MICROSOFT,
            MESA,
            UNKNOWN
        }
        
        public static Vendor vendor = Vendor.UNKNOWN;
        public static String vendorString = "";
        public static String rendererString = "";
        public static String driverVersion = "";
        
        // GPU architecture detection
        public static String architecture = "";
        public static String gpuFamily = "";
        public static int computeUnits = 0;
        public static long videoMemoryBytes = 0;
        public static long sharedMemoryBytes = 0;
        
        // Driver info
        public static int driverMajor = 0;
        public static int driverMinor = 0;
        public static int driverPatch = 0;
        public static String driverDate = "";
        
        // Known driver issues
        public static final Set<String> knownIssues = new HashSet<>();
        
        /**
         * Parse vendor from GL_VENDOR string
         */
        public static Vendor parseVendor(String vendorStr) {
            String lower = vendorStr.toLowerCase();
            if (lower.contains("nvidia")) return Vendor.NVIDIA;
            if (lower.contains("amd") || lower.contains("ati")) return Vendor.AMD;
            if (lower.contains("intel")) return Vendor.INTEL;
            if (lower.contains("arm")) return Vendor.ARM;
            if (lower.contains("qualcomm") || lower.contains("adreno")) return Vendor.QUALCOMM;
            if (lower.contains("apple")) return Vendor.APPLE;
            if (lower.contains("imagination") || lower.contains("powervr")) return Vendor.IMAGINATION;
            if (lower.contains("samsung")) return Vendor.SAMSUNG;
            if (lower.contains("broadcom") || lower.contains("videocore")) return Vendor.BROADCOM;
            if (lower.contains("vivante")) return Vendor.VIVANTE;
            if (lower.contains("microsoft")) return Vendor.MICROSOFT;
            if (lower.contains("mesa") || lower.contains("llvmpipe") || lower.contains("softpipe")) return Vendor.MESA;
            return Vendor.UNKNOWN;
        }
    }
    
    //===========================================================================================================
    // MAIN API - DETECTION
    //===========================================================================================================
    
    /**
     * Detect all capabilities. Thread-safe, only runs once.
     */
    public static void detect() {
        synchronized (initLock) {
            if (initialized) return;
            
            try {
                detectOpenGL();
                detectVulkan();
                resolveFeatures();
                initialized = true;
            } catch (Exception e) {
                System.err.println("[UniversalCapabilities] Detection failed: " + e.getMessage());
                e.printStackTrace();
                applyMinimalFallback();
                initialized = true;
            }
        }
    }
    
    /**
     * Force re-detection (use with caution)
     */
    public static void redetect() {
        synchronized (initLock) {
            initialized = false;
            vulkanInitialized = false;
            clearAll();
            detect();
        }
    }
    
    /**
     * Detect only OpenGL (no Vulkan)
     */
    public static void detectOpenGLOnly() {
        synchronized (initLock) {
            if (initialized) return;
            
            try {
                detectOpenGL();
                resolveFeatures();
                initialized = true;
            } catch (Exception e) {
                System.err.println("[UniversalCapabilities] OpenGL detection failed: " + e.getMessage());
                e.printStackTrace();
                applyMinimalFallback();
                initialized = true;
            }
        }
    }
    
    /**
     * Detect only Vulkan
     */
    public static void detectVulkanOnly() {
        synchronized (initLock) {
            if (vulkanInitialized) return;
            detectVulkan();
        }
    }
    
    //===========================================================================================================
    // OPENGL DETECTION
    //===========================================================================================================
    
    private static void detectOpenGL() {
        try {
            // Get LWJGL 3 capabilities
            org.lwjgl.opengl.GLCapabilities caps = org.lwjgl.opengl.GL.getCapabilities();
            
            if (caps == null || !caps.OpenGL11) {
                System.err.println("[UniversalCapabilities] No valid GL context");
                return;
            }
            
            // Get GL strings
            GL.versionString = safeGetString(org.lwjgl.opengl.GL11.GL_VERSION);
            GL.renderer = safeGetString(org.lwjgl.opengl.GL11.GL_RENDERER);
            GL.vendor = safeGetString(org.lwjgl.opengl.GL11.GL_VENDOR);
            
            if (GL20.GL20) {
                GL.shadingLanguageVersion = safeGetString(org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION);
            }
            
            // Detect wrapper
            detectWrapper();
            
            // Detect GPU vendor
            GPU.vendorString = GL.vendor;
            GPU.rendererString = GL.renderer;
            GPU.vendor = GPU.parseVendor(GL.vendor);
            
            // Detect GLES context
            detectGLESContext();
            
            // Parse GL version
            parseGLVersion();
            
            // Detect GL versions
            detectGLVersions(caps);
            
            // Detect GLSL versions
            detectGLSLVersions(caps);
            
            // Detect SPIR-V support
            detectSPIRVSupport(caps);
            
            // Load extensions
            loadGLExtensions(caps);
            
            // Query limits
            queryGLLimits(caps);
            
        } catch (Exception e) {
            System.err.println("[UniversalCapabilities] OpenGL detection error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String safeGetString(int name) {
        try {
            String s = org.lwjgl.opengl.GL11.glGetString(name);
            return s != null ? s : "";
        } catch (Exception e) {
            return "";
        }
    }
    
    private static void detectWrapper() {
        String version = GL.versionString.toLowerCase();
        String renderer = GL.renderer.toLowerCase();
        String vendor = GL.vendor.toLowerCase();
        
        // Reset wrapper state
        Wrapper.type = Wrapper.Type.NATIVE;
        Wrapper.name = "Native";
        Wrapper.isGLES = false;
        Wrapper.isEmulated = false;
        Wrapper.isVirtualized = false;
        Wrapper.isSoftware = false;
        Wrapper.isTranslated = false;
        
        // gl4es (ARM/mobile GLES to GL wrapper)
        if (version.contains("gl4es") || renderer.contains("gl4es") || vendor.contains("gl4es")) {
            Wrapper.type = Wrapper.Type.GL4ES;
            Wrapper.name = "gl4es";
            Wrapper.isGLES = true;
            Wrapper.isTranslated = true;
            applyGL4ESQuirks();
            return;
        }
        
        // ANGLE (Windows D3D/Vulkan wrapper)
        if (renderer.contains("angle") || renderer.contains("direct3d") || renderer.contains("d3d")) {
            Wrapper.type = Wrapper.Type.ANGLE;
            Wrapper.name = "ANGLE";
            Wrapper.isTranslated = true;
            applyANGLEQuirks();
            return;
        }
        
        // Mesa LLVMpipe/Softpipe (software renderer)
        if (renderer.contains("llvmpipe") || renderer.contains("softpipe") || 
            renderer.contains("software rasterizer") || renderer.contains("swrast")) {
            Wrapper.type = Wrapper.Type.MESA_SOFTWARE;
            Wrapper.name = "Mesa Software";
            Wrapper.isSoftware = true;
            applyMesaSoftwareQuirks();
            return;
        }
        
        // Mesa Zink (Vulkan backend)
        if (renderer.contains("zink")) {
            Wrapper.type = Wrapper.Type.MESA_ZINK;
            Wrapper.name = "Mesa Zink";
            Wrapper.isTranslated = true;
            return;
        }
        
        // VirGL (virtualized)
        if (renderer.contains("virgl")) {
            Wrapper.type = Wrapper.Type.VIRGL;
            Wrapper.name = "VirGL";
            Wrapper.isVirtualized = true;
            return;
        }
        
        // MoltenGL (Metal wrapper)
        if (renderer.contains("moltengl") || renderer.contains("moltenvk")) {
            Wrapper.type = Wrapper.Type.MGL;
            Wrapper.name = "MoltenGL";
            Wrapper.isTranslated = true;
            return;
        }
        
        // Wine/Proton
        if (vendor.contains("wine") || renderer.contains("wine")) {
            Wrapper.type = Wrapper.Type.WINE;
            Wrapper.name = "Wine/Proton";
            Wrapper.isTranslated = true;
            return;
        }
        
        // APITrace
        if (renderer.contains("apitrace")) {
            Wrapper.type = Wrapper.Type.APITRACE;
            Wrapper.name = "APITrace";
            return;
        }
        
        // RenderDoc
        if (renderer.contains("renderdoc")) {
            Wrapper.type = Wrapper.Type.RENDERDOC;
            Wrapper.name = "RenderDoc";
            return;
        }
    }
    
    private static void applyGL4ESQuirks() {
        Wrapper.quirk_brokenDSA = true;
        Wrapper.quirk_brokenPersistentMapping = true;
        Wrapper.quirk_brokenMultiDrawIndirect = true;
        Wrapper.quirk_brokenComputeShaders = true;
        Wrapper.quirk_brokenSSBO = true;
        Wrapper.quirk_brokenSPIRV = true;
        Wrapper.performanceMultiplier = 0.5f;
    }
    
    private static void applyANGLEQuirks() {
        Wrapper.quirk_brokenSPIRV = true;
        Wrapper.quirk_slowBufferOperations = true;
        Wrapper.performanceMultiplier = 0.8f;
    }
    
    private static void applyMesaSoftwareQuirks() {
        Wrapper.quirk_brokenMultiDrawIndirect = true;
        Wrapper.quirk_brokenComputeShaders = true;
        Wrapper.quirk_brokenPersistentMapping = true;
        Wrapper.quirk_slowInstancing = true;
        Wrapper.quirk_slowBufferOperations = true;
        Wrapper.performanceMultiplier = 0.1f;
    }
    
    private static void detectGLESContext() {
        String version = GL.versionString.toLowerCase();
        
        if (version.contains("opengl es") || version.contains("opengl_es")) {
            GLES.isGLESContext = true;
            Wrapper.isGLES = true;
            
            Pattern p = Pattern.compile("opengl\\s*es\\s*(\\d+)\\.(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(GL.versionString);
            
            if (m.find()) {
                GLES.majorVersion = Integer.parseInt(m.group(1));
                GLES.minorVersion = Integer.parseInt(m.group(2));
            }
            
            detectGLESVersions();
        }
    }
    
    private static void detectGLESVersions() {
        int ver = GLES.majorVersion * 10 + GLES.minorVersion;
        
        GLES.ES10 = ver >= 10;
        GLES.ES11 = ver >= 11;
        GLES.ES20 = ver >= 20;
        GLES.ES30 = ver >= 30;
        GLES.ES31 = ver >= 31;
        GLES.ES32 = ver >= 32;
        
        // GLSL ES versions
        GLSL.GLSL_ES_100 = GLES.ES20;
        GLSL.GLSL_ES_300 = GLES.ES30;
        GLSL.GLSL_ES_310 = GLES.ES31;
        GLSL.GLSL_ES_320 = GLES.ES32;
        
        if (GLES.ES32) {
            GLSL.esMajorVersion = 3;
            GLSL.esMinorVersion = 20;
        } else if (GLES.ES31) {
            GLSL.esMajorVersion = 3;
            GLSL.esMinorVersion = 10;
        } else if (GLES.ES30) {
            GLSL.esMajorVersion = 3;
            GLSL.esMinorVersion = 0;
        } else {
            GLSL.esMajorVersion = 1;
            GLSL.esMinorVersion = 0;
        }
    }
    
    private static void parseGLVersion() {
        try {
            Pattern p = Pattern.compile("(\\d+)\\.(\\d+)");
            Matcher m = p.matcher(GL.versionString);
            
            if (m.find()) {
                GL.majorVersion = Integer.parseInt(m.group(1));
                GL.minorVersion = Integer.parseInt(m.group(2));
            }
        } catch (Exception e) {
            GL.majorVersion = 1;
            GL.minorVersion = 1;
        }
        
        // Detect profile
        String lower = GL.versionString.toLowerCase();
        GL.isCoreProfile = lower.contains("core");
        GL.isCompatibilityProfile = lower.contains("compatibility");
        GL.isForwardCompatible = lower.contains("forward");
    }
    
    private static void detectGLVersions(org.lwjgl.opengl.GLCapabilities caps) {
        if (Wrapper.type != Wrapper.Type.NATIVE && !config.trustDriverVersion) {
            detectGLVersionsConservative(caps);
        } else {
            detectGLVersionsNative(caps);
        }
    }
    
    private static void detectGLVersionsNative(org.lwjgl.opengl.GLCapabilities caps) {
        int ver = GL.majorVersion * 10 + GL.minorVersion;
        
        GL.GL10 = ver >= 10;
        GL.GL11 = ver >= 11;
        GL.GL12 = ver >= 12;
        GL.GL13 = ver >= 13;
        GL.GL14 = ver >= 14;
        GL.GL15 = ver >= 15;
        GL.GL20 = ver >= 20;
        GL.GL21 = ver >= 21;
        GL.GL30 = ver >= 30;
        GL.GL31 = ver >= 31;
        GL.GL32 = ver >= 32;
        GL.GL33 = ver >= 33;
        GL.GL40 = ver >= 40;
        GL.GL41 = ver >= 41;
        GL.GL42 = ver >= 42;
        GL.GL43 = ver >= 43;
        GL.GL44 = ver >= 44;
        GL.GL45 = ver >= 45;
        GL.GL46 = ver >= 46;
    }
    
    private static void detectGLVersionsConservative(org.lwjgl.opengl.GLCapabilities caps) {
        // For wrappers, verify extensions exist
        GL.GL10 = true;
        GL.GL11 = caps.OpenGL11;
        GL.GL12 = caps.OpenGL12;
        GL.GL13 = caps.OpenGL13;
        GL.GL14 = caps.OpenGL14;
        GL.GL15 = caps.OpenGL15 || caps.GL_ARB_vertex_buffer_object;
        GL.GL20 = caps.OpenGL20 || (caps.GL_ARB_shader_objects && caps.GL_ARB_vertex_shader && caps.GL_ARB_fragment_shader);
        GL.GL21 = caps.OpenGL21;
        GL.GL30 = caps.OpenGL30 || (caps.GL_ARB_vertex_array_object && caps.GL_ARB_framebuffer_object);
        GL.GL31 = caps.OpenGL31 || (caps.GL_ARB_copy_buffer && caps.GL_ARB_uniform_buffer_object);
        GL.GL32 = caps.OpenGL32 || (caps.GL_ARB_geometry_shader4 && caps.GL_ARB_sync);
        GL.GL33 = caps.OpenGL33 || (caps.GL_ARB_instanced_arrays && caps.GL_ARB_sampler_objects);
        GL.GL40 = caps.OpenGL40 || (caps.GL_ARB_tessellation_shader && caps.GL_ARB_transform_feedback2);
        GL.GL41 = caps.OpenGL41 || (caps.GL_ARB_separate_shader_objects && caps.GL_ARB_viewport_array);
        GL.GL42 = caps.OpenGL42 || (caps.GL_ARB_base_instance && caps.GL_ARB_texture_storage);
        GL.GL43 = caps.OpenGL43 || (caps.GL_ARB_multi_draw_indirect && caps.GL_ARB_compute_shader);
        GL.GL44 = caps.OpenGL44 || (caps.GL_ARB_buffer_storage && caps.GL_ARB_clear_texture);
        GL.GL45 = caps.OpenGL45 || (caps.GL_ARB_direct_state_access && caps.GL_ARB_clip_control);
        GL.GL46 = caps.OpenGL46 || (caps.GL_ARB_spirv_extensions && caps.GL_ARB_polygon_offset_clamp);
    }
    
    private static void detectGLSLVersions(org.lwjgl.opengl.GLCapabilities caps) {
        // Parse GLSL version from string
        try {
            Pattern p = Pattern.compile("(\\d+)\\.(\\d+)");
            Matcher m = p.matcher(GL.shadingLanguageVersion);
            
            if (m.find()) {
                GLSL.majorVersion = Integer.parseInt(m.group(1));
                GLSL.minorVersion = Integer.parseInt(m.group(2));
            }
        } catch (Exception e) {
            // Fallback based on GL version
            if (GL.GL46) { GLSL.majorVersion = 4; GLSL.minorVersion = 60; }
            else if (GL.GL45) { GLSL.majorVersion = 4; GLSL.minorVersion = 50; }
            else if (GL.GL44) { GLSL.majorVersion = 4; GLSL.minorVersion = 40; }
            else if (GL.GL43) { GLSL.majorVersion = 4; GLSL.minorVersion = 30; }
            else if (GL.GL42) { GLSL.majorVersion = 4; GLSL.minorVersion = 20; }
            else if (GL.GL41) { GLSL.majorVersion = 4; GLSL.minorVersion = 10; }
            else if (GL.GL40) { GLSL.majorVersion = 4; GLSL.minorVersion = 0; }
            else if (GL.GL33) { GLSL.majorVersion = 3; GLSL.minorVersion = 30; }
            else if (GL.GL32) { GLSL.majorVersion = 1; GLSL.minorVersion = 50; }
            else if (GL.GL31) { GLSL.majorVersion = 1; GLSL.minorVersion = 40; }
            else if (GL.GL30) { GLSL.majorVersion = 1; GLSL.minorVersion = 30; }
            else if (GL.GL21) { GLSL.majorVersion = 1; GLSL.minorVersion = 20; }
            else { GLSL.majorVersion = 1; GLSL.minorVersion = 10; }
        }
        
        int glslVer = GLSL.majorVersion * 100 + GLSL.minorVersion;
        
        GLSL.GLSL_110 = glslVer >= 110;
        GLSL.GLSL_120 = glslVer >= 120;
        GLSL.GLSL_130 = glslVer >= 130;
        GLSL.GLSL_140 = glslVer >= 140;
        GLSL.GLSL_150 = glslVer >= 150;
        GLSL.GLSL_330 = glslVer >= 330;
        GLSL.GLSL_400 = glslVer >= 400;
        GLSL.GLSL_410 = glslVer >= 410;
        GLSL.GLSL_420 = glslVer >= 420;
        GLSL.GLSL_430 = glslVer >= 430;
        GLSL.GLSL_440 = glslVer >= 440;
        GLSL.GLSL_450 = glslVer >= 450;
        GLSL.GLSL_460 = glslVer >= 460;
        
        // Feature detection
        GLSL.hasExplicitUniformLocation = GL.GL43 || caps.GL_ARB_explicit_uniform_location;
        GLSL.hasSSBO = GL.GL43 || caps.GL_ARB_shader_storage_buffer_object;
        GLSL.hasComputeShaders = GL.GL43 || caps.GL_ARB_compute_shader;
        GLSL.hasSubroutines = GL.GL40 || caps.GL_ARB_shader_subroutine;
        GLSL.hasTessellation = GL.GL40 || caps.GL_ARB_tessellation_shader;
        GLSL.hasGeometryShaders = GL.GL32 || caps.GL_ARB_geometry_shader4;
        GLSL.hasDouble = GL.GL40 || caps.GL_ARB_gpu_shader_fp64;
        GLSL.hasInt64 = caps.GL_ARB_gpu_shader_int64;
        GLSL.hasFloat16 = caps.GL_AMD_gpu_shader_half_float || caps.GL_NV_gpu_shader5;
        GLSL.hasDerivatives = GL.GL20;
        GLSL.hasTextureGather = GL.GL40 || caps.GL_ARB_texture_gather;
        GLSL.hasImageLoadStore = GL.GL42 || caps.GL_ARB_shader_image_load_store;
        GLSL.hasAtomics = GL.GL42 || caps.GL_ARB_shader_atomic_counters;
    }
    
    private static void detectSPIRVSupport(org.lwjgl.opengl.GLCapabilities caps) {
        // GL_ARB_gl_spirv extension
        SPIRV.hasGLSPIRV = caps.GL_ARB_gl_spirv;
        SPIRV.hasGLSPIRVExtensions = caps.GL_ARB_spirv_extensions;
        
        if (SPIRV.hasGLSPIRV) {
            // OpenGL SPIR-V support is typically 1.0-1.5 range
            SPIRV.majorVersion = 1;
            
            if (GL.GL46) {
                SPIRV.minorVersion = 5;
            } else if (GL.GL45) {
                SPIRV.minorVersion = 3;
            } else {
                SPIRV.minorVersion = 0;
            }
            
            SPIRV.SPIRV_10 = true;
            SPIRV.SPIRV_11 = SPIRV.minorVersion >= 1;
            SPIRV.SPIRV_12 = SPIRV.minorVersion >= 2;
            SPIRV.SPIRV_13 = SPIRV.minorVersion >= 3;
            SPIRV.SPIRV_14 = SPIRV.minorVersion >= 4;
            SPIRV.SPIRV_15 = SPIRV.minorVersion >= 5;
            SPIRV.SPIRV_16 = SPIRV.minorVersion >= 6;
            
            GLSL.hasSPIRVInput = true;
        }
        
        // Apply wrapper quirks
        if (Wrapper.quirk_brokenSPIRV) {
            SPIRV.hasGLSPIRV = false;
            GLSL.hasSPIRVInput = false;
        }
    }
    
    private static void loadGLExtensions(org.lwjgl.opengl.GLCapabilities caps) {
        GL.extensions.clear();
        
        try {
            if (GL.GL30) {
                // Modern extension query (GL 3.0+)
                int count = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_NUM_EXTENSIONS);
                GL.extensionCount = count;
                
                for (int i = 0; i < count; i++) {
                    String ext = org.lwjgl.opengl.GL30.glGetStringi(org.lwjgl.opengl.GL11.GL_EXTENSIONS, i);
                    if (ext != null) {
                        GL.extensions.add(ext);
                    }
                }
            } else {
                // Legacy extension query
                String extStr = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_EXTENSIONS);
                if (extStr != null) {
                    String[] exts = extStr.split(" ");
                    GL.extensionCount = exts.length;
                    for (String ext : exts) {
                        if (!ext.isEmpty()) {
                            GL.extensions.add(ext);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[UniversalCapabilities] Failed to load extensions: " + e.getMessage());
        }
    }
    
    private static void queryGLLimits(org.lwjgl.opengl.GLCapabilities caps) {
        try {
            // Texture limits
            Limits.maxTextureSize = safeGetInt(org.lwjgl.opengl.GL11.GL_MAX_TEXTURE_SIZE, 1024);
            
            if (GL.GL12) {
                Limits.maxTextureSize3D = safeGetInt(org.lwjgl.opengl.GL12.GL_MAX_3D_TEXTURE_SIZE, 256);
            }
            
            if (GL.GL13) {
                Limits.maxTextureSizeCube = safeGetInt(org.lwjgl.opengl.GL13.GL_MAX_CUBE_MAP_TEXTURE_SIZE, 1024);
                Limits.maxTextureUnits = safeGetInt(org.lwjgl.opengl.GL13.GL_MAX_TEXTURE_UNITS, 2);
            }
            
            if (GL.GL20) {
                Limits.maxCombinedTextureUnits = safeGetInt(org.lwjgl.opengl.GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, 8);
                Limits.maxVertexAttribs = safeGetInt(org.lwjgl.opengl.GL20.GL_MAX_VERTEX_ATTRIBS, 8);
                Limits.maxVertexUniformComponents = safeGetInt(org.lwjgl.opengl.GL20.GL_MAX_VERTEX_UNIFORM_COMPONENTS, 256);
                Limits.maxFragmentUniformComponents = safeGetInt(org.lwjgl.opengl.GL20.GL_MAX_FRAGMENT_UNIFORM_COMPONENTS, 256);
                Limits.maxVaryingFloats = safeGetInt(org.lwjgl.opengl.GL20.GL_MAX_VARYING_FLOATS, 32);
            }
            
            if (GL.GL30) {
                Limits.maxTextureArrayLayers = safeGetInt(org.lwjgl.opengl.GL30.GL_MAX_ARRAY_TEXTURE_LAYERS, 256);
                Limits.maxColorAttachments = safeGetInt(org.lwjgl.opengl.GL30.GL_MAX_COLOR_ATTACHMENTS, 4);
                Limits.maxRenderbufferSize = safeGetInt(org.lwjgl.opengl.GL30.GL_MAX_RENDERBUFFER_SIZE, 1024);
                Limits.maxSamples = safeGetInt(org.lwjgl.opengl.GL30.GL_MAX_SAMPLES, 4);
            }
            
            if (GL.GL31) {
                Limits.maxUniformBlockSize = safeGetInt(org.lwjgl.opengl.GL31.GL_MAX_UNIFORM_BLOCK_SIZE, 16384);
                Limits.maxUniformBufferBindings = safeGetInt(org.lwjgl.opengl.GL31.GL_MAX_UNIFORM_BUFFER_BINDINGS, 12);
            }
            
            if (GL.GL32) {
                Limits.maxGeometryOutputVertices = safeGetInt(org.lwjgl.opengl.GL32.GL_MAX_GEOMETRY_OUTPUT_VERTICES, 256);
            }
            
            if (GL.GL40) {
                Limits.maxPatchVertices = safeGetInt(org.lwjgl.opengl.GL40.GL_MAX_PATCH_VERTICES, 32);
                Limits.maxTessGenLevel = safeGetInt(org.lwjgl.opengl.GL40.GL_MAX_TESS_GEN_LEVEL, 64);
            }
            
            if (GL.GL41) {
                Limits.maxViewports = safeGetInt(org.lwjgl.opengl.GL41.GL_MAX_VIEWPORTS, 1);
            }
            
            if (GL.GL43) {
                Limits.maxComputeWorkGroupInvocations = safeGetInt(org.lwjgl.opengl.GL43.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, 128);
                Limits.maxComputeSharedMemorySize = safeGetInt(org.lwjgl.opengl.GL43.GL_MAX_COMPUTE_SHARED_MEMORY_SIZE, 16384);
                
                Limits.maxComputeWorkGroupCount[0] = safeGetIntIndexed(org.lwjgl.opengl.GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0, 65535);
                Limits.maxComputeWorkGroupCount[1] = safeGetIntIndexed(org.lwjgl.opengl.GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 1, 65535);
                Limits.maxComputeWorkGroupCount[2] = safeGetIntIndexed(org.lwjgl.opengl.GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 2, 65535);
                
                Limits.maxComputeWorkGroupSize[0] = safeGetIntIndexed(org.lwjgl.opengl.GL43.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0, 128);
                Limits.maxComputeWorkGroupSize[1] = safeGetIntIndexed(org.lwjgl.opengl.GL43.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 1, 128);
                Limits.maxComputeWorkGroupSize[2] = safeGetIntIndexed(org.lwjgl.opengl.GL43.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 2, 64);
                
                Limits.maxSSBOSize = safeGetLong(org.lwjgl.opengl.GL43.GL_MAX_SHADER_STORAGE_BLOCK_SIZE, 16777216);
                Limits.maxSSBOBindings = safeGetInt(org.lwjgl.opengl.GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS, 8);
            }
            
            // Anisotropic filtering
            if (caps.GL_EXT_texture_filter_anisotropic) {
                Limits.maxTextureAnisotropy = safeGetFloat(org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, 1.0f);
            }
            
        } catch (Exception e) {
            System.err.println("[UniversalCapabilities] Failed to query limits: " + e.getMessage());
        }
    }
    
    private static int safeGetInt(int pname, int defaultValue) {
        try {
            return org.lwjgl.opengl.GL11.glGetInteger(pname);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private static int safeGetIntIndexed(int pname, int index, int defaultValue) {
        try {
            return org.lwjgl.opengl.GL30.glGetIntegeri(pname, index);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private static long safeGetLong(int pname, long defaultValue) {
        try {
            return org.lwjgl.opengl.GL32.glGetInteger64(pname);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private static float safeGetFloat(int pname, float defaultValue) {
        try {
            return org.lwjgl.opengl.GL11.glGetFloat(pname);
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    //===========================================================================================================
    // VULKAN DETECTION
    //===========================================================================================================
    
    private static void detectVulkan() {
        try {
            // Check if Vulkan is available through LWJGL
            if (!checkVulkanAvailable()) {
                Vulkan.isAvailable = false;
                return;
            }
            
            Vulkan.isAvailable = true;
            detectVulkanCapabilities();
            vulkanInitialized = true;
            
        } catch (Throwable t) {
            // Vulkan not available or error
            Vulkan.isAvailable = false;
        }
    }
    
    private static boolean checkVulkanAvailable() {
        try {
            // Try to load Vulkan classes
            Class.forName("org.lwjgl.vulkan.VK10");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    private static void detectVulkanCapabilities() {
        // This is a placeholder - actual Vulkan detection requires creating an instance
        // which may not be appropriate in all contexts
        
        // For full Vulkan detection, you would:
        // 1. Create VkInstance
        // 2. Enumerate physical devices
        // 3. Query device properties and features
        // 4. Query extension support
        
        // Here we'll just detect version from instance
        try {
            // Check if we can create a Vulkan instance
            org.lwjgl.vulkan.VK.create();
            
            // Get API version
            int[] versionArray = new int[1];
            org.lwjgl.vulkan.VK10.vkEnumerateInstanceVersion(versionArray);
            int apiVersion = versionArray[0];
            
            Vulkan.apiVersion = apiVersion;
            Vulkan.majorVersion = org.lwjgl.vulkan.VK10.VK_VERSION_MAJOR(apiVersion);
            Vulkan.minorVersion = org.lwjgl.vulkan.VK10.VK_VERSION_MINOR(apiVersion);
            Vulkan.patchVersion = org.lwjgl.vulkan.VK10.VK_VERSION_PATCH(apiVersion);
            
            int ver = Vulkan.majorVersion * 10 + Vulkan.minorVersion;
            
            Vulkan.VK10 = ver >= 10;
            Vulkan.VK11 = ver >= 11;
            Vulkan.VK12 = ver >= 12;
            Vulkan.VK13 = ver >= 13;
            Vulkan.VK14 = ver >= 14;
            
            Vulkan.isLoaded = true;
            
            // SPIR-V version based on Vulkan version
            if (Vulkan.VK13) {
                SPIRV.SPIRV_16 = true;
                SPIRV.minorVersion = Math.max(SPIRV.minorVersion, 6);
            }
            if (Vulkan.VK12) {
                SPIRV.SPIRV_15 = true;
                SPIRV.minorVersion = Math.max(SPIRV.minorVersion, 5);
            }
            if (Vulkan.VK11) {
                SPIRV.SPIRV_13 = true;
                SPIRV.minorVersion = Math.max(SPIRV.minorVersion, 3);
            }
            if (Vulkan.VK10) {
                SPIRV.SPIRV_10 = true;
                SPIRV.majorVersion = 1;
            }
            
        } catch (Throwable t) {
            // Failed to initialize Vulkan
            Vulkan.isAvailable = false;
        }
    }
    
    //===========================================================================================================
    // FEATURE RESOLUTION - Combines user config with detected capabilities
    //===========================================================================================================
    
    private static void resolveFeatures() {
        org.lwjgl.opengl.GLCapabilities caps = null;
        try {
            caps = org.lwjgl.opengl.GL.getCapabilities();
        } catch (Exception e) {
            // No GL context
        }
        
        // Buffer features
        Features.VBO = resolveFeature(config.useVBO, GL.GL15, !Wrapper.quirk_slowBufferOperations);
        Features.VAO = resolveFeature(config.useVAO, GL.GL30, !Wrapper.quirk_requiresVAO || GL.GL30);
        Features.UBO = GL.GL31;
        Features.SSBO = resolveFeature(config.useSSBO, GL.GL43 && caps != null && caps.GL_ARB_shader_storage_buffer_object, !Wrapper.quirk_brokenSSBO);
        Features.PBO = GL.GL21;
        Features.TBO = GL.GL31;
        
        // Persistent mapping
        Features.persistentMapping = resolveFeature(config.usePersistentMapping, 
            GL.GL44 && caps != null && caps.GL_ARB_buffer_storage, 
            !Wrapper.quirk_brokenPersistentMapping && !Wrapper.quirk_noPersistentMapping);
        Features.immutableStorage = GL.GL44 && caps != null && caps.GL_ARB_buffer_storage;
        
        // Draw features
        Features.instancing = resolveFeature(config.useInstancing, GL.GL33, !Wrapper.quirk_slowInstancing);
        Features.baseInstance = resolveFeature(config.useBaseInstance, GL.GL42 && caps != null && caps.GL_ARB_base_instance, true);
        Features.multiDrawIndirect = resolveFeature(config.useMultiDrawIndirect, 
            GL.GL43 && caps != null && caps.GL_ARB_multi_draw_indirect, 
            !Wrapper.quirk_brokenMultiDrawIndirect);
        Features.indirectCount = GL.GL46 && caps != null && caps.GL_ARB_indirect_parameters;
        Features.primitiveRestart = GL.GL31;
        Features.transformFeedback = GL.GL30;
        
        // Shader features
        Features.shaders = GL.GL20;
        Features.geometryShaders = resolveFeature(config.useGeometryShaders, GL.GL32, !Wrapper.quirk_brokenGeometryShaders);
        Features.tessellationShaders = resolveFeature(config.useTessellation, GL.GL40, !Wrapper.quirk_brokenTessellation);
        Features.computeShaders = resolveFeature(config.useComputeShaders, 
            GL.GL43 && caps != null && caps.GL_ARB_compute_shader, 
            !Wrapper.quirk_brokenComputeShaders);
        Features.meshShaders = resolveFeature(config.useMeshShaders, caps != null && caps.GL_NV_mesh_shader, true);
        Features.taskShaders = caps != null && caps.GL_NV_mesh_shader;
        Features.rayTracingShaders = false; // GL doesn't have native ray tracing
        
        // Shader language
        Features.glsl = GL.GL20;
        Features.spirv = resolveFeature(config.useSPIRV, SPIRV.hasGLSPIRV, !Wrapper.quirk_brokenSPIRV);
        Features.glslES = GLES.isGLESContext || Wrapper.isGLES;
        
        // DSA
        Features.DSA = resolveFeature(config.useDSA, GL.GL45 && caps != null && caps.GL_ARB_direct_state_access, !Wrapper.quirk_brokenDSA);
        
        // Texture features
        Features.textureArrays = GL.GL30;
        Features.textureCubeArrays = GL.GL40;
        Features.texture3D = GL.GL12;
        Features.multisampleTextures = GL.GL32;
        Features.textureStorage = GL.GL42 && caps != null && caps.GL_ARB_texture_storage;
        Features.sparseTextures = resolveFeature(config.useSparseTextures, caps != null && caps.GL_ARB_sparse_texture, true);
        Features.bindlessTextures = resolveFeature(config.useBindlessTextures, 
            caps != null && caps.GL_ARB_bindless_texture, 
            !Wrapper.quirk_brokenBindlessTextures);
        Features.anisotropicFiltering = caps != null && caps.GL_EXT_texture_filter_anisotropic;
        Features.textureCompressionS3TC = caps != null && caps.GL_EXT_texture_compression_s3tc;
        Features.textureCompressionBPTC = caps != null && caps.GL_ARB_texture_compression_bptc;
        Features.textureCompressionRGTC = caps != null && caps.GL_ARB_texture_compression_rgtc;
        
        // Framebuffer features
        Features.framebufferObject = GL.GL30 || (caps != null && (caps.GL_ARB_framebuffer_object || caps.GL_EXT_framebuffer_object));
        Features.multipleRenderTargets = GL.GL20;
        Features.framebufferBlit = GL.GL30 || (caps != null && caps.GL_EXT_framebuffer_blit);
        Features.framebufferMultisample = GL.GL30 || (caps != null && caps.GL_EXT_framebuffer_multisample);
        Features.framebufferSRGB = GL.GL30 || (caps != null && caps.GL_ARB_framebuffer_sRGB);
        Features.imagelessFramebuffer = caps != null && caps.GL_ARB_framebuffer_no_attachments;
        Features.dynamicRendering = false; // Vulkan-only concept
        
        // State features
        Features.separateSamplers = GL.GL33 || (caps != null && caps.GL_ARB_sampler_objects);
        Features.programPipelines = GL.GL41 || (caps != null && caps.GL_ARB_separate_shader_objects);
        Features.explicitUniformLocation = GL.GL43 || (caps != null && caps.GL_ARB_explicit_uniform_location);
        Features.samplerObjects = GL.GL33 || (caps != null && caps.GL_ARB_sampler_objects);
        
        // Synchronization
        Features.sync = GL.GL32 || (caps != null && caps.GL_ARB_sync);
        Features.timerQuery = GL.GL33 || (caps != null && caps.GL_ARB_timer_query);
        Features.conditionalRendering = GL.GL30;
        
        // Debug features
        Features.debugOutput = GL.GL43 || (caps != null && caps.GL_ARB_debug_output);
        Features.debugLabels = GL.GL43 || (caps != null && caps.GL_KHR_debug);
        Features.debugGroups = GL.GL43 || (caps != null && caps.GL_KHR_debug);
        
        // Misc features
        Features.clipControl = GL.GL45 || (caps != null && caps.GL_ARB_clip_control);
        Features.depthClamp = GL.GL32 || (caps != null && caps.GL_ARB_depth_clamp);
        Features.polygonOffsetClamp = GL.GL46 || (caps != null && caps.GL_ARB_polygon_offset_clamp);
        Features.viewportArrays = GL.GL41 || (caps != null && caps.GL_ARB_viewport_array);
        Features.scissorArrays = GL.GL41 || (caps != null && caps.GL_ARB_viewport_array);
        Features.conservativeRasterization = caps != null && (caps.GL_NV_conservative_raster || caps.GL_INTEL_conservative_rasterization);
        Features.sampleShading = GL.GL40 || (caps != null && caps.GL_ARB_sample_shading);
        
        // GLSL feature flags
        GLSL.hasDouble = Features.shaders && (GL.GL40 || (caps != null && caps.GL_ARB_gpu_shader_fp64));
        GLSL.hasInt64 = caps != null && caps.GL_ARB_gpu_shader_int64;
        GLSL.hasFloat16 = caps != null && (caps.GL_AMD_gpu_shader_half_float || caps.GL_NV_gpu_shader5);
        GLSL.hasInt8 = caps != null && caps.GL_EXT_shader_explicit_arithmetic_types_int8;
        GLSL.shaderImageLoadStore = Features.computeShaders || (GL.GL42 && caps != null && caps.GL_ARB_shader_image_load_store);
        GLSL.hasAtomics = GL.GL42 || (caps != null && caps.GL_ARB_shader_atomic_counters);
        
        // SPIR-V capabilities based on GL features
        if (SPIRV.hasGLSPIRV) {
            SPIRV.hasShader = true;
            SPIRV.hasGeometry = Features.geometryShaders;
            SPIRV.hasTessellation = Features.tessellationShaders;
            SPIRV.hasFloat64 = GLSL.hasDouble;
            SPIRV.hasInt64 = GLSL.hasInt64;
            SPIRV.hasFloat16 = GLSL.hasFloat16;
            SPIRV.hasInt8 = GLSL.hasInt8;
        }
        
        // Apply render tier restrictions
        applyRenderTierRestrictions();
        
        // Apply config limits
        applyConfigLimits();
    }
    
    /**
     * Resolve a feature based on user preference and hardware capability
     */
    private static boolean resolveFeature(Config.FeatureLevel preference, boolean hardwareSupport, boolean noQuirkIssue) {
        switch (preference) {
            case REQUIRED:
                // User requires this - return true even if hardware doesn't support
                // (will cause errors at runtime if not available)
                return true;
                
            case PREFERRED:
                // User prefers this - only enable if hardware supports and no quirks
                return hardwareSupport && noQuirkIssue;
                
            case DISABLED:
                // User explicitly disabled
                return false;
                
            case AUTO:
            default:
                // System decides - enable if hardware supports and no quirks
                return hardwareSupport && noQuirkIssue;
        }
    }
    
    /**
     * Apply render tier restrictions to features
     */
    private static void applyRenderTierRestrictions() {
        switch (config.renderTier) {
            case POTATO:
                // Minimal feature set
                Features.VAO = false;
                Features.instancing = false;
                Features.baseInstance = false;
                Features.multiDrawIndirect = false;
                Features.persistentMapping = false;
                Features.DSA = false;
                Features.computeShaders = false;
                Features.SSBO = false;
                Features.geometryShaders = false;
                Features.tessellationShaders = false;
                Features.bindlessTextures = false;
                Features.sparseTextures = false;
                Features.meshShaders = false;
                Features.spirv = false;
                break;
                
            case LOW:
                Features.baseInstance = false;
                Features.multiDrawIndirect = false;
                Features.persistentMapping = false;
                Features.DSA = false;
                Features.computeShaders = false;
                Features.SSBO = false;
                Features.geometryShaders = false;
                Features.tessellationShaders = false;
                Features.bindlessTextures = false;
                Features.sparseTextures = false;
                Features.meshShaders = false;
                Features.spirv = false;
                break;
                
            case MEDIUM:
                Features.multiDrawIndirect = false;
                Features.persistentMapping = false;
                Features.DSA = false;
                Features.computeShaders = false;
                Features.SSBO = false;
                Features.bindlessTextures = false;
                Features.sparseTextures = false;
                Features.meshShaders = false;
                Features.spirv = false;
                break;
                
            case HIGH:
                Features.bindlessTextures = false;
                Features.sparseTextures = false;
                Features.meshShaders = false;
                break;
                
            case ULTRA:
            case CUSTOM:
            default:
                // No restrictions
                break;
        }
    }
    
    /**
     * Apply configuration limits (max versions, memory, etc.)
     */
    private static void applyConfigLimits() {
        // Clamp texture size to config max
        if (config.maxTextureSize > 0 && Limits.maxTextureSize > config.maxTextureSize) {
            Limits.maxTextureSize = config.maxTextureSize;
        }
        
        // Clamp anisotropy to config max
        if (config.maxAnisotropy > 0 && Limits.maxTextureAnisotropy > config.maxAnisotropy) {
            Limits.maxTextureAnisotropy = config.maxAnisotropy;
        }
        
        // Disable features beyond max GL version
        int maxGL = config.maxGLMajor * 10 + config.maxGLMinor;
        if (maxGL < 46) {
            GL.GL46 = false;
            if (maxGL < 45) {
                GL.GL45 = false;
                Features.DSA = false;
                Features.clipControl = false;
                if (maxGL < 44) {
                    GL.GL44 = false;
                    Features.persistentMapping = false;
                    if (maxGL < 43) {
                        GL.GL43 = false;
                        Features.computeShaders = false;
                        Features.SSBO = false;
                        Features.multiDrawIndirect = false;
                        if (maxGL < 42) {
                            GL.GL42 = false;
                            Features.baseInstance = false;
                            if (maxGL < 41) {
                                GL.GL41 = false;
                                Features.programPipelines = false;
                                if (maxGL < 40) {
                                    GL.GL40 = false;
                                    Features.tessellationShaders = false;
                                    if (maxGL < 33) {
                                        GL.GL33 = false;
                                        Features.instancing = false;
                                        if (maxGL < 32) {
                                            GL.GL32 = false;
                                            Features.geometryShaders = false;
                                            if (maxGL < 31) {
                                                GL.GL31 = false;
                                                Features.UBO = false;
                                                if (maxGL < 30) {
                                                    GL.GL30 = false;
                                                    Features.VAO = false;
                                                    Features.framebufferObject = false;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Clear all detected capabilities
     */
    private static void clearAll() {
        // Clear GL flags
        GL.GL10 = GL.GL11 = GL.GL12 = GL.GL13 = GL.GL14 = GL.GL15 = false;
        GL.GL20 = GL.GL21 = false;
        GL.GL30 = GL.GL31 = GL.GL32 = GL.GL33 = false;
        GL.GL40 = GL.GL41 = GL.GL42 = GL.GL43 = GL.GL44 = GL.GL45 = GL.GL46 = false;
        GL.majorVersion = 1;
        GL.minorVersion = 0;
        GL.versionString = "";
        GL.renderer = "";
        GL.vendor = "";
        GL.shadingLanguageVersion = "";
        GL.extensions.clear();
        GL.extensionCount = 0;
        GL.isCoreProfile = false;
        GL.isCompatibilityProfile = false;
        GL.isForwardCompatible = false;
        
        // Clear GLES flags
        GLES.ES10 = GLES.ES11 = GLES.ES20 = GLES.ES30 = GLES.ES31 = GLES.ES32 = false;
        GLES.isGLESContext = false;
        GLES.majorVersion = 0;
        GLES.minorVersion = 0;
        GLES.versionString = "";
        GLES.extensions.clear();
        GLES.hasAndroidExtensionPack = false;
        
        // Clear GLSL flags
        GLSL.GLSL_110 = GLSL.GLSL_120 = GLSL.GLSL_130 = GLSL.GLSL_140 = GLSL.GLSL_150 = false;
        GLSL.GLSL_330 = false;
        GLSL.GLSL_400 = GLSL.GLSL_410 = GLSL.GLSL_420 = GLSL.GLSL_430 = GLSL.GLSL_440 = GLSL.GLSL_450 = GLSL.GLSL_460 = false;
        GLSL.GLSL_ES_100 = GLSL.GLSL_ES_300 = GLSL.GLSL_ES_310 = GLSL.GLSL_ES_320 = false;
        GLSL.majorVersion = 1;
        GLSL.minorVersion = 10;
        GLSL.versionString = "";
        GLSL.esMajorVersion = 0;
        GLSL.esMinorVersion = 0;
        GLSL.hasExplicitUniformLocation = false;
        GLSL.hasSSBO = false;
        GLSL.hasComputeShaders = false;
        GLSL.hasSubroutines = false;
        GLSL.hasTessellation = false;
        GLSL.hasGeometryShaders = false;
        GLSL.hasDouble = false;
        GLSL.hasInt64 = false;
        GLSL.hasFloat16 = false;
        GLSL.hasDerivatives = false;
        GLSL.hasTextureGather = false;
        GLSL.hasImageLoadStore = false;
        GLSL.hasAtomics = false;
        GLSL.hasSPIRVInput = false;
        
        // Clear SPIR-V flags
        SPIRV.SPIRV_10 = SPIRV.SPIRV_11 = SPIRV.SPIRV_12 = SPIRV.SPIRV_13 = false;
        SPIRV.SPIRV_14 = SPIRV.SPIRV_15 = SPIRV.SPIRV_16 = false;
        SPIRV.majorVersion = 0;
        SPIRV.minorVersion = 0;
        SPIRV.hasGLSPIRV = false;
        SPIRV.hasGLSPIRVExtensions = false;
        SPIRV.capabilities.clear();
        SPIRV.extensions.clear();
        SPIRV.hasShader = false;
        SPIRV.hasGeometry = false;
        SPIRV.hasTessellation = false;
        SPIRV.hasFloat64 = false;
        SPIRV.hasInt64 = false;
        SPIRV.hasInt16 = false;
        SPIRV.hasInt8 = false;
        SPIRV.hasFloat16 = false;
        SPIRV.hasStorageImageMultisample = false;
        SPIRV.hasVariablePointers = false;
        SPIRV.hasRayTracingKHR = false;
        SPIRV.hasMeshShadingNV = false;
        SPIRV.hasMeshShadingEXT = false;
        
        // Clear Vulkan flags
        Vulkan.VK10 = Vulkan.VK11 = Vulkan.VK12 = Vulkan.VK13 = Vulkan.VK14 = false;
        Vulkan.isAvailable = false;
        Vulkan.isLoaded = false;
        Vulkan.apiVersion = 0;
        Vulkan.driverVersion = 0;
        Vulkan.majorVersion = 0;
        Vulkan.minorVersion = 0;
        Vulkan.patchVersion = 0;
        Vulkan.deviceName = "";
        Vulkan.driverName = "";
        Vulkan.driverInfo = "";
        Vulkan.vendorID = 0;
        Vulkan.deviceID = 0;
        Vulkan.deviceType = 0;
        Vulkan.instanceExtensions.clear();
        Vulkan.deviceExtensions.clear();
        Vulkan.deviceLocalMemoryBytes = 0;
        Vulkan.hostVisibleMemoryBytes = 0;
        Vulkan.hostCoherentMemoryBytes = 0;
        
        // Clear wrapper state
        Wrapper.type = Wrapper.Type.NATIVE;
        Wrapper.name = "Native";
        Wrapper.version = "";
        Wrapper.isGLES = false;
        Wrapper.isEmulated = false;
        Wrapper.isVirtualized = false;
        Wrapper.isSoftware = false;
        Wrapper.isTranslated = false;
        Wrapper.quirk_brokenDSA = false;
        Wrapper.quirk_brokenPersistentMapping = false;
        Wrapper.quirk_brokenMultiDrawIndirect = false;
        Wrapper.quirk_brokenComputeShaders = false;
        Wrapper.quirk_brokenSSBO = false;
        Wrapper.quirk_brokenTessellation = false;
        Wrapper.quirk_brokenGeometryShaders = false;
        Wrapper.quirk_brokenSPIRV = false;
        Wrapper.quirk_brokenBindlessTextures = false;
        Wrapper.quirk_slowInstancing = false;
        Wrapper.quirk_slowBufferOperations = false;
        Wrapper.quirk_requiresVAO = false;
        Wrapper.quirk_requiresCoreProfile = false;
        Wrapper.quirk_noPersistentMapping = false;
        Wrapper.performanceMultiplier = 1.0f;
        Wrapper.recommendedBatchSize = 256;
        Wrapper.recommendedBufferSize = 64 * 1024 * 1024;
        
        // Clear features
        Features.VBO = Features.VAO = Features.UBO = Features.SSBO = Features.TBO = Features.PBO = false;
        Features.persistentMapping = Features.coherentMapping = Features.immutableStorage = Features.sparseBuffers = false;
        Features.instancing = Features.baseInstance = Features.multiDrawIndirect = Features.indirectCount = false;
        Features.primitiveRestart = Features.transformFeedback = false;
        Features.shaders = Features.geometryShaders = Features.tessellationShaders = Features.computeShaders = false;
        Features.meshShaders = Features.taskShaders = Features.rayTracingShaders = false;
        Features.glsl = Features.spirv = Features.glslES = Features.subroutines = false;
        Features.shaderDouble = Features.shaderInt64 = Features.shaderFloat16 = Features.shaderInt8 = false;
        Features.shaderImageLoadStore = Features.shaderAtomics = false;
        Features.textureArrays = Features.textureCubeArrays = Features.texture3D = Features.multisampleTextures = false;
        Features.sparseTextures = Features.bindlessTextures = Features.textureStorage = Features.anisotropicFiltering = false;
        Features.textureCompressionS3TC = Features.textureCompressionBPTC = Features.textureCompressionRGTC = false;
        Features.textureCompressionETC2 = Features.textureCompressionASTC = false;
        Features.framebufferObject = Features.multipleRenderTargets = Features.framebufferBlit = false;
        Features.framebufferMultisample = Features.framebufferSRGB = Features.imagelessFramebuffer = Features.dynamicRendering = false;
        Features.DSA = Features.separateSamplers = Features.programPipelines = Features.explicitUniformLocation = Features.samplerObjects = false;
        Features.sync = Features.timerQuery = Features.conditionalRendering = false;
        Features.debugOutput = Features.debugLabels = Features.debugGroups = false;
        Features.clipControl = Features.depthClamp = Features.polygonOffsetClamp = false;
        Features.viewportArrays = Features.scissorArrays = Features.conservativeRasterization = Features.sampleShading = false;
        
        // Clear limits (set to minimums)
        Limits.maxTextureSize = 64;
        Limits.maxTextureSize3D = 64;
        Limits.maxTextureSizeCube = 64;
        Limits.maxTextureArrayLayers = 1;
        Limits.maxTextureUnits = 1;
        Limits.maxTextureImageUnits = 1;
        Limits.maxCombinedTextureUnits = 2;
        Limits.maxTextureAnisotropy = 1.0f;
        Limits.maxVertexAttribs = 8;
        Limits.maxColorAttachments = 1;
        Limits.maxDrawBuffers = 1;
        Limits.maxSamples = 1;
        
        // Clear GPU info
        GPU.vendor = GPU.Vendor.UNKNOWN;
        GPU.vendorString = "";
        GPU.rendererString = "";
        GPU.driverVersion = "";
        GPU.architecture = "";
        GPU.gpuFamily = "";
        GPU.computeUnits = 0;
        GPU.videoMemoryBytes = 0;
        GPU.sharedMemoryBytes = 0;
        GPU.driverMajor = 0;
        GPU.driverMinor = 0;
        GPU.driverPatch = 0;
        GPU.driverDate = "";
        GPU.knownIssues.clear();
    }
    
    /**
     * Apply minimal fallback when detection fails
     */
    private static void applyMinimalFallback() {
        // Set absolute minimum capabilities (OpenGL 1.5 level)
        GL.GL10 = true;
        GL.GL11 = true;
        GL.GL12 = true;
        GL.GL13 = true;
        GL.GL14 = true;
        GL.GL15 = true;
        GL.majorVersion = 1;
        GL.minorVersion = 5;
        
        GLSL.GLSL_110 = true;
        GLSL.majorVersion = 1;
        GLSL.minorVersion = 10;
        
        Features.VBO = true;
        Features.shaders = false;
        Features.framebufferObject = false;
        
        Limits.maxTextureSize = 1024;
        Limits.maxTextureUnits = 2;
        Limits.maxVertexAttribs = 8;
        
        Wrapper.type = Wrapper.Type.UNKNOWN;
        Wrapper.name = "Unknown (Fallback Mode)";
    }
    
    //===========================================================================================================
    // VALIDATION - Check if user requirements can be met
    //===========================================================================================================
    
    /**
     * Result of capability validation
     */
    public static final class ValidationResult {
        public boolean success = true;
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public final List<String> recommendations = new ArrayList<>();
        
        public void addError(String msg) {
            success = false;
            errors.add(msg);
        }
        
        public void addWarning(String msg) {
            warnings.add(msg);
        }
        
        public void addRecommendation(String msg) {
            recommendations.add(msg);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Validation ").append(success ? "PASSED" : "FAILED").append("\n");
            
            if (!errors.isEmpty()) {
                sb.append("\nErrors:\n");
                for (String e : errors) {
                    sb.append("  [ERROR] ").append(e).append("\n");
                }
            }
            
            if (!warnings.isEmpty()) {
                sb.append("\nWarnings:\n");
                for (String w : warnings) {
                    sb.append("  [WARN] ").append(w).append("\n");
                }
            }
            
            if (!recommendations.isEmpty()) {
                sb.append("\nRecommendations:\n");
                for (String r : recommendations) {
                    sb.append("  [INFO] ").append(r).append("\n");
                }
            }
            
            return sb.toString();
        }
    }
    
    /**
     * Validate that current capabilities meet configuration requirements
     */
    public static ValidationResult validate() {
        if (!initialized) detect();
        
        ValidationResult result = new ValidationResult();
        
        // Check minimum GL version
        int minGL = config.minGLMajor * 10 + config.minGLMinor;
        int currentGL = GL.majorVersion * 10 + GL.minorVersion;
        
        if (currentGL < minGL) {
            result.addError(String.format("OpenGL %d.%d required, but only %d.%d available",
                config.minGLMajor, config.minGLMinor, GL.majorVersion, GL.minorVersion));
        }
        
        // Check required features
        validateRequiredFeature(result, "VBO", config.useVBO, Features.VBO);
        validateRequiredFeature(result, "VAO", config.useVAO, Features.VAO);
        validateRequiredFeature(result, "Instancing", config.useInstancing, Features.instancing);
        validateRequiredFeature(result, "Base Instance", config.useBaseInstance, Features.baseInstance);
        validateRequiredFeature(result, "Multi-Draw Indirect", config.useMultiDrawIndirect, Features.multiDrawIndirect);
        validateRequiredFeature(result, "DSA", config.useDSA, Features.DSA);
        validateRequiredFeature(result, "Persistent Mapping", config.usePersistentMapping, Features.persistentMapping);
        validateRequiredFeature(result, "Compute Shaders", config.useComputeShaders, Features.computeShaders);
        validateRequiredFeature(result, "SSBO", config.useSSBO, Features.SSBO);
        validateRequiredFeature(result, "Tessellation", config.useTessellation, Features.tessellationShaders);
        validateRequiredFeature(result, "Geometry Shaders", config.useGeometryShaders, Features.geometryShaders);
        validateRequiredFeature(result, "SPIR-V", config.useSPIRV, Features.spirv);
        validateRequiredFeature(result, "Bindless Textures", config.useBindlessTextures, Features.bindlessTextures);
        validateRequiredFeature(result, "Sparse Textures", config.useSparseTextures, Features.sparseTextures);
        validateRequiredFeature(result, "Mesh Shaders", config.useMeshShaders, Features.meshShaders);
        
        // Check for wrapper issues
        if (Wrapper.type != Wrapper.Type.NATIVE) {
            result.addWarning("Running on " + Wrapper.name + " wrapper - some features may be limited");
            
            if (Wrapper.isSoftware) {
                result.addWarning("Software rendering detected - performance will be significantly reduced");
                result.addRecommendation("Consider using a hardware GPU for better performance");
            }
            
            if (Wrapper.isGLES) {
                result.addWarning("OpenGL ES context - desktop GL features may be unavailable");
            }
        }
        
        // Performance recommendations
        if (!Features.VAO && GL.GL30) {
            result.addRecommendation("VAO is available but disabled - enabling may improve performance");
        }
        
        if (!Features.DSA && GL.GL45) {
            result.addRecommendation("DSA is available but disabled - enabling may improve performance");
        }
        
        if (!Features.persistentMapping && GL.GL44 && config.preferBatching) {
            result.addRecommendation("Persistent buffer mapping available - consider enabling for batching");
        }
        
        if (Vulkan.isAvailable && config.preferredAPI == Config.PreferredAPI.AUTO) {
            result.addRecommendation("Vulkan " + Vulkan.majorVersion + "." + Vulkan.minorVersion + 
                " is available - consider using Vulkan for better performance");
        }
        
        return result;
    }
    
    private static void validateRequiredFeature(ValidationResult result, String name, 
                                                 Config.FeatureLevel preference, boolean available) {
        if (preference == Config.FeatureLevel.REQUIRED && !available) {
            result.addError(name + " is required but not available");
        } else if (preference == Config.FeatureLevel.PREFERRED && !available) {
            result.addWarning(name + " is preferred but not available");
        }
    }
    
    /**
     * Check if a specific feature combination is supported
     */
    public static boolean supportsFeatureSet(String... features) {
        if (!initialized) detect();
        
        for (String feature : features) {
            if (!isFeatureEnabled(feature)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if a specific feature is enabled
     */
    public static boolean isFeatureEnabled(String featureName) {
        if (!initialized) detect();
        
        switch (featureName.toLowerCase()) {
            case "vbo": return Features.VBO;
            case "vao": return Features.VAO;
            case "ubo": return Features.UBO;
            case "ssbo": return Features.SSBO;
            case "tbo": return Features.TBO;
            case "pbo": return Features.PBO;
            case "persistent_mapping":
            case "persistentmapping": return Features.persistentMapping;
            case "instancing": return Features.instancing;
            case "base_instance":
            case "baseinstance": return Features.baseInstance;
            case "multi_draw_indirect":
            case "multidrawindirect":
            case "mdi": return Features.multiDrawIndirect;
            case "dsa": return Features.DSA;
            case "compute":
            case "compute_shaders":
            case "computeshaders": return Features.computeShaders;
            case "geometry":
            case "geometry_shaders":
            case "geometryshaders": return Features.geometryShaders;
            case "tessellation":
            case "tessellation_shaders":
            case "tessellationshaders": return Features.tessellationShaders;
            case "mesh":
            case "mesh_shaders":
            case "meshshaders": return Features.meshShaders;
            case "spirv":
            case "spir-v": return Features.spirv;
            case "bindless":
            case "bindless_textures":
            case "bindlesstextures": return Features.bindlessTextures;
            case "sparse":
            case "sparse_textures":
            case "sparsetextures": return Features.sparseTextures;
            case "fbo":
            case "framebuffer": return Features.framebufferObject;
            case "mrt": return Features.multipleRenderTargets;
            case "anisotropic":
            case "anisotropic_filtering": return Features.anisotropicFiltering;
            case "debug":
            case "debug_output": return Features.debugOutput;
            default: return false;
        }
    }
    
    //===========================================================================================================
    // REPORT GENERATION
    //===========================================================================================================
    
    /**
     * Get a comprehensive capability report
     */
    public static String getFullReport() {
        if (!initialized) detect();
        
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                    UNIVERSAL GRAPHICS CAPABILITIES REPORT                    ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════════════════════╝\n\n");
        
        // GPU Info
        sb.append("┌─────────────────────────────────────────────────────────────────────────────┐\n");
        sb.append("│ GPU INFORMATION                                                              │\n");
        sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");
        sb.append("│ Vendor:   ").append(padRight(GL.vendor, 65)).append("│\n");
        sb.append("│ Renderer: ").append(padRight(GL.renderer, 65)).append("│\n");
        sb.append("│ Version:  ").append(padRight(GL.versionString, 65)).append("│\n");
        sb.append("│ GLSL:     ").append(padRight(GL.shadingLanguageVersion, 65)).append("│\n");
        if (Wrapper.type != Wrapper.Type.NATIVE) {
            sb.append("│ Wrapper:  ").append(padRight(Wrapper.name + " (Performance: " + 
                String.format("%.0f%%", Wrapper.performanceMultiplier * 100) + ")", 65)).append("│\n");
        }
        sb.append("└─────────────────────────────────────────────────────────────────────────────┘\n\n");
        
        // OpenGL Versions
        sb.append("┌─────────────────────────────────────────────────────────────────────────────┐\n");
        sb.append("│ OPENGL VERSION SUPPORT                                                      │\n");
        sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");
        sb.append("│ ").append(formatVersionGrid()).append(" │\n");
        sb.append("└─────────────────────────────────────────────────────────────────────────────┘\n\n");
        
        // OpenGL ES
        if (GLES.isGLESContext || Wrapper.isGLES) {
            sb.append("┌─────────────────────────────────────────────────────────────────────────────┐\n");
            sb.append("│ OPENGL ES VERSION SUPPORT                                                   │\n");
            sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");
            sb.append("│ ES 1.0: ").append(yn(GLES.ES10)).append("  ES 1.1: ").append(yn(GLES.ES11));
            sb.append("  ES 2.0: ").append(yn(GLES.ES20)).append("  ES 3.0: ").append(yn(GLES.ES30));
            sb.append("  ES 3.1: ").append(yn(GLES.ES31)).append("  ES 3.2: ").append(yn(GLES.ES32));
            sb.append(padRight("", 12)).append("│\n");
            sb.append("└─────────────────────────────────────────────────────────────────────────────┘\n\n");
        }
        
        // GLSL
        sb.append("┌─────────────────────────────────────────────────────────────────────────────┐\n");
        sb.append("│ GLSL VERSION SUPPORT                                                        │\n");
        sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");
        sb.append("│ 1.10: ").append(yn(GLSL.GLSL_110)).append("  1.20: ").append(yn(GLSL.GLSL_120));
        sb.append("  1.30: ").append(yn(GLSL.GLSL_130)).append("  1.40: ").append(yn(GLSL.GLSL_140));
        sb.append("  1.50: ").append(yn(GLSL.GLSL_150)).append(padRight("", 24)).append("│\n");
        sb.append("│ 3.30: ").append(yn(GLSL.GLSL_330)).append("  4.00: ").append(yn(GLSL.GLSL_400));
        sb.append("  4.10: ").append(yn(GLSL.GLSL_410)).append("  4.20: ").append(yn(GLSL.GLSL_420));
        sb.append("  4.30: ").append(yn(GLSL.GLSL_430)).append(padRight("", 24)).append("│\n");
        sb.append("│ 4.40: ").append(yn(GLSL.GLSL_440)).append("  4.50: ").append(yn(GLSL.GLSL_450));
        sb.append("  4.60: ").append(yn(GLSL.GLSL_460)).append(padRight("", 42)).append("│\n");
        sb.append("└─────────────────────────────────────────────────────────────────────────────┘\n\n");
        
        // SPIR-V
        sb.append("┌─────────────────────────────────────────────────────────────────────────────┐\n");
        sb.append("│ SPIR-V SUPPORT                                                              │\n");
        sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");
        sb.append("│ GL SPIR-V: ").append(yn(SPIRV.hasGLSPIRV));
        sb.append("  Version: ").append(SPIRV.majorVersion).append(".").append(SPIRV.minorVersion);
        sb.append(padRight("", 48)).append("│\n");
        sb.append("│ 1.0: ").append(yn(SPIRV.SPIRV_10)).append("  1.1: ").append(yn(SPIRV.SPIRV_11));
        sb.append("  1.2: ").append(yn(SPIRV.SPIRV_12)).append("  1.3: ").append(yn(SPIRV.SPIRV_13));
        sb.append("  1.4: ").append(yn(SPIRV.SPIRV_14)).append("  1.5: ").append(yn(SPIRV.SPIRV_15));
        sb.append("  1.6: ").append(yn(SPIRV.SPIRV_16)).append(padRight("", 6)).append("│\n");
        sb.append("└─────────────────────────────────────────────────────────────────────────────┘\n\n");
        
        // Vulkan
        sb.append("┌─────────────────────────────────────────────────────────────────────────────┐\n");
        sb.append("│ VULKAN SUPPORT                                                              │\n");
        sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");
        if (Vulkan.isAvailable) {
            sb.append("│ Available: Yes  Version: ").append(Vulkan.majorVersion).append(".").append(Vulkan.minorVersion);
            sb.append(".").append(Vulkan.patchVersion).append(padRight("", 44)).append("│\n");
            sb.append("│ 1.0: ").append(yn(Vulkan.VK10)).append("  1.1: ").append(yn(Vulkan.VK11));
            sb.append("  1.2: ").append(yn(Vulkan.VK12)).append("  1.3: ").append(yn(Vulkan.VK13));
            sb.append("  1.4: ").append(yn(Vulkan.VK14)).append(padRight("", 30)).append("│\n");
            if (!Vulkan.deviceName.isEmpty()) {
                sb.append("│ Device: ").append(padRight(Vulkan.deviceName, 67)).append("│\n");
                sb.append("│ Type:   ").append(padRight(Vulkan.getDeviceTypeName(), 67)).append("│\n");
            }
        } else {
            sb.append("│ Available: No").append(padRight("", 62)).append("│\n");
        }
        sb.append("└─────────────────────────────────────────────────────────────────────────────┘\n\n");
        
        // Active Features
        sb.append("┌─────────────────────────────────────────────────────────────────────────────┐\n");
        sb.append("│ ACTIVE FEATURES (Resolved)                                                  │\n");
        sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");
        sb.append("│ Buffer:   VBO:").append(yn(Features.VBO));
        sb.append(" VAO:").append(yn(Features.VAO));
        sb.append(" UBO:").append(yn(Features.UBO));
        sb.append(" SSBO:").append(yn(Features.SSBO));
        sb.append(" PersistMap:").append(yn(Features.persistentMapping));
        sb.append(padRight("", 11)).append("│\n");
        sb.append("│ Draw:     Instancing:").append(yn(Features.instancing));
        sb.append(" BaseInst:").append(yn(Features.baseInstance));
        sb.append(" MDI:").append(yn(Features.multiDrawIndirect));
        sb.append(padRight("", 25)).append("│\n");
        sb.append("│ Shaders:  GLSL:").append(yn(Features.glsl));
        sb.append(" Geom:").append(yn(Features.geometryShaders));
        sb.append(" Tess:").append(yn(Features.tessellationShaders));
        sb.append(" Compute:").append(yn(Features.computeShaders));
        sb.append(" Mesh:").append(yn(Features.meshShaders));
        sb.append(padRight("", 5)).append("│\n");
        sb.append("│ State:    DSA:").append(yn(Features.DSA));
        sb.append(" SepSamplers:").append(yn(Features.separateSamplers));
        sb.append(" Pipelines:").append(yn(Features.programPipelines));
        sb.append(padRight("", 21)).append("│\n");
        sb.append("│ Texture:  Arrays:").append(yn(Features.textureArrays));
        sb.append(" 3D:").append(yn(Features.texture3D));
        sb.append(" Bindless:").append(yn(Features.bindlessTextures));
        sb.append(" Sparse:").append(yn(Features.sparseTextures));
        sb.append(padRight("", 12)).append("│\n");
        sb.append("│ Debug:    Output:").append(yn(Features.debugOutput));
        sb.append(" Labels:").append(yn(Features.debugLabels));
        sb.append(" Groups:").append(yn(Features.debugGroups));
        sb.append(padRight("", 27)).append("│\n");
        sb.append("└─────────────────────────────────────────────────────────────────────────────┘\n\n");
        
        // Key Limits
        sb.append("┌─────────────────────────────────────────────────────────────────────────────┐\n");
        sb.append("│ KEY HARDWARE LIMITS                                                         │\n");
        sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");
        sb.append("│ Max Texture Size:     ").append(padRight(Limits.maxTextureSize + " x " + Limits.maxTextureSize, 53)).append("│\n");
        sb.append("│ Max 3D Texture Size:  ").append(padRight(Limits.maxTextureSize3D + "", 53)).append("│\n");
        sb.append("│ Max Array Layers:     ").append(padRight(Limits.maxTextureArrayLayers + "", 53)).append("│\n");
        sb.append("│ Max Texture Units:    ").append(padRight(Limits.maxCombinedTextureUnits + "", 53)).append("│\n");
        sb.append("│ Max Anisotropy:       ").append(padRight(String.format("%.1fx", Limits.maxTextureAnisotropy), 53)).append("│\n");
        sb.append("│ Max Vertex Attribs:   ").append(padRight(Limits.maxVertexAttribs + "", 53)).append("│\n");
        sb.append("│ Max Color Attachments:").append(padRight(Limits.maxColorAttachments + "", 53)).append("│\n");
        sb.append("│ Max Samples:          ").append(padRight(Limits.maxSamples + "", 53)).append("│\n");
        if (Features.computeShaders) {
            sb.append("│ Max Compute Invoc:    ").append(padRight(Limits.maxComputeWorkGroupInvocations + "", 53)).append("│\n");
            sb.append("│ Max Compute SharedMem:").append(padRight((Limits.maxComputeSharedMemorySize / 1024) + " KB", 53)).append("│\n");
        }
        sb.append("└─────────────────────────────────────────────────────────────────────────────┘\n\n");
        
        // Configuration
        sb.append("┌─────────────────────────────────────────────────────────────────────────────┐\n");
        sb.append("│ CURRENT CONFIGURATION                                                       │\n");
        sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");
        sb.append("│ Preferred API:  ").append(padRight(config.preferredAPI.name(), 59)).append("│\n");
        sb.append("│ Render Tier:    ").append(padRight(config.renderTier.name(), 59)).append("│\n");
        sb.append("│ Min GL Version: ").append(padRight(config.minGLMajor + "." + config.minGLMinor, 59)).append("│\n");
        sb.append("│ Max GL Version: ").append(padRight(config.maxGLMajor + "." + config.maxGLMinor, 59)).append("│\n");
        sb.append("│ Allow Fallback: ").append(padRight(config.allowFallback ? "Yes" : "No", 59)).append("│\n");
        sb.append("│ Debug Output:   ").append(padRight(config.enableDebugOutput ? "Enabled" : "Disabled", 59)).append("│\n");
        sb.append("└─────────────────────────────────────────────────────────────────────────────┘\n\n");
        
        // Wrapper Quirks (if any)
        if (Wrapper.type != Wrapper.Type.NATIVE) {
            sb.append("┌─────────────────────────────────────────────────────────────────────────────┐\n");
            sb.append("│ WRAPPER QUIRKS                                                              │\n");
            sb.append("├─────────────────────────────────────────────────────────────────────────────┤\n");
            List<String> quirks = getActiveQuirks();
            if (quirks.isEmpty()) {
                sb.append("│ No known quirks for this wrapper").append(padRight("", 42)).append("│\n");
            } else {
                for (String quirk : quirks) {
                    sb.append("│ • ").append(padRight(quirk, 72)).append("│\n");
                }
            }
            sb.append("└─────────────────────────────────────────────────────────────────────────────┘\n\n");
        }
        
        // Extension count
        sb.append("Extensions loaded: ").append(GL.extensionCount).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Get a compact capability summary
     */
    public static String getCompactReport() {
        if (!initialized) detect();
        
        StringBuilder sb = new StringBuilder();
        sb.append("[Graphics Capabilities]\n");
        sb.append("GL: ").append(GL.majorVersion).append(".").append(GL.minorVersion);
        sb.append(" | GLSL: ").append(GLSL.majorVersion).append(".").append(GLSL.minorVersion);
        if (SPIRV.hasGLSPIRV) {
            sb.append(" | SPIR-V: ").append(SPIRV.majorVersion).append(".").append(SPIRV.minorVersion);
        }
        if (Vulkan.isAvailable) {
            sb.append(" | VK: ").append(Vulkan.majorVersion).append(".").append(Vulkan.minorVersion);
        }
        sb.append("\n");
        
        sb.append("Renderer: ").append(GL.renderer).append("\n");
        
        if (Wrapper.type != Wrapper.Type.NATIVE) {
            sb.append("Wrapper: ").append(Wrapper.name).append("\n");
        }
        
        sb.append("Features: ");
        if (Features.VBO) sb.append("VBO ");
        if (Features.VAO) sb.append("VAO ");
        if (Features.instancing) sb.append("Instancing ");
        if (Features.DSA) sb.append("DSA ");
        if (Features.persistentMapping) sb.append("PersistMap ");
        if (Features.computeShaders) sb.append("Compute ");
        if (Features.meshShaders) sb.append("Mesh ");
        if (Features.spirv) sb.append("SPIR-V ");
        sb.append("\n");
        
        sb.append("Max Texture: ").append(Limits.maxTextureSize);
        sb.append(" | Attribs: ").append(Limits.maxVertexAttribs);
        sb.append(" | Tex Units: ").append(Limits.maxCombinedTextureUnits);
        sb.append("\n");
        
        return sb.toString();
    }
    
    /**
     * Get JSON-formatted capability data
     */
    public static String getJSONReport() {
        if (!initialized) detect();
        
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        
        // GL
        sb.append("  \"opengl\": {\n");
        sb.append("    \"version\": \"").append(GL.versionString.replace("\"", "\\\"")).append("\",\n");
        sb.append("    \"major\": ").append(GL.majorVersion).append(",\n");
        sb.append("    \"minor\": ").append(GL.minorVersion).append(",\n");
        sb.append("    \"renderer\": \"").append(GL.renderer.replace("\"", "\\\"")).append("\",\n");
        sb.append("    \"vendor\": \"").append(GL.vendor.replace("\"", "\\\"")).append("\",\n");
        sb.append("    \"glslVersion\": \"").append(GL.shadingLanguageVersion.replace("\"", "\\\"")).append("\",\n");
        sb.append("    \"extensionCount\": ").append(GL.extensionCount).append(",\n");
        sb.append("    \"isCoreProfile\": ").append(GL.isCoreProfile).append(",\n");
        sb.append("    \"isCompatibilityProfile\": ").append(GL.isCompatibilityProfile).append("\n");
        sb.append("  },\n");
        
        // GLES
        sb.append("  \"opengles\": {\n");
        sb.append("    \"isContext\": ").append(GLES.isGLESContext).append(",\n");
        sb.append("    \"major\": ").append(GLES.majorVersion).append(",\n");
        sb.append("    \"minor\": ").append(GLES.minorVersion).append("\n");
        sb.append("  },\n");
        
        // GLSL
        sb.append("  \"glsl\": {\n");
        sb.append("    \"major\": ").append(GLSL.majorVersion).append(",\n");
        sb.append("    \"minor\": ").append(GLSL.minorVersion).append(",\n");
        sb.append("    \"hasCompute\": ").append(GLSL.hasComputeShaders).append(",\n");
        sb.append("    \"hasTessellation\": ").append(GLSL.hasTessellation).append(",\n");
        sb.append("    \"hasGeometry\": ").append(GLSL.hasGeometryShaders).append(",\n");
        sb.append("    \"hasSPIRV\": ").append(GLSL.hasSPIRVInput).append("\n");
        sb.append("  },\n");
        
        // SPIR-V
        sb.append("  \"spirv\": {\n");
        sb.append("    \"supported\": ").append(SPIRV.hasGLSPIRV).append(",\n");
        sb.append("    \"major\": ").append(SPIRV.majorVersion).append(",\n");
        sb.append("    \"minor\": ").append(SPIRV.minorVersion).append("\n");
        sb.append("  },\n");
        
        // Vulkan
        sb.append("  \"vulkan\": {\n");
        sb.append("    \"available\": ").append(Vulkan.isAvailable).append(",\n");
        sb.append("    \"major\": ").append(Vulkan.majorVersion).append(",\n");
        sb.append("    \"minor\": ").append(Vulkan.minorVersion).append(",\n");
        sb.append("    \"patch\": ").append(Vulkan.patchVersion).append(",\n");
        sb.append("    \"deviceName\": \"").append(Vulkan.deviceName.replace("\"", "\\\"")).append("\",\n");
        sb.append("    \"deviceType\": \"").append(Vulkan.getDeviceTypeName()).append("\"\n");
        sb.append("  },\n");
        
        // Wrapper
        sb.append("  \"wrapper\": {\n");
        sb.append("    \"type\": \"").append(Wrapper.type.name()).append("\",\n");
        sb.append("    \"name\": \"").append(Wrapper.name).append("\",\n");
        sb.append("    \"isGLES\": ").append(Wrapper.isGLES).append(",\n");
        sb.append("    \"isSoftware\": ").append(Wrapper.isSoftware).append(",\n");
        sb.append("    \"performanceMultiplier\": ").append(Wrapper.performanceMultiplier).append("\n");
        sb.append("  },\n");
        
        // Features
        sb.append("  \"features\": {\n");
        sb.append("    \"vbo\": ").append(Features.VBO).append(",\n");
        sb.append("    \"vao\": ").append(Features.VAO).append(",\n");
        sb.append("    \"ubo\": ").append(Features.UBO).append(",\n");
        sb.append("    \"ssbo\": ").append(Features.SSBO).append(",\n");
        sb.append("    \"persistentMapping\": ").append(Features.persistentMapping).append(",\n");
        sb.append("    \"instancing\": ").append(Features.instancing).append(",\n");
        sb.append("    \"baseInstance\": ").append(Features.baseInstance).append(",\n");
        sb.append("    \"multiDrawIndirect\": ").append(Features.multiDrawIndirect).append(",\n");
        sb.append("    \"dsa\": ").append(Features.DSA).append(",\n");
        sb.append("    \"computeShaders\": ").append(Features.computeShaders).append(",\n");
        sb.append("    \"geometryShaders\": ").append(Features.geometryShaders).append(",\n");
        sb.append("    \"tessellationShaders\": ").append(Features.tessellationShaders).append(",\n");
        sb.append("    \"meshShaders\": ").append(Features.meshShaders).append(",\n");
        sb.append("    \"spirv\": ").append(Features.spirv).append(",\n");
        sb.append("    \"bindlessTextures\": ").append(Features.bindlessTextures).append(",\n");
        sb.append("    \"sparseTextures\": ").append(Features.sparseTextures).append(",\n");
        sb.append("    \"framebufferObject\": ").append(Features.framebufferObject).append(",\n");
        sb.append("    \"debugOutput\": ").append(Features.debugOutput).append("\n");
        sb.append("  },\n");
        
        // Limits
        sb.append("  \"limits\": {\n");
        sb.append("    \"maxTextureSize\": ").append(Limits.maxTextureSize).append(",\n");
        sb.append("    \"maxTextureSize3D\": ").append(Limits.maxTextureSize3D).append(",\n");
        sb.append("    \"maxTextureArrayLayers\": ").append(Limits.maxTextureArrayLayers).append(",\n");
        sb.append("    \"maxCombinedTextureUnits\": ").append(Limits.maxCombinedTextureUnits).append(",\n");
        sb.append("    \"maxTextureAnisotropy\": ").append(Limits.maxTextureAnisotropy).append(",\n");
        sb.append("    \"maxVertexAttribs\": ").append(Limits.maxVertexAttribs).append(",\n");
        sb.append("    \"maxColorAttachments\": ").append(Limits.maxColorAttachments).append(",\n");
        sb.append("    \"maxSamples\": ").append(Limits.maxSamples).append(",\n");
        sb.append("    \"maxComputeWorkGroupInvocations\": ").append(Limits.maxComputeWorkGroupInvocations).append(",\n");
        sb.append("    \"maxComputeSharedMemorySize\": ").append(Limits.maxComputeSharedMemorySize).append("\n");
        sb.append("  }\n");
        
        sb.append("}\n");
        
        return sb.toString();
    }
    
    //===========================================================================================================
    // UTILITY METHODS
    //===========================================================================================================
    
    /**
     * Get the best available graphics API
     */
    public static Config.PreferredAPI getBestAvailableAPI() {
        if (!initialized) detect();
        
        switch (config.preferredAPI) {
            case VULKAN:
                if (Vulkan.isAvailable) return Config.PreferredAPI.VULKAN;
                if (config.allowFallback) return Config.PreferredAPI.OPENGL;
                return Config.PreferredAPI.VULKAN; // Will fail
                
            case OPENGL_ES:
                if (GLES.isGLESContext || Wrapper.isGLES) return Config.PreferredAPI.OPENGL_ES;
                if (config.allowFallback) return Config.PreferredAPI.OPENGL;
                return Config.PreferredAPI.OPENGL_ES;
                
            case OPENGL_CORE:
                if (GL.isCoreProfile || GL.GL32) return Config.PreferredAPI.OPENGL_CORE;
                if (config.allowFallback) return Config.PreferredAPI.OPENGL;
                return Config.PreferredAPI.OPENGL_CORE;
                
            case OPENGL_COMPAT:
                return Config.PreferredAPI.OPENGL_COMPAT;
                
            case OPENGL:
                return Config.PreferredAPI.OPENGL;
                
            case AUTO:
            default:
                // Prefer Vulkan if available and modern enough
                if (Vulkan.isAvailable && Vulkan.VK11) {
                    return Config.PreferredAPI.VULKAN;
                }
                // Otherwise use OpenGL
                if (GL.isCoreProfile) {
                    return Config.PreferredAPI.OPENGL_CORE;
                }
                return Config.PreferredAPI.OPENGL;
        }
    }
    
    /**
     * Get recommended render tier based on hardware
     */
    public static Config.RenderTier getRecommendedTier() {
        if (!initialized) detect();
        
        // Software rendering = POTATO
        if (Wrapper.isSoftware) {
            return Config.RenderTier.POTATO;
        }
        
        // gl4es or other limited wrappers = LOW/MEDIUM
        if (Wrapper.type == Wrapper.Type.GL4ES) {
            return Config.RenderTier.LOW;
        }
        
        // Check GL version
        if (GL.GL45 && Features.DSA && Features.persistentMapping && !Wrapper.isTranslated) {
            return Config.RenderTier.ULTRA;
        }
        
        if (GL.GL43 && Features.computeShaders && Features.multiDrawIndirect) {
            return Config.RenderTier.HIGH;
        }
        
        if (GL.GL33 && Features.instancing) {
            return Config.RenderTier.MEDIUM;
        }
        
        if (GL.GL21) {
            return Config.RenderTier.LOW;
        }
        
        return Config.RenderTier.POTATO;
    }
    
    /**
     * Get the maximum supported OpenGL version
     */
    public static int getMaxSupportedGLVersion() {
        if (!initialized) detect();
        
        if (GL.GL46) return 46;
        if (GL.GL45) return 45;
        if (GL.GL44) return 44;
        if (GL.GL43) return 43;
        if (GL.GL42) return 42;
        if (GL.GL41) return 41;
        if (GL.GL40) return 40;
        if (GL.GL33) return 33;
        if (GL.GL32) return 32;
        if (GL.GL31) return 31;
        if (GL.GL30) return 30;
        if (GL.GL21) return 21;
        if (GL.GL20) return 20;
        if (GL.GL15) return 15;
        if (GL.GL14) return 14;
        if (GL.GL13) return 13;
        if (GL.GL12) return 12;
        if (GL.GL11) return 11;
        return 10;
    }
    
    /**
     * Get the maximum supported GLES version
     */
    public static int getMaxSupportedGLESVersion() {
        if (!initialized) detect();
        
        if (GLES.ES32) return 32;
        if (GLES.ES31) return 31;
        if (GLES.ES30) return 30;
        if (GLES.ES20) return 20;
        if (GLES.ES11) return 11;
        if (GLES.ES10) return 10;
        return 0;
    }
    
    /**
     * Get the maximum supported GLSL version
     */
    public static int getMaxSupportedGLSLVersion() {
        if (!initialized) detect();
        return GLSL.getVersionInt();
    }
    
    /**
     * Get the maximum supported SPIR-V version
     */
    public static int getMaxSupportedSPIRVVersion() {
        if (!initialized) detect();
        return SPIRV.getVersionInt();
    }
    
    /**
     * Get the maximum supported Vulkan version
     */
    public static int getMaxSupportedVulkanVersion() {
        if (!initialized) detect();
        return Vulkan.getVersionInt();
    }
    
    /**
     * Check if running on a specific GPU vendor
     */
    public static boolean isVendor(GPU.Vendor vendor) {
        if (!initialized) detect();
        return GPU.vendor == vendor;
    }
    
    /**
     * Check if running on any wrapper/translation layer
     */
    public static boolean isWrapped() {
        if (!initialized) detect();
        return Wrapper.type != Wrapper.Type.NATIVE;
    }
    
    /**
     * Get list of active wrapper quirks
     */
    public static List<String> getActiveQuirks() {
        List<String> quirks = new ArrayList<>();
        
        if (Wrapper.quirk_brokenDSA) quirks.add("Broken DSA implementation");
        if (Wrapper.quirk_brokenPersistentMapping) quirks.add("Broken persistent buffer mapping");
        if (Wrapper.quirk_brokenMultiDrawIndirect) quirks.add("Broken multi-draw indirect");
        if (Wrapper.quirk_brokenComputeShaders) quirks.add("Broken compute shaders");
        if (Wrapper.quirk_brokenSSBO) quirks.add("Broken SSBO support");
        if (Wrapper.quirk_brokenTessellation) quirks.add("Broken tessellation shaders");
        if (Wrapper.quirk_brokenGeometryShaders) quirks.add("Broken geometry shaders");
        if (Wrapper.quirk_brokenSPIRV) quirks.add("Broken SPIR-V support");
        if (Wrapper.quirk_brokenBindlessTextures) quirks.add("Broken bindless textures");
        if (Wrapper.quirk_slowInstancing) quirks.add("Slow instancing performance");
        if (Wrapper.quirk_slowBufferOperations) quirks.add("Slow buffer operations");
        if (Wrapper.quirk_requiresVAO) quirks.add("Requires VAO for drawing");
        if (Wrapper.quirk_requiresCoreProfile) quirks.add("Requires core profile");
        if (Wrapper.quirk_noPersistentMapping) quirks.add("No persistent mapping support");
        
        return quirks;
    }
    
    /**
     * Check if extension is supported (GL or Vulkan)
     */
    public static boolean hasExtension(String extensionName) {
        if (!initialized) detect();
        
        // Check GL extensions
        if (GL.extensions.contains(extensionName)) return true;
        
        // Check GLES extensions
        if (GLES.extensions.contains(extensionName)) return true;
        
        // Check Vulkan extensions
        if (Vulkan.instanceExtensions.contains(extensionName)) return true;
        if (Vulkan.deviceExtensions.contains(extensionName)) return true;
        
        return false;
    }
    
    /**
     * Get all loaded GL extensions
     */
    public static Set<String> getGLExtensions() {
        if (!initialized) detect();
        return Collections.unmodifiableSet(GL.extensions);
    }
    
    /**
     * Get all loaded GLES extensions
     */
    public static Set<String> getGLESExtensions() {
        if (!initialized) detect();
        return Collections.unmodifiableSet(GLES.extensions);
    }
    
    /**
     * Get all Vulkan device extensions
     */
    public static Set<String> getVulkanDeviceExtensions() {
        if (!initialized) detect();
        return Collections.unmodifiableSet(Vulkan.deviceExtensions);
    }
    
    /**
     * Apply configuration from external source
     */
    public static void applyConfiguration(Map<String, Object> configMap) {
        config.loadFromMap(configMap);
        
        // Re-resolve features with new config
        if (initialized) {
            resolveFeatures();
        }
    }
    
    /**
     * Export current configuration
     */
    public static Map<String, Object> exportConfiguration() {
        return config.toMap();
    }
    
    /**
     * Check if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Check if Vulkan is initialized
     */
    public static boolean isVulkanInitialized() {
        return vulkanInitialized;
    }
    
    //===========================================================================================================
    // HELPER METHODS FOR REPORT FORMATTING
    //===========================================================================================================
    
    private static String yn(boolean value) {
        return value ? "✓" : "✗";
    }
    
    private static String padRight(String s, int length) {
        if (s == null) s = "";
        if (s.length() >= length) return s.substring(0, length);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < length) {
            sb.append(" ");
        }
        return sb.toString();
    }
    
    private static String formatVersionGrid() {
        StringBuilder sb = new StringBuilder();
        
        // Row 1: GL 1.x
        sb.append("1.0:").append(yn(GL.GL10)).append(" 1.1:").append(yn(GL.GL11));
        sb.append(" 1.2:").append(yn(GL.GL12)).append(" 1.3:").append(yn(GL.GL13));
        sb.append(" 1.4:").append(yn(GL.GL14)).append(" 1.5:").append(yn(GL.GL15));
        sb.append(padRight("", 24));
        sb.append(" │\n│ ");
        
        // Row 2: GL 2.x - 3.x
        sb.append("2.0:").append(yn(GL.GL20)).append(" 2.1:").append(yn(GL.GL21));
        sb.append(" 3.0:").append(yn(GL.GL30)).append(" 3.1:").append(yn(GL.GL31));
        sb.append(" 3.2:").append(yn(GL.GL32)).append(" 3.3:").append(yn(GL.GL33));
        sb.append(padRight("", 24));
        sb.append(" │\n│ ");
        
        // Row 3: GL 4.x
        sb.append("4.0:").append(yn(GL.GL40)).append(" 4.1:").append(yn(GL.GL41));
        sb.append(" 4.2:").append(yn(GL.GL42)).append(" 4.3:").append(yn(GL.GL43));
        sb.append(" 4.4:").append(yn(GL.GL44)).append(" 4.5:").append(yn(GL.GL45));
        sb.append(" 4.6:").append(yn(GL.GL46));
        sb.append(padRight("", 18));
        
        return sb.toString();
    }
    
    //===========================================================================================================
    // STATIC CONVENIENCE METHODS
    //===========================================================================================================
    
    /**
     * Quick check if basic modern rendering is available (GL 3.3+)
     */
    public static boolean supportsModernRendering() {
        if (!initialized) detect();
        return GL.GL33 && Features.VAO && Features.shaders;
    }
    
    /**
     * Quick check if advanced rendering is available (GL 4.3+)
     */
    public static boolean supportsAdvancedRendering() {
        if (!initialized) detect();
        return GL.GL43 && Features.computeShaders && Features.SSBO;
    }
    
    /**
     * Quick check if cutting-edge rendering is available (GL 4.5+ DSA)
     */
    public static boolean supportsCuttingEdgeRendering() {
        if (!initialized) detect();
        return GL.GL45 && Features.DSA && Features.persistentMapping;
    }
    
    /**
     * Get a simple capability score (0-100)
     */
    public static int getCapabilityScore() {
        if (!initialized) detect();
        
        int score = 0;
        
        // Base GL version (up to 46 points)
        score += getMaxSupportedGLVersion();
        
        // Key features (up to 30 points)
        if (Features.VAO) score += 3;
        if (Features.instancing) score += 3;
        if (Features.DSA) score += 4;
        if (Features.persistentMapping) score += 4;
        if (Features.computeShaders) score += 5;
        if (Features.multiDrawIndirect) score += 4;
        if (Features.meshShaders) score += 4;
        if (Features.spirv) score += 3;
        
        // Vulkan availability (up to 14 points)
        if (Vulkan.isAvailable) {
            score += Vulkan.getVersionInt();
        }
        
        // Wrapper penalty
        if (Wrapper.isSoftware) score = (int)(score * 0.3);
        else if (Wrapper.isTranslated) score = (int)(score * Wrapper.performanceMultiplier);
        
        return Math.min(100, score);
    }
    
    /**
     * Print capability report to stdout
     */
    public static void printReport() {
        System.out.println(getFullReport());
    }
    
    /**
     * Print compact report to stdout
     */
    public static void printCompactReport() {
        System.out.println(getCompactReport());
    }
}
