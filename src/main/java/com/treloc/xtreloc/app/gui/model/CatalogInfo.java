package com.treloc.xtreloc.app.gui.model;

import java.awt.Color;
import java.io.File;
import java.util.List;

/**
 * カタログ情報を保持するクラス
 * 複数カタログの比較表示に使用
 */
public class CatalogInfo {
    private String name;
    private Color color;
    private SymbolType symbolType;
    private List<Hypocenter> hypocenters;
    private File sourceFile;
    private boolean visible;
    
    /**
     * シンボルタイプ
     */
    public enum SymbolType {
        CIRCLE("Circle"),
        SQUARE("Square"),
        TRIANGLE("Triangle"),
        DIAMOND("Diamond"),
        CROSS("Cross"),
        STAR("Star");
        
        private final String displayName;
        
        SymbolType(String displayName) {
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    public CatalogInfo(String name, Color color, SymbolType symbolType, List<Hypocenter> hypocenters, File sourceFile) {
        this.name = name;
        this.color = color;
        this.symbolType = symbolType;
        this.hypocenters = hypocenters;
        this.sourceFile = sourceFile;
        this.visible = true;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Color getColor() {
        return color;
    }
    
    public void setColor(Color color) {
        this.color = color;
    }
    
    public SymbolType getSymbolType() {
        return symbolType;
    }
    
    public void setSymbolType(SymbolType symbolType) {
        this.symbolType = symbolType;
    }
    
    public List<Hypocenter> getHypocenters() {
        return hypocenters;
    }
    
    public void setHypocenters(List<Hypocenter> hypocenters) {
        this.hypocenters = hypocenters;
    }
    
    public File getSourceFile() {
        return sourceFile;
    }
    
    public void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    /**
     * 時刻文字列から時刻を比較可能な形式に変換
     * ISO 8601形式 (2000-01-01T00:00:00) を想定
     */
    public static String normalizeTime(String time) {
        if (time == null) return "";
        // ミリ秒部分を削除（ある場合）
        int dotIndex = time.indexOf('.');
        if (dotIndex > 0) {
            time = time.substring(0, dotIndex);
        }
        // 秒部分を削除して分単位で比較（必要に応じて調整）
        int lastColon = time.lastIndexOf(':');
        if (lastColon > 0) {
            return time.substring(0, lastColon);
        }
        return time;
    }
    
    /**
     * 2つの時刻が同じイベントかどうかを判定（分単位で比較）
     */
    public static boolean isSameEvent(String time1, String time2) {
        if (time1 == null || time2 == null) return false;
        return normalizeTime(time1).equals(normalizeTime(time2));
    }
}

