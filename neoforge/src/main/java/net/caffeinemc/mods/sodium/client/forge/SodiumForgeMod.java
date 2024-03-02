package net.caffeinemc.mods.sodium.client.forge;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod("sodium")
public class SodiumForgeMod {
    public SodiumForgeMod(IEventBus bus) {
        SodiumClientMod.onInitialization(ModList.get().getModContainerById("sodium").get().getModInfo().getVersion().toString());
        bus.addListener(this::onResourceReload);
    }

    public void onResourceReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(SpriteFinderCache.ReloadListener.INSTANCE);
    }
}