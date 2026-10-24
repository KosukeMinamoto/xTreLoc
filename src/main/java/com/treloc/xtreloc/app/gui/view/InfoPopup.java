package com.treloc.xtreloc.app.gui.view;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * Compact ⓘ control that opens a short explanation dialog.
 */
final class InfoPopup {

    private InfoPopup() {}

    static JButton button(Component parent, String title, String body) {
        JButton b = new JButton("\u24D8");
        b.setMargin(new Insets(0, 4, 0, 4));
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setToolTipText(title);
        b.addActionListener(e -> show(parent, title, body));
        return b;
    }

    static JPanel tabLabel(String name, Component parent, String title, String body) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        p.setOpaque(false);
        p.add(new JLabel(name));
        p.add(button(parent, title, body));
        return p;
    }

    static void show(Component parent, String title, String body) {
        JTextArea area = new JTextArea(body);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setColumns(36);
        area.setRows(Math.min(12, Math.max(4, body.split("\n").length + 2)));
        JOptionPane.showMessageDialog(parent, area, title, JOptionPane.INFORMATION_MESSAGE);
    }
}
