package net.caffeinemc.mods.sodium.client.vk.device;

import net.caffeinemc.mods.sodium.client.vk.VkObjectDestroyable;
import net.caffeinemc.mods.sodium.client.vk.functions.DeviceFunctions;

public interface RenderDevice {
    RenderDevice INSTANCE = new VKRenderDevice();

    CommandList createCommandList();

    DeviceFunctions getDeviceFunctions();

    int getSubTexelPrecisionBits();

    void destroyObjectWhenSafe(VkObjectDestroyable destroyable);

    void flip();
}
