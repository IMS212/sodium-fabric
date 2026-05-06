package net.caffeinemc.mods.sodium.client.gl.arena;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.StagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

// TODO: if the required capacity is huge, maybe it shouldn't be shared, or we should overshoot it more?
// TODO: when moving region to a new buffer, return the next shared arena, or decide that the request is too big and return a regular single-owner
// TODO: allow user to change the vram pre-allocation size via config, but auto-scale regardless if it's too small
// TODO: compaction when multiple shared arenas together have less than 70% of a single one used
// TODO: deallocate shared arenas when they become empty, don't re-use more than some limited amount of memory when deallocated shared arena becomes freed
public class ArenaAggregator {
    // how much bigger than requested a buffer can be to be considered for reuse
    public static final float MAX_BUFFER_REUSE_SIZE_FACTOR = 1.4f;
    private static final int DEFRAG_COPIES_PER_FRAME_BUDGET = 32;
    private static final long DEFRAG_BYTES_PER_FRAME_BUDGET = MathUtil.fromMib(32);
    private static final float MIN_FREE_FRACTION_AFTER_DEALLOC = 0.07f;
    private static final float FREE_FRACTION_AFTER_DEALLOC_ABORT_LIMIT = 0.04f;
    private static final long RATE_MEASURE_INTERVAL_NANOS = 500_000_000L;
    // pause deallocation if allocation rate exceeds this fraction of total memory per second
    // divided by 1 billion to convert from per second to per nanosecond
    private static final float PAUSE_DEALLOCATION_ABOVE_FRACTION = 0.02f / 1_000_000_000f;
    // resize to compact if an arena's free space accounts for this much of the total free space
    private static final float RESIZE_TO_COMPACT_TOTAL_FREE_FRACTION = 0.05f;
    private static final float COMPACTION_MARGIN = 0.1f;

    private static final long NO_MAX_CAPACITY = 0;
    private static final int DISALLOW_NEW_ALLOCATION = 0;
    private static final int ALLOW_NEW_ALLOCATION = 1;
    private static final int REQUIRE_NEW_ALLOCATION = 2;

    final StagingBuffer stagingBuffer;
    private final GpuBuffer[] freeBuffers = new GpuBuffer[8];
    private static int freeBufferCount = 0;

    private final DataType index = new DataType("Index", Integer.BYTES) {
        @Override
        long calculateArenaSize(int newArenaCount, long requiredSize, long maxSize) {
            var factorSize = switch (newArenaCount) {
                case 1 -> MathUtil.fromMib(16);
                case 2 -> MathUtil.fromMib(32);
                default -> MathUtil.fromMib(64);
            };
            var capacitySize = requiredSize * 3;
            if (maxSize != NO_MAX_CAPACITY) {
                capacitySize = requiredSize * 2;
            } else {
                maxSize = Long.MAX_VALUE;
            }
            return Math.min(Math.max(capacitySize, factorSize), maxSize);
        }
    };
    private final DataType geometry = new DataType("Geometry", ChunkMeshFormats.COMPACT.getVertexFormat().getStride()) {
        @Override
        long calculateArenaSize(int newArenaCount, long requiredSize, long maxSize) {
            var factorSize = switch (newArenaCount) {
                case 1 -> MathUtil.fromMib(32);
                case 2 -> MathUtil.fromMib(128);
                default -> MathUtil.fromMib(256);
            };
            var capacitySize = requiredSize * 7;
            if (maxSize != NO_MAX_CAPACITY) {
                capacitySize = requiredSize * 2;
            } else {
                maxSize = Long.MAX_VALUE;
                if (requiredSize >= MathUtil.fromMib(256)) {
                    capacitySize = requiredSize * 2;
                } else if (requiredSize >= MathUtil.fromMib(32) && newArenaCount >= 3) {
                    capacitySize = requiredSize * 4;
                }
            }
            return Math.min(Math.max(capacitySize, factorSize), maxSize);
        }
    };

    // all shared arenas, keyed by stride, and then sorted by the biggest contiguous free block size they have to offer
    private final List<DataType> dataTypes = List.of(this.index, this.geometry);
    private long lastRateMeasureTime = 0;

    private int arenaDefragOffset = 0; // round-robin index for defragmentation
    private int totalCopyCount = 0;
    private long totalCopyBytes = 0;
    private int allocationCount = 0;
    private long allocationBytes = 0;

    public static class DefragBudget {
        private final int startCopyCount;
        private final long startCopyBytes;
        private int copyCount;
        private long copyBytes;
        private long copyElements;

