#version 460 core

#import <sodium:include/fog.glsl>

struct Chunk {
    uvec2 blocks[4096];
};

layout(binding = 8, std430) buffer Voxels {
    Chunk[] ids;
};

int to1D( int x, int y, int z ) {
        return (z * 16 * 16) + (y * 16) + x;
}

layout(binding = 5, std430) buffer Moment {
    bool found;
};

uniform int u_Test;

in vec4 v_Color; // The interpolated vertex color
in vec2 v_TexCoord; // The interpolated block texture coordinates
in float v_FragDistance; // The fragment's distance from the camera

in float v_MaterialMipBias;
in float v_MaterialAlphaCutoff;

uniform sampler2D u_BlockTex; // The block texture

uniform vec4 u_FogColor; // The color of the shader fog
uniform float u_FogStart; // The starting position of the shader fog
uniform float u_FogEnd; // The ending position of the shader fog

out vec4 fragColor; // The output fragment for the color framebuffer

void main() {
    vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);

    // Apply per-vertex color
    diffuseColor *= v_Color;

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }
#endif

    if (found) {
    fragColor = v_Color;
    } else {

    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
    }
}