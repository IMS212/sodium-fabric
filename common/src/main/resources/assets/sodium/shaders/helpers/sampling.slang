public module sampling;

float4 sampleNearest(Sampler2D sampler, float2 uv, float2 pixelSize, float2 du, float2 dv, float2 texelScreenSize) {
    // Convert our UV back up to texel coordinates and find out how far over we are from the center of each pixel
    float2 uvTexelCoords = uv / pixelSize;
    float2 texelCenter = round(uvTexelCoords) - 0.5f;
    float2 texelOffset = uvTexelCoords - texelCenter;

    // Move our offset closer to the texel center based on texel size on screen
    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);

    uv = (texelCenter + texelOffset) * pixelSize;
    return sampler.SampleGrad(uv, du, dv);
}

float4 sampleNearest(Sampler2D source, float2 uv, float2 pixelSize) {
    float2 du = ddx(uv);
    float2 dv = ddy(uv);
    float2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

// Rotated Grid Super-Sampling
float4 sampleRGSS(Sampler2D source, float2 uv, float2 pixelSize) {
    float2 du = ddx(uv);
    float2 dv = ddy(uv);

    float2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);

    float minPixelSize = min(pixelSize.x, pixelSize.y);

    float transitionStart = minPixelSize * 1.0;
    float transitionEnd = minPixelSize * 2.0;
    float blendFactor = smoothstep(transitionStart, transitionEnd, maxTexelSize);

    float duLength = length(du);
    float dvLength = length(dv);
    float minDerivative = min(duLength, dvLength);
    float maxDerivative = max(duLength, dvLength);

    float effectiveDerivative = sqrt(minDerivative * maxDerivative);

    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));

    const float2 offsets[4] = float2[](
        float2(0.125, 0.375),
        float2(-0.125, -0.375),
        float2(0.375, -0.125),
        float2(-0.375, 0.125)
    );

    float4 rgssColor = float4(0.0);
    for (int i = 0; i < 4; ++i) {
        float2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColor += source.SampleLevel(sampleUV, mipLevelExact);
    }
    rgssColor *= 0.25;

    float4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);

    return lerp(nearestColor, rgssColor, blendFactor);
}
