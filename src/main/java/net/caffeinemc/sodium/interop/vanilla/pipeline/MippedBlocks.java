package net.caffeinemc.sodium.interop.vanilla.pipeline;

import net.minecraft.block.Block;

import java.util.HashSet;

public class MippedBlocks {
    private static final HashSet<Block> MIPPED_BLOCKS;

    static {
        MIPPED_BLOCKS = new HashSet<>();
    }

    public static void add(Block block) {
        MIPPED_BLOCKS.add(block);
    }

    public static boolean contains(Block block) {
        return MIPPED_BLOCKS.contains(block);
    }
}
