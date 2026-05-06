package net.caffeinemc.mods.sodium.client.gl.arena.staging;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;

import java.nio.ByteBuffer;

public class FallbackStagingBuffer implements StagingBuffer {
    public FallbackStagingBuffer(CommandList commandList) {
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, GpuBuffer dst, long writeOffset) {
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(dst.slice(writeOffset, data.remaining()), data);
    }

    @Override
    public void flush(CommandList commandList) {
    }

    @Override
    public void delete(CommandList commandList) {
    }

    @Override
    public void flip() {

    }

    @Override
    public String toString() {
        return "Fallback";
    }

    @Override
    public long getUploadSizeLimit(long frameDuration) {
        return Long.MAX_VALUE; // No limit for fallback buffer since time-liming takes care of it
    }
}
