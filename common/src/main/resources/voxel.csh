#version 460 core

layout(binding = 9) uniform Chunks {
    ivec4 chunks[128];
};

struct Chunk {
    uvec2 blocks[4096];
};

layout(binding = 8, std430) buffer Voxels {
    Chunk[] ids;
};

layout(binding = 5, std430) buffer Moment {
    bool found;
};

int to1D( int x, int y, int z ) {
        return (z * 16 * 16) + (y * 16) + x;
}

int sky(uint packedLight) {
   return int(packedLight >> 20u & 15u);
}

layout (local_size_x = 1, local_size_y = 1, local_size_z = 1) in;


void main() {
    ivec4 c = chunks[gl_WorkGroupID.x];

    if (c.x == 52 && c.y == 4 && c.z == 44) {
        found = sky(ids[c.w].blocks[to1D(1, 3, 14)].y) == 15;
    }
}