package net.caffeinemc.mods.sodium.client.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import net.caffeinemc.mods.sodium.client.util.DirectionUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelDataManager;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Matrix4f;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SodiumMultiPlatImpl {
    public static boolean isBlockTransparent(BlockState block, BlockAndTintGetter level, BlockPos pos, FluidState fluidState) {
        return block.shouldDisplayFluidOverlay(level, pos, fluidState);
    }

    public static TextureAtlasSprite findInBlockAtlas(float u, float v) {
        // TODO
        //return SpriteFinderCache.forBlockAtlas().find(u, v);
        return null;
    }

    public static Object getRenderData(Level level, ChunkPos pos, BlockEntity value) {
        return level.getModelDataManager().getAt(pos);
    }

    public static Object getModelData(Object o, BlockPos pos) {
        if ((o instanceof Map<?,?>)) {
            return ((Map<BlockPos, ModelData>) o).getOrDefault(pos, ModelData.EMPTY);
        } else {
            return ModelData.EMPTY;
        }
    }

    public static boolean isFlawlessFramesActive() {
        return false;
    }

    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    public static Iterable<RenderType> getMaterials(BlockRenderContext ctx, RandomSource random, Object modelData) {
        return ctx.model().getRenderTypes(ctx.state(), random, ctx.model().getModelData(ctx.slice(), ctx.pos(), ctx.state(), (ModelData) modelData));
    }

    public static List<BakedQuad> getQuads(BlockRenderContext ctx, Direction face, RandomSource random, RenderType renderType, Object modelData) {
        return ctx.model().getQuads(ctx.state(), face, random, ctx.model().getModelData(ctx.slice(), ctx.pos(), ctx.state(), (ModelData) modelData), renderType);
    }

    public static Object getEmptyModelData() {
        return ModelData.EMPTY;
    }

    public static void runChunkLayerEvents(RenderType renderType, LevelRenderer levelRenderer, PoseStack poseStack, Matrix4f projectionMatrix, int renderTick, Camera camera, Frustum frustum) {
        ForgeHooksClient.dispatchRenderStage(renderType, levelRenderer, poseStack, projectionMatrix, renderTick, camera, frustum);
    }

    public static boolean shouldSkipRender(BlockGetter level, BlockState selfState, BlockState otherState, BlockPos selfPos, Direction facing) {
        return selfState.supportsExternalFaceHiding() && (otherState.hidesNeighborFace(level, selfPos, selfState, DirectionUtil.getOpposite(facing)));
    }
}
