package com.treloc.xtreloc.app.gui.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;

/**
 * Manages application settings stored in ~/.xtreloc/settings.json.
 * Handles loading and saving of user preferences including font, symbol size,
 * color palette, log level, and log history settings.
 * 
 * @author xTreLoc Development Team
 */
public class AppSettings {
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private String font = "default";
    private int symbolSize = 10;
    private String defaultPalette = "Blue to Red";
    private String logLevel = "INFO";
    private int historyLines = 50;
    private boolean autoUpdateEnabled = true;
    private long lastUpdateCheck = 0;
    private String homeDirectory = System.getProperty("user.home");
    private String theme = "System";
    private double zoomWindowSeconds = 10.0; // Default zoom window: ±10 seconds
    
    /**
     * Loads application settings from the settings file.
     * If the file does not exist or cannot be read, returns default settings.
     * 
     * @return AppSettings instance with loaded or default values
     */
    public static AppSettings load() {
        File settingsFile = AppDirectoryManager.getSettingsFile();
        if (settingsFile.exists()) {
            try {
                ObjectNode node = (ObjectNode) mapper.readTree(settingsFile);
                AppSettings settings = new AppSettings();
                if (node.has("font")) {
                    settings.font = node.get("font").asText();
                }
                if (node.has("symbolSize")) {
                    settings.symbolSize = node.get("symbolSize").asInt();
                }
                if (node.has("defaultPalette")) {
                    settings.defaultPalette = node.get("defaultPalette").asText();
                }
                if (node.has("logLevel")) {
                    settings.logLevel = node.get("logLevel").asText();
                }
                if (node.has("historyLines")) {
                    settings.historyLines = node.get("historyLines").asInt();
                }
                if (node.has("autoUpdateEnabled")) {
                    settings.autoUpdateEnabled = node.get("autoUpdateEnabled").asBoolean();
                }
                if (node.has("lastUpdateCheck")) {
                    settings.lastUpdateCheck = node.get("lastUpdateCheck").asLong();
                }
                if (node.has("homeDirectory")) {
                    settings.homeDirectory = node.get("homeDirectory").asText();
                }
                if (node.has("theme")) {
                    settings.theme = node.get("theme").asText();
                }
                if (node.has("zoomWindowSeconds")) {
                    settings.zoomWindowSeconds = node.get("zoomWindowSeconds").asDouble();
                }
                return settings;
            } catch (IOException e) {
                System.err.println("Failed to load settings file: " + e.getMessage());
            }
        }
        return new AppSettings();
    }
    
