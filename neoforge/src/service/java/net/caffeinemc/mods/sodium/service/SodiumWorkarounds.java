package net.caffeinemc.mods.sodium.service;

import net.caffeinemc.mods.sodium.client.compatibility.checks.EarlyDriverScanner;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaWorkarounds;

public class SodiumWorkarounds {
    public String name() {
        return "sodium";
    }

    public void bootstrap(String[] arguments) {
        GraphicsAdapterProbe.findAdapters();
        EarlyDriverScanner.scanDrivers();
        Workarounds.init();
        final boolean applyNvidiaWorkarounds = Workarounds.isWorkaroundEnabled(Workarounds.Reference.NVIDIA_THREADED_OPTIMIZATIONS);

        if (applyNvidiaWorkarounds) {
            System.out.println("[Sodium] Applying NVIDIA workarounds earlier on Forge.");
            NvidiaWorkarounds.install();
        }
    }
}
