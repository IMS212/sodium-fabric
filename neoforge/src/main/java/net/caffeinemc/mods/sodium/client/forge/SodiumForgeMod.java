package net.caffeinemc.mods.sodium.client.forge;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod("sodium")
public class SodiumForgeMod {
    public SodiumForgeMod() {
        SodiumClientMod.onInitialization(ModList.get().getModContainerById("sodium").get().getModInfo().getVersion().toString());

        //ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(SpriteFinderCache.ReloadListener.INSTANCE);
    }
}
