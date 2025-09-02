package net.caffeinemc.sodium.frapi;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;

public interface ExtendedQuadEmitter {
    void transformAndEmit();

    QuadEmitter getDuck();
}
