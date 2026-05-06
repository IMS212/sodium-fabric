package net.caffeinemc.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.caffeinemc.mods.sodium.mixin.core.GlBufferAccessor;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.function.Supplier;

public class MappableRingWrapper implements AutoCloseable {
    private final int size;
    private final long writeAddr;
    private final MappableRingBuffer buffer;

    private final int[] beginFlush = new int[3];
    private final int[] endFlush = new int[3];

    private int frame;

    public MappableRingWrapper(Supplier<String> o, int i, int size) {
        this.size = size;
        this.buffer = new MappableRingBuffer(o, i, size);
        this.writeAddr = MemoryUtil.nmemAlloc(size);
        Arrays.fill(beginFlush, -1);
    }

    @Override
    public void close() {
        buffer.close();
        MemoryUtil.nmemFree(writeAddr);
    }

    public void rotate() {
        buffer.rotate();
        frame = (frame + 1) % 3;
        if (beginFlush[frame] >= 0) {
            try (GpuBufferSlice.MappedView view = this.buffer.currentBuffer().map(false, true)) {
                MemoryUtil.memCopy(writeAddr + beginFlush[frame], MemoryUtil.memAddress(view.data(), beginFlush[frame]),
                        endFlush[frame] - beginFlush[frame]);
            }
            beginFlush[frame] = -1;
        }
    }

    public GpuBuffer currentBuffer() {
        return buffer.currentBuffer();
    }

    public void write(int pos, long key) {
        if (pos > size) throw new IndexOutOfBoundsException(pos + " > " + size);
        if (pos < 0) throw new IndexOutOfBoundsException(pos + " < 0");

        MemoryUtil.memPutLong(writeAddr + pos, key);

        for (int i = 0; i < 3; i++) {
            if (beginFlush[i] == -1) {
                beginFlush[i] = pos;
            } else {
                beginFlush[i] = Math.min(beginFlush[i], pos);
            }

            endFlush[i] = Math.max(endFlush[i], pos + Long.BYTES);
        }
    }
}
