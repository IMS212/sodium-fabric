package net.caffeinemc.mods.sodium.client.vk;

import net.caffeinemc.mods.sodium.client.vk.device.CommandList;

public abstract class VkObjectDestroyable extends VkObject {
    protected abstract void destroyInternal(CommandList commandList);

    public void destroy(CommandList commandList) {
        this.destroyInternal(commandList);
        this.invalidateHandle();
    }
}
