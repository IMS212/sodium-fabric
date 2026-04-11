public module instance_cull;

struct SectionData {
    uint64_t vertexAddr;
    int originX;
    int originY;
    int originZ;
    uint sliceMask;
    uint vertexCount[7];
    uint facingListLo;
    uint facingListHi;
    uint _pad;
};

struct VisibleSection {
    uint sectionId;
    uint visibleFaceMask;
};

struct IndirectDrawCommand {
    uint indexCount;
    uint instanceCount;
    uint firstIndex;
    int  vertexOffset;
    uint firstInstance;
};

struct CullPC {
    uint visibleSectionCount;
};

[[vk::push_constant]]
ConstantBuffer<CullPC, ScalarDataLayout> pc;

[[vk::binding(0, 0)]]
StructuredBuffer<SectionData, ScalarDataLayout> sectionData;

[[vk::binding(1, 0)]]
StructuredBuffer<VisibleSection> visibleSections;

[[vk::binding(2, 0)]]
RWByteAddressBuffer instances;

[[vk::binding(3, 0)]]
RWStructuredBuffer<IndirectDrawCommand> indirectCmd;

uint getFacing(SectionData sec, uint seg) {
    uint packed = (seg < 4) ? sec.facingListLo : sec.facingListHi;
    uint shift = (seg < 4) ? (seg * 8) : ((seg - 4) * 8);
    return (packed >> shift) & 0xFF;
}

[shader("compute")]
[numthreads(64, 1, 1)]
void cullMain(uint3 threadId : SV_DispatchThreadID) {
    uint idx = threadId.x;
    if (idx >= pc.visibleSectionCount) return;

    VisibleSection vis = visibleSections[idx];
    SectionData sec = sectionData[vis.sectionId];

    uint visibleMask = vis.visibleFaceMask & sec.sliceMask;
    if (visibleMask == 0) return;

    uint64_t currentAddr = sec.vertexAddr;

    for (uint seg = 0; seg < 7; seg++) {
        uint vCount = sec.vertexCount[seg];
        uint quadCount = vCount >> 2;

        if (quadCount == 0) {
            continue;
        }

        uint facing = getFacing(sec, seg);

        if (((visibleMask >> facing) & 1) == 0) {
            currentAddr += uint64_t(quadCount) * 64;
            continue;
        }

        uint remaining = quadCount;
        uint64_t groupAddr = currentAddr;

        while (remaining > 0) {
            uint groupSize = min(remaining, 16);

            uint slot;
            InterlockedAdd(indirectCmd[0].instanceCount, 1, slot);

            uint base = slot * 32;
            instances.Store(base + 0,  uint(groupAddr & 0xFFFFFFFF));
            instances.Store(base + 4,  uint(groupAddr >> 32));
            instances.Store(base + 8,  asuint(sec.originX));
            instances.Store(base + 12, asuint(sec.originY));
            instances.Store(base + 16, asuint(sec.originZ));
            instances.Store(base + 20, groupSize);

            groupAddr += uint64_t(groupSize) * 64;
            remaining -= groupSize;
        }

        currentAddr += uint64_t(quadCount) * 64;
    }
}
