package net.caffeinemc.mods.sodium.client.vk.pipeline;

import net.caffeinemc.mods.sodium.client.vk.VulkanAccess;
import net.caffeinemc.mods.sodium.client.vk.VkObjectDestroyable;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import net.caffeinemc.mods.sodium.client.vk.renderpass.VulkanRenderPass;
import org.lwjgl.vulkan.VK13;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

public final class VkPipeline<T> extends VkObjectDestroyable {
    private final T shaderInterface;
    private final VkPipelineLayout layout;

    private VkPipeline(long pipeline, VkPipelineLayout layout, T shaderInterface) {
        this.setHandle(pipeline);
        this.layout = layout;
        this.shaderInterface = shaderInterface;
    }

    public static <T> VkPipeline<T> create(VkPipelineLayout layout, Function<VkGraphicsPipelineBuilder, VkGraphicsPipelineBuilder> pipelineSetup, Class<T> shaderInterfaceClass) {
        VkGraphicsPipelineBuilder builder = VkGraphicsPipelineBuilder.create(VulkanAccess.getDevice(), layout.handle());
        builder = pipelineSetup.apply(builder);

        long pipeline = builder.build();
        T shaderInterface = createShaderInterface(shaderInterfaceClass);

        return new VkPipeline<>(pipeline, layout, shaderInterface);
    }

    public T getInterface() {
        return this.shaderInterface;
    }

    @Override
    protected void destroyInternal(CommandList commandList) {
        VK13.vkDestroyPipeline(VulkanAccess.getDevice(), this.handle(), null);
    }

    public void bind(VulkanRenderPass renderPass) {
        renderPass.bindPipeline(layout, this.handle());
    }

    public VkPipelineLayout getLayout() {
        return this.layout;
    }

    private static <T> T createShaderInterface(Class<T> shaderInterfaceClass) {
        try {
            return shaderInterfaceClass.getConstructor().newInstance();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to construct shader interface " + shaderInterfaceClass.getName(), e);
        }
    }
}
