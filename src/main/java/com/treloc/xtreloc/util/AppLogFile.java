package com.treloc.xtreloc.util;

import java.io.File;

/**
 * Application log file paths under {@code user.home/.xtreloc/}.
 * Used by CLI, TUI, and GUI. Current log: {@code xtreloc.log}; rotated: {@code xtreloc.log.1}, etc.
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

    /**
     * Returns the log file to use for "previous session" display.
     * Uses the single most recent log file: xtreloc.log.1 if it exists (last rotated = previous run),
     * otherwise xtreloc.log (current). Only one file is read, not multiple rotated files.
     *
     * @return the file to read (may not exist)
     */
    public static File getLogFileForPreviousSession() {
        File current = getLogFile();
        File dir = current.getParentFile();
        String baseName = current.getName();
        File rotated1 = new File(dir, baseName + ".1");
        if (rotated1.exists() && rotated1.isFile()) {
            return rotated1;
        }
        return current;
    }

    /**
     * Returns the list of log files in rotation order: current log first, then rotated
     * files (xtreloc.log.1, xtreloc.log.2, ...) up to the count used by LogInitializer.
     * Used when combining multiple files is desired.
     *
     * @param maxRotatedCount maximum number of rotated files to include (e.g. 5)
     * @return list of files: [current, .1, .2, ...]; only existing files are included
     */
    public static java.util.List<File> getLogFilesWithRotated(int maxRotatedCount) {
        java.util.List<File> list = new java.util.ArrayList<>();
        File current = getLogFile();
        list.add(current);
        File dir = current.getParentFile();
        String baseName = current.getName();
        for (int g = 1; g <= maxRotatedCount; g++) {
            File rotated = new File(dir, baseName + "." + g);
            if (rotated.exists() && rotated.isFile()) {
                list.add(rotated);
            }
        }
        return list;
    }
}
