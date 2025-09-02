package net.caffeinemc.sodium.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadViewMutable;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.render.SodiumShadeMode;
import net.caffeinemc.sodium.frapi.DuckModelViewMutable;
import net.caffeinemc.sodium.frapi.ExtendedQuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.renderer.v1.mesh.ShadeMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;


@Mixin(MutableQuadViewImpl.class)
public abstract class MutableQuadViewImplMixin implements QuadEmitter, ExtendedQuadEmitter, MutableQuadView {
    private DuckModelViewMutable sodium$duck;

    @Shadow
    public abstract void clear();

    @Shadow
    protected abstract void emitDirectly();

    @Shadow
    public abstract void internalShadeMode(SodiumShadeMode mode);

    private static final QuadTransform NO_TRANSFORM = q -> true;

    @Override
    public QuadEmitter getDuck() {
        if (sodium$duck == null) {
            sodium$duck = new DuckModelViewMutable((MutableQuadViewImpl) (Object) this);
        }

        return sodium$duck;
    }

    protected QuadTransform activeTransform = NO_TRANSFORM;
    private final ObjectArrayList<QuadTransform> transformStack = new ObjectArrayList<>();
    private final QuadTransform stackTransform = q -> {
        int i = transformStack.size() - 1;

        while (i >= 0) {
            if (!transformStack.get(i--).transform(q)) {
                return false;
            }
        }

        return true;
    };

    @Override
    public void pushTransform(QuadTransform transform) {
        if (transform == null) {
            throw new NullPointerException("QuadTransform cannot be null!");
        }

        transformStack.push(transform);

        if (transformStack.size() == 1) {
            activeTransform = transform;
        } else if (transformStack.size() == 2) {
            activeTransform = stackTransform;
        }
    }

    @Override
    public void popTransform() {
        transformStack.pop();

        if (transformStack.isEmpty()) {
            activeTransform = NO_TRANSFORM;
        } else if (transformStack.size() == 1) {
            activeTransform = transformStack.getFirst();
        }
    }

    /**
     * Apply transforms and then if transforms return true, emit the quad without clearing the underlying data.
     */
    @Override
    public final void transformAndEmit() {
        if (activeTransform.transform(getDuck())) {
            emitDirectly();
        }
    }

    @Overwrite
    public void emitWithTransformers() {
        transformAndEmit();
    }

    @Override
    public QuadEmitter shadeMode(ShadeMode mode) {
        internalShadeMode(mode == ShadeMode.ENHANCED ? SodiumShadeMode.ENHANCED : SodiumShadeMode.STANDARD);
        return this;
    }

    @Override
    public final QuadEmitter emit() {
        transformAndEmit();
        clear();
        return this;
    }


}
