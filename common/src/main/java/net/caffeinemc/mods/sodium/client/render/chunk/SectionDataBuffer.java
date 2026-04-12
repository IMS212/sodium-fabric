package net.caffeinemc.mods.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBufferUsages;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkMappingType;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import net.caffeinemc.mods.sodium.client.vk.util.EnumBitField;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;

public class SectionDataBuffer {
    private static final int SIZE = 64;
    private static final int MAX_PER_RUN = 512;

    private static final int OFFSET_VERTEX_ADDR = 0;
    private static final int OFFSET_ORIGIN_X = 8;
    private static final int OFFSET_ORIGIN_Y = 12;
    private static final int OFFSET_ORIGIN_Z = 16;
    private static final int OFFSET_SLICE_MASK = 20;
    private static final int OFFSET_VERTEX_COUNTS = 24;

    private final long masterAddr;

    private final VkBuffer[] staging = new VkBuffer[3];
    private final long[] stagingAddr = new long[3];
    private final VkBuffer[] buffer = new VkBuffer[3];
    private final IntSet[] dirtySections = new IntSet[3];
    private final int capacity;

    public int getCapacity() {
        return capacity;
    }

    public SectionDataBuffer(CommandList commandList, int renderDistance, int minSectionY, int maxSectionY) {
        int diameter = 2 * (renderDistance * 2 + 1) + 1;
        int sectionsByRd = diameter * diameter * (maxSectionY - minSectionY + 1);
        // TODO: I AM TIRED OF THIS RUNNING OUT
        int totalSections = Math.max(sectionsByRd, 1 << 18);

        this.capacity = totalSections;
        int size = totalSections * SIZE;

        this.masterAddr = MemoryUtil.nmemAlloc(size);
        MemoryUtil.memSet(masterAddr, 0, size);

        for (int i = 0; i < 3; i++) {
            this.dirtySections[i] = new IntOpenHashSet();
            this.staging[i] = commandList.createBuffer("staging section buffer " + i, size, VkMappingType.CPU_ONLY, EnumBitField.of(VkBufferUsages.TRANSFER_SRC));
            this.stagingAddr[i] = staging[i].getMapping().getMappedData();
            this.buffer[i] = commandList.createBuffer("section buffer " + i, size, VkMappingType.GPU_ONLY, EnumBitField.of(VkBufferUsages.TRANSFER_DST, VkBufferUsages.STORAGE_BUFFER, VkBufferUsages.SHADER_DEVICE_ADDRESS));
        }
    }

    public void writeSection(int sectionId, long addr, int[] vertexCounts, long facingList, int sliceMask,
                             int originX, int originY, int originZ) {
        if (sectionId >= capacity) {
            System.out.println("FUCK");
            return;
        }
        long base = masterAddr + ((long) sectionId * SIZE);

        MemoryUtil.memPutLong(base + OFFSET_VERTEX_ADDR, addr);
        MemoryUtil.memPutInt(base + OFFSET_ORIGIN_X, originX);
        MemoryUtil.memPutInt(base + OFFSET_ORIGIN_Y, originY);
        MemoryUtil.memPutInt(base + OFFSET_ORIGIN_Z, originZ);
        MemoryUtil.memPutInt(base + OFFSET_SLICE_MASK, sliceMask);

        for (int i = 0; i < 7; i++) {
            MemoryUtil.memPutInt(base + OFFSET_VERTEX_COUNTS + (i * Integer.BYTES), vertexCounts[i]);
        }

        markDirty(sectionId);
    }

    public void updateSection(int sectionId, long newAddr) {
        if (sectionId >= capacity) {
            System.out.println("FUCK");
            return;
        }
        MemoryUtil.memPutLong(masterAddr + ((long) sectionId * SIZE) + OFFSET_VERTEX_ADDR, newAddr);
        markDirty(sectionId);
    }

    public void removeSection(int sectionId) {
        if (sectionId >= capacity) {
            System.out.println("FUCK");
            return;
        }
        MemoryUtil.memSet(masterAddr + ((long) sectionId * SIZE), 0, SIZE);
        markDirty(sectionId);
    }

    private void markDirty(int sectionId) {
        dirtySections[0].add(sectionId);
        dirtySections[1].add(sectionId);
        dirtySections[2].add(sectionId);
    }

    private int frame = 0;

    public void update(CommandList commandList) {
        frame = (frame + 1) % 3;

        IntSet dirty = dirtySections[frame];
        int count = dirty.size();
        if (count == 0) {
            return;
        }

        VkCommandBuffer cmd = commandList.getCommandBuffer();
        long stageAddr = stagingAddr[frame];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer regions = VkBufferCopy.calloc(Math.min(count, MAX_PER_RUN), stack);
            int batchCount = 0;
            IntIterator it = dirty.iterator();
            while (it.hasNext()) {
                int sectionId = it.nextInt();
                long offset = (long) sectionId * SIZE;
                MemoryUtil.memCopy(masterAddr + offset, stageAddr + offset, SIZE);
                regions.get(batchCount++).srcOffset(offset).dstOffset(offset).size(SIZE);

                if (batchCount == regions.capacity()) {
                    regions.limit(batchCount);
                    VK13.vkCmdCopyBuffer(cmd, staging[frame].handle(), buffer[frame].handle(), regions);
                    regions.limit(regions.capacity());
                    batchCount = 0;
                }
            }

            if (batchCount > 0) {
                regions.limit(batchCount);
                VK13.vkCmdCopyBuffer(cmd, staging[frame].handle(), buffer[frame].handle(), regions);
            }
        }

        dirty.clear();
    }

    public VkBuffer getBuffer() {
        return buffer[frame];
    }

    public void destroy(CommandList commandList) {
        MemoryUtil.nmemFree(masterAddr);

        for (int i = 0; i < 3; i++) {
            commandList.deleteBuffer(staging[i]);
            commandList.deleteBuffer(buffer[i]);
        }
    }
}
