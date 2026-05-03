package com.treloc.xtreloc.app.gui.util;

import javax.swing.*;
import java.awt.*;

/**
 * Resolves UI text fonts from {@code ~/.xtreloc/settings.json} {@code font} (General),
 * excluding log and chart rendering (those use monospaced / {@link ChartAppearanceSettings}).
 */
public final class UiFonts {

    private UiFonts() {
    }

    /**
     * Plain UI font at the given point size from the current {@code font} setting.
     */
    public static Font uiPlain(float pointSize) {
        return fromChoice(AppSettingsCache.snapshot().getFont(), Font.PLAIN, pointSize);
    }

    /**
     * Bold UI font at the given point size from the current {@code font} setting.
     */
    public static Font uiBold(float pointSize) {
        return fromChoice(AppSettingsCache.snapshot().getFont(), Font.BOLD, pointSize);
    }

    /**
     * Standard body text (12 pt) for labels and controls.
     */
    public static Font getLabelFont() {
        return uiPlain(12f);
    }

    /**
     * Section / titled-border title style (13 pt bold).
     */
    public static Font getSectionTitleFont() {
        return uiBold(13f);
    }

    private static Font fromChoice(String choice, int style, float pointSize) {
        if (choice == null) {
            choice = "default";
        }
        choice = choice.trim();
        int size = Math.max(6, Math.round(pointSize));
        switch (choice) {
            case "Sans Serif":
                return new Font(Font.SANS_SERIF, style, size);
            case "Serif":
                return new Font(Font.SERIF, style, size);
            case "Monospaced":
                return new Font(Font.MONOSPACED, style, size);
            default:
                Font laf = UIManager.getFont("Label.font");
                if (laf != null) {
                    return laf.deriveFont(style, pointSize);
                }
                return new Font(Font.SANS_SERIF, style, size);
        }
    }

    /**
     * Pushes the selected UI font into {@link UIManager} for common Swing component keys.
     * No-op when {@code fontChoice} is {@code null} or {@code "default"} (Look &amp; Feel defaults).
     */
    public static void applyToUIManager(String fontChoice) {
        if (fontChoice == null || "default".equalsIgnoreCase(fontChoice.trim())) {
            return;
        }
        Font base = fromChoice(fontChoice, Font.PLAIN, 12f);
        String[] keys = {
            "Label.font", "Button.font", "TextField.font", "ComboBox.font",
            "TextArea.font", "List.font", "Table.font", "TableHeader.font", "Tree.font",
            "TabbedPane.font", "Spinner.font", "CheckBox.font", "RadioButton.font", "ToggleButton.font",
            "MenuBar.font", "Menu.font", "MenuItem.font", "OptionPane.font", "ToolTip.font", "TitledBorder.font"
        };
        for (String key : keys) {
            UIManager.put(key, base);
        }
    }
}
