package net.caffeinemc.mods.sodium.client.vk.attribute;

import org.lwjgl.vulkan.VK10;

/**
 * An enumeration over the supported data types that can be used for vertex attributes.
 */
public record VkVertexAttributeFormat(int typeId, int size) {
    public static final VkVertexAttributeFormat FLOAT = new VkVertexAttributeFormat(VK10.VK_FORMAT_R32_SFLOAT, 4);
    public static final VkVertexAttributeFormat INT = new VkVertexAttributeFormat(VK10.VK_FORMAT_R32_SINT, 4);
    public static final VkVertexAttributeFormat SHORT = new VkVertexAttributeFormat(VK10.VK_FORMAT_R16_SINT, 2);
    public static final VkVertexAttributeFormat BYTE = new VkVertexAttributeFormat(VK10.VK_FORMAT_R8_SINT, 1);
    public static final VkVertexAttributeFormat UNSIGNED_SHORT = new VkVertexAttributeFormat(VK10.VK_FORMAT_R16_UINT, 2);
    public static final VkVertexAttributeFormat UNSIGNED_BYTE = new VkVertexAttributeFormat(VK10.VK_FORMAT_R8_UINT, 1);
    public static final VkVertexAttributeFormat UNSIGNED_INT = new VkVertexAttributeFormat(VK10.VK_FORMAT_R32_UINT, 4);

    public int resolvePackedFormat(int count, boolean normalized, boolean intType) {
        switch (typeId) {
            case VK10.VK_FORMAT_R32_UINT -> {
                if (count == 2 && intType && !normalized) return VK10.VK_FORMAT_R32G32_UINT;
            }

            case VK10.VK_FORMAT_R16_UINT -> {
                if (count == 2 && intType && !normalized) return VK10.VK_FORMAT_R16G16_UINT;
            }

            case VK10.VK_FORMAT_R8_UINT -> {
                if (count == 4 && !intType && normalized) return VK10.VK_FORMAT_R8G8B8A8_UNORM;
                if (count == 4 && intType && !normalized) return VK10.VK_FORMAT_R8G8B8A8_UINT;
            }
        }

        throw new IllegalStateException("i'm not filling out every possibility.");
    }
}
