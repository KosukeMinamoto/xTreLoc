package com.treloc.xtreloc.util;

/**
 * Single source of truth for file-log rotation limits used by {@link LogInitializer}
 * and default values in {@code com.treloc.xtreloc.app.gui.util.AppSettings} ({@code ~/.xtreloc/settings.json}).
 * <p>
 * At runtime, GUI/TUI pass {@link com.treloc.xtreloc.app.gui.util.AppSettings#getLogLimitBytes()} and
 * {@link com.treloc.xtreloc.app.gui.util.AppSettings#getLogCount()} into {@link LogInitializer#setup(String, java.util.logging.Level, int, int)}.
 */
public final class LogRotationDefaults {

    /** Default max size of one log file before rotation (10 MiB). */
    public static final int DEFAULT_LIMIT_BYTES = 10 * 1024 * 1024;

    /** Default number of rotated files to keep (active + .1 … .N−1). */
    public static final int DEFAULT_COUNT = 5;

    public static final int MIN_LIMIT_BYTES = 1024 * 1024;
    public static final int MAX_LIMIT_BYTES = 500 * 1024 * 1024;

    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 20;

    private LogRotationDefaults() {
    }
}
