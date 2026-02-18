package net.caffeinemc.mods.sodium.client.vk.tessellation;

import net.caffeinemc.mods.sodium.client.vk.commands.CommandList;

public interface VkTessellation {
    void delete(CommandList commandList);

    VkPrimitiveType getPrimitiveType();

    VkIndexType getIndexType();

    VkTessellationBinding[] getBindings();
}
