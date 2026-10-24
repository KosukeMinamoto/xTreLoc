package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.AppSettingsCache;
import com.treloc.xtreloc.app.gui.util.GuiExecutionLog;
import com.treloc.xtreloc.app.gui.util.SharedFileManager;
import com.treloc.xtreloc.app.gui.util.UiFonts;
import com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry;
import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D;
import com.treloc.xtreloc.solver.grid.GridTravelTimeTable;
import com.treloc.xtreloc.solver.grid.LayerCakeModel;
import com.treloc.xtreloc.solver.grid.StationTimeGrid;
import com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder;
import com.treloc.xtreloc.solver.grid.TravelTimeGridSet;
import com.treloc.xtreloc.solver.table.TravelTimeTable.Phase;
import com.treloc.xtreloc.solver.table.TravelTimeTables;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

/**
 * Model tab source: load an existing travel-time table, or build one from a visualized 1-D velocity model
 * and Range. Load and Build are swapped with a combo ({@link CardLayout}); both panels stay constructed.
 */
final class TravelTimeTableCard extends JPanel {

    private static final Logger logger = Logger.getLogger(TravelTimeTableCard.class.getName());

    static final String MODE_LOAD = "Travel-time table";
    static final String MODE_BUILD = "Velocity";

    /** What the card needs from the 3D map. */
    interface Host {
        /** The layer cake of the shown 1-D model (with the strike / dip fields applied), or {@code null}. */
        LayerCakeModel layerCake();

        /** Region / spacing of the Model Range tab, or {@code null} if invalid. */
        Geometry geometry();

        /** Stations of the Model Station table. */
        List<TravelTimeGridBuilder.StationPoint> stations();

        /** Adds the travel-time field as a Background → Quantity (keeps the velocity model). */
        void showVolume(VelocityVolume3D v);

        /** Lists loaded grids in the map Quantity combo (does not read every .buf). */
        void setTravelTimeGrids(List<TravelTimeGridSet.GridRef> grids);

        void setStatus(String text);

        /** Source combo: travel-time table vs velocity. Switches the visualized volume. */
        void setTravelTimeSource(boolean travelTime);

        /** Rebuilds the velocity layer cake from the shared Range. */
        void applyRangeFromShared();
    }

    private final Host host;

    private final JCheckBox phaseP = new JCheckBox("P", true);
    private final JCheckBox phaseS = new JCheckBox("S", true);
    private final JCheckBox flatten = new JCheckBox("Earth flattening (spherical Earth)", true);
    private final JTextField outDir = new JTextField(16);
    private final JTextField rootName = new JTextField(10);
    private final JCheckBox plotGridStationsOnly = new JCheckBox("Plot only stations that have a grid", true);
    private final JLabel estimate = new JLabel(" ");
    private final JButton buildButton = new JButton("Build and export");
    private final JButton cancelButton = new JButton("Cancel");
    private final JProgressBar progress = new JProgressBar(0, 100);

    private final JTextField tablePath = new JTextField();
    private final JComboBox<String> modeCombo = new JComboBox<>(new String[] {MODE_LOAD, MODE_BUILD});
    private final CardLayout tableCardsLayout = new CardLayout();
    private final JPanel tableCards = new JPanel(tableCardsLayout);
    private final CardLayout extraCardsLayout = new CardLayout();
    private final JPanel extraCards = new JPanel(extraCardsLayout);
    private final VelocityModelTablePanel velocityModels;

    private List<TravelTimeGridSet.GridRef> grids = new ArrayList<>();
    private Geometry tableRange;
    private Geometry savedVelocityRange;
    private String activeSource;
    private final java.util.List<java.util.function.Consumer<Boolean>> sourceListeners = new ArrayList<>();
    private SwingWorker<?, ?> running;
    private File lastDir;
    private String lastAutoRoot = "";
    private String lastAutoOut = "";
    private boolean loadingTable;

    TravelTimeTableCard(Host host) {
        this(host, null, null);
    }

