package net.caffeinemc.mods.sodium.client.vk.device;

import net.caffeinemc.mods.sodium.client.vk.VulkanContext;
import net.caffeinemc.mods.sodium.client.vk.commands.CommandList;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_CPU_TO_GPU;
import static org.lwjgl.util.vma.Vma.vmaCreateBuffer;
import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;
import static org.lwjgl.util.vma.Vma.vmaFlushAllocation;
import static org.lwjgl.util.vma.Vma.vmaMapMemory;
import static org.lwjgl.util.vma.Vma.vmaUnmapMemory;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public final class VkBufferStreamer {
    private final long buffer;
    private final long allocation;
    private final long writeAddress;

    private final int stride;
    private final long bufferSize;
    private boolean requiresFlush;
    private boolean deleted;

    public VkBufferStreamer(int initialCapacity, int stride) {
        this.stride = stride;
        this.bufferSize = Math.max(1L, (long) initialCapacity * stride);

        BufferAlloc alloc = createMappedBuffer(this.bufferSize);
        this.buffer = alloc.buffer();
        this.allocation = alloc.allocation();
        this.writeAddress = alloc.mappedAddress();

        // Default to -1 so chunk fade stays disabled until populated.
        MemoryUtil.memSet(this.writeAddress, (byte) 0xFF, this.bufferSize);
        this.requiresFlush = true;
    }

    public void writeData(int index, int value) {
        long offset = (long) index * this.stride;

        if (offset < 0 || offset + this.stride > this.bufferSize) {
            throw new IndexOutOfBoundsException("Attempted to write beyond the end of the buffer streamer");
        }

        MemoryUtil.memPutInt(this.writeAddress + offset, value);
        this.requiresFlush = true;
    }

    public long prepare(CommandList commandList) {
        if (this.requiresFlush) {
            this.requiresFlush = false;
            vmaFlushAllocation(VulkanContext.INSTANCE.vmaAllocator(), this.allocation, 0, this.bufferSize);
        }

        return this.buffer;
    }

    public void delete(CommandList commandList) {
        if (this.deleted) {
            return;
        }

        this.deleted = true;
        vmaUnmapMemory(VulkanContext.INSTANCE.vmaAllocator(), this.allocation);
        VulkanContext.INSTANCE.destroyLater(this.buffer, this.allocation);
    }

    private static BufferAlloc createMappedBuffer(long size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_CPU_TO_GPU);

            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);

            int createResult = vmaCreateBuffer(VulkanContext.INSTANCE.vmaAllocator(), bufferInfo, allocationInfo, pBuffer, pAllocation, null);
            if (createResult != VK_SUCCESS) {
                throw new IllegalStateException("vmaCreateBuffer failed: " + createResult);
            }

            PointerBuffer pData = stack.mallocPointer(1);
            int mapResult = vmaMapMemory(VulkanContext.INSTANCE.vmaAllocator(), pAllocation.get(0), pData);
            if (mapResult != VK_SUCCESS) {
                vmaDestroyBuffer(VulkanContext.INSTANCE.vmaAllocator(), pBuffer.get(0), pAllocation.get(0));
                throw new IllegalStateException("vmaMapMemory failed: " + mapResult);
            }

            return new BufferAlloc(pBuffer.get(0), pAllocation.get(0), pData.get(0));
        }
    }

    private record BufferAlloc(long buffer, long allocation, long mappedAddress) {
    }
}
