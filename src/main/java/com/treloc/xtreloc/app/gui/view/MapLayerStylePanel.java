package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.MapLayerStyle;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;

/**
 * Scatter / line style tables for catalogs, stations, shapefiles and the event network.
 */
public final class MapLayerStylePanel extends JPanel {

    private final MapView mapView;
    private final StyleTableModel scatterModel = new StyleTableModel(true);
    private final StyleTableModel lineModel = new StyleTableModel(false);
    private boolean applying;

    public MapLayerStylePanel(MapView mapView) {
        this.mapView = mapView;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(AppPanelStyle.getPanelBg());
        setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        setPreferredSize(new Dimension(440, 320));
        add(section("Scatter", "Catalog and station-file symbols", scatterModel, true));
        add(Box.createVerticalStrut(8));
        add(section("Line", "Shapefile, network and grid strokes", lineModel, false));
        reload();
    }

    public void reload() {
        if (mapView == null) {
            return;
        }
        applying = true;
        try {
            List<Row> scatter = new ArrayList<>();
            for (CatalogInfo c : mapView.getCatalogInfos()) {
                scatter.add(new Row("Catalog", c.getName(), c.toLayerStyle(mapView.getSymbolSize()), c, null, null));
            }
            for (MapView.StationStyleEntry e : mapView.getStationStyleEntries()) {
                if (!e.fileLevel) {
                    continue;
                }
                scatter.add(new Row("Stations", e.label, e.style.copy(), null, e, null));
            }
            scatterModel.setRows(scatter);

            List<Row> lines = new ArrayList<>();
            for (MapView.ShapefileRef s : mapView.getShapefileLayers()) {
                lines.add(new Row("Shapefile", s.name, s.style.copy(), null, null, s));
            }
            lines.add(new Row("Network", "Event links", mapView.getNetworkStyle().copy(), null, null, null));
            lines.add(new Row("Grid", "Map grid", mapView.getGridStyle().copy(), null, null, null));
            lineModel.setRows(lines);
        } finally {
            applying = false;
        }
    }

