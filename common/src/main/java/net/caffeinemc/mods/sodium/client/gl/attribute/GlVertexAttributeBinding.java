package net.caffeinemc.mods.sodium.client.gl.attribute;

import org.lwjgl.vulkan.VkVertexInputAttributeDescription;

public class GlVertexAttributeBinding extends GlVertexAttribute {
    private final int index;

    public GlVertexAttributeBinding(int index, GlVertexAttribute attribute) {
        super(attribute.getFormat(), attribute.getSize(), attribute.getCount(), attribute.isNormalized(), attribute.getPointer(), attribute.getStride(), attribute.isIntType());

        this.index = index;
    }

    public int getIndex() {
        return this.index;
    }

    public VkVertexInputAttributeDescription getDescription() {
        return VkVertexInputAttributeDescription.create()
                .binding(0)
                .location(this.index)
                .format(this.getFormat())
                .offset(this.getPointer());
    }
}
