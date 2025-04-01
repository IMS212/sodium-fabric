#version 330 core

#import <sodium:include/fog.glsl>
#import <sodium:include/chunk_vertex.glsl>
#import <sodium:include/chunk_matrices.glsl>
#import <sodium:include/chunk_material.glsl>

uniform vec3 u_RegionOffset;
uniform vec2 u_TexCoordShrink;
uniform mat4 u_TextureMat;


uvec3 _get_relative_chunk_coord(uint pos) {
    // Packing scheme is defined by LocalSectionIndex
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

vec4 projection_from_position(vec4 position) {
    vec4 projection = position * 0.5;
    projection.xy = vec2(projection.x + projection.w, projection.y + projection.w);
    projection.zw = position.zw;
    return projection;
}


out vec4 shimmer;
out vec2 texCoordBlock;
out vec4 texProjSky;
out vec2 texCoordGlint;

void main() {
    _vert_init();

    // Transform the chunk-local vertex position into world model space
    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;

    // Transform the vertex position into model-view-projection space
    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

    texProjSky = projection_from_position(gl_Position);
    texCoordBlock = (_vert_tex_diffuse_coord_bias * u_TexCoordShrink) + _vert_tex_diffuse_coord;
    texCoordGlint = (u_TextureMat * vec4(texCoordBlock, 0.0, 1.0)).xy;
    // Add the light color to the vertex color, and pass the texture coordinates to the fragment shader
    shimmer = _vert_color;
}
