/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.jellysquid.mods.sodium.client.frapi.render;

import me.jellysquid.mods.sodium.client.frapi.SodiumRenderer;
import me.jellysquid.mods.sodium.client.frapi.helper.ColorHelper;
import me.jellysquid.mods.sodium.client.frapi.mesh.EncodingFormat;
import me.jellysquid.mods.sodium.client.frapi.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.model.light.LightMode;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.immediate.model.BakedModelEncoder;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NonTerrainBlockRenderContext extends AbstractBlockRenderContext {
    private final BlockColors blockColorMap = MinecraftClient.getInstance().getBlockColors();
    private final SingleBlockLightDataCache lightDataCache = new SingleBlockLightDataCache();

    // TODO: is this seriously hardcoded for the whole render?
    // TODO: convert once to VertexBufferWriter
    // Holders for state used in FRAPI as we can't pass them via parameters
    private VertexBufferWriter vertexWriter;
    private MatrixStack.Entry matrixEntry;
    private int overlay;
    // Default AO mode for model (can be overridden by material property)
    private LightMode defaultLightMode;
    private final BlockPos.Mutable cullSearchPos = new BlockPos.Mutable();

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

	private final BakedModelConsumerImpl bakedModelConsumer = new BakedModelConsumerImpl();

    public NonTerrainBlockRenderContext() {
        this.lighters = new LightPipelineProvider(this.lightDataCache);
        this.ctx = new BlockRenderContext(null);
    }

    public void renderModel(BlockRenderView blockView, BakedModel model, BlockState state, BlockPos pos, MatrixStack matrixStack, VertexConsumer buffer, boolean cull, Random random, long seed, int overlay) {
        // Store parameters
        this.vertexWriter = VertexBufferWriter.of(buffer);
        this.matrixEntry = matrixStack.peek();
        this.overlay = overlay;

        // Clear old state
        this.resetCullState(cull);
        this.lightDataCache.reset(pos, blockView);

        // Prepare
        this.ctx.updateWorld(blockView);
        this.ctx.update(pos, BlockPos.ORIGIN, state, model, seed);
        this.defaultLightMode = this.getLightingMode(ctx.state(), ctx.model());

        // Actually render
        model.emitBlockQuads(blockView, state, pos, this.randomSupplier, this);

        // Avoid dangling references
        this.vertexWriter = null;
        this.ctx.updateWorld(null);
    }

    private void renderQuad(MutableQuadViewImpl quad) {
        if (!transform(quad)) {
            return;
        }

        if (!isFaceVisible(this.ctx, quad.cullFace())) {
            return;
        }

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

        BlockRenderContext ctx = this.ctx;

        colorizeQuad(ctx, quad, colorIndex);
        QuadLightData lightData = this.cachedQuadLightData;
        shadeQuad(ctx, quad, lightMode, emissive, lightData);
        applyBrightness(quad, lightData.br);
        bufferQuad(quad);
    }

    /** handles block color, common to all renders. */
    private void colorizeQuad(BlockRenderContext ctx, MutableQuadViewImpl quad, int colorIndex) {
        if (colorIndex != -1) {
            final int blockColor = 0xFF000000 | this.blockColorMap.getColor(ctx.state(), ctx.world(), ctx.pos(), colorIndex);

            for (int i = 0; i < 4; i++) {
                quad.color(i, ColorHelper.multiplyColor(blockColor, quad.color(i)));
            }
        }
    }

    private void applyBrightness(MutableQuadViewImpl quad, float[] brightness) {
        for (int i = 0; i < 4; i++) {
            quad.color(i, ColorHelper.multiplyRGB(quad.color(i), brightness[i]));
        }
    }

    private void bufferQuad(MutableQuadViewImpl quad) {
        BakedModelEncoder.writeQuadVertices(this.vertexWriter, this.matrixEntry, quad, this.overlay);

        SpriteUtil.markSpriteActive(quad.getSprite(this.spriteFinder));
    }

    @Override
	public QuadEmitter getEmitter() {
		editorQuad.clear();
		return editorQuad;
	}

	@Override
	public BakedModelConsumer bakedModelConsumer() {
		return bakedModelConsumer;
	}

	/**
	 * Consumer for vanilla baked models. Generally intended to give visual results matching a vanilla render,
	 * however there could be subtle (and desirable) lighting variations so is good to be able to render
	 * everything consistently.
	 *
	 * <p>Also, the API allows multi-part models that hold multiple vanilla models to render them without
	 * combining quad lists, but the vanilla logic only handles one model per block. To route all of
	 * them through vanilla logic would require additional hooks.
	 */
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
            BlockRenderContext ctx = NonTerrainBlockRenderContext.this.ctx;

			MutableQuadViewImpl editorQuad = this.editorQuad;
			final RenderMaterial defaultMaterial = model.useAmbientOcclusion() ? MATERIAL_SHADED : MATERIAL_FLAT;

			for (int i = 0; i <= ModelHelper.NULL_FACE_ID; i++) {
				final Direction cullFace = ModelHelper.faceFromIndex(i);
				final List<BakedQuad> quads = model.getQuads(state, cullFace, prepareRandom(ctx));
				final int count = quads.size();

				for (int j = 0; j < count; j++) {
					final BakedQuad q = quads.get(j);
					editorQuad.fromVanilla(q, defaultMaterial, cullFace);
					// Call renderQuad directly instead of emit for efficiency
					renderQuad(editorQuad);
				}
			}

			// Do not clear the editorQuad since it is not accessible to API users.
		}
	}
}
