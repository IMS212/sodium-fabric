package net.caffeinemc.mods.sodium.mixin.features.textures.animations.tracking;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexBufferExtension;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
public class VertexBufferMixin implements VertexBufferExtension {
    private TextureAtlasSprite[] sprites = new TextureAtlasSprite[0];

    @Override
    public TextureAtlasSprite[] getSprites() {
        return sprites;
    }

    @Override
    public void setSprites(TextureAtlasSprite[] array) {
        this.sprites = array;
    }

    @Inject(method = "upload", at = @At("RETURN"))
    private void sodium$setMeshDataSprites(MeshData meshData, CallbackInfo ci) {
        setSprites(((VertexBufferExtension) meshData).getSprites());
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void sodium$markSpritesActive(CallbackInfo ci) {
        for (int i = 0, spritesLength = sprites.length; i < spritesLength; i++) {
            SpriteUtil.markSpriteActive(sprites[i]);
        }
    }
}
