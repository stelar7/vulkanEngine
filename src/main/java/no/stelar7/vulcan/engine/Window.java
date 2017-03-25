package no.stelar7.vulcan.engine;

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
    private VulkanRenderer renderer;
    private String         windowName;
    
    private VkExtent2D surfaceSize = VkExtent2D.calloc();
    
    private long windowHandle     = VK_NULL_HANDLE;
    private long surfaceHandle    = VK_NULL_HANDLE;
    private long swapchainHandle  = VK_NULL_HANDLE;
    private long renderPassHandle = VK_NULL_HANDLE;
    
    private VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.calloc();
    
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
    }
    
    public void init()
    {
        createSwapchain();
        createSwapchainImages();
        createDepthStencilImage();
        createRenderPass();
        createFrameBuffers();
        createSync();
    }
    
    public void beginRender()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer imageBuffer = stack.callocInt(1);
            EngineUtils.checkError(vkAcquireNextImageKHR(renderer.getDevice(), swapchainHandle, Long.MAX_VALUE, VK_NULL_HANDLE, swapchainAvaliable, imageBuffer));
            activeSwapchainImageId = imageBuffer.get(0);
            
            EngineUtils.checkError(vkWaitForFences(renderer.getDevice(), swapchainAvaliable, true, Long.MAX_VALUE));
            EngineUtils.checkError(vkResetFences(renderer.getDevice(), swapchainAvaliable));
            EngineUtils.checkError(vkQueueWaitIdle(renderer.getRenderQueue()));
        }
    }
    
    public void endRender(LongBuffer semaphores)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer resultHolder = stack.callocInt(1);
            
            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.callocStack(stack)
                                                           .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                                                           .pWaitSemaphores(semaphores)
                                                           .waitSemaphoreCount(semaphores.remaining())
                                                           .swapchainCount(1)
                                                           .pSwapchains(stack.longs(swapchainHandle))
                                                           .pImageIndices(stack.ints(activeSwapchainImageId))
                                                           .pResults(resultHolder);
            
            
            EngineUtils.checkError(vkQueuePresentKHR(renderer.getRenderQueue(), presentInfo));
            EngineUtils.checkError(resultHolder.get(0));
            EngineUtils.checkError(vkDeviceWaitIdle(renderer.getDevice()));
        }
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
        
        surfaceSize.free();
        surfaceCapabilities.free();
        
        MemoryUtil.memFree(swapchainViewBuffer);
        MemoryUtil.memFree(frameBuffer);
        
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
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
            
            IntBuffer widthBuffer  = stack.callocInt(1);
            IntBuffer heightBuffer = stack.callocInt(1);
            windowHandle = glfwCreateWindow(surfaceSize.width(), surfaceSize.height(), windowName, MemoryUtil.NULL, MemoryUtil.NULL);
            glfwGetFramebufferSize(windowHandle, widthBuffer, heightBuffer);
            
            surfaceSize.width(widthBuffer.get(0));
            surfaceSize.height(heightBuffer.get(0));
            
            glfwShowWindow(windowHandle);
        }
    }
    
    private void createSync()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack)
                                                           .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            
            LongBuffer fenceBuffer = stack.callocLong(1);
            EngineUtils.checkError(vkCreateFence(renderer.getDevice(), fenceInfo, null, fenceBuffer));
            swapchainAvaliable = fenceBuffer.get(0);
        }
        
    }
    
    private void createFrameBuffers()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            frameBuffer = MemoryUtil.memAllocLong(swapchainImageCount);
            LongBuffer framebufferBuffer = stack.callocLong(1);
            
            for (int i = 0; i < swapchainImageCount; i++)
            {
                LongBuffer attachments = stack.longs(depthStencilImageView, swapchainViewBuffer.get(i));
                
                VkFramebufferCreateInfo createInfo = VkFramebufferCreateInfo.callocStack(stack)
                                                                            .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                                                                            .renderPass(renderPassHandle)
                                                                            .pAttachments(attachments)
                                                                            .width(surfaceSize.width())
                                                                            .height(surfaceSize.height())
                                                                            .layers(1);
                
                EngineUtils.checkError(vkCreateFramebuffer(renderer.getDevice(), createInfo, null, framebufferBuffer));
                frameBuffer.put(i, framebufferBuffer.get(0));
            }
        }
    }
    
    private void createRenderPass()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.callocStack(2, stack);
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
            VkAttachmentReference.Buffer subpassFirstColorReference = VkAttachmentReference.callocStack(1, stack)
                                                                                           .attachment(1)
                                                                                           .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            
            VkAttachmentReference subpassFirstDepthReference = VkAttachmentReference.callocStack(stack)
                                                                                    .attachment(0)
                                                                                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            
            VkSubpassDescription.Buffer subpasses = VkSubpassDescription.callocStack(1, stack);
            subpasses.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                     .pColorAttachments(subpassFirstColorReference)
                     .pDepthStencilAttachment(subpassFirstDepthReference);
            
            VkRenderPassCreateInfo createInfo = VkRenderPassCreateInfo.callocStack(stack)
                                                                      .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                                                                      .pAttachments(attachments)
                                                                      .pSubpasses(subpasses);
            
            LongBuffer renderpassBuffer = stack.callocLong(1);
            EngineUtils.checkError(vkCreateRenderPass(renderer.getDevice(), createInfo, null, renderpassBuffer));
            renderPassHandle = renderpassBuffer.get(0);
        }
    }
    
    private void createDepthStencilImage()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            List<Integer> depthFormats = Arrays.asList(VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D16_UNORM_S8_UINT, VK_FORMAT_D32_SFLOAT, VK_FORMAT_D16_UNORM);
            for (Integer testFormat : depthFormats)
            {
                VkFormatProperties formatProperties = VkFormatProperties.callocStack(stack);
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
            
            
            VkExtent3D depthExtent = VkExtent3D.callocStack(stack)
                                               .width(surfaceSize.width())
                                               .height(surfaceSize.height())
                                               .depth(1);
            
            VkImageCreateInfo createInfo = VkImageCreateInfo.callocStack(stack)
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
            
            LongBuffer imageBuffer = stack.callocLong(1);
            EngineUtils.checkError(vkCreateImage(renderer.getDevice(), createInfo, null, imageBuffer));
            depthStencilImage = imageBuffer.get(0);
            
            
            VkMemoryRequirements memoryRequirements = VkMemoryRequirements.callocStack(stack);
            vkGetImageMemoryRequirements(renderer.getDevice(), depthStencilImage, memoryRequirements);
            
            int memoryIndex = EngineUtils.findMemoryTypeIndex(renderer.getGpuMemory(), memoryRequirements, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.callocStack(stack)
                                                                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                                                                    .allocationSize(memoryRequirements.size())
                                                                    .memoryTypeIndex(memoryIndex);
            
            EngineUtils.checkError(vkAllocateMemory(renderer.getDevice(), allocateInfo, null, imageBuffer));
            depthStencilImageMemory = imageBuffer.get(0);
            EngineUtils.checkError(vkBindImageMemory(renderer.getDevice(), depthStencilImage, depthStencilImageMemory, 0));
            
            VkComponentMapping mapping = VkComponentMapping.callocStack(stack)
                                                           .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                           .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                           .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                           .a(VK_COMPONENT_SWIZZLE_IDENTITY);
            
            VkImageSubresourceRange range = VkImageSubresourceRange.callocStack(stack)
                                                                   .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT | (hasStencil ? VK_IMAGE_ASPECT_STENCIL_BIT : 0))
                                                                   .baseMipLevel(0)
                                                                   .levelCount(1)
                                                                   .baseArrayLayer(0)
                                                                   .layerCount(1);
            
            
            VkImageViewCreateInfo viewCreateInfo = VkImageViewCreateInfo.callocStack(stack)
                                                                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                                                                        .image(depthStencilImage)
                                                                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                                                                        .format(depthStencilFormat)
                                                                        .components(mapping)
                                                                        .subresourceRange(range);
            
            EngineUtils.checkError(vkCreateImageView(renderer.getDevice(), viewCreateInfo, null, imageBuffer));
            depthStencilImageView = imageBuffer.get(0);
        }
    }
    
    private void createSwapchainImages()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            
            LongBuffer imageBuffer = stack.callocLong(swapchainImageCount);
            swapchainViewBuffer = MemoryUtil.memAllocLong(swapchainImageCount);
            
            EngineUtils.checkError(vkGetSwapchainImagesKHR(renderer.getDevice(), swapchainHandle, stack.ints(swapchainImageCount), imageBuffer));
            
            VkComponentMapping mapping = VkComponentMapping.callocStack(stack)
                                                           .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                           .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                           .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                           .a(VK_COMPONENT_SWIZZLE_IDENTITY);
            
            VkImageSubresourceRange range = VkImageSubresourceRange.callocStack(stack)
                                                                   .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                                   .baseMipLevel(0)
                                                                   .levelCount(1)
                                                                   .baseArrayLayer(0)
                                                                   .layerCount(1);
            
            for (int i = 0; i < swapchainImageCount; i++)
            {
                VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.callocStack(stack)
                                                                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                                                                        .image(imageBuffer.get(i))
                                                                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                                                                        .format(surfaceFormat.getColorFormat())
                                                                        .components(mapping)
                                                                        .subresourceRange(range);
                
                
                LongBuffer imageViewBuffer = stack.callocLong(1);
                EngineUtils.checkError(vkCreateImageView(renderer.getDevice(), createInfo, null, imageViewBuffer));
                swapchainViewBuffer.put(i, imageViewBuffer.get(0));
            }
        }
    }
    
    
    private void createSwapchain()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer countBuffer = stack.callocInt(1);
            EngineUtils.checkError(vkGetPhysicalDeviceSurfacePresentModesKHR(renderer.getPhysicalDevice(), surfaceHandle, countBuffer, null));
            
            IntBuffer avaliablePresentModes = stack.callocInt(countBuffer.get(0));
            EngineUtils.checkError(vkGetPhysicalDeviceSurfacePresentModesKHR(renderer.getPhysicalDevice(), surfaceHandle, countBuffer, avaliablePresentModes));
            
            // FIFO = v-sync
            int presentMode = VK_PRESENT_MODE_FIFO_KHR;
            for (int i = 0; i < avaliablePresentModes.remaining(); i++)
            {
                // mailbox = not v-sync
                if (avaliablePresentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR)
                {
                    presentMode = avaliablePresentModes.get(i);
                }
            }
            
            
            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.callocStack(stack)
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
            
            LongBuffer responseBuffer = stack.callocLong(1);
            EngineUtils.checkError(vkCreateSwapchainKHR(renderer.getDevice(), createInfo, null, responseBuffer));
            swapchainHandle = responseBuffer.get(0);
            
            EngineUtils.checkError(vkGetSwapchainImagesKHR(renderer.getDevice(), swapchainHandle, countBuffer, null));
            System.out.println("Adjusted request swapchain count: " + swapchainImageCount);
            swapchainImageCount = countBuffer.get(0);
            System.out.println("Recived swapchain count: " + swapchainImageCount);
            System.out.println();
        }
    }
    
    private void createSurface()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            LongBuffer handleBuffer = stack.callocLong(1);
            EngineUtils.checkError(glfwCreateWindowSurface(renderer.getInstance(), windowHandle, null, handleBuffer));
            surfaceHandle = handleBuffer.get(0);
            
            EngineUtils.checkError(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(renderer.getPhysicalDevice(), surfaceHandle, surfaceCapabilities));
            surfaceSize = surfaceCapabilities.currentExtent();
            
            // The surface may support unlimited swapchain images, denoted by the max being 0. If so, we do not need to make sure its in bounds
            System.out.println("Requested swapchain count: " + swapchainImageCount);
            if (surfaceCapabilities.maxImageCount() > 0)
            {
                swapchainImageCount = Math.max(Math.min(swapchainImageCount, surfaceCapabilities.maxImageCount()), surfaceCapabilities.minImageCount());
            }
            
            System.out.println("Max swapchain supported: " + surfaceCapabilities.maxImageCount());
            System.out.println();
            System.out.println("Current Window Size: " + surfaceCapabilities.currentExtent().width() + "x" + surfaceCapabilities.currentExtent().height());
            System.out.println("Max Window Size: " + surfaceCapabilities.maxImageExtent().width() + "x" + surfaceCapabilities.maxImageExtent().height());
            System.out.println();
            
            
            if (!EngineUtils.hasFlag(surfaceCapabilities.supportedUsageFlags(), VK_IMAGE_USAGE_TRANSFER_DST_BIT))
            {
                throw new RuntimeException("VK_IMAGE_USAGE_TRANSFER_DST_BIT not supported by swapchain");
            }
            
            System.out.println("Supported swapchain image uses:");
            EngineUtils.hasFlag(surfaceCapabilities.supportedUsageFlags(), VK_IMAGE_USAGE_TRANSFER_SRC_BIT, "VK_IMAGE_USAGE_TRANSFER_SRC");
            EngineUtils.hasFlag(surfaceCapabilities.supportedUsageFlags(), VK_IMAGE_USAGE_TRANSFER_DST_BIT, "VK_IMAGE_USAGE_TRANSFER_DST");
            EngineUtils.hasFlag(surfaceCapabilities.supportedUsageFlags(), VK_IMAGE_USAGE_SAMPLED_BIT, "VK_IMAGE_USAGE_SAMPLED");
            EngineUtils.hasFlag(surfaceCapabilities.supportedUsageFlags(), VK_IMAGE_USAGE_STORAGE_BIT, "VK_IMAGE_USAGE_STORAGE");
            EngineUtils.hasFlag(surfaceCapabilities.supportedUsageFlags(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT, "VK_IMAGE_USAGE_COLOR_ATTACHMENT");
            EngineUtils.hasFlag(surfaceCapabilities.supportedUsageFlags(), VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, "VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT");
            EngineUtils.hasFlag(surfaceCapabilities.supportedUsageFlags(), VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT, "VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT");
            EngineUtils.hasFlag(surfaceCapabilities.supportedUsageFlags(), VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT, "VK_IMAGE_USAGE_INPUT_ATTACHMENT");
            System.out.println();
            
            IntBuffer formatCount = stack.callocInt(1);
            EngineUtils.checkError(vkGetPhysicalDeviceSurfaceFormatsKHR(renderer.getPhysicalDevice(), surfaceHandle, formatCount, null));
            
            if (formatCount.get(0) < 1)
            {
                throw new RuntimeException("No Surface Formats Found");
            }
            
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.callocStack(formatCount.get(0), stack);
            EngineUtils.checkError(vkGetPhysicalDeviceSurfaceFormatsKHR(renderer.getPhysicalDevice(), surfaceHandle, formatCount, formats));
            
            System.out.println("Supported surface color format count: " + formatCount.get(0));
            surfaceFormat = new ColorFormatSpace();
            if (formatCount.get(0) == 1 && formats.get(0).format() == VK_FORMAT_UNDEFINED)
            {
                surfaceFormat.set(VK_FORMAT_B8G8R8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR);
            } else
            {
                for (int i = 0; i < formats.remaining(); i++)
                {
                    VkSurfaceFormatKHR currentFormat = formats.get(i);
                    if (currentFormat.format() == VK_FORMAT_B8G8R8A8_UNORM)
                    {
                        surfaceFormat.set(currentFormat);
                        break;
                    }
                }
            }
            
            System.out.println("Selected surface color format: " + EngineUtils.vkFormatToString(VK_FORMAT_B8G8R8A8_UNORM));
            System.out.println("Selected surface color space: " + EngineUtils.vkColorSpaceToString(VK_COLOR_SPACE_SRGB_NONLINEAR_KHR));
            System.out.println();
        }
    }
    
    public VkExtent2D getSurfaceSize()
    {
        return surfaceSize;
    }
    
    public String getTitle()
    {
        return windowName;
    }
}
