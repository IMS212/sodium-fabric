package net.caffeinemc.mods.sodium.client.vk;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuSampler;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import net.caffeinemc.mods.sodium.mixin.core.GpuDeviceAccessor;
import net.caffeinemc.mods.sodium.mixin.core.VKCommandEncoderAccessor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.*;

public class VulkanAccess {
    private static VkDevice device;
    private static long allocator;

    public static VkCommandBuffer getCleanCommandBuffer() {

        return (((VKCommandEncoderAccessor) getVKDevice().createCommandEncoder())).getCommandBuffer();
    }

    public static int getSubTexelBits() {
        return 8;
    }

    public static VkDevice getDevice() {
        if (device != null) {
            return device;
        }
        device = getVKDevice().vkDevice();
        return device;
    }

    public static long getAllocator() {
        if (allocator != 0) {
            return allocator;
        }
        allocator = getVKDevice().vma();
        return allocator;
    }

    private static VulkanDevice getVKDevice() {
        return ((VulkanDevice) ((GpuDeviceAccessor) RenderSystem.getDevice()).getBackend());
    }

    public static long getView(@Nullable GpuTextureView colorTextureView) {
        return ((VulkanGpuTextureView) colorTextureView).vkImageView();
    }

    public static void registerExtensions() {

    }

    public static long getSampler(GpuSampler sampler) {
        return ((VulkanGpuSampler) sampler).vkSampler();
    }
}
