package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import java.util.List;

public enum ChunkFogMode {
    NONE(List.of()),
    SMOOTH(List.of("USE_FOG", "USE_FOG_SMOOTH"));

    private final List<String> defines;

    ChunkFogMode(List<String> defines) {
        this.defines = defines;
    }

    public List<String> getDefines() {
        return this.defines;
    }
}
