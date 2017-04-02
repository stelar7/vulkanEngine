package no.stelar7.vulkan.engine.renderer;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public class DepthStencil
{
    private long image;
    private long memory;
    private long view;
    
    public long getImage()
    {
        return image;
    }
    
    public void setImage(long image)
    {
        this.image = image;
    }
    
    public long getMemory()
    {
        return memory;
    }
    
    public void setMemory(long memory)
    {
        this.memory = memory;
    }
    
    public long getView()
    {
        return view;
    }
    
    public void setView(long view)
    {
        this.view = view;
    }
    
    public void free(VkDevice device)
    {
        vkFreeMemory(device, memory, null);
        vkDestroyImage(device, image, null);
        vkDestroyImageView(device, view, null);
    }
}
