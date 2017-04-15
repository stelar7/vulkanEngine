package no.stelar7.vulkan.engine.game;

import no.stelar7.vulkan.engine.buffer.StagedBuffer;
import no.stelar7.vulkan.engine.game.objects.*;
import no.stelar7.vulkan.engine.renderer.VulkanRenderer;
import org.joml.*;
import org.lwjgl.vulkan.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.VK10.*;

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
    
    private List<Vector3f> pos = Arrays.asList(new Vector3f(0.0f, -0.5f, 0.0f),
                                               new Vector3f(0.5f, 0.5f, 0.0f),
                                               new Vector3f(-0.5f, 0.5f, 0.0f));
    
    private List<Vector4f> color = Arrays.asList(new Vector4f(1f, 0f, 0f, 1f),
                                                 new Vector4f(0f, 1f, 0f, 1f),
                                                 new Vector4f(0f, 0f, 1f, 1f));
    
    private List<Integer> indecies = Arrays.asList(0, 1, 2);
    
    private VkClearValue.Buffer      clearColors     = VkClearValue.calloc(2);
    private VkClearColorValue        clearColorValue = VkClearColorValue.calloc();
    private VkClearDepthStencilValue clearDepthValue = VkClearDepthStencilValue.calloc();
    
    private Model      model;
    private GameObject item;
    
    
    @Override
    public void render()
    {
        gameObjects.clear();
        gameObjects.add(item);
    }
    
    @Override
    public void delete()
    {
        clearDepthValue.free();
        clearColorValue.free();
        clearColors.free();
        
        model.free(renderer.getDeviceFamily().getDevice());
    }
    
    @Override
    public void init()
    {
        clearColorValue.float32(0, 0.3f).float32(1, 0.3f).float32(2, .3f).float32(3, 1f);
        clearDepthValue.depth(0);
        
        clearColors.get(0).color(clearColorValue);
        clearColors.get(1).depthStencil(clearDepthValue);
        
        renderer.setClearColor(clearColors);
        
        int         size  = 3 + 4;
        FloatBuffer vData = memAllocFloat(pos.size() * size);
        for (int i = 0; i < pos.size(); i++)
        {
            Vector3f loc = pos.get(i);
            Vector4f col = color.get(i);
            
            vData.put((i * size) + 0, loc.x());
            vData.put((i * size) + 1, loc.y());
            vData.put((i * size) + 2, loc.z());
            
            vData.put((i * size) + 3, col.x());
            vData.put((i * size) + 4, col.y());
            vData.put((i * size) + 5, col.z());
            vData.put((i * size) + 6, col.w());
        }
        
        StagedBuffer vertexBuffer = renderer.createStagedBuffer(renderer.getDeviceFamily(), vData.remaining() * Float.BYTES, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        
        renderer.setFloatBufferData(vertexBuffer, vData);
        renderer.swapHostToDevice(vertexBuffer);
        
        IntBuffer iData = memAllocInt(indecies.size());
        for (int i = 0; i < indecies.size(); i++)
        {
            iData.put(i, indecies.get(i));
        }
        
        StagedBuffer indexBuffer = renderer.createStagedBuffer(renderer.getDeviceFamily(), iData.remaining() * Integer.BYTES, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        
        renderer.setIntBufferData(indexBuffer, iData);
        renderer.swapHostToDevice(indexBuffer);
        
        model = new Model(vertexBuffer, indexBuffer);
        
        item = new GameObject();
        item.setModel(model);
        
        memFree(vData);
        
        super.init();
    }
}
