package no.stelar7.vulkan.engine;

import no.stelar7.vulkan.engine.game.*;
import no.stelar7.vulkan.engine.renderer.VulkanRenderer;

public class MainClass
{
    public static void main(String[] args)
    {
        VulkanRenderer renderer = new VulkanRenderer(800, 600, "Vulkan Test");
        Game           game     = new TestGame(renderer);
        // VulkanRenderer renderer = new VulkanRenderer(16384, 16384, "Vulkan Test");
        
        renderer.useGame(game);
        renderer.start();
    }
}
