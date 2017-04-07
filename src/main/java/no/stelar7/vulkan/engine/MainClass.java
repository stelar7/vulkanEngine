package no.stelar7.vulkan.engine;

import no.stelar7.vulkan.engine.game.*;
import no.stelar7.vulkan.engine.renderer.VulkanRenderer;

public class MainClass
{
    public static void main(String[] args)
    {
        VulkanRenderer renderer = new VulkanRenderer(800, 600, "Vulkan Test");
        
        Game game = new TestGame(renderer);
        renderer.useGame(game);
        renderer.start();
    }
}
