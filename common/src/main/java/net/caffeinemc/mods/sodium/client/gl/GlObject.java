package net.caffeinemc.mods.sodium.client.gl;

/**
 * An abstract object used to represent objects in OpenGL code safely. This class hides the direct handle to a OpenGL
 * object, requiring that it first be checked by all callers to prevent null pointer de-referencing. However, this will
 * not stop code from cloning the handle and trying to use it after it has been deleted and as such should not be
 * relied on too heavily.
 */
public abstract class GlObject {
    private static final int INVALID_HANDLE = Integer.MIN_VALUE;


    protected GlObject() {

    }


}
