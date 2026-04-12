package net.caffeinemc.mods.sodium.client.render.chunk;

import java.util.BitSet;

public class RollingBitSet {
    private final BitSet bits = new BitSet();

    public int allocate() {
        int free = bits.nextClearBit(0);
        bits.set(free);
        return free;
    }

    public void free(int value) {
        if (!bits.get(value)) throw new IllegalArgumentException("Value " + value + " is not allocated");
        bits.clear(value);
    }

    public boolean isUsed(int value) {
        return bits.get(value);
    }
}