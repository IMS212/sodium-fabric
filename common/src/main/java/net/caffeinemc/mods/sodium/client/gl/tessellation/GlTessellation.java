package net.caffeinemc.mods.sodium.client.gl.tessellation;

import net.caffeinemc.mods.sodium.client.gl.device.CommandList;

public interface GlTessellation {
    GlPrimitiveType getPrimitiveType();

    default void delete(CommandList commandList) {
        
    }
}
