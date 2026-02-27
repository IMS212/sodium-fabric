package net.caffeinemc.mods.sodium.client.vk;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import graphics.cinnabar.core.hg3d.Hg3DBackend;
import graphics.cinnabar.core.hg3d.Hg3DGpuDevice;
import graphics.cinnabar.core.hg3d.Hg3DGpuSampler;
import graphics.cinnabar.core.hg3d.Hg3DGpuTextureView;
import graphics.cinnabar.core.mercury.*;
import net.caffeinemc.mods.sodium.mixin.core.GpuDeviceAccessor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.*;

public class CinnabarAccess {
    private static VkDevice device;
    private static long allocator;

    public static VkCommandBuffer getCleanCommandBuffer() {
        return ((MercuryCommandBuffer) ((Hg3DGpuDevice) ((GpuDeviceAccessor) RenderSystem.getDevice()).getBackend()).createCommandEncoder().getCommandBuffer()).vkCommandBuffer();
    }

    public static int getSubTexelBits() {
        return ((Hg3DGpuDevice) ((GpuDeviceAccessor) RenderSystem.getDevice()).getBackend()).hgDevice().properties().maxSubTexelBits();
    }

    public static VkDevice getDevice() {
        if (device != null) {
            return device;
        }
        device = ((MercuryDevice) ((Hg3DGpuDevice) ((GpuDeviceAccessor) RenderSystem.getDevice()).getBackend()).hgDevice()).vkDevice();
        return device;
    }

    public static long getAllocator() {
        if (allocator != 0) {
            return allocator;
        }
        allocator = ((MercuryDevice) ((Hg3DGpuDevice) ((GpuDeviceAccessor) RenderSystem.getDevice()).getBackend()).hgDevice()).vmaAllocator();
        return allocator;
    }

    public static long getView(@Nullable GpuTextureView colorTextureView) {
        return ((MercuryImageView) ((Hg3DGpuTextureView) colorTextureView).imageView()).vkImageView();
    }

    public static void registerExtensions() {
        Hg3DBackend.requireExtension("VK_KHR_dynamic_rendering");
        Hg3DBackend.requireExtension("VK_KHR_push_descriptor");
        Hg3DBackend.requireExtension("VK_EXT_multi_draw");
        Hg3DBackend.injectVKFeatureRequirements((stack, features) -> {
            // chain builder
            MercuryDeviceStartup.findOrAlloc(features.address(), KHRDynamicRendering.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES_KHR, VkPhysicalDeviceDynamicRenderingFeaturesKHR::create, VkPhysicalDeviceDynamicRenderingFeaturesKHR::calloc, stack);
            MercuryDeviceStartup.findOrAlloc(features.address(), EXTMultiDraw.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTI_DRAW_FEATURES_EXT, VkPhysicalDeviceMultiDrawFeaturesEXT::create, VkPhysicalDeviceMultiDrawFeaturesEXT::calloc, stack);

        }, (features) -> {
            // feature checker
            return true;
        }, (availableFeatures, enabledFeatures) -> {
            var dynRen = MercuryDeviceStartup.findPNextStruct(enabledFeatures.address(), KHRDynamicRendering.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES_KHR, VkPhysicalDeviceDynamicRenderingFeaturesKHR::create);;
            var multiD = MercuryDeviceStartup.findPNextStruct(enabledFeatures.address(), EXTMultiDraw.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTI_DRAW_FEATURES_EXT, VkPhysicalDeviceMultiDrawFeaturesEXT::create);;
            dynRen.dynamicRendering(true);
            multiD.multiDraw(true);
            // feature enabler
        });
    }

    public static long getSampler(GpuSampler sampler) {
        return ((MercurySampler) ((Hg3DGpuSampler) sampler).sampler()).vkSampler();
    }
}
