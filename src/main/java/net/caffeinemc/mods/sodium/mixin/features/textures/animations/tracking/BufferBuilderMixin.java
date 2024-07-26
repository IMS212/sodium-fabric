package net.caffeinemc.mods.sodium.mixin.features.textures.animations.tracking;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.caffeinemc.mods.sodium.client.render.vertex.BufferBuilderExtension;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexBufferExtension;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin implements BufferBuilderExtension {
    @Unique
    private final ObjectArraySet<TextureAtlasSprite> sprites = new ObjectArraySet<>();

    @Inject(method = "build", at = @At("RETURN"))
    private void addDataToMesh(CallbackInfoReturnable<MeshData> cir) {
        if (cir.getReturnValue() != null) {
            ((VertexBufferExtension) cir.getReturnValue()).setSprites(sprites.toArray(TextureAtlasSprite[]::new));
        }

        sprites.clear();
    }

    @Override
    public void addSprite(TextureAtlasSprite sprite) {
        sprites.add(sprite);
    }
}
