package no.stelar7.vulcan.engine;


import org.lwjgl.*;
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
    
    private PointerBuffer pBuff = BufferUtils.createPointerBuffer(1);
    private IntBuffer     iBuff = BufferUtils.createIntBuffer(1);
    private LongBuffer    lBuff = BufferUtils.createLongBuffer(1);
    
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
    
    private VkPhysicalDeviceProperties       gpuProperties = VkPhysicalDeviceProperties.create();
    private VkPhysicalDeviceFeatures         gpuFeatures   = VkPhysicalDeviceFeatures.create();
    private VkPhysicalDeviceMemoryProperties gpuMemory     = VkPhysicalDeviceMemoryProperties.create();
    
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
                window.destroy();
                vkDestroyDevice(device, null);
                
                vkDestroyDebugReportCallbackEXT(instance, debugCallbackHandle, null);
                vkDestroyInstance(instance, null);
            }
        } finally
        {
            glfwTerminate();
        }
    }
    
    
    private void initGLFW()
    {
        if (!glfwInit())
        {
            throw new RuntimeException("Failed to initGLFW GLFW");
        }
        
        if (!glfwVulkanSupported())
        {
            throw new RuntimeException("Vulkan not supported");
        }
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
        
        
        EngineUtils.checkError(vkCreateDebugReportCallbackEXT(instance, debugCreateInfo, null, lBuff));
        
        debugCallbackHandle = lBuff.get(0);
        
    }
    
    
    private void getDeviceQueueFamilyPropertyIndecies()
    {
        vkGetPhysicalDeviceQueueFamilyProperties(gpu, iBuff, null);
        int queueFamilies = iBuff.get(0);
        
        if (queueFamilies > 0)
        {
            VkQueueFamilyProperties.Buffer props = VkQueueFamilyProperties.create(queueFamilies);
            vkGetPhysicalDeviceQueueFamilyProperties(gpu, iBuff, props);
            
            for (int i = 0; i < queueFamilies; i++)
            {
                EngineUtils.checkError(KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(gpu, i, window.getSurfaceHandle(), iBuff));
                boolean hasPresent = iBuff.get(0) == VK_TRUE;
                
                if ((props.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) == VK_QUEUE_GRAPHICS_BIT)
                {
                    if (graphicsQueueIndex == -1)
                    {
                        graphicsQueueIndex = i;
                    }
                    
                    if (hasPresent)
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
                    EngineUtils.checkError(KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(gpu, i, window.getSurfaceHandle(), iBuff));
                    boolean hasPresent = iBuff.get(0) == VK_TRUE;
                    if (hasPresent)
                    {
                        presentQueueIndex = i;
                    }
                }
            }
        }
        
        if (presentQueueIndex == -1 || graphicsQueueIndex == -1)
        {
            throw new RuntimeException("No Queue Supporting Graphics And Presentation Found!");
        }
        
        if (graphicsQueueIndex != presentQueueIndex)
        {
            throw new RuntimeException("Could not find a common graphics and a present queue");
        }
    }
    
    private void createPhysicalDevice()
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
            
            
            EngineUtils.checkError(vkEnumerateDeviceExtensionProperties(gpu, (CharSequence) null, iBuff, null));
            boolean hasSwapchain = false;
            
            System.out.println("Supported GPU Extensions:");
            
            if (iBuff.get(0) > 0)
            {
                VkExtensionProperties.Buffer extensions = VkExtensionProperties.create(iBuff.get(0));
                EngineUtils.checkError(vkEnumerateDeviceExtensionProperties(gpu, (CharSequence) null, iBuff, extensions));
                
                deviceExt = BufferUtils.createPointerBuffer(iBuff.get(0));
                
                for (int i = 0; i < iBuff.get(0); i++)
                {
                    System.out.println(extensions.get(i).extensionNameString());
                    
                    if (extensions.get(i).extensionNameString().equals(VK_KHR_SWAPCHAIN_EXTENSION_NAME))
                    {
                        hasSwapchain = true;
                    }
                    deviceExt.put(memASCII(extensions.get(i).extensionNameString()));
                }
            }
            System.out.println();
            
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
    }
    
    private void createVkDevice()
    {
        FloatBuffer priority = BufferUtils.createFloatBuffer(1).put(0, 0);
        
        VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.create(1)
                                                                                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                                                                                .queueFamilyIndex(graphicsQueueIndex)
                                                                                .pQueuePriorities(priority);
        
        VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.create();
        if (gpuFeatures.shaderClipDistance())
        {
            features.shaderClipDistance(true);
        }
        
        VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.create()
                                                                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                                                                .pQueueCreateInfos(queueCreateInfo)
                                                                .ppEnabledExtensionNames(deviceExt)
                                                                .pEnabledFeatures(features);
        
        EngineUtils.checkError(vkCreateDevice(gpu, deviceCreateInfo, null, pBuff));
        
        device = new VkDevice(pBuff.get(0), gpu, deviceCreateInfo);
        
        vkGetDeviceQueue(device, graphicsQueueIndex, 0, pBuff);
        queue = new VkQueue(pBuff.get(0), device);
    }
    
    private void createVkInstance()
    {
        
        
        VkApplicationInfo appInfo = VkApplicationInfo.create()
                                                     .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                                                     .apiVersion(VK_API_VERSION)
                                                     .applicationVersion(VK_APPLICATION_VERSION)
                                                     .engineVersion(VK_APPLICATION_VERSION)
                                                     .pApplicationName(memASCII("Fix title on line 319"))
                                                     .pEngineName(memASCII("No Engine"));
        
        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.create()
                                                              .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                                                              .pApplicationInfo(appInfo)
                                                              .ppEnabledExtensionNames(instanceExt)
                                                              .ppEnabledLayerNames(instanceLayers);
        
        
        EngineUtils.checkError(vkCreateInstance(createInfo, null, pBuff));
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
