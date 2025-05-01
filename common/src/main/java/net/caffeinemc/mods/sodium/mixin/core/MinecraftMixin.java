package net.caffeinemc.mods.sodium.mixin.core;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.checks.ResourcePackScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.lwjgl.opengl.GL32C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Final
    private ReloadableResourceManager resourceManager;
    @Unique
    private final LongArrayFIFOQueue fences = new LongArrayFIFOQueue();

    /**
     * We run this at the beginning of the frame (except for the first frame) to give the previous frame plenty of time
     * to render on the GPU. This allows us to stall on ClientWaitSync for less time.
     */
    @Inject(method = "runTick", at = @At("HEAD"))
    private void preRender(boolean tick, CallbackInfo ci) {

    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void postRender(boolean tick, CallbackInfo ci) {

    }

    /**
     * Check for problematic core shader resource packs after the initial game launch.
     */
    @Inject(method = "buildInitialScreens", at = @At("TAIL"))
    private void postInit(CallbackInfoReturnable<Runnable> cir) {
        ResourcePackScanner.checkIfCoreShaderLoaded(this.resourceManager);
    }

    /**
     * Check for problematic core shader resource packs after every resource reload.
     */
    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("TAIL"))
    private void postResourceReload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        ResourcePackScanner.checkIfCoreShaderLoaded(this.resourceManager);
    }

}
