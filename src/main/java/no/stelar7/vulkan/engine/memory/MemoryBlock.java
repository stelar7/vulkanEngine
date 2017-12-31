package no.stelar7.vulkan.engine.memory;

public class MemoryBlock
{
    private long    memory;
    private long    offset;
    private boolean free;
    private long    size;
    
    public long getMemory()
    {
        return memory;
    }
    
    public void setMemory(long memory)
    {
        this.memory = memory;
    }
    
    public long getOffset()
    {
        return offset;
    }
    
    public void setOffset(long offset)
    {
        this.offset = offset;
    }
    
    public long getSize()
    {
        return size;
    }
    
    public void setSize(long sizeInBytes)
    {
        this.size = sizeInBytes;
    }
    
    public boolean isFree()
    {
        return free;
    }
    
    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        
        MemoryBlock that = (MemoryBlock) o;
        
        if (memory != that.memory)
        {
            return false;
        }
        if (offset != that.offset)
        {
            return false;
        }
        if (free != that.free)
        {
            return false;
        }
        return size == that.size;
    }
    
    @Override
    public int hashCode()
    {
        int result = (int) (memory ^ (memory >>> 32));
        result = 31 * result + (int) (offset ^ (offset >>> 32));
        result = 31 * result + (free ? 1 : 0);
        result = 31 * result + (int) (size ^ (size >>> 32));
        return result;
    }
    
    public void take()
    {
        free = false;
    }
    
    public void free()
    {
        free = true;
    }
}