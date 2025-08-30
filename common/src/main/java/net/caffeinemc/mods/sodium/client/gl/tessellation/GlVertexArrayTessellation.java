package net.caffeinemc.mods.sodium.client.gl.tessellation;

import net.caffeinemc.mods.sodium.client.gl.device.CommandList;

public class GlVertexArrayTessellation extends GlAbstractTessellation {

    public GlVertexArrayTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings) {
        super(primitiveType, bindings);

    }

    public void init(CommandList commandList) {
        this.bindAttributes(commandList);
    }

    @Override
    public void delete(CommandList commandList) {
    }
}
