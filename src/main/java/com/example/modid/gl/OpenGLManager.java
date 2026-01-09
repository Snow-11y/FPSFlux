package com.example.modid.gl;

import com.example.modid.Config;
import com.example.modid.gl.mapping.OpenGLCallMapper;

import com.example.modid.gl.buffer.ops.GLBufferOps10;
import com.example.modid.gl.buffer.ops.GLBufferOps11;
import com.example.modid.gl.buffer.ops.GLBufferOps12;
import com.example.modid.gl.buffer.ops.GLBufferOps121;
import com.example.modid.gl.buffer.ops.GLBufferOps15;
import com.example.modid.gl.buffer.ops.GLBufferOps20;
import com.example.modid.gl.buffer.ops.GLBufferOps21;
import com.example.modid.gl.buffer.ops.GLBufferOps30;
import com.example.modid.gl.buffer.ops.GLBufferOps33;
import com.example.modid.gl.buffer.ops.GLBufferOps40;
import com.example.modid.gl.buffer.ops.GLBufferOps43;
import com.example.modid.gl.buffer.ops.GLBufferOps44;
import com.example.modid.gl.buffer.ops.GLBufferOps45;
import com.example.modid.gl.buffer.ops.GLBufferOps46;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * OpenGLManager
 *
 * The ultra-fast central dispatcher:
 * - Receives GL calls from the game (MC 1.12.2 or any caller)
 * - Chooses the *single* best pipeline at init time
 * - Routes calls to the chosen pipeline with near-zero overhead
 * - Uses OpenGLCallMapper as infrastructure (LWJGL2/3 separation, extensions, translation)
 *
 * HARD RULES IMPLEMENTED:
 * - No per-call GL version branching (only init-time selection)
 * - No per-call allocation
 * - No "emulation" of missing features at manager level
 *   (manager either routes to pipeline/mapper, or throws if strict)
 *
 * IMPORTANT:
 * - This manager assumes the game runs GL on a single render thread.
 * - If vanilla/other mods call GL directly (bypassing manager), call invalidateState()
 *   before your rendering pass to avoid state desync.
 */
public final class OpenGLManager {

    // ---------------------------------------------------------------------
    // Safe publication
    // ---------------------------------------------------------------------

    private static volatile OpenGLManager PUBLISHED;
    private static OpenGLManager FAST;

    public static boolean isInitialized() { return PUBLISHED != null; }

    /** Safe accessor (one volatile read). */
    public static OpenGLManager getSafe() {
        OpenGLManager m = PUBLISHED;
        if (m == null) throw new IllegalStateException("OpenGLManager not initialized. Call OpenGLManager.init() after GL context exists.");
        return m;
    }

    /** Fast accessor (no volatile read). Use ONLY after init(). */
    public static OpenGLManager getFast() { return FAST; }

    // ---------------------------------------------------------------------
    // LWJGL detection (manager-level only for info / diagnostics)
    // Mapper does the real separation.
    // ---------------------------------------------------------------------

    public static final int LWJGL_UNKNOWN = 0;
    public static final int LWJGL_2 = 2;
    public static final int LWJGL_3 = 3;

    public static final int LWJGL_VERSION;
    static {
        int v = LWJGL_UNKNOWN;
        try {
            Class.forName("org.lwjgl.Version");
            v = LWJGL_3;
        } catch (Throwable ignored) {
            try {
                Class.forName("org.lwjgl.Sys");
                v = LWJGL_2;
            } catch (Throwable ignored2) {
                v = LWJGL_UNKNOWN;
            }
        }
        LWJGL_VERSION = v;
    }

    // ---------------------------------------------------------------------
    // Minimal config snapshot (no references; init-time only)
    // Uses reflection-friendly getters to avoid hard dependency on exact Config API.
    // ---------------------------------------------------------------------

    private static final class Settings {
        final int maxGL;                 // max GL version to use (e.g. 33, 45, 46)
        final boolean debug;             // debug logging
        final boolean strictNoEmulation; // if true: unsupported advanced calls throw
        final boolean cacheBinds;        // bind caching (huge CPU win)
        final boolean cacheCommonState;  // enable/disable + depth/blend caching
        final boolean enableMultiDrawIndirect; // allow MultiDrawIndirect usage if supported
        final boolean enablePersistentMapping; // allow persistent map usage (pipelines may use)
        final boolean enableDSA;         // allow DSA usage (pipelines may use)

