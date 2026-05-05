package net.caffeinemc.mods.sodium.mixin.core.render.world;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.caffeinemc.mods.sodium.client.util.FlawlessFrames;
import net.caffeinemc.mods.sodium.client.util.FogStorage;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
    @Shadow
    private @Nullable ClientLevel level;
    @Unique
    private SodiumWorldRenderer renderer;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureRenderer(Minecraft minecraft, LevelRenderState levelRenderState, LevelRenderer levelRenderer, CallbackInfo ci) {
        this.renderer = ((LevelRendererExtension) levelRenderer).sodium$getWorldRenderer();
    }

    /**
     * @reason Redirect to our renderer
     * @author JellySquid
     */
    @Overwrite
    public int countRenderedSections() {
        return this.renderer.getVisibleChunkCount();
    }

    @Redirect(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"))
    private boolean applyFrustum(SectionOcclusionGraph instance, @Local Camera camera) {
        RenderDevice.enterManagedCode();

        var viewport = ((ViewportProvider) camera.getCullFrustum()).sodium$createViewport();
        var updateChunksImmediately = FlawlessFrames.isActive();

        try {
            this.renderer.setupTerrain(camera, viewport, ((FogStorage) Minecraft.getInstance().gameRenderer).sodium$getFogParameters(), Minecraft.getInstance().player != null && Minecraft.getInstance().player.isSpectator(), updateChunksImmediately, ((FrustumAccessor) camera.getCullFrustum()).sodium$getMatrix());
        } finally {
            RenderDevice.exitManagedCode();
        }
        return false;
    }

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void onWorldChanged(ClientLevel level, CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.setLevel(level);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Inject(method = "extractVisibleBlockEntities", at = @At("HEAD"), cancellable = true, require = 1)
    private void extractVisibleBlockEntities(Camera camera, float f, LevelRenderState levelRenderState, CallbackInfo ci) {
        ci.cancel();

        this.renderer.extractBlockEntities(camera, f, this.level.destructionProgress(), levelRenderState);
    }

    /**
     * @reason Replace the debug string
     * @author JellySquid
     */
    @Overwrite
    public String sectionStatistics() {
        return this.renderer.getChunksDebugString();
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void setBlocksDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void setSectionDirtyWithNeighbors(int x, int y, int z) {
        this.renderer.scheduleRebuildForChunks(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, false);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void setBlockDirty(BlockPos pos, boolean important) {
        this.renderer.scheduleRebuildForBlockArea(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, important);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void setSectionDirty(int x, int y, int z, boolean important) {
        this.renderer.scheduleRebuildForChunk(x, y, z, important);
    }
}
