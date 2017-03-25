package no.stelar7.vulcan.engine;

import org.lwjgl.*;
import org.lwjgl.vulkan.*;

import java.nio.*;

import static org.lwjgl.vulkan.VK10.*;

public class TestGame extends Game
{
    public TestGame(VulkanRenderer renderer)
    {
        super(renderer);
    }
    
    private PointerBuffer pBuff = BufferUtils.createPointerBuffer(1);
    private LongBuffer    lBuff = BufferUtils.createLongBuffer(1);
    
    private long       commandPoolHandle       = VK_NULL_HANDLE;
    private LongBuffer renderCompleteSemaphore = BufferUtils.createLongBuffer(1);
    
    
    private VkCommandBuffer commandBuffer;
    
    
    @Override
    public void update()
    {
        
    }
    
    @Override
    public void render()
    {
        renderer.getWindow().beginRender();
        
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.create()
                                                                     .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                                                                     .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        EngineUtils.checkError(vkBeginCommandBuffer(commandBuffer, beginInfo));
        
        VkRect2D area = VkRect2D.create().extent(renderer.getWindow().getSurfaceSize());
        
        VkClearDepthStencilValue depthValue = VkClearDepthStencilValue.create()
                                                                      .depth(0)
                                                                      .stencil(0);
        
        VkClearColorValue clearValue = VkClearColorValue.create().float32(0, 1).float32(1, 1).float32(2, 0).float32(3, 1);
        
        VkClearValue.Buffer clears = VkClearValue.create(2);
        clears.get(0).depthStencil(depthValue);
        clears.get(1).color(clearValue);
        
        
        VkRenderPassBeginInfo passBeginInfo = VkRenderPassBeginInfo.create()
                                                                   .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                                                                   .renderPass(renderer.getWindow().getRenderPassHandle())
                                                                   .framebuffer(renderer.getWindow().getActiveFramebuffer())
                                                                   .renderArea(area)
                                                                   .pClearValues(clears);
        
        vkCmdBeginRenderPass(commandBuffer, passBeginInfo, VK_SUBPASS_CONTENTS_INLINE);
        
        vkCmdEndRenderPass(commandBuffer);
        EngineUtils.checkError(vkEndCommandBuffer(commandBuffer));
        
        VkSubmitInfo submitInfo = VkSubmitInfo.create()
                                              .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                                              .pCommandBuffers(pBuff)
                                              .pSignalSemaphores(renderCompleteSemaphore);
        
        EngineUtils.checkError(vkQueueSubmit(renderer.getRenderQueue(), submitInfo, VK_NULL_HANDLE));
        
        renderer.getWindow().endRender(renderCompleteSemaphore);
    }
    
    @Override
    public void delete()
    {
        vkQueueWaitIdle(renderer.getRenderQueue());
        vkDestroySemaphore(renderer.getDevice(), renderCompleteSemaphore.get(0), null);
        vkDestroyCommandPool(renderer.getDevice(), commandPoolHandle, null);
    }
    
    @Override
    public void init()
    {
        createCommandPool();
        createCommandBuffer();
        createSemaphore();
    }
    
    
    private void createSemaphore()
    {
        
        VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.create()
                                                                   .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        
        vkCreateSemaphore(renderer.getDevice(), semaphoreInfo, null, lBuff);
        renderCompleteSemaphore.put(0, lBuff.get(0));
    }
    
    private void createCommandBuffer()
    {
        
        VkCommandBufferAllocateInfo info = VkCommandBufferAllocateInfo.create()
                                                                      .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                                                                      .commandPool(commandPoolHandle)
                                                                      .commandBufferCount(1)
                                                                      .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
        
        
        EngineUtils.checkError(vkAllocateCommandBuffers(renderer.getDevice(), info, pBuff));
        commandBuffer = new VkCommandBuffer(pBuff.get(0), renderer.getDevice());
    }
    
    private void createCommandPool()
    {
        VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.create()
                                                                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                                                                    .queueFamilyIndex(renderer.getGraphicsQueueIndex())
                                                                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT | VK_COMMAND_POOL_CREATE_TRANSIENT_BIT);
        
        EngineUtils.checkError(vkCreateCommandPool(renderer.getDevice(), createInfo, null, lBuff));
        commandPoolHandle = lBuff.get(0);
    }
}
