package com.example.modid.gl.vulkan.render;

import com.example.modid.gl.GPUBackend;
import java.util.ArrayList;
import java.util.List;

/**
 * RenderPassNode - A single atomic operation in the Render Graph.
 */
public final class RenderPassNode {
    public final String name;
    public final List<ResourceNode> inputs = new ArrayList<>();
    public final List<ResourceNode> outputs = new ArrayList<>();
    
    public PassExecutor executor;
    
    // Pass state
    public float[] clearColor = {0, 0, 0, 1};
    public float clearDepth = 1.0f;
    public boolean clearOnLoad = true;
    
    public RenderPassNode(String name) {
        this.name = name;
    }
    
    public GPUBackend.RenderPassInfo getRenderPassInfo() {
        GPUBackend.RenderPassInfo info = new GPUBackend.RenderPassInfo();
        info.name = this.name;
        info.clearOnLoad = this.clearOnLoad;
        info.clearColor = this.clearColor;
        info.clearDepth = this.clearDepth;
        
        // Map output nodes to attachments
        List<Long> colorList = new ArrayList<>();
        for (ResourceNode out : outputs) {
            if (out.type == ResourceNode.Type.TEXTURE) {
                if (isDepthFormat(out.format)) {
                    info.depthAttachment = out.handle;
                } else {
                    colorList.add(out.handle);
                }
            }
        }
        
        info.colorAttachments = colorList.stream().mapToLong(l -> l).toArray();
        info.width = outputs.isEmpty() ? 1920 : outputs.get(0).width;
        info.height = outputs.isEmpty() ? 1080 : outputs.get(0).height;
        
        return info;
    }
    
    private boolean isDepthFormat(int format) {
        return format == GPUBackend.TextureFormat.DEPTH24_STENCIL8 || 
               format == GPUBackend.TextureFormat.DEPTH32F;
    }
    
    public interface PassExecutor {
        void execute(GPUBackend backend);
    }
}
