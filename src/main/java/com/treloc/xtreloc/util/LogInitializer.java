package com.treloc.xtreloc.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.ConfigLoader;

/**
 * Logger initializer for xTreLoc.
 * Uses log rotation to prevent unbounded disk usage: when the current log file
 * reaches the size limit, it is rotated and only the last N files are kept.
 */
public final class LogInitializer {

    private static final String ROOT_LOGGER = "com.treloc.xtreloc";

    /** Maximum size of one log file before rotation (10 MB). */
    private static final int LOG_LIMIT_BYTES = 10 * 1024 * 1024;
    /** Number of rotated log files to keep (5 → total max ~50 MB). */
    private static final int LOG_COUNT = 5;

    private LogInitializer() {
        // utility class
    }

    /**
     * Setup logger using config file
     *
     * @param logFile    log file path
     * @param configPath config.json path (optional, can be null)
     */
    public static void setup(String logFile, String configPath)
            throws IOException {
        Level level = Level.INFO; // Default level
        
        if (configPath != null) {
            try {
                File configFile = new File(configPath);
                if (configFile.exists() && configFile.isFile()) {
                    ConfigLoader config = new ConfigLoader(configPath);
                    String levelStr = config.getConfig().logLevel;
                    if (levelStr != null && !levelStr.isEmpty()) {
                        level = Level.parse(levelStr.toUpperCase());
                    }
                }
            } catch (Exception e) {
                // If config file doesn't exist or can't be loaded, use default level
            }
        }

        setup(logFile, level);
    }

    /**
     * Setup logger with explicit log level and default rotation (10 MB × 5 files).
     *
     * @param logFile log file path (e.g. ~/.xtreloc/xtreloc.log)
     * @param level   log level
     */
    public static void setup(String logFile, Level level) throws IOException {
        setup(logFile, level, LOG_LIMIT_BYTES, LOG_COUNT);
    }

    /**
     * Setup logger with explicit log level and rotation limits.
     * When the log file reaches limitBytes, it is rotated; only the last count files are kept.
     * The main log file path (logFile) is always the active log; rotated files are logFile.1, logFile.2, etc.
     *
     * @param logFile    log file path
     * @param level      log level
     * @param limitBytes max bytes per file before rotation (e.g. 10 * 1024 * 1024 for 10 MB)
     * @param count      number of rotated files to keep (1–20)
     */
    public static void setup(String logFile, Level level, int limitBytes, int count) throws IOException {
        java.util.logging.LogManager.getLogManager().reset();

        Logger root = Logger.getLogger("");
        root.setLevel(level);

        int limit = Math.max(1024 * 1024, Math.min(500 * 1024 * 1024, limitBytes));
        int c = Math.max(1, Math.min(20, count));
        String pattern = new File(logFile).getAbsolutePath().replace("\\", "/");
        FileHandler handler = new FileHandler(pattern, limit, c);
        handler.setLevel(level);
        handler.setFormatter(new LogFormatter());

        root.addHandler(handler);

        Logger appLogger = Logger.getLogger(ROOT_LOGGER);
        appLogger.setLevel(level);
    }

}
