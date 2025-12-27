package com.treloc.xtreloc.app.gui.view;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;

/**
 * A JTabbedPane with Microsoft-style appearance.
 * Features larger tabs with increased padding and custom styling.
 * 
 * @author xTreLoc Development Team
 */
public class MicrosoftStyleTabbedPane extends JTabbedPane {
    
    /**
     * Constructs a new MicrosoftStyleTabbedPane with custom UI styling.
     */
    public MicrosoftStyleTabbedPane() {
        super(JTabbedPane.TOP);
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        restoreCustomUI();
    }
    
    /**
     * Restores the custom UI after updateComponentTreeUI() is called.
     * This method should be invoked after UI updates to maintain the custom appearance.
     */
    public void restoreCustomUI() {
        setUI(new MicrosoftTabbedPaneUI());
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
    }
    
    /**
     * Custom UI delegate that implements Microsoft-style tab appearance.
     * Provides larger tabs with increased padding and custom colors.
     */
    private static class MicrosoftTabbedPaneUI extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(10, 16, 10, 16);
            selectedTabPadInsets = new Insets(2, 2, 2, 2);
            tabAreaInsets = new Insets(0, 0, 0, 0);
        }
        
        @Override
        protected Insets getTabAreaInsets(int tabPlacement) {
            return new Insets(0, 0, 0, 0);
        }
        
        @Override
        protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
            int width = super.calculateTabWidth(tabPlacement, tabIndex, metrics);
            return Math.max(width + 20, 120);
        }
        
        @Override
        protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
            int height = super.calculateTabHeight(tabPlacement, tabIndex, fontHeight);
            return Math.max(height + 8, 40);
        }
        
        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                         int x, int y, int w, int h, boolean isSelected) {
            if (isSelected) {
                g.setColor(Color.WHITE);
            } else {
                g.setColor(new Color(240, 240, 240));
            }
            g.fillRect(x, y, w, h);
        }
        
        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
            if (isSelected) {
                g.setColor(new Color(200, 200, 200));
                g.drawLine(x, y + h - 1, x + w, y + h - 1);
            }
        }
    }
}

