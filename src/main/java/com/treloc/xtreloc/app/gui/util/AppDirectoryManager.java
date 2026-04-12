package com.treloc.xtreloc.app.gui.util;

import java.io.File;

/**
 * Application data directory under the user home ({@code ~/.xtreloc}).
 * <p>
 * Used for {@link AppSettings}, log paths, update downloads, etc.
 * <p>
 * <b>Not</b> used for bundled images (logo, toolbar icons); those are loaded from the classpath
 * via {@link BundledImageLoader} ({@code src/main/resources/images/}).
 */
public class AppDirectoryManager {

    private static final String APP_DIR_NAME = ".xtreloc";
    private static final String SETTINGS_FILE_NAME = "settings.json";

    private static File appDir;
    private static File settingsFile;

    static {
        initializeAppDirectory();
    }

    private static void initializeAppDirectory() {
        String userHome = System.getProperty("user.home");
        appDir = new File(userHome, APP_DIR_NAME);

        if (!appDir.exists()) {
            appDir.mkdirs();
        }

        settingsFile = new File(appDir, SETTINGS_FILE_NAME);
    }

    public static File getAppDirectory() {
        return appDir;
    }

    public static File getSettingsFile() {
        return settingsFile;
    }
}
