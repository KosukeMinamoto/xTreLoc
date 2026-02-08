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
    private int logLimitBytes = 10 * 1024 * 1024;  // 10 MB per file before rotation
    private int logCount = 5;                       // number of rotated files to keep
    private boolean autoUpdateEnabled = true;
    private long lastUpdateCheck = 0;
    private String homeDirectory = System.getProperty("user.home");
    private String theme = "System";
    private double zoomWindowSeconds = 10.0; // Default zoom window: ±10 seconds
    private boolean confirmBeforeOverwrite = true;
    private int recentFilesCount = 10;
    private ChartAppearanceSettings chartAppearance = new ChartAppearanceSettings();
    /** Default symbol color (map) as hex e.g. "#000000". */
    private String defaultSymbolColor = "#000000";
    /** Picking: mouse button for P (Left, Right, Middle) */
    private String pickingMouseP = "Left";
    /** Picking: mouse button for S (Left, Right, Middle) */
    private String pickingMouseS = "Right";
    /** Picking: mouse button for context menu (Left, Right, Middle) */
    private String pickingMouseContext = "Middle";
    /** Picking: optional key for P (None, P, S, 1, 2). When held, click = P. */
    private String pickingKeyP = "None";
    /** Picking: optional key for S (None, P, S, 1, 2). When held, click = S. */
    private String pickingKeyS = "None";
    
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
                if (node.has("logLimitBytes")) {
                    settings.logLimitBytes = node.get("logLimitBytes").asInt();
                }
                if (node.has("logCount")) {
                    settings.logCount = node.get("logCount").asInt();
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
                if (node.has("confirmBeforeOverwrite")) {
                    settings.confirmBeforeOverwrite = node.get("confirmBeforeOverwrite").asBoolean();
                }
                if (node.has("recentFilesCount")) {
                    settings.recentFilesCount = node.get("recentFilesCount").asInt();
                }
                if (node.has("pickingMouseP")) settings.pickingMouseP = node.get("pickingMouseP").asText();
                if (node.has("pickingMouseS")) settings.pickingMouseS = node.get("pickingMouseS").asText();
                if (node.has("pickingMouseContext")) settings.pickingMouseContext = node.get("pickingMouseContext").asText();
                if (node.has("pickingKeyP")) settings.pickingKeyP = node.get("pickingKeyP").asText();
                if (node.has("pickingKeyS")) settings.pickingKeyS = node.get("pickingKeyS").asText();
                // Load chart appearance settings
                if (node.has("chartAppearance")) {
                    ObjectNode chartNode = (ObjectNode) node.get("chartAppearance");
                    ChartAppearanceSettings chartSettings = new ChartAppearanceSettings();
                    if (chartNode.has("titleFontName")) {
                        chartSettings.setTitleFontName(chartNode.get("titleFontName").asText());
                    }
                    if (chartNode.has("titleFontSize")) {
                        chartSettings.setTitleFontSize(chartNode.get("titleFontSize").asInt());
                    }
                    if (chartNode.has("titleFontStyle")) {
                        chartSettings.setTitleFontStyle(chartNode.get("titleFontStyle").asInt());
                    }
                    if (chartNode.has("axisLabelFontName")) {
                        chartSettings.setAxisLabelFontName(chartNode.get("axisLabelFontName").asText());
                    }
                    if (chartNode.has("axisLabelFontSize")) {
                        chartSettings.setAxisLabelFontSize(chartNode.get("axisLabelFontSize").asInt());
                    }
                    if (chartNode.has("axisLabelFontStyle")) {
                        chartSettings.setAxisLabelFontStyle(chartNode.get("axisLabelFontStyle").asInt());
                    }
                    if (chartNode.has("tickLabelFontName")) {
                        chartSettings.setTickLabelFontName(chartNode.get("tickLabelFontName").asText());
                    }
                    if (chartNode.has("tickLabelFontSize")) {
                        chartSettings.setTickLabelFontSize(chartNode.get("tickLabelFontSize").asInt());
                    }
                    if (chartNode.has("tickLabelFontStyle")) {
                        chartSettings.setTickLabelFontStyle(chartNode.get("tickLabelFontStyle").asInt());
                    }
                    if (chartNode.has("legendFontName")) {
                        chartSettings.setLegendFontName(chartNode.get("legendFontName").asText());
                    }
                    if (chartNode.has("legendFontSize")) {
                        chartSettings.setLegendFontSize(chartNode.get("legendFontSize").asInt());
                    }
                    if (chartNode.has("legendFontStyle")) {
                        chartSettings.setLegendFontStyle(chartNode.get("legendFontStyle").asInt());
                    }
                    if (chartNode.has("backgroundColor")) {
                        chartSettings.setBackgroundColor(chartNode.get("backgroundColor").asText());
                    }
                    if (chartNode.has("gridlineColor")) {
                        chartSettings.setGridlineColor(chartNode.get("gridlineColor").asText());
                    }
                    if (chartNode.has("axisLineColor")) {
                        chartSettings.setAxisLineColor(chartNode.get("axisLineColor").asText());
                    }
                    if (chartNode.has("lineWidth")) {
                        chartSettings.setLineWidth((float) chartNode.get("lineWidth").asDouble());
                    }
                    if (chartNode.has("gridlineWidth")) {
                        chartSettings.setGridlineWidth((float) chartNode.get("gridlineWidth").asDouble());
                    }
                    if (chartNode.has("gridlineStyle")) {
                        chartSettings.setGridlineStyle(chartNode.get("gridlineStyle").asText());
                    }
                    settings.chartAppearance = chartSettings;
                }
                if (node.has("defaultSymbolColor")) {
                    settings.defaultSymbolColor = node.get("defaultSymbolColor").asText();
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
            node.put("logLimitBytes", logLimitBytes);
            node.put("logCount", logCount);
            node.put("autoUpdateEnabled", autoUpdateEnabled);
            node.put("lastUpdateCheck", lastUpdateCheck);
            node.put("homeDirectory", homeDirectory);
            node.put("theme", theme);
            node.put("zoomWindowSeconds", zoomWindowSeconds);
            node.put("confirmBeforeOverwrite", confirmBeforeOverwrite);
            node.put("recentFilesCount", recentFilesCount);
            node.put("pickingMouseP", pickingMouseP);
            node.put("pickingMouseS", pickingMouseS);
            node.put("pickingMouseContext", pickingMouseContext);
            node.put("pickingKeyP", pickingKeyP);
            node.put("pickingKeyS", pickingKeyS);
            // Save chart appearance settings
            ObjectNode chartNode = node.putObject("chartAppearance");
            chartNode.put("titleFontName", chartAppearance.getTitleFontName());
            chartNode.put("titleFontSize", chartAppearance.getTitleFontSize());
            chartNode.put("titleFontStyle", chartAppearance.getTitleFontStyle());
            chartNode.put("axisLabelFontName", chartAppearance.getAxisLabelFontName());
            chartNode.put("axisLabelFontSize", chartAppearance.getAxisLabelFontSize());
            chartNode.put("axisLabelFontStyle", chartAppearance.getAxisLabelFontStyle());
            chartNode.put("tickLabelFontName", chartAppearance.getTickLabelFontName());
            chartNode.put("tickLabelFontSize", chartAppearance.getTickLabelFontSize());
            chartNode.put("tickLabelFontStyle", chartAppearance.getTickLabelFontStyle());
            chartNode.put("legendFontName", chartAppearance.getLegendFontName());
            chartNode.put("legendFontSize", chartAppearance.getLegendFontSize());
            chartNode.put("legendFontStyle", chartAppearance.getLegendFontStyle());
            chartNode.put("backgroundColor", chartAppearance.getBackgroundColor());
            chartNode.put("gridlineColor", chartAppearance.getGridlineColor());
            chartNode.put("axisLineColor", chartAppearance.getAxisLineColor());
            chartNode.put("lineWidth", chartAppearance.getLineWidth());
            chartNode.put("gridlineWidth", chartAppearance.getGridlineWidth());
            chartNode.put("gridlineStyle", chartAppearance.getGridlineStyle());
            node.put("defaultSymbolColor", defaultSymbolColor);
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
     * Gets the maximum log file size in bytes before rotation (default: 10 MB).
     */
    public int getLogLimitBytes() {
        return logLimitBytes;
    }
    
    /**
     * Sets the maximum log file size in bytes before rotation. Takes effect after restart.
     */
    public void setLogLimitBytes(int logLimitBytes) {
        this.logLimitBytes = Math.max(1024 * 1024, Math.min(500 * 1024 * 1024, logLimitBytes)); // 1 MB–500 MB
    }
    
    /**
     * Gets the number of rotated log files to keep (default: 5).
     */
    public int getLogCount() {
        return logCount;
    }
    
    /**
     * Sets the number of rotated log files to keep. Takes effect after restart.
     */
    public void setLogCount(int logCount) {
        this.logCount = Math.max(1, Math.min(20, logCount));
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
    
    /**
     * Gets the chart appearance settings.
     * 
     * @return ChartAppearanceSettings instance
     */
    public ChartAppearanceSettings getChartAppearance() {
        return chartAppearance;
    }
    
    /**
     * Sets the chart appearance settings.
     * 
     * @param chartAppearance ChartAppearanceSettings instance
     */
    public void setChartAppearance(ChartAppearanceSettings chartAppearance) {
        this.chartAppearance = chartAppearance;
    }
    
    /**
     * Gets whether to show a confirmation dialog before overwriting files.
     * @return true if confirmation is enabled (default: true)
     */
    public boolean isConfirmBeforeOverwrite() {
        return confirmBeforeOverwrite;
    }
    
    /**
     * Sets whether to show a confirmation dialog before overwriting files.
     * @param confirmBeforeOverwrite true to enable confirmation
     */
    public void setConfirmBeforeOverwrite(boolean confirmBeforeOverwrite) {
        this.confirmBeforeOverwrite = confirmBeforeOverwrite;
    }
    
    /**
     * Gets the maximum number of recent files to show in menus.
     * @return count (default: 10)
     */
    public int getRecentFilesCount() {
        return recentFilesCount;
    }
    
    /**
     * Sets the maximum number of recent files to show in menus.
     * @param recentFilesCount count (1–50)
     */
    public void setRecentFilesCount(int recentFilesCount) {
        this.recentFilesCount = Math.max(1, Math.min(50, recentFilesCount));
    }
    
    public String getPickingMouseP() { return pickingMouseP; }
    public void setPickingMouseP(String pickingMouseP) { this.pickingMouseP = pickingMouseP != null ? pickingMouseP : "Left"; }
    public String getPickingMouseS() { return pickingMouseS; }
    public void setPickingMouseS(String pickingMouseS) { this.pickingMouseS = pickingMouseS != null ? pickingMouseS : "Right"; }
    public String getPickingMouseContext() { return pickingMouseContext; }
    public void setPickingMouseContext(String pickingMouseContext) { this.pickingMouseContext = pickingMouseContext != null ? pickingMouseContext : "Middle"; }
    public String getPickingKeyP() { return pickingKeyP; }
    public void setPickingKeyP(String pickingKeyP) { this.pickingKeyP = pickingKeyP != null ? pickingKeyP : "None"; }
    public String getPickingKeyS() { return pickingKeyS; }
    public void setPickingKeyS(String pickingKeyS) { this.pickingKeyS = pickingKeyS != null ? pickingKeyS : "None"; }
    public String getDefaultSymbolColor() { return defaultSymbolColor; }
    public void setDefaultSymbolColor(String defaultSymbolColor) { this.defaultSymbolColor = defaultSymbolColor != null ? defaultSymbolColor : "#000000"; }
}

