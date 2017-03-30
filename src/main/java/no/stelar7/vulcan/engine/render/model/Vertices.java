package no.stelar7.vulcan.engine.render.model;

import no.stelar7.vulcan.engine.EngineUtils;
import no.stelar7.vulcan.engine.render.*;
import no.stelar7.vulcan.engine.render.ShaderSpec.UniformSpec;
import org.joml.*;
import org.lwjgl.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;

public class Vertices
{
    
    private int size;
    
    private long vertexBuffer;
    private long vertexMemory;
    private long indexBuffer;
    private long indexMemory;
    
    
    private long uniformBuffer;
    private long uniformStagingBuffer;
    private long uniformMemory;
    private long uniformStagingMemory;
    
    public Vertices(VulkanRenderer renderer, List<Integer> inds, List<Vector3f> pos, List<Vector4f> color)
    {
        size = inds.size();
        
        setupVertexBuffer(renderer, pos, color);
        setupIndexBuffer(renderer, inds);
        setupUniformBuffer(renderer);
    }
    
    public void updateUniformBuffer(VulkanRenderer renderer, UniformSpec spec)
    {
        setUniformBufferData(renderer.getDevice(), spec);
        renderer.copyBuffer(uniformStagingBuffer, uniformBuffer, UniformSpec.BYTE_SIZE);
    }
    
    private void setupUniformBuffer(VulkanRenderer renderer)
    {
        uniformStagingBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageTransferFlag(), UniformSpec.BYTE_SIZE);
        uniformStagingMemory = renderer.createMemory(uniformStagingBuffer, ShaderSpec.getTransferMemoryFlags());
        
        uniformBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageUniformFlag(), UniformSpec.BYTE_SIZE);
        uniformMemory = renderer.createMemory(uniformBuffer, ShaderSpec.getDeviceLocalFlag());
        
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.mallocStack(1, stack)
                                                                             .buffer(uniformBuffer)
                                                                             .offset(0)
                                                                             .range(VK_WHOLE_SIZE);
            
            VkWriteDescriptorSet.Buffer writeDescriptorSet = VkWriteDescriptorSet.mallocStack(1, stack)
                                                                                 .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                                                                                 .pNext(VK_NULL_HANDLE)
                                                                                 .dstSet(renderer.getWindow().getDescriptorSetHandle())
                                                                                 .dstBinding(0)
                                                                                 .dstArrayElement(0)
                                                                                 .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                                                                                 .pBufferInfo(bufferInfo)
                                                                                 .pImageInfo(null)
                                                                                 .pTexelBufferView(null);
            
            vkUpdateDescriptorSets(renderer.getDevice(), writeDescriptorSet, null);
        }
    }
    
    private void setupIndexBuffer(VulkanRenderer renderer, List<Integer> inds)
    {
        long stagingBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageTransferFlag(), getIndexSizeInBytes());
        long stagingMemory = renderer.createMemory(stagingBuffer, ShaderSpec.getTransferMemoryFlags());
        setIndexBufferData(renderer.getDevice(), inds, stagingMemory);
        
        indexBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageIndexFlag(), getIndexSizeInBytes());
        indexMemory = renderer.createMemory(indexBuffer, ShaderSpec.getDeviceLocalFlag());
        renderer.copyBuffer(stagingBuffer, indexBuffer, getIndexSizeInBytes());
        
        renderer.destroyBuffer(stagingBuffer);
        renderer.destroyMemory(stagingMemory);
    }
    
    private void setUniformBufferData(VkDevice device, UniformSpec spec)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer pointer = stack.mallocPointer(1);
            EngineUtils.checkError(vkMapMemory(device, uniformStagingMemory, 0, VK_WHOLE_SIZE, 0, pointer));
            FloatBuffer memoryBuffer = pointer.getFloatBuffer(0, getVertexSize());
            
            spec.getModel().get(memoryBuffer);
            spec.getView().get(16, memoryBuffer);
            spec.getPerspective().get(16, memoryBuffer);
            
            vkUnmapMemory(device, uniformStagingMemory);
        }
    }
    
    private void setIndexBufferData(VkDevice device, List<Integer> inds, long memory)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer pointer = stack.mallocPointer(1);
            EngineUtils.checkError(vkMapMemory(device, memory, 0, VK_WHOLE_SIZE, 0, pointer));
            
            IntBuffer memoryBuffer = pointer.getIntBuffer(0, getVertexSize());
            for (int i = 0; i < getIndexCount(); i++)
            {
                memoryBuffer.put(i, inds.get(i));
            }
            
            vkUnmapMemory(device, memory);
        }
    }
    
    private void setupVertexBuffer(VulkanRenderer renderer, List<Vector3f> pos, List<Vector4f> color)
    {
        long stagingBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageTransferFlag(), getVertexSizeInBytes());
        long stagingMemory = renderer.createMemory(stagingBuffer, ShaderSpec.getTransferMemoryFlags());
        setVertexBufferData(renderer.getDevice(), pos, color, stagingMemory);
        
        vertexBuffer = renderer.createBuffer(ShaderSpec.getBufferUsageVertexFlag(), getVertexSizeInBytes());
        vertexMemory = renderer.createMemory(vertexBuffer, ShaderSpec.getDeviceLocalFlag());
        renderer.copyBuffer(stagingBuffer, vertexBuffer, getVertexSizeInBytes());
        
        renderer.destroyBuffer(stagingBuffer);
        renderer.destroyMemory(stagingMemory);
    }
    
    public void setVertexBufferData(VkDevice device, List<Vector3f> pos, List<Vector4f> color, long memory)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer pointer = stack.mallocPointer(1);
            EngineUtils.checkError(vkMapMemory(device, memory, 0, VK_WHOLE_SIZE, 0, pointer));
            
            
            FloatBuffer memoryBuffer = pointer.getFloatBuffer(0, getVertexSize());
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
    
    public long getUniformBuffer()
    {
        return uniformBuffer;
    }
    
    public long getUniformMemory()
    {
        return uniformMemory;
    }
    
    public int getVertexSizeInBytes()
    {
        return ShaderSpec.getVertexStrideInBytes() * size;
    }
    
    public int getVertexSize()
    {
        return ShaderSpec.getVertexStride() * size;
    }
    
    public int getIndexSizeInBytes()
    {
        return size * Integer.BYTES;
    }
    
    public void destroy(VkDevice device)
    {
        vkDestroyBuffer(device, getVertexBuffer(), null);
        vkDestroyBuffer(device, getIndexBuffer(), null);
        vkDestroyBuffer(device, getUniformBuffer(), null);
        vkDestroyBuffer(device, uniformStagingBuffer, null);
        
        vkFreeMemory(device, getVertexMemory(), null);
        vkFreeMemory(device, getIndexMemory(), null);
        vkFreeMemory(device, getUniformMemory(), null);
        vkFreeMemory(device, uniformStagingMemory, null);
    }
}
