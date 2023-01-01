package me.jellysquid.mods.sodium.mixin.workarounds.nvidia;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @ModifyArg(method = "getWindowTitle", at = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;<init>(Ljava/lang/String;)V"))
    private String overrideWindowTitle(String str) {
        return "Mіnec\u200Eraft";
    }
}
