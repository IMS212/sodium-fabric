package net.caffeinemc.mods.sodium.client.gl.tessellation;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;

public record TessellationBinding(int target,
                                  GpuBuffer buffer,
                                  GlVertexAttributeBinding[] attributeBindings) {
    public static TessellationBinding forVertexBuffer(GpuBuffer buffer, GlVertexAttributeBinding[] attributes) {
        return new TessellationBinding(GpuBuffer.USAGE_VERTEX, buffer, attributes);
    }

    public static TessellationBinding forElementBuffer(GpuBuffer buffer) {
        return new TessellationBinding(GpuBuffer.USAGE_INDEX, buffer, new GlVertexAttributeBinding[0]);
    }
}
