public module test;

struct ChunkVertex {
    float3 position;
    float4 color;
    float2 uv;
    float2 light;
};

static const float MODEL_ORIGIN = 8.0;
static const float MODEL_RANGE = 32.0;
static const float POSITION_MAX_VALUE = float(1 << 20);
static const float OFFSET_MAX_VALUE = float(1 << 14);
static const float OFFSET_RANGE = 4.0;
static const float OFFSET_ORIGIN = 2.0;
static const float TEXTURE_MAX_VALUE = float(1 << 15);

uint getWord(uint4x4 d, uint idx)
{
    switch (idx)
    {
    case 0: return d[0].x;
    case 1: return d[0].y;
    case 2: return d[0].z;
    case 3: return d[0].w;
    case 4: return d[1].x;
    case 5: return d[1].y;
    case 6: return d[1].z;
    case 7: return d[1].w;
    case 8: return d[2].x;
    case 9: return d[2].y;
    case 10: return d[2].z;
    case 11: return d[2].w;
    case 12: return d[3].x;
    case 13: return d[3].y;
    case 14: return d[3].z;
    default: return d[3].w;
    }
}

uint extractBits(uint4x4 d, uint bitOffset, uint count)
{
    uint word = bitOffset >> 5;
    uint bit = bitOffset & 31;

    uint lo = getWord(d, word) >> bit;

    uint result;
    if (bit + count <= 32)
    {
        result = lo;
    }
    else
    {
        uint hi = getWord(d, word + 1) << (32 - bit);
        result = lo | hi;
    }

    return result & ((1u << count) - 1u);
}

float decodeAbsolutePosition(uint raw) {
    return (float(raw) / POSITION_MAX_VALUE) * MODEL_RANGE - MODEL_ORIGIN;
}

float decodeOffsetPosition(uint raw) {
    return (float(raw) / OFFSET_MAX_VALUE) * OFFSET_RANGE - OFFSET_ORIGIN;
}

float4 decodeColor(uint raw) {
    return float4(
        float((raw >>  0) & 0xFF) / 255.0,
        float((raw >>  8) & 0xFF) / 255.0,
        float((raw >> 16) & 0xFF) / 255.0,
        float((raw >> 24) & 0xFF) / 255.0
    );
}

float2 decodeTexture(uint raw) {
    int uBits = int(raw & 0xFFFF);
    int vBits = int(raw >> 16);

    float u = decodeTextureCoord(uBits);
    float v = decodeTextureCoord(vBits);

    return float2(u, v);
}

float decodeTextureCoord(int raw) {
    int signBit = (raw >> 15) & 1;
    int quantized = raw & 0x7FFF;

    float bias = (signBit != 0) ? -1.0 : 1.0;
    float epsilon = 1.0 / TEXTURE_MAX_VALUE;

    return (float(quantized) + bias * epsilon) / TEXTURE_MAX_VALUE;
}

float2 decodeLight(uint raw) {
    float block = float((raw >> 0) & 0xFF);
    float sky   = float((raw >> 8) & 0xFF);
    return float2(block, sky);
}
int signExtend14(uint x)
{
    return (int(x << 18)) >> 18;
}
ChunkVertex decodeQuadVertex(uint4x4 data, uint vertexIndex)
{
    ChunkVertex v;

    float3 baseQ = float3(
        extractBits(data, 0, 20),
        extractBits(data, 20, 20),
        extractBits(data, 40, 20)
    );

    float3 finalQ;

    if (vertexIndex == 0)
    {
        finalQ = baseQ;
    }
    else
    {
        uint base = 60 + (vertexIndex - 1) * 42;

        int dx = signExtend14(extractBits(data, base + 0, 14));
        int dy = signExtend14(extractBits(data, base + 14, 14));
        int dz = signExtend14(extractBits(data, base + 28, 14));

        float scale = (float(POSITION_MAX_VALUE) / MODEL_RANGE) / float(1 << 12);

        float3 deltaQ = float3(dx, dy, dz) * scale;

        finalQ = baseQ + deltaQ;
    }

    v.position = (finalQ / float(POSITION_MAX_VALUE)) * MODEL_RANGE - MODEL_ORIGIN;

    uint colorRaw = extractBits(data, 186 + vertexIndex * 32, 32);
    v.color = decodeColor(colorRaw);

    uint uvRaw = extractBits(data, 314 + vertexIndex * 32, 32);
    v.uv = decodeTexture(uvRaw);

    uint lightRaw = extractBits(data, 442 + vertexIndex * 16, 16);
    v.light = decodeLight(lightRaw);

    return v;
}