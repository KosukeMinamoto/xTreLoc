package com.treloc.xtreloc.app.gui.util;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/**
 * Second click on the already-selected row clears the selection (and the caller clears the map highlight).
 */
public final class TableHighlightToggle {

    @FunctionalInterface
    public interface OnClear {
        void clear();
    }

    private TableHighlightToggle() {
    }

    public static final String CLEARING_PROPERTY = "xtreloc.highlight.toggle.clearing";

    public static void install(JTable table, OnClear onClear) {
        if (table == null || onClear == null) {
            return;
        }
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row == table.getSelectedRow()) {
                    table.putClientProperty(CLEARING_PROPERTY, Boolean.TRUE);
                    table.clearSelection();
                    onClear.clear();
                    e.consume();
                    SwingUtilities.invokeLater(() -> table.putClientProperty(CLEARING_PROPERTY, null));
                }
            }
        });
    }

    public static boolean isClearing(JTable table) {
        return table != null && Boolean.TRUE.equals(table.getClientProperty(CLEARING_PROPERTY));
    }
}