        public DefragBudget(int copyCount, long copyBytes) {
            this.startCopyCount = copyCount;
            this.startCopyBytes = copyBytes;
            this.copyCount = copyCount;
            this.copyBytes = copyBytes;
        }

        public void setupElementCopy(int elementSize) {
            this.copyElements = this.copyBytes / elementSize;
        }

        public void consumeElementCopy(long elementsCopied, long bytesCopied) {
            this.copyCount--;
            this.copyBytes -= bytesCopied;
            this.copyElements -= elementsCopied;
        }

        public boolean isElementBudgetEmpty() {
            return this.copyCount <= 0 || this.copyBytes <= 0 || this.copyElements <= 0;
        }

        public boolean elementCopyExceedsBudget(long elements) {
            return elements > this.copyElements;
        }

        public int getUsedCopyCount() {
            return this.startCopyCount - this.copyCount;
        }

        public long getUsedCopyBytes() {
            return this.startCopyBytes - this.copyBytes;
        }
    }

    private abstract class DataType {
        final String name;
        final int stride;
        final ArrayList<SharedGlBufferArena> arenas;
        long totalUsedLastCheckpoint;
        boolean pauseDeallocation = true;

        DataType(String name, int stride) {
            this.name = name;
            this.stride = stride;
            this.arenas = new ArrayList<>();
        }

        abstract long calculateArenaSize(int newArenaCount, long requiredSize, long maxSize);

        SharedGlBufferArena createSharedArena(CommandList commands, long requiredSize) {
            GpuBuffer buffer = ArenaAggregator.this.getBufferOfSizeAtLeast(commands, requiredSize);
            long actualCapacity = buffer.size() / this.stride;
            return new SharedGlBufferArena(ArenaAggregator.this, buffer, actualCapacity, this.stride);
        }

        SharedGlBufferArena ensureSharedArena(CommandList commands, long requiredCapacity, int newAllocationMode, long maxCapacity) {
            SharedGlBufferArena bestArena = null;
            if (newAllocationMode != REQUIRE_NEW_ALLOCATION) {
                long biggestFreeSegmentSize = requiredCapacity;
                for (var arena : this.arenas) {
                    long arenaBiggestFreeSegmentSize = arena.getBiggestFreeSegmentSize();
                    if (!arena.isEmptying() && arenaBiggestFreeSegmentSize >= biggestFreeSegmentSize) {
                        bestArena = arena;
                        biggestFreeSegmentSize = arenaBiggestFreeSegmentSize;
                    }
                }
            }

            if (bestArena == null && newAllocationMode != DISALLOW_NEW_ALLOCATION) {
                var allocationSize = this.calculateArenaSize(this.arenas.size() + 1, requiredCapacity * this.stride, maxCapacity * this.stride);
                if (allocationSize <= 0) {
                    throw new IllegalStateException("Cannot allocate arena of with " + requiredCapacity + " bytes");
                }
                bestArena = createSharedArena(commands, allocationSize);
                this.arenas.add(bestArena);
            }

            return bestArena;
        }

        long getDeviceUsedMemory() {
            long used = 0;
            for (var arenaEntry : this.arenas) {
                used += arenaEntry.getDeviceUsedMemory();
            }
            return used;
        }

        long getDeviceAllocatedMemory() {
            long allocated = 0;
            for (var arenaEntry : this.arenas) {
                allocated += arenaEntry.getDeviceAllocatedMemory();
            }
            return allocated;
        }

