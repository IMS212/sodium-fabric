package net.caffeinemc.mods.sodium.client.render.chunk.terrain;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.resources.ResourceLocation;

public class DefaultTerrainRenderPasses {
    public static final TerrainRenderPass SOLID = new TerrainRenderPass(ChunkSectionLayer.SOLID, false, false, RenderPipeline.builder()
            .withoutBlend()
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withLocation(ResourceLocation.fromNamespaceAndPath("sodium", "solid"))
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("sodium", "core/vertex"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("sodium", "core/fragment"))
            .withVertexFormat(CompactChunkVertex.VERTEX_FORMAT_MC, VertexFormat.Mode.QUADS)
            .build());
    public static final TerrainRenderPass CUTOUT = new TerrainRenderPass(ChunkSectionLayer.CUTOUT_MIPPED, false, true, RenderPipeline.builder()
            .withoutBlend()
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withLocation(ResourceLocation.fromNamespaceAndPath("sodium", "solid"))
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("sodium", "core/vertex"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("sodium", "core/fragment"))
            .withShaderDefine("USE_FRAGMENT_DISCARD")
            .withVertexFormat(CompactChunkVertex.VERTEX_FORMAT_MC, VertexFormat.Mode.QUADS)
            .build());
    public static final TerrainRenderPass TRANSLUCENT = new TerrainRenderPass(ChunkSectionLayer.TRANSLUCENT, true, false, RenderPipeline.builder()
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withLocation(ResourceLocation.fromNamespaceAndPath("sodium", "solid"))
            .withVertexShader(ResourceLocation.fromNamespaceAndPath("sodium", "core/vertex"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath("sodium", "core/fragment"))
            .withShaderDefine("TRANSLUCENT_PASS")
            .withVertexFormat(CompactChunkVertex.VERTEX_FORMAT_MC, VertexFormat.Mode.QUADS)
            .build());


    public static final TerrainRenderPass[] ALL = new TerrainRenderPass[] { SOLID, CUTOUT, TRANSLUCENT };
}
