package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.util.collections.BitArray;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.device.RenderDevice;
import net.caffeinemc.mods.sodium.mixin.core.render.texture.TextureAtlasAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.util.EnumMap;
import java.util.Map;

/**
 * A forward-rendering shader program for chunks.
 */
public class DefaultShaderInterface implements ChunkShaderInterface {
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f modelViewMatrix = new Matrix4f();
    private final Vector3f regionOffset = new Vector3f();

    public static int PUSH_CONSTANT_SIZE = 152;

    @Override
    public void setupState(TerrainRenderPass pass, FogParameters parameters, GpuSampler terrainSampler) {

    }

    @Override
    public void resetState() {

    }

    /*
    struct PC {
    float3 regionOffset;
    int padding;
    float4x4 modelViewMatrix;
    float4x4 projectionMatrix;
}
     */
    @Override
    public void fillPushConstants(long src) {
        var textureAtlas = (TextureAtlasAccessor) Minecraft.getInstance()
                .getTextureManager()
                .getTexture(TextureAtlas.LOCATION_BLOCKS);

        // There is a limited amount of sub-texel precision when using hardware texture sampling. The mapped texture
        // area must be "shrunk" by at least one sub-texel to avoid bleed between textures in the atlas. And since we
        // offset texture coordinates in the vertex format by one texel, we also need to undo that here.
        double subTexelPrecision = (1 << RenderDevice.INSTANCE.getSubTexelPrecisionBits());
        double subTexelOffset = 1.0f / CompactChunkVertex.TEXTURE_MAX_VALUE;


        this.projectionMatrix.getTransposedToAddress(src + 80);
        this.modelViewMatrix.getTransposedToAddress(src + 16);
        this.regionOffset.getToAddress(src);
        MemoryUtil.memPutFloat(src + 144, (float) (subTexelOffset - (((1.0D / textureAtlas.getWidth()) / subTexelPrecision))));
        MemoryUtil.memPutFloat(src + 148, (float) (subTexelOffset - (((1.0D / textureAtlas.getHeight()) / subTexelPrecision))));
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
    public void setRegionOffset(float x, float y, float z) {
        this.regionOffset.set(x, y, z);
    }

    @Override
    public void setChunkData(VkBuffer buffer, int time) {

    }
}
