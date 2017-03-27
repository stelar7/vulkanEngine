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
    private String               windowName;
    private ColorFormatContainer surfaceFormat;
    
    private long windowHandle         = VK_NULL_HANDLE;
    private long surfaceHandle        = VK_NULL_HANDLE;
    private long swapchainHandle      = VK_NULL_HANDLE;
    private long renderPassHandle     = VK_NULL_HANDLE;
    private long pipelineLayoutHandle = VK_NULL_HANDLE;
    private long pipelineHandle       = VK_NULL_HANDLE;
    
    private static final int IDEAL_PRESENT_MODE = VK_PRESENT_MODE_MAILBOX_KHR;
    
    private VkSurfaceCapabilitiesKHR surfaceCapabilities = VkSurfaceCapabilitiesKHR.malloc();
    private VkExtent2D               windowSize          = VkExtent2D.malloc();
    
    
    private List<Long> swapchainImageViews;
    private List<Long> framebuffers;
    
    private long fragmentShader;
    private long vertexShader;
    
    private float     triangleSize = 1.6f;
    private float[][] triangle     = new float[][]
            {
                    /*rb*/ new float[]{0.5f * triangleSize, (float) (Math.sqrt(3.0f) * 0.25f * triangleSize),   /*R*/1.0f, 0.0f, 0.0f},
                    /* t*/ new float[]{0.0f, (float) (-Math.sqrt(3.0f) * 0.25f * triangleSize),                 /*G*/0.0f, 1.0f, 0.0f},
                    /*lb*/ new float[]{-0.5f * triangleSize, (float) (Math.sqrt(3.0f) * 0.25f * triangleSize),  /*B*/0.0f, 0.0f, 1.0f}
            };
    
    private Vertex vertices = new Vertex(triangle);
    
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
        
        vertices.destroy(device);
        destroyPipeline(device);
        destroyPipelineLayout(device);
        destroyShaders(device);
        destroySwapchainImageViews(device);
        destroyFramebuffers(device);
        destroyRenderPass(device);
        destroySwapchain(device);
        destroySurface(instance);
        destroyWindow();
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
        
        throw new RuntimeException("Ideal present mode is not avaliable, try FIFO instead");
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
    
    public List<Long> getSwapchainImages(VkDevice device)
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
            List<Long> images = getSwapchainImages(device);
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
            VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.mallocStack(1, stack)
                                                                                    .flags(0)
                                                                                    .format(surfaceFormat.getFormat())
                                                                                    .samples(VK_SAMPLE_COUNT_1_BIT)
                                                                                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                                                                                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                                                                                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                                                                                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                                                                                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                                                                                    .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            
            VkAttachmentReference.Buffer colorReference = VkAttachmentReference.mallocStack(1, stack)
                                                                               .attachment(0)
                                                                               .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            
            VkSubpassDescription.Buffer subpass = VkSubpassDescription.mallocStack(1, stack)
                                                                      .flags(0)
                                                                      .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                                                                      .pInputAttachments(null)
                                                                      .colorAttachmentCount(1)
                                                                      .pColorAttachments(colorReference)
                                                                      .pResolveAttachments(null)
                                                                      .pDepthStencilAttachment(null)
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
                                                                      .pAttachments(colorAttachment)
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
                                                                            .pAttachments(stack.longs(imageView))
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
            
            VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.mallocStack(stack)
                                                                              .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                                                                              .pNext(VK_NULL_HANDLE)
                                                                              .flags(0)
                                                                              .pSetLayouts(null)
                                                                              .pPushConstantRanges(null);
            
            LongBuffer handleBuffer = stack.callocLong(1);
            EngineUtils.checkError(vkCreatePipelineLayout(device, createInfo, null, handleBuffer));
            
            pipelineLayoutHandle = handleBuffer.get(0);
        }
    }
    
    public void prepareVertices(VkDevice device, VkPhysicalDeviceMemoryProperties memoryProperties)
    {
        
        long[] size = new long[1];
        vertices.setBuffer(createBuffer(device, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, vertices.getSizeInBytes()));
        vertices.setMemory(createMemory(device, memoryProperties, vertices.getBuffer(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, size));
        vertices.setData(device, size[0]);
    }
    
    private long createMemory(VkDevice device, VkPhysicalDeviceMemoryProperties memory, long buffer, int requiredFlags, long[] allocated)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkMemoryRequirements memoryRequirements = VkMemoryRequirements.mallocStack(stack);
            vkGetBufferMemoryRequirements(device, buffer, memoryRequirements);
            
            int index = EngineUtils.findMemoryTypeIndex(memory, memoryRequirements, requiredFlags);
            
            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.mallocStack(stack)
                                                                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                                                                    .pNext(VK_NULL_HANDLE)
                                                                    .allocationSize(memoryRequirements.size())
                                                                    .memoryTypeIndex(index);
            
            LongBuffer handleHolder = stack.mallocLong(1);
            EngineUtils.checkError(vkAllocateMemory(device, allocateInfo, null, handleHolder));
            
            allocated[0] = allocateInfo.allocationSize();
            return handleHolder.get(0);
        }
    }
    
    private long createBuffer(VkDevice device, int usage, long size)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.mallocStack(stack)
                                                                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                                                                    .pNext(VK_NULL_HANDLE)
                                                                    .flags(0)
                                                                    .size(size)
                                                                    .usage(usage)
                                                                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                                                                    .pQueueFamilyIndices(null);
            
            LongBuffer handleHolder = stack.callocLong(1);
            EngineUtils.checkError(vkCreateBuffer(device, bufferCreateInfo, null, handleHolder));
            return handleHolder.get(0);
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
            
            if (Vertex.getVertexStride() >= limits.maxVertexInputBindingStride())
            {
                throw new RuntimeException("GPU does not support a stride of " + Vertex.getVertexStride());
            }
            
            if (Vertex.getVertexBinding() > limits.maxVertexInputBindings())
            {
                throw new RuntimeException("GPU does not support binding " + Vertex.getVertexBinding() + " inputs");
            }
            
            if (Vertex.getLastAttribIndex() >= limits.maxVertexInputAttributes())
            {
                throw new RuntimeException("GPU does not support " + (Vertex.getLastAttribIndex() + 1) + " shader \"in\"s'");
            }
            
            if (Vertex.getLastAttribOffset() >= limits.maxVertexInputAttributeOffset())
            {
                throw new RuntimeException("GPU does not support " + Vertex.getLastAttribOffset() + " attribute offsets in shaders'");
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
            
            VkGraphicsPipelineCreateInfo.Buffer pipelineCreateInfo = VkGraphicsPipelineCreateInfo.mallocStack(1, stack)
                                                                                                 .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                                                                                                 .pNext(VK_NULL_HANDLE)
                                                                                                 .flags(0)
                                                                                                 .pStages(shaderStageCreateInfo)
                                                                                                 .pVertexInputState(Vertex.getCreateInfo())
                                                                                                 .pInputAssemblyState(inputAssemblyStateCreateInfo)
                                                                                                 .pTessellationState(null)
                                                                                                 .pViewportState(viewportStateCreateInfo)
                                                                                                 .pRasterizationState(rasterizationStateCreateInfo)
                                                                                                 .pMultisampleState(multisampleStateCreateInfo)
                                                                                                 .pDepthStencilState(null)
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
    
/*    private int index = 0;

    public long getNextFramebuffer()
    {
        index = index++ % framebuffers.size();
        return framebuffers.get(index);
    }*/
}
