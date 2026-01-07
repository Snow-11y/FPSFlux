package com.example.modid.gl;

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
    
    public static void detect() {
        if (initialized) return;
        
        // Get context capabilities
        ContextCapabilities caps = GLContext.getCapabilities();
        
        // Version detection
        String version = GL11.glGetString(GL11.GL_VERSION);
        String[] parts = version.split("\\s+")[0].split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        
        GL15 = major > 1 || (major == 1 && minor >= 5);
        GL20 = major >= 2;
        GL30 = major >= 3;
        GL31 = major > 3 || (major == 3 && minor >= 1);
        GL33 = major > 3 || (major == 3 && minor >= 3);
        GL40 = major >= 4;
        GL42 = major > 4 || (major == 4 && minor >= 2);
        GL43 = major > 4 || (major == 4 && minor >= 3);
        GL44 = major > 4 || (major == 4 && minor >= 4);
        GL45 = major > 4 || (major == 4 && minor >= 5);
        GL46 = major > 4 || (major == 4 && minor >= 6);
        
        // Feature detection
        hasVBO = GL15 && caps.GL_ARB_vertex_buffer_object;
        hasVAO = GL30 && caps.GL_ARB_vertex_array_object;
        hasInstancing = GL33 && caps.GL_ARB_instanced_arrays;
        hasBaseInstance = GL42 && caps.GL_ARB_base_instance;
        hasMultiDrawIndirect = GL43 && caps.GL_ARB_multi_draw_indirect;
        hasDSA = GL45 && caps.GL_ARB_direct_state_access;
        hasPersistentMapping = GL44 && caps.GL_ARB_buffer_storage;
        hasComputeShaders = GL43 && caps.GL_ARB_compute_shader;
        hasSSBO = GL43 && caps.GL_ARB_shader_storage_buffer_object;
        
        // Query limits
        maxTextureUnits = GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
        if (GL20) {
            maxVertexAttribs = GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS);
        }
        
        initialized = true;
    }
    
    public static String getReport() {
        if (!initialized) detect();
        
        return String.format(
            "OpenGL %s | VBO:%s VAO:%s Instancing:%s DSA:%s PersistentMap:%s MultiDrawIndirect:%s",
            GL11.glGetString(GL11.GL_VERSION),
            hasVBO, hasVAO, hasInstancing, hasDSA, hasPersistentMapping, hasMultiDrawIndirect
        );
    }
}
