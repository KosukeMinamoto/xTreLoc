package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.UiFonts;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Edits map symbol shape and color for all visible catalogs (opened from the map legend).
 */
public final class LegendCatalogStyleDialog {

    private LegendCatalogStyleDialog() {
    }

    /**
     * Shows a dialog listing every catalog that currently appears in the legend (visible and non-empty).
     */
    public static void showDialog(Window parent, List<CatalogInfo> allCatalogs, MapView mapView) {
        if (mapView == null || allCatalogs == null) {
            return;
        }
        List<CatalogInfo> rows = new ArrayList<>();
        for (CatalogInfo c : allCatalogs) {
            if (c.isVisible() && c.getHypocenters() != null && !c.getHypocenters().isEmpty()) {
                rows.add(c);
            }
        }

        JDialog dlg = new JDialog(parent, "Catalog symbol appearance", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(10, 10));
        AppPanelStyle.setPanelBackground(root);
        dlg.setContentPane(root);

        JLabel hint = new JLabel(
            rows.isEmpty()
                ? "No catalogs are shown on the map."
                : "Change symbol shape and color for each catalog. Changes apply on Apply.");
        hint.setFont(UiFonts.uiPlain(11f));
        hint.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 4));
        root.add(hint, BorderLayout.NORTH);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        AppPanelStyle.setPanelBackground(listPanel);

        List<JComboBox<CatalogInfo.SymbolType>> combos = new ArrayList<>();
        List<Color[]> colorHolders = new ArrayList<>();

        for (CatalogInfo cat : rows) {
            JPanel row = new JPanel(new GridBagLayout());
            row.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(4, 6, 4, 6);
            gc.anchor = GridBagConstraints.WEST;
            gc.gridy = 0;

            gc.gridx = 0;
            gc.weightx = 0;
            gc.fill = GridBagConstraints.NONE;
            JLabel nameLab = new JLabel(cat.getName() != null ? cat.getName() : "(unnamed)");
            nameLab.setFont(UiFonts.uiPlain(12f));
            row.add(nameLab, gc);

            gc.gridx = 1;
            JComboBox<CatalogInfo.SymbolType> shapeCombo = new JComboBox<>(CatalogInfo.SymbolType.values());
            shapeCombo.setSelectedItem(cat.getSymbolType() != null ? cat.getSymbolType() : CatalogInfo.SymbolType.CIRCLE);
            combos.add(shapeCombo);
            row.add(shapeCombo, gc);

            gc.gridx = 2;
            Color initial = cat.getColor() != null ? cat.getColor() : new Color(96, 96, 96);
            Color[] holder = { initial };
            colorHolders.add(holder);

            JPanel swatch = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(holder[0]);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(Color.DARK_GRAY);
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.dispose();
                }
            };
            swatch.setPreferredSize(new Dimension(40, 22));

            JButton pickColor = new JButton("Color…");
            pickColor.addActionListener(e -> {
                Color chosen = JColorChooser.showDialog(dlg, "Color", holder[0]);
                if (chosen != null) {
                    holder[0] = chosen;
                    swatch.repaint();
                }
            });

            JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            colorRow.setOpaque(false);
            colorRow.add(swatch);
            colorRow.add(pickColor);
            row.add(colorRow, gc);

            listPanel.add(row);
            listPanel.add(Box.createVerticalStrut(2));
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dlg.dispose());
        JButton apply = new JButton("Apply");
        apply.setEnabled(!rows.isEmpty());
        apply.addActionListener(e -> {
            for (int i = 0; i < rows.size(); i++) {
                CatalogInfo cat = rows.get(i);
                cat.setColor(colorHolders.get(i)[0]);
                cat.setSymbolType((CatalogInfo.SymbolType) combos.get(i).getSelectedItem());
            }
            mapView.updateMultipleCatalogsDisplay();
            dlg.dispose();
        });
        buttons.add(cancel);
        buttons.add(apply);
        root.add(buttons, BorderLayout.SOUTH);

        dlg.setMinimumSize(new Dimension(420, 200));
        dlg.pack();
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }
}
