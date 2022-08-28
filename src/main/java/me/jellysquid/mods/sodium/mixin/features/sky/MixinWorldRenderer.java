package me.jellysquid.mods.sodium.mixin.features.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.VertexDrain;
import me.jellysquid.mods.sodium.client.model.vertex.formats.clouds.CloudVertexSink;
import me.jellysquid.mods.sodium.client.model.vertex.formats.quad.QuadVertexSink;
import me.jellysquid.mods.sodium.client.util.Norm3b;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private @Nullable CloudRenderMode lastCloudRenderMode;

    /**
     * <p>Prevents the sky layer from rendering when the fog distance is reduced
     * from the default. This helps prevent situations where the sky can be seen
     * through chunks culled by fog occlusion. This also fixes the vanilla issue
     * <a href="https://bugs.mojang.com/browse/MC-152504">MC-152504</a> since it
     * is also caused by being able to see the sky through invisible chunks.</p>
     * 
     * <p>However, this fix comes with some caveats. When underwater, it becomes 
     * impossible to see the sun, stars, and moon since the sky is not rendered.
     * While this does not exactly match the vanilla game, it is consistent with
     * what Bedrock Edition does, so it can be considered vanilla-style. This is
     * also more "correct" in the sense that underwater fog is applied to chunks
     * outside of water, so the fog should also be covering the sun and sky.</p>
     * 
     * <p>When updating Sodium to new releases of the game, please check for new
     * ways the fog can be reduced in {@link BackgroundRenderer#applyFog(Camera, BackgroundRenderer.FogType, float, boolean)} ()}.</p>
     */
    @Inject(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/math/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    private void preRenderSky(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean bl, Runnable runnable, CallbackInfo ci) {
        // Cancels sky rendering when the camera is submersed underwater.
        // This prevents the sky from being visible through chunks culled by Sodium's fog occlusion.
        // Fixes https://bugs.mojang.com/browse/MC-152504.
        // Credit to bytzo for noticing the change in 1.18.2.
        if (camera.getSubmersionType() == CameraSubmersionType.WATER) {
            ci.cancel();
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    private BufferBuilder.BuiltBuffer renderClouds(BufferBuilder builder, double x, double y, double z, Vec3d color) {
        float f = 4.0f;
        float g = 0.00390625f;
        int i = 8;
        int j = 4;
        float h = 9.765625E-4f;
        float k = (float)MathHelper.floor(x) * 0.00390625f;
        float l = (float)MathHelper.floor(z) * 0.00390625f;
        float m = (float)color.x;
        float n = (float)color.y;
        float o = (float)color.z;
        float p = m * 0.9f;
        float q = n * 0.9f;
        float r = o * 0.9f;
        float s = m * 0.7f;
        float t = n * 0.7f;
        float u = o * 0.7f;
        float v = m * 0.8f;
        float w = n * 0.8f;
        float aa = o * 0.8f;

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
        RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);
        CloudVertexSink drain = VertexDrain.of(builder).createSink(VanillaVertexTypes.CLOUDS);
        drain.ensureCapacity(24);
        float ab = (float)Math.floor(y / 4.0) * 4.0f;
        if (this.lastCloudRenderMode == CloudRenderMode.FANCY) {
            int colorABGR = ColorABGR.pack(s, t, u, 0.8f);
            int norm;
            for (int ac = -3; ac <= 4; ++ac) {
                for (int ad = -3; ad <= 4; ++ad) {
                    int ag;
                    float ae = ac * 8;
                    float af = ad * 8;
                    if (ab > -5.0f) {
                        norm = Norm3b.pack(0.0f, -1.0f, 0.0f);
                        drain.writeQuad(ae + 0.0f, ab + 0.0f, af + 8.0f, colorABGR, (ae + 0.0f) * 0.00390625f + k, (af + 8.0f) * 0.00390625f + l, norm);
                        drain.writeQuad(ae + 8.0f, ab + 0.0f, af + 8.0f, colorABGR, (ae + 8.0f) * 0.00390625f + k, (af + 8.0f) * 0.00390625f + l, norm);
                        drain.writeQuad(ae + 8.0f, ab + 0.0f, af + 0.0f, colorABGR, (ae + 8.0f) * 0.00390625f + k, (af + 0.0f) * 0.00390625f + l, norm);
                        drain.writeQuad(ae + 0.0f, ab + 0.0f, af + 0.0f, colorABGR, (ae + 0.0f) * 0.00390625f + k, (af + 0.0f) * 0.00390625f + l, norm);
                    }
                    if (ab <= 5.0f) {
                        colorABGR = ColorABGR.pack(m, n, o, 0.8f);
                        norm = Norm3b.pack(0.0f, 1.0f, 0.0f);
                        drain.writeQuad(ae + 0.0f, ab + 4.0f - 9.765625E-4f, af + 8.0f, colorABGR, (ae + 0.0f) * 0.00390625f + k, (af + 8.0f) * 0.00390625f + l, norm);
                        drain.writeQuad(ae + 8.0f, ab + 4.0f - 9.765625E-4f, af + 8.0f, colorABGR, (ae + 8.0f) * 0.00390625f + k, (af + 8.0f) * 0.00390625f + l, norm);
                        drain.writeQuad(ae + 8.0f, ab + 4.0f - 9.765625E-4f, af + 0.0f, colorABGR, (ae + 8.0f) * 0.00390625f + k, (af + 0.0f) * 0.00390625f + l, norm);
                        drain.writeQuad(ae + 0.0f, ab + 4.0f - 9.765625E-4f, af + 0.0f, colorABGR, (ae + 0.0f) * 0.00390625f + k, (af + 0.0f) * 0.00390625f + l, norm);
                    }
                    if (ac > -1) {
                        norm = Norm3b.pack(-1.0f, 0.0f, 0.0f);
                        colorABGR = ColorABGR.pack(p, q, r, 0.8f);
                        for (ag = 0; ag < 8; ++ag) {
                            drain.writeQuad(ae + (float)ag + 0.0f, ab + 0.0f, af + 8.0f, colorABGR, (ae + (float)ag + 0.5f) * 0.00390625f + k, (af + 8.0f) * 0.00390625f + l, norm);
                            drain.writeQuad(ae + (float)ag + 0.0f, ab + 4.0f, af + 8.0f, colorABGR, (ae + (float)ag + 0.5f) * 0.00390625f + k, (af + 8.0f) * 0.00390625f + l, norm);
                            drain.writeQuad(ae + (float)ag + 0.0f, ab + 4.0f, af + 0.0f, colorABGR, (ae + (float)ag + 0.5f) * 0.00390625f + k, (af + 0.0f) * 0.00390625f + l, norm);
                            drain.writeQuad(ae + (float)ag + 0.0f, ab + 0.0f, af + 0.0f, colorABGR, (ae + (float)ag + 0.5f) * 0.00390625f + k, (af + 0.0f) * 0.00390625f + l, norm);
                        }
                    }
                    if (ac <= 1) {
                        norm = Norm3b.pack(1.0f, 0.0f, 0.0f);
                        colorABGR = ColorABGR.pack(p, q, r, 0.8f);
                        for (ag = 0; ag < 8; ++ag) {
                            drain.writeQuad(ae + (float)ag + 1.0f - 9.765625E-4f, ab + 0.0f, af + 8.0f, colorABGR, (ae + (float)ag + 0.5f) * 0.00390625f + k, (af + 8.0f) * 0.00390625f + l, norm);
                            drain.writeQuad(ae + (float)ag + 1.0f - 9.765625E-4f, ab + 4.0f, af + 8.0f, colorABGR, (ae + (float)ag + 0.5f) * 0.00390625f + k, (af + 8.0f) * 0.00390625f + l, norm);
                            drain.writeQuad(ae + (float)ag + 1.0f - 9.765625E-4f, ab + 4.0f, af + 0.0f, colorABGR, (ae + (float)ag + 0.5f) * 0.00390625f + k, (af + 0.0f) * 0.00390625f + l, norm);
                            drain.writeQuad(ae + (float)ag + 1.0f - 9.765625E-4f, ab + 0.0f, af + 0.0f, colorABGR, (ae + (float)ag + 0.5f) * 0.00390625f + k, (af + 0.0f) * 0.00390625f + l, norm);
                        }
                    }

                    colorABGR = ColorABGR.pack(v, w, aa, 0.8f);

                    if (ad > -1) {
                        norm = Norm3b.pack(0.0f, 0.0f, -1.0f);
                        for (ag = 0; ag < 8; ++ag) {
                            drain.writeQuad(ae + 0.0f, ab + 4.0f, af + (float)ag + 0.0f, colorABGR, (ae + 0.0f) * 0.00390625f + k, (af + (float)ag + 0.5f) * 0.00390625f + l, norm);
                            drain.writeQuad(ae + 8.0f, ab + 4.0f, af + (float)ag + 0.0f, colorABGR, (ae + 8.0f) * 0.00390625f + k, (af + (float)ag + 0.5f) * 0.00390625f + l, norm);
                            drain.writeQuad(ae + 8.0f, ab + 0.0f, af + (float)ag + 0.0f, colorABGR, (ae + 8.0f) * 0.00390625f + k, (af + (float)ag + 0.5f) * 0.00390625f + l, norm);
                            drain.writeQuad(ae + 0.0f, ab + 0.0f, af + (float)ag + 0.0f, colorABGR, (ae + 0.0f) * 0.00390625f + k, (af + (float)ag + 0.5f) * 0.00390625f + l, norm);
                        }
                    }
                    if (ad > 1) continue;
                    norm = Norm3b.pack(0.0f, 0.0f, 1.0f);
                    for (ag = 0; ag < 8; ++ag) {
                        drain.writeQuad(ae + 0.0f, ab + 4.0f, af + (float)ag + 1.0f - 9.765625E-4f, colorABGR, (ae + 0.0f) * 0.00390625f + k, (af + (float)ag + 0.5f) * 0.00390625f + l, norm);
                        drain.writeQuad(ae + 8.0f, ab + 4.0f, af + (float)ag + 1.0f - 9.765625E-4f, colorABGR, (ae + 8.0f) * 0.00390625f + k, (af + (float)ag + 0.5f) * 0.00390625f + l, norm);
                        drain.writeQuad(ae + 8.0f, ab + 0.0f, af + (float)ag + 1.0f - 9.765625E-4f, colorABGR, (ae + 8.0f) * 0.00390625f + k, (af + (float)ag + 0.5f) * 0.00390625f + l, norm);
                        drain.writeQuad(ae + 0.0f, ab + 0.0f, af + (float)ag + 1.0f - 9.765625E-4f, colorABGR, (ae + 0.0f) * 0.00390625f + k, (af + (float)ag + 0.5f) * 0.00390625f + l, norm);
                    }
                }
            }
        } else {
            boolean ac = true;
            int ad = 32;
            int colorABGR = ColorABGR.pack(m, n, o, 0.8f);
            int norm = Norm3b.pack(0.0f, -1.0f, 0.0f);
            for (int ah = -32; ah < 32; ah += 32) {
                for (int ai = -32; ai < 32; ai += 32) {
                    drain.writeQuad(ah + 0, ab, ai + 32, colorABGR, (float)(ah + 0) * 0.00390625f + k, (float)(ai + 32) * 0.00390625f + l, norm);
                    drain.writeQuad(ah + 32, ab, ai + 32, colorABGR, (float)(ah + 32) * 0.00390625f + k, (float)(ai + 32) * 0.00390625f + l, norm);
                    drain.writeQuad(ah + 32, ab, ai + 0, colorABGR, (float)(ah + 32) * 0.00390625f + k, (float)(ai + 0) * 0.00390625f + l, norm);
                    drain.writeQuad(ah + 0, ab, ai + 0, colorABGR, (float)(ah + 0) * 0.00390625f + k, (float)(ai + 0) * 0.00390625f + l, norm);
                }
            }
        }

        drain.flush();
        return builder.end();
    }
}
