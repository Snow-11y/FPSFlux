package com.example.gl.dispatch;

import com.example.gl.mapping.GLSLCallMapper;
import com.example.gl.mapping.OpenGLCallMapper;
import com.example.gl.mapping.SPIRVCallMapper;
import com.example.gl.mapping.VulkanCallMapper;

import java.lang.invoke.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.*;
import jdk.incubator.vector.*;

/**
 * Ultra-high-performance GL/GLSL call dispatcher with advanced safety guarantees.
 * 
 * <p>This dispatcher implements a zero-overhead abstraction layer that routes
 * immediate GL/GLSL calls to specialized mapper implementations with:
 * <ul>
 *   <li>Lock-free concurrent dispatch via VarHandles</li>
 *   <li>MethodHandle-based fast invocation with LambdaMetafactory</li>
 *   <li>NUMA-aware thread-local command buffering</li>
 *   <li>Adaptive JIT-friendly dispatch strategies</li>
 *   <li>Cache-line padded structures preventing false sharing</li>
 *   <li>Ring-buffer based command batching</li>
 *   <li>Circuit breaker pattern for fault tolerance</li>
 *   <li>Escape analysis optimized allocation patterns</li>
 * </ul>
 *
 * @author Advanced GL Systems
 * @version 3.0.0
 * @since JDK 21+
 */
@SuppressWarnings({"unchecked", "sunapi"})
public final class GLCallDispatcher {

    // ═══════════════════════════════════════════════════════════════════════════
    // COMPILE-TIME CONSTANTS - JIT will fold these
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final int CACHE_LINE_SIZE = 64;
    private static final int RING_BUFFER_SIZE = 1 << 16; // 65536 - power of 2 for fast modulo
    private static final int RING_BUFFER_MASK = RING_BUFFER_SIZE - 1;
    private static final int MAX_BATCH_SIZE = 1024;
    private static final int DISPATCH_TABLE_SIZE = 1 << 12; // 4096 entries
    private static final long SPIN_THRESHOLD_NS = 1000L;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int MAPPER_COUNT = 4;
    
    // Feature flags - can be set via system properties
    private static final boolean ENABLE_VALIDATION;
    private static final boolean ENABLE_TRACING;
    private static final boolean ENABLE_ASYNC_DISPATCH;
    private static final boolean ENABLE_COMMAND_RECORDING;
    private static final boolean ENABLE_METRICS;
    private static final boolean USE_UNSAFE_OPERATIONS;
    
