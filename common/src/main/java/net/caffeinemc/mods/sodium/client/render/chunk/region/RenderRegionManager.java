package net.caffeinemc.mods.sodium.client.render.chunk.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferArena;
import net.caffeinemc.mods.sodium.client.gl.arena.PendingUpload;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.FallbackStagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.StagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.PersistentBuffer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.SharedIndexSorter;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL46C;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class RenderRegionManager {
    private final Long2ReferenceOpenHashMap<RenderRegion> regions = new Long2ReferenceOpenHashMap<>();

    private final StagingBuffer stagingBuffer;
    private final GlBufferArena voxelArena;
    private final PersistentBuffer voxelIndices;
    public static int sectionHeight;
    public static int renderDistanceInDiameter;
    public  static int sectionsToReachZero;

    public RenderRegionManager(int renderDistance, ClientLevel level, CommandList commandList) {
        this.stagingBuffer = createStagingBuffer(commandList);
        this.sectionsToReachZero = level.getMinSectionY() >= 0 ? 0 : Math.abs(level.getMinSectionY());
        this.sectionHeight = Math.abs(level.getMaxSectionY() - level.getMinSectionY()) + 1;
        this.renderDistanceInDiameter = ((renderDistance + 1)) * 2;
        int renderDistanceAmount = renderDistanceInDiameter * renderDistanceInDiameter * sectionHeight;
        this.voxelIndices = new PersistentBuffer(renderDistanceAmount * Integer.BYTES);
        this.voxelArena = new GlBufferArena(commandList, 2048, 16 * 16 * 16 * 4, stagingBuffer);
       // NativeBuffer buffer = new NativeBuffer(16 * 16 * 16 * 4);
       // buffer.clearAll();
       // this.voxelArena.upload(commandList, Stream.of(new PendingUpload(buffer)));
       // this.stagingBuffer.flip();
    }

    public int to1D( int x, int y, int z ) {
        return (z * renderDistanceInDiameter * sectionHeight) + (y * renderDistanceInDiameter) + x;
    }

    public void update() {

        GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 9, this.voxelArena.getBufferObject().handle());
        GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 10, this.voxelIndices.getId());
        this.stagingBuffer.flip();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            Iterator<RenderRegion> it = this.regions.values()
                    .iterator();

            while (it.hasNext()) {
                RenderRegion region = it.next();
                region.update(commandList);

                if (region.isEmpty()) {
                    region.delete(commandList);

                    it.remove();
                }
            }
        }
    }

    public void uploadResults(CommandList commandList, Collection<BuilderTaskOutput> results) {
        for (var entry : this.createMeshUploadQueues(results)) {
            this.uploadResults(commandList, entry.getKey(), entry.getValue());
        }
    }

    private void uploadResults(CommandList commandList, RenderRegion region, Collection<BuilderTaskOutput> results) {
        var uploads = new ArrayList<PendingSectionMeshUpload>();
        var indexUploads = new ArrayList<PendingSectionIndexBufferUpload>();

        for (BuilderTaskOutput result : results) {
            int renderSectionIndex = result.render.getSectionIndex();

            if (result.render.isDisposed()) {
                throw new IllegalStateException("Render section is disposed");
            }

            if (result instanceof ChunkBuildOutput chunkBuildOutput) {
                for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
                    var storage = region.getStorage(pass);

                    if (storage != null) {
                        storage.removeVertexData(renderSectionIndex);
                        region.clearCachedBatchFor(pass);
                    }

                    BuiltSectionMeshParts mesh = chunkBuildOutput.getMesh(pass);

                    if (mesh != null) {
                        uploads.add(new PendingSectionMeshUpload(result.render, chunkBuildOutput.voxelData == null ? null : new PendingUpload(chunkBuildOutput.voxelData), mesh, pass,
                                new PendingUpload(mesh.getVertexData())));
                    }
                }
            }

            if (result instanceof ChunkSortOutput indexDataOutput && !indexDataOutput.isReusingUploadedIndexData()) {
                var sorter = indexDataOutput.getSorter();
                if (sorter instanceof SharedIndexSorter sharedIndexSorter) {
                    var storage = region.createStorage(DefaultTerrainRenderPasses.TRANSLUCENT);
                    storage.removeIndexData(renderSectionIndex);

                    // clear batch cache if it's newly using the shared index buffer and was not previously.
                    // updates to the shared index buffer which cause the batch cache to be invalidated are handled with needsSharedIndexUpdate
                    if (storage.setSharedIndexUsage(renderSectionIndex, sharedIndexSorter.quadCount())) {
                        region.clearCachedBatchFor(DefaultTerrainRenderPasses.TRANSLUCENT);
                    }
                } else {
                    var storage = region.getStorage(DefaultTerrainRenderPasses.TRANSLUCENT);
                    if (storage != null) {
                        storage.removeIndexData(renderSectionIndex);
                        storage.setSharedIndexUsage(renderSectionIndex, 0);

                        // always clear batch cache on uploads of new index data
                        region.clearCachedBatchFor(DefaultTerrainRenderPasses.TRANSLUCENT);
                    }

                    if (sorter == null) {
                        continue;
                    }
                    // when a non-present TranslucentData is used like NoData, the indexBuffer is null
                    var buffer = sorter.getIndexBuffer();
                    if (buffer == null) {
                        continue;
                    }

                    indexUploads.add(new PendingSectionIndexBufferUpload(result.render, new PendingUpload(buffer)));
                }
            }
        }

        ProfilerFiller profiler = Profiler.get();

        // If we have nothing to upload, abort!
        var translucentStorage = region.getStorage(DefaultTerrainRenderPasses.TRANSLUCENT);
        var needsSharedIndexUpdate = translucentStorage != null && translucentStorage.needsSharedIndexUpdate();
        if (uploads.isEmpty() && indexUploads.isEmpty() && !needsSharedIndexUpdate) {
            return;
        }

        var resources = region.createResources(commandList);

        profiler.push("upload_vertices");

        if (!uploads.isEmpty()) {
            var arena = resources.getGeometryArena();
            boolean bufferChanged = arena.upload(commandList, uploads.stream()
                    .map(upload -> upload.vertexUpload));
            boolean bufferChangedVoxels = voxelArena.upload(commandList, uploads.stream()
                    .filter(upload -> upload.voxelData != null).map(upload -> upload.voxelData));

            // If any of the buffers changed, the tessellation will need to be updated
            // Once invalidated the tessellation will be re-created on the next attempted use
            if (bufferChanged) {
                region.refreshTesselation(commandList);
                region.clearAllCachedBatches();
            }

            // Collect the upload results
            for (PendingSectionMeshUpload upload : uploads) {
                var storage = region.createStorage(upload.pass);
                storage.setVertexData(upload.section.getSectionIndex(),
                        upload.vertexUpload.getResult(), upload.meshData.getVertexSegments(), upload.voxelData);

                this.writeVoxels(upload);
            }
        }

        profiler.popPush("upload_indices");
        var indexBufferChanged = false;

        if (!indexUploads.isEmpty()) {
            var arena = resources.getIndexArena();
            indexBufferChanged = arena.upload(commandList, indexUploads.stream()
                    .map(upload -> upload.indexBufferUpload));

            for (PendingSectionIndexBufferUpload upload : indexUploads) {
                var storage = region.createStorage(DefaultTerrainRenderPasses.TRANSLUCENT);
                storage.setIndexData(upload.section.getSectionIndex(), upload.indexBufferUpload.getResult());
            }
        }

        if (needsSharedIndexUpdate) {
            indexBufferChanged |= translucentStorage.updateSharedIndexData(commandList, resources.getIndexArena());
        }

        if (indexBufferChanged) {
            region.refreshIndexedTesselation(commandList);
            region.clearCachedBatchFor(DefaultTerrainRenderPasses.TRANSLUCENT);
        }

        profiler.pop();

        voxelIndices.flush();
    }

    private void writeVoxels(PendingSectionMeshUpload chunkBuildOutput) {
        int chunkIndex = to1D(Math.floorMod(chunkBuildOutput.section.getChunkX(), renderDistanceInDiameter), chunkBuildOutput.section.getChunkY() + sectionsToReachZero, Math.floorMod(chunkBuildOutput.section.getChunkZ(), renderDistanceInDiameter));

        if (chunkBuildOutput.voxelData == null) {
            voxelIndices.write(chunkIndex * 4L, 0);
        } else {
            voxelIndices.write(chunkIndex * 4L, (int) chunkBuildOutput.voxelData.getResult().getOffset());
        }
    }

    private Reference2ReferenceMap.FastEntrySet<RenderRegion, List<BuilderTaskOutput>> createMeshUploadQueues(Collection<BuilderTaskOutput> results) {
        var map = new Reference2ReferenceOpenHashMap<RenderRegion, List<BuilderTaskOutput>>();

        for (var result : results) {
            var queue = map.computeIfAbsent(result.render.getRegion(), k -> new ArrayList<>());
            queue.add(result);
        }

        return map.reference2ReferenceEntrySet();
    }

    public void delete(CommandList commandList) {
        for (RenderRegion region : this.regions.values()) {
            region.delete(commandList);
        }

        this.voxelArena.delete(commandList);
        this.voxelIndices.destroy();

        this.regions.clear();
        this.stagingBuffer.delete(commandList);
    }

    public Collection<RenderRegion> getLoadedRegions() {
        return this.regions.values();
    }

    public StagingBuffer getStagingBuffer() {
        return this.stagingBuffer;
    }

    public RenderRegion createForChunk(int chunkX, int chunkY, int chunkZ) {
        return this.create(chunkX >> RenderRegion.REGION_WIDTH_SH,
                chunkY >> RenderRegion.REGION_HEIGHT_SH,
                chunkZ >> RenderRegion.REGION_LENGTH_SH);
    }

    public RenderRegion getForChunk(int chunkX, int chunkY, int chunkZ) {
        return this.regions.get(RenderRegion.key(chunkX >> RenderRegion.REGION_WIDTH_SH,
                chunkY >> RenderRegion.REGION_HEIGHT_SH,
                chunkZ >> RenderRegion.REGION_LENGTH_SH));
    }

    @NotNull
    private RenderRegion create(int x, int y, int z) {
        var key = RenderRegion.key(x, y, z);
        var instance = this.regions.get(key);

        if (instance == null) {
            this.regions.put(key, instance = new RenderRegion(x, y, z, this.stagingBuffer));
        }

        return instance;
    }

    private record PendingSectionMeshUpload(RenderSection section, PendingUpload voxelData, BuiltSectionMeshParts meshData, TerrainRenderPass pass, PendingUpload vertexUpload) {
    }

    private record PendingSectionIndexBufferUpload(RenderSection section, PendingUpload indexBufferUpload) {
    }

    private static StagingBuffer createStagingBuffer(CommandList commandList) {
        if (SodiumClientMod.options().advanced.useAdvancedStagingBuffers && MappedStagingBuffer.isSupported(RenderDevice.INSTANCE)) {
            return new MappedStagingBuffer(commandList);
        }

        return new FallbackStagingBuffer(commandList);
    }
}
