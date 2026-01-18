/**
 * MixinUniversalPatcher - Universal First-Layer Call Router & Critical Patch System
 *
 * <h2>Architecture Overview:</h2>
 * <pre>
 * ╔══════════════════════════════════════════════════════════════════════════════════════╗
 * ║                      MixinUniversalPatcher v1.0 - First Layer                         ║
 * ╠══════════════════════════════════════════════════════════════════════════════════════╣
 * ║                                                                                       ║
 * ║    ┌─────────────────────────────────────────────────────────────────────────────┐   ║
 * ║    │                         INCOMING CALLS (Any Source)                          │   ║
 * ║    │  GlStateManager │ Raw GL* │ Other Mods │ Shaders │ Render Systems           │   ║
 * ║    └────────────────────────────────────┬────────────────────────────────────────┘   ║
 * ║                                         │                                            ║
 * ║                                         ▼                                            ║
 * ║    ┌─────────────────────────────────────────────────────────────────────────────┐   ║
 * ║    │                      ENVIRONMENT DETECTION LAYER                             │   ║
 * ║    │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐   │   ║
 * ║    │  │ Wrapper  │ │ Renderer │ │  Engine  │ │ Shaders  │ │ Installed Mods   │   │   ║
 * ║    │  │Detection │ │Detection │ │Detection │ │Detection │ │    Detection     │   │   ║
 * ║    │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────────────┘   │   ║
 * ║    └────────────────────────────────────┬────────────────────────────────────────┘   ║
 * ║                                         │                                            ║
 * ║                                         ▼                                            ║
 * ║    ┌─────────────────────────────────────────────────────────────────────────────┐   ║
 * ║    │                       CRITICAL PATCHES LAYER                                 │   ║
 * ║    │  • GlStateManager Cache Sync    • Exception Safety Wrappers                 │   ║
 * ║    │  • Raw GL Call Interception     • Configurable Thresholds                   │   ║
 * ║    │  • State Validation             • EntityAITasks Fix                         │   ║
 * ║    └────────────────────────────────────┬────────────────────────────────────────┘   ║
 * ║                                         │                                            ║
 * ║                                         ▼                                            ║
 * ║    ┌─────────────────────────────────────────────────────────────────────────────┐   ║
 * ║    │                        DYNAMIC CALL ROUTER                                   │   ║
 * ║    │                                                                              │   ║
 * ║    │   Config ──► ┌────────────────────────────────────────────────────────┐     │   ║
 * ║    │              │              Routing Decision Engine                    │     │   ║
 * ║    │              │   • Checks user config preferences                      │     │   ║
 * ║    │              │   • Validates hardware capabilities                     │     │   ║
 * ║    │              │   • Selects optimal backend                             │     │   ║
 * ║    │              │   • Handles fallbacks automatically                     │     │   ║
 * ║    │              └────────────────────────────────────────────────────────┘     │   ║
 * ║    └────────────────────────────────────┬────────────────────────────────────────┘   ║
 * ║                                         │                                            ║
 * ║         ┌───────────────┬───────────────┼───────────────┬───────────────┐            ║
 * ║         ▼               ▼               ▼               ▼               ▼            ║
 * ║    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐          ║
 * ║    │   GL    │    │  GLES   │    │  GLSL   │    │ SPIR-V  │    │ Vulkan  │          ║
 * ║    │ Manager │    │ Manager │    │ Manager │    │ Manager │    │ Manager │          ║
 * ║    └─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘          ║
 * ║                                                                                       ║
 * ║    ┌─────────────────────────────────────────────────────────────────────────────┐   ║
 * ║    │                         LOGGING INFRASTRUCTURE                               │   ║
 * ║    │  FpsFlux/CallLogs.log ◄── Detailed call traces, fallbacks, results          │   ║
 * ║    └─────────────────────────────────────────────────────────────────────────────┘   ║
 * ╚══════════════════════════════════════════════════════════════════════════════════════╝
 * </pre>
 *
 * <h2>Critical Patches Applied:</h2>
 * <ul>
 *   <li><b>GlStateManager Cache Coherency:</b> Intercepts raw GL calls to sync internal cache</li>
 *   <li><b>Exception-Safe Rendering:</b> Wraps all render operations in try/finally for state restoration</li>
 *   <li><b>Configurable Thresholds:</b> Teleport detection, frame timing, batch sizes all configurable</li>
 *   <li><b>EntityAITasks Fix:</b> Proper reflection-based entity owner retrieval with caching</li>
 *   <li><b>Mod Compatibility:</b> Detects and adapts to Optifine/Sodium/Rubidium/Shaders/etc.</li>
 * </ul>
 *
 * <h2>Supported Wrappers:</h2>
 * <ul>
 *   <li>Native (Direct Driver, best)</li>
 *   <li>Mesa (LLVMpipe, Softpipe, Zink)</li>
 *   <li>Vulkan Zink</li>
 *   <li>ANGLE (D3D9/D3D11/Vulkan backend)</li>
 *   <li>gl4es / gl4es+ / Holy gl4es / No-gl4es (Krypton Wrapper)</li>
 *   <li>MoltenVK / MoltenGL</li>
 *   <li>VirGL (Virtualized)</li>
 *   <li>Freedreno / Panfrost / Lima</li>
 *   <li>VGPU / MobileGLUES / LTW</li>
 * </ul>
 *
 * @author FPSFlux Team
 * @version 1.0.0 - Java 25 / LWJGL 3.3.6
 * @since FPSFlux 1.0
 */

package com.example.modid.mixins;

// ═══════════════════════════════════════════════════════════════════════════════════════
// MINECRAFT IMPORTS
// ═══════════════════════════════════════════════════════════════════════════════════════
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAITasks;

// ═══════════════════════════════════════════════════════════════════════════════════════
// MIXIN FRAMEWORK IMPORTS
// ═══════════════════════════════════════════════════════════════════════════════════════
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ═══════════════════════════════════════════════════════════════════════════════════════
// LWJGL 3.3.6 - OpenGL 1.0-4.6 Complete Import Set
// ═══════════════════════════════════════════════════════════════════════════════════════
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL41;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GL46;
import org.lwjgl.opengl.GLCapabilities;

// ═══════════════════════════════════════════════════════════════════════════════════════
// LWJGL 3.3.6 - OpenGL ES 2.0-3.2 Complete Import Set
// ═══════════════════════════════════════════════════════════════════════════════════════
import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLES20;
import org.lwjgl.opengles.GLES30;
import org.lwjgl.opengles.GLES31;
import org.lwjgl.opengles.GLES32;
import org.lwjgl.opengles.GLESCapabilities;

// ═══════════════════════════════════════════════════════════════════════════════════════
// LWJGL 3.3.6 - Vulkan 1.0-1.4 Complete Import Set
// ═══════════════════════════════════════════════════════════════════════════════════════
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VK14;

// ═══════════════════════════════════════════════════════════════════════════════════════
// LWJGL 3.3.6 - Shaderc & SPIR-V Cross Compiler
// ═══════════════════════════════════════════════════════════════════════════════════════
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spvc;

// ═══════════════════════════════════════════════════════════════════════════════════════
// LWJGL System & Memory
// ═══════════════════════════════════════════════════════════════════════════════════════
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;

// ═══════════════════════════════════════════════════════════════════════════════════════
// FPSFlux Internal Imports
// ═══════════════════════════════════════════════════════════════════════════════════════
import com.example.modid.gl.UniversalCapabilities;
import com.example.modid.gl.GLManager;
import com.example.modid.gl.GLESManager;
import com.example.modid.gl.GLSLManager;
import com.example.modid.gl.SPIRVManager;
import com.example.modid.gl.VulkanManager;
import com.example.modid.controlpanel.Config;
import com.example.modid.logs.ManagersCallLogs;

// ═══════════════════════════════════════════════════════════════════════════════════════
// Java 25 Modern Imports
// ═══════════════════════════════════════════════════════════════════════════════════════
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import java.util.stream.Collectors;


/**
 * Universal First-Layer Patcher for all graphics API calls.
 * 
 * This mixin intercepts all GlStateManager calls and routes them through
 * the appropriate backend manager based on configuration and hardware capabilities.
 */
@Mixin(GlStateManager.class)
public abstract class MixinUniversalPatcher {

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 1: CONSTANTS & CONFIGURATION DEFAULTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /** Mod identifier prefix for unique field/method names */
    @Unique
    private static final String FPSFLUX_PREFIX = "fpsflux$";

    /** Version identifier for this patcher */
    @Unique
    private static final String PATCHER_VERSION = "1.0.0";

    /** Log directory name (created outside mods folder) */
    @Unique
    private static final String LOG_DIRECTORY = "FpsFlux";

    /** Main call log filename */
    @Unique
    private static final String CALL_LOG_FILE = "CallLogs.log";

    /** Maximum log entries before flush */
    @Unique
    private static final int LOG_BUFFER_SIZE = 1024;

    /** Log flush interval in milliseconds */
    @Unique
    private static final long LOG_FLUSH_INTERVAL_MS = 5000L;

    /** Default teleport detection threshold (squared distance) */
    @Unique
    private static final double DEFAULT_TELEPORT_THRESHOLD_SQ = 64.0;

    /** Maximum fallback attempts before hard failure */
    @Unique
    private static final int MAX_FALLBACK_ATTEMPTS = 5;

    /** State validation interval in frames */
    @Unique
    private static final int STATE_VALIDATION_INTERVAL = 60;

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 2: INITIALIZATION STATE
    // ═══════════════════════════════════════════════════════════════════════════════════

    /** Global initialization flag - atomic for thread safety */
    @Unique
    private static final AtomicBoolean fpsflux$initialized = new AtomicBoolean(false);

    /** Capabilities detection complete flag */
    @Unique
    private static final AtomicBoolean fpsflux$capabilitiesDetected = new AtomicBoolean(false);

    /** Environment detection complete flag */
    @Unique
    private static final AtomicBoolean fpsflux$environmentDetected = new AtomicBoolean(false);

    /** Managers initialization complete flag */
    @Unique
    private static final AtomicBoolean fpsflux$managersInitialized = new AtomicBoolean(false);

    /** Logger initialization complete flag */
    @Unique
    private static final AtomicBoolean fpsflux$loggerInitialized = new AtomicBoolean(false);

    /** Frame counter for periodic operations */
    @Unique
    private static final AtomicLong fpsflux$frameCounter = new AtomicLong(0L);

    /** Initialization lock for thread-safe startup */
    @Unique
    private static final Object fpsflux$initLock = new Object();

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 3: ENVIRONMENT DETECTION RESULTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Enumeration of supported rendering mods
     */
    @Unique
    public enum RenderMod {
        VANILLA("Vanilla", "net.minecraft.client.renderer.EntityRenderer"),
        OPTIFINE("Optifine", "optifine.OptiFineClassTransformer"),
        SODIUM("Sodium", "me.jellysquid.mods.sodium.client.SodiumClientMod"),
        RUBIDIUM("Rubidium", "me.jellysquid.mods.rubidium.RubidiumMod"),
        EMBEDDIUM("Embeddium", "org.embeddedt.embeddium.impl.Embeddium"),
        CELERITAS("Celeritas", "net.celeritas.CeleritasMod"),
        NOTHIRIUM("Nothirium", "meldexun.nothirium.Nothirium"),
        NEONIUM("Neonium", "neonium.NeoniumMod"),
        RELICTIUM("Relictium", "relictium.RelictiumMod"),
        VINTAGIUM("Vintagium", "vintagium.VintagiumMod"),
        KIRINO("Kirino", "kirino.KirinoMod"),
        SNOWIUM("Snowium", "snowium.SnowiumMod"),
        IRIS("Iris", "net.coderbot.iris.Iris"),
        OCULUS("Oculus", "net.coderbot.iris.Iris"); // Oculus uses Iris classes

        public final String displayName;
        public final String detectionClass;

        RenderMod(String displayName, String detectionClass) {
            this.displayName = displayName;
            this.detectionClass = detectionClass;
        }
    }

    /**
     * Enumeration of graphics API engines
     */
    @Unique
    public enum GraphicsEngine {
        OPENGL("OpenGL"),
        OPENGL_ES("OpenGL ES"),
        VULKAN("Vulkan");

        public final String displayName;

        GraphicsEngine(String displayName) {
            this.displayName = displayName;
        }
    }

    /**
     * Enumeration of shader languages
     */
    @Unique
    public enum ShaderEngine {
        GLSL("GLSL"),
        SPIRV("SPIR-V"),
        NONE("None");

        public final String displayName;

        ShaderEngine(String displayName) {
            this.displayName = displayName;
        }
    }

    /**
     * Enumeration of supported GL wrappers
     */
    @Unique
    public enum GLWrapper {
        NATIVE("Native", "Direct driver access"),
        MESA_LLVMPIPE("Mesa LLVMpipe", "Software renderer"),
        MESA_SOFTPIPE("Mesa Softpipe", "Software renderer"),
        MESA_ZINK("Mesa Zink", "Vulkan-backed OpenGL"),
        ANGLE("ANGLE", "D3D/Vulkan to GL translation"),
        GL4ES("gl4es", "GLES to GL translation"),
        GL4ES_PLUS("gl4es+", "Enhanced gl4es"),
        HOLY_GL4ES("Holy gl4es", "Modified gl4es"),
        MOLTENVK("MoltenVK", "Vulkan to Metal"),
        MOLTENGL("MoltenGL", "OpenGL to Metal"),
        VIRGL("VirGL", "Virtualized GL"),
        FREEDRENO("Freedreno", "Qualcomm Adreno driver"),
        PANFROST("Panfrost", "ARM Mali driver"),
        LIMA("Lima", "ARM Mali-400 driver"),
        VGPU("VGPU", "Virtual GPU"),
        MOBILEGLUES("MobileGLUES", "Mobile GL wrapper"),
        LTW("LTW", "LibraryToWrapper"),
        KRYPTON("Krypton", "Krypton wrapper"),
        UNKNOWN("Unknown", "Unrecognized wrapper");

        public final String displayName;
        public final String description;

        GLWrapper(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    /** Detected active rendering mod */
    @Unique
    private static volatile RenderMod fpsflux$activeRenderMod = RenderMod.VANILLA;

    /** Detected graphics engine */
    @Unique
    private static volatile GraphicsEngine fpsflux$activeEngine = GraphicsEngine.OPENGL;

    /** Detected shader engine */
    @Unique
    private static volatile ShaderEngine fpsflux$activeShaderEngine = ShaderEngine.GLSL;

    /** Detected GL wrapper */
    @Unique
    private static volatile GLWrapper fpsflux$activeWrapper = GLWrapper.NATIVE;

    /** Engine version string */
    @Unique
    private static volatile String fpsflux$engineVersion = "Unknown";

    /** Shader engine version string */
    @Unique
    private static volatile String fpsflux$shaderEngineVersion = "Unknown";

    /** Set of all detected rendering mods (multiple can be active) */
    @Unique
    private static final Set<RenderMod> fpsflux$detectedMods = ConcurrentHashMap.newKeySet();

    /** Map of mod-specific compatibility flags */
    @Unique
    private static final ConcurrentHashMap<String, Boolean> fpsflux$compatibilityFlags = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 4: MANAGER REFERENCES
    // ═══════════════════════════════════════════════════════════════════════════════════

    /** Reference to GL Manager - lazily initialized */
    @Unique
    private static volatile GLManager fpsflux$glManager;

    /** Reference to GLES Manager - lazily initialized */
    @Unique
    private static volatile GLESManager fpsflux$glesManager;

    /** Reference to GLSL Manager - lazily initialized */
    @Unique
    private static volatile GLSLManager fpsflux$glslManager;

    /** Reference to SPIR-V Manager - lazily initialized */
    @Unique
    private static volatile SPIRVManager fpsflux$spirvManager;

    /** Reference to Vulkan Manager - lazily initialized */
    @Unique
    private static volatile VulkanManager fpsflux$vulkanManager;

    /** Active manager selector based on config */
    @Unique
    private static volatile ManagerType fpsflux$activeManagerType = ManagerType.GL;

    /**
     * Enumeration of available manager types
     */
    @Unique
    public enum ManagerType {
        GL, GLES, GLSL, SPIRV, VULKAN
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 5: STATE CACHE FOR GLSTATEMANAGER SYNC
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Off-heap state cache using Foreign Memory API (Java 21+)
     * Layout: 512 bytes total, cache-line aligned
     * 
     * [  0- 63] Capability flags (64 boolean slots as bits)
     * [ 64-127] Blend state (src, dst, srcAlpha, dstAlpha, enabled)
     * [128-191] Depth state (func, mask, enabled, range)
     * [192-255] Stencil state (func, ref, mask, fail, zfail, zpass)
     * [256-319] Cull state (mode, enabled, frontFace)
     * [320-383] Color state (r, g, b, a as floats)
     * [384-447] Viewport state (x, y, width, height)
     * [448-511] Bindings (texture, program, vao, vbo, fbo)
     */
    @Unique
    private static final int STATE_CACHE_SIZE = 512;

    @Unique
    private static Arena fpsflux$stateArena;

    @Unique
    private static MemorySegment fpsflux$stateCache;

    /** Dirty flags for state categories - bitfield */
    @Unique
    private static final AtomicLong fpsflux$stateDirtyFlags = new AtomicLong(0L);

    // State category bit positions
    @Unique private static final int DIRTY_CAPABILITIES = 0;
    @Unique private static final int DIRTY_BLEND = 1;
    @Unique private static final int DIRTY_DEPTH = 2;
    @Unique private static final int DIRTY_STENCIL = 3;
    @Unique private static final int DIRTY_CULL = 4;
    @Unique private static final int DIRTY_COLOR = 5;
    @Unique private static final int DIRTY_VIEWPORT = 6;
    @Unique private static final int DIRTY_BINDINGS = 7;

    /** VarHandle for atomic state cache access */
    @Unique
    private static final VarHandle fpsflux$stateVH;

    static {
        try {
            fpsflux$stateVH = MethodHandles.lookup().findStaticVarHandle(
                MixinUniversalPatcher.class, "fpsflux$stateDirtyFlags", AtomicLong.class
            );
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Last validated GL state hash - for detecting external modifications */
    @Unique
    private static final AtomicLong fpsflux$lastStateHash = new AtomicLong(0L);

    /** Capability state cache - mirrors GL capabilities */
    @Unique
    private static final ConcurrentHashMap<Integer, Boolean> fpsflux$capabilityCache = new ConcurrentHashMap<>(32);

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 6: EXCEPTION SAFETY INFRASTRUCTURE
    // ═══════════════════════════════════════════════════════════════════════════════════

    /** Thread-local state stack for exception recovery */
    @Unique
    private static final ThreadLocal<ArrayDeque<StateSnapshot>> fpsflux$stateStack = 
        ThreadLocal.withInitial(() -> new ArrayDeque<>(16));

    /** Current render depth for nested call tracking */
    @Unique
    private static final ThreadLocal<Integer> fpsflux$renderDepth = 
        ThreadLocal.withInitial(() -> 0);

    /** Exception counter for monitoring */
    @Unique
    private static final LongAdder fpsflux$exceptionCounter = new LongAdder();

    /** Last exception details for debugging */
    @Unique
    private static volatile String fpsflux$lastExceptionMessage = "";

    /**
     * State snapshot for exception recovery
     */
    @Unique
    private static final class StateSnapshot {
        final long timestamp;
        final long capabilityBits;
        final int blendSrc, blendDst;
        final int depthFunc;
        final boolean depthMask;
        final int cullMode;
        final float[] color = new float[4];
        final int boundTexture;
        final int boundProgram;
        final int boundVAO;
        final int boundFBO;

        StateSnapshot() {
            this.timestamp = System.nanoTime();
            this.capabilityBits = captureCapabilityBits();
            this.blendSrc = safeGetInteger(GL11.GL_BLEND_SRC);
            this.blendDst = safeGetInteger(GL11.GL_BLEND_DST);
            this.depthFunc = safeGetInteger(GL11.GL_DEPTH_FUNC);
            this.depthMask = safeGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            this.cullMode = safeGetInteger(GL11.GL_CULL_FACE_MODE);
            safeGetFloatv(GL11.GL_CURRENT_COLOR, this.color);
            this.boundTexture = safeGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            this.boundProgram = safeGetInteger(GL20.GL_CURRENT_PROGRAM);
            this.boundVAO = safeGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            this.boundFBO = safeGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        }

        void restore() {
            restoreCapabilityBits(this.capabilityBits);
            GL11.glBlendFunc(this.blendSrc, this.blendDst);
            GL11.glDepthFunc(this.depthFunc);
            GL11.glDepthMask(this.depthMask);
            GL11.glCullFace(this.cullMode);
            GL11.glColor4f(this.color[0], this.color[1], this.color[2], this.color[3]);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.boundTexture);
            if (UniversalCapabilities.GL.GL20) {
                GL20.glUseProgram(this.boundProgram);
            }
            if (UniversalCapabilities.GL.GL30) {
                GL30.glBindVertexArray(this.boundVAO);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.boundFBO);
            }
        }

        private static long captureCapabilityBits() {
            long bits = 0L;
            if (GL11.glIsEnabled(GL11.GL_BLEND)) bits |= (1L << 0);
            if (GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) bits |= (1L << 1);
            if (GL11.glIsEnabled(GL11.GL_CULL_FACE)) bits |= (1L << 2);
            if (GL11.glIsEnabled(GL11.GL_ALPHA_TEST)) bits |= (1L << 3);
            if (GL11.glIsEnabled(GL11.GL_LIGHTING)) bits |= (1L << 4);
            if (GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) bits |= (1L << 5);
            if (GL11.glIsEnabled(GL11.GL_FOG)) bits |= (1L << 6);
            if (GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL)) bits |= (1L << 7);
            if (GL11.glIsEnabled(GL11.GL_NORMALIZE)) bits |= (1L << 8);
            if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) bits |= (1L << 9);
            if (GL11.glIsEnabled(GL11.GL_STENCIL_TEST)) bits |= (1L << 10);
            if (GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL)) bits |= (1L << 11);
            return bits;
        }

