package net.caffeinemc.mods.sodium.client.vk.renderpass;

import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.vk.VulkanAccess;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkIndexType;
import net.caffeinemc.mods.sodium.client.vk.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.vk.pipeline.VkPipeline;
import net.caffeinemc.mods.sodium.client.vk.pipeline.VkPipelineLayout;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

public class VulkanRenderPass implements AutoCloseable {
    private final VkCommandBuffer commandBuffer;

    public VulkanRenderPass(VkCommandBuffer commandBuffer, long colorTexture) {
        this.commandBuffer = commandBuffer;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var width = Minecraft.getInstance().getMainRenderTarget().width;
            var height = Minecraft.getInstance().getMainRenderTarget().height;
            var depthTexture = VulkanAccess.getView(Minecraft.getInstance().getMainRenderTarget().getDepthTextureView());

            var renderingInfo = VkRenderingInfo.calloc(stack);
            renderingInfo.sType$Default().pColorAttachments(VkRenderingAttachmentInfo.calloc(1, stack).sType$Default().imageView(colorTexture)
                    .imageLayout(VK13.VK_IMAGE_LAYOUT_GENERAL).loadOp(VK13.VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK13.VK_ATTACHMENT_STORE_OP_STORE));
            renderingInfo.pDepthAttachment(VkRenderingAttachmentInfo.calloc(stack).sType$Default().imageView(depthTexture)
                    .imageLayout(VK13.VK_IMAGE_LAYOUT_GENERAL).loadOp(VK13.VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK13.VK_ATTACHMENT_STORE_OP_STORE));
            renderingInfo.layerCount(1);
            renderingInfo.renderArea(rect -> rect.offset(r -> r.set(0, 0)).extent(r  -> r.set(width, height)));

            KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, renderingInfo);

            VK13.vkCmdSetViewport(commandBuffer, 0, VkViewport.calloc(1, stack)
                    .maxDepth(1.0f).minDepth(0.0f).x(0.0f).y(0.0f).width(width).height(height));

            VK13.vkCmdSetScissor(commandBuffer, 0, VkRect2D.calloc(1, stack).offset(i -> i.set(0, 0)).extent(i -> i.set(width, height)));
        }
    }

    @Override
    public void close() {
        KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
    }

    public void bindVertexBuffer(VkBuffer geometryBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VK13.vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(geometryBuffer.handle()), stack.longs(0));
        }
    }

    public void bindIndexBuffer(VkBuffer indexBuffer, VkIndexType indexType) {
        VK13.vkCmdBindIndexBuffer(commandBuffer, indexBuffer.handle(), 0, indexType.getFormatId());
    }

    public void draw(MultiDrawBatch batch) {
       EXTMultiDraw.nvkCmdDrawMultiIndexedEXT(commandBuffer, batch.size, batch.pIndexedInfos, 1, 0, VkMultiDrawIndexedInfoEXT.SIZEOF, 0);
    }

    public void drawIndexedIndirect(VkBuffer buffer, long offset, int drawCount, int stride) {
        VK13.vkCmdDrawIndexedIndirect(commandBuffer, buffer.handle(), offset, drawCount, stride);
    }

    public void drawMeshTasks(int groupCountX, int groupCountY, int groupCountZ) {
        EXTMeshShader.vkCmdDrawMeshTasksEXT(commandBuffer, groupCountX, groupCountY, groupCountZ);
    }

    public void bindPipeline(VkPipelineLayout layout, long pipeline) {
        VK13.vkCmdBindPipeline(commandBuffer, VK13.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
    }

    public void pushConstants(VkPipeline<? extends ChunkShaderInterface> activeProgram, long pushData, int pushConstantSize) {
        VK13.nvkCmdPushConstants(commandBuffer, activeProgram.getLayout().handle(), VK13.VK_SHADER_STAGE_ALL, 0, pushConstantSize, pushData);
    }

    public void pushDescriptors(VkPipeline<? extends ChunkShaderInterface> activeProgram, VkWriteDescriptorSet.Buffer buf) {
        KHRPushDescriptor.vkCmdPushDescriptorSetKHR(commandBuffer, VK13.VK_PIPELINE_BIND_POINT_GRAPHICS, activeProgram.getLayout().handle(), 0, buf);
    }
}
