package com.treloc.xtreloc.app.gui.util;

/**
 * テーマ管理クラス（非推奨）
 * ダークモード関連の機能は削除され、defaultテーマのみを使用します。
 * このクラスは後方互換性のため残されていますが、何も行いません。
 */
public class ThemeManager {
    
    /**
     * 初期化（何もしない）
     */
    public static void initialize() {
        // 何もしない
    }
    
    /**
     * テーマを適用（何もしない）
     * @param themeName テーマ名（無視される）
     */
    public static void applyTheme(String themeName) {
        // 何もしない（defaultテーマのみ使用）
    }
    
    /**
     * 利用可能なテーマ名のリストを取得（空配列を返す）
     * @return 空の配列
     */
    public static String[] getAvailableThemes() {
        return new String[0];
    }
    
    /**
     * テーマ定義を再読み込み（何もしない）
     */
    public static void reload() {
        // 何もしない
    }
    
    /**
     * CSSテーマファイルのディレクトリを取得（nullを返す）
     * @return null
     */
    public static java.io.File getCSSThemesDirectory() {
        return null;
    }
    
    /**
     * 指定されたテーマ名のCSSファイルが存在するか確認（常にfalseを返す）
     * @param themeName テーマ名
     * @return 常にfalse
     */
    public static boolean hasCSSTtheme(String themeName) {
        return false;
    }
}
