package net.caffeinemc.mods.sodium.mixin.core;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import org.lwjgl.vulkan.EXTMultiDraw;
import org.lwjgl.vulkan.VkPhysicalDeviceMultiDrawFeaturesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceSynchronization2Features;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(VulkanBackend.class)
public class MixinVulkanInit {
    @Shadow
    @Final
    @Mutable
    public static Set<String> REQUIRED_DEVICE_EXTENSIONS;
    @Shadow
    @Final
    @Mutable
    public static Set<VulkanFeature> REQUIRED_DEVICE_FEATURES;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void addExtension(CallbackInfo ci) {
        REQUIRED_DEVICE_EXTENSIONS = new HashSet<>(REQUIRED_DEVICE_EXTENSIONS);
        REQUIRED_DEVICE_EXTENSIONS.add("VK_EXT_multi_draw");
        REQUIRED_DEVICE_FEATURES = new HashSet<>(REQUIRED_DEVICE_FEATURES);
        VulkanPNextStruct A = new VulkanPNextStruct(EXTMultiDraw.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTI_DRAW_FEATURES_EXT, VkPhysicalDeviceMultiDrawFeaturesEXT.SIZEOF);

        REQUIRED_DEVICE_FEATURES.add(new VulkanFeature(A, "multiDraw", VkPhysicalDeviceMultiDrawFeaturesEXT.MULTIDRAW));

    }
}
