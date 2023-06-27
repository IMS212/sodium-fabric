package me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline;

import me.jellysquid.mods.sodium.client.frapi.SodiumRenderer;
import me.jellysquid.mods.sodium.client.frapi.helper.ColorHelper;
import me.jellysquid.mods.sodium.client.frapi.mesh.EncodingFormat;
import me.jellysquid.mods.sodium.client.frapi.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.frapi.render.AbstractRenderContext;
import me.jellysquid.mods.sodium.client.model.light.*;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.blender.BiomeColorBlenderFRAPI;
import me.jellysquid.mods.sodium.client.model.quad.blender.ColorSampler;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.util.ModelQuadUtil;
import me.jellysquid.mods.sodium.client.world.biome.BlockColorsExtended;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BlockRendererFRAPI implements IBlockRenderer {
    // TODO: can be removed as it's now in the BlockRenderContext
    private final Random random = new LocalRandom(42L);

    private final BlockColorsExtended blockColors;
    private final BlockOcclusionCache occlusionCache;

    private final QuadLightData cachedQuadLightData = new QuadLightData();

    private final BiomeColorBlenderFRAPI biomeColorBlender;
    private final LightPipelineProviderFRAPI lighters;

    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();

    private final boolean useAmbientOcclusion;

    // Holders for state used in FRAPI as we can't pass them via parameters
    private BlockRenderContext ctx;
    private ChunkBuildBuffers buffers;
    private ChunkRenderBounds.Builder bounds;
    // Offset of model
    private Vec3d renderOffset;
    // Default AO mode for model (can be overridden by material property)
    private LightMode defaultLightMode;
    // Default material (can be overridden by blend mode per-quad)
    private Material defaultMaterial;
    // Cull cache (as it's checked per-quad instead of once in vanilla)
    private int cullCompletionFlags;
    private int cullResultFlags;
    // Color sampler cache
    @Nullable
    ColorSampler<BlockState> colorSampler;

    private final Context renderContext = new Context();

    public BlockRendererFRAPI(MinecraftClient client, LightPipelineProviderFRAPI lighters, BiomeColorBlenderFRAPI biomeColorBlender) {
        this.blockColors = (BlockColorsExtended) client.getBlockColors();
        this.biomeColorBlender = biomeColorBlender;

        this.lighters = lighters;

        this.occlusionCache = new BlockOcclusionCache();
        this.useAmbientOcclusion = MinecraftClient.isAmbientOcclusionEnabled();
    }

    public void renderModel(BlockRenderContext ctx, ChunkBuildBuffers buffers, ChunkRenderBounds.Builder bounds) {
        // Store parameters
        this.ctx = ctx;
        this.buffers = buffers;
        this.bounds = bounds;

        // Clear old state
        this.cullCompletionFlags = 0;
        this.cullResultFlags = 0;
        this.colorSampler = null;

        // Prepare
        this.renderOffset = ctx.state().getModelOffset(ctx.world(), ctx.pos());
        this.defaultLightMode = this.getLightingMode(ctx.state(), ctx.model());
        this.defaultMaterial = DefaultMaterials.forBlockState(ctx.state());

        // Actually render
        ctx.model().emitBlockQuads(ctx.world(), ctx.state(), ctx.pos(), ctx.randomSupplier, this.renderContext);
    }

    private boolean isFaceVisible(BlockRenderContext ctx, @Nullable Direction face) {
        if (face == null) {
            return true;
        }

        final int mask = 1 << face.getId();

        if ((this.cullCompletionFlags & mask) == 0) {
            this.cullCompletionFlags |= mask;

            if (this.occlusionCache.shouldDrawSide(ctx.state(), ctx.world(), ctx.pos(), face)) {
                this.cullResultFlags |= mask;
                return true;
            } else {
                return false;
            }
        } else {
            return (this.cullResultFlags & mask) != 0;
        }
    }

    private LightMode getLightingMode(BlockState state, BakedModel model) {
        if (this.useAmbientOcclusion && model.useAmbientOcclusion() && state.getLuminance() == 0) {
            return LightMode.SMOOTH;
        } else {
            return LightMode.FLAT;
        }
    }

    /**
     * Process quad, after quad transforms and the culling check have been applied.
     */
    private void processQuad(MutableQuadViewImpl quad) {
        final RenderMaterial mat = quad.material();
        final int colorIndex = mat.disableColorIndex() ? -1 : quad.colorIndex();
        final TriState aoMode = mat.ambientOcclusion();
        final LightMode lightMode;
        if (aoMode == TriState.DEFAULT) {
            lightMode = this.defaultLightMode;
        } else {
            lightMode = this.useAmbientOcclusion && aoMode.get() ? LightMode.SMOOTH : LightMode.FLAT;
        }
        final boolean emissive = mat.emissive();
        final BlendMode blendMode = mat.blendMode();
        final Material material;
        if (blendMode == BlendMode.DEFAULT) {
            material = this.defaultMaterial;
        } else {
            material = DefaultMaterials.forRenderLayer(blendMode.blockRenderLayer);
        }

        BlockRenderContext ctx = this.ctx;

        colorizeQuad(ctx, quad, colorIndex);
        QuadLightData lightData = this.cachedQuadLightData;
        shadeQuad(ctx, quad, lightMode, emissive, lightData);
        bufferQuad(ctx, quad, lightData.br, material);
    }

    private void colorizeQuad(BlockRenderContext ctx, MutableQuadViewImpl quad, int colorIndex) {
        if (colorIndex != -1) {
            ColorSampler<BlockState> colorizer = this.colorSampler;

            if (this.colorSampler == null) {
                this.colorSampler = colorizer = this.blockColors.getColorProvider(ctx.state());
            }

            int[] colors = this.biomeColorBlender.getColors(ctx.world(), ctx.pos(), quad, colorizer, ctx.state());

            for (int i = 0; i < 4; i++) {
                quad.color(i, ColorHelper.multiplyColor(colors[i], quad.color(i)));
            }
        }
    }

    private void shadeQuad(BlockRenderContext ctx, MutableQuadViewImpl quad, LightMode lightMode, boolean emissive, QuadLightData lightData) {
        LightPipelineFRAPI lighter = this.lighters.getLighter(lightMode);
        lighter.calculate(quad, ctx.pos(), lightData, quad.cullFace(), quad.lightFace(), quad.hasShade());

        // routines below have a bit of copy-paste code reuse to avoid conditional execution inside a hot loop
        if (emissive) {
            for (int i = 0; i < 4; i++) {
                quad.lightmap(i, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                quad.lightmap(i, lightData.lm[i]);
            }
        }
    }

    private void bufferQuad(BlockRenderContext ctx, MutableQuadViewImpl quad, float[] brightness, Material material) {
        ModelQuadOrientation orientation = ModelQuadOrientation.orientByBrightness(brightness);
        ChunkVertexEncoder.Vertex[] vertices = this.vertices;

        // TODO: this should be precomputed and stored in the QuadViewImpl
        ModelQuadFacing normalFace = ModelQuadUtil.findNormalFace(quad.packedFaceNormal());
        Vec3d offset = this.renderOffset;
        ChunkRenderBounds.Builder bounds = this.bounds;

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = orientation.getVertexIndex(dstIndex);

            var out = vertices[dstIndex];
            out.x = ctx.origin().x() + quad.x(srcIndex) + (float) offset.getX();
            out.y = ctx.origin().y() + quad.y(srcIndex) + (float) offset.getY();
            out.z = ctx.origin().z() + quad.z(srcIndex) + (float) offset.getZ();

            // TODO: alpha from quad is ignored entirely
            // TODO: do we need endianness changes to color? (seems ok from tests)
            out.color = ColorABGR.withAlpha(quad.color(srcIndex), brightness[srcIndex]);

            out.u = quad.u(srcIndex);
            out.v = quad.v(srcIndex);

            out.light = quad.lightmap(srcIndex);

            bounds.add(out.x, out.y, out.z, normalFace);
        }

        var vertexBuffer = buffers.get(material).getVertexBuffer(normalFace);
        vertexBuffer.push(vertices, material);
    }

    private class Context extends AbstractRenderContext {
        private void renderQuad(MutableQuadViewImpl quad) {
            if (!transform(quad)) {
                return;
            }

            if (!isFaceVisible(ctx, quad.cullFace())) {
                return;
            }

            processQuad(quad);
        }

        private final MutableQuadViewImpl editorQuad = new MutableQuadViewImpl() {
            {
                data = new int[EncodingFormat.TOTAL_STRIDE];
                clear();
            }

            @Override
            public void emitDirectly() {
                renderQuad(this);
            }
        };
        private final BakedModelConsumer bakedModelConsumer = new BakedModelConsumerImpl();

        @Override
        public QuadEmitter getEmitter() {
            editorQuad.clear();
            return editorQuad;
        }

        @Override
        public BakedModelConsumer bakedModelConsumer() {
            return bakedModelConsumer;
        }

        private class BakedModelConsumerImpl implements BakedModelConsumer {
            private static final RenderMaterial MATERIAL_SHADED = SodiumRenderer.INSTANCE.materialFinder().find();
            private static final RenderMaterial MATERIAL_FLAT = SodiumRenderer.INSTANCE.materialFinder().ambientOcclusion(TriState.FALSE).find();

            private final MutableQuadViewImpl editorQuad = new MutableQuadViewImpl() {
                {
                    data = new int[EncodingFormat.TOTAL_STRIDE];
                    clear();
                }

                @Override
                public void emitDirectly() {
                    renderQuad(this);
                }
            };

            @Override
            public void accept(BakedModel model) {
                accept(model, ctx.state());
            }

            @Override
            public void accept(BakedModel model, @Nullable BlockState state) {
                BlockRenderContext ctx = BlockRendererFRAPI.this.ctx;

                MutableQuadViewImpl editorQuad = this.editorQuad;
                final RenderMaterial defaultMaterial = model.useAmbientOcclusion() ? MATERIAL_SHADED : MATERIAL_FLAT;

                // If there is no transform, we can check the culling face once for all the quads,
                // and we don't need to check for transforms per-quad.
                boolean noTransform = !Context.this.hasTransform();

                for (int i = 0; i <= ModelHelper.NULL_FACE_ID; i++) {
                    final Direction cullFace = ModelHelper.faceFromIndex(i);
                    final List<BakedQuad> quads = model.getQuads(state, cullFace, ctx.randomSupplier.get());

                    if (quads.isEmpty()) {
                        continue;
                    }

                    final int count = quads.size();

                    if (noTransform) {
                        if (!isFaceVisible(ctx, cullFace)) {
                            continue;
                        }

                        for (int j = 0; j < count; j++) {
                            final BakedQuad q = quads.get(j);
                            editorQuad.fromVanilla(q, defaultMaterial, cullFace);
                            // Call processQuad directly for efficiency
                            processQuad(editorQuad);
                        }
                    } else {
                        for (int j = 0; j < count; j++) {
                            final BakedQuad q = quads.get(j);
                            editorQuad.fromVanilla(q, defaultMaterial, cullFace);
                            // Call renderQuad directly instead of emit for efficiency
                            renderQuad(editorQuad);
                        }
                    }
                }

                // Do not clear the editorQuad since it is not accessible to API users.
            }
        }
    }
}
