package net.caffeinemc.mods.sodium.client.vk.device;

import net.caffeinemc.mods.sodium.client.util.UInt32;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.EXTMultiDraw;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkMultiDrawIndexedInfoEXT;

public final class MultiDrawBatch {
    public final long pCommands;

    public int size;
    public boolean isFilled;

    private final int capacity;
    private static final boolean supportsMultiDrawExt = true;

    public MultiDrawBatch(int capacity) {
        this.capacity = capacity;

        this.pCommands = MemoryUtil.nmemAlignedAlloc(32, (long) capacity * VkMultiDrawIndexedInfoEXT.SIZEOF);

        MemoryUtil.memSet(this.pCommands, 0, (long) capacity * VkMultiDrawIndexedInfoEXT.SIZEOF);
    }

    public void pushCommand(long indexCount, long firstIndex, long vertexOffset) {
        if (this.size >= this.capacity) {
            throw new IndexOutOfBoundsException("out of space :(");
        }

        this.setCommand(this.size++, indexCount, firstIndex, vertexOffset);
        this.isFilled = false;
    }

    public void clear() {
        this.size = 0;
        this.isFilled = false;
    }

    public void delete() {
        MemoryUtil.nmemAlignedFree(this.pCommands);
    }

    public boolean isEmpty() {
        return this.size <= 0;
    }

    public int getIndexBufferSize() {
        int elements = 0;

        for (int i = 0; i < this.size; i++) {
            long base = this.pCommands + ((long) i * VkMultiDrawIndexedInfoEXT.SIZEOF);

            int indexCount = MemoryUtil.memGetInt(base + VkMultiDrawIndexedInfoEXT.INDEXCOUNT);
            int firstIndex = MemoryUtil.memGetInt(base + VkMultiDrawIndexedInfoEXT.FIRSTINDEX);

            elements = Math.max(elements, indexCount + firstIndex);
        }

        return elements;
    }

    public void run(VkCommandBuffer commandBuffer) {
        if (this.size <= 0) {
            return;
        }

        if (this.supportsMultiDrawExt) {
            EXTMultiDraw.nvkCmdDrawMultiIndexedEXT(commandBuffer, this.size, this.pCommands, 1, 0, VkMultiDrawIndexedInfoEXT.SIZEOF, 0);
        } else {
            runFallback(commandBuffer);
        }
    }

    private void runFallback(VkCommandBuffer commandBuffer) {
        for (int i = 0; i < this.size; i++) {
            long base = this.pCommands + ((long) i * VkMultiDrawIndexedInfoEXT.SIZEOF);

            int indexCount = MemoryUtil.memGetInt(base + VkMultiDrawIndexedInfoEXT.INDEXCOUNT);
            int firstIndex = MemoryUtil.memGetInt(base + VkMultiDrawIndexedInfoEXT.FIRSTINDEX);
            int vertexOffset = MemoryUtil.memGetInt(base + VkMultiDrawIndexedInfoEXT.VERTEXOFFSET);

            VK10.vkCmdDrawIndexed(commandBuffer, indexCount, 1, firstIndex, vertexOffset, 0);
        }
    }

    private void setCommand(int index, long indexCount, long firstIndex, long vertexOffset) {
        if (index < 0 || index >= this.capacity) {
            throw new IndexOutOfBoundsException("Command index out of bounds");
        }

        long base = this.pCommands + ((long) index * VkMultiDrawIndexedInfoEXT.SIZEOF);

        MemoryUtil.memPutInt(base + VkMultiDrawIndexedInfoEXT.INDEXCOUNT, UInt32.downcast(indexCount));
        MemoryUtil.memPutInt(base + VkMultiDrawIndexedInfoEXT.FIRSTINDEX, UInt32.downcast(firstIndex));
        MemoryUtil.memPutInt(base + VkMultiDrawIndexedInfoEXT.VERTEXOFFSET, UInt32.uncheckedDowncast(vertexOffset));
    }
}
