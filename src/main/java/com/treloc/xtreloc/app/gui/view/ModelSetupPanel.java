package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.controller.MapController;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.SharedFileManager;
import com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry;
import com.treloc.xtreloc.io.Station;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;

/**
 * Left pane of the Model tab: a layer tree (Model, Solver, Stations, Hypocenters, Shapefiles) on
 * top and the detail panel of the selected node below. The detail panels are the former tabs.
 */
public class ModelSetupPanel extends JPanel {

    private final MapView mapView;
    private final VelocityViewerPanel viewer;
    private final StationTablePanel stationPanel;
    private final VelocityModelTablePanel velocityPanel;
    private final CatalogTablePanel catalogPanel;
    private final ShapefileTablePanel shapefilePanel;
    private final JTextField[] regionFields = new JTextField[8];
    private final JTextField strikeField = new JTextField("0", 6);
    private final JTextField dipField = new JTextField("0", 6);
    private final JLabel strikeLabel = fieldLabel("strike");
    private final JLabel dipLabel = fieldLabel("dip");
    private final JLabel status = new JLabel(" ");
    private static final String CARD_MODEL = "Model";
    private static final String CARD_SOLVER = "Solver";
    private static final String CARD_STATION = "Station";
    private static final String CARD_CATALOG = "Catalog";
    private static final String CARD_SHAPEFILE = "Shapefile";
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final LayerTreePanel layerTree = new LayerTreePanel();
    private final TravelTimeTableCard travelTimeCard;
    private JPanel rangePanel;
    private boolean updating;

    public ModelSetupPanel(MapView mapView, MapController mapController, VelocityViewerPanel viewer) {
        this.mapView = mapView;
        this.viewer = viewer;
        setLayout(new BorderLayout());
        AppPanelStyle.setPanelBackground(this);

        stationPanel = new StationTablePanel(mapView);
        viewer.setStationSource(stationPanel::getVisibleStations);
        velocityPanel = new VelocityModelTablePanel();
        catalogPanel = new CatalogTablePanel(mapView);
        shapefilePanel = new ShapefileTablePanel(mapView, mapController);
        velocityPanel.addVisualizeListener(viewer::setModel);
        velocityPanel.addStationFileListener(stationPanel::setLinkedStationFile);
        stationPanel.addStationsChangedListener(this::autoFillRangeFromStationsIfVelocity);

        AppPanelStyle.setPanelBackground(cards);
        rangePanel = geometryPanel();
        travelTimeCard = new TravelTimeTableCard(viewer.tableHost(), velocityPanel, rangePanel);
        travelTimeCard.addSourceListener(tt -> setStrikeDipEnabled(!tt));
        setStrikeDipEnabled(!travelTimeCard.isTravelTimeSource());
        viewer.addVolumeListener(travelTimeCard::refresh);
        SharedFileManager.getInstance().addGeometryListener(travelTimeCard::refresh);
        cards.add(travelTimeCard, CARD_MODEL);
        cards.add(stationPanel, CARD_STATION);
        cards.add(catalogPanel, CARD_CATALOG);
        cards.add(shapefilePanel, CARD_SHAPEFILE);
        wireLayerTree();
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, layerTree, cards);
        split.setResizeWeight(0.0);
        split.setDividerLocation(190);
        split.setOneTouchExpandable(true);
        split.setContinuousLayout(true);
        split.setBackground(AppPanelStyle.getPanelBg());
        add(split, BorderLayout.CENTER);

        status.setFont(AppPanelStyle.getLabelFont());
        status.setForeground(AppPanelStyle.getMutedTextColor());
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(status, BorderLayout.SOUTH);

