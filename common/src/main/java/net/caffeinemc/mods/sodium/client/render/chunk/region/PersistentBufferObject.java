package net.caffeinemc.mods.sodium.client.render.chunk.region;

import it.unimi.dsi.fastutil.longs.Long2LongFunction;
import org.lwjgl.opengl.GL46C;

public class PersistentBufferObject {
	public int frameId;
	public int bufferId;
	public long size;
	private long mapLocation;

	public PersistentBufferObject(long size) {
		bufferId = GL46C.glCreateBuffers();
		this.size = size;
		if (size == 0) return;

		GL46C.glNamedBufferStorage(bufferId, size * 3, GL46C.GL_MAP_WRITE_BIT | GL46C.GL_MAP_PERSISTENT_BIT);
		mapLocation = GL46C.nglMapNamedBufferRange(bufferId, 0, size * 3, GL46C.GL_MAP_WRITE_BIT | GL46C.GL_MAP_FLUSH_EXPLICIT_BIT | GL46C.GL_MAP_PERSISTENT_BIT);
	}

	public void beginFrame() {
		frameId = (frameId + 1) % 3;
	}

	public void updateAndFlush(Long2LongFunction updater) {
		if (size == 0) return;

		long usedSize = updater.applyAsLong(mapLocation + (size * frameId));

		GL46C.glFlushMappedNamedBufferRange(bufferId, size * frameId, usedSize);
	}

	public void bind() {
		if (size == 0) return;
		GL46C.glBindBufferRange(GL46C.GL_UNIFORM_BUFFER, 9, bufferId, size * frameId, size);
	}

	public void close() {
		GL46C.glUnmapNamedBuffer(bufferId);
		GL46C.glDeleteBuffers(bufferId);
	}
}
