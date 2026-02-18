layout(scalar, push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    mat4 u_ModelViewMatrix;
    vec3 u_RegionOffset;
    uint u_CurrentTime;
    float u_FadePeriodInv;
    vec4 u_FogColor; // The color of the shader fog
    vec2 u_EnvironmentFog; // The start and end position for environmental fog
    vec2 u_RenderFog; // The start and end position for border fog
    vec2 u_TexCoordShrink;
    vec2 u_TexelSize;
    bool u_UseRGSS;
};
