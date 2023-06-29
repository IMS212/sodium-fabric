package me.jellysquid.mods.sodium.client.frapi.render;

import me.jellysquid.mods.sodium.client.frapi.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.model.light.*;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Base class for the few functions that can be shared between the terrain and non-terrain pipelines.
 *
 * <p>Make sure to set the {@link #lighters} in the subclass constructor.
 */
public abstract class AbstractBlockRenderContext extends AbstractRenderContext {
    protected BlockRenderContext ctx;

    /* Random handling */
    private final Random random = new LocalRandom(42L);
    protected final Supplier<Random> randomSupplier = () -> prepareRandom(this.ctx);

    protected Random prepareRandom(BlockRenderContext ctx) {
        var random = this.random;
        random.setSeed(ctx.seed());
        return random;
    }

    /* Occlusion handling */
    private final BlockOcclusionCache occlusionCache = new BlockOcclusionCache();
    /**
     * Whether culling is enabled at all.
     */
    private boolean enableCulling = true;
    // Cull cache (as it's checked per-quad instead of once in vanilla)
    private int cullCompletionFlags;
    private int cullResultFlags;

    protected boolean isFaceVisible(BlockRenderContext ctx, @Nullable Direction face) {
        if (face == null || !enableCulling) {
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

    protected void resetCullState(boolean enableCulling) {
        this.enableCulling = enableCulling;
        this.cullCompletionFlags = 0;
        this.cullResultFlags = 0;
    }

    /* Shading handling */
    // TODO: is it problematic to cache this value here?
    protected final boolean useAmbientOcclusion = MinecraftClient.isAmbientOcclusionEnabled();
    protected final QuadLightData cachedQuadLightData = new QuadLightData();
    /**
     * Must be set by the subclass constructor.
     */
    protected LightPipelineProvider lighters;

    protected LightMode getLightingMode(BlockState state, BakedModel model) {
        if (this.useAmbientOcclusion && model.useAmbientOcclusion() && state.getLuminance() == 0) {
            return LightMode.SMOOTH;
        } else {
            return LightMode.FLAT;
        }
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
