package net.caffeinemc.mods.sodium.client.render;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;

public class VoxelHelpers {
    public static int to1DInChunk( int x, int y, int z ) {
        int ox = Math.floorMod(x, 16);
        int oy = Math.floorMod(y, 16); // TODO don't hardcode this???
        int oz = Math.floorMod(z, 16);
        return oz * 16 * 16
                + oy * 16
                + ox;
    }

    public static int[] to3DInChunk( int index ) {
        int x = index & 15;
        int y = (index >>> 4) & 15;
        int z = index >>> 8;
        return new int[]{ x, y, z };
    }

    public static long convertSection(int verticalDistance, int diameter, RenderSection section) {
        int ox = Math.floorMod(section.getChunkX(), diameter);
        int oy = Math.floorMod(section.getChunkY() + 4, verticalDistance); // TODO don't hardcode this???
        int oz = Math.floorMod(section.getChunkZ(), diameter);
        return (long)oz * diameter * verticalDistance
                + (long)oy * diameter
                + ox;
    }
}
