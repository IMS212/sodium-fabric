package net.caffeinemc.mods.sodium.client.gl.arena.staging;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;

import java.nio.ByteBuffer;

public class FallbackStagingBuffer implements StagingBuffer {
    //private final GpuBuffer fallbackBufferObject;

    public FallbackStagingBuffer(CommandList commandList) {
        //this.fallbackBufferObject = commandList.createMutableBuffer();
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, GpuBuffer dst, long writeOffset) {
        // TODO: confirm
        SodiumClientMod.getCommandEncoder().writeToBuffer(dst.slice((int) writeOffset, data.remaining()), data);
        //commandList.uploadData(this.fallbackBufferObject, data, GlBufferUsage.STREAM_COPY);
        //commandList.copyBufferSubData(this.fallbackBufferObject, dst, 0, writeOffset, data.remaining());
    }

    @Override
    public void flush(CommandList commandList) {
        //commandList.allocateStorage(this.fallbackBufferObject, 0L, GlBufferUsage.STREAM_COPY);
    }

    @Override
    public void delete(CommandList commandList) {
        //commandList.deleteBuffer(this.fallbackBufferObject);
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
