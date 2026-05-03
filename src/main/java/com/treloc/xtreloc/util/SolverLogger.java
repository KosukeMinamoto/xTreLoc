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
    /** When true, {@code INFO} (and below) are not forwarded to the callback (GUI); file/console unchanged. */
    private static volatile boolean suppressInfoInCallback = false;

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

    /**
     * When {@code true}, {@link #log} does not invoke the GUI callback for {@link Level#INFO}, {@link Level#CONFIG},
     * {@link Level#FINE}, etc. Warnings and errors are still forwarded. Used for multi-event batch runs.
     */
    public static void setSuppressInfoInCallback(boolean suppress) {
        suppressInfoInCallback = suppress;
    }

    public static boolean isSuppressInfoInCallback() {
        return suppressInfoInCallback;
    }

    public static void fine(String msg) {
        log(Level.FINE, msg);
    }

    /** DEBUG-level (FINER) for detailed solver progress. */
    public static void finer(String msg) {
        log(Level.FINER, msg);
    }

    /** TRACE-level (FINEST) for very verbose solver progress. */
    public static void finest(String msg) {
        log(Level.FINEST, msg);
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
            if (suppressInfoInCallback && level.intValue() <= Level.INFO.intValue()) {
                return;
            }
            callback.accept(msg, level);
        }
    }
}
