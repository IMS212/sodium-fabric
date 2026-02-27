package net.caffeinemc.mods.sodium.client.vk.pipeline;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;

import java.nio.LongBuffer;
import java.util.Objects;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;

public final class VkPipelineLayoutBuilder {
    private final VkDevice device;

    private long[] setLayouts = new long[0];
    private PushConstantRange[] pushConstants = new PushConstantRange[0];
    private int flags;

    private VkPipelineLayoutBuilder(VkDevice device) {
        this.device = Objects.requireNonNull(device, "device");
    }

    public static VkPipelineLayoutBuilder create(VkDevice device) {
        return new VkPipelineLayoutBuilder(device);
    }

    public VkPipelineLayoutBuilder flags(int flags) {
        this.flags = flags;
        return this;
    }

    public VkPipelineLayoutBuilder setLayouts(long... setLayouts) {
        this.setLayouts = setLayouts != null ? setLayouts : new long[0];
        return this;
    }

    public VkPipelineLayoutBuilder pushConstants(PushConstantRange... ranges) {
        this.pushConstants = ranges != null ? ranges : new PushConstantRange[0];
        return this;
    }

    public VkPipelineLayout build() {
        this.validate();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineLayoutCreateInfo info = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(this.flags);

            if (this.setLayouts.length > 0) {
                info.pSetLayouts(stack.longs(this.setLayouts));
            }

            if (this.pushConstants.length > 0) {
                VkPushConstantRange.Buffer ranges = VkPushConstantRange.calloc(this.pushConstants.length, stack);
                for (int i = 0; i < this.pushConstants.length; i++) {
                    PushConstantRange range = this.pushConstants[i];
                    ranges.get(i)
                            .stageFlags(range.getStageFlags())
                            .offset(range.getOffset())
                            .size(range.getSize());
                }
                info.pPushConstantRanges(ranges);
            }

            LongBuffer pLayout = stack.mallocLong(1);
            int result = vkCreatePipelineLayout(this.device, info, null, pLayout);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create pipeline layout " + result);
            }

            return new VkPipelineLayout(pLayout.get(0));
        }
    }

    private void validate() {
        // uhhhhh
    }

    public static final class PushConstantRange {
        private final int stageFlags;
        private final int offset;
        private final int size;

        public PushConstantRange(int stageFlags, int offset, int size) {
            this.stageFlags = stageFlags;
            this.offset = offset;
            this.size = size;
        }

        public int getStageFlags() {
            return this.stageFlags;
        }

        public int getOffset() {
            return this.offset;
        }

        public int getSize() {
            return this.size;
        }
    }
}
