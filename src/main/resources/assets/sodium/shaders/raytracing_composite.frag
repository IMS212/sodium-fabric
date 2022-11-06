#version 460 core
#extension GL_EXT_ray_query : enable
#extension GL_EXT_buffer_reference : enable
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : enable

layout(location = 0) in vec3 pos;
layout(std140, binding = 0) uniform CameraInfo {
    vec3 corners[4];
    mat4 viewInverse;
} cam;


struct Quad {
    vec2 uv[4];
    vec4 color[4];
    vec4 normal;
};

layout(buffer_reference) buffer Quads {Quad quads[]; };

layout(binding = 2) buffer BlasDataAddresses { uint64_t address[]; } quadBlobs;

layout(binding = 1) uniform accelerationStructureEXT acc;

layout(binding = 3) uniform  sampler2D blockTex;

layout(location=0) out vec4 color;


vec2 ray2uvCo(rayQueryEXT ray) {
    bool isSideA = (rayQueryGetIntersectionPrimitiveIndexEXT(ray, true)&1)==0;
    vec2 barry = rayQueryGetIntersectionBarycentricsEXT(ray, true);

    vec2 t0 = vec2(0,0);
    vec2 t2 = isSideA?vec2(1,1):vec2(1,0);
    vec2 t1 = isSideA?vec2(0,1):vec2(1,1);

    vec3 barys = vec3(1.0f - barry.x - barry.y, barry.x, barry.y);
    vec2 texCoords = t0 * barys.x + t1 * barys.y + t2 * barys.z;
    return texCoords;
}


vec2 ray2uvCoQu(rayQueryEXT ray, Quad quad) {
    bool isSideA = (rayQueryGetIntersectionPrimitiveIndexEXT(ray, true)&1)==0;
    vec2 barry = rayQueryGetIntersectionBarycentricsEXT(ray, true);

    vec2 t0 = quad.uv[0];
    vec2 t2 = isSideA?quad.uv[2]:quad.uv[3];
    vec2 t1 = isSideA?quad.uv[1]:quad.uv[2];

    vec3 barys = vec3(1.0f - barry.x - barry.y, barry.x, barry.y);
    vec2 texCoords = t0 * barys.x + t1 * barys.y + t2 * barys.z;
    return texCoords;
}

vec4 ray2colorCoQu(rayQueryEXT ray, Quad quad) {
    bool isSideA = (rayQueryGetIntersectionPrimitiveIndexEXT(ray, true)&1)==0;
    vec2 barry = rayQueryGetIntersectionBarycentricsEXT(ray, true);

    vec4 t0 = quad.color[0];
    vec4 t2 = isSideA?quad.color[2]:quad.color[3];
    vec4 t1 = isSideA?quad.color[1]:quad.color[2];

    vec3 barys = vec3(1.0f - barry.x - barry.y, barry.x, barry.y);
    vec4 texCoords = t0 * barys.x + t1 * barys.y + t2 * barys.z;
    return vec4(texCoords.rgb / 255, texCoords.a);
}

Quad getRayQuad(rayQueryEXT ray) {
    int blasBlob = rayQueryGetIntersectionInstanceCustomIndexEXT(ray, true);
    return Quads(quadBlobs.address[blasBlob]).quads[rayQueryGetIntersectionPrimitiveIndexEXT(ray, true)>>1];
}


