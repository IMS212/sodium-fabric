package net.caffeinemc.mods.sodium.client.render.immediate.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadBrightness;
import com.mojang.blaze3d.vertex.QuadLightmapCoords;
import net.caffeinemc.mods.sodium.api.util.ColorMixer;
import net.caffeinemc.mods.sodium.api.vertex.format.common.BlockVertex;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorU8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public class BakedModelEncoder {
    private static int mergeLighting(int stored, int calculated) {
        if (stored == 0) return calculated;

        int blockLight = Math.max(stored & 0xFFFF, calculated & 0xFFFF);
        int skyLight = Math.max((stored >> 16) & 0xFFFF, (calculated >> 16) & 0xFFFF);
        return blockLight | (skyLight << 16);
    }

    private static final boolean MULTIPLY_ALPHA = PlatformRuntimeInformation.getInstance().usesAlphaMultiplication();

    public static void writeQuadVertices(VertexBufferWriter writer, PoseStack.Pose matrices, ModelQuadView quad, int color, QuadBrightness brightness, QuadLightmapCoords lightmapCoord, int overlayCoords, boolean writeEntity) {
        Matrix3f matNormal = matrices.normal();
        Matrix4f matPosition = matrices.pose();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.nmalloc(4 * (writeEntity ? EntityVertex.STRIDE : BlockVertex.STRIDE));
            long ptr = buffer;

            for (int i = 0; i < 4; i++) {
                // The position vector
                float x = quad.getX(i);
                float y = quad.getY(i);
                float z = quad.getZ(i);

                // The transformed position vector
                float xt = MatrixHelper.transformPositionX(matPosition, x, y, z);
                float yt = MatrixHelper.transformPositionY(matPosition, x, y, z);
                float zt = MatrixHelper.transformPositionZ(matPosition, x, y, z);

                var normal = MatrixHelper.transformNormal(matNormal, matrices.trustedNormals, quad.getAccurateNormal(i));

                int vertexColor = brightness.scaleColor(i, color);
                int light = lightmapCoord.composeWithEmission(i, quad.getLightEmission());
                if (writeEntity) {
                    EntityVertex.write(ptr, xt, yt, zt, vertexColor, quad.getTexU(i), quad.getTexV(i), overlayCoords, light, normal);
                } else {
                    BlockVertex.write(ptr, xt, yt, zt, vertexColor, quad.getTexU(i), quad.getTexV(i), light);
                }
                ptr += writeEntity ? EntityVertex.STRIDE : BlockVertex.STRIDE;
            }

            writer.push(stack, buffer, 4, writeEntity ? EntityVertex.FORMAT : BlockVertex.FORMAT);
        }
    }

    public static boolean shouldMultiplyAlpha() {
        return MULTIPLY_ALPHA;
    }
}
