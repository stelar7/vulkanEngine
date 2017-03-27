package no.stelar7.vulcan.engine;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public class VertexSpecification
{
    
    private static int vertexBinding = 0;
    
    private static int positionCount = 3;
    private static int colorCount    = 4;
    
    private static int positionLocation = 0;
    private static int colorLocation    = 1;
    private static int vertexStride     = positionCount + colorCount;
    
    private static VkPipelineVertexInputStateCreateInfo     createInfo          = VkPipelineVertexInputStateCreateInfo.malloc();
    private static VkVertexInputBindingDescription.Buffer   vertexBindingBuffer = VkVertexInputBindingDescription.malloc(1);
    private static VkVertexInputAttributeDescription.Buffer vertexAttribsBuffer = VkVertexInputAttributeDescription.malloc(2);
    
    static
    {
        getVertexBindingBuffer().binding(VertexSpecification.getVertexBinding())
                                .stride(VertexSpecification.getVertexStride())
                                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
        
        getVertexAttributeBuffer().get(0)
                                  .location(VertexSpecification.getPositionLocation())
                                  .binding(VertexSpecification.getVertexBinding())
                                  .format(VK_FORMAT_R32G32B32_SFLOAT)
                                  .offset(VertexSpecification.getPositionOffset());
        
        getVertexAttributeBuffer().get(1)
                                  .location(VertexSpecification.getColorLocation())
                                  .binding(VertexSpecification.getVertexBinding())
                                  .format(VK_FORMAT_R32G32B32A32_SFLOAT)
                                  .offset(VertexSpecification.getColorOffset());
        
        
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
    
    public static void destroy()
    {
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
}
