package net.caffeinemc.sodium.mixin.features.render_layer.leaves;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.sodium.SodiumClientMod;
import net.caffeinemc.sodium.interop.vanilla.pipeline.MippedBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.fluid.Fluid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Map;

@Mixin(value = RenderLayers.class, priority = 1010)
public class MixinRenderLayers {
    @Mutable
    @Shadow
    @Final
    private static Map<Block, RenderLayer> BLOCKS;

    @Mutable
    @Shadow
    @Final
    private static Map<Fluid, RenderLayer> FLUIDS;

    static {
        // Replace the backing collection types with something a bit faster, since this is a hot spot in chunk rendering.
        BLOCKS = new Reference2ReferenceOpenHashMap<>(BLOCKS);

        // TODO: This is a temporary fix to solve frogspawn blocks making the underlying water invisible due to translucency sorting.
        // This slightly affects the look of the block, but is better than the alternative for now.
        BLOCKS.replace(Blocks.FROGSPAWN, RenderLayer.getCutout());

        FLUIDS = new Reference2ReferenceOpenHashMap<>(FLUIDS);
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void overrideCutout(CallbackInfo ci) {
         BLOCKS.forEach((block, renderLayer) -> {
             if (renderLayer != RenderLayer.getCutout()) {
                 MippedBlocks.add(block);
             }
             if (renderLayer == RenderLayer.getCutoutMipped()) {
                 BLOCKS.replace(block, RenderLayer.getCutout());
             }
         });
    }

    @Inject(method = "getBlockLayer(Lnet/minecraft/block/BlockState;)Lnet/minecraft/client/render/RenderLayer;", at = @At(value = "RETURN"), cancellable = true)
    private static void redirectLeavesGraphics(BlockState state, CallbackInfoReturnable<RenderLayer> cir) {
        if (state.getBlock() instanceof LeavesBlock) {
            boolean fancyLeaves = SodiumClientMod.options().quality.leavesQuality.isFancy(MinecraftClient.getInstance().options.getGraphicsMode().getValue());
            cir.setReturnValue(fancyLeaves ? RenderLayer.getCutout() : RenderLayer.getSolid());
        }
    }
}
