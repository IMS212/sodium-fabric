package net.caffeinemc.mods.sodium.client.vk.fence;

import net.caffeinemc.mods.sodium.client.vk.VulkanAccess;
import net.caffeinemc.mods.sodium.client.vk.VkObject;
import org.lwjgl.vulkan.VK13;

public class VkFence extends VkObject {
    protected boolean spent;
    protected VkFenceQueue queue;

    public VkFence(long handle, VkFenceQueue queue) {
        this.setHandle(handle);
        this.queue = queue;
    }

    public void sync() {
        sync(Long.MAX_VALUE);
    }

    public void sync(long timeout) {
        VK13.vkWaitForFences(VulkanAccess.getDevice(), this.handle(), true, timeout);
        VK13.vkResetFences(VulkanAccess.getDevice(), this.handle());
        this.spent = true;
        this.queue.giveBack(this);
    }

    public VkFence markReady() {
        this.spent = false;
        return this;
    }

    public boolean returnIfReady() {
        var ready = VK13.vkGetFenceStatus(VulkanAccess.getDevice(), this.handle()) == VK13.VK_SUCCESS;
        if (ready) {
            this.spent = true;
            VK13.vkResetFences(VulkanAccess.getDevice(), this.handle());
            this.queue.giveBack(this);
            return true;
        }
        return false;
    }

    public void forceSpend(VkFenceQueue vkFenceQueue) {
        this.spent = true;
        VK13.vkResetFences(VulkanAccess.getDevice(), this.handle());
    }
}
