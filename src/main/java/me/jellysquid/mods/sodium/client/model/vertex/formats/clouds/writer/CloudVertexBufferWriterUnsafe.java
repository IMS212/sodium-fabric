package me.jellysquid.mods.sodium.client.model.vertex.formats.clouds.writer;

import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.model.vertex.formats.clouds.CloudVertexSink;
import org.lwjgl.system.MemoryUtil;

public class CloudVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements CloudVertexSink {
    public CloudVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
        super(backingBuffer, VanillaVertexTypes.CLOUDS);
    }

    @Override
    public void writeQuad(float x, float y, float z, int color, float u, float v, int normal) {
        long i = this.writePointer;

        MemoryUtil.memPutFloat(i, x);
        MemoryUtil.memPutFloat(i + 4, y);
        MemoryUtil.memPutFloat(i + 8, z);
        MemoryUtil.memPutFloat(i + 12, u);
        MemoryUtil.memPutFloat(i + 16, v);
        MemoryUtil.memPutInt(i + 20, color);
        MemoryUtil.memPutInt(i + 24, normal);

        this.advance();
    }
}
