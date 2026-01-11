package com.example.modid.gl.vulkan.render;

import com.example.modid.gl.GPUBackend;
import com.example.modid.gl.GPUBackendSelector;
import com.example.modid.FPSFlux;

import java.util.*;

/**
 * RenderGraph - DAG-based rendering orchestrator.
 * 
 * <p>Manages the lifecycle, dependencies, and execution of rendering passes.</p>
 */
public final class RenderGraph {
    
    private final List<RenderPassNode> nodes = new ArrayList<>();
    private final Map<String, ResourceNode> resources = new HashMap<>();
    private final GPUBackend backend;
    
    public RenderGraph() {
        this.backend = GPUBackendSelector.get();
    }
    
    /**
     * Add a new pass to the graph.
     */
    public PassBuilder addPass(String name) {
        RenderPassNode node = new RenderPassNode(name);
        nodes.add(node);
        return new PassBuilder(this, node);
    }
    
    /**
     * Define a transient texture resource.
     */
    public ResourceNode createTexture(String name, int width, int height, int format) {
        ResourceNode node = new ResourceNode(name, ResourceNode.Type.TEXTURE);
        node.width = width;
        node.height = height;
        node.format = format;
        resources.put(name, node);
        return node;
    }
    
    /**
     * Define a transient buffer resource.
     */
    public ResourceNode createBuffer(String name, long size, int usage) {
        ResourceNode node = new ResourceNode(name, ResourceNode.Type.BUFFER);
        node.size = size;
        node.usage = usage;
        resources.put(name, node);
        return node;
    }
    
    /**
     * Execute the graph for the current frame.
     */
    public void execute() {
        // 1. Sort nodes based on dependencies (Topological Sort)
        List<RenderPassNode> sortedNodes = sortNodes();
        
        // 2. Allocate/Recycle resources
        setupResources(sortedNodes);
        
        // 3. Execute nodes
        backend.beginFrame();
        
        for (RenderPassNode node : sortedNodes) {
            // Insert Barriers based on dependencies
            insertBarriers(node);
            
            // Execute actual rendering logic
            GPUBackend.RenderPassInfo info = node.getRenderPassInfo();
            backend.beginRenderPass(info);
            
            if (node.executor != null) {
                node.executor.execute(backend);
            }
            
            backend.endRenderPass();
        }
        
        backend.endFrame();
    }
    
    private List<RenderPassNode> sortNodes() {
        List<RenderPassNode> sorted = new ArrayList<>();
        Set<RenderPassNode> visited = new HashSet<>();
        Set<RenderPassNode> recursionStack = new HashSet<>();
        
        for (RenderPassNode node : nodes) {
            if (!visited.contains(node)) {
                topologicalSort(node, visited, recursionStack, sorted);
            }
        }
        
        Collections.reverse(sorted);
        return sorted;
    }
    
    private void topologicalSort(RenderPassNode node, Set<RenderPassNode> visited, 
                                 Set<RenderPassNode> stack, List<RenderPassNode> result) {
        visited.add(node);
        stack.add(node);
        
        for (ResourceNode input : node.inputs) {
            RenderPassNode producer = input.producer;
            if (producer != null && !visited.contains(producer)) {
                topologicalSort(producer, visited, stack, result);
            }
        }
        
        stack.remove(node);
        result.add(node);
    }
    
    private void setupResources(List<RenderPassNode> sortedNodes) {
        for (ResourceNode res : resources.values()) {
            if (res.handle == 0) {
                if (res.type == ResourceNode.Type.TEXTURE) {
                    res.handle = backend.createTexture2D(res.width, res.height, res.format, 1);
                } else {
                    res.handle = backend.createBuffer(res.size, res.usage, GPUBackend.MemoryFlags.DEVICE_LOCAL);
                }
            }
        }
    }
    
    private void insertBarriers(RenderPassNode node) {
        int barrierBits = 0;
        for (ResourceNode input : node.inputs) {
            if (input.type == ResourceNode.Type.BUFFER) {
                barrierBits |= GPUBackend.BarrierType.SHADER_STORAGE;
            } else {
                barrierBits |= GPUBackend.BarrierType.TEXTURE_FETCH;
            }
        }
        
        if (barrierBits != 0) {
            backend.memoryBarrier(barrierBits);
        }
    }
    
    public void shutdown() {
        for (ResourceNode res : resources.values()) {
            if (res.handle != 0) {
                if (res.type == ResourceNode.Type.TEXTURE) {
                    backend.destroyTexture(res.handle);
                } else {
                    backend.destroyBuffer(res.handle);
                }
            }
        }
        resources.clear();
        nodes.clear();
    }
}
