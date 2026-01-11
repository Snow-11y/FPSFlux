package com.example.modid.gl.vulkan.resolution;

import com.example.modid.gl.GPUBackend;
import com.example.modid.gl.vulkan.render.RenderGraph;
import com.example.modid.gl.vulkan.render.ResourceNode;

public class ResolutionManager {
    
    private float renderScale = 1.0f; // 1.0 = Native, 0.75 = Quality, 0.5 = Performance
    private int displayWidth;
    private int displayHeight;
    
    public void setDisplaySize(int width, int height) {
        this.displayWidth = width;
        this.displayHeight = height;
    }
    
    public void setRenderScale(float scale) {
        this.renderScale = Math.max(0.1f, Math.min(2.0f, scale));
    }
    
    public int getRenderWidth() {
        return (int) (displayWidth * renderScale);
    }
    
    public int getRenderHeight() {
        return (int) (displayHeight * renderScale);
    }
    
    /**
     * injects an Upscale Pass into the Render Graph.
     */
    public void injectUpscalePass(RenderGraph graph, ResourceNode lowResInput, ResourceNode highResOutput) {
        if (Math.abs(renderScale - 1.0f) < 0.01f) {
            // Native resolution: Simple Blit/Copy
            graph.addPass("NativeBlit")
                .read(lowResInput)
                .write(highResOutput)
                .setExecutor(backend -> {
                    // Perform simple texture copy or draw full-screen quad with nearest/linear
                    // ... implementation details ...
                });
        } else {
            // Scaled: Use SuperSampler
            graph.addPass("FSR_Upscale")
                .read(lowResInput)
                .write(highResOutput)
                .setExecutor(backend -> {
                    // Bind FSR/Bilinear shader
                    // Draw full-screen triangle
                });
        }
    }
}