        Settings(Config cfg) {
            this.maxGL = getInt(cfg, "getMaxGLVersion", 46);
            this.debug = getBool(cfg, "getDebugMode", false);

            // Default to your rule: translation only, no emulation.
            this.strictNoEmulation = getBool(cfg, "getStrictNoEmulation", true);

            this.cacheBinds = getBool(cfg, "getCacheBinds", true);
            this.cacheCommonState = getBool(cfg, "getCacheCommonState", true);

            this.enableMultiDrawIndirect = getBool(cfg, "getUseMultiDrawIndirect", true);
            this.enablePersistentMapping = getBool(cfg, "getUsePersistentMapping", true);
            this.enableDSA = getBool(cfg, "getUseDSA", true);
        }

        private static boolean getBool(Object o, String method, boolean def) {
            try {
                return (Boolean) o.getClass().getMethod(method).invoke(o);
            } catch (Throwable t) {
                return def;
            }
        }

        private static int getInt(Object o, String method, int def) {
            try {
                return (Integer) o.getClass().getMethod(method).invoke(o);
            } catch (Throwable t) {
                return def;
            }
        }
    }

    // ---------------------------------------------------------------------
    // GL constants (no LWJGL dependency)
    // ---------------------------------------------------------------------

    public static final int GL_VERSION = 0x1F02;

    public static final int GL_ARRAY_BUFFER = 0x8892;
    public static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    public static final int GL_COPY_READ_BUFFER = 0x8F36;
    public static final int GL_COPY_WRITE_BUFFER = 0x8F37;

    public static final int GL_DEPTH_TEST = 0x0B71;
    public static final int GL_BLEND = 0x0BE2;
    public static final int GL_CULL_FACE = 0x0B44;
    public static final int GL_SCISSOR_TEST = 0x0C11;
    public static final int GL_STENCIL_TEST = 0x0B90;

    // ---------------------------------------------------------------------
    // Tiny state cache (single-thread assumed)
    // ---------------------------------------------------------------------

    private static final class State {
        int arrayBuffer = -1;
        int elementBuffer = -1;
        long enableBits = 0L;

        int depthFunc = -1;
        boolean depthMask = true;

        int blendSrcRGB = -1, blendDstRGB = -1, blendSrcA = -1, blendDstA = -1;

        static final int BIT_DEPTH = 0;
        static final int BIT_BLEND = 1;
        static final int BIT_CULL  = 2;
        static final int BIT_SCISS = 3;
        static final int BIT_STEN  = 4;

        void invalidate() {
            arrayBuffer = -1;
            elementBuffer = -1;
            enableBits = 0L;
            depthFunc = -1;
            depthMask = true;
            blendSrcRGB = blendDstRGB = blendSrcA = blendDstA = -1;
        }
    }

    // ---------------------------------------------------------------------
    // Buffer pipeline dispatch WITHOUT requiring pipeline classes to implement an interface.
    // Uses MethodHandles bound at init:
    // - No per-call reflection
    // - No per-call branching
    // - Monomorphic invokeExact on constant handles (JIT can optimize)
    // ---------------------------------------------------------------------

    private static final class BufferDispatch {
        final Object impl;

        final MethodHandle mhGenBuffer; // ()int
        final MethodHandle mhDeleteBuffer; // (int)void
        final MethodHandle mhBindBuffer; // (int,int)void

        final MethodHandle mhBufferDataSize; // (int,long,int)void
        final MethodHandle mhBufferDataBB;   // (int,ByteBuffer,int)void
        final MethodHandle mhBufferDataFB;   // (int,FloatBuffer,int)void
        final MethodHandle mhBufferDataIB;   // (int,IntBuffer,int)void

        final MethodHandle mhBufferSubDataBB; // (int,long,ByteBuffer)void
        final MethodHandle mhBufferSubDataFB; // (int,long,FloatBuffer)void
        final MethodHandle mhBufferSubDataIB; // (int,long,IntBuffer)void

        final MethodHandle mhMapBuffer;       // either (int,int,long)ByteBuffer OR (int,int)ByteBuffer
        final boolean mapBufferHasLength;

        final MethodHandle mhMapBufferRange;  // (int,long,long,int)ByteBuffer (optional)
        final boolean hasMapBufferRange;

