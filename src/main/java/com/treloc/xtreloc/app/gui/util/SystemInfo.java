package com.treloc.xtreloc.app.gui.util;

import java.util.Properties;

/**
 * Utility class for collecting system information for error reports.
 */
public class SystemInfo {
    
    /**
     * Collects system information as a formatted string.
     * 
     * @return system information string
     */
    public static String collectSystemInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== System Information ===\n");
        
        // Application version
        info.append("Application: ").append(VersionInfo.getVersionString()).append("\n");
        
        // Java version
        info.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
        info.append("Java Vendor: ").append(System.getProperty("java.vendor")).append("\n");
        info.append("Java Home: ").append(System.getProperty("java.home")).append("\n");
        
        // OS information
        info.append("OS Name: ").append(System.getProperty("os.name")).append("\n");
        info.append("OS Version: ").append(System.getProperty("os.version")).append("\n");
        info.append("OS Architecture: ").append(System.getProperty("os.arch")).append("\n");
        
        // Memory information
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        info.append("Max Memory: ").append(formatBytes(maxMemory)).append("\n");
        info.append("Total Memory: ").append(formatBytes(totalMemory)).append("\n");
        info.append("Free Memory: ").append(formatBytes(freeMemory)).append("\n");
        info.append("Used Memory: ").append(formatBytes(usedMemory)).append("\n");
        
        // User information
        info.append("User Name: ").append(System.getProperty("user.name")).append("\n");
        info.append("User Home: ").append(System.getProperty("user.home")).append("\n");
        info.append("Working Directory: ").append(System.getProperty("user.dir")).append("\n");
        
        return info.toString();
    }
    
    /**
     * Formats bytes to human-readable format.
     * 
     * @param bytes number of bytes
     * @return formatted string
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * Collects system properties as a formatted string.
     * 
     * @return system properties string
     */
    public static String collectSystemProperties() {
        StringBuilder props = new StringBuilder();
        props.append("=== System Properties ===\n");
        
        Properties systemProps = System.getProperties();
        systemProps.stringPropertyNames().stream()
            .sorted()
            .forEach(key -> {
                String value = systemProps.getProperty(key);
                // Mask sensitive information
                if (key.toLowerCase().contains("password") || 
                    key.toLowerCase().contains("secret") ||
                    key.toLowerCase().contains("key")) {
                    value = "***";
                }
                props.append(key).append(" = ").append(value).append("\n");
            });
        
        return props.toString();
    }
}

