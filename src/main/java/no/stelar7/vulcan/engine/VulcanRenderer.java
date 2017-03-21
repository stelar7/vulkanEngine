package no.stelar7.vulcan.engine;


import org.lwjgl.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugReport.*;
import static org.lwjgl.vulkan.KHRSurface.*;
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
    private boolean debugMode = true;
    
    private PointerBuffer pBuff = BufferUtils.createPointerBuffer(1);
    private IntBuffer     iBuff = BufferUtils.createIntBuffer(1);
    private LongBuffer    lBuff = BufferUtils.createLongBuffer(1);
    
    private PointerBuffer instanceExt;
    private PointerBuffer instanceLayers;
    
    private static final int VK_API_VERSION         = VK_MAKE_VERSION(1, 0, 3);
    private static final int VK_APPLICATION_VERSION = VK_MAKE_VERSION(0, 1, 0);
    
    private VkInstance       instance;
    private VkDevice         device;
    private VkPhysicalDevice gpu;
    private VkQueue          queue;
    
    private VkPhysicalDeviceProperties gpuProperties = VkPhysicalDeviceProperties.create();
    private VkPhysicalDeviceFeatures   gpuFeatures   = VkPhysicalDeviceFeatures.create();
    
    private ColorFormatAndSpace colorFormatAndSpace;
    
    private long debugCallbackHandle = VK_NULL_HANDLE;
    private long commandPoolHandle   = VK_NULL_HANDLE;
    private long fenceHandle         = VK_NULL_HANDLE;
    private long semaphoreHandle     = VK_NULL_HANDLE;
    private long surfaceHandle       = VK_NULL_HANDLE;
    private long windowHandle        = VK_NULL_HANDLE;
    
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
                vkDestroyDevice(device, null);
                
                KHRSurface.vkDestroySurfaceKHR(instance, surfaceHandle, null);
                
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
        
        if (debugMode)
        {
            setupVkDebug(requiredExtensions);
        }
        
        createVkInstance();
        
        if (debugMode)
        {
            createVkDebug();
        }
        
        createVkDevice();
        createVkSurface();
        
        createVkFence();
        createVkSemaphore();
        createVkCommandPool();
        createVkCommandBuffer();
        
        glfwShowWindow(windowHandle);
        
    }
    
    private void createVkSurface()
    {
        
        int status = glfwCreateWindowSurface(instance, windowHandle, null, lBuff);
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
        surfaceHandle = lBuff.get(0);
        
        IntBuffer wsiBuffer = BufferUtils.createIntBuffer(1).put(VK_TRUE);
        wsiBuffer.flip();
        status = vkGetPhysicalDeviceSurfaceSupportKHR(gpu, getDeviceQueueFamilyPropertyIndex(VK_QUEUE_GRAPHICS_BIT), surfaceHandle, wsiBuffer);
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
        
        VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.create();
        status = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(gpu, surfaceHandle, capabilities);
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
        System.out.println("Max Framebuffers: " + capabilities.maxImageCount());
        System.out.println("Current Window Size: " + capabilities.currentExtent().width() + "x" + capabilities.currentExtent().height());
        System.out.println("Max Window Size: " + capabilities.maxImageExtent().width() + "x" + capabilities.maxImageExtent().height());
        System.out.println();
        
        IntBuffer formatCount = BufferUtils.createIntBuffer(1);
        status = vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, surfaceHandle, formatCount, null);
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
        if (formatCount.get(0) < 1)
        {
            throw new RuntimeException("No Surface Formats Found");
        }
        
        VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.create(1);
        status = vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, surfaceHandle, formatCount, formats);
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
        colorFormatAndSpace = new ColorFormatAndSpace();
        colorFormatAndSpace.colorSpace = formats.get(0).colorSpace();
        if (formats.get(0).format() == VK_FORMAT_UNDEFINED)
        {
            colorFormatAndSpace.colorFormat = VK_FORMAT_B8G8R8_UNORM;
        } else
        {
            colorFormatAndSpace.colorFormat = formats.get(0).format();
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
        
        
        int status = vkAllocateCommandBuffers(device, info, pBuff);
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
        VkCommandBuffer commandBuffer = new VkCommandBuffer(pBuff.get(0), device);
        VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.create()
                                                                     .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        
        status = vkBeginCommandBuffer(commandBuffer, beginInfo);
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
        VkViewport.Buffer viewPort = VkViewport.create(1)
                                               .maxDepth(1)
                                               .minDepth(0)
                                               .width(WIDTH)
                                               .height(HEIGHT)
                                               .x(0)
                                               .y(0);
        vkCmdSetViewport(commandBuffer, 0, viewPort);
        
        status = vkEndCommandBuffer(commandBuffer);
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
        
        PointerBuffer commandBuff = BufferUtils.createPointerBuffer(1).put(commandBuffer).flip();
        
        VkSubmitInfo submitInfo = VkSubmitInfo.create()
                                              .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                                              .pCommandBuffers(commandBuff);
        
        status = vkQueueSubmit(queue, submitInfo, fenceHandle);
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
        status = vkWaitForFences(device, fenceHandle, false, Long.MAX_VALUE);
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
    }
    
    private void createVkCommandPool()
    {
        VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.create()
                                                                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                                                                    .queueFamilyIndex(getDeviceQueueFamilyPropertyIndex(VK_QUEUE_GRAPHICS_BIT))
                                                                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
        
        int status = vkCreateCommandPool(device, createInfo, null, lBuff);
        
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
        commandPoolHandle = lBuff.get(0);
    }
    
    private void setupVkDebug(PointerBuffer requiredExtensions)
    {
        instanceLayers = BufferUtils.createPointerBuffer(1);
        instanceLayers
                .put(memASCII("VK_LAYER_LUNARG_standard_validation"))
                .flip();
        
        instanceExt = BufferUtils.createPointerBuffer(requiredExtensions.remaining() + 1);
        instanceExt.put(requiredExtensions)
                   .put(memASCII("VK_EXT_debug_report"))
                   .flip();
        
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
        
        int status = vkCreateDebugReportCallbackEXT(instance, debugCreateInfo, null, lBuff);
        
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
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
            
            
            System.out.println("API version: " + EngineUtils.vkVersionToString(gpuProperties.apiVersion()));
            System.out.println("Driver version: " + EngineUtils.vkVersionToString(gpuProperties.driverVersion()));
            System.out.println("Vendor ID: " + EngineUtils.vkVendorToString(gpuProperties.vendorID()));
            System.out.println("Device Name: " + gpuProperties.deviceNameString());
            System.out.println("Device ID: " + gpuProperties.deviceID());
            System.out.println("Device Type: " + EngineUtils.vkPhysicalDeviceToString(gpuProperties.deviceType()));
            System.out.println();
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
                                                                .pQueueCreateInfos(queueCreateInfo);
        
        int status = vkCreateDevice(gpu, deviceCreateInfo, null, pBuff);
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
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
                                                              .pApplicationInfo(appInfo);
        if (debugMode)
        {
            createInfo.ppEnabledExtensionNames(instanceExt)
                      .ppEnabledLayerNames(instanceLayers);
        }
        
        
        int status = vkCreateInstance(createInfo, null, pBuff);
        
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
        
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
