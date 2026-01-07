package com.example.modid.gl;

import com.example.modid.gl.buffer.ops.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class CompatibilityLayer {
    
    public enum CompatibilityLevel {
        OPTIMAL,        // Native GPU, best path available
        DEGRADED,       // Wrapper/limited GPU, some features disabled
        MINIMAL,        // Very old GPU, basic features only
        UNSUPPORTED     // Cannot run at all
    }
    
    private static CompatibilityLevel level = CompatibilityLevel.UNSUPPORTED;
    private static String compatibilityMessage = "";
    
    public static CompatibilityLevel evaluate() {
        GLCapabilities.detect();
        
        // Check absolute minimum requirements
        if (!GLCapabilities.hasVBO || !GLCapabilities.GL15) {
            level = CompatibilityLevel.UNSUPPORTED;
            compatibilityMessage = "GPU does not support OpenGL 1.5 or VBO. FPSFlux cannot run.";
            return level;
        }
        
        // Determine compatibility level
        if (GLCapabilities.isGLWrapper) {
            // Running on wrapper - evaluate carefully
            evaluateWrapperCompatibility();
        } else {
            // Native GPU - evaluate based on features
            evaluateNativeCompatibility();
        }
        
        return level;
    }
    
    private static void evaluateWrapperCompatibility() {
        int maxVersion = GLCapabilities.getMaxSupportedVersion();
        
        if (maxVersion >= 33 && GLCapabilities.hasVAO && GLCapabilities.hasInstancing) {
            // Wrapper supports modern features - degraded but usable
            level = CompatibilityLevel.DEGRADED;
            compatibilityMessage = String.format(
                "Running on %s wrapper. Some advanced features disabled for compatibility.",
                GLCapabilities.wrapperName
            );
        } else if (maxVersion >= 20 && GLCapabilities.hasVBO) {
            // Basic wrapper support
            level = CompatibilityLevel.MINIMAL;
            compatibilityMessage = String.format(
                "Running on %s wrapper with limited GL support. Performance may be reduced.",
                GLCapabilities.wrapperName
            );
        } else {
            // Wrapper too limited
            level = CompatibilityLevel.UNSUPPORTED;
            compatibilityMessage = String.format(
                "%s wrapper does not support required OpenGL features.",
                GLCapabilities.wrapperName
            );
        }
    }
    
    private static void evaluateNativeCompatibility() {
        int maxVersion = GLCapabilities.getMaxSupportedVersion();
        
        if (maxVersion >= 44 && GLCapabilities.hasPersistentMapping && GLCapabilities.hasDSA) {
            // Modern GPU with all features
            level = CompatibilityLevel.OPTIMAL;
            compatibilityMessage = "Native GPU with full OpenGL 4.4+ support. All optimizations enabled.";
        } else if (maxVersion >= 33 && GLCapabilities.hasVAO && GLCapabilities.hasInstancing) {
            // Mid-range GPU
            level = CompatibilityLevel.DEGRADED;
            compatibilityMessage = "GPU supports OpenGL 3.3+. Most optimizations enabled.";
        } else if (maxVersion >= 15 && GLCapabilities.hasVBO) {
            // Old GPU
            level = CompatibilityLevel.MINIMAL;
            compatibilityMessage = "Older GPU detected. Basic optimizations only.";
        } else {
            level = CompatibilityLevel.UNSUPPORTED;
            compatibilityMessage = "GPU too old to support FPSFlux.";
        }
    }
    
    public static CompatibilityLevel getLevel() {
        return level;
    }
    
    public static String getMessage() {
        return compatibilityMessage;
    }
    
    public static void displayInGameMessage() {
        Minecraft mc = Minecraft.getMinecraft();
        
        if (level == CompatibilityLevel.UNSUPPORTED) {
            // Critical error
            mc.player.sendMessage(new TextComponentString(
                TextFormatting.RED + "[FPSFlux] " + compatibilityMessage
            ));
            mc.player.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + "The mod will be disabled. Your game will continue normally."
            ));
        } else if (level == CompatibilityLevel.MINIMAL) {
            // Warning
            mc.player.sendMessage(new TextComponentString(
                TextFormatting.YELLOW + "[FPSFlux] " + compatibilityMessage
            ));
        } else if (level == CompatibilityLevel.DEGRADED) {
            // Info
            mc.player.sendMessage(new TextComponentString(
                TextFormatting.GOLD + "[FPSFlux] " + compatibilityMessage
            ));
        }
        // OPTIMAL level gets no message (silent success)
    }
}
