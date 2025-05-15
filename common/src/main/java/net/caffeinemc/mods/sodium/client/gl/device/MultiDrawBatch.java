package net.caffeinemc.mods.sodium.client.gl.device;

import com.mojang.blaze3d.systems.RenderSystem;
import graphics.cinnabar.api.util.Destroyable;
import graphics.cinnabar.core.b3d.CinnabarDevice;
import graphics.cinnabar.core.vk.memory.VkBuffer;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.util.ModelQuadUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDrawIndexedIndirectCommand;

import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * Provides a fixed-size queue for building a draw-command list usable with
 * {@link org.lwjgl.opengl.GL33C#glMultiDrawElementsBaseVertex(int, IntBuffer, int, PointerBuffer, IntBuffer)}.
 */
public final class MultiDrawBatch implements Destroyable {
    public final VkBuffer info;
    public final VkDrawIndexedIndirectCommand.Buffer wrapper;

    private final int capacity;
    private final long pointer;

    public int size;
    public int[] arrayIndexCount;

    public MultiDrawBatch(int capacity) {
        this.info = new VkBuffer(SodiumClientMod.getDevice(), (long) capacity * VkDrawIndexedIndirectCommand.SIZEOF,
                VK12.VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK12.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT, ((CinnabarDevice) RenderSystem.getDevice()).hostPersistentMemoryPool);
        this.info.setVulkanName("MultiDrawBatch");
        this.pointer = info.allocation.cpu().hostPointer.pointer();
        MemoryUtil.memSet(this.pointer, 0, (long) capacity * VkDrawIndexedIndirectCommand.SIZEOF);
        this.wrapper = VkDrawIndexedIndirectCommand.create(pointer, capacity);

        for (long i = 0; i < capacity; i++) {
            MemoryUtil.memPutInt(pointer + (i * VkDrawIndexedIndirectCommand.SIZEOF) + VkDrawIndexedIndirectCommand.FIRSTINDEX, 0);
            MemoryUtil.memPutInt(pointer + (i * VkDrawIndexedIndirectCommand.SIZEOF) + VkDrawIndexedIndirectCommand.INSTANCECOUNT, 1);
        }
        this.capacity = capacity;
        this.arrayIndexCount = new int[capacity];
    }

    public int size() {
        return this.size;
    }

    public int capacity() {
        return this.capacity;
    }

    public void clear() {
        this.size = 0;
        Arrays.fill(this.arrayIndexCount, 0);
        MemoryUtil.memSet(this.pointer, 0, (long) capacity * VkDrawIndexedIndirectCommand.SIZEOF);
        for (long i = 0; i < capacity; i++) {
            MemoryUtil.memPutInt(pointer + (i * VkDrawIndexedIndirectCommand.SIZEOF) + VkDrawIndexedIndirectCommand.INSTANCECOUNT, 1);
        }
    }

    public void delete() {
        info.destroy();
    }

    public boolean isEmpty() {
        return this.size <= 0;
    }

    public int getIndexBufferSize() {
        int elements = 0;

        for (var index = 0; index < this.size; index++) {
            elements = Math.max(elements, this.wrapper.position(index).indexCount());
        }

        return elements;
    }

    @Override
    public void destroy() {
        delete();
    }
}
