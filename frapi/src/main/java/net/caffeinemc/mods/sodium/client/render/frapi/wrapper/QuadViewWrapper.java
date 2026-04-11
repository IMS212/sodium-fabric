package net.caffeinemc.mods.sodium.client.render.frapi.wrapper;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.model.QuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.model.SodiumQuadAtlas;
import net.caffeinemc.mods.sodium.client.render.model.SodiumShadeMode;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import org.joml.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class QuadViewWrapper implements QuadView {
    private static final TriState[] TO_FABRIC = new TriState[] {
        TriState.TRUE, TriState.FALSE, TriState.DEFAULT
    };

    private QuadViewImpl quad;
    private final Vector4f posVec = new Vector4f();
    private final Vector3f normalVec = new Vector3f();
    private final Vector3f normalVec1 = new Vector3f();

    public QuadViewWrapper(QuadViewImpl quad) {
        this.quad = quad;
    }

    @Override
    public float x(int vertexIndex) {
        return quad.getX(vertexIndex);
    }

    @Override
    public float y(int vertexIndex) {
        return quad.getY(vertexIndex);
    }

    @Override
    public float z(int vertexIndex) {
        return quad.getZ(vertexIndex);
    }

    @Override
    public float posByIndex(int vertexIndex, int coordinateIndex) {
        return quad.posByIndex(vertexIndex, coordinateIndex);
    }

    @Override
    public Vector3f copyPos(int vertexIndex, @Nullable Vector3f target) {
        return quad.copyPos(vertexIndex, target);
    }

    @Override
    public int color(int vertexIndex) {
        return quad.getColor(vertexIndex);
    }

    @Override
    public float u(int vertexIndex) {
        return quad.getTexU(vertexIndex);
    }

    @Override
    public float v(int vertexIndex) {
        return quad.getTexV(vertexIndex);
    }

    @Override
    public Vector2f copyUv(int vertexIndex, @Nullable Vector2f target) {
        return quad.copyUv(vertexIndex, target);
    }

    @Override
    public int lightmap(int vertexIndex) {
        return quad.getLight(vertexIndex);
    }

    @Override
    public boolean hasNormal(int vertexIndex) {
        return quad.hasNormal(vertexIndex);
    }

    @Override
    public float normalX(int vertexIndex) {
        return quad.normalX(vertexIndex);
    }

    @Override
    public float normalY(int vertexIndex) {
        return quad.normalY(vertexIndex);
    }

    @Override
    public float normalZ(int vertexIndex) {
        return quad.normalZ(vertexIndex);
    }

    @Override
    public @Nullable Vector3f copyNormal(int vertexIndex, @Nullable Vector3f target) {
        return quad.copyNormal(vertexIndex, target);
    }

    @Override
    public Vector3fc faceNormal() {
        return quad.faceNormal();
    }

    @Override
    public @NonNull Direction lightFace() {
        return quad.getLightFace();
    }

    @Override
    public @Nullable Direction nominalFace() {
        return quad.getNominalFace();
    }

    @Override
    public @Nullable Direction cullFace() {
        return quad.getCullFace();
    }

    @Override
    public @Nullable ChunkSectionLayer chunkLayer() {
        return quad.getRenderType();
    }

    @Override
    public RenderType itemRenderType() {
        return quad.itemRenderType();
    }

    @Override
    public boolean emissive() {
        return quad.emissive();
    }

    @Override
    public boolean diffuseShade() {
        return quad.diffuseShade();
    }

    @Override
    public TriState ambientOcclusion() {
        return TO_FABRIC[quad.ambientOcclusion().ordinal()];
    }

    @Override
    public ItemStackRenderState.@Nullable FoilType foilType() {
        return quad.glint();
    }

    @Override
    public ShadeMode shadeMode() {
        return quad.getShadeMode() == SodiumShadeMode.ENHANCED ? ShadeMode.ENHANCED : ShadeMode.VANILLA;
    }

    @Override
    public boolean animated() {
        return quad.animated();
    }

    public QuadAtlas atlas() {
        return quad.getQuadAtlas() == SodiumQuadAtlas.BLOCK ? QuadAtlas.BLOCK : QuadAtlas.ITEM;
    }

    @Override
    public int tintIndex() {
        return quad.getTintIndex();
    }

    @Override
    public int tag() {
        return quad.getTag();
    }

    @Override
    public final void buffer(int overlayCoords, VertexConsumer vertexConsumer) {
        if (!quad.hasVertexNormals()) {
            final Vector3fc faceNormal = faceNormal();

            for (int i = 0; i < 4; i++) {
                vertexConsumer.addVertex(x(i), y(i), z(i), color(i), u(i), v(i), overlayCoords, lightmap(i), faceNormal.x(), faceNormal.y(), faceNormal.z());
            }
        } else if (quad.hasAllVertexNormals()) {
            final Vector3f normalVec = this.normalVec;

            for (int i = 0; i < 4; i++) {
                copyNormal(i, normalVec);
                vertexConsumer.addVertex(x(i), y(i), z(i), color(i), u(i), v(i), overlayCoords, lightmap(i), normalVec.x(), normalVec.y(), normalVec.z());
            }
        } else {
            final Vector3f normalVec = this.normalVec;
            final Vector3fc faceNormal = faceNormal();

            for (int i = 0; i < 4; i++) {
                if (hasNormal(i)) {
                    copyNormal(i, normalVec);
                } else {
                    normalVec.set(faceNormal);
                }

                vertexConsumer.addVertex(x(i), y(i), z(i), color(i), u(i), v(i), overlayCoords, lightmap(i), normalVec.x(), normalVec.y(), normalVec.z());
            }
        }
    }

    // TODO: Optimize this (26.1)
    @Override
    public final void buffer(int overlayCoords, PoseStack.Pose pose, VertexConsumer vertexConsumer) {
        final Vector4f posVec = this.posVec;
        final Vector3f normalVec = this.normalVec;
        final Matrix4f posMatrix = pose.pose();

        if (!quad.hasVertexNormals()) {
            pose.transformNormal(faceNormal(), normalVec);

            for (int i = 0; i < 4; i++) {
                posVec.set(x(i), y(i), z(i), 1.0f);
                posVec.mul(posMatrix);
                vertexConsumer.addVertex(posVec.x(), posVec.y(), posVec.z(), color(i), u(i), v(i), overlayCoords, lightmap(i), normalVec.x(), normalVec.y(), normalVec.z());
            }
        } else if (quad.hasAllVertexNormals()) {
            for (int i = 0; i < 4; i++) {
                posVec.set(x(i), y(i), z(i), 1.0f);
                posVec.mul(posMatrix);
                copyNormal(i, normalVec);
                pose.transformNormal(normalVec, normalVec);
                vertexConsumer.addVertex(posVec.x(), posVec.y(), posVec.z(), color(i), u(i), v(i), overlayCoords, lightmap(i), normalVec.x(), normalVec.y(), normalVec.z());
            }
        } else {
            final Vector3f transformedFaceNormal = pose.transformNormal(faceNormal(), normalVec1);

            for (int i = 0; i < 4; i++) {
                posVec.set(x(i), y(i), z(i), 1.0f);
                posVec.mul(posMatrix);

                if (hasNormal(i)) {
                    copyNormal(i, normalVec);
                    pose.transformNormal(normalVec, normalVec);
                } else {
                    normalVec.set(transformedFaceNormal);
                }

                vertexConsumer.addVertex(posVec.x(), posVec.y(), posVec.z(), color(i), u(i), v(i), overlayCoords, lightmap(i), normalVec.x(), normalVec.y(), normalVec.z());
            }
        }
    }

    public QuadViewImpl getOriginal() {
        return quad;
    }

    protected void setDelegate(QuadViewImpl impl) {
        this.quad = impl;
    }
}
