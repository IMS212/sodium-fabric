package me.jellysquid.mods.sodium.client.frapi.render;

import me.jellysquid.mods.sodium.client.frapi.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.model.light.*;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.util.math.random.Random;

import java.util.function.Supplier;

/**
 * Base class for the few functions that can be shared between the terrain and non-terrain pipelines.
 *
 * <p>Make sure to set the {@link #lighters} in the subclass constructor.
 */
public abstract class AbstractBlockRenderContext extends AbstractRenderContext {
    protected final Random random = new LocalRandom(42L);
    protected final QuadLightData cachedQuadLightData = new QuadLightData();

    protected final boolean useAmbientOcclusion;

    protected LightPipelineProvider lighters;

    protected BlockRenderContext ctx;

    protected final Supplier<Random> randomSupplier = () -> prepareRandom(this.ctx);

    protected AbstractBlockRenderContext() {
        // TODO: is it problematic to cache this value here?
        this.useAmbientOcclusion = MinecraftClient.isAmbientOcclusionEnabled();
    }

    protected LightMode getLightingMode(BlockState state, BakedModel model) {
        if (this.useAmbientOcclusion && model.useAmbientOcclusion() && state.getLuminance() == 0) {
            return LightMode.SMOOTH;
        } else {
            return LightMode.FLAT;
        }
    }

    protected Random prepareRandom(BlockRenderContext ctx) {
        var random = this.random;
        random.setSeed(ctx.seed());
        return random;
    }

    /**
     * @param lightData Just use {@link #cachedQuadLightData}, but pass it as a parameter to avoid a field lookup.
     */
    protected void shadeQuad(BlockRenderContext ctx, MutableQuadViewImpl quad, LightMode lightMode, boolean emissive, QuadLightData lightData) {
        // TODO: do we want normal-based diffuse shading like in Indigo?
        // TODO: do we want to port enhanced AO from Indigo to the smooth pipeline?

        LightPipeline lighter = this.lighters.getLighter(lightMode);
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
}
