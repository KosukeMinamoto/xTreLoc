package com.treloc.xtreloc.app.gui.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages log history for display on application startup.
 * Reads the last N lines from the log file and provides them for console output.
 */
public class LogHistoryManager {
    private static final String LOG_FILE_NAME = "xtreloc.log";
    private static final int DEFAULT_HISTORY_LINES = 50;
    
    /**
     * Gets the log file path in the application directory.
     * 
     * @return the log file
     */
    public static File getLogFile() {
        File appDir = AppDirectoryManager.getAppDirectory();
        return new File(appDir, LOG_FILE_NAME);
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
            System.err.println("ログ履歴の読み込みに失敗: " + e.getMessage());
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
     * Reads the last N lines using default count.
     * 
     * @return list of log lines (most recent first)
     */
    public static List<String> readLastLines() {
        return readLastLines(DEFAULT_HISTORY_LINES);
    }
    
    /**
     * Prints the log history to console.
     * 
     * @param numLines the number of lines to display
     */
    public static void printHistory(int numLines) {
        List<String> lines = readLastLines(numLines);
        
        if (lines.isEmpty()) {
            System.out.println("=== ログ履歴（なし） ===");
            return;
        }
        
        System.out.println("=== ログ履歴（最新 " + lines.size() + " 行） ===");
        for (String line : lines) {
            System.out.println(line);
        }
        System.out.println("=== ログ履歴終了 ===");
    }
    
    /**
     * Prints the log history using default line count.
     */
    public static void printHistory() {
        printHistory(DEFAULT_HISTORY_LINES);
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
    
    /**
     * Gets the log history using default line count for GUI display.
     * 
     * @return formatted log history string with separator
     */
    public static String getHistoryForGUI() {
        return getHistoryForGUI(DEFAULT_HISTORY_LINES);
    }
}

