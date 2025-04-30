#version 460 core

#import <sodium:include/fog.glsl>

in vec4 v_Color; // The interpolated vertex color
in vec2 v_TexCoord; // The interpolated block texture coordinates
in float v_FragDistance; // The fragment's distance from the camera

in float v_MaterialMipBias;
in float v_MaterialAlphaCutoff;

uniform sampler2D u_BlockTex; // The block texture

uniform vec4 u_FogColor; // The color of the shader fog
uniform float u_FogStart; // The starting position of the shader fog
uniform float u_FogEnd; // The ending position of the shader fog

uniform ivec2 chunkDiameter;

out vec4 fragColor; // The output fragment for the color framebuffer
int wrap(int v, int s) {
    int m = v % s;
    return m < 0 ? m + s : m;
}
uint to1DBlock( uint x, uint y, uint z ) {
        return (z * 16 * 16) + (y * 16) + x;
}

layout(binding = 10) buffer VoxelIndices {
    uint voxelIndices[];
};

struct Chunk {
    ivec2 chunkData[16 * 16 *16];
};

layout(binding = 11) buffer VoxelData {
    Chunk voxelData[];
};

int imod(int x, int m) {
    int r = x % m;
    return (r < 0) ? (r + m) : r;
}

uint packSectionIndex(ivec3 coords, int diameter, int verticalDistance) {
int sx = wrap(coords.x, diameter);
int sz = wrap(coords.z, diameter);
int sy = wrap(coords.y + 4, verticalDistance);

return
    uint(sz) * uint(diameter * verticalDistance) +
    uint(sy) * uint(diameter) +
    uint(sx);
}

bool checkObjectExistence(ivec3 loc) {
     uint sect = packSectionIndex(ivec3(loc.x >> 4, loc.y >> 4, loc.z >> 4), chunkDiameter.x, chunkDiameter.y);
     if (voxelIndices[sect] == uint(-1)) return false;
     return voxelData[voxelIndices[sect]].chunkData[to1DBlock(loc.x & 15, loc.y & 15, loc.z & 15)].x != 0;
}

bool sectionLoaded(ivec3 loc) {
     uint sect = packSectionIndex(ivec3(loc.x >> 4, loc.y >> 4, loc.z >> 4), chunkDiameter.x, chunkDiameter.y);
     if (voxelIndices[sect] == uint(-1)) return false;
     return true;
}

void main() {
    vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);

    // Apply per-vertex color
    diffuseColor *= v_Color;

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }
#endif

    uint sect = packSectionIndex(ivec3(0, 0, 0), chunkDiameter.x, chunkDiameter.y);
    if (!checkObjectExistence(ivec3(1024, 64, 1024))) {
        if (!sectionLoaded(ivec3(1024, 64, 1024))) {
                diffuseColor = vec4(0.0, 1.0, 0.0, 1.0) * diffuseColor;

        } else {
        diffuseColor = vec4(1.0, 0.0, 0.0, 1.0) * diffuseColor;
        }
    }

    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
}