package net.caffeinemc.mods.sodium.client.vk.device;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkMultiDrawIndexedInfoEXT;

import java.nio.ByteBuffer;

/**
 * Provides a fixed-size queue for building a draw-command list usable with
 * {@link org.lwjgl.opengl.GL33C#glMultiDrawElementsBaseVertex(int, java.nio.IntBuffer, int, org.lwjgl.PointerBuffer, java.nio.IntBuffer)}.
 */
public final class MultiDrawBatch {
    public final long pIndexedInfos;

    private final int capacity;
    private final int stride;

    public int size;
    public boolean isFilled;

    public MultiDrawBatch(int capacity) {
        this.capacity = capacity;
        this.stride = VkMultiDrawIndexedInfoEXT.SIZEOF;

        this.pIndexedInfos = MemoryUtil.nmemAlignedAlloc(
                32,
                (long) capacity * this.stride
        );

        MemoryUtil.memSet(this.pIndexedInfos, 0x0, (long) capacity * this.stride);
    }

    public void clear() {
        this.size = 0;
        this.isFilled = false;
    }

    public void delete() {
        MemoryUtil.nmemAlignedFree(this.pIndexedInfos);
    }

    public boolean isEmpty() {
        return this.size <= 0;
    }

    public int getIndexBufferSize() {
        int elements = 0;

        for (int index = 0; index < this.size; index++) {
            long addr = this.pIndexedInfos + (long) index * this.stride;
            int count = VkMultiDrawIndexedInfoEXT.nindexCount(addr);
            elements = Math.max(elements, count);
        }

        return elements;
    }

    public void addDraw(int indexCount, int firstIndex, int vertexOffset) {
        if (this.size >= this.capacity) {
            throw new IllegalStateException("MultiDrawBatch capacity exceeded");
        }

        long addr = this.pIndexedInfos + (long) this.size * this.stride;

        VkMultiDrawIndexedInfoEXT.nindexCount(addr, indexCount);
        VkMultiDrawIndexedInfoEXT.nfirstIndex(addr, firstIndex);
        VkMultiDrawIndexedInfoEXT.nvertexOffset(addr, vertexOffset);

        this.size++;
    }

    public long address() {
        return this.pIndexedInfos;
    }

    public int drawCount() {
        return this.size;
    }

    public int stride() {
        return this.stride;
    }
}