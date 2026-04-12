package net.caffeinemc.mods.sodium.client.render.chunk;

import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBufferUsages;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkMapping;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkMappingType;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import net.caffeinemc.mods.sodium.client.vk.util.EnumBitField;
import org.lwjgl.system.MemoryUtil;

public class PageAddressBuffer {
    private VkBuffer[] buffers = new VkBuffer[3];
    private VkMapping[] mappings = new VkMapping[3];

    public PageAddressBuffer(CommandList commandList) {
        for (int i = 0; i < 3; i++) {
            buffers[i] = commandList.createBuffer("PAB " + i, Long.BYTES * 256, VkMappingType.CPU_MAPPABLE, EnumBitField.of(VkBufferUsages.STORAGE_BUFFER, VkBufferUsages.UNIFORM_BUFFER, VkBufferUsages.SHADER_DEVICE_ADDRESS, VkBufferUsages.TRANSFER_DST));
            mappings[i] = commandList.mapBuffer(buffers[i], 0, Long.BYTES * 256);
        }
    }

    private int frame = 0;

    public void updateForFrame(long[] addresses) {
        frame = (frame + 1) % 3;
        for (int i = 0; i < addresses.length; i++) {
            MemoryUtil.memPutLong(mappings[frame].getMappedData() + (i * Long.BYTES), addresses[i]);
        }

    }

    public VkBuffer getCurrent() {
        return buffers[frame];
    }

    public void destroy(CommandList commandList) {
        for (int i = 0; i < 3; i++) {
            commandList.unmap(mappings[i]);
            commandList.deleteBuffer(buffers[i]);
        }
    }
}
