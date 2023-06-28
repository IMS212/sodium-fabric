package me.jellysquid.mods.sodium.mixin.features.model;

import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BasicBakedModel;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/**
 * This mixin provides access to the private fields of BasicBakedModel,
 * to implement a fast path in the BlockRenderer that avoid the getQuads calls.
 */
@Mixin(BasicBakedModel.class)
public interface BasicBakedModelAccessor {
    @Accessor
    List<BakedQuad> getQuads();

    @Accessor
    Map<Direction, List<BakedQuad>> getFaceQuads();
}
