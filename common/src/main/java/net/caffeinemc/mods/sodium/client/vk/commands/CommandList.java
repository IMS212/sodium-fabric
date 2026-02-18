package net.caffeinemc.mods.sodium.client.vk.commands;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.caffeinemc.mods.sodium.client.vk.SodiumRenderPass;
import net.caffeinemc.mods.sodium.client.vk.VulkanContext;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO;
import static org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_RENDERING_INFO;
import static org.lwjgl.vulkan.VK13.vkCmdBeginRendering;

public final class CommandList implements AutoCloseable {
    private final VkCommandBuffer vkCommandBuffer;

    public CommandList(VkCommandBuffer vkCommandBuffer) {
        this.vkCommandBuffer = vkCommandBuffer;
    }

    public void flush() {
        // we don't own this command buffer. don't do that.
    }

    @Override
    public void close() {
        this.flush();
    }

    public SodiumRenderPass createRenderPass(@Nullable GpuTextureView colorTextureView, @Nullable GpuTextureView depthTextureView) {
        int width = Integer.MAX_VALUE;
        int height = Integer.MAX_VALUE;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingAttachmentInfo.Buffer colorAttachment = null;

            if (colorTextureView != null) {
                long colorImageView = VulkanContext.INSTANCE.getVulkanImageView(colorTextureView);

                width = Math.min(width, colorTextureView.getWidth(0));
                height = Math.min(height, colorTextureView.getHeight(0));

                colorAttachment = VkRenderingAttachmentInfo.calloc(1, stack);
                colorAttachment.get(0).sType$Default().imageView(colorImageView).imageLayout(VK_IMAGE_LAYOUT_GENERAL).loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            }

            VkRenderingAttachmentInfo depthAttachment = null;

            if (depthTextureView != null) {
                long depthImageView = VulkanContext.INSTANCE.getVulkanImageView(depthTextureView);

                width = Math.min(width, depthTextureView.getWidth(0));
                height = Math.min(height, depthTextureView.getHeight(0));

                depthAttachment = VkRenderingAttachmentInfo.calloc(stack).sType$Default().imageView(depthImageView).imageLayout(VK_IMAGE_LAYOUT_GENERAL).loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            }

            VkRenderingInfo renderingInfo = VkRenderingInfo.calloc(stack).sType$Default().layerCount(1).viewMask(0);
            renderingInfo.renderArea().offset().set(0, 0);
            renderingInfo.renderArea().extent().set(width, height);

            if (colorAttachment != null) {
                renderingInfo.pColorAttachments(colorAttachment);
            }

            if (depthAttachment != null) {
                renderingInfo.pDepthAttachment(depthAttachment);
            }

            vkCmdBeginRendering(this.vkCommandBuffer, renderingInfo);
        }

        return new SodiumRenderPass(this.vkCommandBuffer);
    }
}
