package net.caffeinemc.mods.sodium.client.render.chunk.terrain;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.dsa.DirectStateAccess;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;

public class TerrainRenderPass {
    @Deprecated(forRemoval = true)
    private final RenderType renderType;

    private final boolean isTranslucent;
    private final boolean fragmentDiscard;

    public TerrainRenderPass(RenderType renderType, boolean isTranslucent, boolean allowFragmentDiscard) {
        this.renderType = renderType;

        this.isTranslucent = isTranslucent;
        this.fragmentDiscard = allowFragmentDiscard;
    }

    public boolean isTranslucent() {
        return this.isTranslucent;
    }

    private void applyPipelineState(RenderPipeline renderPipeline) {
        if (renderPipeline.getDepthTestFunction() != DepthTestFunction.NO_DEPTH_TEST) {
            GlStateManager._enableDepthTest();
            GlStateManager._depthFunc(GlConst.toGl(renderPipeline.getDepthTestFunction()));
        } else {
            GlStateManager._disableDepthTest();
        }

        if (renderPipeline.isCull()) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }

        if (renderPipeline.getBlendFunction().isPresent()) {
            GlStateManager._enableBlend();
            BlendFunction blendFunction = (BlendFunction)renderPipeline.getBlendFunction().get();
            GlStateManager._blendFuncSeparate(
                    GlConst.toGl(blendFunction.sourceColor()),
                    GlConst.toGl(blendFunction.destColor()),
                    GlConst.toGl(blendFunction.sourceAlpha()),
                    GlConst.toGl(blendFunction.destAlpha())
            );
        } else {
            GlStateManager._disableBlend();
        }

        GlStateManager._polygonMode(1032, GlConst.toGl(renderPipeline.getPolygonMode()));
        GlStateManager._depthMask(renderPipeline.isWriteDepth());
        GlStateManager._colorMask(renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteAlpha());
        if (renderPipeline.getDepthBiasConstant() == 0.0F && renderPipeline.getDepthBiasScaleFactor() == 0.0F) {
            GlStateManager._disablePolygonOffset();
        } else {
            GlStateManager._polygonOffset(renderPipeline.getDepthBiasScaleFactor(), renderPipeline.getDepthBiasConstant());
            GlStateManager._enablePolygonOffset();
        }

        switch (renderPipeline.getColorLogic()) {
            case NONE:
                GlStateManager._disableColorLogicOp();
                break;
            case OR_REVERSE:
                GlStateManager._enableColorLogicOp();
                GlStateManager._logicOp(5387);
        }

    }

    @Deprecated
    public void startDrawing() {
        this.renderType.setupRenderState();
        applyPipelineState(this.renderType.getRenderPipeline());
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, ((GlTexture) renderType.getRenderTarget().getColorTexture()).getFbo(((GlDevice) RenderSystem.getDevice()).directStateAccess(), renderType.getRenderTarget().getDepthTexture()));
    }

    @Deprecated
    public void endDrawing() {
        this.renderType.clearRenderState();
    }

    public boolean supportsFragmentDiscard() {
        return this.fragmentDiscard;
    }
}
