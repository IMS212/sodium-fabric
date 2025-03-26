package net.caffeinemc.mods.sodium.client.render.chunk;

import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

public class PersistentBuffer {
    private final int buffer;
    private final long mapping;
    private final long size;

    public PersistentBuffer(int size) {
        this.size = size;
        this.buffer = GL46C.glCreateBuffers();
        GL46C.glNamedBufferStorage(buffer, size, GL46C.GL_MAP_WRITE_BIT | GL46C.GL_MAP_PERSISTENT_BIT);
        this.mapping = GL46C.nglMapNamedBufferRange(buffer, 0, size, GL46C.GL_MAP_WRITE_BIT | GL46C.GL_MAP_PERSISTENT_BIT | GL46C.GL_MAP_FLUSH_EXPLICIT_BIT);
    }

    public void destroy() {
        GL46C.glUnmapNamedBuffer(buffer);
        GL46C.glDeleteBuffers(buffer);
    }

    private long minSize = Long.MAX_VALUE, maxSize = -Long.MAX_VALUE;

    public void write(long pos, int value) {
        if ((pos) > (size) || pos < 0) {
            throw new IllegalStateException("Tried to write outside of buffer (" + pos + " / " + size + ")");
        }
        MemoryUtil.memPutInt(mapping + pos, value);
        this.minSize = Math.min(minSize, pos);
        this.maxSize = Math.max(maxSize, pos + 4);
    }

    public void flush() {
        if (minSize > 0 && maxSize > 0) {
            GL46C.glFlushMappedNamedBufferRange(buffer, minSize, maxSize - minSize);
            minSize = Long.MAX_VALUE;
            maxSize = -Long.MAX_VALUE;
        }
    }

    public int getId() {
        return buffer;
    }
}
