package net.caffeinemc.mods.sodium.client.platform.windows;

import net.caffeinemc.mods.sodium.client.platform.windows.api.Kernel32;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.BufferOverflowException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class WindowsCommandLine {
    private static CommandLineHook ACTIVE_COMMAND_LINE_HOOK;

    public static void setCommandLine(String modifiedCmdline) {
        if (ACTIVE_COMMAND_LINE_HOOK != null) {
            throw new IllegalStateException("Command line is already modified");
        }

        // Pointer into the command-line arguments stored within the Windows process structure
        // We do not own this memory, and it should not be freed.
        MemorySegment pCmdline  = Kernel32.getCommandLine();
        MemorySegment pCmdlineA = Kernel32.getCommandLineA();

        // The original command-line the process was started with.
        String cmdline = pCmdline.getString(0, StandardCharsets.UTF_16LE);
        int cmdlineLen = (cmdline.length() + 1) * 2;

        // refer to lower comment about ANSI :(
        Charset ansi = Charset.defaultCharset();
        String cmdlineA = pCmdlineA.getString(0, ansi);
        int cmdLineLenA = cmdlineA.getBytes(ansi).length + 1;

        byte[] modifiedUtf16 = modifiedCmdline.getBytes(StandardCharsets.UTF_16LE);
        if (modifiedUtf16.length + 2 > cmdlineLen) {
            // We can never write a string which is larger than what we were given, as there
            // may not be enough space remaining. Realistically, this should never happen, since
            // our identifying string is very short, and the command line is *at least* going to contain
            // the class entrypoint.
            throw new BufferOverflowException();
        }

        byte[] modifiedAnsi = modifiedCmdline.getBytes(ansi);
        if (modifiedAnsi.length + 1 > cmdLineLenA) {
            throw new BufferOverflowException();
        }

        // Write the new command line arguments into the process structure.
        // The Windows API documentation explicitly says this is forbidden, but it *does* give us a pointer
        // directly into the PEB structure, so...
        pCmdline.setString(0, modifiedCmdline, StandardCharsets.UTF_16LE);
        pCmdlineA.setString(0, modifiedCmdline, ansi);

        // Make sure we can actually see our changes in the process structure
        // We don't know if this could ever actually happen, but since we're doing something pretty hacky
        // it's not out of the question that Windows might try to prevent it in a newer version.
        if (!Objects.equals(modifiedCmdline, pCmdline.getString(0, StandardCharsets.UTF_16LE))) {
            throw new RuntimeException("Sanity check failed, the command line arguments did not appear to change");
        }
        if (!Objects.equals(modifiedCmdline, pCmdlineA.getString(0, ansi))) {
            throw new RuntimeException("Sanity check failed, the command line arguments did not appear to change");
        }

        ACTIVE_COMMAND_LINE_HOOK = new CommandLineHook(cmdline, cmdlineA, cmdlineLen, cmdLineLenA);

    }

    public static void resetCommandLine() {
        if (ACTIVE_COMMAND_LINE_HOOK != null) {
            ACTIVE_COMMAND_LINE_HOOK.uninstall();
            ACTIVE_COMMAND_LINE_HOOK = null;
        }
    }

    private static class CommandLineHook {
        private final String cmdline;
        private final String cmdlineA;
        private final int cmdlineLen;
        private final int cmdlineLenA;

        private boolean active = true;

        private CommandLineHook(String cmdline, String cmdlineA, int cmdlineLen, int cmdlineLenA) {
            this.cmdline = cmdline;
            this.cmdlineA = cmdlineA;
            this.cmdlineLen = cmdlineLen;
            this.cmdlineLenA = cmdlineLenA;
        }

        public void uninstall() {
            if (!this.active) {
                throw new IllegalStateException("Hook was already uninstalled");
            }

            // Restore the original value of the command line arguments
            // Must be null-terminated (as it was given to us)
            MemorySegment pCmdline = Kernel32.getCommandLine();
            MemorySegment pCmdlineA = Kernel32.getCommandLineA();

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment srcUtf16 =
                        arena.allocateFrom(this.cmdline, StandardCharsets.UTF_16LE);

                pCmdline.asSlice(0, srcUtf16.byteSize())
                        .copyFrom(srcUtf16);

                // UTF-8 is supposedly defaultCharset, from what I can find. However, it seems to work for all cases of GetCommandLineA that matter here.
                // Is it guaranteed? Probably not.
                // Does anything use GetCommandLineA? Probably not!
                // Have I made sure it works...? Yes. Well, as of 26H2. There is that "beta: use UTF-8" feature that might either fix it or break everything.
                // so if you're reading this ten years from now, the answer is: Probably not!
                Charset ansi = Charset.defaultCharset();
                MemorySegment srcAnsi = arena.allocateFrom(this.cmdlineA, ansi);

                pCmdlineA.asSlice(0, srcAnsi.byteSize()).copyFrom(srcAnsi);
            }

            this.active = false;
        }
    }
}
