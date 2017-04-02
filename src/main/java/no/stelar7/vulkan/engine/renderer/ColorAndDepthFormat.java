package no.stelar7.vulkan.engine.renderer;

public class ColorAndDepthFormat
{
    private int colorFormat;
    private int colorSpace;
    private int depthFormat;
    
    public int getColorSpace()
    {
        return colorSpace;
    }
    
    public void setColorSpace(int colorSpace)
    {
        this.colorSpace = colorSpace;
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
    
    
}
