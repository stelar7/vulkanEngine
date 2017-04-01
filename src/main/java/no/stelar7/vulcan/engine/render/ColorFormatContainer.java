package no.stelar7.vulcan.engine.render;

import org.lwjgl.vulkan.*;

public class ColorFormatContainer
{
    
    private int colorFormat;
    private int colorSpace;
    private int depthFormat;
    
    public void set(VkSurfaceFormatKHR surface)
    {
        colorFormat = surface.format();
        colorSpace = surface.colorSpace();
    }
    
    public int getDepthFormat()
    {
        return depthFormat;
    }
    
    public void setDepthFormat(int depthFormat)
    {
        this.depthFormat = depthFormat;
    }
    
    public int getColorFormat()
    {
        return colorFormat;
    }
    
    public void setColorFormat(int colorFormat)
    {
        this.colorFormat = colorFormat;
    }
    
    public void setColorSpace(int colorSpace)
    {
        this.colorSpace = colorSpace;
    }
    
    public int getColorSpace()
    {
        
        return colorSpace;
    }
    
}
