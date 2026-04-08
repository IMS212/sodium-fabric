package net.caffeinemc.mods.sodium.client.vk.device;

import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBufferUsages;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkMappingType;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkMapping;
import net.caffeinemc.mods.sodium.client.vk.fence.VkFence;
import net.caffeinemc.mods.sodium.client.vk.renderpass.VulkanRenderPass;
import net.caffeinemc.mods.sodium.client.vk.util.EnumBitField;
import org.lwjgl.vulkan.VkCommandBuffer;

public interface CommandList extends AutoCloseable {
    VkBuffer createBuffer(long bufferSize, VkMappingType mappingType, EnumBitField<VkBufferUsages> flags);

    void copyBufferToBuffer(VkBuffer src, VkBuffer dst, long readOffset, long writeOffset, long bytes);

    void deleteBuffer(VkBuffer buffer);

    void flush();

    @Override
    default void close() {
        this.flush();
    }

    VkMapping mapBuffer(VkBuffer buffer, long offset, long length);

    void unmap(VkMapping map);

    void flushMappedRange(VkMapping map, int offset, int length);

    VkFence createFence();

    VulkanRenderPass startRenderPass(long colorTextureView);

    void deleteAllFences();

    VkCommandBuffer getCommandBuffer();
}