        void update(CommandList commands, DefragBudget budget, long nanosSinceMeasure) {
            budget.setupElementCopy(this.stride);

            // calculate total unfragmented free and capacity, remove empty arenas.
            // note that this uses unfragmented free instead of calculating total free from total usage and total capacity because if we do this with fragmented free it might try to deallocate and arena that requires moving data but can't because there's not enough contiguous free space in the other arenas.
            long totalUsed = 0;
            long totalCapacity = 0;
            long totalUnfragmentedFree = 0;
            SharedGlBufferArena emptyingArena = null;
            var canDeleteArena = this.arenas.size() > 1;
            var it = this.arenas.iterator();
            while (it.hasNext()) {
                var arena = it.next();

                if (arena.isEmpty() && canDeleteArena && !arena.isCompactionTarget()) {
                    arena.deleteShared(commands);
                    it.remove();
                    continue;
                }

                totalUsed += arena.getUsed();
                totalCapacity += arena.getCapacity();
                totalUnfragmentedFree += arena.getBiggestFreeSegmentSize();
                if (arena.isEmptying()) {
                    emptyingArena = arena;
                }
            }

            // update deallocation pausing when allocation rate is high
            if (nanosSinceMeasure >= RATE_MEASURE_INTERVAL_NANOS) {
                var allocationRate = totalUsed - this.totalUsedLastCheckpoint;
                var allocationFractionPerSecond = (allocationRate / (float) totalCapacity) / nanosSinceMeasure;
                this.pauseDeallocation = allocationFractionPerSecond > PAUSE_DEALLOCATION_ABOVE_FRACTION;
                this.totalUsedLastCheckpoint = totalUsed;
            }

            // perform emptying on the currently emptying arena
            if (emptyingArena != null) {
                // make sure the arena that's emptying wouldn't cause there to be too little free space or too few arenas
                if (emptyingArena.getGlobalFreeFractionAfterEmptying(totalCapacity, totalUnfragmentedFree) < FREE_FRACTION_AFTER_DEALLOC_ABORT_LIMIT || !canDeleteArena || this.pauseDeallocation) {
                    emptyingArena.setEmptying(false);
                    emptyingArena = null;
                }
                // remove if emptying results in empty
                else if (emptyingArena.continueEmptying(commands, budget)) {
                    emptyingArena.deleteShared(commands);
                    this.arenas.remove(emptyingArena);
                    emptyingArena = null;
                }
            }

            // stop if the budget has been used up
            if (emptyingArena != null && budget.isElementBudgetEmpty()) {
                return;
            }

            // run defragmentation and identify candidates for types of emptying
            SharedGlBufferArena leastUsedArena = null;
            SharedGlBufferArena smallestArena = null;
            SharedGlBufferArena compactionCandidate = null;
            for (int i = 0; i < this.arenas.size(); i++) {
                int arenaIndex = (ArenaAggregator.this.arenaDefragOffset + i) % this.arenas.size();
                var arena = this.arenas.get(arenaIndex);

                if (!arena.isEmptying()) {
                    if (leastUsedArena == null || arena.getUsed() < leastUsedArena.getUsed()) {
                        leastUsedArena = arena;
                    }

                    if (smallestArena == null || arena.getCapacity() < smallestArena.getCapacity()) {
                        smallestArena = arena;
                    }

                    if ((compactionCandidate == null || arena.getFree() > compactionCandidate.getFree()) && arena.isNotCompacting()) {
                        compactionCandidate = arena;
                    }

                    if (!budget.isElementBudgetEmpty()) {
                        arena.defragmentIncremental(commands, budget);
                    }
                }
            }

            // check if we can deallocate the least used arena by relocating its data into the others
            if (emptyingArena == null && leastUsedArena != null && canDeleteArena && !this.pauseDeallocation && leastUsedArena.isNotCompacting() &&
                    leastUsedArena.getGlobalFreeFractionAfterEmptying(totalCapacity, totalUnfragmentedFree) >= MIN_FREE_FRACTION_AFTER_DEALLOC) {
                leastUsedArena.setEmptying(true);
                emptyingArena = leastUsedArena;
            }

            // if no arena has been selected for emptying, check if we can transfer the smallest arena's data into the others
            if (emptyingArena == null && canDeleteArena && smallestArena != null && smallestArena.isNotCompacting() &&
                    smallestArena.getGlobalFreeFractionAfterEmptying(totalCapacity, totalUnfragmentedFree) >= MIN_FREE_FRACTION_AFTER_DEALLOC) {
                smallestArena.setEmptying(true);
                emptyingArena = smallestArena;
            }

            // TODO: this does't work well yet, leads to excessive allocation and buffers that get immediately deleted
            // if there's not yet an emptying arena, check if we can resize the biggest free arena to compact it
//            if (emptyingArena == null && compactionCandidate != null && !this.pauseDeallocation) {
//                float freeFraction = compactionCandidate.getFree() / (float) totalUnfragmentedFree;
//                if (freeFraction >= RESIZE_TO_COMPACT_TOTAL_FREE_FRACTION) {
//                    var sizeTarget = compactionCandidate.getUsed() + (long) (compactionCandidate.getFree() * COMPACTION_MARGIN);
//                    var compactionTarget = this.ensureSharedArena(commands, compactionCandidate.getUsed(), REQUIRE_NEW_ALLOCATION, sizeTarget);
//                    compactionCandidate.makeCompactionSource(compactionTarget);
//                    // emptyingArena = biggestFreeArena;
//                }
//            }
        }
    }

    public ArenaAggregator(StagingBuffer stagingBuffer) {
        this.stagingBuffer = stagingBuffer;
    }

