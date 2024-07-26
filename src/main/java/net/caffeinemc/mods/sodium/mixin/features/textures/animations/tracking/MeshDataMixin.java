package net.caffeinemc.mods.sodium.mixin.features.textures.animations.tracking;

import com.mojang.blaze3d.vertex.MeshData;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexBufferExtension;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MeshData.class)
public class MeshDataMixin implements VertexBufferExtension {
    private TextureAtlasSprite[] sprites = new TextureAtlasSprite[0];

    @Override
    public TextureAtlasSprite[] getSprites() {
        return sprites;
    }

    @Override
    public void setSprites(TextureAtlasSprite[] array) {
        this.sprites = array;
    }
}
