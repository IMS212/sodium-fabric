package net.caffeinemc.mods.sodium.client.vk.fence;

import net.caffeinemc.mods.sodium.client.vk.CinnabarAccess;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkFenceCreateInfo;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

public class VkFenceQueue {
    private final Deque<VkFence> free = new ArrayDeque<>();

    public VkFence take() {
        var fence = free.pollLast();
        if (fence == null) {
            fence = create();
        }

        return fence.markReady();
    }

    private VkFence create() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer outBuffer = stack.mallocLong(1);
            int result = VK13.vkCreateFence(CinnabarAccess.getDevice(), VkFenceCreateInfo.calloc(stack).sType$Default(), null, outBuffer);
            if (result != VK13.VK_SUCCESS) {
                throw new RuntimeException("failed to create fence: " + result);
            }
            return new VkFence(outBuffer.get(0));
        }
    }

    public void giveBack(VkFence vkFence) {
        free.add(vkFence);
    }
}
