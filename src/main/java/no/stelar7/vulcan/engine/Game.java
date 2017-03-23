package no.stelar7.vulcan.engine;

public abstract class Game
{
    protected VulkanRenderer renderer;
    
    public Game(VulkanRenderer renderer)
    {
        this.renderer = renderer;
    }
    
    public abstract void update();
    
    public abstract void render();
    
    public abstract void delete();
    
    /**
     * I'm not sure if how im gonna do this yet, so stick to creating stuff in the init method for now...
     */
    public abstract void init();
}
