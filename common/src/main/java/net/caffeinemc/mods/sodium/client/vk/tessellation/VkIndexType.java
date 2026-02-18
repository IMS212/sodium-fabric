package net.caffeinemc.mods.sodium.client.vk.tessellation;

import org.lwjgl.vulkan.EXTIndexTypeUint8;
import org.lwjgl.vulkan.VK10;

public enum VkIndexType {
    // unsigned byte? is that useful...?
    UNSIGNED_SHORT(VK10.VK_INDEX_TYPE_UINT16, 2),
    UNSIGNED_INT(VK10.VK_INDEX_TYPE_UINT32, 4);

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
