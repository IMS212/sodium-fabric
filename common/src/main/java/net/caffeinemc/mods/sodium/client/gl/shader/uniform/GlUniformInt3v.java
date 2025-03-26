package net.caffeinemc.mods.sodium.client.gl.shader.uniform;

import org.lwjgl.opengl.GL30C;

public class GlUniformInt3v extends GlUniform<int[]> {
    public GlUniformInt3v(int index) {
        super(index);
    }

    @Override
    public void set(int[] value) {
        if (value.length != 3) {
            throw new IllegalArgumentException("value.length != 3");
        }

        GL30C.glUniform3iv(this.index, value);
    }

    public void set(int x, int y, int z) {
        GL30C.glUniform3i(this.index, x, y, z);
    }
}
