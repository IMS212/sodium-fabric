package net.caffeinemc.mods.sodium.client.gl.shader;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
    VERTEX(0x8B31),
    GEOMETRY(0x8DD9),
    TESS_CONTROL(0x8E88),
    TESS_EVALUATION(0x8E87),
    FRAGMENT(0x8B30);

    public final int id;

    ShaderType(int id) {
        this.id = id;
    }

    public static ShaderType fromGlShaderType(int id) {
        for (ShaderType type : values()) {
            if (type.id == id) {
                return type;
            }
        }

        return null;
    }
}
