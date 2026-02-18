package net.caffeinemc.mods.sodium.client.vk.arena;

import net.caffeinemc.mods.sodium.client.vk.VulkanContext;
import net.caffeinemc.mods.sodium.client.vk.arena.staging.StagingBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_GPU_ONLY;
import static org.lwjgl.util.vma.Vma.vmaCreateBuffer;
import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;

public class VkBufferArena {
    static final boolean CHECK_ASSERTIONS = false;

    // how many segments we require before using an average-size growth estimate
    public static final int MIN_SEGMENTS_FOR_AVG = 16;
    // growth factor to use when we do not have enough segments for a stable average
    public static final float FEW_SEGMENTS_GROWTH_FACTOR = 1.5f;
    // safety factor applied to expected allocation growth
    public static final float EXPECTED_SIZE_TARGET_FACTOR = 1.5f;
    // how much bigger than requested a reusable buffer is allowed to be
    public static final float MAX_BUFFER_REUSE_SIZE_FACTOR = 1.4f;
    public static final int FRAMES_IN_FLIGHT = 3;

    private static final int BUFFER_USAGE = VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT;

    private static final VmaBuffer[] freeBuffers = new VmaBuffer[8];
    private static final ArrayDeque<List<VmaBuffer>> deleteEventually = new ArrayDeque<>();
    private static List<VmaBuffer> pendingDelete = new ArrayList<>();
    private static int freeBufferCount = 0;

    private final StagingBuffer stagingBuffer;
    private final int stride;

    private VmaBuffer arenaBuffer;
    private VkBufferSegment head;
    private long capacity;
    private long used;
    private int segmentCount;

    public VkBufferArena(int initialCapacity, int stride, StagingBuffer stagingBuffer) {
        this.capacity = initialCapacity;
        this.stride = stride;
        this.head = new VkBufferSegment(this, 0, this.capacity);
        this.head.setFree(true);
        this.arenaBuffer = getBufferOfSizeAtLeast(Math.max(1L, this.capacity * stride));
        this.capacity = this.arenaBuffer.size() / stride;
        this.stagingBuffer = stagingBuffer;
    }

    public long getDeviceUsedMemory() {
        return this.used * this.stride;
    }

    public long getDeviceAllocatedMemory() {
        return this.capacity * this.stride;
    }

    public long getBufferHandle() {
        return this.arenaBuffer.buffer();
    }

    public boolean isEmpty() {
        return this.used <= 0;
    }

    public void delete() {
        if (this.arenaBuffer != null) {
            releaseBufferForReuse(this.arenaBuffer);
            this.arenaBuffer = null;
        }
    }

    public static synchronized void flip() {
        deleteEventually.addLast(pendingDelete);
        pendingDelete = new ArrayList<>();

        while (deleteEventually.size() > FRAMES_IN_FLIGHT) {
            for (VmaBuffer buffer : deleteEventually.removeFirst()) {
                addFreeBuffer(buffer);
            }
        }
    }

    public boolean upload(Stream<PendingUpload> stream, float regionFillFractionInv) {
        return this.upload(VulkanContext.INSTANCE.mainCommandBuffer(), stream, regionFillFractionInv);
    }

    private boolean upload(VkCommandBuffer commandBuffer, Stream<PendingUpload> stream, float regionFillFractionInv) {
        long previousBuffer = this.arenaBuffer.buffer();
        long totalUploadSize = 0;
        List<PendingUpload> queue = new LinkedList<>();

        for (var upload : (Iterable<PendingUpload>) stream::iterator) {
            totalUploadSize += upload.getDataBuffer().getLength();
            queue.add(upload);
        }

        if (totalUploadSize < (this.capacity - this.used) * this.stride) {
            this.tryUploads(commandBuffer, queue);
        }

        if (!queue.isEmpty()) {
            this.resize(commandBuffer, estimateNewCapacity(regionFillFractionInv, queue));
            this.tryUploads(commandBuffer, queue);

            if (!queue.isEmpty()) {
                throw new RuntimeException("Failed to upload all buffers... despite resizing. That shouldn't be possible.");
            }
        }

        return this.arenaBuffer.buffer() != previousBuffer;
    }

