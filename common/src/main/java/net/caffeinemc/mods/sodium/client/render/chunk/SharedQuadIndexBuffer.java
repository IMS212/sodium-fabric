package net.caffeinemc.mods.sodium.client.render.chunk;

import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.caffeinemc.mods.sodium.client.vk.VulkanContext;
import net.caffeinemc.mods.sodium.client.vk.commands.CommandList;
import net.caffeinemc.mods.sodium.client.vk.tessellation.VkIndexType;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_CPU_TO_GPU;
import static org.lwjgl.util.vma.Vma.vmaCreateBuffer;
import static org.lwjgl.util.vma.Vma.vmaDestroyBuffer;
import static org.lwjgl.util.vma.Vma.vmaFlushAllocation;
import static org.lwjgl.util.vma.Vma.vmaMapMemory;
import static org.lwjgl.util.vma.Vma.vmaUnmapMemory;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

public class SharedQuadIndexBuffer {
    private static final int ELEMENTS_PER_PRIMITIVE = 6;
    private static final int VERTICES_PER_PRIMITIVE = 4;

    private VmaBuffer buffer;
    private final IndexType indexType;

    private int maxPrimitives;

    public SharedQuadIndexBuffer(IndexType indexType) {
        this.indexType = indexType;
    }

    public void ensureCapacity(CommandList commandList, int elementCount) {
        if (elementCount > this.indexType.getMaxElementCount()) {
            throw new IllegalArgumentException("Tried to reserve storage for more vertices in this buffer than it can hold");
        }

        int primitiveCount = elementCount / ELEMENTS_PER_PRIMITIVE;

        if (primitiveCount > this.maxPrimitives) {
            this.grow(commandList, this.getNextSize(primitiveCount));
        }
    }

    private int getNextSize(int primitiveCount) {
        return Math.min(Math.max(this.maxPrimitives * 2, primitiveCount + 16384), this.indexType.getMaxPrimitiveCount());
    }

    private void grow(CommandList commandList, int primitiveCount) {
        long bufferSize = (long) primitiveCount * this.indexType.getBytesPerElement() * ELEMENTS_PER_PRIMITIVE;
        VmaBuffer replacement = createBuffer(bufferSize);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            int mapResult = vmaMapMemory(VulkanContext.INSTANCE.vmaAllocator(), replacement.allocation(), pData);
            if (mapResult != VK_SUCCESS) {
                vmaDestroyBuffer(VulkanContext.INSTANCE.vmaAllocator(), replacement.buffer(), replacement.allocation());
                throw new IllegalStateException("vmaMapMemory failed: " + mapResult);
            }

            ByteBuffer mapped = MemoryUtil.memByteBuffer(pData.get(0), Math.toIntExact(bufferSize));
            this.indexType.createIndexBuffer(mapped, primitiveCount);

            vmaFlushAllocation(VulkanContext.INSTANCE.vmaAllocator(), replacement.allocation(), 0, bufferSize);
            vmaUnmapMemory(VulkanContext.INSTANCE.vmaAllocator(), replacement.allocation());
        }

        if (this.buffer != null) {
            VulkanContext.INSTANCE.destroyLater(this.buffer.buffer(), this.buffer.allocation());
        }

        this.buffer = replacement;
        this.maxPrimitives = primitiveCount;
    }

    public static NativeBuffer createIndexBuffer(IndexType indexType, int primitiveCount) {
        var bufferSize = primitiveCount * indexType.getBytesPerElement() * ELEMENTS_PER_PRIMITIVE;
        var buffer = new NativeBuffer(bufferSize);

        indexType.createIndexBuffer(buffer.getDirectBuffer(), primitiveCount);

        return buffer;
    }

    public long getBufferHandle() {
        return this.buffer != null ? this.buffer.buffer() : 0;
    }

    public void delete(CommandList commandList) {
        if (this.buffer != null) {
            VulkanContext.INSTANCE.destroyLater(this.buffer.buffer(), this.buffer.allocation());
            this.buffer = null;
            this.maxPrimitives = 0;
        }
    }

    private static VmaBuffer createBuffer(long size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(VK_BUFFER_USAGE_INDEX_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_CPU_TO_GPU);

            LongBuffer pBuffer = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);

            int createResult = vmaCreateBuffer(VulkanContext.INSTANCE.vmaAllocator(), bufferInfo, allocationInfo, pBuffer, pAllocation, null);
            if (createResult != VK_SUCCESS) {
                throw new IllegalStateException("vmaCreateBuffer failed: " + createResult);
            }

            return new VmaBuffer(pBuffer.get(0), pAllocation.get(0));
        }
    }

    public enum IndexType {
        SHORT(VkIndexType.UNSIGNED_SHORT, 64 * 1024) {
            @Override
            public void createIndexBuffer(ByteBuffer byteBuffer, int primitiveCount) {
                ShortBuffer shortBuffer = byteBuffer.asShortBuffer();

                for (int primitiveIndex = 0; primitiveIndex < primitiveCount; primitiveIndex++) {
                    int indexOffset = primitiveIndex * ELEMENTS_PER_PRIMITIVE;
                    int vertexOffset = primitiveIndex * VERTICES_PER_PRIMITIVE;

                    shortBuffer.put(indexOffset, (short) (vertexOffset));
                    shortBuffer.put(indexOffset + 1, (short) (vertexOffset + 1));
                    shortBuffer.put(indexOffset + 2, (short) (vertexOffset + 2));
                    shortBuffer.put(indexOffset + 3, (short) (vertexOffset + 2));
                    shortBuffer.put(indexOffset + 4, (short) (vertexOffset + 3));
                    shortBuffer.put(indexOffset + 5, (short) (vertexOffset));
                }
            }
        },
        INTEGER(VkIndexType.UNSIGNED_INT, Integer.MAX_VALUE) {
            @Override
            public void createIndexBuffer(ByteBuffer byteBuffer, int primitiveCount) {
                IntBuffer intBuffer = byteBuffer.asIntBuffer();

                for (int primitiveIndex = 0; primitiveIndex < primitiveCount; primitiveIndex++) {
                    int indexOffset = primitiveIndex * ELEMENTS_PER_PRIMITIVE;
                    int vertexOffset = primitiveIndex * VERTICES_PER_PRIMITIVE;

                    intBuffer.put(indexOffset, vertexOffset);
                    intBuffer.put(indexOffset + 1, vertexOffset + 1);
                    intBuffer.put(indexOffset + 2, vertexOffset + 2);
                    intBuffer.put(indexOffset + 3, vertexOffset + 2);
                    intBuffer.put(indexOffset + 4, vertexOffset + 3);
                    intBuffer.put(indexOffset + 5, vertexOffset);
                }
            }
        };

        public static final IndexType[] VALUES = IndexType.values();

        private final VkIndexType format;
        private final int maxElementCount;

        IndexType(VkIndexType format, int maxElementCount) {
            this.format = format;
            this.maxElementCount = maxElementCount;
        }

        public abstract void createIndexBuffer(ByteBuffer buffer, int primitiveCount);

        public int getBytesPerElement() {
            return this.format.getStride();
        }

        public VkIndexType getFormat() {
            return this.format;
        }

        public int getMaxPrimitiveCount() {
            return this.maxElementCount / 4;
        }

        public int getMaxElementCount() {
            return this.maxElementCount;
        }
    }

    private record VmaBuffer(long buffer, long allocation) {
    }
}
