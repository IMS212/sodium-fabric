package net.caffeinemc.mods.sodium.mixin.features.model;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.multipart.MultiPartModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

@Mixin(MultiPartModel.SharedBakedState.class)
public class MultiPartBakedModelMixin {
    @Shadow
    @Final
    private List<MultiPartModel.Selector<BlockStateModel>> selectors;
    @Unique
    private final Map<BlockState, List<BlockStateModel>> stateCacheFast = new Reference2ReferenceOpenHashMap<>();
    @Unique
    private final StampedLock lock = new StampedLock();

    /**
     * @author JellySquid
     * @reason Avoid expensive allocations and replace bitfield indirection
     */
    @Overwrite
    public List<BlockStateModel> selectModels(BlockState blockState) {
        if (blockState == null) {
            return Collections.emptyList();
        }

        List<BlockStateModel> models;

        long readStamp = this.lock.readLock();
        try {
            models = this.stateCacheFast.get(blockState);
        } finally {
            this.lock.unlockRead(readStamp);
        }

        if (models == null) {
            long writeStamp = this.lock.writeLock();
            try {
                List<BlockStateModel> modelList = new ArrayList<>(this.selectors.size());

                for (MultiPartModel.Selector<BlockStateModel> selector : this.selectors) {
                    if (selector.condition().test(blockState)) {
                        modelList.add(selector.model());
                    }
                }

                models = modelList;
                this.stateCacheFast.put(blockState, models);
            } finally {
                this.lock.unlockWrite(writeStamp);
            }
        }

        return models;
    }
}
