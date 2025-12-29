package com.treloc.xtreloc.app.gui.util;

import java.util.logging.Logger;

/**
 * Utility class for retrieving application version information.
 * 
 * @author K.Minamoto
 */
public class VersionInfo {
    private static final Logger logger = Logger.getLogger(VersionInfo.class.getName());
    
    private static final String DEFAULT_VERSION = "1.0-SNAPSHOT";
    
    /**
     * Gets the current application version.
     * 
     * @return the version string
     */
    public static String getVersion() {
        try {
            Package pkg = VersionInfo.class.getPackage();
            String version = pkg.getImplementationVersion();
            if (version != null && !version.isEmpty()) {
                return version;
            }
        } catch (Exception e) {
            logger.warning("Failed to get version from package: " + e.getMessage());
        }
        
        // Fallback: try to get from manifest
        try {
            java.io.InputStream manifestStream = VersionInfo.class.getClassLoader()
                .getResourceAsStream("META-INF/MANIFEST.MF");
            if (manifestStream != null) {
                java.util.jar.Manifest manifest = new java.util.jar.Manifest(manifestStream);
                String version = manifest.getMainAttributes().getValue("Implementation-Version");
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to get version from manifest: " + e.getMessage());
        }
        
        // Final fallback: return default version
        return DEFAULT_VERSION;
    }
    
    /**
     * Gets the application name.
     * 
     * @return the application name
     */
    public static String getApplicationName() {
        return "xTreLoc";
    }
    
    /**
     * Gets formatted version information string.
     * 
     * @return formatted version string
     */
    public static String getVersionString() {
        return getApplicationName() + " " + getVersion();
    }
}

