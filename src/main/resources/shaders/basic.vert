#version 450

layout(location = 0) in vec3 inPos;
layout(location = 1) in vec4 inColor;

layout(location = 0) out vec4 outColor;

out gl_PerVertex
{
    vec4 gl_Position;
};

layout(binding = 0) uniform UniformBufferObject
{
    mat4 mvc = mat4(1);
} ubo;

void main()
{
    outColor = inColor;

    gl_Position = vec4(inPos, 1);
}