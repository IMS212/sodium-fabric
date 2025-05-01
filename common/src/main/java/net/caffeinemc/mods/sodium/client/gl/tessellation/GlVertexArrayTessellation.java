package net.caffeinemc.mods.sodium.client.gl.tessellation;

import net.caffeinemc.mods.sodium.client.gl.array.GlVertexArray;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;

public class GlVertexArrayTessellation extends GlAbstractTessellation {
    private final GlVertexArray array;

    public GlVertexArrayTessellation(GlVertexArray array, GlPrimitiveType primitiveType, TessellationBinding[] bindings) {
        super(primitiveType, bindings);

        this.array = array;
    }

    public void init(CommandList commandList) {
        this.bindAttributes(commandList);
    }
}