    private JPanel section(String title, String hint, StyleTableModel model, boolean scatter) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lab = new JLabel(title);
        lab.setFont(AppPanelStyle.getSectionTitleFont());
        lab.setForeground(AppPanelStyle.getTitleColor());
        JLabel sub = new JLabel(hint);
        sub.setFont(AppPanelStyle.getLabelFont());
        sub.setForeground(AppPanelStyle.getMutedTextColor());
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);
        head.add(lab);
        head.add(sub);
        p.add(head, BorderLayout.NORTH);

        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        AppPanelStyle.styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(72);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(44);
        table.getColumnModel().getColumn(3).setPreferredWidth(88);
        table.getColumnModel().getColumn(4).setPreferredWidth(52);
        table.getColumnModel().getColumn(5).setPreferredWidth(56);
        table.getColumnModel().getColumn(2).setCellRenderer(new SwatchRenderer());
        table.getColumnModel().getColumn(2).setCellEditor(new SwatchEditor());
        if (scatter) {
            JComboBox<CatalogInfo.SymbolType> symbols = new JComboBox<>(CatalogInfo.SymbolType.values());
            table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(symbols));
        } else {
            JComboBox<MapLayerStyle.LineStyle> lines = new JComboBox<>(MapLayerStyle.LineStyle.values());
            table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(lines));
        }
        table.getColumnModel().getColumn(4).setCellEditor(new SpinnerEditor(1, 40, 1));
        table.getColumnModel().getColumn(5).setCellEditor(new SpinnerEditor(0, 100, 5));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(420, scatter ? 140 : 110));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(scroll, BorderLayout.CENTER);
        model.addTableModelListener(e -> {
            if (!applying && e.getFirstRow() >= 0) {
                applyRow(model.rowAt(e.getFirstRow()));
            }
        });
        return p;
    }

    private void applyRow(Row row) {
        if (row == null || mapView == null) {
            return;
        }
        if (row.catalog != null) {
            row.catalog.applyLayerStyle(row.style);
            mapView.preferLayerStyleColors(row.catalog);
            return;
        }
        if (row.station != null) {
            mapView.applyStationStyle(row.station.id, row.style);
            if (row.station.fileLevel) {
                reload();
            }
            return;
        }
        if (row.shapefile != null) {
            mapView.setShapefileStyle(row.shapefile.title, row.style);
            return;
        }
        if ("Grid".equals(row.kind)) {
            mapView.setGridStyle(row.style);
            return;
        }
        mapView.setNetworkStyle(row.style);
    }

    private static final class Row {
        final String kind;
        final String name;
        final MapLayerStyle style;
        final CatalogInfo catalog;
        final MapView.StationStyleEntry station;
        final MapView.ShapefileRef shapefile;

        Row(String kind, String name, MapLayerStyle style, CatalogInfo catalog,
                MapView.StationStyleEntry station, MapView.ShapefileRef shapefile) {
            this.kind = kind;
            this.name = name;
            this.style = style;
            this.catalog = catalog;
            this.station = station;
            this.shapefile = shapefile;
        }
    }

    private static final class StyleTableModel extends AbstractTableModel {
        private final boolean scatter;
        private final List<Row> rows = new ArrayList<>();

        StyleTableModel(boolean scatter) {
            this.scatter = scatter;
        }

        void setRows(List<Row> next) {
            rows.clear();
            rows.addAll(next);
            fireTableDataChanged();
        }

        Row rowAt(int r) {
            return r >= 0 && r < rows.size() ? rows.get(r) : null;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Type";
                case 1 -> "Name";
                case 2 -> "Color";
                case 3 -> scatter ? "Symbol" : "Line";
                case 4 -> "Size";
                default -> "Opacity";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 2) {
                return Color.class;
            }
            if (columnIndex == 4 || columnIndex == 5) {
                return Integer.class;
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex >= 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.kind;
                case 1 -> row.name;
                case 2 -> row.style.getColor();
                case 3 -> scatter ? row.style.getSymbol() : row.style.getLineStyle();
                case 4 -> Math.round(row.style.getSize());
                default -> Math.round(row.style.getOpacity() * 100f);
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            if (columnIndex == 2 && aValue instanceof Color) {
                row.style.setColor((Color) aValue);
            } else if (columnIndex == 3) {
                if (scatter && aValue instanceof CatalogInfo.SymbolType) {
                    row.style.setSymbol((CatalogInfo.SymbolType) aValue);
                } else if (!scatter && aValue instanceof MapLayerStyle.LineStyle) {
                    row.style.setLineStyle((MapLayerStyle.LineStyle) aValue);
                }
            } else if (columnIndex == 4 && aValue instanceof Number) {
                row.style.setSize(((Number) aValue).floatValue());
            } else if (columnIndex == 5 && aValue instanceof Number) {
                row.style.setOpacity(((Number) aValue).floatValue() / 100f);
            }
            fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }

    private static final class SwatchRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
            p.setOpaque(true);
            p.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            p.add(swatch(value instanceof Color ? (Color) value : Color.GRAY));
            return p;
        }
    }

    private static final class SwatchEditor extends AbstractCellEditor implements TableCellEditor {
        private Color value = Color.GRAY;

        @Override
        public Object getCellEditorValue() {
            return value;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.value = value instanceof Color ? (Color) value : Color.GRAY;
            JPanel swatch = swatch(this.value);
            swatch.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Color chosen = javax.swing.JColorChooser.showDialog(table, "Color", SwatchEditor.this.value);
                    if (chosen != null) {
                        SwatchEditor.this.value = chosen;
                    }
                    stopCellEditing();
                }
            });
            return swatch;
        }
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
        p.setOpaque(false);
        return p;
    }

    private static final class SpinnerEditor extends AbstractCellEditor implements TableCellEditor {
        private final JSpinner spinner;

        SpinnerEditor(int min, int max, int step) {
            spinner = new JSpinner(new SpinnerNumberModel(min, min, max, step));
        }

        @Override
        public Object getCellEditorValue() {
            return spinner.getValue();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            spinner.setValue(value instanceof Number ? ((Number) value).intValue() : 1);
            return spinner;
        }
    }
}
