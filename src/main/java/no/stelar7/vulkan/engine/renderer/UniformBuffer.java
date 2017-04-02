package no.stelar7.vulkan.engine.renderer;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public class UniformBuffer
{
    private long memory;
    private long buffer;
    private long offset;
    private long range;
    
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
    
    public long getRange()
    {
        return range;
    }
    
    public void setRange(long range)
    {
        this.range = range;
    }
    
    
    public void free(VkDevice device)
    {
        vkFreeMemory(device, memory, null);
        vkDestroyBuffer(device, buffer, null);
    }
}
