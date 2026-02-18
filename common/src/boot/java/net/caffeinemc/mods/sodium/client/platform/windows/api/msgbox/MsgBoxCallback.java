package net.caffeinemc.mods.sodium.client.platform.windows.api.msgbox;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MsgBoxCallback {
    private final MemorySegment callbackHandle;
    private static final MethodHandle TARGET;

    static {
        try {
            TARGET = MethodHandles.lookup()
                    .findVirtual(MsgBoxCallbackI.class, "invoke", MethodType.methodType(void.class, MemorySegment.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private MsgBoxCallback(MemorySegment callbackHandle) {
        this.callbackHandle = callbackHandle;
    }

    public MemorySegment callbackHandle() {
        return callbackHandle;
    }

    public static MsgBoxCallback create(Arena arena, MsgBoxCallbackI instance) {
        return new MsgBoxCallback(Linker.nativeLinker().upcallStub(TARGET.bindTo(instance), MsgBoxCallbackI.MSGBOX_CALLBACK_DESC, arena));
    }
}
