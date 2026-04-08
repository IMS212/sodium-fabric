package net.caffeinemc.mods.sodium.client.render.chunk;

import java.util.BitSet;

public class RollingBitSet {
    private final BitSet bits = new BitSet();
    private int nextCandidate = 0;

    public int allocate() {
        int free = bits.nextClearBit(nextCandidate);
        bits.set(free);
        nextCandidate = free + 1;
        return free;
    }

    public void free(int value) {
        bits.clear(value);
        if (value < nextCandidate) {
            nextCandidate = value;
        }
    }

    public boolean isUsed(int value) {
        return bits.get(value);
    }
}