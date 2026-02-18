package net.caffeinemc.mods.sodium.client.vk;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import graphics.cinnabar.api.c3d.C3DGpuDevice;
import graphics.cinnabar.core.hg3d.Hg3DGpuTextureView;
import graphics.cinnabar.core.mercury.MercuryCommandBuffer;
import graphics.cinnabar.core.mercury.MercuryDevice;
import graphics.cinnabar.core.mercury.MercuryImageView;
import net.caffeinemc.mods.sodium.client.vk.commands.CommandList;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;

public final class VulkanContext {
    public static final VulkanContext INSTANCE = new VulkanContext();

    private static final int FRAMES_IN_FLIGHT = 3;

    private final C3DGpuDevice device;
    private final VkDevice vkDevice;
    private final long vmaAllocator;

    private final ArrayDeque<List<DeferredBufferDelete>> deleteEventually = new ArrayDeque<>();
    private List<DeferredBufferDelete> pendingDeletes = new ArrayList<>();

    private VulkanContext() {
        this.device = (C3DGpuDevice) RenderSystem.getDevice().backend;
        this.vkDevice = this.device.hgDevice().vkDevice();
        this.vmaAllocator = ((MercuryDevice) this.device.hgDevice()).vmaAllocator();
    }

    public VkDevice vkDevice() {
        return this.vkDevice;
    }

    public long vmaAllocator() {
        return this.vmaAllocator;
    }

    public VkCommandBuffer earlyCommandBuffer() {
        throw new UnsupportedOperationException("Early command buffers are not supported yet :(");
    }

    public VkCommandBuffer mainCommandBuffer() {
        return ((MercuryCommandBuffer) this.device.createCommandEncoder().getCommandBuffer()).vkCommandBuffer();
    }

    public VkCommandBuffer currentCommandBuffer() {
        return ((MercuryCommandBuffer) this.device.createCommandEncoder().getCommandBufferForcefully()).vkCommandBuffer();
    }

    public CommandList createCommandList() {
        return new CommandList(this.mainCommandBuffer());
    }

    public long getVulkanImageView(GpuTextureView gpuTextureView) {
        return ((MercuryImageView) ((Hg3DGpuTextureView) gpuTextureView).imageView()).vkImageView();
    }

    public synchronized void advanceFrame() {
        this.deleteEventually.addLast(this.pendingDeletes);
        this.pendingDeletes = new ArrayList<>();

        while (this.deleteEventually.size() > FRAMES_IN_FLIGHT) {
            this.destroyBuffers(this.deleteEventually.removeFirst());
        }
    }

    public synchronized void destroyLater(long buffer, long allocation) {
        if (buffer == 0L || allocation == 0L) {
            return;
        }

        this.pendingDeletes.add(new DeferredBufferDelete(buffer, allocation));
    }

    public synchronized void flushDeferredBufferDeletes() {
        this.destroyBuffers(this.pendingDeletes);
        this.pendingDeletes = new ArrayList<>();

        while (!this.deleteEventually.isEmpty()) {
            this.destroyBuffers(this.deleteEventually.removeFirst());
        }
    }

    private void destroyBuffers(List<DeferredBufferDelete> deletes) {
        if (deletes.isEmpty()) {
            return;
        }

        for (var entry : deletes) {
            vmaDestroyBuffer(this.vmaAllocator, entry.buffer(), entry.allocation());
        }
    }

    private record DeferredBufferDelete(long buffer, long allocation) {
    }
}
