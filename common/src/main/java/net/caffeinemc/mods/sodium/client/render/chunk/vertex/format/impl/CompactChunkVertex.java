package net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl;

import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.vk.attribute.VkVertexFormat;
import net.minecraft.util.Mth;

public class CompactChunkVertex implements ChunkVertexType {
    // One packed quad record:
    // 506 bits used, rounded up to 512 bits = 64 bytes
    public static final int STRIDE = 64;

    public static final int POSITION_MAX_VALUE = 1 << 20;
    public static final int TEXTURE_MAX_VALUE = 1 << 15;

    private static final float MODEL_ORIGIN = 8.0f;
    private static final float MODEL_RANGE = 32.0f;

    // 14-bit signed fixed-point offsets from vertex 0, using Q2.12.
    // Range: [-2.0, 1.999755859375]
    private static final int RELATIVE_POSITION_BITS = 14;
    private static final int RELATIVE_POSITION_FRACTION_BITS = 12;
    private static final int RELATIVE_POSITION_ONE = 1 << RELATIVE_POSITION_FRACTION_BITS;
    private static final int RELATIVE_POSITION_MIN = -(2 << RELATIVE_POSITION_FRACTION_BITS);      // -8192
    private static final int RELATIVE_POSITION_MAX =  (2 << RELATIVE_POSITION_FRACTION_BITS) - 1;  //  8191

    // Bit offsets
    private static final int POS0_X_OFFSET = 0;
    private static final int POS0_Y_OFFSET = 20;
    private static final int POS0_Z_OFFSET = 40;

    private static final int POS1_X_OFFSET = 60;
    private static final int POS1_Y_OFFSET = 74;
    private static final int POS1_Z_OFFSET = 88;

    private static final int POS2_X_OFFSET = 102;
    private static final int POS2_Y_OFFSET = 116;
    private static final int POS2_Z_OFFSET = 130;

    private static final int POS3_X_OFFSET = 144;
    private static final int POS3_Y_OFFSET = 158;
    private static final int POS3_Z_OFFSET = 172;

    private static final int COLOR0_OFFSET = 186;
    private static final int COLOR1_OFFSET = 218;
    private static final int COLOR2_OFFSET = 250;
    private static final int COLOR3_OFFSET = 282;

    private static final int UV0_OFFSET = 314;
    private static final int UV1_OFFSET = 346;
    private static final int UV2_OFFSET = 378;
    private static final int UV3_OFFSET = 410;

    private static final int LIGHT0_OFFSET = 442;
    private static final int LIGHT1_OFFSET = 456;
    private static final int LIGHT2_OFFSET = 470;
    private static final int LIGHT3_OFFSET = 484;
    private static final int ID_OFFSET = 498;
    private static final VkVertexFormat EMPTY = VkVertexFormat.builder(16).build();

    @Override
    public VkVertexFormat getVertexFormat() {
        return EMPTY;
    }

