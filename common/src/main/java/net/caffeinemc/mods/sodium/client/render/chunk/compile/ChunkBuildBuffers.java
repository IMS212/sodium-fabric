package net.caffeinemc.mods.sodium.client.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.BakedChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree.UpdatedQuadsList;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;

import java.nio.ByteBuffer;

/**
 * A collection of temporary buffers for each worker thread which will be used to build chunk meshes for given render
 * passes. This makes a best-effort attempt to pick a suitable size for each scratch buffer, but will never try to
 * shrink a buffer.
 */
public class ChunkBuildBuffers {
    private static final int UNASSIGNED_SEGMENT_INDEX = ModelQuadFacing.UNASSIGNED.ordinal() << 1;

    private final Reference2ReferenceOpenHashMap<TerrainRenderPass, BakedChunkModelBuilder> builders = new Reference2ReferenceOpenHashMap<>();

    private final int[] vertexBufferStrides;

    public ChunkBuildBuffers(ChunkVertexType vertexType) {
        this.vertexBufferStrides = vertexType.getVertexBufferStrides();

        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            var vertexBuffers = new ChunkMeshBufferBuilder[ModelQuadFacing.COUNT];

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                vertexBuffers[facing] = new ChunkMeshBufferBuilder(vertexType, 128 * 1024);
            }

            this.builders.put(pass, new BakedChunkModelBuilder(vertexBuffers));
        }
    }

    public void init(BuiltSectionInfo.Builder renderData, int sectionIndex) {
        for (var builder : this.builders.values()) {
            builder.begin(renderData, sectionIndex);
        }
    }

    public ChunkModelBuilder get(Material material) {
        return this.builders.get(material.pass);
    }

    public ChunkModelBuilder get(TerrainRenderPass pass) {
        return this.builders.get(pass);
    }

    public static int[] makeVertexSegments() {
        return new int[ModelQuadFacing.COUNT << 1];
    }

    /**
     * Creates immutable baked chunk meshes from all non-empty scratch buffers. This is used after all blocks
     * have been rendered to pass the finished meshes over to the graphics card. This function can be called multiple
     * times to return multiple copies.
     */
    public BuiltSectionMeshParts createMesh(TerrainRenderPass pass, int visibleSlices, boolean forceUnassigned, boolean sliceReordering) {
        var builder = this.builders.get(pass);
        int[] vertexSegments = makeVertexSegments();
        int vertexTotal = 0;

        // get the total vertex count to initialize the buffer
        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            vertexTotal += builder.getVertexBuffer(facing).count();
        }

        if (vertexTotal == 0) {
            return null;
        }

        var mergedBuffers = this.createMergedBuffers(vertexTotal);
        var mergedBufferBuilders = getDirectBuffers(mergedBuffers);

        if (sliceReordering) {
            // sliceReordering implies !forceUnassigned

            // write all currently visible slices first, and then the rest.
            // start with unassigned as it will never become invisible
            var unassignedBuffer = builder.getVertexBuffer(ModelQuadFacing.UNASSIGNED);
            int vertexSegmentCount = 0;
            vertexSegments[vertexSegmentCount++] = unassignedBuffer.count();
            vertexSegments[vertexSegmentCount++] = ModelQuadFacing.UNASSIGNED.ordinal();
            if (!unassignedBuffer.isEmpty()) {
                appendBuffers(mergedBufferBuilders, unassignedBuffer);
            }

            // write all visible and then invisible slices
            for (var step = 0; step < 2; step++) {
                for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                    var facingIndex = facing.ordinal();
                    if (facing == ModelQuadFacing.UNASSIGNED || ((visibleSlices >> facingIndex) & 1) == step) {
                        continue;
                    }

                    var buffer = builder.getVertexBuffer(facing);

                    // generate empty ranges to prevent SectionRenderData storage from making up indexes for null ranges
                    vertexSegments[vertexSegmentCount++] = buffer.count();
                    vertexSegments[vertexSegmentCount++] = facingIndex;

                    if (!buffer.isEmpty()) {
                        appendBuffers(mergedBufferBuilders, buffer);
                    }
                }
            }
        } else {
            // forceUnassigned implies !sliceReordering

            if (forceUnassigned) {
                vertexSegments[UNASSIGNED_SEGMENT_INDEX] = vertexTotal;
                vertexSegments[UNASSIGNED_SEGMENT_INDEX + 1] = ModelQuadFacing.UNASSIGNED.ordinal();
            }

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                var buffer = builder.getVertexBuffer(facing);
                if (!buffer.isEmpty()) {
                    if (!forceUnassigned) {
                        var facingIndex = facing.ordinal();
                        var segmentIndex = facingIndex << 1;
                        vertexSegments[segmentIndex] = buffer.count();
                        vertexSegments[segmentIndex + 1] = facingIndex;
                    }
                    appendBuffers(mergedBufferBuilders, buffer);
                }
            }
        }

        return new BuiltSectionMeshParts(mergedBuffers, vertexSegments);
    }

    public BuiltSectionMeshParts createModifiedTranslucentMesh(UpdatedQuadsList updatedQuads) {
        // mesh modification assumes non-empty mesh with predetermined size

        var builder = this.builders.get(DefaultTerrainRenderPasses.TRANSLUCENT);

        var vertexTotal = TranslucentData.quadCountToVertexCount(updatedQuads.getMeshQuadCount());
        var mergedBuffers = this.createMergedBuffers(vertexTotal);
        var mergedBufferBuilders = getDirectBuffers(mergedBuffers);

        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            var buffer = builder.getVertexBuffer(facing);
            if (!buffer.isEmpty()) {
                appendBuffers(mergedBufferBuilders, buffer);
            }
        }

        updatedQuads.applyBufferUpdates(builder.getVertexBuffer(ModelQuadFacing.UNASSIGNED), mergedBufferBuilders);

        int[] vertexSegments = makeVertexSegments();
        vertexSegments[UNASSIGNED_SEGMENT_INDEX] = vertexTotal;
        vertexSegments[UNASSIGNED_SEGMENT_INDEX + 1] = ModelQuadFacing.UNASSIGNED.ordinal();

        return new BuiltSectionMeshParts(mergedBuffers, vertexSegments);
    }

    private NativeBuffer[] createMergedBuffers(int vertexTotal) {
        var buffers = new NativeBuffer[this.vertexBufferStrides.length];

        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = new NativeBuffer(vertexTotal * this.vertexBufferStrides[i]);
        }

        return buffers;
    }

    private static ByteBuffer[] getDirectBuffers(NativeBuffer[] buffers) {
        var directBuffers = new ByteBuffer[buffers.length];

        for (int i = 0; i < buffers.length; i++) {
            directBuffers[i] = buffers[i].getDirectBuffer();
        }

        return directBuffers;
    }

    private static void appendBuffers(ByteBuffer[] destinations, ChunkMeshBufferBuilder source) {
        for (int i = 0; i < destinations.length; i++) {
            destinations[i].put(source.slice(i));
        }
    }

    public void destroy() {
        for (var builder : this.builders.values()) {
            builder.destroy();
        }
    }
}
