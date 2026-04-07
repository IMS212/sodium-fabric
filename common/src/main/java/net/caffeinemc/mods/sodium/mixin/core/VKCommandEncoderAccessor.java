package net.caffeinemc.mods.sodium.mixin.core;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VulkanCommandEncoder.class)
public interface VKCommandEncoderAccessor {
    @Invoker("commandBuffer")
    VkCommandBuffer getCommandBuffer();

    @Accessor("currentRenderPass")
    VulkanRenderPass getRenderPass();
}
