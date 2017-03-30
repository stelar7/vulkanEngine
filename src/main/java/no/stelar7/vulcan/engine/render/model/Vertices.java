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
    
    private long vertexBuffer;
    private long vertexMemory;
    
    public Vertices(VulkanRenderer renderer, List<Vector3f> pos, List<Vector4f> color)
    {
        this.pos = Collections.unmodifiableList(pos);
        this.color = Collections.unmodifiableList(color);
        
        long stagingBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageTransferFlag(), getSizeInBytes());
        long stagingMemory = renderer.createMemory(stagingBuffer, ShaderSpec.getTransferMemoryFlags());
        setBufferData(renderer.getDevice(), stagingBuffer, stagingMemory);
        
        vertexBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageVertexFlag(), getSizeInBytes());
        vertexMemory = renderer.createMemory(vertexBuffer, ShaderSpec.getVertexMemoryFlags());
        renderer.copyBuffer(stagingBuffer, vertexBuffer, getSizeInBytes());
        
        renderer.destroyBuffer(stagingBuffer);
        renderer.destroyMemory(stagingMemory);
    }
    
    
    public int getVertexCount()
    {
        return pos.size();
    }
    
    public long getVertexBuffer()
    {
        return vertexBuffer;
    }
    
    public long getVertexMemory()
    {
        return vertexMemory;
    }
    
    public void setBufferData(VkDevice device, long buffer, long memory)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer pointer = stack.mallocPointer(1);
            EngineUtils.checkError(vkMapMemory(device, memory, 0, VK_WHOLE_SIZE, 0, pointer));
            
            
            FloatBuffer memoryBuffer = pointer.getFloatBuffer(0, (int) getSize());
            int         stride       = ShaderSpec.getVertexStride();
            for (int i = 0; i < getVertexCount(); i++)
            {
                pos.get(i).get(i * stride, memoryBuffer);
                color.get(i).get(ShaderSpec.getPositionComponentCount() + (i * stride), memoryBuffer);
            }
            
            
            /*
            ByteBuffer  vertexBuffer = stack.calloc((int) getSizeInBytes());
            MemoryUtil.memCopy(MemoryUtil.memAddress(vertexBuffer), pointer.get(0), vertexBuffer.remaining());
            */
            
            /*
            
            If we are not using device-coherent-bit!
            
            VkMappedMemoryRange flushRange = VkMappedMemoryRange.mallocStack(stack)
                                                                .sType(VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE)
                                                                .pNext(VK_NULL_HANDLE)
                                                                .vertexMemory(getVertexMemory())
                                                                .offset(0)
                                                                .size(VK_WHOLE_SIZE);
            
            EngineUtils.checkError(vkFlushMappedMemoryRanges(device, flushRange));
            */
            
            vkUnmapMemory(device, memory);
        }
    }
    
    public long getSizeInBytes()
    {
        return ShaderSpec.getVertexStrideInBytes() * (long) pos.size();
    }
    
    public long getSize()
    {
        return ShaderSpec.getVertexStride() * (long) pos.size();
    }
    
    
    public void destroy(VkDevice device)
    {
        vkDestroyBuffer(device, getVertexBuffer(), null);
        vkFreeMemory(device, getVertexMemory(), null);
    }
}
