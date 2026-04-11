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
    float4x4 modelViewMatrix;
    float4x4 projectionMatrix;
    float2 u_TexCoordShrink;
    int3 cameraBlockPos;
    float3 cameraFracPos;
}

[[vk::push_constant]]
ConstantBuffer<PC, ScalarDataLayout> pc;

[[vk::binding(0, 0)]]
Sampler2D blockAtlas;

[[vk::binding(1)]]
Sampler2D lightTex;

[[vk::binding(2)]]
ByteAddressBuffer instanceBuffer;

[[shader("vertex")]]
VertexOutput vertexMain(uint vertexId: SV_VulkanVertexID, uint instanceId: SV_InstanceID) {
    VertexOutput output = VertexOutput();

    uint base = instanceId * 32;
    uint4 d0 = instanceBuffer.Load4Aligned(base);
    uint2 d1 = instanceBuffer.Load2Aligned(base + 16);

    uint64_t quadAddr = uint64_t(d0.x) | (uint64_t(d0.y) << 32);
    int originX = asint(d0.z);
    int originY = asint(d0.w);
    int originZ = asint(d1.x);
    uint quadCount = d1.y;

    uint quadId = vertexId >> 2;
    uint localVert = vertexId & 3;

    if (quadId >= quadCount) {
        output.pos = float4(0.0, 0.0, 0.0, 1.0);
        output.color = float4(0.0, 0.0, 0.0, 0.0);
        output.uv = float2(0.0, 0.0);
        output.fragDistance = float2(0.0, 0.0);
        output.material = 0;
        return output;
    }

    uint64_t quadPtr = quadAddr + uint64_t(quadId) * 64;
    uint4x4 loaded = loadAligned<16, uint4x4>(Ptr<uint4x4>(quadPtr));
    ChunkVertex vertex = decodeQuadVertex(loaded, localVert);

    float3 sectionOrigin = float3(float(originX), float(originY), float(originZ));
    float3 cameraBlock = float3(float(pc.cameraBlockPos.x), float(pc.cameraBlockPos.y), float(pc.cameraBlockPos.z));
    float3 translation = (sectionOrigin - cameraBlock) - pc.cameraFracPos;

    float3 position = vertex.position + translation;

    output.fragDistance = getFragDistance(position);
    output.pos = mul(pc.projectionMatrix, mul(pc.modelViewMatrix, float4(position, 1.0)));
    output.color = vertex.color * lightTex.SampleLevel(vertex.light, 0);
    output.uv = vertex.uv;
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