    public RegionAllocatorHandle getGeometryBufferAllocator(CommandList commands, RenderRegion region, int stride, RegionAllocatorHandle.AllocationChangeConsumer onChange) {
        return createAllocator(commands, region, stride, onChange);
    }

    public RegionAllocatorHandle getIndexBufferAllocator(CommandList commands, RenderRegion region, int stride, RegionAllocatorHandle.AllocationChangeConsumer onChange) {
        return createAllocator(commands, region, stride, onChange);
    }

    private RegionAllocatorHandle createAllocator(CommandList commands, RenderRegion region, int stride, RegionAllocatorHandle.AllocationChangeConsumer onChange) {
        GlBufferArena backingArena = getArenaFittingFor(commands, 0, stride, true);
        return new RegionAllocatorHandle(region, onChange, backingArena);
    }

    private DataType getDataTypeForStride(int stride) {
        if (stride == this.index.stride) {
            return this.index;
        } else if (stride == this.geometry.stride) {
            return this.geometry;
        } else {
            throw new IllegalArgumentException("Unsupported stride: " + stride);
        }
    }

    GlBufferArena getArenaFittingFor(CommandList commands, long requiredCapacity, int stride, boolean allowNewAllocation) {
        // TODO: create arena size based on top k region sizes, and scale up if all regions are big
        return getDataTypeForStride(stride).ensureSharedArena(commands, requiredCapacity, allowNewAllocation ? ALLOW_NEW_ALLOCATION : DISALLOW_NEW_ALLOCATION, NO_MAX_CAPACITY);
    }

    GlBufferArena createDedicatedArena(CommandList commands, long requiredCapacity, int stride) {
        GpuBuffer buffer = getBufferOfSizeAtLeast(commands, requiredCapacity * stride);
        long actualCapacity = buffer.size() / stride;
        return new SingleOwnerGlBufferArena(this, buffer, actualCapacity, stride);
    }

    GpuBuffer getBufferOfSizeAtLeast(CommandList commands, long bytes) {
        GpuBuffer buffer = null;

        if (freeBufferCount > 0) {
            // get any buffer of at least the requested size but at most MAX_BUFFER_REUSE_SIZE_FACTOR larger
            long maxAcceptableSize = (long) (bytes * MAX_BUFFER_REUSE_SIZE_FACTOR);

            // iterate buffers to get the smallest acceptable one
            int candidateIndex = -1;
            for (int i = 0; i < this.freeBuffers.length; i++) {
                GpuBuffer freeBuffer = this.freeBuffers[i];
                if (freeBuffer != null) {
                    long testSize = freeBuffer.size();
                    if (testSize >= bytes && testSize <= maxAcceptableSize &&
                            (buffer == null || testSize < buffer.size())) {
                        candidateIndex = i;
                        buffer = freeBuffer;
                    }
                }
            }
            if (buffer != null) {
                this.freeBuffers[candidateIndex] = null;
                freeBufferCount--;
            }
        }

        if (buffer == null) {
            buffer = RenderSystem.getDevice().createBuffer(() -> "Arena buffer",
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC, bytes);
            this.allocationCount++;
            this.allocationBytes += bytes;
        }
        return buffer;
    }

    void releaseBufferForReuse(CommandList commands, GpuBuffer buffer) {
        // find an empty slot if there is one
        if (freeBufferCount < this.freeBuffers.length) {
            for (int i = 0; i < this.freeBuffers.length; i++) {
                if (this.freeBuffers[i] == null) {
                    this.freeBuffers[i] = buffer;
                    freeBufferCount++;
                    return;
                }
            }
        }

        // evict randomly if no empty slot available
        int evictIndex = (int) (Math.random() * this.freeBuffers.length);
        if (this.freeBuffers[evictIndex] != null) this.freeBuffers[evictIndex].close();
        this.freeBuffers[evictIndex] = buffer;
    }

    public void delete(CommandList commands) {
        for (int i = 0; i < this.freeBuffers.length; i++) {
            GpuBuffer buffer = this.freeBuffers[i];
            if (buffer != null) {
                buffer.close();
                this.freeBuffers[i] = null;
            }
        }
        freeBufferCount = 0;

        for (var dataType : this.dataTypes) {
            for (var arenaEntry : dataType.arenas) {
                arenaEntry.deleteShared(commands);
            }
            dataType.arenas.clear();
        }
    }

