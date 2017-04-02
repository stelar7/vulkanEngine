package no.stelar7.vulkan.engine.renderer;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public class DeviceFamily
{
    private VkDevice                         device;
    private int                              queueFamily;
    private VkPhysicalDeviceMemoryProperties memoryProperties;
    
    public void free()
    {
        memoryProperties.free();
        vkDestroyDevice(device, null);
    }
    
    public VkDevice getDevice()
    {
        return device;
    }
    
    public void setDevice(VkDevice device)
    {
        this.device = device;
    }
    
    public int getQueueFamily()
    {
        return queueFamily;
    }
    
    public void setQueueFamily(int queueFamily)
    {
        this.queueFamily = queueFamily;
    }
    
    public VkPhysicalDeviceMemoryProperties getMemoryProperties()
    {
        return memoryProperties;
    }
    
    public void setMemoryProperties(VkPhysicalDeviceMemoryProperties memoryProperties)
    {
        this.memoryProperties = memoryProperties;
    }
}