    TravelTimeTableCard(Host host, JComponent velocityPanel, JComponent rangePanel) {
        this.host = host;
        this.velocityModels = velocityPanel instanceof VelocityModelTablePanel
            ? (VelocityModelTablePanel) velocityPanel : null;
        setLayout(new BorderLayout(0, 4));
        setBackground(AppPanelStyle.getPanelBg());
        setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

        JPanel modeRow = row();
        modeRow.add(label("Source"));
        AppPanelStyle.styleComboBox(modeCombo);
        modeCombo.addActionListener(e -> showSelectedMode());
        modeRow.add(modeCombo);
        add(modeRow, BorderLayout.NORTH);

        tableCards.setBackground(AppPanelStyle.getPanelBg());
        tableCards.add(createLoadPicker(), MODE_LOAD);
        tableCards.add(velocityPanel != null ? velocityPanel : new JPanel(), MODE_BUILD);
        stretch(tableCards, 72);

        extraCards.setBackground(AppPanelStyle.getPanelBg());
        extraCards.add(createLoadExtras(), MODE_LOAD);
        extraCards.add(createBuildControls(), MODE_BUILD);
        extraCards.setAlignmentX(Component.LEFT_ALIGNMENT);
        extraCards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(AppPanelStyle.getPanelBg());
        body.add(tableCards);
        if (rangePanel != null) {
            rangePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            rangePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 360));
            body.add(rangePanel);
        }
        body.add(extraCards);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        phaseP.addActionListener(e -> refresh());
        phaseS.addActionListener(e -> refresh());
        SharedFileManager.getInstance().addTaupFileListener(this::onSharedTravelTime);
        refresh();
        String taup = SharedFileManager.getInstance().getTaupFile();
        selectMode(taup != null && TravelTimeTables.isTableSpec(taup) ? MODE_LOAD : MODE_BUILD);
        onSharedTravelTime(taup);
    }

    private JPanel createLoadPicker() {
        AppPanelStyle.styleTextField(tablePath);
        tablePath.setToolTipText("Directory of NonLinLoc grids, or a *.time.hdr file");
        tablePath.addActionListener(e -> {
            String spec = tablePath.getText() != null ? tablePath.getText().trim() : "";
            if (!spec.isEmpty()) {
                loadPath(spec);
            }
        });
        JButton browse = new JButton();
        try {
            Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
            if (fileIcon != null) {
                browse.setIcon(fileIcon);
            } else {
                browse.setText("📄");
            }
        } catch (RuntimeException e) {
            browse.setText("📄");
        }
        browse.setToolTipText("Select travel-time table (directory or *.time.hdr)");
        AppPanelStyle.stylePrimaryButton(browse);
        browse.addActionListener(e -> loadTable());

        JPanel pathRow = new JPanel(new BorderLayout());
        pathRow.setBackground(AppPanelStyle.getPanelBg());
        pathRow.add(browse, BorderLayout.WEST);
        pathRow.add(tablePath, BorderLayout.CENTER);

        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBackground(AppPanelStyle.getPanelBg());
        p.add(label("Table"), BorderLayout.WEST);
        p.add(pathRow, BorderLayout.CENTER);
        return p;
    }

    private JPanel createLoadExtras() {
        JPanel extras = new JPanel();
        extras.setLayout(new BoxLayout(extras, BoxLayout.Y_AXIS));
        extras.setBackground(AppPanelStyle.getPanelBg());
        extras.setAlignmentX(Component.LEFT_ALIGNMENT);
        plotGridStationsOnly.setBackground(AppPanelStyle.getPanelBg());
        plotGridStationsOnly.setFont(AppPanelStyle.getLabelFont());
        plotGridStationsOnly.addActionListener(e ->
            SharedFileManager.getInstance().setPlotOnlyGridStations(plotGridStationsOnly.isSelected()));
        extras.add(left(plotGridStationsOnly));
        return extras;
    }

    private JPanel createBuildControls() {
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(AppPanelStyle.getPanelBg());
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel r1 = row();
        r1.add(label("Phases"));
        r1.add(phaseP);
        r1.add(phaseS);
        controls.add(r1);
        flatten.setBackground(AppPanelStyle.getPanelBg());
        flatten.setFont(AppPanelStyle.getLabelFont());
        controls.add(left(flatten));
        JPanel r2 = row();
        r2.add(label("Output"));
        AppPanelStyle.styleTextField(outDir);
        r2.add(outDir);
        JButton browse = new JButton("...");
        browse.addActionListener(e -> browseOutput());
        r2.add(browse);
        controls.add(r2);
        AppPanelStyle.styleTextField(rootName);
        rootName.setVisible(false);
        estimate.setFont(AppPanelStyle.getLabelFont());
        estimate.setForeground(AppPanelStyle.getMutedTextColor());
        controls.add(left(estimate));
        JPanel r4 = row();
        buildButton.setText("▶ Build and export");
        buildButton.setFont(UiFonts.uiBold(12f));
        buildButton.setPreferredSize(new Dimension(180, 35));
        AppPanelStyle.styleSuccessButton(buildButton);
        buildButton.addActionListener(e -> startBuild());
        cancelButton.setText("⏹ Cancel");
        cancelButton.setFont(UiFonts.uiBold(14f));
        cancelButton.setPreferredSize(new Dimension(120, 35));
        cancelButton.setBackground(new Color(200, 50, 50));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFocusPainted(false);
        cancelButton.setBorderPainted(false);
        cancelButton.setOpaque(true);
        cancelButton.addActionListener(e -> cancel());
        cancelButton.setEnabled(false);
        r4.add(buildButton);
        r4.add(cancelButton);
        controls.add(r4);
        progress.setStringPainted(true);
        progress.setString(" ");
        controls.add(left(progress));
        return controls;
    }

    private static void stretch(JComponent c, int prefH) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setPreferredSize(new Dimension(240, prefH));
        c.setMinimumSize(new Dimension(80, 40));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, prefH + 8));
    }

    private void showSelectedMode() {
        Object m = modeCombo.getSelectedItem();
        if (m == null) {
            return;
        }
        applySourceMode(m.toString());
    }

    private void selectMode(String mode) {
        if (mode == null) {
            return;
        }
        if (!mode.equals(modeCombo.getSelectedItem())) {
            modeCombo.setSelectedItem(mode);
        }
        applySourceMode(mode);
    }

    private void applySourceMode(String mode) {
        tableCardsLayout.show(tableCards, mode);
        extraCardsLayout.show(extraCards, mode);
        boolean build = MODE_BUILD.equals(mode);
        if (MODE_BUILD.equals(activeSource) && MODE_LOAD.equals(mode)) {
            savedVelocityRange = SharedFileManager.getInstance().getGeometry();
        }
        activeSource = mode;
        if (velocityModels != null) {
            velocityModels.setApplyToSolver(build);
        }
        if (build) {
            if (savedVelocityRange != null) {
                SharedFileManager.getInstance().setGeometry(savedVelocityRange);
            }
            host.setTravelTimeSource(false);
            host.applyRangeFromShared();
            fireSourceListeners(false);
            return;
        }
        String spec = tablePath.getText() != null ? tablePath.getText().trim() : "";
        if (!spec.isEmpty() && TravelTimeTables.isTableSpec(spec) && !loadingTable) {
            String cur = SharedFileManager.getInstance().getTaupFile();
            if (!spec.equals(cur)) {
                SharedFileManager.getInstance().setTaupFile(spec);
            }
        }
        if (!grids.isEmpty()) {
            autofillRangeFromGrids();
        }
        host.setTravelTimeSource(true);
        fireSourceListeners(!build);
    }

    void addSourceListener(java.util.function.Consumer<Boolean> listener) {
        if (listener != null) {
            sourceListeners.add(listener);
        }
    }

    private void fireSourceListeners(boolean travelTime) {
        for (java.util.function.Consumer<Boolean> l : new ArrayList<>(sourceListeners)) {
            l.accept(travelTime);
        }
    }

    boolean isTravelTimeSource() {
        return MODE_LOAD.equals(modeCombo.getSelectedItem());
    }

    /** Full lon/lat/depth box of the loaded travel-time table, or {@code null}. */
    Geometry tableRange() {
        return tableRange == null ? null : tableRange.copy();
    }

    /** Re-applies the table's lon/lat/depth box to Range (travel-time source). */
    void refillRangeFromTable() {
        if (!grids.isEmpty()) {
            autofillRangeFromGrids();
        }
    }

    // ------------------------------------------------------------------ layout helpers

    private static JPanel row() {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        r.setBackground(AppPanelStyle.getPanelBg());
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppPanelStyle.getLabelFont());
        l.setForeground(AppPanelStyle.getContentTextColor());
        return l;
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    // ------------------------------------------------------------------ state

    /** Re-reads the model, region and stations (called when the card is opened or the model changes). */
    void refresh() {
        LayerCakeModel cake = host.layerCake();
        Geometry g = host.geometry();
        List<TravelTimeGridBuilder.StationPoint> st = host.stations();
        boolean ok = cake != null && g != null;
        if (cake != null) {
            String auto = defaultRoot(cake);
            if (rootName.getText().trim().isEmpty() || rootName.getText().trim().equals(lastAutoRoot)) {
                rootName.setText(auto);
            }
            lastAutoRoot = auto;
        }
        autofillOutput();
        if (ok) {
            try {
                TravelTimeGridBuilder.Region reg = region(g);
                int nPh = (phaseP.isSelected() ? 1 : 0) + (phaseS.isSelected() ? 1 : 0);
                double mbOne = reg.nodeCount() * 4.0 / 1.0e6;
                estimate.setText(String.format(Locale.ROOT,
                    "<html><body style='width:210px'>%d stations x %d phases, %,d nodes (%.1f MB) per grid, %.0f MB in total</body></html>",
                    st.size(), nPh, reg.nodeCount(), mbOne, mbOne * st.size() * nPh));
            } catch (RuntimeException e) {
                estimate.setText(e.getMessage());
            }
        } else {
            estimate.setText(" ");
        }
        buildButton.setEnabled(ok && running == null);
    }

    private static String defaultRoot(LayerCakeModel cake) {
        String n = cake.name();
        int dot = n.lastIndexOf('.');
        return (dot > 0 ? n.substring(0, dot) : n).replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /** {@code <settings home>/time/<root>} — NonLinLoc {@code time} folder. */
    static File defaultTableDir(String home, String root) {
        String h = home == null || home.isBlank() ? System.getProperty("user.home") : home;
        File time = new File(h, "time");
        if (root == null || root.isBlank()) {
            return time;
        }
        return new File(time, root);
    }

    private void autofillOutput() {
        String home = AppSettingsCache.snapshot().getHomeDirectory();
        String root = rootName.getText().trim();
        if (root.isEmpty()) {
            root = lastAutoRoot;
        }
        String auto = defaultTableDir(home, root).getAbsolutePath();
        String cur = outDir.getText().trim();
        if (cur.isEmpty() || cur.equals(lastAutoOut)) {
            outDir.setText(auto);
        }
        lastAutoOut = auto;
    }

    private TravelTimeGridBuilder.Region region(Geometry g) {
        return TravelTimeGridBuilder.Region.fromLonLatBox(g.lonMin, g.lonMax, g.latMin, g.latMax,
            g.minDepthKm, g.maxDepthKm, g.spacingHoriz(), g.spacingDepth());
    }

    private void browseOutput() {
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Output directory of the travel-time grids");
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            outDir.setText(fc.getSelectedFile().getAbsolutePath());
            lastDir = fc.getSelectedFile().getParentFile();
        }
    }

    // ------------------------------------------------------------------ build

    private void startBuild() {
        LayerCakeModel cake = host.layerCake();
        Geometry g = host.geometry();
        List<TravelTimeGridBuilder.StationPoint> stations = host.stations();
        if (cake == null || g == null) {
            error("Show a 1-D model with a valid region first.");
            return;
        }
        logger.info(String.format(Locale.ROOT, "Build from %s  strike %.1f dip %.1f",
            cake.name(), cake.strikeDeg(), cake.dipDeg()));
        if (stations.isEmpty()) {
            error("No stations. Load a station file (Station tab) first.");
            return;
        }
        Set<Phase> phases = EnumSet.noneOf(Phase.class);
        if (phaseP.isSelected()) {
            phases.add(Phase.P);
        }
        if (phaseS.isSelected()) {
            phases.add(Phase.S);
        }
        if (phases.isEmpty()) {
            error("Select at least one phase.");
            return;
        }
        autofillOutput();
        String dirText = outDir.getText().trim();
        String root = rootName.getText().trim();
        if (root.isEmpty()) {
            root = lastAutoRoot.isEmpty() ? defaultRoot(cake) : lastAutoRoot;
            rootName.setText(root);
        }
        if (dirText.isEmpty()) {
            dirText = defaultTableDir(AppSettingsCache.snapshot().getHomeDirectory(), root).getAbsolutePath();
            outDir.setText(dirText);
        }
        final Path dir = new File(dirText).toPath();
        final TravelTimeGridBuilder builder;
        try {
            builder = new TravelTimeGridBuilder(cake, region(g), flatten.isSelected(), phases);
        } catch (RuntimeException ex) {
            error(ex.getMessage());
            return;
        }
        double totalMb = builder.nodesPerGrid() * 4.0 / 1.0e6 * stations.size() * phases.size();
        if (totalMb > 2000.0 && JOptionPane.showConfirmDialog(this,
            String.format(Locale.ROOT, "The tables will need about %.1f GB on disk. Continue?", totalMb / 1000.0),
            "Travel-time table", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        final int total = stations.size() * phases.size();
        final String rootFinal = root;
        progress.setMaximum(total);
        progress.setValue(0);
        progress.setString("0 / " + total);
        host.setStatus("Building travel-time tables ...");

        SwingWorker<Integer, Integer> w = new SwingWorker<Integer, Integer>() {
            @Override
            protected Integer doInBackground() throws Exception {
                java.util.concurrent.atomic.AtomicInteger written = new java.util.concurrent.atomic.AtomicInteger();
                builder.build(stations, (done, tot, msg) -> {
                    if (isCancelled()) {
                        throw new CancellationException();
                    }
                    publish(done);
                }, g -> {
                    try {
                        g.write(dir, rootFinal);
                        written.incrementAndGet();
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
                return written.get();
            }

            @Override
            protected void process(List<Integer> chunks) {
                int d = chunks.get(chunks.size() - 1);
                progress.setValue(d);
                progress.setString(d + " / " + total);
            }

            @Override
            protected void done() {
                running = null;
                cancelButton.setEnabled(false);
                SharedFileManager.getInstance().setComputationBusy(false);
                try {
                    int n = get();
                    progress.setString("done: " + n + " grids");
                    String dest = dir.toAbsolutePath().toString();
                    host.setStatus("Wrote " + n + " travel-time grids to " + dest);
                    GuiExecutionLog.info("Travel-time tables exported to " + dest);
                    logger.info("Travel-time tables exported to " + dest);
                    setTable(dir.toString(), rootFinal);
                    SharedFileManager.getInstance().setTaupFile(dir.toString());
                    loadBuiltTable(dir.toString());
                    selectMode(MODE_LOAD);
                } catch (CancellationException ex) {
                    progress.setString("cancelled");
                    host.setStatus("Travel-time table build cancelled.");
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    if (c instanceof CancellationException) {
                        progress.setString("cancelled");
                    } else {
                        logger.log(Level.WARNING, "Travel-time table build failed", c);
                        progress.setString("failed");
                        error("Could not build the tables:\n" + c.getMessage());
                    }
                }
                refresh();
            }
        };
        running = w;
        SharedFileManager.getInstance().setComputationBusy(true);
        w.execute();
    }

    private void cancel() {
        if (running != null) {
            running.cancel(true);
        }
    }

    // ------------------------------------------------------------------ load / show

    private void loadTable() {
        JFileChooser fc = new JFileChooser(lastDir);
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setDialogTitle("Travel-time table (directory, or a *.time.hdr file)");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        lastDir = f.isDirectory() ? f : f.getParentFile();
        loadPath(f.getAbsolutePath());
    }

    private void loadPath(String spec) {
        if (spec == null || spec.isEmpty() || loadingTable) {
            return;
        }
        File f = new File(spec);
        if (!TravelTimeTables.isTableSpec(spec)) {
            error("Not a travel-time table:\n" + spec
                + "\nExpected a directory of NonLinLoc grids (P and S *.time.hdr).");
            return;
        }
        loadingTable = true;
        try {
            String root = f.isFile() ? GridTravelTimeTable.rootOf(f.getName()) : null;
            Path dir = f.isDirectory() ? f.toPath() : f.getParentFile().toPath();
            if (GridTravelTimeTable.isGridSpec(spec)) {
                // validates (several sets in one directory are rejected with a clear message)
                List<TravelTimeGridSet.GridRef> refs = TravelTimeGridSet.list(dir, root);
                if (f.isDirectory()) {
                    java.util.TreeSet<String> roots = new java.util.TreeSet<>();
                    for (TravelTimeGridSet.GridRef g : refs) {
                        String r = GridTravelTimeTable.rootOf(g.header.getFileName().toString());
                        if (r != null) {
                            roots.add(r);
                        }
                    }
                    if (roots.size() > 1) {
                        error("The directory holds several grid sets " + roots + ".\n"
                            + "Select one of the .time.hdr files to choose a set.");
                        return;
                    }
                }
                setTable(spec, root);
            } else {
                grids = new ArrayList<>();
                tablePath.setText(spec);
                publishGridLabels();
            }
            SharedFileManager.getInstance().setTaupFile(spec);
            host.setStatus("Travel-time table set for the Solver: " + spec);
            host.setTravelTimeGrids(grids);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Table load failed", ex);
            error("Could not read the table:\n" + ex.getMessage());
        } finally {
            loadingTable = false;
        }
    }

    /** Lists the grids of a table directory and shows them in the station combo boxes. */
    private void setTable(String spec, String root) {
        try {
            File f = new File(spec);
            Path dir = f.isDirectory() ? f.toPath() : f.getParentFile().toPath();
            grids = TravelTimeGridSet.list(dir, root);
            tablePath.setText(f.isDirectory() ? dir.toAbsolutePath().toString() : spec);
            warnIfMixedGeometry(grids);
            publishGridLabels();
            autofillRangeFromGrids();
            host.setTravelTimeGrids(grids);
        } catch (java.io.IOException ex) {
            error("Could not list the grids:\n" + ex.getMessage());
        }
    }

    /** Loads a just-built directory into Table in use; Quantity lists every grid. */
    private void loadBuiltTable(String spec) {
        tablePath.setText(spec);
        publishGridLabels();
        host.setTravelTimeGrids(grids);
    }

    private void autofillRangeFromGrids() {
        if (grids == null || grids.isEmpty()) {
            return;
        }
        try {
            StationTimeGrid.Header h = StationTimeGrid.parseHeader(grids.get(0).header);
            if (h.transform == null) {
                return;
            }
            double x1 = h.x0 + (h.nx - 1) * h.dx;
            double y1 = h.y0 + (h.ny - 1) * h.dy;
            double[] sw = h.transform.toLatLon(h.x0, h.y0);
            double[] se = h.transform.toLatLon(x1, h.y0);
            double[] nw = h.transform.toLatLon(h.x0, y1);
            double[] ne = h.transform.toLatLon(x1, y1);
            Geometry n = SharedFileManager.getInstance().getGeometry();
            n.latMin = Math.min(Math.min(sw[0], se[0]), Math.min(nw[0], ne[0]));
            n.latMax = Math.max(Math.max(sw[0], se[0]), Math.max(nw[0], ne[0]));
            n.lonMin = Math.min(Math.min(sw[1], se[1]), Math.min(nw[1], ne[1]));
            n.lonMax = Math.max(Math.max(sw[1], se[1]), Math.max(nw[1], ne[1]));
            n.minDepthKm = h.z0;
            n.maxDepthKm = h.z0 + (h.nz - 1) * h.dz;
            n.spacingKm = h.dx;
            n.spacingDepthKm = h.dz;
            tableRange = n.copy();
            SharedFileManager.getInstance().setGeometry(n);
            logger.info(String.format(Locale.ROOT,
                "Range from table: lon %.3f–%.3f lat %.3f–%.3f depth %.1f–%.1f dxy=%.2f dz=%.2f (strike %.1f dip %.1f)",
                n.lonMin, n.lonMax, n.latMin, n.latMax, n.minDepthKm, n.maxDepthKm,
                n.spacingKm, n.spacingDepthKm, n.strikeDeg, n.dipDeg));
        } catch (Exception ex) {
            logger.log(Level.FINE, "Could not autofill range from table", ex);
        }
    }

    private void publishGridLabels() {
        java.util.LinkedHashSet<String> labels = new java.util.LinkedHashSet<>();
        for (TravelTimeGridSet.GridRef g : grids) {
            if (g.label != null && !g.label.isBlank()) {
                labels.add(g.label);
            }
        }
        SharedFileManager.getInstance().setTravelTimeGridLabels(labels);
    }

    /** Solver combo can load a table too; keep the plot filter in sync. */
    private void onSharedTravelTime(String file) {
        if (loadingTable) {
            return;
        }
        if (file != null && TravelTimeTables.isTableSpec(file)) {
            String cur = tablePath.getText() != null ? tablePath.getText().trim() : "";
            if (!file.equals(cur)) {
                loadPath(file);
            }
            selectMode(MODE_LOAD);
            return;
        }
        if (!grids.isEmpty() || !SharedFileManager.getInstance().getTravelTimeGridLabels().isEmpty()) {
            SharedFileManager.getInstance().setTravelTimeGridLabels(java.util.List.of());
        }
    }

    void setInputsLocked(boolean locked) {
        AppPanelStyle.setTreeEnabled(this, !locked, cancelButton);
        cancelButton.setEnabled(locked && running != null);
        if (!locked) {
            refresh();
        }
    }

    /**
     * Same root / folder can hold leftover grids from an earlier spacing. Each station still uses its own
     * header; warn so a mixed directory is not taken as a single uniform table.
     */
    private void warnIfMixedGeometry(List<TravelTimeGridSet.GridRef> refs) {
        if (refs == null || refs.size() < 2) {
            return;
        }
        try {
            StationTimeGrid.Header first = StationTimeGrid.parseHeader(refs.get(0).header);
            for (int i = 1; i < refs.size(); i++) {
                StationTimeGrid.Header h = StationTimeGrid.parseHeader(refs.get(i).header);
                if (first.nx != h.nx || first.ny != h.ny || first.nz != h.nz
                        || first.dx != h.dx || first.dy != h.dy || first.dz != h.dz
                        || first.x0 != h.x0 || first.y0 != h.y0 || first.z0 != h.z0) {
                    JOptionPane.showMessageDialog(this,
                        "This folder contains travel-time grids with different spacing or region.\n"
                            + "Each station uses its own grid. Prefer one spacing per folder.",
                        "Travel-time table", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    static String quantityLabel(TravelTimeGridSet.GridRef g) {
        return "Travel time " + g.phase + " " + g.label;
    }

    void showField() {
        if (grids.isEmpty()) {
            return;
        }
        showField(grids.get(0).phase, grids.get(0).label);
    }

    void showField(String phase, String label) {
        if (phase == null || label == null) {
            return;
        }
        for (TravelTimeGridSet.GridRef g : grids) {
            if (g.phase.equals(phase) && g.label.equals(label)) {
                host.setStatus("Reading " + g.header.getFileName() + " ...");
                new SwingWorker<VelocityVolume3D, Void>() {
                    @Override
                    protected VelocityVolume3D doInBackground() throws Exception {
                        StationTimeGrid grid = StationTimeGrid.read(g.header);
                        return grid.toVolume(quantityLabel(g));
                    }

                    @Override
                    protected void done() {
                        try {
                            host.showVolume(get());
                            host.setStatus("Travel time of " + g.phase + " from station " + g.label);
                        } catch (Exception ex) {
                            Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                            error("Could not show the grid:\n" + c.getMessage());
                        }
                    }
                }.execute();
                return;
            }
        }
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Travel-time table", JOptionPane.ERROR_MESSAGE);
    }

    // ------------------------------------------------------------------ tests

    /** Grids listed in the "Table in use" section (for tests). */
    int gridCountForTest() {
        return grids.size();
    }

    /** Loads a table without a file chooser (for tests). */
    void setTableForTest(String spec, String root) {
        setTable(spec, root);
    }

    boolean isBuildEnabledForTest() {
        return buildButton.isEnabled();
    }

    String fileRootForTest() {
        return rootName.getText().trim();
    }
}
