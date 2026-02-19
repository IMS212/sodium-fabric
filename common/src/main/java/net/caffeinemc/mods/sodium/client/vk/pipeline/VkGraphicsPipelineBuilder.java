package net.caffeinemc.mods.sodium.client.vk.pipeline;

import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderType;
import net.caffeinemc.mods.sodium.client.vk.VulkanContext;
import net.caffeinemc.mods.sodium.client.vk.attribute.VkVertexAttributeBinding;
import net.caffeinemc.mods.sodium.client.vk.attribute.VkVertexFormat;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;

import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.VK10.*;

public final class VkGraphicsPipelineBuilder {
    private final VkDevice device;

    private final ArrayList<ShaderWithEntrypoint> stages = new ArrayList<>();
    private VkVertexFormat vertexFormat;

    private long[] setLayouts = new long[0];
    private int pushStageFlags, pushOffset, pushSize;

    private int[] colorFormats = new int[0];
    private int depthFormat = VK_FORMAT_UNDEFINED;

    private boolean depthTest = true;
    private boolean depthWrite = true;

    private boolean blendEnable = false;
    private int blendSrcColor = VK_BLEND_FACTOR_SRC_ALPHA;
    private int blendDstColor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
    private int blendSrcAlpha = VK_BLEND_FACTOR_ONE;
    private int blendDstAlpha = VK_BLEND_FACTOR_ZERO;
    private int frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    private boolean cull = true;

    public VkGraphicsPipelineBuilder() {
        this.device = VulkanContext.INSTANCE.vkDevice();
    }

    private static int toShadercKind(ShaderType type) {
        return switch (type) {
            case VERTEX -> shaderc_glsl_vertex_shader;
            case FRAGMENT -> shaderc_glsl_fragment_shader;
            default -> throw new IllegalArgumentException("Unsupported stage: " + type);
        };
    }

    private static int toStageBit(ShaderType type) {
        return switch (type) {
            case VERTEX -> VK_SHADER_STAGE_VERTEX_BIT;
            case FRAGMENT -> VK_SHADER_STAGE_FRAGMENT_BIT;
            default -> throw new IllegalArgumentException("Unsupported stage: " + type);
        };
    }

    public VkGraphicsPipelineBuilder setCull(boolean cull) {
        this.cull = cull;
        return this;
    }

    public VkGraphicsPipelineBuilder addShader(ShaderLoader.LoadedShader shader) {
        stages.add(new ShaderWithEntrypoint(shader, "main"));
        return this;
    }

    public VkGraphicsPipelineBuilder setVertexFormat(VkVertexFormat fmt) {
        this.vertexFormat = fmt;
        return this;
    }

    public VkGraphicsPipelineBuilder setDescriptorSetLayouts(long... layouts) {
        this.setLayouts = layouts;
        return this;
    }

    public VkGraphicsPipelineBuilder setPushConstants(int stageFlags, int offset, int size) {
        this.pushStageFlags = stageFlags;
        this.pushOffset = offset;
        this.pushSize = size;
        return this;
    }

    public VkGraphicsPipelineBuilder setFormats(int[] colorFmts, int depthFmt) {
        this.colorFormats = colorFmts != null ? colorFmts : new int[0];
        this.depthFormat = depthFmt;
        return this;
    }

    public VkGraphicsPipelineBuilder setDepthState(boolean test, boolean write) {
        this.depthTest = test;
        this.depthWrite = write;
        return this;
    }

    public VkGraphicsPipelineBuilder turnOnBlend() {
        this.blendEnable = true;
        this.blendSrcColor = VK_BLEND_FACTOR_SRC_ALPHA;
        this.blendDstColor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
        this.blendSrcAlpha = VK_BLEND_FACTOR_ONE;
        this.blendDstAlpha = VK_BLEND_FACTOR_ZERO;
        return this;
    }

    // -------------------------------------------------------------------------

    public VkGraphicsPipelineBuilder setBlend(int srcColor, int dstColor, int srcAlpha, int dstAlpha) {
        this.blendEnable = true;
        this.blendSrcColor = srcColor;
        this.blendDstColor = dstColor;
        this.blendSrcAlpha = srcAlpha;
        this.blendDstAlpha = dstAlpha;
        return this;
    }

    public VkGraphicsPipeline build() {
        long layout = createLayout();
        var compiled = new ArrayList<BuiltShader>(stages.size());
        long pipeline = VK_NULL_HANDLE;

        try {
            for (var stage : stages) {
                compiled.add(compileShader(stage));
            }

            pipeline = createPipeline(layout, compiled);
            return new VkGraphicsPipeline(device, pipeline, layout);
        } catch (Throwable t) {
            if (pipeline != VK_NULL_HANDLE) {
                VK10.vkDestroyPipeline(device, pipeline, null);
            }
            vkDestroyPipelineLayout(device, layout, null);
            throw t;
        } finally {
            for (var s : compiled) {
                vkDestroyShaderModule(device, s.module, null);
            }
        }
    }

