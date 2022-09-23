#version 450 core

#import <sodium:include/terrain_textures.glsl>

in vec2 tex_diffuse_coord;
flat in int frag_block_state;

void main() {
vec4 frag_diffuse;
    if (frag_block_state == 1) {
        frag_diffuse = texture(tex_diffuse, tex_diffuse_coord);
    } else {
        frag_diffuse = textureLod(tex_diffuse, tex_diffuse_coord, 0);
    }

    if (frag_diffuse.a < 0.1) {
        discard;
    }
}