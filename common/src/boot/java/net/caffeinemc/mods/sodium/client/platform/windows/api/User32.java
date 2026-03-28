package net.caffeinemc.mods.sodium.client.platform.windows.api;

import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.platform.windows.api.msgbox.MsgBoxParamSw;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.JNI;
import org.lwjgl.system.SharedLibrary;

import static org.lwjgl.system.APIUtil.apiGetFunctionAddress;
import static org.lwjgl.system.APIUtil.apiGetFunctionAddressOptional;

public class User32 {
    private static final SharedLibrary LIBRARY;

    static {
        if (OsUtils.getOs() == OsUtils.OperatingSystem.WIN) {
            LIBRARY = APIUtil.apiCreateLibrary("user32");

            PFN_MessageBoxIndirectW = apiGetFunctionAddress(LIBRARY, "MessageBoxIndirectW");
            PFN_GetKeyboardLayout = apiGetFunctionAddressOptional(LIBRARY, "GetKeyboardLayout");
        } else {
            LIBRARY = null;
            PFN_GetKeyboardLayout = -1;
            PFN_MessageBoxIndirectW = -1;
        }
    }

    private static final long PFN_MessageBoxIndirectW;
    private static final long PFN_GetKeyboardLayout;

    /**
     * @see <a href="https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-messageboxw>Winuser.h Documentation</a>
     */
    public static void callMessageBoxIndirectW(MsgBoxParamSw params) {
        if (PFN_MessageBoxIndirectW == -1) return;

        JNI.callPI(params.address(), PFN_MessageBoxIndirectW);
    }

    public static long callGetKeyboardLayout(int thread) {
        if (PFN_GetKeyboardLayout == -1) return 0;

        return JNI.callPI(thread, PFN_GetKeyboardLayout);
    }
}
