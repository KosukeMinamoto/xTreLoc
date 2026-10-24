package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class SettingsPanelTest {

    @Test
    public void chartCardDoesNotUseHtmlItalicAndIsScrollable() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final SettingsPanel[] holder = new SettingsPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new SettingsPanel(null));
        assertTrue("Chart card needs a scroll pane", hasScrollPane(holder[0]));
        assertTrue(hasLabel(holder[0], "Plot (Hist / Scatter / Residual)"));
        assertTrue(hasLabel(holder[0], "Date / time"));
        assertTrue(hasLabel(holder[0], "Color bar time labels"));
        assertFalse(hasLabelContaining(holder[0], "<html><i>— Plot"));
        assertFalse(hasLabelContaining(holder[0], "<html><i>— Date"));
    }

    private static boolean hasScrollPane(Container c) {
        if (c instanceof JScrollPane) {
            return true;
        }
        for (Component k : c.getComponents()) {
            if (k instanceof Container && hasScrollPane((Container) k)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLabel(Container c, String text) {
        if (c instanceof JLabel && text.equals(((JLabel) c).getText())) {
            return true;
        }
        for (Component k : c.getComponents()) {
            if (k instanceof Container && hasLabel((Container) k, text)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLabelContaining(Container c, String fragment) {
        if (c instanceof JLabel) {
            String t = ((JLabel) c).getText();
            if (t != null && t.contains(fragment)) {
                return true;
            }
        }
        for (Component k : c.getComponents()) {
            if (k instanceof Container && hasLabelContaining((Container) k, fragment)) {
                return true;
            }
        }
        return false;
    }
}
