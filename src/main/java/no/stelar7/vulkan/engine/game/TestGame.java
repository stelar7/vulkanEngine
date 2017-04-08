package no.stelar7.vulkan.engine.game;

import no.stelar7.vulkan.engine.EngineUtils;
import no.stelar7.vulkan.engine.buffer.*;
import no.stelar7.vulkan.engine.game.objects.*;
import no.stelar7.vulkan.engine.renderer.VulkanRenderer;
import org.joml.*;
import org.lwjgl.*;
import org.lwjgl.vulkan.*;

import java.nio.FloatBuffer;
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
        
        FloatBuffer vData = memAllocFloat(pos.size() * 3);
        for (int i = 0; i < pos.size(); i++)
        {
            Vector3f vertice = pos.get(i);
            vData.put(i * 3, vertice.get(0));
            vData.put((i * 3) + 1, vertice.get(1));
            vData.put((i * 3) + 2, vertice.get(2));
        }
        StagedBuffer stagedBuffer = renderer.createStagedBuffer(renderer.getDeviceFamily(), vData.remaining() * Float.BYTES, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        renderer.setBufferData(stagedBuffer, vData);
        renderer.swapHostToDevice(stagedBuffer);
        model = new Model(stagedBuffer);
        memFree(vData);
        
        item = new GameObject();
        item.setModel(model);
    
        Buffer hostBuffer = model.getVertexBuffer().getHostBuffer();
    
        PointerBuffer stagedPointer = memAllocPointer(1);
        EngineUtils.checkError(vkMapMemory(renderer.getDeviceFamily().getDevice(), hostBuffer.getMemoryBlock().getMemory(), hostBuffer.getMemoryBlock().getOffset(), hostBuffer.getSize(), 0, stagedPointer));
        long pointer = stagedPointer.get(0);
        memFree(stagedPointer);
    
        FloatBuffer data = memFloatBuffer(pointer, model.getVertexCount());
        EngineUtils.printBuffer(data);
        vkUnmapMemory(renderer.getDeviceFamily().getDevice(), hostBuffer.getMemoryBlock().getMemory());
        
        super.init();
    }
}
