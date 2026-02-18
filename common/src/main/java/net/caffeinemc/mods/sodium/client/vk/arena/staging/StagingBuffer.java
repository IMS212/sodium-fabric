package net.caffeinemc.mods.sodium.client.vk.arena.staging;

import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;

public interface StagingBuffer {
    void enqueueCopy(VkCommandBuffer commandBuffer, ByteBuffer data, long dstBuffer, long writeOffset);

    void flush(VkCommandBuffer commandBuffer);

    void delete();

    void flip();

    long getUploadSizeLimit(long frameDuration);
}
