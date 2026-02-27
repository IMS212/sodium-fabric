package net.caffeinemc.mods.sodium.client.vk.pipeline;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSpecializationInfo;
import org.lwjgl.vulkan.VkSpecializationMapEntry;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO;
import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_BACK_BIT;
import static org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_SCISSOR;
import static org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_VIEWPORT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;

public final class VkGraphicsPipelineBuilder {
    private final VkDevice device;
    private final long pipelineLayout;

    private final List<ShaderStage> stages = new ArrayList<>(2);
    private final List<VertexBinding> vertexBindings = new ArrayList<>(4);
    private final List<VertexAttribute> vertexAttributes = new ArrayList<>(8);

    private int[] colorAttachmentFormats = new int[0];
    private int depthAttachmentFormat = VK_FORMAT_UNDEFINED;
    private int stencilAttachmentFormat = VK_FORMAT_UNDEFINED;

    private int polygonMode = VK_POLYGON_MODE_FILL;
    private int cullMode = VK_CULL_MODE_BACK_BIT;
    private int frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;

    private boolean depthTestEnable;
    private boolean depthWriteEnable;
    private int depthCompareOp = VK_COMPARE_OP_LESS_OR_EQUAL;

    private ColorBlendAttachment[] colorBlendAttachments;

    private VkGraphicsPipelineBuilder(VkDevice device, long pipelineLayout) {
        this.device = Objects.requireNonNull(device, "device");
        this.pipelineLayout = pipelineLayout;
    }

    public static VkGraphicsPipelineBuilder create(VkDevice device, long pipelineLayout) {
        return new VkGraphicsPipelineBuilder(device, pipelineLayout);
    }

    public VkGraphicsPipelineBuilder shared(byte[] spv, String vertexName, String fragmentName, Specialization specialization) {
        this.stages.clear();
        this.stages.add(ShaderStage.vertex(spv).withEntryPoint(vertexName).withSpecialization(specialization));
        this.stages.add(ShaderStage.fragment(spv).withEntryPoint(fragmentName).withSpecialization(specialization));
        return this;
    }

    public VkGraphicsPipelineBuilder setLayouts(long... setLayouts) {
        return this;
    }

    public VkGraphicsPipelineBuilder pushConstants(PushConstantRange... ranges) {
        return this;
    }

    public VkGraphicsPipelineBuilder dynamicRendering(int[] colorFormats, int depthFormat, int stencilFormat) {
        this.colorAttachmentFormats = colorFormats != null ? colorFormats : new int[0];
        this.depthAttachmentFormat = depthFormat;
        this.stencilAttachmentFormat = stencilFormat;
        return this;
    }

    public VkGraphicsPipelineBuilder addVertexBinding(int binding, int stride, int inputRate) {
        this.vertexBindings.add(new VertexBinding(binding, stride, inputRate));
        return this;
    }

    public VkGraphicsPipelineBuilder addVertexAttribute(int location, int binding, int format, int offset) {
        this.vertexAttributes.add(new VertexAttribute(location, binding, format, offset));
        return this;
    }

    public VkGraphicsPipelineBuilder rasterization(int polygonMode, int cullMode, int frontFace) {
        this.polygonMode = polygonMode;
        this.cullMode = cullMode;
        this.frontFace = frontFace;
        return this;
    }

    public VkGraphicsPipelineBuilder depthStencil(boolean depthTestEnable, boolean depthWriteEnable, int compareOp) {
        this.depthTestEnable = depthTestEnable;
        this.depthWriteEnable = depthWriteEnable;
        this.depthCompareOp = compareOp;
        return this;
    }

    public VkGraphicsPipelineBuilder setColorBlendAttachments(ColorBlendAttachment... attachments) {
        this.colorBlendAttachments = attachments;
        return this;
    }

    public long build() {
        if (this.pipelineLayout == VK_NULL_HANDLE) {
            throw new IllegalStateException("Pipeline layout was not specified");
        }

        if (this.stages.isEmpty()) {
            throw new IllegalStateException("No shader stages were specified");
        }

        if (this.colorAttachmentFormats.length == 0) {
            throw new IllegalStateException("No dynamic rendering color formats were specified");
        }

        long[] modules = new long[this.stages.size()];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer stageInfos = VkPipelineShaderStageCreateInfo.calloc(this.stages.size(), stack);
            for (int i = 0; i < this.stages.size(); i++) {
                ShaderStage stage = this.stages.get(i);
                modules[i] = this.createShaderModule(stage.spirv);

                VkPipelineShaderStageCreateInfo stageInfo = stageInfos.get(i);
                stageInfo.sType$Default();
                stageInfo.stage(stage.stage);
                stageInfo.module(modules[i]);
                stageInfo.pName(stack.UTF8(stage.entryPoint));

                if (stage.specialization != null) {
                    stageInfo.pSpecializationInfo(stage.specialization.toVk(stack));
                }
            }

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
            vertexInput.pVertexBindingDescriptions(this.toVkVertexBindings(stack));
            vertexInput.pVertexAttributeDescriptions(this.toVkVertexAttributes(stack));

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);

            VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .rasterizerDiscardEnable(false)
                    .depthClampEnable(false)
                    .polygonMode(this.polygonMode)
                    .cullMode(this.cullMode)
                    .frontFace(this.frontFace)
                    .depthBiasEnable(false)
                    .lineWidth(1.0f);

            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthTestEnable(this.depthTestEnable)
                    .depthWriteEnable(this.depthWriteEnable)
                    .depthCompareOp(this.depthCompareOp)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);

            VkPipelineColorBlendAttachmentState.Buffer blendAttachments = this.createBlendAttachments(stack);
            VkPipelineColorBlendStateCreateInfo blendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .logicOpEnable(false)
                    .pAttachments(blendAttachments);

            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkPipelineRenderingCreateInfo rendering = VkPipelineRenderingCreateInfo.calloc(stack);
            rendering.sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO_KHR);
            rendering.pColorAttachmentFormats(stack.ints(this.colorAttachmentFormats));
            rendering.depthAttachmentFormat(this.depthAttachmentFormat);
            rendering.stencilAttachmentFormat(this.stencilAttachmentFormat);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0)
                    .sType$Default()
                    .pNext(rendering.address())
                    .layout(this.pipelineLayout)
                    .pStages(stageInfos)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(raster)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(blendState)
                    .pDynamicState(dynamicState)
                    .renderPass(VK_NULL_HANDLE)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);

            LongBuffer pPipeline = stack.mallocLong(1);
            int result = vkCreateGraphicsPipelines(this.device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create graphics pipeline: " + result);
            }

            return pPipeline.get(0);
        } finally {
            for (long module : modules) {
                if (module != VK_NULL_HANDLE) {
                    vkDestroyShaderModule(this.device, module, null);
                }
            }
        }
    }

    private long createShaderModule(byte[] spirv) {
        Objects.requireNonNull(spirv, "spirv");
        if ((spirv.length & 3) != 0) {
            throw new IllegalArgumentException("SPIR-V bytecode length must be a multiple of 4");// thank you random person online for teaching me this is a thing
        }

        ByteBuffer code = MemoryUtil.memAlloc(spirv.length);
        code.put(spirv).flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack).sType$Default();
            info.pCode(code);

            LongBuffer pModule = stack.mallocLong(1);
            int result = vkCreateShaderModule(this.device, info, null, pModule);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("failed to create shader module " + result);
            }

            return pModule.get(0);
        } finally {
            MemoryUtil.memFree(code);
        }
    }

    private VkVertexInputBindingDescription.Buffer toVkVertexBindings(MemoryStack stack) {
        if (this.vertexBindings.isEmpty()) {
            return null;
        }

        VkVertexInputBindingDescription.Buffer bindings = VkVertexInputBindingDescription.calloc(this.vertexBindings.size(), stack);
        for (int i = 0; i < this.vertexBindings.size(); i++) {
            VertexBinding binding = this.vertexBindings.get(i);
            bindings.get(i)
                    .binding(binding.binding)
                    .stride(binding.stride)
                    .inputRate(binding.inputRate);
        }

        return bindings;
    }

    private VkVertexInputAttributeDescription.Buffer toVkVertexAttributes(MemoryStack stack) {
        if (this.vertexAttributes.isEmpty()) {
            return null;
        }

        VkVertexInputAttributeDescription.Buffer attributes = VkVertexInputAttributeDescription.calloc(this.vertexAttributes.size(), stack);
        for (int i = 0; i < this.vertexAttributes.size(); i++) {
            VertexAttribute attribute = this.vertexAttributes.get(i);
            attributes.get(i)
                    .location(attribute.location)
                    .binding(attribute.binding)
                    .format(attribute.format)
                    .offset(attribute.offset);
        }

        return attributes;
    }

    private VkPipelineColorBlendAttachmentState.Buffer createBlendAttachments(MemoryStack stack) {
        int attachmentCount = this.colorAttachmentFormats.length;
        VkPipelineColorBlendAttachmentState.Buffer attachments = VkPipelineColorBlendAttachmentState.calloc(attachmentCount, stack);

        for (int i = 0; i < attachmentCount; i++) {
            ColorBlendAttachment blend = this.colorBlendAttachments != null ? this.colorBlendAttachments[i] : ColorBlendAttachment.disabled();
            attachments.get(i)
                    .blendEnable(blend.blendEnable)
                    .srcColorBlendFactor(blend.srcColorBlendFactor)
                    .dstColorBlendFactor(blend.dstColorBlendFactor)
                    .colorBlendOp(blend.colorBlendOp)
                    .srcAlphaBlendFactor(blend.srcAlphaBlendFactor)
                    .dstAlphaBlendFactor(blend.dstAlphaBlendFactor)
                    .alphaBlendOp(blend.alphaBlendOp)
                    .colorWriteMask(blend.colorWriteMask);
        }

        return attachments;
    }

    public static final class ShaderStage {
        private final int stage;
        private final byte[] spirv;
        private final String entryPoint;
        private final Specialization specialization;

        private ShaderStage(int stage, byte[] spirv, String entryPoint, Specialization specialization) {
            this.stage = stage;
            this.spirv = Objects.requireNonNull(spirv, "spirv");
            this.entryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
            this.specialization = specialization;
        }

        public static ShaderStage vertex(byte[] spirv) {
            return new ShaderStage(VK_SHADER_STAGE_VERTEX_BIT, spirv, "main", null);
        }

        public static ShaderStage fragment(byte[] spirv) {
            return new ShaderStage(VK_SHADER_STAGE_FRAGMENT_BIT, spirv, "main", null);
        }

        public ShaderStage withEntryPoint(String entryPoint) {
            return new ShaderStage(this.stage, this.spirv, entryPoint, this.specialization);
        }

        public ShaderStage withSpecialization(Specialization specialization) {
            return new ShaderStage(this.stage, this.spirv, this.entryPoint, specialization);
        }
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

    private static final class VertexBinding {
        private final int binding;
        private final int stride;
        private final int inputRate;

        private VertexBinding(int binding, int stride, int inputRate) {
            this.binding = binding;
            this.stride = stride;
            this.inputRate = inputRate;
        }
    }

    private static final class VertexAttribute {
        private final int location;
        private final int binding;
        private final int format;
        private final int offset;

        private VertexAttribute(int location, int binding, int format, int offset) {
            this.location = location;
            this.binding = binding;
            this.format = format;
            this.offset = offset;
        }
    }

    public static final class ColorBlendAttachment {
        private final boolean blendEnable;
        private final int srcColorBlendFactor;
        private final int dstColorBlendFactor;
        private final int colorBlendOp;
        private final int srcAlphaBlendFactor;
        private final int dstAlphaBlendFactor;
        private final int alphaBlendOp;
        private final int colorWriteMask;

        public ColorBlendAttachment(boolean blendEnable,
                                    int srcColorBlendFactor,
                                    int dstColorBlendFactor,
                                    int colorBlendOp,
                                    int srcAlphaBlendFactor,
                                    int dstAlphaBlendFactor,
                                    int alphaBlendOp,
                                    int colorWriteMask) {
            this.blendEnable = blendEnable;
            this.srcColorBlendFactor = srcColorBlendFactor;
            this.dstColorBlendFactor = dstColorBlendFactor;
            this.colorBlendOp = colorBlendOp;
            this.srcAlphaBlendFactor = srcAlphaBlendFactor;
            this.dstAlphaBlendFactor = dstAlphaBlendFactor;
            this.alphaBlendOp = alphaBlendOp;
            this.colorWriteMask = colorWriteMask;
        }

        public static ColorBlendAttachment disabled() {
            return new ColorBlendAttachment(
                    false,
                    VK_BLEND_FACTOR_ONE,
                    VK_BLEND_FACTOR_ZERO,
                    VK_BLEND_OP_ADD,
                    VK_BLEND_FACTOR_ONE,
                    VK_BLEND_FACTOR_ZERO,
                    VK_BLEND_OP_ADD,
                    VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT
            );
        }

        public static ColorBlendAttachment alpha() {
            return new ColorBlendAttachment(
                    true,
                    VK_BLEND_FACTOR_SRC_ALPHA,
                    VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                    VK_BLEND_OP_ADD,
                    VK_BLEND_FACTOR_ONE,
                    VK_BLEND_FACTOR_ZERO,
                    VK_BLEND_OP_ADD,
                    VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT
            );
        }
    }

    public static final class Specialization {
        private final int[] constantIds;
        private final int[] offsets;
        private final int[] sizes;
        private final ByteBuffer data;

        public Specialization(int[] constantIds, int[] offsets, int[] sizes, ByteBuffer data) {
            this.constantIds = Objects.requireNonNull(constantIds, "constantIds");
            this.offsets = Objects.requireNonNull(offsets, "offsets");
            this.sizes = Objects.requireNonNull(sizes, "sizes");
            this.data = Objects.requireNonNull(data, "data");
        }

        private VkSpecializationInfo toVk(MemoryStack stack) {
            if (this.constantIds.length != this.offsets.length || this.constantIds.length != this.sizes.length) {
                throw new IllegalStateException("Specialization arrays must match in length");
            }

            VkSpecializationMapEntry.Buffer entries = VkSpecializationMapEntry.calloc(this.constantIds.length, stack);
            for (int i = 0; i < this.constantIds.length; i++) {
                entries.get(i)
                        .constantID(this.constantIds[i])
                        .offset(this.offsets[i])
                        .size(this.sizes[i]);
            }

            VkSpecializationInfo info = VkSpecializationInfo.calloc(stack);
            info.pMapEntries(entries);
            info.pData(this.data);
            return info;
        }
    }
}
