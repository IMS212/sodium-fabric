package net.caffeinemc.mods.sodium.client.vk.attribute;

public class VkVertexAttributeBinding extends VkVertexAttribute {
    private final int index;

    public VkVertexAttributeBinding(int index, VkVertexAttribute attribute) {
        super(attribute.getFormat(), attribute.getSize(), attribute.getCount(), attribute.isNormalized(), attribute.getPointer(), attribute.getStride(), attribute.isIntType());

        this.index = index;
    }

    public int getIndex() {
        return this.index;
    }
}