    /**
     * Saves the current settings to the settings file.
     * Creates the file if it does not exist.
     */
    public void save() {
        File settingsFile = AppDirectoryManager.getSettingsFile();
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("font", font);
            node.put("symbolSize", symbolSize);
            node.put("defaultPalette", defaultPalette);
            node.put("logLevel", logLevel);
            node.put("historyLines", historyLines);
            node.put("autoUpdateEnabled", autoUpdateEnabled);
            node.put("lastUpdateCheck", lastUpdateCheck);
            node.put("homeDirectory", homeDirectory);
            node.put("theme", theme);
            node.put("zoomWindowSeconds", zoomWindowSeconds);
            mapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile, node);
        } catch (IOException e) {
            System.err.println("Failed to save settings file: " + e.getMessage());
        }
    }
    
    /**
     * Gets the font preference.
     * 
     * @return font name ("default", "Sans Serif", "Serif", or "Monospaced")
     */
    public String getFont() {
        return font;
    }
    
    /**
     * Sets the font preference.
     * 
     * @param font font name ("default", "Sans Serif", "Serif", or "Monospaced")
     */
    public void setFont(String font) {
        this.font = font;
    }
    
    /**
     * Gets the symbol size for map markers.
     * 
     * @return symbol size in pixels (5-50)
     */
    public int getSymbolSize() {
        return symbolSize;
    }
    
    /**
     * Sets the symbol size for map markers.
     * 
     * @param symbolSize symbol size in pixels (5-50)
     */
    public void setSymbolSize(int symbolSize) {
        this.symbolSize = symbolSize;
    }
    
    /**
     * Gets the default color palette for map visualization.
     * 
     * @return palette name ("Blue to Red", "Viridis", "Plasma", etc.)
     */
    public String getDefaultPalette() {
        return defaultPalette;
    }
    
    /**
     * Sets the default color palette for map visualization.
     * 
     * @param defaultPalette palette name ("Blue to Red", "Viridis", "Plasma", etc.)
     */
    public void setDefaultPalette(String defaultPalette) {
        this.defaultPalette = defaultPalette;
    }
    
    /**
     * Gets the log level for application logging.
     * 
     * @return log level ("INFO", "DEBUG", "WARNING", or "SEVERE")
     */
    public String getLogLevel() {
        return logLevel;
    }
    
    /**
     * Sets the log level for application logging.
     * 
     * @param logLevel log level ("INFO", "DEBUG", "WARNING", or "SEVERE")
     */
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
    
    /**
     * Gets the number of log history lines to display.
     * 
     * @return number of log history lines (10-1000)
     */
    public int getHistoryLines() {
        return historyLines;
    }
    
    /**
     * Sets the number of log history lines to display.
     * 
     * @param historyLines number of log history lines (10-1000)
     */
    public void setHistoryLines(int historyLines) {
        this.historyLines = historyLines;
    }
    
    /**
     * Gets whether automatic update checking is enabled.
     * 
     * @return true if auto-update is enabled
     */
    public boolean isAutoUpdateEnabled() {
        return autoUpdateEnabled;
    }
    
    /**
     * Sets whether automatic update checking is enabled.
     * 
     * @param autoUpdateEnabled true to enable auto-update
     */
    public void setAutoUpdateEnabled(boolean autoUpdateEnabled) {
        this.autoUpdateEnabled = autoUpdateEnabled;
    }
    
    /**
     * Gets the timestamp of the last update check.
     * 
     * @return timestamp in milliseconds since epoch
     */
    public long getLastUpdateCheck() {
        return lastUpdateCheck;
    }
    
    /**
     * Sets the timestamp of the last update check.
     * 
     * @param lastUpdateCheck timestamp in milliseconds since epoch
     */
    public void setLastUpdateCheck(long lastUpdateCheck) {
        this.lastUpdateCheck = lastUpdateCheck;
    }
    
    /**
     * Gets the home directory for file dialogs.
     * 
     * @return home directory path (defaults to user.home)
     */
    public String getHomeDirectory() {
        return homeDirectory;
    }
    
    /**
     * Sets the home directory for file dialogs.
     * 
     * @param homeDirectory home directory path
     */
    public void setHomeDirectory(String homeDirectory) {
        this.homeDirectory = homeDirectory;
    }
    
    /**
     * Gets the UI theme preference.
     * 
     * @return theme name ("System", "Metal", "Nimbus", etc.)
     */
    public String getTheme() {
        return theme;
    }
    
    /**
     * Sets the UI theme preference.
     * 
     * @param theme theme name ("System", "Metal", "Nimbus", etc.)
     */
    public void setTheme(String theme) {
        this.theme = theme;
    }
    
    /**
     * Gets the zoom window size in seconds (half-width, so total window is 2x this value).
     * 
     * @return zoom window size in seconds (default: 10.0)
     */
    public double getZoomWindowSeconds() {
        return zoomWindowSeconds;
    }
    
    /**
     * Sets the zoom window size in seconds (half-width, so total window is 2x this value).
     * 
     * @param zoomWindowSeconds zoom window size in seconds
     */
    public void setZoomWindowSeconds(double zoomWindowSeconds) {
        this.zoomWindowSeconds = zoomWindowSeconds;
    }
}

