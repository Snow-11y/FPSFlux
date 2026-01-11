package com.example.modid.gl.vulkan.shaders;

import com.example.modid.gl.GPUBackend;
import com.example.modid.gl.GPUBackendSelector;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages variations of shaders based on #define macros.
 */
public class ShaderPermutationManager {
    
    private static final String VERSION_HEADER = "#version 450\n";
    
    // Cache: "ShaderSource + DefinedKeys" -> CompiledShaderHandle
    private final Map<String, Long> shaderCache = new HashMap<>();
    // Cache: "VertHandle + FragHandle" -> PipelineHandle
    private final Map<String, Long> pipelineCache = new HashMap<>();
    
    private final GPUBackend backend;
    
    public ShaderPermutationManager() {
        this.backend = GPUBackendSelector.get();
    }
    
    /**
     * Get a specific variant of a shader pipeline.
     */
    public long getPipeline(String vertSrc, String fragSrc, String... defines) {
        // 1. Generate Variant Key
        StringBuilder defineHeader = new StringBuilder();
        for (String def : defines) {
            defineHeader.append("#define ").append(def).append("\n");
        }
        String header = defineHeader.toString();
        
        // 2. Compile/Get Shaders
        long vert = getShader(GPUBackend.ShaderStage.VERTEX, vertSrc, header);
        long frag = getShader(GPUBackend.ShaderStage.FRAGMENT, fragSrc, header);
        
        // 3. Link/Get Pipeline
        String pipeKey = vert + "_" + frag;
        if (pipelineCache.containsKey(pipeKey)) {
            return pipelineCache.get(pipeKey);
        }
        
        long pipeline = backend.createProgram(vert, frag);
        pipelineCache.put(pipeKey, pipeline);
        return pipeline;
    }
    
    private long getShader(int stage, String source, String defines) {
        // Clean source (remove version if exists, prepend ours)
        String cleanSrc = source.replace("#version 450", "").replace("#version 460", "");
        String finalSrc = VERSION_HEADER + defines + cleanSrc;
        
        String key = stage + "_" + finalSrc.hashCode(); // Simple hash for demo
        
        if (shaderCache.containsKey(key)) {
            return shaderCache.get(key);
        }
        
        long handle = backend.createShader(stage, finalSrc);
        shaderCache.put(key, handle);
        return handle;
    }
    
    public void clear() {
        // Destroy all valid handles in cache
        shaderCache.values().forEach(backend::destroyShader);
        pipelineCache.values().forEach(backend::destroyProgram);
        shaderCache.clear();
        pipelineCache.clear();
    }
}
