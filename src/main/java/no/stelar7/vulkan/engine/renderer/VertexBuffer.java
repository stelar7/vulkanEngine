package no.stelar7.vulkan.engine.renderer;

public class VertexBuffer
{
    private long buffer;
    private long memory;
    private int  size;
    
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
    
    public void setSize(int size)
    {
        this.size = size;
    }
    
    public int getSize()
    {
        return size;
    }
}
