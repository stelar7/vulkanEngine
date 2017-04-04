package no.stelar7.vulkan.engine.memory;

import no.stelar7.vulkan.engine.EngineUtils;
import no.stelar7.vulkan.engine.renderer.DeviceFamily;
import org.lwjgl.*;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.*;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;

public class MemoryChunk
{
    private VkDevice device;
    
    private long memory;
    private int  index;
    private int  size;
    
    private long pointer;
    
    private List<MemoryBlock> blocks = new ArrayList<>();
    
    
    public MemoryChunk(DeviceFamily deviceFamily, int memoryIndex, int size)
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
        
        
        if (EngineUtils.hasFlag(deviceFamily.getMemoryProperties().memoryTypes(memoryIndex).propertyFlags(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT))
        {
            PointerBuffer pointerHolder = memAllocPointer(1);
            vkMapMemory(deviceFamily.getDevice(), memory, 0, VK_WHOLE_SIZE, 0, pointerHolder);
            this.pointer = pointerHolder.get(0);
            memFree(pointerHolder);
        }
        
        MemoryBlock block = new MemoryBlock();
        block.setMemory(memory);
        block.setOffset(0);
        block.setSize(size);
        block.setFree(true);
        
        blocks.add(block);
    }
    
    public int getMemoryIndex()
    {
        return index;
    }
    
    public void deallocate(MemoryBlock block)
    {
        blocks.stream().filter(a -> a.equals(block)).findFirst().ifPresent(memoryBlock -> memoryBlock.setFree(true));
    }
    
    public MemoryBlock allocate(int requestSize)
    {
        if (requestSize > size)
        {
            return null;
        }
        
        for (int i = 0; i < blocks.size(); i++)
        {
            if (blocks.get(i).isFree())
            {
                if (blocks.get(i).getSize() >= requestSize)
                {
                    if (blocks.get(i).getSize() == requestSize)
                    {
                        blocks.get(i).setFree(false);
                        return blocks.get(i);
                    }
                    
                    MemoryBlock nextBlock = new MemoryBlock();
                    nextBlock.setSize(blocks.get(i).getSize() - requestSize);
                    nextBlock.setOffset(blocks.get(i).getOffset() + blocks.get(i).getSize());
                    nextBlock.setMemory(memory);
                    nextBlock.setFree(true);
                    blocks.add(nextBlock);
                    
                    blocks.get(i).setSize(requestSize);
                    blocks.get(i).setFree(false);
                    
                    return blocks.get(i);
                }
            }
        }
        return null;
    }
    
    public void free()
    {
        vkFreeMemory(device, memory, null);
    }
}