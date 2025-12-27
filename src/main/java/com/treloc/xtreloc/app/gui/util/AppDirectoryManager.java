package com.treloc.xtreloc.app.gui.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * アプリケーションディレクトリとリソースファイルの管理
 */
public class AppDirectoryManager {
    private static final String APP_DIR_NAME = ".xtreloc";
    private static final String LOGO_FILE_NAME = "logo.png";
    private static final String SETTINGS_FILE_NAME = "settings.json";
    
    private static File appDir;
    private static File logoFile;
    private static File settingsFile;
    
    static {
        initializeAppDirectory();
    }
    
    /**
     * アプリケーションディレクトリを初期化
     */
    private static void initializeAppDirectory() {
        // ユーザーのホームディレクトリ配下にアプリケーションディレクトリを作成
        String userHome = System.getProperty("user.home");
        appDir = new File(userHome, APP_DIR_NAME);
        
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        
        // logo.pngのパスを設定
        logoFile = new File(appDir, LOGO_FILE_NAME);
        
        // 現在のディレクトリにlogo.pngがある場合はコピー
        File currentLogo = new File(LOGO_FILE_NAME);
        if (currentLogo.exists() && !logoFile.exists()) {
            try {
                Files.copy(currentLogo.toPath(), logoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("ロゴファイルのコピーに失敗: " + e.getMessage());
            }
        }
        
        // 設定ファイルのパスを設定
        settingsFile = new File(appDir, SETTINGS_FILE_NAME);
    }
    
    /**
     * アプリケーションディレクトリを取得
     */
    public static File getAppDirectory() {
        return appDir;
    }
    
    /**
     * ロゴファイルのパスを取得
     */
    public static File getLogoFile() {
        return logoFile;
    }
    
    /**
     * 設定ファイルのパスを取得
     */
    public static File getSettingsFile() {
        return settingsFile;
    }
    
    /**
     * ロゴファイルが存在するか確認
     */
    public static boolean logoFileExists() {
        return logoFile != null && logoFile.exists();
    }
}

