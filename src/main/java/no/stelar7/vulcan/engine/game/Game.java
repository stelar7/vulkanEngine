package no.stelar7.vulcan.engine.game;

import no.stelar7.vulcan.engine.render.VulkanRenderer;

public abstract class Game
{
    protected VulkanRenderer renderer;
    
    private boolean initOk;
    
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
    public void init()
    {
        initOk = true;
    }
    
    public boolean isInit()
    {
        return initOk;
    }
}