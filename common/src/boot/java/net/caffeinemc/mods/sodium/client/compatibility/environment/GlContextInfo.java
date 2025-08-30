package net.caffeinemc.mods.sodium.client.compatibility.environment;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11C;

import java.util.Objects;

public record GlContextInfo(String vendor, String renderer, String version) {
    public static GlContextInfo create() {

        return new GlContextInfo("NVIDIA", "4.6", "1");
    }
}