        private static void restoreCapabilityBits(long bits) {
            setCapability(GL11.GL_BLEND, (bits & (1L << 0)) != 0);
            setCapability(GL11.GL_DEPTH_TEST, (bits & (1L << 1)) != 0);
            setCapability(GL11.GL_CULL_FACE, (bits & (1L << 2)) != 0);
            setCapability(GL11.GL_ALPHA_TEST, (bits & (1L << 3)) != 0);
            setCapability(GL11.GL_LIGHTING, (bits & (1L << 4)) != 0);
            setCapability(GL11.GL_TEXTURE_2D, (bits & (1L << 5)) != 0);
            setCapability(GL11.GL_FOG, (bits & (1L << 6)) != 0);
            setCapability(GL11.GL_COLOR_MATERIAL, (bits & (1L << 7)) != 0);
            setCapability(GL11.GL_NORMALIZE, (bits & (1L << 8)) != 0);
            setCapability(GL11.GL_SCISSOR_TEST, (bits & (1L << 9)) != 0);
            setCapability(GL11.GL_STENCIL_TEST, (bits & (1L << 10)) != 0);
            setCapability(GL11.GL_POLYGON_OFFSET_FILL, (bits & (1L << 11)) != 0);
        }

        private static void setCapability(int cap, boolean enabled) {
            if (enabled) GL11.glEnable(cap); else GL11.glDisable(cap);
        }

        private static int safeGetInteger(int pname) {
            try { return GL11.glGetInteger(pname); } catch (Exception e) { return 0; }
        }

        private static boolean safeGetBoolean(int pname) {
            try { return GL11.glGetBoolean(pname); } catch (Exception e) { return false; }
        }

