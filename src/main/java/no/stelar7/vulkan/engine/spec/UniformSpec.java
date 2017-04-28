package no.stelar7.vulkan.engine.spec;

import org.joml.Matrix4f;

import java.util.*;

public final class UniformSpec
{
    
    private UniformSpec()
    {
        // Hide constructor
    }
    
    public static final class SizeMapping
    {
        
        private SizeMapping()
        {
            // Hide constructor
        }
        
        public static int getMat4SizeInBytes()
        {
            return 16 * Float.BYTES;
        }
        
        public static int getVec3SizeInBytes()
        {
            return 3 * Float.BYTES;
        }
        
        public static int getVec2SizeInBytes()
        {
            return 2 * Float.BYTES;
        }
    }
    
    
    // UBO Elements start
    private static final List<Object> uboElements = Collections.singletonList(new Matrix4f());
    // UBO Elements end
    
    
    public List<Object> getUboElements()
    {
        return new ArrayList<>(uboElements);
    }
    
    public static int getSizeInBytes()
    {
        int size = 0;
        
        // MVC
        for (Object elem : uboElements)
        {
            if (elem instanceof Matrix4f)
            {
                size += SizeMapping.getMat4SizeInBytes();
            }
        }
        
        
        return size;
    }
}