    public void update(CommandList commands) {
        long currentTime = System.nanoTime();
        long timeSinceLastMeasure = currentTime - this.lastRateMeasureTime;
        if (timeSinceLastMeasure >= RATE_MEASURE_INTERVAL_NANOS) {
            this.lastRateMeasureTime = currentTime;
        }

        // TODO: adjust based on total memory usage? if we have more memory usage we need to move more of it around
        var budget = new DefragBudget(DEFRAG_COPIES_PER_FRAME_BUDGET, DEFRAG_BYTES_PER_FRAME_BUDGET);

        // perform some amount of defragmentation on update
        var typeOffset = (int) Math.floor(Math.random() * this.dataTypes.size());
        for (int i = 0; i < this.dataTypes.size(); i++) {
            int dataTypeIndex = (typeOffset + i) % this.dataTypes.size();
            var dataType = this.dataTypes.get(dataTypeIndex);
            dataType.update(commands, budget, timeSinceLastMeasure);
        }

        this.totalCopyCount += budget.getUsedCopyCount();
        this.totalCopyBytes += budget.getUsedCopyBytes();
        this.arenaDefragOffset++;
    }

    public long getGeometryDeviceUsedMemory() {
        return this.geometry.getDeviceUsedMemory();
    }

    public long getIndexDeviceUsedMemory() {
        return this.index.getDeviceUsedMemory();
    }

    public long getGeometryDeviceAllocatedMemory() {
        return this.geometry.getDeviceAllocatedMemory();
    }

    public long getIndexDeviceAllocatedMemory() {
        return this.index.getDeviceAllocatedMemory();
    }

    public long getMiscAllocatedMemory() {
        long allocated = 0;
        for (GpuBuffer buffer : this.freeBuffers) {
            if (buffer != null) {
                allocated += buffer.size();
            }
        }
        return allocated;
    }

    public int getBufferCount() {
        int count = freeBufferCount;
        for (var dataType : this.dataTypes) {
            count += dataType.arenas.size();
        }
        return count;
    }

    public void renderBufferDebug(GuiGraphicsExtractor graphics) {
        int leftPadding = 10;
        int verticalPadding = 10;
        int arenaPadding = 4;
        int targetWidth = graphics.guiWidth() / 2;
        int targetHeight = graphics.guiHeight();
        var totalMapHeight = (targetHeight - verticalPadding - 2 * verticalPadding * this.dataTypes.size());

        // count number of maps to adjust heights
        var countOffset = 2;
        int mapCount = this.dataTypes.stream().mapToInt(dt -> dt.arenas.size() + countOffset).sum();
        if (mapCount == 0) {
            return;
        }

        int y = verticalPadding;
        for (var dataType : this.dataTypes) {
            // dataType.name + " Shared Arenas: " + dataType.arenas.size()
            var str = String.format("%s Shared Arenas: %d (Used: %d MiB / Allocated: %d MiB) %s",
                    dataType.name,
                    dataType.arenas.size(),
                    MathUtil.toMib(dataType.getDeviceUsedMemory()),
                    MathUtil.toMib(dataType.getDeviceAllocatedMemory()),
                    dataType.pauseDeallocation ? "deallocation paused" : "");
            graphics.text(Minecraft.getInstance().font, str, leftPadding, y, Colors.FOREGROUND);
            y += verticalPadding;
            var x = leftPadding;
            var arenaCount = dataType.arenas.size();
            if (arenaCount == 0) {
                continue;
            }

            int mapWidth = (targetWidth - leftPadding * 2 - arenaPadding * (arenaCount - 1)) / arenaCount;
            int mapHeight = (totalMapHeight * (arenaCount + countOffset)) / mapCount;
            for (var arenaEntry : dataType.arenas) {
                arenaEntry.renderDebugMap(graphics, x, y, mapWidth, mapHeight);
                x += mapWidth + arenaPadding;
            }

            y += mapHeight + verticalPadding;
        }

        // show total copies and bytes
        graphics.text(Minecraft.getInstance().font,
                String.format("Defragmentation copies: %d (%d MiB)",
                        this.totalCopyCount, MathUtil.toMib(this.totalCopyBytes)),
                leftPadding, 30, Colors.FOREGROUND);

        // budget per frame
        graphics.text(Minecraft.getInstance().font,
                String.format("Defragmentation budget per frame: %d copies / %d MiB",
                        DEFRAG_COPIES_PER_FRAME_BUDGET,
                        MathUtil.toMib(DEFRAG_BYTES_PER_FRAME_BUDGET)),
                leftPadding, 40, Colors.FOREGROUND);

        // allocation stats
        graphics.text(Minecraft.getInstance().font,
                String.format("Buffer allocations: %d (%d MiB)",
                        this.allocationCount, MathUtil.toMib(this.allocationBytes)),
                leftPadding, 50, Colors.FOREGROUND);
    }
}
