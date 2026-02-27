package net.caffeinemc.mods.sodium.client.vk;

/**
 * An abstract object used to represent objects in Vulkan code safely. This class hides the direct handle to a Vulkan
 * object, requiring that it first be checked by all callers to prevent null pointer de-referencing. However, this will
 * not stop code from cloning the handle and trying to use it after it has been deleted and as such should not be
 * relied on too heavily.
 */
public abstract class VkObject {
    private static final long INVALID_HANDLE = 0;

    private long handle = INVALID_HANDLE;

    protected VkObject() {

    }

    protected final void setHandle(long handle) {
        this.handle = handle;
    }

    public final long handle() {
        this.checkHandle();

        return this.handle;
    }

    protected final void checkHandle() {
        if (!this.isHandleValid()) {
            throw new IllegalStateException("Handle is not valid");
        }
    }

    protected final boolean isHandleValid() {
        return this.handle != INVALID_HANDLE;
    }

    public final void invalidateHandle() {
        this.handle = INVALID_HANDLE;
    }
}
