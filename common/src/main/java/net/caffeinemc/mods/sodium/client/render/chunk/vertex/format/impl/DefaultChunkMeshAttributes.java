package net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl;

import net.caffeinemc.mods.sodium.client.vk.attribute.VkVertexAttributeFormat;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexFormatAttribute;

public class DefaultChunkMeshAttributes {
    public static final VertexFormatAttribute POSITION = new VertexFormatAttribute("POSITION", VkVertexAttributeFormat.UNSIGNED_INT, 2, false, true);
    public static final VertexFormatAttribute COLOR = new VertexFormatAttribute("COLOR", VkVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false);
    public static final VertexFormatAttribute TEXTURE = new VertexFormatAttribute("TEXTURE", VkVertexAttributeFormat.UNSIGNED_SHORT, 2, false, true);
    public static final VertexFormatAttribute LIGHT_MATERIAL_INDEX = new VertexFormatAttribute("LIGHT_MATERIAL_INDEX", VkVertexAttributeFormat.UNSIGNED_BYTE, 4, false, true);
}
