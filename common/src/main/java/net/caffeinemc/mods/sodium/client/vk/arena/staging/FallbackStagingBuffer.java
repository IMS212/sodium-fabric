package net.caffeinemc.mods.sodium.client.vk.arena.staging;

import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBufferUsages;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkMappingType;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import net.caffeinemc.mods.sodium.client.vk.fence.VkFence;
import net.caffeinemc.mods.sodium.client.vk.util.EnumBitField;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.lwjgl.system.MemoryStack.stackPush;

public class FallbackStagingBuffer implements StagingBuffer {
    private final Deque<PendingUpload> pending = new ArrayDeque<>();

    public FallbackStagingBuffer(CommandList commandList) {
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, VkBuffer dstBuffer, long writeOffset) {
        int size = data.remaining();

        try (MemoryStack stack = stackPush()) {
            var buffer = commandList.createBuffer("Fallback staging buffer (temporary)", size, VkMappingType.CPU_ONLY, EnumBitField.of(VkBufferUsages.TRANSFER_SRC));
            long mapped = buffer.getMapping().getMappedData();
            MemoryUtil.memCopy(MemoryUtil.memAddress(data), mapped, size);

            commandList.flushMappedRange(buffer.getMapping(), 0, size);

            commandList.copyBufferToBuffer(buffer, dstBuffer, 0, writeOffset, size);

            VkFence fence = commandList.createFence();

            pending.add(new PendingUpload(buffer, fence));
        }
    }

    @Override
    public void flush(CommandList commandList) {
        // Each upload submits immediately.
    }

    @Override
    public void flip(CommandList commandList) {
        while (!pending.isEmpty()) {
            PendingUpload upload = pending.peekFirst();

            if (!upload.fence.returnIfReady()) break;

            commandList.deleteBuffer(upload.buffer);

            pending.pollFirst();
        }
    }

    @Override
    public void delete(CommandList commandList) {
        for (PendingUpload upload : pending) {
            commandList.deleteBuffer(upload.buffer);
        }
        pending.clear();
    }

    @Override
    public long getUploadSizeLimit(long frameDuration) {
        return Long.MAX_VALUE;
    }

    @Override
    public String toString() {
        return "Fallback";
    }

    private record PendingUpload(VkBuffer buffer, VkFence fence) { }
}