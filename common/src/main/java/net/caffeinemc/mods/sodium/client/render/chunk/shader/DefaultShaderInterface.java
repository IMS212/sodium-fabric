package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.vk.VulkanContext;
import net.caffeinemc.mods.sodium.client.vk.pipeline.VkGraphicsPipeline;
import net.caffeinemc.mods.sodium.mixin.core.render.texture.TextureAtlasAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

public class DefaultShaderInterface implements ChunkShaderInterface {
    public static final int PUSH_CONSTANT_SIZE = 200;

    private static final int OFFSET_PROJECTION_MATRIX = 0;
    private static final int OFFSET_MODEL_VIEW_MATRIX = 64;
    private static final int OFFSET_REGION_X = 128;
    private static final int OFFSET_REGION_Y = 132;
    private static final int OFFSET_REGION_Z = 136;
    private static final int OFFSET_CURRENT_TIME = 140;
    private static final int OFFSET_FADE_PERIOD_INV = 144;
    private static final int OFFSET_FOG_R = 148;
    private static final int OFFSET_FOG_G = 152;
    private static final int OFFSET_FOG_B = 156;
    private static final int OFFSET_FOG_A = 160;
    private static final int OFFSET_ENV_FOG_START = 164;
    private static final int OFFSET_ENV_FOG_END = 168;
    private static final int OFFSET_RENDER_FOG_START = 172;
    private static final int OFFSET_RENDER_FOG_END = 176;
    private static final int OFFSET_TEXCOORD_SHRINK_X = 180;
    private static final int OFFSET_TEXCOORD_SHRINK_Y = 184;
    private static final int OFFSET_TEXEL_SIZE_X = 188;
    private static final int OFFSET_TEXEL_SIZE_Y = 192;
    private static final int OFFSET_USE_RGSS = 196;

    private final VkGraphicsPipeline pipeline;
    private final long pcs = MemoryUtil.nmemAlloc(PUSH_CONSTANT_SIZE);

    private long blockTextureImageView;
    private long lightTextureImageView;
    private long textureSampler;
    private long chunkDataBuffer;

    public DefaultShaderInterface(VkGraphicsPipeline pipeline, ChunkShaderOptions options) {
        this.pipeline = pipeline;
    }

