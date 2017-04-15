package no.stelar7.vulkan.engine.game.objects;

import no.stelar7.vulkan.engine.buffer.StagedBuffer;
import no.stelar7.vulkan.engine.spec.VertexSpec;
import org.lwjgl.vulkan.*;

public class Model
{
    private StagedBuffer vertexBuffer;
    private int          vertexCount;
    
    public Model(StagedBuffer vertexBuffer)
    {
        this.vertexBuffer = vertexBuffer;
        setVertexCount((int) (vertexBuffer.getHostBuffer().getSize() / VertexSpec.getVertexInputState().pVertexAttributeDescriptions().get(1).offset()));
    }
    
    public StagedBuffer getVertexBuffer()
    {
        return vertexBuffer;
    }
    
    public int getVertexCount()
    {
        return vertexCount;
    }
    
    public void setVertexCount(int vertexCount)
    {
        this.vertexCount = vertexCount;
    }
    
    public void free(VkDevice device)
    {
        vertexBuffer.free(device);
    }
}
