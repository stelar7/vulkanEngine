package no.stelar7.vulkan.engine.spec;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public final class VertexSpec
{
    
    private VertexSpec()
    {
        // Hide constructor
    }
    
    private static final VkPipelineVertexInputStateCreateInfo vertexInputState = VkPipelineVertexInputStateCreateInfo.calloc();
    
    static
    {
        
        VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(2);
        // Location 0 : Position
        attributeDescriptions.get(0)
                             .binding(0)
                             .location(0)
                             .format(VK_FORMAT_R32G32B32_SFLOAT)
                             .offset(0);
        // Location 1 : Color
        attributeDescriptions.get(1)
                             .binding(0)
                             .location(1)
                             .format(VK_FORMAT_R32G32B32A32_SFLOAT)
                             .offset(3 * Float.BYTES);
        
        // Binding description
        VkVertexInputBindingDescription.Buffer bindingDescriptor = VkVertexInputBindingDescription.calloc(1)
                                                                                                  .binding(0)
                                                                                                  .stride((3 + 4) * Float.BYTES)
                                                                                                  .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
        
        // Assign to vertex buffer
        vertexInputState.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                        .pVertexBindingDescriptions(bindingDescriptor)
                        .pVertexAttributeDescriptions(attributeDescriptions);
    }
    
    public static VkPipelineVertexInputStateCreateInfo getVertexInputState()
    {
        return vertexInputState;
    }
    
    public static void free()
    {
        vertexInputState.pVertexAttributeDescriptions().free();
        vertexInputState.pVertexBindingDescriptions().free();
        vertexInputState.free();
    }
}
