package net.caffeinemc.mods.sodium.client.gl.tessellation;

import graphics.cinnabar.core.vk.memory.VkBuffer;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBufferTarget;

public record TessellationBinding(GlBufferTarget target,
                                  VkBuffer buffer,
                                  GlVertexAttributeBinding[] attributeBindings) {
    public static TessellationBinding forVertexBuffer(VkBuffer buffer, GlVertexAttributeBinding[] attributes) {
        return new TessellationBinding(GlBufferTarget.ARRAY_BUFFER, buffer, attributes);
    }

    public static TessellationBinding forElementBuffer(VkBuffer buffer) {
        return new TessellationBinding(GlBufferTarget.ELEMENT_BUFFER, buffer, new GlVertexAttributeBinding[0]);
    }
}
