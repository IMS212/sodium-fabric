package net.caffeinemc.mods.sodium.client.vk;

import org.lwjgl.vulkan.VkCommandBuffer;

import static org.lwjgl.vulkan.VK13.vkCmdEndRendering;

public final class SodiumRenderPass implements AutoCloseable {
    private final VkCommandBuffer vkCommandBuffer;
    private boolean closed;

    public SodiumRenderPass(VkCommandBuffer vkCommandBuffer) {
        this.vkCommandBuffer = vkCommandBuffer;
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }

        vkCmdEndRendering(this.vkCommandBuffer);
        this.closed = true;
    }
}
