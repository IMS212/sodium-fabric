package net.caffeinemc.mods.sodium.client.vk.buffer;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class VkMapping {
    private final VkBuffer buffer;
    private final long pointer;
    private final long size;

    public VkMapping(VkBuffer buffer, long pointer, long size) {
        this.buffer = buffer;
        this.pointer = pointer;
        this.size = size;
    }

    public VkBuffer getBuffer() {
        return buffer;
    }

    public long getMappedData() {
        return pointer;
    }

    public long getSize() {
        return size;
    }

    public ByteBuffer getByteBuffer() {
        return MemoryUtil.memByteBuffer(pointer, (int) size);
    }
}
