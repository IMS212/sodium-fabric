package me.jellysquid.mods.sodium.client.model.vertex.formats.clouds;

import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.formats.clouds.writer.CloudVertexBufferWriterNio;
import me.jellysquid.mods.sodium.client.model.vertex.formats.clouds.writer.CloudVertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.model.vertex.formats.clouds.writer.CloudVertexWriterFallback;
import me.jellysquid.mods.sodium.client.model.vertex.type.BlittableVertexType;
import me.jellysquid.mods.sodium.client.model.vertex.type.VanillaVertexType;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;

public class CloudVertexType implements VanillaVertexType<CloudVertexSink>, BlittableVertexType<CloudVertexSink> {
    @Override
    public CloudVertexSink createFallbackWriter(VertexConsumer consumer) {
        return new CloudVertexWriterFallback(consumer);
    }

    @Override
    public CloudVertexSink createBufferWriter(VertexBufferView buffer, boolean direct) {
        return direct ? new CloudVertexBufferWriterUnsafe(buffer) : new CloudVertexBufferWriterNio(buffer);
    }

    @Override
    public VertexFormat getVertexFormat() {
        return CloudVertexSink.VERTEX_FORMAT;
    }

    @Override
    public BlittableVertexType<CloudVertexSink> asBlittable() {
        return this;
    }
}
