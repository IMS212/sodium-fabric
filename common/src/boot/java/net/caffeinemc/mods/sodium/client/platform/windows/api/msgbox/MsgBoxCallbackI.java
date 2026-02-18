package net.caffeinemc.mods.sodium.client.platform.windows.api.msgbox;


import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

@FunctionalInterface
public interface MsgBoxCallbackI {
    FunctionDescriptor MSGBOX_CALLBACK_DESC = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS); // LPHELPINFO

    void invoke(MemorySegment lpHelpInfo);
}
