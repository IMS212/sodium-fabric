package net.caffeinemc.mods.sodium.client.vk.buffer;

import net.caffeinemc.mods.sodium.client.vk.util.EnumBit;
import org.lwjgl.vulkan.VK13;

public enum VkBufferUsages implements EnumBit {
    STORAGE_BUFFER(VK13.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT),
    UNIFORM_BUFFER(VK13.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT),
    INDEX_BUFFER(VK13.VK_BUFFER_USAGE_INDEX_BUFFER_BIT),
    VERTEX_BUFFER(VK13.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT),
    INDIRECT_BUFFER(VK13.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT),
    SHADER_DEVICE_ADDRESS(VK13.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT),
    TRANSFER_SRC(VK13.VK_BUFFER_USAGE_TRANSFER_SRC_BIT),
    TRANSFER_DST(VK13.VK_BUFFER_USAGE_TRANSFER_DST_BIT);

    private final int bits;

    VkBufferUsages(int bits) {
        this.bits = bits;
    }

    @Override
    public int getBits() {
        return this.bits;
    }
}
