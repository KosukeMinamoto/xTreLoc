package com.treloc.xtreloc.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central logger for solver progress messages.
 * Supports optional file logging, console output, and a callback (e.g. for TUI).
 */
public final class SolverLogger {

    private static final Logger LOG = Logger.getLogger("com.treloc.xtreloc.solver");

    private static boolean logToFile = true;
    private static boolean logToConsole = false;
    private static boolean useCallback = false;
    private static java.util.function.BiConsumer<String, Level> callback;

    private SolverLogger() {
    }

    public static void setMode(boolean toFile, boolean toConsole, boolean callbackEnabled) {
        logToFile = toFile;
        logToConsole = toConsole;
        useCallback = callbackEnabled;
    }

    public static void setCallback(java.util.function.BiConsumer<String, Level> cb) {
        callback = cb;
    }

    public static void fine(String msg) {
        log(Level.FINE, msg);
    }

    public static void info(String msg) {
        log(Level.INFO, msg);
    }

    public static void warning(String msg) {
        log(Level.WARNING, msg);
    }

    public static void severe(String msg) {
        log(Level.SEVERE, msg);
    }

    private static void log(Level level, String msg) {
        if (logToFile) {
            LOG.log(level, msg);
        }
        if (logToConsole) {
            System.out.println("[" + level.getName() + "] " + msg);
        }
        if (useCallback && callback != null) {
            callback.accept(msg, level);
        }
    }
}
