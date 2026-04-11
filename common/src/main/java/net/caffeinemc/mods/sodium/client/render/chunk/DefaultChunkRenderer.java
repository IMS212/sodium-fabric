package net.caffeinemc.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gui.SodiumConfigBuilder;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.DefaultShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.BitwiseMath;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.vk.VulkanAccess;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBuffer;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkBufferUsages;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkIndexType;
import net.caffeinemc.mods.sodium.client.vk.buffer.VkMappingType;
import net.caffeinemc.mods.sodium.client.vk.device.CommandList;
import net.caffeinemc.mods.sodium.client.vk.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.vk.pipeline.VkComputePipeline;
import net.caffeinemc.mods.sodium.client.vk.pipeline.VkDescriptorSetLayoutBuilder;
import net.caffeinemc.mods.sodium.client.vk.renderpass.VulkanRenderPass;
import net.caffeinemc.mods.sodium.client.vk.util.EnumBitField;
import net.minecraft.client.Minecraft;
import net.minecraft.data.AtlasIds;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.Iterator;

public class DefaultChunkRenderer extends ShaderChunkRenderer {
    private static final int MODEL_UNASSIGNED = ModelQuadFacing.UNASSIGNED.ordinal();
    private static final int MODEL_POS_X = ModelQuadFacing.POS_X.ordinal();
    private static final int MODEL_POS_Y = ModelQuadFacing.POS_Y.ordinal();
    private static final int MODEL_POS_Z = ModelQuadFacing.POS_Z.ordinal();
    private static final int MODEL_NEG_X = ModelQuadFacing.NEG_X.ordinal();
    private static final int MODEL_NEG_Y = ModelQuadFacing.NEG_Y.ordinal();
    private static final int MODEL_NEG_Z = ModelQuadFacing.NEG_Z.ordinal();

    private static final int QUADS_PER_GROUP = 16;
    private static final int INDICES_PER_GROUP = QUADS_PER_GROUP * 6;
    private static final int VISIBLE_SECTION_STRIDE = 8;
    private static final int INSTANCE_DATA_STRIDE = 32;
    private static final int INDIRECT_CMD_SIZE = 20;
    private static final int MAX_INSTANCES = 1 << 20;

    private final VkComputePipeline cullPipeline;
    private final VkDescriptorSetLayoutBuilder.VkDescriptorSetLayout computeSetLayout;

    private final VkBuffer indexBuffer16Quad;
    private final VkBuffer[] indirectCmdBuffer = new VkBuffer[3];
    private final VkBuffer[] instanceBuffer = new VkBuffer[3];
    private final VkBuffer visibleSectionsStaging;
    private final VkBuffer[] visibleSectionsGpu = new VkBuffer[3];
    private final long visibleSectionsStagingAddr;
    private final int maxVisibleSections;

