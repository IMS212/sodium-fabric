package net.caffeinemc.mods.sodium.client.render.chunk.vertex.format;

import net.caffeinemc.mods.sodium.client.vk.attribute.VkVertexFormat;

public interface ChunkVertexType {
    VkVertexFormat getVertexFormat();

    ChunkVertexEncoder getEncoder();
}
