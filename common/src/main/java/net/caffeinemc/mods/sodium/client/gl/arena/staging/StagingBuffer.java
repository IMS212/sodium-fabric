package net.caffeinemc.mods.sodium.client.gl.arena.staging;

import graphics.cinnabar.core.vk.memory.VkBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;

import java.nio.ByteBuffer;

public interface StagingBuffer {
    void enqueueCopy(CommandList commandList, ByteBuffer data, VkBuffer dst, long writeOffset);

    void flush(CommandList commandList);

    void delete(CommandList commandList);

    void flip();
}
