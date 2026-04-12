package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.device.RenderDevice;
import net.caffeinemc.mods.sodium.mixin.core.render.texture.TextureAtlasAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;

public class DefaultShaderInterface implements ChunkShaderInterface {
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f modelViewMatrix = new Matrix4f();
    private int cameraBlockPosX, cameraBlockPosY, cameraBlockPosZ;
    private float cameraFracX, cameraFracY, cameraFracZ;

    public static int PUSH_CONSTANT_SIZE = 160;

    @Override
    public void setupState(TerrainRenderPass pass, FogParameters parameters, GpuSampler terrainSampler) {
    }

    @Override
    public void resetState() {
    }

    @Override
    public void fillPushConstants(long src) {
        var textureAtlas = (TextureAtlasAccessor) Minecraft.getInstance()
                .getTextureManager()
                .getTexture(TextureAtlas.LOCATION_BLOCKS);

        double subTexelPrecision = (1 << RenderDevice.INSTANCE.getSubTexelPrecisionBits());
        double subTexelOffset = 1.0f / CompactChunkVertex.TEXTURE_MAX_VALUE;

        this.modelViewMatrix.getTransposedToAddress(src);
        this.projectionMatrix.getTransposedToAddress(src + 64);
        MemoryUtil.memPutFloat(src + 128, (float) (subTexelOffset - (((1.0D / textureAtlas.sodium$getWidth()) / subTexelPrecision))));
        MemoryUtil.memPutFloat(src + 132, (float) (subTexelOffset - (((1.0D / textureAtlas.sodium$getHeight()) / subTexelPrecision))));
        MemoryUtil.memPutInt(src + 136, cameraBlockPosX);
        MemoryUtil.memPutInt(src + 140, cameraBlockPosY);
        MemoryUtil.memPutInt(src + 144, cameraBlockPosZ);
        MemoryUtil.memPutFloat(src + 148, cameraFracX);
        MemoryUtil.memPutFloat(src + 152, cameraFracY);
        MemoryUtil.memPutFloat(src + 156, cameraFracZ);
    }

    @Override
    public void setProjectionMatrix(Matrix4fc matrix) {
        this.projectionMatrix.set(matrix);
    }

    @Override
    public void setModelViewMatrix(Matrix4fc matrix) {
        this.modelViewMatrix.set(matrix);
    }

    @Override
    public void setCameraTransform(CameraTransform camera) {
        this.cameraBlockPosX = camera.intX;
        this.cameraBlockPosY = camera.intY;
        this.cameraBlockPosZ = camera.intZ;
        this.cameraFracX = camera.fracX;
        this.cameraFracY = camera.fracY;
        this.cameraFracZ = camera.fracZ;
    }
}
