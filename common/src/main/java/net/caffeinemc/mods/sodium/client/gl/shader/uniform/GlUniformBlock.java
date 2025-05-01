package net.caffeinemc.mods.sodium.client.gl.shader.uniform;

import graphics.cinnabar.core.vk.memory.VkBuffer;
import org.lwjgl.opengl.GL32C;

public class GlUniformBlock {
    private final int binding;

    public GlUniformBlock(int uniformBlockBinding) {
        this.binding = uniformBlockBinding;
    }

    public void bindBuffer(VkBuffer buffer) {
    }
}
