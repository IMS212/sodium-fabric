package net.caffeinemc.mods.sodium.client.render.vertex;

import net.caffeinemc.mods.sodium.client.vk.attribute.VkVertexAttributeFormat;

public record VertexFormatAttribute(String name, VkVertexAttributeFormat format, int count, boolean normalized, boolean intType) {

}
