package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;

/**
 * A JTabbedPane with a refined, modern tab bar.
 * Uses AppPanelStyle for consistent colors; selected tab has an accent line and blends with content.
 */
public class MicrosoftStyleTabbedPane extends JTabbedPane {

    private static final int TAB_ACCENT_HEIGHT = 3;
    private static final int TAB_HORIZONTAL_PADDING = 20;
    private static final int TAB_VERTICAL_PADDING = 12;
    private static final int TAB_MIN_WIDTH = 100;
    private static final int TAB_MIN_HEIGHT = 40;

    public MicrosoftStyleTabbedPane() {
        super(JTabbedPane.TOP);
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        restoreCustomUI();
    }

    public void restoreCustomUI() {
        setUI(new RefinedTabbedPaneUI());
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        setBackground(AppPanelStyle.getPanelBg());
        setForeground(AppPanelStyle.getContentTextColor());
    }

    private static class RefinedTabbedPaneUI extends BasicTabbedPaneUI {

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(TAB_VERTICAL_PADDING, TAB_HORIZONTAL_PADDING, TAB_VERTICAL_PADDING, TAB_HORIZONTAL_PADDING);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(0, 8, 0, 0);
            contentBorderInsets = new Insets(0, 0, 0, 0);
            tabPane.setBackground(AppPanelStyle.getPanelBg());
            tabPane.setForeground(AppPanelStyle.getContentTextColor());
        }

        @Override
        protected Insets getTabAreaInsets(int tabPlacement) {
            return new Insets(0, 8, 0, 0);
        }

        @Override
        protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
            int width = super.calculateTabWidth(tabPlacement, tabIndex, metrics);
            return Math.max(width, TAB_MIN_WIDTH);
        }

        @Override
        protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
            int height = super.calculateTabHeight(tabPlacement, tabIndex, fontHeight);
            return Math.max(height, TAB_MIN_HEIGHT);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                         int x, int y, int w, int h, boolean isSelected) {
            if (isSelected) {
                g.setColor(AppPanelStyle.getContentBg());
                g.fillRect(x, y, w, h);
                g.setColor(AppPanelStyle.getPrimaryButtonColor());
                g.fillRect(x, y + h - TAB_ACCENT_HEIGHT, w, TAB_ACCENT_HEIGHT);
            } else {
                g.setColor(AppPanelStyle.getPanelBg());
                g.fillRect(x, y, w, h);
            }
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
            // No per-tab border; accent line is drawn in paintTabBackground
        }

        @Override
        protected void paintContentBorderTopEdge(Graphics g, int tabPlacement, int selectedIndex,
                                                int x, int y, int w, int h) {
            g.setColor(AppPanelStyle.getBorderColor());
            g.drawLine(x, y, x + w, y);
        }

        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                                int tabIndex, String title, Rectangle textRect, boolean isSelected) {
            if (isSelected) {
                g.setFont(font.deriveFont(Font.BOLD));
                g.setColor(AppPanelStyle.getContentTextColor());
            } else {
                g.setFont(font);
                g.setColor(AppPanelStyle.getMutedTextColor());
            }
            super.paintText(g, tabPlacement, font, metrics, tabIndex, title, textRect, isSelected);
        }
    }
}

