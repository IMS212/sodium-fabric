package net.caffeinemc.mods.sodium.client.platform.windows.api;

import net.caffeinemc.mods.sodium.client.platform.windows.api.msgbox.MsgBoxParamSw;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class User32 {
    private static final MethodHandle PFN_MessageBoxIndirectW;

    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup kernel = SymbolLookup.libraryLookup("user32", Arena.global());

        PFN_MessageBoxIndirectW = linker.downcallHandle(kernel.findOrThrow("MessageBoxIndirectW"), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    }

    /**
     * @see <a href="https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-messageboxw>Winuser.h Documentation</a>
     */
    public static void callMessageBoxIndirectW(MsgBoxParamSw params) {
        try {
            int returns = (int) PFN_MessageBoxIndirectW.invokeExact(params.segment());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
