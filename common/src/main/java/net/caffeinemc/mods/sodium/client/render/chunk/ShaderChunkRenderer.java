package net.caffeinemc.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.mixin.core.CommandEncoderAccessor;
import net.caffeinemc.mods.sodium.mixin.core.GlCommandEncoderAccessor;
import net.caffeinemc.mods.sodium.mixin.core.GpuDeviceAccessor;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private static final Map<TerrainRenderPass, RenderPipeline> programs = new Object2ObjectOpenHashMap<>();

    protected final ChunkVertexType vertexType;
    protected final VertexFormat vertexFormat;

    protected final RenderDevice device;

    protected RenderPipeline activeProgram;

    public ShaderChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        this.device = device;
        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getVertexFormat();
    }

    protected RenderPipeline compileProgram(TerrainRenderPass options) {
        RenderPipeline program = this.programs.get(options);

        if (program == null) {
            this.programs.put(options, program = this.createShader("blocks/block_layer_opaque", options));
        }

        return program;
    }

    private RenderPipeline createShader(String path, TerrainRenderPass options) {
        var builder = RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("ChunkUniforms", UniformType.UNIFORM_BUFFER)
                        .withUniform("ChunkData", UniformType.UNIFORM_BUFFER)
                        .withUniform("PosBuffer", UniformType.TEXEL_BUFFER, GpuFormat.RG32_UINT)
                        .withSampler("u_BlockTex").withSampler("u_LightTex").build())
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                .withColorTargetState(new ColorTargetState(
                        options.isTranslucent() ? Optional.of(BlendFunction.TRANSLUCENT) : Optional.empty(), GpuFormat.RGBA8_UNORM, 0xFFFFFFFF))
                .withFragmentShader(Identifier.tryParse("sodium:blocks/block_layer_opaque"))
                .withLocation(Identifier.fromNamespaceAndPath("sodium", path))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(true)
                .withShaderDefine("USE_FOG")
                .withShaderDefine("USE_VERTEX_COMPRESSION")
                .withVertexBinding(0, CompactChunkVertex.VERTEX_FORMAT)
                .withVertexShader(Identifier.tryParse("sodium:blocks/block_layer_opaque"));

        if (options.supportsFragmentDiscard()) {
            builder.withShaderDefine("USE_FRAGMENT_DISCARD");
        }

        return builder.build();
    }

    protected void begin(TerrainRenderPass pass, FogParameters parameters, GpuSampler terrainSampler, GpuBuffer posBuffer, GpuBuffer ubo) {

        var options = pass;

        this.activeProgram = this.compileProgram(options);
    }

    protected void end(TerrainRenderPass pass) {
        this.activeProgram = null;
    }

    @Override
    public void delete(CommandList commandList) {
    }

}
