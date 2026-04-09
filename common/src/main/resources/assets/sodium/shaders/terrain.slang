public module terrain;

import helpers.chunk_material;
import helpers.chunk_vertex;
import helpers.fog;
import helpers.sampling;
import helpers.test;

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
    uint currentPage;
}

[[vk::push_constant]]
ConstantBuffer<PC, ScalarDataLayout> pc;

[[vk::binding(0, 0)]]
Sampler2D blockAtlas;

[[vk::binding(1)]]
Sampler2D lightTex;

[[vk::binding(2)]]
ConstantBuffer<uint64_t[256], ScalarDataLayout> u_pageBuffer;

[[shader("vertex")]]
VertexOutput vertexMain(uint id: SV_VulkanVertexID) {

    uint quadId = id >> 2;
    uint vertexId = id & 3;

    uint64_t basePtr = u_pageBuffer[pc.currentPage] + (64 * quadId);

    uint4x4 loaded = loadAligned<16, uint4x4>(Ptr<uint4x4>(basePtr));
    ChunkVertex vertex = decodeQuadVertex(loaded, vertexId);

    VertexOutput output = VertexOutput();

    // Transform the chunk-local vertex position into world model space
    float3 translation = pc.regionOffset /*+ _get_draw_translation(vertex.drawId)*/; // TODO
    float3 position = vertex.position + translation;

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
    output.color = vertex.color * lightTex.SampleLevel(vertex.light, 0); //  * texture(u_LightTex, vertex.lightCoord);
    output.uv = vertex.uv; // FMA for precision

    output.material = 0;

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