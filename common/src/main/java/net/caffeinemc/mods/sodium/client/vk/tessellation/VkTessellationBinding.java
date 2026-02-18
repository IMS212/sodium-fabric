package net.caffeinemc.mods.sodium.client.vk.tessellation;

import net.caffeinemc.mods.sodium.client.vk.attribute.VkVertexAttributeBinding;

public record VkTessellationBinding(VkBufferTarget target, long buffer, long offset, VkVertexAttributeBinding[] attributeBindings) {
    public static VkTessellationBinding forVertexBuffer(long buffer, long offset, VkVertexAttributeBinding[] attributes) {
        return new VkTessellationBinding(VkBufferTarget.VERTEX, buffer, offset, attributes);
    }

    public static VkTessellationBinding forElementBuffer(long buffer, long offset) {
        return new VkTessellationBinding(VkBufferTarget.INDEX, buffer, offset, new VkVertexAttributeBinding[0]);
    }
}
