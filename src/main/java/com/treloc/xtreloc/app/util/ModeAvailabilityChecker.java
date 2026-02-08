package com.treloc.xtreloc.app.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to check which interface modes are available at runtime.
 * Checks if the required classes for each mode are present in the classpath.
 * 
 * @author K.Minamoto
 */
public class ModeAvailabilityChecker {
    
    /**
     * Checks if GUI mode is available.
     * 
     * @return true if GUI classes are present
     */
    public static boolean isGUIAvailable() {
        try {
            Class.forName("com.treloc.xtreloc.app.gui.XTreLocGUI");
            Class.forName("javax.swing.JFrame");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Checks if TUI mode is available.
     * 
     * @return true if TUI classes are present
     */
    public static boolean isTUIAvailable() {
        try {
            Class.forName("com.treloc.xtreloc.app.tui.XTreLocTUI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Checks if CLI mode is available.
     * 
     * @return true if CLI classes are present
     */
    public static boolean isCLIAvailable() {
        try {
            Class.forName("com.treloc.xtreloc.app.cli.XTreLocCLI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Gets a list of available modes.
     * 
     * @return list of available mode names ("GUI", "TUI", "CLI")
     */
    public static List<String> getAvailableModes() {
        List<String> modes = new ArrayList<>();
        if (isGUIAvailable()) {
            modes.add("GUI");
        }
        if (isTUIAvailable()) {
            modes.add("TUI");
        }
        if (isCLIAvailable()) {
            modes.add("CLI");
        }
        return modes;
    }
    
    /**
     * Checks if at least one mode is available.
     * 
     * @return true if at least one mode is available
     */
    public static boolean hasAnyMode() {
        return isGUIAvailable() || isTUIAvailable() || isCLIAvailable();
    }
}
