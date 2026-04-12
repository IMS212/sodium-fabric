public module terrain;

import helpers.chunk_material;
import helpers.chunk_vertex;
import helpers.fog;
import helpers.sampling;
import helpers.test;

static const uint QUADS_PER_WG = 32;

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

struct SectionData {
    uint64_t vertexAddr;
    int originX;
    int originY;
    int originZ;
    uint sliceMask;
    uint vertexCount[7];
    uint pad[3]; // :(
};

struct VisibleSection {
    uint sectionId;
    uint visibleFaceMask;
};

[[vk::push_constant]]
ConstantBuffer<PC, ScalarDataLayout> pc;

[[vk::binding(0, 0)]]
Sampler2D blockAtlas;

[[vk::binding(1)]]
Sampler2D lightTex;

[[vk::binding(2)]]
ByteAddressBuffer instanceBuffer;

[[vk::binding(3)]]
StructuredBuffer<SectionData, ScalarDataLayout> sectionData;

[[vk::binding(4)]]
StructuredBuffer<VisibleSection> visibleSections;

struct SegmentInfo {
    uint quadCount;
    uint srcOffset;
    uint base;
};

struct Payload {
    uint64_t vertexAddr;
    int originX;
    int originY;
    int originZ;
    uint segCount;
    uint totalQuads;
    SegmentInfo seg[7];
};

groupshared Payload taskPayload;

[[shader("amplification")]]
[numthreads(1, 1, 1)]
void taskMain(uint3 groupId : SV_GroupID) {
    uint idx = groupId.x;
    int sectionId = visibleSections[idx].sectionId;
    SectionData sec = sectionData[sectionId];

    taskPayload.vertexAddr = sec.vertexAddr;
    taskPayload.originX = sec.originX;
    taskPayload.originY = sec.originY;
    taskPayload.originZ = sec.originZ;

    uint segCount = 0;
    uint srcOffset = 0;
    uint base = 0;

    for (int i = 0; i < 7; i++) {
        int quadCount = sec.vertexCount[i] >> 2;
        if (quadCount > 0) {
            taskPayload.seg[segCount].quadCount = quadCount;
            taskPayload.seg[segCount].srcOffset = srcOffset;
            taskPayload.seg[segCount].base = base;
            base += quadCount;
            segCount++;
        }
        srcOffset += quadCount;
    }

    taskPayload.segCount = segCount;
    taskPayload.totalQuads = base;

    if (base == 0) {
        DispatchMesh(0, 1, 1, taskPayload);
        return;
    }

    DispatchMesh((base + 16 - 1) / 16, 1, 1, taskPayload);
}

[[shader("mesh")]]
[numthreads(16, 1, 1)]
[outputtopology("triangle")]
void meshMain(
    uint lane : SV_GroupIndex, uint3 meshWG : SV_GroupID, in payload Payload p, out vertices VertexOutput verts[64],
    out indices uint3 prims[32])
{
    uint base = meshWG.x * 16;
    uint theIdx = base + lane;
    bool run = theIdx < p.totalQuads;

    VertexOutput out0;
    VertexOutput out1;
    VertexOutput out2;
    VertexOutput out3;

    if (run) {
        uint theRealId = 0;
        for (uint s = 0; s < p.segCount && s < 7; s++) {
            uint segBase = p.seg[s].base;
            uint segEnd  = segBase + p.seg[s].quadCount;

            if (theIdx >= segBase && theIdx < segEnd) {
                theRealId = p.seg[s].srcOffset + (theIdx - segBase);
                break;
            }
        }

        // never use a direct struct load, apparantly, because slang will murder your family
        uint64_t quadPtr = p.vertexAddr + uint64_t(theRealId) * 64;
        uint4x4 raw = loadAligned<16, uint4x4>(Ptr<uint4x4>(quadPtr));

        ChunkVertex v0 = decodeQuadVertex(raw, 0);
        ChunkVertex v1 = decodeQuadVertex(raw, 1);
        ChunkVertex v2 = decodeQuadVertex(raw, 2);
        ChunkVertex v3 = decodeQuadVertex(raw, 3);

        float3 sectionOffset = float3(float(p.originX - pc.cameraBlockPos.x), float(p.originY - pc.cameraBlockPos.y), float(p.originZ - pc.cameraBlockPos.z)) - pc.cameraFracPos; // stolen from cameratransform, don't want to redo this right now

        float3 pos0 = v0.position + sectionOffset;
        float3 pos1 = v1.position + sectionOffset;
        float3 pos2 = v2.position + sectionOffset;
        float3 pos3 = v3.position + sectionOffset;

        float4x4 mvp = mul(pc.projectionMatrix, pc.modelViewMatrix);

        out0.pos = mul(mvp, float4(pos0, 1.0));
        out1.pos = mul(mvp, float4(pos1, 1.0));
        out2.pos = mul(mvp, float4(pos2, 1.0));
        out3.pos = mul(mvp, float4(pos3, 1.0));

        // i don't think `light` is being decoded right; I checked lightTex though and that's bound (4/10)
        out0.color = v0.color * lightTex.SampleLevel(v0.light, 0);
        out1.color = v1.color * lightTex.SampleLevel(v1.light, 0);
        out2.color = v2.color * lightTex.SampleLevel(v2.light, 0);
        out3.color = v3.color * lightTex.SampleLevel(v3.light, 0);

        out0.uv = v0.uv;
        out1.uv = v1.uv;
        out2.uv = v2.uv;
        out3.uv = v3.uv;

        out0.fragDistance = getFragDistance(pos0);
        out1.fragDistance = getFragDistance(pos1);
        out2.fragDistance = getFragDistance(pos2);
        out3.fragDistance = getFragDistance(pos3);
    }

    int count = run ? 1 : 0;

    uint baseX = WavePrefixSum(count);
    uint total = WaveActiveSum(count);
    uint vertBase = baseX * 4;
    uint triBase = baseX * 2;
    uint totalVerts = total * 4;
    uint totalTris = total * 2;

    SetMeshOutputCounts(totalVerts, totalTris);

    // why can't this be earlier again?
    if (!run) return;

    verts[vertBase + 0] = out0;
    verts[vertBase + 1] = out1;
    verts[vertBase + 2] = out2;
    verts[vertBase + 3] = out3;

    prims[triBase + 0] = uint3(vertBase + 0, vertBase + 1, vertBase + 2);
    prims[triBase + 1] = uint3(vertBase + 2, vertBase + 3, vertBase + 0);
}

[[vk::constant_id(0)]]
 const bool isCutout;

[[shader("fragment")]]
float4 fragmentMain(VertexOutput input) : SV_Target0 {
    float4 col = blockAtlas.Sample(input.uv) * input.color;
    if (isCutout && col.a < 0.1) discard;
    return col;
}
