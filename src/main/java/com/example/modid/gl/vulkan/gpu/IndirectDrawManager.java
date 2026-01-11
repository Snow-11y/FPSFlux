package com.example.modid.gl.vulkan.gpu;

import com.example.modid.ecs.Archetype;
import com.example.modid.ecs.ComponentArray;
import com.example.modid.gl.GPUBackend;
import com.example.modid.gl.GPUBackendSelector;
import com.example.modid.gl.vulkan.VulkanState;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * IndirectDrawManager - Automates GPU-driven draw submission.
 */
public final class IndirectDrawManager {
    
    private final GPUBackend backend;
    private long commandBuffer;
    private long countBuffer;
    private long instanceBuffer;
    
    private ByteBuffer commandMapped;
    private ByteBuffer countMapped;
    
    private static final int MAX_DRAWS = 65536;
    
    public IndirectDrawManager() {
        this.backend = GPUBackendSelector.get();
        
        // 1. Create the Command Buffer (Indirect Args)
        this.commandBuffer = backend.createBuffer(
            MAX_DRAWS * 20L, 
            GPUBackend.BufferUsage.INDIRECT | GPUBackend.BufferUsage.STORAGE,
            GPUBackend.MemoryFlags.HOST_VISIBLE | GPUBackend.MemoryFlags.PERSISTENT
        );
        
        // 2. Create the Count Buffer (for MDI Count)
        this.countBuffer = backend.createBuffer(
            4, 
            GPUBackend.BufferUsage.INDIRECT | GPUBackend.BufferUsage.STORAGE,
            GPUBackend.MemoryFlags.HOST_VISIBLE | GPUBackend.MemoryFlags.PERSISTENT
        );
        
        this.commandMapped = backend.mapBuffer(commandBuffer, 0, MAX_DRAWS * 20L);
        this.countMapped = backend.mapBuffer(countBuffer, 0, 4);
    }
    
    /**
     * Binds an ECS Archetype's component arrays directly to GPU Bindings.
     * ZERO-COPY: We use the buffer handle from the SoA array.
     */
    public void submitArchetype(Archetype archetype, long program) {
        backend.bindProgram(program);
        
        // Bind the "Position" component array directly as a Vertex Buffer
        // Assuming Position is type ID 0
        ComponentArray posArray = archetype.getComponentArray(0);
        if (posArray != null) {
            backend.bindVertexBuffer(0, posArray.getGpuBuffer(), 0, 12); // float3
        }
        
        // Build the indirect command
        commandMapped.clear();
        commandMapped.putInt(archetype.getEntityCount() * 3); // indexCount (assuming triangles)
        commandMapped.putInt(1); // instanceCount
        commandMapped.putInt(0); // firstIndex
        commandMapped.putInt(0); // vertexOffset
        commandMapped.putInt(0); // firstInstance
        
        countMapped.clear();
        countMapped.putInt(1);
        
        // Execute!
        if (backend.supportsIndirectCount()) {
            backend.drawIndexedIndirectCount(commandBuffer, 0, countBuffer, 0, 1, 20);
        } else {
            backend.drawIndexedIndirect(commandBuffer, 0, 1, 20);
        }
    }
}
