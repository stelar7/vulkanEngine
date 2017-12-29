package no.stelar7.vulkan.engine.game;

import no.stelar7.vulkan.engine.buffer.StagedBuffer;
import no.stelar7.vulkan.engine.game.objects.*;
import no.stelar7.vulkan.engine.renderer.*;
import org.joml.*;

import java.util.*;

public class TestGame extends Game
{
    public TestGame(VulkanRenderer renderer)
    {
        super(renderer);
    }
    
    private List<Vector3f> pos = Arrays.asList(new Vector3f(0.0f, -0.5f, 0.0f),
                                               new Vector3f(0.5f, 0.5f, 0.0f),
                                               new Vector3f(-0.5f, 0.5f, 0.0f));
    
    private List<Vector4f> color = Arrays.asList(new Vector4f(1f, 0f, 0f, 1f),
                                                 new Vector4f(0f, 1f, 0f, 1f),
                                                 new Vector4f(0f, 0f, 1f, 1f));
    
    private List<Integer> indecies = Arrays.asList(0, 1, 2);
    
    private ClearColor clear = new ClearColor(.3f, .3f, .3f, 1f, 0);
    
    private Model model;
    
    
    @Override
    public void render()
    {
        // todo?
    }
    
    
    @Override
    public void update()
    {
        // TODO: nothing to do yet
    }
    
    @Override
    public void destroy()
    {
        
        model.destroy(renderer.getDeviceFamily().getDevice());
        clear.destroy();
    }
    
    @Override
    public void init()
    {
        renderer.setClearColor(clear);
        
        
        GameObject   item         = new GameObject();
        StagedBuffer vertexBuffer = renderer.createVertexBuffer(pos, color);
        StagedBuffer indexBuffer  = renderer.createIndexBuffer(indecies);
        
        model = new Model(vertexBuffer, indexBuffer);
        item.setModel(model);
        gameObjects.add(item);
        
        super.init();
    }
}
