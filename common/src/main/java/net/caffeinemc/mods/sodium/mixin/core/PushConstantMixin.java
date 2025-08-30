package net.caffeinemc.mods.sodium.mixin.core;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import graphics.cinnabar.api.hg.HgGraphicsPipeline;
import graphics.cinnabar.api.hg.HgUniformSet;
import graphics.cinnabar.core.hg3d.Hg3DGpuDevice;
import graphics.cinnabar.core.hg3d.Hg3DRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.BiFunction;

@Mixin(Hg3DRenderPipeline.class)
public class PushConstantMixin {
    //@ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lgraphics/cinnabar/api/hg/HgGraphicsPipeline$Layout$CreateInfo;<init>(Lgraphics/cinnabar/api/hg/HgGraphicsPipeline$Layout;Ljava/util/List;I)V", remap = false), index = 0)
    private void i() {
        System.out.println("GAMING");
    }
}
