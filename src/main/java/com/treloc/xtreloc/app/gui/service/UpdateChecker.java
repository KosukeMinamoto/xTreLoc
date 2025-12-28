package com.treloc.xtreloc.app.gui.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treloc.xtreloc.app.gui.util.UpdateInfo;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * アプリケーションの更新をチェックし、ダウンロードするサービスクラス
 */
public class UpdateChecker {
    private static final Logger logger = Logger.getLogger(UpdateChecker.class.getName());
    
    // 更新情報を取得するURL（GitHub Releases APIなど）
    // 実際のURLに置き換えてください
    private static final String UPDATE_CHECK_URL = "https://api.github.com/repos/KosukeMinamoto/xTreLoc/releases/latest";
    
    /**
     * 現在のバージョンを取得
     * 
     * @return 現在のバージョン文字列
     */
    private static String getCurrentVersion() {
        try {
            Package pkg = UpdateChecker.class.getPackage();
            String version = pkg.getImplementationVersion();
            if (version != null && !version.isEmpty()) {
                return version;
            }
        } catch (Exception e) {
            logger.warning("バージョン情報の取得に失敗しました: " + e.getMessage());
        }
        // フォールバック: デフォルトバージョン
        return "1.0-SNAPSHOT";
    }
    
    /**
     * 更新が利用可能かチェック
     * 
     * @return UpdateInfo 更新情報、更新がない場合はnull
     */
    public static UpdateInfo checkForUpdates() {
        try {
            URL url = new URL(UPDATE_CHECK_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(response.toString());
                    
                    String latestVersion = jsonNode.get("tag_name").asText().replace("v", "");
                    String releaseNotes = jsonNode.has("body") ? jsonNode.get("body").asText() : "";
                    
                    String downloadUrl = null;
                    if (jsonNode.has("assets")) {
                        for (JsonNode asset : jsonNode.get("assets")) {
                            String assetName = asset.get("name").asText();
                            if (assetName.endsWith(".app") || assetName.endsWith(".dmg") || 
                                assetName.endsWith(".jar")) {
                                downloadUrl = asset.get("browser_download_url").asText();
                                break;
                            }
                        }
                    }
                    
                    String currentVersion = getCurrentVersion();
                    if (isNewerVersion(latestVersion, currentVersion)) {
                        logger.info("New version found: " + latestVersion + " (current: " + currentVersion + ")");
                        return new UpdateInfo(latestVersion, downloadUrl, releaseNotes);
                    } else {
                        logger.info("Latest version: " + currentVersion);
                        return null;
                    }
                }
            } else {
                logger.warning("Update check failed. HTTP response code: " + responseCode);
            }
        } catch (Exception e) {
            logger.warning("Error during update check: " + e.getMessage());
        }
        return null;
    }
    
    private static boolean isNewerVersion(String newVersion, String currentVersion) {
        newVersion = newVersion.replace("-SNAPSHOT", "");
        currentVersion = currentVersion.replace("-SNAPSHOT", "");
        
        String[] newParts = newVersion.split("\\.");
        String[] currentParts = currentVersion.split("\\.");
        
        int maxLength = Math.max(newParts.length, currentParts.length);
        for (int i = 0; i < maxLength; i++) {
            int newPart = i < newParts.length ? Integer.parseInt(newParts[i]) : 0;
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            
            if (newPart > currentPart) {
                return true;
            } else if (newPart < currentPart) {
                return false;
            }
        }
        return false;
    }
    
    public static boolean downloadUpdate(String downloadUrl, File destinationFile, 
                                         ProgressCallback progressCallback) {
        try {
            URL url = new URL(downloadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                long contentLength = connection.getContentLengthLong();
                
                try (InputStream inputStream = connection.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
                    
                    byte[] buffer = new byte[8192];
                    long totalBytesRead = 0;
                    int bytesRead;
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        
                        if (progressCallback != null && contentLength > 0) {
                            int progress = (int) ((totalBytesRead * 100) / contentLength);
                            progressCallback.onProgress(progress);
                        }
                    }
                    
                    logger.info("ダウンロード完了: " + destinationFile.getAbsolutePath());
                    return true;
                }
            } else {
                logger.warning("ダウンロードに失敗しました。HTTPレスポンスコード: " + responseCode);
            }
        } catch (Exception e) {
            logger.severe("ダウンロード中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    public interface ProgressCallback {
        void onProgress(int percentage);
    }
}

