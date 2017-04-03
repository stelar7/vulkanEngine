package no.stelar7.vulkan.engine.spec;

import org.joml.Matrix4f;

public final class UniformSpec
{
    
    private UniformSpec()
    {
        // Hide constructor
    }
    
    static final class SizeMapping
    {
        
        private SizeMapping()
        {
            // Hide constructor
        }
        
        public static int getMat4Size()
        {
            return 16;
        }
        
        public static int getVec3Size()
        {
            return 3;
        }
        
        public static int getVec2Size()
        {
            return 2;
        }
    }
    
    
    // UBO Elements start
    private static final Matrix4f mvc = null;
    // UBO Elements end
    
    public static int getSizeInBytes()
    {
        int size = 0;
        
        // MVC
        size += SizeMapping.getMat4Size();
        
        
        // IN BYTES
        size *= Float.BYTES;
        return size;
    }
}
