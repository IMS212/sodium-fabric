#version 450 core

layout(std140, binding = 2) uniform ChunkUniforms {
    mat4 u_ModelViewMatrix;
    mat4 u_ProjectionMatrix;

    vec2 u_TexelSize;
    vec2 u_TexCoordShrink;

    int u_CurrentTime;
    bool u_UseRGSS;
    float u_FadePeriodInv;

    ivec4 u_CameraPosInt;

    vec4 u_CameraPosFract;
    vec4 u_FogColor;

    vec2 u_EnvironmentFog;
    vec2 u_RenderFog;
};

#import <sodium:include/fog.glsl>
#import <sodium:include/chunk_vertex.glsl>
#import <sodium:include/chunk_matrices.glsl>

out vec4 v_Color;
out vec2 v_TexCoord;


#ifdef USE_FOG
out vec2 v_FragDistance;
out float fadeFactor;
#endif

uniform sampler2D u_LightTex; // The light map texture sampler

layout(std140) uniform ChunkData {
    ivec4 u_chunkFades[64]; // Packing into ivec4 is needed to avoid wasting 3KB...
};

layout(std430) buffer PosBuffer {
    uvec2 chunkPos[];
};


ivec3 _sign_extend_section_pos(uvec3 v) {
    return ivec3(
        (int(v.x << 10u)) >> 10,
        (int(v.y << 12u)) >> 12,
        (int(v.z << 10u)) >> 10
    );
}

ivec3 _unpack_section_pos(uvec2 pkd) {
    uint lo = pkd.x;
    uint hi = pkd.y;

    uint x = (hi >> 10u) & 0x3FFFFFu;

    uint y = lo & 0xFFFFFu;

    uint z = ((lo >> 20u) & 0xFFFu) | ((hi & 0x3FFu) << 12u);

    return _sign_extend_section_pos(uvec3(x, y, z));
}

uvec3 _get_relative_chunk_coord(uint pos) {
    // Packing scheme is defined by LocalSectionIndex
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

void main() {
    _vert_init();

    // Transform the chunk-local vertex position into world model space
    ivec3 sectionCoord = _unpack_section_pos(chunkPos[_draw_id]) * 16;
    sectionCoord -= u_CameraPosInt.xyz;

    vec3 translation =
        (vec3(sectionCoord)) - u_CameraPosFract.xyz;
    vec3 position = _vert_position + translation;

#ifdef USE_FOG
    v_FragDistance = getFragDistance(position);

    int chunkId = int(_draw_id);
    int chunkFade = u_chunkFades[chunkId >> 2][chunkId & 3];
    int fadeTime = u_CurrentTime - chunkFade;
    float elapsed = float(fadeTime);
    float fade = clamp(float(u_CurrentTime - chunkFade) * u_FadePeriodInv, 0.0, 1.0);
    fadeFactor = 1.0; // TODO (chunkFade < 0) ? 1.0 : fade;
#endif

    // Transform the vertex position into model-view-projection space
    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

    // Add the light color to the vertex color, and pass the texture coordinates to the fragment shader
    v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);
    v_TexCoord = (_vert_tex_diffuse_coord_bias * u_TexCoordShrink) + _vert_tex_diffuse_coord; // FMA for precision

}
