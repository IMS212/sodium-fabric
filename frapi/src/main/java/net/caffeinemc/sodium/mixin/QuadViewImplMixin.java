package net.caffeinemc.sodium.mixin;

import net.caffeinemc.mods.sodium.client.render.frapi.mesh.QuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.render.SodiumShadeMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.ShadeMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(QuadViewImpl.class)
public abstract class QuadViewImplMixin implements QuadView {
    @Shadow
    public abstract SodiumShadeMode sodiumShadeMode();

    @Override
    public ShadeMode shadeMode() {
        return this.sodiumShadeMode() == SodiumShadeMode.ENHANCED ? ShadeMode.ENHANCED : ShadeMode.VANILLA;
    }
}
