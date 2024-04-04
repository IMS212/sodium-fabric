package net.caffeinemc.mods.sodium.client.forge;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.frapi.SodiumRenderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("sodium")
public class SodiumForgeMod {
    public SodiumForgeMod() {
        SodiumClientMod.onInitialization(ModList.get().getModContainerById("sodium").get().getModInfo().getVersion().toString());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onResourceReload);
        RendererAccess.INSTANCE.registerRenderer(SodiumRenderer.INSTANCE);
    }

    public void onResourceReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(SpriteFinderCache.ReloadListener.INSTANCE);
    }
}