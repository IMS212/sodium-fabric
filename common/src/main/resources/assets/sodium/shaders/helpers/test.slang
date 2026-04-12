public module test;

struct ChunkVertex {
    float3 position;
    float4 color;
    float2 uv;
    float2 light;
    uint id;
};

static const float MODEL_ORIGIN = 8.0;
static const float MODEL_RANGE = 32.0;
static const float POSITION_MAX_VALUE = float(1 << 20);
static const float OFFSET_MAX_VALUE = float(1 << 14);
static const float OFFSET_RANGE = 4.0;
static const float OFFSET_ORIGIN = 2.0;
static const float TEXTURE_MAX_VALUE = float(1 << 15);

// performance is a lie
uint extractBits(uint4x4 d, uint bitOffset, uint count) {
    uint word = bitOffset >> 5;
    uint bit = bitOffset & 31;

    uint row = word / 4;
    uint col = word % 4;

    uint lo = d[row][col] >> bit;

    uint result;
    if (bit + count <= 32)
    {
        result = lo;
    }
    else
    {
        uint row2 = (word + 1) / 4;
        uint col2 = (word + 1) % 4;
        uint hi = d[row2][col2] << (32 - bit);
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
    float block = float((raw >> 0) & 0x7F) * 2.0;
    float sky = float((raw >> 7) & 0x7F) * 2.0;
    return float2(block, sky);
}

int signExtend14(uint x) {
    return (int(x << 18)) >> 18;
}

// yes, this decoding logic is slop
// but i have confirmed it works, so I shall keep it until the pre-production rewrite
// (that always seems to happen)
ChunkVertex decodeQuadVertex(uint4x4 data, uint vertexIndex) {
    ChunkVertex v;

    float3 baseQ = float3(
        extractBits(data, 0, 20),
        extractBits(data, 20, 20),
        extractBits(data, 40, 20)
    );

    float3 finalQ;

    if (vertexIndex == 0) {
        finalQ = baseQ;
    } else{
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

    uint lightRaw = extractBits(data, 442 + vertexIndex * 14, 14);
    v.light = decodeLight(lightRaw);
    v.id = extractBits(data, 498, 8); 
    return v;
}