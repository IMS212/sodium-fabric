package net.caffeinemc.mods.sodium.client.world;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.renderer.culling.Frustum;

public interface LevelRendererExtension {
    SodiumWorldRenderer sodium$getWorldRenderer();
    Frustum sodium$getFrustum();
    int sodium$getTickCount();
}
