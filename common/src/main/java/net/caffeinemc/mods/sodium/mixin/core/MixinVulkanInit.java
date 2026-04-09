package net.caffeinemc.mods.sodium.mixin.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.vulkan.*;
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

    @Shadow
    @Final
    public static VulkanPNextStruct VK10_FEATURES_STRUCT;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void addExtension(CallbackInfo ci) {
        REQUIRED_DEVICE_EXTENSIONS = new HashSet<>(REQUIRED_DEVICE_EXTENSIONS);
        REQUIRED_DEVICE_EXTENSIONS.add("VK_EXT_multi_draw");
        REQUIRED_DEVICE_FEATURES = new HashSet<>(REQUIRED_DEVICE_FEATURES);
        VulkanPNextStruct A = new VulkanPNextStruct(EXTMultiDraw.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTI_DRAW_FEATURES_EXT, VkPhysicalDeviceMultiDrawFeaturesEXT.SIZEOF);

        REQUIRED_DEVICE_FEATURES.add(new VulkanFeature(A, "multiDraw", VkPhysicalDeviceMultiDrawFeaturesEXT.MULTIDRAW));
        VulkanPNextStruct B = new VulkanPNextStruct(VK12.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES, VkPhysicalDeviceVulkan11Features.SIZEOF);
        VulkanPNextStruct C = new VulkanPNextStruct(VK12.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES, VkPhysicalDeviceVulkan12Features.SIZEOF);

        REQUIRED_DEVICE_FEATURES.add(new VulkanFeature(B, "shaderDrawParameters", VkPhysicalDeviceVulkan11Features.SHADERDRAWPARAMETERS));
        REQUIRED_DEVICE_FEATURES.add(new VulkanFeature(VK10_FEATURES_STRUCT, "shaderInt64", VkPhysicalDeviceFeatures.SHADERINT64));
        REQUIRED_DEVICE_FEATURES.add(new VulkanFeature(C, "bufferDeviceAddress", VkPhysicalDeviceVulkan12Features.BUFFERDEVICEADDRESS));
        REQUIRED_DEVICE_FEATURES.add(new VulkanFeature(C, "uniformBufferStandardLayout", VkPhysicalDeviceVulkan12Features.UNIFORMBUFFERSTANDARDLAYOUT));
        REQUIRED_DEVICE_FEATURES.add(new VulkanFeature(C, "scalarBlockLayout", VkPhysicalDeviceVulkan12Features.SCALARBLOCKLAYOUT));

    }

    @WrapOperation(method = "createVma", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/vma/VmaAllocatorCreateInfo;vulkanApiVersion(I)Lorg/lwjgl/util/vma/VmaAllocatorCreateInfo;"))
    private static VmaAllocatorCreateInfo createVma(VmaAllocatorCreateInfo instance, int value, Operation<VmaAllocatorCreateInfo> original) {
        return original.call(instance, value).flags(Vma.VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT);
    }
}
