package net.caffeinemc.mods.sodium.client.vk.arena.staging;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBufferUsages;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkMappingType;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import net.caffeinemc.mods.sodium.client.vk.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.vk.fence.VkFence;
import net.caffeinemc.mods.sodium.client.vk.util.EnumBitField;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MappedStagingBuffer implements StagingBuffer {
    private static final float UPLOAD_LIMIT_MARGIN = 0.8f;

    private final FallbackStagingBuffer fallbackStagingBuffer;

    private final PriorityQueue<CopyCommand> pendingCopies = new ObjectArrayFIFOQueue<>();
    private final PriorityQueue<FencedMemoryRegion> fencedRegions = new ObjectArrayFIFOQueue<>();

    private final VkBuffer buffer;
    private final long mapping;

    private int start = 0;
    private int pos = 0;

    private final int capacity;
    private int remaining;

    public MappedStagingBuffer(CommandList commandList) {
        this(commandList, (int) MathUtil.fromMib(16));
    }

    public MappedStagingBuffer(CommandList commandList, int capacity) {
        this.buffer = commandList.createBuffer(capacity, VkMappingType.CPU_ONLY, EnumBitField.of(VkBufferUsages.TRANSFER_SRC, VkBufferUsages.TRANSFER_DST));
        this.mapping = buffer.getMapping().getMappedData();

        this.fallbackStagingBuffer = new FallbackStagingBuffer(commandList);
        this.capacity = capacity;
        this.remaining = this.capacity;
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, VkBuffer dst, long writeOffset) {
        int length = data.remaining();

        if (length > this.remaining) {
            this.fallbackStagingBuffer.enqueueCopy(commandList, data, dst, writeOffset);

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

    private void addTransfer(ByteBuffer data, VkBuffer dst, long readOffset, long writeOffset) {
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), this.mapping + readOffset, data.remaining());
        this.pendingCopies.enqueue(new CopyCommand(dst, readOffset, writeOffset, data.remaining()));
    }

    @Override
    public void flush(CommandList commandList) {
        if (this.pendingCopies.isEmpty()) {
            return;
        }

        if (this.pos < this.start) {
            commandList.flushMappedRange(this.buffer.getMapping(), this.start, this.capacity - this.start);
            commandList.flushMappedRange(this.buffer.getMapping(), 0, this.pos);
        } else {
            commandList.flushMappedRange(this.buffer.getMapping(), this.start, this.pos - this.start);
        }

        int bytes = 0;

        for (CopyCommand command : consolidateCopies(this.pendingCopies)) {
            bytes += command.bytes;

            commandList.copyBufferToBuffer(this.buffer, command.buffer, command.readOffset, command.writeOffset, command.bytes);
        }

        this.fencedRegions.enqueue(new FencedMemoryRegion(commandList.createFence(), bytes));

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
        while (!this.fencedRegions.isEmpty()) {
            var region = this.fencedRegions.dequeue();
            var fence = region.fence();
            fence.sync();
        }

        commandList.deleteBuffer(buffer);
        this.fallbackStagingBuffer.delete(commandList);
        this.pendingCopies.clear();
    }

    @Override
    public void flip(CommandList commandList) {
        while (!this.fencedRegions.isEmpty()) {
            var region = this.fencedRegions.first();
            var fence = region.fence();

            if (!fence.returnIfReady()) {
                break;
            }

            this.fencedRegions.dequeue();
            this.remaining += region.length();
        }
    }

    @Override
    public long getUploadSizeLimit(long frameDuration) {
        return (long) (this.capacity * UPLOAD_LIMIT_MARGIN);
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

    private record FencedMemoryRegion(VkFence fence, int length) {

    }

    @Override
    public String toString() {
        return "Mapped (%s/%s MiB)".formatted(MathUtil.toMib(this.remaining), MathUtil.toMib(this.capacity));
    }
}