    public void free(VkBufferSegment entry) {
        if (entry.isFree()) {
            throw new IllegalStateException("Already freed");
        }

        entry.setFree(true);
        this.updateUsed(-entry.getLength());

        VkBufferSegment next = entry.getNext();
        if (next != null && next.isFree()) {
            entry.mergeInto(next);
        }

        VkBufferSegment prev = entry.getPrev();
        if (prev != null && prev.isFree()) {
            prev.mergeInto(entry);
        }

        this.checkAssertions();
    }

    private void resize(VkCommandBuffer commandBuffer, long newCapacity) {
        if (this.used > newCapacity) {
            throw new UnsupportedOperationException("New capacity must be larger than used size");
        }

        this.checkAssertions();

        long tail = newCapacity - this.used;
        List<VkBufferSegment> usedSegments = this.getUsedSegments();
        List<PendingBufferCopyCommand> pendingCopies = this.buildTransferList(usedSegments, tail);

        this.transferSegments(commandBuffer, pendingCopies, newCapacity);

        this.head = new VkBufferSegment(this, 0, tail);
        this.head.setFree(true);

        if (usedSegments.isEmpty()) {
            this.head.setNext(null);
        } else {
            this.head.setNext(usedSegments.get(0));
            this.head.getNext().setPrev(this.head);
        }

        this.checkAssertions();
    }

    private List<PendingBufferCopyCommand> buildTransferList(List<VkBufferSegment> usedSegments, long base) {
        List<PendingBufferCopyCommand> pendingCopies = new ArrayList<>();
        PendingBufferCopyCommand currentCopyCommand = null;
        long writeOffset = base;

        for (int i = 0; i < usedSegments.size(); i++) {
            VkBufferSegment segment = usedSegments.get(i);

            if (currentCopyCommand == null || currentCopyCommand.getReadOffset() + currentCopyCommand.getLength() != segment.getOffset()) {
                if (currentCopyCommand != null) {
                    pendingCopies.add(currentCopyCommand);
                }

                currentCopyCommand = new PendingBufferCopyCommand(segment.getOffset(), writeOffset, segment.getLength());
            } else {
                currentCopyCommand.setLength(currentCopyCommand.getLength() + segment.getLength());
            }

            segment.setOffset(writeOffset);
            segment.setNext(i + 1 < usedSegments.size() ? usedSegments.get(i + 1) : null);
            segment.setPrev(i > 0 ? usedSegments.get(i - 1) : null);
            writeOffset += segment.getLength();
        }

        if (currentCopyCommand != null) {
            pendingCopies.add(currentCopyCommand);
        }

        return pendingCopies;
    }

    private void transferSegments(VkCommandBuffer commandBuffer, Collection<PendingBufferCopyCommand> commands, long newCapacity) {
        long bufferSize = newCapacity * this.stride;
        if (bufferSize >= (1L << 32)) {
            throw new IllegalArgumentException("Maximum arena buffer size is 4 GiB");
        }

        VmaBuffer srcBuffer = this.arenaBuffer;
        VmaBuffer dstBuffer = getBufferOfSizeAtLeast(Math.max(1L, bufferSize));

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            for (PendingBufferCopyCommand command : commands) {
                copyRegion.srcOffset(command.getReadOffset() * this.stride);
                copyRegion.dstOffset(command.getWriteOffset() * this.stride);
                copyRegion.size(command.getLength() * this.stride);
                vkCmdCopyBuffer(commandBuffer, srcBuffer.buffer(), dstBuffer.buffer(), copyRegion);
            }
        }

