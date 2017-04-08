package no.stelar7.vulkan.engine.buffer;

import no.stelar7.vulkan.engine.memory.MemoryBlock;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public class Buffer
{
    private MemoryBlock memoryBlock;
    private long        bufferHandle;
    private long        bufferSize;
    
    public MemoryBlock getMemoryBlock()
    {
        return memoryBlock;
    }
    
    public void setMemoryBlock(MemoryBlock memoryBlock)
    {
        this.memoryBlock = memoryBlock;
    }
    
    public long getBufferHandle()
    {
        return bufferHandle;
    }
    
    public void setBufferHandle(long bufferHandle)
    {
        this.bufferHandle = bufferHandle;
    }
    
    
    public void free(VkDevice device)
    {
        vkDestroyBuffer(device, bufferHandle, null);
    }
    
    /**
     * Gets the size of the buffer (In bytes)
     */
    public long getSize()
    {
        return bufferSize;
    }
    
    /**
     * Sets the size of the buffer (In bytes)
     */
    public void setSize(long size)
    {
        this.bufferSize = size;
    }
}
