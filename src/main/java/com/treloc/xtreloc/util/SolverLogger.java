package com.treloc.xtreloc.util;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Unified logging system for solvers.
 * Routes logs to appropriate outputs based on context:
 * - GUI: AppSettings/Execution log
 * - CLI: System.out/System.err
 * - TUI: TUI log system
 * 
 * All logs are also written to the Java logger (file).
 */
public class SolverLogger {
    private static final Logger fileLogger = Logger.getLogger(SolverLogger.class.getName());
    
    private static volatile LogCallback callback = null;
    private static volatile boolean isGUIMode = false;
    private static volatile boolean isCLIMode = false;
    private static volatile boolean isTUIMode = false;
    
    /**
     * Interface for receiving log messages from solvers.
     * Implementations can route logs to GUI, CLI, TUI, or other destinations.
     */
    @FunctionalInterface
    public interface LogCallback {
        void log(String message, Level level);
    }
    
    /**
     * Set the log callback for GUI/CLI/TUI integration.
     */
    public static void setCallback(LogCallback cb) {
        callback = cb;
    }
    
    /**
     * Set mode indicators.
     */
    public static void setMode(boolean gui, boolean cli, boolean tui) {
        isGUIMode = gui;
        isCLIMode = cli;
        isTUIMode = tui;
    }
    
    /**
     * Log an info message.
     * @param message the message to log
     */
    public static void info(String message) {
        log(message, Level.INFO);
    }
    
    /**
     * Log a warning message.
     * @param message the message to log
     */
    public static void warning(String message) {
        log(message, Level.WARNING);
    }
    
    /**
     * Log a severe error message.
     * @param message the message to log
     */
    public static void severe(String message) {
        log(message, Level.SEVERE);
    }
    
    /**
     * Log a fine-level debug message.
     * @param message the message to log
     */
    public static void fine(String message) {
        log(message, Level.FINE);
    }
    
    /**
     * Universal log method.
     */
    public static void log(String message, Level level) {
        if (message == null || message.isEmpty()) {
            return;
        }
        
        // Always log to file
        fileLogger.log(level, message);
        
        // Route to UI/CLI based on context
        if (callback != null) {
            try {
                callback.log(message, level);
            } catch (Exception e) {
                // Fallback if callback fails
                fallbackLog(message, level);
            }
        } else {
            fallbackLog(message, level);
        }
    }
    
    /**
     * Fallback logging for when no callback is set.
     */
    private static void fallbackLog(String message, Level level) {
        if (isCLIMode) {
            // CLI: print to stdout/stderr
            if (level.intValue() >= Level.WARNING.intValue()) {
                System.err.println("[" + level.getLocalizedName() + "] " + message);
            } else {
                System.out.println(message);
            }
        } else if (isGUIMode || isTUIMode) {
            // GUI/TUI: just print to stdout (will be captured by UI)
            System.out.println(message);
        } else {
            // Default: stdout
            System.out.println(message);
        }
    }
    
    /**
     * Log with a throwable.
     */
    public static void log(String message, Level level, Throwable throwable) {
        if (message == null || message.isEmpty()) {
            return;
        }
        
        // Always log to file
        fileLogger.log(level, message, throwable);
        
        // Route to UI/CLI
        String fullMessage = message;
        if (throwable != null) {
            fullMessage += "\n" + getStackTrace(throwable);
        }
        
        if (callback != null) {
            try {
                callback.log(fullMessage, level);
            } catch (Exception e) {
                fallbackLog(fullMessage, level);
            }
        } else {
            fallbackLog(fullMessage, level);
        }
    }
    
    /**
     * Get stack trace from throwable.
     */
    private static String getStackTrace(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
