package no.stelar7.vulcan.engine;

import org.lwjgl.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.FloatBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class Vertex
{
    
    private static int vertexBinding = 0;
    
    private static int positionCount = 3;
    private static int colorCount    = 4;
    
    private static int positionLocation = 0;
    private static int colorLocation    = 1;
    private static int vertexStride     = positionCount + colorCount;
    
    private float[][] vertices;
    
    private long buffer;
    private long memory;
    
    private static VkPipelineVertexInputStateCreateInfo     createInfo          = VkPipelineVertexInputStateCreateInfo.malloc();
    private static VkVertexInputBindingDescription.Buffer   vertexBindingBuffer = VkVertexInputBindingDescription.malloc(1);
    private static VkVertexInputAttributeDescription.Buffer vertexAttribsBuffer = VkVertexInputAttributeDescription.malloc(2);
    
    public Vertex(final float[][] vertices)
    {
        this.vertices = new float[vertices.length][vertices[0].length];
        
        for (int i = 0; i < vertices.length; i++)
        {
            System.arraycopy(this.vertices[i], 0, vertices[i], 0, vertices[0].length);
        }
        
        getVertexBindingBuffer().binding(Vertex.getVertexBinding())
                                .stride(Vertex.getVertexStride())
                                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
        
        getVertexAttributeBuffer().get(0)
                                  .location(Vertex.getPositionLocation())
                                  .binding(Vertex.getVertexBinding())
                                  .format(VK_FORMAT_R32G32B32_SFLOAT)
                                  .offset(Vertex.getPositionOffset());
        
        getVertexAttributeBuffer().get(1)
                                  .location(Vertex.getColorLocation())
                                  .binding(Vertex.getVertexBinding())
                                  .format(VK_FORMAT_R32G32B32A32_SFLOAT)
                                  .offset(Vertex.getColorOffset());
        
        
        getCreateInfo().sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                       .pNext(VK_NULL_HANDLE)
                       .flags(0)
                       .pVertexBindingDescriptions(getVertexBindingBuffer())
                       .pVertexAttributeDescriptions(getVertexAttributeBuffer());
        
    }
    
    public static int getPositionCount()
    {
        return positionCount;
    }
    
    public static int getColorCount()
    {
        return colorCount;
    }
    
    public static int getVertexStride()
    {
        return vertexStride;
    }
    
    public static int getVertexBinding()
    {
        return vertexBinding;
    }
    
    public static int getPositionLocation()
    {
        return positionLocation;
    }
    
    public static int getColorLocation()
    {
        return colorLocation;
    }
    
    public static VkPipelineVertexInputStateCreateInfo getCreateInfo()
    {
        return createInfo;
    }
    
    public static VkVertexInputBindingDescription.Buffer getVertexBindingBuffer()
    {
        return vertexBindingBuffer;
    }
    
    public static VkVertexInputAttributeDescription.Buffer getVertexAttributeBuffer()
    {
        return vertexAttribsBuffer;
    }
    
    public void destroy(VkDevice device)
    {
        vkDestroyBuffer(device, buffer, null);
        vkFreeMemory(device, memory, null);
        
        vertexBindingBuffer.free();
        vertexAttribsBuffer.free();
        createInfo.free();
    }
    
    public static int getColorOffset()
    {
        return ((positionLocation) + 1) * Float.BYTES;
    }
    
    public static int getPositionOffset()
    {
        return 0;
    }
    
    public static int getLastAttribIndex()
    {
        return colorLocation;
    }
    
    public static int getLastAttribOffset()
    {
        return getColorOffset();
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
}
