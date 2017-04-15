package no.stelar7.vulkan.engine.buffer;

import org.lwjgl.vulkan.*;

public class StagedBuffer
{
    private Buffer  hostBuffer;
    private Buffer  deviceBuffer;
    private boolean dirty;
    
    public StagedBuffer(Buffer staged, Buffer used)
    {
        this.hostBuffer = staged;
        this.deviceBuffer = used;
    }
    
    /**
     * Sets the host-local buffer
     */
    public Buffer getHostBuffer()
    {
        return hostBuffer;
    }
    
    /**
     * Sets the host-local buffer
     */
    public void setHostBuffer(Buffer hostBuffer)
    {
        this.hostBuffer = hostBuffer;
    }
    
    
    /**
     * Gets the device-local buffer
     */
    public Buffer getDeviceBuffer()
    {
        return deviceBuffer;
    }
    
    /**
     * Sets the device-local buffer
     */
    public void setDeviceBuffer(Buffer deviceBuffer)
    {
        this.deviceBuffer = deviceBuffer;
    }
    
    public void free(VkDevice device)
    {
        hostBuffer.free(device);
        deviceBuffer.free(device);
    }
    
    public boolean isDirty()
    {
        return dirty;
    }
    
    public void setDirty(boolean dirty)
    {
        this.dirty = dirty;
    }
}
