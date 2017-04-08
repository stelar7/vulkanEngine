package no.stelar7.vulkan.engine.game;


import no.stelar7.vulkan.engine.game.objects.GameObject;
import no.stelar7.vulkan.engine.renderer.VulkanRenderer;

import java.util.*;

public abstract class Game
{
    protected final List<GameObject> gameObjects = new ArrayList<>();
    protected final VulkanRenderer renderer;
    
    private boolean initOk;
    
    public Game(VulkanRenderer renderer)
    {
        this.renderer = renderer;
    }
    
    public abstract void update();
    
    /**
     * Add objects to gameObjects to render them... for now..
     */
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
    
    public List<GameObject> getGameObjects()
    {
        return Collections.unmodifiableList(gameObjects);
    }
}
