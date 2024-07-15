package net.caffeinemc.mods.sodium.client.render.chunk.terrain;

import net.minecraft.client.renderer.RenderType;

public class TerrainRenderPass {
    @Deprecated(forRemoval = true)
    private final RenderType renderType;

    @Deprecated(forRemoval = true)
    private final RenderType[] replacementTypes;

    private final boolean isTranslucent;
    private final boolean fragmentDiscard;

    public TerrainRenderPass(RenderType renderType, RenderType[] replacementTypes, boolean isTranslucent, boolean allowFragmentDiscard) {
        this.renderType = renderType;
        this.replacementTypes = replacementTypes;

        this.isTranslucent = isTranslucent;
        this.fragmentDiscard = allowFragmentDiscard;
    }

    public boolean isTranslucent() {
        return this.isTranslucent;
    }

    @Deprecated
    public void startDrawing() {
        this.renderType.setupRenderState();
    }

    @Deprecated
    public void endDrawing() {
        this.renderType.clearRenderState();
    }

    public boolean supportsFragmentDiscard() {
        return this.fragmentDiscard;
    }

    public RenderType[] getVanillaLayers() {
        return replacementTypes;
    }
}
