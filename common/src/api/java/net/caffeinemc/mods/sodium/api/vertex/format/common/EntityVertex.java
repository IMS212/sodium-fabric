package net.caffeinemc.mods.sodium.api.vertex.format.common;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.caffeinemc.mods.sodium.api.vertex.attributes.common.*;

public final class EntityVertex {
    public static final VertexFormat FORMAT = DefaultVertexFormat.ENTITY;

    public static final int STRIDE = DefaultVertexFormat.ENTITY.getVertexSize();

    private static final int OFFSET_POSITION = DefaultVertexFormat.ENTITY.getOffset(VertexFormatElement.POSITION);
    private static final int OFFSET_COLOR = DefaultVertexFormat.ENTITY.getOffset(VertexFormatElement.COLOR);
    private static final int OFFSET_TEXTURE = DefaultVertexFormat.ENTITY.getOffset(VertexFormatElement.UV0);
    private static final int OFFSET_OVERLAY = DefaultVertexFormat.ENTITY.getOffset(VertexFormatElement.UV1);
    private static final int OFFSET_LIGHT = DefaultVertexFormat.ENTITY.getOffset(VertexFormatElement.UV2);
    private static final int OFFSET_NORMAL = DefaultVertexFormat.ENTITY.getOffset(VertexFormatElement.NORMAL);

    public static void write(long ptr,
                             float x, float y, float z, int color, float u, float v, int overlay, int light, int normal) {
        PositionAttribute.put(ptr + OFFSET_POSITION, x, y, z);
        ColorAttribute.set(ptr + OFFSET_COLOR, color);
        TextureAttribute.put(ptr + OFFSET_TEXTURE, u, v);
        OverlayAttribute.set(ptr + OFFSET_OVERLAY, overlay);
        LightAttribute.set(ptr + OFFSET_LIGHT, light);
        NormalAttribute.set(ptr + OFFSET_NORMAL, normal);
    }
}
