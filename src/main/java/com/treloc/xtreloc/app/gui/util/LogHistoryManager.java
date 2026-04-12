package com.treloc.xtreloc.app.gui.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages log history for display on application startup.
 * Reads the last N lines from a single log file (the previous session's file only).
 */
public class LogHistoryManager {
    private static final Logger logger = Logger.getLogger(LogHistoryManager.class.getName());

    /**
     * Gets the log file path in the application directory.
     *
     * @return the log file
     */
    public static File getLogFile() {
        return com.treloc.xtreloc.util.AppLogFile.getLogFile();
    }

    /**
     * Reads the last N lines from the single "previous session" log file only.
     * Uses xtreloc.log.1 if it exists (last rotated file), otherwise xtreloc.log.
     * Does not combine multiple rotated files.
     *
     * @param numLines the number of lines to read
     * @return list of log lines in chronological order (oldest first)
     */
    public static List<String> readLastLines(int numLines) {
        File logFile = com.treloc.xtreloc.util.AppLogFile.getLogFileForPreviousSession();
        if (!logFile.exists() || !logFile.isFile()) {
            return Collections.emptyList();
        }
        try {
            return readLastLinesFromFile(logFile, numLines);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read log history from " + logFile.getName() + ": " + e.getMessage());
            return Collections.emptyList();
        } catch (SecurityException e) {
            logger.log(Level.WARNING, "No permission to read log history (e.g. on Linux): " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Reads the last N lines from a file by scanning backwards from the end.
     *
     * @param file the file to read
     * @param numLines maximum number of lines to return
     * @return list of lines in chronological order (oldest first)
     * @throws IOException if reading fails
     */
    private static List<String> readLastLinesFromFile(File file, int numLines) throws IOException {
        List<String> lines = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) {
                return lines;
            }
            StringBuilder sb = new StringBuilder();
            long pointer = fileLength - 1;
            int lineCount = 0;
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
            if (sb.length() > 0 && lineCount < numLines) {
                lines.add(sb.reverse().toString());
            }
        }
        Collections.reverse(lines);
        return lines;
    }

    /**
     * Returns log history as a formatted string for the GUI Execution Log panel.
     * Includes a header and "Current Session" separator.
     *
     * @param numLines number of lines to load from the previous-session log file
     * @return formatted string with separators
     */
    public static String getHistoryForGUI(int numLines) {
        List<String> lines = readLastLines(numLines);
        StringBuilder sb = new StringBuilder();
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

