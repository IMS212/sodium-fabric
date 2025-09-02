package net.caffeinemc.sodium.mixin.frapi;

import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.caffeinemc.sodium.frapi.DuckModelViewMutable;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockModelPart;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Predicate;

@Mixin(BlockModelPart.class)
public interface BlockModelPartMixin extends FabricBlockModelPart {
    @Override
    default void emitQuads(QuadEmitter e, Predicate<@Nullable Direction> cullTest) {
        QuadEmitter emitter;

        if (e instanceof DuckModelViewMutable duck) {
            emitter = (QuadEmitter) duck.getOriginal();
        } else {
            emitter = e;
        }

        if (emitter instanceof AbstractBlockRenderContext.BlockEmitter be) {
            be.emitPart((BlockModelPart) this, cullTest);
        } else {
            FabricBlockModelPart.super.emitQuads(e, cullTest);
        }
    }
}
