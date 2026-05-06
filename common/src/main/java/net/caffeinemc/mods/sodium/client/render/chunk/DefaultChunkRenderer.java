package net.caffeinemc.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.*;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.BitwiseMath;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.util.UInt32;
import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;
import net.caffeinemc.mods.sodium.mixin.core.GlBufferAccessor;
import net.caffeinemc.mods.sodium.mixin.core.render.texture.TextureAtlasAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

import java.util.Iterator;

public class DefaultChunkRenderer extends ShaderChunkRenderer {
    private final SharedQuadIndexBuffer sharedIndexBuffer;

    public DefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);

        this.sharedIndexBuffer = new SharedQuadIndexBuffer(device.createCommandList(), SharedQuadIndexBuffer.SodiumIndexType.INTEGER);
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
                       TerrainRenderPass terrainPass,
                       CameraTransform camera,
                       FogParameters parameters,
                       boolean indexedRenderingEnabled,
                       GpuSampler terrainSampler, GpuBuffer posBuffer, GpuBuffer ubo) {
        super.begin(terrainPass, parameters, terrainSampler, posBuffer, ubo);

        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(RenderPassDescriptor.create(() -> terrainPass.getPipeline().getLocation().toString())
                .withRenderArea(new RenderPass.RenderArea(0, 0, Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight()))
                .withColorAttachment(terrainPass.getTarget().getColorTextureView())
                .withDepthAttachment(terrainPass.getTarget().getDepthTextureView()))) {
            final boolean useBlockFaceCulling = SodiumClientMod.options().performance.useBlockFaceCulling;
            final boolean useIndexedTessellation = terrainPass.isTranslucent() && indexedRenderingEnabled;

            renderPass.setPipeline(activeProgram);
            renderPass.bindTexture("u_BlockTex", terrainPass.getAtlas(), terrainSampler);
            renderPass.bindTexture("u_LightTex", Minecraft.getInstance().gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

            renderPass.setUniform("ChunkUniforms", ubo);
            renderPass.setUniform("PosBuffer", posBuffer);

            Iterator<ChunkRenderList> iterator = renderLists.iterator(terrainPass.isTranslucent());

            while (iterator.hasNext()) {
                ChunkRenderList renderList = iterator.next();

                var region = renderList.getRegion();
                var storage = region.getStorage(terrainPass);

                if (storage == null) {
                    continue;
                }

                var batch = region.getCachedBatch(terrainPass);
                if (!batch.isFilled) {
                    fillCommandBuffer(batch, region, storage, renderList, camera, terrainPass, useBlockFaceCulling, useIndexedTessellation);
                }

                if (batch.isEmpty()) {
                    continue;
                }

                // When the shared index buffer is being used, we must ensure the storage has been allocated *before*
                // the tessellation is prepared.
                if (!useIndexedTessellation) {
                    this.sharedIndexBuffer.ensureCapacity(commandList, batch.getIndexBufferSize());
                }

                renderPass.setUniform("ChunkData", region.getResources().prepareChunkData(commandList));

                renderPass.setVertexBuffer(0, region.getResources().getGeometryBuffer().slice());
                renderPass.setIndexBuffer(useIndexedTessellation ? region.getResources().getIndexBuffer() : sharedIndexBuffer.getBufferObject(), IndexType.INT);

                for (int index = 0; index < batch.size; index++) {
                    long ptrOffset = (long) index * Pointer.POINTER_SIZE;
                    long intOffset = (long) index * Integer.BYTES;

                    long elementPointer = MemoryUtil.memGetAddress(batch.pElementPointer + ptrOffset);
                    int elementCount = MemoryIntrinsics.getInt(batch.pElementCount + intOffset);
                    int baseVertex = MemoryIntrinsics.getInt(batch.pBaseVertex + intOffset);

                    renderPass.drawIndexed(baseVertex, Math.toIntExact(elementPointer / 4), elementCount, 1);
                }
            }
        }

        super.end(terrainPass);
    }

    private static void fillCommandBuffer(MultiDrawBatch batch,
                                          RenderRegion renderRegion,
                                          SectionRenderDataStorage renderDataStorage,
                                          ChunkRenderList renderList,
                                          CameraTransform camera,
                                          TerrainRenderPass pass,
                                          boolean useBlockFaceCulling,
                                          boolean useIndexedTessellation) {
        batch.isFilled = true;

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

            // it's necessary to sometimes not the locally-indexed command generator even for indexed tessellations since
            // sometimes the index buffer is shared, but not globally shared. This means that translucent sections that
            // are sharing an index buffer amongst them need to use the shared index command generator since it sets the
            // same element offset for each draw command and doesn't increment it. Recall that in each draw command the indexing
            // of the elements needs to start at 0 and thus starting somewhere further into the shared index buffer is invalid.
            // there's also the optimization that draw commands can be combined when using a shared index buffer, be it
            // globally shared or just shared within the region, which isn't possible with the locally-indexed command generator.
            if (useIndexedTessellation && SectionRenderDataUnsafe.isLocalIndex(pMeshData)) {
                addLocalIndexedDrawCommands(batch, pMeshData, slices);
            } else {
                addSharedIndexedDrawCommands(batch, pMeshData, slices);
            }
        }
    }

    /**
     * Generates the draw commands for a chunk's meshes, where each mesh has a separate index buffer. This is used
     * when rendering translucent geometry, as each geometry set needs a sorted index buffer.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    private static void addLocalIndexedDrawCommands(MultiDrawBatch batch, long pMeshData, int mask) {
        final var pElementPointer = batch.pElementPointer;
        final var pBaseVertex = batch.pBaseVertex;
        final var pElementCount = batch.pElementCount;

        int size = batch.size;

        long elementOffset = SectionRenderDataUnsafe.getBaseElement(pMeshData);
        long baseVertex = SectionRenderDataUnsafe.getBaseVertex(pMeshData);

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            final long vertexCount = SectionRenderDataUnsafe.getVertexCount(pMeshData, facing);
            final long elementCount = (vertexCount >> 2) * 6;

            MemoryIntrinsics.putInt(pElementCount + (size << 2), UInt32.uncheckedDowncast(elementCount));
            MemoryIntrinsics.putInt(pBaseVertex + (size << 2), UInt32.uncheckedDowncast(baseVertex));

            // * 4 to convert to bytes (the index buffer contains integers)
            MemoryIntrinsics.putAddress(pElementPointer + (size << Pointer.POINTER_SHIFT), elementOffset << 2);

            baseVertex += vertexCount;
            elementOffset += elementCount;

            size += (mask >> facing) & 1;
        }

        batch.size = size;
    }

    /**
     * Generates the draw commands for a chunk's meshes using the shared index buffer.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    private static void addSharedIndexedDrawCommands(MultiDrawBatch batch, long pMeshData, int mask) {
        final var pElementPointer = batch.pElementPointer;
        final var pBaseVertex = batch.pBaseVertex;
        final var pElementCount = batch.pElementCount;

        // this is either zero (global shared index buffer) or the offset to the location of the shared element buffer (region shared index buffer)
        final var elementOffsetBytes = SectionRenderDataUnsafe.getBaseElement(pMeshData) << 2;
        final var facingList = SectionRenderDataUnsafe.getFacingList(pMeshData);

        int size = batch.size;
        long groupVertexCount = 0;
        long baseVertex = SectionRenderDataUnsafe.getBaseVertex(pMeshData);
        int lastMaskBit = 0;

        for (int i = 0; i <= ModelQuadFacing.COUNT; i++) {
            var maskBit = 0;
            long vertexCount = 0;
            if (i < ModelQuadFacing.COUNT) {
                vertexCount = SectionRenderDataUnsafe.getVertexCount(pMeshData, i);

                // if there's no vertexes, the mask bit is just 0
                if (vertexCount != 0) {
                    var facing = (facingList >>> (i * 8)) & 0xFF;
                    maskBit = (mask >>> facing) & 1;
                }
            }

            if (maskBit == 0) {
                if (lastMaskBit == 1) {
                    // delay writing out draw command if there's a zero-size group
                    if (i < ModelQuadFacing.COUNT && vertexCount == 0) {
                        continue;
                    }

                    MemoryIntrinsics.putInt(pElementCount + (size << 2), UInt32.uncheckedDowncast((groupVertexCount >> 2) * 6));
                    MemoryIntrinsics.putInt(pBaseVertex + (size << 2), UInt32.uncheckedDowncast(baseVertex));
                    MemoryIntrinsics.putAddress(pElementPointer + (size << Pointer.POINTER_SHIFT), elementOffsetBytes);
                    size++;
                    baseVertex += groupVertexCount;
                    groupVertexCount = 0;
                }

                baseVertex += vertexCount;
            } else {
                groupVertexCount += vertexCount;
            }

            lastMaskBit = maskBit;
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

    public static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
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

    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);

        this.sharedIndexBuffer.delete(commandList);
    }

    @Override
    public int getUniformDataSize() {
        return new Std140SizeCalculator()
                .putMat4f() // model view
                .putMat4f() // projection
                .putVec2() // texel size
                .putVec2() // tex coord shrink
                .putInt() // current time
                .putInt() // use RGSS
                .putFloat() // u_FadePeriodInv
                .putIVec4() // cameraPosInt
                .putVec4() // cameraPosFract
                .putVec4() // fog color
                .putVec2() // env start/end
                .putVec2() // distance start/end
                .get();
    }

    @Override
    public void fillUniformData(GpuBuffer gpuBuffer, ChunkRenderMatrices matrices, FogParameters fogParameters, double x, double y, double z) {
        try (var map = gpuBuffer.map(false, true)) {
            var textureAtlas = (TextureAtlasAccessor) Minecraft.getInstance()
                    .getTextureManager()
                    .getTexture(TextureAtlas.LOCATION_BLOCKS);

            // There is a limited amount of sub-texel precision when using hardware texture sampling. The mapped texture
            // area must be "shrunk" by at least one sub-texel to avoid bleed between textures in the atlas. And since we
            // offset texture coordinates in the vertex format by one texel, we also need to undo that here.
            double subTexelPrecision = (1 << GLRenderDevice.INSTANCE.getSubTexelPrecisionBits());
            double subTexelOffset = 1.0f / CompactChunkVertex.TEXTURE_MAX_VALUE;

            Std140Builder.intoBuffer(map.data())
                    .putMat4f(matrices.modelView())
                    .putMat4f(matrices.projection())
                    .putVec2(1.0f / textureAtlas.sodium$getWidth(),
                            1.0f / textureAtlas.sodium$getHeight())
                    .putVec2((float) (subTexelOffset - (((1.0D / textureAtlas.sodium$getWidth()) / subTexelPrecision))),
                            (float) (subTexelOffset - (((1.0D / textureAtlas.sodium$getHeight()) / subTexelPrecision))))
                    .putInt(0) // TODO: fix chunk fade
                    .putInt(Minecraft.getInstance().options.textureFiltering().get() == TextureFilteringMethod.RGSS ? 1 : 0)
                    .putFloat(1.0f) // TODO: fix chunk fade
                    .putIVec4((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), 0)
                    .putVec4((float) (x - Math.floor(x)), (float) (y - Math.floor(y)), (float) (z - Math.floor(z)), 0.0f)
                    .putVec4(fogParameters.red(), fogParameters.green(), fogParameters.blue(), fogParameters.alpha())
                    .putVec2(fogParameters.environmentalStart(), fogParameters.environmentalEnd())
                    .putVec2(fogParameters.renderStart(), fogParameters.renderEnd());
        }
    }
}
