package no.stelar7.vulcan.engine.render.model;

import no.stelar7.vulcan.engine.EngineUtils;
import no.stelar7.vulcan.engine.render.*;
import org.joml.*;
import org.lwjgl.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.FloatBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;

public class Vertices
{
    
    private List<Vector3f> pos;
    private List<Vector4f> color;
    
    private long buffer;
    private long memory;
    
    public Vertices(VulkanRenderer renderer, List<Vector3f> pos, List<Vector4f> color)
    {
        this.pos = Collections.unmodifiableList(pos);
        this.color = Collections.unmodifiableList(color);
        
        long[] size = new long[1];
        buffer = renderer.createBuffer(ShaderSpec.getBufferUsageFlag(), getSizeInBytes());
        memory = renderer.createMemory(getBuffer(), ShaderSpec.getVertexMemoryFlags(), size);
        setData(renderer.getDevice(), size[0]);
    }
    
    
    public int getVertexCount()
    {
        return pos.size();
    }
    
    public long getBuffer()
    {
        return buffer;
    }
    
    public long getMemory()
    {
        return memory;
    }
    
    public void setData(VkDevice device, long size)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer pointer = stack.mallocPointer(1);
            EngineUtils.checkError(vkMapMemory(device, getMemory(), 0, VK_WHOLE_SIZE, 0, pointer));
            FloatBuffer data = pointer.getFloatBuffer(0, (int) size);
            
            /*int stride = ShaderSpec.getVertexStride();
            for (int i = 0; i < getVertexCount(); i++)
            {
                pos.get(i).get(i * stride, data);
                color.get(i).get(ShaderSpec.getPositionComponentCount() + (i * stride), data);
            }
            */
            for (int i = 0; i <= 3 * 7; i++)
            {
                data.put(i);
            }
            data.flip();
            
            VkMappedMemoryRange flushRange = VkMappedMemoryRange.mallocStack(stack)
                                                                .sType(VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE)
                                                                .pNext(VK_NULL_HANDLE)
                                                                .memory(getMemory())
                                                                .offset(0)
                                                                .size(VK_WHOLE_SIZE);
            
            EngineUtils.checkError(vkFlushMappedMemoryRanges(device, flushRange));
            
            vkUnmapMemory(device, getMemory());
            EngineUtils.checkError(vkBindBufferMemory(device, getBuffer(), getMemory(), 0));
        }
    }
    
    public long getSizeInBytes()
    {
        return (pos.size() * ShaderSpec.getPositionComponentCount()) + (color.size() * ShaderSpec.getColorComponentCount()) * (long) Float.BYTES;
    }
    
    public void destroy(VkDevice device)
    {
        vkDestroyBuffer(device, getBuffer(), null);
        vkFreeMemory(device, getMemory(), null);
    }
}
