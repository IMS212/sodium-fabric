package net.caffeinemc.mods.sodium.api.vertex.format.common;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.*;

public final class BlockVertex {
    public static final VertexFormat FORMAT = DefaultVertexFormat.BLOCK;

    public static final int STRIDE = DefaultVertexFormat.BLOCK.getVertexSize();

    private static final int OFFSET_POSITION = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.POSITION);
    private static final int OFFSET_COLOR = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.COLOR);
    private static final int OFFSET_TEXTURE = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.UV0);
    private static final int OFFSET_LIGHT = DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.UV2);

    public static void write(long ptr,
                             float x, float y, float z, int color, float u, float v, int light) {
        PositionAttribute.put(ptr + OFFSET_POSITION, x, y, z);
        ColorAttribute.set(ptr + OFFSET_COLOR, color);
        TextureAttribute.put(ptr + OFFSET_TEXTURE, u, v);
        LightAttribute.set(ptr + OFFSET_LIGHT, light);
    }
}
