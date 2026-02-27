package net.caffeinemc.mods.sodium.client.vk.device;

import net.caffeinemc.mods.sodium.client.vk.buffer.VkIndexType;

public interface DrawCommandList extends AutoCloseable {
    void multiDrawElementsBaseVertex(MultiDrawBatch batch, VkIndexType indexType);

    void endTessellating();

    void flush();

    @Override
    default void close() {
        this.flush();
    }
}
