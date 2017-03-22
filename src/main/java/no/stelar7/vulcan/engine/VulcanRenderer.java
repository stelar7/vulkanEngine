package no.stelar7.vulcan.engine;


import org.lwjgl.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugReport.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class VulcanRenderer
{
    private static final int    WIDTH  = 800;
    private static final int    HEIGHT = 600;
    private static final String TITLE  = "Vulcan Game Engine";
    private Game game;
    
    private static class ColorFormatAndSpace
    {
        protected int colorFormat;
        protected int colorSpace;
    }
    
    private final Object lock = new Object();
    private boolean shouldClose;
    
    private PointerBuffer pBuff = BufferUtils.createPointerBuffer(1);
    private IntBuffer     iBuff = BufferUtils.createIntBuffer(1);
    private LongBuffer    lBuff = BufferUtils.createLongBuffer(1);
    
    private PointerBuffer instanceExt;
    private PointerBuffer instanceLayers;
    
    
    private LongBuffer imageViewBuffer;
    private long       depthStencilImage;
    private long       depthStencilImageView;
    private long       depthStencilImageMemory;
    
    
    private PointerBuffer deviceExt;
    
    private static final int VK_API_VERSION         = VK_MAKE_VERSION(1, 0, 3);
    private static final int VK_APPLICATION_VERSION = VK_MAKE_VERSION(0, 1, 0);
    
    private VkInstance       instance;
    private VkDevice         device;
    private VkPhysicalDevice gpu;
    private VkQueue          queue;
    private VkExtent2D       surfaceSize;
    
    private VkPhysicalDeviceProperties       gpuProperties       = VkPhysicalDeviceProperties.create();
    private VkPhysicalDeviceFeatures         gpuFeatures         = VkPhysicalDeviceFeatures.create();
    private VkPhysicalDeviceMemoryProperties gpuMemory           = VkPhysicalDeviceMemoryProperties.create();
    private VkSurfaceCapabilitiesKHR         surfaceCapabilities = VkSurfaceCapabilitiesKHR.create();
    
    private ColorFormatAndSpace surfaceFormat;
    
    private int swapchainImageCount = 2;
    
    private int statusHolder;
    
    private long debugCallbackHandle = VK_NULL_HANDLE;
    private long commandPoolHandle   = VK_NULL_HANDLE;
    private long fenceHandle         = VK_NULL_HANDLE;
    private long semaphoreHandle     = VK_NULL_HANDLE;
    private long surfaceHandle       = VK_NULL_HANDLE;
    private long windowHandle        = VK_NULL_HANDLE;
    private long swapchainHandle     = VK_NULL_HANDLE;
    
    public VkDevice getDevice()
    {
        return device;
    }
    
    public VkInstance getInstance()
    {
        return instance;
    }
    
    public VkPhysicalDevice getPhysicalDevice()
    {
        return gpu;
    }
    
    
    public static void main(String[] args)
    {
        new VulcanRenderer().run();
    }
    
    public void run()
    {
        try
        {
            init();
            
            new Thread(this::loop).start();
            
            while (!shouldClose)
            {
                glfwWaitEvents();
            }
            
            synchronized (lock)
            {
                vkDestroyFence(device, fenceHandle, null);
                vkDestroySemaphore(device, semaphoreHandle, null);
                vkDestroyCommandPool(device, commandPoolHandle, null);
                vkDestroySwapchainKHR(device, swapchainHandle, null);
                
                for (int i = 0; i < swapchainImageCount; i++)
                {
                    vkDestroyImageView(device, imageViewBuffer.get(i), null);
                }
                
                
                vkDestroyImageView(device, depthStencilImageView, null);
                vkFreeMemory(device, depthStencilImageMemory, null);
                vkDestroyImage(device, depthStencilImage, null);
                
                
                vkDestroyDevice(device, null);
                vkDestroySurfaceKHR(instance, surfaceHandle, null);
                
                vkDestroyDebugReportCallbackEXT(instance, debugCallbackHandle, null);
                vkDestroyInstance(instance, null);
                
                glfwFreeCallbacks(windowHandle);
                glfwDestroyWindow(windowHandle);
            }
        } finally
        {
            glfwTerminate();
        }
    }
    
    private void init()
    {
        if (!glfwInit())
        {
            throw new RuntimeException("Failed to init GLFW");
        }
        
        if (!glfwVulkanSupported())
        {
            throw new RuntimeException("Vulkan not supported");
        }
        
        PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
        if (requiredExtensions == null)
        {
            throw new RuntimeException("Failed to get required extensions");
        }
        
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        windowHandle = glfwCreateWindow(WIDTH, HEIGHT, TITLE, MemoryUtil.NULL, MemoryUtil.NULL);
        
        setupInstance(requiredExtensions);
        createVkInstance();
        createVkDebug();
        
        createVkDevice();
        createVkSurface();
        
        createVkSwapchain();
        createVkSwapchainImage();
        
        createDepthStencilImage();
        
        createVkFence();
        createVkSemaphore();
        createVkCommandPool();
        createVkCommandBuffer();
        
        glfwShowWindow(windowHandle);
        
    }
    
    private void createDepthStencilImage()
    {
        int depthStencilFormat = -1;
        
        List<Integer> depthFormats = Arrays.asList(VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D16_UNORM_S8_UINT, VK_FORMAT_D32_SFLOAT, VK_FORMAT_D16_UNORM);
        for (Integer testFormat : depthFormats)
        {
            VkFormatProperties formatProperties = VkFormatProperties.create();
            vkGetPhysicalDeviceFormatProperties(gpu, testFormat, formatProperties);
            
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
        
        statusHolder = vkCreateImage(device, createInfo, null, lBuff);
        depthStencilImage = lBuff.get(0);
        EngineUtils.checkError(statusHolder);
        
        
        VkMemoryRequirements memoryRequirements = VkMemoryRequirements.create();
        vkGetImageMemoryRequirements(device, depthStencilImage, memoryRequirements);
        
        int memoryIndex = EngineUtils.findMemoryTypeIndex(gpuMemory, memoryRequirements, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.create()
                                                                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                                                                .allocationSize(memoryRequirements.size())
                                                                .memoryTypeIndex(memoryIndex);
        
        vkAllocateMemory(device, allocateInfo, null, lBuff);
        depthStencilImageMemory = lBuff.get(0);
        vkBindImageMemory(device, depthStencilImage, depthStencilImageMemory, 0);
        
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
        
        statusHolder = vkCreateImageView(device, viewCreateInfo, null, lBuff);
        depthStencilImageView = lBuff.get(0);
        EngineUtils.checkError(statusHolder);
    }
    
    
    private void createVkSwapchainImage()
    {
        LongBuffer imageBuffer = BufferUtils.createLongBuffer(swapchainImageCount);
        imageViewBuffer = BufferUtils.createLongBuffer(swapchainImageCount);
        
        statusHolder = vkGetSwapchainImagesKHR(device, swapchainHandle, iBuff, imageBuffer);
        EngineUtils.checkError(statusHolder);
        
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
                                                                    .format(surfaceFormat.colorFormat)
                                                                    .components(mapping)
                                                                    .subresourceRange(range);
            
            
            statusHolder = vkCreateImageView(device, createInfo, null, lBuff);
            imageViewBuffer.put(i, lBuff.get(0));
            EngineUtils.checkError(statusHolder);
        }
    }
    
    private void createVkSwapchain()
    {
        statusHolder = vkGetPhysicalDeviceSurfacePresentModesKHR(gpu, surfaceHandle, iBuff, null);
        EngineUtils.checkError(statusHolder);
        
        IntBuffer avaliablePresentModes = BufferUtils.createIntBuffer(iBuff.get(0));
        statusHolder = vkGetPhysicalDeviceSurfacePresentModesKHR(gpu, surfaceHandle, iBuff, avaliablePresentModes);
        EngineUtils.checkError(statusHolder);
        
        int presentMode = VK_PRESENT_MODE_FIFO_KHR;
        for (int i = 0; i < avaliablePresentModes.remaining(); i++)
        {
            // Tripplebuffer vSync
            if (avaliablePresentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR)
            {
                presentMode = avaliablePresentModes.get(i);
            }
            
            /*
            // Doublebuffer vSync
            if (avaliablePresentModes.get(i) == VK_PRESENT_MODE_FIFO_KHR)
            {
                presentMode = avaliablePresentModes.get(i);
            }
            
            // No buffer, no vSync
            if (avaliablePresentModes.get(i) == VK_PRESENT_MODE_IMMEDIATE_KHR)
            {
                presentMode = avaliablePresentModes.get(i);
            }
            */
        }
        
        
        VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.create()
                                                                      .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                                                                      .surface(surfaceHandle)
                                                                      .minImageCount(swapchainImageCount)
                                                                      .imageFormat(surfaceFormat.colorFormat)
                                                                      .imageColorSpace(surfaceFormat.colorSpace)
                                                                      .imageExtent(surfaceSize)
                                                                      .imageArrayLayers(1)
                                                                      .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                                                                      .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                                                                      .preTransform(VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)
                                                                      .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                                                                      .presentMode(presentMode)
                                                                      .clipped(true)
                                                                      .oldSwapchain(VK_NULL_HANDLE);
        
        statusHolder = vkCreateSwapchainKHR(device, createInfo, null, lBuff);
        EngineUtils.checkError(statusHolder);
        swapchainHandle = lBuff.get(0);
        
        statusHolder = vkGetSwapchainImagesKHR(device, swapchainHandle, iBuff, null);
        EngineUtils.checkError(statusHolder);
        
        System.out.println("Requested swapchain count: " + swapchainImageCount);
        swapchainImageCount = iBuff.get(0);
        System.out.println("Actual swapchain count: " + swapchainImageCount);
        System.out.println();
        
    }
    
    private void createVkSurface()
    {
        
        statusHolder = glfwCreateWindowSurface(instance, windowHandle, null, lBuff);
        EngineUtils.checkError(statusHolder);
        
        surfaceHandle = lBuff.get(0);
        
        IntBuffer wsiBuffer = BufferUtils.createIntBuffer(1).put(VK_TRUE);
        wsiBuffer.flip();
        statusHolder = vkGetPhysicalDeviceSurfaceSupportKHR(gpu, getDeviceQueueFamilyPropertyIndex(VK_QUEUE_GRAPHICS_BIT), surfaceHandle, wsiBuffer);
        EngineUtils.checkError(statusHolder);
        
        
        statusHolder = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(gpu, surfaceHandle, surfaceCapabilities);
        EngineUtils.checkError(statusHolder);
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
        statusHolder = vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, surfaceHandle, formatCount, null);
        EngineUtils.checkError(statusHolder);
        
        if (formatCount.get(0) < 1)
        {
            throw new RuntimeException("No Surface Formats Found");
        }
        
        VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.create(formatCount.get(0));
        statusHolder = vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, surfaceHandle, formatCount, formats);
        EngineUtils.checkError(statusHolder);
        
        
        surfaceFormat = new ColorFormatAndSpace();
        surfaceFormat.colorSpace = formats.get(0).colorSpace();
        if (formats.get(0).format() == VK_FORMAT_UNDEFINED)
        {
            surfaceFormat.colorFormat = VK_FORMAT_B8G8R8_UNORM;
        } else
        {
            surfaceFormat.colorFormat = formats.get(0).format();
        }
        
    }
    
    private void createVkSemaphore()
    {
        
        VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.create()
                                                                   .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        
        vkCreateSemaphore(device, semaphoreInfo, null, lBuff);
        semaphoreHandle = lBuff.get(0);
    }
    
    private void createVkFence()
    {
        VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.create()
                                                       .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
        
        vkCreateFence(device, fenceInfo, null, lBuff);
        fenceHandle = lBuff.get(0);
        
    }
    
    private void createVkCommandBuffer()
    {
        
        VkCommandBufferAllocateInfo info = VkCommandBufferAllocateInfo.create()
                                                                      .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                                                                      .commandPool(commandPoolHandle)
                                                                      .commandBufferCount(1)
                                                                      .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
        
        
        statusHolder = vkAllocateCommandBuffers(device, info, pBuff);
        EngineUtils.checkError(statusHolder);
        
        
        VkCommandBuffer commandBuffer = new VkCommandBuffer(pBuff.get(0), device);
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.create()
                                                                     .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        
        statusHolder = vkBeginCommandBuffer(commandBuffer, beginInfo);
        EngineUtils.checkError(statusHolder);
        
        
        VkViewport.Buffer viewPort = VkViewport.create(1)
                                               .maxDepth(1)
                                               .minDepth(0)
                                               .width(WIDTH)
                                               .height(HEIGHT)
                                               .x(0)
                                               .y(0);
        vkCmdSetViewport(commandBuffer, 0, viewPort);
        
        statusHolder = vkEndCommandBuffer(commandBuffer);
        EngineUtils.checkError(statusHolder);
        
        
        PointerBuffer commandBuff = BufferUtils.createPointerBuffer(1).put(commandBuffer).flip();
        
        VkSubmitInfo submitInfo = VkSubmitInfo.create()
                                              .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                                              .pCommandBuffers(commandBuff);
        
        statusHolder = vkQueueSubmit(queue, submitInfo, fenceHandle);
        EngineUtils.checkError(statusHolder);
        
        statusHolder = vkWaitForFences(device, fenceHandle, false, Long.MAX_VALUE);
        EngineUtils.checkError(statusHolder);
        
    }
    
    private void createVkCommandPool()
    {
        VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.create()
                                                                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                                                                    .queueFamilyIndex(getDeviceQueueFamilyPropertyIndex(VK_QUEUE_GRAPHICS_BIT))
                                                                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
        
        statusHolder = vkCreateCommandPool(device, createInfo, null, lBuff);
        EngineUtils.checkError(statusHolder);
        
        commandPoolHandle = lBuff.get(0);
    }
    
    private void setupInstance(PointerBuffer requiredExtensions)
    {
        instanceLayers = BufferUtils.createPointerBuffer(1);
        instanceLayers.put(memASCII("VK_LAYER_LUNARG_standard_validation"));
        instanceLayers.flip();
        
        
        instanceExt = BufferUtils.createPointerBuffer(requiredExtensions.remaining() + 1);
        instanceExt.put(requiredExtensions);
        instanceExt.put(memASCII("VK_EXT_debug_report"));
        instanceExt.flip();
    }
    
    private void createVkDebug()
    {
        
        VkDebugReportCallbackEXT debugCallback = new VkDebugReportCallbackEXT()
        {
            @Override
            public int invoke(int flags, int objectType, long object, long location, int messageCode, long pLayerPrefix, long pMessage, long pUserData)
            {
                System.err.format("%s: [%s] Code %d : %s%n", EngineUtils.vkDebugFlagToString(flags), pLayerPrefix, messageCode, memASCII(pMessage));
                return VK_FALSE;
            }
        };
        
        VkDebugReportCallbackCreateInfoEXT debugCreateInfo = VkDebugReportCallbackCreateInfoEXT.create()
                                                                                               .sType(VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT)
                                                                                               .flags(VK_DEBUG_REPORT_ERROR_BIT_EXT | VK_DEBUG_REPORT_WARNING_BIT_EXT | VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT)
                                                                                               .pfnCallback(debugCallback);
        
        
        statusHolder = vkCreateDebugReportCallbackEXT(instance, debugCreateInfo, null, lBuff);
        EngineUtils.checkError(statusHolder);
        
        debugCallbackHandle = lBuff.get(0);
        
    }
    
    
    public int getDeviceQueueFamilyPropertyIndex(final int flag)
    {
        vkGetPhysicalDeviceQueueFamilyProperties(gpu, iBuff, null);
        VkQueueFamilyProperties.Buffer props = VkQueueFamilyProperties.create(iBuff.get(0));
        
        if (iBuff.get(0) > 0)
        {
            vkGetPhysicalDeviceQueueFamilyProperties(gpu, iBuff, props);
            
            for (int i = 0; i < iBuff.get(0); i++)
            {
                if ((props.get(i).queueFlags() & flag) == flag)
                {
                    return i;
                }
            }
        }
        throw new RuntimeException("No Queue Supporting Graphics Found!");
    }
    
    private void createVkDevice()
    {
        vkEnumeratePhysicalDevices(instance, iBuff, null);
        if (iBuff.get(0) > 0)
        {
            PointerBuffer gpuHandles = BufferUtils.createPointerBuffer(iBuff.get(0));
            vkEnumeratePhysicalDevices(instance, iBuff, gpuHandles);
            
            // Only use the first GPU
            gpu = new VkPhysicalDevice(gpuHandles.get(0), instance);
            vkGetPhysicalDeviceProperties(gpu, gpuProperties);
            vkGetPhysicalDeviceFeatures(gpu, gpuFeatures);
            vkGetPhysicalDeviceMemoryProperties(gpu, gpuMemory);
            
            
            System.out.println("Device Type: " + EngineUtils.vkPhysicalDeviceToString(gpuProperties.deviceType()));
            System.out.println("Vulkan API version: " + EngineUtils.vkVersionToString(gpuProperties.apiVersion()));
            System.out.println("Driver version: " + EngineUtils.vkVersionToString(gpuProperties.driverVersion()));
            System.out.println("Vendor ID: " + gpuProperties.vendorID());
            System.out.println("Device ID: " + gpuProperties.deviceID());
            System.out.println("Device Name: " + gpuProperties.deviceNameString());
            System.out.println("Device Memory: " + gpuMemory.memoryHeaps(0).size() / 1_000_000_000f + "GB");
            System.out.println("Host Memory: " + gpuMemory.memoryHeaps(1).size() / 1_000_000_000f + "GB");
            System.out.println();
            
            
            statusHolder = vkEnumerateDeviceExtensionProperties(gpu, (CharSequence) null, iBuff, null);
            EngineUtils.checkError(statusHolder);
            boolean hasSwapchain = false;
            
            if (iBuff.get(0) > 0)
            {
                VkExtensionProperties.Buffer extensions = VkExtensionProperties.create(iBuff.get(0));
                statusHolder = vkEnumerateDeviceExtensionProperties(gpu, (CharSequence) null, iBuff, extensions);
                EngineUtils.checkError(statusHolder);
                
                deviceExt = BufferUtils.createPointerBuffer(iBuff.get(0));
                
                for (int i = 0; i < iBuff.get(0); i++)
                {
                    if (extensions.get(i).extensionNameString().equals(VK_KHR_SWAPCHAIN_EXTENSION_NAME))
                    {
                        hasSwapchain = true;
                    }
                    deviceExt.put(memASCII(extensions.get(i).extensionNameString()));
                }
            }
            
            if (!hasSwapchain)
            {
                throw new RuntimeException("GPU does not support swapchains!");
            }
            
            deviceExt.flip();
            
        }
        
        vkEnumerateInstanceLayerProperties(iBuff, null);
        VkLayerProperties.Buffer layerProperties = VkLayerProperties.create(iBuff.get(0));
        vkEnumerateInstanceLayerProperties(iBuff, layerProperties);
        
        System.out.println("Instance Layers:");
        for (int i = 0; i < layerProperties.capacity(); i++)
        {
            System.out.format("%-40s \t | \t %s%n", layerProperties.get(i).layerNameString(), layerProperties.get(i).descriptionString());
        }
        System.out.println();
        
        vkEnumerateDeviceLayerProperties(gpu, iBuff, null);
        layerProperties = VkLayerProperties.create(iBuff.get(0));
        vkEnumerateDeviceLayerProperties(gpu, iBuff, layerProperties);
        
        System.out.println("Device Layers:");
        for (int i = 0; i < layerProperties.capacity(); i++)
        {
            System.out.format("%-40s \t | \t %s%n", layerProperties.get(i).layerNameString(), layerProperties.get(i).descriptionString());
        }
        System.out.println();
        
        
        FloatBuffer priority = BufferUtils.createFloatBuffer(1);
        priority.put(1).flip();
        
        VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.create(1)
                                                                                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                                                                                .queueFamilyIndex(getDeviceQueueFamilyPropertyIndex(VK_QUEUE_GRAPHICS_BIT))
                                                                                .pQueuePriorities(priority);
        
        VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.create()
                                                                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                                                                .pQueueCreateInfos(queueCreateInfo)
                                                                .ppEnabledExtensionNames(deviceExt);
        
        statusHolder = vkCreateDevice(gpu, deviceCreateInfo, null, pBuff);
        EngineUtils.checkError(statusHolder);
        
        device = new VkDevice(pBuff.get(0), gpu, deviceCreateInfo);
        
        vkGetDeviceQueue(device, getDeviceQueueFamilyPropertyIndex(VK_QUEUE_GRAPHICS_BIT), 0, pBuff);
        queue = new VkQueue(pBuff.get(0), device);
        
    }
    
    private void createVkInstance()
    {
        
        
        VkApplicationInfo appInfo = VkApplicationInfo.create()
                                                     .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                                                     .apiVersion(VK_API_VERSION)
                                                     .applicationVersion(VK_APPLICATION_VERSION)
                                                     .pApplicationName(MemoryStack.stackUTF8(TITLE));
        
        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.create()
                                                              .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                                                              .pApplicationInfo(appInfo)
                                                              .ppEnabledExtensionNames(instanceExt)
                                                              .ppEnabledLayerNames(instanceLayers);
        
        
        statusHolder = vkCreateInstance(createInfo, null, pBuff);
        EngineUtils.checkError(statusHolder);
        
        instance = new VkInstance(pBuff.get(0), createInfo);
    }
    
    
    private void loop()
    {
        postInit();
        
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
            
            render();
            fps++;
            
            synchronized (lock)
            {
                shouldClose = glfwWindowShouldClose(windowHandle);
                
                if (shouldClose)
                {
                    game.delete();
                    return;
                }
                
                glfwSwapBuffers(windowHandle);
            }
        }
    }
    
    private void postInit()
    {
        game = new TestGame(this);
    }
    
    private void update()
    {
        game.update();
    }
    
    private void render()
    {
        game.render();
    }
}
