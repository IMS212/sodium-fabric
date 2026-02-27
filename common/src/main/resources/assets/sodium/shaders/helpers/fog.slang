public module fog;

static const int FOG_SHAPE_SPHERICAL = 0;
static const int FOG_SHAPE_CYLINDRICAL = 1;

float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }

    return (vertexDistance - fogStart) / (fogEnd - fogStart);
}

float total_fog_value(float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmantalEnd, float renderDistanceStart, float renderDistanceEnd) {
    return max(linear_fog_value(sphericalVertexDistance, environmentalStart, environmantalEnd), linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd));
}

float4 _linearFog(float4 fragColor, float2 fragDistance, float4 fogColor, float2 environmentFog, float2 renderFog, float fadeFactor) {
    float fogValue = max(1.0 - fadeFactor, total_fog_value(fragDistance.y, fragDistance.x, environmentFog.x, environmentFog.y, renderFog.x, renderFog.y));
    return float4(lerp(fragColor.rgb, fogColor.rgb, fogValue * fogColor.a), fragColor.a);
}

float2 getFragDistance(float3 position) {
    return float2(max(length(position.xz), abs(position.y)), length(position));
}