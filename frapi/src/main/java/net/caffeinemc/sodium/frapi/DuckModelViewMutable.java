package net.caffeinemc.sodium.frapi;

import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.QuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.render.SodiumShadeMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class DuckModelViewMutable implements QuadEmitter {
    private final MutableQuadViewImpl quad;

    public DuckModelViewMutable(MutableQuadViewImpl quad) {
        this.quad = quad;
    }

    @Override
    public QuadEmitter pos(int vertexIndex, float x, float y, float z) {
        quad.pos(vertexIndex, x, y, z);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter color(int vertexIndex, int color) {
        quad.color(vertexIndex, color);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter uv(int vertexIndex, float u, float v) {
        quad.uv(vertexIndex, u, v);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter lightmap(int vertexIndex, int lightmap) {
        quad.lightmap(vertexIndex, lightmap);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter normal(int vertexIndex, float x, float y, float z) {
        quad.normal(vertexIndex, x, y, z);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter nominalFace(@Nullable Direction face) {
        quad.nominalFace(face);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter cullFace(@Nullable Direction face) {
        quad.cullFace(face);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter renderLayer(@Nullable ChunkSectionLayer renderLayer) {
        quad.renderLayer(renderLayer);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter emissive(boolean emissive) {
        quad.emissive(emissive);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter diffuseShade(boolean shade) {
        quad.diffuseShade(shade);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter ambientOcclusion(TriState ao) {
        quad.ambientOcclusion(ao);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter glint(ItemStackRenderState.@Nullable FoilType glint) {
        quad.glint(glint);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter shadeMode(ShadeMode mode) {
        quad.internalShadeMode(mode == ShadeMode.ENHANCED ? SodiumShadeMode.ENHANCED : SodiumShadeMode.STANDARD);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter tintIndex(int tintIndex) {
        quad.tintIndex(tintIndex);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter tag(int tag) {
        quad.tag(tag);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter copyFrom(QuadView quadView) {
        quad.copyFrom((QuadViewImpl) quadView);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter fromVanilla(int[] vertexData, int startIndex) {
        quad.fromVanilla(vertexData, startIndex);
        return (QuadEmitter) quad;
    }

    @Override
    public QuadEmitter fromBakedQuad(BakedQuad baked) {
        quad.fromBakedQuad(baked);
        return (QuadEmitter) quad;
    }

    @Override
    public void pushTransform(QuadTransform transform) {
        ((QuadEmitter) quad).pushTransform(transform);
    }

    @Override
    public void popTransform() {
        ((QuadEmitter) quad).popTransform();
    }

    @Override
    public QuadEmitter emit() {
        return ((QuadEmitter) quad).emit();
    }

    @Override
    public float x(int vertexIndex) {
        return quad.x(vertexIndex);
    }

    @Override
    public float y(int vertexIndex) {
        return quad.y(vertexIndex);
    }

    @Override
    public float z(int vertexIndex) {
        return quad.z(vertexIndex);
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
        return quad.color(vertexIndex);
    }

    @Override
    public float u(int vertexIndex) {
        return quad.u(vertexIndex);
    }

    @Override
    public float v(int vertexIndex) {
        return quad.v(vertexIndex);
    }

    @Override
    public Vector2f copyUv(int vertexIndex, @Nullable Vector2f target) {
        return quad.copyUv(vertexIndex, target);
    }

    @Override
    public int lightmap(int vertexIndex) {
        return quad.lightmap(vertexIndex);
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
    public @NotNull Direction lightFace() {
        return quad.lightFace();
    }

    @Override
    public @Nullable Direction nominalFace() {
        return quad.nominalFace();
    }

    @Override
    public @Nullable Direction cullFace() {
        return quad.cullFace();
    }

    @Override
    public @Nullable ChunkSectionLayer renderLayer() {
        return quad.renderLayer();
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
        return quad.ambientOcclusion();
    }

    @Override
    public ItemStackRenderState.@Nullable FoilType glint() {
        return quad.glint();
    }

    @Override
    public ShadeMode shadeMode() {
        return quad.sodiumShadeMode() == SodiumShadeMode.ENHANCED ? ShadeMode.ENHANCED : ShadeMode.VANILLA;
    }

    @Override
    public int tintIndex() {
        return quad.tintIndex();
    }

    @Override
    public int tag() {
        return quad.tag();
    }

    @Override
    public void toVanilla(int[] target, int startIndex) {
        quad.toVanilla(target, startIndex);
    }

    public MutableQuadViewImpl getOriginal() {
        return quad;
    }
}
