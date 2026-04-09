package net.caffeinemc.mods.sodium.client.render.chunk;

import net.caffeinemc.mods.sodium.client.vk.device.CommandList;

public class SectionDataBuffer {
    public SectionDataBuffer(CommandList commandList, int renderDistance, int minSectionY, int maxSectionY) {
        int diameter = 2 * renderDistance + 1;
        int chunkColumns = diameter * diameter;

        int sectionsPerColumn = maxSectionY - minSectionY + 1;

        int totalSections = chunkColumns * sectionsPerColumn;
    }
}