    static {
        ENABLE_VALIDATION = Boolean.getBoolean("gl.dispatch.validation");
        ENABLE_TRACING = Boolean.getBoolean("gl.dispatch.tracing");
        ENABLE_ASYNC_DISPATCH = Boolean.getBoolean("gl.dispatch.async");
        ENABLE_COMMAND_RECORDING = Boolean.getBoolean("gl.dispatch.recording");
        ENABLE_METRICS = Boolean.getBoolean("gl.dispatch.metrics");
        USE_UNSAFE_OPERATIONS = Boolean.getBoolean("gl.dispatch.unsafe");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // API TYPE ENUMERATION - Optimized for switch dispatch
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Graphics API type enumeration with ordinal-based fast dispatch.
     * Ordinals are carefully assigned to enable tableswitch bytecode generation.
     */
    public enum APIType {
        OPENGL(0, "OpenGL", OpenGLCallMapper.class),
        GLSL(1, "GLSL", GLSLCallMapper.class),
        VULKAN(2, "Vulkan", VulkanCallMapper.class),
        SPIRV(3, "SPIR-V", SPIRVCallMapper.class);
        
        private final int dispatchIndex;
        private final String displayName;
        private final Class<?> mapperClass;
        
        APIType(int dispatchIndex, String displayName, Class<?> mapperClass) {
            this.dispatchIndex = dispatchIndex;
            this.displayName = displayName;
            this.mapperClass = mapperClass;
        }
        
        public int dispatchIndex() { return dispatchIndex; }
        public String displayName() { return displayName; }
        public Class<?> mapperClass() { return mapperClass; }
        
        // Pre-computed lookup table for O(1) access
        private static final APIType[] BY_INDEX = new APIType[MAPPER_COUNT];
        static {
            for (APIType type : values()) {
                BY_INDEX[type.dispatchIndex] = type;
            }
        }
        
        public static APIType fromIndex(int index) {
            return BY_INDEX[index & (MAPPER_COUNT - 1)]; // Branchless bounds check
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CACHE-LINE PADDED ATOMIC COUNTERS - Prevents false sharing
    // ═══════════════════════════════════════════════════════════════════════════
    
    @jdk.internal.vm.annotation.Contended
    private static final class PaddedAtomicLong extends AtomicLong {
        private static final long serialVersionUID = 1L;
        volatile long p1, p2, p3, p4, p5, p6, p7; // Padding to fill cache line
        
        PaddedAtomicLong(long initialValue) {
            super(initialValue);
        }
        
        // Prevent JVM from optimizing away padding
        public long preventOptimization() {
            return p1 + p2 + p3 + p4 + p5 + p6 + p7;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VARHANDLE DECLARATIONS - Lock-free memory access
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final VarHandle DISPATCH_STATE_HANDLE;
    private static final VarHandle SEQUENCE_HANDLE;
    private static final VarHandle CALL_COUNTER_HANDLE;
    private static final VarHandle CIRCUIT_STATE_HANDLE;
    private static final VarHandle ACTIVE_MAPPER_HANDLE;
    
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            
            DISPATCH_STATE_HANDLE = lookup.findVarHandle(
                GLCallDispatcher.class, "dispatchState", int.class);
            SEQUENCE_HANDLE = lookup.findVarHandle(
                GLCallDispatcher.class, "sequence", long.class);
            CALL_COUNTER_HANDLE = lookup.findVarHandle(
                GLCallDispatcher.class, "callCounter", long.class);
            CIRCUIT_STATE_HANDLE = lookup.findVarHandle(
                GLCallDispatcher.class, "circuitState", int.class);
            ACTIVE_MAPPER_HANDLE = lookup.findVarHandle(
                GLCallDispatcher.class, "activeMapperIndex", int.class);
                
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DISPATCH STATE MACHINE
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static final int STATE_UNINITIALIZED = 0;
    private static final int STATE_INITIALIZING = 1;
    private static final int STATE_READY = 2;
    private static final int STATE_DISPATCHING = 3;
    private static final int STATE_SUSPENDED = 4;
    private static final int STATE_SHUTDOWN = 5;
    
    // Circuit breaker states
    private static final int CIRCUIT_CLOSED = 0;
    private static final int CIRCUIT_OPEN = 1;
    private static final int CIRCUIT_HALF_OPEN = 2;

    // ═══════════════════════════════════════════════════════════════════════════
    // INSTANCE FIELDS - Memory layout optimized
    // ═══════════════════════════════════════════════════════════════════════════
    
    // Hot fields - frequently accessed, grouped together
    private volatile int dispatchState;
    private volatile long sequence;
    private volatile long callCounter;
    private volatile int circuitState;
    private volatile int activeMapperIndex;
    
    // Mapper instances - array for indexed access
    private final Object[] mappers;
    private final MethodHandle[] dispatchHandles;
    private final BiFunction<Integer, Object[], Object>[] fastPaths;
    
    // Thread-local command buffers
    private final ThreadLocal<CommandBuffer> threadLocalBuffer;
    
    // Ring buffer for async dispatch
    private final CommandSlot[] ringBuffer;
    private final PaddedAtomicLong producerSequence;
    private final PaddedAtomicLong consumerSequence;
    
    // Metrics (when enabled)
    private final DispatchMetrics metrics;
    
    // Validation layer
    private final ValidationLayer validationLayer;
    
    // Command recorder
    private final CommandRecorder recorder;
    
    // Dispatch table for function hash lookup
    private final DispatchEntry[] dispatchTable;
    
    // Stamped lock for configuration changes
    private final StampedLock configLock;
    
    // Executor for async dispatch
    private final ExecutorService asyncExecutor;
    
    // Weak reference cache for method handles
    private final ConcurrentHashMap<MethodSignature, WeakReference<MethodHandle>> handleCache;

    // ═══════════════════════════════════════════════════════════════════════════
    // SINGLETON PATTERN - Double-checked locking with VarHandle
    // ═══════════════════════════════════════════════════════════════════════════
    
    private static volatile GLCallDispatcher instance;
    private static final Object INSTANCE_LOCK = new Object();
    
    public static GLCallDispatcher getInstance() {
        GLCallDispatcher local = instance;
        if (local == null) {
            synchronized (INSTANCE_LOCK) {
                local = instance;
                if (local == null) {
                    instance = local = new GLCallDispatcher();
                }
            }
        }
        return local;
    }
    
    /**
     * Creates a new dispatcher instance (for testing/custom configurations).
     */
    public static GLCallDispatcher createInstance(DispatcherConfiguration config) {
        return new GLCallDispatcher(config);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════════════════
    
    private GLCallDispatcher() {
        this(DispatcherConfiguration.defaults());
    }
    
    private GLCallDispatcher(DispatcherConfiguration config) {
        // Initialize state
        this.dispatchState = STATE_UNINITIALIZED;
        this.sequence = 0L;
        this.callCounter = 0L;
        this.circuitState = CIRCUIT_CLOSED;
        this.activeMapperIndex = 0;
        
        // Initialize mappers array
        this.mappers = new Object[MAPPER_COUNT];
        this.dispatchHandles = new MethodHandle[MAPPER_COUNT];
        this.fastPaths = new BiFunction[MAPPER_COUNT];
        
        // Initialize thread-local buffers
        this.threadLocalBuffer = ThreadLocal.withInitial(() -> 
            new CommandBuffer(config.bufferSize()));
        
        // Initialize ring buffer
        this.ringBuffer = new CommandSlot[RING_BUFFER_SIZE];
        for (int i = 0; i < RING_BUFFER_SIZE; i++) {
            this.ringBuffer[i] = new CommandSlot();
        }
        this.producerSequence = new PaddedAtomicLong(-1L);
        this.consumerSequence = new PaddedAtomicLong(-1L);
        
        // Initialize metrics
        this.metrics = ENABLE_METRICS ? new DispatchMetrics() : DispatchMetrics.NOOP;
        
        // Initialize validation layer
        this.validationLayer = ENABLE_VALIDATION ? 
            new ValidationLayer() : ValidationLayer.NOOP;
        
        // Initialize command recorder
        this.recorder = ENABLE_COMMAND_RECORDING ? 
            new CommandRecorder(config.recordingBufferSize()) : CommandRecorder.NOOP;
        
        // Initialize dispatch table
        this.dispatchTable = new DispatchEntry[DISPATCH_TABLE_SIZE];
        
        // Initialize locks
        this.configLock = new StampedLock();
        
        // Initialize async executor
        if (ENABLE_ASYNC_DISPATCH) {
            this.asyncExecutor = new ThreadPoolExecutor(
                config.coreThreads(),
                config.maxThreads(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.queueCapacity()),
                new DispatcherThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
            );
        } else {
            this.asyncExecutor = null;
        }
        
        // Initialize handle cache
        this.handleCache = new ConcurrentHashMap<>(256);
        
        // Perform initialization
        initialize(config);
    }
    
    private void initialize(DispatcherConfiguration config) {
        // CAS to initializing state
        if (!DISPATCH_STATE_HANDLE.compareAndSet(this, STATE_UNINITIALIZED, STATE_INITIALIZING)) {
            throw new IllegalStateException("Dispatcher already initialized");
        }
        
        try {
            // Initialize mappers
            initializeMappers(config);
            
            // Build dispatch table
            buildDispatchTable();
            
            // Create fast paths via LambdaMetafactory
            createFastPaths();
            
            // Warm up JIT
            if (config.enableWarmup()) {
                warmUp();
            }
            
            // Transition to ready state
            DISPATCH_STATE_HANDLE.setRelease(this, STATE_READY);
            
        } catch (Exception e) {
            DISPATCH_STATE_HANDLE.setRelease(this, STATE_UNINITIALIZED);
            throw new DispatcherInitializationException("Failed to initialize dispatcher", e);
        }
    }
    
    private void initializeMappers(DispatcherConfiguration config) {
        mappers[APIType.OPENGL.dispatchIndex()] = createMapper(OpenGLCallMapper.class, config);
        mappers[APIType.GLSL.dispatchIndex()] = createMapper(GLSLCallMapper.class, config);
        mappers[APIType.VULKAN.dispatchIndex()] = createMapper(VulkanCallMapper.class, config);
        mappers[APIType.SPIRV.dispatchIndex()] = createMapper(SPIRVCallMapper.class, config);
    }
    
    @SuppressWarnings("unchecked")
    private <T> T createMapper(Class<T> mapperClass, DispatcherConfiguration config) {
        try {
            // Try configuration-based factory first
            if (config.hasMapperFactory(mapperClass)) {
                return config.createMapper(mapperClass);
            }
            
            // Fall back to reflection with MethodHandles
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                mapperClass, MethodHandles.lookup());
            MethodHandle constructor = lookup.findConstructor(
                mapperClass, MethodType.methodType(void.class));
            return (T) constructor.invoke();
            
        } catch (Throwable t) {
            throw new MapperCreationException("Failed to create mapper: " + mapperClass.getName(), t);
        }
    }
    
    private void buildDispatchTable() {
        // Pre-compute hash-based dispatch entries for common functions
        registerDispatchEntry("glBindBuffer", APIType.OPENGL, 0x0001);
        registerDispatchEntry("glBufferData", APIType.OPENGL, 0x0002);
        registerDispatchEntry("glDrawArrays", APIType.OPENGL, 0x0003);
        registerDispatchEntry("glDrawElements", APIType.OPENGL, 0x0004);
        registerDispatchEntry("glUseProgram", APIType.OPENGL, 0x0005);
        registerDispatchEntry("glUniform1f", APIType.OPENGL, 0x0006);
        registerDispatchEntry("glUniform2f", APIType.OPENGL, 0x0007);
        registerDispatchEntry("glUniform3f", APIType.OPENGL, 0x0008);
        registerDispatchEntry("glUniform4f", APIType.OPENGL, 0x0009);
        registerDispatchEntry("glUniformMatrix4fv", APIType.OPENGL, 0x000A);
        registerDispatchEntry("glVertexAttribPointer", APIType.OPENGL, 0x000B);
        registerDispatchEntry("glEnableVertexAttribArray", APIType.OPENGL, 0x000C);
        
        // GLSL entries
        registerDispatchEntry("compileShader", APIType.GLSL, 0x0101);
        registerDispatchEntry("linkProgram", APIType.GLSL, 0x0102);
        registerDispatchEntry("validateProgram", APIType.GLSL, 0x0103);
        
        // Vulkan entries  
        registerDispatchEntry("vkCreateInstance", APIType.VULKAN, 0x0201);
        registerDispatchEntry("vkCreateDevice", APIType.VULKAN, 0x0202);
        registerDispatchEntry("vkCmdDraw", APIType.VULKAN, 0x0203);
        registerDispatchEntry("vkCmdDrawIndexed", APIType.VULKAN, 0x0204);
        
        // SPIR-V entries
        registerDispatchEntry("spirvParse", APIType.SPIRV, 0x0301);
        registerDispatchEntry("spirvValidate", APIType.SPIRV, 0x0302);
        registerDispatchEntry("spirvOptimize", APIType.SPIRV, 0x0303);
    }
    
    private void registerDispatchEntry(String functionName, APIType apiType, int functionId) {
        int hash = functionHash(functionName);
        int index = hash & (DISPATCH_TABLE_SIZE - 1);
        
        // Linear probing for collision resolution
        while (dispatchTable[index] != null) {
            index = (index + 1) & (DISPATCH_TABLE_SIZE - 1);
        }
        
        dispatchTable[index] = new DispatchEntry(functionName, apiType, functionId, hash);
    }
    
    private static int functionHash(String functionName) {
        // FNV-1a hash for better distribution
        int hash = 0x811c9dc5;
        for (int i = 0; i < functionName.length(); i++) {
            hash ^= functionName.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }
    
    private void createFastPaths() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            
            for (int i = 0; i < MAPPER_COUNT; i++) {
                Object mapper = mappers[i];
                if (mapper != null) {
                    Class<?> mapperClass = mapper.getClass();
                    
                    // Find the dispatch method
                    MethodHandle dispatchHandle = lookup.findVirtual(
                        mapperClass,
                        "dispatch",
                        MethodType.methodType(Object.class, int.class, Object[].class)
                    );
                    
                    // Bind to instance
                    dispatchHandles[i] = dispatchHandle.bindTo(mapper);
                    
                    // Create lambda fast path via LambdaMetafactory
                    fastPaths[i] = createLambdaFastPath(mapper, mapperClass);
                }
            }
        } catch (Throwable t) {
            throw new DispatcherInitializationException("Failed to create fast paths", t);
        }
    }
    
    @SuppressWarnings("unchecked")
    private BiFunction<Integer, Object[], Object> createLambdaFastPath(
            Object mapper, Class<?> mapperClass) throws Throwable {
        
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
            mapperClass, MethodHandles.lookup());
        
        MethodHandle dispatchMH = lookup.findVirtual(
            mapperClass,
            "dispatch", 
            MethodType.methodType(Object.class, int.class, Object[].class)
        );
        
        CallSite callSite = LambdaMetafactory.metafactory(
            lookup,
            "apply",
            MethodType.methodType(BiFunction.class, mapperClass),
            MethodType.methodType(Object.class, Object.class, Object.class),
            dispatchMH,
            MethodType.methodType(Object.class, Integer.class, Object[].class)
        );
        
        return (BiFunction<Integer, Object[], Object>) callSite.getTarget().invoke(mapper);
    }
    
    private void warmUp() {
        // JIT warm-up with synthetic calls
        Object[] dummyArgs = new Object[0];
        for (int i = 0; i < 10000; i++) {
            for (int j = 0; j < MAPPER_COUNT; j++) {
                try {
                    dispatchInternal(j, 0, dummyArgs, false);
                } catch (Exception ignored) {
                    // Expected during warmup
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIMARY DISPATCH API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Dispatches a GL call to the appropriate mapper.
     * This is the primary entry point for synchronous dispatch.
     *
     * @param functionName the GL function name
     * @param args the function arguments
     * @return the result of the call
     */
    public Object dispatch(String functionName, Object... args) {
        // Fast path: use pre-computed dispatch table
        DispatchEntry entry = lookupDispatchEntry(functionName);
        if (entry != null) {
            return dispatchViaEntry(entry, args);
        }
        
        // Slow path: determine API type from function name
        APIType apiType = inferAPIType(functionName);
        return dispatch(apiType, functionName, args);
    }
    
    /**
     * Dispatches a call to a specific API mapper.
     *
     * @param apiType the target API type
     * @param functionName the function name  
     * @param args the function arguments
     * @return the result of the call
     */
    public Object dispatch(APIType apiType, String functionName, Object... args) {
        Objects.requireNonNull(apiType, "apiType");
        Objects.requireNonNull(functionName, "functionName");
        
        int functionId = functionHash(functionName);
        return dispatchInternal(apiType.dispatchIndex(), functionId, args, true);
    }
    
    /**
     * Dispatches a call by function ID for maximum performance.
     * Use when function ID is known at compile time.
     *
     * @param apiType the target API type
     * @param functionId the pre-computed function ID
     * @param args the function arguments
     * @return the result of the call
     */
    public Object dispatchById(APIType apiType, int functionId, Object... args) {
        return dispatchInternal(apiType.dispatchIndex(), functionId, args, true);
    }
    
    /**
     * Async dispatch variant for non-blocking operation.
     *
     * @param apiType the target API type
     * @param functionId the function ID
     * @param args the function arguments
     * @return a CompletableFuture with the result
     */
    public CompletableFuture<Object> dispatchAsync(APIType apiType, int functionId, Object... args) {
        if (!ENABLE_ASYNC_DISPATCH) {
            return CompletableFuture.completedFuture(
                dispatchInternal(apiType.dispatchIndex(), functionId, args, true));
        }
        
        return CompletableFuture.supplyAsync(
            () -> dispatchInternal(apiType.dispatchIndex(), functionId, args, true),
            asyncExecutor
        );
    }
    
    /**
     * Batch dispatch for multiple commands.
     *
     * @param commands the commands to dispatch
     * @return array of results
     */
    public Object[] dispatchBatch(DispatchCommand[] commands) {
        Objects.requireNonNull(commands, "commands");
        
        int length = commands.length;
        if (length == 0) {
            return new Object[0];
        }
        
        Object[] results = new Object[length];
        
        // Use thread-local buffer for batching
        CommandBuffer buffer = threadLocalBuffer.get();
        buffer.reset();
        
        for (int i = 0; i < length; i++) {
            DispatchCommand cmd = commands[i];
            results[i] = dispatchInternal(
                cmd.apiType().dispatchIndex(),
                cmd.functionId(),
                cmd.args(),
                true
            );
        }
        
        return results;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL DISPATCH IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private Object dispatchInternal(int mapperIndex, int functionId, Object[] args, boolean validate) {
        // Check circuit breaker
        if (circuitState != CIRCUIT_CLOSED) {
            return handleCircuitOpen(mapperIndex, functionId, args);
        }
        
        // Increment sequence (memory fence)
        long seq = (long) SEQUENCE_HANDLE.getAndAdd(this, 1L);
        
        // Metrics
        long startNanos = ENABLE_METRICS ? System.nanoTime() : 0L;
        
        try {
            // Validation layer
            if (validate && ENABLE_VALIDATION) {
                validationLayer.validateCall(mapperIndex, functionId, args);
            }
            
            // Recording
            if (ENABLE_COMMAND_RECORDING) {
                recorder.record(mapperIndex, functionId, args);
            }
            
            // Actual dispatch via fast path
            Object result = invokeFastPath(mapperIndex, functionId, args);
            
            // Metrics recording
            if (ENABLE_METRICS) {
                long elapsedNanos = System.nanoTime() - startNanos;
                metrics.recordSuccess(mapperIndex, functionId, elapsedNanos);
            }
            
            return result;
            
        } catch (Throwable t) {
            // Handle failure
            if (ENABLE_METRICS) {
                metrics.recordFailure(mapperIndex, functionId);
            }
            
            // Update circuit breaker
            handleFailure(mapperIndex, t);
            
            throw wrapException(t);
        } finally {
            // Increment call counter
            CALL_COUNTER_HANDLE.getAndAdd(this, 1L);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Object invokeFastPath(int mapperIndex, int functionId, Object[] args) {
        // Bounds check with branchless operation
        int safeIndex = mapperIndex & (MAPPER_COUNT - 1);
        
        BiFunction<Integer, Object[], Object> fastPath = fastPaths[safeIndex];
        if (fastPath != null) {
            return fastPath.apply(functionId, args);
        }
        
        // Fallback to method handle
        try {
            MethodHandle handle = dispatchHandles[safeIndex];
            if (handle != null) {
                return handle.invoke(functionId, args);
            }
        } catch (Throwable t) {
            throw wrapException(t);
        }
        
        throw new DispatchException("No mapper available for index: " + mapperIndex);
    }
    
    private DispatchEntry lookupDispatchEntry(String functionName) {
        int hash = functionHash(functionName);
        int index = hash & (DISPATCH_TABLE_SIZE - 1);
        
        // Linear probe lookup
        int probeCount = 0;
        while (probeCount < 16) { // Max probe depth
            DispatchEntry entry = dispatchTable[index];
            if (entry == null) {
                return null;
            }
            if (entry.hash == hash && entry.functionName.equals(functionName)) {
                return entry;
            }
            index = (index + 1) & (DISPATCH_TABLE_SIZE - 1);
            probeCount++;
        }
        
        return null;
    }
    
    private Object dispatchViaEntry(DispatchEntry entry, Object[] args) {
        return dispatchInternal(entry.apiType.dispatchIndex(), entry.functionId, args, true);
    }
    
    private APIType inferAPIType(String functionName) {
        // Prefix-based inference for common patterns
        if (functionName.startsWith("gl") && !functionName.startsWith("glsl")) {
            return APIType.OPENGL;
        }
        if (functionName.startsWith("glsl") || functionName.contains("Shader") || 
            functionName.contains("Program")) {
            return APIType.GLSL;
        }
        if (functionName.startsWith("vk")) {
            return APIType.VULKAN;
        }
        if (functionName.startsWith("spirv") || functionName.startsWith("spv")) {
            return APIType.SPIRV;
        }
        
        // Default to OpenGL
        return APIType.OPENGL;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CIRCUIT BREAKER IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    private Object handleCircuitOpen(int mapperIndex, int functionId, Object[] args) {
        int state = (int) CIRCUIT_STATE_HANDLE.getAcquire(this);
        
        if (state == CIRCUIT_OPEN) {
            throw new CircuitBreakerOpenException("Circuit breaker is open for mapper: " + mapperIndex);
        }
        
        if (state == CIRCUIT_HALF_OPEN) {
            // Attempt recovery
            try {
                Object result = invokeFastPath(mapperIndex, functionId, args);
                // Success - close circuit
                CIRCUIT_STATE_HANDLE.setRelease(this, CIRCUIT_CLOSED);
                return result;
            } catch (Throwable t) {
                // Failure - reopen circuit
                CIRCUIT_STATE_HANDLE.setRelease(this, CIRCUIT_OPEN);
                throw wrapException(t);
            }
        }
        
        throw new IllegalStateException("Unknown circuit state: " + state);
    }
    
    private void handleFailure(int mapperIndex, Throwable t) {
        if (ENABLE_METRICS) {
            int failureCount = metrics.getFailureCount(mapperIndex);
            if (failureCount > MAX_RETRY_ATTEMPTS) {
                CIRCUIT_STATE_HANDLE.setRelease(this, CIRCUIT_OPEN);
                scheduleCircuitReset(mapperIndex);
            }
        }
    }
    
    private void scheduleCircuitReset(int mapperIndex) {
        if (asyncExecutor != null) {
            asyncExecutor.schedule(() -> {
                CIRCUIT_STATE_HANDLE.setRelease(this, CIRCUIT_HALF_OPEN);
            }, 30, TimeUnit.SECONDS);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OPENGL-SPECIFIC DISPATCH METHODS (Type-safe wrappers)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * OpenGL-specific dispatcher with compile-time type safety.
     */
    public final class OpenGL {
        private static final int MAPPER_INDEX = 0;
        
        public void glBindBuffer(int target, int buffer) {
            dispatchInternal(MAPPER_INDEX, 0x0001, new Object[]{target, buffer}, true);
        }
        
        public void glBufferData(int target, long size, ByteBuffer data, int usage) {
            dispatchInternal(MAPPER_INDEX, 0x0002, new Object[]{target, size, data, usage}, true);
        }
        
        public void glDrawArrays(int mode, int first, int count) {
            dispatchInternal(MAPPER_INDEX, 0x0003, new Object[]{mode, first, count}, true);
        }
        
        public void glDrawElements(int mode, int count, int type, long indices) {
            dispatchInternal(MAPPER_INDEX, 0x0004, new Object[]{mode, count, type, indices}, true);
        }
        
        public void glUseProgram(int program) {
            dispatchInternal(MAPPER_INDEX, 0x0005, new Object[]{program}, true);
        }
        
        public void glUniform1f(int location, float v0) {
            dispatchInternal(MAPPER_INDEX, 0x0006, new Object[]{location, v0}, true);
        }
        
        public void glUniform2f(int location, float v0, float v1) {
            dispatchInternal(MAPPER_INDEX, 0x0007, new Object[]{location, v0, v1}, true);
        }
        
        public void glUniform3f(int location, float v0, float v1, float v2) {
            dispatchInternal(MAPPER_INDEX, 0x0008, new Object[]{location, v0, v1, v2}, true);
        }
        
        public void glUniform4f(int location, float v0, float v1, float v2, float v3) {
            dispatchInternal(MAPPER_INDEX, 0x0009, new Object[]{location, v0, v1, v2, v3}, true);
        }
        
        public void glUniformMatrix4fv(int location, int count, boolean transpose, float[] value) {
            dispatchInternal(MAPPER_INDEX, 0x000A, new Object[]{location, count, transpose, value}, true);
        }
    }
    
    /**
     * GLSL-specific dispatcher.
     */
    public final class GLSL {
        private static final int MAPPER_INDEX = 1;
        
        public int compileShader(int shaderType, String source) {
            return (int) dispatchInternal(MAPPER_INDEX, 0x0101, new Object[]{shaderType, source}, true);
        }
        
        public boolean linkProgram(int program) {
            return (boolean) dispatchInternal(MAPPER_INDEX, 0x0102, new Object[]{program}, true);
        }
        
        public boolean validateProgram(int program) {
            return (boolean) dispatchInternal(MAPPER_INDEX, 0x0103, new Object[]{program}, true);
        }
    }
    
    /**
     * Vulkan-specific dispatcher.
     */
    public final class Vulkan {
        private static final int MAPPER_INDEX = 2;
        
        public long vkCreateInstance(Object createInfo, Object allocator) {
            return (long) dispatchInternal(MAPPER_INDEX, 0x0201, new Object[]{createInfo, allocator}, true);
        }
        
        public long vkCreateDevice(long physicalDevice, Object createInfo, Object allocator) {
            return (long) dispatchInternal(MAPPER_INDEX, 0x0202, 
                new Object[]{physicalDevice, createInfo, allocator}, true);
        }
        
        public void vkCmdDraw(long commandBuffer, int vertexCount, int instanceCount, 
                              int firstVertex, int firstInstance) {
            dispatchInternal(MAPPER_INDEX, 0x0203, 
                new Object[]{commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance}, true);
        }
    }
    
    /**
     * SPIR-V specific dispatcher.
     */
    public final class SPIRV {
        private static final int MAPPER_INDEX = 3;
        
        public Object parse(ByteBuffer spirvBinary) {
            return dispatchInternal(MAPPER_INDEX, 0x0301, new Object[]{spirvBinary}, true);
        }
        
        public boolean validate(Object spirvModule) {
            return (boolean) dispatchInternal(MAPPER_INDEX, 0x0302, new Object[]{spirvModule}, true);
        }
        
        public ByteBuffer optimize(ByteBuffer spirvBinary, int optimizationLevel) {
            return (ByteBuffer) dispatchInternal(MAPPER_INDEX, 0x0303, 
                new Object[]{spirvBinary, optimizationLevel}, true);
        }
    }
    
    // Cached dispatcher instances
    private OpenGL openGLDispatcher;
    private GLSL glslDispatcher;
    private Vulkan vulkanDispatcher;
    private SPIRV spirvDispatcher;
    
    public OpenGL openGL() {
        if (openGLDispatcher == null) {
            openGLDispatcher = new OpenGL();
        }
        return openGLDispatcher;
    }
    
    public GLSL glsl() {
        if (glslDispatcher == null) {
            glslDispatcher = new GLSL();
        }
        return glslDispatcher;
    }
    
    public Vulkan vulkan() {
        if (vulkanDispatcher == null) {
            vulkanDispatcher = new Vulkan();
        }
        return vulkanDispatcher;
    }
    
    public SPIRV spirv() {
        if (spirvDispatcher == null) {
            spirvDispatcher = new SPIRV();
        }
        return spirvDispatcher;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RING BUFFER FOR ASYNC DISPATCH
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Publishes a command to the ring buffer for async processing.
     */
    public long publishCommand(int mapperIndex, int functionId, Object[] args) {
        long sequence;
        CommandSlot slot;
        
        do {
            sequence = producerSequence.get() + 1L;
            slot = ringBuffer[(int) (sequence & RING_BUFFER_MASK)];
            
            // Wait for slot to be available
            while (slot.sequence.get() != sequence - RING_BUFFER_SIZE) {
                Thread.onSpinWait();
            }
        } while (!producerSequence.compareAndSet(sequence - 1, sequence));
        
        // Write command
        slot.mapperIndex = mapperIndex;
        slot.functionId = functionId;
        slot.args = args;
        
        // Publish
        slot.sequence.setRelease(sequence);
        
        return sequence;
    }
    
    /**
     * Consumes commands from the ring buffer.
     */
    public void consumeCommands(int batchSize) {
        long nextSequence = consumerSequence.get() + 1L;
        long availableSequence = producerSequence.get();
        
        int count = 0;
        while (nextSequence <= availableSequence && count < batchSize) {
            CommandSlot slot = ringBuffer[(int) (nextSequence & RING_BUFFER_MASK)];
            
            // Wait for slot to be published
            while (slot.sequence.get() < nextSequence) {
                Thread.onSpinWait();
            }
            
            // Process command
            try {
                invokeFastPath(slot.mapperIndex, slot.functionId, slot.args);
            } catch (Throwable t) {
                // Log but continue processing
                handleFailure(slot.mapperIndex, t);
            }
            
            // Mark as consumed
            slot.sequence.set(nextSequence + RING_BUFFER_SIZE);
            
            nextSequence++;
            count++;
        }
        
        consumerSequence.set(nextSequence - 1);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════
    
    public void suspend() {
        DISPATCH_STATE_HANDLE.setRelease(this, STATE_SUSPENDED);
    }
    
    public void resume() {
        int expected = STATE_SUSPENDED;
        DISPATCH_STATE_HANDLE.compareAndSet(this, expected, STATE_READY);
    }
    
    public void shutdown() {
        DISPATCH_STATE_HANDLE.setRelease(this, STATE_SHUTDOWN);
        
        if (asyncExecutor != null) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        threadLocalBuffer.remove();
        handleCache.clear();
    }
    
    public boolean isReady() {
        return dispatchState == STATE_READY;
    }
    
    public DispatcherStatistics getStatistics() {
        return new DispatcherStatistics(
            callCounter,
            sequence,
            dispatchState,
            circuitState,
            metrics.snapshot()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXCEPTION HANDLING
    // ═══════════════════════════════════════════════════════════════════════════
    
    private RuntimeException wrapException(Throwable t) {
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        return new DispatchException("Dispatch failed", t);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INNER CLASSES - Supporting structures
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Command slot in ring buffer.
     */
    private static final class CommandSlot {
        final AtomicLong sequence = new AtomicLong(-1L);
        volatile int mapperIndex;
        volatile int functionId;
        volatile Object[] args;
    }
    
    /**
     * Dispatch table entry.
     */
    private static final class DispatchEntry {
        final String functionName;
        final APIType apiType;
        final int functionId;
        final int hash;
        
        DispatchEntry(String functionName, APIType apiType, int functionId, int hash) {
            this.functionName = functionName;
            this.apiType = apiType;
            this.functionId = functionId;
            this.hash = hash;
        }
    }
    
    /**
     * Thread-local command buffer for batching.
     */
    private static final class CommandBuffer {
        private final Object[] commands;
        private int position;
        
        CommandBuffer(int capacity) {
            this.commands = new Object[capacity * 3]; // mapperIndex, functionId, args
            this.position = 0;
        }
        
        void add(int mapperIndex, int functionId, Object[] args) {
            commands[position++] = mapperIndex;
            commands[position++] = functionId;
            commands[position++] = args;
        }
        
        void reset() {
            position = 0;
        }
        
        int size() {
            return position / 3;
        }
    }
    
    /**
     * Method signature for handle caching.
     */
    private record MethodSignature(Class<?> targetClass, String methodName, Class<?>[] paramTypes) {
        @Override
        public int hashCode() {
            return Objects.hash(targetClass, methodName, Arrays.hashCode(paramTypes));
        }
    }
    
    /**
     * Dispatcher thread factory for async execution.
     */
    private static final class DispatcherThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);
        
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "gl-dispatcher-" + counter.getAndIncrement());
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY - 1);
            return thread;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC RECORDS - API Types
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Dispatch command for batch operations.
     */
    public record DispatchCommand(APIType apiType, int functionId, Object[] args) {}
    
    /**
     * Dispatcher statistics snapshot.
     */
    public record DispatcherStatistics(
        long totalCalls,
        long sequence,
        int dispatchState,
        int circuitState,
        MetricsSnapshot metrics
    ) {}
    
    /**
     * Metrics snapshot.
     */
    public record MetricsSnapshot(
        long[] callCounts,
        long[] totalLatencyNanos,
        int[] failureCounts
    ) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Dispatcher configuration builder.
     */
    public static final class DispatcherConfiguration {
        private int bufferSize = 1024;
        private int recordingBufferSize = 65536;
        private int coreThreads = Runtime.getRuntime().availableProcessors();
        private int maxThreads = coreThreads * 2;
        private int queueCapacity = 10000;
        private boolean enableWarmup = true;
        private final Map<Class<?>, Supplier<?>> mapperFactories = new HashMap<>();
        
        public static DispatcherConfiguration defaults() {
            return new DispatcherConfiguration();
        }
        
        public DispatcherConfiguration bufferSize(int size) {
            this.bufferSize = size;
            return this;
        }
        
        public DispatcherConfiguration recordingBufferSize(int size) {
            this.recordingBufferSize = size;
            return this;
        }
        
        public DispatcherConfiguration coreThreads(int threads) {
            this.coreThreads = threads;
            return this;
        }
        
        public DispatcherConfiguration maxThreads(int threads) {
            this.maxThreads = threads;
            return this;
        }
        
        public DispatcherConfiguration queueCapacity(int capacity) {
            this.queueCapacity = capacity;
            return this;
        }
        
        public DispatcherConfiguration enableWarmup(boolean enable) {
            this.enableWarmup = enable;
            return this;
        }
        
        public <T> DispatcherConfiguration withMapperFactory(Class<T> type, Supplier<T> factory) {
            mapperFactories.put(type, factory);
            return this;
        }
        
        int bufferSize() { return bufferSize; }
        int recordingBufferSize() { return recordingBufferSize; }
        int coreThreads() { return coreThreads; }
        int maxThreads() { return maxThreads; }
        int queueCapacity() { return queueCapacity; }
        boolean enableWarmup() { return enableWarmup; }
        
        boolean hasMapperFactory(Class<?> type) {
            return mapperFactories.containsKey(type);
        }
        
        @SuppressWarnings("unchecked")
        <T> T createMapper(Class<T> type) {
            return (T) mapperFactories.get(type).get();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION LAYER
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Optional validation layer for debugging.
     */
    private static class ValidationLayer {
        static final ValidationLayer NOOP = new ValidationLayer() {
            @Override void validateCall(int mapperIndex, int functionId, Object[] args) {}
        };
        
        void validateCall(int mapperIndex, int functionId, Object[] args) {
            if (mapperIndex < 0 || mapperIndex >= MAPPER_COUNT) {
                throw new ValidationException("Invalid mapper index: " + mapperIndex);
            }
            if (functionId < 0) {
                throw new ValidationException("Invalid function ID: " + functionId);
            }
            if (args == null) {
                throw new ValidationException("Arguments cannot be null");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMAND RECORDER
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Command recorder for debugging/replay.
     */
    private static class CommandRecorder {
        static final CommandRecorder NOOP = new CommandRecorder(0) {
            @Override void record(int mapperIndex, int functionId, Object[] args) {}
        };
        
        private final ByteBuffer buffer;
        private int position;
        
        CommandRecorder(int capacity) {
            this.buffer = capacity > 0 ? 
                ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder()) : null;
            this.position = 0;
        }
        
        void record(int mapperIndex, int functionId, Object[] args) {
            if (buffer == null || position >= buffer.capacity() - 16) {
                return;
            }
            
            buffer.putInt(position, mapperIndex);
            buffer.putInt(position + 4, functionId);
            buffer.putInt(position + 8, args.length);
            buffer.putLong(position + 12, System.nanoTime());
            position += 20;
        }
        
        void reset() {
            position = 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // METRICS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * High-performance metrics collector.
     */
    private static class DispatchMetrics {
        static final DispatchMetrics NOOP = new DispatchMetrics() {
            @Override void recordSuccess(int mapperIndex, int functionId, long elapsedNanos) {}
            @Override void recordFailure(int mapperIndex, int functionId) {}
            @Override int getFailureCount(int mapperIndex) { return 0; }
            @Override MetricsSnapshot snapshot() { return new MetricsSnapshot(new long[0], new long[0], new int[0]); }
        };
        
        private final LongAdder[] callCounts;
        private final LongAdder[] totalLatency;
        private final AtomicIntegerArray failureCounts;
        
        DispatchMetrics() {
            this.callCounts = new LongAdder[MAPPER_COUNT];
            this.totalLatency = new LongAdder[MAPPER_COUNT];
            this.failureCounts = new AtomicIntegerArray(MAPPER_COUNT);
            
            for (int i = 0; i < MAPPER_COUNT; i++) {
                callCounts[i] = new LongAdder();
                totalLatency[i] = new LongAdder();
            }
        }
        
        void recordSuccess(int mapperIndex, int functionId, long elapsedNanos) {
            callCounts[mapperIndex].increment();
            totalLatency[mapperIndex].add(elapsedNanos);
            // Reset failure count on success
            failureCounts.set(mapperIndex, 0);
        }
        
        void recordFailure(int mapperIndex, int functionId) {
            failureCounts.incrementAndGet(mapperIndex);
        }
        
        int getFailureCount(int mapperIndex) {
            return failureCounts.get(mapperIndex);
        }
        
        MetricsSnapshot snapshot() {
            long[] counts = new long[MAPPER_COUNT];
            long[] latencies = new long[MAPPER_COUNT];
            int[] failures = new int[MAPPER_COUNT];
            
            for (int i = 0; i < MAPPER_COUNT; i++) {
                counts[i] = callCounts[i].sum();
                latencies[i] = totalLatency[i].sum();
                failures[i] = failureCounts.get(i);
            }
            
            return new MetricsSnapshot(counts, latencies, failures);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXCEPTIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    public static class DispatchException extends RuntimeException {
        public DispatchException(String message) { super(message); }
        public DispatchException(String message, Throwable cause) { super(message, cause); }
    }
    
    public static class DispatcherInitializationException extends RuntimeException {
        public DispatcherInitializationException(String message, Throwable cause) { 
            super(message, cause); 
        }
    }
    
    public static class MapperCreationException extends RuntimeException {
        public MapperCreationException(String message, Throwable cause) { 
            super(message, cause); 
        }
    }
    
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) { super(message); }
    }
    
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }
}
