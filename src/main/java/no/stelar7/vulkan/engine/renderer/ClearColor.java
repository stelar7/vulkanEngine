package no.stelar7.vulkan.engine.renderer;

import org.lwjgl.vulkan.*;
import org.lwjgl.vulkan.VkClearValue.*;

public class ClearColor
{
    
    private VkClearValue.Buffer      clearColors     = VkClearValue.calloc(2);
    private VkClearColorValue        clearColorValue = VkClearColorValue.calloc();
    private VkClearDepthStencilValue clearDepthValue = VkClearDepthStencilValue.calloc();
    
    public ClearColor(float r, float g, float b, float a, int depth)
    {
        clearColorValue.float32(0, r).float32(1, g).float32(2, b).float32(3, a);
        clearDepthValue.depth(depth);
        clearColors.get(0).color(clearColorValue);
        clearColors.get(1).depthStencil(clearDepthValue);
    }
    
    public Buffer getClearColors()
    {
        return clearColors;
    }
    
    public void destroy()
    {
        clearDepthValue.free();
        clearColorValue.free();
        clearColors.free();
    }
}
