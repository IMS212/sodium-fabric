package net.caffeinemc.mods.sodium.client.vk.arena.staging;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.caffeinemc.mods.sodium.client.vk.VulkanContext;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaVirtualAllocationCreateInfo;
import org.lwjgl.util.vma.VmaVirtualBlockCreateInfo;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

public class MappedStagingBuffer implements StagingBuffer {
    private static final float UPLOAD_LIMIT_MARGIN = 0.8f;
    private static final int FRAMES_IN_FLIGHT = 3; // cinnabar
    private static final long ALIGNMENT = 16;

    private final long vkBuffer;
    private final long vmaAllocation;
    private final long mappedPtr;
    private final long capacity;

    private final long virtualBlock;

    private final List<CopyCommand> pendingCopies = new ArrayList<>();
    private final LongList pending = new LongArrayList();

    private final ArrayDeque<LongList> toDeleteLater = new ArrayDeque<>();
    private LongList allocatedThisFrame = new LongArrayList();

    public MappedStagingBuffer(long capacity) {
        this.capacity = capacity;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            var bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default().size(capacity).usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            var allocationInfo = VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_CPU_ONLY);

            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);

            int result = vmaCreateBuffer(VulkanContext.INSTANCE.vmaAllocator(), bufferInfo, allocationInfo, pBuffer, pAllocation, null);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("creating the buffer failed: " + result);
            }

            this.vkBuffer = pBuffer.get(0);
            this.vmaAllocation = pAllocation.get(0);

            PointerBuffer pData = stack.mallocPointer(1);
            result = vmaMapMemory(VulkanContext.INSTANCE.vmaAllocator(), this.vmaAllocation, pData);
            if (result != VK_SUCCESS) {
                vmaDestroyBuffer(VulkanContext.INSTANCE.vmaAllocator(), this.vkBuffer, this.vmaAllocation);
                throw new IllegalStateException("failed to map memory " + result);
            }

            this.mappedPtr = pData.get(0);

            var virtualBlockInfo = VmaVirtualBlockCreateInfo.calloc(stack)
                    .size(capacity)
                    .flags(0);

            PointerBuffer pVirtualBlock = stack.mallocPointer(1);
            result = vmaCreateVirtualBlock(virtualBlockInfo, pVirtualBlock);
            if (result != VK_SUCCESS) {
                vmaUnmapMemory(VulkanContext.INSTANCE.vmaAllocator(), this.vmaAllocation);
                vmaDestroyBuffer(VulkanContext.INSTANCE.vmaAllocator(), this.vkBuffer, this.vmaAllocation);
                throw new IllegalStateException("vmaCreateVirtualBlock failed, oom? " + result);
            }

            this.virtualBlock = pVirtualBlock.get(0);
        }
    }

    @Override
    public void enqueueCopy(VkCommandBuffer commandBuffer, ByteBuffer data, long dstBuffer, long writeOffset) {
        // todo: fix this stupid api
        long src = MemoryUtil.memAddress(data);
        int length = data.remaining();

        if (length <= 0) {
            return;
        }

        long offset = virtualAllocate(length);
        if (offset < 0) {
            flush(commandBuffer);
            offset = virtualAllocate(length);
            if (offset < 0) {
                throw new IllegalStateException("we're out of memory :(");
            }
        }

        MemoryUtil.memCopy(src, this.mappedPtr + offset, length);
        this.pendingCopies.add(new CopyCommand(dstBuffer, offset, writeOffset, length));
    }

    private long virtualAllocate(int size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var allocInfo = VmaVirtualAllocationCreateInfo.calloc(stack)
                    .size(size)
                    .alignment(ALIGNMENT);

            PointerBuffer pAllocation = stack.mallocPointer(1);
            LongBuffer pOffset = stack.mallocLong(1);

            int result = vmaVirtualAllocate(this.virtualBlock, allocInfo, pAllocation, pOffset);
            if (result != VK_SUCCESS) {
                return -1;
            }

            this.pending.add(pAllocation.get(0));
            return pOffset.get(0);
        }
    }

    @Override
    public void flush(VkCommandBuffer commandBuffer) {
        if (this.pendingCopies.isEmpty()) {
            return;
        }

        long minOffset = Long.MAX_VALUE;
        long maxEnd = 0;

        for (var copy : this.pendingCopies) {
            if (copy.readOffset < minOffset) minOffset = copy.readOffset;
            if (copy.readOffset + copy.bytes > maxEnd) maxEnd = copy.readOffset + copy.bytes;
        }

        vmaFlushAllocation(VulkanContext.INSTANCE.vmaAllocator(), this.vmaAllocation, minOffset, maxEnd - minOffset);

        var copies = consolidateCopies(this.pendingCopies);

        try (var stack = MemoryStack.stackPush()) {
            var region = VkBufferCopy.calloc(1, stack);

            for (var copy : copies) {
                region.srcOffset(copy.readOffset)
                        .dstOffset(copy.writeOffset)
                        .size(copy.bytes);

                vkCmdCopyBuffer(commandBuffer, this.vkBuffer, copy.buffer, region);
            }
        }

        this.allocatedThisFrame.addAll(this.pending);
        this.pending.clear();
        this.pendingCopies.clear();
    }

    @Override
    public void flip() {
        this.toDeleteLater.addLast(this.allocatedThisFrame);
        this.allocatedThisFrame = new LongArrayList();

        while (this.toDeleteLater.size() > FRAMES_IN_FLIGHT) {
            for (long allocation : this.toDeleteLater.removeFirst()) {
                vmaVirtualFree(this.virtualBlock, allocation);
            }
        }
    }

    @Override
    public void delete() {
        for (long allocation : this.pending) {
            vmaVirtualFree(this.virtualBlock, allocation);
        }
        this.pending.clear();

        for (long allocation : this.allocatedThisFrame) {
            vmaVirtualFree(this.virtualBlock, allocation);
        }
        this.allocatedThisFrame.clear();

        while (!this.toDeleteLater.isEmpty()) {
            for (long allocation : this.toDeleteLater.removeFirst()) {
                vmaVirtualFree(this.virtualBlock, allocation);
            }
        }

        this.pendingCopies.clear();

        vmaDestroyVirtualBlock(this.virtualBlock);
        vmaUnmapMemory(VulkanContext.INSTANCE.vmaAllocator(), this.vmaAllocation);
        vmaDestroyBuffer(VulkanContext.INSTANCE.vmaAllocator(), this.vkBuffer, this.vmaAllocation);
    }

    @Override
    public long getUploadSizeLimit(long frameDuration) {
        return (long) (this.capacity * UPLOAD_LIMIT_MARGIN);
    }

    @Override
    public String toString() {
        return "Mapped (%s MiB)".formatted(MathUtil.toMib(this.capacity));
    }

    private static List<CopyCommand> consolidateCopies(List<CopyCommand> queue) {
        List<CopyCommand> merged = new ArrayList<>();
        CopyCommand last = null;

        for (var command : queue) {
            if (last != null) {
                if (last.buffer == command.buffer &&
                        last.writeOffset + last.bytes == command.writeOffset &&
                        last.readOffset + last.bytes == command.readOffset) {
                    last.bytes += command.bytes;
                    continue;
                }
            }

            merged.add(last = new CopyCommand(command));
        }

        return merged;
    }

    private static final class CopyCommand {
        private final long buffer;
        private final long readOffset;
        private final long writeOffset;
        private long bytes;

        private CopyCommand(long buffer, long readOffset, long writeOffset, long bytes) {
            this.buffer = buffer;
            this.readOffset = readOffset;
            this.writeOffset = writeOffset;
            this.bytes = bytes;
        }

        private CopyCommand(CopyCommand other) {
            this.buffer = other.buffer;
            this.readOffset = other.readOffset;
            this.writeOffset = other.writeOffset;
            this.bytes = other.bytes;
        }
    }
}