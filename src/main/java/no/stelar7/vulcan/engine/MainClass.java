package no.stelar7.vulcan.engine;

public class MainClass
{
    public static void main(String[] args)
    {
        VulkanRenderer renderer = new VulkanRenderer();
        Game           game     = new TestGame(renderer);
        
        renderer.openWindow(800, 600, "Vulkan Test");
        renderer.useGame(game);
        
        renderer.start();
    }
    
}
