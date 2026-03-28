package net.caffeinemc.mods.sodium.client.platform.windows.api;

public class Imm32 {
   //private static final MethodHandle IsIME;

    static {
        //Linker linker = Linker.nativeLinker();
        //SymbolLookup imm32 = SymbolLookup.libraryLookup("imm32", Arena.global());
        //IsIME = linker.downcallHandle(imm32.find("ImmIsIME").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    }

    public static boolean CheckIMEStatus() {
        long handle = User32.callGetKeyboardLayout(0);
        int langId = (int)(handle & 0xFFFF);

        // ImmIsIME does not return a sensible result for this, sadly. Maybe it will some day. But it returns true almost all the time right now.
        return isImeLanguage(langId);
    }

    public static boolean isImeLanguage(int langId) {
        return langId == 2052 /* zh-CN */ || langId == 1028 /* zh-TW */ || langId == 3076 /* zh-HK */ || langId == 4100 /* zh-SG */ || langId == 1041 /* ja-JP */ || langId == 1042 /* ko-KR */;
    }
}
