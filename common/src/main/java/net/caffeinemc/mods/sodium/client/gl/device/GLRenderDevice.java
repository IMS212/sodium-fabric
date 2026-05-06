package net.caffeinemc.mods.sodium.client.gl.device;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.gl.buffer.*;
import net.caffeinemc.mods.sodium.client.gl.sync.GlFence;
import net.caffeinemc.mods.sodium.client.gl.util.EnumBitField;
import net.caffeinemc.mods.sodium.mixin.core.GlBufferAccessor;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class GLRenderDevice implements RenderDevice {
    private final CommandList commandList = new ImmediateCommandList();


    private boolean isActive;

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
    public GLCapabilities getCapabilities() {
        return GL.getCapabilities();
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

        private ImmediateCommandList() {
        }

        @Override
        public void flush() {
            // NO-OP
        }

        @Override
        public void flushMappedRange(GpuBufferSlice.MappedView map, int start, int length) {
            //int handle = ((GlBufferAccessor) map.slice().buffer()).getHandle();
            //GL46C.glFlushMappedNamedBufferRange(handle, start, length);
        }
    }
}