    public DefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);

        CommandList cmd = device.createCommandList();

        int indexBufSize = INDICES_PER_GROUP * Integer.BYTES;
        VkBuffer indexStaging = cmd.createBuffer(indexBufSize, VkMappingType.CPU_ONLY, EnumBitField.of(VkBufferUsages.TRANSFER_SRC));
        long ptr = indexStaging.getMapping().getMappedData();
        for (int q = 0; q < QUADS_PER_GROUP; q++) {
            int base = q * 4;
            int off = q * 6 * Integer.BYTES;
            MemoryUtil.memPutInt(ptr + off,      base);
            MemoryUtil.memPutInt(ptr + off + 4,  base + 1);
            MemoryUtil.memPutInt(ptr + off + 8,  base + 2);
            MemoryUtil.memPutInt(ptr + off + 12, base + 2);
            MemoryUtil.memPutInt(ptr + off + 16, base + 3);
            MemoryUtil.memPutInt(ptr + off + 20, base);
        }
        this.indexBuffer16Quad = cmd.createBuffer(indexBufSize, VkMappingType.GPU_ONLY, EnumBitField.of(VkBufferUsages.INDEX_BUFFER, VkBufferUsages.TRANSFER_DST));
        cmd.copyBufferToBuffer(indexStaging, indexBuffer16Quad, 0, 0, indexBufSize);
        device.destroyObjectWhenSafe(indexStaging);

        for (int i = 0; i < 3; i++) {
            this.indirectCmdBuffer[i] = cmd.createBuffer(INDIRECT_CMD_SIZE, VkMappingType.GPU_ONLY,
                    EnumBitField.of(VkBufferUsages.INDIRECT_BUFFER, VkBufferUsages.STORAGE_BUFFER, VkBufferUsages.TRANSFER_DST));
        }

        for (int i = 0; i < 3; i++) {
            this.instanceBuffer[i] = cmd.createBuffer((long) MAX_INSTANCES * INSTANCE_DATA_STRIDE, VkMappingType.GPU_ONLY,
                    EnumBitField.of(VkBufferUsages.STORAGE_BUFFER));
        }

        this.maxVisibleSections = 100_000;
        int visBufSize = maxVisibleSections * VISIBLE_SECTION_STRIDE;
        this.visibleSectionsStaging = cmd.createBuffer(visBufSize, VkMappingType.CPU_ONLY, EnumBitField.of(VkBufferUsages.TRANSFER_SRC));
        this.visibleSectionsStagingAddr = visibleSectionsStaging.getMapping().getMappedData();
        for (int i = 0; i < 3; i++) {
            this.visibleSectionsGpu[i] = cmd.createBuffer(visBufSize, VkMappingType.GPU_ONLY,
                    EnumBitField.of(VkBufferUsages.STORAGE_BUFFER, VkBufferUsages.TRANSFER_DST));
        }

        this.computeSetLayout = VkDescriptorSetLayoutBuilder.create(VulkanAccess.getDevice())
                .addBinding(0, VK13.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK13.VK_SHADER_STAGE_COMPUTE_BIT)
                .addBinding(1, VK13.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK13.VK_SHADER_STAGE_COMPUTE_BIT)
                .addBinding(2, VK13.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK13.VK_SHADER_STAGE_COMPUTE_BIT)
                .addBinding(3, VK13.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK13.VK_SHADER_STAGE_COMPUTE_BIT)
                .flags(VK14.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT)
                .build();

        byte[] computeShaderData;
        try (InputStream is = SodiumConfigBuilder.class.getResourceAsStream("/assets/sodium/shaders/instance_cull.spv")) {
            computeShaderData = is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load instance_cull.spv", e);
        }

        this.cullPipeline = new VkComputePipeline(computeShaderData, "instance_cull", computeSetLayout, "cullMain", 4);

        cmd.flush();
    }

    public static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
        int boundsMinX = (chunkX << 4), boundsMaxX = boundsMinX + 16;
        int boundsMinY = (chunkY << 4), boundsMaxY = boundsMinY + 16;
        int boundsMinZ = (chunkZ << 4), boundsMaxZ = boundsMinZ + 16;

        int planes = (1 << MODEL_UNASSIGNED);

        planes |= BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_POS_X;
        planes |= BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_POS_Y;
        planes |= BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_POS_Z;

        planes |= BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_NEG_X;
        planes |= BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_NEG_Y;
        planes |= BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_NEG_Z;

        return planes;
    }

    @Override
    public void render(ChunkRenderMatrices matrices,
                       CommandList commandList,
                       ChunkRenderListIterable renderLists,
                       TerrainRenderPass renderPass,
                       CameraTransform camera,
                       FogParameters parameters,
                       boolean indexedRenderingEnabled,
                       GpuSampler terrainSampler, PageAddressBuffer pageBuf,
                       SectionDataBuffer sectionDataBuffer) {
        if (renderPass.isTranslucent()) {
            return;
        }

        VkCommandBuffer vkCmd = commandList.getCommandBuffer();

        pipelineBarrier(vkCmd,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR
                        | KHRSynchronization2.VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT_KHR
                        | KHRSynchronization2.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_SHADER_READ_BIT_KHR
                        | KHRSynchronization2.VK_ACCESS_2_SHADER_WRITE_BIT_KHR
                        | KHRSynchronization2.VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COPY_BIT_KHR
                        | KHRSynchronization2.VK_PIPELINE_STAGE_2_CLEAR_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR);

        final boolean useBlockFaceCulling = SodiumClientMod.options().performance.useBlockFaceCulling;
        int frameIndex = RenderDevice.INSTANCE.getFrameIndex();

        int visibleCount = 0;
        long visPtr = this.visibleSectionsStagingAddr;

        Iterator<ChunkRenderList> iterator = renderLists.iterator(false);
        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();
            var region = renderList.getRegion();

            if (region.getStorage(renderPass) == null) {
                continue;
            }

            var sectIter = renderList.sectionsWithGeometryIterator(false);
            if (sectIter == null) continue;

            int originX = region.getChunkX();
            int originY = region.getChunkY();
            int originZ = region.getChunkZ();

            while (sectIter.hasNext()) {
                int sectionIndex = sectIter.nextByteAsInt();
                var section = region.getSection(sectionIndex);
                if (section == null) continue;

                int chunkX = originX + LocalSectionIndex.unpackX(sectionIndex);
                int chunkY = originY + LocalSectionIndex.unpackY(sectionIndex);
                int chunkZ = originZ + LocalSectionIndex.unpackZ(sectionIndex);

                int visibleFaces = useBlockFaceCulling
                        ? getVisibleFaces(camera.intX, camera.intY, camera.intZ, chunkX, chunkY, chunkZ)
                        : ModelQuadFacing.ALL;

                if (visibleCount < maxVisibleSections) {
                    long offset = (long) visibleCount * VISIBLE_SECTION_STRIDE;
                    MemoryUtil.memPutInt(visPtr + offset, section.getSectionId());
                    MemoryUtil.memPutInt(visPtr + offset + 4, visibleFaces);
                    visibleCount++;
                }
            }
        }

        if (visibleCount == 0) {
            return;
        }

        VkBuffer visBuf = visibleSectionsGpu[frameIndex];
        VkBuffer indirectBuf = indirectCmdBuffer[frameIndex];

        commandList.copyBufferToBuffer(visibleSectionsStaging, visBuf, 0, 0, (long) visibleCount * VISIBLE_SECTION_STRIDE);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer data = stack.callocInt(5);
            data.put(0, INDICES_PER_GROUP);
            VK13.vkCmdUpdateBuffer(vkCmd, indirectBuf.handle(), 0, data);
        }

        pipelineBarrier(vkCmd,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_SHADER_READ_BIT_KHR | KHRSynchronization2.VK_ACCESS_2_SHADER_WRITE_BIT_KHR);

        VkBuffer sectionDataBuf = sectionDataBuffer.getBuffer();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long pcData = stack.nmalloc(4);
            MemoryUtil.memPutInt(pcData, visibleCount);
            cullPipeline.pushConstants(commandList, pcData, 4);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(4, stack);
            writeStorageBufferDescriptor(writes, stack, 0, sectionDataBuf, VK13.VK_WHOLE_SIZE);
            writeStorageBufferDescriptor(writes, stack, 1, visBuf, VK13.VK_WHOLE_SIZE);
            writeStorageBufferDescriptor(writes, stack, 2, instanceBuffer[frameIndex], VK13.VK_WHOLE_SIZE);
            writeStorageBufferDescriptor(writes, stack, 3, indirectBuf, INDIRECT_CMD_SIZE);
            cullPipeline.pushDescriptor(commandList, writes);
        }

        cullPipeline.dispatch(commandList, (visibleCount + 63) / 64, 1, 1);

        pipelineBarrier(vkCmd,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_SHADER_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT_KHR | KHRSynchronization2.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT_KHR | KHRSynchronization2.VK_ACCESS_2_SHADER_READ_BIT_KHR);

        try (VulkanRenderPass pass = commandList.startRenderPass(VulkanAccess.getView(renderPass.getTarget().getColorTextureView()))) {
            super.begin(pass, renderPass, parameters, terrainSampler);

            ChunkShaderInterface shader = this.activeProgram.getInterface();
            shader.setProjectionMatrix(matrices.projection());
            shader.setModelViewMatrix(matrices.modelView());
            shader.setCameraTransform(camera);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                long pushData = stack.nmalloc(DefaultShaderInterface.PUSH_CONSTANT_SIZE);
                shader.fillPushConstants(pushData);
                pass.pushConstants(this.activeProgram, pushData, DefaultShaderInterface.PUSH_CONSTANT_SIZE);

                VkWriteDescriptorSet.Buffer buf = VkWriteDescriptorSet.calloc(3, stack);
                buf.get(0).sType$Default().dstSet(0).dstBinding(0).descriptorCount(1)
                        .descriptorType(VK13.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).dstArrayElement(0)
                        .pImageInfo(VkDescriptorImageInfo.calloc(1, stack)
                                .imageView(VulkanAccess.getView(Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getTextureView()))
                                .imageLayout(VK13.VK_IMAGE_LAYOUT_GENERAL)
                                .sampler(VulkanAccess.getSampler(RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, true))));
                buf.get(1).sType$Default().dstSet(0).dstBinding(1).descriptorCount(1)
                        .descriptorType(VK13.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).dstArrayElement(0)
                        .pImageInfo(VkDescriptorImageInfo.calloc(1, stack)
                                .imageView(VulkanAccess.getView(Minecraft.getInstance().gameRenderer.lightmap()))
                                .imageLayout(VK13.VK_IMAGE_LAYOUT_GENERAL)
                                .sampler(VulkanAccess.getSampler(RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false))));
                buf.get(2).sType$Default().dstSet(0).dstBinding(2).descriptorCount(1)
                        .descriptorType(VK13.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).dstArrayElement(0)
                        .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack)
                                .buffer(instanceBuffer[frameIndex].handle()).offset(0).range(VK13.VK_WHOLE_SIZE));
                pass.pushDescriptors(this.activeProgram, buf);
            }

            pass.bindIndexBuffer(indexBuffer16Quad, VkIndexType.UNSIGNED_INT);
            pass.drawIndexedIndirect(indirectBuf, 0, 1, INDIRECT_CMD_SIZE);

            super.end(renderPass);
        }
    }

    private static void writeStorageBufferDescriptor(VkWriteDescriptorSet.Buffer writes, MemoryStack stack,
                                                     int binding, VkBuffer buffer, long range) {
        writes.get(binding).sType$Default().dstSet(0).dstBinding(binding).descriptorCount(1)
                .descriptorType(VK13.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).dstArrayElement(0)
                .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(buffer.handle()).offset(0).range(range));
    }

    private static void pipelineBarrier(VkCommandBuffer vkCmd, long srcStage, long srcAccess, long dstStage, long dstAccess) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer barriers = VkMemoryBarrier2.calloc(1, stack);
            barriers.get(0).sType$Default()
                    .srcStageMask(srcStage).srcAccessMask(srcAccess)
                    .dstStageMask(dstStage).dstAccessMask(dstAccess);
            VkDependencyInfo depInfo = VkDependencyInfo.calloc(stack).sType$Default()
                    .pMemoryBarriers(barriers);
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(vkCmd, depInfo);
        }
    }

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);

        commandList.deleteBuffer(indexBuffer16Quad);
        commandList.deleteBuffer(visibleSectionsStaging);

        for (int i = 0; i < 3; i++) {
            commandList.deleteBuffer(indirectCmdBuffer[i]);
            commandList.deleteBuffer(instanceBuffer[i]);
            commandList.deleteBuffer(visibleSectionsGpu[i]);
        }

        cullPipeline.destroy(commandList);
        computeSetLayout.delete();
    }
}
