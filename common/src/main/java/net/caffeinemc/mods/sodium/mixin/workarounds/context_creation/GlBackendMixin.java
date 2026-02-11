package net.caffeinemc.mods.sodium.mixin.workarounds.context_creation;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.opengl.GlBackend;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.amd.AmdWorkarounds;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaWorkarounds;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GlBackend.class)
public class GlBackendMixin {
    @WrapOperation(method = "createDeviceWithWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"), expect = 0, require = 0)
    private long wrapGlfwCreateWindow(int titleEncoded, int width, CharSequence height, long title, long monitor, Operation<Long> original) {
        NvidiaWorkarounds.applyEnvironmentChanges();
        AmdWorkarounds.applyEnvironmentChanges();

        if (!PlatformRuntimeInformation.getInstance().platformHasEarlyLoadingScreen()) {
            if (SodiumClientMod.options().performance.useNoErrorGLContext) {
                if (!Workarounds.isWorkaroundEnabled(Workarounds.Reference.NO_ERROR_CONTEXT_UNSUPPORTED)) {
                    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_NO_ERROR, GLFW.GLFW_TRUE);
                }
            }
        }

        try {
            return original.call(titleEncoded, width, height, title, monitor);
        } finally {
            NvidiaWorkarounds.undoEnvironmentChanges();
            AmdWorkarounds.undoEnvironmentChanges();
        }
    }
}
