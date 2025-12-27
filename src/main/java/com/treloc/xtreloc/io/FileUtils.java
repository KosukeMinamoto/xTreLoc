package com.treloc.xtreloc.io;

/**
 * Utility class for file operations.
 * 
 * @author K.M.
 * @version 0.1
 * @since 2025-02-22
 */
public final class FileUtils {
    
    private FileUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Retrieves the file extension from the given file name.
     *
     * @param fileName the file name
     * @return the file extension (without the dot), or empty string if no extension
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot != -1 && lastDot != 0) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }
}

