package net.caffeinemc.mods.sodium.mixin.debug.checks.threading;

import com.mojang.blaze3d.pipeline.RenderCall;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.client.render.util.DeferredRenderTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public abstract class MixinRenderSystem {
    /**
     * @author JellySquid
     * @reason Disallow the use of RenderSystem.recordRenderCall entirely
     */
    @Overwrite(remap = false)
    public static void recordRenderCall(RenderCall call) {
        throw new UnsupportedOperationException("Usage of RenderSystem#recordRenderCall is likely a bug, " +
                "which is handled as an error when Sodium is enabled in debug mode");
    }

    @Inject(method = "replayQueue", at = @At("HEAD"))
    private static void handleDeferredTasks(CallbackInfo ci) {
        DeferredRenderTask.runAll();
    }
}
