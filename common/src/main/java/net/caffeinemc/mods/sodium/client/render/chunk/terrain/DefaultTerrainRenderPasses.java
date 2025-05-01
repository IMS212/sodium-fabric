package net.caffeinemc.mods.sodium.client.render.chunk.terrain;

import net.minecraft.client.renderer.RenderType;

public class DefaultTerrainRenderPasses {
    public static final TerrainRenderPass SOLID = new TerrainRenderPass("solid", RenderType.solid(), false, false);
    public static final TerrainRenderPass CUTOUT = new TerrainRenderPass("cutout", RenderType.cutoutMipped(), false, true);
    public static final TerrainRenderPass TRANSLUCENT = new TerrainRenderPass("translucent", RenderType.translucent(), true, false);


    public static final TerrainRenderPass[] ALL = new TerrainRenderPass[] { SOLID, CUTOUT, TRANSLUCENT };
}
