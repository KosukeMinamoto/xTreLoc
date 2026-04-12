package com.treloc.xtreloc.util;

import java.util.logging.Logger;

/**
 * Application version information.
 * Shared by CLI, TUI, and GUI without depending on GUI packages.
 */
public final class VersionInfo {

    private static final Logger logger = Logger.getLogger(VersionInfo.class.getName());
    private static final String DEFAULT_VERSION = "1.0-SNAPSHOT";

    private VersionInfo() {
    }

    /**
     * Returns the application version string.
     *
     * @return the version string
     */
    public static String getVersion() {
        try {
            Package pkg = VersionInfo.class.getPackage();
            String version = pkg != null ? pkg.getImplementationVersion() : null;
            if (version != null && !version.isEmpty()) {
                return version;
            }
        } catch (Exception e) {
            logger.warning("Failed to get version from package: " + e.getMessage());
        }

        try {
            java.io.InputStream manifestStream = VersionInfo.class.getClassLoader()
                .getResourceAsStream("META-INF/MANIFEST.MF");
            if (manifestStream != null) {
                try {
                    java.util.jar.Manifest manifest = new java.util.jar.Manifest(manifestStream);
                    String version = manifest.getMainAttributes().getValue("Implementation-Version");
                    if (version != null && !version.isEmpty()) {
                        return version;
                    }
                } finally {
                    manifestStream.close();
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to get version from manifest: " + e.getMessage());
        }

        return DEFAULT_VERSION;
    }

    /**
     * Returns the application name.
     *
     * @return the application name
     */
    public static String getApplicationName() {
        return "xTreLoc";
    }

    /**
     * Returns a formatted version string (e.g. "xTreLoc 1.0.0").
     *
     * @return formatted version string
     */
    public static String getVersionString() {
        return getApplicationName() + " " + getVersion();
    }
}
