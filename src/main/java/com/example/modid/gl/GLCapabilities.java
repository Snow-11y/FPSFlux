package com.example.modid.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

public class GLCapabilities {
    private static GLVersion detectedVersion = null;
    
    public enum GLVersion {
        GL12(1, 2),
        GL15(1, 5),
        GL20(2, 0),
        GL24(2, 4),
        GL30(3, 0),
        GL33(3, 3),
        GL40(4, 0),
        GL42(4, 2),
        GL43(4, 3),
        GL45(4, 5),
        GL46(4, 6);
        
        public final int major;
        public final int minor;
        
        GLVersion(int major, int minor) {
            this.major = major;
            this.minor = minor;
        }
        
        public boolean isAtLeast(GLVersion other) {
            if (this.major != other.major) {
                return this.major > other.major;
            }
            return this.minor >= other.minor;
        }
    }
    
    public static GLVersion detect() {
        if (detectedVersion != null) return detectedVersion;
        
        // Get GL version string (e.g., "4.6.0 NVIDIA 531.18")
        String versionStr = GL11.glGetString(GL11.GL_VERSION);
        if (versionStr == null) {
            detectedVersion = GLVersion.GL12; // Fallback
            return detectedVersion;
        }
        
        // Parse major.minor
        String[] parts = versionStr.split("\\s+")[0].split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        
        // Find highest supported version we implement
        GLVersion[] versions = GLVersion.values();
        for (int i = versions.length - 1; i >= 0; i--) {
            GLVersion v = versions[i];
            if (major > v.major || (major == v.major && minor >= v.minor)) {
                detectedVersion = v;
                return detectedVersion;
            }
        }
        
        detectedVersion = GLVersion.GL12;
        return detectedVersion;
    }
    
    public static boolean hasVBO() {
        return detect().isAtLeast(GLVersion.GL15);
    }
    
    public static boolean hasVAO() {
        return detect().isAtLeast(GLVersion.GL30);
    }
    
    public static boolean hasDSA() {
        return detect().isAtLeast(GLVersion.GL45);
    }
    
    public static boolean hasBaseInstance() {
        return detect().isAtLeast(GLVersion.GL42);
    }
}
