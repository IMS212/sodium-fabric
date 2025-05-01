package net.caffeinemc.mods.sodium.client.gl.device;

import graphics.cinnabar.core.vk.memory.VkBuffer;
import net.caffeinemc.mods.sodium.client.gl.array.GlVertexArray;
import net.caffeinemc.mods.sodium.client.gl.buffer.*;
import net.caffeinemc.mods.sodium.client.gl.sync.GlFence;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlTessellation;
import net.caffeinemc.mods.sodium.client.gl.tessellation.TessellationBinding;
import net.caffeinemc.mods.sodium.client.gl.util.EnumBitField;

import java.nio.ByteBuffer;

public interface CommandList extends AutoCloseable {
    GlTessellation createTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings);


    void deleteVertexArray(GlVertexArray vertexArray);

    void flush();

    DrawCommandList beginTessellating(GlTessellation tessellation);

    void deleteTessellation(GlTessellation tessellation);

    @Override
    default void close() {
        this.flush();
    }


    void flushMappedRange(GlBufferMapping map, int offset, int length);

    GlFence createFence();

    void deleteBuffer(VkBuffer srcBufferObj);
}
