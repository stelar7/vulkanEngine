package no.stelar7.vulcan.engine.render;

import org.joml.Matrix4f;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public final class ShaderSpec
{
    private ShaderSpec()
    {
        // Hide public constructor
    }
    
    private static final int POSITION_COMPONENT_COUNT = 3;
    private static final int COLOR_COMPONENT_COUNT    = 4;
    
    private static final int POSITION_LOCATION = 0;
    private static final int COLOR_LOCATION    = 1;
    private static final int VERTEX_STRIDE     = POSITION_COMPONENT_COUNT + COLOR_COMPONENT_COUNT;
    
    private static final int FORMAT_1_ELEMENTS = VK_FORMAT_R32_SFLOAT;
    private static final int FORMAT_2_ELEMENTS = VK_FORMAT_R32G32_SFLOAT;
    private static final int FORMAT_3_ELEMENTS = VK_FORMAT_R32G32B32_SFLOAT;
    private static final int FORMAT_4_ELEMENTS = VK_FORMAT_R32G32B32A32_SFLOAT;
    
    private static VkPipelineVertexInputStateCreateInfo     createInfo          = VkPipelineVertexInputStateCreateInfo.malloc();
    private static VkVertexInputBindingDescription.Buffer   vertexBindingBuffer = VkVertexInputBindingDescription.malloc(1);
    private static VkVertexInputAttributeDescription.Buffer vertexAttribsBuffer = VkVertexInputAttributeDescription.malloc(2);
    
    private static VkDescriptorSetLayoutBinding.Buffer uniformLayoutBinding = VkDescriptorSetLayoutBinding.malloc(1);
    
    public static final class UniformSpec
    {
        private Matrix4f model;
        private Matrix4f view;
        private Matrix4f perspective;
        
        public Matrix4f getModel()
        {
            return model;
        }
        
        public Matrix4f getView()
        {
            return view;
        }
        
        public Matrix4f getPerspective()
        {
            return perspective;
        }
        
        public static final int ELEMENT_SIZE = 16 + 16 + 16;
        public static final int BYTE_SIZE    = ELEMENT_SIZE * Float.BYTES;
    }
    
    static
    {
        getVertexBindingBuffer().binding(ShaderSpec.getVertexBindingIndex())
                                .stride(ShaderSpec.getVertexStrideInBytes())
                                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
        
        getVertexAttributeBuffer().get(0)
                                  .location(ShaderSpec.getPositionLocation())
                                  .binding(getVertexBindingBuffer().binding())
                                  .format(FORMAT_3_ELEMENTS)
                                  .offset(ShaderSpec.getPositionOffsetInBytes());
        
        getVertexAttributeBuffer().get(1)
                                  .location(ShaderSpec.getColorLocation())
                                  .binding(getVertexBindingBuffer().binding())
                                  .format(FORMAT_4_ELEMENTS)
                                  .offset(ShaderSpec.getColorOffsetInBytes());
        
        
        getCreateInfo().sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                       .pNext(VK_NULL_HANDLE)
                       .flags(0)
                       .pVertexBindingDescriptions(getVertexBindingBuffer())
                       .pVertexAttributeDescriptions(getVertexAttributeBuffer());
        
        getUniformLayoutBinding().get(0)
                                 .binding(getVertexBindingBuffer().binding())
                                 .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                                 .pImmutableSamplers(null)
                                 .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                                 .descriptorCount(1);
        
        
    }
    
    
    public static int getPositionComponentCount()
    {
        return POSITION_COMPONENT_COUNT;
    }
    
    public static int getColorComponentCount()
    {
        return COLOR_COMPONENT_COUNT;
    }
    
    public static int getVertexStride()
    {
        return VERTEX_STRIDE;
    }
    
    public static int getVertexStrideInBytes()
    {
        return VERTEX_STRIDE * Float.BYTES;
    }
    
    public static int getVertexBindingIndex()
    {
        return 0;
    }
    
    public static int getPositionLocation()
    {
        return POSITION_LOCATION;
    }
    
    public static int getColorLocation()
    {
        return COLOR_LOCATION;
    }
    
    public static VkDescriptorSetLayoutBinding.Buffer getUniformLayoutBinding()
    {
        return uniformLayoutBinding;
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
    
    
    public static void destroy()
    {
        vertexBindingBuffer.free();
        vertexAttribsBuffer.free();
        createInfo.free();
        
        uniformLayoutBinding.free();
    }
    
    
    public static int getColorOffset()
    {
        return getPositionComponentCount();
    }
    
    public static int getColorOffsetInBytes()
    {
        return getColorOffset() * Float.BYTES;
    }
    
    public static int getPositionOffsetInBytes()
    {
        return 0;
    }
    
    public static int getLastAttribIndex()
    {
        return COLOR_LOCATION;
    }
    
    public static int getLastAttribOffsetInBytes()
    {
        return getColorOffsetInBytes();
    }
    
    public static int getTransferMemoryFlags()
    {
        return VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
    }
    
    public static int getDeviceLocalFlag()
    {
        return VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
    }
    
    public static int getBufferUsageVertexFlag()
    {
        return VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    }
    
    public static int getBufferUsageUniformFlag()
    {
        return VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
    }
    
    public static int getBufferUsageIndexFlag()
    {
        return VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    }
    
    public static int getBufferUsageTransferFlag()
    {
        return VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
    }
    
    public static int getLastAttribOffset()
    {
        return getColorOffset();
    }
}
