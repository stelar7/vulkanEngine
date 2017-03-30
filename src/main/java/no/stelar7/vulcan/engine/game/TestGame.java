package no.stelar7.vulcan.engine.game;

import no.stelar7.vulcan.engine.render.*;
import no.stelar7.vulcan.engine.render.model.Vertices;
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
    
    private List<Vector3f> pos = Arrays.asList(new Vector3f(0.0f, -0.5f, 0),
                                               new Vector3f(0.5f, 0.5f, 0),
                                               new Vector3f(-0.5f, 0.5f, 0));
    
    private List<Vector4f> color = Arrays.asList(new Vector4f(1f, 0f, 0f, 1f),
                                                 new Vector4f(0f, 1f, 0f, 1f),
                                                 new Vector4f(0f, 0f, 1f, 1f));
    
    private Vertices triangleVertex = new Vertices(renderer, pos, color);
    
    private VkClearValue.Buffer clearColors     = VkClearValue.calloc(1);
    private VkClearColorValue   clearColorValue = VkClearColorValue.calloc();
    
    
    @Override
    public void render()
    {
        int imageIndex = renderer.getWindow().getNextImageIndex(renderer.getDevice());
        renderer.getWindow().submitToRenderQueue(renderer.getRenderQueue(), imageIndex);
        renderer.getWindow().presentRenderQueue(renderer.getRenderQueue(), imageIndex);
    }
    
    @Override
    public void delete()
    {
        triangleVertex.destroy(renderer.getDevice());
        clearColorValue.free();
        clearColors.free();
    }
    
    @Override
    public void init()
    {
        clearColorValue.float32(0, 0.3f).float32(1, 0.3f).float32(2, .3f).float32(3, 1f);
        clearColors.color(clearColorValue);
        
        Window window = renderer.getWindow();
        
        for (int i = 0; i < window.getSwapchainImageViews().size(); i++)
        {
            VkCommandBuffer commandBuffer = renderer.getWindow().getCommandBuffer(i);
            long            frameBuffer   = renderer.getWindow().getFrameBuffer(i);
            
            window.beginCommandBuffer(window.getCommandBuffer(i));
            window.beginRenderPass(commandBuffer, frameBuffer, clearColors);
            
            window.bindPipeline(commandBuffer);
            window.bindVertexBuffer(commandBuffer, ShaderSpec.getVertexBindingIndex(), triangleVertex.getVertexBuffer());
            
            window.setViewPort(commandBuffer);
            window.setScissor(commandBuffer);
            
            window.draw(commandBuffer, triangleVertex.getVertexCount());
            
            window.endRenderPass(commandBuffer);
            window.endCommandBuffer(commandBuffer);
        }
        
        super.init();
    }
}
