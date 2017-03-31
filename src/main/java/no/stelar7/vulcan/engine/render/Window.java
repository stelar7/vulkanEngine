package no.stelar7.vulcan.engine.render;

import no.stelar7.vulcan.engine.EngineUtils;
import org.lwjgl.*;
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
    private String               windowName;
    private ColorFormatContainer surfaceFormat;
    
    private long windowHandle           = VK_NULL_HANDLE;
    private long surfaceHandle          = VK_NULL_HANDLE;
    private long swapchainHandle        = VK_NULL_HANDLE;
    private long renderPassHandle       = VK_NULL_HANDLE;
    private long depthImageHandle       = VK_NULL_HANDLE;
    private long depthImageMemory       = VK_NULL_HANDLE;
    private long depthImageviewHandle   = VK_NULL_HANDLE;
    private long pipelineLayoutHandle   = VK_NULL_HANDLE;
    private long descriptorLayoutHandle = VK_NULL_HANDLE;
    private long descriptorPoolHandle   = VK_NULL_HANDLE;
    private long descriptorSetHandle    = VK_NULL_HANDLE;
    private long pipelineHandle         = VK_NULL_HANDLE;
    private long commandPoolHandle      = VK_NULL_HANDLE;
    private long imageReadySemaphore    = VK_NULL_HANDLE;
    private long renderDoneSemaphore    = VK_NULL_HANDLE;
    
    private static final int IDEAL_PRESENT_MODE = VK_PRESENT_MODE_MAILBOX_KHR;
    
    private VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.malloc();
    private VkExtent2D               windowSize          = VkExtent2D.malloc();
    
    
    private List<Long> swapchainImageViews = new ArrayList<>();
    private List<Long> framebuffers        = new ArrayList<>();
    
    private List<VkCommandBuffer> commandBuffers = new ArrayList<>();
    
    private long fragmentShader;
    private long vertexShader;
    
    
    public long getFrameBuffer(int index)
    {
        return framebuffers.get(index);
    }
    
    public VkCommandBuffer getCommandBuffer(int index)
    {
        return commandBuffers.get(index);
    }
    
    public List<Long> getSwapchainImageViews()
    {
        return Collections.unmodifiableList(swapchainImageViews);
    }
    
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
    
    public long getCommandPoolHandle()
    {
        return commandPoolHandle;
    }
    
    public long getPipelineLayoutHandle()
    {
        return pipelineLayoutHandle;
    }
    
    public long getDescriptorLayoutHandle()
    {
        return descriptorLayoutHandle;
    }
    
    public long getDescriptorPoolHandle()
    {
        return descriptorPoolHandle;
    }
    
    public long getDescriptorSetHandle()
    {
        return descriptorSetHandle;
    }
    
    public long getPipelineHandle()
    {
        return pipelineHandle;
    }
    
    public VkExtent2D getWindowSize()
    {
        return windowSize;
    }
    
    public Window(String name, int width, int height)
    {
        this.windowSize = windowSize.width(width).height(height);
        this.windowName = name;
        
        createWindow();
    }
    
    public boolean shouldClose()
    {
        return glfwWindowShouldClose(windowHandle);
    }
    
    public void destroy(VkInstance instance, VkDevice device)
    {
        vkDeviceWaitIdle(device);
        
        surfaceCapabilities.free();
        windowSize.free();
        
        ShaderSpec.destroy();
        
        
        destroyDepthImage(device);
        destroySemaphores(device);
        destroyCommandPool(device);
        destroyPipeline(device);
        destroyDescriptorPool(device);
        destroyDescriptorSetLayout(device);
        destroyPipelineLayout(device);
        destroyShaders(device);
        destroySwapchainImageViews(device);
        destroyFramebuffers(device);
        destroyRenderPass(device);
        destroySwapchain(device);
        destroySurface(instance);
        destroyWindow();
    }
    
    private void destroyDepthImage(VkDevice device)
    {
        vkFreeMemory(device, depthImageMemory, null);
        vkDestroyImageView(device, depthImageviewHandle, null);
        vkDestroyImage(device, depthImageHandle, null);
    }
    
    private void destroyDescriptorPool(VkDevice device)
    {
        vkDestroyDescriptorPool(device, descriptorPoolHandle, null);
    }
    
    private void destroyDescriptorSetLayout(VkDevice device)
    {
        vkDestroyDescriptorSetLayout(device, descriptorLayoutHandle, null);
    }
    
    private void destroySemaphores(VkDevice device)
    {
        vkDestroySemaphore(device, imageReadySemaphore, null);
        vkDestroySemaphore(device, renderDoneSemaphore, null);
    }
    
    private void destroyCommandPool(VkDevice device)
    {
        vkDestroyCommandPool(device, commandPoolHandle, null);
    }
    
    private void destroyPipeline(VkDevice device)
    {
        vkDestroyPipeline(device, pipelineHandle, null);
    }
    
    private void destroyPipelineLayout(VkDevice device)
    {
        vkDestroyPipelineLayout(device, pipelineLayoutHandle, null);
    }
    
    private void destroyShaders(VkDevice device)
    {
        vkDestroyShaderModule(device, fragmentShader, null);
        vkDestroyShaderModule(device, vertexShader, null);
    }
    
    private void destroySwapchainImageViews(VkDevice device)
    {
        for (Long view : swapchainImageViews)
        {
            vkDestroyImageView(device, view, null);
        }
    }
    
    private void destroyFramebuffers(VkDevice device)
    {
        for (Long framebuffer : framebuffers)
        {
            vkDestroyFramebuffer(device, framebuffer, null);
        }
    }
    
    private void destroyWindow()
    {
        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
    }
    
    private void destroySurface(VkInstance instance)
    {
        vkDestroySurfaceKHR(instance, surfaceHandle, null);
    }
    
    private void destroySwapchain(VkDevice device)
    {
        vkDestroySwapchainKHR(device, swapchainHandle, null);
    }
    
    
    private void destroyRenderPass(VkDevice device)
    {
        vkDestroyRenderPass(device, renderPassHandle, null);
    }
    
    
    private void createWindow()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
            
            IntBuffer widthBuffer  = stack.mallocInt(1);
            IntBuffer heightBuffer = stack.mallocInt(1);
            windowHandle = glfwCreateWindow(windowSize.width(), windowSize.height(), windowName, MemoryUtil.NULL, MemoryUtil.NULL);
            
            glfwGetFramebufferSize(windowHandle, widthBuffer, heightBuffer);
            windowSize.width(widthBuffer.get(0)).height(heightBuffer.get(0));
            
            glfwShowWindow(windowHandle);
        }
    }
    
    protected void createSwapchain(VkPhysicalDevice physicalDevice, VkDevice device)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkSurfaceCapabilitiesKHR capabilities = getSurfaceCapabilities(physicalDevice);
            
            if (!EngineUtils.hasFlag(capabilities.supportedUsageFlags(), VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
            {
                throw new RuntimeException("VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT is not supported");
            }
            
            if (!EngineUtils.hasFlag(capabilities.supportedUsageFlags(), VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR))
            {
                throw new RuntimeException("VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR is not supported");
            }
            
            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.mallocStack(stack)
                                                                          .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                                                                          .pNext(VK_NULL_HANDLE)
                                                                          .flags(0)
                                                                          .surface(surfaceHandle)
                                                                          .minImageCount(capabilities.minImageCount())
                                                                          .imageFormat(surfaceFormat.getFormat())
                                                                          .imageColorSpace(surfaceFormat.getColorSpace())
                                                                          .imageExtent(capabilities.currentExtent())
                                                                          .imageArrayLayers(1)
                                                                          .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                                                                          .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                                                                          .pQueueFamilyIndices(null)
                                                                          .preTransform(capabilities.currentTransform())
                                                                          .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                                                                          .presentMode(getSurfacePresentMode(physicalDevice))
                                                                          .clipped(true)
                                                                          .oldSwapchain(VK_NULL_HANDLE);
            
            LongBuffer responseBuffer = stack.mallocLong(1);
            EngineUtils.checkError(vkCreateSwapchainKHR(device, createInfo, null, responseBuffer));
            swapchainHandle = responseBuffer.get(0);
        }
    }
    
    
    private List<Integer> getSurfacePresentModes(VkPhysicalDevice physicalDevice)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer modeCount = stack.mallocInt(1);
            EngineUtils.checkError(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surfaceHandle, modeCount, null));
            
            IntBuffer presentModes = stack.mallocInt(modeCount.get(0));
            EngineUtils.checkError(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surfaceHandle, modeCount, presentModes));
            
            List<Integer> imagePointers = new ArrayList<>(modeCount.get(0));
            
            for (int i = 0; i < modeCount.get(0); i++)
            {
                imagePointers.add(presentModes.get(i));
            }
            
            return imagePointers;
        }
    }
    
    private int getSurfacePresentMode(VkPhysicalDevice physicalDevice)
    {
        List<Integer> modes = getSurfacePresentModes(physicalDevice);
        
        for (Integer mode : modes)
        {
            if (mode == IDEAL_PRESENT_MODE)
            {
                return mode;
            }
        }
        
        throw new RuntimeException("Ideal present mode is not avaliable, tell dev to try FIFO instead");
    }
    
    protected void createSurface(VkInstance instance)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            LongBuffer handleBuffer = stack.mallocLong(1);
            EngineUtils.checkError(glfwCreateWindowSurface(instance, windowHandle, null, handleBuffer));
            surfaceHandle = handleBuffer.get(0);
        }
    }
    
    public VkSurfaceCapabilitiesKHR getSurfaceCapabilities(VkPhysicalDevice physicalDevice)
    {
        System.out.println("Looking up surface capabilities");
        
        EngineUtils.checkError(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surfaceHandle, surfaceCapabilities));
        
        // The surface may support unlimited swapchain images, denoted by the max being 0. If so, we do not need to make sure its in bounds
        
        System.out.println("Min swapchain supported: " + surfaceCapabilities.minImageCount());
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
        
        if (surfaceCapabilities.currentExtent().width() != windowSize.width() || surfaceCapabilities.currentExtent().height() != windowSize.height())
        {
            System.out.format("Requested surface size: %sx%s%n", surfaceCapabilities.currentExtent().width(), surfaceCapabilities.currentExtent().height());
            System.out.format("Recieved surface size: %sx%s%n", windowSize.width(), windowSize.height());
        }
        
        return surfaceCapabilities;
    }
    
    public VkSurfaceFormatKHR.Buffer getSurfaceFormats(VkPhysicalDevice physicalDevice)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer formatCount = stack.mallocInt(1);
            EngineUtils.checkError(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surfaceHandle, formatCount, null));
            
            if (formatCount.get(0) < 1)
            {
                throw new RuntimeException("No Surface Formats Found");
            }
            
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.mallocStack(formatCount.get(0), stack);
            EngineUtils.checkError(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surfaceHandle, formatCount, formats));
            
            return formats;
        }
    }
    
    public void getSurfaceFormat(VkPhysicalDevice physicalDevice)
    {
        VkSurfaceFormatKHR.Buffer formats = getSurfaceFormats(physicalDevice);
        
        for (int i = 0; i < formats.remaining(); i++)
        {
            formats.position(i);
            
            if (formats.format() == VK_FORMAT_B8G8R8A8_UNORM)
            {
                if (formats.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                {
                    System.out.println("Selected surface color format: " + EngineUtils.vkFormatToString(VK_FORMAT_B8G8R8A8_UNORM));
                    System.out.println("Selected surface color space: " + EngineUtils.vkColorSpaceToString(VK_COLOR_SPACE_SRGB_NONLINEAR_KHR));
                    System.out.println();
                    surfaceFormat = new ColorFormatContainer(formats.get());
                    return;
                }
            }
        }
        
        throw new RuntimeException("No suitable color format found");
    }
    
    public String getTitle()
    {
        return windowName;
    }
    
    public VkExtent2D getSurfaceSize()
    {
        return windowSize;
    }
    
    public List<Long> createSwapchainImages(VkDevice device)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer imageCount = stack.mallocInt(1);
            EngineUtils.checkError(vkGetSwapchainImagesKHR(device, swapchainHandle, imageCount, null));
            
            if (imageCount.get(0) < 1)
            {
                throw new RuntimeException("No Swapchain Images Found");
            }
            
            LongBuffer imageHolder = stack.mallocLong(imageCount.get(0));
            EngineUtils.checkError(vkGetSwapchainImagesKHR(device, swapchainHandle, imageCount, imageHolder));
            
            List<Long> imagePointers = new ArrayList<>(imageCount.get(0));
            
            for (int i = 0; i < imageHolder.remaining(); i++)
            {
                imagePointers.add(imageHolder.get(i));
            }
            
            
            return imagePointers;
        }
    }
    
    public void createSwapchainImageViews(VkDevice device)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            List<Long> images = createSwapchainImages(device);
            List<Long> views  = new ArrayList<>(images.size());
            
            for (Long image : images)
            {
                views.add(createImageView(device, image));
            }
            
            swapchainImageViews = views;
        }
    }
    
    private long createImageView(VkDevice device, long imageHandle)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            
            VkComponentMapping componentMapping = VkComponentMapping.mallocStack(stack)
                                                                    .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                                    .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                                    .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                                    .a(VK_COMPONENT_SWIZZLE_IDENTITY);
            
            VkImageSubresourceRange imageSubresourceRange = VkImageSubresourceRange.mallocStack(stack)
                                                                                   .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                                                                   .baseMipLevel(0)
                                                                                   .levelCount(1)
                                                                                   .baseArrayLayer(0)
                                                                                   .layerCount(1);
            
            VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.mallocStack(stack)
                                                                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                                                                    .pNext(VK_NULL_HANDLE)
                                                                    .flags(0)
                                                                    .image(imageHandle)
                                                                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                                                                    .format(surfaceFormat.getFormat())
                                                                    .components(componentMapping)
                                                                    .subresourceRange(imageSubresourceRange);
            
            LongBuffer handleHolder = stack.mallocLong(1);
            EngineUtils.checkError(vkCreateImageView(device, createInfo, null, handleHolder));
            return handleHolder.get(0);
        }
    }
    
    public void createRenderPass(VkDevice device)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.mallocStack(2, stack);
            
            // Color
            attachments.get(0)
                       .flags(0)
                       .format(surfaceFormat.getFormat())
                       .samples(VK_SAMPLE_COUNT_1_BIT)
                       .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                       .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                       .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                       .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                       .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                       .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            
            // Depth
            attachments.get(1)
                       .flags(0)
                       .format(VK_FORMAT_D32_SFLOAT)
                       .samples(VK_SAMPLE_COUNT_1_BIT)
                       .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                       .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                       .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                       .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                       .initialLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                       .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            
            VkAttachmentReference.Buffer colorReference = VkAttachmentReference.mallocStack(1, stack)
                                                                               .attachment(0)
                                                                               .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            
            VkAttachmentReference depthReference = VkAttachmentReference.mallocStack(stack)
                                                                        .attachment(1)
                                                                        .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            
            
            VkSubpassDescription.Buffer subpass = VkSubpassDescription.mallocStack(1, stack)
                                                                      .flags(0)
                                                                      .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                                                                      .pInputAttachments(null)
                                                                      .colorAttachmentCount(1)
                                                                      .pColorAttachments(colorReference)
                                                                      .pResolveAttachments(null)
                                                                      .pDepthStencilAttachment(depthReference)
                                                                      .pPreserveAttachments(null);
            
            VkSubpassDependency srcDependency = VkSubpassDependency.mallocStack(stack)
                                                                   .srcSubpass(VK_SUBPASS_EXTERNAL)
                                                                   .dstSubpass(0)
                                                                   .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                                                                   .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                                                                   .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                                                                   .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                                                                   .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
            
            VkSubpassDependency dstDependency = VkSubpassDependency.mallocStack(stack)
                                                                   .srcSubpass(0)
                                                                   .dstSubpass(VK_SUBPASS_EXTERNAL)
                                                                   .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                                                                   .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                                                                   .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                                                                   .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                                                                   .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
            
            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.mallocStack(2, stack);
            dependencies.put(0, srcDependency);
            dependencies.put(1, dstDependency);
            
            
            VkRenderPassCreateInfo createInfo = VkRenderPassCreateInfo.mallocStack(stack)
                                                                      .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                                                                      .pNext(VK_NULL_HANDLE)
                                                                      .flags(0)
                                                                      .pAttachments(attachments)
                                                                      .pSubpasses(subpass)
                                                                      .pDependencies(dependencies);
            
            LongBuffer handleHolder = stack.mallocLong(1);
            EngineUtils.checkError(vkCreateRenderPass(device, createInfo, null, handleHolder));
            
            renderPassHandle = handleHolder.get(0);
        }
    }
    
    public void createFramebuffers(VkDevice device)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            List<Long> createdFramebuffers = new ArrayList<>(swapchainImageViews.size());
            LongBuffer handleHolder        = stack.mallocLong(1);
            
            for (Long imageView : swapchainImageViews)
            {
                VkFramebufferCreateInfo createInfo = VkFramebufferCreateInfo.mallocStack(stack)
                                                                            .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                                                                            .pNext(VK_NULL_HANDLE)
                                                                            .flags(0)
                                                                            .renderPass(renderPassHandle)
                                                                            .pAttachments(stack.longs(imageView, depthImageviewHandle))
                                                                            .width(windowSize.width())
                                                                            .height(windowSize.height())
                                                                            .layers(1);
                
                EngineUtils.checkError(vkCreateFramebuffer(device, createInfo, null, handleHolder));
                createdFramebuffers.add(handleHolder.get(0));
            }
            
            framebuffers = createdFramebuffers;
        }
    }
    
    public void createShaders(VkDevice device)
    {
        fragmentShader = createShader(device, "/shaders/compiled/basic.frag.spv");
        vertexShader = createShader(device, "/shaders/compiled/basic.vert.spv");
    }
    
    
    private long createShader(VkDevice device, String filename)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            byte[]     data = EngineUtils.readFileToArray(filename);
            ByteBuffer code = stack.malloc(data.length).put(data);
            code.flip();
            
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.mallocStack(stack)
                                                                          .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                                                                          .pNext(VK_NULL_HANDLE)
                                                                          .flags(0)
                                                                          .pCode(code);
            
            LongBuffer handleBuffer = stack.callocLong(1);
            EngineUtils.checkError(vkCreateShaderModule(device, createInfo, null, handleBuffer));
            return handleBuffer.get(0);
        }
    }
    
    public void createPipelineLayout(VkDevice device)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkDescriptorSetLayoutCreateInfo setLayoutCreateInfo = VkDescriptorSetLayoutCreateInfo.mallocStack(stack)
                                                                                                 .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                                                                                                 .pNext(VK_NULL_HANDLE)
                                                                                                 .flags(0)
                                                                                                 .pBindings(ShaderSpec.getUniformLayoutBinding());
            
            LongBuffer handleBuffer = stack.callocLong(1);
            EngineUtils.checkError(vkCreateDescriptorSetLayout(device, setLayoutCreateInfo, null, handleBuffer));
            descriptorLayoutHandle = handleBuffer.get(0);
            
            VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.mallocStack(stack)
                                                                              .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                                                                              .pNext(VK_NULL_HANDLE)
                                                                              .flags(0)
                                                                              .pSetLayouts(handleBuffer)
                                                                              .pPushConstantRanges(null);
            
            EngineUtils.checkError(vkCreatePipelineLayout(device, createInfo, null, handleBuffer));
            pipelineLayoutHandle = handleBuffer.get(0);
        }
    }
    
    public void createPipeline(VkDevice device, VkPhysicalDeviceLimits limits)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            
            VkPipelineShaderStageCreateInfo vertexCreateInfo = VkPipelineShaderStageCreateInfo.mallocStack(stack)
                                                                                              .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                                                                                              .pNext(VK_NULL_HANDLE)
                                                                                              .flags(0)
                                                                                              .stage(VK_SHADER_STAGE_VERTEX_BIT)
                                                                                              .module(vertexShader)
                                                                                              .pName(MemoryUtil.memASCII("main"))
                                                                                              .pSpecializationInfo(null);
            
            VkPipelineShaderStageCreateInfo fragmentCreateInfo = VkPipelineShaderStageCreateInfo.mallocStack(stack)
                                                                                                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                                                                                                .pNext(VK_NULL_HANDLE)
                                                                                                .flags(0)
                                                                                                .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                                                                                                .module(fragmentShader)
                                                                                                .pName(MemoryUtil.memASCII("main"))
                                                                                                .pSpecializationInfo(null);
            
            
            VkPipelineShaderStageCreateInfo.Buffer shaderStageCreateInfo = VkPipelineShaderStageCreateInfo.mallocStack(2, stack);
            shaderStageCreateInfo.put(0, vertexCreateInfo).put(1, fragmentCreateInfo);
            
            if (ShaderSpec.getVertexStride() >= limits.maxVertexInputBindingStride())
            {
                throw new RuntimeException("GPU does not support a stride of " + ShaderSpec.getVertexStride());
            }
            
            if (ShaderSpec.getVertexBindingIndex() > limits.maxVertexInputBindings())
            {
                throw new RuntimeException("GPU does not support binding " + ShaderSpec.getVertexBindingIndex() + " inputs");
            }
            
            if (ShaderSpec.getLastAttribIndex() >= limits.maxVertexInputAttributes())
            {
                throw new RuntimeException("GPU does not support " + (ShaderSpec.getLastAttribIndex() + 1) + " shader \"in\"s'");
            }
            
            if (ShaderSpec.getLastAttribOffset() >= limits.maxVertexInputAttributeOffset())
            {
                throw new RuntimeException("GPU does not support " + ShaderSpec.getLastAttribOffset() + " attribute offsets in shaders'");
            }
            
            VkPipelineInputAssemblyStateCreateInfo inputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.mallocStack(stack)
                                                                                                                        .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                                                                                                                        .pNext(VK_NULL_HANDLE)
                                                                                                                        .flags(0)
                                                                                                                        .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                                                                                                                        .primitiveRestartEnable(false);
            
            VkPipelineViewportStateCreateInfo viewportStateCreateInfo = VkPipelineViewportStateCreateInfo.mallocStack(stack)
                                                                                                         .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                                                                                                         .pNext(VK_NULL_HANDLE).flags(0)
                                                                                                         .viewportCount(1)
                                                                                                         .pViewports(null)
                                                                                                         .scissorCount(1)
                                                                                                         .pScissors(null);
            
            VkPipelineRasterizationStateCreateInfo rasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.mallocStack(stack)
                                                                                                                        .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                                                                                                                        .pNext(VK_NULL_HANDLE)
                                                                                                                        .flags(0)
                                                                                                                        .depthClampEnable(false)
                                                                                                                        .rasterizerDiscardEnable(false)
                                                                                                                        .polygonMode(VK_POLYGON_MODE_FILL)
                                                                                                                        .cullMode(VK_CULL_MODE_BACK_BIT)
                                                                                                                        .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                                                                                                                        .depthBiasEnable(false)
                                                                                                                        .depthBiasConstantFactor(0)
                                                                                                                        .depthBiasClamp(0)
                                                                                                                        .depthBiasSlopeFactor(0)
                                                                                                                        .lineWidth(1);
            
            VkPipelineMultisampleStateCreateInfo multisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.mallocStack(stack)
                                                                                                                  .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                                                                                                                  .pNext(VK_NULL_HANDLE)
                                                                                                                  .flags(0)
                                                                                                                  .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                                                                                                                  .sampleShadingEnable(false)
                                                                                                                  .minSampleShading(0)
                                                                                                                  .pSampleMask(null)
                                                                                                                  .alphaToCoverageEnable(false)
                                                                                                                  .alphaToOneEnable(false);
            
            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.mallocStack(1, stack)
                                                                                                                 .blendEnable(false)
                                                                                                                 .srcColorBlendFactor(VK_BLEND_FACTOR_ZERO)
                                                                                                                 .dstColorBlendFactor(VK_BLEND_FACTOR_ZERO)
                                                                                                                 .colorBlendOp(VK_BLEND_OP_ADD)
                                                                                                                 .srcAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                                                                                                                 .dstColorBlendFactor(VK_BLEND_FACTOR_ZERO)
                                                                                                                 .alphaBlendOp(VK_BLEND_OP_ADD)
                                                                                                                 .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
            
            VkPipelineColorBlendStateCreateInfo colorBlendStateCreateInfo = VkPipelineColorBlendStateCreateInfo.mallocStack(stack)
                                                                                                               .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                                                                                                               .pNext(VK_NULL_HANDLE)
                                                                                                               .flags(0)
                                                                                                               .logicOpEnable(false)
                                                                                                               .logicOp(VK_LOGIC_OP_COPY)
                                                                                                               .pAttachments(colorBlendAttachment)
                                                                                                               .blendConstants(0, 0)
                                                                                                               .blendConstants(1, 0)
                                                                                                               .blendConstants(2, 0)
                                                                                                               .blendConstants(3, 0);
            
            VkPipelineDynamicStateCreateInfo dynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.mallocStack(stack)
                                                                                                      .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                                                                                                      .pNext(VK_NULL_HANDLE)
                                                                                                      .flags(0)
                                                                                                      .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));
            
            VkStencilOpState noOpState = VkStencilOpState.mallocStack(stack)
                                                         .failOp(VK_STENCIL_OP_KEEP)
                                                         .passOp(VK_STENCIL_OP_KEEP)
                                                         .depthFailOp(VK_STENCIL_OP_KEEP)
                                                         .compareOp(VK_COMPARE_OP_ALWAYS)
                                                         .compareMask(0)
                                                         .writeMask(0)
                                                         .reference(0);
            
            VkPipelineDepthStencilStateCreateInfo stencilStateCreateInfo = VkPipelineDepthStencilStateCreateInfo.mallocStack(stack)
                                                                                                                .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                                                                                                                .pNext(VK_NULL_HANDLE)
                                                                                                                .flags(0)
                                                                                                                .depthTestEnable(true)
                                                                                                                .depthWriteEnable(true)
                                                                                                                .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                                                                                                                .depthBoundsTestEnable(false)
                                                                                                                .stencilTestEnable(false)
                                                                                                                .front(noOpState)
                                                                                                                .back(noOpState)
                                                                                                                .minDepthBounds(0)
                                                                                                                .maxDepthBounds(0);
            
            VkGraphicsPipelineCreateInfo.Buffer pipelineCreateInfo = VkGraphicsPipelineCreateInfo.mallocStack(1, stack)
                                                                                                 .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                                                                                                 .pNext(VK_NULL_HANDLE)
                                                                                                 .flags(0)
                                                                                                 .pStages(shaderStageCreateInfo)
                                                                                                 .pVertexInputState(ShaderSpec.getCreateInfo())
                                                                                                 .pInputAssemblyState(inputAssemblyStateCreateInfo)
                                                                                                 .pTessellationState(null)
                                                                                                 .pViewportState(viewportStateCreateInfo)
                                                                                                 .pRasterizationState(rasterizationStateCreateInfo)
                                                                                                 .pMultisampleState(multisampleStateCreateInfo)
                                                                                                 .pDepthStencilState(stencilStateCreateInfo)
                                                                                                 .pColorBlendState(colorBlendStateCreateInfo)
                                                                                                 .pDynamicState(dynamicStateCreateInfo)
                                                                                                 .layout(pipelineLayoutHandle)
                                                                                                 .renderPass(renderPassHandle)
                                                                                                 .subpass(0)
                                                                                                 .basePipelineHandle(VK_NULL_HANDLE)
                                                                                                 .basePipelineIndex(-1);
            
            LongBuffer handleHolder = stack.mallocLong(1);
            EngineUtils.checkError(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineCreateInfo, null, handleHolder));
            
            pipelineHandle = handleHolder.get(0);
        }
    }
    
    public void createCommandpool(VkDevice device, int graphicsQueueIndex)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.mallocStack(stack)
                                                                        .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                                                                        .pNext(VK_NULL_HANDLE)
                                                                        .flags(0)
                                                                        .queueFamilyIndex(graphicsQueueIndex);
            
            LongBuffer handleHolder = stack.mallocLong(1);
            EngineUtils.checkError(vkCreateCommandPool(device, createInfo, null, handleHolder));
            commandPoolHandle = handleHolder.get(0);
        }
    }
    
    public void recreateSwapchain(VkPhysicalDevice physicalDevice, VkDevice device, VkPhysicalDeviceMemoryProperties memoryProperties, VkQueue queue)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            
            EngineUtils.checkError(vkDeviceWaitIdle(device));
            EngineUtils.checkError(vkResetCommandPool(device, commandPoolHandle, 0));
            
            destroySemaphores(device);
            destroyFramebuffers(device);
            destroySwapchainImageViews(device);
            destroySwapchain(device);
            
            createDepthImage(device, memoryProperties, queue);
            createSwapchain(physicalDevice, device);
            createSwapchainImageViews(device);
            createFramebuffers(device);
            createCommandBuffers(device, commandPoolHandle, swapchainImageViews.size());
            createDescriptorSetPool(device);
            createDescriptorSet(device);
            createSemaphores(device);
        }
    }
    
    private void createDepthImage(VkDevice device, VkPhysicalDeviceMemoryProperties memory, VkQueue renderQueue)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            // Create depth image
            
            VkImageCreateInfo createInfo = VkImageCreateInfo.mallocStack(stack)
                                                            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                                                            .pNext(VK_NULL_HANDLE)
                                                            .flags(0)
                                                            .imageType(VK_IMAGE_TYPE_2D)
                                                            .format(VK_FORMAT_D32_SFLOAT)
                                                            .extent(VkExtent3D.mallocStack(stack)
                                                                              .width(getWindowSize().width())
                                                                              .height(getWindowSize().height())
                                                                              .depth(1))
                                                            .mipLevels(1)
                                                            .arrayLayers(1)
                                                            .samples(VK_SAMPLE_COUNT_1_BIT)
                                                            .tiling(VK_IMAGE_TILING_OPTIMAL)
                                                            .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                                                            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                                                            .pQueueFamilyIndices(null)
                                                            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            
            LongBuffer handleHolder = stack.callocLong(1);
            EngineUtils.checkError(vkCreateImage(device, createInfo, null, handleHolder));
            depthImageHandle = handleHolder.get(0);
            
            // Create depth memory
            
            VkMemoryRequirements memoryReq = VkMemoryRequirements.mallocStack(stack);
            vkGetImageMemoryRequirements(device, depthImageHandle, memoryReq);
            int index = EngineUtils.findMemoryTypeIndex(memory, memoryReq, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            
            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.mallocStack(stack)
                                                                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                                                                    .pNext(VK_NULL_HANDLE)
                                                                    .memoryTypeIndex(index)
                                                                    .allocationSize(memoryReq.size());
            
            EngineUtils.checkError(vkAllocateMemory(device, allocateInfo, null, handleHolder));
            depthImageMemory = handleHolder.get(0);
            EngineUtils.checkError(vkBindImageMemory(device, depthImageHandle, depthImageMemory, 0));
            
            // Set image layout
            
            VkCommandBufferAllocateInfo bufferAllocateInfo = VkCommandBufferAllocateInfo.mallocStack(stack)
                                                                                        .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                                                                                        .pNext(VK_NULL_HANDLE)
                                                                                        .commandPool(getCommandPoolHandle())
                                                                                        .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                                                                                        .commandBufferCount(1);
            PointerBuffer pointerBuffer = stack.callocPointer(1);
            vkAllocateCommandBuffers(device, bufferAllocateInfo, pointerBuffer);
            
            VkCommandBuffer buffer = new VkCommandBuffer(pointerBuffer.get(0), device);
            
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.mallocStack(stack)
                                                                         .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                                                                         .pNext(VK_NULL_HANDLE)
                                                                         .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
                                                                         .pInheritanceInfo(null);
            
            vkBeginCommandBuffer(buffer, beginInfo);
            
            
            VkImageSubresourceRange subresourceRange = VkImageSubresourceRange.mallocStack(stack)
                                                                              .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                                                                              .baseMipLevel(0)
                                                                              .levelCount(1)
                                                                              .baseArrayLayer(0)
                                                                              .layerCount(1);
            
            VkImageMemoryBarrier.Buffer imageMemoryBarrier = VkImageMemoryBarrier.mallocStack(1, stack)
                                                                                 .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                                                                                 .pNext(VK_NULL_HANDLE)
                                                                                 .srcAccessMask(0)
                                                                                 .dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                                                                                 .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                                                                                 .newLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                                                                                 .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                                                                                 .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                                                                                 .image(depthImageHandle)
                                                                                 .subresourceRange(subresourceRange);
            
            vkCmdPipelineBarrier(buffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, null, null, imageMemoryBarrier);
            EngineUtils.checkError(vkEndCommandBuffer(buffer));
            
            VkSubmitInfo submitInfo = VkSubmitInfo.mallocStack(stack)
                                                  .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                                                  .pNext(VK_NULL_HANDLE)
                                                  .waitSemaphoreCount(0)
                                                  .pWaitSemaphores(null)
                                                  .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                                                  .pCommandBuffers(stack.pointers(buffer))
                                                  .pSignalSemaphores(null);
            
            EngineUtils.checkError(vkQueueSubmit(renderQueue, submitInfo, VK_NULL_HANDLE));
            
            // Set depth image view
            
            
            VkComponentMapping componentMapping = VkComponentMapping.mallocStack(stack)
                                                                    .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                                    .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                                    .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                                                                    .a(VK_COMPONENT_SWIZZLE_IDENTITY);
            
            
            VkImageViewCreateInfo imageViewCreateInfo = VkImageViewCreateInfo.mallocStack(stack)
                                                                             .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                                                                             .pNext(VK_NULL_HANDLE)
                                                                             .flags(0)
                                                                             .image(depthImageHandle)
                                                                             .viewType(VK_IMAGE_VIEW_TYPE_2D)
                                                                             .format(createInfo.format())
                                                                             .components(componentMapping)
                                                                             .subresourceRange(subresourceRange);
            
            EngineUtils.checkError(vkCreateImageView(device, imageViewCreateInfo, null, handleHolder));
            depthImageviewHandle = handleHolder.get(0);
        }
    }
    
    private void createDescriptorSet(VkDevice device)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.mallocStack(stack)
                                                                                  .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                                                                                  .pNext(VK_NULL_HANDLE)
                                                                                  .descriptorPool(descriptorPoolHandle)
                                                                                  .pSetLayouts(stack.longs(descriptorLayoutHandle));
            
            LongBuffer handleHolder = stack.mallocLong(1);
            EngineUtils.checkError(vkAllocateDescriptorSets(device, allocateInfo, handleHolder));
            descriptorSetHandle = handleHolder.get(0);
        }
    }
    
    private void createDescriptorSetPool(VkDevice device)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkDescriptorPoolSize.Buffer descriptorPoolSize       = VkDescriptorPoolSize.mallocStack(1, stack);
            VkDescriptorPoolCreateInfo  descriptorPoolCreateInfo = VkDescriptorPoolCreateInfo.mallocStack(stack);
            
            descriptorPoolSize.type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                              .descriptorCount(1);
            
            descriptorPoolCreateInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                                    .pNext(VK_NULL_HANDLE)
                                    .flags(0)
                                    .pPoolSizes(descriptorPoolSize)
                                    .maxSets(1);
            
            LongBuffer handleHolder = stack.mallocLong(1);
            EngineUtils.checkError(vkCreateDescriptorPool(device, descriptorPoolCreateInfo, null, handleHolder));
            descriptorPoolHandle = handleHolder.get(0);
        }
    }
    
    private void createSemaphores(VkDevice device)
    {
        imageReadySemaphore = createSemaphore(device);
        renderDoneSemaphore = createSemaphore(device);
    }
    
    private long createSemaphore(VkDevice device)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkSemaphoreCreateInfo createInfo = VkSemaphoreCreateInfo.mallocStack(stack)
                                                                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
                                                                    .pNext(VK_NULL_HANDLE)
                                                                    .flags(0);
            
            LongBuffer handleHolder = stack.callocLong(1);
            EngineUtils.checkError(vkCreateSemaphore(device, createInfo, null, handleHolder));
            return handleHolder.get(0);
        }
    }
    
    private void createCommandBuffers(VkDevice device, long commandPoolHandle, int size)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            int currentSize = commandBuffers.size();
            
            if (size > currentSize)
            {
                int requestCount = size - currentSize;
                
                VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.mallocStack(stack)
                                                                                      .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                                                                                      .pNext(VK_NULL_HANDLE)
                                                                                      .commandPool(commandPoolHandle)
                                                                                      .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                                                                                      .commandBufferCount(requestCount);
                PointerBuffer pointerBuffer = stack.callocPointer(requestCount);
                vkAllocateCommandBuffers(device, allocateInfo, pointerBuffer);
                
                for (int i = 0; i < pointerBuffer.remaining(); i++)
                {
                    VkCommandBuffer buffer = new VkCommandBuffer(pointerBuffer.get(i), device);
                    commandBuffers.add(buffer);
                }
            }
        }
    }
    
    public int getNextImageIndex(VkDevice device)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            
            IntBuffer imageHolder = stack.mallocInt(1);
            EngineUtils.checkError(vkAcquireNextImageKHR(device, swapchainHandle, Long.MAX_VALUE, imageReadySemaphore, VK_NULL_HANDLE, imageHolder));
            return imageHolder.get(0);
        }
    }
    
    public void beginCommandBuffer(VkCommandBuffer commandBuffer)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.mallocStack(stack)
                                                                         .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                                                                         .pNext(VK_NULL_HANDLE)
                                                                         .flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT)
                                                                         .pInheritanceInfo(null);
            
            EngineUtils.checkError(vkBeginCommandBuffer(commandBuffer, beginInfo));
        }
    }
    
    public void beginRenderPass(VkCommandBuffer commandBuffer, long frameBuffer, VkClearValue.Buffer clearColor)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkRenderPassBeginInfo passBeginInfo = VkRenderPassBeginInfo.mallocStack(stack)
                                                                       .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                                                                       .pNext(VK_NULL_HANDLE)
                                                                       .renderPass(renderPassHandle)
                                                                       .framebuffer(frameBuffer)
                                                                       .renderArea(VkRect2D.mallocStack(stack).extent(getSurfaceSize()).offset(VkOffset2D.callocStack(stack)))
                                                                       .pClearValues(clearColor);
            
            vkCmdBeginRenderPass(commandBuffer, passBeginInfo, VK_SUBPASS_CONTENTS_INLINE);
        }
    }
    
    public void bindPipeline(VkCommandBuffer commandBuffer)
    {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineHandle);
    }
    
    public void bindVertexBuffer(VkCommandBuffer commandBuffer, int vertexBufferBindingIndex, long buffer)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            vkCmdBindVertexBuffers(commandBuffer, vertexBufferBindingIndex, stack.longs(buffer), stack.longs(0));
        }
    }
    
    public void bindIndexBuffer(VkCommandBuffer commandBuffer, long indexBuffer)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            vkCmdBindIndexBuffer(commandBuffer, indexBuffer, 0, VK_INDEX_TYPE_UINT32);
        }
    }
    
    
    public void setViewPort(VkCommandBuffer commandBuffer)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkViewport.Buffer viewports = VkViewport.mallocStack(1, stack)
                                                    .x(0)
                                                    .y(0)
                                                    .width(windowSize.width())
                                                    .height(windowSize.height())
                                                    .minDepth(0)
                                                    .maxDepth(1);
            
            vkCmdSetViewport(commandBuffer, 0, viewports);
        }
    }
    
    public void setScissor(VkCommandBuffer commandBuffer)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkRect2D.Buffer scissors = VkRect2D.mallocStack(1, stack)
                                               .extent(windowSize)
                                               .offset(VkOffset2D.callocStack(stack));
            
            vkCmdSetScissor(commandBuffer, 0, scissors);
        }
    }
    
    public void draw(VkCommandBuffer commandBuffer, int vertexCount)
    {
        vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0);
    }
    
    public void drawIndexed(VkCommandBuffer commandBuffer, int indexCount)
    {
        vkCmdDrawIndexed(commandBuffer, indexCount, 1, 0, 0, 0);
    }
    
    public void endRenderPass(VkCommandBuffer commandBuffer)
    {
        vkCmdEndRenderPass(commandBuffer);
    }
    
    public void endCommandBuffer(VkCommandBuffer commandBuffer)
    {
        EngineUtils.checkError(vkEndCommandBuffer(commandBuffer));
    }
    
    public void submitToRenderQueue(VkQueue renderQueue, int imageIndex)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            
            VkSubmitInfo submitInfo = VkSubmitInfo.mallocStack(stack)
                                                  .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                                                  .pNext(VK_NULL_HANDLE)
                                                  .waitSemaphoreCount(1)
                                                  .pWaitSemaphores(stack.longs(imageReadySemaphore))
                                                  .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                                                  .pCommandBuffers(stack.pointers(getCommandBuffer(imageIndex)))
                                                  .pSignalSemaphores(stack.longs(renderDoneSemaphore));
            
            EngineUtils.checkError(vkQueueSubmit(renderQueue, submitInfo, VK_NULL_HANDLE));
            
        }
    }
    
    public void presentRenderQueue(VkQueue renderQueue, int imageIndex)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.mallocStack(stack)
                                                           .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                                                           .pNext(VK_NULL_HANDLE)
                                                           .waitSemaphoreCount(1)
                                                           .pWaitSemaphores(stack.longs(renderDoneSemaphore))
                                                           .swapchainCount(1)
                                                           .pSwapchains(stack.longs(swapchainHandle))
                                                           .pImageIndices(stack.ints(imageIndex))
                                                           .pResults(null);
            
            EngineUtils.checkError(vkQueuePresentKHR(renderQueue, presentInfo));
        }
    }
    
    public void bindDescriptorSet(VkCommandBuffer commandBuffer)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayoutHandle, 0, stack.longs(descriptorSetHandle), null);
        }
    }
}
