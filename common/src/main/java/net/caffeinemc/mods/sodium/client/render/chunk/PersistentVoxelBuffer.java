package net.caffeinemc.mods.sodium.client.render.chunk;

import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryUtil;

public class PersistentVoxelBuffer {
    private final int id;
    private final long capacity;
    private long mapping;
    private boolean flushIfNeeded;
    private long minRange = Long.MAX_VALUE, maxRange = Long.MIN_VALUE;

    public PersistentVoxelBuffer(long capacity) {
        this.id = GL46C.glCreateBuffers();
        this.capacity = capacity;

        GL46C.glNamedBufferStorage(id, capacity, GL46C.GL_MAP_WRITE_BIT | GL46C.GL_MAP_PERSISTENT_BIT);

        mapping = GL46C.nglMapNamedBufferRange(id, 0, capacity, GL46C.GL_MAP_WRITE_BIT | GL46C.GL_MAP_FLUSH_EXPLICIT_BIT | GL46C.GL_MAP_PERSISTENT_BIT);
    }

    public int getId() {
        return id;
    }

    public void write(long offset, int id) {
        flushIfNeeded = true;
        minRange = Math.min(minRange, offset);
        maxRange = Math.max(maxRange, offset + 4);
        MemoryUtil.memPutInt(mapping + offset, id);
    }

    public void flush() {
        if (flushIfNeeded) {
            GL46C.glFlushMappedNamedBufferRange(id, minRange, maxRange - minRange);

            minRange = Long.MAX_VALUE;
            maxRange = Long.MIN_VALUE;
            flushIfNeeded = false;
        }
    }
}
