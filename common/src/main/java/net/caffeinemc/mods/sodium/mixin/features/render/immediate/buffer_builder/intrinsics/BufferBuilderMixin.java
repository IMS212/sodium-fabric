package net.caffeinemc.mods.sodium.mixin.features.render.immediate.buffer_builder.intrinsics;

import com.mojang.blaze3d.vertex.*;
import net.caffeinemc.mods.sodium.api.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.render.immediate.model.BakedModelEncoder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@SuppressWarnings({ "SameParameterValue" })
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderMixin implements VertexConsumer {
    @Shadow
    @Final
    private boolean fastFormat;

    @Shadow
    @Final
    private boolean fullFormat;

    @Override
    public void putBulkData(final PoseStack.Pose pose, final BakedQuad bakedQuad, final QuadBrightness brightness, final int color, final QuadLightmapCoords lightmapCoord, final int overlayCoords) {
        if (!this.fastFormat) {
            VertexConsumer.super.putBulkData(pose, bakedQuad, brightness, color, lightmapCoord, overlayCoords);

            if (bakedQuad.spriteInfo().sprite() != null) {
                SpriteUtil.INSTANCE.markSpriteActive(bakedQuad.spriteInfo().sprite());
            }

            return;
        }

        VertexBufferWriter writer = VertexBufferWriter.of(this);

        ModelQuadView quad = (ModelQuadView) (Object) bakedQuad;

        BakedModelEncoder.writeQuadVertices(writer, pose, quad, color, brightness, lightmapCoord, overlayCoords, this.fullFormat);

        if (quad.getSprite() != null) {
            SpriteUtil.INSTANCE.markSpriteActive(quad.getSprite());
        }
    }
}
