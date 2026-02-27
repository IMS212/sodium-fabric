package net.caffeinemc.mods.sodium.client.vk.pipeline;

import net.caffeinemc.mods.sodium.client.vk.CinnabarAccess;
import net.caffeinemc.mods.sodium.client.vk.VkObjectDestroyable;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import org.lwjgl.vulkan.VK13;

public final class VkPipelineLayout extends VkObjectDestroyable {
    public VkPipelineLayout(long handle) {
        this.setHandle(handle);
    }

    public void delete() {
        this.destroyInternal(null);
        this.invalidateHandle();
    }

    @Override
    protected void destroyInternal(CommandList commandList) {
        VK13.vkDestroyPipelineLayout(CinnabarAccess.getDevice(), this.handle(), null);
    }
}
