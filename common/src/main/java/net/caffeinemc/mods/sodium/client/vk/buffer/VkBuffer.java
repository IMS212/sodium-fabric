package net.caffeinemc.mods.sodium.client.vk.buffer;

import net.caffeinemc.mods.sodium.client.vk.CinnabarAccess;
import net.caffeinemc.mods.sodium.client.vk.VkObjectDestroyable;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.vma.Vma;

public class VkBuffer extends VkObjectDestroyable {
    private final long allocation;
    private final long size;

    private VkMapping mapping;

    public VkBuffer(long buffer, long allocation, long size, @Nullable VkMapping mapping) {
        this.setHandle(buffer);
        this.allocation = allocation;
        this.size = size;
        this.mapping = mapping;
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

    @Override
    protected void destroyInternal(CommandList commandList) {
        Vma.vmaDestroyBuffer(CinnabarAccess.getAllocator(), handle(), allocation);
    }
}
