package no.stelar7.vulkan.engine.memory;

import no.stelar7.vulkan.engine.renderer.DeviceFamily;

import java.util.*;

public class MemoryAllocator
{
    // 256MB
    private int defaultChunkSize = 256_000_000;
    
    private List<MemoryChunk> chunks = new ArrayList<>();
    private DeviceFamily deviceFamily;
    
    public static MemoryAllocator getInstance()
    {
        return instance;
    }
    
    private static MemoryAllocator instance = null;
    
    public MemoryAllocator(DeviceFamily deviceFamily)
    {
        this.deviceFamily = deviceFamily;
        instance = this;
    }
    
    public MemoryBlock allocate(long size, long alignment, int memoryIndex)
    {
        for (MemoryChunk chunk : chunks)
        {
            if (chunk.getMemoryIndex() == memoryIndex)
            {
                MemoryBlock block = chunk.allocate(size, alignment);
                if (block != null)
                {
                    return block;
                }
            }
        }
        
        chunks.add(allocateChunk(alignment, memoryIndex));
        return allocate(size, alignment, memoryIndex);
    }
    
    public void deallocate(MemoryBlock block)
    {
        chunks.stream().filter(s -> s.hasBlock(block)).findFirst().ifPresent(c -> c.deallocate(block));
    }
    
    public void free()
    {
        chunks.forEach(MemoryChunk::free);
    }
    
    private MemoryChunk allocateChunk(long size, int memoryIndex)
    {
        long chunkSize = (defaultChunkSize < size) ? getNextPowerOfTwo(size) : defaultChunkSize;
        return new MemoryChunk(deviceFamily, memoryIndex, chunkSize);
    }
    
    
    private long getNextPowerOfTwo(long a)
    {
        long n = a;
        n--;
        n = n | (n >> 1);
        n = n | (n >> 2);
        n = n | (n >> 4);
        n = n | (n >> 8);
        n = n | (n >> 16);
        n++;
        return n;
    }
    
}
