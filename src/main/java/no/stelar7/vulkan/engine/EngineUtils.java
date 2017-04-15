package no.stelar7.vulkan.engine;

import org.lwjgl.*;
import org.lwjgl.vulkan.*;

import java.io.*;
import java.net.URL;
import java.nio.*;
import java.nio.channels.*;
import java.util.Locale;

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
                return "The device does not match any other available types (VK_PHYSICAL_DEVICE_TYPE_OTHER)";
            case 1:
                return "The device is typically one embedded in or tightly coupled with the host (VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU)";
            case 2:
                return "The device is typically a separate processor connected to the host via an interlink (VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU)";
            case 3:
                return "The device is typically a virtual node in a virtualization environment (VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU)";
            case 4:
                return "The device is typically running on the same processors as the host (VK_PHYSICAL_DEVICE_TYPE_CPU)";
            
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
        
        // We get 2 bits to many, so we need to shift them out
        int minor = ((i >>> 12) & 0x3ff) >>> 2;
        
        
        return String.format("%d.%d.%d", major, minor, patch);
    }
    
    public static boolean hasFlag(int value, int flag, String... print)
    {
        boolean has = (value & flag) == flag;
        
        if (has)
        {
            for (String data : print)
            {
                System.out.println(data);
            }
        }
        
        return has;
    }
    
    
    public static String vkDebugFlagToString(int flags)
    {
        
        if (hasFlag(flags, VK_DEBUG_REPORT_INFORMATION_BIT_EXT))
        {
            return "INFO";
        }
        if (hasFlag(flags, VK_DEBUG_REPORT_WARNING_BIT_EXT))
        {
            return "WARNING";
        }
        if (hasFlag(flags, VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT))
        {
            return "PERFORMANCE";
        }
        if (hasFlag(flags, VK_DEBUG_REPORT_ERROR_BIT_EXT))
        {
            return "ERROR";
        }
        if (hasFlag(flags, VK_DEBUG_REPORT_DEBUG_BIT_EXT))
        {
            return "DEBUG";
        }
        
        return "Unknown";
        
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
    
    public static String vkFormatToString(int format)
    {
        if (format == 44)
        {
            return "VK_FORMAT_B8G8R8A8_UNORM";
        }
        return "UNKNOWN " + format;
        
    }
    
    public static String vkColorSpaceToString(int colorspace)
    {
        if (colorspace == 0)
        {
            return "VK_COLOR_SPACE_SRGB_NONLINEAR_KHR";
        }
        return "UNKNOWN " + colorspace;
    }
    
    public static ByteBuffer resourceToByteBuffer(String resource)
    {
        ByteBuffer buffer;
        URL        url  = Thread.currentThread().getContextClassLoader().getResource(resource);
        File       file = new File(url.getFile());
        if (file.isFile())
        {
            try (FileInputStream fis = new FileInputStream(file); FileChannel fc = fis.getChannel())
            {
                buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                return buffer;
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        } else
        {
            buffer = BufferUtils.createByteBuffer(1024);
            try (InputStream source = url.openStream(); ReadableByteChannel rbc = Channels.newChannel(source))
            {
                while (rbc.read(buffer) != -1)
                {
                    if (buffer.remaining() == 0)
                    {
                        buffer = resizeBuffer(buffer, buffer.capacity() * 2);
                    }
                }
                buffer.flip();
                return buffer;
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return null;
    }
    
    private static ByteBuffer resizeBuffer(ByteBuffer buffer, int newCapacity)
    {
        ByteBuffer newBuffer = BufferUtils.createByteBuffer(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }
    
    public static void printBuffer(FloatBuffer data)
    {
        StringBuilder result = new StringBuilder("(");
        
        data.mark();
        
        while (data.remaining() > 0)
        {
            result.append(data.get()).append(", ");
        }
        
        result.reverse().deleteCharAt(0).deleteCharAt(0).reverse().append(")");
        data.reset();
        
        System.out.println(result);
    }
    
    public static void printBuffer(IntBuffer data)
    {
        StringBuilder result = new StringBuilder("(");
        
        data.mark();
        
        while (data.remaining() > 0)
        {
            result.append(data.get()).append(", ");
        }
        
        result.reverse().deleteCharAt(0).deleteCharAt(0).reverse().append(")");
        data.reset();
        
        System.out.println(result);
    }
    
    public static void printBuffer(ByteBuffer data)
    {
        StringBuilder result = new StringBuilder();
        
        data.mark();
        
        while (data.remaining() > 0)
        {
            String hex = Integer.toHexString(Byte.toUnsignedInt(data.get())).toUpperCase(Locale.ENGLISH);
            if (hex.length() != 2)
            {
                hex = "0" + hex;
            }
            result.append(hex).append(" ");
        }
        
        data.reset();
        
        result.reverse().deleteCharAt(0).reverse();
        
        System.out.println(result);
    }
}
