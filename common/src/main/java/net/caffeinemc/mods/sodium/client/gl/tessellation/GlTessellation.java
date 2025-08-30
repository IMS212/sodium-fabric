package net.caffeinemc.mods.sodium.client.gl.tessellation;

import net.caffeinemc.mods.sodium.client.gl.device.CommandList;

public interface GlTessellation {
    void delete(CommandList commandList);


    GlPrimitiveType getPrimitiveType();
}
