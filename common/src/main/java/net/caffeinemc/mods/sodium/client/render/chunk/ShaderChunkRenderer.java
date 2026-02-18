package net.caffeinemc.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.textures.GpuSampler;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderType;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.vk.VulkanContext;
import net.caffeinemc.mods.sodium.client.vk.attribute.VkVertexFormat;
import net.caffeinemc.mods.sodium.client.vk.commands.CommandList;
import net.caffeinemc.mods.sodium.client.vk.pipeline.VkGraphicsPipeline;
import net.caffeinemc.mods.sodium.client.vk.pipeline.VkGraphicsPipelineBuilder;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;
import java.util.Map;

import static org.lwjgl.vulkan.KHRPushDescriptor.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR;
import static org.lwjgl.vulkan.VK10.*;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private final Map<ChunkShaderOptions, VkShaderProgram> programs = new Object2ObjectOpenHashMap<>();

    protected final ChunkVertexType vertexType;
    protected final VkVertexFormat vertexFormat;
    protected final long pushDescriptorLayout;

    protected VkShaderProgram activeProgram;

    public ShaderChunkRenderer(ChunkVertexType vertexType) {
        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getVertexFormat();
        this.pushDescriptorLayout = createChunkDescriptorSetLayout(VulkanContext.INSTANCE.vkDevice());
    }

    protected VkShaderProgram compileProgram(ChunkShaderOptions options) {
        VkShaderProgram program = this.programs.get(options);

        if (program == null) {
            this.programs.put(options, program = this.createProgram("blocks/block_layer_opaque", options));
        }

        return program;
    }

    private VkShaderProgram createProgram(String path, ChunkShaderOptions options) {
        var pipeline = new VkGraphicsPipelineBuilder()
                .setVertexFormat(options.vertexType().getVertexFormat())
                .setFrontFace(VK_FRONT_FACE_CLOCKWISE)
                .setDepthState(true, !options.pass().isTranslucent())
                .setDescriptorSetLayouts(this.pushDescriptorLayout)
                .setPushConstants(VK_SHADER_STAGE_ALL, 0, DefaultShaderInterface.PUSH_CONSTANT_SIZE)
                .setFormats(new int[]{ VK_FORMAT_R8G8B8A8_UNORM }, VK_FORMAT_D32_SFLOAT)
                .addShader(ShaderLoader.loadShaderSource(ShaderType.VERTEX, Identifier.fromNamespaceAndPath("sodium", path + ".vsh"), options.constants()))
                .addShader(ShaderLoader.loadShaderSource(ShaderType.FRAGMENT, Identifier.fromNamespaceAndPath("sodium", path + ".fsh"), options.constants()));

        if (options.pass().isTranslucent()) pipeline.turnOnBlend();

        var built = pipeline.build();

        return new VkShaderProgram(built, new DefaultShaderInterface(built, options));
    }

    protected void begin(TerrainRenderPass pass, FogParameters parameters, GpuSampler terrainSampler) {
        ChunkFogMode fogMode = parameters.equals(FogParameters.NONE) ? ChunkFogMode.NONE : ChunkFogMode.SMOOTH;
        ChunkShaderOptions options = new ChunkShaderOptions(fogMode, pass, this.vertexType);

        this.activeProgram = this.compileProgram(options);
        this.activeProgram.getInterface().setupState(pass, parameters, terrainSampler);
    }

    protected void end(TerrainRenderPass pass) {
        this.activeProgram.getInterface().resetState();
        this.activeProgram = null;
    }

    @Override
    public void delete(CommandList commandList) {
        this.programs.values().forEach(VkShaderProgram::delete);
        this.programs.clear();
        vkDestroyDescriptorSetLayout(VulkanContext.INSTANCE.vkDevice(), this.pushDescriptorLayout, null);
    }

    private static long createChunkDescriptorSetLayout(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(3, stack);

            bindings.get(0)
                    .binding(0)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            bindings.get(1)
                    .binding(1)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

            bindings.get(2)
                    .binding(2)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);

            VkDescriptorSetLayoutCreateInfo createInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR)
                    .pBindings(bindings);

            LongBuffer pLayout = stack.mallocLong(1);
            int result = vkCreateDescriptorSetLayout(device, createInfo, null, pLayout);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateDescriptorSetLayout failed " + result);
            }

            return pLayout.get(0);
        }
    }

    protected static final class VkShaderProgram {
        private final VkGraphicsPipeline pipeline;
        private final ChunkShaderInterface shaderInterface;

        private VkShaderProgram(VkGraphicsPipeline pipeline, ChunkShaderInterface shaderInterface) {
            this.pipeline = pipeline;
            this.shaderInterface = shaderInterface;
        }

        public ChunkShaderInterface getInterface() {
            return this.shaderInterface;
        }

        public VkGraphicsPipeline getPipeline() {
            return this.pipeline;
        }

        public void delete() {
            this.pipeline.delete();
        }
    }
}
