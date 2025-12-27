package com.treloc.xtreloc.app.gui.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 共通ファイル選択を管理するクラス
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
        // 全てのリスナーに通知
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
        // 全てのリスナーに通知
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
     * 観測点ファイル変更のリスナー
     */
    public interface StationFileListener {
        void onStationFileChanged(File file);
    }
    
    /**
     * 速度構造ファイル変更のリスナー
     */
    public interface TaupFileListener {
        void onTaupFileChanged(String file);
    }
}

