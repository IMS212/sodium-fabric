package net.caffeinemc.mods.sodium.client.gl.arena.staging;

import com.mojang.blaze3d.systems.RenderSystem;
import graphics.cinnabar.core.b3d.CinnabarDevice;
import graphics.cinnabar.core.vk.memory.VkBuffer;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gl.functions.BufferStorageFunctions;
import net.caffeinemc.mods.sodium.client.gl.sync.GlFence;
import net.caffeinemc.mods.sodium.client.gl.util.EnumBitField;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.caffeinemc.mods.sodium.client.gl.buffer.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;

public class MappedStagingBuffer implements StagingBuffer {
    private static final EnumBitField<GlBufferStorageFlags> STORAGE_FLAGS =
            EnumBitField.of(GlBufferStorageFlags.PERSISTENT, GlBufferStorageFlags.CLIENT_STORAGE, GlBufferStorageFlags.MAP_WRITE);

    private static final EnumBitField<GlBufferMapFlags> MAP_FLAGS =
            EnumBitField.of(GlBufferMapFlags.PERSISTENT, GlBufferMapFlags.INVALIDATE_BUFFER, GlBufferMapFlags.WRITE, GlBufferMapFlags.EXPLICIT_FLUSH);

    private MappedBuffer mappedBuffer;
    private final PriorityQueue<CopyCommand> pendingCopies = new ObjectArrayFIFOQueue<>();
    private final PriorityQueue<FencedMemoryRegion> fencedRegions = new ObjectArrayFIFOQueue<>();

    private int start = 0;
    private int pos = 0;

    private final int capacity;
    private int remaining;

    public MappedStagingBuffer(CommandList commandList) {
        this(commandList, 1024 * 1024 * 16 /* 16 MB */);
    }

    public MappedStagingBuffer(CommandList commandList, int capacity) {
        VkBuffer buffer = new VkBuffer(SodiumClientMod.getDevice(), capacity, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, ((CinnabarDevice) RenderSystem.getDevice()).hostPersistentMemoryPool);

        this.mappedBuffer = new MappedBuffer(buffer, mapBuffer(buffer, 0, capacity));
        this.capacity = capacity;
        this.remaining = this.capacity;
    }

    private GlBufferMapping mapBuffer(VkBuffer buffer, int i, int capacity) {
        return new GlBufferMapping(buffer, MemoryUtil.memByteBuffer(buffer.allocation.cpu().hostPointer.pointer(), capacity));
    }

    public static boolean isSupported(RenderDevice instance) {
        return instance.getDeviceFunctions().getBufferStorageFunctions() != BufferStorageFunctions.NONE;
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, VkBuffer dst, long writeOffset) {
        int length = data.remaining();

        if (length > this.remaining) {
            this.resizeBuffer(length);

            return;
        }

        int remaining = this.capacity - this.pos;

        // Split the transfer in two if we have enough available memory at the end and start of the buffer
        if (length > remaining) {
            int split = length - remaining;

            this.addTransfer(data.slice(0, remaining), dst, this.pos, writeOffset);
            this.addTransfer(data.slice(remaining, split), dst, 0, writeOffset + remaining);

            this.pos = split;
        } else {
            this.addTransfer(data, dst, this.pos, writeOffset);
            this.pos += length;
        }

        this.remaining -= length;
    }

    private void resizeBuffer(int length) {
        VkBuffer b = mappedBuffer.buffer;
        VkBuffer buffer = new VkBuffer(SodiumClientMod.getDevice(), capacity, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, ((CinnabarDevice) RenderSystem.getDevice()).hostPersistentMemoryPool);
        SodiumClientMod.getCommandEncoder().copyBufferToBuffer(b, buffer);

        this.mappedBuffer = new MappedBuffer(buffer, mapBuffer(buffer, 0, capacity));
    }

    private void addTransfer(ByteBuffer data, VkBuffer dst, long readOffset, long writeOffset) {
        this.mappedBuffer.map.write(data, (int) readOffset);
        this.pendingCopies.enqueue(new CopyCommand(dst, readOffset, writeOffset, data.remaining()));
    }

