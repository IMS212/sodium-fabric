package net.caffeinemc.mods.sodium.client.vk.arena.staging;

import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;

import java.nio.ByteBuffer;

public interface StagingBuffer {
    void enqueueCopy(CommandList commandList, ByteBuffer data, VkBuffer dst, long writeOffset);

    void flush(CommandList commandList);

    void flip(CommandList commandList);

    void delete(CommandList commandList);

    long getUploadSizeLimit(long frameDuration);
}
