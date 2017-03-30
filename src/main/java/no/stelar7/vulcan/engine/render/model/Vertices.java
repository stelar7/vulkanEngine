package no.stelar7.vulcan.engine.render.model;

import no.stelar7.vulcan.engine.EngineUtils;
import no.stelar7.vulcan.engine.render.*;
import org.joml.*;
import org.lwjgl.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.*;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

public class Vertices
{
    
    private int size;
    
    private long vertexBuffer;
    private long vertexMemory;
    
    private long indexBuffer;
    private long indexMemory;
    
    public Vertices(VulkanRenderer renderer, List<Integer> inds, List<Vector3f> pos, List<Vector4f> color)
    {
        size = inds.size();
        
        setupVertexBuffer(renderer, pos, color);
        setupIndexBuffer(renderer, inds);
    }
    
    private void setupIndexBuffer(VulkanRenderer renderer, List<Integer> inds)
    {
        long stagingBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageTransferFlag(), getSizeInBytes());
        long stagingMemory = renderer.createMemory(stagingBuffer, ShaderSpec.getTransferMemoryFlags());
        setIndexBufferData(renderer.getDevice(), inds, stagingMemory);
        
        indexBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageIndexFlag(), getSizeInBytes());
        indexMemory = renderer.createMemory(indexBuffer, ShaderSpec.getDeviceLocalFlag());
        renderer.copyBuffer(stagingBuffer, indexBuffer, getSizeInBytes());
        
        renderer.destroyBuffer(stagingBuffer);
        renderer.destroyMemory(stagingMemory);
    }
    
    private void setIndexBufferData(VkDevice device, List<Integer> inds, long memory)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer pointer = stack.mallocPointer(1);
            EngineUtils.checkError(vkMapMemory(device, memory, 0, VK_WHOLE_SIZE, 0, pointer));
            
            FloatBuffer memoryBuffer = pointer.getFloatBuffer(0, getSize());
            for (int i = 0; i < getIndexCount(); i++)
            {
                memoryBuffer.put(i, inds.get(i));
            }
            
            vkUnmapMemory(device, memory);
        }
    }
    
    private void setupVertexBuffer(VulkanRenderer renderer, List<Vector3f> pos, List<Vector4f> color)
    {
        long stagingBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageTransferFlag(), getSizeInBytes());
        long stagingMemory = renderer.createMemory(stagingBuffer, ShaderSpec.getTransferMemoryFlags());
        setVertexBufferData(renderer.getDevice(), pos, color, stagingMemory);
        
        vertexBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageVertexFlag(), getSizeInBytes());
        vertexMemory = renderer.createMemory(vertexBuffer, ShaderSpec.getDeviceLocalFlag());
        renderer.copyBuffer(stagingBuffer, vertexBuffer, getSizeInBytes());
        
        renderer.destroyBuffer(stagingBuffer);
        renderer.destroyMemory(stagingMemory);
    }
    
    public void setVertexBufferData(VkDevice device, List<Vector3f> pos, List<Vector4f> color, long memory)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer pointer = stack.mallocPointer(1);
            EngineUtils.checkError(vkMapMemory(device, memory, 0, VK_WHOLE_SIZE, 0, pointer));
            
            
            FloatBuffer memoryBuffer = pointer.getFloatBuffer(0, getSize());
            int         stride       = ShaderSpec.getVertexStride();
            for (int i = 0; i < pos.size(); i++)
            {
                pos.get(i).get(i * stride, memoryBuffer);
                color.get(i).get(ShaderSpec.getPositionComponentCount() + (i * stride), memoryBuffer);
            }
            
            vkUnmapMemory(device, memory);
        }
    }
    
    
    public int getIndexCount()
    {
        return size;
    }
    
    public long getVertexBuffer()
    {
        return vertexBuffer;
    }
    
    public long getVertexMemory()
    {
        return vertexMemory;
    }
    
    public long getIndexBuffer()
    {
        return indexBuffer;
    }
    
    public long getIndexMemory()
    {
        return indexMemory;
    }
    
    public int getSizeInBytes()
    {
        return ShaderSpec.getVertexStrideInBytes() * size;
    }
    
    public int getSize()
    {
        return ShaderSpec.getVertexStride() * size;
    }
    
    
    public void destroy(VkDevice device)
    {
        vkDestroyBuffer(device, getVertexBuffer(), null);
        vkFreeMemory(device, getVertexMemory(), null);
        
        vkDestroyBuffer(device, getIndexBuffer(), null);
        vkFreeMemory(device, getIndexMemory(), null);
    }
}
