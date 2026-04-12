package com.treloc.xtreloc.app.gui.util;

import javax.swing.SwingUtilities;
import java.util.logging.Level;
import java.util.function.BiConsumer;

/**
 * Routes GUI-wide user/action messages (Viewer, Settings, etc.) to the Solver Execution log with a {@link Level}.
 */
public final class GuiExecutionLog {

    private static volatile BiConsumer<Level, String> sink;

    private GuiExecutionLog() {}

    public static void setSink(BiConsumer<Level, String> sink) {
        GuiExecutionLog.sink = sink;
    }

    public static void log(Level level, String message) {
        if (message == null) {
            return;
        }
        BiConsumer<Level, String> s = sink;
        if (s == null) {
            return;
        }
        Level lv = level != null ? level : Level.INFO;
        if (SwingUtilities.isEventDispatchThread()) {
            s.accept(lv, message);
        } else {
            SwingUtilities.invokeLater(() -> {
                BiConsumer<Level, String> s2 = sink;
                if (s2 != null) {
                    s2.accept(lv, message);
                }
            });
        }
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void config(String message) {
        log(Level.CONFIG, message);
    }

    public static void warning(String message) {
        log(Level.WARNING, message);
    }

    public static void severe(String message) {
        log(Level.SEVERE, message);
    }
}
