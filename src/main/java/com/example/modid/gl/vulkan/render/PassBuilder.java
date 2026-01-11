package com.example.modid.gl.vulkan.render;

import com.example.modid.gl.GPUBackend;

/**
 * PassBuilder - Fluent API to define pass requirements.
 */
public final class PassBuilder {
    private final RenderGraph graph;
    private final RenderPassNode node;
    
    public PassBuilder(RenderGraph graph, RenderPassNode node) {
        this.graph = graph;
        this.node = node;
    }
    
    public PassBuilder read(ResourceNode resource) {
        node.inputs.add(resource);
        return this;
    }
    
    public PassBuilder write(ResourceNode resource) {
        node.outputs.add(resource);
        resource.producer = node;
        return this;
    }
    
    public PassBuilder setExecutor(RenderPassNode.PassExecutor executor) {
        node.executor = executor;
        return this;
    }
    
    public PassBuilder setClearColor(float r, float g, float b, float a) {
        node.clearColor = new float[]{r, g, b, a};
        return this;
    }
}
