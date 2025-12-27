package com.treloc.xtreloc.util;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.ConfigLoader;

/**
 * Logger initializer for xTreLoc
 */
public final class LogInitializer {

    private static final String ROOT_LOGGER = "com.treloc.xtreloc";

    private LogInitializer() {
        // utility class
    }

    /**
     * Setup logger using config file
     *
     * @param logFile    log file path
     * @param configPath config.json path
     */
    public static void setup(String logFile, String configPath)
            throws IOException {

        ConfigLoader config = new ConfigLoader(configPath);
        String levelStr = config.getConfig().logLevel;

        Level level = Level.parse(levelStr.toUpperCase());

        setup(logFile, level);
    }

    /**
     * Setup logger with explicit log level
     *
     * @param logFile log file path
     * @param level   log level
     */
    public static void setup(String logFile, Level level)
            throws IOException {

        Logger logger = Logger.getLogger(ROOT_LOGGER);
        logger.setUseParentHandlers(false);
        logger.setLevel(level);

        FileHandler handler = new FileHandler(logFile, true);
        handler.setLevel(level);
        handler.setFormatter(new LogFormatter());

        logger.addHandler(handler);
    }
}
