package net.caffeinemc.mods.sodium.client.platform.windows.api;

import org.jspecify.annotations.Nullable;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Kernel32 {
    private static final int MAX_PATH = 32767;

    private static final int GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT = 1 << 0;
    private static final int GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS = 1 << 2;

    private static final MethodHandle PFN_GetCommandLineW;
    private static final MethodHandle PFN_GetCommandLineA;
    private static final MethodHandle PFN_SetEnvironmentVariableW;

    private static final MethodHandle PFN_GetModuleHandleExW;
    private static final MethodHandle PFN_GetLastError;

    private static final MethodHandle PFN_GetModuleFileNameW;


    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup kernel = SymbolLookup.libraryLookup("kernel32", Arena.global());

        PFN_GetCommandLineW = linker.downcallHandle(kernel.findOrThrow("GetCommandLineW"), FunctionDescriptor.of(ValueLayout.ADDRESS));
        PFN_GetCommandLineA = linker.downcallHandle(kernel.findOrThrow("GetCommandLineA"), FunctionDescriptor.of(ValueLayout.ADDRESS));
        PFN_SetEnvironmentVariableW = linker.downcallHandle(kernel.findOrThrow("SetEnvironmentVariableW"), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        PFN_GetModuleHandleExW = linker.downcallHandle(kernel.findOrThrow("GetModuleHandleExW"), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        PFN_GetLastError = linker.downcallHandle(kernel.findOrThrow("GetLastError"), FunctionDescriptor.of(ValueLayout.JAVA_INT));
        PFN_GetModuleFileNameW = linker.downcallHandle(kernel.findOrThrow("GetModuleFileNameW"), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    }

    public static void setEnvironmentVariable(String name, @Nullable String value) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nameSeg = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
            MemorySegment valueSeg = value != null ? arena.allocateFrom(value, StandardCharsets.UTF_16LE) : MemorySegment.NULL;

            int result = (int) PFN_SetEnvironmentVariableW.invokeExact(nameSeg, valueSeg);

            if (result == 0) {
                throw new RuntimeException("SetEnvironmentVariableW failed, error=" + getLastError());
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static MemorySegment getCommandLine() {
        try {
            return ((MemorySegment) PFN_GetCommandLineW.invokeExact()).reinterpret(Long.MAX_VALUE);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static MemorySegment getCommandLineA() {
        try {
            return ((MemorySegment) PFN_GetCommandLineA.invokeExact()).reinterpret(Long.MAX_VALUE);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static MemorySegment getModuleHandleByNames(String[] names) {
        for (String name : names) {
            var handle = getModuleHandleByName(name);

            if (handle != MemorySegment.NULL) {
                return handle;
            }
        }

        throw new RuntimeException("Could not obtain handle of module");
    }

    public static MemorySegment getModuleHandleByName(String name) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment moduleName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
            MemorySegment moduleReturn = arena.allocate(ValueLayout.ADDRESS);

            int result = (int) PFN_GetModuleHandleExW.invokeExact(GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT, moduleName, moduleReturn);

            if (result == 0) {
                int error = getLastError();
                if (error == 126) { // ERROR_MOD_NOT_FOUND
                    return MemorySegment.NULL;
                } else {
                    throw new RuntimeException("GetModuleHandleExW failed, error=" + error);
                }
            }

            return moduleReturn.get(ValueLayout.ADDRESS, 0);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static String getModuleFileName(MemorySegment phModule) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment fileName = arena.allocate(MAX_PATH * 2L); // this wasn't in the last version, but afaik technically needed since WCHAR is 2 bytes

            int length = (int) PFN_GetModuleFileNameW.invokeExact(phModule, fileName, MAX_PATH);

            if (length == 0) {
                throw new RuntimeException("GetModuleFileNameW failed, error=" + getLastError());
            }

            // can't use getstring since it technically doesn't need to be null terminated?
            byte[] data = fileName.reinterpret(length * 2L).toArray(ValueLayout.JAVA_BYTE);

            return new String(data, StandardCharsets.UTF_16LE);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static int getLastError() {
        try {
            return (int) PFN_GetLastError.invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
