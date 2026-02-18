package net.caffeinemc.mods.sodium.client.platform;

import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.platform.windows.api.Shell32;
import net.caffeinemc.mods.sodium.client.platform.windows.api.User32;
import net.caffeinemc.mods.sodium.client.platform.windows.api.msgbox.MsgBoxCallback;
import net.caffeinemc.mods.sodium.client.platform.windows.api.msgbox.MsgBoxParamSw;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class MessageBox {
    private static final @Nullable MessageBoxImpl IMPL = MessageBoxImpl.chooseImpl();

    public static void showMessageBox(NativeWindowHandle window,
                                      IconType icon, String title,
                                      String description,
                                      @Nullable String helpUrl) {
        if (IMPL != null) {
            IMPL.showMessageBox(window, icon, title, description, helpUrl);
        }
    }

    public enum IconType {
        INFO,
        WARNING,
        ERROR
    }

    private interface MessageBoxImpl {
        static @Nullable MessageBoxImpl chooseImpl() {
            if (OsUtils.getOs() == OsUtils.OperatingSystem.WIN) {
                return new WindowsMessageBoxImpl();
            } else {
                // TODO: Tiny File Dialogs is really bad. We need something better.
                //return new TFDMessageBoxImpl();
                return null;
            }
        }

        void showMessageBox(NativeWindowHandle window,
                            IconType icon, String title,
                            String description,
                            @Nullable String helpUrl);
    }

    private static class WindowsMessageBoxImpl implements MessageBoxImpl {
        private static int getStyle(IconType icon, boolean showHelp) {
            int style = switch (icon) {
                case INFO -> 0x00000040; /* MB_ICONINFORMATION */
                case WARNING -> 0x00000030; /* MB_ICONWARNING */
                case ERROR -> 0x00000010; /* MB_ICONERROR */
            };

            if (showHelp) {
                style |= 0x00004000 /* MB_HELP */;
            }

            return style;
        }

        @Override
        public void showMessageBox(NativeWindowHandle window,
                                   IconType icon, String title,
                                   String description,
                                   @Nullable String helpUrl) {
            try (Arena arena = Arena.ofConfined()) {
                Objects.requireNonNull(title);
                Objects.requireNonNull(description);
                Objects.requireNonNull(icon);

                final MsgBoxCallback msgBoxCallback;

                if (helpUrl != null) {
                    msgBoxCallback = MsgBoxCallback.create(arena, lpHelpInfo -> {
                        Shell32.browseUrl(window, helpUrl);
                    });
                } else {
                    msgBoxCallback = null;
                }

                final MemorySegment hWndOwner;

                if (window != null) {
                    hWndOwner = MemorySegment.ofAddress(window.getWin32Handle());
                } else {
                    hWndOwner = MemorySegment.NULL;
                }


                MemorySegment lpText = arena.allocateFrom(description, StandardCharsets.UTF_16LE);
                MemorySegment lpCaption = arena.allocateFrom(title, StandardCharsets.UTF_16LE);

                var params = MsgBoxParamSw.allocate(arena);
                params.setCbSize(MsgBoxParamSw.SIZEOF);
                params.setHWndOwner(hWndOwner);
                params.setText(lpText);
                params.setCaption(lpCaption);
                params.setStyle(getStyle(icon, msgBoxCallback != null));
                params.setCallback(msgBoxCallback.callbackHandle());

                User32.callMessageBoxIndirectW(params);

            }
        }

    }
}
