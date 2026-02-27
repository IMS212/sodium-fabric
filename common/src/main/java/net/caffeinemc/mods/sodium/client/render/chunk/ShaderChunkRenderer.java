package net.caffeinemc.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.caffeinemc.mods.sodium.client.gui.SodiumConfigBuilder;
import net.caffeinemc.mods.sodium.client.vk.CinnabarAccess;
import net.caffeinemc.mods.sodium.client.vk.attribute.VkVertexFormat;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import net.caffeinemc.mods.sodium.client.vk.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.*;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.vk.pipeline.*;
import net.caffeinemc.mods.sodium.client.vk.renderpass.VulkanRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.mixin.core.CommandEncoderAccessor;
import net.caffeinemc.mods.sodium.mixin.core.GlCommandEncoderAccessor;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VK14;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private final Map<TerrainRenderPass, VkPipeline<? extends ChunkShaderInterface>> programs = new Object2ObjectOpenHashMap<>();

    protected final ChunkVertexType vertexType;
    protected final VkVertexFormat vertexFormat;

    protected final RenderDevice device;
    private final VkPipelineLayout layout;
    private final VkDescriptorSetLayoutBuilder.VkDescriptorSetLayout setLayout;

    protected VkPipeline<? extends ChunkShaderInterface> activeProgram;

    public ShaderChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        this.device = device;
        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getVertexFormat();

        this.setLayout = VkDescriptorSetLayoutBuilder.create(CinnabarAccess.getDevice())
                .addBinding(0, VK13.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK13.VK_SHADER_STAGE_ALL).addBinding(1, VK13.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK13.VK_SHADER_STAGE_ALL).flags(VK14.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT).build();
        this.layout = VkPipelineLayoutBuilder.create(CinnabarAccess.getDevice())
                .pushConstants(new VkPipelineLayoutBuilder.PushConstantRange(VK13.VK_SHADER_STAGE_ALL, 0, DefaultShaderInterface.PUSH_CONSTANT_SIZE))
                .setLayouts(setLayout.handle())
                .build();
    }

    protected VkPipeline<? extends ChunkShaderInterface> compileProgram(TerrainRenderPass pass) {
        VkPipeline<? extends ChunkShaderInterface> program = this.programs.get(pass);

        if (program == null) {
            this.programs.put(pass, program = this.createShader("blocks/block_layer_opaque", pass));
        }

        return program;
    }

    private VkPipeline<? extends ChunkShaderInterface> createShader(String path, TerrainRenderPass pass) {
        byte[] data;

        try (InputStream inputStream = SodiumConfigBuilder.class.getResourceAsStream("/assets/sodium/shaders/terrain.spv")) {
            data = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long specData = stack.nmalloc(4);
            MemoryUtil.memPutInt(specData, pass.supportsFragmentDiscard() ? 1 : 0);

            VkGraphicsPipelineBuilder.Specialization spec =
                    new VkGraphicsPipelineBuilder.Specialization(new int[] { 0 }, new int[] { 0 }, new int[] { 4 }, MemoryUtil.memByteBuffer(specData, 4));

            return VkPipeline.create(layout, builder -> {
                builder.shared(data, "vertexMain", "fragmentMain", spec)
                        .dynamicRendering(new int[] { VK13.VK_FORMAT_R8G8B8A8_UNORM }, VK13.VK_FORMAT_D32_SFLOAT, VK13.VK_FORMAT_UNDEFINED)
                        .pushConstants(new VkGraphicsPipelineBuilder.PushConstantRange(VK13.VK_SHADER_STAGE_ALL, 0, DefaultShaderInterface.PUSH_CONSTANT_SIZE))
                        .setLayouts(setLayout.handle())
                        .addVertexBinding(0, vertexType.getVertexFormat().getStride(), VK13.VK_VERTEX_INPUT_RATE_VERTEX)
                        .rasterization(VK13.VK_POLYGON_MODE_FILL, VK13.VK_CULL_MODE_BACK_BIT, VK13.VK_FRONT_FACE_CLOCKWISE)
                        .depthStencil(true, true, VK13.VK_COMPARE_OP_LESS_OR_EQUAL)
                        .setColorBlendAttachments(pass.isTranslucent() ? VkGraphicsPipelineBuilder.ColorBlendAttachment.alpha() : VkGraphicsPipelineBuilder.ColorBlendAttachment.disabled());

                for (var attribute : vertexType.getVertexFormat().getShaderBindings()) {
                    builder.addVertexAttribute(attribute.getIndex(), 0, attribute.getFormat().vkFormat(), attribute.getPointer());
                }

                return builder;
            }, DefaultShaderInterface.class);
        }
    }

    protected void begin(VulkanRenderPass renderPass, TerrainRenderPass pass, FogParameters parameters, GpuSampler terrainSampler) {
        this.activeProgram = this.compileProgram(pass);
        this.activeProgram.bind(renderPass);
        this.activeProgram.getInterface()
                .setupState(pass, parameters, terrainSampler);
    }

    protected void end(TerrainRenderPass pass) {
        this.activeProgram.getInterface()
                .resetState();
        this.activeProgram = null;
    }

    @Override
    public void delete(CommandList commandList) {
        this.programs.values()
                .forEach(RenderDevice.INSTANCE::destroyObjectWhenSafe);
    }

}
