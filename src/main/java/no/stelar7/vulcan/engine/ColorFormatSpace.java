package no.stelar7.vulcan.engine;

import org.lwjgl.vulkan.*;

public class ColorFormatSpace
{
    private int colorFormat;
    private int colorSpace;
    
    public int getColorFormat()
    {
        return colorFormat;
    }
    
    public int getColorSpace()
    {
        return colorSpace;
    }
    
    public void setColorSpace(int colorSpace)
    {
        this.colorSpace = colorSpace;
    }
    
    public void setColorFormat(int colorFormat)
    {
        this.colorFormat = colorFormat;
    }
    
    public void set(VkSurfaceFormatKHR currentFormat)
    {
        setColorFormat(currentFormat.format());
        setColorSpace(currentFormat.colorSpace());
    }
    
    public void set(int format, int space)
    {
        setColorFormat(format);
        setColorSpace(space);
    }
}
