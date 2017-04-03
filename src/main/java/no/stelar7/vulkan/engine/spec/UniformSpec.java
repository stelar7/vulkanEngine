package no.stelar7.vulkan.engine.spec;

import org.joml.Matrix4f;

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
    private static final Matrix4f mvc = null;
    // UBO Elements end
    
    public static int getSizeInBytes()
    {
        int size = 0;
        
        // MVC
        size += SizeMapping.getMat4SizeInBytes();
        
        
        return size;
    }
}
