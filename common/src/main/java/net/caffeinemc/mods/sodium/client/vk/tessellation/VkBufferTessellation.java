package net.caffeinemc.mods.sodium.client.vk.tessellation;

import net.caffeinemc.mods.sodium.client.vk.commands.CommandList;

public class VkBufferTessellation implements VkTessellation {
    private final VkPrimitiveType primitiveType;
    private final VkIndexType indexType;
    private final VkTessellationBinding[] bindings;

    public VkBufferTessellation(VkPrimitiveType primitiveType, VkIndexType indexType, VkTessellationBinding[] bindings) {
        this.primitiveType = primitiveType;
        this.indexType = indexType;
        this.bindings = bindings;
    }

    @Override
    public void delete(CommandList commandList) {
        // ...don't. i just don't want to rewrite this right now.
    }

    @Override
    public VkPrimitiveType getPrimitiveType() {
        return this.primitiveType;
    }

    @Override
    public VkIndexType getIndexType() {
        return this.indexType;
    }

    @Override
    public VkTessellationBinding[] getBindings() {
        return this.bindings;
    }
}