        final MethodHandle mhUnmapBuffer;     // either (int)boolean OR (int)void
        final boolean unmapReturnsBoolean;

        final MethodHandle mhFlushMappedRange; // (int,long,long)void (optional)
        final boolean hasFlushMappedRange;

        final MethodHandle mhCopyBufferSubData; // (int,int,long,long,long)void (optional)
        final boolean hasCopyBufferSubData;

        final MethodHandle mhShutdown; // ()void (optional)
        final boolean hasShutdown;

        BufferDispatch(Object impl, MethodHandles.Lookup lookup) {
            this.impl = impl;

            // Required core methods (must exist)
            mhGenBuffer = bindRequired(lookup, impl, "genBuffer", MethodType.methodType(int.class));
            mhDeleteBuffer = bindRequired(lookup, impl, "deleteBuffer", MethodType.methodType(void.class, int.class));
            mhBindBuffer = bindRequired(lookup, impl, "bindBuffer", MethodType.methodType(void.class, int.class, int.class));

            mhBufferDataSize = bindRequired(lookup, impl, "bufferData", MethodType.methodType(void.class, int.class, long.class, int.class));
            mhBufferDataBB = bindRequired(lookup, impl, "bufferData", MethodType.methodType(void.class, int.class, ByteBuffer.class, int.class));
            mhBufferDataFB = bindRequired(lookup, impl, "bufferData", MethodType.methodType(void.class, int.class, FloatBuffer.class, int.class));
            mhBufferDataIB = bindRequired(lookup, impl, "bufferData", MethodType.methodType(void.class, int.class, IntBuffer.class, int.class));

            mhBufferSubDataBB = bindRequired(lookup, impl, "bufferSubData", MethodType.methodType(void.class, int.class, long.class, ByteBuffer.class));
            mhBufferSubDataFB = bindRequired(lookup, impl, "bufferSubData", MethodType.methodType(void.class, int.class, long.class, FloatBuffer.class));
            mhBufferSubDataIB = bindRequired(lookup, impl, "bufferSubData", MethodType.methodType(void.class, int.class, long.class, IntBuffer.class));

            // mapBuffer: try (int,int,long)->ByteBuffer, else (int,int)->ByteBuffer
            MethodHandle mapA = bindOptional(lookup, impl, "mapBuffer",
                    MethodType.methodType(ByteBuffer.class, int.class, int.class, long.class));
            if (mapA != null) {
                mhMapBuffer = mapA;
                mapBufferHasLength = true;
            } else {
                MethodHandle mapB = bindOptional(lookup, impl, "mapBuffer",
                        MethodType.methodType(ByteBuffer.class, int.class, int.class));
                mhMapBuffer = requireNonNull(mapB, "mapBuffer");
                mapBufferHasLength = false;
            }

            // mapBufferRange optional (GL 3.0+)
            MethodHandle mbr = bindOptional(lookup, impl, "mapBufferRange",
                    MethodType.methodType(ByteBuffer.class, int.class, long.class, long.class, int.class));
            mhMapBufferRange = mbr;
            hasMapBufferRange = (mbr != null);

            // unmapBuffer: try boolean return, else void
            MethodHandle unA = bindOptional(lookup, impl, "unmapBuffer",
                    MethodType.methodType(boolean.class, int.class));
            if (unA != null) {
                mhUnmapBuffer = unA;
                unmapReturnsBoolean = true;
            } else {
                MethodHandle unB = bindOptional(lookup, impl, "unmapBuffer",
                        MethodType.methodType(void.class, int.class));
                mhUnmapBuffer = requireNonNull(unB, "unmapBuffer");
                unmapReturnsBoolean = false;
            }

            MethodHandle flush = bindOptional(lookup, impl, "flushMappedBufferRange",
                    MethodType.methodType(void.class, int.class, long.class, long.class));
            mhFlushMappedRange = flush;
            hasFlushMappedRange = (flush != null);

            MethodHandle copy = bindOptional(lookup, impl, "copyBufferSubData",
                    MethodType.methodType(void.class, int.class, int.class, long.class, long.class, long.class));
            mhCopyBufferSubData = copy;
            hasCopyBufferSubData = (copy != null);

            MethodHandle shut = bindOptional(lookup, impl, "shutdown",
                    MethodType.methodType(void.class));
            mhShutdown = shut;
            hasShutdown = (shut != null);
        }

