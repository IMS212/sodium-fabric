package me.jellysquid.mods.sodium.client.model.vertex.formats.clouds.writer;

import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterNio;
import me.jellysquid.mods.sodium.client.model.vertex.formats.clouds.CloudVertexSink;

import java.nio.ByteBuffer;

public class CloudVertexBufferWriterNio extends VertexBufferWriterNio implements CloudVertexSink {
    public CloudVertexBufferWriterNio(VertexBufferView backingBuffer) {
        super(backingBuffer, VanillaVertexTypes.CLOUDS);
    }

    @Override
    public void writeQuad(float x, float y, float z, int color, float u, float v, int normal) {
        int i = this.writeOffset;

        ByteBuffer buf = this.byteBuffer;
        buf.putFloat(i, x);
        buf.putFloat(i + 4, y);
        buf.putFloat(i + 8, z);
        buf.putFloat(i + 12, u);
        buf.putFloat(i + 16, v);
        buf.putInt(i + 20, color);
        buf.putInt(i + 24, normal);

        this.advance();
    }
}
