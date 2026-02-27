package net.caffeinemc.mods.sodium.client.vk.pipeline;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;

public final class VkDescriptorSetLayoutBuilder {
    private final VkDevice device;
    private final List<Binding> bindings = new ArrayList<>(4);
    private int flags;

    private VkDescriptorSetLayoutBuilder(VkDevice device) {
        this.device = Objects.requireNonNull(device, "device");
    }

    public static VkDescriptorSetLayoutBuilder create(VkDevice device) {
        return new VkDescriptorSetLayoutBuilder(device);
    }

    public VkDescriptorSetLayoutBuilder flags(int flags) {
        this.flags = flags;
        return this;
    }

    public VkDescriptorSetLayoutBuilder addBinding(int binding, int descriptorType, int descriptorCount, int stageFlags) {
        this.bindings.add(new Binding(binding, descriptorType, descriptorCount, stageFlags));
        return this;
    }

    public VkDescriptorSetLayout build() {
        this.validate();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer vkBindings = VkDescriptorSetLayoutBinding.calloc(this.bindings.size(), stack);
            for (int i = 0; i < this.bindings.size(); i++) {
                Binding binding = this.bindings.get(i);
                vkBindings.get(i)
                        .binding(binding.binding)
                        .descriptorType(binding.descriptorType)
                        .descriptorCount(binding.descriptorCount)
                        .stageFlags(binding.stageFlags);
            }

            VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(this.flags)
                    .pBindings(vkBindings);

            LongBuffer pLayout = stack.mallocLong(1);
            int result = vkCreateDescriptorSetLayout(this.device, info, null, pLayout);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create descriptor set layout " + result);
            }

            return new VkDescriptorSetLayout(this.device, pLayout.get(0));
        }
    }

    private void validate() {
        if (this.bindings.isEmpty()) {
            throw new IllegalStateException("...an empty set? really?");
        }

        for (int i = 0; i < this.bindings.size(); i++) {
            Binding binding = this.bindings.get(i);
            if (binding.descriptorCount <= 0) {
                throw new IllegalStateException("bindings[" + i + "] descriptorCount must be >0");
            }

            if (binding.stageFlags == 0) {
                throw new IllegalStateException("bindings[" + i + "] stageFlags must be something");
            }

            for (int j = i + 1; j < this.bindings.size(); j++) {
                if (binding.binding == this.bindings.get(j).binding) {
                    throw new IllegalStateException("duplicate at " + binding.binding);
                }
            }
        }
    }

    public static final class VkDescriptorSetLayout {
        private final VkDevice device;
        private final long handle;

        public VkDescriptorSetLayout(VkDevice device, long handle) {
            this.device = device;
            this.handle = handle;
        }

        public long handle() {
            return this.handle;
        }

        public void delete() {
            vkDestroyDescriptorSetLayout(this.device, this.handle, null);
        }
    }

    private static final class Binding {
        private final int binding;
        private final int descriptorType;
        private final int descriptorCount;
        private final int stageFlags;

        private Binding(int binding, int descriptorType, int descriptorCount, int stageFlags) {
            this.binding = binding;
            this.descriptorType = descriptorType;
            this.descriptorCount = descriptorCount;
            this.stageFlags = stageFlags;
        }
    }
}
