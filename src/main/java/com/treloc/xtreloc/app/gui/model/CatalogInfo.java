package com.treloc.xtreloc.app.gui.model;

import com.treloc.xtreloc.util.TimeFormatConverter;

import java.awt.Color;
import java.io.File;
import java.util.List;

/**
 * Catalog information class
 * Used for comparing multiple catalogs
 */
public class CatalogInfo {
    private String name;
    private Color color;
    private SymbolType symbolType;
    private List<Hypocenter> hypocenters;
    /** Filtered list for display (screening). When null, full hypocenters are shown. */
    private List<Hypocenter> displayHypocenters;
    private File sourceFile;
    private boolean visible;
    
    public enum SymbolType {
        CIRCLE("Circle"),
        SQUARE("Square"),
        TRIANGLE("Triangle"),
        DIAMOND("Diamond"),
        CROSS("Cross"),
        STAR("Star");
        
        private final String displayName;
        
        SymbolType(String displayName) {
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    public CatalogInfo(String name, Color color, SymbolType symbolType, List<Hypocenter> hypocenters, File sourceFile) {
        this.name = name;
        this.color = color;
        this.symbolType = symbolType;
        this.hypocenters = hypocenters;
        this.sourceFile = sourceFile;
        this.visible = true;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Color getColor() {
        return color;
    }
    
    public void setColor(Color color) {
        this.color = color;
    }
    
    public SymbolType getSymbolType() {
        return symbolType;
    }
    
    public void setSymbolType(SymbolType symbolType) {
        this.symbolType = symbolType;
    }
    
    /**
     * Returns the list of hypocenters to display.
     * If a display filter is set (screening), returns the filtered list; otherwise returns the full list.
     */
    public List<Hypocenter> getHypocenters() {
        return displayHypocenters != null ? displayHypocenters : hypocenters;
    }
    
    public void setHypocenters(List<Hypocenter> hypocenters) {
        this.hypocenters = hypocenters;
        this.displayHypocenters = null;
    }
    
    /** Sets the filtered list for display (screening). Pass null to clear the filter. */
    public void setDisplayHypocenters(List<Hypocenter> displayHypocenters) {
        this.displayHypocenters = displayHypocenters;
    }
    
    /** Clears the display filter so the full hypocenter list is shown. */
    public void clearDisplayFilter() {
        this.displayHypocenters = null;
    }
    
    /** Returns the full (unfiltered) hypocenter list. */
    public List<Hypocenter> getHypocentersFull() {
        return hypocenters;
    }
    
    public File getSourceFile() {
        return sourceFile;
    }
    
    public void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    /**
     * Normalizes time string for comparison
     * Supports both ISO 8601 and yymmdd.hhmmss formats
     * Removes seconds for minute-level comparison
     */
    public static String normalizeTime(String time) {
        return TimeFormatConverter.normalizeTimeForComparison(time);
    }
    
    /**
     * Checks if two times represent the same event (minute-level comparison)
     */
    public static boolean isSameEvent(String time1, String time2) {
        if (time1 == null || time2 == null) return false;
        return normalizeTime(time1).equals(normalizeTime(time2));
    }
}

