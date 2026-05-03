package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.UiFonts;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * Tab bar styled like Windows / Microsoft apps: light bar, hover highlight,
 * selected tab on white with a blue accent strip.
 */
public class MicrosoftStyleTabbedPane extends JTabbedPane {

    private static final Color BAR_BG = new Color(243, 243, 243);
    private static final Color TAB_HOVER = new Color(230, 230, 230);
    private static final Color TAB_SELECTED = Color.WHITE;
    private static final Color TAB_BORDER = new Color(217, 217, 217);
    private static final Color ACCENT = new Color(0, 120, 212);
    private static final Color TEXT = new Color(32, 32, 32);
    private static final Color TEXT_DIM = new Color(90, 90, 90);

    private static final int ACCENT_HEIGHT = 3;

    public MicrosoftStyleTabbedPane() {
        super(JTabbedPane.TOP);
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        setOpaque(true);
        setBackground(BAR_BG);
        setForeground(TEXT);
        setFont(resolveUiFont());
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, TAB_BORDER));
        restoreCustomUI();
    }

    /**
     * Re-applies the custom UI (e.g. after LaF change).
     */
    public void restoreCustomUI() {
        setUI(new WindowsTabUI());
        setFont(resolveUiFont());
        setBackground(BAR_BG);
        setForeground(TEXT);
    }

    private static Font resolveUiFont() {
        return UiFonts.uiPlain(13f);
    }

    private static class WindowsTabUI extends BasicTabbedPaneUI {

        private int hoverTab = -1;

        private final MouseMotionAdapter motionAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = tabForCoordinate(tabPane, e.getX(), e.getY());
                if (hoverTab != idx) {
                    hoverTab = idx;
                    tabPane.repaint();
                }
            }
        };

        private final MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoverTab = -1;
                tabPane.repaint();
            }
        };

        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabInsets = new Insets(10, 18, 10, 18);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            contentBorderInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(6, 10, 0, 10);
            tabPane.setOpaque(true);
            tabPane.setBackground(BAR_BG);
            tabPane.setForeground(TEXT);
        }

        @Override
        protected void installListeners() {
            super.installListeners();
            tabPane.addMouseMotionListener(motionAdapter);
            tabPane.addMouseListener(mouseAdapter);
        }

        @Override
        protected void uninstallListeners() {
            tabPane.removeMouseMotionListener(motionAdapter);
            tabPane.removeMouseListener(mouseAdapter);
            super.uninstallListeners();
        }

        @Override
        protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
            return 38;
        }

        @Override
        protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
            return super.calculateTabWidth(tabPlacement, tabIndex, metrics) + 10;
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            if (isSelected) {
                g2.setColor(TAB_SELECTED);
                g2.fillRect(x, y, w, h);
                g2.setColor(ACCENT);
                g2.fillRect(x, y + h - ACCENT_HEIGHT, w, ACCENT_HEIGHT);
                g2.setColor(TAB_BORDER);
                g2.drawLine(x, y, x + w - 1, y);
                g2.drawLine(x, y, x, y + h - 1);
                g2.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
            } else if (tabIndex == hoverTab) {
                g2.setColor(TAB_HOVER);
                g2.fillRect(x, y, w, h);
            } else {
                g2.setColor(BAR_BG);
                g2.fillRect(x, y, w, h);
            }
            g2.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
            // Borders for selected tab are drawn in paintTabBackground
        }

        @Override
        protected void paintContentBorderTopEdge(Graphics g, int tabPlacement, int selectedIndex,
                                                 int x, int y, int w, int h) {
            g.setColor(TAB_BORDER);
            g.drawLine(x, y, x + w - 1, y);
        }

        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                                 int tabIndex, String title, Rectangle textRect, boolean isSelected) {
            if (isSelected) {
                g.setFont(font.deriveFont(Font.BOLD));
                g.setColor(TEXT);
            } else {
                g.setFont(font);
                g.setColor(TEXT_DIM);
            }
            super.paintText(g, tabPlacement, font, metrics, tabIndex, title, textRect, isSelected);
        }
    }
}
