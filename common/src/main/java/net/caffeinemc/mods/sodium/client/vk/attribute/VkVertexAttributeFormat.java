package net.caffeinemc.mods.sodium.client.vk.attribute;

import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK14;

public record VkVertexAttributeFormat(int vkFormat, int componentCount) {


    public static VkVertexAttributeFormat resolve(
            ScalarType type,
            int componentCount,
            boolean normalized,
            boolean integer
    ) {
        if (type == ScalarType.UNSIGNED_BYTE) {
            if (normalized) {
                if (componentCount == 4) return new VkVertexAttributeFormat(VK14.VK_FORMAT_R8G8B8A8_UNORM, 4);
            } else if (integer) {
                if (componentCount == 4) return new VkVertexAttributeFormat(VK14.VK_FORMAT_R8G8B8A8_UINT, 4);
            }
        }

        if (type == ScalarType.UNSIGNED_INT) {
            if (integer) {
                if (componentCount == 2) return new VkVertexAttributeFormat(VK14.VK_FORMAT_R32G32_UINT, 2);
            }
        }

        if (type == ScalarType.UNSIGNED_SHORT) {
            if (integer) {
                if (componentCount == 2) return new VkVertexAttributeFormat(VK14.VK_FORMAT_R16G16_UINT, 2);
            }
        }

        throw new IllegalStateException("uh oh");
    }
}