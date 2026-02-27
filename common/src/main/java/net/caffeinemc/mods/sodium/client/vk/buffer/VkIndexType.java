package net.caffeinemc.mods.sodium.client.vk.buffer;

import org.lwjgl.vulkan.VK14;

public enum VkIndexType {
    UNSIGNED_BYTE(VK14.VK_INDEX_TYPE_UINT8, 1),
    UNSIGNED_SHORT(VK14.VK_INDEX_TYPE_UINT16, 2),
    UNSIGNED_INT(VK14.VK_INDEX_TYPE_UINT32, 4);

    private final int id;
    private final int stride;

    VkIndexType(int id, int stride) {
        this.id = id;
        this.stride = stride;
    }

    public int getFormatId() {
        return this.id;
    }

    public int getStride() {
        return this.stride;
    }
}
