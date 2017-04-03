package no.stelar7.vulkan.engine.renderer;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public class Buffer
{
    private long memory;
    private long buffer;
    
    private long offset;
    private long sizeInBytes;
    
    public long getMemory()
    {
        return memory;
    }
    
    public void setMemory(long memory)
    {
        this.memory = memory;
    }
    
    public long getBuffer()
    {
        return buffer;
    }
    
    public void setBuffer(long buffer)
    {
        this.buffer = buffer;
    }
    
    
    public long getOffset()
    {
        return offset;
    }
    
    public void setOffset(long offset)
    {
        this.offset = offset;
    }
    
    public long getSizeInBytes()
    {
        return sizeInBytes;
    }
    
    public void setSizeInBytes(long sizeInBytes)
    {
        this.sizeInBytes = sizeInBytes;
    }
    
    
    public void free(VkDevice device)
    {
        vkFreeMemory(device, memory, null);
        vkDestroyBuffer(device, buffer, null);
    }
}