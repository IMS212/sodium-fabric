package net.caffeinemc.mods.sodium.client.vk.tessellation;

import org.lwjgl.vulkan.VK10;

public enum VkPrimitiveType {
    POINTS(VK10.VK_PRIMITIVE_TOPOLOGY_POINT_LIST),
    LINES(VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST),
    TRIANGLES(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST),
    PATCHES(VK10.VK_PRIMITIVE_TOPOLOGY_PATCH_LIST);

    private final int id;

    VkPrimitiveType(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }
}
