package no.stelar7.vulkan.engine.buffer;

import org.lwjgl.vulkan.*;

public class StagedBuffer
{
    private Buffer staged;
    private Buffer used;
    
    public StagedBuffer(Buffer staged, Buffer used)
    {
        this.staged = staged;
        this.used = used;
    }
    
    public Buffer getStaged()
    {
        return staged;
    }
    
    public void setStaged(Buffer staged)
    {
        this.staged = staged;
    }
    
    public Buffer getUsed()
    {
        return used;
    }
    
    public void setUsed(Buffer used)
    {
        this.used = used;
    }
    
    public void free(VkDevice device)
    {
        staged.free(device);
        used.free(device);
    }
}
