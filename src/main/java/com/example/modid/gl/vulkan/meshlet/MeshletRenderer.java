package com.example.modid.gl.vulkan.meshlet;

import com.example.modid.gl.GPUBackend;
import com.example.modid.gl.GPUBackendSelector;

public class MeshletRenderer {
    
    private final GPUBackend backend;
    private long taskShader;
    private long meshShader;
    private long fragmentShader;
    private long pipeline;
    
    public MeshletRenderer() {
        this.backend = GPUBackendSelector.get();
        if (!backend.supportsMeshShaders()) {
            throw new UnsupportedOperationException("Mesh Shaders not supported on this hardware/driver.");
        }
    }
    
    public void compileShaders(String taskSrc, String meshSrc, String fragSrc) {
        taskShader = backend.createShader(GPUBackend.ShaderStage.TASK, taskSrc);
        meshShader = backend.createShader(GPUBackend.ShaderStage.MESH, meshSrc);
        fragmentShader = backend.createShader(GPUBackend.ShaderStage.FRAGMENT, fragSrc);
        
        // Pipeline creation specifically for Mesh Shaders skips Vertex/Geometry stages
        pipeline = backend.createProgram(taskShader, meshShader, fragmentShader);
    }
    
    /**
     * Dispatches the Meshlet Pipeline.
     * 
     * @param meshletCount Total number of meshlets to process.
     * @param meshletBuffer Buffer containing MeshletData array.
     */
    public void draw(int meshletCount, long meshletBuffer) {
        backend.bindProgram(pipeline);
        
        // Bind the meshlet descriptor buffer to binding 0 (Storage Buffer)
        // This replaces the Vertex Buffer entirely
        backend.bindVertexBuffer(0, meshletBuffer, 0, MeshletData.SIZE_BYTES); // Logical bind
        
        // Task shaders work in workgroups. Assuming LocalSize=32.
        int groups = (meshletCount + 31) / 32;
        
        // Vulkan: vkCmdDrawMeshTasksEXT / OpenGL: glDrawMeshTasksNV
        // The backend abstraction maps this call.
        // Arguments: GroupCountX, GroupCountY, GroupCountZ
        ((com.example.modid.gl.vulkan.VulkanManager)com.example.modid.gl.vulkan.VulkanManager.getFast())
            .drawMeshTasks(groups, 1, 1);
    }
}
