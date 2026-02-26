package net.caffeinemc.mods.sodium.mixin.features.gui.hooks.debug;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {
    @Inject(method = "render", at = @At(value = "RETURN"))
    private void injectAfterPop(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (Minecraft.getInstance().debugEntries.isOverlayVisible()) {
            SodiumWorldRenderer.instance().renderBufferDebug(guiGraphics);
        }
    }
}
