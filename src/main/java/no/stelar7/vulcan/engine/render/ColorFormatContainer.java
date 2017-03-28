package no.stelar7.vulcan.engine.render;

import org.lwjgl.vulkan.*;

public class ColorFormatContainer
{
    
    private int format;
    private int colorSpace;
    
    public ColorFormatContainer(VkSurfaceFormatKHR surface)
    {
        format = surface.format();
        colorSpace = surface.colorSpace();
    }
    
    public int getFormat()
    {
        return format;
    }
    
    public void setFormat(int format)
    {
        this.format = format;
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