        int genBuffer() {
            try {
                return (int) mhGenBuffer.invokeExact();
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void deleteBuffer(int buffer) {
            try {
                mhDeleteBuffer.invokeExact(buffer);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void bindBuffer(int target, int buffer) {
            try {
                mhBindBuffer.invokeExact(target, buffer);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void bufferData(int target, long size, int usage) {
            try {
                mhBufferDataSize.invokeExact(target, size, usage);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void bufferData(int target, ByteBuffer data, int usage) {
            try {
                mhBufferDataBB.invokeExact(target, data, usage);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void bufferData(int target, FloatBuffer data, int usage) {
            try {
                mhBufferDataFB.invokeExact(target, data, usage);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void bufferData(int target, IntBuffer data, int usage) {
            try {
                mhBufferDataIB.invokeExact(target, data, usage);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void bufferSubData(int target, long offset, ByteBuffer data) {
            try {
                mhBufferSubDataBB.invokeExact(target, offset, data);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void bufferSubData(int target, long offset, FloatBuffer data) {
            try {
                mhBufferSubDataFB.invokeExact(target, offset, data);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void bufferSubData(int target, long offset, IntBuffer data) {
            try {
                mhBufferSubDataIB.invokeExact(target, offset, data);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        ByteBuffer mapBuffer(int target, int access, long lengthIfNeeded) {
            try {
                if (mapBufferHasLength) {
                    return (ByteBuffer) mhMapBuffer.invokeExact(target, access, lengthIfNeeded);
                }
                return (ByteBuffer) mhMapBuffer.invokeExact(target, access);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        ByteBuffer mapBufferRange(int target, long offset, long length, int accessFlags) {
            if (!hasMapBufferRange) return null;
            try {
                return (ByteBuffer) mhMapBufferRange.invokeExact(target, offset, length, accessFlags);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        boolean unmapBuffer(int target) {
            try {
                if (unmapReturnsBoolean) {
                    return (boolean) mhUnmapBuffer.invokeExact(target);
                }
                mhUnmapBuffer.invokeExact(target);
                return true;
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void flushMappedBufferRange(int target, long offset, long length) {
            if (!hasFlushMappedRange) return;
            try {
                mhFlushMappedRange.invokeExact(target, offset, length);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
            if (!hasCopyBufferSubData) return;
            try {
                mhCopyBufferSubData.invokeExact(readTarget, writeTarget, readOffset, writeOffset, size);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        void shutdown() {
            if (!hasShutdown) return;
            try {
                mhShutdown.invokeExact();
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        private static MethodHandle bindRequired(MethodHandles.Lookup lookup, Object impl, String name, MethodType type) {
            MethodHandle mh = bindOptional(lookup, impl, name, type);
            return requireNonNull(mh, name + type);
        }

        private static MethodHandle bindOptional(MethodHandles.Lookup lookup, Object impl, String name, MethodType type) {
            try {
                MethodHandle mh = MethodHandles.publicLookup()
                        .findVirtual(impl.getClass(), name, type)
                        .bindTo(impl);
                return mh;
            } catch (Throwable t) {
                return null;
            }
        }

        private static MethodHandle requireNonNull(MethodHandle mh, String what) {
            if (mh == null) {
                throw new IllegalStateException("Pipeline missing required method: " + what);
            }
            return mh;
        }

        private static RuntimeException rethrow(Throwable t) {
            if (t instanceof RuntimeException) return (RuntimeException) t;
            if (t instanceof Error) throw (Error) t;
            return new RuntimeException(t);
        }
    }

    // ---------------------------------------------------------------------
    // Instance fields (ALL final for JIT friendliness)
    // ---------------------------------------------------------------------

    private final Settings settings;
    private final Thread renderThread;
    private final boolean debug;

    private final int detectedGL;       // e.g., 46, 45, 33, 121
    private final int effectiveGL;      // limited by config max
    private final int bufferOpsVersion; // one of the allowed set

    private final BufferDispatch buffer;
    private final State state;

    // cached capability flags (read once, used for strict checks)
    private final boolean hasMultiDrawIndirect;   // GL 4.3+ / extension
    private final boolean hasPersistentMapping;   // GL 4.4+ / extension
    private final boolean hasDSA;                 // GL 4.5+ / extension

    private OpenGLManager(Settings s, int detectedGL, int effectiveGL, int opsVer,
                          BufferDispatch buffer, State state,
                          boolean hasMDI, boolean hasPM, boolean hasDSA,
                          Thread renderThread) {
        this.settings = s;
        this.renderThread = renderThread;
        this.debug = s.debug;

        this.detectedGL = detectedGL;
        this.effectiveGL = effectiveGL;
        this.bufferOpsVersion = opsVer;

        this.buffer = buffer;
        this.state = state;

        this.hasMultiDrawIndirect = hasMDI;
        this.hasPersistentMapping = hasPM;
        this.hasDSA = hasDSA;
    }

    // ---------------------------------------------------------------------
    // init()
    // ---------------------------------------------------------------------

    /**
     * Initialize (must be called after GL context exists).
     * Creates exactly one buffer pipeline instance:
     * GLBufferOps10/11/12/121/15/20/21/30/33/40/43/44/45/46
     */
    public static boolean init() {
        if (PUBLISHED != null) return true;

        synchronized (OpenGLManager.class) {
            if (PUBLISHED != null) return true;

            try {
                OpenGLCallMapper.initialize();

                Config cfg = Config.getInstance();
                Settings s = new Settings(cfg);

                String ver = OpenGLCallMapper.getString(GL_VERSION);
                int detected = parseGLVersionCode(ver);
                int effective = clampToMax(detected, s.maxGL);
                int opsVer = chooseBufferOpsVersion(effective);

                Object opsImpl = instantiateBufferOps(opsVer);

                // Detect important advanced capabilities from mapper (infra)
                // If your OpenGLCallMapper doesn’t expose these, keep them conservative.
                boolean hasMDI = safeHas(OpenGLCallMapper.class, "hasFeatureMultiDrawIndirect");
                boolean hasPM = safeHas(OpenGLCallMapper.class, "hasFeaturePersistentMapping");
                boolean hasDSA = safeHas(OpenGLCallMapper.class, "hasFeatureDSA");

                BufferDispatch dispatch = new BufferDispatch(opsImpl, MethodHandles.lookup());
                State st = new State();
                st.invalidate();

                Thread rt = Thread.currentThread();

                OpenGLManager mgr = new OpenGLManager(s, detected, effective, opsVer, dispatch, st, hasMDI, hasPM, hasDSA, rt);

                PUBLISHED = mgr;
                FAST = mgr;

                if (s.debug) {
                    System.out.println("[OpenGLManager] init ok" +
                            " LWJGL=" + LWJGL_VERSION +
                            " GL=" + ver +
                            " detected=" + detected +
                            " effective=" + effective +
                            " bufferOps=" + opsVer);
                }

                return true;
            } catch (Throwable t) {
                System.err.println("[OpenGLManager] init failed: " + t.getMessage());
                t.printStackTrace();
                return false;
            }
        }
    }

    // ---------------------------------------------------------------------
    // Buffer pipeline selection (ONLY YOUR OPS)
    // ---------------------------------------------------------------------

    private static Object instantiateBufferOps(int version) {
        switch (version) {
            case 10:  return new GLBufferOps10();
            case 11:  return new GLBufferOps11();
            case 12:  return new GLBufferOps12();
            case 121: return new GLBufferOps121();
            case 15:  return new GLBufferOps15();
            case 20:  return new GLBufferOps20();
            case 21:  return new GLBufferOps21();
            case 30:  return new GLBufferOps30();
            case 33:  return new GLBufferOps33();
            case 40:  return new GLBufferOps40();
            case 43:  return new GLBufferOps43();
            case 44:  return new GLBufferOps44();
            case 45:  return new GLBufferOps45();
            case 46:  return new GLBufferOps46();
            default:  return new GLBufferOps10();
        }
    }

    private static int chooseBufferOpsVersion(int gl) {
        // The only allowed pipelines:
        // 10,11,12,121,15,20,21,30,33,40,43,44,45,46
        if (gl == 121) return 121;
        if (gl <= 10) return 10;
        if (gl == 11) return 11;
        if (gl == 12) return 12;

        // 1.3/1.4 not listed -> use 1.2.1 pipeline
        if (gl == 13 || gl == 14) return 121;

        if (gl == 15) return 15;

        if (gl == 20) return 20;
        if (gl == 21) return 21;

        // 3.0-3.2 -> 30 pipeline
        if (gl == 30 || gl == 31 || gl == 32) return 30;
        if (gl == 33) return 33;

        // 4.0-4.2 -> 40 pipeline
        if (gl == 40 || gl == 41 || gl == 42) return 40;
        if (gl == 43) return 43;
        if (gl == 44) return 44;
        if (gl == 45) return 45;
        if (gl >= 46) return 46;

        return 10;
    }

    // ---------------------------------------------------------------------
    // Version parsing including 1.2.1
    // ---------------------------------------------------------------------

    private static int parseGLVersionCode(String versionString) {
        if (versionString == null || versionString.isEmpty()) return 11;

        int major = 1, minor = 1, patch = 0;

        int len = versionString.length();
        int i = 0;

        major = parseIntAt(versionString, i);
        i = indexOf(versionString, '.', i);
        if (i >= 0 && i + 1 < len) {
            i++;
            minor = parseIntAt(versionString, i);
            int dot2 = indexOf(versionString, '.', i);
            if (dot2 >= 0 && dot2 + 1 < len) {
                patch = parseIntAt(versionString, dot2 + 1);
            }
        }

        if (major == 1 && minor == 2 && patch == 1) return 121;
        return major * 10 + minor;
    }

    private static int parseIntAt(String s, int start) {
        int len = s.length();
        int i = start;
        int val = 0;
        boolean any = false;
        while (i < len) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') break;
            any = true;
            val = val * 10 + (c - '0');
            i++;
        }
        return any ? val : 0;
    }

    private static int indexOf(String s, char ch, int start) {
        int len = s.length();
        for (int i = start; i < len; i++) {
            if (s.charAt(i) == ch) return i;
            // stop if we reach whitespace before finding dots
            if (s.charAt(i) <= ' ') break;
        }
        return -1;
    }

    private static int clampToMax(int detected, int max) {
        // Interpret 1.2.1 as 1.2 for comparison against user max,
        // but preserve 121 if it is the detected version and allowed.
        int d = (detected == 121) ? 12 : detected;
        int m = (max == 121) ? 12 : max;

        return (d <= m) ? detected : max;
    }

    // ---------------------------------------------------------------------
    // Capability detection from OpenGLCallMapper (best-effort).
    // If your mapper has different names, this safely returns false.
    // ---------------------------------------------------------------------

    private static boolean safeHas(Class<?> mapper, String method) {
        try {
            Object v = mapper.getMethod(method).invoke(null);
            return (v instanceof Boolean) && (Boolean) v;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Debug-only render-thread check (zero overhead when debug=false)
    // ---------------------------------------------------------------------

    private void assertRenderThread() {
        if (!debug) return;
        if (Thread.currentThread() != renderThread) {
            throw new IllegalStateException("OpenGLManager used from wrong thread. Expected=" +
                    renderThread.getName() + " got=" + Thread.currentThread().getName());
        }
    }

    // ---------------------------------------------------------------------
    // Public: state desync handling
    // ---------------------------------------------------------------------

    /**
     * If any code calls GL directly bypassing this manager, call invalidateState()
     * before you start your rendering pass.
     */
    public void invalidateState() {
        assertRenderThread();
        state.invalidate();
    }

    // ---------------------------------------------------------------------
    // Public: Buffer routing (fastest hot path)
    // ---------------------------------------------------------------------

    public int genBuffer() {
        assertRenderThread();
        return buffer.genBuffer();
    }

    public void deleteBuffer(int id) {
        assertRenderThread();
        buffer.deleteBuffer(id);
        // keep cache correct
        if (state.arrayBuffer == id) state.arrayBuffer = -1;
        if (state.elementBuffer == id) state.elementBuffer = -1;
    }

    /**
     * Bind caching here is *very* worth it.
     * It prevents massive JNI/driver churn.
     */
    public void bindBuffer(int target, int id) {
        assertRenderThread();

        if (settings.cacheBinds) {
            if (target == GL_ARRAY_BUFFER) {
                if (state.arrayBuffer == id) return;
                state.arrayBuffer = id;
            } else if (target == GL_ELEMENT_ARRAY_BUFFER) {
                if (state.elementBuffer == id) return;
                state.elementBuffer = id;
            }
        }

        buffer.bindBuffer(target, id);
    }

    public void bufferData(int target, long size, int usage) {
        assertRenderThread();
        buffer.bufferData(target, size, usage);
    }

    public void bufferData(int target, ByteBuffer data, int usage) {
        assertRenderThread();
        buffer.bufferData(target, data, usage);
    }

    public void bufferData(int target, FloatBuffer data, int usage) {
        assertRenderThread();
        buffer.bufferData(target, data, usage);
    }

    public void bufferData(int target, IntBuffer data, int usage) {
        assertRenderThread();
        buffer.bufferData(target, data, usage);
    }

    public void bufferSubData(int target, long offset, ByteBuffer data) {
        assertRenderThread();
        buffer.bufferSubData(target, offset, data);
    }

    public void bufferSubData(int target, long offset, FloatBuffer data) {
        assertRenderThread();
        buffer.bufferSubData(target, offset, data);
    }

    public void bufferSubData(int target, long offset, IntBuffer data) {
        assertRenderThread();
        buffer.bufferSubData(target, offset, data);
    }

    /**
     * mapBuffer:
     * Some pipelines expose mapBuffer(target, access) and some mapBuffer(target, access, length).
     * Manager exposes the superset; if pipeline needs length we pass it.
     */
    public ByteBuffer mapBuffer(int target, int access, long lengthIfNeeded) {
        assertRenderThread();
        return buffer.mapBuffer(target, access, lengthIfNeeded);
    }

    /**
     * mapBufferRange:
     * Returns null if pipeline does not support it.
     * If strict, throws.
     */
    public ByteBuffer mapBufferRange(int target, long offset, long length, int accessFlags) {
        assertRenderThread();
        ByteBuffer bb = buffer.mapBufferRange(target, offset, length, accessFlags);
        if (bb == null && settings.strictNoEmulation) {
            throw new UnsupportedOperationException("mapBufferRange not supported by pipeline GLBufferOps" + bufferOpsVersion);
        }
        return bb;
    }

    public boolean unmapBuffer(int target) {
        assertRenderThread();
        return buffer.unmapBuffer(target);
    }

    /**
     * Flush mapped range:
     * No-op if pipeline doesn’t expose it.
     * If strict and requested, throw.
     */
    public void flushMappedBufferRange(int target, long offset, long length) {
        assertRenderThread();
        if (!buffer.hasFlushMappedRange && settings.strictNoEmulation) {
            throw new UnsupportedOperationException("flushMappedBufferRange not supported by pipeline GLBufferOps" + bufferOpsVersion);
        }
        buffer.flushMappedBufferRange(target, offset, length);
    }

    /**
     * Copy buffer sub data:
     * No-op if pipeline doesn’t expose it.
     * If strict, throw.
     */
    public void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        assertRenderThread();
        if (!buffer.hasCopyBufferSubData && settings.strictNoEmulation) {
            throw new UnsupportedOperationException("copyBufferSubData not supported by pipeline GLBufferOps" + bufferOpsVersion);
        }
        buffer.copyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    // ---------------------------------------------------------------------
    // Public: extreme-perf features that the manager can route (no emulation)
    // (These are optional entrypoints; pipelines + mapper do the heavy lifting.)
    // ---------------------------------------------------------------------

    /**
     * MultiDrawArraysIndirect (GL 4.3+):
     * Manager will NOT emulate by looping unless you explicitly disable strictNoEmulation.
     */
    public void multiDrawArraysIndirect(int mode, long indirect, int drawCount, int stride) {
        assertRenderThread();
        if (!settings.enableMultiDrawIndirect || !hasMultiDrawIndirect) {
            if (settings.strictNoEmulation) {
                throw new UnsupportedOperationException("MultiDrawArraysIndirect not supported/enabled");
            }
            return;
        }
        OpenGLCallMapper.multiDrawArraysIndirect(mode, indirect, drawCount, stride);
    }

    /**
     * MultiDrawElementsIndirect (GL 4.3+):
     * No emulation here.
     */
    public void multiDrawElementsIndirect(int mode, int type, long indirect, int drawCount, int stride) {
        assertRenderThread();
        if (!settings.enableMultiDrawIndirect || !hasMultiDrawIndirect) {
            if (settings.strictNoEmulation) {
                throw new UnsupportedOperationException("MultiDrawElementsIndirect not supported/enabled");
            }
            return;
        }
        OpenGLCallMapper.multiDrawElementsIndirect(mode, type, indirect, drawCount, stride);
    }

    // ---------------------------------------------------------------------
    // Public: common state caching (tiny, fast, big win)
    // ---------------------------------------------------------------------

    public void enable(int cap) {
        assertRenderThread();
        if (!settings.cacheCommonState) {
            OpenGLCallMapper.enable(cap);
            return;
        }
        int bit = capToBit(cap);
        if (bit >= 0) {
            long mask = 1L << bit;
            if ((state.enableBits & mask) != 0L) return;
            state.enableBits |= mask;
        }
        OpenGLCallMapper.enable(cap);
    }

    public void disable(int cap) {
        assertRenderThread();
        if (!settings.cacheCommonState) {
            OpenGLCallMapper.disable(cap);
            return;
        }
        int bit = capToBit(cap);
        if (bit >= 0) {
            long mask = 1L << bit;
            if ((state.enableBits & mask) == 0L) return;
            state.enableBits &= ~mask;
        }
        OpenGLCallMapper.disable(cap);
    }

    private static int capToBit(int cap) {
        switch (cap) {
            case GL_DEPTH_TEST:   return State.BIT_DEPTH;
            case GL_BLEND:        return State.BIT_BLEND;
            case GL_CULL_FACE:    return State.BIT_CULL;
            case GL_SCISSOR_TEST: return State.BIT_SCISS;
            case GL_STENCIL_TEST: return State.BIT_STEN;
            default:              return -1;
        }
    }

    public void depthFunc(int func) {
        assertRenderThread();
        if (settings.cacheCommonState && state.depthFunc == func) return;
        state.depthFunc = func;
        OpenGLCallMapper.depthFunc(func);
    }

    public void depthMask(boolean flag) {
        assertRenderThread();
        if (settings.cacheCommonState && state.depthMask == flag) return;
        state.depthMask = flag;
        OpenGLCallMapper.depthMask(flag);
    }

    public void blendFunc(int sfactor, int dfactor) {
        assertRenderThread();
        if (settings.cacheCommonState &&
                state.blendSrcRGB == sfactor && state.blendDstRGB == dfactor &&
                state.blendSrcA == sfactor && state.blendDstA == dfactor) {
            return;
        }
        state.blendSrcRGB = sfactor;
        state.blendDstRGB = dfactor;
        state.blendSrcA = sfactor;
        state.blendDstA = dfactor;
        OpenGLCallMapper.blendFunc(sfactor, dfactor);
    }

    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcA, int dstA) {
        assertRenderThread();
        if (settings.cacheCommonState &&
                state.blendSrcRGB == srcRGB && state.blendDstRGB == dstRGB &&
                state.blendSrcA == srcA && state.blendDstA == dstA) {
            return;
        }
        state.blendSrcRGB = srcRGB;
        state.blendDstRGB = dstRGB;
        state.blendSrcA = srcA;
        state.blendDstA = dstA;
        OpenGLCallMapper.blendFuncSeparate(srcRGB, dstRGB, srcA, dstA);
    }

    // ---------------------------------------------------------------------
    // Lightweight diagnostics / getters
    // ---------------------------------------------------------------------

    public int getDetectedGLVersion() { return detectedGL; }
    public int getEffectiveGLVersion() { return effectiveGL; }
    public int getBufferOpsVersion() { return bufferOpsVersion; }

    public boolean isDSAAvailable() { return hasDSA; }
    public boolean isPersistentMappingAvailable() { return hasPersistentMapping; }
    public boolean isMultiDrawIndirectAvailable() { return hasMultiDrawIndirect; }

    // ---------------------------------------------------------------------
    // Shutdown: no leaks (pipelines + mapper caches)
    // ---------------------------------------------------------------------

    public void shutdown() {
        assertRenderThread();

        // Ask pipeline to cleanup if it owns any internal resources (should be lightweight).
        try {
            buffer.shutdown();
        } catch (Throwable ignored) {}

        // Clear mapper caches (shader/program caches, etc.) if present.
        try {
            OpenGLCallMapper.clearAllCaches();
        } catch (Throwable ignored) {}

        state.invalidate();

        synchronized (OpenGLManager.class) {
            PUBLISHED = null;
            FAST = null;
        }
    }
}
