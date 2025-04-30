package net.caffeinemc.mods.sodium.client.gl.shader.uniform;

import org.lwjgl.opengl.GL30C;

public class GlUniformInt2v extends GlUniform<int[]> {
    public GlUniformInt2v(int index) {
        super(index);
    }

    @Override
    public void set(int[] value) {
        if (value.length != 2) {
            throw new IllegalArgumentException("value.length != 2");
        }

        GL30C.glUniform2iv(this.index, value);
    }

    public void set(int x, int y) {
        GL30C.glUniform2i(this.index, x, y);
    }
}
