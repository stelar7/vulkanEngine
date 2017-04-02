package no.stelar7.vulkan.engine.renderer;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public class Pipeline
{
    private long layout;
    private long handle;
    
    public long getLayout()
    {
        return layout;
    }
    
    public void setLayout(long layout)
    {
        this.layout = layout;
    }
    
    public long getHandle()
    {
        return handle;
    }
    
    public void setHandle(long handle)
    {
        this.handle = handle;
    }
    
    public void free(VkDevice device)
    {
        vkDestroyPipelineLayout(device, layout, null);
        vkDestroyPipeline(device, handle, null);
    }
}
