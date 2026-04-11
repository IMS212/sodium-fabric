package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import org.joml.Matrix4fc;

public interface ChunkShaderInterface {
    @Deprecated
    void setupState(TerrainRenderPass pass, FogParameters parameters, GpuSampler terrainSampler);

    @Deprecated
    void resetState();

    void fillPushConstants(long src);

    void setProjectionMatrix(Matrix4fc matrix);

    void setModelViewMatrix(Matrix4fc matrix);

    void setCameraTransform(CameraTransform camera);
}