    @Override
    public ChunkVertexEncoder getEncoder() {
        return (ptr, materialBits, vertices, section) -> {
            // materialBits/section are intentionally unused:
            // this bit layout has no space for them.

            var v0 = vertices[0];
            var v1 = vertices[1];
            var v2 = vertices[2];
            var v3 = vertices[3];

            float texCentroidU = 0.0f;
            float texCentroidV = 0.0f;

            for (var vertex : vertices) {
                texCentroidU += vertex.u;
                texCentroidV += vertex.v;
            }

            texCentroidU *= 0.25f;
            texCentroidV *= 0.25f;

            int pos0x = quantizePosition(v0.x);
            int pos0y = quantizePosition(v0.y);
            int pos0z = quantizePosition(v0.z);

            int pos1x = encodeRelativePosition(v1.x - v0.x);
            int pos1y = encodeRelativePosition(v1.y - v0.y);
            int pos1z = encodeRelativePosition(v1.z - v0.z);

            int pos2x = encodeRelativePosition(v2.x - v0.x);
            int pos2y = encodeRelativePosition(v2.y - v0.y);
            int pos2z = encodeRelativePosition(v2.z - v0.z);

            int pos3x = encodeRelativePosition(v3.x - v0.x);
            int pos3y = encodeRelativePosition(v3.y - v0.y);
            int pos3z = encodeRelativePosition(v3.z - v0.z);

            int color0 = ColorARGB.mulRGB(v0.color, v0.ao);
            int color1 = ColorARGB.mulRGB(v1.color, v1.ao);
            int color2 = ColorARGB.mulRGB(v2.color, v2.ao);
            int color3 = ColorARGB.mulRGB(v3.color, v3.ao);

            int uv0 = packTexture(encodeTexture(texCentroidU, v0.u), encodeTexture(texCentroidV, v0.v));
            int uv1 = packTexture(encodeTexture(texCentroidU, v1.u), encodeTexture(texCentroidV, v1.v));
            int uv2 = packTexture(encodeTexture(texCentroidU, v2.u), encodeTexture(texCentroidV, v2.v));
            int uv3 = packTexture(encodeTexture(texCentroidU, v3.u), encodeTexture(texCentroidV, v3.v));

            int light0 = encodeLight(v0.light);
            int light1 = encodeLight(v1.light);
            int light2 = encodeLight(v2.light);
            int light3 = encodeLight(v3.light);

            long[] words = new long[8];

            packBits(words, POS0_X_OFFSET, 20, pos0x);
            packBits(words, POS0_Y_OFFSET, 20, pos0y);
            packBits(words, POS0_Z_OFFSET, 20, pos0z);

            packBits(words, POS1_X_OFFSET, 14, pos1x);
            packBits(words, POS1_Y_OFFSET, 14, pos1y);
            packBits(words, POS1_Z_OFFSET, 14, pos1z);

            packBits(words, POS2_X_OFFSET, 14, pos2x);
            packBits(words, POS2_Y_OFFSET, 14, pos2y);
            packBits(words, POS2_Z_OFFSET, 14, pos2z);

            packBits(words, POS3_X_OFFSET, 14, pos3x);
            packBits(words, POS3_Y_OFFSET, 14, pos3y);
            packBits(words, POS3_Z_OFFSET, 14, pos3z);

            packBits(words, COLOR0_OFFSET, 32, color0);
            packBits(words, COLOR1_OFFSET, 32, color1);
            packBits(words, COLOR2_OFFSET, 32, color2);
            packBits(words, COLOR3_OFFSET, 32, color3);

            packBits(words, UV0_OFFSET, 32, uv0);
            packBits(words, UV1_OFFSET, 32, uv1);
            packBits(words, UV2_OFFSET, 32, uv2);
            packBits(words, UV3_OFFSET, 32, uv3);
            packBits(words, LIGHT0_OFFSET, 14, light0);
            packBits(words, LIGHT1_OFFSET, 14, light1);
            packBits(words, LIGHT2_OFFSET, 14, light2);
            packBits(words, LIGHT3_OFFSET, 14, light3);
            packBits(words, ID_OFFSET, 8, section);
            writeWords(ptr, words);

            return ptr + STRIDE;
        };
    }

    private static void writeWords(long ptr, long[] words) {
        for (int i = 0; i < words.length; i++) {
            long word = words[i];
            long base = ptr + (i * 8L);

            MemoryIntrinsics.putInt(base + 0L, (int) word);
            MemoryIntrinsics.putInt(base + 4L, (int) (word >>> 32));
        }
    }

    private static void packBits(long[] words, int bitOffset, int bitCount, long value) {
        long masked = value & bitMask(bitCount);

        int wordIndex = bitOffset >>> 6;
        int wordBit = bitOffset & 63;

        words[wordIndex] |= masked << wordBit;

        if (wordBit + bitCount > 64) {
            words[wordIndex + 1] |= masked >>> (64 - wordBit);
        }
    }

    private static long bitMask(int bitCount) {
        return (1L << bitCount) - 1L;
    }

    private static int quantizePosition(float position) {
        return ((int) (normalizePosition(position) * POSITION_MAX_VALUE)) & 0xFFFFF;
    }

    private static float normalizePosition(float v) {
        return (MODEL_ORIGIN + v) / MODEL_RANGE;
    }

    private static int encodeRelativePosition(float delta) {
        int quantized = Math.round(delta * RELATIVE_POSITION_ONE);
        quantized = Mth.clamp(quantized, RELATIVE_POSITION_MIN, RELATIVE_POSITION_MAX);

        // Store as 14-bit two's-complement.
        return quantized & ((1 << RELATIVE_POSITION_BITS) - 1);
    }

    private static int packTexture(int u, int v) {
        return ((u & 0xFFFF) << 0) | ((v & 0xFFFF) << 16);
    }

    private static int encodeTexture(float center, float x) {
        int bias = (x < center) ? 1 : -1;
        int quantized = Math.round(x * TEXTURE_MAX_VALUE) + bias;
        return (quantized & 0x7FFF) | (sign(bias) << 15);
    }

    private static int encodeLight(int light) {
        int sky   = Mth.clamp(((light >>> 16) & 0xFF) + 8, 8, 248) >>> 1;
        int block = Mth.clamp(((light >>>  0) & 0xFF) + 8, 8, 248) >>> 1;
        return (block << 0) | (sky << 7);
    }

    private static int sign(int x) {
        return x >>> 31;
    }
}