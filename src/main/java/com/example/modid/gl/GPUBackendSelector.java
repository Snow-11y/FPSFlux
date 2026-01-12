package com.example.modid.gl;

import com.example.modid.FPSFlux;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/**
 * GPUBackendSelector - Advanced GPU backend selection and initialization with Java 21+ features.
 *
 * <p>Core Features:</p>
 * <ul>
 *   <li>Automatic backend detection with scoring</li>
 *   <li>Feature requirement validation</li>
 *   <li>Configurable fallback chains</li>
 *   <li>Async initialization support</li>
 *   <li>Backend lifecycle management</li>
 *   <li>Event-driven state changes</li>
 *   <li>Hot-reload support for development</li>
 *   <li>Capability-based selection</li>
 *   <li>Performance profiling</li>
 *   <li>Diagnostic and validation modes</li>
 * </ul>
 *
 * @author Enhanced GPU Framework
 * @version 2.0.0
 * @since Java 21
 */
public final class GPUBackendSelector implements AutoCloseable {

    // ========================================================================
    // SINGLETON
    // ========================================================================

    private static volatile GPUBackendSelector INSTANCE;
    private static final Object INSTANCE_LOCK = new Object();

    /**
     * Get singleton instance.
     */
    public static GPUBackendSelector instance() {
        GPUBackendSelector instance = INSTANCE;
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                instance = INSTANCE;
                if (instance == null) {
                    INSTANCE = instance = new GPUBackendSelector();
                }
            }
        }
        return instance;
    }

    // ========================================================================
    // ENUMS
    // ========================================================================

    /**
     * Backend preference for selection.
     */
    public enum PreferredBackend {
        /** Auto-detect best available backend */
        AUTO("Automatic", "Select best available backend based on capabilities"),
        
        /** Force OpenGL backend */
        OPENGL("OpenGL", "Use OpenGL backend (maximum compatibility)"),
        
        /** Force Vulkan backend */
        VULKAN("Vulkan", "Use Vulkan backend (maximum performance)"),
        
        /** Use software renderer (for testing/fallback) */
        SOFTWARE("Software", "Use software renderer (CPU-based)"),
        
        /** Headless mode (no display) */
        HEADLESS("Headless", "Headless rendering mode (offscreen only)"),
        
        /** Null backend (for testing) */
        NULL("Null", "Null backend for testing (no-op)");

        private final String displayName;
        private final String description;

        PreferredBackend(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() { return displayName; }
        public String description() { return description; }
    }

    /**
     * Backend selection strategy.
     */
    public enum SelectionStrategy {
        /** Select first working backend from preference list */
        FIRST_AVAILABLE,
        
        /** Select backend with highest capability score */
        HIGHEST_CAPABILITY,
        
        /** Select backend with best performance characteristics */
        BEST_PERFORMANCE,
        
        /** Select backend with lowest memory usage */
        LOWEST_MEMORY,
        
        /** Select based on custom scoring function */
        CUSTOM
    }

    /**
     * Initialization state.
     */
    public enum State {
        UNINITIALIZED,
        INITIALIZING,
        INITIALIZED,
        FAILED,
        SHUTTING_DOWN,
        SHUTDOWN
    }

    /**
     * Feature level requirements.
     */
    public enum FeatureLevel {
        /** Basic rendering (OpenGL 3.3 equivalent) */
        BASIC(33, "Basic rendering"),
        
        /** Compute shaders (OpenGL 4.3 equivalent) */
        COMPUTE(43, "Compute shader support"),
        
        /** Multi-draw indirect (OpenGL 4.3 equivalent) */
        INDIRECT(43, "Indirect draw support"),
        
        /** GPU-driven rendering (OpenGL 4.6 / Vulkan) */
        GPU_DRIVEN(46, "Full GPU-driven rendering"),
        
        /** Mesh shaders (Vulkan extension) */
        MESH_SHADERS(0, "Mesh shader support"),
        
        /** Ray tracing (Vulkan extension) */
        RAY_TRACING(0, "Ray tracing support");

        private final int minGLVersion;
        private final String description;

        FeatureLevel(int minGLVersion, String description) {
            this.minGLVersion = minGLVersion;
            this.description = description;
        }

        public int minGLVersion() { return minGLVersion; }
        public String description() { return description; }
    }

    // ========================================================================
    // RECORDS
    // ========================================================================

    /**
     * Backend selection configuration.
     */
    public record Config(
        PreferredBackend preferred,
        SelectionStrategy strategy,
        List<PreferredBackend> fallbackChain,
        Set<FeatureLevel> requiredFeatures,
        Set<FeatureLevel> optionalFeatures,
        boolean enableValidation,
        boolean enableProfiling,
        boolean enableDebugOutput,
        boolean allowSoftwareFallback,
        boolean asyncInitialization,
        Duration initTimeout,
        int maxRetries,
        Map<String, Object> backendOptions
    ) {
        public static Config defaults() {
            return new Config(
                PreferredBackend.AUTO,
                SelectionStrategy.HIGHEST_CAPABILITY,
                List.of(PreferredBackend.VULKAN, PreferredBackend.OPENGL),
                Set.of(FeatureLevel.BASIC),
                Set.of(FeatureLevel.COMPUTE, FeatureLevel.INDIRECT),
                false,
                true,
                false,
                false,
                false,
                Duration.ofSeconds(30),
                3,
                Map.of()
            );
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private PreferredBackend preferred = PreferredBackend.AUTO;
            private SelectionStrategy strategy = SelectionStrategy.HIGHEST_CAPABILITY;
            private List<PreferredBackend> fallbackChain = new ArrayList<>(
                List.of(PreferredBackend.VULKAN, PreferredBackend.OPENGL));
            private Set<FeatureLevel> requiredFeatures = EnumSet.of(FeatureLevel.BASIC);
            private Set<FeatureLevel> optionalFeatures = EnumSet.of(FeatureLevel.COMPUTE, FeatureLevel.INDIRECT);
            private boolean enableValidation = false;
            private boolean enableProfiling = true;
            private boolean enableDebugOutput = false;
            private boolean allowSoftwareFallback = false;
            private boolean asyncInitialization = false;
            private Duration initTimeout = Duration.ofSeconds(30);
            private int maxRetries = 3;
            private Map<String, Object> backendOptions = new HashMap<>();

            public Builder preferred(PreferredBackend val) { preferred = val; return this; }
            public Builder strategy(SelectionStrategy val) { strategy = val; return this; }
            public Builder fallbackChain(PreferredBackend... backends) {
                fallbackChain = new ArrayList<>(List.of(backends));
                return this;
            }
            public Builder require(FeatureLevel... features) {
                requiredFeatures = EnumSet.copyOf(List.of(features));
                return this;
            }
            public Builder optional(FeatureLevel... features) {
                optionalFeatures = EnumSet.copyOf(List.of(features));
                return this;
            }
            public Builder enableValidation(boolean val) { enableValidation = val; return this; }
            public Builder enableProfiling(boolean val) { enableProfiling = val; return this; }
            public Builder enableDebugOutput(boolean val) { enableDebugOutput = val; return this; }
            public Builder allowSoftwareFallback(boolean val) { allowSoftwareFallback = val; return this; }
            public Builder asyncInitialization(boolean val) { asyncInitialization = val; return this; }
            public Builder initTimeout(Duration val) { initTimeout = val; return this; }
            public Builder maxRetries(int val) { maxRetries = val; return this; }
            public Builder option(String key, Object value) {
                backendOptions.put(key, value);
                return this;
            }
            public Builder options(Map<String, Object> opts) {
                backendOptions.putAll(opts);
                return this;
            }

            // Convenience methods
            public Builder forDevelopment() {
                return enableValidation(true)
                    .enableDebugOutput(true)
                    .enableProfiling(true);
            }

            public Builder forProduction() {
                return enableValidation(false)
                    .enableDebugOutput(false)
                    .enableProfiling(false);
            }

            public Builder forGpuDriven() {
                return require(FeatureLevel.GPU_DRIVEN)
                    .strategy(SelectionStrategy.HIGHEST_CAPABILITY);
            }

            public Builder preferVulkan() {
                return preferred(PreferredBackend.VULKAN)
                    .fallbackChain(PreferredBackend.VULKAN, PreferredBackend.OPENGL);
            }

            public Builder preferOpenGL() {
                return preferred(PreferredBackend.OPENGL)
                    .fallbackChain(PreferredBackend.OPENGL, PreferredBackend.VULKAN);
            }

            public Config build() {
                return new Config(
                    preferred, strategy, List.copyOf(fallbackChain),
                    Set.copyOf(requiredFeatures), Set.copyOf(optionalFeatures),
                    enableValidation, enableProfiling, enableDebugOutput,
                    allowSoftwareFallback, asyncInitialization, initTimeout,
                    maxRetries, Map.copyOf(backendOptions)
                );
            }
        }
    }

    /**
     * Backend initialization result.
     */
    public record InitResult(
        boolean success,
        GPUBackend backend,
        GPUBackend.Type type,
        String version,
        Duration initTime,
        List<FeatureLevel> supportedFeatures,
        List<FeatureLevel> missingFeatures,
        int capabilityScore,
        List<String> warnings,
        Throwable error
    ) {
        public static InitResult success(GPUBackend backend, Duration initTime,
                                         List<FeatureLevel> supported, int score) {
            return new InitResult(
                true, backend, backend.getType(), backend.getVersionString(),
                initTime, supported, List.of(), score, List.of(), null
            );
        }

        public static InitResult failure(Throwable error, List<String> warnings) {
            return new InitResult(
                false, null, null, null, Duration.ZERO,
                List.of(), List.of(), 0, warnings, error
            );
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * Backend capability score.
     */
    public record CapabilityScore(
        GPUBackend.Type type,
        int totalScore,
        Map<String, Integer> categoryScores,
        Set<FeatureLevel> supportedFeatures,
        boolean meetsRequirements
    ) implements Comparable<CapabilityScore> {
        @Override
        public int compareTo(CapabilityScore other) {
            return Integer.compare(other.totalScore, this.totalScore);
        }
    }

    /**
     * Backend probe result (before full initialization).
     */
    public record ProbeResult(
        GPUBackend.Type type,
        boolean available,
        String version,
        CapabilityScore score,
        Duration probeTime,
        String unavailableReason
    ) {}

    /**
     * Initialization event.
     */
    public sealed interface InitEvent permits
            InitEvent.Starting,
            InitEvent.ProbeComplete,
            InitEvent.BackendSelected,
            InitEvent.InitializingBackend,
            InitEvent.BackendReady,
            InitEvent.BackendFailed,
            InitEvent.FallbackTriggered,
            InitEvent.Complete,
            InitEvent.Shutdown {

        record Starting(Config config, Instant timestamp) implements InitEvent {}
        record ProbeComplete(List<ProbeResult> results, Instant timestamp) implements InitEvent {}
        record BackendSelected(GPUBackend.Type type, CapabilityScore score, Instant timestamp) implements InitEvent {}
        record InitializingBackend(GPUBackend.Type type, int attempt, Instant timestamp) implements InitEvent {}
        record BackendReady(GPUBackend backend, Duration initTime, Instant timestamp) implements InitEvent {}
        record BackendFailed(GPUBackend.Type type, Throwable error, Instant timestamp) implements InitEvent {}
        record FallbackTriggered(GPUBackend.Type from, GPUBackend.Type to, String reason, Instant timestamp) implements InitEvent {}
        record Complete(InitResult result, Instant timestamp) implements InitEvent {}
        record Shutdown(GPUBackend.Type type, Duration uptime, Instant timestamp) implements InitEvent {}
    }

    // ========================================================================
    // STATE
    // ========================================================================

    private volatile Config config = Config.defaults();
    private volatile GPUBackend activeBackend;
    private volatile State state = State.UNINITIALIZED;
    private volatile InitResult lastResult;
    private final Instant creationTime = Instant.now();

    // Probed backends
    private final Map<GPUBackend.Type, ProbeResult> probeResults = new ConcurrentHashMap<>();
    private final Map<GPUBackend.Type, GPUBackend> availableBackends = new ConcurrentHashMap<>();

    // Event listeners
    private final List<Consumer<InitEvent>> eventListeners = new CopyOnWriteArrayList<>();

    // Statistics
    private final AtomicInteger initAttempts = new AtomicInteger(0);
    private final AtomicInteger failedAttempts = new AtomicInteger(0);
    private volatile Instant initStartTime;
    private volatile Instant initEndTime;

    // Async initialization
    private volatile CompletableFuture<InitResult> asyncInitFuture;

    // Custom scoring function
    private volatile Function<GPUBackend.Capabilities, Integer> customScoringFunction;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    private GPUBackendSelector() {
        // Private constructor for singleton
    }

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    /**
     * Initialize with default configuration.
     */
    public static GPUBackend initialize() {
        return initialize(PreferredBackend.AUTO);
    }

    /**
     * Initialize with preferred backend (legacy API).
     */
    public static GPUBackend initialize(PreferredBackend preferred) {
        return initialize(Config.builder().preferred(preferred).build());
    }

    /**
     * Initialize with full configuration.
     */
    public static GPUBackend initialize(Config config) {
        GPUBackendSelector selector = instance();
        InitResult result = selector.initializeInternal(config);

        if (!result.success()) {
            throw new BackendInitializationException(
                "Failed to initialize GPU backend", result.error());
        }

        // Register with GPUBackendRegistry
        GPUBackendRegistry.setActive(result.backend());

        return result.backend();
    }

    /**
     * Initialize asynchronously.
     */
    public static CompletableFuture<GPUBackend> initializeAsync(Config config) {
        GPUBackendSelector selector = instance();
        return selector.initializeAsyncInternal(config)
            .thenApply(result -> {
                if (!result.success()) {
                    throw new CompletionException(new BackendInitializationException(
                        "Failed to initialize GPU backend", result.error()));
                }
                GPUBackendRegistry.setActive(result.backend());
                return result.backend();
            });
    }

    /**
     * Internal initialization logic.
     */
    private InitResult initializeInternal(Config config) {
        if (state == State.INITIALIZED && activeBackend != null) {
            FPSFlux.LOGGER.warn("[GPU] Backend already initialized, returning existing");
            return lastResult;
        }

        this.config = config;
        this.state = State.INITIALIZING;
        this.initStartTime = Instant.now();

        publishEvent(new InitEvent.Starting(config, Instant.now()));

        try {
            // Probe available backends
            List<ProbeResult> probes = probeBackends();
            publishEvent(new InitEvent.ProbeComplete(probes, Instant.now()));

            // Select backend based on strategy
            GPUBackend.Type selectedType = selectBackend(probes);
            if (selectedType == null) {
                throw new BackendInitializationException("No suitable backend found");
            }

            CapabilityScore score = probeResults.get(selectedType).score();
            publishEvent(new InitEvent.BackendSelected(selectedType, score, Instant.now()));

            // Initialize selected backend with retries
            InitResult result = initializeWithRetries(selectedType);

            this.initEndTime = Instant.now();
            this.lastResult = result;

            if (result.success()) {
                this.activeBackend = result.backend();
                this.state = State.INITIALIZED;
                publishEvent(new InitEvent.BackendReady(
                    result.backend(), result.initTime(), Instant.now()));
            } else {
                this.state = State.FAILED;
            }

            publishEvent(new InitEvent.Complete(result, Instant.now()));

            logInitResult(result);
            return result;

        } catch (Exception e) {
            this.state = State.FAILED;
            this.initEndTime = Instant.now();

            InitResult result = InitResult.failure(e, List.of(e.getMessage()));
            this.lastResult = result;

            publishEvent(new InitEvent.Complete(result, Instant.now()));
            FPSFlux.LOGGER.error("[GPU] Backend initialization failed", e);

            return result;
        }
    }

    /**
     * Async initialization.
     */
    private CompletableFuture<InitResult> initializeAsyncInternal(Config config) {
        if (asyncInitFuture != null && !asyncInitFuture.isDone()) {
            return asyncInitFuture;
        }

        asyncInitFuture = CompletableFuture.supplyAsync(() -> initializeInternal(config));

        if (config.initTimeout().toMillis() > 0) {
            asyncInitFuture = asyncInitFuture.orTimeout(
                config.initTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        return asyncInitFuture;
    }

    // ========================================================================
    // BACKEND PROBING
    // ========================================================================

    /**
     * Probe all available backends.
     */
    private List<ProbeResult> probeBackends() {
        List<ProbeResult> results = new ArrayList<>();

        // Probe Vulkan
        results.add(probeBackend(GPUBackend.Type.VULKAN));

        // Probe OpenGL
        results.add(probeBackend(GPUBackend.Type.OPENGL));

        // Store results
        for (ProbeResult result : results) {
            probeResults.put(result.type(), result);
        }

        return results;
    }

    /**
     * Probe a specific backend.
     */
    private ProbeResult probeBackend(GPUBackend.Type type) {
        Instant start = Instant.now();

        try {
            GPUBackend backend = createBackendInstance(type);
            if (backend == null) {
                return new ProbeResult(type, false, null, null,
                    Duration.between(start, Instant.now()), "Backend not available");
            }

            // Try to initialize
            boolean initialized = initializeBackendInstance(backend);
            if (!initialized) {
                return new ProbeResult(type, false, null, null,
                    Duration.between(start, Instant.now()), "Initialization failed");
            }

            // Score capabilities
            CapabilityScore score = scoreBackend(backend);

            // Store for later use
            availableBackends.put(type, backend);

            return new ProbeResult(
                type, true, backend.getVersionString(), score,
                Duration.between(start, Instant.now()), null
            );

        } catch (Exception e) {
            FPSFlux.LOGGER.debug("[GPU] Probe failed for {}: {}", type, e.getMessage());
            return new ProbeResult(type, false, null, null,
                Duration.between(start, Instant.now()), e.getMessage());
        }
    }

    /**
     * Create backend instance by type.
     */
    private GPUBackend createBackendInstance(GPUBackend.Type type) {
        return switch (type) {
            case VULKAN -> VulkanBackend.get();
            case OPENGL -> OpenGLBackend.get();
            default -> null;
        };
    }

    /**
     * Initialize backend instance.
     */
    private boolean initializeBackendInstance(GPUBackend backend) {
        return switch (backend) {
            case VulkanBackend vk -> vk.initialize();
            case OpenGLBackend gl -> gl.initialize();
            default -> false;
        };
    }

    // ========================================================================
    // BACKEND SELECTION
    // ========================================================================

    /**
     * Select best backend based on strategy.
     */
    private GPUBackend.Type selectBackend(List<ProbeResult> probes) {
        // Filter available backends
        List<ProbeResult> available = probes.stream()
            .filter(ProbeResult::available)
            .filter(p -> p.score().meetsRequirements())
            .toList();

        if (available.isEmpty()) {
            // Check if any backend is available at all
            List<ProbeResult> anyAvailable = probes.stream()
                .filter(ProbeResult::available)
                .toList();

            if (anyAvailable.isEmpty()) {
                return null;
            }

            // If requirements not met but software fallback allowed
            if (config.allowSoftwareFallback()) {
                FPSFlux.LOGGER.warn("[GPU] No backend meets requirements, using best available");
                available = anyAvailable;
            } else {
                FPSFlux.LOGGER.error("[GPU] No backend meets feature requirements");
                return null;
            }
        }

        // Handle specific preference
        if (config.preferred() != PreferredBackend.AUTO) {
            GPUBackend.Type preferredType = preferenceToType(config.preferred());
            if (preferredType != null) {
                Optional<ProbeResult> preferred = available.stream()
                    .filter(p -> p.type() == preferredType)
                    .findFirst();

                if (preferred.isPresent()) {
                    return preferred.get().type();
                }

                FPSFlux.LOGGER.warn("[GPU] Preferred backend {} not available", config.preferred());
            }
        }

        // Apply selection strategy
        return switch (config.strategy()) {
            case FIRST_AVAILABLE -> selectFirstAvailable(available);
            case HIGHEST_CAPABILITY -> selectHighestCapability(available);
            case BEST_PERFORMANCE -> selectBestPerformance(available);
            case LOWEST_MEMORY -> selectLowestMemory(available);
            case CUSTOM -> selectCustom(available);
        };
    }

    private GPUBackend.Type selectFirstAvailable(List<ProbeResult> available) {
        for (PreferredBackend pref : config.fallbackChain()) {
            GPUBackend.Type type = preferenceToType(pref);
            if (type != null && available.stream().anyMatch(p -> p.type() == type)) {
                return type;
            }
        }
        return available.isEmpty() ? null : available.get(0).type();
    }

    private GPUBackend.Type selectHighestCapability(List<ProbeResult> available) {
        return available.stream()
            .max(Comparator.comparing(p -> p.score().totalScore()))
            .map(ProbeResult::type)
            .orElse(null);
    }

    private GPUBackend.Type selectBestPerformance(List<ProbeResult> available) {
        // Prefer Vulkan for performance
        Optional<ProbeResult> vulkan = available.stream()
            .filter(p -> p.type() == GPUBackend.Type.VULKAN)
            .findFirst();

        if (vulkan.isPresent()) {
            return vulkan.get().type();
        }

        return selectHighestCapability(available);
    }

    private GPUBackend.Type selectLowestMemory(List<ProbeResult> available) {
        // OpenGL typically has lower memory overhead
        Optional<ProbeResult> opengl = available.stream()
            .filter(p -> p.type() == GPUBackend.Type.OPENGL)
            .findFirst();

        if (opengl.isPresent()) {
            return opengl.get().type();
        }

        return selectFirstAvailable(available);
    }

    private GPUBackend.Type selectCustom(List<ProbeResult> available) {
        if (customScoringFunction == null) {
            return selectHighestCapability(available);
        }

        return available.stream()
            .max(Comparator.comparingInt(p -> {
                GPUBackend backend = availableBackends.get(p.type());
                if (backend != null) {
                    return customScoringFunction.apply(backend.getCapabilities());
                }
                return 0;
            }))
            .map(ProbeResult::type)
            .orElse(null);
    }

    private GPUBackend.Type preferenceToType(PreferredBackend pref) {
        return switch (pref) {
            case VULKAN -> GPUBackend.Type.VULKAN;
            case OPENGL -> GPUBackend.Type.OPENGL;
            default -> null;
        };
    }

    // ========================================================================
    // CAPABILITY SCORING
    // ========================================================================

    /**
     * Score backend capabilities.
     */
    private CapabilityScore scoreBackend(GPUBackend backend) {
        GPUBackend.Capabilities caps = backend.getCapabilities();
        Map<String, Integer> categoryScores = new HashMap<>();
        Set<FeatureLevel> supported = EnumSet.noneOf(FeatureLevel.class);
        int totalScore = 0;

        // Base score for being available
        totalScore += 100;
        categoryScores.put("base", 100);

        // Modern API bonus
        if (backend.getType().isModernApi()) {
            totalScore += 50;
            categoryScores.put("modern_api", 50);
        }

        // Feature scores
        int featureScore = 0;

        if (caps.computeShaders()) {
            featureScore += 100;
            supported.add(FeatureLevel.COMPUTE);
        }

        if (caps.multiDrawIndirect()) {
            featureScore += 80;
            supported.add(FeatureLevel.INDIRECT);
        }

        if (caps.indirectCount()) {
            featureScore += 100;
            supported.add(FeatureLevel.GPU_DRIVEN);
        }

        if (caps.meshShaders()) {
            featureScore += 150;
            supported.add(FeatureLevel.MESH_SHADERS);
        }

        if (caps.rayTracing()) {
            featureScore += 200;
            supported.add(FeatureLevel.RAY_TRACING);
        }

        if (caps.bufferDeviceAddress()) {
            featureScore += 50;
        }

        if (caps.persistentMapping()) {
            featureScore += 40;
        }

        if (caps.bindlessTextures()) {
            featureScore += 60;
        }

        totalScore += featureScore;
        categoryScores.put("features", featureScore);

        // Always support basic
        supported.add(FeatureLevel.BASIC);

        // Limits score
        int limitsScore = 0;
        limitsScore += Math.min(caps.maxComputeWorkGroupSize() / 10, 100);
        limitsScore += Math.min(caps.maxDrawIndirectCount() / 1000, 50);
        limitsScore += Math.min(caps.maxTextureSize() / 1024, 50);

        totalScore += limitsScore;
        categoryScores.put("limits", limitsScore);

        // Check if requirements are met
        boolean meetsRequirements = supported.containsAll(config.requiredFeatures());

        return new CapabilityScore(
            backend.getType(), totalScore, categoryScores, supported, meetsRequirements
        );
    }

    // ========================================================================
    // INITIALIZATION WITH RETRIES
    // ========================================================================

    /**
     * Initialize backend with retry logic.
     */
    private InitResult initializeWithRetries(GPUBackend.Type type) {
        GPUBackend backend = availableBackends.get(type);
        List<String> warnings = new ArrayList<>();
        Throwable lastError = null;

        for (int attempt = 1; attempt <= config.maxRetries(); attempt++) {
            initAttempts.incrementAndGet();
            publishEvent(new InitEvent.InitializingBackend(type, attempt, Instant.now()));

            Instant start = Instant.now();

            try {
                // Backend should already be initialized from probe
                if (backend != null && backend.isInitialized()) {
                    Duration initTime = Duration.between(start, Instant.now());
                    CapabilityScore score = probeResults.get(type).score();

                    return InitResult.success(
                        backend, initTime,
                        new ArrayList<>(score.supportedFeatures()),
                        score.totalScore()
                    );
                }

                // Try to reinitialize
                backend = createBackendInstance(type);
                if (backend != null && initializeBackendInstance(backend)) {
                    Duration initTime = Duration.between(start, Instant.now());
                    CapabilityScore score = scoreBackend(backend);

                    return InitResult.success(
                        backend, initTime,
                        new ArrayList<>(score.supportedFeatures()),
                        score.totalScore()
                    );
                }

                warnings.add("Attempt " + attempt + ": Initialization returned false");

            } catch (Exception e) {
                lastError = e;
                failedAttempts.incrementAndGet();
                warnings.add("Attempt " + attempt + ": " + e.getMessage());

                publishEvent(new InitEvent.BackendFailed(type, e, Instant.now()));

                FPSFlux.LOGGER.warn("[GPU] Initialization attempt {} failed: {}",
                    attempt, e.getMessage());

                // Wait before retry
                if (attempt < config.maxRetries()) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // All retries failed, try fallback
        return tryFallback(type, warnings, lastError);
    }

    /**
     * Try fallback backends.
     */
    private InitResult tryFallback(GPUBackend.Type failedType, List<String> warnings, Throwable lastError) {
        for (PreferredBackend pref : config.fallbackChain()) {
            GPUBackend.Type fallbackType = preferenceToType(pref);
            if (fallbackType == null || fallbackType == failedType) {
                continue;
            }

            ProbeResult probe = probeResults.get(fallbackType);
            if (probe == null || !probe.available()) {
                continue;
            }

            publishEvent(new InitEvent.FallbackTriggered(
                failedType, fallbackType, "Primary backend failed", Instant.now()));

            FPSFlux.LOGGER.info("[GPU] Falling back from {} to {}",
                failedType, fallbackType);

            GPUBackend backend = availableBackends.get(fallbackType);
            if (backend != null && backend.isInitialized()) {
                CapabilityScore score = probe.score();
                warnings.add("Using fallback backend: " + fallbackType);

                return new InitResult(
                    true, backend, fallbackType, backend.getVersionString(),
                    probe.probeTime(),
                    new ArrayList<>(score.supportedFeatures()),
                    new ArrayList<>(),
                    score.totalScore(),
                    warnings, null
                );
            }
        }

        // No fallback available
        return InitResult.failure(
            lastError != null ? lastError : new BackendInitializationException("All backends failed"),
            warnings
        );
    }

    // ========================================================================
    // EVENT SYSTEM
    // ========================================================================

    /**
     * Add event listener.
     */
    public void addEventListener(Consumer<InitEvent> listener) {
        eventListeners.add(listener);
    }

    /**
     * Remove event listener.
     */
    public void removeEventListener(Consumer<InitEvent> listener) {
        eventListeners.remove(listener);
    }

    /**
     * Publish event to listeners.
     */
    private void publishEvent(InitEvent event) {
        for (Consumer<InitEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                FPSFlux.LOGGER.warn("[GPU] Event listener error: {}", e.getMessage());
            }
        }
    }

    // ========================================================================
    // ACCESSORS
    // ========================================================================

    /**
     * Get active backend.
     */
    public static GPUBackend get() {
        GPUBackendSelector selector = instance();
        if (selector.activeBackend == null) {
            throw new IllegalStateException("GPU backend not initialized. Call initialize() first.");
        }
        return selector.activeBackend;
    }

    /**
     * Get active backend (optional).
     */
    public static Optional<GPUBackend> getOptional() {
        return Optional.ofNullable(instance().activeBackend);
    }

    /**
     * Check if initialized.
     */
    public static boolean isInitialized() {
        return instance().state == State.INITIALIZED && instance().activeBackend != null;
    }

    /**
     * Get backend type.
     */
    public static GPUBackend.Type getType() {
        GPUBackend backend = instance().activeBackend;
        return backend != null ? backend.getType() : null;
    }

    /**
     * Get current state.
     */
    public State getState() {
        return state;
    }

    /**
     * Get last initialization result.
     */
    public Optional<InitResult> getLastResult() {
        return Optional.ofNullable(lastResult);
    }

    /**
     * Get probe results.
     */
    public Map<GPUBackend.Type, ProbeResult> getProbeResults() {
        return Collections.unmodifiableMap(probeResults);
    }

    /**
     * Get available backends.
     */
    public Set<GPUBackend.Type> getAvailableBackends() {
        return availableBackends.keySet();
    }

    /**
     * Get uptime.
     */
    public Duration getUptime() {
        if (initEndTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(initEndTime, Instant.now());
    }

    /**
     * Set custom scoring function.
     */
    public void setCustomScoringFunction(Function<GPUBackend.Capabilities, Integer> function) {
        this.customScoringFunction = function;
    }

    // ========================================================================
    // HOT RELOAD (DEVELOPMENT)
    // ========================================================================

    /**
     * Reinitialize with different backend (development only).
     */
    public InitResult hotReload(PreferredBackend newPreferred) {
        FPSFlux.LOGGER.info("[GPU] Hot-reloading backend to {}", newPreferred);

        // Shutdown current
        if (activeBackend != null) {
            try {
                if (activeBackend instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } catch (Exception e) {
                FPSFlux.LOGGER.warn("[GPU] Error closing old backend: {}", e.getMessage());
            }
        }

        // Clear state
        activeBackend = null;
        state = State.UNINITIALIZED;
        probeResults.clear();
        availableBackends.clear();

        // Reinitialize
        Config newConfig = Config.builder()
            .preferred(newPreferred)
            .strategy(config.strategy())
            .require(config.requiredFeatures().toArray(FeatureLevel[]::new))
            .enableValidation(config.enableValidation())
            .enableProfiling(config.enableProfiling())
            .enableDebugOutput(config.enableDebugOutput())
            .build();

        return initializeInternal(newConfig);
    }

    // ========================================================================
    // STATISTICS
    // ========================================================================

    /**
     * Get selector statistics.
     */
    public SelectorStats getStats() {
        return new SelectorStats(
            state,
            activeBackend != null ? activeBackend.getType() : null,
            initAttempts.get(),
            failedAttempts.get(),
            initStartTime != null && initEndTime != null
                ? Duration.between(initStartTime, initEndTime)
                : Duration.ZERO,
            getUptime(),
            probeResults.size(),
            availableBackends.size()
        );
    }

    public record SelectorStats(
        State state,
        GPUBackend.Type activeType,
        int initAttempts,
        int failedAttempts,
        Duration initTime,
        Duration uptime,
        int probedBackends,
        int availableBackends
    ) {}

    // ========================================================================
    // LOGGING
    // ========================================================================

    private void logInitResult(InitResult result) {
        if (result.success()) {
            FPSFlux.LOGGER.info("[GPU] ═══════════════════════════════════════════════════");
            FPSFlux.LOGGER.info("[GPU] Backend initialized successfully");
            FPSFlux.LOGGER.info("[GPU] Type: {} ({})", result.type(), result.version());
            FPSFlux.LOGGER.info("[GPU] Init time: {}ms", result.initTime().toMillis());
            FPSFlux.LOGGER.info("[GPU] Capability score: {}", result.capabilityScore());
            FPSFlux.LOGGER.info("[GPU] Supported features: {}", result.supportedFeatures());

            if (result.hasWarnings()) {
                for (String warning : result.warnings()) {
                    FPSFlux.LOGGER.warn("[GPU] Warning: {}", warning);
                }
            }

            FPSFlux.LOGGER.info("[GPU] ═══════════════════════════════════════════════════");
        } else {
            FPSFlux.LOGGER.error("[GPU] Backend initialization failed!");
            for (String warning : result.warnings()) {
                FPSFlux.LOGGER.error("[GPU] {}", warning);
            }
            if (result.error() != null) {
                FPSFlux.LOGGER.error("[GPU] Error: {}", result.error().getMessage());
            }
        }
    }

    // ========================================================================
    // SHUTDOWN
    // ========================================================================

    /**
     * Shutdown the selector and active backend.
     */
    public void shutdown() {
        if (state == State.SHUTDOWN || state == State.SHUTTING_DOWN) {
            return;
        }

        state = State.SHUTTING_DOWN;
        FPSFlux.LOGGER.info("[GPU] Shutting down backend selector...");

        GPUBackend.Type type = activeBackend != null ? activeBackend.getType() : null;
        Duration uptime = getUptime();

        // Close active backend
        if (activeBackend != null) {
            try {
                if (activeBackend instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } catch (Exception e) {
                FPSFlux.LOGGER.warn("[GPU] Error closing backend: {}", e.getMessage());
            }
            activeBackend = null;
        }

        // Close any other initialized backends
        for (GPUBackend backend : availableBackends.values()) {
            if (backend instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {}
            }
        }
        availableBackends.clear();
        probeResults.clear();

        state = State.SHUTDOWN;

        publishEvent(new InitEvent.Shutdown(type, uptime, Instant.now()));
        FPSFlux.LOGGER.info("[GPU] Backend selector shutdown complete. Uptime: {}", uptime);
    }

    @Override
    public void close() {
        shutdown();
    }

    // ========================================================================
    // EXCEPTIONS
    // ========================================================================

    /**
     * Backend initialization exception.
     */
    public static class BackendInitializationException extends RuntimeException {
        public BackendInitializationException(String message) {
            super(message);
        }

        public BackendInitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ========================================================================
    // STATIC CONVENIENCE METHODS
    // ========================================================================

    /**
     * Quick check if GPU-driven rendering is available.
     */
    public static boolean supportsGpuDriven() {
        if (!isInitialized()) return false;
        return get().getCapabilities().supportsGpuDriven();
    }

    /**
     * Quick check if compute shaders are available.
     */
    public static boolean supportsCompute() {
        if (!isInitialized()) return false;
        return get().supportsComputeShaders();
    }

    /**
     * Quick check if mesh shaders are available.
     */
    public static boolean supportsMeshShaders() {
        if (!isInitialized()) return false;
        return get().supportsMeshShaders();
    }

    /**
     * Execute with active backend.
     */
    public static void withBackend(Consumer<GPUBackend> action) {
        if (isInitialized()) {
            action.accept(get());
        }
    }

    /**
     * Execute with active backend if available.
     */
    public static <T> Optional<T> withBackend(Function<GPUBackend, T> action) {
        if (isInitialized()) {
            return Optional.ofNullable(action.apply(get()));
        }
        return Optional.empty();
    }
}
