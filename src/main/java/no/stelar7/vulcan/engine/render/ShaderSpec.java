package no.stelar7.vulcan.engine.render;

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
    
    public static int getVertexMemoryFlags()
    {
        return VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
    }
    
    public static int getBufferUsageFlag()
    {
        return VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
    }
    
    public static int getLastAttribOffset()
    {
        return getColorOffset();
    }
}
