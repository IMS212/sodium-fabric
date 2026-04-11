package net.caffeinemc.mods.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBufferUsages;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkMappingType;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import net.caffeinemc.mods.sodium.client.vk.util.EnumBitField;
import org.lwjgl.system.MemoryUtil;

// struct SectionData { // 64 bytes, ScalarDataLayout
//   uint64_t vertexAddr;     // [0]
//   int      originX, Y, Z;  // [8]
//   uint     sliceMask;       // [20]
//   uint     vertexCount[7];  // [24]
//   uint     facingListLo;    // [52]
//   uint     facingListHi;    // [56]
//   uint     _pad;            // [60]
// };
public class SectionDataBuffer {
    private static final int SIZE = 64;

    private static final int OFFSET_VERTEX_ADDR = 0;
    private static final int OFFSET_ORIGIN_X = 8;
    private static final int OFFSET_ORIGIN_Y = 12;
    private static final int OFFSET_ORIGIN_Z = 16;
    private static final int OFFSET_SLICE_MASK = 20;
    private static final int OFFSET_VERTEX_COUNTS = 24;
    private static final int OFFSET_FACING_LIST_LO = 52;
    private static final int OFFSET_FACING_LIST_HI = 56;

    private final VkBuffer staging;
    private final VkBuffer[] buffer = new VkBuffer[3];
    private final IntList[] toReload = new IntList[3];
    private final long stagingAddr;
    private final int bufferSize;

    public SectionDataBuffer(CommandList commandList, int renderDistance, int minSectionY, int maxSectionY) {
        int diameter = 2 * renderDistance + 1;
        int totalSections = diameter * diameter * (maxSectionY - minSectionY + 1);

        int size = totalSections * SIZE;
        this.bufferSize = size;

        this.staging = commandList.createBuffer(size, VkMappingType.CPU_ONLY, EnumBitField.of(VkBufferUsages.TRANSFER_SRC));
        this.stagingAddr = staging.getMapping().getMappedData();
        MemoryUtil.memSet(stagingAddr, 0, size);

        for (int i = 0; i < 3; i++) {
            toReload[i] = new IntArrayList();
            this.buffer[i] = commandList.createBuffer(size, VkMappingType.GPU_ONLY, EnumBitField.of(VkBufferUsages.TRANSFER_DST, VkBufferUsages.STORAGE_BUFFER, VkBufferUsages.SHADER_DEVICE_ADDRESS));
        }
    }

    public void writeSection(int sectionId, long addr, int[] vertexCounts, long facingList, int sliceMask,
                             int originX, int originY, int originZ) {
        long base = stagingAddr + ((long) sectionId * SIZE);

        MemoryUtil.memPutLong(base + OFFSET_VERTEX_ADDR, addr);
        MemoryUtil.memPutInt(base + OFFSET_ORIGIN_X, originX);
        MemoryUtil.memPutInt(base + OFFSET_ORIGIN_Y, originY);
        MemoryUtil.memPutInt(base + OFFSET_ORIGIN_Z, originZ);
        MemoryUtil.memPutInt(base + OFFSET_SLICE_MASK, sliceMask);

        for (int i = 0; i < 7; i++) {
            MemoryUtil.memPutInt(base + OFFSET_VERTEX_COUNTS + (i * Integer.BYTES), vertexCounts[i]);
        }

        MemoryUtil.memPutInt(base + OFFSET_FACING_LIST_LO, (int) (facingList & 0xFFFFFFFFL));
        MemoryUtil.memPutInt(base + OFFSET_FACING_LIST_HI, (int) ((facingList >>> 32) & 0xFFFFFFFFL));

        for (int i = 0; i < 3; i++) {
            toReload[i].add(sectionId);
        }
    }

    public void updateSection(int sectionId, long newAddr) {
        MemoryUtil.memPutLong(stagingAddr + ((long) sectionId * SIZE) + OFFSET_VERTEX_ADDR, newAddr);
        for (int i = 0; i < 3; i++) {
            toReload[i].add(sectionId);
        }
    }

    public void removeSection(int sectionId) {
        MemoryUtil.memSet(stagingAddr + ((long) sectionId * SIZE), 0, SIZE);
        for (int i = 0; i < 3; i++) {
            toReload[i].add(sectionId);
        }
    }

    private int frame = 0;

    public void update(CommandList commandList) {
        frame = (frame + 1) % 3;

        if (!toReload[frame].isEmpty()) {
            commandList.copyBufferToBuffer(staging, buffer[frame], 0, 0, bufferSize);
            toReload[frame].clear();
        }
    }

    public VkBuffer getBuffer() {
        return buffer[frame];
    }

    public void destroy(CommandList commandList) {
        for (VkBuffer vkBuffer : buffer) {
            commandList.deleteBuffer(vkBuffer);
        }

        commandList.deleteBuffer(staging);
    }
}
