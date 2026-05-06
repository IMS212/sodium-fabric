package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.*;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.mixin.core.render.texture.TextureAtlasAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4fc;
import org.jspecify.annotations.NonNull;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;

import java.util.EnumMap;
import java.util.Map;

/**
 * A forward-rendering shader program for chunks.
 */
public class DefaultShaderInterface implements ChunkShaderInterface {
    private final Map<ChunkShaderTextureSlot, GlUniformInt> uniformTextures;

    private final GlUniformBlock uniformChunkData;

    // The fog shader component used by this program in order to set up the appropriate GL state
    private final GlStorageBlock uniformChunkPos;
    private final GlUniformBlock ubo;

    public DefaultShaderInterface(ShaderBindingContext context, ChunkShaderOptions options) {
        this.uniformChunkData = context.bindUniformBlock("ChunkData", 0);
        this.uniformChunkPos = context.bindStorageBlock("PosBuffer", 1);

        this.uniformTextures = new EnumMap<>(ChunkShaderTextureSlot.class);
        this.uniformTextures.put(ChunkShaderTextureSlot.BLOCK, context.bindUniform("u_BlockTex", GlUniformInt::new));
        this.uniformTextures.put(ChunkShaderTextureSlot.LIGHT, context.bindUniform("u_LightTex", GlUniformInt::new));

        this.ubo = context.bindUniformBlock("ChunkUniforms", 2);
    }

    @Override // the shader interface should not modify pipeline state
    public void setupState(TerrainRenderPass pass, FogParameters parameters, GpuSampler terrainSampler, GpuBuffer posBuffer, GpuBuffer ubo) {
        this.bindTexture(ChunkShaderTextureSlot.BLOCK, pass.getAtlas(), terrainSampler);
        this.bindTexture(ChunkShaderTextureSlot.LIGHT, Minecraft.getInstance().gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

        var textureAtlas = (TextureAtlasAccessor) Minecraft.getInstance()
                .getTextureManager()
                .getTexture(TextureAtlas.LOCATION_BLOCKS);

        uniformChunkPos.bindBuffer(posBuffer);

        this.ubo.bindBuffer(ubo);
 }

    @Override // the shader interface should not modify pipeline state
    public void resetState() {
        // This is used by alternate implementations.
    }

    @Deprecated(forRemoval = true) // should be handled properly in GFX instead.
    private void bindTexture(ChunkShaderTextureSlot slot, GpuTextureView textureView, GpuSampler sampler) {
        GlTexture tex = (GlTexture) textureView.texture();
        GlStateManager._activeTexture(GL32C.GL_TEXTURE0 + slot.ordinal());
        GlStateManager._bindTexture(tex.glId());
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, 33084, textureView.baseMipLevel());
        GlStateManager._texParameter(GL32C.GL_TEXTURE_2D, 33085, textureView.baseMipLevel() + textureView.mipLevels() - 1);
        GL33C.glBindSampler(slot.ordinal(), ((GlSampler) sampler).getId());

        var uniform = this.uniformTextures.get(slot);
        uniform.setInt(slot.ordinal());
    }

    @Override
    public void setChunkData(GpuBuffer data, int time) {
        uniformChunkData.bindBuffer(data);
    }

    @Override
    public void setProjectionMatrix(Matrix4fc matrix) {
    }

    @Override
    public void setModelViewMatrix(Matrix4fc matrix) {
    }

    @Override
    public void setRegionOffset(float x, float y, float z) {
    }
}
