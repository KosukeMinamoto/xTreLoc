package com.treloc.xtreloc.app.gui.util;

import javax.swing.JFileChooser;
import java.io.File;

/**
 * Helper class for setting default directory in file choosers based on AppSettings.
 */
public class FileChooserHelper {
    
    /**
     * Sets the current directory for a file chooser based on AppSettings home directory.
     * If the home directory is set and exists, uses it. Otherwise, falls back to user.home.
     * 
     * @param fileChooser the file chooser to configure
     */
    public static void setDefaultDirectory(JFileChooser fileChooser) {
        AppSettings settings = AppSettings.load();
        String homeDir = settings.getHomeDirectory();
        
        if (homeDir != null && !homeDir.isEmpty()) {
            File home = new File(homeDir);
            if (home.exists() && home.isDirectory()) {
                fileChooser.setCurrentDirectory(home);
                return;
            }
        }
        
        // Fallback to user.home
        String userHome = System.getProperty("user.home");
        File userHomeFile = new File(userHome);
        if (userHomeFile.exists() && userHomeFile.isDirectory()) {
            fileChooser.setCurrentDirectory(userHomeFile);
        } else {
            // Final fallback to current working directory
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        }
    }
}

