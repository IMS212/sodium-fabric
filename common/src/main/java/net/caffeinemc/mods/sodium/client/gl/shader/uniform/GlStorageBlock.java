package net.caffeinemc.mods.sodium.client.gl.shader.uniform;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.caffeinemc.mods.sodium.mixin.core.GlBufferAccessor;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL46C;

public class GlStorageBlock {
    private final int binding;

    public GlStorageBlock(int uniformBlockBinding) {
        this.binding = uniformBlockBinding;
    }

    public void bindBuffer(GpuBuffer buffer) {
        GL32C.glBindBufferBase(GL46C.GL_SHADER_STORAGE_BUFFER, this.binding, ((GlBufferAccessor) buffer).getHandle());
    }
}
