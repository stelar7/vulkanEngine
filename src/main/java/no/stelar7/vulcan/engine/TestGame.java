package no.stelar7.vulcan.engine;

public class TestGame extends Game
{
    public TestGame(VulkanRenderer renderer)
    {
        super(renderer);
    }
    
    
    private float     triangleSize = 1.6f;
    private float     sqrt3        = (float) Math.sqrt(3);
    private float[][] triangle     = new float[][]
            {
                    /*rb*/ new float[]{0.5f * triangleSize, sqrt3 * 0.25f * triangleSize, 0,     /*R*/1f, 0f, 0f, 1f},
                    /* t*/ new float[]{0f, sqrt3 * 0.25f * triangleSize, 0,                      /*G*/0f, 1f, 0f, 1f},
                    /*lb*/ new float[]{-0.5f * triangleSize, sqrt3 * 0.25f * triangleSize, 0,    /*B*/0f, 0f, 1f, 1f}
            };
    
    private Vertex triangleVertex = new Vertex(renderer, triangle);
    
    @Override
    public void update()
    {
        
    }
    
    @Override
    public void render()
    {
        
        
    }
    
    @Override
    public void delete()
    {
        triangleVertex.destroy(renderer.getDevice());
    }
    
    @Override
    public void init()
    {
    }
}
