package no.stelar7.vulcan.engine;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.EXTDebugReport.*;
import static org.lwjgl.vulkan.KHRDisplaySwapchain.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public final class EngineUtils
{
    
    
    private EngineUtils()
    {
        // Hide public constructor
    }
    
    public static String vkPhysicalDeviceToString(int result)
    {
        switch (result)
        {
            case 0:
                return "VK_PHYSICAL_DEVICE_TYPE_OTHER";
            case 1:
                return "VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU";
            case 2:
                return "VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU";
            case 3:
                return "VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU";
            case 4:
                return "VK_PHYSICAL_DEVICE_TYPE_CPU";
            
            default:
                return "Unknown Device Type";
        }
    }
    
    public static String vkErrorToString(int result)
    {
        switch (result)
        {
            case VK_SUCCESS:
                return "Command successfully completed.";
            case VK_NOT_READY:
                return "A fence or query has not yet completed.";
            case VK_TIMEOUT:
                return "A wait operation has not completed in the specified time.";
            case VK_EVENT_SET:
                return "An event is signaled.";
            case VK_EVENT_RESET:
                return "An event is unsignaled.";
            case VK_INCOMPLETE:
                return "A return array was too small for the result.";
            case VK_SUBOPTIMAL_KHR:
                return "A swapchain no longer matches the surface properties exactly, but can still be used to present to the surface successfully.";
            case VK_ERROR_OUT_OF_HOST_MEMORY:
                return "A host memory allocation has failed.";
            case VK_ERROR_OUT_OF_DEVICE_MEMORY:
                return "A device memory allocation has failed.";
            case VK_ERROR_INITIALIZATION_FAILED:
                return "Initialization of an object could not be completed for implementation-specific reasons.";
            case VK_ERROR_DEVICE_LOST:
                return "The logical or physical device has been lost.";
            case VK_ERROR_MEMORY_MAP_FAILED:
                return "Mapping of a memory object has failed.";
            case VK_ERROR_LAYER_NOT_PRESENT:
                return "A requested layer is not present or could not be loaded.";
            case VK_ERROR_EXTENSION_NOT_PRESENT:
                return "A requested extension is not supported.";
            case VK_ERROR_FEATURE_NOT_PRESENT:
                return "A requested feature is not supported.";
            case VK_ERROR_INCOMPATIBLE_DRIVER:
                return "The requested version of Vulkan is not supported by the driver or is otherwise incompatible for implementation-specific reasons.";
            case VK_ERROR_TOO_MANY_OBJECTS:
                return "Too many objects of the type have already been created.";
            case VK_ERROR_FORMAT_NOT_SUPPORTED:
                return "A requested format is not supported on this device.";
            case VK_ERROR_SURFACE_LOST_KHR:
                return "A surface is no longer available.";
            case VK_ERROR_NATIVE_WINDOW_IN_USE_KHR:
                return "The requested window is already connected to a VkSurfaceKHR, or to some other non-Vulkan API.";
            case VK_ERROR_OUT_OF_DATE_KHR:
                return "A surface has changed in such a way that it is no longer compatible with the swapchain, and further presentation requests using the swapchain will fail. " +
                       "Applications must query the new surface properties and recreate their swapchain if they wish to continue presenting to the surface.";
            case VK_ERROR_INCOMPATIBLE_DISPLAY_KHR:
                return "The display used by a swapchain does not use the same presentable image layout, or is incompatible in a way that prevents sharing an image.";
            case VK_ERROR_VALIDATION_FAILED_EXT:
                return "A validation layer found an error.";
            default:
                return String.format("%s [%d]", "Unknown", result);
        }
    }
    
    public static String vkVersionToString(int i)
    {
        
        int major = i >>> 22;
        int patch = i & 0xfff;
        
        // This needs another 2 bits shifted for some reason...
        int minor = ((i >>> 12) & 0x3ff) >>> 2;
        
        
        return String.format("%d.%d.%d", major, minor, patch);
    }
    
    
    public static String vkDebugFlagToString(int flags)
    {
        StringBuilder sb = new StringBuilder();
        if ((flags & VK_DEBUG_REPORT_INFORMATION_BIT_EXT) == VK_DEBUG_REPORT_INFORMATION_BIT_EXT)
        {
            sb.append("INFO");
        }
        if ((flags & VK_DEBUG_REPORT_WARNING_BIT_EXT) == VK_DEBUG_REPORT_WARNING_BIT_EXT)
        {
            sb.append("WARNING");
            
        }
        if ((flags & VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT) == VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT)
        {
            sb.append("PERFORMANCE");
        }
        if ((flags & VK_DEBUG_REPORT_ERROR_BIT_EXT) == VK_DEBUG_REPORT_ERROR_BIT_EXT)
        {
            sb.append("ERROR");
            
        }
        if ((flags & VK_DEBUG_REPORT_DEBUG_BIT_EXT) == VK_DEBUG_REPORT_DEBUG_BIT_EXT)
        {
            sb.append("DEBUG");
        }
        return sb.toString();
    }
    
    public static void checkError(int status)
    {
        if (status != VK_SUCCESS)
        {
            throw new RuntimeException(EngineUtils.vkErrorToString(status));
        }
    }
    
    public static int findMemoryTypeIndex(VkPhysicalDeviceMemoryProperties gpuMemory, VkMemoryRequirements requirements, int requiredProperties)
    {
        for (int i = 0; i < gpuMemory.memoryTypeCount(); i++)
        {
            if ((requirements.memoryTypeBits() & (1 << i)) == (1 << i))
            {
                if ((gpuMemory.memoryTypes(i).propertyFlags() & requiredProperties) == requiredProperties)
                {
                    return i;
                }
            }
        }
        throw new RuntimeException("No memory matching required type found");
    }
    
}
