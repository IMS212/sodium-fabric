package net.caffeinemc.mods.sodium.client.render.vertex;

import net.caffeinemc.mods.sodium.client.vk.attribute.ScalarType;

public record VertexFormatAttribute(String name, ScalarType format, int count, boolean normalized, boolean intType) {

}