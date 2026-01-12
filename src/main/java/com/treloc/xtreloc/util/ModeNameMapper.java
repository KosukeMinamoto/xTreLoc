package com.treloc.xtreloc.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for mapping between mode abbreviations and display names.
 * 
 * Provides consistent naming:
 * - GUI display: Full descriptive names (e.g., "Station-pair DD")
 * - Catalog output: 3-letter abbreviations (e.g., "LMO")
 */
public class ModeNameMapper {
    
    /**
     * Map from abbreviation to display name for GUI
     */
    private static final Map<String, String> ABBREV_TO_DISPLAY = new HashMap<>();
    
    /**
     * Map from display name to abbreviation
     */
    private static final Map<String, String> DISPLAY_TO_ABBREV = new HashMap<>();
    
    static {
        // Initialize mappings
        ABBREV_TO_DISPLAY.put("GRD", "Grid Search");
        ABBREV_TO_DISPLAY.put("LMO", "Levenberg-Marquardt");
        ABBREV_TO_DISPLAY.put("TRD", "Triple Difference");
        ABBREV_TO_DISPLAY.put("MCMC", "MCMC");
        ABBREV_TO_DISPLAY.put("DE", "Differential Evolution");
        ABBREV_TO_DISPLAY.put("CLS", "Clustering");
        ABBREV_TO_DISPLAY.put("SYN", "Synthetic Test");
        
        // Create reverse mapping
        for (Map.Entry<String, String> entry : ABBREV_TO_DISPLAY.entrySet()) {
            DISPLAY_TO_ABBREV.put(entry.getValue(), entry.getKey());
        }
    }
    
    /**
     * Gets the display name for a mode abbreviation.
     * 
     * @param abbrev mode abbreviation (e.g., "LMO")
     * @return display name (e.g., "Station-pair DD"), or the original string if not found
     */
    public static String getDisplayName(String abbrev) {
        if (abbrev == null) {
            return null;
        }
        return ABBREV_TO_DISPLAY.getOrDefault(abbrev.toUpperCase(), abbrev);
    }
    
    /**
     * Gets the abbreviation for a display name.
     * 
     * @param displayName display name (e.g., "Station-pair DD")
     * @return abbreviation (e.g., "LMO"), or the original string if not found
     */
    public static String getAbbreviation(String displayName) {
        if (displayName == null) {
            return null;
        }
        // First try direct lookup
        String abbrev = DISPLAY_TO_ABBREV.get(displayName);
        if (abbrev != null) {
            return abbrev;
        }
        // If not found, check if it's already an abbreviation
        if (ABBREV_TO_DISPLAY.containsKey(displayName.toUpperCase())) {
            return displayName.toUpperCase();
        }
        // Return original if no mapping found
        return displayName;
    }
    
    /**
     * Gets all available mode abbreviations.
     * 
     * @return array of mode abbreviations
     */
    public static String[] getAllAbbreviations() {
        return ABBREV_TO_DISPLAY.keySet().toArray(new String[0]);
    }
    
    /**
     * Gets all available display names.
     * 
     * @return array of display names
     */
    public static String[] getAllDisplayNames() {
        return ABBREV_TO_DISPLAY.values().toArray(new String[0]);
    }
    
    /**
     * Checks if a string is a valid mode abbreviation.
     * 
     * @param abbrev mode abbreviation to check
     * @return true if valid, false otherwise
     */
    public static boolean isValidAbbreviation(String abbrev) {
        if (abbrev == null) {
            return false;
        }
        return ABBREV_TO_DISPLAY.containsKey(abbrev.toUpperCase());
    }
    
    /**
     * Normalizes a mode name to its abbreviation.
     * If the input is already an abbreviation, returns it.
     * If the input is a display name, returns the corresponding abbreviation.
     * 
     * @param modeName mode name (can be abbreviation or display name)
     * @return normalized abbreviation
     */
    public static String normalizeToAbbreviation(String modeName) {
        if (modeName == null) {
            return null;
        }
        // Check if it's already an abbreviation
        String upper = modeName.toUpperCase();
        if (ABBREV_TO_DISPLAY.containsKey(upper)) {
            return upper;
        }
        // Try to get abbreviation from display name
        return getAbbreviation(modeName);
    }
}
