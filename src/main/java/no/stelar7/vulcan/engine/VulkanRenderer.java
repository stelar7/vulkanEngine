package no.stelar7.vulcan.engine;


import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugReport.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class VulkanRenderer
{
    private Game game;
    
    private final Object lock = new Object();
    private boolean shouldClose;
    
    private PointerBuffer instanceExt;
    private PointerBuffer deviceExt;
    
    private PointerBuffer instanceLayers;
    
    private Window window;
    
    private long debugCallbackHandle = VK_NULL_HANDLE;
    
    
    private int presentQueueIndex  = -1;
    private int graphicsQueueIndex = -1;
    
    
    private static final int VK_API_VERSION         = VK_MAKE_VERSION(1, 0, 3);
    private static final int VK_APPLICATION_VERSION = VK_MAKE_VERSION(0, 1, 0);
    
    private VkInstance       instance;
    private VkDevice         device;
    private VkPhysicalDevice gpu;
    private VkQueue          queue;
    
    private VkPhysicalDeviceProperties       gpuProperties = VkPhysicalDeviceProperties.calloc();
    private VkPhysicalDeviceFeatures         gpuFeatures   = VkPhysicalDeviceFeatures.calloc();
    private VkPhysicalDeviceMemoryProperties gpuMemory     = VkPhysicalDeviceMemoryProperties.calloc();
    
    public VkPhysicalDeviceProperties getGpuProperties()
    {
        return gpuProperties;
    }
    
    public VkPhysicalDeviceFeatures getGpuFeatures()
    {
        return gpuFeatures;
    }
    
    public VkPhysicalDeviceMemoryProperties getGpuMemory()
    {
        return gpuMemory;
    }
    
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
    
    public VkQueue getRenderQueue()
    {
        return queue;
    }
    
    public VulkanRenderer(int width, int height, String title)
    {
        initGLFW();
        initVulkan();
        window = new Window(this, width, height, title);
        getDeviceQueueFamilyPropertyIndecies();
        createVkDevice();
        window.init();
    }
    
    private void initVulkan()
    {
        PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
        if (requiredExtensions == null)
        {
            throw new RuntimeException("Failed to get required extensions");
        }
        
        
        setupInstance(requiredExtensions);
        createVkInstance();
        createVkDebug();
        createPhysicalDevice();
    }
    
    public void start()
    {
        try
        {
            if (window == null)
            {
                throw new RuntimeException("Renderer started without a window!");
            }
            
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
                instanceLayers.free();
                instanceExt.free();
                deviceExt.free();
                gpuProperties.free();
                gpuFeatures.free();
                gpuMemory.free();
                
                window.destroy();
                vkDestroyDevice(device, null);
                
                vkDestroyDebugReportCallbackEXT(instance, debugCallbackHandle, null);
                vkDestroyInstance(instance, null);
            }
        } finally
        {
            glfwSetErrorCallback(null).free();
            glfwTerminate();
        }
    }
    
    
    private void initGLFW()
    {
        if (!glfwInit())
        {
            throw new RuntimeException("Failed to initGLFW GLFW");
        }
        
        GLFWErrorCallback.createPrint(System.err).set();
        
        if (!glfwVulkanSupported())
        {
            throw new RuntimeException("Vulkan not supported");
        }
    }
    
    
    private void setupInstance(PointerBuffer requiredExtensions)
    {
        instanceLayers = MemoryStack.stackMallocPointer(1);
        instanceLayers.put(memASCII("VK_LAYER_LUNARG_standard_validation"));
        instanceLayers.flip();
        
        
        instanceExt = MemoryStack.stackMallocPointer(requiredExtensions.remaining() + 1);
        instanceExt.put(requiredExtensions);
        instanceExt.put(memASCII("VK_EXT_debug_report"));
        instanceExt.flip();
    }
    
    private void createVkDebug()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
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
            
            VkDebugReportCallbackCreateInfoEXT debugCreateInfo = VkDebugReportCallbackCreateInfoEXT.callocStack(stack)
                                                                                                   .sType(VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT)
                                                                                                   .flags(VK_DEBUG_REPORT_ERROR_BIT_EXT | VK_DEBUG_REPORT_WARNING_BIT_EXT | VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT)
                                                                                                   .pfnCallback(debugCallback);
            
            LongBuffer handleContainer = stack.callocLong(1);
            EngineUtils.checkError(vkCreateDebugReportCallbackEXT(instance, debugCreateInfo, null, handleContainer));
            
            debugCallbackHandle = handleContainer.get(0);
        }
    }
    
    
    private void getDeviceQueueFamilyPropertyIndecies()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer queueFamilyProperties = stack.callocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(gpu, queueFamilyProperties, null);
            int queueFamilies = queueFamilyProperties.get(0);
            
            if (queueFamilies > 0)
            {
                IntBuffer surfaceSupports = stack.callocInt(queueFamilies);
                
                VkQueueFamilyProperties.Buffer props = VkQueueFamilyProperties.callocStack(queueFamilies, stack);
                vkGetPhysicalDeviceQueueFamilyProperties(gpu, queueFamilyProperties, props);
                
                for (int i = 0; i < queueFamilies; i++)
                {
                    surfaceSupports.position(i);
                    EngineUtils.checkError(KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(gpu, i, window.getSurfaceHandle(), surfaceSupports));
                }
                
                for (int i = 0; i < queueFamilies; i++)
                {
                    boolean hasPresentQueue = surfaceSupports.get(i) == VK_TRUE;
                    
                    if ((props.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) == VK_QUEUE_GRAPHICS_BIT)
                    {
                        if (graphicsQueueIndex == -1)
                        {
                            graphicsQueueIndex = i;
                        }
                        
                        if (hasPresentQueue)
                        {
                            graphicsQueueIndex = i;
                            presentQueueIndex = i;
                        }
                    }
                }
                
                if (presentQueueIndex == -1)
                {
                    for (int i = 0; i < queueFamilies; i++)
                    {
                        boolean hasPresentQueue = surfaceSupports.get(0) == VK_TRUE;
                        if (hasPresentQueue)
                        {
                            presentQueueIndex = i;
                        }
                    }
                }
            }
            
            System.out.println("Graphics Queue Index: " + graphicsQueueIndex);
            System.out.println("Present Queue Index: " + presentQueueIndex);
            if (graphicsQueueIndex == -1 || presentQueueIndex == -1)
            {
                throw new RuntimeException("No Queue Supporting Graphics Or Presentation Found!");
            }
            
            if (graphicsQueueIndex != presentQueueIndex)
            {
                throw new RuntimeException("Could not find a common graphics and a present queue");
            }
        }
    }
    
    private void createPhysicalDevice()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer deviceBuffer = stack.callocInt(1);
            vkEnumeratePhysicalDevices(instance, deviceBuffer, null);
            if (deviceBuffer.get(0) > 0)
            {
                PointerBuffer gpuHandles = stack.callocPointer(deviceBuffer.get(0));
                vkEnumeratePhysicalDevices(instance, deviceBuffer, gpuHandles);
                
                // Only use the first GPU
                gpu = new VkPhysicalDevice(gpuHandles.get(0), instance);
                vkGetPhysicalDeviceProperties(gpu, gpuProperties);
                vkGetPhysicalDeviceFeatures(gpu, gpuFeatures);
                vkGetPhysicalDeviceMemoryProperties(gpu, gpuMemory);
                
                
                System.out.println("System Information:");
                System.out.println("Device Type: " + EngineUtils.vkPhysicalDeviceToString(gpuProperties.deviceType()));
                System.out.println("Vulkan API version: " + EngineUtils.vkVersionToString(gpuProperties.apiVersion()));
                System.out.println("Driver version: " + EngineUtils.vkVersionToString(gpuProperties.driverVersion()));
                System.out.println("Vendor ID: " + gpuProperties.vendorID());
                System.out.println("Device ID: " + gpuProperties.deviceID());
                System.out.println("Device Name: " + gpuProperties.deviceNameString());
                System.out.println("Device Memory: " + gpuMemory.memoryHeaps(0).size() / 1_000_000_000f + "GB");
                System.out.println("Host Memory: " + gpuMemory.memoryHeaps(1).size() / 1_000_000_000f + "GB");
                System.out.println();
                
                
                EngineUtils.checkError(vkEnumerateDeviceExtensionProperties(gpu, (CharSequence) null, deviceBuffer, null));
                boolean hasSwapchain = false;
                
                System.out.println("Supported GPU Extensions:");
                
                if (deviceBuffer.get(0) > 0)
                {
                    VkExtensionProperties.Buffer extensions = VkExtensionProperties.callocStack(deviceBuffer.get(0), stack);
                    EngineUtils.checkError(vkEnumerateDeviceExtensionProperties(gpu, (CharSequence) null, deviceBuffer, extensions));
                    
                    deviceExt = MemoryStack.stackMallocPointer(deviceBuffer.get(0));
                    for (int i = 0; i < deviceBuffer.get(0); i++)
                    {
                        extensions.position(i);
                        
                        System.out.println(extensions.extensionNameString());
                        deviceExt.put(memASCII(extensions.extensionNameString()));
                        if (extensions.extensionNameString().equals(VK_KHR_SWAPCHAIN_EXTENSION_NAME))
                        {
                            hasSwapchain = true;
                        }
                    }
                    deviceExt.flip();
                }
                System.out.println();
                
                if (!hasSwapchain)
                {
                    throw new RuntimeException("GPU does not support swapchains!");
                }
            } else
            {
                throw new RuntimeException("No physical device avaliable");
            }
            
            vkEnumerateInstanceLayerProperties(deviceBuffer, null);
            VkLayerProperties.Buffer layerProperties = VkLayerProperties.callocStack(deviceBuffer.get(0), stack);
            vkEnumerateInstanceLayerProperties(deviceBuffer, layerProperties);
            
            System.out.println("Instance Layers:");
            for (int i = 0; i < layerProperties.capacity(); i++)
            {
                System.out.format("%-40s \t | \t %s%n", layerProperties.get(i).layerNameString(), layerProperties.get(i).descriptionString());
            }
            System.out.println();
            
            vkEnumerateDeviceLayerProperties(gpu, deviceBuffer, null);
            layerProperties = VkLayerProperties.callocStack(deviceBuffer.get(0), stack);
            vkEnumerateDeviceLayerProperties(gpu, deviceBuffer, layerProperties);
            
            System.out.println("Device Layers:");
            for (int i = 0; i < layerProperties.capacity(); i++)
            {
                System.out.format("%-40s \t | \t %s%n", layerProperties.get(i).layerNameString(), layerProperties.get(i).descriptionString());
            }
            System.out.println();
        }
    }
    
    private void createVkDevice()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.callocStack(1, stack)
                                                                                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                                                                                    .queueFamilyIndex(graphicsQueueIndex)
                                                                                    .pQueuePriorities(stack.floats(1));
            
            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.callocStack(stack);
            if (gpuFeatures.shaderClipDistance())
            {
                features.shaderClipDistance(true);
            }
            
            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.callocStack(stack)
                                                                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                                                                    .pQueueCreateInfos(queueCreateInfo)
                                                                    .ppEnabledExtensionNames(deviceExt)
                                                                    .pEnabledFeatures(features);
            
            PointerBuffer deviceBuffer = stack.callocPointer(1);
            
            EngineUtils.checkError(vkCreateDevice(gpu, deviceCreateInfo, null, deviceBuffer));
            device = new VkDevice(deviceBuffer.get(0), gpu, deviceCreateInfo);
            
            vkGetDeviceQueue(device, graphicsQueueIndex, 0, deviceBuffer);
            queue = new VkQueue(deviceBuffer.get(0), device);
        }
    }
    
    private void createVkInstance()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            
            VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack)
                                                         .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                                                         .apiVersion(VK_API_VERSION)
                                                         .applicationVersion(VK_APPLICATION_VERSION)
                                                         .engineVersion(VK_APPLICATION_VERSION)
                                                         .pApplicationName(memASCII("Fix title on line 319"))
                                                         .pEngineName(memASCII("No Engine"));
            
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.callocStack(stack)
                                                                  .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                                                                  .pApplicationInfo(appInfo)
                                                                  .ppEnabledExtensionNames(instanceExt)
                                                                  .ppEnabledLayerNames(instanceLayers);
            
            PointerBuffer instanceBuffer = stack.callocPointer(1);
            EngineUtils.checkError(vkCreateInstance(createInfo, null, instanceBuffer));
            instance = new VkInstance(instanceBuffer.get(0), createInfo);
        }
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
                shouldClose = window.shouldClose();
                if (shouldClose)
                {
                    game.delete();
                    return;
                }
            }
        }
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
    
    public Window getWindow()
    {
        return window;
    }
    
    public int getGraphicsQueueIndex()
    {
        return graphicsQueueIndex;
    }
    
    public int getPresentQueueIndex()
    {
        return presentQueueIndex;
    }
}
