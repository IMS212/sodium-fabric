package net.caffeinemc.mods.sodium.client.render.chunk.region;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.vk.arena.ArenaAggregator;
import net.caffeinemc.mods.sodium.client.vk.arena.RegionAllocatorHandle;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import net.caffeinemc.mods.sodium.client.vk.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.minecraft.core.SectionPos;
import org.apache.commons.lang3.Validate;

import java.util.Arrays;
import java.util.Map;

public class RenderRegion {
    public static final int SECTION_VERTEX_COUNT_ESTIMATE = 756;
    public static final int SECTION_INDEX_COUNT_ESTIMATE = (SECTION_VERTEX_COUNT_ESTIMATE / DefaultTerrainRenderPasses.ALL.length / 4) * 6;
    public static final int SECTION_BUFFER_ESTIMATE = SECTION_VERTEX_COUNT_ESTIMATE * ChunkMeshFormats.COMPACT.getVertexFormat().getStride() + SECTION_INDEX_COUNT_ESTIMATE * Integer.BYTES;

    public static final int REGION_WIDTH = 8;
    public static final int REGION_HEIGHT = 4;
    public static final int REGION_LENGTH = 8;

    public static final int REGION_WIDTH_M = RenderRegion.REGION_WIDTH - 1;
    public static final int REGION_HEIGHT_M = RenderRegion.REGION_HEIGHT - 1;
    public static final int REGION_LENGTH_M = RenderRegion.REGION_LENGTH - 1;

    public static final int REGION_WIDTH_SH = Integer.bitCount(REGION_WIDTH_M);
    public static final int REGION_HEIGHT_SH = Integer.bitCount(REGION_HEIGHT_M);
    public static final int REGION_LENGTH_SH = Integer.bitCount(REGION_LENGTH_M);

    public static final int REGION_SIZE = REGION_WIDTH * REGION_HEIGHT * REGION_LENGTH;

    public static final int SHARED_INDEX_DATA_INDEX = -1;

