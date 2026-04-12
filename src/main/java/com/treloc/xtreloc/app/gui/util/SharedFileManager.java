package com.treloc.xtreloc.app.gui.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for managing shared file selection
 */
public class SharedFileManager {
    private static SharedFileManager instance;
    
    private File stationFile;
    private String taupFile;
    private List<StationFileListener> stationFileListeners = new ArrayList<>();
    private List<TaupFileListener> taupFileListeners = new ArrayList<>();
    
    private SharedFileManager() {
    }
    
    public static SharedFileManager getInstance() {
        if (instance == null) {
            instance = new SharedFileManager();
        }
        return instance;
    }
    
    public File getStationFile() {
        return stationFile;
    }
    
    public void setStationFile(File file) {
        this.stationFile = file;
        // Notify all listeners
        for (StationFileListener listener : stationFileListeners) {
            listener.onStationFileChanged(file);
        }
    }
    
    public void addStationFileListener(StationFileListener listener) {
        stationFileListeners.add(listener);
    }
    
    public void removeStationFileListener(StationFileListener listener) {
        stationFileListeners.remove(listener);
    }
    
    public String getTaupFile() {
        return taupFile;
    }
    
    public void setTaupFile(String file) {
        this.taupFile = file;
        // Notify all listeners
        for (TaupFileListener listener : taupFileListeners) {
            listener.onTaupFileChanged(file);
        }
    }
    
    public void addTaupFileListener(TaupFileListener listener) {
        taupFileListeners.add(listener);
    }
    
    public void removeTaupFileListener(TaupFileListener listener) {
        taupFileListeners.remove(listener);
    }
    
    /**
     * Listener for station file changes
     */
    public interface StationFileListener {
        void onStationFileChanged(File file);
    }
    
    /**
     * Listener for velocity structure file changes
     */
    public interface TaupFileListener {
        void onTaupFileChanged(String file);
    }
}

