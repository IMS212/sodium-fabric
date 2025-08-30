package net.caffeinemc.mods.sodium.client.gl.device;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkDrawIndexedIndirectCommand;
import org.lwjgl.vulkan.VkDrawIndirectCommand;

import java.nio.IntBuffer;

/**
 * Provides a fixed-size queue for building a draw-command list usable with
 * {@link org.lwjgl.opengl.GL33C#glMultiDrawElementsBaseVertex(int, IntBuffer, int, PointerBuffer, IntBuffer)}.
 */
public final class MultiDrawBatch {
    public final VkDrawIndexedIndirectCommand.Buffer commands;

    public int size;
    public boolean isFilled;

    public MultiDrawBatch(int capacity) {
        commands = VkDrawIndexedIndirectCommand.calloc(capacity);
    }

    public void clear() {
        this.size = 0;
        this.isFilled = false;
    }

    public void delete() {
        commands.close();
    }

    public boolean isEmpty() {
        return this.size <= 0;
    }

    public int getIndexBufferSize() {
        int elements = 0;

        for (var index = 0; index < this.size; index++) {
            elements = Math.max(elements, commands.get(index).indexCount());
        }

        return elements;
    }
}