        private static void safeGetFloatv(int pname, float[] params) {
            try {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    FloatBuffer buf = stack.mallocFloat(4);
                    GL11.glGetFloatv(pname, buf);
                    params[0] = buf.get(0);
                    params[1] = buf.get(1);
                    params[2] = buf.get(2);
                    params[3] = buf.get(3);
                }
            } catch (Exception e) {
                Arrays.fill(params, 1.0f);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 7: LOGGING INFRASTRUCTURE
    // ═══════════════════════════════════════════════════════════════════════════════════

    /** Log entry buffer for batched writing */
    @Unique
    private static final ConcurrentLinkedQueue<CallLogEntry> fpsflux$logBuffer = new ConcurrentLinkedQueue<>();

    /** Log file path */
    @Unique
    private static volatile Path fpsflux$logFilePath;

    /** Scheduled executor for periodic log flushing */
    @Unique
    private static ScheduledExecutorService fpsflux$logExecutor;

    /** Log write lock */
    @Unique
    private static final StampedLock fpsflux$logLock = new StampedLock();

    /** Total calls logged counter */
    @Unique
    private static final LongAdder fpsflux$totalCallsLogged = new LongAdder();

    /** Timestamp formatter for log entries */
    @Unique
    private static final DateTimeFormatter fpsflux$timestampFormatter = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    /**
     * Call log entry containing all required information
     */
    @Unique
    public static final class CallLogEntry {
        public final long timestamp;
        public final String callId;
        public final RenderMod renderMod;
        public final GraphicsEngine engine;
        public final ShaderEngine shaderEngine;
        public final String engineVersion;
        public final String shaderEngineVersion;
        public final String callContent;
        public final StringBuilder results;
        public final int fallbackCount;
        public final List<String> fallbackDetails;
        public final boolean success;
        public final GLWrapper wrapper;
        public final String errorMessage;
        public final long durationNanos;

        private CallLogEntry(Builder builder) {
            this.timestamp = builder.timestamp;
            this.callId = builder.callId;
            this.renderMod = builder.renderMod;
            this.engine = builder.engine;
            this.shaderEngine = builder.shaderEngine;
            this.engineVersion = builder.engineVersion;
            this.shaderEngineVersion = builder.shaderEngineVersion;
            this.callContent = builder.callContent;
            this.results = builder.results;
            this.fallbackCount = builder.fallbackCount;
            this.fallbackDetails = List.copyOf(builder.fallbackDetails);
            this.success = builder.success;
            this.wrapper = builder.wrapper;
            this.errorMessage = builder.errorMessage;
            this.durationNanos = builder.durationNanos;
        }

        public String format() {
            StringBuilder sb = new StringBuilder(512);
            String timestamp = fpsflux$timestampFormatter.format(Instant.ofEpochMilli(this.timestamp));
            
            sb.append("═══════════════════════════════════════════════════════════════════\n");
            sb.append("║ CALL LOG ENTRY: ").append(callId).append("\n");
            sb.append("║ Timestamp: ").append(timestamp).append("\n");
            sb.append("╠═══════════════════════════════════════════════════════════════════\n");
            sb.append("║ Renderer Used:      ").append(renderMod.displayName).append("\n");
            sb.append("║ Engine Used:        ").append(engine.displayName).append("\n");
            sb.append("║ Shaders Engine:     ").append(shaderEngine.displayName).append("\n");
            sb.append("║ Engine Version:     ").append(engineVersion).append("\n");
            sb.append("║ Shader Version:     ").append(shaderEngineVersion).append("\n");
            sb.append("║ Wrapper:            ").append(wrapper.displayName).append(" (").append(wrapper.description).append(")\n");
            sb.append("╠═══════════════════════════════════════════════════════════════════\n");
            sb.append("║ Call Content:\n");
            sb.append("║   ").append(callContent.replace("\n", "\n║   ")).append("\n");
            sb.append("╠═══════════════════════════════════════════════════════════════════\n");
            sb.append("║ Results:\n");
            sb.append("║   ").append(results.toString().replace("\n", "\n║   ")).append("\n");
            sb.append("╠═══════════════════════════════════════════════════════════════════\n");
            sb.append("║ Fallbacks Count:    ").append(fallbackCount).append("\n");
            if (!fallbackDetails.isEmpty()) {
                sb.append("║ Fallback Details:\n");
                for (String detail : fallbackDetails) {
                    sb.append("║   • ").append(detail).append("\n");
                }
            }
            sb.append("╠═══════════════════════════════════════════════════════════════════\n");
            sb.append("║ Status:             ").append(success ? "✓ SUCCESS" : "✗ FAIL").append("\n");
            if (!success && errorMessage != null && !errorMessage.isEmpty()) {
                sb.append("║ Error:              ").append(errorMessage).append("\n");
            }
            sb.append("║ Duration:           ").append(String.format("%.3f ms", durationNanos / 1_000_000.0)).append("\n");
            sb.append("╚═══════════════════════════════════════════════════════════════════\n\n");
            
            return sb.toString();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private long timestamp = System.currentTimeMillis();
            private String callId = "";
            private RenderMod renderMod = RenderMod.VANILLA;
            private GraphicsEngine engine = GraphicsEngine.OPENGL;
            private ShaderEngine shaderEngine = ShaderEngine.GLSL;
            private String engineVersion = "";
            private String shaderEngineVersion = "";
            private String callContent = "";
            private StringBuilder results = new StringBuilder();
            private int fallbackCount = 0;
            private List<String> fallbackDetails = new ArrayList<>();
            private boolean success = true;
            private GLWrapper wrapper = GLWrapper.NATIVE;
            private String errorMessage = "";
            private long durationNanos = 0;

            public Builder callId(String callId) { this.callId = callId; return this; }
            public Builder renderMod(RenderMod mod) { this.renderMod = mod; return this; }
            public Builder engine(GraphicsEngine engine) { this.engine = engine; return this; }
            public Builder shaderEngine(ShaderEngine engine) { this.shaderEngine = engine; return this; }
            public Builder engineVersion(String version) { this.engineVersion = version; return this; }
            public Builder shaderEngineVersion(String version) { this.shaderEngineVersion = version; return this; }
            public Builder callContent(String content) { this.callContent = content; return this; }
            public Builder appendResult(String result) { this.results.append(result).append("\n"); return this; }
            public Builder results(StringBuilder results) { this.results = results; return this; }
            public Builder fallbackCount(int count) { this.fallbackCount = count; return this; }
            public Builder addFallbackDetail(String detail) { this.fallbackDetails.add(detail); return this; }
            public Builder success(boolean success) { this.success = success; return this; }
            public Builder wrapper(GLWrapper wrapper) { this.wrapper = wrapper; return this; }
            public Builder errorMessage(String msg) { this.errorMessage = msg; return this; }
            public Builder durationNanos(long nanos) { this.durationNanos = nanos; return this; }

            public CallLogEntry build() {
                return new CallLogEntry(this);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 8: CALL ROUTING INFRASTRUCTURE
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Call types for routing
     */
    @Unique
    public enum CallType {
        STATE_ENABLE,
        STATE_DISABLE,
        BLEND,
        DEPTH,
        STENCIL,
        CULL,
        COLOR,
        VIEWPORT,
        SCISSOR,
        TEXTURE,
        SHADER,
        BUFFER,
        FRAMEBUFFER,
        DRAW,
        CLEAR,
        MATRIX,
        OTHER
    }

    /**
     * Routing decision result
     */
    @Unique
    public static final class RoutingDecision {
        public final ManagerType primaryManager;
        public final ManagerType fallbackManager;
        public final boolean useHardwarePath;
        public final boolean requiresStateSync;
        public final String reason;

        public RoutingDecision(ManagerType primary, ManagerType fallback, 
                               boolean hwPath, boolean stateSync, String reason) {
            this.primaryManager = primary;
            this.fallbackManager = fallback;
            this.useHardwarePath = hwPath;
            this.requiresStateSync = stateSync;
            this.reason = reason;
        }
    }

    /** Routing decision cache */
    @Unique
    private static final ConcurrentHashMap<CallType, RoutingDecision> fpsflux$routingCache = 
        new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 9: ENTITY AI TASKS FIX
    // ═══════════════════════════════════════════════════════════════════════════════════

    /** Cached Field reference for EntityAITasks owner entity */
    @Unique
    private static volatile Field fpsflux$entityAITasksOwnerField;

    /** Field lookup attempted flag */
    @Unique
    private static final AtomicBoolean fpsflux$entityFieldLookupAttempted = new AtomicBoolean(false);

    /** Weak cache for task -> entity mapping */
    @Unique
    private static final WeakHashMap<EntityAITasks, WeakReference<EntityLiving>> fpsflux$taskEntityCache = 
        new WeakHashMap<>();

    /** Lock for entity cache access */
    @Unique
    private static final StampedLock fpsflux$entityCacheLock = new StampedLock();

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 10: CONFIGURABLE THRESHOLDS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /** Teleport detection threshold (squared distance) - configurable */
    @Unique
    private static volatile double fpsflux$teleportThresholdSq = DEFAULT_TELEPORT_THRESHOLD_SQ;

    /** Maximum frame time for fixed timestep (seconds) */
    @Unique
    private static volatile double fpsflux$maxFrameTime = 0.25;

    /** Fixed timestep for physics (seconds) */
    @Unique
    private static volatile double fpsflux$fixedTimestep = 1.0 / 20.0;

    /** AI task throttle mask (bitwise AND with tick counter) */
    @Unique
    private static volatile int fpsflux$aiThrottleMask = 1; // Every other tick

    /** Batch size for draw call batching */
    @Unique
    private static volatile int fpsflux$batchSize = 256;

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 11: PERFORMANCE METRICS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /** State change counters by category */
    @Unique
    private static final EnumMap<CallType, LongAdder> fpsflux$callCounters = new EnumMap<>(CallType.class);

    /** Redundant call elimination counter */
    @Unique
    private static final LongAdder fpsflux$redundantCallsEliminated = new LongAdder();

    /** Fallback usage counter */
    @Unique
    private static final LongAdder fpsflux$fallbacksUsed = new LongAdder();

    /** Total call routing time (nanoseconds) */
    @Unique
    private static final LongAdder fpsflux$totalRoutingTimeNanos = new LongAdder();

    static {
        // Initialize call counters
        for (CallType type : CallType.values()) {
            fpsflux$callCounters.put(type, new LongAdder());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 12: INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Main initialization entry point - called once on first use
     */
    @Unique
    private static void fpsflux$initialize() {
        if (fpsflux$initialized.get()) return;

        synchronized (fpsflux$initLock) {
            if (fpsflux$initialized.get()) return;

            try {
                long startTime = System.nanoTime();

                // Step 1: Initialize logging first for diagnostics
                fpsflux$initializeLogging();

                // Step 2: Detect hardware capabilities
                fpsflux$detectCapabilities();

                // Step 3: Detect environment (mods, wrappers, etc.)
                fpsflux$detectEnvironment();

                // Step 4: Load configuration
                fpsflux$loadConfiguration();

                // Step 5: Initialize state cache
                fpsflux$initializeStateCache();

                // Step 6: Initialize managers based on config
                fpsflux$initializeManagers();

                // Step 7: Build routing cache
                fpsflux$buildRoutingCache();

                // Step 8: Setup EntityAITasks fix
                fpsflux$setupEntityAITasksFix();

                long duration = System.nanoTime() - startTime;
                
                fpsflux$logInitialization(duration);

                fpsflux$initialized.set(true);

            } catch (Throwable t) {
                fpsflux$logError("Initialization failed", t);
                // Apply minimal fallback to keep game running
                fpsflux$applyMinimalFallback();
                fpsflux$initialized.set(true);
            }
        }
    }

    /**
     * Initialize logging infrastructure
     */
    @Unique
    private static void fpsflux$initializeLogging() {
        try {
            // Create log directory outside mods folder
            Path gameDir = Paths.get("").toAbsolutePath();
            Path logDir = gameDir.resolve(LOG_DIRECTORY);
            Files.createDirectories(logDir);

            fpsflux$logFilePath = logDir.resolve(CALL_LOG_FILE);

            // Initialize log file with header
            String header = """
                ╔══════════════════════════════════════════════════════════════════════════════╗
                ║                     FPSFlux Universal Patcher Call Logs                       ║
                ║                           Version: %s                                      ║
                ║                    Initialized: %s                         ║
                ╚══════════════════════════════════════════════════════════════════════════════╝
                
                """.formatted(PATCHER_VERSION, fpsflux$timestampFormatter.format(Instant.now()));

            Files.writeString(fpsflux$logFilePath, header, 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Setup periodic flush executor
            fpsflux$logExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "FPSFlux-LogFlush");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });

            fpsflux$logExecutor.scheduleAtFixedRate(
                MixinUniversalPatcher::fpsflux$flushLogs,
                LOG_FLUSH_INTERVAL_MS,
                LOG_FLUSH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );

            fpsflux$loggerInitialized.set(true);

        } catch (IOException e) {
            System.err.println("[FPSFlux] Failed to initialize logging: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Detect hardware capabilities using UniversalCapabilities
     */
    @Unique
    private static void fpsflux$detectCapabilities() {
        try {
            // Initialize UniversalCapabilities
            UniversalCapabilities.detect();

            // Extract version information
            fpsflux$engineVersion = UniversalCapabilities.GL.majorVersion + "." + 
                                    UniversalCapabilities.GL.minorVersion;
            
            fpsflux$shaderEngineVersion = UniversalCapabilities.GLSL.majorVersion + "." + 
                                          UniversalCapabilities.GLSL.minorVersion;

            // Determine active engine based on capabilities
            if (UniversalCapabilities.GLES.isGLESContext || UniversalCapabilities.Wrapper.isGLES) {
                fpsflux$activeEngine = GraphicsEngine.OPENGL_ES;
                fpsflux$engineVersion = UniversalCapabilities.GLES.majorVersion + "." + 
                                        UniversalCapabilities.GLES.minorVersion;
            } else if (UniversalCapabilities.Vulkan.isAvailable && 
                       Config.getPreferredAPI() == Config.PreferredAPI.VULKAN) {
                fpsflux$activeEngine = GraphicsEngine.VULKAN;
                fpsflux$engineVersion = UniversalCapabilities.Vulkan.majorVersion + "." + 
                                        UniversalCapabilities.Vulkan.minorVersion + "." +
                                        UniversalCapabilities.Vulkan.patchVersion;
            } else {
                fpsflux$activeEngine = GraphicsEngine.OPENGL;
            }

            // Determine shader engine
            if (UniversalCapabilities.SPIRV.hasGLSPIRV && 
                Config.getPreferredShaderEngine() == Config.ShaderEngine.SPIRV) {
                fpsflux$activeShaderEngine = ShaderEngine.SPIRV;
                fpsflux$shaderEngineVersion = UniversalCapabilities.SPIRV.majorVersion + "." + 
                                              UniversalCapabilities.SPIRV.minorVersion;
            } else if (UniversalCapabilities.Features.glsl) {
                fpsflux$activeShaderEngine = ShaderEngine.GLSL;
            } else {
                fpsflux$activeShaderEngine = ShaderEngine.NONE;
            }

            fpsflux$capabilitiesDetected.set(true);

        } catch (Throwable t) {
            fpsflux$logError("Capability detection failed", t);
            // Set safe defaults
            fpsflux$activeEngine = GraphicsEngine.OPENGL;
            fpsflux$activeShaderEngine = ShaderEngine.GLSL;
            fpsflux$engineVersion = "1.1";
            fpsflux$shaderEngineVersion = "1.10";
        }
    }

    /**
     * Detect environment - wrappers, rendering mods, etc.
     */
    @Unique
    private static void fpsflux$detectEnvironment() {
        try {
            // Detect wrapper from UniversalCapabilities
            fpsflux$activeWrapper = fpsflux$mapWrapperType(UniversalCapabilities.Wrapper.type);

            // Detect rendering mods
            for (RenderMod mod : RenderMod.values()) {
                if (mod == RenderMod.VANILLA) continue;
                try {
                    Class.forName(mod.detectionClass);
                    fpsflux$detectedMods.add(mod);
                    fpsflux$logInfo("Detected rendering mod: " + mod.displayName);
                } catch (ClassNotFoundException ignored) {
                    // Mod not present
                }
            }

            // Set active render mod (prioritize highest impact)
            if (fpsflux$detectedMods.contains(RenderMod.SODIUM)) {
                fpsflux$activeRenderMod = RenderMod.SODIUM;
            } else if (fpsflux$detectedMods.contains(RenderMod.RUBIDIUM)) {
                fpsflux$activeRenderMod = RenderMod.RUBIDIUM;
            } else if (fpsflux$detectedMods.contains(RenderMod.EMBEDDIUM)) {
                fpsflux$activeRenderMod = RenderMod.EMBEDDIUM;
            } else if (fpsflux$detectedMods.contains(RenderMod.OPTIFINE)) {
                fpsflux$activeRenderMod = RenderMod.OPTIFINE;
            } else if (!fpsflux$detectedMods.isEmpty()) {
                fpsflux$activeRenderMod = fpsflux$detectedMods.iterator().next();
            } else {
                fpsflux$activeRenderMod = RenderMod.VANILLA;
            }

            // Set compatibility flags based on detected mods
            fpsflux$setupCompatibilityFlags();

            fpsflux$environmentDetected.set(true);

        } catch (Throwable t) {
            fpsflux$logError("Environment detection failed", t);
            fpsflux$activeRenderMod = RenderMod.VANILLA;
            fpsflux$activeWrapper = GLWrapper.UNKNOWN;
        }
    }

    /**
     * Map UniversalCapabilities wrapper type to our enum
     */
    @Unique
    private static GLWrapper fpsflux$mapWrapperType(UniversalCapabilities.Wrapper.Type type) {
        return switch (type) {
            case NATIVE -> GLWrapper.NATIVE;
            case GL4ES -> GLWrapper.GL4ES;
            case ANGLE -> GLWrapper.ANGLE;
            case MESA_SOFTWARE -> GLWrapper.MESA_LLVMPIPE;
            case MESA_ZINK -> GLWrapper.MESA_ZINK;
            case VIRGL -> GLWrapper.VIRGL;
            case MGL -> GLWrapper.MOLTENGL;
            case WINE -> GLWrapper.NATIVE; // Wine uses native-ish GL
            default -> GLWrapper.UNKNOWN;
        };
    }

    /**
     * Setup compatibility flags based on detected mods
     */
    @Unique
    private static void fpsflux$setupCompatibilityFlags() {
        // OptiFine compatibility
        if (fpsflux$detectedMods.contains(RenderMod.OPTIFINE)) {
            fpsflux$compatibilityFlags.put("optifine.shaders", true);
            fpsflux$compatibilityFlags.put("optifine.fastRender", true);
            fpsflux$compatibilityFlags.put("skip.stateManager.overwrite", true);
        }

        // Sodium/Rubidium compatibility
        if (fpsflux$detectedMods.contains(RenderMod.SODIUM) || 
            fpsflux$detectedMods.contains(RenderMod.RUBIDIUM) ||
            fpsflux$detectedMods.contains(RenderMod.EMBEDDIUM)) {
            fpsflux$compatibilityFlags.put("sodium.chunkRenderer", true);
            fpsflux$compatibilityFlags.put("sodium.vertexFormat", true);
            fpsflux$compatibilityFlags.put("defer.stateChanges", true);
        }

        // Iris/Oculus compatibility
        if (fpsflux$detectedMods.contains(RenderMod.IRIS) || 
            fpsflux$detectedMods.contains(RenderMod.OCULUS)) {
            fpsflux$compatibilityFlags.put("iris.shaderPack", true);
            fpsflux$compatibilityFlags.put("iris.deferredRendering", true);
        }

        // Wrapper-specific flags
        if (fpsflux$activeWrapper == GLWrapper.GL4ES || 
            fpsflux$activeWrapper == GLWrapper.GL4ES_PLUS ||
            fpsflux$activeWrapper == GLWrapper.HOLY_GL4ES) {
            fpsflux$compatibilityFlags.put("wrapper.gles", true);
            fpsflux$compatibilityFlags.put("wrapper.limitedFeatures", true);
        }

        if (fpsflux$activeWrapper == GLWrapper.ANGLE) {
            fpsflux$compatibilityFlags.put("wrapper.angle", true);
            fpsflux$compatibilityFlags.put("wrapper.d3dBackend", true);
        }

        if (fpsflux$activeWrapper == GLWrapper.MESA_ZINK) {
            fpsflux$compatibilityFlags.put("wrapper.zink", true);
            fpsflux$compatibilityFlags.put("wrapper.vulkanBacked", true);
        }
    }

    /**
     * Load configuration from Config
     */
    @Unique
    private static void fpsflux$loadConfiguration() {
        try {
            // Load thresholds from config
            fpsflux$teleportThresholdSq = Config.getTeleportThresholdSquared();
            fpsflux$maxFrameTime = Config.getMaxFrameTime();
            fpsflux$fixedTimestep = Config.getFixedTimestep();
            fpsflux$aiThrottleMask = Config.getAIThrottleMask();
            fpsflux$batchSize = Config.getBatchSize();

            // Determine active manager type from config
            fpsflux$activeManagerType = switch (Config.getPreferredAPI()) {
                case VULKAN -> UniversalCapabilities.Vulkan.isAvailable ? 
                               ManagerType.VULKAN : ManagerType.GL;
                case OPENGL_ES -> UniversalCapabilities.GLES.isGLESContext ? 
                                  ManagerType.GLES : ManagerType.GL;
                default -> ManagerType.GL;
            };

            fpsflux$logInfo("Configuration loaded successfully");

        } catch (Throwable t) {
            fpsflux$logError("Configuration loading failed, using defaults", t);
            // Defaults already set in field initializers
        }
    }

    /**
     * Initialize off-heap state cache
     */
    @Unique
    private static void fpsflux$initializeStateCache() {
        try {
            // Use confined arena for automatic cleanup
            fpsflux$stateArena = Arena.ofShared();
            fpsflux$stateCache = fpsflux$stateArena.allocate(STATE_CACHE_SIZE, 64); // 64-byte aligned

            // Zero-initialize
            fpsflux$stateCache.fill((byte) 0);

            // Capture initial state
            fpsflux$syncStateFromGL();

            fpsflux$logInfo("State cache initialized (" + STATE_CACHE_SIZE + " bytes, 64-byte aligned)");

        } catch (Throwable t) {
            fpsflux$logError("State cache initialization failed", t);
            // Fall back to capability cache only
        }
    }

    /**
     * Initialize backend managers
     */
    @Unique
    private static void fpsflux$initializeManagers() {
        try {
            // Always initialize GL manager as fallback
            fpsflux$glManager = GLManager.getInstance();

            // Initialize other managers based on capabilities and config
            if (UniversalCapabilities.GLES.isGLESContext || 
                UniversalCapabilities.Wrapper.isGLES) {
                fpsflux$glesManager = GLESManager.getInstance();
            }

            if (UniversalCapabilities.Features.glsl) {
                fpsflux$glslManager = GLSLManager.getInstance();
            }

            if (UniversalCapabilities.SPIRV.hasGLSPIRV) {
                fpsflux$spirvManager = SPIRVManager.getInstance();
            }

            if (UniversalCapabilities.Vulkan.isAvailable && 
                Config.getPreferredAPI() == Config.PreferredAPI.VULKAN) {
                fpsflux$vulkanManager = VulkanManager.getInstance();
            }

            fpsflux$managersInitialized.set(true);
            fpsflux$logInfo("Managers initialized: GL=" + (fpsflux$glManager != null) +
                           ", GLES=" + (fpsflux$glesManager != null) +
                           ", GLSL=" + (fpsflux$glslManager != null) +
                           ", SPIRV=" + (fpsflux$spirvManager != null) +
                           ", Vulkan=" + (fpsflux$vulkanManager != null));

        } catch (Throwable t) {
            fpsflux$logError("Manager initialization failed", t);
            // Ensure at least GL fallback
            if (fpsflux$glManager == null) {
                try {
                    fpsflux$glManager = GLManager.getInstance();
                } catch (Throwable t2) {
                    fpsflux$logError("GL Manager fallback failed", t2);
                }
            }
        }
    }

    /**
     * Build routing decision cache
     */
    @Unique
    private static void fpsflux$buildRoutingCache() {
        for (CallType type : CallType.values()) {
            RoutingDecision decision = fpsflux$computeRoutingDecision(type);
            fpsflux$routingCache.put(type, decision);
        }
        fpsflux$logInfo("Routing cache built for " + CallType.values().length + " call types");
    }

    /**
     * Compute routing decision for a call type
     */
    @Unique
    private static RoutingDecision fpsflux$computeRoutingDecision(CallType type) {
        ManagerType primary = fpsflux$activeManagerType;
        ManagerType fallback = ManagerType.GL;
        boolean hwPath = true;
        boolean stateSync = false;
        String reason;

        // Shader-related calls go to shader managers
        if (type == CallType.SHADER) {
            if (fpsflux$activeShaderEngine == ShaderEngine.SPIRV && fpsflux$spirvManager != null) {
                primary = ManagerType.SPIRV;
                fallback = ManagerType.GLSL;
                reason = "SPIR-V shader engine selected";
            } else if (fpsflux$glslManager != null) {
                primary = ManagerType.GLSL;
                fallback = ManagerType.GL;
                reason = "GLSL shader engine selected";
            } else {
                primary = ManagerType.GL;
                fallback = ManagerType.GL;
                reason = "No shader manager available, using GL fallback";
            }
        }
        // State changes need sync
        else if (type == CallType.STATE_ENABLE || type == CallType.STATE_DISABLE) {
            stateSync = true;
            reason = "State change requires cache sync";
        }
        // Draw calls use current active manager
        else if (type == CallType.DRAW) {
            reason = "Draw call using active manager: " + primary;
        }
        // Default case
        else {
            reason = "Default routing to " + primary;
        }

        // Apply wrapper quirks
        if (UniversalCapabilities.Wrapper.isSoftware) {
            hwPath = false;
            reason += " (software path - wrapper is software renderer)";
        }

        return new RoutingDecision(primary, fallback, hwPath, stateSync, reason);
    }

    /**
     * Setup EntityAITasks owner field access
     */
    @Unique
    private static void fpsflux$setupEntityAITasksFix() {
        if (fpsflux$entityFieldLookupAttempted.getAndSet(true)) return;

        try {
            // Try to find the entity field in EntityAITasks
            Class<?> tasksClass = EntityAITasks.class;
            
            // Common field names across versions
            String[] possibleNames = {"entity", "owner", "this$0", "field_75256_a"};
            
            for (String name : possibleNames) {
                try {
                    Field field = tasksClass.getDeclaredField(name);
                    if (EntityLiving.class.isAssignableFrom(field.getType()) ||
                        Entity.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        fpsflux$entityAITasksOwnerField = field;
                        fpsflux$logInfo("Found EntityAITasks owner field: " + name);
                        return;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }

            // Search all fields if specific names didn't work
            for (Field field : tasksClass.getDeclaredFields()) {
                if (EntityLiving.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    fpsflux$entityAITasksOwnerField = field;
                    fpsflux$logInfo("Found EntityAITasks owner field by type search: " + field.getName());
                    return;
                }
            }

            fpsflux$logInfo("Could not find EntityAITasks owner field - AI throttling may be limited");

        } catch (Throwable t) {
            fpsflux$logError("EntityAITasks field lookup failed", t);
        }
    }

    /**
     * Apply minimal fallback configuration
     */
    @Unique
    private static void fpsflux$applyMinimalFallback() {
        fpsflux$activeEngine = GraphicsEngine.OPENGL;
        fpsflux$activeShaderEngine = ShaderEngine.NONE;
        fpsflux$activeWrapper = GLWrapper.UNKNOWN;
        fpsflux$activeRenderMod = RenderMod.VANILLA;
        fpsflux$activeManagerType = ManagerType.GL;
        fpsflux$engineVersion = "1.1";
        fpsflux$shaderEngineVersion = "1.10";

        // Initialize basic routing
        for (CallType type : CallType.values()) {
            fpsflux$routingCache.put(type, new RoutingDecision(
                ManagerType.GL, ManagerType.GL, false, false, "Minimal fallback mode"
            ));
        }

        fpsflux$logInfo("Applied minimal fallback configuration");
    }

    /**
     * Log initialization summary
     */
    @Unique
    private static void fpsflux$logInitialization(long durationNanos) {
        String summary = """
            
            ╔══════════════════════════════════════════════════════════════════════════════╗
            ║              FPSFlux Universal Patcher Initialized Successfully              ║
            ╠══════════════════════════════════════════════════════════════════════════════╣
            ║ Patcher Version:    %s                                                    ║
            ║ Renderer:           %-50s ║
            ║ Engine:             %-12s (Version: %-25s) ║
            ║ Shader Engine:      %-12s (Version: %-25s) ║
            ║ Wrapper:            %-50s ║
            ║ Active Manager:     %-50s ║
            ║ Detected Mods:      %-50s ║
            ║ Initialization:     %.3f ms                                              ║
            ╚══════════════════════════════════════════════════════════════════════════════╝
            """.formatted(
                PATCHER_VERSION,
                fpsflux$activeRenderMod.displayName,
                fpsflux$activeEngine.displayName, fpsflux$engineVersion,
                fpsflux$activeShaderEngine.displayName, fpsflux$shaderEngineVersion,
                fpsflux$activeWrapper.displayName + " (" + fpsflux$activeWrapper.description + ")",
                fpsflux$activeManagerType.name(),
                fpsflux$detectedMods.isEmpty() ? "None" : 
                    fpsflux$detectedMods.stream().map(m -> m.displayName).collect(Collectors.joining(", ")),
                durationNanos / 1_000_000.0
            );

        fpsflux$logInfo(summary);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 13: STATE CACHE SYNCHRONIZATION
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Sync state cache from actual GL state.
     * Called periodically to detect external modifications.
     */
    @Unique
    private static void fpsflux$syncStateFromGL() {
        try {
            // Update capability cache from GL
            fpsflux$capabilityCache.put(GL11.GL_BLEND, GL11.glIsEnabled(GL11.GL_BLEND));
            fpsflux$capabilityCache.put(GL11.GL_DEPTH_TEST, GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
            fpsflux$capabilityCache.put(GL11.GL_CULL_FACE, GL11.glIsEnabled(GL11.GL_CULL_FACE));
            fpsflux$capabilityCache.put(GL11.GL_ALPHA_TEST, GL11.glIsEnabled(GL11.GL_ALPHA_TEST));
            fpsflux$capabilityCache.put(GL11.GL_TEXTURE_2D, GL11.glIsEnabled(GL11.GL_TEXTURE_2D));
            fpsflux$capabilityCache.put(GL11.GL_LIGHTING, GL11.glIsEnabled(GL11.GL_LIGHTING));
            fpsflux$capabilityCache.put(GL11.GL_FOG, GL11.glIsEnabled(GL11.GL_FOG));
            fpsflux$capabilityCache.put(GL11.GL_SCISSOR_TEST, GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
            fpsflux$capabilityCache.put(GL11.GL_STENCIL_TEST, GL11.glIsEnabled(GL11.GL_STENCIL_TEST));

            // Compute state hash for change detection
            long hash = fpsflux$computeStateHash();
            fpsflux$lastStateHash.set(hash);

            // Clear dirty flags
            fpsflux$stateDirtyFlags.set(0L);

        } catch (Throwable t) {
            // Silently ignore - state sync is best-effort
        }
    }

    /**
     * Compute hash of current GL state for change detection
     */
    @Unique
    private static long fpsflux$computeStateHash() {
        long hash = 17L;
        for (Map.Entry<Integer, Boolean> entry : fpsflux$capabilityCache.entrySet()) {
            hash = hash * 31 + entry.getKey();
            hash = hash * 31 + (entry.getValue() ? 1 : 0);
        }
        return hash;
    }

    /**
     * Validate state cache against actual GL state.
     * Detects when other mods have modified state directly.
     */
    @Unique
    private static boolean fpsflux$validateStateCache() {
        // Quick hash check first
        long currentHash = fpsflux$computeStateHash();
        long cachedHash = fpsflux$lastStateHash.get();

        if (currentHash != cachedHash) {
            // State has changed externally - resync
            fpsflux$syncStateFromGL();
            return false;
        }
        return true;
    }

    /**
     * Mark state category as dirty
     */
    @Unique
    private static void fpsflux$markStateDirty(int category) {
        fpsflux$stateDirtyFlags.updateAndGet(flags -> flags | (1L << category));
    }

    /**
     * Check if state category is dirty
     */
    @Unique
    private static boolean fpsflux$isStateDirty(int category) {
        return (fpsflux$stateDirtyFlags.get() & (1L << category)) != 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 14: CALL ROUTING ENGINE
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Main call routing entry point.
     * Routes a graphics call to the appropriate manager based on type and config.
     *
     * @param callType The type of call being made
     * @param callContent Description of the call for logging
     * @param executor The actual call execution
     * @return True if call succeeded, false if failed
     */
    @Unique
    private static boolean fpsflux$routeCall(CallType callType, String callContent, Runnable executor) {
        long startTime = System.nanoTime();
        CallLogEntry.Builder logBuilder = CallLogEntry.builder()
            .callId(callType.name() + "_" + System.nanoTime())
            .callContent(callContent)
            .renderMod(fpsflux$activeRenderMod)
            .engine(fpsflux$activeEngine)
            .shaderEngine(fpsflux$activeShaderEngine)
            .engineVersion(fpsflux$engineVersion)
            .shaderEngineVersion(fpsflux$shaderEngineVersion)
            .wrapper(fpsflux$activeWrapper);

        RoutingDecision decision = fpsflux$routingCache.get(callType);
        if (decision == null) {
            decision = fpsflux$computeRoutingDecision(callType);
            fpsflux$routingCache.put(callType, decision);
        }

        logBuilder.appendResult("Routing Decision: " + decision.reason);

        // Sync state if required
        if (decision.requiresStateSync) {
            fpsflux$validateStateCache();
            logBuilder.appendResult("State cache validated");
        }

        int fallbackCount = 0;
        List<String> fallbackDetails = new ArrayList<>();
        boolean success = false;
        String errorMessage = "";

        // Try primary manager
        ManagerType currentManager = decision.primaryManager;
        for (int attempt = 0; attempt <= MAX_FALLBACK_ATTEMPTS; attempt++) {
            try {
                fpsflux$executeWithManager(currentManager, executor);
                success = true;
                logBuilder.appendResult("Executed successfully with " + currentManager);
                break;

            } catch (Throwable t) {
                fallbackCount++;
                errorMessage = t.getMessage();
                String fallbackDetail = "Attempt " + (attempt + 1) + " failed with " + 
                                       currentManager + ": " + t.getClass().getSimpleName() + 
                                       " - " + t.getMessage();
                fallbackDetails.add(fallbackDetail);
                logBuilder.appendResult(fallbackDetail);

                // Determine next fallback
                if (currentManager != ManagerType.GL) {
                    currentManager = ManagerType.GL;
                    logBuilder.appendResult("Falling back to GL manager");
                } else {
                    // Last resort - try direct GL call
                    try {
                        executor.run();
                        success = true;
                        logBuilder.appendResult("Direct execution succeeded");
                        break;
                    } catch (Throwable t2) {
                        errorMessage = t2.getMessage();
                        logBuilder.appendResult("Direct execution failed: " + t2.getMessage());
                    }
                }
            }
        }

        long duration = System.nanoTime() - startTime;

        // Update metrics
        fpsflux$callCounters.get(callType).increment();
        fpsflux$totalRoutingTimeNanos.add(duration);
        if (fallbackCount > 0) {
            fpsflux$fallbacksUsed.add(fallbackCount);
        }

        // Build and queue log entry
        logBuilder.fallbackCount(fallbackCount)
                  .success(success)
                  .errorMessage(success ? "" : errorMessage)
                  .durationNanos(duration);

        for (String detail : fallbackDetails) {
            logBuilder.addFallbackDetail(detail);
        }

        // Import logged data from managers
        try {
            StringBuilder managerLogs = ManagersCallLogs.getRecentLogs();
            if (managerLogs != null && managerLogs.length() > 0) {
                logBuilder.appendResult("--- Manager Logs ---\n" + managerLogs);
            }
        } catch (Throwable ignored) {

        // Queue log entry for async writing
        fpsflux$queueLogEntry(logBuilder.build());

        return success;
    }

    /**
     * Execute call with specific manager
     */
    @Unique
    private static void fpsflux$executeWithManager(ManagerType manager, Runnable executor) throws Throwable {
        switch (manager) {
            case GL -> {
                if (fpsflux$glManager != null) {
                    fpsflux$glManager.execute(executor);
                } else {
                    executor.run();
                }
            }
            case GLES -> {
                if (fpsflux$glesManager != null) {
                    fpsflux$glesManager.execute(executor);
                } else {
                    throw new UnsupportedOperationException("GLES manager not available");
                }
            }
            case GLSL -> {
                if (fpsflux$glslManager != null) {
                    fpsflux$glslManager.execute(executor);
                } else {
                    throw new UnsupportedOperationException("GLSL manager not available");
                }
            }
            case SPIRV -> {
                if (fpsflux$spirvManager != null) {
                    fpsflux$spirvManager.execute(executor);
                } else {
                    throw new UnsupportedOperationException("SPIR-V manager not available");
                }
            }
            case VULKAN -> {
                if (fpsflux$vulkanManager != null) {
                    fpsflux$vulkanManager.execute(executor);
                } else {
                    throw new UnsupportedOperationException("Vulkan manager not available");
                }
            }
        }
    }

    /**
     * Route call with return value
     */
    @Unique
    private static <T> T fpsflux$routeCallWithResult(CallType callType, String callContent, Supplier<T> executor) {
        long startTime = System.nanoTime();
        CallLogEntry.Builder logBuilder = CallLogEntry.builder()
            .callId(callType.name() + "_" + System.nanoTime())
            .callContent(callContent)
            .renderMod(fpsflux$activeRenderMod)
            .engine(fpsflux$activeEngine)
            .shaderEngine(fpsflux$activeShaderEngine)
            .engineVersion(fpsflux$engineVersion)
            .shaderEngineVersion(fpsflux$shaderEngineVersion)
            .wrapper(fpsflux$activeWrapper);

        RoutingDecision decision = fpsflux$routingCache.get(callType);
        if (decision == null) {
            decision = fpsflux$computeRoutingDecision(callType);
            fpsflux$routingCache.put(callType, decision);
        }

        if (decision.requiresStateSync) {
            fpsflux$validateStateCache();
        }

        int fallbackCount = 0;
        List<String> fallbackDetails = new ArrayList<>();
        T result = null;
        boolean success = false;
        String errorMessage = "";

        ManagerType currentManager = decision.primaryManager;
        for (int attempt = 0; attempt <= MAX_FALLBACK_ATTEMPTS; attempt++) {
            try {
                result = fpsflux$executeWithManagerResult(currentManager, executor);
                success = true;
                logBuilder.appendResult("Executed successfully with " + currentManager + ", result: " + result);
                break;
            } catch (Throwable t) {
                fallbackCount++;
                errorMessage = t.getMessage();
                String fallbackDetail = "Attempt " + (attempt + 1) + " failed: " + t.getMessage();
                fallbackDetails.add(fallbackDetail);

                if (currentManager != ManagerType.GL) {
                    currentManager = ManagerType.GL;
                } else {
                    try {
                        result = executor.get();
                        success = true;
                        break;
                    } catch (Throwable t2) {
                        errorMessage = t2.getMessage();
                    }
                }
            }
        }

        long duration = System.nanoTime() - startTime;
        fpsflux$callCounters.get(callType).increment();
        fpsflux$totalRoutingTimeNanos.add(duration);
        if (fallbackCount > 0) fpsflux$fallbacksUsed.add(fallbackCount);

        logBuilder.fallbackCount(fallbackCount)
                  .success(success)
                  .errorMessage(success ? "" : errorMessage)
                  .durationNanos(duration);
        for (String detail : fallbackDetails) {
            logBuilder.addFallbackDetail(detail);
        }

        fpsflux$queueLogEntry(logBuilder.build());
        return result;
    }

    /**
     * Execute with manager returning result
     */
    @Unique
    private static <T> T fpsflux$executeWithManagerResult(ManagerType manager, Supplier<T> executor) throws Throwable {
        return switch (manager) {
            case GL -> fpsflux$glManager != null ? fpsflux$glManager.executeWithResult(executor) : executor.get();
            case GLES -> {
                if (fpsflux$glesManager != null) yield fpsflux$glesManager.executeWithResult(executor);
                throw new UnsupportedOperationException("GLES manager not available");
            }
            case GLSL -> {
                if (fpsflux$glslManager != null) yield fpsflux$glslManager.executeWithResult(executor);
                throw new UnsupportedOperationException("GLSL manager not available");
            }
            case SPIRV -> {
                if (fpsflux$spirvManager != null) yield fpsflux$spirvManager.executeWithResult(executor);
                throw new UnsupportedOperationException("SPIR-V manager not available");
            }
            case VULKAN -> {
                if (fpsflux$vulkanManager != null) yield fpsflux$vulkanManager.executeWithResult(executor);
                throw new UnsupportedOperationException("Vulkan manager not available");
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 15: EXCEPTION SAFETY WRAPPERS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Push current state onto recovery stack before risky operation
     */
    @Unique
    public static void fpsflux$pushState() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        ArrayDeque<StateSnapshot> stack = fpsflux$stateStack.get();
        stack.push(new StateSnapshot());
        fpsflux$renderDepth.set(fpsflux$renderDepth.get() + 1);
    }

    /**
     * Pop and restore state from recovery stack
     */
    @Unique
    public static void fpsflux$popState() {
        ArrayDeque<StateSnapshot> stack = fpsflux$stateStack.get();
        if (!stack.isEmpty()) {
            StateSnapshot snapshot = stack.pop();
            snapshot.restore();
        }
        int depth = fpsflux$renderDepth.get();
        if (depth > 0) {
            fpsflux$renderDepth.set(depth - 1);
        }
    }

    /**
     * Execute render operation with exception safety
     */
    @Unique
    public static void fpsflux$executeWithSafety(Runnable operation, String operationName) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();

        fpsflux$pushState();
        try {
            operation.run();
        } catch (Throwable t) {
            fpsflux$exceptionCounter.increment();
            fpsflux$lastExceptionMessage = operationName + ": " + t.getMessage();
            fpsflux$logError("Exception in " + operationName, t);
        } finally {
            fpsflux$popState();
        }
    }

    /**
     * Execute render operation with exception safety and return value
     */
    @Unique
    public static <T> T fpsflux$executeWithSafetyResult(Supplier<T> operation, T defaultValue, String operationName) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();

        fpsflux$pushState();
        try {
            return operation.get();
        } catch (Throwable t) {
            fpsflux$exceptionCounter.increment();
            fpsflux$lastExceptionMessage = operationName + ": " + t.getMessage();
            fpsflux$logError("Exception in " + operationName, t);
            return defaultValue;
        } finally {
            fpsflux$popState();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 16: ENTITYAITASKS FIX IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Get owning entity from EntityAITasks instance with caching
     */
    @Unique
    public static EntityLiving fpsflux$getOwningEntity(EntityAITasks tasks) {
        if (tasks == null) return null;

        // Try cache first
        long stamp = fpsflux$entityCacheLock.tryOptimisticRead();
        WeakReference<EntityLiving> cachedRef = fpsflux$taskEntityCache.get(tasks);
        if (fpsflux$entityCacheLock.validate(stamp) && cachedRef != null) {
            EntityLiving cached = cachedRef.get();
            if (cached != null) return cached;
        }

        // Cache miss or stale - do lookup
        stamp = fpsflux$entityCacheLock.writeLock();
        try {
            // Double-check after acquiring lock
            cachedRef = fpsflux$taskEntityCache.get(tasks);
            if (cachedRef != null) {
                EntityLiving cached = cachedRef.get();
                if (cached != null) return cached;
            }

            // Perform reflection lookup
            EntityLiving entity = fpsflux$lookupOwningEntity(tasks);
            if (entity != null) {
                fpsflux$taskEntityCache.put(tasks, new WeakReference<>(entity));
            }
            return entity;

        } finally {
            fpsflux$entityCacheLock.unlockWrite(stamp);
        }
    }

    /**
     * Perform actual reflection lookup for owner entity
     */
    @Unique
    private static EntityLiving fpsflux$lookupOwningEntity(EntityAITasks tasks) {
        if (fpsflux$entityAITasksOwnerField == null) return null;

        try {
            Object value = fpsflux$entityAITasksOwnerField.get(tasks);
            if (value instanceof EntityLiving living) {
                return living;
            }
        } catch (Throwable t) {
            // Silently fail - reflection might not work in all contexts
        }
        return null;
    }

    /**
     * Check if AI task should be throttled this tick
     */
    @Unique
    public static boolean fpsflux$shouldThrottleAI(long tickCounter) {
        return (tickCounter & fpsflux$aiThrottleMask) != 0;
    }

    /**
     * Get teleport threshold for entity movement detection
     */
    @Unique
    public static double fpsflux$getTeleportThresholdSq() {
        return fpsflux$teleportThresholdSq;
    }

    /**
     * Get max frame time for fixed timestep
     */
    @Unique
    public static double fpsflux$getMaxFrameTime() {
        return fpsflux$maxFrameTime;
    }

    /**
     * Get fixed timestep value
     */
    @Unique
    public static double fpsflux$getFixedTimestep() {
        return fpsflux$fixedTimestep;
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 17: LOGGING IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Queue log entry for async writing
     */
    @Unique
    private static void fpsflux$queueLogEntry(CallLogEntry entry) {
        if (!fpsflux$loggerInitialized.get()) return;

        fpsflux$logBuffer.offer(entry);
        fpsflux$totalCallsLogged.increment();

        // Flush if buffer is full
        if (fpsflux$logBuffer.size() >= LOG_BUFFER_SIZE) {
            fpsflux$flushLogs();
        }
    }

    /**
     * Flush pending log entries to file
     */
    @Unique
    private static void fpsflux$flushLogs() {
        if (!fpsflux$loggerInitialized.get() || fpsflux$logBuffer.isEmpty()) return;

        long stamp = fpsflux$logLock.writeLock();
        try {
            StringBuilder batch = new StringBuilder(LOG_BUFFER_SIZE * 512);
            CallLogEntry entry;
            int count = 0;
            while ((entry = fpsflux$logBuffer.poll()) != null && count < LOG_BUFFER_SIZE) {
                batch.append(entry.format());
                count++;
            }

            if (batch.length() > 0) {
                Files.writeString(fpsflux$logFilePath, batch.toString(), 
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            }
        } catch (IOException e) {
            System.err.println("[FPSFlux] Failed to flush logs: " + e.getMessage());
        } finally {
            fpsflux$logLock.unlockWrite(stamp);
        }
    }

    /**
     * Log info message
     */
    @Unique
    private static void fpsflux$logInfo(String message) {
        String formatted = "[" + fpsflux$timestampFormatter.format(Instant.now()) + "] [INFO] " + message + "\n";
        System.out.println("[FPSFlux] " + message);
        
        if (fpsflux$loggerInitialized.get()) {
            try {
                Files.writeString(fpsflux$logFilePath, formatted, 
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            } catch (IOException ignored) {}
        }
    }

    /**
     * Log error message with exception
     */
    @Unique
    private static void fpsflux$logError(String message, Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(fpsflux$timestampFormatter.format(Instant.now())).append("] [ERROR] ");
        sb.append(message);
        if (t != null) {
            sb.append(": ").append(t.getClass().getName()).append(" - ").append(t.getMessage()).append("\n");
            for (StackTraceElement element : t.getStackTrace()) {
                sb.append("    at ").append(element.toString()).append("\n");
                if (sb.length() > 4096) break; // Limit stack trace length
            }
        }
        sb.append("\n");

        System.err.println("[FPSFlux] " + message + (t != null ? ": " + t.getMessage() : ""));
        
        if (fpsflux$loggerInitialized.get()) {
            try {
                Files.writeString(fpsflux$logFilePath, sb.toString(), 
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            } catch (IOException ignored) {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 18: RAW GL CALL INTERCEPTION (Cache Sync)
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Intercept capability enable and sync cache
     */
    @Unique
    public static void fpsflux$onGLEnable(int cap) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        Boolean cached = fpsflux$capabilityCache.get(cap);
        if (cached != null && cached) {
            fpsflux$redundantCallsEliminated.increment();
            return; // Already enabled
        }
        
        fpsflux$capabilityCache.put(cap, true);
        fpsflux$markStateDirty(DIRTY_CAPABILITIES);
    }

    /**
     * Intercept capability disable and sync cache
     */
    @Unique
    public static void fpsflux$onGLDisable(int cap) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        Boolean cached = fpsflux$capabilityCache.get(cap);
        if (cached != null && !cached) {
            fpsflux$redundantCallsEliminated.increment();
            return; // Already disabled
        }
        
        fpsflux$capabilityCache.put(cap, false);
        fpsflux$markStateDirty(DIRTY_CAPABILITIES);
    }

    /**
     * Check if capability is enabled (cached)
     */
    @Unique
    public static boolean fpsflux$isEnabled(int cap) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        Boolean cached = fpsflux$capabilityCache.get(cap);
        if (cached != null) {
            return cached;
        }
        
        // Cache miss - query GL
        boolean enabled = GL11.glIsEnabled(cap);
        fpsflux$capabilityCache.put(cap, enabled);
        return enabled;
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 19: GLSTATEMANAGER OVERWRITES
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for enableAlpha
     */
    @Overwrite
    public static void enableAlpha() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            return;
        }

        fpsflux$routeCall(CallType.STATE_ENABLE, "enableAlpha()", () -> {
            fpsflux$onGLEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for disableAlpha
     */
    @Overwrite
    public static void disableAlpha() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            return;
        }

        fpsflux$routeCall(CallType.STATE_DISABLE, "disableAlpha()", () -> {
            fpsflux$onGLDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for enableBlend
     */
    @Overwrite
    public static void enableBlend() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glEnable(GL11.GL_BLEND);
            return;
        }

        fpsflux$routeCall(CallType.STATE_ENABLE, "enableBlend()", () -> {
            fpsflux$onGLEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_BLEND);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for disableBlend
     */
    @Overwrite
    public static void disableBlend() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glDisable(GL11.GL_BLEND);
            return;
        }

        fpsflux$routeCall(CallType.STATE_DISABLE, "disableBlend()", () -> {
            fpsflux$onGLDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_BLEND);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for blendFunc
     */
    @Overwrite
    public static void blendFunc(int srcFactor, int dstFactor) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.BLEND, 
            "blendFunc(src=" + srcFactor + ", dst=" + dstFactor + ")", () -> {
            fpsflux$markStateDirty(DIRTY_BLEND);
            GL11.glBlendFunc(srcFactor, dstFactor);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for tryBlendFuncSeparate
     */
    @Overwrite
    public static void tryBlendFuncSeparate(int srcFactor, int dstFactor, int srcFactorAlpha, int dstFactorAlpha) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.BLEND,
            "tryBlendFuncSeparate(src=" + srcFactor + ", dst=" + dstFactor + 
            ", srcA=" + srcFactorAlpha + ", dstA=" + dstFactorAlpha + ")", () -> {
            fpsflux$markStateDirty(DIRTY_BLEND);
            if (UniversalCapabilities.GL.GL14) {
                GL14.glBlendFuncSeparate(srcFactor, dstFactor, srcFactorAlpha, dstFactorAlpha);
            } else {
                GL11.glBlendFunc(srcFactor, dstFactor);
            }
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for enableDepth
     */
    @Overwrite
    public static void enableDepth() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            return;
        }

        fpsflux$routeCall(CallType.STATE_ENABLE, "enableDepth()", () -> {
            fpsflux$onGLEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for disableDepth
     */
    @Overwrite
    public static void disableDepth() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            return;
        }

        fpsflux$routeCall(CallType.STATE_DISABLE, "disableDepth()", () -> {
            fpsflux$onGLDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for depthFunc
     */
    @Overwrite
    public static void depthFunc(int depthFunc) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.DEPTH, "depthFunc(" + depthFunc + ")", () -> {
            fpsflux$markStateDirty(DIRTY_DEPTH);
            GL11.glDepthFunc(depthFunc);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for depthMask
     */
    @Overwrite
    public static void depthMask(boolean flagIn) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.DEPTH, "depthMask(" + flagIn + ")", () -> {
            fpsflux$markStateDirty(DIRTY_DEPTH);
            GL11.glDepthMask(flagIn);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for enableCull
     */
    @Overwrite
    public static void enableCull() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glEnable(GL11.GL_CULL_FACE);
            return;
        }

        fpsflux$routeCall(CallType.STATE_ENABLE, "enableCull()", () -> {
            fpsflux$onGLEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_CULL_FACE);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for disableCull
     */
    @Overwrite
    public static void disableCull() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glDisable(GL11.GL_CULL_FACE);
            return;
        }

        fpsflux$routeCall(CallType.STATE_DISABLE, "disableCull()", () -> {
            fpsflux$onGLDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_CULL_FACE);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for cullFace
     */
    @Overwrite
    public static void cullFace(int mode) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.CULL, "cullFace(" + mode + ")", () -> {
            fpsflux$markStateDirty(DIRTY_CULL);
            GL11.glCullFace(mode);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for enableTexture2D
     */
    @Overwrite
    public static void enableTexture2D() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            return;
        }

        fpsflux$routeCall(CallType.STATE_ENABLE, "enableTexture2D()", () -> {
            fpsflux$onGLEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for disableTexture2D
     */
    @Overwrite
    public static void disableTexture2D() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            return;
        }

        fpsflux$routeCall(CallType.STATE_DISABLE, "disableTexture2D()", () -> {
            fpsflux$onGLDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for bindTexture
     */
    @Overwrite
    public static void bindTexture(int texture) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE, "bindTexture(" + texture + ")", () -> {
            fpsflux$markStateDirty(DIRTY_BINDINGS);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for enableLighting
     */
    @Overwrite
    public static void enableLighting() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glEnable(GL11.GL_LIGHTING);
            return;
        }

        fpsflux$routeCall(CallType.STATE_ENABLE, "enableLighting()", () -> {
            fpsflux$onGLEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_LIGHTING);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for disableLighting
     */
    @Overwrite
    public static void disableLighting() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glDisable(GL11.GL_LIGHTING);
            return;
        }

        fpsflux$routeCall(CallType.STATE_DISABLE, "disableLighting()", () -> {
            fpsflux$onGLDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_LIGHTING);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for enableFog
     */
    @Overwrite
    public static void enableFog() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glEnable(GL11.GL_FOG);
            return;
        }

        fpsflux$routeCall(CallType.STATE_ENABLE, "enableFog()", () -> {
            fpsflux$onGLEnable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_FOG);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for disableFog
     */
    @Overwrite
    public static void disableFog() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        if (fpsflux$compatibilityFlags.getOrDefault("skip.stateManager.overwrite", false)) {
            GL11.glDisable(GL11.GL_FOG);
            return;
        }

        fpsflux$routeCall(CallType.STATE_DISABLE, "disableFog()", () -> {
            fpsflux$onGLDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_FOG);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for enableColorMaterial
     */
    @Overwrite
    public static void enableColorMaterial() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STATE_ENABLE, "enableColorMaterial()", () -> {
            fpsflux$onGLEnable(GL11.GL_COLOR_MATERIAL);
            GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for disableColorMaterial
     */
    @Overwrite
    public static void disableColorMaterial() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STATE_DISABLE, "disableColorMaterial()", () -> {
            fpsflux$onGLDisable(GL11.GL_COLOR_MATERIAL);
            GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for color
     */
    @Overwrite
    public static void color(float r, float g, float b, float a) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.COLOR, 
            "color(" + r + ", " + g + ", " + b + ", " + a + ")", () -> {
            fpsflux$markStateDirty(DIRTY_COLOR);
            GL11.glColor4f(r, g, b, a);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for color (RGB)
     */
    @Overwrite
    public static void color(float r, float g, float b) {
        color(r, g, b, 1.0f);
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for resetColor
     */
    @Overwrite
    public static void resetColor() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.COLOR, "resetColor()", () -> {
            fpsflux$markStateDirty(DIRTY_COLOR);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for clear
     */
    @Overwrite
    public static void clear(int mask) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.CLEAR, "clear(mask=" + mask + ")", () -> {
            GL11.glClear(mask);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for clearColor
     */
    @Overwrite
    public static void clearColor(float r, float g, float b, float a) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.CLEAR, 
            "clearColor(" + r + ", " + g + ", " + b + ", " + a + ")", () -> {
            GL11.glClearColor(r, g, b, a);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for viewport
     */
    @Overwrite
    public static void viewport(int x, int y, int width, int height) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.VIEWPORT, 
            "viewport(" + x + ", " + y + ", " + width + ", " + height + ")", () -> {
            fpsflux$markStateDirty(DIRTY_VIEWPORT);
            GL11.glViewport(x, y, width, height);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for colorMask
     */
    @Overwrite
    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.COLOR, 
            "colorMask(" + red + ", " + green + ", " + blue + ", " + alpha + ")", () -> {
            GL11.glColorMask(red, green, blue, alpha);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for enablePolygonOffset
     */
    @Overwrite
    public static void enablePolygonOffset() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STATE_ENABLE, "enablePolygonOffset()", () -> {
            fpsflux$onGLEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for disablePolygonOffset
     */
    @Overwrite
    public static void disablePolygonOffset() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STATE_DISABLE, "disablePolygonOffset()", () -> {
            fpsflux$onGLDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for doPolygonOffset
     */
    @Overwrite
    public static void doPolygonOffset(float factor, float units) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, 
            "doPolygonOffset(" + factor + ", " + units + ")", () -> {
            GL11.glPolygonOffset(factor, units);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for enableNormalize
     */
    @Overwrite
    public static void enableNormalize() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STATE_ENABLE, "enableNormalize()", () -> {
            fpsflux$onGLEnable(GL11.GL_NORMALIZE);
            GL11.glEnable(GL11.GL_NORMALIZE);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for disableNormalize
     */
    @Overwrite
    public static void disableNormalize() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STATE_DISABLE, "disableNormalize()", () -> {
            fpsflux$onGLDisable(GL11.GL_NORMALIZE);
            GL11.glDisable(GL11.GL_NORMALIZE);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for shadeModel
     */
    @Overwrite
    public static void shadeModel(int mode) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "shadeModel(" + mode + ")", () -> {
            GL11.glShadeModel(mode);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for enableRescaleNormal
     */
    @Overwrite
    public static void enableRescaleNormal() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STATE_ENABLE, "enableRescaleNormal()", () -> {
            if (UniversalCapabilities.GL.GL12) {
                fpsflux$onGLEnable(GL12.GL_RESCALE_NORMAL);
                GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            } else {
                fpsflux$onGLEnable(GL11.GL_NORMALIZE);
                GL11.glEnable(GL11.GL_NORMALIZE);
            }
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing and cache sync for disableRescaleNormal
     */
    @Overwrite
    public static void disableRescaleNormal() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STATE_DISABLE, "disableRescaleNormal()", () -> {
            if (UniversalCapabilities.GL.GL12) {
                fpsflux$onGLDisable(GL12.GL_RESCALE_NORMAL);
                GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            } else {
                fpsflux$onGLDisable(GL11.GL_NORMALIZE);
                GL11.glDisable(GL11.GL_NORMALIZE);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 20: MATRIX OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Universal routing for pushMatrix
     */
    @Overwrite
    public static void pushMatrix() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.MATRIX, "pushMatrix()", GL11::glPushMatrix);
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for popMatrix
     */
    @Overwrite
    public static void popMatrix() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.MATRIX, "popMatrix()", GL11::glPopMatrix);
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for loadIdentity
     */
    @Overwrite
    public static void loadIdentity() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.MATRIX, "loadIdentity()", GL11::glLoadIdentity);
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for translate
     */
    @Overwrite
    public static void translate(float x, float y, float z) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.MATRIX, 
            "translate(" + x + ", " + y + ", " + z + ")", () -> {
            GL11.glTranslatef(x, y, z);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for translate (double)
     */
    @Overwrite
    public static void translate(double x, double y, double z) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.MATRIX, 
            "translate(" + x + ", " + y + ", " + z + ")", () -> {
            GL11.glTranslated(x, y, z);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for scale
     */
    @Overwrite
    public static void scale(float x, float y, float z) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.MATRIX, 
            "scale(" + x + ", " + y + ", " + z + ")", () -> {
            GL11.glScalef(x, y, z);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for scale (double)
     */
    @Overwrite
    public static void scale(double x, double y, double z) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.MATRIX, 
            "scale(" + x + ", " + y + ", " + z + ")", () -> {
            GL11.glScaled(x, y, z);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for rotate
     */
    @Overwrite
    public static void rotate(float angle, float x, float y, float z) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.MATRIX, 
            "rotate(" + angle + ", " + x + ", " + y + ", " + z + ")", () -> {
            GL11.glRotatef(angle, x, y, z);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for matrixMode
     */
    @Overwrite
    public static void matrixMode(int mode) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.MATRIX, "matrixMode(" + mode + ")", () -> {
            GL11.glMatrixMode(mode);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for ortho
     */
    @Overwrite
    public static void ortho(double left, double right, double bottom, double top, double zNear, double zFar) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.MATRIX, 
            "ortho(" + left + ", " + right + ", " + bottom + ", " + top + ", " + zNear + ", " + zFar + ")", () -> {
            GL11.glOrtho(left, right, bottom, top, zNear, zFar);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 21: TEXTURE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Universal routing for setActiveTexture
     */
    @Overwrite
    public static void setActiveTexture(int texture) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE, "setActiveTexture(" + texture + ")", () -> {
            fpsflux$markStateDirty(DIRTY_BINDINGS);
            if (UniversalCapabilities.GL.GL13) {
                GL13.glActiveTexture(texture);
            }
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for deleteTexture
     */
    @Overwrite
    public static void deleteTexture(int texture) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE, "deleteTexture(" + texture + ")", () -> {
            GL11.glDeleteTextures(texture);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for generateTexture
     */
    @Overwrite
    public static int generateTexture() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        return fpsflux$routeCallWithResult(CallType.TEXTURE, "generateTexture()", GL11::glGenTextures);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 22: STENCIL AND SCISSOR OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Universal routing for enableScissor (added for completeness)
     */
    @Unique
    public static void fpsflux$enableScissor() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SCISSOR, "enableScissor()", () -> {
            fpsflux$onGLEnable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        });
    }

    /**
     * @author FPSFlux  
     * @reason Universal routing for disableScissor (added for completeness)
     */
    @Unique
    public static void fpsflux$disableScissor() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SCISSOR, "disableScissor()", () -> {
            fpsflux$onGLDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        });
    }

    /**
     * @author FPSFlux
     * @reason Universal routing for scissor (added for completeness)
     */
    @Unique
    public static void fpsflux$scissor(int x, int y, int width, int height) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SCISSOR, 
            "scissor(" + x + ", " + y + ", " + width + ", " + height + ")", () -> {
            GL11.glScissor(x, y, width, height);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 23: FRAME LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Called at frame start for periodic maintenance
     */
    @Unique
    public static void fpsflux$onFrameStart() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();

        long frame = fpsflux$frameCounter.incrementAndGet();

        // Periodic state validation
        if ((frame % STATE_VALIDATION_INTERVAL) == 0) {
            fpsflux$validateStateCache();
        }
    }

    /**
     * Called at frame end
     */
    @Unique
    public static void fpsflux$onFrameEnd() {
        // Flush logs if buffer is getting full
        if (fpsflux$logBuffer.size() > LOG_BUFFER_SIZE / 2) {
            fpsflux$flushLogs();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 24: METRICS AND DIAGNOSTICS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Get performance metrics summary
     */
    @Unique
    public static String fpsflux$getMetricsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════════╗\n");
        sb.append("║               FPSFlux Universal Patcher Metrics                  ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Frames Processed:      ").append(String.format("%,15d", fpsflux$frameCounter.get())).append("            ║\n");
        sb.append("║ Total Calls Logged:    ").append(String.format("%,15d", fpsflux$totalCallsLogged.sum())).append("            ║\n");
        sb.append("║ Redundant Eliminated:  ").append(String.format("%,15d", fpsflux$redundantCallsEliminated.sum())).append("            ║\n");
        sb.append("║ Fallbacks Used:        ").append(String.format("%,15d", fpsflux$fallbacksUsed.sum())).append("            ║\n");
        sb.append("║ Exceptions Caught:     ").append(String.format("%,15d", fpsflux$exceptionCounter.sum())).append("            ║\n");
        sb.append("║ Total Routing Time:    ").append(String.format("%,12.3f ms", fpsflux$totalRoutingTimeNanos.sum() / 1_000_000.0)).append("          ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Call Breakdown:                                                  ║\n");
        for (CallType type : CallType.values()) {
            long count = fpsflux$callCounters.get(type).sum();
            if (count > 0) {
                sb.append("║   ").append(String.format("%-20s", type.name())).append(": ");
                sb.append(String.format("%,15d", count)).append("                   ║\n");
            }
        }
        sb.append("╚══════════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }

    /**
     * Reset all metrics counters
     */
    @Unique
    public static void fpsflux$resetMetrics() {
        fpsflux$frameCounter.set(0);
        fpsflux$redundantCallsEliminated.reset();
        fpsflux$fallbacksUsed.reset();
        fpsflux$totalRoutingTimeNanos.reset();
        fpsflux$exceptionCounter.reset();
        fpsflux$totalCallsLogged.reset();
        for (LongAdder counter : fpsflux$callCounters.values()) {
            counter.reset();
        }
    }

    /**
     * Check if a compatibility flag is set
     */
    @Unique
    public static boolean fpsflux$hasCompatibilityFlag(String flag) {
        return fpsflux$compatibilityFlags.getOrDefault(flag, false);
    }

    /**
     * Set a compatibility flag
     */
    @Unique
    public static void fpsflux$setCompatibilityFlag(String flag, boolean value) {
        fpsflux$compatibilityFlags.put(flag, value);
    }

    /**
     * Get detected rendering mods
     */
    @Unique
    public static Set<RenderMod> fpsflux$getDetectedMods() {
        return Collections.unmodifiableSet(fpsflux$detectedMods);
    }

    /**
     * Get active wrapper
     */
    @Unique
    public static GLWrapper fpsflux$getActiveWrapper() {
        return fpsflux$activeWrapper;
    }

    /**
     * Get active render mod
     */
    @Unique
    public static RenderMod fpsflux$getActiveRenderMod() {
        return fpsflux$activeRenderMod;
    }

    /**
     * Get active engine
     */
    @Unique
    public static GraphicsEngine fpsflux$getActiveEngine() {
        return fpsflux$activeEngine;
    }

    /**
     * Get active shader engine
     */
    @Unique
    public static ShaderEngine fpsflux$getActiveShaderEngine() {
        return fpsflux$activeShaderEngine;
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 25: SHUTDOWN AND CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Cleanup on mod unload
     */
    @Unique
    public static void fpsflux$shutdown() {
        try {
            // Flush remaining logs
            fpsflux$flushLogs();

            // Shutdown log executor
            if (fpsflux$logExecutor != null) {
                fpsflux$logExecutor.shutdown();
                try {
                    if (!fpsflux$logExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        fpsflux$logExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    fpsflux$logExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // Close state arena
            if (fpsflux$stateArena != null) {
                try {
                    fpsflux$stateArena.close();
                } catch (Throwable ignored) {}
            }

            // Log final metrics
            fpsflux$logInfo("Shutting down...\n" + fpsflux$getMetricsSummary());

            // Clear caches
            fpsflux$capabilityCache.clear();
            fpsflux$routingCache.clear();
            fpsflux$compatibilityFlags.clear();
            fpsflux$detectedMods.clear();
            fpsflux$taskEntityCache.clear();

            fpsflux$initialized.set(false);

        } catch (Throwable t) {
            System.err.println("[FPSFlux] Shutdown error: " + t.getMessage());
        }
    }

    /**
     * Register shutdown hook
     */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(MixinUniversalPatcher::fpsflux$shutdown, "FPSFlux-Shutdown"));
    }
}

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 26: MERGED FROM ORIGINAL MixinGlStateManager - LOCAL CACHES
    // ═══════════════════════════════════════════════════════════════════════════════════

    /** Cached alpha function */
    @Unique
    private static int fpsflux$cachedAlphaFunc = GL11.GL_ALWAYS;

    /** Cached alpha reference value */
    @Unique
    private static float fpsflux$cachedAlphaRef = 0.0f;

    /** Cached cull mode */
    @Unique
    private static int fpsflux$cachedCullMode = GL11.GL_BACK;

    /** Cached shade model */
    @Unique
    private static int fpsflux$cachedShadeModel = GL11.GL_SMOOTH;

    /** Cached matrix mode */
    @Unique
    private static int fpsflux$cachedMatrixMode = GL11.GL_MODELVIEW;

    /** Cached color state */
    @Unique
    private static float fpsflux$cachedRed = 1.0f;
    @Unique
    private static float fpsflux$cachedGreen = 1.0f;
    @Unique
    private static float fpsflux$cachedBlue = 1.0f;
    @Unique
    private static float fpsflux$cachedAlpha = 1.0f;
    @Unique
    private static boolean fpsflux$colorStateDirty = true;

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 27: ALPHA FUNCTION
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Cached alpha function with state tracking
     */
    @Overwrite
    public static void alphaFunc(int func, float ref) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();

        if (func != fpsflux$cachedAlphaFunc || ref != fpsflux$cachedAlphaRef) {
            fpsflux$cachedAlphaFunc = func;
            fpsflux$cachedAlphaRef = ref;
            fpsflux$routeCall(CallType.OTHER, "alphaFunc(" + func + ", " + ref + ")", () -> {
                GL11.glAlphaFunc(func, ref);
            });
        } else {
            fpsflux$redundantCallsEliminated.increment();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 28: TEXTURE OPERATIONS (Extended)
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Texture parameter caching
     */
    @Overwrite
    public static void glTexParameteri(int target, int pname, int param) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE, 
            "glTexParameteri(" + target + ", " + pname + ", " + param + ")", () -> {
            GL11.glTexParameteri(target, pname, param);
        });
    }

    /**
     * @author FPSFlux
     * @reason Direct texture parameter call
     */
    @Overwrite
    public static void glTexParameterf(int target, int pname, float param) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE, 
            "glTexParameterf(" + target + ", " + pname + ", " + param + ")", () -> {
            GL11.glTexParameterf(target, pname, param);
        });
    }

    /**
     * @author FPSFlux
     * @reason Texture image specification
     */
    @Overwrite
    public static void glTexImage2D(int target, int level, int internalFormat, int width, int height,
                                     int border, int format, int type, IntBuffer pixels) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE,
            "glTexImage2D(" + target + ", " + level + ", " + width + "x" + height + ")", () -> {
            GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
        });
    }

    /**
     * @author FPSFlux
     * @reason Texture subimage update
     */
    @Overwrite
    public static void glTexSubImage2D(int target, int level, int xOffset, int yOffset,
                                        int width, int height, int format, int type, IntBuffer pixels) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE,
            "glTexSubImage2D(" + target + ", " + level + ", " + xOffset + ", " + yOffset + ")", () -> {
            GL11.glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, pixels);
        });
    }

    /**
     * @author FPSFlux
     * @reason Copy pixels from framebuffer to texture
     */
    @Overwrite
    public static void glCopyTexSubImage2D(int target, int level, int xOffset, int yOffset,
                                            int x, int y, int width, int height) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE,
            "glCopyTexSubImage2D(" + target + ", " + level + ")", () -> {
            GL11.glCopyTexSubImage2D(target, level, xOffset, yOffset, x, y, width, height);
        });
    }

    /**
     * @author FPSFlux
     * @reason Get texture level parameter
     */
    @Overwrite
    public static void glGetTexLevelParameteriv(int target, int level, int pname, IntBuffer params) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE, "glGetTexLevelParameteriv(" + target + ", " + level + ")", () -> {
            GL11.glGetTexLevelParameteriv(target, level, pname, params);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 29: DEPTH OPERATIONS (Extended)
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Clear depth setting
     */
    @Overwrite
    public static void clearDepth(double depth) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.DEPTH, "clearDepth(" + depth + ")", () -> {
            GL11.glClearDepth(depth);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 30: BLEND EQUATION
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Blend equation support
     */
    @Overwrite
    public static void blendEquation(int mode) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.BLEND, "blendEquation(" + mode + ")", () -> {
            fpsflux$markStateDirty(DIRTY_BLEND);
            GL14.glBlendEquation(mode);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 31: SCISSOR OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Scissor test enable
     */
    @Overwrite
    public static void enableScissorTest() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SCISSOR, "enableScissorTest()", () -> {
            fpsflux$onGLEnable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        });
    }

    /**
     * @author FPSFlux
     * @reason Scissor test disable
     */
    @Overwrite
    public static void disableScissorTest() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SCISSOR, "disableScissorTest()", () -> {
            fpsflux$onGLDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        });
    }

    /**
     * @author FPSFlux
     * @reason Scissor box definition
     */
    @Overwrite
    public static void scissor(int x, int y, int width, int height) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SCISSOR, 
            "scissor(" + x + ", " + y + ", " + width + ", " + height + ")", () -> {
            GL11.glScissor(x, y, width, height);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 32: LIGHTING OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Enable individual light
     */
    @Overwrite
    public static void enableLight(int light) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        int cap = GL11.GL_LIGHT0 + light;
        fpsflux$routeCall(CallType.STATE_ENABLE, "enableLight(" + light + ")", () -> {
            fpsflux$onGLEnable(cap);
            GL11.glEnable(cap);
        });
    }

    /**
     * @author FPSFlux
     * @reason Disable individual light
     */
    @Overwrite
    public static void disableLight(int light) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        int cap = GL11.GL_LIGHT0 + light;
        fpsflux$routeCall(CallType.STATE_DISABLE, "disableLight(" + light + ")", () -> {
            fpsflux$onGLDisable(cap);
            GL11.glDisable(cap);
        });
    }

    /**
     * @author FPSFlux
     * @reason Light parameter setting
     */
    @Overwrite
    public static void glLight(int light, int pname, FloatBuffer params) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "glLight(" + light + ", " + pname + ")", () -> {
            GL11.glLightfv(light, pname, params);
        });
    }

    /**
     * @author FPSFlux
     * @reason Light model setting
     */
    @Overwrite
    public static void glLightModel(int pname, FloatBuffer params) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "glLightModel(" + pname + ")", () -> {
            GL11.glLightModelfv(pname, params);
        });
    }

    /**
     * @author FPSFlux
     * @reason Color material mode
     */
    @Overwrite
    public static void colorMaterial(int face, int mode) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "colorMaterial(" + face + ", " + mode + ")", () -> {
            GL11.glColorMaterial(face, mode);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 33: FOG OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Set fog mode
     */
    @Overwrite
    public static void setFog(int mode) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "setFog(" + mode + ")", () -> {
            GL11.glFogi(GL11.GL_FOG_MODE, mode);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set fog density
     */
    @Overwrite
    public static void setFogDensity(float density) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "setFogDensity(" + density + ")", () -> {
            GL11.glFogf(GL11.GL_FOG_DENSITY, density);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set fog start distance
     */
    @Overwrite
    public static void setFogStart(float start) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "setFogStart(" + start + ")", () -> {
            GL11.glFogf(GL11.GL_FOG_START, start);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set fog end distance
     */
    @Overwrite
    public static void setFogEnd(float end) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "setFogEnd(" + end + ")", () -> {
            GL11.glFogf(GL11.GL_FOG_END, end);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set fog color
     */
    @Overwrite
    public static void glFog(int pname, FloatBuffer params) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "glFog(" + pname + ")", () -> {
            GL11.glFogfv(pname, params);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set fog integer parameter
     */
    @Overwrite
    public static void glFogi(int pname, int param) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "glFogi(" + pname + ", " + param + ")", () -> {
            GL11.glFogi(pname, param);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 34: LINE & POLYGON OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Enable line smoothing
     */
    @Overwrite
    public static void enableLineSmooth() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STATE_ENABLE, "enableLineSmooth()", () -> {
            fpsflux$onGLEnable(GL11.GL_LINE_SMOOTH);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
        });
    }

    /**
     * @author FPSFlux
     * @reason Disable line smoothing
     */
    @Overwrite
    public static void disableLineSmooth() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STATE_DISABLE, "disableLineSmooth()", () -> {
            fpsflux$onGLDisable(GL11.GL_LINE_SMOOTH);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set line width
     */
    @Overwrite
    public static void glLineWidth(float width) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "glLineWidth(" + width + ")", () -> {
            GL11.glLineWidth(width);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set polygon mode
     */
    @Overwrite
    public static void glPolygonMode(int face, int mode) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "glPolygonMode(" + face + ", " + mode + ")", () -> {
            GL11.glPolygonMode(face, mode);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 35: MATRIX OPERATIONS (Extended)
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Multiply by matrix
     */
    @Overwrite
    public static void multMatrix(FloatBuffer matrix) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.MATRIX, "multMatrix()", () -> {
            GL11.glMultMatrixf(matrix);
        });
    }

    /**
     * @author FPSFlux
     * @reason Get current matrix
     */
    @Overwrite
    public static void getFloat(int pname, FloatBuffer params) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "getFloat(" + pname + ")", () -> {
            GL11.glGetFloatv(pname, params);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 36: TEXTURE ENVIRONMENT
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Set texture environment mode
     */
    @Overwrite
    public static void glTexEnvi(int target, int pname, int param) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE, "glTexEnvi(" + target + ", " + pname + ", " + param + ")", () -> {
            GL11.glTexEnvi(target, pname, param);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set texture environment float parameter
     */
    @Overwrite
    public static void glTexEnvf(int target, int pname, float param) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE, "glTexEnvf(" + target + ", " + pname + ", " + param + ")", () -> {
            GL11.glTexEnvf(target, pname, param);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set texture environment with float buffer
     */
    @Overwrite
    public static void glTexEnv(int target, int pname, FloatBuffer params) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.TEXTURE, "glTexEnv(" + target + ", " + pname + ")", () -> {
            GL11.glTexEnvfv(target, pname, params);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 37: SHADER PROGRAM MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Cached shader program binding
     */
    @Overwrite
    public static void glUseProgram(int program) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SHADER, "glUseProgram(" + program + ")", () -> {
            fpsflux$markStateDirty(DIRTY_BINDINGS);
            if (fpsflux$glManager != null) {
                fpsflux$glManager.useProgram(program);
            } else {
                GL20.glUseProgram(program);
            }
        });
    }

    /**
     * @author FPSFlux
     * @reason Get uniform location
     */
    @Overwrite
    public static int glGetUniformLocation(int program, CharSequence name) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        return fpsflux$routeCallWithResult(CallType.SHADER, 
            "glGetUniformLocation(" + program + ", " + name + ")", () -> {
            return GL20.glGetUniformLocation(program, name);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set integer uniform
     */
    @Overwrite
    public static void glUniform1i(int location, int v0) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SHADER, "glUniform1i(" + location + ", " + v0 + ")", () -> {
            GL20.glUniform1i(location, v0);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set float uniform
     */
    @Overwrite
    public static void glUniform1f(int location, float v0) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SHADER, "glUniform1f(" + location + ", " + v0 + ")", () -> {
            GL20.glUniform1f(location, v0);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set vec2 uniform
     */
    @Overwrite
    public static void glUniform2f(int location, float v0, float v1) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SHADER, "glUniform2f(" + location + ")", () -> {
            GL20.glUniform2f(location, v0, v1);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set vec3 uniform
     */
    @Overwrite
    public static void glUniform3f(int location, float v0, float v1, float v2) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SHADER, "glUniform3f(" + location + ")", () -> {
            GL20.glUniform3f(location, v0, v1, v2);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set vec4 uniform
     */
    @Overwrite
    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SHADER, "glUniform4f(" + location + ")", () -> {
            GL20.glUniform4f(location, v0, v1, v2, v3);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set matrix4 uniform
     */
    @Overwrite
    public static void glUniformMatrix4(int location, boolean transpose, FloatBuffer matrix) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.SHADER, "glUniformMatrix4(" + location + ")", () -> {
            GL20.glUniformMatrix4fv(location, transpose, matrix);
        });
    }

    /**
     * @author FPSFlux
     * @reason Get attribute location
     */
    @Overwrite
    public static int glGetAttribLocation(int program, CharSequence name) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        return fpsflux$routeCallWithResult(CallType.SHADER, 
            "glGetAttribLocation(" + program + ", " + name + ")", () -> {
            return GL20.glGetAttribLocation(program, name);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 38: FRAMEBUFFER OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Cached framebuffer binding
     */
    @Overwrite
    public static void glBindFramebuffer(int target, int framebuffer) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.FRAMEBUFFER, "glBindFramebuffer(" + target + ", " + framebuffer + ")", () -> {
            fpsflux$markStateDirty(DIRTY_BINDINGS);
            if (fpsflux$glManager != null) {
                fpsflux$glManager.bindFramebuffer(target, framebuffer);
            } else {
                GL30.glBindFramebuffer(target, framebuffer);
            }
        });
    }

    /**
     * @author FPSFlux
     * @reason Delete framebuffer
     */
    @Overwrite
    public static void glDeleteFramebuffers(int framebuffer) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.FRAMEBUFFER, "glDeleteFramebuffers(" + framebuffer + ")", () -> {
            GL30.glDeleteFramebuffers(framebuffer);
        });
    }

    /**
     * @author FPSFlux
     * @reason Generate framebuffer
     */
    @Overwrite
    public static int glGenFramebuffers() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        return fpsflux$routeCallWithResult(CallType.FRAMEBUFFER, "glGenFramebuffers()", GL30::glGenFramebuffers);
    }

    /**
     * @author FPSFlux
     * @reason Check framebuffer status
     */
    @Overwrite
    public static int glCheckFramebufferStatus(int target) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        return fpsflux$routeCallWithResult(CallType.FRAMEBUFFER, 
            "glCheckFramebufferStatus(" + target + ")", () -> {
            return GL30.glCheckFramebufferStatus(target);
        });
    }

    /**
     * @author FPSFlux
     * @reason Attach texture to framebuffer
     */
    @Overwrite
    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.FRAMEBUFFER, 
            "glFramebufferTexture2D(" + target + ", " + attachment + ", " + texture + ")", () -> {
            GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 39: BUFFER OBJECT MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Cached buffer binding
     */
    @Overwrite
    public static void glBindBuffer(int target, int buffer) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.BUFFER, "glBindBuffer(" + target + ", " + buffer + ")", () -> {
            fpsflux$markStateDirty(DIRTY_BINDINGS);
            if (fpsflux$glManager != null) {
                fpsflux$glManager.bindBuffer(target, buffer);
            } else {
                GL15.glBindBuffer(target, buffer);
            }
        });
    }

    /**
     * @author FPSFlux
     * @reason Generate buffer
     */
    @Overwrite
    public static int glGenBuffers() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        return fpsflux$routeCallWithResult(CallType.BUFFER, "glGenBuffers()", GL15::glGenBuffers);
    }

    /**
     * @author FPSFlux
     * @reason Delete buffer
     */
    @Overwrite
    public static void glDeleteBuffers(int buffer) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.BUFFER, "glDeleteBuffers(" + buffer + ")", () -> {
            if (fpsflux$glManager != null) {
                fpsflux$glManager.deleteBuffer(buffer);
            }
            GL15.glDeleteBuffers(buffer);
        });
    }

    /**
     * @author FPSFlux
     * @reason Buffer data upload
     */
    @Overwrite
    public static void glBufferData(int target, ByteBuffer data, int usage) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.BUFFER, 
            "glBufferData(" + target + ", " + (data != null ? data.remaining() : 0) + " bytes)", () -> {
            GL15.glBufferData(target, data, usage);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 40: VERTEX ARRAY OBJECTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Cached VAO binding
     */
    @Overwrite
    public static void glBindVertexArray(int array) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.BUFFER, "glBindVertexArray(" + array + ")", () -> {
            fpsflux$markStateDirty(DIRTY_BINDINGS);
            if (fpsflux$glManager != null) {
                fpsflux$glManager.bindVertexArray(array);
            } else {
                GL30.glBindVertexArray(array);
            }
        });
    }

    /**
     * @author FPSFlux
     * @reason Generate VAO
     */
    @Overwrite
    public static int glGenVertexArrays() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        return fpsflux$routeCallWithResult(CallType.BUFFER, "glGenVertexArrays()", GL30::glGenVertexArrays);
    }

    /**
     * @author FPSFlux
     * @reason Delete VAO
     */
    @Overwrite
    public static void glDeleteVertexArrays(int array) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.BUFFER, "glDeleteVertexArrays(" + array + ")", () -> {
            GL30.glDeleteVertexArrays(array);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 41: VERTEX ATTRIBUTES
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Enable vertex attribute
     */
    @Overwrite
    public static void glEnableVertexAttribArray(int index) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.BUFFER, "glEnableVertexAttribArray(" + index + ")", () -> {
            GL20.glEnableVertexAttribArray(index);
        });
    }

    /**
     * @author FPSFlux
     * @reason Disable vertex attribute
     */
    @Overwrite
    public static void glDisableVertexAttribArray(int index) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.BUFFER, "glDisableVertexAttribArray(" + index + ")", () -> {
            GL20.glDisableVertexAttribArray(index);
        });
    }

    /**
     * @author FPSFlux
     * @reason Define vertex attribute pointer
     */
    @Overwrite
    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.BUFFER, 
            "glVertexAttribPointer(" + index + ", " + size + ", " + type + ")", () -> {
            GL20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 42: STENCIL OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Enable stencil test
     */
    @Overwrite
    public static void enableStencilTest() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STENCIL, "enableStencilTest()", () -> {
            fpsflux$onGLEnable(GL11.GL_STENCIL_TEST);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
        });
    }

    /**
     * @author FPSFlux
     * @reason Disable stencil test
     */
    @Overwrite
    public static void disableStencilTest() {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STENCIL, "disableStencilTest()", () -> {
            fpsflux$onGLDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set stencil function
     */
    @Overwrite
    public static void stencilFunc(int func, int ref, int mask) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STENCIL, 
            "stencilFunc(" + func + ", " + ref + ", " + mask + ")", () -> {
            fpsflux$markStateDirty(DIRTY_STENCIL);
            GL11.glStencilFunc(func, ref, mask);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set stencil mask
     */
    @Overwrite
    public static void stencilMask(int mask) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STENCIL, "stencilMask(" + mask + ")", () -> {
            fpsflux$markStateDirty(DIRTY_STENCIL);
            GL11.glStencilMask(mask);
        });
    }

    /**
     * @author FPSFlux
     * @reason Set stencil operation
     */
    @Overwrite
    public static void stencilOp(int sfail, int dpfail, int dppass) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STENCIL, 
            "stencilOp(" + sfail + ", " + dpfail + ", " + dppass + ")", () -> {
            fpsflux$markStateDirty(DIRTY_STENCIL);
            GL11.glStencilOp(sfail, dpfail, dppass);
        });
    }

    /**
     * @author FPSFlux
     * @reason Clear stencil buffer value
     */
    @Overwrite
    public static void clearStencil(int s) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.STENCIL, "clearStencil(" + s + ")", () -> {
            GL11.glClearStencil(s);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 43: READING & QUERY OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * @author FPSFlux
     * @reason Read pixels from framebuffer
     */
    @Overwrite
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, 
            "glReadPixels(" + x + ", " + y + ", " + width + "x" + height + ")", () -> {
            GL11.glReadPixels(x, y, width, height, format, type, pixels);
        });
    }

    /**
     * @author FPSFlux
     * @reason Get error state
     */
    @Overwrite
    public static int glGetError() {
        // Don't route this - it's used for error checking and should be fast
        return GL11.glGetError();
    }

    /**
     * @author FPSFlux
     * @reason Get string parameter
     */
    @Overwrite
    public static String glGetString(int name) {
        // Don't route this - it's informational and should be fast
        return GL11.glGetString(name);
    }

    /**
     * @author FPSFlux
     * @reason Get integer parameter
     */
    @Overwrite
    public static void glGetInteger(int pname, IntBuffer params) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fpsflux$routeCall(CallType.OTHER, "glGetInteger(" + pname + ")", () -> {
            GL11.glGetIntegerv(pname, params);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SECTION 44: STATE RESET & CACHE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════════════

    /**
     * Resets all locally cached state. Call when GL context changes.
     */
    @Unique
    public static void fpsflux$resetLocalCache() {
        fpsflux$cachedRed = 1.0f;
        fpsflux$cachedGreen = 1.0f;
        fpsflux$cachedBlue = 1.0f;
        fpsflux$cachedAlpha = 1.0f;
        fpsflux$colorStateDirty = true;
        fpsflux$cachedAlphaFunc = GL11.GL_ALWAYS;
        fpsflux$cachedAlphaRef = 0.0f;
        fpsflux$cachedCullMode = GL11.GL_BACK;
        fpsflux$cachedShadeModel = GL11.GL_SMOOTH;
        fpsflux$cachedMatrixMode = GL11.GL_MODELVIEW;
        
        // Also reset main capability cache
        fpsflux$capabilityCache.clear();
        fpsflux$stateDirtyFlags.set(0xFFFFFFFFL); // Mark all dirty
        
        fpsflux$logInfo("Local state cache reset");
    }

    /**
     * Invalidate texture bindings in cache
     */
    @Unique
    public static void fpsflux$invalidateTextureBindings() {
        fpsflux$markStateDirty(DIRTY_BINDINGS);
        if (fpsflux$glManager != null) {
            fpsflux$glManager.invalidateTextureBindings();
        }
    }

    /**
     * Force complete state resync from GL
     */
    @Unique
    public static void fpsflux$forceStateResync() {
        fpsflux$resetLocalCache();
        fpsflux$syncStateFromGL();
        fpsflux$logInfo("Forced complete state resync");
}

// ═══════════════════════════════════════════════════════════════════════════════════════
// SECTION 45: LAMBDA POOLING - ELIMINATES HOT PATH ALLOCATIONS
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Pre-allocated reusable executors for common GL operations.
 * Eliminates lambda allocation overhead in the hot render path.
 * 
 * <h3>Problem Solved:</h3>
 * Creating a new lambda for every GL call generates garbage:
 * <pre>
 *     () -> { GL11.glEnable(GL11.GL_BLEND); }  // NEW OBJECT EVERY CALL!
 * </pre>
 * 
 * <h3>Solution:</h3>
 * Pre-allocate static Runnable instances for common operations.
 */
@Unique
private static final class LambdaPool {
    
    // ─────────────────────────────────────────────────────────────────────────
    // Capability Enable/Disable - Most frequently called operations
    // ─────────────────────────────────────────────────────────────────────────
    
    static final Runnable ENABLE_BLEND = () -> GL11.glEnable(GL11.GL_BLEND);
    static final Runnable DISABLE_BLEND = () -> GL11.glDisable(GL11.GL_BLEND);
    
    static final Runnable ENABLE_DEPTH = () -> GL11.glEnable(GL11.GL_DEPTH_TEST);
    static final Runnable DISABLE_DEPTH = () -> GL11.glDisable(GL11.GL_DEPTH_TEST);
    
    static final Runnable ENABLE_CULL = () -> GL11.glEnable(GL11.GL_CULL_FACE);
    static final Runnable DISABLE_CULL = () -> GL11.glDisable(GL11.GL_CULL_FACE);
    
    static final Runnable ENABLE_ALPHA = () -> GL11.glEnable(GL11.GL_ALPHA_TEST);
    static final Runnable DISABLE_ALPHA = () -> GL11.glDisable(GL11.GL_ALPHA_TEST);
    
    static final Runnable ENABLE_TEXTURE2D = () -> GL11.glEnable(GL11.GL_TEXTURE_2D);
    static final Runnable DISABLE_TEXTURE2D = () -> GL11.glDisable(GL11.GL_TEXTURE_2D);
    
    static final Runnable ENABLE_LIGHTING = () -> GL11.glEnable(GL11.GL_LIGHTING);
    static final Runnable DISABLE_LIGHTING = () -> GL11.glDisable(GL11.GL_LIGHTING);
    
    static final Runnable ENABLE_FOG = () -> GL11.glEnable(GL11.GL_FOG);
    static final Runnable DISABLE_FOG = () -> GL11.glDisable(GL11.GL_FOG);
    
    static final Runnable ENABLE_COLOR_MATERIAL = () -> GL11.glEnable(GL11.GL_COLOR_MATERIAL);
    static final Runnable DISABLE_COLOR_MATERIAL = () -> GL11.glDisable(GL11.GL_COLOR_MATERIAL);
    
    static final Runnable ENABLE_NORMALIZE = () -> GL11.glEnable(GL11.GL_NORMALIZE);
    static final Runnable DISABLE_NORMALIZE = () -> GL11.glDisable(GL11.GL_NORMALIZE);
    
    static final Runnable ENABLE_POLYGON_OFFSET = () -> GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
    static final Runnable DISABLE_POLYGON_OFFSET = () -> GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    
    static final Runnable ENABLE_SCISSOR = () -> GL11.glEnable(GL11.GL_SCISSOR_TEST);
    static final Runnable DISABLE_SCISSOR = () -> GL11.glDisable(GL11.GL_SCISSOR_TEST);
    
    static final Runnable ENABLE_STENCIL = () -> GL11.glEnable(GL11.GL_STENCIL_TEST);
    static final Runnable DISABLE_STENCIL = () -> GL11.glDisable(GL11.GL_STENCIL_TEST);
    
    static final Runnable ENABLE_LINE_SMOOTH = () -> GL11.glEnable(GL11.GL_LINE_SMOOTH);
    static final Runnable DISABLE_LINE_SMOOTH = () -> GL11.glDisable(GL11.GL_LINE_SMOOTH);
    
    // ─────────────────────────────────────────────────────────────────────────
    // Matrix Operations - Called thousands of times per frame
    // ─────────────────────────────────────────────────────────────────────────
    
    static final Runnable PUSH_MATRIX = GL11::glPushMatrix;
    static final Runnable POP_MATRIX = GL11::glPopMatrix;
    static final Runnable LOAD_IDENTITY = GL11::glLoadIdentity;
    
    // ─────────────────────────────────────────────────────────────────────────
    // Color Operations
    // ─────────────────────────────────────────────────────────────────────────
    
    static final Runnable RESET_COLOR = () -> GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    
    // ─────────────────────────────────────────────────────────────────────────
    // Lookup map for dynamic capability enable/disable
    // ─────────────────────────────────────────────────────────────────────────
    
    private static final Map<Integer, Runnable> ENABLE_MAP = new HashMap<>(32);
    private static final Map<Integer, Runnable> DISABLE_MAP = new HashMap<>(32);
    
    static {
        // Populate enable map
        ENABLE_MAP.put(GL11.GL_BLEND, ENABLE_BLEND);
        ENABLE_MAP.put(GL11.GL_DEPTH_TEST, ENABLE_DEPTH);
        ENABLE_MAP.put(GL11.GL_CULL_FACE, ENABLE_CULL);
        ENABLE_MAP.put(GL11.GL_ALPHA_TEST, ENABLE_ALPHA);
        ENABLE_MAP.put(GL11.GL_TEXTURE_2D, ENABLE_TEXTURE2D);
        ENABLE_MAP.put(GL11.GL_LIGHTING, ENABLE_LIGHTING);
        ENABLE_MAP.put(GL11.GL_FOG, ENABLE_FOG);
        ENABLE_MAP.put(GL11.GL_COLOR_MATERIAL, ENABLE_COLOR_MATERIAL);
        ENABLE_MAP.put(GL11.GL_NORMALIZE, ENABLE_NORMALIZE);
        ENABLE_MAP.put(GL11.GL_POLYGON_OFFSET_FILL, ENABLE_POLYGON_OFFSET);
        ENABLE_MAP.put(GL11.GL_SCISSOR_TEST, ENABLE_SCISSOR);
        ENABLE_MAP.put(GL11.GL_STENCIL_TEST, ENABLE_STENCIL);
        ENABLE_MAP.put(GL11.GL_LINE_SMOOTH, ENABLE_LINE_SMOOTH);
        
        // Populate disable map
        DISABLE_MAP.put(GL11.GL_BLEND, DISABLE_BLEND);
        DISABLE_MAP.put(GL11.GL_DEPTH_TEST, DISABLE_DEPTH);
        DISABLE_MAP.put(GL11.GL_CULL_FACE, DISABLE_CULL);
        DISABLE_MAP.put(GL11.GL_ALPHA_TEST, DISABLE_ALPHA);
        DISABLE_MAP.put(GL11.GL_TEXTURE_2D, DISABLE_TEXTURE2D);
        DISABLE_MAP.put(GL11.GL_LIGHTING, DISABLE_LIGHTING);
        DISABLE_MAP.put(GL11.GL_FOG, DISABLE_FOG);
        DISABLE_MAP.put(GL11.GL_COLOR_MATERIAL, DISABLE_COLOR_MATERIAL);
        DISABLE_MAP.put(GL11.GL_NORMALIZE, DISABLE_NORMALIZE);
        DISABLE_MAP.put(GL11.GL_POLYGON_OFFSET_FILL, DISABLE_POLYGON_OFFSET);
        DISABLE_MAP.put(GL11.GL_SCISSOR_TEST, DISABLE_SCISSOR);
        DISABLE_MAP.put(GL11.GL_STENCIL_TEST, DISABLE_STENCIL);
        DISABLE_MAP.put(GL11.GL_LINE_SMOOTH, DISABLE_LINE_SMOOTH);
    }
    
    /**
     * Get pooled enable runnable or create new one
     */
    static Runnable getEnable(int cap) {
        Runnable pooled = ENABLE_MAP.get(cap);
        return pooled != null ? pooled : () -> GL11.glEnable(cap);
    }
    
    /**
     * Get pooled disable runnable or create new one
     */
    static Runnable getDisable(int cap) {
        Runnable pooled = DISABLE_MAP.get(cap);
        return pooled != null ? pooled : () -> GL11.glDisable(cap);
    }
}


// ═══════════════════════════════════════════════════════════════════════════════════════
// SECTION 46: REUSABLE PARAMETER EXECUTORS - ZERO-ALLOC PARAMETERIZED CALLS
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Thread-local reusable executors for parameterized GL calls.
 * Avoids creating new lambdas that capture parameters.
 * 
 * <h3>Usage Pattern:</h3>
 * <pre>
 *     // Instead of: fpsflux$routeCall(type, desc, () -> GL11.glBlendFunc(src, dst));
 *     // Use:        fpsflux$routeCall(type, desc, ReusableExecutors.blendFunc(src, dst));
 * </pre>
 */
@Unique
private static final class ReusableExecutors {
    
    // ─────────────────────────────────────────────────────────────────────────
    // Thread-local instances to avoid contention
    // ─────────────────────────────────────────────────────────────────────────
    
    private static final ThreadLocal<BlendFuncExecutor> BLEND_FUNC = 
        ThreadLocal.withInitial(BlendFuncExecutor::new);
    
    private static final ThreadLocal<BlendFuncSeparateExecutor> BLEND_FUNC_SEPARATE = 
        ThreadLocal.withInitial(BlendFuncSeparateExecutor::new);
    
    private static final ThreadLocal<DepthFuncExecutor> DEPTH_FUNC = 
        ThreadLocal.withInitial(DepthFuncExecutor::new);
    
    private static final ThreadLocal<DepthMaskExecutor> DEPTH_MASK = 
        ThreadLocal.withInitial(DepthMaskExecutor::new);
    
    private static final ThreadLocal<CullFaceExecutor> CULL_FACE = 
        ThreadLocal.withInitial(CullFaceExecutor::new);
    
    private static final ThreadLocal<ColorExecutor> COLOR = 
        ThreadLocal.withInitial(ColorExecutor::new);
    
    private static final ThreadLocal<ViewportExecutor> VIEWPORT = 
        ThreadLocal.withInitial(ViewportExecutor::new);
    
    private static final ThreadLocal<ScissorExecutor> SCISSOR = 
        ThreadLocal.withInitial(ScissorExecutor::new);
    
    private static final ThreadLocal<BindTextureExecutor> BIND_TEXTURE = 
        ThreadLocal.withInitial(BindTextureExecutor::new);
    
    private static final ThreadLocal<TranslateExecutor> TRANSLATE_F = 
        ThreadLocal.withInitial(TranslateExecutor::new);
    
    private static final ThreadLocal<TranslateExecutorD> TRANSLATE_D = 
        ThreadLocal.withInitial(TranslateExecutorD::new);
    
    private static final ThreadLocal<ScaleExecutor> SCALE_F = 
        ThreadLocal.withInitial(ScaleExecutor::new);
    
    private static final ThreadLocal<ScaleExecutorD> SCALE_D = 
        ThreadLocal.withInitial(ScaleExecutorD::new);
    
    private static final ThreadLocal<RotateExecutor> ROTATE = 
        ThreadLocal.withInitial(RotateExecutor::new);
    
    private static final ThreadLocal<MatrixModeExecutor> MATRIX_MODE = 
        ThreadLocal.withInitial(MatrixModeExecutor::new);
    
    private static final ThreadLocal<ClearExecutor> CLEAR = 
        ThreadLocal.withInitial(ClearExecutor::new);
    
    private static final ThreadLocal<ClearColorExecutor> CLEAR_COLOR = 
        ThreadLocal.withInitial(ClearColorExecutor::new);
    
    // ─────────────────────────────────────────────────────────────────────────
    // Executor Classes
    // ─────────────────────────────────────────────────────────────────────────
    
    static final class BlendFuncExecutor implements Runnable {
        int src, dst;
        BlendFuncExecutor set(int src, int dst) { this.src = src; this.dst = dst; return this; }
        @Override public void run() { GL11.glBlendFunc(src, dst); }
    }
    
    static final class BlendFuncSeparateExecutor implements Runnable {
        int srcRGB, dstRGB, srcA, dstA;
        BlendFuncSeparateExecutor set(int srcRGB, int dstRGB, int srcA, int dstA) {
            this.srcRGB = srcRGB; this.dstRGB = dstRGB; this.srcA = srcA; this.dstA = dstA;
            return this;
        }
        @Override public void run() { 
            if (UniversalCapabilities.GL.GL14) {
                GL14.glBlendFuncSeparate(srcRGB, dstRGB, srcA, dstA);
            } else {
                GL11.glBlendFunc(srcRGB, dstRGB);
            }
        }
    }
    
    static final class DepthFuncExecutor implements Runnable {
        int func;
        DepthFuncExecutor set(int func) { this.func = func; return this; }
        @Override public void run() { GL11.glDepthFunc(func); }
    }
    
    static final class DepthMaskExecutor implements Runnable {
        boolean flag;
        DepthMaskExecutor set(boolean flag) { this.flag = flag; return this; }
        @Override public void run() { GL11.glDepthMask(flag); }
    }
    
    static final class CullFaceExecutor implements Runnable {
        int mode;
        CullFaceExecutor set(int mode) { this.mode = mode; return this; }
        @Override public void run() { GL11.glCullFace(mode); }
    }
    
    static final class ColorExecutor implements Runnable {
        float r, g, b, a;
        ColorExecutor set(float r, float g, float b, float a) {
            this.r = r; this.g = g; this.b = b; this.a = a;
            return this;
        }
        @Override public void run() { GL11.glColor4f(r, g, b, a); }
    }
    
    static final class ViewportExecutor implements Runnable {
        int x, y, w, h;
        ViewportExecutor set(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            return this;
        }
        @Override public void run() { GL11.glViewport(x, y, w, h); }
    }
    
    static final class ScissorExecutor implements Runnable {
        int x, y, w, h;
        ScissorExecutor set(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            return this;
        }
        @Override public void run() { GL11.glScissor(x, y, w, h); }
    }
    
    static final class BindTextureExecutor implements Runnable {
        int texture;
        BindTextureExecutor set(int texture) { this.texture = texture; return this; }
        @Override public void run() { GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); }
    }
    
    static final class TranslateExecutor implements Runnable {
        float x, y, z;
        TranslateExecutor set(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
            return this;
        }
        @Override public void run() { GL11.glTranslatef(x, y, z); }
    }
    
    static final class TranslateExecutorD implements Runnable {
        double x, y, z;
        TranslateExecutorD set(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z;
            return this;
        }
        @Override public void run() { GL11.glTranslated(x, y, z); }
    }
    
    static final class ScaleExecutor implements Runnable {
        float x, y, z;
        ScaleExecutor set(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
            return this;
        }
        @Override public void run() { GL11.glScalef(x, y, z); }
    }
    
    static final class ScaleExecutorD implements Runnable {
        double x, y, z;
        ScaleExecutorD set(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z;
            return this;
        }
        @Override public void run() { GL11.glScaled(x, y, z); }
    }
    
    static final class RotateExecutor implements Runnable {
        float angle, x, y, z;
        RotateExecutor set(float angle, float x, float y, float z) {
            this.angle = angle; this.x = x; this.y = y; this.z = z;
            return this;
        }
        @Override public void run() { GL11.glRotatef(angle, x, y, z); }
    }
    
    static final class MatrixModeExecutor implements Runnable {
        int mode;
        MatrixModeExecutor set(int mode) { this.mode = mode; return this; }
        @Override public void run() { GL11.glMatrixMode(mode); }
    }
    
    static final class ClearExecutor implements Runnable {
        int mask;
        ClearExecutor set(int mask) { this.mask = mask; return this; }
        @Override public void run() { GL11.glClear(mask); }
    }
    
    static final class ClearColorExecutor implements Runnable {
        float r, g, b, a;
        ClearColorExecutor set(float r, float g, float b, float a) {
            this.r = r; this.g = g; this.b = b; this.a = a;
            return this;
        }
        @Override public void run() { GL11.glClearColor(r, g, b, a); }
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Static accessor methods
    // ─────────────────────────────────────────────────────────────────────────
    
    static Runnable blendFunc(int src, int dst) {
        return BLEND_FUNC.get().set(src, dst);
    }
    
    static Runnable blendFuncSeparate(int srcRGB, int dstRGB, int srcA, int dstA) {
        return BLEND_FUNC_SEPARATE.get().set(srcRGB, dstRGB, srcA, dstA);
    }
    
    static Runnable depthFunc(int func) {
        return DEPTH_FUNC.get().set(func);
    }
    
    static Runnable depthMask(boolean flag) {
        return DEPTH_MASK.get().set(flag);
    }
    
    static Runnable cullFace(int mode) {
        return CULL_FACE.get().set(mode);
    }
    
    static Runnable color(float r, float g, float b, float a) {
        return COLOR.get().set(r, g, b, a);
    }
    
    static Runnable viewport(int x, int y, int w, int h) {
        return VIEWPORT.get().set(x, y, w, h);
    }
    
    static Runnable scissor(int x, int y, int w, int h) {
        return SCISSOR.get().set(x, y, w, h);
    }
    
    static Runnable bindTexture(int texture) {
        return BIND_TEXTURE.get().set(texture);
    }
    
    static Runnable translatef(float x, float y, float z) {
        return TRANSLATE_F.get().set(x, y, z);
    }
    
    static Runnable translated(double x, double y, double z) {
        return TRANSLATE_D.get().set(x, y, z);
    }
    
    static Runnable scalef(float x, float y, float z) {
        return SCALE_F.get().set(x, y, z);
    }
    
    static Runnable scaled(double x, double y, double z) {
        return SCALE_D.get().set(x, y, z);
    }
    
    static Runnable rotate(float angle, float x, float y, float z) {
        return ROTATE.get().set(angle, x, y, z);
    }
    
    static Runnable matrixMode(int mode) {
        return MATRIX_MODE.get().set(mode);
    }
    
    static Runnable clear(int mask) {
        return CLEAR.get().set(mask);
    }
    
    static Runnable clearColor(float r, float g, float b, float a) {
        return CLEAR_COLOR.get().set(r, g, b, a);
    }
}


// ═══════════════════════════════════════════════════════════════════════════════════════
// SECTION 47: SAFE PATH REGISTRY - EXTERNAL MOD ROUTING SYSTEM
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * The Safe Path Registry intercepts and routes external modifications to GlStateManager.
 * 
 * <h2>Problem:</h2>
 * When multiple mods (Sodium, OptiFine, Iris, etc.) all try to @Overwrite the same
 * GlStateManager methods, crashes occur due to Mixin conflicts.
 * 
 * <h2>Solution:</h2>
 * Instead of fighting over who "owns" each method, we:
 * <ol>
 *     <li>Register "safe paths" - validated, tested execution paths for each GL operation</li>
 *     <li>Allow external mods to register their own handlers via API</li>
 *     <li>Route all calls through a priority-based handler chain</li>
 *     <li>Catch failures and fall back to the next handler in the chain</li>
 * </ol>
 * 
 * <h2>Architecture:</h2>
 * <pre>
 *     External Call (Sodium, Iris, etc.)
 *              │
 *              ▼
 *     ┌────────────────────────┐
 *     │   SafePathRegistry     │
 *     │                        │
 *     │  Priority Chain:       │
 *     │   1. Registered Mod    │ ◄── If Sodium registered, try first
 *     │   2. FPSFlux Handler   │ ◄── Our implementation
 *     │   3. Vanilla Fallback  │ ◄── Direct GL call
 *     │   4. Error Handler     │ ◄── Log and recover
 *     └────────────────────────┘
 *              │
 *              ▼
 *         GL Driver
 * </pre>
 */
@Unique
public static final class SafePathRegistry {
    
    // ─────────────────────────────────────────────────────────────────────────
    // Handler Priority Constants
    // ─────────────────────────────────────────────────────────────────────────
    
    public static final int PRIORITY_HIGHEST = 1000;      // Shader mods (Iris/Oculus)
    public static final int PRIORITY_HIGH = 750;          // Render mods (Sodium/Rubidium)
    public static final int PRIORITY_NORMAL = 500;        // FPSFlux default
    public static final int PRIORITY_LOW = 250;           // Compatibility layers
    public static final int PRIORITY_FALLBACK = 0;        // Vanilla GL calls
    
    // ─────────────────────────────────────────────────────────────────────────
    // Operation Types
    // ─────────────────────────────────────────────────────────────────────────
    
    public enum Operation {
        // State enable/disable
        ENABLE_CAPABILITY,
        DISABLE_CAPABILITY,
        
        // Blend
        BLEND_FUNC,
        BLEND_FUNC_SEPARATE,
        BLEND_EQUATION,
        
        // Depth
        DEPTH_FUNC,
        DEPTH_MASK,
        CLEAR_DEPTH,
        
        // Stencil
        STENCIL_FUNC,
        STENCIL_MASK,
        STENCIL_OP,
        CLEAR_STENCIL,
        
        // Cull
        CULL_FACE,
        
        // Color
        COLOR,
        COLOR_MASK,
        CLEAR_COLOR,
        
        // Viewport/Scissor
        VIEWPORT,
        SCISSOR,
        
        // Texture
        BIND_TEXTURE,
        ACTIVE_TEXTURE,
        TEX_PARAMETER,
        
        // Matrix
        PUSH_MATRIX,
        POP_MATRIX,
        LOAD_IDENTITY,
        TRANSLATE,
        ROTATE,
        SCALE,
        MATRIX_MODE,
        MULT_MATRIX,
        ORTHO,
        
        // Shader
        USE_PROGRAM,
        UNIFORM,
        GET_UNIFORM_LOCATION,
        
        // Buffer
        BIND_BUFFER,
        BIND_VAO,
        BUFFER_DATA,
        VERTEX_ATTRIB_POINTER,
        
        // Framebuffer
        BIND_FRAMEBUFFER,
        FRAMEBUFFER_TEXTURE,
        
        // Draw
        CLEAR,
        DRAW_ARRAYS,
        DRAW_ELEMENTS,
        
        // Other
        OTHER
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Handler Interface
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Handler interface for safe path execution
     */
    @FunctionalInterface
    public interface SafePathHandler {
        /**
         * Execute the operation with the given context
         * @param context The execution context containing parameters
         * @return true if handled successfully, false to try next handler
         */
        boolean execute(ExecutionContext context);
    }
    
    /**
     * Execution context passed to handlers
     */
    public static final class ExecutionContext {
        public final Operation operation;
        public final Object[] parameters;
        public final String callerMod;
        public final long timestamp;
        
        // Result storage
        private Object result;
        private boolean hasResult;
        private Throwable error;
        
        ExecutionContext(Operation op, Object[] params, String caller) {
            this.operation = op;
            this.parameters = params;
            this.callerMod = caller;
            this.timestamp = System.nanoTime();
        }
        
        public void setResult(Object result) {
            this.result = result;
            this.hasResult = true;
        }
        
        @SuppressWarnings("unchecked")
        public <T> T getResult(T defaultValue) {
            return hasResult ? (T) result : defaultValue;
        }
        
        public void setError(Throwable t) {
            this.error = t;
        }
        
        public Throwable getError() {
            return error;
        }
        
        public int getInt(int index) {
            return parameters.length > index ? (Integer) parameters[index] : 0;
        }
        
        public float getFloat(int index) {
            return parameters.length > index ? (Float) parameters[index] : 0f;
        }
        
        public double getDouble(int index) {
            return parameters.length > index ? (Double) parameters[index] : 0.0;
        }
        
        public boolean getBoolean(int index) {
            return parameters.length > index && (Boolean) parameters[index];
        }
        
        @SuppressWarnings("unchecked")
        public <T> T getParam(int index) {
            return parameters.length > index ? (T) parameters[index] : null;
        }
    }
    
    /**
     * Registered handler with priority
     */
    private static final class RegisteredHandler implements Comparable<RegisteredHandler> {
        final String modId;
        final int priority;
        final SafePathHandler handler;
        final Set<Operation> operations;
        
        RegisteredHandler(String modId, int priority, SafePathHandler handler, Set<Operation> ops) {
            this.modId = modId;
            this.priority = priority;
            this.handler = handler;
            this.operations = ops;
        }
        
        @Override
        public int compareTo(RegisteredHandler other) {
            return Integer.compare(other.priority, this.priority); // Higher priority first
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Registry State
    // ─────────────────────────────────────────────────────────────────────────
    
    /** All registered handlers, sorted by priority */
    private static final CopyOnWriteArrayList<RegisteredHandler> handlers = new CopyOnWriteArrayList<>();
    
    /** Quick lookup: operation -> sorted list of handlers */
    private static final EnumMap<Operation, List<RegisteredHandler>> operationHandlers = 
        new EnumMap<>(Operation.class);
    
    /** Lock for registration */
    private static final ReentrantReadWriteLock registryLock = new ReentrantReadWriteLock();
    
    /** Handler call statistics */
    private static final EnumMap<Operation, LongAdder> handlerCallCounts = new EnumMap<>(Operation.class);
    private static final EnumMap<Operation, LongAdder> handlerFallbackCounts = new EnumMap<>(Operation.class);
    
    /** Initialize operation maps */
    static {
        for (Operation op : Operation.values()) {
            operationHandlers.put(op, new CopyOnWriteArrayList<>());
            handlerCallCounts.put(op, new LongAdder());
            handlerFallbackCounts.put(op, new LongAdder());
        }
        
        // Register FPSFlux default handlers
        registerDefaultHandlers();
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Register a handler for specific operations.
     * Called by external mods to integrate with FPSFlux.
     * 
     * @param modId      Your mod's identifier (e.g., "sodium")
     * @param priority   Handler priority (use PRIORITY_* constants)
     * @param handler    Your handler implementation
     * @param operations Operations your handler can process
     */
    public static void registerHandler(String modId, int priority, SafePathHandler handler, 
                                        Operation... operations) {
        registryLock.writeLock().lock();
        try {
            Set<Operation> opSet = EnumSet.noneOf(Operation.class);
            Collections.addAll(opSet, operations);
            
            RegisteredHandler reg = new RegisteredHandler(modId, priority, handler, opSet);
            handlers.add(reg);
            Collections.sort(handlers);
            
            // Update per-operation lists
            for (Operation op : operations) {
                List<RegisteredHandler> list = operationHandlers.get(op);
                list.add(reg);
                Collections.sort(list);
            }
            
            fpsflux$logInfo("Registered safe path handler: " + modId + 
                           " (priority=" + priority + ", operations=" + opSet.size() + ")");
            
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    /**
     * Register a handler for ALL operations (catch-all).
     */
    public static void registerGlobalHandler(String modId, int priority, SafePathHandler handler) {
        registerHandler(modId, priority, handler, Operation.values());
    }
    
    /**
     * Unregister all handlers for a mod.
     */
    public static void unregisterMod(String modId) {
        registryLock.writeLock().lock();
        try {
            handlers.removeIf(h -> h.modId.equals(modId));
            for (List<RegisteredHandler> list : operationHandlers.values()) {
                list.removeIf(h -> h.modId.equals(modId));
            }
            fpsflux$logInfo("Unregistered all handlers for: " + modId);
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Internal Execution
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Execute an operation through the safe path chain.
     * Tries each registered handler in priority order until one succeeds.
     */
    static boolean execute(Operation operation, Object... params) {
        return execute(operation, "unknown", params);
    }
    
    static boolean execute(Operation operation, String caller, Object... params) {
        handlerCallCounts.get(operation).increment();
        
        ExecutionContext context = new ExecutionContext(operation, params, caller);
        List<RegisteredHandler> handlersForOp = operationHandlers.get(operation);
        
        int attemptCount = 0;
        for (RegisteredHandler handler : handlersForOp) {
            attemptCount++;
            try {
                if (handler.handler.execute(context)) {
                    // Success!
                    if (attemptCount > 1) {
                        handlerFallbackCounts.get(operation).increment();
                    }
                    return true;
                }
            } catch (Throwable t) {
                context.setError(t);
                fpsflux$logError("Handler " + handler.modId + " failed for " + operation, t);
                // Continue to next handler
            }
        }
        
        // All handlers failed - log and return false
        fpsflux$logError("All handlers failed for " + operation + ", params=" + 
                        Arrays.toString(params), context.getError());
        return false;
    }
    
    /**
     * Execute and return a result
     */
    @SuppressWarnings("unchecked")
    static <T> T executeWithResult(Operation operation, T defaultValue, Object... params) {
        return executeWithResult(operation, "unknown", defaultValue, params);
    }
    
    @SuppressWarnings("unchecked")
    static <T> T executeWithResult(Operation operation, String caller, T defaultValue, Object... params) {
        handlerCallCounts.get(operation).increment();
        
        ExecutionContext context = new ExecutionContext(operation, params, caller);
        List<RegisteredHandler> handlersForOp = operationHandlers.get(operation);
        
        for (RegisteredHandler handler : handlersForOp) {
            try {
                if (handler.handler.execute(context)) {
                    return context.getResult(defaultValue);
                }
            } catch (Throwable t) {
                context.setError(t);
                // Continue to next handler
            }
        }
        
        return defaultValue;
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Default Handler Registration
    // ─────────────────────────────────────────────────────────────────────────
    
    private static void registerDefaultHandlers() {
        
        // FPSFlux main handler - PRIORITY_NORMAL
        registerHandler("fpsflux", PRIORITY_NORMAL, ctx -> {
            return handleFPSFluxOperation(ctx);
        }, Operation.values());
        
        // Vanilla fallback handler - PRIORITY_FALLBACK
        registerHandler("vanilla_fallback", PRIORITY_FALLBACK, ctx -> {
            return handleVanillaFallback(ctx);
        }, Operation.values());
    }
    
    /**
     * FPSFlux's main operation handler
     */
    private static boolean handleFPSFluxOperation(ExecutionContext ctx) {
        try {
            switch (ctx.operation) {
                case ENABLE_CAPABILITY -> {
                    int cap = ctx.getInt(0);
                    fpsflux$onGLEnable(cap);
                    GL11.glEnable(cap);
                    return true;
                }
                case DISABLE_CAPABILITY -> {
                    int cap = ctx.getInt(0);
                    fpsflux$onGLDisable(cap);
                    GL11.glDisable(cap);
                    return true;
                }
                case BLEND_FUNC -> {
                    fpsflux$markStateDirty(DIRTY_BLEND);
                    GL11.glBlendFunc(ctx.getInt(0), ctx.getInt(1));
                    return true;
                }
                case BLEND_FUNC_SEPARATE -> {
                    fpsflux$markStateDirty(DIRTY_BLEND);
                    if (UniversalCapabilities.GL.GL14) {
                        GL14.glBlendFuncSeparate(ctx.getInt(0), ctx.getInt(1), 
                                                 ctx.getInt(2), ctx.getInt(3));
                    } else {
                        GL11.glBlendFunc(ctx.getInt(0), ctx.getInt(1));
                    }
                    return true;
                }
                case DEPTH_FUNC -> {
                    fpsflux$markStateDirty(DIRTY_DEPTH);
                    GL11.glDepthFunc(ctx.getInt(0));
                    return true;
                }
                case DEPTH_MASK -> {
                    fpsflux$markStateDirty(DIRTY_DEPTH);
                    GL11.glDepthMask(ctx.getBoolean(0));
                    return true;
                }
                case CULL_FACE -> {
                    fpsflux$markStateDirty(DIRTY_CULL);
                    GL11.glCullFace(ctx.getInt(0));
                    return true;
                }
                case COLOR -> {
                    fpsflux$markStateDirty(DIRTY_COLOR);
                    GL11.glColor4f(ctx.getFloat(0), ctx.getFloat(1), 
                                   ctx.getFloat(2), ctx.getFloat(3));
                    return true;
                }
                case VIEWPORT -> {
                    fpsflux$markStateDirty(DIRTY_VIEWPORT);
                    GL11.glViewport(ctx.getInt(0), ctx.getInt(1), 
                                    ctx.getInt(2), ctx.getInt(3));
                    return true;
                }
                case BIND_TEXTURE -> {
                    fpsflux$markStateDirty(DIRTY_BINDINGS);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, ctx.getInt(0));
                    return true;
                }
                case PUSH_MATRIX -> {
                    GL11.glPushMatrix();
                    return true;
                }
                case POP_MATRIX -> {
                    GL11.glPopMatrix();
                    return true;
                }
                case LOAD_IDENTITY -> {
                    GL11.glLoadIdentity();
                    return true;
                }
                case TRANSLATE -> {
                    if (ctx.parameters[0] instanceof Float) {
                        GL11.glTranslatef(ctx.getFloat(0), ctx.getFloat(1), ctx.getFloat(2));
                    } else {
                        GL11.glTranslated(ctx.getDouble(0), ctx.getDouble(1), ctx.getDouble(2));
                    }
                    return true;
                }
                case SCALE -> {
                    if (ctx.parameters[0] instanceof Float) {
                        GL11.glScalef(ctx.getFloat(0), ctx.getFloat(1), ctx.getFloat(2));
                    } else {
                        GL11.glScaled(ctx.getDouble(0), ctx.getDouble(1), ctx.getDouble(2));
                    }
                    return true;
                }
                case ROTATE -> {
                    GL11.glRotatef(ctx.getFloat(0), ctx.getFloat(1), 
                                   ctx.getFloat(2), ctx.getFloat(3));
                    return true;
                }
                case MATRIX_MODE -> {
                    GL11.glMatrixMode(ctx.getInt(0));
                    return true;
                }
                case CLEAR -> {
                    GL11.glClear(ctx.getInt(0));
                    return true;
                }
                case CLEAR_COLOR -> {
                    GL11.glClearColor(ctx.getFloat(0), ctx.getFloat(1), 
                                      ctx.getFloat(2), ctx.getFloat(3));
                    return true;
                }
                case USE_PROGRAM -> {
                    fpsflux$markStateDirty(DIRTY_BINDINGS);
                    GL20.glUseProgram(ctx.getInt(0));
                    return true;
                }
                case BIND_FRAMEBUFFER -> {
                    fpsflux$markStateDirty(DIRTY_BINDINGS);
                    GL30.glBindFramebuffer(ctx.getInt(0), ctx.getInt(1));
                    return true;
                }
                case BIND_BUFFER -> {
                    fpsflux$markStateDirty(DIRTY_BINDINGS);
                    GL15.glBindBuffer(ctx.getInt(0), ctx.getInt(1));
                    return true;
                }
                case BIND_VAO -> {
                    fpsflux$markStateDirty(DIRTY_BINDINGS);
                    GL30.glBindVertexArray(ctx.getInt(0));
                    return true;
                }
                default -> {
                    // Return false to let next handler try
                    return false;
                }
            }
        } catch (Throwable t) {
            ctx.setError(t);
            return false;
        }
    }
    
    /**
     * Vanilla fallback - direct GL calls with no caching
     */
    private static boolean handleVanillaFallback(ExecutionContext ctx) {
        try {
            switch (ctx.operation) {
                case ENABLE_CAPABILITY -> GL11.glEnable(ctx.getInt(0));
                case DISABLE_CAPABILITY -> GL11.glDisable(ctx.getInt(0));
                case BLEND_FUNC -> GL11.glBlendFunc(ctx.getInt(0), ctx.getInt(1));
                case DEPTH_FUNC -> GL11.glDepthFunc(ctx.getInt(0));
                case DEPTH_MASK -> GL11.glDepthMask(ctx.getBoolean(0));
                case COLOR -> GL11.glColor4f(ctx.getFloat(0), ctx.getFloat(1), 
                                             ctx.getFloat(2), ctx.getFloat(3));
                case VIEWPORT -> GL11.glViewport(ctx.getInt(0), ctx.getInt(1), 
                                                 ctx.getInt(2), ctx.getInt(3));
                case CLEAR -> GL11.glClear(ctx.getInt(0));
                case PUSH_MATRIX -> GL11.glPushMatrix();
                case POP_MATRIX -> GL11.glPopMatrix();
                case LOAD_IDENTITY -> GL11.glLoadIdentity();
                default -> {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            ctx.setError(t);
            return false;
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Statistics
    // ─────────────────────────────────────────────────────────────────────────
    
    public static String getStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════════╗\n");
        sb.append("║           Safe Path Registry Statistics                          ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Registered Handlers: ").append(String.format("%5d", handlers.size())).append("                                   ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
        
        for (Operation op : Operation.values()) {
            long calls = handlerCallCounts.get(op).sum();
            long fallbacks = handlerFallbackCounts.get(op).sum();
            if (calls > 0) {
                sb.append("║ ").append(String.format("%-25s", op.name()));
                sb.append(String.format("Calls: %8d  Fallbacks: %5d", calls, fallbacks));
                sb.append(" ║\n");
            }
        }
        
        sb.append("╚══════════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }
}


// ═══════════════════════════════════════════════════════════════════════════════════════
// SECTION 48: EXTERNAL MOD INTERCEPTION - CONFLICT PREVENTION LAYER
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * This section provides infrastructure for intercepting external mod calls
 * BEFORE they can cause @Overwrite conflicts.
 * 
 * <h2>Strategy:</h2>
 * We use early-loading static initializers and class transformers to:
 * <ol>
 *     <li>Detect which mods are present at startup</li>
 *     <li>Register their known patterns with SafePathRegistry</li>
 *     <li>Redirect their internal GL calls through our safe paths</li>
 * </ol>
 */
@Unique
public static final class ExternalModInterceptor {
    
    /** Known mod patterns and their detection classes */
    private static final Map<String, ModInterceptConfig> KNOWN_MODS = new LinkedHashMap<>();
    
    /** Interceptor installed flag */
    private static final AtomicBoolean interceptorInstalled = new AtomicBoolean(false);
    
    static {
        // Register known mod configurations
        registerKnownMod("optifine", "optifine.OptiFineClassTransformer", 
            new String[]{"CustomColors", "Shaders", "Reflections"});
        
        registerKnownMod("sodium", "me.jellysquid.mods.sodium.client.SodiumClientMod",
            new String[]{"ChunkRenderer", "BlockRenderer", "TerrainRenderer"});
        
        registerKnownMod("rubidium", "me.jellysquid.mods.rubidium.RubidiumMod",
            new String[]{"ChunkRenderer", "BlockRenderer"});
        
        registerKnownMod("embeddium", "org.embeddedt.embeddium.impl.Embeddium",
            new String[]{"ChunkRenderer", "BlockRenderer"});
        
        registerKnownMod("iris", "net.coderbot.iris.Iris",
            new String[]{"ShaderPipeline", "ShadowRenderer", "DeferredRenderer"});
        
        registerKnownMod("oculus", "net.coderbot.iris.Iris",  // Oculus uses Iris classes
            new String[]{"ShaderPipeline", "ShadowRenderer"});
        
        registerKnownMod("canvas", "grondag.canvas.CanvasMod",
            new String[]{"MaterialShader", "TerrainPipeline"});
    }
    
    /**
     * Configuration for intercepting a known mod
     */
    private static final class ModInterceptConfig {
        final String modId;
        final String detectionClass;
        final String[] sensitiveClasses;
        boolean detected = false;
        boolean handlerRegistered = false;
        
        ModInterceptConfig(String modId, String detectionClass, String[] sensitiveClasses) {
            this.modId = modId;
            this.detectionClass = detectionClass;
            this.sensitiveClasses = sensitiveClasses;
        }
    }
    
    private static void registerKnownMod(String modId, String detectionClass, String[] sensitiveClasses) {
        KNOWN_MODS.put(modId, new ModInterceptConfig(modId, detectionClass, sensitiveClasses));
    }
    
    /**
     * Initialize the interceptor - call during mod loading
     */
    public static void initialize() {
        if (interceptorInstalled.getAndSet(true)) return;
        
        fpsflux$logInfo("Initializing External Mod Interceptor...");
        
        // Detect present mods
        for (ModInterceptConfig config : KNOWN_MODS.values()) {
            try {
                Class.forName(config.detectionClass);
                config.detected = true;
                fpsflux$logInfo("  Detected: " + config.modId);
                
                // Register mod-specific safe path handler
                registerModHandler(config);
                
            } catch (ClassNotFoundException ignored) {
                // Mod not present
            }
        }
        
        fpsflux$logInfo("External Mod Interceptor initialized");
    }
    
    /**
     * Register safe path handler for a detected mod
     */
    private static void registerModHandler(ModInterceptConfig config) {
        switch (config.modId) {
            case "optifine" -> registerOptifineHandler(config);
            case "sodium", "rubidium", "embeddium" -> registerSodiumFamilyHandler(config);
            case "iris", "oculus" -> registerIrisHandler(config);
            case "canvas" -> registerCanvasHandler(config);
        }
        config.handlerRegistered = true;
    }
    
    /**
     * OptiFine-specific handler
     */
    private static void registerOptifineHandler(ModInterceptConfig config) {
        SafePathRegistry.registerHandler(
            "optifine_compat",
            SafePathRegistry.PRIORITY_HIGH,
            ctx -> {
                // OptiFine often bypasses GlStateManager for shaders
                // We need to sync our cache after OptiFine's direct GL calls
                
                if (isOptifineShaderActive()) {
                    // Let OptiFine handle shader-related operations directly
                    // but mark our cache as dirty
                    fpsflux$stateDirtyFlags.set(0xFFFFFFFFL);
                    return false; // Let next handler (FPSFlux) actually do the call
                }
                
                return false; // Not handled - pass to next
            },
            SafePathRegistry.Operation.values()
        );
    }
    
    /**
     * Sodium/Rubidium/Embeddium handler
     */
    private static void registerSodiumFamilyHandler(ModInterceptConfig config) {
        SafePathRegistry.registerHandler(
            config.modId + "_compat",
            SafePathRegistry.PRIORITY_HIGH,
            ctx -> {
                // Sodium uses its own vertex format and buffer management
                // We need to track when it binds its buffers
                
                if (ctx.operation == SafePathRegistry.Operation.BIND_BUFFER ||
                    ctx.operation == SafePathRegistry.Operation.BIND_VAO) {
                    // Sodium is taking control of buffers
                    // Mark bindings dirty so we re-query on our next use
                    fpsflux$markStateDirty(DIRTY_BINDINGS);
                }
                
                return false; // Pass to FPSFlux handler
            },
            SafePathRegistry.Operation.BIND_BUFFER,
            SafePathRegistry.Operation.BIND_VAO,
            SafePathRegistry.Operation.VERTEX_ATTRIB_POINTER
        );
    }
    
    /**
     * Iris/Oculus shader handler
     */
    private static void registerIrisHandler(ModInterceptConfig config) {
        SafePathRegistry.registerHandler(
            config.modId + "_compat",
            SafePathRegistry.PRIORITY_HIGHEST,  // Shaders get highest priority
            ctx -> {
                // Iris manages its own shader programs and framebuffers
                // We should not interfere with these
                
                if (ctx.operation == SafePathRegistry.Operation.USE_PROGRAM ||
                    ctx.operation == SafePathRegistry.Operation.BIND_FRAMEBUFFER) {
                    // Let Iris handle shaders directly
                    // Just sync our cache
                    fpsflux$markStateDirty(DIRTY_BINDINGS);
                    
                    // Actually execute the call
                    switch (ctx.operation) {
                        case USE_PROGRAM -> GL20.glUseProgram(ctx.getInt(0));
                        case BIND_FRAMEBUFFER -> GL30.glBindFramebuffer(ctx.getInt(0), ctx.getInt(1));
                    }
                    return true; // Handled
                }
                
                return false; // Pass to next handler
            },
            SafePathRegistry.Operation.USE_PROGRAM,
            SafePathRegistry.Operation.BIND_FRAMEBUFFER,
            SafePathRegistry.Operation.UNIFORM
        );
    }
    
    /**
     * Canvas handler
     */
    private static void registerCanvasHandler(ModInterceptConfig config) {
        SafePathRegistry.registerHandler(
            "canvas_compat",
            SafePathRegistry.PRIORITY_HIGH,
            ctx -> {
                // Canvas has its own material system
                // We sync state and let it proceed
                fpsflux$markStateDirty(DIRTY_BINDINGS);
                return false;
            },
            SafePathRegistry.Operation.USE_PROGRAM,
            SafePathRegistry.Operation.BIND_FRAMEBUFFER
        );
    }
    
    /**
     * Check if OptiFine shaders are currently active
     */
    private static boolean isOptifineShaderActive() {
        try {
            Class<?> shadersClass = Class.forName("net.optifine.shaders.Shaders");
            java.lang.reflect.Field activeField = shadersClass.getDeclaredField("shaderPackLoaded");
            activeField.setAccessible(true);
            return activeField.getBoolean(null);
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * Get status of all detected mods
     */
    public static String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("External Mod Interceptor Status:\n");
        for (ModInterceptConfig config : KNOWN_MODS.values()) {
            sb.append("  ").append(config.modId).append(": ");
            if (config.detected) {
                sb.append("DETECTED");
                if (config.handlerRegistered) {
                    sb.append(" (handler registered)");
                }
            } else {
                sb.append("not present");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}


// ═══════════════════════════════════════════════════════════════════════════════════════
// SECTION 49: FAST PATH ROUTING - OPTIMIZED HOT PATH EXECUTION
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Ultra-fast routing for the hottest paths.
 * Bypasses the full logging and fallback infrastructure for maximum performance.
 * 
 * <h2>When to Use:</h2>
 * <ul>
 *     <li>State changes that happen thousands of times per frame</li>
 *     <li>Matrix operations during entity/block rendering</li>
 *     <li>Color/blend state toggles in GUI rendering</li>
 * </ul>
 * 
 * <h2>When NOT to Use:</h2>
 * <ul>
 *     <li>Shader program changes (need full tracking)</li>
 *     <li>Framebuffer binds (need validation)</li>
 *     <li>Any operation you need logged</li>
 * </ul>
 */
@Unique
private static final class FastPath {
    
    /** Bypass all logging for ultra-fast execution */
    private static volatile boolean ultraFastMode = false;
    
    /** Counter for ultra-fast calls (for metrics) */
    private static final LongAdder fastPathCalls = new LongAdder();
    
    /**
     * Enable ultra-fast mode (disables all logging in hot paths)
     */
    public static void enableUltraFast() {
        ultraFastMode = true;
        fpsflux$logInfo("Ultra-fast mode ENABLED - hot path logging disabled");
    }
    
    /**
     * Disable ultra-fast mode
     */
    public static void disableUltraFast() {
        ultraFastMode = false;
        fpsflux$logInfo("Ultra-fast mode DISABLED - full logging restored");
    }
    
    /**
     * Fast enable capability - minimal overhead
     */
    public static void enable(int cap) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fastPathCalls.increment();
        
        // Check cache first
        Boolean cached = fpsflux$capabilityCache.get(cap);
        if (cached != null && cached) {
            fpsflux$redundantCallsEliminated.increment();
            return; // Already enabled
        }
        
        // Update cache and call GL
        fpsflux$capabilityCache.put(cap, true);
        fpsflux$markStateDirty(DIRTY_CAPABILITIES);
        GL11.glEnable(cap);
    }
    
    /**
     * Fast disable capability - minimal overhead
     */
    public static void disable(int cap) {
        if (!fpsflux$initialized.get()) fpsflux$initialize();
        
        fastPathCalls.increment();
        
        // Check cache first
        Boolean cached = fpsflux$capabilityCache.get(cap);
        if (cached != null && !cached) {
            fpsflux$redundantCallsEliminated.increment();
            return; // Already disabled
        }
        
        // Update cache and call GL
        fpsflux$capabilityCache.put(cap, false);
        fpsflux$markStateDirty(DIRTY_CAPABILITIES);
        GL11.glDisable(cap);
    }
    
    /**
     * Fast push matrix
     */
    public static void pushMatrix() {
        fastPathCalls.increment();
        GL11.glPushMatrix();
    }
    
    /**
     * Fast pop matrix
     */
    public static void popMatrix() {
        fastPathCalls.increment();
        GL11.glPopMatrix();
    }
    
    /**
     * Fast translate
     */
    public static void translate(float x, float y, float z) {
        fastPathCalls.increment();
        GL11.glTranslatef(x, y, z);
    }
    
    /**
     * Fast translate (double)
     */
    public static void translate(double x, double y, double z) {
        fastPathCalls.increment();
        GL11.glTranslated(x, y, z);
    }
    
    /**
     * Fast scale
     */
    public static void scale(float x, float y, float z) {
        fastPathCalls.increment();
        GL11.glScalef(x, y, z);
    }
    
    /**
     * Fast rotate
     */
    public static void rotate(float angle, float x, float y, float z) {
        fastPathCalls.increment();
        GL11.glRotatef(angle, x, y, z);
    }
    
    /**
     * Fast color
     */
    public static void color(float r, float g, float b, float a) {
        fastPathCalls.increment();
        fpsflux$markStateDirty(DIRTY_COLOR);
        GL11.glColor4f(r, g, b, a);
    }
    
    /**
     * Fast blend func
     */
    public static void blendFunc(int src, int dst) {
        fastPathCalls.increment();
        fpsflux$markStateDirty(DIRTY_BLEND);
        GL11.glBlendFunc(src, dst);
    }
    
    /**
     * Fast depth mask
     */
    public static void depthMask(boolean flag) {
        fastPathCalls.increment();
        fpsflux$markStateDirty(DIRTY_DEPTH);
        GL11.glDepthMask(flag);
    }
    
    /**
     * Get fast path call count
     */
    public static long getCallCount() {
        return fastPathCalls.sum();
    }
    
    /**
     * Reset counters
     */
    public static void resetCounters() {
        fastPathCalls.reset();
    }
}


// ═══════════════════════════════════════════════════════════════════════════════════════
// SECTION 50: CONFLICT DETECTOR - RUNTIME MIXIN CONFLICT DETECTION
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Detects and reports Mixin conflicts at runtime.
 * Provides warnings before crashes occur.
 */
@Unique
public static final class ConflictDetector {
    
    /** Known conflict patterns */
    private static final List<ConflictPattern> knownConflicts = new ArrayList<>();
    
    /** Detected conflicts */
    private static final List<String> detectedConflicts = new CopyOnWriteArrayList<>();
    
    /** Conflict detection run flag */
    private static final AtomicBoolean detectionRun = new AtomicBoolean(false);
    
    static {
        // Register known conflict patterns
        registerConflict("GlStateManager", "enableAlpha", 
            "OptiFine", "FPSFlux", "Both mods overwrite enableAlpha");
        registerConflict("GlStateManager", "enableBlend", 
            "Sodium", "FPSFlux", "Both mods overwrite enableBlend");
        registerConflict("GlStateManager", "pushMatrix", 
            "Canvas", "FPSFlux", "Both mods overwrite pushMatrix");
    }
    
    private static final class ConflictPattern {
        final String targetClass;
        final String targetMethod;
        final String mod1;
        final String mod2;
        final String description;
        
        ConflictPattern(String targetClass, String targetMethod, 
                       String mod1, String mod2, String description) {
            this.targetClass = targetClass;
            this.targetMethod = targetMethod;
            this.mod1 = mod1;
            this.mod2 = mod2;
            this.description = description;
        }
    }
    
    private static void registerConflict(String targetClass, String targetMethod,
                                         String mod1, String mod2, String description) {
        knownConflicts.add(new ConflictPattern(targetClass, targetMethod, mod1, mod2, description));
    }
    
    /**
     * Run conflict detection
     */
    public static void detectConflicts() {
        if (detectionRun.getAndSet(true)) return;
        
        fpsflux$logInfo("Running Mixin conflict detection...");
        
        // Check for known mod combinations
        Set<String> presentMods = new HashSet<>();
        
        // Check OptiFine
        try {
            Class.forName("optifine.OptiFineClassTransformer");
            presentMods.add("OptiFine");
        } catch (ClassNotFoundException ignored) {}
        
        // Check Sodium family
        try {
            Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
            presentMods.add("Sodium");
        } catch (ClassNotFoundException ignored) {}
        
        try {
            Class.forName("me.jellysquid.mods.rubidium.RubidiumMod");
            presentMods.add("Rubidium");
        } catch (ClassNotFoundException ignored) {}
        
        // Check Iris
        try {
            Class.forName("net.coderbot.iris.Iris");
            presentMods.add("Iris");
        } catch (ClassNotFoundException ignored) {}
        
        // Check Canvas
        try {
            Class.forName("grondag.canvas.CanvasMod");
            presentMods.add("Canvas");
        } catch (ClassNotFoundException ignored) {}
        
        // FPSFlux is always present
        presentMods.add("FPSFlux");
        
        // Check for conflicts
        for (ConflictPattern pattern : knownConflicts) {
            if (presentMods.contains(pattern.mod1) && presentMods.contains(pattern.mod2)) {
                String conflict = String.format(
                    "POTENTIAL CONFLICT: %s.%s - %s and %s - %s",
                    pattern.targetClass, pattern.targetMethod,
                    pattern.mod1, pattern.mod2, pattern.description
                );
                detectedConflicts.add(conflict);
                fpsflux$logInfo("  ⚠ " + conflict);
            }
        }
        
        if (detectedConflicts.isEmpty()) {
            fpsflux$logInfo("  ✓ No conflicts detected");
        } else {
            fpsflux$logInfo("  Found " + detectedConflicts.size() + " potential conflicts");
            fpsflux$logInfo("  Safe Path Registry will attempt to prevent crashes");
        }
    }
    
    /**
     * Get list of detected conflicts
     */
    public static List<String> getDetectedConflicts() {
        return Collections.unmodifiableList(detectedConflicts);
    }
    
    /**
     * Check if any conflicts were detected
     */
    public static boolean hasConflicts() {
        return !detectedConflicts.isEmpty();
    }
}


// ═══════════════════════════════════════════════════════════════════════════════════════
// SECTION 51: UPDATED INITIALIZATION - INTEGRATE NEW SYSTEMS
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Add this call to the existing fpsflux$initialize() method, 
 * after fpsflux$initializeManagers() and before fpsflux$buildRoutingCache()
 */
@Unique
private static void fpsflux$initializeNewSystems() {
    // Step 5.5: Initialize external mod interceptor
    ExternalModInterceptor.initialize();
    
    // Step 5.6: Run conflict detection
    ConflictDetector.detectConflicts();
    
    // Step 5.7: Check if ultra-fast mode should be enabled
    if (Config.isUltraFastModeEnabled()) {
        FastPath.enableUltraFast();
    }
    
    fpsflux$logInfo("New subsystems initialized");
}


// ═══════════════════════════════════════════════════════════════════════════════════════
// SECTION 52: PUBLIC API - FOR EXTERNAL MODS TO INTEGRATE
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Public API for external mods to integrate with FPSFlux's safe path system.
 * 
 * <h2>Usage by External Mods:</h2>
 * <pre>
 *     // In your mod's initialization:
 *     FPSFluxAPI.registerHandler("mymod", 750, (ctx) -> {
 *         if (ctx.operation == Operation.BIND_TEXTURE) {
 *             // My mod's custom texture binding logic
 *             return true; // Handled
 *         }
 *         return false; // Let FPSFlux handle it
 *     }, Operation.BIND_TEXTURE, Operation.BIND_FRAMEBUFFER);
 * </pre>
 */
@Unique
public static final class FPSFluxAPI {
    
    /**
     * Register a safe path handler.
     * @see SafePathRegistry#registerHandler
     */
    public static void registerHandler(String modId, int priority, 
                                        SafePathRegistry.SafePathHandler handler,
                                        SafePathRegistry.Operation... operations) {
        SafePathRegistry.registerHandler(modId, priority, handler, operations);
    }
    
    /**
     * Unregister all handlers for a mod.
     */
    public static void unregisterMod(String modId) {
        SafePathRegistry.unregisterMod(modId);
    }
    
    /**
     * Get detected rendering mods.
     */
    public static Set<RenderMod> getDetectedMods() {
        return fpsflux$getDetectedMods();
    }
    
    /**
     * Check if a specific mod is detected.
     */
    public static boolean isModDetected(RenderMod mod) {
        return fpsflux$detectedMods.contains(mod);
    }
    
    /**
     * Get the active wrapper type.
     */
    public static GLWrapper getActiveWrapper() {
        return fpsflux$getActiveWrapper();
    }
    
    /**
     * Force a complete state resync.
     */
    public static void forceStateResync() {
        fpsflux$forceStateResync();
    }
    
    /**
     * Get metrics summary.
     */
    public static String getMetrics() {
        return fpsflux$getMetricsSummary();
    }
    
    /**
     * Check for conflicts.
     */
    public static List<String> getConflicts() {
        return ConflictDetector.getDetectedConflicts();
    }
    
    /**
     * Enable ultra-fast mode (disables logging in hot paths).
     */
    public static void enableUltraFastMode() {
        FastPath.enableUltraFast();
    }
    
    /**
     * Disable ultra-fast mode.
     */
    public static void disableUltraFastMode() {
        FastPath.disableUltraFast();
    }
    
    /**
     * Priority constants for handler registration.
     */
    public static final int PRIORITY_HIGHEST = SafePathRegistry.PRIORITY_HIGHEST;
    public static final int PRIORITY_HIGH = SafePathRegistry.PRIORITY_HIGH;
    public static final int PRIORITY_NORMAL = SafePathRegistry.PRIORITY_NORMAL;
    public static final int PRIORITY_LOW = SafePathRegistry.PRIORITY_LOW;
    public static final int PRIORITY_FALLBACK = SafePathRegistry.PRIORITY_FALLBACK;
}
