package net.caffeinemc.mods.sodium.client.render.vertex;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public interface VertexBufferExtension {
    TextureAtlasSprite[] getSprites();

    void setSprites(TextureAtlasSprite[] array);
}
