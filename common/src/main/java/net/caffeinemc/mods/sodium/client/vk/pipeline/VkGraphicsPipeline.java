package net.caffeinemc.mods.sodium.client.vk.pipeline;

import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;

public final class VkGraphicsPipeline implements AutoCloseable {
    private final VkDevice device;
    private final long pipeline;
    private final long layout;

    public VkGraphicsPipeline(VkDevice device, long pipeline, long layout) {
        this.device = device;
        this.pipeline = pipeline;
        this.layout = layout;
    }

    public long pipeline() {
        return this.pipeline;
    }

    public long layout() {
        return this.layout;
    }

    public void delete() {
        vkDestroyPipeline(this.device, this.pipeline, null);
        vkDestroyPipelineLayout(this.device, this.layout, null);
    }

    @Override
    public void close() {
        this.delete();
    }
}
