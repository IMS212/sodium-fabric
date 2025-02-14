package net.caffeinemc.mods.sodium.client.render.chunk.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
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
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RenderRegionManager {
    private static final int NUM_OF_CHUNKS = 128;
    private final Long2ReferenceOpenHashMap<RenderRegion> regions = new Long2ReferenceOpenHashMap<>();

    private final StagingBuffer stagingBuffer;
    public final GlBufferArena voxelArena;
    private Deque<PendingSectionVoxelUpload> upcomingUploads = new ArrayDeque<>();
    private Deque<PendingDispatch> upcomingDispatches = new ArrayDeque<>();
    private PersistentBufferObject ubo = new PersistentBufferObject(16 * NUM_OF_CHUNKS);
    private List<PendingDispatch>[] dispatches = new ArrayList[3];

    private int frame = 0;

    public RenderRegionManager(CommandList commandList) {
        this.stagingBuffer = createStagingBuffer(commandList);
        int rd = 2 * Minecraft.getInstance().options.getEffectiveRenderDistance() + 1;
        this.voxelArena = new GlBufferArena(commandList, rd * 16,
                32768, stagingBuffer);
        for (int i = 0; i < 3; i++) {
            dispatches[i] = new ArrayList<>();
        }

        int buffer = GL46C.glGenBuffers();
        GL46C.glNamedBufferStorage(buffer, 4, 0);

        GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 5, buffer);
    }

    public void update() {
        GL46C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, 8, voxelArena.getBufferObject().handle());
        this.stagingBuffer.flip();


        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            int queueSize = Math.min(256, this.upcomingUploads.size());
            PendingSectionVoxelUpload[] toUpload = new PendingSectionVoxelUpload[queueSize];

            for (int i = 0; i < queueSize; i++) {
                toUpload[i] = this.upcomingUploads.poll();
                if (toUpload[i].uploaded.get()) throw new IllegalStateException("HOW");
            }

            boolean bufferChanged = voxelArena.upload(commandList, Arrays.stream(toUpload)
                    .map(upload1 -> upload1.upload));

            for (int i = 0; i < queueSize; i++) {
                toUpload[i].section.setVoxelOffset(toUpload[i].upload.getResult());
                toUpload[i].data.free();
                toUpload[i].uploaded.set(true);
                DefaultShaderInterface.VOXEL = (int) (toUpload[i].upload.getResult().getOffset());

                upcomingDispatches.add(new PendingDispatch(frame, toUpload[i].upload.getResult(), toUpload[i].section));
            }


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

            ubo.beginFrame();

            ubo.updateAndFlush((buffer) -> {
                long offset = 0;

                for (int i = 0; i < Math.min(upcomingDispatches.size(), NUM_OF_CHUNKS); i++) {
                    // pretend for now
                    PendingDispatch dispatch = upcomingDispatches.poll();
                    if (frame - dispatch.frame() < 2) {
                        upcomingDispatches.add(dispatch);
                        continue;
                    }

                    dispatches[frame % 3].add(dispatch);
                    MemoryUtil.memPutInt(buffer + offset, dispatch.section().getChunkX());
                    MemoryUtil.memPutInt(buffer + offset + 4, dispatch.section().getChunkY());
                    MemoryUtil.memPutInt(buffer + offset + 8, dispatch.section().getChunkZ());
                    MemoryUtil.memPutInt(buffer + offset + 12, (int) (dispatch.segment().getOffset()));

                    offset += 16;
                }

                return offset;
            });

            VoxelCompute.run(dispatches[frame % 3].size(), ubo);

            for (PendingDispatch dispatch : dispatches[(frame + 2) % 3]) {
                dispatch.segment().delete();
            }

            dispatches[(frame + 2) % 3].clear();
        }

        frame++;
    }

    public void uploadResults(CommandList commandList, Collection<BuilderTaskOutput> results) {
        for (var entry : this.createMeshUploadQueues(results)) {
            this.uploadResults(commandList, entry.getKey(), entry.getValue());
        }
    }

    public int[] to3D( int idx ) {
        final int z = idx / (16 * 16);
        idx -= (z * 16 * 16);
        final int y = idx / 16;
        final int x = idx % 16;
        return new int[]{ x, y, z };
    }

    private void uploadResults(CommandList commandList, RenderRegion region, Collection<BuilderTaskOutput> results) {
        var uploads = new ArrayList<PendingSectionMeshUpload>();
        var voxelUpload = new ObjectArraySet<PendingSectionVoxelUpload>();
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
                    }

                    BuiltSectionMeshParts mesh = chunkBuildOutput.getMesh(pass);

                    if (mesh != null) {
                        uploads.add(new PendingSectionMeshUpload(result.render, mesh, pass,
                        new PendingUpload(mesh.getVertexData())));
                    }

                    NativeBuffer voxels = chunkBuildOutput.getVoxels();

                    if (voxels != null) {
                        voxelUpload.add(new PendingSectionVoxelUpload(result.render, voxels, new PendingUpload(voxels), new AtomicBoolean()));
                    }
                }
            }

            if (result instanceof ChunkSortOutput indexDataOutput && !indexDataOutput.isReusingUploadedIndexData()) {
                var buffer = indexDataOutput.getIndexBuffer();

                // when a non-present TranslucentData is used like NoData, the indexBuffer is null
                if (buffer == null) {
                    continue;
                }

                indexUploads.add(new PendingSectionIndexBufferUpload(result.render, new PendingUpload(buffer)));

                var storage = region.getStorage(DefaultTerrainRenderPasses.TRANSLUCENT);
                if (storage != null) {
                    storage.removeIndexData(renderSectionIndex);
                }
            }
        }

        ProfilerFiller profiler = Profiler.get();

        // If we have nothing to upload, abort!
        if (uploads.isEmpty() && indexUploads.isEmpty() && voxelUpload.isEmpty()) {
            return;
        }

        var resources = region.createResources(commandList);

        profiler.push("upload_vertices");

        if (!uploads.isEmpty()) {
            var arena = resources.getGeometryArena();
            boolean bufferChanged = arena.upload(commandList, uploads.stream()
                    .map(upload -> upload.vertexUpload));

            // If any of the buffers changed, the tessellation will need to be updated
            // Once invalidated the tessellation will be re-created on the next attempted use
            if (bufferChanged) {
                region.refreshTesselation(commandList);
            }

            // Collect the upload results
            for (PendingSectionMeshUpload upload : uploads) {
                var storage = region.createStorage(upload.pass);
                storage.setVertexData(upload.section.getSectionIndex(),
                        upload.vertexUpload.getResult(), upload.meshData.getVertexCounts());
            }
        }

        profiler.popPush("upload_voxels");

        if (!voxelUpload.isEmpty()) {
            upcomingUploads.addAll(voxelUpload);
        }

        profiler.popPush("upload_indices");

        if (!indexUploads.isEmpty()) {
            var arena = resources.getIndexArena();
            boolean bufferChanged = arena.upload(commandList, indexUploads.stream()
                    .map(upload -> upload.indexBufferUpload));

            if (bufferChanged) {
                region.refreshIndexedTesselation(commandList);
            }

            for (PendingSectionIndexBufferUpload upload : indexUploads) {
                var storage = region.createStorage(DefaultTerrainRenderPasses.TRANSLUCENT);
                storage.setIndexData(upload.section.getSectionIndex(), upload.indexBufferUpload.getResult());
            }
        }

        profiler.pop();
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

        this.regions.clear();
        this.voxelArena.delete(commandList);
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

    private record PendingSectionMeshUpload(RenderSection section, BuiltSectionMeshParts meshData, TerrainRenderPass pass, PendingUpload vertexUpload) {
    }

    private record PendingSectionVoxelUpload(RenderSection section, NativeBuffer data, PendingUpload upload, AtomicBoolean uploaded) {
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PendingSectionVoxelUpload)) {
                return false;
            }

            PendingSectionVoxelUpload other = (PendingSectionVoxelUpload) obj;

            return section.equals(other.section) && data.getAddress() == other.data.getAddress();
        }

        @Override
        public int hashCode() {
            return Objects.hash(section, data.getAddress());
        }
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
