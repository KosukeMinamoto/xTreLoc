package com.treloc.xtreloc.app.gui.util;

/**
 * 更新情報を保持するクラス
 */
public class UpdateInfo {
    private String version;
    private String downloadUrl;
    private String releaseNotes;
    private boolean isUpdateAvailable;
    
    public UpdateInfo() {
        this.isUpdateAvailable = false;
    }
    
    public UpdateInfo(String version, String downloadUrl, String releaseNotes) {
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.releaseNotes = releaseNotes;
        this.isUpdateAvailable = true;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
    
    public String getReleaseNotes() {
        return releaseNotes;
    }
    
    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }
    
    public boolean isUpdateAvailable() {
        return isUpdateAvailable;
    }
    
    public void setUpdateAvailable(boolean updateAvailable) {
        isUpdateAvailable = updateAvailable;
    }
}

