package no.stelar7.vulkan.engine.renderer;

import no.stelar7.vulkan.engine.buffer.StagedBuffer;

public class Model
{
    private StagedBuffer vertexBuffer;
    private int          vertexCount;
    
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
}
