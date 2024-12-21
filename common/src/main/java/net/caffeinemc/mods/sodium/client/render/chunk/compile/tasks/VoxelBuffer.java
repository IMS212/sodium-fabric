package net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks;

import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import org.lwjgl.system.MemoryUtil;

public class VoxelBuffer {
    private final NativeBuffer buffer;

    public static final int VOXEL_SIZE = 8;

    public VoxelBuffer() {
        buffer = new NativeBuffer((16 * 16 * 16) * VOXEL_SIZE);
    }

    public void write(int x, int y, int z, int id, int light) {
        long index = this.buffer.getPointer() + (x + y * 16L + z * 16L * 16L);
        MemoryUtil.memPutInt(index, id);
        MemoryUtil.memPutInt(index + 4, light);
    }

    public NativeBuffer getBuffer() {
        return buffer;
    }

    public void delete() {
        buffer.free();
    }
}
