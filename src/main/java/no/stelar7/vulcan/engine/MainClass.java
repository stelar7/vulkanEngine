package no.stelar7.vulcan.engine;

import no.stelar7.vulcan.engine.game.*;
import no.stelar7.vulcan.engine.render.VulkanRenderer;

public class MainClass
{
    public static void main(String[] args)
    {
        VulkanRenderer renderer = new VulkanRenderer(800, 600, "Vulkan Test");
        // VulkanRenderer renderer = new VulkanRenderer(16384, 16384, "Vulkan Test");
        Game game = new TestGame(renderer);
        
        renderer.useGame(game);
        
        renderer.start();
    }
    
}
