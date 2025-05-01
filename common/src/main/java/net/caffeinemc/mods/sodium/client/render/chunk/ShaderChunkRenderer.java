package net.caffeinemc.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import graphics.cinnabar.core.b3d.pipeline.CinnabarPipeline;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexFormat;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.*;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.gl.shader.*;
import net.caffeinemc.mods.sodium.mixin.core.GlCommandEncoderAccessor;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.function.BiFunction;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private final Map<ChunkShaderOptions, CinnabarPipeline> programs = new Object2ObjectOpenHashMap<>();

    protected final ChunkVertexType vertexType;
    protected final GlVertexFormat vertexFormat;

    protected final RenderDevice device;

    protected CinnabarPipeline activeProgram;

    public ShaderChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        this.device = device;
        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getVertexFormat();
    }

    protected CinnabarPipeline compileProgram(ChunkShaderOptions options) {
        CinnabarPipeline program = this.programs.get(options);

        if (program == null) {
            RenderPipeline.Builder builder = RenderPipeline.builder().withLocation(ResourceLocation.fromNamespaceAndPath("sodium", "opaque_" + options.pass().toString()))
                    .withVertexShader("f" + options.pass().toString()).withFragmentShader("f" + options.pass().toString()).withSampler("u_BlockTex").withSampler("u_LightTex");
            if (options.pass().isTranslucent()) {
                builder.withBlend(BlendFunction.TRANSLUCENT);
            }
            program = new CinnabarPipeline(SodiumClientMod.getDevice(), this.createShader("blocks/block_layer_opaque", options),
                    builder.withDepthWrite(true).withColorWrite(true).withVertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS).build(),
                    ChunkMeshFormats.COMPACT.getVertexFormat().getDesc());
            this.programs.put(options, program);
        }

        return program;
    }

    private BiFunction<ResourceLocation, com.mojang.blaze3d.shaders.ShaderType, String> createShader(String path, ChunkShaderOptions options) {
        ShaderConstants constants = options.constants();

        return (loc, type) -> {
            if (type == com.mojang.blaze3d.shaders.ShaderType.VERTEX) {
                return ShaderLoader.loadShader(ShaderType.VERTEX,
                        ResourceLocation.fromNamespaceAndPath("sodium", path + ".vsh"), constants);
            } else if (type == com.mojang.blaze3d.shaders.ShaderType.FRAGMENT) {
                return ShaderLoader.loadShader(ShaderType.FRAGMENT,
                        ResourceLocation.fromNamespaceAndPath("sodium", path + ".fsh"), constants).replace("MAYBE_DISCARD", options.pass().supportsFragmentDiscard() ? "discard;" : "");
            } else {
                throw new IllegalArgumentException("Unknown shader type: " + type);
            }
        };
    }

    protected void begin(TerrainRenderPass pass) {
        pass.startDrawing();

        RenderTarget target = pass.getTarget();

        ChunkShaderOptions options = new ChunkShaderOptions(ChunkFogMode.SMOOTH, pass, this.vertexType);

       /* this.activeProgram.bind();
        this.activeProgram.getInterface()
                .setupState();*/
    }

    protected void end(TerrainRenderPass pass) {
        /*this.activeProgram.getInterface()
                .resetState();
        this.activeProgram.unbind();*/
        this.activeProgram = null;

        pass.endDrawing();
    }

    @Override
    public void delete(CommandList commandList) {
        this.programs.values()
                .forEach(t -> SodiumClientMod.getDevice().destroyEndOfFrame(t));
    }

}
