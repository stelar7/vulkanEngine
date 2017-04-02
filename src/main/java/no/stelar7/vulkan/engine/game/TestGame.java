package no.stelar7.vulkan.engine.game;

import no.stelar7.vulkan.engine.renderer.VulkanRenderer;
import org.joml.*;
import org.lwjgl.vulkan.*;

import java.util.*;

public class TestGame extends Game
{
    public TestGame(VulkanRenderer renderer)
    {
        super(renderer);
    }
    
    @Override
    public void update()
    {
        // TODO: nothing to do yet
    }
    
    private List<Vector3f> pos = Arrays.asList(new Vector3f(0.0f, -0.5f, 0.5f),
                                               new Vector3f(0.5f, 0.5f, 0.5f),
                                               new Vector3f(-0.5f, 0.5f, 0.5f));
    
    private List<Vector4f> color = Arrays.asList(new Vector4f(1f, 0f, 0f, 1f),
                                                 new Vector4f(0f, 1f, 0f, 1f),
                                                 new Vector4f(0f, 0f, 1f, 1f));
    
    private List<Integer> indecies = Arrays.asList(0, 1, 2);
    
    private VkClearValue.Buffer      clearColors     = VkClearValue.calloc(2);
    private VkClearColorValue        clearColorValue = VkClearColorValue.calloc();
    private VkClearDepthStencilValue clearDepthValue = VkClearDepthStencilValue.calloc();
    
    
    @Override
    public void render()
    {
    }
    
    @Override
    public void delete()
    {
        clearDepthValue.free();
        clearColorValue.free();
        clearColors.free();
    }
    
    @Override
    public void init()
    {
        clearColorValue.float32(0, 0.3f).float32(1, 0.3f).float32(2, .3f).float32(3, 1f);
        clearDepthValue.depth(0);
        
        clearColors.get(0).color(clearColorValue);
        clearColors.get(1).depthStencil(clearDepthValue);
        
        super.init();
    }
}
