public module chunk_vertex;

static const uint POSITION_BITS        = 20u;
static const uint POSITION_MAX_COORD   = 1u << POSITION_BITS;
static const uint POSITION_MAX_VALUE   = POSITION_MAX_COORD - 1u;

static const uint TEXTURE_BITS         = 15u;
static const uint TEXTURE_MAX_COORD    = 1u << TEXTURE_BITS;
static const uint TEXTURE_MAX_VALUE    = TEXTURE_MAX_COORD - 1u;

static const float VERTEX_SCALE = 32.0 / float(POSITION_MAX_COORD);
static const float VERTEX_OFFSET = -8.0;


struct VertexInput {
    [[vk::location(0)]] uint2 position;
    [[vk::location(1)]] float4 color;
    [[vk::location(2)]] uint2 texCoord;
    [[vk::location(3)]] uint4 lightAndData;
}

uint3 _deinterleave_u20x3(uint2 data) {
    uint3 hi = (uint3(data.x) >> uint3(0u, 10u, 20u)) & 0x3FFu;
    uint3 lo = (uint3(data.y) >> uint3(0u, 10u, 20u)) & 0x3FFu;

    return (hi << 10u) | lo;
}

float2 _get_texcoord(VertexInput input) {
    return float2(input.texCoord & TEXTURE_MAX_VALUE) / float(TEXTURE_MAX_COORD);
}

float2 _get_texcoord_bias(VertexInput input) {
    return lerp(float2(-1.0), float2(1.0), bool2(input.texCoord >> TEXTURE_BITS));
}

uint3 _get_relative_chunk_coord(uint pos) {
    // Packing scheme is defined by LocalSectionIndex
    return uint3(pos) >> uint3(5u, 0u, 2u) & uint3(7u, 3u, 7u);
}

float3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * float3(16.0);
}

struct Vertex {
    float3 pos;
    float4 color;
    float2 texCoord;
    float2 texCoordBias;
    float2 lightCoord;

    uint materialParams;
    uint drawId;
}

Vertex _vert_init(VertexInput input) {
    Vertex v = Vertex();

    v.pos = float3(_deinterleave_u20x3(input.position)) * VERTEX_SCALE + VERTEX_OFFSET;
    v.color = input.color;
    v.texCoord = _get_texcoord(input);
    v.texCoordBias = _get_texcoord_bias(input);
    v.lightCoord = float2(input.lightAndData.xy) / float2(256.0);

    v.materialParams = input.lightAndData[2];
    v.drawId = input.lightAndData[3];

    return v;
}