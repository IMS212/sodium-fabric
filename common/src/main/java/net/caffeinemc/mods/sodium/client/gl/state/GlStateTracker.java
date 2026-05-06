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

    public void notifyVertexArrayDeleted(GlVertexArray vertexArray) {
        if (this.vertexArrayState == vertexArray.handle()) {
            this.vertexArrayState = UNASSIGNED_HANDLE;
        }
    }
    public boolean makeVertexArrayActive(GlVertexArray array) {
        int handle = array == null ? GlVertexArray.NULL_ARRAY_ID : array.handle();
        boolean changed = this.vertexArrayState != handle;

        if (changed) {
            this.vertexArrayState = handle;

            Arrays.fill(this.bufferState, UNASSIGNED_HANDLE);
        }

        return changed;
    }

    public void clear() {
        Arrays.fill(this.bufferState, -1);
        this.vertexArrayState = -1;
    }
}
