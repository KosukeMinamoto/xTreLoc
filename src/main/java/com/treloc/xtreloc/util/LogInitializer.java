package com.treloc.xtreloc.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.ConfigLoader;

/**
 * Logger initializer for xTreLoc.
 * Uses log rotation to prevent unbounded disk usage: when the current log file
 * reaches the size limit, it is rotated and only the last N files are kept.
 * <p>
 * Default rotation limits match {@link LogRotationDefaults}; GUI/TUI should pass
 * values from {@code AppSettings} when available so they stay in sync with {@code settings.json}.
 */
public final class LogInitializer {

    private static final String ROOT_LOGGER = "com.treloc.xtreloc";
    private static final Logger logger = Logger.getLogger(LogInitializer.class.getName());

    private LogInitializer() {
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
                logger.log(Level.FINE, "Could not read logLevel from config path " + configPath + "; using default.", e);
            }
        }

        setup(logFile, level);
    }

    /**
     * Setup logger with explicit log level and {@link LogRotationDefaults default} rotation.
     *
     * @param logFile log file path (e.g. ~/.xtreloc/xtreloc.log)
     * @param level   log level
     */
    public static void setup(String logFile, Level level) throws IOException {
        setup(logFile, level, LogRotationDefaults.DEFAULT_LIMIT_BYTES, LogRotationDefaults.DEFAULT_COUNT);
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

        int limit = Math.max(LogRotationDefaults.MIN_LIMIT_BYTES,
            Math.min(LogRotationDefaults.MAX_LIMIT_BYTES, limitBytes));
        int c = Math.max(LogRotationDefaults.MIN_COUNT, Math.min(LogRotationDefaults.MAX_COUNT, count));
        String pattern = new File(logFile).getAbsolutePath().replace("\\", "/");
        FileHandler handler = new FileHandler(pattern, limit, c);
        handler.setLevel(level);
        handler.setFormatter(new LogFormatter());

        root.addHandler(handler);

        ConsoleHandler console = new ConsoleHandler();
        console.setLevel(level);
        console.setFormatter(new LogFormatter());
        root.addHandler(console);

        Logger appLogger = Logger.getLogger(ROOT_LOGGER);
        appLogger.setLevel(level);
    }

}