        SharedFileManager.getInstance().addComputationBusyListener(this::setInputsLocked);
        SharedFileManager.getInstance().addGeometryListener(this::loadGeometryFromShared);
        loadGeometryFromShared();
        String taup = SharedFileManager.getInstance().getTaupFile();
        if (taup != null && !taup.isBlank()
            && !com.treloc.xtreloc.solver.table.TravelTimeTables.isTableSpec(taup)) {
            velocityPanel.selectModel(taup);
        }
        viewer.setModel(velocityPanel.getVisualized());
        AppPanelStyle.applyThemeTo(this);
    }

    public VelocityModelTablePanel getVelocityModelPanel() {
        return velocityPanel;
    }

    public StationTablePanel getStationPanel() {
        return stationPanel;
    }

    public CatalogTablePanel getCatalogPanel() {
        return catalogPanel;
    }

    /** Adds the Solver node (after Model) and its detail panel. */
    public void insertSolverTab(JComponent solver) {
        if (solver == null) {
            return;
        }
        cards.add(solver, CARD_SOLVER);
        layerTree.setSolverAvailable(true);
    }

    /** Titles of the top-level layer-tree nodes (Model, Solver, Stations, Hypocenters, Shapefiles). */
    List<String> layerNodeTitles() {
        return layerTree.topLevelTitles();
    }

    LayerTreePanel layerTreeForTest() {
        return layerTree;
    }

    private void wireLayerTree() {
        layerTree.setListener(new LayerTreePanel.Listener() {
            @Override
            public void onSelect(LayerTreePanel.Kind kind, int index) {
                switch (kind) {
                    case MODEL:
                        cardLayout.show(cards, CARD_MODEL);
                        break;
                    case SOLVER:
                        cardLayout.show(cards, CARD_SOLVER);
                        break;
                    case STATIONS:
                        cardLayout.show(cards, CARD_STATION);
                        break;
                    case CATALOGS:
                    case CATALOG:
                        cardLayout.show(cards, CARD_CATALOG);
                        if (kind == LayerTreePanel.Kind.CATALOG) {
                            catalogPanel.selectCatalogAt(index);
                        }
                        break;
                    case SHAPEFILES:
                    case SHAPEFILE:
                        cardLayout.show(cards, CARD_SHAPEFILE);
                        if (kind == LayerTreePanel.Kind.SHAPEFILE) {
                            shapefilePanel.selectAt(index);
                        }
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onToggle(LayerTreePanel.Kind kind, int index, boolean visible) {
                if (kind == LayerTreePanel.Kind.CATALOG) {
                    catalogPanel.setCatalogVisibleAt(index, visible);
                } else if (kind == LayerTreePanel.Kind.SHAPEFILE) {
                    shapefilePanel.setVisibleAt(index, visible);
                }
            }
        });
        catalogPanel.addCatalogListChangeListener(this::refreshCatalogLayers);
        shapefilePanel.addListChangeListener(this::refreshShapefileLayers);
        refreshCatalogLayers();
        refreshShapefileLayers();
    }

    private void refreshCatalogLayers() {
        List<String> names = new ArrayList<>();
        List<Boolean> visible = new ArrayList<>();
        List<com.treloc.xtreloc.app.gui.model.CatalogInfo> infos = catalogPanel.getCatalogInfos();
        for (int i = 0; i < infos.size(); i++) {
            names.add(infos.get(i).getName());
            visible.add(catalogPanel.isCatalogVisibleAt(i));
        }
        layerTree.setCatalogs(names, visible);
    }

    private void refreshShapefileLayers() {
        List<String> names = new ArrayList<>();
        List<Boolean> visible = new ArrayList<>();
        List<java.io.File> files = shapefilePanel.getShapefiles();
        for (int i = 0; i < files.size(); i++) {
            names.add(files.get(i).getName());
            visible.add(shapefilePanel.isVisibleAt(i));
        }
        layerTree.setShapefiles(names, visible);
    }

    private JPanel geometryPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        AppPanelStyle.setPanelBackground(p);
        p.setBorder(AppPanelStyle.createTitledSectionBorder("Range"));

        for (int i = 0; i < regionFields.length; i++) {
            regionFields[i] = new JTextField(7);
            AppPanelStyle.styleTextField(regionFields[i]);
            regionFields[i].addActionListener(e -> applyGeometry());
        }
        p.add(rangeRow("lat", "°", regionFields[2], regionFields[3]));
        p.add(rangeRow("lon", "°", regionFields[0], regionFields[1]));
        p.add(rangeRow("depth", "km", regionFields[4], regionFields[5]));

        JPanel spacing = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        spacing.setBackground(AppPanelStyle.getPanelBg());
        spacing.setAlignmentX(Component.LEFT_ALIGNMENT);
        spacing.add(fieldLabel("spacing H"));
        spacing.add(regionFields[6]);
        spacing.add(fieldLabel("km"));
        spacing.add(fieldLabel("Z"));
        spacing.add(regionFields[7]);
        spacing.add(fieldLabel("km"));
        regionFields[6].setToolTipText("Horizontal grid spacing (NonLinLoc dx = dy, km)");
        regionFields[7].setToolTipText("Depth grid spacing (NonLinLoc dz, km)");
        p.add(spacing);

        AppPanelStyle.styleTextField(strikeField);
        AppPanelStyle.styleTextField(dipField);
        strikeField.setToolTipText("Strike of the layering, azimuth from north (deg)");
        dipField.setToolTipText("Dip of the layering (0 = horizontal; negative reverses the dip direction, −85 to 85)");
        strikeField.addActionListener(e -> applyGeometry());
        dipField.addActionListener(e -> applyGeometry());
        JPanel dip = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        dip.setBackground(AppPanelStyle.getPanelBg());
        dip.setAlignmentX(Component.LEFT_ALIGNMENT);
        dip.add(strikeLabel);
        dip.add(strikeField);
        dip.add(fieldLabel("°"));
        dip.add(dipLabel);
        dip.add(dipField);
        dip.add(fieldLabel("°"));
        p.add(dip);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        buttons.setBackground(AppPanelStyle.getPanelBg());
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton autoRange = new JButton("Auto-fill range");
        autoRange.setToolTipText("Set the rectangle from stations (Velocity) or the travel-time table");
        autoRange.addActionListener(e -> autoFillRange());
        JButton apply = new JButton("Apply");
        apply.addActionListener(e -> writeGeometry(true));
        buttons.add(autoRange);
        buttons.add(apply);
        p.add(buttons);
        return p;
    }

    private static JPanel rangeRow(String name, String unit, JTextField min, JTextField max) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        r.setBackground(AppPanelStyle.getPanelBg());
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.add(fieldLabel(name));
        r.add(min);
        r.add(fieldLabel("–"));
        r.add(max);
        r.add(fieldLabel(unit));
        return r;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppPanelStyle.getLabelFont());
        l.setForeground(AppPanelStyle.getContentTextColor());
        return l;
    }

    private void loadGeometryFromShared() {
        Geometry g = SharedFileManager.getInstance().getGeometry();
        updating = true;
        try {
            if (g == null || !g.hasRegion()) {
                for (int i = 0; i < 8; i++) {
                    if (regionFields[i] != null) {
                        regionFields[i].setText("");
                    }
                }
                strikeField.setText("");
                dipField.setText("");
            } else {
                double[] v = {g.lonMin, g.lonMax, g.latMin, g.latMax, g.minDepthKm, g.maxDepthKm,
                    g.spacingHoriz(), g.spacingDepth()};
                for (int i = 0; i < 8; i++) {
                    if (regionFields[i] != null) {
                        regionFields[i].setText(format(v[i]));
                    }
                }
                strikeField.setText(format(g.strikeDeg));
                dipField.setText(format(g.dipDeg));
            }
        } finally {
            updating = false;
        }
    }

    private void applyGeometry() {
        writeGeometry(true);
    }

    /** Writes Range to {@link SharedFileManager}. Rebuilds the visualized volume only when requested. */
    private void writeGeometry(boolean rebuildViewer) {
        if (updating) {
            return;
        }
        try {
            Geometry g = new Geometry();
            g.lonMin = Double.parseDouble(regionFields[0].getText().trim());
            g.lonMax = Double.parseDouble(regionFields[1].getText().trim());
            g.latMin = Double.parseDouble(regionFields[2].getText().trim());
            g.latMax = Double.parseDouble(regionFields[3].getText().trim());
            g.minDepthKm = Double.parseDouble(regionFields[4].getText().trim());
            g.maxDepthKm = Double.parseDouble(regionFields[5].getText().trim());
            g.spacingKm = Double.parseDouble(regionFields[6].getText().trim());
            g.spacingDepthKm = Double.parseDouble(regionFields[7].getText().trim());
            g.strikeDeg = parseOrZero(strikeField.getText());
            g.dipDeg = parseOrZero(dipField.getText());
            if (!g.hasRegion() || !(g.spacingKm > 0) || !(g.spacingDepthKm > 0)) {
                throw new IllegalArgumentException("Region must satisfy min < max, max depth > min depth, spacing > 0.");
            }
            if (travelTimeCard.isTravelTimeSource()) {
                Geometry table = travelTimeCard.tableRange();
                if (table != null && exceedsTableRange(g, table)) {
                    restoreRangeFields();
                    String msg = "Range is larger than the travel-time table. "
                        + "Use a box inside the table, or Auto-fill range. Values were restored.";
                    status.setText(msg);
                    try {
                        JOptionPane.showMessageDialog(this, msg, "Range", JOptionPane.ERROR_MESSAGE);
                    } catch (java.awt.HeadlessException ignored) {
                    }
                    return;
                }
                g.strikeDeg = table != null ? table.strikeDeg : g.strikeDeg;
                g.dipDeg = table != null ? table.dipDeg : g.dipDeg;
            }
            SharedFileManager.getInstance().setGeometry(g);
            if (rebuildViewer) {
                viewer.applyGeometryFromShared();
            }
            status.setText(String.format(Locale.ROOT,
                "Range %.2f–%.2f, %.2f–%.2f  depth %.0f–%.0f km  dxy=%.1f dz=%.1f  strike %.0f dip %.0f",
                g.lonMin, g.lonMax, g.latMin, g.latMax, g.minDepthKm, g.maxDepthKm, g.spacingKm, g.spacingDepthKm,
                g.strikeDeg, g.dipDeg));
        } catch (RuntimeException ex) {
            restoreRangeFields();
            status.setText("Invalid range: " + ex.getMessage());
        }
    }

    private void restoreRangeFields() {
        loadGeometryFromShared();
    }

    private void setStrikeDipEnabled(boolean on) {
        strikeField.setEnabled(on);
        dipField.setEnabled(on);
        strikeLabel.setEnabled(on);
        dipLabel.setEnabled(on);
        if (on) {
            AppPanelStyle.styleTextField(strikeField);
            AppPanelStyle.styleTextField(dipField);
            strikeField.setToolTipText("Strike of the layering, azimuth from north (deg)");
            dipField.setToolTipText("Dip of the layering (0 = horizontal; negative reverses the dip direction, −85 to 85)");
        } else {
            strikeField.setBackground(AppPanelStyle.getDisabledContentBg());
            strikeField.setForeground(AppPanelStyle.getDisabledContentFg());
            dipField.setBackground(AppPanelStyle.getDisabledContentBg());
            dipField.setForeground(AppPanelStyle.getDisabledContentFg());
            strikeField.setToolTipText("Fixed by the travel-time table");
            dipField.setToolTipText("Fixed by the travel-time table");
        }
    }

    private void autoFillRange() {
        if (travelTimeCard.isTravelTimeSource()) {
            travelTimeCard.refillRangeFromTable();
            return;
        }
        if (autoFillRangeFromStations()) {
            writeGeometry(true);
        }
    }

    private void autoFillRangeFromStationsIfVelocity() {
        if (travelTimeCard.isTravelTimeSource()) {
            return;
        }
        if (autoFillRangeFromStations()) {
            writeGeometry(true);
        }
    }

    /**
     * Sets Range from visible stations. A 1-D velocity model has no geographic box, so stations
     * (or a travel-time table) define the rectangle. Returns false when there are no stations.
     */
    private boolean autoFillRangeFromStations() {
        double lo0 = Double.POSITIVE_INFINITY;
        double lo1 = Double.NEGATIVE_INFINITY;
        double la0 = Double.POSITIVE_INFINITY;
        double la1 = Double.NEGATIVE_INFINITY;
        for (Station st : stationPanel.getVisibleStations()) {
            if (st == null || !Double.isFinite(st.getLon()) || !Double.isFinite(st.getLat())) {
                continue;
            }
            lo0 = Math.min(lo0, st.getLon());
            lo1 = Math.max(lo1, st.getLon());
            la0 = Math.min(la0, st.getLat());
            la1 = Math.max(la1, st.getLat());
        }
        if (!(lo1 > lo0 && la1 > la0)) {
            return false;
        }
        double padLon = Math.max(0.2, 0.05 * (lo1 - lo0));
        double padLat = Math.max(0.2, 0.05 * (la1 - la0));
        lo0 -= padLon;
        lo1 += padLon;
        la0 -= padLat;
        la1 += padLat;
        Geometry cur = SharedFileManager.getInstance().getGeometry();
        double z0 = cur != null && cur.maxDepthKm > cur.minDepthKm ? cur.minDepthKm : 0.0;
        double z1 = cur != null && cur.maxDepthKm > cur.minDepthKm ? cur.maxDepthKm : 100.0;
        double dxy = cur != null && cur.spacingKm > 0 ? cur.spacingHoriz() : 2.0;
        double dz = cur != null && cur.spacingDepthKm > 0 ? cur.spacingDepth() : 2.0;
        regionFields[0].setText(format(Math.floor(lo0 * 10) / 10));
        regionFields[1].setText(format(Math.ceil(lo1 * 10) / 10));
        regionFields[2].setText(format(Math.floor(la0 * 10) / 10));
        regionFields[3].setText(format(Math.ceil(la1 * 10) / 10));
        regionFields[4].setText(format(z0));
        regionFields[5].setText(format(z1));
        regionFields[6].setText(format(dxy));
        regionFields[7].setText(format(dz));
        if (strikeField.getText() == null || strikeField.getText().isBlank()) {
            strikeField.setText("0");
        }
        if (dipField.getText() == null || dipField.getText().isBlank()) {
            dipField.setText("0");
        }
        return true;
    }

    /** True if {@code range} extends outside the travel-time table box. */
    static boolean exceedsTableRange(Geometry range, Geometry table) {
        if (range == null || table == null) {
            return false;
        }
        final double e = 1.0e-4;
        return range.lonMin < table.lonMin - e || range.lonMax > table.lonMax + e
            || range.latMin < table.latMin - e || range.latMax > table.latMax + e
            || range.minDepthKm < table.minDepthKm - e || range.maxDepthKm > table.maxDepthKm + e;
    }

    /** Clips {@code box} {lonMin, lonMax, latMin, latMax} to {@code grid}; empty results fall back to the grid. */
    static void clipLonLat(double[] box, double[] grid) {
        box[0] = Math.max(box[0], grid[0]);
        box[1] = Math.min(box[1], grid[1]);
        box[2] = Math.max(box[2], grid[2]);
        box[3] = Math.min(box[3], grid[3]);
        if (!(box[1] > box[0])) {
            box[0] = grid[0];
            box[1] = grid[1];
        }
        if (!(box[3] > box[2])) {
            box[2] = grid[2];
            box[3] = grid[3];
        }
    }

    private static double parseOrZero(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(text.trim());
    }

    private static String format(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private void setInputsLocked(boolean locked) {
        AppPanelStyle.setTreeEnabled(stationPanel, !locked);
        AppPanelStyle.setTreeEnabled(velocityPanel, !locked);
        AppPanelStyle.setTreeEnabled(rangePanel, !locked);
        AppPanelStyle.setTreeEnabled(catalogPanel, !locked);
        AppPanelStyle.setTreeEnabled(shapefilePanel, !locked);
        travelTimeCard.setInputsLocked(locked);
        if (!locked) {
            setStrikeDipEnabled(!travelTimeCard.isTravelTimeSource());
        }
    }
}
