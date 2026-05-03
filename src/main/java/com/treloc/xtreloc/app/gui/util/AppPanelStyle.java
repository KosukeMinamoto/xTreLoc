package com.treloc.xtreloc.app.gui.util;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * Shared panel styling for a polished, commercial-software look.
 * Use for Solver, Viewer, Picking, and Settings tabs for consistent appearance.
 * Design is centralized here; all colors and style helpers are defined in one place.
 */
public final class AppPanelStyle {

    /* ----- Colors (single theme, centralized) ----- */
    private static final Color PANEL_BG = new Color(250, 250, 252);
    private static final Color BORDER_COLOR = new Color(214, 218, 223);
    private static final Color TITLE_COLOR = new Color(45, 55, 72);
    private static final Color PRIMARY_BUTTON = new Color(59, 130, 246);
    private static final Color SUCCESS_BUTTON = new Color(34, 197, 94);
    private static final Color MUTED_TEXT = new Color(107, 114, 128);
    private static final Color CONTENT_BG = new Color(255, 255, 255);
    private static final Color CONTENT_FG = new Color(30, 35, 42);
    private static final Color DISABLED_BG = new Color(240, 240, 240);
    private static final Color DISABLED_FG = new Color(150, 150, 150);
    private static final Color TABLE_HEADER_BG = new Color(240, 240, 242);

    /* ----- Typography: from settings.json "font" via {@link UiFonts} ----- */

    /** Body label font (12 pt) from General → Font. */
    public static Font getLabelFont() {
        return UiFonts.getLabelFont();
    }

    /** Section / titled-border title (13 pt bold) from General → Font. */
    public static Font getSectionTitleFont() {
        return UiFonts.getSectionTitleFont();
    }

    /* ----- Spacing ----- */
    /** Standard insets for section content */
    public static final Insets SECTION_INSETS = new Insets(12, 14, 12, 14);
    /** Gap between label and control (pixels) */
    public static final int GAP = 8;

    private AppPanelStyle() {}

    public static Color getPanelBg() { return PANEL_BG; }
    public static Color getBorderColor() { return BORDER_COLOR; }
    public static Color getTitleColor() { return TITLE_COLOR; }
    public static Color getPrimaryButtonColor() { return PRIMARY_BUTTON; }
    public static Color getSuccessButtonColor() { return SUCCESS_BUTTON; }
    public static Color getMutedTextColor() { return MUTED_TEXT; }
    public static Color getContentBg() { return CONTENT_BG; }
    public static Color getContentTextColor() { return CONTENT_FG; }
    public static Color getDisabledContentBg() { return DISABLED_BG; }
    public static Color getDisabledContentFg() { return DISABLED_FG; }

    /**
     * Creates a section border with title (commercial-style: thin border + left-aligned title).
     */
    public static Border createSectionBorder(String title) {
        Border line = BorderFactory.createLineBorder(getBorderColor(), 1);
        TitledBorder titled = BorderFactory.createTitledBorder(
            line,
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP,
            getSectionTitleFont(),
            getTitleColor()
        );
        return new CompoundBorder(titled, new EmptyBorder(SECTION_INSETS));
    }

    /**
     * Creates a simple titled border (no extra inner padding).
     */
    public static Border createTitledSectionBorder(String title) {
        Border line = BorderFactory.createLineBorder(getBorderColor(), 1);
        TitledBorder titled = BorderFactory.createTitledBorder(
            line,
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP,
            getSectionTitleFont(),
            getTitleColor()
        );
        return titled;
    }

    /**
     * Style a primary action button (blue).
     */
    public static void stylePrimaryButton(JButton button) {
        if (button == null) return;
        button.setBackground(getPrimaryButtonColor());
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
    }

    /**
     * Style a success/save button (green).
     */
    public static void styleSuccessButton(JButton button) {
        if (button == null) return;
        button.setBackground(getSuccessButtonColor());
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
    }

    /**
     * Set panel background to the standard panel color for the current theme.
     */
    public static void setPanelBackground(JPanel panel) {
        if (panel != null) panel.setBackground(getPanelBg());
    }