        releaseBufferForReuse(srcBuffer);
        this.arenaBuffer = dstBuffer;
        this.capacity = this.arenaBuffer.size() / this.stride;
    }

    private static synchronized VmaBuffer getBufferOfSizeAtLeast(long size) {
        VmaBuffer buffer = null;

        if (freeBufferCount > 0) {
            long maxAcceptableSize = (long) (size * MAX_BUFFER_REUSE_SIZE_FACTOR);
            int candidateIndex = -1;

            for (int i = 0; i < freeBuffers.length; i++) {
                VmaBuffer freeBuffer = freeBuffers[i];
                if (freeBuffer == null) {
                    continue;
                }

                long testSize = freeBuffer.size();
                if (testSize >= size && testSize <= maxAcceptableSize &&
                        (buffer == null || testSize < buffer.size())) {
                    candidateIndex = i;
                    buffer = freeBuffer;
                }
            }

            if (candidateIndex >= 0) {
                freeBuffers[candidateIndex] = null;
                freeBufferCount--;
            }
        }

        return buffer != null ? buffer : createBuffer(size, BUFFER_USAGE, VMA_MEMORY_USAGE_GPU_ONLY);
    }

    private static synchronized void releaseBufferForReuse(VmaBuffer buffer) {
        pendingDelete.add(buffer);
    }

    private static void addFreeBuffer(VmaBuffer buffer) {
        if (freeBufferCount < freeBuffers.length) {
            for (int i = 0; i < freeBuffers.length; i++) {
                if (freeBuffers[i] == null) {
                    freeBuffers[i] = buffer;
                    freeBufferCount++;
                    return;
                }
            }
        }

        int evictIndex = ThreadLocalRandom.current().nextInt(freeBuffers.length);
        destroyBuffer(freeBuffers[evictIndex]);
        freeBuffers[evictIndex] = buffer;
    }

    private static VmaBuffer createBuffer(long size, int usage, int memoryUsage) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(memoryUsage);

            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);

            int result = vmaCreateBuffer(VulkanContext.INSTANCE.vmaAllocator(), bufferInfo, allocationInfo, pBuffer, pAllocation, null);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vmaCreateBuffer failed " + result);
            }

            return new VmaBuffer(pBuffer.get(0), pAllocation.get(0), size);
        }
    }

    private static void destroyBuffer(VmaBuffer buffer) {
        vmaDestroyBuffer(VulkanContext.INSTANCE.vmaAllocator(), buffer.buffer(), buffer.allocation());
    }

    private ArrayList<VkBufferSegment> getUsedSegments() {
        ArrayList<VkBufferSegment> usedSegments = new ArrayList<>();
        VkBufferSegment segment = this.head;

        while (segment != null) {
            VkBufferSegment next = segment.getNext();
            if (!segment.isFree()) {
                usedSegments.add(segment);
            }
            segment = next;
        }

        return usedSegments;
    }

    private void updateUsed(long deltaUsed) {
        this.used += deltaUsed;
        this.segmentCount += Long.signum(deltaUsed);
    }

    private VkBufferSegment alloc(int size) {
        VkBufferSegment entry = this.findFree(size);

        if (entry == null) {
            return null;
        }

        VkBufferSegment result;

        if (entry.getLength() == size) {
            entry.setFree(false);
            result = entry;
        } else {
            VkBufferSegment split = new VkBufferSegment(this, entry.getEnd() - size, size);
            split.setNext(entry.getNext());
            split.setPrev(entry);

            if (split.getNext() != null) {
                split.getNext().setPrev(split);
            }

            entry.setLength(entry.getLength() - size);
            entry.setNext(split);
            result = split;
        }

        this.updateUsed(result.getLength());
        this.checkAssertions();
        return result;
    }

    private VkBufferSegment findFree(int size) {
        VkBufferSegment entry = this.head;
        VkBufferSegment best = null;

        while (entry != null) {
            if (entry.isFree()) {
                if (entry.getLength() == size) {
                    return entry;
                }

                if (entry.getLength() >= size && (best == null || best.getLength() > entry.getLength())) {
                    best = entry;
                }
            }

            entry = entry.getNext();
        }

        return best;
    }

    private void tryUploads(VkCommandBuffer commandBuffer, List<PendingUpload> queue) {
        queue.removeIf(upload -> this.tryUpload(commandBuffer, upload));
        this.stagingBuffer.flush(commandBuffer);
    }

    private boolean tryUpload(VkCommandBuffer commandBuffer, PendingUpload upload) {
        ByteBuffer data = upload.getDataBuffer().getDirectBuffer();
        int elementCount = data.remaining() / this.stride;
        VkBufferSegment destination = this.alloc(elementCount);

        if (destination == null) {
            return false;
        }

        this.stagingBuffer.enqueueCopy(commandBuffer, data, this.arenaBuffer.buffer(), destination.getOffset() * this.stride);
        upload.setResult(destination);
        return true;
    }

    private long estimateNewCapacity(float regionFillFractionInv, List<PendingUpload> queue) {
        long requiredTotalSize = getRequiredTotalSize(queue);
        int newSegmentCount = this.segmentCount + queue.size();
        long newCapacity;

        if (newSegmentCount >= MIN_SEGMENTS_FOR_AVG) {
            long averageNewSegmentSize = (requiredTotalSize / newSegmentCount) + 1;
            float expectedSegmentCount = newSegmentCount * regionFillFractionInv;
            newCapacity = (long) (averageNewSegmentSize * expectedSegmentCount * EXPECTED_SIZE_TARGET_FACTOR);
        } else {
            newCapacity = (long) (requiredTotalSize * FEW_SEGMENTS_GROWTH_FACTOR);
        }

        return Math.max(requiredTotalSize, newCapacity);
    }

    private long getRequiredTotalSize(List<PendingUpload> queue) {
        long remainingUploadSize = 0;

        for (var upload : queue) {
            remainingUploadSize += upload.getDataBuffer().getLength();
        }

        long remainingElements = remainingUploadSize / this.stride;
        return remainingElements + this.used;
    }

    private void checkAssertions() {
        if (CHECK_ASSERTIONS) {
            this.checkAssertions0();
        }
    }

    private void checkAssertions0() {
        VkBufferSegment segment = this.head;
        long measuredUsed = 0;

        while (segment != null) {
            if (segment.getOffset() < 0) {
                throw new IllegalStateException("segment.start < 0: out of bounds");
            }
            if (segment.getEnd() > this.capacity) {
                throw new IllegalStateException("segment.end > arena.capacity: out of bounds");
            }

            if (!segment.isFree()) {
                measuredUsed += segment.getLength();
            }

            VkBufferSegment next = segment.getNext();
            if (next != null) {
                if (next.getOffset() < segment.getEnd()) {
                    throw new IllegalStateException("segment.next.start < segment.end: overlapping segments");
                }
                if (next.getOffset() > segment.getEnd()) {
                    throw new IllegalStateException("segment.next.start > segment.end: sparsity error");
                }
                if (next.isFree() && next.getNext() != null && next.getNext().isFree()) {
                    throw new IllegalStateException("consecutive free segments are not merged");
                }
            }

            VkBufferSegment prev = segment.getPrev();
            if (prev != null) {
                if (prev.getEnd() > segment.getOffset()) {
                    throw new IllegalStateException("segment.prev.end > segment.start: overlapping segments");
                }
                if (prev.getEnd() < segment.getOffset()) {
                    throw new IllegalStateException("segment.prev.end < segment.start: sparsity error");
                }
                if (prev.isFree() && prev.getPrev() != null && prev.getPrev().isFree()) {
                    throw new IllegalStateException("consecutive free segments are not merged");
                }
            }

            segment = next;
        }

        if (this.used < 0) {
            throw new IllegalStateException("arena.used < 0");
        }
        if (this.used > this.capacity) {
            throw new IllegalStateException("arena.used > arena.capacity");
        }
        if (this.used != measuredUsed) {
            throw new IllegalStateException("arena.used is invalid");
        }
    }

    private record VmaBuffer(long buffer, long allocation, long size) {
    }
}
