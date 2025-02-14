package net.caffeinemc.mods.sodium.client.render.chunk.region;

import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferSegment;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

public record PendingDispatch(int frame, GlBufferSegment segment, RenderSection section) {
}