    @Override
    public void setupState(TerrainRenderPass pass, FogParameters parameters, GpuSampler terrainSampler) {
        VkCommandBuffer commandBuffer = VulkanContext.INSTANCE.currentCommandBuffer();
        if (commandBuffer == null) {
            return;
        }

        VK13.vkCmdBindPipeline(commandBuffer, VK13.VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline.pipeline());

        int width = Minecraft.getInstance().getWindow().getWidth();
        int height = Minecraft.getInstance().getWindow().getHeight();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0).x(0.0f).y(0.0f).width(width).height(height).minDepth(0.0f).maxDepth(1.0f);
            VK13.vkCmdSetViewport(commandBuffer, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).offset().set(0, 0);
            scissor.get(0).extent().set(width, height);
            VK13.vkCmdSetScissor(commandBuffer, 0, scissor);
        }

        var textureAtlas = (TextureAtlasAccessor) Minecraft.getInstance()
                .getTextureManager()
                .getTexture(TextureAtlas.LOCATION_BLOCKS);

        double subTexelPrecision = 256.0;
        double subTexelOffset = 1.0 / CompactChunkVertex.TEXTURE_MAX_VALUE;
        MemoryUtil.memPutFloat(this.pcs + OFFSET_TEXCOORD_SHRINK_X, (float) (subTexelOffset - (((1.0D / textureAtlas.getWidth()) / subTexelPrecision))));
        MemoryUtil.memPutFloat(this.pcs + OFFSET_TEXCOORD_SHRINK_Y, (float) (subTexelOffset - (((1.0D / textureAtlas.getHeight()) / subTexelPrecision))));

        MemoryUtil.memPutFloat(this.pcs + OFFSET_FADE_PERIOD_INV, (float) (1.0 / (Minecraft.getInstance().options.chunkSectionFadeInTime().get() * 1000.0)));
        MemoryUtil.memPutFloat(this.pcs + OFFSET_FOG_R, parameters.red());
        MemoryUtil.memPutFloat(this.pcs + OFFSET_FOG_G, parameters.green());
        MemoryUtil.memPutFloat(this.pcs + OFFSET_FOG_B, parameters.blue());
        MemoryUtil.memPutFloat(this.pcs + OFFSET_FOG_A, parameters.alpha());
        MemoryUtil.memPutFloat(this.pcs + OFFSET_ENV_FOG_START, parameters.environmentalStart());
        MemoryUtil.memPutFloat(this.pcs + OFFSET_ENV_FOG_END, parameters.environmentalEnd());
        MemoryUtil.memPutFloat(this.pcs + OFFSET_RENDER_FOG_START, parameters.renderStart());
        MemoryUtil.memPutFloat(this.pcs + OFFSET_RENDER_FOG_END, parameters.renderEnd());
        MemoryUtil.memPutFloat(this.pcs + OFFSET_TEXEL_SIZE_X, 1.0f / textureAtlas.getWidth());
        MemoryUtil.memPutFloat(this.pcs + OFFSET_TEXEL_SIZE_Y, 1.0f / textureAtlas.getHeight());
        MemoryUtil.memPutInt(this.pcs + OFFSET_USE_RGSS, Minecraft.getInstance().options.textureFiltering().get() == TextureFilteringMethod.RGSS ? 1 : 0);

        this.pushThoseConstants(commandBuffer);
    }

    @Override
    public void resetState() {
    }

    @Override
    public void setProjectionMatrix(Matrix4fc matrix) {
        matrix.getToAddress(this.pcs + OFFSET_PROJECTION_MATRIX);
    }

    @Override
    public void setModelViewMatrix(Matrix4fc matrix) {
        matrix.getToAddress(this.pcs + OFFSET_MODEL_VIEW_MATRIX);
    }

    @Override
    public void setRegionOffset(float x, float y, float z) {
        MemoryUtil.memPutFloat(this.pcs + OFFSET_REGION_X, x);
        MemoryUtil.memPutFloat(this.pcs + OFFSET_REGION_Y, y);
        MemoryUtil.memPutFloat(this.pcs + OFFSET_REGION_Z, z);
    }

    @Override
    public void setChunkData(long buffer, int time) {
        this.chunkDataBuffer = buffer;
        MemoryUtil.memPutInt(this.pcs + OFFSET_CURRENT_TIME, time);
    }

    public long getChunkDataBuffer() {
        return this.chunkDataBuffer;
    }

    public void uploadPushConstants() {
        VkCommandBuffer commandBuffer = VulkanContext.INSTANCE.currentCommandBuffer();
        if (commandBuffer == null) {
            return;
        }

        this.uploadPushConstants(commandBuffer);
    }

    @Override
    public void setDescriptorHandles(long blockTextureImageView, long lightTextureImageView, long textureSampler) {
        this.blockTextureImageView = blockTextureImageView;
        this.lightTextureImageView = lightTextureImageView;
        this.textureSampler = textureSampler;
    }

    private void uploadPushConstants(VkCommandBuffer commandBuffer) {
        this.pushThoseConstants(commandBuffer);
        this.pushThoseObjects(commandBuffer);
    }

    private void pushThoseConstants(VkCommandBuffer commandBuffer) {
        VK10.nvkCmdPushConstants(
                commandBuffer,
                this.pipeline.layout(),
                VK13.VK_SHADER_STAGE_ALL,
                0,
                PUSH_CONSTANT_SIZE,
                this.pcs
        );
    }

    private void pushThoseObjects(VkCommandBuffer commandBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer blockTextureInfo = VkDescriptorImageInfo.calloc(1, stack);
            blockTextureInfo.get(0)
                    .sampler(this.textureSampler)
                    .imageView(this.blockTextureImageView)
                    .imageLayout(VK13.VK_IMAGE_LAYOUT_GENERAL);

            VkDescriptorImageInfo.Buffer lightTextureInfo = VkDescriptorImageInfo.calloc(1, stack);
            lightTextureInfo.get(0)
                    .sampler(this.textureSampler)
                    .imageView(this.lightTextureImageView)
                    .imageLayout(VK13.VK_IMAGE_LAYOUT_GENERAL);

            VkDescriptorBufferInfo.Buffer chunkDataInfo = VkDescriptorBufferInfo.calloc(1, stack);
            chunkDataInfo.get(0)
                    .buffer(this.chunkDataBuffer)
                    .offset(0L)
                    .range(VK13.VK_WHOLE_SIZE);

            VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.calloc(3, stack);

            descriptorWrites.get(0)
                    .sType$Default()
                    .dstBinding(0)
                    .descriptorCount(1)
                    .descriptorType(VK13.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(blockTextureInfo);

            descriptorWrites.get(1)
                    .sType$Default()
                    .dstBinding(1)
                    .descriptorCount(1)
                    .descriptorType(VK13.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(lightTextureInfo);

            descriptorWrites.get(2)
                    .sType$Default()
                    .dstBinding(2)
                    .descriptorCount(1)
                    .descriptorType(VK13.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pBufferInfo(chunkDataInfo);

            KHRPushDescriptor.vkCmdPushDescriptorSetKHR(
                    commandBuffer,
                    VK13.VK_PIPELINE_BIND_POINT_GRAPHICS,
                    this.pipeline.layout(),
                    0,
                    descriptorWrites
            );
        }
    }
}