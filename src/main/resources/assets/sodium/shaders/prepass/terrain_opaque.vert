#version 450 core

#import <sodium:include/terrain_draw.vert>
#import <sodium:include/terrain_view.vert>
#import <sodium:include/terrain_format.vert>
#import <sodium:include/terrain_textures.glsl>

out vec2 tex_diffuse_coord;
flat out int frag_block_state;

void main() {
    _vert_init();

    // Local space -> View space
    vec3 view_position = _apply_view_transform(_vert_position);

    // View space -> Clip space
    gl_Position = mat_modelviewproj * vec4(view_position, 1.0);

    // Pass the texture coordinates verbatim
    tex_diffuse_coord = _vert_tex_diffuse_coord;

frag_block_state = _vert_block_state;
}
