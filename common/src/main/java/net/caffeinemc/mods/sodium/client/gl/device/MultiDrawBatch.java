package net.caffeinemc.mods.sodium.client.gl.device;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkMultiDrawIndexedInfoEXT;

import java.nio.IntBuffer;

/**
 * Provides a fixed-size queue for building a draw-command list usable with
 * {@link org.lwjgl.opengl.GL33C#glMultiDrawElementsBaseVertex(int, IntBuffer, int, PointerBuffer, IntBuffer)}.
 */
public final class MultiDrawBatch {
    public final VkMultiDrawIndexedInfoEXT.Buffer info;

    private final int capacity;

    public int size;
    public int[] indexCount;

    public MultiDrawBatch(int capacity) {
        this.info = VkMultiDrawIndexedInfoEXT.calloc(capacity);

        this.capacity = capacity;
        this.indexCount = new int[capacity];
    }

    public int size() {
        return this.size;
    }

    public int capacity() {
        return this.capacity;
    }

    public void clear() {
        this.size = 0;
    }

    public void delete() {
        MemoryUtil.memFree(info);
    }

    public boolean isEmpty() {
        return this.size <= 0;
    }

    public int getIndexBufferSize() {
        int elements = 0;

        for (var index = 0; index < this.size; index++) {
            elements = Math.max(elements, this.indexCount[index]);
        }

        return elements;
    }
}
