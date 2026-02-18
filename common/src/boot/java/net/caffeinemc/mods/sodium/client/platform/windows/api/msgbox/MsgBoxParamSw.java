package net.caffeinemc.mods.sodium.client.platform.windows.api.msgbox;

import org.jspecify.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class MsgBoxParamSw {
    // The magical padding struct.
    // ...don't touch it.
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("cbSize"),
            MemoryLayout.paddingLayout(4),

            ValueLayout.ADDRESS.withName("hwndOwner"),
            ValueLayout.ADDRESS.withName("hInstance"),
            ValueLayout.ADDRESS.withName("lpszText"),
            ValueLayout.ADDRESS.withName("lpszCaption"),
            ValueLayout.JAVA_INT.withName("dwStyle"),
            MemoryLayout.paddingLayout(4),

            ValueLayout.ADDRESS.withName("lpszIcon"),
            ValueLayout.ADDRESS.withName("dwContextHelpId"),
            ValueLayout.ADDRESS.withName("lpfnMsgBoxCallback"),
            ValueLayout.JAVA_INT.withName("dwLanguageId"),
            MemoryLayout.paddingLayout(4)
            );

    private static final long OFFSET_CB_SIZE = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("cbSize"));
    private static final long OFFSET_HWND_OWNER = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("hwndOwner"));
    private static final long OFFSET_HINSTANCE = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("hInstance"));
    private static final long OFFSET_LPSZ_TEXT = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("lpszText"));
    private static final long OFFSET_LPSZ_CAPTION = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("lpszCaption"));
    private static final long OFFSET_DW_STYLE = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("dwStyle"));
    private static final long OFFSET_LPSZ_ICON = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("lpszIcon"));
    private static final long OFFSET_DW_CONTEXT_HELP_ID = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("dwContextHelpId"));
    private static final long OFFSET_LPFN_MSG_BOX_CALLBACK = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("lpfnMsgBoxCallback"));
    private static final long OFFSET_DW_LANGUAGE_ID = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("dwLanguageId"));

    public static final int SIZEOF = (int) LAYOUT.byteSize();

    private final MemorySegment segment;

    private MsgBoxParamSw(MemorySegment segment) {
        this.segment = segment;
    }

    public static MsgBoxParamSw allocate(Arena arena) {
        MemorySegment seg = arena.allocate(LAYOUT);
        return new MsgBoxParamSw(seg);
    }

    public MemorySegment segment() {
        return segment;
    }

    public void setCbSize(int size) {
        segment.set(ValueLayout.JAVA_INT, OFFSET_CB_SIZE, size);
    }

    public void setHWndOwner(MemorySegment hWnd) {
        segment.set(ValueLayout.ADDRESS, OFFSET_HWND_OWNER, hWnd);
    }

    public void setHInstance(MemorySegment hInstance) {
        segment.set(ValueLayout.ADDRESS, OFFSET_HINSTANCE, hInstance);
    }

    public void setText(MemorySegment text) {
        segment.set(ValueLayout.ADDRESS, OFFSET_LPSZ_TEXT, text);
    }

    public void setCaption(MemorySegment caption) {
        segment.set(ValueLayout.ADDRESS, OFFSET_LPSZ_CAPTION, caption);
    }

    public void setStyle(int style) {
        segment.set(ValueLayout.JAVA_INT, OFFSET_DW_STYLE, style);
    }

    public void setIcon(MemorySegment icon) {
        segment.set(ValueLayout.ADDRESS, OFFSET_LPSZ_ICON, icon);
    }

    public void setContextHelpId(MemorySegment helpId) {
        segment.set(ValueLayout.ADDRESS, OFFSET_DW_CONTEXT_HELP_ID, helpId);
    }

    public void setCallback(@Nullable MemorySegment callback) {
        segment.set(ValueLayout.ADDRESS, OFFSET_LPFN_MSG_BOX_CALLBACK,
                callback == null ? MemorySegment.NULL : callback);
    }

    public void setLanguageId(int langId) {
        segment.set(ValueLayout.JAVA_INT, OFFSET_DW_LANGUAGE_ID, langId);
    }
}
