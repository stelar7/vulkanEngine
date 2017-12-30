package no.stelar7.vulkan.engine.memory;

import no.stelar7.vulkan.engine.EngineUtils;
import no.stelar7.vulkan.engine.renderer.DeviceFamily;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.*;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;

public class MemoryChunk
{
    private VkDevice device;
    
    private long memory;
    private long size;
    private int  index;
    
    private List<MemoryBlock> blocks = new ArrayList<>();
    
    
    public MemoryChunk(DeviceFamily deviceFamily, int memoryIndex, long size)
    {
        
        VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc()
                                                                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                                                                .memoryTypeIndex(memoryIndex)
                                                                .allocationSize(size);
        
        LongBuffer handleHolder = memAllocLong(1);
        EngineUtils.checkError(vkAllocateMemory(deviceFamily.getDevice(), allocateInfo, null, handleHolder));
        
        
        this.memory = handleHolder.get(0);
        this.device = deviceFamily.getDevice();
        this.index = memoryIndex;
        this.size = size;
        
        memFree(handleHolder);
        allocateInfo.free();
        
        MemoryBlock block = new MemoryBlock();
        block.setMemory(memory);
        block.setOffset(0);
        block.setSize(size);
        block.free(device);
        
        blocks.add(block);
    }
    
    public int getMemoryIndex()
    {
        return index;
    }
    
    public void deallocate(MemoryBlock block)
    {
        blocks.stream().filter(a -> a.equals(block)).findFirst().ifPresent(memoryBlock -> memoryBlock.free(device));
    }
    
    public MemoryBlock allocate(long requestSize, long alignment)
    {
        // TODO: take alignment into consideration
        
        if (requestSize > size)
        {
            return null;
        }
        
        for (int i = 0; i < blocks.size(); i++)
        {
            MemoryBlock currentBlock = blocks.get(i);
            
            
            if (currentBlock.isFree())
            {
                // TODO: Better defragmentation
                if (i > 0)
                {
                    MemoryBlock prevBlock = blocks.get(i - 1);
                    if (prevBlock.isFree())
                    {
                        prevBlock.setSize(prevBlock.getSize() + currentBlock.getSize());
                        blocks.remove(currentBlock);
                        return allocate(requestSize, alignment);
                    }
                }
                
                if (currentBlock.getSize() >= requestSize)
                {
                    if (currentBlock.getSize() == requestSize)
                    {
                        currentBlock.take();
                        return currentBlock;
                    }
                    
                    
                    MemoryBlock nextBlock = new MemoryBlock();
                    nextBlock.setSize(currentBlock.getSize() - requestSize);
                    nextBlock.setOffset(currentBlock.getOffset() + requestSize);
                    nextBlock.setMemory(memory);
                    nextBlock.free(device);
                    blocks.add(nextBlock);
                    
                    currentBlock.setSize(requestSize);
                    currentBlock.take();
                    
                    return currentBlock;
                }
            }
        }
        return null;
    }
    
    public void free()
    {
        vkFreeMemory(device, memory, null);
    }
    
    public boolean hasBlock(MemoryBlock block)
    {
        return blocks.stream().anyMatch(a -> a.equals(block));
    }
}