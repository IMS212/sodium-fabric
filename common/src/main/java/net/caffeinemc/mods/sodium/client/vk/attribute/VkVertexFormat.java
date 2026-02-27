package net.caffeinemc.mods.sodium.client.vk.attribute;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.caffeinemc.mods.sodium.client.render.vertex.VertexFormatAttribute;

import java.util.Map;

/**
 * Provides a generic vertex format which contains the attributes defined by a {@link VertexFormatAttribute}. Other code can then retrieve
 * the attributes and work with encoded data in a generic manner without needing to rely on a specific format.
 */
public class VkVertexFormat {
    private final Map<VertexFormatAttribute, VkVertexAttribute> attributesKeyed;

    private final int stride;
    private final VkVertexAttributeBinding[] bindings;

    public VkVertexFormat(Map<VertexFormatAttribute, VkVertexAttribute> attributesKeyed, VkVertexAttributeBinding[] bindings, int stride) {
        this.attributesKeyed = attributesKeyed;
        this.bindings = bindings;
        this.stride = stride;
    }

    public static Builder builder(int stride) {
        return new Builder(stride);
    }

    /**
     * Returns the {@link VkVertexAttribute} of this vertex format bound to the type {@param name}.
     * @throws NullPointerException If the attribute does not exist in this format
     */
    public VkVertexAttribute getAttribute(VertexFormatAttribute name) {
        VkVertexAttribute attr = this.attributesKeyed.get(name);

        if (attr == null) {
            throw new NullPointerException("No attribute exists for " + name.toString());
        }

        return attr;
    }

    /**
     * @return The stride (or the size of) the vertex format in bytes
     */
    public int getStride() {
        return this.stride;
    }

    @Override
    public String toString() {
        return String.format("GlVertexFormat{attributes=%d,stride=%d}",
                this.attributesKeyed.size(), this.stride);
    }

    public VkVertexAttributeBinding[] getShaderBindings() {
        return bindings;
    }

    public static class Builder {
        private final Map<VertexFormatAttribute, VkVertexAttribute> attributes;
        private final Object2IntMap<VkVertexAttribute> bindings;
        private final int stride;

        public Builder(int stride) {
            this.attributes = new Object2ObjectArrayMap<>();
            this.bindings = new Object2IntArrayMap<>();
            this.stride = stride;
        }

        public Builder addElement(VertexFormatAttribute attribute, int binding, int pointer) {
            return this.addElement(attribute, binding, new VkVertexAttribute(attribute.format(), attribute.count(), attribute.normalized(), pointer, this.stride, attribute.intType()));
        }

        /**
         * Adds a vertex attribute which will be bound to the given generic attribute type.
         *
         * @param type The generic attribute type
         * @param attribute The attribute to bind
         * @throws IllegalStateException If an attribute is already bound to the generic type
         */
        private Builder addElement(VertexFormatAttribute type, int binding, VkVertexAttribute attribute) {
            if (attribute.getPointer() >= this.stride) {
                throw new IllegalArgumentException("Element starts outside vertex format");
            }

            if (attribute.getPointer() + attribute.getSize() > this.stride) {
                throw new IllegalArgumentException("Element extends outside vertex format");
            }

            if (this.attributes.put(type, attribute) != null) {
                throw new IllegalStateException("Generic attribute " + type.name() + " already defined in vertex format");
            }

            if (binding != -1) {
                this.bindings.put(attribute, binding);
            }

            return this;
        }

        /**
         * Creates a {@link VkVertexFormat} from the current builder.
         */
        public VkVertexFormat build() {
            int size = 0;

            for (VkVertexAttribute attribute : this.attributes.values()) {
                size = Math.max(size, attribute.getPointer() + attribute.getSize());
            }

            // The stride must be large enough to cover all attributes. This still allows for additional padding
            // to be added to the end of the vertex to accommodate alignment restrictions.
            if (this.stride < size) {
                throw new IllegalArgumentException("Stride is too small");
            }

            VkVertexAttributeBinding[] bindings = this.bindings.object2IntEntrySet().stream()
                    .map(entry -> new VkVertexAttributeBinding(entry.getIntValue(), entry.getKey()))
                    .toArray(VkVertexAttributeBinding[]::new);

            return new VkVertexFormat(this.attributes, bindings, this.stride);
        }
    }
}