void trace2(in rayQueryEXT rayQuery, vec3 origin, vec3 dir, float max) {
    rayQueryInitializeEXT(rayQuery,
        acc,
        gl_RayFlagsOpaqueEXT,
        0xFF,
        origin,
        0.001,
        dir,
        max);
    while(rayQueryProceedEXT(rayQuery));
}
float random(vec2 p){return fract(cos(dot(p,vec2(23.14069263277926,2.665144142690225)))*12345.6789);}
void trace(in rayQueryEXT rayQuery, vec3 origin, vec3 dir, float max, out float distance, out vec4 colour, out Quad quad, inout bool hitWater) {
    distance = 0;
    float currentAlpha = 1.0;
    while (true) {
        rayQueryInitializeEXT(rayQuery,
            acc,
            gl_RayFlagsOpaqueEXT,
            0xFF,
            origin,
            0.01,
            dir,
            max);
        while (rayQueryProceedEXT(rayQuery));
        float dist = rayQueryGetIntersectionTEXT(rayQuery, true);
        distance += dist;
        if (distance > max-0.001) {
            if (hitWater) {
                colour = vec4(0, 0, 1, 1);
            }
            colour = vec4(0, 0.5, 1, 0);
            return;
        }
        origin = dir*dist + origin;
        max -= dist;
        quad = getRayQuad(rayQuery);
        vec4 hitColour = textureLod(blockTex, ray2uvCoQu(rayQuery, quad), 0);
        hitColour.rgb *= ray2colorCoQu(rayQuery, quad).rgb;
        origin += dir * 0.00001;


        dir.x += (random(dir.xy) / 100);
        dir.y += (random(dir.xy) / 100);
        dir.z += (random(dir.xy) / 100);
        if (quad.normal.w > 0.5) {
            hitWater = true;
            dir = reflect(dir, quad.normal.xyz);
            continue;
        }
        if (hitColour.w < 0.01) {
            continue;
        }
        currentAlpha *= 1-hitColour.w;
        //colour = colour*(1-currentAlpha)+hitColour*currentAlpha;
        colour = hitColour;

        if (currentAlpha < 0.01) {
            return;
        }
    }
}
vec3 uniformSampleHemisphere(const float r1, const float r2)
{
    // cos(theta) = r1 = y
    // cos^2(theta) + sin^2(theta) = 1 -> sin(theta) = srtf(1 - cos^2(theta))
    float sinTheta = sqrt(1 - r1 * r1);
    float phi = 2 * (3.14159265358f) * r2;
    float x = sinTheta * cos(phi);
    float z = sinTheta * sin(phi);
    return vec3(x, r1, z);
}


uint state;

uint rand() {
    state = (state << 13U) ^ state;
    state = state * (state * state * 15731U + 789221U) + 1376312589U;
    return state;
}
float randFloat() {
    return float(rand() & uvec3(0x7fffffffU)) / float(0x7fffffff);
}

vec2 randVec2() {
    return vec2(randFloat(), randFloat());
}

vec3 randomDirection(vec3 normal) {
    vec2 v = randVec2();
    float angle = 2.0 * (3.14159265) * v.x;
    float u = 2.0 * v.y - 1.0;

    vec3 directionOffset = vec3(sqrt(1.0 - u * u) * vec2(cos(angle), sin(angle)), u);
    return normalize(normal + directionOffset);
}

void main(void) {
    vec2  p         = pos.xy;
    vec3  origin    = cam.viewInverse[3].xyz;
    vec3  target    = mix(mix(cam.corners[0], cam.corners[2], p.y), mix(cam.corners[1], cam.corners[3], p.y), p.x);
    vec4  direction = cam.viewInverse * vec4(normalize(target.xyz), 0.0);

    state = floatBitsToUint(p.x);
    rand();
    state ^= floatBitsToUint(p.y);
    rand();

    bool hitWater;
    rayQueryEXT rayQuery;
    float d;
    Quad quad;
    trace(rayQuery, origin, direction.xyz, 1024.0, d, color, quad, hitWater);

    if (color.a < 0.1) {
        discard;
    }


    state ^= floatBitsToUint(d);
    rand();

    vec3 hitPos = origin + direction.xyz*d;

    Quad dump2;
    vec4 dump;
    rayQueryEXT rayQuery2;
    bool unused;
    trace(rayQuery2, hitPos+vec3(0.0,0.01,0), vec3(0.7,0.5,0.1), 1024.0, d, dump, dump2, unused);

    if (d<1000) {
        color*=0.5;
    }


    float ao = 1;

    // BROKEN AO
    for (int i = 0; i < 4; i++) {
        state ^= i<<4;
        rand();
        rayQueryEXT rayQuery3;
        float dist;
        trace(rayQuery3, hitPos, randomDirection(quad.normal.xyz), 1, dist, dump, dump2, unused);
        if (dist<0.49) {
            ao += (1-dist)*2.5/32.0;
        }
    }
    color *= 1.0/ao;
    //color = quad.normal/2+0.5;

    //color = vec4(((r>>0)&15)/15.0, ((r>>4)&15)/15.0,((r>>8)&15)/15.0,1)*((d>500)?1:0.25);
}
