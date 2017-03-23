package no.stelar7.vulcan.engine;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class Window
{
    
    
    private IntBuffer  iBuff = BufferUtils.createIntBuffer(1);
    private LongBuffer lBuff = BufferUtils.createLongBuffer(1);
    
    private VulkanRenderer renderer;
    private String         windowName;
    
    private VkExtent2D surfaceSize = VkExtent2D.create();
    
    private long windowHandle     = VK_NULL_HANDLE;
    private long surfaceHandle    = VK_NULL_HANDLE;
    private long swapchainHandle  = VK_NULL_HANDLE;
    private long renderPassHandle = VK_NULL_HANDLE;
    
    private VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.create();
    
    private ColorFormatSpace surfaceFormat;
    
    private int  swapchainImageCount    = 2;
    private int  activeSwapchainImageId = -1;
    private long swapchainAvaliable     = VK_NULL_HANDLE;
    
    private LongBuffer swapchainViewBuffer;
    private LongBuffer frameBuffer;
    
    private int depthStencilFormat = -1;
    private long depthStencilImage;
    private long depthStencilImageView;
    private long depthStencilImageMemory;
    
    public long getWindowHandle()
    {
        return windowHandle;
    }
    
    public long getSurfaceHandle()
    {
        return surfaceHandle;
    }
    
    public long getSwapchainHandle()
    {
        return swapchainHandle;
    }
    
    public long getRenderPassHandle()
    {
        return renderPassHandle;
    }
    
    public Window(VulkanRenderer renderer, int width, int height, String name)
    {
        this.renderer = renderer;
        this.surfaceSize.width(width).height(height);
        this.windowName = name;
        
        
        createWindow();
        createSurface();
        createSwapchain();
        createSwapchainImages();
        createDepthStencilImage();
        createRenderPass();
        createFrameBuffers();
        createSync();
    }
    
    public void beginRender()
    {
        EngineUtils.checkError(vkAcquireNextImageKHR(renderer.getDevice(), swapchainHandle, Long.MAX_VALUE, VK_NULL_HANDLE, swapchainAvaliable, iBuff));
        activeSwapchainImageId = iBuff.get(0);
        
        EngineUtils.checkError(vkWaitForFences(renderer.getDevice(), swapchainAvaliable, true, Long.MAX_VALUE));
        EngineUtils.checkError(vkResetFences(renderer.getDevice(), swapchainAvaliable));
        EngineUtils.checkError(vkQueueWaitIdle(renderer.getRenderQueue()));
    }
    
    public void endRender(LongBuffer semaphores)
    {
        LongBuffer swapchainHolder     = BufferUtils.createLongBuffer(1).put(0, swapchainHandle);
        IntBuffer  activeImageIdHolder = BufferUtils.createIntBuffer(1).put(0, activeSwapchainImageId);
        IntBuffer  resultHolder        = BufferUtils.createIntBuffer(1);
        
        VkPresentInfoKHR presentInfo = VkPresentInfoKHR.create()
                                                       .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                                                       .pWaitSemaphores(semaphores)
                                                       .waitSemaphoreCount(semaphores.remaining())
                                                       .swapchainCount(1)
                                                       .pSwapchains(swapchainHolder)
                                                       .pImageIndices(activeImageIdHolder)
                                                       .pResults(resultHolder);
        
        
        EngineUtils.checkError(vkQueuePresentKHR(renderer.getRenderQueue(), presentInfo));
        EngineUtils.checkError(resultHolder.get(0));
        EngineUtils.checkError(vkDeviceWaitIdle(renderer.getDevice()));
    }
    
    public long getActiveFramebuffer()
    {
        return frameBuffer.get(activeSwapchainImageId);
    }
    
    public boolean shouldClose()
    {
        return glfwWindowShouldClose(windowHandle);
    }
    
    public void destroy()
    {
        vkQueueWaitIdle(renderer.getRenderQueue());
        destroySync();
        destroyFramebuffers();
        destroyRenderPass();
        destroyDepthStencilImage();
        destroySwapchainImages();
        destroySwapchain();
        destroySurface();
        destroyWindow();
    }
    
    private void destroyWindow()
    {
        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
    }
    
    private void destroySurface()
    {
        vkDestroySurfaceKHR(renderer.getInstance(), surfaceHandle, null);
    }
    
    private void destroySwapchain()
    {
        vkDestroySwapchainKHR(renderer.getDevice(), swapchainHandle, null);
    }
    
    private void destroySwapchainImages()
    {
        for (int i = 0; i < swapchainImageCount; i++)
        {
            vkDestroyImageView(renderer.getDevice(), swapchainViewBuffer.get(i), null);
        }
    }
    
    private void destroyDepthStencilImage()
    {
        vkDestroyImageView(renderer.getDevice(), depthStencilImageView, null);
        vkFreeMemory(renderer.getDevice(), depthStencilImageMemory, null);
        vkDestroyImage(renderer.getDevice(), depthStencilImage, null);
        
    }
    
    private void destroyRenderPass()
    {
        vkDestroyRenderPass(renderer.getDevice(), renderPassHandle, null);
    }
    
    private void destroyFramebuffers()
    {
        for (int i = 0; i < swapchainImageCount; i++)
        {
            vkDestroyFramebuffer(renderer.getDevice(), frameBuffer.get(i), null);
        }
    }
    
    private void destroySync()
    {
        vkDestroyFence(renderer.getDevice(), swapchainAvaliable, null);
    }
    
    
    private void createWindow()
    {
        IntBuffer widthBuffer  = BufferUtils.createIntBuffer(1);
        IntBuffer heightBuffer = BufferUtils.createIntBuffer(1);
        
        GLFWErrorCallback.createPrint().set();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        
        windowHandle = glfwCreateWindow(surfaceSize.width(), surfaceSize.height(), windowName, MemoryUtil.NULL, MemoryUtil.NULL);
        glfwGetFramebufferSize(windowHandle, widthBuffer, heightBuffer);
        
        
        surfaceSize.width(widthBuffer.get(0));
        surfaceSize.height(heightBuffer.get(0));
        
        glfwShowWindow(windowHandle);
    }
    
    private void createSync()
    {
        VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.create()
                                                       .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
        
        EngineUtils.checkError(vkCreateFence(renderer.getDevice(), fenceInfo, null, lBuff));
        swapchainAvaliable = lBuff.get(0);
        
    }
    
    private void createFrameBuffers()
    {
        frameBuffer = BufferUtils.createLongBuffer(swapchainImageCount);
        
        for (int i = 0; i < swapchainImageCount; i++)
        {
            LongBuffer attachments = BufferUtils.createLongBuffer(2);
            attachments.put(0, depthStencilImageView);
            attachments.put(1, swapchainViewBuffer.get(i));
            
            
            VkFramebufferCreateInfo createInfo = VkFramebufferCreateInfo.create()
                                                                        .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                                                                        .renderPass(renderPassHandle)
                                                                        .pAttachments(attachments)
                                                                        .width(surfaceSize.width())
                                                                        .height(surfaceSize.height())
                                                                        .layers(1);
            
            EngineUtils.checkError(vkCreateFramebuffer(renderer.getDevice(), createInfo, null, lBuff));
            frameBuffer.put(i, lBuff.get(0));
        }
    }
    
    private void createRenderPass()
    {
        
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.create(2);
        attachments.get(0)
                   .flags(0)
                   .format(depthStencilFormat)
                   .samples(VK_SAMPLE_COUNT_1_BIT)
                   .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                   .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                   .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                   .stencilStoreOp(VK_ATTACHMENT_STORE_OP_STORE)
                   .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                   .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        
        
        attachments.get(1)
                   .flags(0)
                   .format(surfaceFormat.getColorFormat())
                   .samples(VK_SAMPLE_COUNT_1_BIT)
                   .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                   .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                   .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                   .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        
        
        // attachments.get(x) = .attachment(x)
        VkAttachmentReference.Buffer subpassFirstColorReference = VkAttachmentReference.create(1)
                                                                                       .attachment(1)
                                                                                       .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        
        VkAttachmentReference subpassFirstDepthReference = VkAttachmentReference.create()
                                                                                .attachment(0)
                                                                                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        
        VkSubpassDescription.Buffer subpasses = VkSubpassDescription.create(1);
        subpasses.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                 .pColorAttachments(subpassFirstColorReference)
                 .pDepthStencilAttachment(subpassFirstDepthReference);
        
        VkRenderPassCreateInfo createInfo = VkRenderPassCreateInfo.create()
                                                                  .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                                                                  .pAttachments(attachments)
                                                                  .pSubpasses(subpasses);
        
        EngineUtils.checkError(vkCreateRenderPass(renderer.getDevice(), createInfo, null, lBuff));
        renderPassHandle = lBuff.get(0);
        
    }
    
    private void createDepthStencilImage()
    {
        List<Integer> depthFormats = Arrays.asList(VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D16_UNORM_S8_UINT, VK_FORMAT_D32_SFLOAT, VK_FORMAT_D16_UNORM);
        for (Integer testFormat : depthFormats)
        {
            VkFormatProperties formatProperties = VkFormatProperties.create();
            vkGetPhysicalDeviceFormatProperties(renderer.getPhysicalDevice(), testFormat, formatProperties);
            
            if ((formatProperties.optimalTilingFeatures() & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) == VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT)
            {
                depthStencilFormat = testFormat;
                break;
            }
        }
        
        if (depthStencilFormat == -1)
        {
            throw new RuntimeException("No Depth Stencil format found");
        }
        
        boolean hasStencil = (depthStencilFormat == VK_FORMAT_D32_SFLOAT_S8_UINT) ||
                             (depthStencilFormat == VK_FORMAT_D24_UNORM_S8_UINT) ||
                             (depthStencilFormat == VK_FORMAT_D16_UNORM_S8_UINT);
        
        
        VkExtent3D depthExtent = VkExtent3D.create()
                                           .width(surfaceSize.width())
                                           .height(surfaceSize.height())
                                           .depth(1);
        
        VkImageCreateInfo createInfo = VkImageCreateInfo.create()
                                                        .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                                                        .flags(0)
                                                        .imageType(VK_IMAGE_TYPE_2D)
                                                        .format(depthStencilFormat)
                                                        .extent(depthExtent)
                                                        .mipLevels(1)
                                                        .arrayLayers(1)
                                                        .samples(VK_SAMPLE_COUNT_1_BIT)
                                                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                                                        .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                                                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                                                        .pQueueFamilyIndices(null)
                                                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
        
        EngineUtils.checkError(vkCreateImage(renderer.getDevice(), createInfo, null, lBuff));
        depthStencilImage = lBuff.get(0);
        
        
        VkMemoryRequirements memoryRequirements = VkMemoryRequirements.create();
        vkGetImageMemoryRequirements(renderer.getDevice(), depthStencilImage, memoryRequirements);
        
        int memoryIndex = EngineUtils.findMemoryTypeIndex(renderer.getGpuMemory(), memoryRequirements, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.create()
                                                                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                                                                .allocationSize(memoryRequirements.size())
                                                                .memoryTypeIndex(memoryIndex);
        
        EngineUtils.checkError(vkAllocateMemory(renderer.getDevice(), allocateInfo, null, lBuff));
        depthStencilImageMemory = lBuff.get(0);
        EngineUtils.checkError(vkBindImageMemory(renderer.getDevice(), depthStencilImage, depthStencilImageMemory, 0));
        
        VkComponentMapping mapping = VkComponentMapping.create()
                                                       .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                       .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                       .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                       .a(VK_COMPONENT_SWIZZLE_IDENTITY);
        
        VkImageSubresourceRange range = VkImageSubresourceRange.create()
                                                               .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT | (hasStencil ? VK_IMAGE_ASPECT_STENCIL_BIT : 0))
                                                               .baseMipLevel(0)
                                                               .levelCount(1)
                                                               .baseArrayLayer(0)
                                                               .layerCount(1);
        
        
        lBuff.put(0, depthStencilImage);
        VkImageViewCreateInfo viewCreateInfo = VkImageViewCreateInfo.create()
                                                                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                                                                    .image(lBuff.get(0))
                                                                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                                                                    .format(depthStencilFormat)
                                                                    .components(mapping)
                                                                    .subresourceRange(range);
        
        EngineUtils.checkError(vkCreateImageView(renderer.getDevice(), viewCreateInfo, null, lBuff));
        depthStencilImageView = lBuff.get(0);
    }
    
    
    private void createSwapchainImages()
    {
        LongBuffer imageBuffer = BufferUtils.createLongBuffer(swapchainImageCount);
        swapchainViewBuffer = BufferUtils.createLongBuffer(swapchainImageCount);
        
        EngineUtils.checkError(vkGetSwapchainImagesKHR(renderer.getDevice(), swapchainHandle, iBuff, imageBuffer));
        
        VkComponentMapping mapping = VkComponentMapping.create()
                                                       .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                       .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                       .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                       .a(VK_COMPONENT_SWIZZLE_IDENTITY);
        
        VkImageSubresourceRange range = VkImageSubresourceRange.create()
                                                               .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                               .baseMipLevel(0)
                                                               .levelCount(1)
                                                               .baseArrayLayer(0)
                                                               .layerCount(1);
        
        for (int i = 0; i < swapchainImageCount; i++)
        {
            VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.create()
                                                                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                                                                    .image(imageBuffer.get(i))
                                                                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                                                                    .format(surfaceFormat.getColorFormat())
                                                                    .components(mapping)
                                                                    .subresourceRange(range);
            
            
            EngineUtils.checkError(vkCreateImageView(renderer.getDevice(), createInfo, null, lBuff));
            swapchainViewBuffer.put(i, lBuff.get(0));
        }
    }
    
    
    private void createSwapchain()
    {
        EngineUtils.checkError(vkGetPhysicalDeviceSurfacePresentModesKHR(renderer.getPhysicalDevice(), surfaceHandle, iBuff, null));
        
        IntBuffer avaliablePresentModes = BufferUtils.createIntBuffer(iBuff.get(0));
        EngineUtils.checkError(vkGetPhysicalDeviceSurfacePresentModesKHR(renderer.getPhysicalDevice(), surfaceHandle, iBuff, avaliablePresentModes));
        
        int presentMode = VK_PRESENT_MODE_FIFO_KHR;
        for (int i = 0; i < avaliablePresentModes.remaining(); i++)
        {
            if (avaliablePresentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR)
            {
                presentMode = avaliablePresentModes.get(i);
            }
        }
        
        
        VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.create()
                                                                      .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                                                                      .surface(surfaceHandle)
                                                                      .minImageCount(swapchainImageCount)
                                                                      .imageFormat(surfaceFormat.getColorFormat())
                                                                      .imageColorSpace(surfaceFormat.getColorSpace())
                                                                      .imageExtent(surfaceSize)
                                                                      .imageArrayLayers(1)
                                                                      .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                                                                      .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                                                                      .preTransform(VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)
                                                                      .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                                                                      .presentMode(presentMode)
                                                                      .clipped(true)
                                                                      .oldSwapchain(VK_NULL_HANDLE);
        
        EngineUtils.checkError(vkCreateSwapchainKHR(renderer.getDevice(), createInfo, null, lBuff));
        swapchainHandle = lBuff.get(0);
        
        EngineUtils.checkError(vkGetSwapchainImagesKHR(renderer.getDevice(), swapchainHandle, iBuff, null));
        
        System.out.println("Requested swapchain count: " + swapchainImageCount);
        swapchainImageCount = iBuff.get(0);
        System.out.println("Actual swapchain count: " + swapchainImageCount);
        System.out.println();
        
    }
    
    private void createSurface()
    {
        
        EngineUtils.checkError(glfwCreateWindowSurface(renderer.getInstance(), windowHandle, null, lBuff));
        
        surfaceHandle = lBuff.get(0);
        
        IntBuffer wsiBuffer = BufferUtils.createIntBuffer(1).put(VK_TRUE);
        wsiBuffer.flip();
        EngineUtils.checkError(vkGetPhysicalDeviceSurfaceSupportKHR(renderer.getPhysicalDevice(), renderer.getDeviceQueueFamilyPropertyIndex(VK_QUEUE_GRAPHICS_BIT), surfaceHandle, wsiBuffer));
        
        
        EngineUtils.checkError(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(renderer.getPhysicalDevice(), surfaceHandle, surfaceCapabilities));
        surfaceSize = surfaceCapabilities.currentExtent();
        
        // The surface may support unlimited swapchain images, denoted by the max being 0. If so, we do not need to make sure its in bounds
        System.out.println("Expected swapchain count: " + swapchainImageCount);
        if (surfaceCapabilities.maxImageCount() > 0)
        {
            swapchainImageCount = Math.max(Math.min(swapchainImageCount, surfaceCapabilities.maxImageCount()), surfaceCapabilities.minImageCount());
        }
        
        System.out.println("Max swapchain supported: " + surfaceCapabilities.maxImageCount());
        System.out.println();
        System.out.println("Current Window Size: " + surfaceCapabilities.currentExtent().width() + "x" + surfaceCapabilities.currentExtent().height());
        System.out.println("Max Window Size: " + surfaceCapabilities.maxImageExtent().width() + "x" + surfaceCapabilities.maxImageExtent().height());
        System.out.println();
        
        IntBuffer formatCount = BufferUtils.createIntBuffer(1);
        EngineUtils.checkError(vkGetPhysicalDeviceSurfaceFormatsKHR(renderer.getPhysicalDevice(), surfaceHandle, formatCount, null));
        
        if (formatCount.get(0) < 1)
        {
            throw new RuntimeException("No Surface Formats Found");
        }
        
        VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.create(formatCount.get(0));
        EngineUtils.checkError(vkGetPhysicalDeviceSurfaceFormatsKHR(renderer.getPhysicalDevice(), surfaceHandle, formatCount, formats));
        
        
        surfaceFormat = new ColorFormatSpace();
        surfaceFormat.setColorSpace(formats.get(0).colorSpace());
        if (formats.get(0).format() == VK_FORMAT_UNDEFINED)
        {
            surfaceFormat.setColorFormat(VK_FORMAT_B8G8R8_UNORM);
        } else
        {
            surfaceFormat.setColorFormat(formats.get(0).format());
        }
    }
    
    public VkExtent2D getSurfaceSize()
    {
        return surfaceSize;
    }
}
