package net.caffeinemc.mods.sodium.client.gl.device;

import graphics.cinnabar.core.vk.memory.VkBuffer;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.gl.array.GlVertexArray;
import net.caffeinemc.mods.sodium.client.gl.buffer.*;
import net.caffeinemc.mods.sodium.client.gl.functions.DeviceFunctions;
import net.caffeinemc.mods.sodium.client.gl.state.GlStateTracker;
import net.caffeinemc.mods.sodium.client.gl.sync.GlFence;
import net.caffeinemc.mods.sodium.client.gl.tessellation.*;
import net.caffeinemc.mods.sodium.client.gl.util.EnumBitField;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;

public class GLRenderDevice implements RenderDevice {
    private final GlStateTracker stateTracker = new GlStateTracker();
    private final CommandList commandList = new ImmediateCommandList(this.stateTracker);
    private final DrawCommandList drawCommandList = new ImmediateDrawCommandList();

    private final DeviceFunctions functions = new DeviceFunctions(this);

    private boolean isActive;
    private GlTessellation activeTessellation;

    @Override
    public CommandList createCommandList() {
        GLRenderDevice.this.checkDeviceActive();

        return this.commandList;
    }

    @Override
    public void makeActive() {
        if (this.isActive) {
            return;
        }

        this.isActive = true;
    }

    @Override
    public void makeInactive() {
        if (!this.isActive) {
            return;
        }

        this.isActive = false;
    }


    @Override
    public DeviceFunctions getDeviceFunctions() {
        return this.functions;
    }

    @Override
    public int getSubTexelPrecisionBits() {
        // OpenGL only specifies "at least" 4 bits of sub-texel precision for texture fetches. Thankfully, nearly every
        // graphics card is Direct3D-compatible and capable of providing 8 bits of precision. The only exception to this
        // rule seems to be when using OpenGL on macOS, where it appears to arbitrarily limit the precision to 4 bits
        // *even if* the hardware is capable of better.
        if (OsUtils.getOs() == OsUtils.OperatingSystem.MAC) {
            return 4;
        }

        return 8;
    }

    private void checkDeviceActive() {
        if (!this.isActive) {
            throw new IllegalStateException("Tried to access device from unmanaged context");
        }
    }

    private class ImmediateCommandList implements CommandList {
        private final GlStateTracker stateTracker;

        private ImmediateCommandList(GlStateTracker stateTracker) {
            this.stateTracker = stateTracker;
        }

        @Override
        public void deleteVertexArray(GlVertexArray vertexArray) {

           // GL30C.glDeleteVertexArrays(handle);
        }

        @Override
        public void flush() {
            // NO-OP
        }

        @Override
        public DrawCommandList beginTessellating(GlTessellation tessellation) {
            GLRenderDevice.this.activeTessellation = tessellation;

            return GLRenderDevice.this.drawCommandList;
        }

        @Override
        public void deleteTessellation(GlTessellation tessellation) {
        }

        @Override
        public void flushMappedRange(GlBufferMapping map, int offset, int length) {
            checkMapDisposed(map);

            VkBuffer buffer = map.getBufferObject();

            try (final var stack = MemoryStack.stackPush()) {
                VK10.vkFlushMappedMemoryRanges(SodiumClientMod.getDevice().vkDevice, VkMappedMemoryRange.calloc(stack).sType$Default().memory(buffer.allocation.memoryHandle).size(length).offset(offset));
            }
        }

        @Override
        public GlFence createFence() {
            return new GlFence(GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));
        }

        @Override
        public void deleteBuffer(VkBuffer srcBufferObj) {
            SodiumClientMod.getDevice().destroyEndOfFrame(srcBufferObj);
        }

        private void checkMapDisposed(GlBufferMapping map) {
            if (map.isDisposed()) {
                throw new IllegalStateException("Buffer mapping is already disposed");
            }
        }

        @Override
        public GlTessellation createTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings) {
            GlVertexArrayTessellation tessellation = new GlVertexArrayTessellation(new GlVertexArray(), primitiveType, bindings);
            tessellation.init(this);

            return tessellation;
        }
    }

    private class ImmediateDrawCommandList implements DrawCommandList {
        public ImmediateDrawCommandList() {

        }

        @Override
        public void multiDrawElementsBaseVertex(MultiDrawBatch batch, GlIndexType indexType) {
            GlPrimitiveType primitiveType = GLRenderDevice.this.activeTessellation.getPrimitiveType();

            EXTMultiDraw.nvkCmdDrawMultiIndexedEXT(SodiumClientMod.getCommandEncoder().mainDrawCommandBuffer,
                    batch.size, batch.info.address0(), 1, 0, VkMultiDrawIndexedInfoEXT.SIZEOF, MemoryUtil.NULL);
        }

        @Override
        public void endTessellating() {
            GLRenderDevice.this.activeTessellation = null;
        }

        @Override
        public void flush() {
            if (GLRenderDevice.this.activeTessellation != null) {
                this.endTessellating();
            }
        }
    }
}
