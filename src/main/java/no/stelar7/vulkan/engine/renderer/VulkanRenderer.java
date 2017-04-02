package no.stelar7.vulkan.engine.renderer;

import no.stelar7.vulkan.engine.EngineUtils;
import no.stelar7.vulkan.engine.game.*;
import no.stelar7.vulkan.engine.spec.*;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugReport.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class VulkanRenderer
{
    private Game game;
    
    private final Object lock = new Object();
    private boolean shouldClose;
    
    private static final int VK_API_VERSION         = VK_MAKE_VERSION(1, 0, 3);
    private static final int VK_APPLICATION_VERSION = VK_MAKE_VERSION(0, 1, 0);
    
    private long windowHandle            = VK_NULL_HANDLE;
    private long surfaceHandle           = VK_NULL_HANDLE;
    private long debugCallback           = VK_NULL_HANDLE;
    private long commandPoolHandle       = VK_NULL_HANDLE;
    private long renderpassHandle        = VK_NULL_HANDLE;
    private long renderCommandPoolHandle = VK_NULL_HANDLE;
    private long descriptorPoolHandle    = VK_NULL_HANDLE;
    private long descriptorSetLayout     = VK_NULL_HANDLE;
    private long descriptorSetHandle     = VK_NULL_HANDLE;
    
    private long imageAcquredSemaphore   = VK_NULL_HANDLE;
    private long renderCompleteSemaphore = VK_NULL_HANDLE;
    
    private VkInstance       instance;
    private VkPhysicalDevice physicalDevice;
    private VkCommandBuffer  setupCommandBuffer;
    private VkCommandBuffer  postPresentCommandBuffer;
    private VkQueue          deviceQueue;
    
    private DeviceFamily        deviceFamily;
    private ColorAndDepthFormat colorAndDepthFormat;
    private Pipeline            pipeline;
    private UniformBuffer       uniformBuffer;
    
    private Swapchain         swapchain;
    private long[]            framebuffers;
    private VkCommandBuffer[] renderCommandBuffers;
    private DepthStencil      depthStencil;
    private boolean shouldRecreate = false;
    private int width;
    private int height;
    
    private List<Long> shaders = new ArrayList<>();
    
    private static final ByteBuffer[] validationLayers = {
            memUTF8("VK_LAYER_LUNARG_standard_validation"),
            };
    
    private VkDebugReportCallbackEXT debugCallbackFunc = new VkDebugReportCallbackEXT()
    {
        @Override
        public int invoke(int flags, int objectType, long object, long location, int messageCode, long pLayerPrefix, long pMessage, long pUserData)
        {
            System.err.format("%s: [%s] Code %d : %s%n", EngineUtils.vkDebugFlagToString(flags), pLayerPrefix, messageCode, memASCII(pMessage));
            return VK_FALSE;
        }
    };
    
    private GLFWKeyCallback keyCallback = new GLFWKeyCallback()
    {
        @Override
        public void invoke(long window, int key, int scancode, int action, int mods)
        {
            if (action != GLFW_RELEASE)
            {
                return;
            }
            if (key == GLFW_KEY_ESCAPE)
            {
                glfwSetWindowShouldClose(window, true);
            }
        }
    };
    
    private GLFWFramebufferSizeCallback framebufferCallback = new GLFWFramebufferSizeCallback()
    {
        @Override
        public void invoke(long window, int width, int height)
        {
            if (width <= 0 || height <= 0)
            {
                return;
            }
            
            synchronized (lock)
            {
                shouldRecreate = true;
                VulkanRenderer.this.width = width;
                VulkanRenderer.this.height = height;
            }
        }
    };
    
    
    public void start()
    {
        if (game == null)
        {
            throw new RuntimeException("Renderer started without a game!");
        }
        
        new Thread(this::loop).start();
        
        while (!shouldClose)
        {
            glfwWaitEvents();
        }
        
        synchronized (lock)
        {
            destroy();
        }
    }
    
    private void destroy()
    {
        vkDeviceWaitIdle(deviceFamily.getDevice());
        
        
        vkDestroySemaphore(deviceFamily.getDevice(), renderCompleteSemaphore, null);
        vkDestroySemaphore(deviceFamily.getDevice(), imageAcquredSemaphore, null);
        
        for (long framebuffer : framebuffers)
        {
            vkDestroyFramebuffer(deviceFamily.getDevice(), framebuffer, null);
        }
        
        depthStencil.free(deviceFamily.getDevice());
        swapchain.free(deviceFamily.getDevice(), true);
        for (Long shader : shaders)
        {
            vkDestroyShaderModule(deviceFamily.getDevice(), shader, null);
        }
        
        uniformBuffer.free(deviceFamily.getDevice());
        pipeline.free(deviceFamily.getDevice());
        
        vkDestroyDescriptorSetLayout(deviceFamily.getDevice(), descriptorSetLayout, null);
        vkDestroyDescriptorPool(deviceFamily.getDevice(), descriptorPoolHandle, null);
        vkDestroyRenderPass(deviceFamily.getDevice(), renderpassHandle, null);
        vkDestroyCommandPool(deviceFamily.getDevice(), renderCommandPoolHandle, null);
        vkDestroyCommandPool(deviceFamily.getDevice(), commandPoolHandle, null);
        
        deviceFamily.free();
        
        vkDestroySurfaceKHR(instance, surfaceHandle, null);
        vkDestroyDebugReportCallbackEXT(instance, debugCallback, null);
        vkDestroyInstance(instance, null);
        
        keyCallback.free();
        glfwSetErrorCallback(null).free();
        glfwTerminate();
    }
    
    public VulkanRenderer(int width, int height, String title)
    {
        this.width = width;
        this.height = height;
        
        
        GLFWErrorCallback.createPrint(System.err).set();
        
        if (!(glfwInit() && glfwVulkanSupported()))
        {
            throw new RuntimeException("Failed to init");
        }
        
        instance = createInstance(title, glfwGetRequiredInstanceExtensions());
        debugCallback = createDebug(instance, VK_DEBUG_REPORT_ERROR_BIT_EXT | VK_DEBUG_REPORT_WARNING_BIT_EXT | VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT);
        physicalDevice = getFirstPhysicalDevice(instance);
        deviceFamily = createDeviceAndQueue(physicalDevice);
        windowHandle = createWindow(width, height, title);
        surfaceHandle = createSurface(instance, windowHandle);
        setQueuePresent(physicalDevice, surfaceHandle, deviceFamily);
        colorAndDepthFormat = getColorFormat(physicalDevice, surfaceHandle);
        commandPoolHandle = createCommandPool(deviceFamily);
        setupCommandBuffer = createCommandBuffer(deviceFamily.getDevice(), commandPoolHandle);
        postPresentCommandBuffer = createCommandBuffer(deviceFamily.getDevice(), commandPoolHandle);
        deviceQueue = createDeviceQueue(deviceFamily);
        renderpassHandle = createRenderpass(deviceFamily.getDevice(), colorAndDepthFormat);
        renderCommandPoolHandle = createCommandPool(deviceFamily);
        
        // TODO: vertex buffer, index buffer?
        
        uniformBuffer = createUniformBuffer(deviceFamily);
        descriptorPoolHandle = createDescriptorPool(deviceFamily.getDevice());
        descriptorSetLayout = createDescriptorSetLayout(deviceFamily.getDevice());
        descriptorSetHandle = createDescriptorSet(deviceFamily.getDevice(), descriptorPoolHandle, descriptorSetLayout, uniformBuffer);
        pipeline = createPipeline(deviceFamily.getDevice(), renderpassHandle, VertexSpec.getVertexInputState(), descriptorSetLayout);
        
        imageAcquredSemaphore = createSemaphore(deviceFamily.getDevice());
        renderCompleteSemaphore = createSemaphore(deviceFamily.getDevice());
        
        glfwShowWindow(windowHandle);
    }
    
    
    private void postPresentBarrier(long image, VkCommandBuffer buffer, VkQueue queue)
    {
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc()
                                                                     .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        
        EngineUtils.checkError(vkBeginCommandBuffer(buffer, beginInfo));
        beginInfo.free();
        
        
        int srcStage  = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        int dstStage  = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        int mask      = VK_IMAGE_ASPECT_COLOR_BIT;
        int srcLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        int dstLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        int srcAccess = VK_ACCESS_MEMORY_READ_BIT;
        int dstAccess = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        
        imageBarrier(buffer, image, srcStage, dstStage, mask, srcLayout, srcAccess, dstLayout, dstAccess);
        
        EngineUtils.checkError(vkEndCommandBuffer(buffer));
        submitCommandBuffer(queue, buffer);
    }
    
    
    private long createSemaphore(VkDevice device)
    {
        VkSemaphoreCreateInfo createInfo = VkSemaphoreCreateInfo.calloc()
                                                                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        
        LongBuffer handleHolder = memAllocLong(1);
        EngineUtils.checkError(vkCreateSemaphore(device, createInfo, null, handleHolder));
        long handle = handleHolder.get(0);
        
        memFree(handleHolder);
        createInfo.free();
        
        return handle;
    }
    
    
    private void recreateSwapchain()
    {
        vkDeviceWaitIdle(deviceFamily.getDevice());
        
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc()
                                                                     .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        
        EngineUtils.checkError(vkBeginCommandBuffer(setupCommandBuffer, beginInfo));
        beginInfo.free();
        
        long oldChain = swapchain == null ? VK_NULL_HANDLE : swapchain.getHandle();
        
        if (swapchain != null)
        {
            swapchain.free(deviceFamily.getDevice(), false);
        }
        swapchain = createSwapchain(deviceFamily.getDevice(), physicalDevice, surfaceHandle, oldChain, setupCommandBuffer, width, height, colorAndDepthFormat);
        
        
        if (depthStencil != null)
        {
            depthStencil.free(deviceFamily.getDevice());
        }
        depthStencil = createDepthStencil(deviceFamily, colorAndDepthFormat, setupCommandBuffer);
        
        EngineUtils.checkError(vkEndCommandBuffer(setupCommandBuffer));
        submitCommandBuffer(deviceQueue, setupCommandBuffer);
        vkQueueWaitIdle(deviceQueue);
        
        if (framebuffers != null)
        {
            for (long framebuffer : framebuffers)
            {
                vkDestroyFramebuffer(deviceFamily.getDevice(), framebuffer, null);
            }
        }
        
        framebuffers = createFramebuffers(deviceFamily.getDevice(), swapchain, renderpassHandle, width, height, depthStencil);
        
        if (renderCommandBuffers != null)
        {
            vkResetCommandPool(deviceFamily.getDevice(), renderCommandPoolHandle, 0);
        }
        
        renderCommandBuffers = createRenderCommandBufffers(deviceFamily.getDevice(), renderCommandPoolHandle, framebuffers, renderpassHandle, width, height, pipeline, descriptorSetHandle, game.getGameObjects());
        
        shouldRecreate = false;
    }
    
    private VkCommandBuffer[] createRenderCommandBufffers(VkDevice device, long cmdPool, long[] framebuffers, long renderpass, int width, int height, Pipeline pipeline, long descriptorSet, List<GameObject> gameObjects)
    {
        VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc()
                                                                              .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                                                                              .commandBufferCount(framebuffers.length)
                                                                              .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                                                                              .commandPool(cmdPool);
        
        PointerBuffer bufferHandles = memAllocPointer(framebuffers.length);
        EngineUtils.checkError(vkAllocateCommandBuffers(device, allocateInfo, bufferHandles));
        
        
        VkCommandBuffer[] buffers = new VkCommandBuffer[framebuffers.length];
        for (int i = 0; i < buffers.length; i++)
        {
            buffers[i] = new VkCommandBuffer(bufferHandles.get(i), device);
        }
        memFree(bufferHandles);
        allocateInfo.free();
        
        
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc()
                                                                     .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        
        VkClearValue.Buffer clearValues = VkClearValue.calloc(2);
        clearValues.get(0).color()
                   .float32(0, .8f)
                   .float32(1, .4f)
                   .float32(2, .6f)
                   .float32(3, 1);
        clearValues.get(1).depthStencil().depth(0).stencil(0);
        
        VkRenderPassBeginInfo passBeginInfo = VkRenderPassBeginInfo.calloc()
                                                                   .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                                                                   .pClearValues(clearValues)
                                                                   .renderPass(renderpass);
        passBeginInfo.renderArea().offset().set(0, 0);
        passBeginInfo.renderArea().extent().set(width, height);
        
        VkViewport.Buffer viewport = VkViewport.calloc(1)
                                               .height(height)
                                               .width(width)
                                               .minDepth(1)
                                               .maxDepth(0);
        
        VkRect2D.Buffer scissor = VkRect2D.calloc(1);
        scissor.extent().set(width, height);
        scissor.offset().set(0, 0);
        
        LongBuffer descriptorHolder = memAllocLong(1).put(0, descriptorSet);
        LongBuffer offsetHolder     = memAllocLong(1).put(0, 0);
        LongBuffer vertexHolder     = memAllocLong(1);
        
        for (int i = 0; i < buffers.length; i++)
        {
            VkCommandBuffer renderBuffer = buffers[i];
            
            passBeginInfo.framebuffer(framebuffers[i]);
            EngineUtils.checkError(vkBeginCommandBuffer(renderBuffer, beginInfo));
            
            vkCmdBeginRenderPass(renderBuffer, passBeginInfo, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdSetViewport(renderBuffer, 0, viewport);
            vkCmdSetScissor(renderBuffer, 0, scissor);
            vkCmdBindDescriptorSets(renderBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getLayout(), 0, descriptorHolder, null);
            vkCmdBindPipeline(renderBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getHandle());
            
            for (GameObject obj : gameObjects)
            {
                vertexHolder.put(0, obj.getModel().getVertexBuffer().getBuffer());
                vkCmdBindVertexBuffers(renderBuffer, 0, vertexHolder, offsetHolder);
                vkCmdDraw(renderBuffer, obj.getModel().getVertexBuffer().getSize(), 1, 0, 0);
            }
            
            vkCmdEndRenderPass(renderBuffer);
            
            int srcStage  = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            int dstStage  = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            int mask      = VK_IMAGE_ASPECT_COLOR_BIT;
            int srcLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
            int dstLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
            int srcAccess = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            int dstAccess = VK_ACCESS_MEMORY_READ_BIT;
            
            imageBarrier(renderBuffer, swapchain.getImage(i), srcStage, dstStage, mask, srcLayout, srcAccess, dstLayout, dstAccess);
            
            EngineUtils.checkError(vkEndCommandBuffer(renderBuffer));
        }
        
        scissor.free();
        viewport.free();
        beginInfo.free();
        clearValues.free();
        passBeginInfo.free();
        memFree(vertexHolder);
        memFree(offsetHolder);
        memFree(descriptorHolder);
        
        return buffers;
    }
    
    private long[] createFramebuffers(VkDevice device, Swapchain swapchain, long renderpass, int width, int height, DepthStencil depthStencil)
    {
        LongBuffer attachments = memAllocLong(2).put(1, depthStencil.getView());
        
        VkFramebufferCreateInfo createInfo = VkFramebufferCreateInfo.calloc()
                                                                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                                                                    .pAttachments(attachments)
                                                                    .renderPass(renderpass)
                                                                    .height(height)
                                                                    .width(width)
                                                                    .layers(1);
        
        long[]     localBuffers = new long[swapchain.getImageCount()];
        LongBuffer handleHolder = memAllocLong(1);
        
        for (int i = 0; i < localBuffers.length; i++)
        {
            attachments.put(0, swapchain.getViews(i));
            EngineUtils.checkError(vkCreateFramebuffer(device, createInfo, null, handleHolder));
            localBuffers[i] = handleHolder.get(0);
        }
        
        memFree(handleHolder);
        memFree(attachments);
        createInfo.free();
        
        return localBuffers;
    }
    
    private void submitCommandBuffer(VkQueue deviceQueue, VkCommandBuffer cmdBuffer)
    {
        PointerBuffer buffer = memAllocPointer(1).put(0, cmdBuffer);
        
        VkSubmitInfo submitInfo = VkSubmitInfo.calloc()
                                              .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                                              .pCommandBuffers(buffer);
        
        EngineUtils.checkError(vkQueueSubmit(deviceQueue, submitInfo, 0));
        
        memFree(buffer);
        submitInfo.free();
    }
    
    private DepthStencil createDepthStencil(DeviceFamily deviceFamily, ColorAndDepthFormat cad, VkCommandBuffer buffer)
    {
        DepthStencil stencil = new DepthStencil();
        
        VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc()
                                                             .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                                                             .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                                                             .tiling(VK_IMAGE_TILING_OPTIMAL)
                                                             .samples(VK_SAMPLE_COUNT_1_BIT)
                                                             .format(cad.getDepthFormat())
                                                             .imageType(VK_IMAGE_TYPE_2D)
                                                             .arrayLayers(1)
                                                             .mipLevels(1);
        imageCreateInfo.extent()
                       .height(height)
                       .width(width)
                       .depth(1);
        
        
        LongBuffer handleHolder = memAllocLong(1);
        EngineUtils.checkError(vkCreateImage(deviceFamily.getDevice(), imageCreateInfo, null, handleHolder));
        stencil.setImage(handleHolder.get(0));
        
        VkMemoryRequirements memoryRequirements = VkMemoryRequirements.calloc();
        vkGetImageMemoryRequirements(deviceFamily.getDevice(), stencil.getImage(), memoryRequirements);
        
        VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc()
                                                                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                                                                .allocationSize(memoryRequirements.size())
                                                                .memoryTypeIndex(EngineUtils.findMemoryTypeIndex(deviceFamily.getMemoryProperties(), memoryRequirements, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
        
        EngineUtils.checkError(vkAllocateMemory(deviceFamily.getDevice(), allocateInfo, null, handleHolder));
        stencil.setMemory(handleHolder.get(0));
        
        vkBindImageMemory(deviceFamily.getDevice(), stencil.getImage(), stencil.getMemory(), 0);
        
        int stage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        int mask  = VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT;
        
        imageBarrier(buffer, stencil.getImage(), stage, stage, mask, VK_IMAGE_LAYOUT_UNDEFINED, 0, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
        
        
        VkImageViewCreateInfo viewCreateInfo = VkImageViewCreateInfo.calloc()
                                                                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                                                                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                                                                    .format(cad.getDepthFormat())
                                                                    .image(stencil.getImage());
        viewCreateInfo.subresourceRange()
                      .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT)
                      .levelCount(1)
                      .layerCount(1);
        
        EngineUtils.checkError(vkCreateImageView(deviceFamily.getDevice(), viewCreateInfo, null, handleHolder));
        stencil.setView(handleHolder.get(0));
        
        
        memoryRequirements.free();
        imageCreateInfo.free();
        viewCreateInfo.free();
        memFree(handleHolder);
        allocateInfo.free();
        
        return stencil;
    }
    
    private int getBestPresentMode(VkPhysicalDevice physicalDevice, long surface)
    {
        IntBuffer presentModeCount = memAllocInt(1);
        EngineUtils.checkError(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, null));
        int modeCount = presentModeCount.get(0);
        
        IntBuffer presentModes = memAllocInt(modeCount);
        EngineUtils.checkError(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, presentModes));
        
        int presentMode = VK_PRESENT_MODE_FIFO_KHR;
        for (int i = 0; i < modeCount; i++)
        {
            int mode = presentModes.get(i);
            
            if (mode == VK_PRESENT_MODE_IMMEDIATE_KHR)
            {
                presentMode = mode;
            }
            
            if (mode == VK_PRESENT_MODE_MAILBOX_KHR)
            {
                presentMode = mode;
                break;
            }
        }
        
        memFree(presentModeCount);
        memFree(presentModes);
        
        return presentMode;
    }
    
    private int getSwapchainImageCount(VkSurfaceCapabilitiesKHR surfaceCapabilities)
    {
        int preferedCount = surfaceCapabilities.minImageCount() + 1;
        
        if (surfaceCapabilities.maxImageCount() > 0 && preferedCount > surfaceCapabilities.maxImageCount())
        {
            preferedCount = surfaceCapabilities.maxImageCount();
        }
        
        return preferedCount;
    }
    
    private int getPreTransform(VkSurfaceCapabilitiesKHR surfaceCapabilities)
    {
        int preTransform = surfaceCapabilities.currentTransform();
        
        if (EngineUtils.hasFlag(surfaceCapabilities.supportedTransforms(), VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR))
        {
            preTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
        }
        
        return preTransform;
    }
    
    private void adjustFramebufferSize(VkSurfaceCapabilitiesKHR surfaceCapabilities, int newWidth, int newHeight)
    {
        
        VkExtent2D size = surfaceCapabilities.currentExtent();
        if (size.width() != -1 && size.height() != -1)
        {
            this.width = size.width();
            this.height = size.height();
        } else
        {
            this.width = newWidth;
            this.height = newHeight;
        }
        
    }
    
    
    private Swapchain createSwapchain(VkDevice device, VkPhysicalDevice physicalDevice, long surface, long oldChain, VkCommandBuffer buffer, int newWidth, int newHeight, ColorAndDepthFormat cad)
    {
        Swapchain localChain = new Swapchain();
        
        VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.calloc();
        EngineUtils.checkError(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, surfaceCapabilities));
        int presentMode         = getBestPresentMode(physicalDevice, surface);
        int swapchainImageCount = getSwapchainImageCount(surfaceCapabilities);
        int preTransform        = getPreTransform(surfaceCapabilities);
        adjustFramebufferSize(surfaceCapabilities, newWidth, newHeight);
        surfaceCapabilities.free();
        
        
        VkSwapchainCreateInfoKHR swapchainCreateInfo = VkSwapchainCreateInfoKHR.calloc()
                                                                               .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                                                                               .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                                                                               .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                                                                               .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                                                                               .imageColorSpace(cad.getColorSpace())
                                                                               .minImageCount(swapchainImageCount)
                                                                               .imageFormat(cad.getColorFormat())
                                                                               .preTransform(preTransform)
                                                                               .presentMode(presentMode)
                                                                               .oldSwapchain(oldChain)
                                                                               .imageArrayLayers(1)
                                                                               .surface(surface)
                                                                               .clipped(true);
        swapchainCreateInfo.imageExtent()
                           .width(width)
                           .height(height);
        
        LongBuffer handleHolder = memAllocLong(1);
        EngineUtils.checkError(vkCreateSwapchainKHR(device, swapchainCreateInfo, null, handleHolder));
        localChain.setHandle(handleHolder.get(0));
        
        swapchainCreateInfo.free();
        
        if (oldChain != VK_NULL_HANDLE)
        {
            vkDestroySwapchainKHR(device, oldChain, null);
        }
        
        
        IntBuffer imageCountHolder = memAllocInt(1);
        EngineUtils.checkError(vkGetSwapchainImagesKHR(device, localChain.getHandle(), imageCountHolder, null));
        int imageCount = imageCountHolder.get(0);
        
        LongBuffer swapImages = memAllocLong(imageCount);
        EngineUtils.checkError(vkGetSwapchainImagesKHR(device, localChain.getHandle(), imageCountHolder, swapImages));
        memFree(imageCountHolder);
        
        long[] images = new long[imageCount];
        long[] views  = new long[imageCount];
        
        
        VkImageViewCreateInfo viewCreateInfo = VkImageViewCreateInfo.calloc()
                                                                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                                                                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                                                                    .format(cad.getColorFormat());
        viewCreateInfo.components()
                      .r(VK_COMPONENT_SWIZZLE_R)
                      .g(VK_COMPONENT_SWIZZLE_G)
                      .b(VK_COMPONENT_SWIZZLE_B)
                      .a(VK_COMPONENT_SWIZZLE_A);
        viewCreateInfo.subresourceRange()
                      .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                      .levelCount(1)
                      .layerCount(1);
        
        
        int stage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        
        for (int i = 0; i < imageCount; i++)
        {
            images[i] = swapImages.get(i);
            imageBarrier(buffer, images[i], stage, stage, VK_IMAGE_ASPECT_COLOR_BIT, VK_IMAGE_LAYOUT_UNDEFINED, 0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
            viewCreateInfo.image(images[i]);
            EngineUtils.checkError(vkCreateImageView(device, viewCreateInfo, null, handleHolder));
            views[i] = handleHolder.get(0);
        }
        
        
        memFree(swapImages);
        memFree(handleHolder);
        viewCreateInfo.free();
        
        localChain.setImages(images);
        localChain.setViews(views);
        return localChain;
    }
    
    private void imageBarrier(VkCommandBuffer buffer, long image, int srcStage, int dstStage, int mask, int oldLayout, int srcAccess, int newLayout, int dstAccess)
    {
        VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(1)
                                                                   .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                                                                   .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                                                                   .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                                                                   .srcAccessMask(srcAccess)
                                                                   .dstAccessMask(dstAccess)
                                                                   .oldLayout(oldLayout)
                                                                   .newLayout(newLayout)
                                                                   .image(image);
        barriers.get(0)
                .subresourceRange()
                .aspectMask(mask)
                .levelCount(1)
                .layerCount(1);
        
        vkCmdPipelineBarrier(buffer, srcStage, dstStage, 0, null, null, barriers);
        barriers.free();
    }
    
    private Pipeline createPipeline(VkDevice device, long renderpassHandle, VkPipelineVertexInputStateCreateInfo vertexInputState, long descriptorSetLayout)
    {
        Pipeline localPipeline = new Pipeline();
        
        VkPipelineInputAssemblyStateCreateInfo inputAssemblyState = VkPipelineInputAssemblyStateCreateInfo.calloc()
                                                                                                          .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                                                                                                          .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        
        VkPipelineRasterizationStateCreateInfo rasterizationState = VkPipelineRasterizationStateCreateInfo.calloc()
                                                                                                          .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                                                                                                          .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                                                                                                          .polygonMode(VK_POLYGON_MODE_FILL)
                                                                                                          .cullMode(VK_CULL_MODE_BACK_BIT)
                                                                                                          .lineWidth(1);
        
        VkPipelineColorBlendAttachmentState.Buffer colorWriteMask = VkPipelineColorBlendAttachmentState.calloc(1)
                                                                                                       .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
        
        VkPipelineColorBlendStateCreateInfo colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc()
                                                                                                 .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                                                                                                 .pAttachments(colorWriteMask);
        
        VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc()
                                                                                           .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                                                                                           .viewportCount(1)
                                                                                           .scissorCount(1);
        
        IntBuffer dynStates = memAllocInt(2);
        dynStates.put(VK_DYNAMIC_STATE_VIEWPORT).put(VK_DYNAMIC_STATE_SCISSOR).flip();
        VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc()
                                                                                        .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                                                                                        .pDynamicStates(dynStates);
        
        VkPipelineDepthStencilStateCreateInfo depthStencilState = VkPipelineDepthStencilStateCreateInfo.calloc()
                                                                                                       .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                                                                                                       .depthTestEnable(true)
                                                                                                       .depthWriteEnable(true)
                                                                                                       .depthCompareOp(VK_COMPARE_OP_GREATER_OR_EQUAL);
        depthStencilState.back()
                         .failOp(VK_STENCIL_OP_KEEP)
                         .passOp(VK_STENCIL_OP_KEEP)
                         .compareOp(VK_COMPARE_OP_ALWAYS);
        depthStencilState.front(depthStencilState.back());
        
        
        VkPipelineMultisampleStateCreateInfo multisampleState = VkPipelineMultisampleStateCreateInfo.calloc()
                                                                                                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                                                                                                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
        
        VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2);
        shaderStages.get(0).set(loadShader(device, "shaders/compiled/basic.vert.spv", VK_SHADER_STAGE_VERTEX_BIT));
        shaderStages.get(1).set(loadShader(device, "shaders/compiled/basic.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT));
        
        LongBuffer setLayout = memAllocLong(1).put(0, descriptorSetLayout);
        VkPipelineLayoutCreateInfo pipelineLayout = VkPipelineLayoutCreateInfo.calloc()
                                                                              .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                                                                              .pSetLayouts(setLayout);
        
        LongBuffer handleHolder = memAllocLong(1);
        EngineUtils.checkError(vkCreatePipelineLayout(device, pipelineLayout, null, handleHolder));
        localPipeline.setLayout(handleHolder.get(0));
        
        
        VkGraphicsPipelineCreateInfo.Buffer pipelineCreateInfo = VkGraphicsPipelineCreateInfo.calloc(1)
                                                                                             .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                                                                                             .layout(localPipeline.getLayout())
                                                                                             .renderPass(renderpassHandle)
                                                                                             .pVertexInputState(vertexInputState)
                                                                                             .pInputAssemblyState(inputAssemblyState)
                                                                                             .pRasterizationState(rasterizationState)
                                                                                             .pColorBlendState(colorBlendState)
                                                                                             .pMultisampleState(multisampleState)
                                                                                             .pViewportState(viewportState)
                                                                                             .pDepthStencilState(depthStencilState)
                                                                                             .pStages(shaderStages)
                                                                                             .pDynamicState(dynamicState);
        
        EngineUtils.checkError(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineCreateInfo, null, handleHolder));
        localPipeline.setHandle(handleHolder.get(0));
        
        
        inputAssemblyState.free();
        rasterizationState.free();
        depthStencilState.free();
        colorBlendState.free();
        colorWriteMask.free();
        pipelineLayout.free();
        memFree(handleHolder);
        viewportState.free();
        shaderStages.free();
        dynamicState.free();
        memFree(dynStates);
        memFree(setLayout);
        
        return localPipeline;
    }
    
    private VkPipelineShaderStageCreateInfo loadShader(VkDevice device, String path, int stage)
    {
        ByteBuffer shaderCode = memAlloc(1024);
        shaderCode.put(EngineUtils.resourceToByteBuffer(path));
        shaderCode.flip();
        
        VkShaderModuleCreateInfo shaderModule = VkShaderModuleCreateInfo.calloc()
                                                                        .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                                                                        .pCode(shaderCode);
        
        LongBuffer handleHolder = memAllocLong(1);
        EngineUtils.checkError(vkCreateShaderModule(device, shaderModule, null, handleHolder));
        long shader = handleHolder.get(0);
        
        shaders.add(shader);
        
        VkPipelineShaderStageCreateInfo shaderStage = VkPipelineShaderStageCreateInfo.calloc()
                                                                                     .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                                                                                     .stage(stage)
                                                                                     .module(shader)
                                                                                     .pName(memUTF8("main"));
        
        memFree(handleHolder);
        memFree(shaderCode);
        
        return shaderStage;
    }
    
    private long createDescriptorSet(VkDevice device, long descriptorPool, long descriptorSetLayout, UniformBuffer ubo)
    {
        LongBuffer setLayout = memAllocLong(1).put(0, descriptorSetLayout);
        VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc()
                                                                              .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                                                                              .descriptorPool(descriptorPool)
                                                                              .pSetLayouts(setLayout);
        
        LongBuffer handleHolder = memAllocLong(1);
        EngineUtils.checkError(vkAllocateDescriptorSets(device, allocateInfo, handleHolder));
        long setHandle = handleHolder.get(0);
        
        memFree(setLayout);
        allocateInfo.free();
        memFree(handleHolder);
        
        VkDescriptorBufferInfo.Buffer descriptor = VkDescriptorBufferInfo.calloc(1)
                                                                         .buffer(ubo.getBuffer())
                                                                         .range(ubo.getRange())
                                                                         .offset(ubo.getOffset());
        
        VkWriteDescriptorSet.Buffer writeDescriptor = VkWriteDescriptorSet.calloc(1)
                                                                          .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                                                                          .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                                                                          .pBufferInfo(descriptor)
                                                                          .dstSet(setHandle)
                                                                          .dstBinding(0);
        
        vkUpdateDescriptorSets(device, writeDescriptor, null);
        
        writeDescriptor.free();
        descriptor.free();
        
        return setHandle;
    }
    
    private UniformBuffer createUniformBuffer(DeviceFamily deviceFamily)
    {
        UniformBuffer ubo = new UniformBuffer();
        ubo.setRange(UniformSpec.getSizeInBytes());
        ubo.setOffset(0);
        
        LongBuffer handleHolder = memAllocLong(1);
        VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc()
                                                                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                                                                .size(UniformSpec.getSizeInBytes())
                                                                .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
        
        EngineUtils.checkError(vkCreateBuffer(deviceFamily.getDevice(), bufferCreateInfo, null, handleHolder));
        ubo.setBuffer(handleHolder.get(0));
        bufferCreateInfo.free();
        
        
        VkMemoryRequirements requirements = VkMemoryRequirements.calloc();
        vkGetBufferMemoryRequirements(deviceFamily.getDevice(), ubo.getBuffer(), requirements);
        long size  = requirements.size();
        int  index = EngineUtils.findMemoryTypeIndex(deviceFamily.getMemoryProperties(), requirements, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        requirements.free();
        
        VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc()
                                                                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                                                                .allocationSize(size)
                                                                .memoryTypeIndex(index);
        
        EngineUtils.checkError(vkAllocateMemory(deviceFamily.getDevice(), allocateInfo, null, handleHolder));
        ubo.setMemory(handleHolder.get(0));
        allocateInfo.free();
        
        EngineUtils.checkError(vkBindBufferMemory(deviceFamily.getDevice(), ubo.getBuffer(), ubo.getMemory(), 0));
        memFree(handleHolder);
        
        return ubo;
    }
    
    
    private long createDescriptorSetLayout(VkDevice device)
    {
        VkDescriptorSetLayoutBinding.Buffer layoutBinding = VkDescriptorSetLayoutBinding.calloc(1)
                                                                                        .binding(0)
                                                                                        .descriptorCount(1)
                                                                                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                                                                                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                                                                                        .pImmutableSamplers(null);
        
        VkDescriptorSetLayoutCreateInfo createInfo = VkDescriptorSetLayoutCreateInfo.calloc()
                                                                                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                                                                                    .pBindings(layoutBinding);
        
        LongBuffer handleHolder = memAllocLong(1);
        EngineUtils.checkError(vkCreateDescriptorSetLayout(device, createInfo, null, handleHolder));
        long setLayout = handleHolder.get(0);
        
        memFree(handleHolder);
        layoutBinding.free();
        createInfo.free();
        
        return setLayout;
    }
    
    private long createDescriptorPool(VkDevice device)
    {
        VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1)
                                                                   .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                                                                   .descriptorCount(1);
        
        VkDescriptorPoolCreateInfo createInfo = VkDescriptorPoolCreateInfo.calloc()
                                                                          .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                                                                          .pPoolSizes(poolSize)
                                                                          .maxSets(1);
        
        LongBuffer handleHolder = memAllocLong(1);
        EngineUtils.checkError(vkCreateDescriptorPool(device, createInfo, null, handleHolder));
        long descriptorPool = handleHolder.get(0);
        
        memFree(handleHolder);
        createInfo.free();
        poolSize.free();
        
        return descriptorPool;
    }
    
    private long createRenderpass(VkDevice device, ColorAndDepthFormat colorAndDepthFormat)
    {
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2);
        attachments.get(0)
                   .format(colorAndDepthFormat.getColorFormat())
                   .samples(VK_SAMPLE_COUNT_1_BIT)
                   .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                   .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                   .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                   .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                   .initialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                   .finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        
        attachments.get(1)
                   .format(colorAndDepthFormat.getDepthFormat())
                   .samples(VK_SAMPLE_COUNT_1_BIT)
                   .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                   .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                   .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                   .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                   .initialLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                   .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        
        VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1)
                                                                           .attachment(0)
                                                                           .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        
        VkAttachmentReference depthReference = VkAttachmentReference.calloc()
                                                                    .attachment(1)
                                                                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        
        VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1)
                                                                  .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                                                                  .pInputAttachments(null)
                                                                  .pResolveAttachments(null)
                                                                  .pPreserveAttachments(null)
                                                                  .colorAttachmentCount(colorReference.remaining())
                                                                  .pColorAttachments(colorReference)
                                                                  .pDepthStencilAttachment(depthReference);
        
        
        VkRenderPassCreateInfo createInfo = VkRenderPassCreateInfo.calloc()
                                                                  .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                                                                  .pAttachments(attachments)
                                                                  .pSubpasses(subpass)
                                                                  .pDependencies(null);
        
        LongBuffer handleHolder = memAllocLong(1);
        EngineUtils.checkError(vkCreateRenderPass(device, createInfo, null, handleHolder));
        long renderpass = handleHolder.get(0);
        
        memFree(handleHolder);
        depthReference.free();
        colorReference.free();
        attachments.free();
        createInfo.free();
        subpass.free();
        
        return renderpass;
        
    }
    
    private VkQueue createDeviceQueue(DeviceFamily deviceFamily)
    {
        PointerBuffer handleHolder = memAllocPointer(1);
        vkGetDeviceQueue(deviceFamily.getDevice(), deviceFamily.getQueueFamily(), 0, handleHolder);
        long queueHandle = handleHolder.get(0);
        memFree(handleHolder);
        
        return new VkQueue(queueHandle, deviceFamily.getDevice());
    }
    
    private VkCommandBuffer createCommandBuffer(VkDevice device, long commandPoolHandle)
    {
        VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc()
                                                                              .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                                                                              .commandPool(commandPoolHandle)
                                                                              .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                                                                              .commandBufferCount(1);
        
        PointerBuffer handleHolder = memAllocPointer(1);
        EngineUtils.checkError(vkAllocateCommandBuffers(device, allocateInfo, handleHolder));
        long commandBuffer = handleHolder.get(0);
        
        memFree(handleHolder);
        allocateInfo.free();
        
        return new VkCommandBuffer(commandBuffer, device);
    }
    
    private long createCommandPool(DeviceFamily deviceFamily)
    {
        VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.calloc()
                                                                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                                                                    .queueFamilyIndex(deviceFamily.getQueueFamily())
                                                                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
        
        LongBuffer handleHolder = memAllocLong(1);
        EngineUtils.checkError(vkCreateCommandPool(deviceFamily.getDevice(), createInfo, null, handleHolder));
        long handle = handleHolder.get(0);
        
        memFree(handleHolder);
        createInfo.free();
        
        return handle;
    }
    
    private void setQueuePresent(VkPhysicalDevice physicalDevice, long surface, DeviceFamily daq)
    {
        IntBuffer queueCountBuffer = memAllocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueCountBuffer, null);
        
        int                            queueCount      = queueCountBuffer.get(0);
        VkQueueFamilyProperties.Buffer queueProperties = VkQueueFamilyProperties.calloc(queueCount);
        
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueCountBuffer, queueProperties);
        memFree(queueCountBuffer);
        
        IntBuffer hasPresentBit = memAllocInt(queueCount);
        for (int i = 0; i < queueCount; i++)
        {
            hasPresentBit.position(i);
            EngineUtils.checkError(vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, hasPresentBit));
        }
        
        int graphicsQueue = -1;
        int presentQueue  = -1;
        
        for (int i = 0; i < queueCount; i++)
        {
            if (EngineUtils.hasFlag(queueProperties.get(i).queueFlags(), VK_QUEUE_GRAPHICS_BIT))
            {
                if (graphicsQueue == -1)
                {
                    graphicsQueue = i;
                }
                
                if (hasPresentBit.get(i) == VK_TRUE)
                {
                    graphicsQueue = i;
                    presentQueue = i;
                    break;
                }
            }
        }
        queueProperties.free();
        
        if (presentQueue == -1)
        {
            for (int i = 0; i < queueCount; i++)
            {
                if (hasPresentBit.get(i) == VK_TRUE)
                {
                    presentQueue = i;
                    break;
                }
            }
        }
        memFree(hasPresentBit);
        
        if ((graphicsQueue != presentQueue) || (presentQueue == -1))
        {
            throw new RuntimeException("Failed to find queues");
        }
        
        daq.setQueueFamily(graphicsQueue);
    }
    
    private ColorAndDepthFormat getColorFormat(VkPhysicalDevice physicalDevice, long surface)
    {
        IntBuffer countHolder = memAllocInt(1);
        EngineUtils.checkError(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, countHolder, null));
        int formatCount = countHolder.get(0);
        
        VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.calloc(formatCount);
        EngineUtils.checkError(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, countHolder, formats));
        memFree(countHolder);
        
        ColorAndDepthFormat cad = new ColorAndDepthFormat();
        cad.setColorSpace(formats.get(0).colorSpace());
        if (formatCount == 1 && formats.get(0).format() == VK_FORMAT_UNDEFINED)
        {
            cad.setColorFormat(VK_FORMAT_B8G8R8A8_UNORM);
        } else
        {
            cad.setColorFormat(formats.get(0).format());
        }
        cad.setDepthFormat(getBestDepthFormat(physicalDevice));
        
        formats.free();
        
        return cad;
    }
    
    private int getBestDepthFormat(VkPhysicalDevice physicalDevice)
    {
        int[] depthFormats = {
                VK_FORMAT_D32_SFLOAT_S8_UINT,
                VK_FORMAT_D32_SFLOAT,
                VK_FORMAT_D24_UNORM_S8_UINT,
                VK_FORMAT_D16_UNORM_S8_UINT,
                VK_FORMAT_D16_UNORM
        };
        
        VkFormatProperties props = VkFormatProperties.calloc();
        for (int format : depthFormats)
        {
            vkGetPhysicalDeviceFormatProperties(physicalDevice, format, props);
            
            if (EngineUtils.hasFlag(props.optimalTilingFeatures(), VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT))
            {
                props.free();
                return format;
            }
        }
        throw new RuntimeException("No depth format found!");
    }
    
    private long createSurface(VkInstance instance, long window)
    {
        LongBuffer surfaceHolder = memAllocLong(1);
        glfwCreateWindowSurface(instance, window, null, surfaceHolder);
        
        long surface = surfaceHolder.get(0);
        
        memFree(surfaceHolder);
        
        return surface;
    }
    
    private long createWindow(int width, int height, String title)
    {
        
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        
        long handle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        
        glfwSetKeyCallback(handle, keyCallback);
        glfwSetFramebufferSizeCallback(handle, framebufferCallback);
        
        return handle;
    }
    
    private DeviceFamily createDeviceAndQueue(VkPhysicalDevice physicalDevice)
    {
        DeviceFamily daq = new DeviceFamily();
        
        IntBuffer queueCountBuffer = memAllocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueCountBuffer, null);
        
        int                            queueCount      = queueCountBuffer.get(0);
        VkQueueFamilyProperties.Buffer queueProperties = VkQueueFamilyProperties.calloc(queueCount);
        
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueCountBuffer, queueProperties);
        memFree(queueCountBuffer);
        
        int queueIndex;
        for (queueIndex = 0; queueIndex < queueCount; queueIndex++)
        {
            if (EngineUtils.hasFlag(queueProperties.get(queueIndex).queueFlags(), VK_QUEUE_GRAPHICS_BIT))
            {
                break;
            }
        }
        daq.setQueueFamily(queueIndex);
        queueProperties.free();
        
        FloatBuffer queuePrio = memCallocFloat(1);
        VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1)
                                                                                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                                                                                .queueFamilyIndex(queueIndex)
                                                                                .pQueuePriorities(queuePrio);
        
        PointerBuffer deviceExt = memAllocPointer(1);
        deviceExt.put(memUTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME)).flip();
        
        PointerBuffer validation = memAllocPointer(validationLayers.length);
        for (ByteBuffer layer : validationLayers)
        {
            validation.put(layer);
        }
        validation.flip();
        
        VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc()
                                                                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                                                                .pQueueCreateInfos(queueCreateInfo)
                                                                .ppEnabledLayerNames(validation)
                                                                .ppEnabledExtensionNames(deviceExt);
        
        PointerBuffer handleHolder = memAllocPointer(1);
        EngineUtils.checkError(vkCreateDevice(physicalDevice, deviceCreateInfo, null, handleHolder));
        long deviceHandle = handleHolder.get(0);
        daq.setDevice(new VkDevice(deviceHandle, physicalDevice, deviceCreateInfo));
        
        VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
        daq.setMemoryProperties(memoryProperties);
        
        
        deviceCreateInfo.free();
        queueCreateInfo.free();
        memFree(handleHolder);
        memFree(validation);
        memFree(deviceExt);
        memFree(queuePrio);
        
        return daq;
    }
    
    private VkPhysicalDevice getFirstPhysicalDevice(VkInstance instance)
    {
        IntBuffer deviceCount = memAllocInt(1);
        EngineUtils.checkError(vkEnumeratePhysicalDevices(instance, deviceCount, null));
        
        PointerBuffer devices = memAllocPointer(deviceCount.get(0));
        EngineUtils.checkError(vkEnumeratePhysicalDevices(instance, deviceCount, devices));
        
        long firstDevice = devices.get(0);
        
        memFree(deviceCount);
        memFree(devices);
        
        return new VkPhysicalDevice(firstDevice, instance);
    }
    
    private long createDebug(VkInstance instance, int debugFlags)
    {
        VkDebugReportCallbackCreateInfoEXT debugCreateInfo = VkDebugReportCallbackCreateInfoEXT.calloc()
                                                                                               .sType(VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT)
                                                                                               .flags(debugFlags)
                                                                                               .pfnCallback(debugCallbackFunc);
        
        LongBuffer handleContainer = memAllocLong(1);
        EngineUtils.checkError(vkCreateDebugReportCallbackEXT(instance, debugCreateInfo, null, handleContainer));
        long handle = handleContainer.get(0);
        
        memFree(handleContainer);
        debugCreateInfo.free();
        
        return handle;
    }
    
    private VkInstance createInstance(String title, PointerBuffer extensions)
    {
        
        VkApplicationInfo appInfo = VkApplicationInfo.calloc()
                                                     .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                                                     .pApplicationName(memUTF8(title))
                                                     .applicationVersion(VK_APPLICATION_VERSION)
                                                     .pEngineName(memUTF8("No Engine"))
                                                     .engineVersion(VK_APPLICATION_VERSION)
                                                     .apiVersion(VK_API_VERSION);
        
        PointerBuffer exts = memAllocPointer(extensions.remaining() + 1);
        exts.put(extensions);
        exts.put(memUTF8(VK_EXT_DEBUG_REPORT_EXTENSION_NAME));
        exts.flip();
        
        PointerBuffer validation = memAllocPointer(validationLayers.length);
        for (ByteBuffer layer : validationLayers)
        {
            validation.put(layer);
        }
        validation.flip();
        
        VkDebugReportCallbackCreateInfoEXT instanceDebug = VkDebugReportCallbackCreateInfoEXT.calloc()
                                                                                             .sType(VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT)
                                                                                             .flags(0xe)
                                                                                             .pfnCallback(debugCallbackFunc);
        
        
        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc()
                                                              .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                                                              .pNext(instanceDebug.address())
                                                              .pApplicationInfo(appInfo)
                                                              .ppEnabledExtensionNames(exts)
                                                              .ppEnabledLayerNames(validation);
        
        PointerBuffer instanceBuffer = memAllocPointer(1);
        EngineUtils.checkError(vkCreateInstance(createInfo, null, instanceBuffer));
        VkInstance instanceHolder = new VkInstance(instanceBuffer.get(0), createInfo);
        
        memFree(exts);
        appInfo.free();
        createInfo.free();
        memFree(validation);
        instanceDebug.free();
        memFree(appInfo.pEngineName());
        memFree(appInfo.pApplicationName());
        
        return instanceHolder;
    }
    
    
    private void loop()
    {
        postInit();
        
        LongBuffer imageSemaphore  = memAllocLong(1).put(0, imageAcquredSemaphore);
        LongBuffer renderSemaphore = memAllocLong(1).put(0, renderCompleteSemaphore);
        LongBuffer swapchains      = memAllocLong(1);
        
        PointerBuffer commandBuffers = memAllocPointer(1);
        
        IntBuffer imageIndex = memAllocInt(1);
        IntBuffer waitMask   = memAllocInt(1).put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
        
        
        VkSubmitInfo submitInfo = VkSubmitInfo.calloc()
                                              .waitSemaphoreCount(imageSemaphore.remaining())
                                              .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                                              .pSignalSemaphores(renderSemaphore)
                                              .pWaitSemaphores(imageSemaphore)
                                              .pCommandBuffers(commandBuffers)
                                              .pWaitDstStageMask(waitMask);
        
        VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc()
                                                       .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                                                       .waitSemaphoreCount(renderSemaphore.remaining())
                                                       .pWaitSemaphores(renderSemaphore)
                                                       .swapchainCount(swapchains.remaining())
                                                       .pImageIndices(imageIndex)
                                                       .pSwapchains(swapchains);
        
        
        int   updatesPerSecond = 60;
        int   maxFramesSkipped = 20;
        float skipInterval     = 1000f / updatesPerSecond;
        
        int ups = 0;
        int fps = 0;
        
        int loops;
        
        double timer    = System.currentTimeMillis();
        long   fpstimer = System.currentTimeMillis();
        
        while (!shouldClose)
        {
            
            if (System.currentTimeMillis() > fpstimer + 1000)
            {
                System.out.format("fps: %d  ups: %d%n", fps, ups);
                fpstimer = System.currentTimeMillis();
                fps = ups = 0;
            }
            
            loops = 0;
            while (System.currentTimeMillis() > timer && loops < maxFramesSkipped)
            {
                update();
                loops++;
                ups++;
                timer += skipInterval;
            }
            
            if (shouldRecreate)
            {
                synchronized (lock)
                {
                    recreateSwapchain();
                }
            }
            
            EngineUtils.checkError(vkAcquireNextImageKHR(deviceFamily.getDevice(), swapchain.getHandle(), Long.MAX_VALUE, imageSemaphore.get(0), VK_NULL_HANDLE, imageIndex));
            int index = imageIndex.get(0);
            
            commandBuffers.put(0, renderCommandBuffers[index]);
            EngineUtils.checkError(vkQueueSubmit(deviceQueue, submitInfo, VK_NULL_HANDLE));
            
            swapchains.put(0, swapchain.getHandle());
            EngineUtils.checkError(vkQueuePresentKHR(deviceQueue, presentInfo));
            
            vkQueueWaitIdle(deviceQueue);
            
            postPresentBarrier(swapchain.getImage(index), postPresentCommandBuffer, deviceQueue);
            
            render();
            fps++;
            
            synchronized (lock)
            {
                shouldClose = glfwWindowShouldClose(windowHandle);
            }
        }
        
        
        memFree(renderSemaphore);
        memFree(imageSemaphore);
        commandBuffers.free();
        presentInfo.free();
        memFree(swapchains);
        memFree(imageIndex);
        memFree(waitMask);
        submitInfo.free();
        
        
    }
    
    private void postInit()
    {
        game.init();
    }
    
    private void update()
    {
        game.update();
    }
    
    private void render()
    {
        game.render();
    }
    
    public void useGame(Game game)
    {
        this.game = game;
    }
}
