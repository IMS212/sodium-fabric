package net.caffeinemc.mods.sodium.client.vk.arena;

import net.caffeinemc.mods.sodium.client.util.NativeBuffer;

public class PendingUpload {
    private final NativeBuffer data;
    private VkBufferSegment result;

    public PendingUpload(NativeBuffer data) {
        this.data = data;
    }

    public NativeBuffer getDataBuffer() {
        return this.data;
    }

    protected void setResult(VkBufferSegment result) {
        if (this.result != null) {
            throw new IllegalStateException("Result already provided");
        }

        this.result = result;
    }

    public VkBufferSegment getResult() {
        if (this.result == null) {
            throw new IllegalStateException("Result not computed");
        }

        return this.result;
    }

    public int getLength() {
        return this.data.getLength();
    }
}
