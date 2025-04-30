package net.caffeinemc.mods.sodium.mixin.debug.checks.threading;

import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.client.render.util.DeferredRenderTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public abstract class MixinRenderSystem {
}
