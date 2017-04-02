package no.stelar7.vulkan.engine.renderer;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class Swapchain
{
    private long   handle;
    private long[] images;
    private long[] views;
    
    public long getHandle()
    {
        return handle;
    }
    
    public long getImage(int index)
    {
        return images[index];
    }
    
    public long getViews(int index)
    {
        return views[index];
    }
    
    public int getImageCount()
    {
        return images.length;
    }
    
    public int getViewCount()
    {
        return views.length;
    }
    
    public void setHandle(long handle)
    {
        this.handle = handle;
    }
    
    public void setImages(long[] images)
    {
        this.images = images.clone();
    }
    
    public void setViews(long[] views)
    {
        this.views = views.clone();
    }
    
    public void free(VkDevice device, boolean destroyHandle)
    {
        for (long view : views)
        {
            vkDestroyImageView(device, view, null);
        }
        
        if (destroyHandle)
        {
            vkDestroySwapchainKHR(device, handle, null);
        }
    }
}
