package no.stelar7.vulcan.engine;

import org.lwjgl.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.FloatBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class Vertex
{
    
    private float[][] vertices;
    
    private long buffer;
    private long memory;
    
    /**
     * Takes a float[][] in the format:
     * [
     * [XYZRGBA]
     * [XYZRGBA]
     * [XYZRGBA]
     * ]
     */
    public Vertex(VulkanRenderer renderer, float[][] vertices)
    {
        this.vertices = new float[vertices.length][vertices[0].length];
        
        for (int i = 0; i < vertices.length; i++)
        {
            System.arraycopy(this.vertices[i], 0, vertices[i], 0, vertices[0].length);
        }
        
        
        long[] size = new long[1];
        buffer = renderer.createBuffer(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, getSizeInBytes());
        memory = renderer.createMemory(getBuffer(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, size);
        setData(renderer.getDevice(), size[0]);
    }
    
    
    public int getVertexCount()
    {
        return vertices.length;
    }
    
    public void setBuffer(long buffer)
    {
        this.buffer = buffer;
    }
    
    public long getBuffer()
    {
        return buffer;
    }
    
    public void setMemory(long memory)
    {
        this.memory = memory;
    }
    
    public void setData(VkDevice device, long size)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer pointer = stack.mallocPointer(1);
            EngineUtils.checkError(vkMapMemory(device, memory, 0, VK_WHOLE_SIZE, 0, pointer));
            
            FloatBuffer data = pointer.getFloatBuffer(0, ((int) size) >> 2);
            for (float[] vertex : vertices)
            {
                data.put(vertex);
            }
            data.flip();
            
            vkUnmapMemory(device, memory);
        }
    }
    
    public long getSizeInBytes()
    {
        return vertices.length * vertices[0].length * (long) Float.BYTES;
    }
    
    public void destroy(VkDevice device)
    {
        vkDestroyBuffer(device, buffer, null);
        vkFreeMemory(device, memory, null);
    }
}
