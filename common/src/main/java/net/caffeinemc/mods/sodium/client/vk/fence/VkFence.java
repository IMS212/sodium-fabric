package net.caffeinemc.mods.sodium.client.vk.fence;

import net.caffeinemc.mods.sodium.client.vk.CinnabarAccess;
import net.caffeinemc.mods.sodium.client.vk.VkObject;
import org.lwjgl.vulkan.VK13;

public class VkFence extends VkObject {
    protected boolean spent;
    protected VkFenceQueue queue;

    public VkFence(long handle) {
        this.setHandle(handle);
    }

    public void sync() {
        sync(Long.MAX_VALUE);
    }

    public void sync(long timeout) {
        VK13.vkWaitForFences(CinnabarAccess.getDevice(), this.handle(), true, timeout);
        VK13.vkResetFences(CinnabarAccess.getDevice(), this.handle());
        this.spent = true;
        this.queue.giveBack(this);
    }

    public VkFence markReady() {
        this.spent = false;
        return this;
    }

    public boolean returnIfReady() {
        var ready = VK13.vkGetFenceStatus(CinnabarAccess.getDevice(), this.handle()) == VK13.VK_SUCCESS;
        if (ready) {
            this.spent = true;
            this.queue.giveBack(this);
            return true;
        }
        return false;
    }
}
