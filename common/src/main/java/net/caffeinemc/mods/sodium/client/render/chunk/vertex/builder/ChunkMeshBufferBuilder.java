package net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder;

import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class ChunkMeshBufferBuilder {
    private final ChunkVertexEncoder encoder;
    private final int[] strides;
    private final long[] writePointers;

    private final int initialCapacity;

    private final MemorySegment[] buffers;
    private int vertexCount;
    private int vertexCapacity;

    private int sectionIndex;

    public ChunkMeshBufferBuilder(ChunkVertexType vertexType, int initialCapacity) {
        this.encoder = vertexType.getEncoder();
        this.strides = vertexType.getVertexBufferStrides();
        this.writePointers = new long[this.strides.length];
        this.buffers = new MemorySegment[this.strides.length];

        this.vertexCapacity = initialCapacity;
        this.initialCapacity = initialCapacity;
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        this.push(vertices, material.bits());
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, int materialBits) {
        if (vertices.length != 4) {
            throw new IllegalArgumentException("Only quad primitives (with 4 vertices) can be pushed");
        }

        this.ensureCapacity(4);

        for (int i = 0; i < this.buffers.length; i++) {
            this.writePointers[i] = this.buffers[i].address() + ((long) this.vertexCount * this.strides[i]);
        }

        this.encoder.write(this.writePointers, materialBits, vertices, this.sectionIndex);
        this.vertexCount += 4;
    }

    public void writeExternal(ByteBuffer[] buffers, int position, ChunkVertexEncoder.Vertex[] vertices, Material material) {
        for (int i = 0; i < buffers.length; i++) {
            this.writePointers[i] = MemoryUtil.memAddress(buffers[i], position * this.strides[i]);
        }

        this.encoder.write(this.writePointers, material.bits(), vertices, this.sectionIndex);
    }

    private void ensureCapacity(int vertexCount) {
        if (this.vertexCount + vertexCount >= this.vertexCapacity) {
            this.grow(vertexCount);
        }
    }

    private void grow(int vertexCount) {
        this.reallocate(
                // The new capacity will at least twice as large
                Math.max(this.vertexCapacity * 2, this.vertexCapacity + vertexCount)
        );
    }

    private void reallocate(int vertexCount) {
        for (int i = 0; i < this.buffers.length; i++) {
            var buffer = this.buffers[i];
            var length = (long) vertexCount * this.strides[i];
            this.buffers[i] = MemorySegment.ofAddress(MemoryUtil.nmemRealloc(buffer == null ? 0L : buffer.address(), length)).reinterpret(length);
        }

        this.vertexCapacity = vertexCount;
    }

    public void start(int sectionIndex) {
        this.vertexCount = 0;
        this.sectionIndex = sectionIndex;

        this.reallocate(this.initialCapacity);
    }

    public void destroy() {
        for (int i = 0; i < this.buffers.length; i++) {
            if (this.buffers[i] != null) {
                MemoryUtil.nmemFree(this.buffers[i].address());
            }

            this.buffers[i] = null;
        }
    }

    public boolean isEmpty() {
        return this.vertexCount == 0;
    }

    public ByteBuffer slice() {
        return this.slice(0);
    }

    public ByteBuffer slice(int bufferIndex) {
        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        return this.buffers[bufferIndex].asSlice(0, (long) this.strides[bufferIndex] * this.vertexCount).asByteBuffer();
    }

    public int count() {
        return this.vertexCount;
    }
}
