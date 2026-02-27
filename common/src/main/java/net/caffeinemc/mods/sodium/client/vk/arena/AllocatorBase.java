package net.caffeinemc.mods.sodium.client.vk.arena;

import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;

public interface AllocatorBase {
    long getDeviceUsedMemory();

    long getDeviceAllocatedMemory();

    void free(VkBufferSegment entry);

    boolean isEmpty();

    VkBuffer getBufferObject();
}
