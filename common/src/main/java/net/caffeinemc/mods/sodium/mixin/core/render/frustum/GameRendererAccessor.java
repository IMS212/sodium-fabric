package net.caffeinemc.mods.sodium.mixin.core.render.frustum;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.OptionsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor
    float getSpinningEffectTime();

    @Accessor
    float getSpinningEffectSpeed();
}
