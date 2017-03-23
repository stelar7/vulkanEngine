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
    
    private long debugCallbackHandle = VK_NULL_HANDLE;
    
    private Window window;
    
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
    
    
    public VulkanRenderer()
    {
        init();
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
        
        
        setupInstance(requiredExtensions);
        createVkInstance();
        createVkDebug();
        createVkDevice();
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
            
            
            EngineUtils.checkError(vkEnumerateDeviceExtensionProperties(gpu, (CharSequence) null, iBuff, null));
            boolean hasSwapchain = false;
            
            if (iBuff.get(0) > 0)
            {
                VkExtensionProperties.Buffer extensions = VkExtensionProperties.create(iBuff.get(0));
                EngineUtils.checkError(vkEnumerateDeviceExtensionProperties(gpu, (CharSequence) null, iBuff, extensions));
                
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
        
        EngineUtils.checkError(vkCreateDevice(gpu, deviceCreateInfo, null, pBuff));
        
        device = new VkDevice(pBuff.get(0), gpu, deviceCreateInfo);
        
        vkGetDeviceQueue(device, getDeviceQueueFamilyPropertyIndex(VK_QUEUE_GRAPHICS_BIT), 0, pBuff);
        queue = new VkQueue(pBuff.get(0), device);
        
    }
    
    private void createVkInstance()
    {
        
        
        VkApplicationInfo appInfo = VkApplicationInfo.create()
                                                     .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                                                     .apiVersion(VK_API_VERSION)
                                                     .applicationVersion(VK_APPLICATION_VERSION);
        // Application name?
        
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
    
    public Window openWindow(int width, int height, String title)
    {
        window = new Window(this, width, height, title);
        return window;
    }
    
    public void useGame(Game game)
    {
        this.game = game;
    }
    
    public Window getWindow()
    {
        return window;
    }
}
