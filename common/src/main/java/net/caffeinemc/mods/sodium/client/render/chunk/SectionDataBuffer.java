package net.caffeinemc.mods.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBufferUsages;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkMappingType;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import net.caffeinemc.mods.sodium.client.vk.util.EnumBitField;
import org.lwjgl.system.MemoryUtil;

public class SectionDataBuffer {
    private static final int SIZE = 24;
    private final VkBuffer staging;
    private final VkBuffer[] buffer = new VkBuffer[3];
    private final IntList[] toReload = new IntList[3];
    private final long stagingAddr;
    private final int bufferSize;

    public SectionDataBuffer(CommandList commandList, int renderDistance, int minSectionY, int maxSectionY) {
        int diameter = 2 * renderDistance + 1;
        int chunkColumns = diameter * diameter;

        int sectionsPerColumn = maxSectionY - minSectionY + 1;

        int totalSections = chunkColumns * sectionsPerColumn;

        int size = totalSections * SIZE;
        this.bufferSize = size;

        this.staging = commandList.createBuffer(size, VkMappingType.CPU_ONLY, EnumBitField.of(VkBufferUsages.TRANSFER_SRC));
        this.stagingAddr = staging.getMapping().getMappedData();

        for (int i = 0; i < 3; i++) {
            toReload[i] = new IntArrayList();
            this.buffer[i] = commandList.createBuffer(size, VkMappingType.GPU_ONLY, EnumBitField.of(VkBufferUsages.TRANSFER_DST, VkBufferUsages.STORAGE_BUFFER, VkBufferUsages.SHADER_DEVICE_ADDRESS));
        }
    }

    public void writeSection(int sectionId, long addr, int[] segmentOffsets) {
        MemoryUtil.memPutLong(stagingAddr + ((long) sectionId * SIZE), addr);
        for (int i = 0; i < 7; i++) {
            MemoryUtil.memPutShort(stagingAddr + ((long) sectionId * SIZE) + 8 + (i * Short.BYTES), (short) segmentOffsets[i]);
        }
        for (int i = 0; i < 3; i++) {
            toReload[i].add(sectionId);
        }
    }

    public void updateSection(int sectionId, long newAddr) {
        MemoryUtil.memPutLong(stagingAddr + ((long) sectionId * SIZE), newAddr);
        for (int i = 0; i < 3; i++) {
            toReload[i].add(sectionId);
        }
    }

    public void removeSection(int sectionId) {
        MemoryUtil.memPutLong(stagingAddr + ((long) sectionId * SIZE), 0);
        for (int i = 0; i < 3; i++) {
            toReload[i].removeInt(sectionId);
        }
    }

    private int frame = 0;

    public void update(CommandList commandList) {
        frame = (frame + 1) % 3;

        if (!toReload[frame].isEmpty()) {
            // TODO: I was going to do something smart here
            commandList.copyBufferToBuffer(staging, buffer[frame], 0, 0, bufferSize);
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