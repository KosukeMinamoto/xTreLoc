package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Hist / Scatter chrome: per-catalog colour swatches that write {@link CatalogInfo#setColor}.
 */
public final class ChartCatalogColorCard extends JPanel {

    private static final long serialVersionUID = 1L;

    private final CatalogTablePanel catalogPanel;
    private final MapView mapView;
    private final Runnable onChanged;
    private boolean rebuilding;

    public ChartCatalogColorCard(CatalogTablePanel catalogPanel, MapView mapView, Runnable onChanged) {
        this.catalogPanel = catalogPanel;
        this.mapView = mapView;
        this.onChanged = onChanged;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppPanelStyle.getPanelBg());
        setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        if (catalogPanel != null) {
            catalogPanel.addViewerChartChangeListener(this::rebuild);
            catalogPanel.addCatalogLoadListener(hypos -> rebuild());
        }
        if (mapView != null) {
            mapView.addCatalogVisibilityChangeListener(this::rebuild);
        }
        rebuild();
    }

    void applyColor(CatalogInfo info, Color color) {
        if (info == null || color == null) {
            return;
        }
        info.setColor(color);
        if (mapView != null) {
            mapView.preferLayerStyleColors(info);
        }
        rebuild();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    void rebuild() {
        if (rebuilding) {
            return;
        }
        rebuilding = true;
        try {
            removeAll();
            JLabel hint = new JLabel("Catalog colours");
            hint.setFont(AppPanelStyle.getSectionTitleFont());
            hint.setForeground(AppPanelStyle.getTitleColor());
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(hint);
            add(Box.createVerticalStrut(6));
            List<CatalogInfo> catalogs = catalogPanel != null
                ? catalogPanel.getCatalogInfos()
                : List.of();
            if (catalogs.isEmpty()) {
                JLabel empty = new JLabel("No catalogs loaded");
                empty.setFont(AppPanelStyle.getLabelFont());
                empty.setForeground(AppPanelStyle.getMutedTextColor());
                empty.setAlignmentX(Component.LEFT_ALIGNMENT);
                add(empty);
            } else {
                for (CatalogInfo info : catalogs) {
                    add(colorRow(info));
                    add(Box.createVerticalStrut(4));
                }
            }
            revalidate();
            repaint();
        } finally {
            rebuilding = false;
        }
    }

    private JPanel colorRow(CatalogInfo info) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBackground(AppPanelStyle.getPanelBg());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JPanel swatch = swatch(info.getColor());
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        swatch.setToolTipText("Change colour");
        swatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Color chosen = JColorChooser.showDialog(ChartCatalogColorCard.this, "Color",
                    info.getColor() != null ? info.getColor() : Color.GRAY);
                if (chosen != null) {
                    applyColor(info, chosen);
                }
            }
        });
        JLabel name = new JLabel(info.getName() != null ? info.getName() : "");
        name.setFont(AppPanelStyle.getLabelFont());
        name.setForeground(AppPanelStyle.getContentTextColor());
        row.add(swatch);
        row.add(Box.createHorizontalStrut(8));
        row.add(name);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private static JPanel swatch(Color color) {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(color != null ? color : Color.GRAY);
                g2.fillRect(1, 1, getWidth() - 2, getHeight() - 2);
                g2.setColor(Color.DARK_GRAY);
                g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                g2.dispose();
            }
        };
        p.setPreferredSize(new Dimension(28, 16));
        p.setMinimumSize(new Dimension(28, 16));
        p.setMaximumSize(new Dimension(28, 16));
        p.setOpaque(false);
        return p;
    }
}
