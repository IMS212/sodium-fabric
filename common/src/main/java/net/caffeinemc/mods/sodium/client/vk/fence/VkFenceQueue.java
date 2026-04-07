package net.caffeinemc.mods.sodium.client.vk.fence;

import net.caffeinemc.mods.sodium.client.vk.VulkanAccess;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkFenceCreateInfo;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class VkFenceQueue {
    private final Deque<VkFence> free = new ArrayDeque<>();
    private final Set<VkFence> used = new HashSet<>();

    public VkFence take() {
        var fence = free.pollLast();
        if (fence == null) {
            fence = create();
        }

        used.add(fence);

        return fence.markReady();
    }

    private VkFence create() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer outBuffer = stack.mallocLong(1);
            int result = VK13.vkCreateFence(VulkanAccess.getDevice(), VkFenceCreateInfo.calloc(stack).sType$Default(), null, outBuffer);
            if (result != VK13.VK_SUCCESS) {
                throw new RuntimeException("failed to create fence: " + result);
            }
            return new VkFence(outBuffer.get(0), this);
        }
    }

    public void freeAll() {
        for (VkFence fence : this.used) {
            fence.forceSpend(this);
        }

        free.addAll(used);
        used.clear();
    }

    public void giveBack(VkFence vkFence) {
        free.add(vkFence);
        used.remove(vkFence);
    }
}
