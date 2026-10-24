package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.VelocityModelSpec;
import com.treloc.xtreloc.app.gui.service.ShapefileOverlayExtractor;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.volume3d.ColorMaps;
import com.treloc.xtreloc.app.gui.volume3d.Map3DIcons;
import com.treloc.xtreloc.app.gui.volume3d.Volume3DCanvas;
import com.treloc.xtreloc.app.gui.volume3d.Volume3DRenderer;
import com.treloc.xtreloc.io.Station;
import com.treloc.xtreloc.solver.Raytrace1D;
import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D;
import com.treloc.xtreloc.solver.grid.LayerCakeModel;
import com.treloc.xtreloc.solver.grid.LayerCakeVolumes;
import com.treloc.xtreloc.solver.grid.StationTimeGrid;
import com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder;
import com.treloc.xtreloc.solver.grid.TravelTimeGridSet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * "3D map": orthogonal longitude / latitude / depth sections of a velocity model that can be rotated, zoomed and
 * panned. The full width is used for the view; settings live in pop-up cards opened from an icon strip on the right
 * (View, Background, Range, Style, Tools, Export).
 *
 * <p>The model is the one checked ("Visualize") in the 2D map's Velocity Model tab ({@link #setModel}); shapefiles,
 * stations and hypocentres follow what the 2D map shows. The colour palette and range are edited through the same
 * dialog as in the 2D map (click the colour bar).</p>
 *
 * <p>Models are loaded lazily, the first time the tab is shown ({@link #ensureLoaded()}), because the bundled grid
 * has several hundred thousand nodes.</p>
 */
public class VelocityViewerPanel {

    private static final Logger logger = Logger.getLogger(VelocityViewerPanel.class.getName());

    private static final int STRIP_WIDTH = 46;
    private static final int CARD_WIDTH = 240;
    private static final int SLIDER_STEPS = 1000;
    private static final int OVERLAY_DEBOUNCE_MS = 150;
    private final Volume3DCanvas canvas = new Volume3DCanvas();
    private final Volume3DRenderer renderer = canvas.renderer();
    private final MapView mapView;
    private final MapView.ColorBarPanel colorBar = new MapView.ColorBarPanel(MapView.ColorPalette.SEISMIC);

    private final JPanel root = new JPanel(new BorderLayout());
    private final JLayeredPane area;
    private final JPanel strip = new JPanel();
    private final JPanel cardHost = new JPanel(new BorderLayout());
    private final JLabel cardTitle = new JLabel();
    private final JScrollPane cardScroll = new JScrollPane();
    private final Map<String, JToggleButton> stripButtons = new LinkedHashMap<>();
    private final Map<String, JComponent> cards = new HashMap<>();
    private String openCard;
    private MapLayerStylePanel stylePanel;

    private JLabel statusLabel;
    private JLabel rangeInfoLabel;
    private JComboBox<String> channelCombo;
    private final JTextField[] regionFields = new JTextField[8];
    private JSlider opacitySlider;
    private JSpinner vexagSpinner;
    private JButton paletteButton;
    private JButton autoButton;
    private JButton saveButton;
    private JButton copyButton;
    private final JCheckBox[] layerChecks = new JCheckBox[4];
    private final JCheckBox[] sliceChecks = new JCheckBox[3];
    private final JSlider[] sliceSliders = new JSlider[3];
    private final JLabel[] sliceLabels = new JLabel[3];

    private VelocityVolume3D volume;
    private VelocityVolume3D modelVolume;
    private VelocityVolume3D timeVolume;
    private String timeQuantity;
    private List<TravelTimeGridSet.GridRef> timeGrids = new ArrayList<>();
    private Supplier<List<Station>> stationSource;
    private boolean activated;
    private boolean modelRequested;
    private VelocityModelSpec pendingSpec;
    private VelocityModelSpec currentSpec;
    private int loadSeq;
    private boolean updatingUi;
    private boolean preferTravelTime;
    private File lastDir;

    /** The 1-D profile {depthsKm, vs} behind the current layer-cake volume, or {@code null} for 3-D models. */
    private double[][] profile1D;
    /** The layer cake (Vp and Vs, no dip) of the current 1-D model, or {@code null} (3-D model, unsupported format). */
    private LayerCakeModel cake;
    private final JTextField strikeField = new JTextField("0", 5);
    private final JTextField dipField = new JTextField("0", 5);
    private TravelTimeTableCard tableCard;
    private final java.util.List<Runnable> volumeListeners = new java.util.ArrayList<>();

    private final javax.swing.Timer overlayTimer;
    private final Map<String, List<double[]>> shapeCache = new java.util.concurrent.ConcurrentHashMap<>();
    private int shapeGeneration;

    /**
     * @param mapView the 2D map whose shapefiles, stations and hypocentres are shared ({@code null}: no overlays)
     */
    public VelocityViewerPanel(MapView mapView) {
        this.mapView = mapView;
        area = new JLayeredPane() {
            @Override
            public void doLayout() {
                layoutArea(getWidth(), getHeight());
            }
        };
        buildStrip();
        buildCardHost();
        buildCards();
        area.add(canvas, JLayeredPane.DEFAULT_LAYER);
        area.add(strip, JLayeredPane.PALETTE_LAYER);
        area.add(cardHost, JLayeredPane.POPUP_LAYER);
        cardHost.setVisible(false);
        area.setMinimumSize(new Dimension(200, 150));
        area.setPreferredSize(new Dimension(700, 500));

        root.setBackground(AppPanelStyle.getPanelBg());
        root.add(area, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setControlsEnabled(false);

        colorBar.addChangeListener(this::onColorBarChanged);
        canvas.setColorBarClickListener(this::openColorBarDialog);
        canvas.setCatalogColorBarClickListener(this::openCatalogColorBarDialog);
        canvas.setProbeListener(p -> statusLabel.setText(p == null || volume == null ? " " : describe(p)));
        overlayTimer = new javax.swing.Timer(OVERLAY_DEBOUNCE_MS, e -> refreshOverlays());
        overlayTimer.setRepeats(false);
        if (mapView != null) {
            mapView.addOverlayChangeListener(this::scheduleOverlayRefresh);
        }
        com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().addPlotFilterListener(this::scheduleOverlayRefresh);
        updateRangeInfo();
        refreshOverlays();
    }

    /** The whole 3D map (view, icon strip with pop-up cards, status line). */
    public JComponent getComponent() {
        return root;
    }

    /** The colour bar model shared with the colour map dialog (palette, range). */
    public MapView.ColorBarPanel getColorBar() {
        return colorBar;
    }

    /** Renderer state, for tests. */
    Volume3DRenderer rendererForTest() {
        return renderer;
    }

    /** True once a model has been loaded and displayed. */
    public boolean isModelLoaded() {
        return volume != null;
    }

    /** Stations used to build / filter travel-time tables (Model Station tab). Falls back to the 2D map. */
    public void setStationSource(Supplier<List<Station>> source) {
        this.stationSource = source;
    }

    // ------------------------------------------------------------------ model selection (from the 2D map)

    /** Sets the model to show ({@code null}: none). Loaded immediately so the 2-D map can plot it. */
    public void setModel(VelocityModelSpec spec) {
        pendingSpec = spec;
        modelRequested = true;
        applyPending();
    }

    /** Called when the 3D map tab is shown: loads the requested model. */
    public void ensureLoaded() {
        if (activated) {
            return;
        }
        activated = true;
        applyPending();
    }

    private void applyPending() {
        VelocityModelSpec s = pendingSpec;
        if (s == null) {
            if (modelRequested) {
                clearModel();
            }
            return;
        }
        if (s.equals(currentSpec) && volume != null) {
            return;
        }
        currentSpec = s;
        switch (s.kind) {
            case BUNDLED_3D:
                final String resource = s.ref;
                runLoad(s, () -> {
                    try (InputStream in = VelocityViewerPanel.class.getClassLoader().getResourceAsStream(resource)) {
                        if (in == null) {
                            throw new java.io.FileNotFoundException("Bundled model not found: " + resource);
                        }
                        return VelocityVolume3D.fromCsv(in, s.label);
                    }
                });
                break;
            case FILE_3D:
                final File f = new File(s.ref);
                runLoad(s, () -> VelocityVolume3D.fromCsv(f.toPath()));
                break;
            default:
                load1d(s);
                break;
        }
    }

    private void clearModel() {
        loadSeq++;
        currentSpec = null;
        volume = null;
        modelVolume = null;
        timeVolume = null;
        timeQuantity = null;
        timeGrids = new ArrayList<>();
        profile1D = null;
        cake = null;
        if (tableCard != null) {
            tableCard.refresh();
        }
        renderer.clearVolume();
        canvas.setShapeLines(new ArrayList<>());
        canvas.invalidateScene(false);
        setControlsEnabled(false);
        area.revalidate();
    }

    private interface Loader {
        VelocityVolume3D load() throws Exception;
    }

    private void runLoad(VelocityModelSpec spec, Loader loader) {
        final int seq = ++loadSeq;
        profile1D = null;
        cake = null;
        if (statusLabel != null) {
            statusLabel.setText("Loading " + spec.label + " ...");
        }
        setControlsEnabled(false);
        new SwingWorker<VelocityVolume3D, Void>() {
            @Override
            protected VelocityVolume3D doInBackground() throws Exception {
                return loader.load();
            }

            @Override
            protected void done() {
                if (seq != loadSeq) {
                    return;
                }
                try {
                    setVolume(get());
                } catch (Exception ex) {
                    reportLoadFailure(ex.getCause() != null ? ex.getCause() : ex);
                }
            }
        }.execute();
    }

    /** Loads a 1-D model; the layer-cake volume is built over the current region once the profile is known. */
    private void load1d(VelocityModelSpec spec) {
        final int seq = ++loadSeq;
        if (statusLabel != null) {
            statusLabel.setText("Loading " + spec.label + " ...");
        }
        setControlsEnabled(false);
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                double[][] prof = Raytrace1D.load(spec.ref, false).sVelocityProfile();
                LayerCakeModel lc = null;
                try {
                    lc = LayerCakeModel.load(spec.ref); // Vp and Vs; not available for every format (e.g. .aob)
                } catch (Exception e) {
                    logger.log(Level.FINE, "No P/S layer cake for " + spec.ref, e);
                }
                return new Object[] {prof, lc};
            }

            @Override
            protected void done() {
                if (seq != loadSeq) {
                    return;
                }
                try {
                    Object[] res = get();
                    double[][] prof = (double[][]) res[0];
                    cake = (LayerCakeModel) res[1];
                    if (prof == null || prof[0].length < 2) {
                        throw new IllegalStateException("The model has no solid layers (no S velocity).");
                    }
                    profile1D = prof;
                    applyGeometryFromShared();
                } catch (Exception ex) {
                    reportLoadFailure(ex.getCause() != null ? ex.getCause() : ex);
                }
            }
        }.execute();
    }

    private void reportLoadFailure(Throwable c) {
        logger.log(Level.WARNING, "Velocity model load failed", c);
        JOptionPane.showMessageDialog(root, "Could not load the velocity model:\n" + c.getMessage(),
            "Velocity model", JOptionPane.ERROR_MESSAGE);
        setControlsEnabled(volume != null);
    }

    // ------------------------------------------------------------------ layer-cake region

    private double[] readRegion() {
        double[] r = new double[8];
        for (int i = 0; i < 8; i++) {
            r[i] = Double.parseDouble(regionFields[i].getText().trim());
        }
        return r;
    }

    private void setRegionFields(double lonMin, double lonMax, double latMin, double latMax,
                                 double minDepth, double maxDepth, double spacingH, double spacingZ) {
        double[] v = {lonMin, lonMax, latMin, latMax, minDepth, maxDepth, spacingH, spacingZ};
        for (int i = 0; i < 8; i++) {
            regionFields[i].setText(format(v[i]));
        }
    }

    /** Builds the layer-cake volume of the current 1-D profile over the region fields. */
    private void applyRegion() {
        if (profile1D == null || currentSpec == null) {
            return;
        }
        try {
            double[] r = readRegion();
            if (!(r[1] > r[0] && r[3] > r[2] && r[5] > r[4] && r[6] > 0 && r[7] > 0)) {
                throw new IllegalArgumentException("Region must satisfy min < max, max depth > min depth, spacing > 0.");
            }
            publishGeometryFromFields();
            VelocityVolume3D v;
            LayerCakeModel lc = currentCake();
            if (lc != null) {
                int nd = (int) Math.max(41, Math.min(161, Math.round((r[5] - r[4]) / 2.0) + 1));
                v = LayerCakeVolumes.build(lc, currentSpec.label + " (layer cake)", r[0], r[1], r[2], r[3], r[4], r[5],
                    41, nd);
            } else {
                v = VelocityVolume3D.fromProfile1D(currentSpec.label + " (layer cake)", profile1D[0],
                    profile1D[1], r[0], r[1], r[2], r[3], r[5]);
            }
            setVolume(v);
        } catch (RuntimeException ex) {
            reportLoadFailure(ex);
        }
    }

    private void publishGeometryFromFields() {
        try {
            double[] r = readRegion();
            com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry g =
                new com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry();
            g.lonMin = r[0];
            g.lonMax = r[1];
            g.latMin = r[2];
            g.latMax = r[3];
            g.minDepthKm = r[4];
            g.maxDepthKm = r[5];
            g.spacingKm = r[6];
            g.spacingDepthKm = r[7];
            com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry cur =
                com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getGeometry();
            g.strikeDeg = cur.strikeDeg;
            g.dipDeg = cur.dipDeg;
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setGeometry(g);
        } catch (RuntimeException ignored) {
            // invalid fields: leave the shared geometry as it is
        }
    }

    /** The layer cake with the Model Range strike / dip, or {@code null}. */
    private LayerCakeModel currentCake() {
        if (cake == null) {
            return null;
        }
        com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry g =
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getGeometry();
        return cake.withStrikeDip(g.strikeDeg, g.dipDeg);
    }

    // ------------------------------------------------------------------ travel-time tables (Table card)

    private final TravelTimeTableCard.Host tableHost = new TravelTimeTableCard.Host() {
        @Override
        public LayerCakeModel layerCake() {
            try {
                return currentCake();
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        @Override
        public com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry geometry() {
            com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry g =
                com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getGeometry();
            return g != null && g.hasRegion() && g.spacingKm > 0 ? g : null;
        }

        @Override
        public List<TravelTimeGridBuilder.StationPoint> stations() {
            List<TravelTimeGridBuilder.StationPoint> out = new ArrayList<>();
            List<Station> src = stationSource != null ? stationSource.get()
                : (mapView != null ? mapView.getDisplayedStations() : null);
            if (src != null) {
                for (Station st : src) {
                    if (st != null && Double.isFinite(st.getLon()) && Double.isFinite(st.getLat())) {
                        double depKm = Double.isFinite(st.getDep()) ? -st.getDep() / 1000.0 : 0.0;
                        out.add(new TravelTimeGridBuilder.StationPoint(st.getCode(), st.getLat(), st.getLon(), depKm));
                    }
                }
            }
            return out;
        }

        @Override
        public void showVolume(VelocityVolume3D v) {
            addTimeQuantity(v, v.name());
        }

        @Override
        public void setTravelTimeGrids(List<TravelTimeGridSet.GridRef> grids) {
            applyTravelTimeGrids(grids);
        }

        @Override
        public void setStatus(String text) {
            statusLabel.setText(text);
        }

        @Override
        public void setTravelTimeSource(boolean travelTime) {
            VelocityViewerPanel.this.setTravelTimeSource(travelTime);
        }

        @Override
        public void applyRangeFromShared() {
            applyGeometryFromShared();
        }
    };

    // ------------------------------------------------------------------ overlays shared with the 2D map

    private void scheduleOverlayRefresh() {
        overlayTimer.restart();
    }

    /** Pulls stations, hypocentres and shapefiles from the 2D map into the canvas. */
    void refreshOverlays() {
        if (mapView == null) {
            return;
        }
        List<Volume3DCanvas.StationPoint> sts = new ArrayList<>();
        for (Station st : mapView.getDisplayedStations()) {
            if (Double.isFinite(st.getLon()) && Double.isFinite(st.getLat())) {
                double depKm = Double.isFinite(st.getDep()) ? -st.getDep() / 1000.0 : 0.0;
                com.treloc.xtreloc.app.gui.model.MapLayerStyle style = mapView.styleForStation(st);
                Color col = style != null ? style.colorWithOpacity() : Color.BLACK;
                sts.add(new Volume3DCanvas.StationPoint(
                    st.getCode(), st.getLon(), st.getLat(), depKm, col, mapView.isStationFilled(st)));
            }
        }
        canvas.setStations(sts);
        canvas.setHypocenterSize(mapView.getSymbolSize());
        List<Volume3DCanvas.PointGroup> groups = new ArrayList<>();
        for (MapView.OverlayPoints g : mapView.getDisplayedHypocenters()) {
            groups.add(new Volume3DCanvas.PointGroup(g.color, g.points, g.pointColors, g.size));
        }
        canvas.setHypocenterGroups(groups);
        canvas.setCatalogColorBar(mapView.hasCatalogColorMap() ? mapView.getColorBar() : null);
        List<Volume3DCanvas.LegendItem> legend = new ArrayList<>();
        if (!sts.isEmpty()) {
            legend.add(new Volume3DCanvas.LegendItem("Stations", new Color(20, 20, 20)));
        }
        for (MapView.OverlayPoints g : mapView.getDisplayedHypocenters()) {
            if (g != null && g.name != null) {
                legend.add(new Volume3DCanvas.LegendItem(g.name, g.color != null ? g.color : Color.GRAY));
            }
        }
        canvas.setLegendItems(legend);
        refreshShapefiles();
    }

    private String regionKey(MapView.ShapefileRef ref) {
        return ref.title + "|" + volume.minLon() + "," + volume.maxLon() + "," + volume.minLat() + "," + volume.maxLat();
    }

    private void refreshShapefiles() {
        if (mapView == null || volume == null) {
            return;
        }
        final int gen = ++shapeGeneration;
        final double lo0 = volume.minLon();
        final double lo1 = volume.maxLon();
        final double la0 = volume.minLat();
        final double la1 = volume.maxLat();
        List<MapView.ShapefileRef> visible = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (MapView.ShapefileRef ref : mapView.getShapefileLayers()) {
            if (ref.visible) {
                visible.add(ref);
                keys.add(regionKey(ref));
            }
        }
        boolean allCached = true;
        for (String k : keys) {
            if (!shapeCache.containsKey(k)) {
                allCached = false;
                break;
            }
        }
        if (allCached) {
            applyShapes(keys);
            return;
        }
        new SwingWorker<Map<String, List<double[]>>, Void>() {
            @Override
            protected Map<String, List<double[]>> doInBackground() {
                Map<String, List<double[]>> res = new HashMap<>();
                for (int i = 0; i < visible.size(); i++) {
                    if (!shapeCache.containsKey(keys.get(i))) {
                        List<double[]> lines;
                        try {
                            lines = ShapefileOverlayExtractor.extract(visible.get(i).source, lo0, lo1, la0, la1);
                        } catch (java.io.IOException ex) {
                            logger.log(Level.WARNING, "Could not read shapefile " + visible.get(i).title, ex);
                            lines = new ArrayList<>();
                        }
                        res.put(keys.get(i), lines);
                    }
                }
                return res;
            }

            @Override
            protected void done() {
                try {
                    shapeCache.putAll(get());
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Shapefile extraction failed", ex);
                }
                if (gen == shapeGeneration) {
                    applyShapes(keys);
                }
            }
        }.execute();
    }

    private void applyShapes(List<String> keys) {
        List<double[]> all = new ArrayList<>();
        for (String k : keys) {
            List<double[]> l = shapeCache.get(k);
            if (l != null) {
                all.addAll(l);
            }
        }
        canvas.setShapeLines(all);
    }

    // ------------------------------------------------------------------ colour bar

    private void openColorBarDialog() {
        if (volume == null || VelocityVolume3D.CH_LAYER.equals(renderer.channel)) {
            return;
        }
        colorBar.setDialogParent(SwingUtilities.getWindowAncestor(canvas));
        colorBar.showSettingsDialog();
    }

    private void openCatalogColorBarDialog() {
        if (mapView == null || mapView.getColorBar() == null || !mapView.hasCatalogColorMap()) {
            return;
        }
        mapView.getColorBar().setDialogParent(SwingUtilities.getWindowAncestor(canvas));
        mapView.getColorBar().showSettingsDialog();
        refreshOverlays();
    }

    private void onColorBarChanged() {
        renderer.vmin = colorBar.getMinValue();
        renderer.vmax = colorBar.getMaxValue();
        renderer.setLut(colorBar.getColorArray());
        updateRangeInfo();
        canvas.invalidateScene(false);
    }

    private void setColorRange(double lo, double hi) {
        colorBar.setRange(lo, hi, Volume3DCanvas.channelTitle(renderer.channel));
    }

    private void updateRangeInfo() {
        if (rangeInfoLabel == null) {
            return;
        }
        rangeInfoLabel.setText(String.format(Locale.ROOT, "<html>%s<br>%s .. %s &nbsp; (%s)</html>",
            Volume3DCanvas.channelTitle(renderer.channel), format(colorBar.getMinValue()),
            format(colorBar.getMaxValue()), colorBar.getPalette()));
    }

    // ------------------------------------------------------------------ layout: strip, cards

    private void layoutArea(int w, int h) {
        int cw = Math.max(0, w - STRIP_WIDTH);
        canvas.setBounds(0, 0, cw, h);
        strip.setBounds(cw, 0, STRIP_WIDTH, h);
        if (cardHost.isVisible()) {
            Dimension p = cardHost.getPreferredSize();
            int width = Math.min(Math.max(p.width, CARD_WIDTH), Math.max(120, cw - 12));
            int height = Math.min(p.height, Math.max(80, h - 12));
            cardHost.setBounds(cw - width - 6, 6, width, height);
        }
    }

    private void buildStrip() {
        strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
        strip.setBackground(AppPanelStyle.getPanelBg());
        strip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, AppPanelStyle.getBorderColor()));
        strip.add(Box.createVerticalStrut(6));
        Object[][] items = {
            {"View", Map3DIcons.Kind.VIEW, "View presets and slice positions"},
            {"Background", Map3DIcons.Kind.PARAMETER, "Quantity, layers and vertical exaggeration"},
            {"Range", Map3DIcons.Kind.RANGE, "Color range and palette, opacity"},
            {"Style", Map3DIcons.Kind.TABLE, "Symbol, line style, colour, size and opacity"},
            {"Tools", Map3DIcons.Kind.TOOLS, "Legend, colour bar, box, labels, grid"},
            {"Export", Map3DIcons.Kind.EXPORT, "Export to image"},
        };
        for (Object[] it : items) {
            final String name = (String) it[0];
            JToggleButton b = new JToggleButton(new Map3DIcons((Map3DIcons.Kind) it[1], 24,
                AppPanelStyle.getContentTextColor()));
            b.setToolTipText((String) it[2]);
            b.setFocusPainted(false);
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMargin(new java.awt.Insets(4, 4, 4, 4));
            Dimension d = new Dimension(STRIP_WIDTH - 6, 38);
            b.setPreferredSize(d);
            b.setMaximumSize(d);
            b.addActionListener(e -> showCard(name));
            stripButtons.put(name, b);
            strip.add(b);
            strip.add(Box.createVerticalStrut(4));
        }
        strip.add(Box.createVerticalGlue());
        JToggleButton refresh = new JToggleButton(new Map3DIcons(Map3DIcons.Kind.REFRESH, 24,
            AppPanelStyle.getContentTextColor()));
        refresh.setToolTipText("Reload hypocentres, stations and shapefiles from the 2D map");
        refresh.setFocusPainted(false);
        refresh.setAlignmentX(Component.CENTER_ALIGNMENT);
        refresh.setMargin(new java.awt.Insets(4, 4, 4, 4));
        Dimension rd = new Dimension(STRIP_WIDTH - 6, 38);
        refresh.setPreferredSize(rd);
        refresh.setMaximumSize(rd);
        refresh.addActionListener(e -> {
            refresh.setSelected(false);
            refreshView();
        });
        strip.add(refresh);
        strip.add(Box.createVerticalStrut(6));
    }

    /** Reloads overlays from the 2D map and redraws the 3D scene. */
    public void refreshView() {
        if (mapView != null) {
            mapView.reloadMap();
        }
        refreshOverlays();
        canvas.invalidateScene(false);
        canvas.repaint();
    }

    private void buildCardHost() {
        cardHost.setBackground(AppPanelStyle.getPanelBg());
        cardHost.setOpaque(true);
        cardHost.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppPanelStyle.getBorderColor(), 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        cardTitle.setFont(AppPanelStyle.getSectionTitleFont());
        cardTitle.setForeground(AppPanelStyle.getTitleColor());
        cardTitle.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));
        JButton close = new JButton("\u00D7");
        close.setToolTipText("Close");
        close.setFocusPainted(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.addActionListener(e -> showCard(null));
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppPanelStyle.getPanelBg());
        header.add(cardTitle, BorderLayout.CENTER);
        header.add(close, BorderLayout.EAST);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppPanelStyle.getBorderColor()));
        cardScroll.setBorder(null);
        cardScroll.getViewport().setBackground(AppPanelStyle.getPanelBg());
        cardScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        cardScroll.getVerticalScrollBar().setUnitIncrement(16);
        cardHost.add(header, BorderLayout.NORTH);
        cardHost.add(cardScroll, BorderLayout.CENTER);
    }

    /** Opens the card {@code name}; closes it when it is already open (or {@code null}). */
    private void showCard(String name) {
        if (name == null || name.equals(openCard)) {
            openCard = null;
            cardHost.setVisible(false);
        } else {
            openCard = name;
            cardTitle.setText(name);
            if ("Style".equals(name) && stylePanel != null) {
                stylePanel.reload();
            }
            cardScroll.setViewportView(cards.get(name));
            cardHost.setVisible(true);
        }
        for (Map.Entry<String, JToggleButton> e : stripButtons.entrySet()) {
            e.getValue().setSelected(e.getKey().equals(openCard));
        }
        area.revalidate();
        area.repaint();
    }

    /** Names of the pop-up cards, in strip order (for tests). */
    List<String> cardNames() {
        return new ArrayList<>(stripButtons.keySet());
    }

    JComponent cardBodyForTest(String name) {
        return cards.get(name);
    }

    /** Sets strike / dip and rebuilds the layer cake (for tests). */
    void applyStrikeDipForTest(String strike, String dip) {
        strikeField.setText(strike);
        dipField.setText(dip);
        com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry g =
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getGeometry();
        g.strikeDeg = Double.parseDouble(strike.trim());
        g.dipDeg = Double.parseDouble(dip.trim());
        com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setGeometry(g);
        applyRegion();
    }

    VelocityVolume3D volumeForTest() {
        return volume;
    }

    /** Velocity / travel-time model volume (may differ from the displayed Quantity). */
    public VelocityVolume3D modelVolume() {
        return modelVolume;
    }

    /** Travel-time field added as a Quantity, or {@code null}. */
    public VelocityVolume3D timeVolume() {
        return timeVolume;
    }

    /** Label of {@link #timeVolume()} in the Quantity combo, or {@code null}. */
    public String timeQuantityName() {
        return timeQuantity;
    }

    /** Quantity labels for every complete grid in the loaded travel-time table. */
    public List<String> timeQuantityNames() {
        List<String> out = new ArrayList<>();
        for (TravelTimeGridSet.GridRef g : timeGrids) {
            out.add(TravelTimeTableCard.quantityLabel(g));
        }
        return out;
    }

    /** Loads or shows the travel-time Quantity chosen on the 2-D or 3-D map. */
    public void selectTimeQuantity(String name) {
        if (name == null) {
            return;
        }
        if (name.equals(timeQuantity) && timeVolume != null) {
            updatingUi = true;
            try {
                fillQuantityCombo(name);
            } finally {
                updatingUi = false;
            }
            displayVolume(timeVolume, timeVolume.channelNames().get(0));
            return;
        }
        for (TravelTimeGridSet.GridRef g : timeGrids) {
            if (name.equals(TravelTimeTableCard.quantityLabel(g))) {
                loadTimeGrid(g);
                return;
            }
        }
    }

    public void addVolumeListener(Runnable listener) {
        if (listener != null) {
            volumeListeners.add(listener);
        }
    }

    TravelTimeTableCard tableCardForTest() {
        return tableCard;
    }

    /** Shared host for the Model tab's Table card (same region / stations / show-volume as this view). */
    public TravelTimeTableCard.Host tableHost() {
        return tableHost;
    }

    /**
     * Model tab Source: travel-time table vs velocity. The visualized volume and Range clip follow the
     * active source (XOR — either is enough).
     */
    public void setTravelTimeSource(boolean travelTime) {
        preferTravelTime = travelTime;
        if (travelTime) {
            if (timeVolume != null) {
                displayVolume(timeVolume, timeVolume.channelNames().get(0));
            } else if (!timeGrids.isEmpty()) {
                loadTimeGrid(timeGrids.get(0));
            } else {
                hideDisplayedVolume();
            }
            fireVolumeListeners();
            return;
        }
        com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry g =
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getGeometry();
        boolean oneDNeedsRange = currentSpec != null && !currentSpec.is3D();
        if (modelVolume != null && !modelVolume.channelNames().isEmpty()
            && (!oneDNeedsRange || (g != null && g.hasRegion()))) {
            String ch = modelVolume.hasChannel(VelocityVolume3D.CH_DVS_MEAN)
                ? VelocityVolume3D.CH_DVS_MEAN : modelVolume.channelNames().get(0);
            updatingUi = true;
            try {
                fillQuantityCombo(ch);
            } finally {
                updatingUi = false;
            }
            displayVolume(modelVolume, ch);
        } else {
            hideDisplayedVolume();
        }
        fireVolumeListeners();
    }

    private void hideDisplayedVolume() {
        volume = null;
        renderer.clearVolume();
        canvas.invalidateScene(false);
    }

    public boolean isTravelTimeSource() {
        return preferTravelTime;
    }

    /** Rebuilds the layer cake from {@link com.treloc.xtreloc.app.gui.util.SharedFileManager} geometry. */
    public void applyGeometryFromShared() {
        com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry g =
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getGeometry();
        setRegionFields(g.lonMin, g.lonMax, g.latMin, g.latMax, g.minDepthKm, g.maxDepthKm,
            g.spacingHoriz(), g.spacingDepth());
        strikeField.setText(format(g.strikeDeg));
        dipField.setText(format(g.dipDeg));
        if (!g.hasRegion()) {
            if (profile1D != null && !preferTravelTime) {
                modelVolume = null;
                hideDisplayedVolume();
                fireVolumeListeners();
            }
            if (tableCard != null) {
                tableCard.refresh();
            }
            return;
        }
        if (profile1D != null && !preferTravelTime) {
            applyRegion();
        }
        if (tableCard != null) {
            tableCard.refresh();
        }
    }

    /** Opens a card programmatically (for tests). */
    void openCardForTest(String name) {
        showCard(name);
    }

    boolean isCardOpenForTest() {
        return cardHost.isVisible();
    }

    private JPanel body() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(AppPanelStyle.getPanelBg());
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        p.setPreferredSize(null);
        return p;
    }

    private JPanel row() {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        r.setBackground(AppPanelStyle.getPanelBg());
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppPanelStyle.getLabelFont());
        l.setForeground(AppPanelStyle.getContentTextColor());
        return l;
    }

    private JLabel hint(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppPanelStyle.getLabelFont());
        l.setForeground(AppPanelStyle.getMutedTextColor());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
        return l;
    }

    private JCheckBox check(String text, boolean selected) {
        JCheckBox cb = new JCheckBox(text, selected);
        cb.setBackground(AppPanelStyle.getPanelBg());
        cb.setFont(AppPanelStyle.getLabelFont());
        return cb;
    }

    private JButton cardButton(String text) {
        JButton b = new JButton(text);
        b.setMargin(new java.awt.Insets(2, 8, 2, 8));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
        return b;
    }

    private void buildCards() {
        initRegionFields();
        cards.put("View", viewCard());
        cards.put("Background", parameterCard());
        cards.put("Range", rangeCard());
        stylePanel = new MapLayerStylePanel(mapView);
        cards.put("Style", stylePanel);
        cards.put("Tools", toolsCard());
        tableCard = new TravelTimeTableCard(tableHost);
        cards.put("Export", exportCard());
    }

    /** Hidden buffers synced from Model → Range; not shown on the 3D icon strip. */
    private void initRegionFields() {
        for (int i = 0; i < regionFields.length; i++) {
            regionFields[i] = new JTextField(6);
        }
        AppPanelStyle.styleTextField(strikeField);
        AppPanelStyle.styleTextField(dipField);
    }

    private JPanel parameterCard() {
        JPanel p = body();
        channelCombo = new JComboBox<>();
        AppPanelStyle.styleComboBox(channelCombo);
        channelCombo.addActionListener(e -> {
            if (!updatingUi) {
                onChannelChanged();
            }
        });
        JPanel r1 = row();
        r1.add(label("Quantity"));
        r1.add(channelCombo);
        p.add(r1);

        vexagSpinner = new JSpinner(new SpinnerNumberModel(renderer.verticalExaggeration, 0.2, 10.0, 0.25));
        vexagSpinner.addChangeListener(e -> {
            renderer.verticalExaggeration = ((Number) vexagSpinner.getValue()).doubleValue();
            canvas.invalidateScene(false);
        });
        JPanel rVe = row();
        rVe.add(label("Vertical exaggeration"));
        rVe.add(vexagSpinner);
        p.add(rVe);

        JPanel r6 = row();
        r6.add(label("Layers"));
        for (int i = 0; i < layerChecks.length; i++) {
            JCheckBox cb = check(String.valueOf(i + 1), true);
            cb.setToolTipText(ColorMaps.LAYER_NAMES[i]);
            cb.addActionListener(e -> applyLayerMask());
            layerChecks[i] = cb;
            r6.add(cb);
        }
        p.add(r6);
        p.add(hint("Layers (layer channel only): 1 upper crust, 2 lower crust, 3 mantle, 4 slab"));
        return p;
    }

    private JPanel rangeCard() {
        JPanel p = body();
        rangeInfoLabel = new JLabel(" ");
        rangeInfoLabel.setFont(AppPanelStyle.getLabelFont());
        rangeInfoLabel.setForeground(AppPanelStyle.getContentTextColor());
        rangeInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        rangeInfoLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 6, 6));
        p.add(rangeInfoLabel);

        paletteButton = new JButton("Palette and range...");
        paletteButton.setToolTipText("Same dialog as the 2D map color bar (also opened by clicking the color bar)");
        paletteButton.addActionListener(e -> openColorBarDialog());
        autoButton = new JButton("Auto range");
        autoButton.setToolTipText("2-98 percentile (symmetric about 0 for anomalies; full range for layer cakes)");
        autoButton.addActionListener(e -> autoRange());
        JPanel r1 = row();
        r1.add(paletteButton);
        r1.add(autoButton);
        p.add(r1);

        opacitySlider = new JSlider(10, 100, 100);
        opacitySlider.setBackground(AppPanelStyle.getPanelBg());
        opacitySlider.addChangeListener(e -> {
            renderer.alpha = opacitySlider.getValue() / 100.0;
            canvas.invalidateScene(opacitySlider.getValueIsAdjusting());
        });
        JPanel r2 = row();
        r2.add(label("Opacity"));
        r2.add(opacitySlider);
        p.add(r2);
        p.add(hint("You can also click the color bar in the view."));
        return p;
    }

    private JPanel viewCard() {
        JPanel p = body();
        String[][] presets = {{"Top", "top"}, {"From S", "south"}, {"From W", "west"}};
        for (String[] pr : presets) {
            JButton b = cardButton(pr[0]);
            b.addActionListener(e -> canvas.setViewPreset(pr[1]));
            p.add(b);
            p.add(Box.createVerticalStrut(4));
        }
        JButton reset = cardButton("Reset");
        reset.addActionListener(e -> canvas.resetView());
        p.add(reset);
        p.add(Box.createVerticalStrut(8));

        String[] names = {"Longitude", "Latitude", "Depth"};
        for (int a = 0; a < 3; a++) {
            final int axis = a;
            JCheckBox cb = check(names[a], true);
            cb.addActionListener(e -> {
                applySliceVisibility();
                canvas.invalidateScene(false);
            });
            sliceChecks[a] = cb;
            JSlider sl = new JSlider(0, SLIDER_STEPS, SLIDER_STEPS / 2);
            sl.setBackground(AppPanelStyle.getPanelBg());
            sl.addChangeListener(e -> {
                if (!updatingUi) {
                    onSliceSlider(axis);
                    canvas.invalidateScene(sl.getValueIsAdjusting());
                }
            });
            sliceSliders[a] = sl;
            JLabel val = label(" ");
            val.setPreferredSize(new Dimension(70, 18));
            sliceLabels[a] = val;
            JPanel top = new JPanel(new BorderLayout(6, 0));
            top.setBackground(AppPanelStyle.getPanelBg());
            top.setAlignmentX(Component.LEFT_ALIGNMENT);
            top.add(cb, BorderLayout.WEST);
            top.add(val, BorderLayout.EAST);
            p.add(top);
            sl.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(sl);
            p.add(Box.createVerticalStrut(4));
        }
        p.add(hint("Shapefiles, stations and hypocenters follow the 2D map (Visualize check boxes)."));
        return p;
    }

    private JPanel toolsCard() {
        JPanel p = body();
        JCheckBox codes = check("Station codes", true);
        canvas.setShowStationLabels(true);
        codes.addActionListener(e -> canvas.setShowStationLabels(codes.isSelected()));
        JCheckBox box = check("Bounding box", true);
        box.addActionListener(e -> canvas.setShowBox(box.isSelected()));
        JCheckBox axes = check("Axis labels", true);
        axes.addActionListener(e -> canvas.setShowAxes(axes.isSelected()));
        JCheckBox gridLines = check("Grid lines", true);
        gridLines.addActionListener(e -> canvas.setShowGrid(gridLines.isSelected()));
        JCheckBox legend = check("Legend", true);
        JCheckBox bgBar = check("Background colour bar", true);
        JCheckBox hypoBar = check("Hypocenter colour bar", true);
        JCheckBox scale = check("Scale bar", true);
        JCheckBox nesw = check("N / S / E / W", true);
        legend.addActionListener(e -> canvas.setShowLegend(legend.isSelected()));
        bgBar.addActionListener(e -> canvas.setShowColorBar(bgBar.isSelected()));
        hypoBar.addActionListener(e -> canvas.setShowCatalogColorBar(hypoBar.isSelected()));
        scale.addActionListener(e -> canvas.setShowScale(scale.isSelected()));
        nesw.addActionListener(e -> canvas.setShowCompass(nesw.isSelected()));
        for (JCheckBox cb : new JCheckBox[] {codes, box, axes, gridLines, legend, bgBar, hypoBar, scale, nesw}) {
            p.add(cb);
        }
        return p;
    }

    private JPanel exportCard() {
        JPanel p = body();
        saveButton = new JButton("Save as PNG...");
        saveButton.addActionListener(e -> saveImage());
        copyButton = new JButton("Copy to clipboard");
        copyButton.addActionListener(e -> copyImage());
        JPanel r1 = row();
        r1.add(saveButton);
        p.add(r1);
        JPanel r2 = row();
        r2.add(copyButton);
        p.add(r2);
        p.add(hint("Exports the current view at full resolution, including color bar and overlays."));
        return p;
    }

    private JPanel buildStatusBar() {
        statusLabel = new JLabel(" ");
        statusLabel.setFont(AppPanelStyle.getLabelFont());
        statusLabel.setForeground(AppPanelStyle.getContentTextColor());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        JLabel hint = new JLabel("Drag: rotate   Right/Shift-drag: pan   Wheel: zoom   Double-click: reset");
        hint.setFont(AppPanelStyle.getLabelFont());
        hint.setForeground(AppPanelStyle.getMutedTextColor());
        hint.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        JPanel south = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(south);
        south.add(statusLabel, BorderLayout.CENTER);
        south.add(hint, BorderLayout.EAST);
        return south;
    }

    private void setControlsEnabled(boolean on) {
        for (Component c : new Component[] {channelCombo, opacitySlider, vexagSpinner, paletteButton,
            autoButton, saveButton, copyButton}) {
            if (c != null) {
                c.setEnabled(on);
            }
        }
        for (JTextField f : regionFields) {
            if (f != null) {
                f.setEnabled(on);
            }
        }
        strikeField.setEnabled(on);
        dipField.setEnabled(on);
        for (int i = 0; i < 3; i++) {
            if (sliceChecks[i] != null) {
                sliceChecks[i].setEnabled(on);
                sliceSliders[i].setEnabled(on);
            }
        }
        for (JCheckBox cb : layerChecks) {
            if (cb != null) {
                cb.setEnabled(on && volume != null && volume.hasChannel(VelocityVolume3D.CH_LAYER));
            }
        }
    }

    // ------------------------------------------------------------------ volume

    private void setVolume(VelocityVolume3D v) {
        boolean keepView = this.volume != null || this.modelVolume != null;
        boolean showingTime = preferTravelTime && timeVolume != null && this.volume == timeVolume;
        VelocityVolume3D keepTime = timeVolume;
        String keepQ = timeQuantity;
        this.volume = v;
        this.modelVolume = v;
        this.timeVolume = keepTime;
        this.timeQuantity = keepQ;
        updatingUi = true;
        try {
            renderer.setVolume(v, null, keepView);
            fillQuantityCombo(showingTime && keepQ != null ? keepQ : null);
            String preferred = v.hasChannel(VelocityVolume3D.CH_DVS_MEAN)
                ? VelocityVolume3D.CH_DVS_MEAN : v.channelNames().get(0);
            Object sel = channelCombo.getSelectedItem();
            if (sel != null && v.hasChannel(sel.toString())) {
                preferred = sel.toString();
            }
            renderer.channel = preferred;
            renderer.layerVisible = v.hasChannel(VelocityVolume3D.CH_LAYER) ? currentLayerMask() : null;
            autoRange();
            for (int a = 0; a < 3; a++) {
                sliceSliders[a].setValue(sliderFor(a));
            }
            updateSliceLabels();
        } finally {
            updatingUi = false;
        }
        if (showingTime && keepTime != null) {
            displayVolume(keepTime, keepTime.channelNames().get(0));
        }
        setControlsEnabled(true);
        if (tableCard != null) {
            tableCard.refresh();
        }
        applySliceVisibility();
        if (!keepView) {
            canvas.resetView();
        } else {
            canvas.invalidateScene(false);
        }
        refreshShapefiles();
        area.revalidate();
        fireVolumeListeners();
    }

    private void fireVolumeListeners() {
        for (Runnable listener : volumeListeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // keep the view
            }
        }
    }

    private boolean[] currentLayerMask() {
        boolean[] m = new boolean[layerChecks.length];
        for (int i = 0; i < m.length; i++) {
            m[i] = layerChecks[i].isSelected();
        }
        return m;
    }

    private void applyLayerMask() {
        if (volume == null || !volume.hasChannel(VelocityVolume3D.CH_LAYER)) {
            return;
        }
        renderer.layerVisible = currentLayerMask();
        canvas.invalidateScene(false);
    }

    private void applySliceVisibility() {
        renderer.showLonSlice = sliceChecks[0].isSelected();
        renderer.showLatSlice = sliceChecks[1].isSelected();
        renderer.showDepthSlice = sliceChecks[2].isSelected();
    }

    private double axisMin(int a) {
        return a == 0 ? volume.minLon() : (a == 1 ? volume.minLat() : volume.minDepth());
    }

    private double axisMax(int a) {
        return a == 0 ? volume.maxLon() : (a == 1 ? volume.maxLat() : volume.maxDepth());
    }

    private double sliceValue(int a) {
        return a == 0 ? renderer.lonSlice : (a == 1 ? renderer.latSlice : renderer.depthSlice);
    }

    private int sliderFor(int a) {
        double f = (sliceValue(a) - axisMin(a)) / (axisMax(a) - axisMin(a));
        return (int) Math.round(Math.max(0.0, Math.min(1.0, f)) * SLIDER_STEPS);
    }

    private void onSliceSlider(int a) {
        if (volume == null) {
            return;
        }
        double v = axisMin(a) + sliceSliders[a].getValue() / (double) SLIDER_STEPS * (axisMax(a) - axisMin(a));
        if (a == 0) {
            renderer.lonSlice = v;
        } else if (a == 1) {
            renderer.latSlice = v;
        } else {
            renderer.depthSlice = v;
        }
        updateSliceLabels();
    }

    private void updateSliceLabels() {
        if (volume == null) {
            return;
        }
        sliceLabels[0].setText(String.format(Locale.ROOT, "%.3f\u00B0E", renderer.lonSlice));
        sliceLabels[1].setText(String.format(Locale.ROOT, "%.3f\u00B0N", renderer.latSlice));
        sliceLabels[2].setText(String.format(Locale.ROOT, "%.1f km", renderer.depthSlice));
    }

    private void fillQuantityCombo(String select) {
        if (channelCombo == null) {
            return;
        }
        channelCombo.removeAllItems();
        if (modelVolume != null) {
            for (String c : modelVolume.channelNames()) {
                channelCombo.addItem(c);
            }
        }
        for (String n : timeQuantityNames()) {
            channelCombo.addItem(n);
        }
        if (select != null) {
            channelCombo.setSelectedItem(select);
        } else if (modelVolume != null && !modelVolume.channelNames().isEmpty()) {
            String preferred = modelVolume.hasChannel(VelocityVolume3D.CH_DVS_MEAN)
                ? VelocityVolume3D.CH_DVS_MEAN : modelVolume.channelNames().get(0);
            channelCombo.setSelectedItem(preferred);
        }
    }

    private void applyTravelTimeGrids(List<TravelTimeGridSet.GridRef> grids) {
        timeGrids = grids == null ? new ArrayList<>() : new ArrayList<>(grids);
        if (timeGrids.isEmpty()) {
            timeVolume = null;
            timeQuantity = null;
        }
        updatingUi = true;
        try {
            fillQuantityCombo(timeQuantity);
        } finally {
            updatingUi = false;
        }
        fireVolumeListeners();
        if (preferTravelTime && !timeGrids.isEmpty()) {
            boolean have = timeQuantity != null && timeQuantityNames().contains(timeQuantity);
            if (!have) {
                loadTimeGrid(timeGrids.get(0));
            }
        }
    }

    private void loadTimeGrid(TravelTimeGridSet.GridRef g) {
        if (g == null || g.header == null) {
            return;
        }
        String label = TravelTimeTableCard.quantityLabel(g);
        statusLabel.setText("Reading " + g.header.getFileName() + " ...");
        new SwingWorker<VelocityVolume3D, Void>() {
            @Override
            protected VelocityVolume3D doInBackground() throws Exception {
                return StationTimeGrid.read(g.header).toVolume(label);
            }

            @Override
            protected void done() {
                try {
                    addTimeQuantity(get(), label);
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    logger.log(java.util.logging.Level.WARNING, "Travel-time grid load failed", c);
                }
            }
        }.execute();
    }

    private void addTimeQuantity(VelocityVolume3D v, String label) {
        if (v == null || label == null) {
            return;
        }
        timeVolume = v;
        timeQuantity = label;
        if (modelVolume == null) {
            setVolume(v);
            return;
        }
        updatingUi = true;
        try {
            fillQuantityCombo(label);
        } finally {
            updatingUi = false;
        }
        displayVolume(v, v.channelNames().get(0));
    }

    private void displayVolume(VelocityVolume3D v, String channel) {
        boolean same = this.volume == v;
        this.volume = v;
        if (!same) {
            renderer.setVolume(v, channel, true);
        }
        renderer.channel = channel;
        autoRange();
        canvas.invalidateScene(false);
        updateRangeInfo();
        fireVolumeListeners();
    }

    private void onChannelChanged() {
        String ch = channelCombo.getSelectedItem() == null ? null : channelCombo.getSelectedItem().toString();
        if (ch == null) {
            return;
        }
        if (timeQuantityNames().contains(ch)) {
            if (ch.equals(timeQuantity) && timeVolume != null) {
                displayVolume(timeVolume, timeVolume.channelNames().get(0));
                return;
            }
            for (TravelTimeGridSet.GridRef g : timeGrids) {
                if (ch.equals(TravelTimeTableCard.quantityLabel(g))) {
                    loadTimeGrid(g);
                    return;
                }
            }
            return;
        }
        if (modelVolume != null) {
            displayVolume(modelVolume, ch);
            return;
        }
        if (volume == null) {
            return;
        }
        renderer.channel = ch;
        autoRange();
        canvas.invalidateScene(false);
    }

    /** Sets the colour bar range from the data of the current channel. */
    private void autoRange() {
        if (volume == null || renderer.channel == null) {
            return;
        }
        if (VelocityVolume3D.CH_LAYER.equals(renderer.channel)) {
            updateRangeInfo();
            canvas.invalidateScene(false);
            return;
        }
        double lo;
        double hi;
        if (profile1D != null) {
            double[] r = volume.range(renderer.channel);
            lo = Math.floor(r[0] * 20.0) / 20.0;
            hi = Math.ceil(r[1] * 20.0) / 20.0;
        } else {
            lo = volume.quantile(renderer.channel, 0.02);
            hi = volume.quantile(renderer.channel, 0.98);
            if (renderer.channel.startsWith("dvs") || (lo < 0 && hi > 0)) {
                double m = Math.max(Math.abs(lo), Math.abs(hi));
                m = Math.max(0.5, Math.ceil(m * 2.0) / 2.0);
                lo = -m;
                hi = m;
            } else {
                lo = Math.floor(lo * 20.0) / 20.0;
                hi = Math.ceil(hi * 20.0) / 20.0;
            }
        }
        if (hi <= lo) {
            hi = lo + 0.1;
        }
        setColorRange(lo, hi);
    }

    private static String format(double v) {
        return String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String describe(Volume3DRenderer.Probe p) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "lon %.3f\u00B0E  lat %.3f\u00B0N  depth %.1f km", p.lon, p.lat, p.depthKm));
        List<String> names = volume.channelNames();
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            sb.append("  |  ");
            if (VelocityVolume3D.CH_LAYER.equals(n)) {
                int id = (int) Math.round(p.values[i]);
                sb.append("layer ").append(id);
                if (id >= 1 && id <= ColorMaps.LAYER_NAMES.length) {
                    sb.append(" (").append(ColorMaps.LAYER_NAMES[id - 1]).append(')');
                }
            } else {
                sb.append(Volume3DCanvas.channelTitle(n)).append(' ')
                  .append(String.format(Locale.ROOT, "%.3f", p.values[i]));
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ export

    private void saveImage() {
        if (volume == null) {
            return;
        }
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setSelectedFile(new File("velocity_model_3d.png"));
        if (fc.showSaveDialog(root) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
            f = new File(f.getParentFile(), f.getName() + ".png");
        }
        try {
            ImageIO.write(canvas.snapshot(), "png", f);
            lastDir = f.getParentFile();
            statusLabel.setText("Saved " + f.getAbsolutePath());
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Image export failed", ex);
            JOptionPane.showMessageDialog(root, "Could not save image:\n" + ex.getMessage(),
                "Velocity model", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copyImage() {
        if (volume == null) {
            return;
        }
        final BufferedImage img = canvas.snapshot();
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] {DataFlavor.imageFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return DataFlavor.imageFlavor.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(flavor)) {
                        throw new UnsupportedFlavorException(flavor);
                    }
                    return img;
                }
            }, null);
            statusLabel.setText("Image copied to the clipboard");
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Clipboard copy failed", ex);
            statusLabel.setText("Could not copy the image: " + ex.getMessage());
        }
    }
}
