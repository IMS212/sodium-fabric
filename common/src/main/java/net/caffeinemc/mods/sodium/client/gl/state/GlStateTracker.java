package net.caffeinemc.mods.sodium.client.gl.state;

import net.caffeinemc.mods.sodium.client.gl.array.GlVertexArray;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferTarget;

import java.util.Arrays;

public class GlStateTracker {
    private static final int UNASSIGNED_HANDLE = -1;

    private final int[] bufferState = new int[GlBufferTarget.COUNT];
    private int vertexArrayState;

    public GlStateTracker() {

    }

}
