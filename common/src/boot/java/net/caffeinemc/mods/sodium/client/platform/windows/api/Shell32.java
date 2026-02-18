package net.caffeinemc.mods.sodium.client.platform.windows.api;

import net.caffeinemc.mods.sodium.client.platform.NativeWindowHandle;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Shell32 {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LIBRARY = SymbolLookup.libraryLookup("shell32", Arena.global());

    private static final MethodHandle PFN_ShellExecuteW =
            LIBRARY.find("ShellExecuteW")
                    .map(addr -> LINKER.downcallHandle(
                            addr,
                            FunctionDescriptor.of(
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                    ValueLayout.JAVA_INT
                            )
                    ))
                    .orElse(null);

    public static void browseUrl(@Nullable NativeWindowHandle window, String url) {
        Objects.requireNonNull(url, "URL parameter must be non-null");

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment lpOperation = arena.allocateFrom("open", StandardCharsets.UTF_16LE);
            MemorySegment lpFile = arena.allocateFrom(url, StandardCharsets.UTF_16LE);

            nShellExecuteW(
                    window != null ? MemorySegment.ofAddress(window.getWin32Handle()) : MemorySegment.NULL,
                    lpOperation,
                    lpFile,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    0x1 /* SW_NORMAL */
            );
        }
    }

    public static MemorySegment nShellExecuteW(
            /* HWND */      MemorySegment hwnd,
            /* LPCWSTR */   MemorySegment lpOperation,
            /* LPCWSTR */   MemorySegment lpFile,
            /* LPCWSTR */   MemorySegment lpParameters,
            /* LPCWSTR */   MemorySegment lpDirectory,
            /* INT */       int nShowCmd
    ) {
        try {
            return (MemorySegment) checkPfn(PFN_ShellExecuteW)
                    .invokeExact(hwnd, lpOperation, lpFile, lpParameters, lpDirectory, nShowCmd);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static MethodHandle checkPfn(MethodHandle pfn) {
        if (pfn == null) {
            throw new NullPointerException("Function pointer not available");
        }

        return pfn;
    }
}
