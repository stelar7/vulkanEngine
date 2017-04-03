package no.stelar7.vulkan.engine.renderer;

public class VertexBuffer
{
    private long buffer;
    private long memory;
    private int  sizeInBytes;
    
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
    
    public void setSizeInBytes(int sizeInBytes)
    {
        this.sizeInBytes = sizeInBytes;
    }
    
    public int getSizeInBytes()
    {
        return sizeInBytes;
    }
}
