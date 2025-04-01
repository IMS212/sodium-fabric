#version 410 core

uniform sampler2D u_blockTex;
uniform sampler2D u_skyTex;
uniform sampler2D u_glintTex;

in vec4 shimmer;
in vec2 texCoordBlock;
in vec4 texProjSky;
in vec2 texCoordGlint;

out vec4 fragColor; // The output fragment for the color framebuffer

void main() {
    vec4 textureColor = texture(u_blockTex, texCoordBlock);
    vec4 skyColor = textureProj(u_skyTex, texProjSky);
    vec4 glintColor = texture(u_glintTex, texCoordGlint) * shimmer;

    fragColor = mix(skyColor, textureColor, glintColor * textureColor.a);
}