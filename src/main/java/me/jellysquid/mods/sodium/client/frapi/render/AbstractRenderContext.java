package me.jellysquid.mods.sodium.client.frapi.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;

import java.util.function.Consumer;

public abstract class AbstractRenderContext implements RenderContext {
    private static final QuadTransform NO_TRANSFORM = q -> true;

    private QuadTransform activeTransform = NO_TRANSFORM;
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

    @Deprecated
    private final Consumer<Mesh> meshConsumer = mesh -> mesh.outputTo(getEmitter());

    protected final boolean transform(MutableQuadView q) {
        return activeTransform.transform(q);
    }

    protected boolean hasTransform() {
        return activeTransform != NO_TRANSFORM;
    }

    @Override
    public void pushTransform(QuadTransform transform) {
        if (transform == null) {
            throw new NullPointerException("Renderer received null QuadTransform.");
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

        if (transformStack.size() == 0) {
            activeTransform = NO_TRANSFORM;
        } else if (transformStack.size() == 1) {
            activeTransform = transformStack.get(0);
        }
    }

    // Overridden to prevent allocating a lambda every time this method is called.
    @Deprecated
    @Override
    public Consumer<Mesh> meshConsumer() {
        return meshConsumer;
    }
}
