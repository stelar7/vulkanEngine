package no.stelar7.vulkan.engine.renderer;

import no.stelar7.vulkan.engine.memory.*;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public class DepthStencil
{
    private long        image;
    private long        view;
    private MemoryBlock memoryBlock;
    
    public long getImage()
    {
        return image;
    }
    
    public void setImage(long image)
    {
        this.image = image;
    }
    
    public MemoryBlock getMemoryBlock()
    {
        return memoryBlock;
    }
    
    public void setMemoryBlock(MemoryBlock memory)
    {
        this.memoryBlock = memory;
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
        vkDestroyImage(device, image, null);
        vkDestroyImageView(device, view, null);
        MemoryAllocator.INSTANCE.deallocate(memoryBlock);
    }
}
