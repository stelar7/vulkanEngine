package no.stelar7.vulkan.engine.game.objects;

import no.stelar7.vulkan.engine.buffer.StagedBuffer;
import org.lwjgl.vulkan.*;

public class Model
{
    private StagedBuffer vertexBuffer;
    private StagedBuffer indexBuffer;
    private int          indexCount;
    
    public Model(StagedBuffer vertexBuffer, StagedBuffer indexBuffer)
    {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        indexCount = (int) (indexBuffer.getHostBuffer().getSize() / Integer.BYTES);
    }
    
    public StagedBuffer getVertexBuffer()
    {
        return vertexBuffer;
    }
    
    public int getIndexCount()
    {
        return indexCount;
    }
    
    public void free(VkDevice device)
    {
        vertexBuffer.free(device);
        indexBuffer.free(device);
    }
    
    public StagedBuffer getIndexBuffer()
    {
        return indexBuffer;
    }
}
