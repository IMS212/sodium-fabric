package net.caffeinemc.mods.sodium.client.vk.attribute;

public class VkVertexAttribute {
    private final VkVertexAttributeFormat format;

    private final int pointer;
    private final int stride;
    private final int size;
    private final boolean intType;
    private final int count;
    private final boolean normalized;

    public VkVertexAttribute(ScalarType type, int count, boolean normalized, int pointer, int stride, boolean intType) {
        this(VkVertexAttributeFormat.resolve(type, count, normalized, intType), pointer, stride, count, normalized, intType, count * type.bytes);
    }
    public VkVertexAttribute(VkVertexAttributeFormat resolve, int pointer, int stride, int count, boolean normalized, boolean intType, int size) {
        this.format = resolve;
        this.pointer = pointer;
        this.stride = stride;
        this.size = size;
        this.count = count;
        this.normalized = normalized;
        this.intType = intType;
    }

    public VkVertexAttribute(VkVertexAttribute attribute) {
        this.format = attribute.format;
        this.pointer = attribute.pointer;
        this.stride = attribute.stride;
        this.size = attribute.size;
        this.count = attribute.count;
        this.normalized = attribute.normalized;
        this.intType = attribute.intType;
    }

    public VkVertexAttributeFormat getFormat() {
        return this.format;
    }

    public int getPointer() {
        return pointer;
    }

    public int getStride() {
        return stride;
    }

    public int getSize() {
        return size;
    }

    protected int getCount() {
        return count;
    }

    protected boolean isNormalized() {
        return normalized;
    }

    protected boolean isIntType() {
        return intType;
    }
}
