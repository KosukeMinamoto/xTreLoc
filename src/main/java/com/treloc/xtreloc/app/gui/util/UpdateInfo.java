package com.treloc.xtreloc.app.gui.util;

/**
 * DTO for update-check results: version string, download URL, and release notes.
 * Used by {@link com.treloc.xtreloc.app.gui.service.UpdateChecker} and {@link com.treloc.xtreloc.app.gui.view.UpdateDialog}.
 */
public class UpdateInfo {
    private String version;
    private String downloadUrl;
    private String releaseNotes;
    private boolean isUpdateAvailable;

    /** Creates an instance indicating no update. */
    public UpdateInfo() {
        this.isUpdateAvailable = false;
    }

    /**
     * Creates an instance indicating an update is available.
     *
     * @param version available version (e.g. "1.0.1")
     * @param downloadUrl URL of the asset to download (may be null)
     * @param releaseNotes text from the release (may be null or empty)
     */
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

