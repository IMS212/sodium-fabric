public module terrain;

import helpers.chunk_material;
import helpers.chunk_vertex;
import helpers.fog;
import helpers.sampling;

struct VertexOutput {
    float4 pos : SV_Position;
    float4 color;
    float2 uv;
    float2 fragDistance;
    uint material;
}

struct PC {
    float3 regionOffset;
    int padding;
    float4x4 modelViewMatrix;
    float4x4 projectionMatrix;
    float2 u_TexCoordShrink;
}

[[vk::push_constant]]
ConstantBuffer<PC, ScalarDataLayout> pc;

[[vk::binding(0, 0)]]
Sampler2D blockAtlas;

[[vk::binding(1)]]
Sampler2D lightTex;

[[shader("vertex")]]
VertexOutput vertexMain(VertexInput input) {
    Vertex vertex = _vert_init(input);

    VertexOutput output = VertexOutput();

    // Transform the chunk-local vertex position into world model space
    float3 translation = pc.regionOffset + _get_draw_translation(vertex.drawId);
    float3 position = vertex.pos + translation;

    output.fragDistance = getFragDistance(position);

    //int chunkId = int(_draw_id);
    //int chunkFade = u_chunkFades[chunkId >> 2][chunkId & 3];
    //int fadeTime = u_CurrentTime - chunkFade;
   // float elapsed = float(fadeTime);
    //float fade = clamp(float(u_CurrentTime - chunkFade) * u_FadePeriodInv, 0.0, 1.0);
    //fadeFactor = (chunkFade < 0) ? 1.0 : fade;

    // Transform the vertex position into model-view-projection space
    output.pos = mul(pc.projectionMatrix, mul(pc.modelViewMatrix, float4(position, 1.0)));

    // Add the light color to the vertex color, and pass the texture coordinates to the fragment shader
    output.color = vertex.color * lightTex.Sample(vertex.lightCoord); //  * texture(u_LightTex, vertex.lightCoord);
    output.uv = (vertex.texCoordBias * pc.u_TexCoordShrink) + vertex.texCoord; // FMA for precision

    output.material = vertex.materialParams;

    return output;
}

[[vk::constant_id(0)]]
 const bool isCutout;

[[shader("fragment")]]
float4 fragmentMain(VertexOutput input) : SV_Target0 {
    float4 col = blockAtlas.Sample(input.uv) * input.color;
    if (isCutout && col.a < 0.1) discard;
    return col;
}