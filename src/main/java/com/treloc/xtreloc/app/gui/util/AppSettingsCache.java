package com.treloc.xtreloc.app.gui.util;

import java.io.File;

/**
 * Caches {@link AppSettings} loaded from disk and invalidates when {@code settings.json}
 * changes (mtime) or after {@link AppSettings#save()}. Reduces repeated full reads from
 * paint handlers, key listeners, and chart code paths.
 */
public final class AppSettingsCache {

    private static volatile AppSettings cached;
    private static volatile long settingsMtime = Long.MIN_VALUE;

    private AppSettingsCache() {}

    /** Clears the cache so the next {@link #snapshot()} reloads from disk. */
    public static void invalidate() {
        synchronized (AppSettingsCache.class) {
            settingsMtime = Long.MIN_VALUE;
            cached = null;
        }
    }

    /**
     * Returns settings from cache if the settings file mtime is unchanged; otherwise reloads.
     */
    public static AppSettings snapshot() {
        File f = AppDirectoryManager.getSettingsFile();
        long mtime = f.exists() ? f.lastModified() : 0L;
        AppSettings c = cached;
        if (c != null && mtime == settingsMtime) {
            return c;
        }
        synchronized (AppSettingsCache.class) {
            if (cached != null && mtime == settingsMtime) {
                return cached;
            }
            cached = AppSettings.load();
            settingsMtime = mtime;
            return cached;
        }
    }

    public static ChartAppearanceSettings chartAppearance() {
        return snapshot().getChartAppearance();
    }
}
