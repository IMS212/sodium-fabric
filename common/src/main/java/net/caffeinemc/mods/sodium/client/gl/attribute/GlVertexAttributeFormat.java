package net.caffeinemc.mods.sodium.client.gl.attribute;

import org.lwjgl.vulkan.VK10;

/**
 * An enumeration over the supported data types that can be used for vertex attributes.
 */
public record GlVertexAttributeFormat(int size) {
    public static final GlVertexAttributeFormat FLOAT = new GlVertexAttributeFormat( 4);
    public static final GlVertexAttributeFormat INT = new GlVertexAttributeFormat( 4);
    public static final GlVertexAttributeFormat SHORT = new GlVertexAttributeFormat(2);
    public static final GlVertexAttributeFormat BYTE = new GlVertexAttributeFormat(1);
    public static final GlVertexAttributeFormat UNSIGNED_SHORT = new GlVertexAttributeFormat(2);
    public static final GlVertexAttributeFormat UNSIGNED_BYTE = new GlVertexAttributeFormat(1);
    public static final GlVertexAttributeFormat UNSIGNED_INT = new GlVertexAttributeFormat(4);

    public int calculateFormatFromCount(int count, boolean normalized, boolean intType) {
        if (this == UNSIGNED_INT) {
            if (count == 1) {
                if (intType) {
                    return VK10.VK_FORMAT_R32_UINT;
                }
            } else if (count == 2) {
                if (intType) {
                    return VK10.VK_FORMAT_R32G32_UINT;
                }
            } else if (count == 3) {
                if (intType) {
                    return VK10.VK_FORMAT_R32G32B32_UINT;
                }
            } else if (count == 4) {
                if (intType) {
                    return VK10.VK_FORMAT_R32G32B32A32_UINT;
                }
            }
        } else if (this == UNSIGNED_SHORT) {
            if (count == 1) {
                if (intType) {
                    return VK10.VK_FORMAT_R16_UINT;
                } else if (normalized) {
                    return VK10.VK_FORMAT_R16_UNORM;
                } else {
                    return VK10.VK_FORMAT_R16_USCALED;
                }
            } else if (count == 2) {
                if (intType) {
                    return VK10.VK_FORMAT_R16G16_UINT;
                } else if (normalized) {
                    return VK10.VK_FORMAT_R16G16_UNORM;
                } else {
                    return VK10.VK_FORMAT_R16G16_USCALED;
                }
            } else if (count == 3) {
                if (intType) {
                    return VK10.VK_FORMAT_R16G16B16_UINT;
                } else if (normalized) {
                    return VK10.VK_FORMAT_R16G16B16_UNORM;
                } else {
                    return VK10.VK_FORMAT_R16G16B16_USCALED;
                }
            } else if (count == 4) {
                if (intType) {
                    return VK10.VK_FORMAT_R16G16B16A16_UINT;
                } else if (normalized) {
                    return VK10.VK_FORMAT_R16G16B16A16_UNORM;
                } else {
                    return VK10.VK_FORMAT_R16G16B16A16_USCALED;
                }
            }
        } else if (this == UNSIGNED_BYTE) {
            if (count == 1) {
                if (intType) {
                    return VK10.VK_FORMAT_R8_UINT;
                } else if (normalized) {
                    return VK10.VK_FORMAT_R8_UNORM;
                } else {
                    return VK10.VK_FORMAT_R8_USCALED;
                }
            } else if (count == 2) {
                if (intType) {
                    return VK10.VK_FORMAT_R8G8_UINT;
                } else if (normalized) {
                    return VK10.VK_FORMAT_R8G8_UNORM;
                } else {
                    return VK10.VK_FORMAT_R8G8_USCALED;
                }
            } else if (count == 3) {
                if (intType) {
                    return VK10.VK_FORMAT_R8G8B8_UINT;
                } else if (normalized) {
                    return VK10.VK_FORMAT_R8G8B8_UNORM;
                } else {
                    return VK10.VK_FORMAT_R8G8B8_USCALED;
                }
            } else if (count == 4) {
                if (intType) {
                    return VK10.VK_FORMAT_R8G8B8A8_UINT;
                } else if (normalized) {
                    return VK10.VK_FORMAT_R8G8B8A8_UNORM;
                } else {
                    return VK10.VK_FORMAT_R8G8B8A8_USCALED;
                }
            }
        }

        throw new IllegalStateException("Unknown format for count: " + count + " and type: " + this + " with normalized: " + normalized + " and intType: " + intType);
    }
}
