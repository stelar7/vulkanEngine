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
import static org.lwjgl.vulkan.VK10.*;

public class MainClass
{
    private static final int    WIDTH  = 800;
    private static final int    HEIGHT = 600;
    private static final String TITLE  = "Vulcan Game Engine";
    private Game game;
    
    private final Object lock = new Object();
    private long    window;
    private boolean shouldClose;
    
    private boolean debugMode = true;
    
    
    private PointerBuffer pBuff = BufferUtils.createPointerBuffer(1);
    private IntBuffer     iBuff = BufferUtils.createIntBuffer(1);
    private LongBuffer    lBuff = BufferUtils.createLongBuffer(1);
    
    private PointerBuffer instanceExt    = BufferUtils.createPointerBuffer(12);
    private PointerBuffer instanceLayers = BufferUtils.createPointerBuffer(12);
    
    
    private static final int VK_API_VERSION         = VK_MAKE_VERSION(1, 0, 27);
    private static final int VK_APPLICATION_VERSION = VK_MAKE_VERSION(0, 1, 0);
    
    private VkInstance       instance;
    private VkDevice         device;
    private VkPhysicalDevice gpu;
    
    private long debugCallbackHandle;
    
    private VkPhysicalDeviceProperties gpuProperties = VkPhysicalDeviceProperties.create();
    private VkPhysicalDeviceFeatures   gpuFeatures   = VkPhysicalDeviceFeatures.create();
    
    private int graphicsFamilyIndex;
    
    
    public static void main(String[] args)
    {
        new MainClass().run();
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
                vkDestroyDevice(device, null);
                
                vkDestroyDebugReportCallbackEXT(instance, debugCallbackHandle, null);
                vkDestroyInstance(instance, null);
                
                glfwFreeCallbacks(window);
                glfwDestroyWindow(window);
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
        
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        window = glfwCreateWindow(WIDTH, HEIGHT, TITLE, MemoryUtil.NULL, MemoryUtil.NULL);
        
        if (debugMode)
        {
            setupVkDebug();
        }
        
        createVkInstance();
        
        if (debugMode)
        {
            createVkDebug();
        }
        
        createVkDevice();
        
    }
    
    private void setupVkDebug()
    {
        instanceLayers
                .put(memASCII("VK_LAYER_LUNARG_standard_validation"))
                /*.put(memASCII("VK_LAYER_LUNARG_api_dump"))
                .put(memASCII("VK_LAYER_LUNARG_device_limits"))
                .put(memASCII("VK_LAYER_LUNARG_draw_state"))
                .put(memASCII("VK_LAYER_LUNARG_image"))
                .put(memASCII("VK_LAYER_LUNARG_mem_tracker"))
                .put(memASCII("VK_LAYER_LUNARG_object_tracker"))
                .put(memASCII("VK_LAYER_LUNARG_param_checker"))
                .put(memASCII("VK_LAYER_LUNARG_swapchain"))
                .put(memASCII("VK_LAYER_LUNARG_threading"))
                .put(memASCII("VK_LAYER_GOOGLE_unique_objects"))*/
                .flip();
        
        instanceExt.put(memASCII("VK_KHR_surface"))
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
                if ((flags & VK_DEBUG_REPORT_ERROR_BIT_EXT) != VK_DEBUG_REPORT_ERROR_BIT_EXT)
                {
                    System.err.format("ERROR: [%s] Code %d : %s%n", pLayerPrefix, messageCode, memASCII(pMessage));
                }
                
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
        
        
        graphicsFamilyIndex = getDeviceQueueFamilyPropertyIndex(VK_QUEUE_GRAPHICS_BIT);
        
        FloatBuffer priority = BufferUtils.createFloatBuffer(1);
        priority.put(1).flip();
        
        VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.create(1)
                                                                                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                                                                                .queueFamilyIndex(graphicsFamilyIndex)
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
                shouldClose = glfwWindowShouldClose(window);
                
                if (shouldClose)
                {
                    game.delete();
                    return;
                }
                
                glfwSwapBuffers(window);
            }
        }
    }
    
    private void postInit()
    {
        game = new TestGame();
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
