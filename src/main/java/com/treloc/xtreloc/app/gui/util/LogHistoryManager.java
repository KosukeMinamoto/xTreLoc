package com.treloc.xtreloc.app.gui.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages log history for display on application startup.
 * Reads the last N lines from the log file and provides them for console output.
 */
public class LogHistoryManager {
    private static final Logger logger = Logger.getLogger(LogHistoryManager.class.getName());
    private static final String LOG_FILE_NAME = "xtreloc.log";
    private static final int DEFAULT_HISTORY_LINES = 50;
    
    /**
     * Gets the log file path in the application directory.
     * 
     * @return the log file
     */
    public static File getLogFile() {
        return com.treloc.xtreloc.util.AppLogFile.getLogFile();
    }
    
    /**
     * Reads the last N lines from the log file.
     * 
     * @param numLines the number of lines to read (default: 50)
     * @return list of log lines (most recent first)
     */
    public static List<String> readLastLines(int numLines) {
        File logFile = getLogFile();
        
        if (!logFile.exists() || !logFile.isFile()) {
            return Collections.emptyList();
        }
        
        try {
            return readLastLinesFromFile(logFile, numLines);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read log history: " + e.getMessage(), e);
            return Collections.emptyList();
        } catch (SecurityException e) {
            logger.log(Level.WARNING, "No permission to read log history (e.g. on Linux): " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Reads the last N lines from a file efficiently.
     * 
     * @param file the file to read
     * @param numLines the number of lines to read
     * @return list of log lines (most recent first)
     * @throws IOException if reading fails
     */
    private static List<String> readLastLinesFromFile(File file, int numLines) throws IOException {
        List<String> lines = new ArrayList<>();
        
        // Use RandomAccessFile for efficient reading from the end
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) {
                return lines;
            }
            
            StringBuilder sb = new StringBuilder();
            long pointer = fileLength - 1;
            int lineCount = 0;
            
            // Read backwards from the end of the file
            while (pointer >= 0 && lineCount < numLines) {
                raf.seek(pointer);
                char c = (char) raf.read();
                
                if (c == '\n') {
                    if (sb.length() > 0) {
                        lines.add(sb.reverse().toString());
                        sb.setLength(0);
                        lineCount++;
                    }
                } else if (c != '\r') {
                    sb.append(c);
                }
                
                pointer--;
            }
            
            // Add the last line if exists
            if (sb.length() > 0 && lineCount < numLines) {
                lines.add(sb.reverse().toString());
            }
        }
        
        // Reverse to get chronological order (oldest first)
        Collections.reverse(lines);
        return lines;
    }
    
    /**
     * Gets the log history as a formatted string for GUI display.
     * Includes a separator line to distinguish from previous session.
     * 
     * @param numLines the number of lines to display
     * @return formatted log history string with separator
     */
    public static String getHistoryForGUI(int numLines) {
        List<String> lines = readLastLines(numLines);
        
        StringBuilder sb = new StringBuilder();
        
        // Add separator line to distinguish from previous session
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("Application Started - Previous Log History (").append(lines.size()).append(" lines)\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        
        if (lines.isEmpty()) {
            sb.append("(No previous log history)\n");
        } else {
            for (String line : lines) {
                sb.append(line).append("\n");
            }
        }
        
        sb.append("───────────────────────────────────────────────────────────────\n");
        sb.append("Current Session\n");
        sb.append("───────────────────────────────────────────────────────────────\n");
        
        return sb.toString();
    }
}

