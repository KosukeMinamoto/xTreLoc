package com.treloc.xtreloc.app.gui.util;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/**
 * Preset and bounds helpers for the main application window size ({@code settings.json}).
 */
public final class MainWindowSizePresets {

    public static final int WIDTH_MIN = 800;
    public static final int WIDTH_MAX = 3840;
    public static final int HEIGHT_MIN = 600;
    public static final int HEIGHT_MAX = 2400;

    /** Last item in the preset combo; width/height spinners apply only when this is selected. */
    public static final String CUSTOM_LABEL = "Custom";

    public static final class Entry {
        public final String label;
        public final int width;
        public final int height;

        Entry(String label, int width, int height) {
            this.label = label;
            this.width = clampWidth(width);
            this.height = clampHeight(height);
        }
    }

    private static final Entry[] ENTRIES = {
        new Entry("1280 × 720 (HD)", 1280, 720),
        new Entry("1366 × 768 (laptop)", 1366, 768),
        new Entry("1440 × 900 (WXGA+)", 1440, 900),
        new Entry("1536 × 864 (scale-friendly)", 1536, 864),
        new Entry("1600 × 900", 1600, 900),
        new Entry("1920 × 1080 (Full HD)", 1920, 1080),
        new Entry("2048 × 1152", 2048, 1152),
        new Entry("2560 × 1440 (QHD)", 2560, 1440),
        new Entry("1800 × 850 (xTreLoc default)", 1800, 850),
    };

    /** Combo index of {@link #CUSTOM_LABEL} (presets are {@code 0 .. ENTRIES.length-1}). */
    public static final int CUSTOM_COMBO_INDEX = ENTRIES.length;

    private MainWindowSizePresets() {
    }

    public static Entry[] entries() {
        return ENTRIES.clone();
    }

    /**
     * Combo box items: indices {@code 0 .. ENTRIES.length-1} = presets, last = {@link #CUSTOM_LABEL}.
     */
    public static String[] comboLabels() {
        String[] out = new String[ENTRIES.length + 1];
        for (int i = 0; i < ENTRIES.length; i++) {
            out[i] = ENTRIES[i].label;
        }
        out[ENTRIES.length] = CUSTOM_LABEL;
        return out;
    }

    public static boolean isCustomComboIndex(int comboIndex) {
        return comboIndex == CUSTOM_COMBO_INDEX;
    }

    /** One-line description for a preset row (not for custom index). */
    public static String presetSummary(int comboIndex) {
        if (comboIndex < 0 || comboIndex >= ENTRIES.length) {
            return "";
        }
        Entry e = ENTRIES[comboIndex];
        return e.width + " × " + e.height + " px — " + e.label;
    }

    public static int clampWidth(int w) {
        return Math.max(WIDTH_MIN, Math.min(WIDTH_MAX, w));
    }

    public static int clampHeight(int h) {
        return Math.max(HEIGHT_MIN, Math.min(HEIGHT_MAX, h));
    }

    /**
     * @return index into {@link #ENTRIES} if dimensions match a preset, else {@link #CUSTOM_COMBO_INDEX}
     */
    public static int presetIndexForDimensions(int w, int h) {
        int cw = clampWidth(w);
        int ch = clampHeight(h);
        for (int i = 0; i < ENTRIES.length; i++) {
            if (ENTRIES[i].width == cw && ENTRIES[i].height == ch) {
                return i;
            }
        }
        return CUSTOM_COMBO_INDEX;
    }

    public static int presetWidth(int comboIndex) {
        if (comboIndex < 0 || comboIndex >= ENTRIES.length) {
            return 1800;
        }
        return ENTRIES[comboIndex].width;
    }

    public static int presetHeight(int comboIndex) {
        if (comboIndex < 0 || comboIndex >= ENTRIES.length) {
            return 850;
        }
        return ENTRIES[comboIndex].height;
    }

    /**
     * Usable area of the default screen (taskbar / menu bar aware on many platforms).
     */
    public static Rectangle primaryScreenWorkArea() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        return ge.getMaximumWindowBounds();
    }
}