    static {
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_WIDTH));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_HEIGHT));
        Validate.isTrue(MathUtil.isPowerOfTwo(REGION_LENGTH));
    }

    private final ArenaAggregator arenaAggregator;
    private final int x, y, z;

    private final ChunkRenderList renderList;

    private final RenderSection[] sections = new RenderSection[RenderRegion.REGION_SIZE];
    private final long creationTime;
    private int sectionCount;

    private final Map<TerrainRenderPass, SectionRenderDataStorage> sectionRenderData = new Reference2ReferenceOpenHashMap<>();
    private DeviceResources resources;

    private final Map<TerrainRenderPass, MultiDrawBatch> cachedBatches = new Reference2ReferenceOpenHashMap<>();

    public RenderRegion(int x, int y, int z, ArenaAggregator arenaAggregator) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.creationTime = System.currentTimeMillis();

        this.arenaAggregator = arenaAggregator;
        this.renderList = new ChunkRenderList(this);
    }

    public static long key(int x, int y, int z) {
        return SectionPos.asLong(x, y, z);
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public int getChunkX() {
        return this.x << REGION_WIDTH_SH;
    }

    public int getChunkY() {
        return this.y << REGION_HEIGHT_SH;
    }

    public int getChunkZ() {
        return this.z << REGION_LENGTH_SH;
    }

    public int getOriginX() {
        return this.getChunkX() << 4;
    }

    public int getOriginY() {
        return this.getChunkY() << 4;
    }

    public int getOriginZ() {
        return this.getChunkZ() << 4;
    }

    public void delete(CommandList commandList) {
        for (var storage : this.sectionRenderData.values()) {
            storage.delete();
        }

        this.sectionRenderData.clear();

        if (this.resources != null) {
            this.resources.delete(commandList);
            this.resources = null;
        }

        Arrays.fill(this.sections, null);

        for (var batch : this.cachedBatches.values()) {
            batch.delete();
        }
        this.cachedBatches.clear();
    }

    public void clearAllCachedBatches() {
        for (var batch : this.cachedBatches.values()) {
            batch.clear();
        }
    }

    public void clearCachedBatchFor(TerrainRenderPass pass) {
        var batch = this.cachedBatches.get(pass);
        if (batch != null) {
            batch.clear();
        }
    }

    public MultiDrawBatch getCachedBatch(TerrainRenderPass pass) {
        MultiDrawBatch batch = this.cachedBatches.get(pass);
        if (batch != null) {
            return batch;
        }

        batch = new MultiDrawBatch((ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1);
        this.cachedBatches.put(pass, batch);
        return batch;
    }

    public boolean isEmpty() {
        return this.sectionCount == 0;
    }

    public SectionRenderDataStorage getStorage(TerrainRenderPass pass) {
        return this.sectionRenderData.get(pass);
    }

    public SectionRenderDataStorage createStorage(TerrainRenderPass pass) {
        var storage = this.sectionRenderData.get(pass);

        if (storage == null) {
            storage = new SectionRenderDataStorage(pass.isTranslucent());
            this.sectionRenderData.put(pass, storage);
        }

        return storage;
    }

    public void onGeometryBufferChange(CommandList commandList) {
        for (var storage : this.sectionRenderData.values()) {
            storage.onBufferResized();
        }

        // invalidate the cached batches
        this.clearAllCachedBatches();
    }

    public void onIndexBufferChange(CommandList commandList) {
        var indexStorage = this.sectionRenderData.get(DefaultTerrainRenderPasses.TRANSLUCENT);
        if (indexStorage != null) {
            indexStorage.onIndexBufferResized();
        }

        // invalidate the cached batches
        this.clearCachedBatchFor(DefaultTerrainRenderPasses.TRANSLUCENT);
    }

    public static int packOwnerIndex(int sectionIndex, int passIndex) {
        return (passIndex << 16) | (sectionIndex & 0xFFFF);
    }

    public static int unpackSectionIndex(int ownerIndex) {
        return ownerIndex & 0xFFFF;
    }

    public static int unpackPassIndex(int ownerIndex) {
        return (ownerIndex >> 16) & 0xFF;
    }

    private void onGeometrySegmentChange(CommandList commandList, int ownerIndex) {
        var sectionIndex = RenderRegion.unpackSectionIndex(ownerIndex);
        var passIndex = RenderRegion.unpackPassIndex(ownerIndex);
        var storage = this.sectionRenderData.get(DefaultTerrainRenderPasses.ALL[passIndex]);

        storage.onVertexSegmentChanged(sectionIndex);

        this.clearAllCachedBatches();
    }

    private void onIndexSegmentChange(CommandList commandList, int ownerIndex) {
        var storage = this.sectionRenderData.get(DefaultTerrainRenderPasses.TRANSLUCENT);

        if (ownerIndex == SHARED_INDEX_DATA_INDEX) {
            storage.onSharedIndexSegmentChanged();
        } else {
            var sectionIndex = RenderRegion.unpackSectionIndex(ownerIndex);
            storage.onIndexSegmentChanged(sectionIndex);
        }

        this.clearCachedBatchFor(DefaultTerrainRenderPasses.TRANSLUCENT);
    }

    public void addSection(RenderSection section) {
        var sectionIndex = section.getSectionIndex();
        var prev = this.sections[sectionIndex];

        if (prev != null) {
            throw new IllegalStateException("Section has already been added to the region");
        }

        this.sections[sectionIndex] = section;
        this.sectionCount++;
    }

    public void removeSection(RenderSection section) {
        var sectionIndex = section.getSectionIndex();
        var prev = this.sections[sectionIndex];

        if (prev == null) {
            throw new IllegalStateException("Section was not loaded within the region");
        } else if (prev != section) {
            throw new IllegalStateException("Tried to remove the wrong section");
        }

        for (var storage : this.sectionRenderData.values()) {
            storage.removeData(sectionIndex);
        }

        this.sections[sectionIndex] = null;
        this.sectionCount--;
    }

    public float getFillFractionInv() {
        return (float) RenderRegion.REGION_SIZE / (float) this.sectionCount;
    }

    public RenderSection getSection(int id) {
        return this.sections[id];
    }

    public DeviceResources getResources() {
        return this.resources;
    }

    public DeviceResources createResources(CommandList commandList) {
        if (this.resources == null) {
            this.resources = new DeviceResources(commandList, this);
        }

        return this.resources;
    }

    public void update(CommandList commandList) {
        if (this.resources != null && this.resources.shouldDelete()) {
            this.resources.delete(commandList);
            this.resources = null;
        }
    }

    public ChunkRenderList getRenderList() {
        return this.renderList;
    }

    private final RegionAllocatorHandle.AllocationChangeConsumer geometryChangeConsumer = new RegionAllocatorHandle.AllocationChangeConsumer() {
        @Override
        public void onBufferChanged(CommandList commandList) {
            RenderRegion.this.onGeometryBufferChange(commandList);
        }

        @Override
        public void onSegmentChanged(CommandList commandList, int ownerIndex) {
            RenderRegion.this.onGeometrySegmentChange(commandList, ownerIndex);
        }
    };

    private final RegionAllocatorHandle.AllocationChangeConsumer indexChangeConsumer = new RegionAllocatorHandle.AllocationChangeConsumer() {
        @Override
        public void onBufferChanged(CommandList commandList) {
            RenderRegion.this.onIndexBufferChange(commandList);
        }

        @Override
        public void onSegmentChanged(CommandList commandList, int ownerIndex) {
            RenderRegion.this.onIndexSegmentChange(commandList, ownerIndex);
        }
    };

    public static class DeviceResources {
        private final RegionAllocatorHandle geometryArena;
        private final RegionAllocatorHandle indexArena;
        //private final GlBufferStreamer chunkFades;

        /**
         * The buffer arenas return offsets in terms of how many stride units big things
         * are. This means that if the stride is the length of a vertex, the buffer
         * arena works with vertices and returns vertex offsets. The arena working with
         * indices has as stride of four corresponding to the length of an integer. The
         * two can't easily be combined because integers and vertices require different
         * amounts of data which makes the returned offsets incompatible.
         */
        public DeviceResources(CommandList commandList, RenderRegion region) {
            int stride = ChunkMeshFormats.COMPACT.getVertexFormat().getStride();

            this.geometryArena = region.arenaAggregator.getGeometryBufferAllocator(commandList, region, stride,
                    region.geometryChangeConsumer);
            //this.chunkFades = new GlBufferStreamer(commandList, REGION_SIZE, Integer.BYTES);
            this.indexArena = region.arenaAggregator.getIndexBufferAllocator(commandList, region, Integer.BYTES, region.indexChangeConsumer);
        }

        public void writeMeshTimes(int sectionIndex, int millisecondToCompare) {
           // this.chunkFades.writeData(sectionIndex, millisecondToCompare);
        }

        public VkBuffer prepareChunkData(CommandList commandList) {
            return null;//this.chunkFades.prepare(commandList);
        }


        public VkBuffer getGeometryBuffer() {
            return this.geometryArena.getBufferObject();
        }

        public VkBuffer getIndexBuffer() {
            return this.indexArena.getBufferObject();
        }

        public void delete(CommandList commandList) {
            this.geometryArena.deleteSingleOwner(commandList);
            this.indexArena.deleteSingleOwner(commandList);
        }

        public RegionAllocatorHandle getGeometryAllocator() {
            return this.geometryArena;
        }

        public RegionAllocatorHandle getIndexAllocator() {
            return this.indexArena;
        }

        public boolean shouldDelete() {
            return this.geometryArena.isEmpty() && this.indexArena.isEmpty();
        }
    }
}
