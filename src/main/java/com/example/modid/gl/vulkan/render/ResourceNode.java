package com.example.modid.gl.vulkan.render;

/**
 * ResourceNode - Represents a GPU resource (Texture/Buffer) in the graph.
 */
public final class ResourceNode {
    public enum Type { TEXTURE, BUFFER }
    
    public final String name;
    public final Type type;
    public long handle = 0; // The actual GL/VK handle
    
    // Texture props
    public int width, height, format;
    
    // Buffer props
    public long size;
    public int usage;
    
    // Dependency Tracking
    public RenderPassNode producer;
    
    public ResourceNode(String name, Type type) {
        this.name = name;
        this.type = type;
    }
}