    @Override
    public void flush(CommandList commandList) {
        if (this.pendingCopies.isEmpty()) {
            return;
        }

        VkCommandBuffer commandBuffer = SodiumClientMod.getCommandEncoder().commandPools.get(SodiumClientMod.getDevice().currentFrameIndex()).alloc("sodiumTransfer" + SodiumClientMod.getDevice().currentFrameIndex());

        try (var stack = MemoryStack.stackPush()) {
            VK10.vkBeginCommandBuffer(commandBuffer, VkCommandBufferBeginInfo.calloc(stack).sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO).flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT));
        }

        if (this.pos < this.start) {
            commandList.flushMappedRange(this.mappedBuffer.map, this.start, this.capacity - this.start);
            commandList.flushMappedRange(this.mappedBuffer.map, 0, this.pos);
        } else {
            commandList.flushMappedRange(this.mappedBuffer.map, this.start, this.pos - this.start);
        }

        int bytes = 0;
        long fence;
        try (final var stack = MemoryStack.stackPush()) {
            LongBuffer b = stack.callocLong(1);
            VK10.vkCreateFence(SodiumClientMod.getDevice().vkDevice, VkFenceCreateInfo.calloc(stack).sType$Default().flags(0), null, b);
            fence = b.get(0);
        }

        for (CopyCommand command : consolidateCopies(this.pendingCopies)) {
            bytes += command.bytes;

            try (final var stack = MemoryStack.stackPush()) {
                final var copyRange = VkBufferCopy.calloc(1, stack);
                copyRange.srcOffset(command.readOffset);
                copyRange.dstOffset(command.writeOffset);
                copyRange.size(command.bytes);
                vkCmdCopyBuffer(commandBuffer, this.mappedBuffer.buffer.handle, command.buffer.handle, copyRange);
            }
        }

        try (final var stack = MemoryStack.stackPush()) {
            VK10.vkEndCommandBuffer(commandBuffer);
            VK10.vkQueueSubmit(SodiumClientMod.getDevice().graphicsQueue, VkSubmitInfo.calloc(stack).sType$Default().pCommandBuffers(stack.pointers(commandBuffer)), fence);
        }

        this.fencedRegions.enqueue(new FencedMemoryRegion(fence, bytes));

        this.start = this.pos;
    }

    private static List<CopyCommand> consolidateCopies(PriorityQueue<CopyCommand> queue) {
        List<CopyCommand> merged = new ArrayList<>();
        CopyCommand last = null;

        while (!queue.isEmpty()) {
            CopyCommand command = queue.dequeue();

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

    @Override
    public void delete(CommandList commandList) {
        this.mappedBuffer.delete(commandList);
        this.pendingCopies.clear();
    }

    @Override
    public void flip() {
        while (!this.fencedRegions.isEmpty()) {
            var region = this.fencedRegions.first();
            var fence = region.fence();

            if (VK10.vkGetFenceStatus(SodiumClientMod.getDevice().vkDevice, fence) == VK10.VK_NOT_READY) {
                break;
            }

            VK10.vkDestroyFence(SodiumClientMod.getDevice().vkDevice, fence, null);

            this.fencedRegions.dequeue();
            this.remaining += region.length();
        }
    }

    private static final class CopyCommand {
        private final VkBuffer buffer;
        private final long readOffset;
        private final long writeOffset;

        private long bytes;

        private CopyCommand(VkBuffer buffer, long readOffset, long writeOffset, long bytes) {
            this.buffer = buffer;
            this.readOffset = readOffset;
            this.writeOffset = writeOffset;
            this.bytes = bytes;
        }

        public CopyCommand(CopyCommand command) {
            this.buffer = command.buffer;
            this.writeOffset = command.writeOffset;
            this.readOffset = command.readOffset;
            this.bytes = command.bytes;
        }
    }

    private record MappedBuffer(VkBuffer buffer,
                                GlBufferMapping map) {
        public void delete(CommandList commandList) {
            SodiumClientMod.getDevice().destroyEndOfFrame(this.buffer);
        }
    }

    private record FencedMemoryRegion(long fence, int length) {

    }

    @Override
    public String toString() {
        return "Mapped (%s/%s MiB)".formatted(MathUtil.toMib(this.remaining), MathUtil.toMib(this.capacity));
    }
}
