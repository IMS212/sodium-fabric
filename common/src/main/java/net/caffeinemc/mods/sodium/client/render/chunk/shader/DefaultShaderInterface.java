package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat2v;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat3v;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformInt;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformMatrix4f;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.caffeinemc.mods.sodium.mixin.core.render.texture.TextureAtlasAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.vulkan.VK12;

import java.util.EnumMap;
import java.util.Map;

/**
 * A forward-rendering shader program for chunks.
 */
public class DefaultShaderInterface implements ChunkShaderInterface {
    private static final int BUFFER_SIZE = new Std140SizeCalculator()
            .putMat4f() // modelview
            .putMat4f() // projection
            .putVec2() // texcoord shrink
            .putVec2() // distance fog
            .putVec2() // env fog
            .putVec4() // fog color
            .get();

    private final GpuBuffer buffer;

    // The fog shader component used by this program in order to set up the appropriate GL state
    private Matrix4fc projection, modelView;
    private FogParameters parameters;
    private int currentFrame = 0;

    public DefaultShaderInterface(ShaderBindingContext context, ChunkShaderOptions options) {
        this.buffer = SodiumClientMod.getDevice().createBuffer(() -> "Sodium UBO", 130, MathUtil.align(BUFFER_SIZE, 64) * 3);

    }

    @Override // the shader interface should not modify pipeline state
    public void setupState(TerrainRenderPass pass, FogParameters parameters) {
        currentFrame += 1;
        if (currentFrame == 3) currentFrame = 0;

        this.bindTexture(ChunkShaderTextureSlot.BLOCK, pass.getAtlas());
        this.bindTexture(ChunkShaderTextureSlot.LIGHT, Minecraft.getInstance().gameRenderer.lightTexture().getTextureView());


        this.parameters = parameters;
    }

    @Override // the shader interface should not modify pipeline state
    public void resetState() {
        // This is used by alternate implementations.
    }

    @Deprecated(forRemoval = true) // should be handled properly in GFX instead.
    private void bindTexture(ChunkShaderTextureSlot slot, GpuTextureView textureView) {
    }

    @Override
    public void setProjectionMatrix(Matrix4fc matrix) {
        this.projection = matrix;
    }

    @Override
    public void setModelViewMatrix(Matrix4fc matrix) {
        this.modelView = matrix;
    }

    @Override
    public void setRegionOffset(float x, float y, float z) {
       // this.uniformRegionOffset.set(x, y, z);
    }

    public GpuBufferSlice  getBuffer() {
        return buffer.slice(MathUtil.align(BUFFER_SIZE, 64) * currentFrame, BUFFER_SIZE);
    }

    @Override
    public void destroy() {
        buffer.close();
    }

    @Override
    public void uploadData() {
        var textureAtlas = (TextureAtlasAccessor) Minecraft.getInstance()
                .getTextureManager()
                .getTexture(TextureAtlas.LOCATION_BLOCKS);


        // There is a limited amount of sub-texel precision when using hardware texture sampling. The mapped texture
        // area must be "shrunk" by at least one sub-texel to avoid bleed between textures in the atlas. And since we
        // offset texture coordinates in the vertex format by one texel, we also need to undo that here.
        double subTexelPrecision = (1 << GLRenderDevice.INSTANCE.getSubTexelPrecisionBits());
        double subTexelOffset = 1.0f / CompactChunkVertex.TEXTURE_MAX_VALUE;
        try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.buffer, false, true)) {
            mappedView.data().position(MathUtil.align(BUFFER_SIZE, 64) * currentFrame);
            Std140Builder.intoBuffer(mappedView.data())
                    .putMat4f(modelView)
                    .putMat4f(projection)
                    .putVec2((float) (subTexelOffset - (((1.0D / textureAtlas.getWidth()) / subTexelPrecision))), (float) (subTexelOffset - (((1.0D / textureAtlas.getHeight()) / subTexelPrecision))))
                    .putVec2(parameters.renderStart(), parameters.renderEnd())
                    .putVec2(parameters.environmentalStart(), parameters.environmentalEnd())
                    .putVec4(parameters.red(), parameters.green(), parameters.blue(), parameters.alpha())
            ;
        }
    }
}