    private long createPipeline(long layout, ArrayList<BuiltShader> builtShaders) {
        try (var stack = MemoryStack.stackPush()) {
            var stageInfos = VkPipelineShaderStageCreateInfo.calloc(builtShaders.size(), stack);
            for (int i = 0; i < builtShaders.size(); i++) {
                var s = builtShaders.get(i);
                stageInfos.get(i)
                        .sType$Default()
                        .stage(s.stageBit)
                        .module(s.module)
                        .pName(stack.UTF8(s.entryPoint));
            }

            var bindDesc = VkVertexInputBindingDescription.calloc(1, stack);
            bindDesc.get(0)
                    .binding(0)
                    .stride(vertexFormat.getStride())
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkVertexAttributeBinding[] bindings = vertexFormat.getShaderBindings();
            var attrDesc = VkVertexInputAttributeDescription.calloc(bindings.length, stack);
            for (int i = 0; i < bindings.length; i++) {
                attrDesc.get(i)
                        .location(bindings[i].getIndex())
                        .binding(0)
                        .format(bindings[i].getFormat())
                        .offset(bindings[i].getPointer());
            }

            var vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pVertexBindingDescriptions(bindDesc)
                    .pVertexAttributeDescriptions(attrDesc);

            var inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            var viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);

            var rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .cullMode(cull ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE)
                    .frontFace(frontFace)
                    .lineWidth(1.0f);

            var multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            var depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthTestEnable(depthTest)
                    .depthWriteEnable(depthWrite)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);

            var blendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .logicOp(VK_LOGIC_OP_COPY);

            if (colorFormats.length > 0) {
                var attachments = VkPipelineColorBlendAttachmentState.calloc(colorFormats.length, stack);
                for (int i = 0; i < colorFormats.length; i++) {
                    attachments.get(i)
                            .blendEnable(blendEnable)
                            .srcColorBlendFactor(blendSrcColor)
                            .dstColorBlendFactor(blendDstColor)
                            .colorBlendOp(VK_BLEND_OP_ADD)
                            .srcAlphaBlendFactor(blendSrcAlpha)
                            .dstAlphaBlendFactor(blendDstAlpha)
                            .alphaBlendOp(VK_BLEND_OP_ADD)
                            .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                    VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
                }
                blendState.pAttachments(attachments);
            }

            var dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            var renderingInfo = VkPipelineRenderingCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthAttachmentFormat(depthFormat);

            if (colorFormats.length > 0) {
                IntBuffer fmtBuf = stack.mallocInt(colorFormats.length);
                for (int i = 0; i < colorFormats.length; i++) fmtBuf.put(i, colorFormats[i]);
                renderingInfo.pColorAttachmentFormats(fmtBuf);
            }

            var createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            createInfo.get(0)
                    .sType$Default()
                    .pNext(renderingInfo.address())
                    .stageCount(stageInfos.remaining())
                    .pStages(stageInfos)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(blendState)
                    .pDynamicState(dynamicState)
                    .layout(layout)
                    .renderPass(VK_NULL_HANDLE)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);

            LongBuffer pPipeline = stack.mallocLong(1);
            int res = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, createInfo, null, pPipeline);
            if (res != VK_SUCCESS)
                throw new IllegalStateException("vkCreateGraphicsPipelines failed: " + res);

            return pPipeline.get(0);
        }
    }

    private long createLayout() {
        try (var stack = MemoryStack.stackPush()) {
            var createInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default();

            if (setLayouts.length > 0) {
                LongBuffer buf = stack.mallocLong(setLayouts.length);
                for (int i = 0; i < setLayouts.length; i++) buf.put(i, setLayouts[i]);
                createInfo.pSetLayouts(buf);
            }

            if (pushStageFlags != 0 && pushSize > 0) {
                var ranges = VkPushConstantRange.calloc(1, stack);
                ranges.get(0).stageFlags(pushStageFlags).offset(pushOffset).size(pushSize);
                createInfo.pPushConstantRanges(ranges);
            }

            LongBuffer pLayout = stack.mallocLong(1);
            int res = vkCreatePipelineLayout(device, createInfo, null, pLayout);
            if (res != VK_SUCCESS)
                throw new IllegalStateException("vkCreatePipelineLayout failed: " + res);

            return pLayout.get(0);
        }
    }

    private BuiltShader compileShader(ShaderWithEntrypoint spec) {
        String source = spec.shader.parsedShader().src();

        long compiler = shaderc_compiler_initialize();

        long options = shaderc_compile_options_initialize();

        shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);
        shaderc_compile_options_set_generate_debug_info(options);
        shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);

        try {
            long result = shaderc_compile_into_spv(compiler, source,
                    toShadercKind(spec.shader.type()),
                    spec.shader.name().toString(), spec.entryPoint, options);

            try {
                if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
                    throw new IllegalStateException(shaderc_result_get_error_message(result));
                }

                ByteBuffer spirv = shaderc_result_get_bytes(result);
                return new BuiltShader(createShaderModule(spirv), toStageBit(spec.shader.type()), spec.entryPoint);
            } finally {
                shaderc_result_release(result);
            }
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    private long createShaderModule(ByteBuffer spirv) {
        try (var stack = MemoryStack.stackPush()) {
            var createInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(spirv);
            LongBuffer pModule = stack.mallocLong(1);
            int res = vkCreateShaderModule(device, createInfo, null, pModule);
            if (res != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateShaderModule failed: " + res);
            }
            return pModule.get(0);
        }
    }

    public VkGraphicsPipelineBuilder setFrontFace(int vkFrontFaceClockwise) {
        this.frontFace = vkFrontFaceClockwise;
        return this;
    }

    private record ShaderWithEntrypoint(ShaderLoader.LoadedShader shader, String entryPoint) {
    }

    private record BuiltShader(long module, int stageBit, String entryPoint) {
    }
}