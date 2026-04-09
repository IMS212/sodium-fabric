package net.caffeinemc.mods.sodium.client.vk.buffer;

import net.caffeinemc.mods.sodium.client.vk.VulkanAccess;
import net.caffeinemc.mods.sodium.client.vk.VkObjectDestroyable;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.vma.Vma;

public class VkBuffer extends VkObjectDestroyable {
    private final long allocation;
    private final long size;
    private final long deviceAddress;

    private VkMapping mapping;

    public VkBuffer(long buffer, long allocation, long size, @Nullable VkMapping mapping, long deviceAddress) {
        this.setHandle(buffer);
        this.allocation = allocation;
        this.size = size;
        this.mapping = mapping;
        this.deviceAddress = deviceAddress;
    }

    public long getSize() {
        return size;
    }

    public VkMapping getMapping() {
        return mapping;
    }

    public void setMapping(VkMapping mapping) {
        this.mapping = mapping;
    }

    public long getAllocation() {
        return allocation;
    }

    public long getDeviceAddress() {
        if (deviceAddress == 0) System.out.println("Just tried to get an address of a buffer that had none. You're probably going to crash.");
        return deviceAddress;
    }

    @Override
    protected void destroyInternal(CommandList commandList) {
        Vma.vmaDestroyBuffer(VulkanAccess.getAllocator(), handle(), allocation);
    }
}
