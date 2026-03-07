package com.treloc.xtreloc.app.gui.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class AppDirectoryManager {
    private static final String APP_DIR_NAME = ".xtreloc";
    private static final String RESOURCES_DIR = "images";
    private static final String LOGO_FILE_NAME = "logo.png";
    private static final String SAVE_ICON_FILE_NAME = "save.png";
    private static final String SETTINGS_FILE_NAME = "settings.json";
    
    private static File appDir;
    private static File logoFile;
    private static File saveIconFile;
    private static File settingsFile;
    
    static {
        initializeAppDirectory();
    }
    
    private static void initializeAppDirectory() {
        String userHome = System.getProperty("user.home");
        appDir = new File(userHome, APP_DIR_NAME);
        
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        
        logoFile = new File(appDir, LOGO_FILE_NAME);
        copyResourceIfNotExists(RESOURCES_DIR + "/" + LOGO_FILE_NAME, logoFile);
        
        saveIconFile = new File(appDir, SAVE_ICON_FILE_NAME);
        copyResourceIfNotExists(RESOURCES_DIR + "/" + SAVE_ICON_FILE_NAME, saveIconFile);
        
        settingsFile = new File(appDir, SETTINGS_FILE_NAME);
    }
    
    private static void copyResourceIfNotExists(String resourcePath, File targetFile) {
        if (targetFile.exists()) {
            return;
        }
        
        try (InputStream is = AppDirectoryManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                File fallbackFile = new File("docs/" + targetFile.getName());
                if (fallbackFile.exists()) {
                    Files.copy(fallbackFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    File currentDirFile = new File(targetFile.getName());
                    if (currentDirFile.exists()) {
                        Files.copy(currentDirFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to copy resource file " + resourcePath + ": " + e.getMessage());
        }
    }
    
    public static File getAppDirectory() {
        return appDir;
    }
    
    public static File getLogoFile() {
        return logoFile;
    }
    
    public static File getSettingsFile() {
        return settingsFile;
    }
    
    public static boolean logoFileExists() {
        return logoFile != null && logoFile.exists();
    }
    
    public static File getSaveIconFile() {
        if (saveIconFile != null && saveIconFile.exists()) {
            return saveIconFile;
        }
        return saveIconFile;
    }
}

