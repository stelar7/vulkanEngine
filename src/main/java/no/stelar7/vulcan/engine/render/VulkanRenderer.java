package no.stelar7.vulcan.engine.render;


import no.stelar7.vulcan.engine.EngineUtils;
import no.stelar7.vulcan.engine.game.Game;
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
    private Game   game;
    private Window window;
    
    private final Object lock = new Object();
    private boolean shouldClose;
    
    private long debugCallbackHandle = VK_NULL_HANDLE;
    
    
    private static final int VK_API_VERSION         = VK_MAKE_VERSION(1, 0, 3);
    private static final int VK_APPLICATION_VERSION = VK_MAKE_VERSION(0, 1, 0);
    
    private VkPhysicalDeviceProperties       gpuProperties = VkPhysicalDeviceProperties.malloc();
    private VkPhysicalDeviceFeatures         gpuFeatures   = VkPhysicalDeviceFeatures.malloc();
    private VkPhysicalDeviceMemoryProperties gpuMemory     = VkPhysicalDeviceMemoryProperties.malloc();
    
    private VkInstance       instance;
    private VkDevice         device;
    private VkPhysicalDevice physicalDevice;
    private VkQueue          queue;
    
    private int presentQueueIndex  = -1;
    private int graphicsQueueIndex = -1;
    
    
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
        return physicalDevice;
    }
    
    public VkQueue getRenderQueue()
    {
        return queue;
    }
    
    public VulkanRenderer(int width, int height, String title)
    {
        initGLFW();
        initVulkan(width, height, title);
    }
    
    private void initVulkan(int width, int height, String title)
    {
        
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            
            PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
            if (requiredExtensions == null)
            {
                throw new RuntimeException("Failed to get required extensions");
            }
            
            // Setup validation
            PointerBuffer validationLayers = stack.mallocPointer(1);
            PointerBuffer deviceExt        = stack.mallocPointer(1);
            PointerBuffer instanceExt      = stack.mallocPointer(requiredExtensions.remaining() + 1);
            validationLayers.put(memASCII("VK_LAYER_LUNARG_standard_validation"));
            validationLayers.flip();
            deviceExt.put(memASCII("VK_KHR_swapchain"));
            deviceExt.flip();
            instanceExt.put(requiredExtensions);
            instanceExt.put(memASCII("VK_EXT_debug_report"));
            instanceExt.flip();
            
            // Create instance
            getInstancecLayerProperties();
            createVkInstance(instanceExt, validationLayers);
            
            // Create debug
            int debugFlags = VK_DEBUG_REPORT_ERROR_BIT_EXT | VK_DEBUG_REPORT_WARNING_BIT_EXT | VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT;
            createVkDebug(debugFlags);
            
            // Create physical device
            createPhysicalDevice(instance);
            getPhysicalDeviceProperties(physicalDevice);
            getPhysicalDeviceMemoryProperties(physicalDevice);
            
            if (!supportsSwapchain(physicalDevice))
            {
                throw new RuntimeException("Physical device does not support swapchains");
            }
            
            
            // Create surface
            createWindow(title, width, height);
            window.createSurface(instance);
            
            // Setup queue
            getQueueFamily(physicalDevice);
            
            // (presentQueueIndex == graphicsQueueIndex)
            if (graphicsQueueIndex == -1)
            {
                throw new RuntimeException("Physical device does not support present queue");
            }
            
            getDeviceLayerProperties(physicalDevice);
            device = createDevice(physicalDevice, graphicsQueueIndex, validationLayers, deviceExt);
            queue = getQueue(device, graphicsQueueIndex, 0);
            
            window.getSurfaceFormat(physicalDevice);
            window.createSwapchain(physicalDevice, device);
            window.createSwapchainImageViews(device);
            window.createCommandpool(device, presentQueueIndex);
            window.createCommandBuffers(device);
            window.createDepthImage(device, gpuMemory, queue);
            window.createDescriptorSetPool(device);
            window.createRenderPass(device);
            window.createFramebuffers(device);
            window.createShaders(device);
            window.createPipelineLayout(device);
            window.createDescriptorSet(device);
            window.createPipeline(device, gpuProperties.limits());
            window.createSemaphores(device);
        }
    }
    
    
    private VkQueue getQueue(VkDevice device, int queueFamilyIndex, int index)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            PointerBuffer pointer = stack.mallocPointer(1);
            vkGetDeviceQueue(device, queueFamilyIndex, index, pointer);
            queue = new VkQueue(pointer.get(0), device);
            
            return queue;
        }
    }
    
    private VkDevice createDevice(VkPhysicalDevice physicalDevice, int queueFamiliyIndex, PointerBuffer validationLayers, PointerBuffer deviceExt)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            vkGetPhysicalDeviceFeatures(physicalDevice, gpuFeatures);
            
            return createDevice(physicalDevice, gpuFeatures, queueFamiliyIndex, validationLayers, deviceExt);
        }
    }
    
    private VkDevice createDevice(VkPhysicalDevice physicalDevice, VkPhysicalDeviceFeatures features, int graphicsQueueIndex, PointerBuffer validationLayers, PointerBuffer deviceExt)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.mallocStack(1, stack)
                                                                                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                                                                                    .pNext(VK_NULL_HANDLE)
                                                                                    .flags(0)
                                                                                    .queueFamilyIndex(presentQueueIndex)
                                                                                    .pQueuePriorities(stack.floats(1));
            
            
            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.mallocStack(stack)
                                                                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                                                                    .pNext(VK_NULL_HANDLE)
                                                                    .flags(0)
                                                                    .pQueueCreateInfos(queueCreateInfo)
                                                                    .ppEnabledLayerNames(validationLayers)
                                                                    .ppEnabledExtensionNames(deviceExt)
                                                                    .pEnabledFeatures(features);
            
            PointerBuffer deviceBuffer = stack.mallocPointer(1);
            
            EngineUtils.checkError(vkCreateDevice(physicalDevice, deviceCreateInfo, null, deviceBuffer));
            device = new VkDevice(deviceBuffer.get(0), physicalDevice, deviceCreateInfo);
            
            return device;
        }
    }
    
    private void getQueueFamily(VkPhysicalDevice physicalDevice)
    {
        VkQueueFamilyProperties.Buffer families = getQueueFamilyProperties(physicalDevice);
        
        for (int i = 0; i < families.remaining(); i++)
        {
            families.position(i);
            if (EngineUtils.hasFlag(families.queueFlags(), VK_QUEUE_GRAPHICS_BIT))
            {
                if (hasPresentationSupport(physicalDevice, i))
                {
                    presentQueueIndex = graphicsQueueIndex = i;
                    break;
                }
            }
        }
    }
    
    
    private boolean hasPresentationSupport(VkPhysicalDevice physicalDevice, int index)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer surfaceSupports = stack.mallocInt(1);
            EngineUtils.checkError(KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, index, window.getSurfaceHandle(), surfaceSupports));
            
            return surfaceSupports.get(0) == VK_TRUE;
        }
    }
    
    private VkQueueFamilyProperties.Buffer getQueueFamilyProperties(VkPhysicalDevice physicalDevice)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer familyCount = stack.mallocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, familyCount, null);
            
            
            VkQueueFamilyProperties.Buffer queueFamily = VkQueueFamilyProperties.mallocStack(familyCount.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, familyCount, queueFamily);
            
            return queueFamily;
        }
    }
    
    private VkPhysicalDeviceMemoryProperties getPhysicalDeviceMemoryProperties(VkPhysicalDevice physicalDevice)
    {
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, gpuMemory);
        
        System.out.println("Memory Information:");
        System.out.println("Device Memory: " + gpuMemory.memoryHeaps(0).size() / 1_000_000_000f + "GB");
        System.out.println("Host Memory: " + gpuMemory.memoryHeaps(1).size() / 1_000_000_000f + "GB");
        System.out.println();
        
        return gpuMemory;
    }
    
    private PointerBuffer getInstancecLayerProperties()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer propertyCount = stack.mallocInt(1);
            vkEnumerateInstanceLayerProperties(propertyCount, null);
            VkLayerProperties.Buffer layerProperties = VkLayerProperties.mallocStack(propertyCount.get(0), stack);
            vkEnumerateInstanceLayerProperties(propertyCount, layerProperties);
            
            PointerBuffer pointers = stack.mallocPointer(layerProperties.capacity());
            System.out.println("Instance Layers:");
            for (int i = 0; i < layerProperties.capacity(); i++)
            {
                VkLayerProperties currentLayer = layerProperties.get(i);
                pointers.put(i, currentLayer);
                System.out.format("%-40s \t | \t %s%n", currentLayer.layerNameString(), currentLayer.descriptionString());
            }
            System.out.println();
            
            return pointers;
        }
    }
    
    private PointerBuffer getDeviceLayerProperties(VkPhysicalDevice physicalDevice)
    {
        
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer propertyCount = stack.mallocInt(1);
            vkEnumerateDeviceLayerProperties(physicalDevice, propertyCount, null);
            VkLayerProperties.Buffer layerProperties = VkLayerProperties.mallocStack(propertyCount.get(0), stack);
            vkEnumerateDeviceLayerProperties(physicalDevice, propertyCount, layerProperties);
            
            PointerBuffer pointers = stack.mallocPointer(layerProperties.capacity());
            System.out.println("Device Layers:");
            for (int i = 0; i < layerProperties.capacity(); i++)
            {
                VkLayerProperties currentLayer = layerProperties.get(i);
                pointers.put(i, currentLayer);
                System.out.format("%-40s \t | \t %s%n", currentLayer.layerNameString(), currentLayer.descriptionString());
            }
            System.out.println();
            
            return pointers;
        }
    }
    
    private boolean supportsSwapchain(VkPhysicalDevice physicalDevice)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer extensionCount = stack.mallocInt(1);
            EngineUtils.checkError(vkEnumerateDeviceExtensionProperties(physicalDevice, (CharSequence) null, extensionCount, null));
            boolean hasSwapchain = false;
            
            System.out.println("Supported GPU Extensions:");
            
            if (extensionCount.get(0) > 0)
            {
                VkExtensionProperties.Buffer extensions = VkExtensionProperties.mallocStack(extensionCount.get(0), stack);
                EngineUtils.checkError(vkEnumerateDeviceExtensionProperties(physicalDevice, (CharSequence) null, extensionCount, extensions));
                
                for (int i = 0; i < extensionCount.get(0); i++)
                {
                    extensions.position(i);
                    System.out.println(extensions.extensionNameString());
                    
                    if (extensions.extensionNameString().equals(VK_KHR_SWAPCHAIN_EXTENSION_NAME))
                    {
                        hasSwapchain = true;
                    }
                }
            }
            System.out.println();
            
            return hasSwapchain;
        }
    }
    
    private VkPhysicalDeviceProperties getPhysicalDeviceProperties(VkPhysicalDevice physicalDevice)
    {
        vkGetPhysicalDeviceProperties(physicalDevice, gpuProperties);
        
        System.out.println("System Information:");
        System.out.println("Device Type: " + EngineUtils.vkPhysicalDeviceToString(gpuProperties.deviceType()));
        System.out.println("Vulkan API version: " + EngineUtils.vkVersionToString(gpuProperties.apiVersion()));
        System.out.println("Driver version: " + EngineUtils.vkVersionToString(gpuProperties.driverVersion()));
        System.out.println("Vendor ID: " + gpuProperties.vendorID());
        System.out.println("Device ID: " + gpuProperties.deviceID());
        System.out.println("Device Name: " + gpuProperties.deviceNameString());
        System.out.println();
        
        return gpuProperties;
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
                gpuProperties.free();
                gpuFeatures.free();
                gpuMemory.free();
                
                window.destroy(instance, device);
                game.delete();
                
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
            throw new RuntimeException("Vulkan not supported! (Make sure you are using a GTX600/Radeon HD77xx or newer, and have up-to-date drivers)");
        }
        
    }
    
    public long createMemory(long buffer, int requiredFlags)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkMemoryRequirements memoryRequirements = VkMemoryRequirements.mallocStack(stack);
            vkGetBufferMemoryRequirements(device, buffer, memoryRequirements);
            
            int index = EngineUtils.findMemoryTypeIndex(gpuMemory, memoryRequirements, requiredFlags);
            
            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.mallocStack(stack)
                                                                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                                                                    .pNext(VK_NULL_HANDLE)
                                                                    .allocationSize(memoryRequirements.size())
                                                                    .memoryTypeIndex(index);
            
            LongBuffer handleHolder = stack.mallocLong(1);
            EngineUtils.checkError(vkAllocateMemory(device, allocateInfo, null, handleHolder));
            
            long memory = handleHolder.get(0);
            EngineUtils.checkError(vkBindBufferMemory(device, buffer, memory, 0));
            
            return memory;
        }
    }
    
    public long createBuffer(int usage, long size)
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
    
    
    public void copyBuffer(long srcBuffer, long dstBuffer, long size)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.mallocStack(stack)
                                                                                  .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                                                                                  .pNext(VK_NULL_HANDLE)
                                                                                  .commandPool(getWindow().getCommandPoolHandle())
                                                                                  .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                                                                                  .commandBufferCount(1);
            PointerBuffer pointerBuffer = stack.callocPointer(1);
            vkAllocateCommandBuffers(device, allocateInfo, pointerBuffer);
            
            VkCommandBuffer buffer = new VkCommandBuffer(pointerBuffer.get(0), device);
            
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.mallocStack(stack)
                                                                         .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                                                                         .pNext(VK_NULL_HANDLE)
                                                                         .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
                                                                         .pInheritanceInfo(null);
            
            EngineUtils.checkError(vkBeginCommandBuffer(buffer, beginInfo));
            
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.mallocStack(1, stack)
                                                         .srcOffset(0)
                                                         .dstOffset(0)
                                                         .size(size);
            
            vkCmdCopyBuffer(buffer, srcBuffer, dstBuffer, copyRegion);
            EngineUtils.checkError(vkEndCommandBuffer(buffer));
            
            
            VkSubmitInfo submitInfo = VkSubmitInfo.mallocStack(stack)
                                                  .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                                                  .pNext(VK_NULL_HANDLE)
                                                  .waitSemaphoreCount(0)
                                                  .pWaitSemaphores(null)
                                                  .pWaitDstStageMask(null)
                                                  .pCommandBuffers(stack.pointers(buffer))
                                                  .pSignalSemaphores(null);
            
            EngineUtils.checkError(vkQueueSubmit(getRenderQueue(), submitInfo, VK_NULL_HANDLE));
            EngineUtils.checkError(vkQueueWaitIdle(getRenderQueue()));
        }
    }
    
    public void destroyMemory(long memory)
    {
        vkFreeMemory(device, memory, null);
    }
    
    public void destroyBuffer(long buffer)
    {
        vkDestroyBuffer(device, buffer, null);
    }
    
    private void createVkDebug(int debugFlags)
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
            
            
            VkDebugReportCallbackCreateInfoEXT debugCreateInfo = VkDebugReportCallbackCreateInfoEXT.mallocStack(stack)
                                                                                                   .sType(VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT)
                                                                                                   .pNext(VK_NULL_HANDLE)
                                                                                                   .flags(debugFlags)
                                                                                                   .pfnCallback(debugCallback)
                                                                                                   .pUserData(VK_NULL_HANDLE);
            
            LongBuffer handleContainer = stack.mallocLong(1);
            EngineUtils.checkError(vkCreateDebugReportCallbackEXT(instance, debugCreateInfo, null, handleContainer));
            
            debugCallbackHandle = handleContainer.get(0);
        }
    }
    
    
    private void createPhysicalDevice(VkInstance instance)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer deviceBuffer = stack.mallocInt(1);
            vkEnumeratePhysicalDevices(instance, deviceBuffer, null);
            if (deviceBuffer.get(0) > 0)
            {
                PointerBuffer gpuHandles = stack.mallocPointer(deviceBuffer.get(0));
                vkEnumeratePhysicalDevices(instance, deviceBuffer, gpuHandles);
                
                
                // Only use the first GPU
                physicalDevice = new VkPhysicalDevice(gpuHandles.get(0), instance);
            } else
            {
                throw new RuntimeException("No physical device avaliable");
            }
        }
    }
    
    private void createVkInstance(PointerBuffer instanceExt, PointerBuffer validationLayers)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            
            VkApplicationInfo appInfo = VkApplicationInfo.mallocStack(stack)
                                                         .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                                                         .pNext(VK_NULL_HANDLE)
                                                         .pApplicationName(memASCII("Fix title on line 563"))
                                                         .applicationVersion(VK_APPLICATION_VERSION)
                                                         .pEngineName(memASCII("No Engine"))
                                                         .engineVersion(VK_APPLICATION_VERSION)
                                                         .apiVersion(VK_API_VERSION);
            
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.mallocStack(stack)
                                                                  .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                                                                  .pNext(VK_NULL_HANDLE)
                                                                  .flags(0)
                                                                  .pApplicationInfo(appInfo)
                                                                  .ppEnabledExtensionNames(instanceExt)
                                                                  .ppEnabledLayerNames(validationLayers);
            
            PointerBuffer instanceBuffer = stack.mallocPointer(1);
            EngineUtils.checkError(vkCreateInstance(createInfo, null, instanceBuffer));
            instance = new VkInstance(instanceBuffer.get(0), createInfo);
        }
    }
    
    private void createWindow(String title, int width, int height)
    {
        window = new Window(title, width, height);
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
