package com.treloc.xtreloc.util;

import java.io.File;

/**
 * Application log file path utility.
 * Used by CLI/TUI/GUI without depending on GUI-specific resources.
 */
public final class AppLogFile {

    private static final String APP_DIR_NAME = ".xtreloc";
    private static final String LOG_FILE_NAME = "xtreloc.log";

    private AppLogFile() {
    }

    /**
     * Returns the application directory (e.g. user.home/.xtreloc).
     * Creates the directory if it does not exist.
     *
     * @return the application directory
     */
    public static File getAppDirectory() {
        String userHome = System.getProperty("user.home");
        File appDir = new File(userHome, APP_DIR_NAME);
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        return appDir;
    }

    /**
     * Returns the log file used by the application.
     *
     * @return the log file (may not exist yet)
     */
    public static File getLogFile() {
        return new File(getAppDirectory(), LOG_FILE_NAME);
    }
}
