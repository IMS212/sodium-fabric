package me.jellysquid.mods.sodium.client.model;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by {@link BakedModel}s that forward to an inner model based on the block state and random seed,
 * but not on the face.
 * This allows Sodium to only evaluate the logic to access the inner model a single time, instead of once per face.
 */
public interface NestedModelAccessor {
    /**
     * @return model that will be used for all faces. Return {@code this} if there's no inner model.
     */
    BakedModel sodium_getNestedModel(@Nullable BlockState state, Random random);
}
