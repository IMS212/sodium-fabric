package net.caffeinemc.mods.sodium.client.gl.buffer;

import graphics.cinnabar.core.vk.memory.VkBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class GlBufferMapping {
    private final VkBuffer buffer;
    private final ByteBuffer map;

    protected boolean disposed;

    public GlBufferMapping(VkBuffer buffer, ByteBuffer map) {
        this.buffer = buffer;
        this.map = map;
    }

    public void write(ByteBuffer data, int writeOffset) {
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), MemoryUtil.memAddress(this.map, writeOffset), data.remaining());
    }

    public VkBuffer getBufferObject() {
        return this.buffer;
    }

    public void dispose() {
        this.disposed = true;
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public ByteBuffer getMemoryBuffer() {
        return this.map;
    }
}
