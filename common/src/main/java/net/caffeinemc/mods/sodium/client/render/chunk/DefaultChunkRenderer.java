package net.caffeinemc.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import graphics.cinnabar.core.b3d.renderpass.CinnabarRenderPass;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.*;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlIndexType;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlTessellation;
import net.caffeinemc.mods.sodium.client.gl.tessellation.TessellationBinding;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.BitwiseMath;
import net.caffeinemc.mods.sodium.client.util.UInt32;
import net.caffeinemc.mods.sodium.mixin.core.render.texture.TextureAtlasAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;

public class DefaultChunkRenderer extends ShaderChunkRenderer {
    private final MultiDrawBatch batch;

    private final SharedQuadIndexBuffer sharedIndexBuffer;

    public DefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);

        this.batch = new MultiDrawBatch((ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1);
        this.sharedIndexBuffer = new SharedQuadIndexBuffer(device.createCommandList(), SharedQuadIndexBuffer.IndexType.INTEGER);
    }

    /**
     * Renders the terrain for a particular render pass. Each region is rendered
     * with one draw call. The command buffer for each draw command is filled by
     * iterating the sections and adding the draw commands for each section.
     */
    @Override
    public void render(ChunkRenderMatrices matrices,
                       CommandList commandList,
                       ChunkRenderListIterable renderLists,
                       TerrainRenderPass renderPass,
                       CameraTransform camera) {
        ChunkShaderOptions options = new ChunkShaderOptions(ChunkFogMode.SMOOTH, renderPass, this.vertexType);
        super.begin(renderPass);

        try (RenderPass pass = SodiumClientMod.getCommandEncoder().createRenderPass(Minecraft.getInstance().getMainRenderTarget().getColorTexture(), OptionalInt.empty(), Minecraft.getInstance().getMainRenderTarget().getDepthTexture(), OptionalDouble.empty())) {
            ((CinnabarRenderPass) pass).setPipelineCinna(this.compileProgram(options));

            pass.setUniform("u_ModelViewMatrix", (Matrix4f) matrices.modelView());
        pass.setUniform("u_ProjectionMatrix", (Matrix4f) matrices.projection());
        pass.setUniform("u_FogStart", RenderSystem.getShaderFog().start());
        pass.setUniform("u_FogEnd", RenderSystem.getShaderFog().end());
        pass.setUniform("u_FogShape", RenderSystem.getShaderFog().shape().getIndex());
        pass.setUniform("u_FogColor", RenderSystem.getShaderFog().red(), RenderSystem.getShaderFog().green(), RenderSystem.getShaderFog().blue(), RenderSystem.getShaderFog().alpha());

            var textureAtlas = (TextureAtlasAccessor) Minecraft.getInstance()
                    .getTextureManager()
                    .getTexture(TextureAtlas.LOCATION_BLOCKS);

            // There is a limited amount of sub-texel precision when using hardware texture sampling. The mapped texture
            // area must be "shrunk" by at least one sub-texel to avoid bleed between textures in the atlas. And since we
            // offset texture coordinates in the vertex format by one texel, we also need to undo that here.
            double subTexelPrecision = (1 << GLRenderDevice.INSTANCE.getSubTexelPrecisionBits());
            double subTexelOffset = 1.0f / CompactChunkVertex.TEXTURE_MAX_VALUE;

            pass.setUniform("u_TexCoordShrink",
                    (float) (subTexelOffset - (((1.0D / textureAtlas.getWidth()) / subTexelPrecision))),
                    (float) (subTexelOffset - (((1.0D / textureAtlas.getHeight()) / subTexelPrecision)))
            );


            pass.bindSampler("u_BlockTex", RenderSystem.getShaderTexture(0));
        pass.bindSampler("u_LightTex", RenderSystem.getShaderTexture(2));

        final boolean useBlockFaceCulling = SodiumClientMod.options().performance.useBlockFaceCulling;
        final boolean useIndexedTessellation = isTranslucentRenderPass(renderPass);

        ChunkShaderInterface shader = null;
        //shader.setProjectionMatrix(matrices.projection());
        //shader.setModelViewMatrix(matrices.modelView());

        Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isTranslucent());

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();

            var region = renderList.getRegion();
            var storage = region.getStorage(renderPass);

            if (storage == null) {
                continue;
            }

            fillCommandBuffer(this.batch, region, storage, renderList, camera, renderPass, useBlockFaceCulling);

            if (this.batch.isEmpty()) {
                continue;
            }

            // When the shared index buffer is being used, we must ensure the storage has been allocated *before*
            // the tessellation is prepared.
            if (!useIndexedTessellation) {
                this.sharedIndexBuffer.ensureCapacity(commandList, this.batch.getIndexBufferSize());
            }

            GlTessellation tessellation;

            if (useIndexedTessellation) {
                tessellation = this.prepareIndexedTessellation(commandList, region);
            } else {
                tessellation = this.prepareTessellation(commandList, region);
            }

            /*
            TessellationBinding.forVertexBuffer(resources.getGeometryBuffer(), this.vertexFormat.getShaderBindings()),
                TessellationBinding.forElementBuffer(useSharedIndexBuffer
                        ? this.sharedIndexBuffer.getBufferObject()
                        : resources.getIndexBuffer())
             */
            vkCmdBindVertexBuffers(SodiumClientMod.getCommandEncoder().mainDrawCommandBuffer, 0, new long[]{region.getResources().getGeometryBuffer().handle}, new long[]{0});
            // TODO: Always U32, right?
            vkCmdBindIndexBuffer(SodiumClientMod.getCommandEncoder().mainDrawCommandBuffer, (renderPass.isTranslucent() ? region.getResources().getIndexBuffer() : this.sharedIndexBuffer.getBufferObject()).handle, 0, VK_INDEX_TYPE_UINT32);

            setModelMatrixUniforms(pass, region, camera);

            ((CinnabarRenderPass) pass).updateUniforms();

            executeDrawBatch(commandList, tessellation, this.batch);
        }

        }
        super.end(renderPass);

    }

    private static boolean isTranslucentRenderPass(TerrainRenderPass renderPass) {
        return renderPass.isTranslucent() && SodiumClientMod.options().debug.getSortBehavior() != SortBehavior.OFF;
    }

    private static void fillCommandBuffer(MultiDrawBatch batch,
                                          RenderRegion renderRegion,
                                          SectionRenderDataStorage renderDataStorage,
                                          ChunkRenderList renderList,
                                          CameraTransform camera,
                                          TerrainRenderPass pass,
                                          boolean useBlockFaceCulling) {
        batch.clear();

        var iterator = renderList.sectionsWithGeometryIterator(pass.isTranslucent());

        if (iterator == null) {
            return;
        }

        // The origin of the chunk in world space
        int originX = renderRegion.getChunkX();
        int originY = renderRegion.getChunkY();
        int originZ = renderRegion.getChunkZ();

        while (iterator.hasNext()) {
            int sectionIndex = iterator.nextByteAsInt();

            var pMeshData = renderDataStorage.getDataPointer(sectionIndex);

            int chunkX = originX + LocalSectionIndex.unpackX(sectionIndex);
            int chunkY = originY + LocalSectionIndex.unpackY(sectionIndex);
            int chunkZ = originZ + LocalSectionIndex.unpackZ(sectionIndex);

            // The bit field of "visible" geometry sets which should be rendered
            int slices;

            if (useBlockFaceCulling) {
                slices = getVisibleFaces(camera.intX, camera.intY, camera.intZ, chunkX, chunkY, chunkZ);
            } else {
                slices = ModelQuadFacing.ALL;
            }

            // Mask off any geometry sets which are empty (contain no geometry)
            slices &= SectionRenderDataUnsafe.getSliceMask(pMeshData);

            // If there are no geometry sets to render, don't try to build a draw command buffer for this section
            if (slices == 0) {
                continue;
            }

            if (pass.isTranslucent()) {
                addIndexedDrawCommands(batch, pMeshData, slices);
            } else {
                addNonIndexedDrawCommands(batch, pMeshData, slices);
            }
        }
    }

    /**
     * Generates the draw commands for a chunk's meshes using the shared index buffer.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    private static void addNonIndexedDrawCommands(MultiDrawBatch batch, long pMeshData, int mask) {
        int size = batch.size;

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            // Uint32 -> Int32 cast is always safe and should be optimized away
            // TODO: I DO NOT MATH
            batch.info.position((size)).indexCount((int) SectionRenderDataUnsafe.getElementCount(pMeshData, facing))
                    .vertexOffset((int) SectionRenderDataUnsafe.getVertexOffset(pMeshData, facing)).firstIndex(0);
            batch.indexCount[size] = (int) SectionRenderDataUnsafe.getElementCount(pMeshData, facing);
            //MemoryUtil.memPutInt(pBaseVertex + (size << 2), (int) SectionRenderDataUnsafe.getVertexOffset(pMeshData, facing));
            //MemoryUtil.memPutInt(pElementCount + (size << 2), (int) SectionRenderDataUnsafe.getElementCount(pMeshData, facing));
           // MemoryUtil.memPutAddress(pElementPointer + (size << Pointer.POINTER_SHIFT), 0 /* using a shared index buffer */);

            size++;
        }

        batch.size = size;
    }

    /**
     * Generates the draw commands for a chunk's meshes, where each mesh has a separate index buffer. This is used
     * when rendering translucent geometry, as each geometry set needs a sorted index buffer.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    private static void addIndexedDrawCommands(MultiDrawBatch batch, long pMeshData, int mask) {


        int size = batch.size;

        long elementOffset = SectionRenderDataUnsafe.getBaseElement(pMeshData);

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            final long vertexOffset = SectionRenderDataUnsafe.getVertexOffset(pMeshData, facing);
            final long elementCount = SectionRenderDataUnsafe.getElementCount(pMeshData, facing);

            // TODO: I DO NOT MATH
            batch.info.position((size)).indexCount(UInt32.uncheckedDowncast(elementCount))
                    .vertexOffset(UInt32.uncheckedDowncast(vertexOffset)).firstIndex(Math.toIntExact(elementOffset));

            batch.indexCount[size] = UInt32.uncheckedDowncast(elementCount);

            // adding the number of elements works because the index data has one index per element (which are the indices)
            elementOffset += elementCount;
            size++;
        }

        batch.size = size;
    }

    private static final int MODEL_UNASSIGNED = ModelQuadFacing.UNASSIGNED.ordinal();
    private static final int MODEL_POS_X      = ModelQuadFacing.POS_X.ordinal();
    private static final int MODEL_POS_Y      = ModelQuadFacing.POS_Y.ordinal();
    private static final int MODEL_POS_Z      = ModelQuadFacing.POS_Z.ordinal();

    private static final int MODEL_NEG_X      = ModelQuadFacing.NEG_X.ordinal();
    private static final int MODEL_NEG_Y      = ModelQuadFacing.NEG_Y.ordinal();
    private static final int MODEL_NEG_Z      = ModelQuadFacing.NEG_Z.ordinal();

    private static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
        // This is carefully written so that we can keep everything branch-less.
        //
        // Normally, this would be a ridiculous way to handle the problem. But the Hotspot VM's
        // heuristic for generating SETcc/CMOV instructions is broken, and it will always create a
        // branch even when a trivial ternary is encountered.
        //
        // For example, the following will never be transformed into a SETcc:
        //   (a > b) ? 1 : 0
        //
        // So we have to instead rely on sign-bit extension and masking (which generates a ton
        // of unnecessary instructions) to get this to be branch-less.
        //
        // To do this, we can transform the previous expression into the following.
        //   (b - a) >> 31
        //
        // This works because if (a > b) then (b - a) will always create a negative number. We then shift the sign bit
        // into the least significant bit's position (which also discards any bits following the sign bit) to get the
        // output we are looking for.
        //
        // If you look at the output which LLVM produces for a series of ternaries, you will instantly become distraught,
        // because it manages to a) correctly evaluate the cost of instructions, and b) go so far
        // as to actually produce vector code.  (https://godbolt.org/z/GaaEx39T9)

        int boundsMinX = (chunkX << 4), boundsMaxX = boundsMinX + 16;
        int boundsMinY = (chunkY << 4), boundsMaxY = boundsMinY + 16;
        int boundsMinZ = (chunkZ << 4), boundsMaxZ = boundsMinZ + 16;

        // the "unassigned" plane is always front-facing, since we can't check it
        int planes = (1 << MODEL_UNASSIGNED);

        planes |= BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_POS_X;
        planes |= BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_POS_Y;
        planes |= BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_POS_Z;

        planes |=    BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_NEG_X;
        planes |=    BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_NEG_Y;
        planes |=    BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_NEG_Z;

        return planes;
    }

    private static void setModelMatrixUniforms(RenderPass shader, RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);

        shader.setUniform("u_RegionOffset", x, y, z);
    }

    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

    private GlTessellation prepareTessellation(CommandList commandList, RenderRegion region) {
        var resources = region.getResources();

        GlTessellation tessellation = resources.getTessellation();
        if (tessellation == null) {
            tessellation = this.createRegionTessellation(commandList, resources, true);
            resources.updateTessellation(commandList, tessellation);
        }

        return tessellation;
    }

    private GlTessellation prepareIndexedTessellation(CommandList commandList, RenderRegion region) {
        var resources = region.getResources();

        GlTessellation tessellation = resources.getIndexedTessellation();
        if (tessellation == null) {
            tessellation = this.createRegionTessellation(commandList, resources, false);
            resources.updateIndexedTessellation(commandList, tessellation);
        }

        return tessellation;
    }

    private GlTessellation createRegionTessellation(CommandList commandList, RenderRegion.DeviceResources resources, boolean useSharedIndexBuffer) {
        return commandList.createTessellation(GlPrimitiveType.TRIANGLES, new TessellationBinding[] {
                TessellationBinding.forVertexBuffer(resources.getGeometryBuffer(), this.vertexFormat.getShaderBindings()),
                TessellationBinding.forElementBuffer(useSharedIndexBuffer
                        ? this.sharedIndexBuffer.getBufferObject()
                        : resources.getIndexBuffer())
        });
    }

    private static void executeDrawBatch(CommandList commandList, GlTessellation tessellation, MultiDrawBatch batch) {
        try (DrawCommandList drawCommandList = commandList.beginTessellating(tessellation)) {
            drawCommandList.multiDrawElementsBaseVertex(batch, GlIndexType.UNSIGNED_INT);
        }
    }

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);

        this.sharedIndexBuffer.delete(commandList);
        this.batch.delete();
    }
}
