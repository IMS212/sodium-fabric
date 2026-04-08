package net.caffeinemc.mods.sodium.client.vk.pipeline;

import net.caffeinemc.mods.sodium.client.vk.VkObject;
import net.caffeinemc.mods.sodium.client.vk.VkObjectDestroyable;
import net.caffeinemc.mods.sodium.client.vk.VulkanAccess;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

public class VkComputePipeline extends VkObjectDestroyable {
    private final long pipelineLayout;
    private final long pipeline;

    public VkComputePipeline(byte[] data, String name, VkDescriptorSetLayoutBuilder.VkDescriptorSetLayout descriptorLayout, String entryPoint) {
        this(data, name, descriptorLayout, entryPoint, 0);
    }

    public VkComputePipeline(byte[] data, String name, VkDescriptorSetLayoutBuilder.VkDescriptorSetLayout descriptorLayout, String entryPoint, int pushConstantSize) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer code = stack.malloc(data.length);
            code.put(data).flip();

            VkShaderModuleCreateInfo shaderInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(code);

            VkDevice device = VulkanAccess.getDevice();

            LongBuffer pShaderModule = stack.mallocLong(1);
            if (VK13.vkCreateShaderModule(device, shaderInfo, null, pShaderModule) != VK13.VK_SUCCESS) {
                throw new RuntimeException("failed to create shader module");
            }

            long shaderModule = pShaderModule.get(0);

            VkPipelineLayoutCreateInfo pipelineLayout = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default().setLayoutCount(1).pSetLayouts(stack.longs(descriptorLayout.handle()));

            if (pushConstantSize > 0) {
                pipelineLayout.pPushConstantRanges(VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK13.VK_SHADER_STAGE_COMPUTE_BIT)
                        .offset(0)
                        .size(pushConstantSize));
            }

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            if (VK13.vkCreatePipelineLayout(device, pipelineLayout, null, pPipelineLayout) != VK13.VK_SUCCESS) {
                throw new RuntimeException("failed to create pipeline layout");
            }

            this.pipelineLayout = pPipelineLayout.get(0);

            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType$Default()
                    .stage(VK13.VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(shaderModule)
                    .pName(stack.UTF8(entryPoint));

            VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .stage(stage)
                    .layout(this.pipelineLayout);

            LongBuffer pPipeline = stack.mallocLong(1);
            var x = VK13.vkCreateComputePipelines(device, 0, pipelineInfo, null, pPipeline);
            if (x != VK13.VK_SUCCESS) {
                throw new RuntimeException("failed to create compute pipeline: " + x);
            }

            this.pipeline = pPipeline.get(0);

            VK13.vkDestroyShaderModule(device, shaderModule, null);
        }
    }

    public void pushDescriptor(CommandList commandList, VkWriteDescriptorSet.Buffer buf) {
        KHRPushDescriptor.vkCmdPushDescriptorSetKHR(commandList.getCommandBuffer(), VK13.VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0, buf);
    }

    public void dispatch(CommandList commandList, int x, int y, int z) {
        VK13.vkCmdBindPipeline(commandList.getCommandBuffer(), VK13.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        VK13.vkCmdDispatch(commandList.getCommandBuffer(), x, y, z);
    }

    public void pushConstants(CommandList commandList, long pushData, int pushConstantSize) {
        VK13.nvkCmdPushConstants(commandList.getCommandBuffer(), pipelineLayout, VK13.VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstantSize, pushData);
    }

    @Override
    protected void destroyInternal(CommandList commandList) {
        VK13.vkDestroyPipeline(VulkanAccess.getDevice(), pipeline, null);
        VK13.vkDestroyPipelineLayout(VulkanAccess.getDevice(), pipelineLayout, null);
    }
}