    /**
     * Apply theme-aware background and foreground to a table (cell and header).
     */
    public static void styleTable(JTable table) {
        if (table == null) return;
        table.setBackground(getContentBg());
        table.setForeground(getContentTextColor());
        table.setGridColor(BORDER_COLOR);
        if (table.getTableHeader() != null) {
            table.getTableHeader().setBackground(TABLE_HEADER_BG);
            table.getTableHeader().setForeground(getContentTextColor());
        }
    }

    /**
     * Apply theme-aware background and foreground to a text field.
     */
    public static void styleTextField(JTextField field) {
        if (field == null) return;
        field.setBackground(getContentBg());
        field.setForeground(getContentTextColor());
        field.setCaretColor(getContentTextColor());
    }

    /**
     * Apply theme-aware background and foreground to a combo box (and its list).
     */
    public static void styleComboBox(JComboBox<?> combo) {
        if (combo == null) return;
        combo.setBackground(getContentBg());
        combo.setForeground(getContentTextColor());
    }

    /**
     * Apply theme-aware background to a scroll pane's viewport (e.g. for JList, JTable, JTextArea).
     */
    public static void styleScrollPane(JScrollPane scroll) {
        if (scroll == null) return;
        scroll.getViewport().setBackground(getContentBg());
        Component view = scroll.getViewport().getView();
        if (view != null) {
            view.setBackground(getContentBg());
            if (view instanceof JTextComponent) {
                ((JTextComponent) view).setForeground(getContentTextColor());
                ((JTextComponent) view).setCaretColor(getContentTextColor());
            }
            if (view instanceof JList) {
                ((JList<?>) view).setForeground(getContentTextColor());
            }
            if (view instanceof JTable) {
                styleTable((JTable) view);
            }
            if (view instanceof JTree) {
                styleTree((JTree) view);
            }
        }
    }

    /**
     * Apply theme-aware background and foreground to a list.
     */
    public static void styleList(JList<?> list) {
        if (list == null) return;
        list.setBackground(getContentBg());
        list.setForeground(getContentTextColor());
        list.setSelectionBackground(PRIMARY_BUTTON);
        list.setSelectionForeground(Color.WHITE);
    }

    /**
     * Apply theme-aware background and foreground to a tree.
     */
    public static void styleTree(JTree tree) {
        if (tree == null) return;
        tree.setBackground(getContentBg());
        tree.setForeground(getContentTextColor());
    }

    /**
     * Recursively apply theme to a container: panel bg, label/checkbox fg, tables, text fields, lists, trees, combos, spinners, scroll panes, tabbed panes.
     * Call from panel constructors so all child components get correct background and text color.
     */
    public static void applyThemeTo(Container c) {
        if (c == null) return;
        if (c instanceof JPanel) {
            ((JPanel) c).setBackground(getPanelBg());
        }
        if (c instanceof JLabel) {
            ((JLabel) c).setForeground(getContentTextColor());
        }
        if (c instanceof JCheckBox) {
            ((JCheckBox) c).setForeground(getContentTextColor());
        }
        if (c instanceof JTextField) {
            styleTextField((JTextField) c);
        }
        if (c instanceof JComboBox) {
            styleComboBox((JComboBox<?>) c);
        }
        if (c instanceof JTable) {
            styleTable((JTable) c);
        }
        if (c instanceof JList) {
            styleList((JList<?>) c);
        }
        if (c instanceof JTree) {
            styleTree((JTree) c);
        }
        if (c instanceof JSpinner) {
            c.setBackground(getContentBg());
            c.setForeground(getContentTextColor());
            JComponent editor = ((JSpinner) c).getEditor();
            if (editor instanceof JSpinner.DefaultEditor) {
                JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
                styleTextField(tf);
            }
        }
        if (c instanceof JScrollPane) {
            styleScrollPane((JScrollPane) c);
        }
        if (c instanceof JTabbedPane) {
            c.setBackground(getPanelBg());
            c.setForeground(getContentTextColor());
        }
        if (c instanceof JSplitPane) {
            c.setBackground(getPanelBg());
        }
        for (Component child : c.getComponents()) {
            if (child instanceof Container) {
                applyThemeTo((Container) child);
            } else if (child instanceof JLabel) {
                ((JLabel) child).setForeground(getContentTextColor());
            } else if (child instanceof JCheckBox) {
                ((JCheckBox) child).setForeground(getContentTextColor());
            }
        }
    }
}
