package com.treloc.xtreloc.app.gui.view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import edu.sc.seis.seisFile.sac.SacTimeSeries;

import com.treloc.xtreloc.app.gui.service.AicPicker;
import com.treloc.xtreloc.app.gui.service.AutoPicker;
import com.treloc.xtreloc.app.gui.service.EqtPicker;
import com.treloc.xtreloc.app.gui.service.KurtosisPicker;
import com.treloc.xtreloc.app.gui.service.PickEditor;
import com.treloc.xtreloc.app.gui.service.PickPool;
import com.treloc.xtreloc.app.gui.service.PickProposal;
import com.treloc.xtreloc.app.gui.service.ArrivalTime;
import com.treloc.xtreloc.app.gui.service.ArrivalTimeManager;
import com.treloc.xtreloc.app.gui.service.CrossCorrelationPickingService;
import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.service.DifferentialDatWriter;
import com.treloc.xtreloc.app.gui.service.EventWaveformResolver;
import com.treloc.xtreloc.app.gui.util.EventSelectionModel;
import com.treloc.xtreloc.app.gui.service.SacComponentClassifier;
import com.treloc.xtreloc.app.gui.service.WaveProcessor;
import com.treloc.xtreloc.app.gui.service.WinPickfileWriter;
import com.treloc.xtreloc.app.gui.volume3d.Map3DIcons;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.AppSettings;
import com.treloc.xtreloc.app.gui.util.AppSettingsCache;
import com.treloc.xtreloc.app.gui.util.ChartImageExport;
import com.treloc.xtreloc.app.gui.util.DirectoryChooserHelper;
import com.treloc.xtreloc.app.gui.util.FrequencySpec;

/**
 * Pick viewer: AIC auto P/S, optional manual click correction, CF/envelope overlay,
 * and a custom stacked-waveform canvas (no JFreeChart).
 * <p>
 * Layout: directory tree with add/remove (left), a mode switch (middle) between
 * {@link Mode#ABSOLUTE Absolute} picking (auto-pick, manual P/S, export) and
 * {@link Mode#CORRELATION Correlation} (CF / envelope functions, XCorr),
 * stacked waveforms with filter / manual-edit (right).
 * <p>
 * Exports (see {@link #exportPicks()}):
 * <ul>
 *   <li>{@code .obs} phase picks (NONLINLOC style)</li>
 *   <li>WIN {@code pickfile} ({@code #p} / {@code #s})</li>
 *   <li>station-pair differential table {@code .dat} ({@code stnk,stnl,lagt,coef}) for the P or the S phase</li>
 * </ul>
 * Sliding-window cross-correlation of already-written CF SAC lives on Correlation → XCorr.
 *
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class PickViewerPanel {

    /** Which family of picks the Seismic tab is working on. */
    public enum Mode {
        /** Absolute arrival times (auto-pick, manual P/S, .obs / WIN export). */
        ABSOLUTE,
        /** Station-pair lags from sliding-window cross-correlation of CF / envelope traces. */
        CORRELATION
    }



    private static final Logger logger = Logger.getLogger(PickViewerPanel.class.getName());

    /** Processing-settings column (Absolute / Correlation). */
    private static final int SETTINGS_COLUMN_WIDTH = 280;
    private static final String FUNCTIONS_INFO =
        "Compute a characteristic function or horizontal envelope from the folder selected in the tree "
            + "and write one SAC per station.";
    private static final String XCORR_INFO =
        "Sliding-window cross-correlation of CF SAC files from Functions.\n"
            + "Select those files in the left tree, then Run. CF is not recomputed.";
    private static final DateTimeFormatter BASE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyMMdd.HHmmss", Locale.US);

    private final ArrivalTimeManager arrivalTimeManager = new ArrivalTimeManager();
    /** All edits of the absolute picks go through this editor (Undo / Redo, one step per action). */
    private final PickEditor pickEditor = new PickEditor(arrivalTimeManager);
    private PickTablePanel pickTable;
    private JComboBox<String> autoMethodCombo;
    private JTextField autoOnsetField;
    private JPanel autoOnsetRow;
    private JPanel classicParams;
    private JPanel eqtParams;
    private JTextField eqtThrPField;
    private JTextField eqtThrSField;
    private JComboBox<String> eqtMergeCombo;
    private JButton autoRunButton;
    private JButton eqtExportButton;
    private JCheckBox onlyEmptyCheck;
    private JButton applyAllButton;
    private JButton discardButton;
    private final List<Runnable> stateListeners = new ArrayList<>();
    private final List<Consumer<String>> statusListeners = new ArrayList<>();
    {
        pickEditor.addListener(e -> {
            syncPicksToCanvas();
            fireState();
        });
    }
    private SacDirectoryBrowser browser;

    private File selectedDir;
    private String outputDir = ".";
    private String stationFilePath;
    private Consumer<File> inputRootListener;

    private JPanel leftPanel;
    private JPanel settingsPanel;
    private JPanel browserPanel;
    private JPanel rightPanel;
    private JPanel chartPanel;
    private JPanel zoomChartPanel;
    private JTextField outputDirField;
    private JTextField stationFileField;
    private JTextField lowFreqField;
    private JTextField highFreqField;
    private JLabel mousePositionLabel;
    private JLabel statusLabel;
    private ViewerSideChrome.Host waveformChrome;
    private JTextField aicPFracField;
    private JTextField aicMinPsField;
    private JTextField aicMaxPsField;
    private JPanel functionsHost;
    private JPanel xcorrHost;
    private CardLayout modeCardLayout;
    private JPanel modeCards;
    private JTabbedPane corrTabs;
    private final PickPool pickPool = new PickPool();
    private Mode mode = Mode.ABSOLUTE;
    private JPanel progressHost;
    private TraceProcessingPanel crossCorr;
    private WaveformCanvas waveformCanvas;
    private WaveformZoomCanvas zoomCanvas;

    /** Last applied display filter (after typing); null corner = omit. */
    private Float displayFilterFreqMin = null;
    private Float displayFilterFreqMax = null;
    private Timer filterPlotDebounceTimer;

    /** Catalog event whose waveforms are shown (set by the map link or found from the folder name). */
    private EventKey currentEvent;
    /** Set while {@link #openEvent} drives the folder tree, so the tree does not look the event up again. */
    private EventKey pendingEvent;
    private Supplier<List<CatalogInfo>> catalogsSupplier;
    private RelocateHandler relocateHandler;
    private JLabel eventLabel;
    private JLabel relocateNote;
    private JButton relocateButton;
    /** Folder names give the origin time to this many seconds when matched to a catalog event. */
    private static final double FOLDER_EVENT_TOLERANCE_S = 2.0;

    private SacTimeSeries[] currentWaveforms = new SacTimeSeries[0];
    private String[] currentFileNames = new String[0];
    private List<File> plottedFiles = List.of();
    private Color colorVertical = Color.BLUE;
    private Color colorH1 = Color.BLACK;
    private Color colorH2 = Color.RED;
    private Color colorOther = new Color(90, 90, 90);

    public PickViewerPanel() {
        AppSettings settings = AppSettingsCache.snapshot();
        String homeDir = settings.getHomeDirectory();
        if (homeDir != null && !homeDir.isEmpty()) {
            File homeDirFile = new File(homeDir);
            if (homeDirFile.exists() && homeDirFile.isDirectory()) {
                this.outputDir = homeDir;
            }
        }
        rightPanel = createRightPanel();
        settingsPanel = createSettingsPanel();
        browserPanel = createBrowserPanel();
        leftPanel = settingsPanel;
        updateCharts(new SacTimeSeries[0]);
    }

    /** Functions / Picking settings (middle column). */
    public JPanel getSettingsPanel() {
        return settingsPanel;
    }

    /** Directory tree with add / remove (left column). */
    public JPanel getBrowserPanel() {
        return browserPanel;
    }

    /** Controls, directory tree and file list. */
    public JPanel getLeftPanel() {
        return leftPanel;
    }

    /** Waveform and zoom display. */
    public JPanel getRightPanel() {
        return rightPanel;
    }

    public void setInputRootListener(Consumer<File> listener) {
        this.inputRootListener = listener;
    }

    /** Sets the SAC root (shared with the Cross-corr tab). */
    public void setInputRoot(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        if (browser != null) {
            browser.addRoot(dir);
        }
        if (inputRootListener != null) {
            inputRootListener.accept(dir);
        }
    }

    /** Relocates the current event from the picks and reports the outcome through {@code done} (EDT). */
    public interface RelocateHandler {
        void relocate(EventKey event, Collection<ArrivalTime> picks, Consumer<String> done);
    }

    /** Catalogs searched to find the event of a folder; without it folders are never linked to events. */
    public void setCatalogsSupplier(Supplier<List<CatalogInfo>> supplier) {
        this.catalogsSupplier = supplier;
    }

    public void setRelocateHandler(RelocateHandler handler) {
        this.relocateHandler = handler;
        updateEventUi();
    }

    /** Catalog event shown now, or {@code null}. */
    public EventKey getCurrentEvent() {
        return currentEvent;
    }

    /**
     * Shows a catalog event: adds its waveform folders to the tree, selects the first one and plots it.
     * The event becomes the shared focus so the map shows it too.
     *
     * @param folders      waveform folders of the event (the first one is displayed)
     * @param otherFolders folders of other events; only added to the tree
     */
    public void openEvent(EventKey key, List<Path> folders, List<Path> otherFolders) {
        if (key == null || folders == null || folders.isEmpty()) {
            return;
        }
        pendingEvent = key;
        try {
            if (otherFolders != null) {
                for (Path p : otherFolders) {
                    if (browser != null) {
                        browser.addRoot(p.toFile());
                    }
                }
            }
            for (int i = 1; i < folders.size(); i++) {
                if (browser != null) {
                    browser.addRoot(folders.get(i).toFile());
                }
            }
            File first = folders.get(0).toFile();
            setInputRoot(first);
            previewDirectory(first);
        } finally {
            pendingEvent = null;
        }
        setCurrentEvent(key);
        EventSelectionModel.getInstance().setFocus(key);
    }

    private void setCurrentEvent(EventKey key) {
        currentEvent = key;
        updateEventUi();
        fireState();
    }

    // ------------------------------------------------------------------ commands for the menu / tool bar

    /** Called when picks, proposals or the linked event change (menus and the status bar follow it). */
    public void addStateListener(Runnable listener) {
        if (listener != null) {
            stateListeners.add(listener);
        }
    }

    /** Called with every new message of the status line. */
    public void addStatusListener(Consumer<String> listener) {
        if (listener != null) {
            statusListeners.add(listener);
        }
    }

    private void fireState() {
        for (Runnable r : new ArrayList<>(stateListeners)) {
            r.run();
        }
    }

    /** Selection model of the auto-pick method; share it with a combo box elsewhere. */
    public javax.swing.ComboBoxModel<String> autoMethodModel() {
        return autoMethodCombo.getModel();
    }

    public void runAutoPickNow() {
        runAutoPick();
    }

    public void applyAllProposalsNow() {
        if (hasProposals()) {
            applyAllProposals();
        }
    }

    public void discardProposalsNow() {
        if (pickTable != null) {
            pickTable.discardProposals();
        }
    }

    public boolean undoPick() {
        return pickEditor.undo();
    }

    public boolean redoPick() {
        return pickEditor.redo();
    }

    public boolean canUndoPick() {
        return pickEditor.canUndo();
    }

    public boolean canRedoPick() {
        return pickEditor.canRedo();
    }

    public boolean hasProposals() {
        return pickTable != null && !pickTable.proposals().isEmpty();
    }

    public boolean canRelocate() {
        return currentEvent != null && relocateHandler != null;
    }

    /** Number of stations with a P pick and with an S pick. */
    public int[] pickCounts() {
        return new int[] {arrivalTimeManager.countStations("P"), arrivalTimeManager.countStations("S")};
    }

    public void exportPicksNow() {
        exportPicks();
    }

    public void pickAllToPoolNow() {
        eqtAllToPool();
    }

    public void relocateNow() {
        relocateCurrentEvent();
    }

    /** Asks for a SAC folder and adds it to the directory tree. */
    public void chooseInputRoot() {
        File start = browser == null ? null : browser.getSelectedDirectory();
        File dir = DirectoryChooserHelper.selectDirectory(leftPanel, "Open SAC folder", start);
        if (dir != null) {
            setInputRoot(dir);
        }
    }

    private void updateEventUi() {
        if (eventLabel == null) {
            return;
        }
        if (currentEvent == null) {
            eventLabel.setText("<html><small>No catalog event linked to this folder.</small></html>");
        } else {
            eventLabel.setText("<html><small><b>" + currentEvent.catalog() + "</b> &nbsp;" + currentEvent.time()
                + "<br>" + String.format(Locale.ROOT, "%.4f, %.4f, %.2f km",
                    currentEvent.lat(), currentEvent.lon(), currentEvent.depth()) + "</small></html>");
        }
        if (relocateButton != null) {
            relocateButton.setEnabled(currentEvent != null && relocateHandler != null);
        }
    }

    /** Finds the catalog event of a folder from its name (yyMMdd.HHmmss) and links it. */
    private void linkEventOfFolder(File dir) {
        EventKey found = null;
        if (catalogsSupplier != null && dir != null) {
            found = EventWaveformResolver.findEvent(catalogsSupplier.get(),
                EventWaveformResolver.timeFromFolderName(dir.getName()), FOLDER_EVENT_TOLERANCE_S);
        }
        setCurrentEvent(found);
        if (found != null) {
            EventSelectionModel.getInstance().setFocus(found);
        }
    }

    private void relocateCurrentEvent() {
        if (currentEvent == null || relocateHandler == null) {
            return;
        }
        List<ArrivalTime> picks = new ArrayList<>(arrivalTimeManager.getArrivalTimeMap().values());
        if (picks.isEmpty()) {
            relocateNote.setText("No picks yet: pick P or S at two or more stations.");
            return;
        }
        relocateButton.setEnabled(false);
        relocateNote.setText("Relocating...");
        relocateHandler.relocate(currentEvent, picks, message -> {
            relocateNote.setText(message);
            relocateButton.setEnabled(currentEvent != null && relocateHandler != null);
        });
    }

    private JPanel buildEventPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        AppPanelStyle.setPanelBackground(p);
        p.setBorder(AppPanelStyle.createTitledSectionBorder("Event"));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        eventLabel = new JLabel();
        eventLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        relocateButton = new JButton("Relocate \u2192 Working");
        relocateButton.setFocusPainted(false);
        relocateButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        relocateButton.setToolTipText("Locate this event from the current picks with the solver selected in the "
            + "Solver panel, and show the result on the map as the Working layer");
        relocateButton.addActionListener(e -> relocateCurrentEvent());
        relocateNote = new JLabel(" ");
        relocateNote.setFont(AppPanelStyle.getLabelFont().deriveFont(11f));
        relocateNote.setForeground(AppPanelStyle.getMutedTextColor());
        relocateNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(eventLabel);
        p.add(Box.createVerticalStrut(4));
        p.add(relocateButton);
        p.add(relocateNote);
        updateEventUi();
        return p;
    }

    /** Embeds Input table, Functions, XCorr and the shared progress strip. */
    public void embedCrossCorr(TraceProcessingPanel panel) {
        this.crossCorr = panel;
        if (panel != null) {
            panel.setStationFileSupplier(() -> stationFilePath);
        }
        if (functionsHost != null && panel != null) {
            functionsHost.removeAll();
            functionsHost.add(panel.getFunctionsSection(), BorderLayout.CENTER);
            functionsHost.revalidate();
            functionsHost.repaint();
        }
        if (xcorrHost != null && panel != null) {
            xcorrHost.removeAll();
            xcorrHost.add(panel.getXcorrSection(), BorderLayout.CENTER);
            xcorrHost.revalidate();
            xcorrHost.repaint();
        }
        if (progressHost != null && panel != null) {
            progressHost.removeAll();
            progressHost.add(panel.getProgressSection(), BorderLayout.CENTER);
            progressHost.revalidate();
            progressHost.repaint();
        }
        if (selectedDir != null && panel != null) {
            panel.setWorkingDirectory(selectedDir);
        }
    }

    /** Picks of "All peaks \u2192 Pool", waiting for the Associate step. */
    public PickPool pickPool() {
        return pickPool;
    }

    /** Shows the Functions (CF / envelope) or the XCorr tab of the Correlation mode. */
    public void showCorrelationTab(boolean xcorr) {
        if (corrTabs != null) {
            corrTabs.setSelectedIndex(xcorr ? 1 : 0);
        }
    }

    List<String> leftSectionTitles() {
        List<String> titles = new ArrayList<>();
        titles.add("Absolute");
        titles.add("Correlation");
        return titles;
    }

    WaveformCanvas waveformCanvasForTest() {
        return waveformCanvas;
    }

    void showWaveformsForTest(SacTimeSeries[] waveforms) {
        updateCharts(waveforms);
    }

    void runAutoPickForTest(String method) {
        autoMethodCombo.setSelectedItem(method);
        runAutoPick();
    }

    boolean autoPickIdleForTest() {
        return autoRunButton.isEnabled();
    }

    boolean eqtRowsVisibleForTest() {
        return eqtParams.isVisible() && !classicParams.isVisible();
    }

    JComboBox<String> autoMethodComboForTest() {
        return autoMethodCombo;
    }

    void applyAllForTest() {
        applyAllProposals();
    }

    PickEditor pickEditorForTest() {
        return pickEditor;
    }

    PickTablePanel pickTableForTest() {
        return pickTable;
    }

    boolean isManualEditEnabled() {
        return waveformCanvas != null && waveformCanvas.isManualEditEnabled();
    }

    /** Shows the SAC files of {@code dir} in the shared waveform pane (used from Cross-corr). */
    public void previewDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) {
            updateCharts(new SacTimeSeries[0]);
            return;
        }
        File[] sac = SacDirectoryBrowser.listSacFiles(dir);
        if (sac == null || sac.length == 0) {
            updateCharts(new SacTimeSeries[0]);
            if (statusLabel != null) {
                statusLabel.setText("No SAC files in " + dir.getName());
            }
            return;
        }
        List<File> files = new ArrayList<>();
        java.util.Arrays.sort(sac, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        Collections.addAll(files, sac);
        onFilesSelected(files);
    }

    // ------------------------------------------------------------------ layout

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(panel);
        panel.setPreferredSize(new Dimension(SETTINGS_COLUMN_WIDTH, 400));
        panel.setMinimumSize(new Dimension(200, 120));

        functionsHost = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(functionsHost);
        JScrollPane functionsScroll = new JScrollPane(functionsHost);
        functionsScroll.setBorder(null);
        functionsScroll.getViewport().setBackground(AppPanelStyle.getPanelBg());
        functionsScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel aicStack = new JPanel();
        aicStack.setLayout(new BoxLayout(aicStack, BoxLayout.Y_AXIS));
        aicStack.setBackground(AppPanelStyle.getPanelBg());
        JPanel eventPanel = buildEventPanel();
        aicStack.add(eventPanel);
        JPanel aic = buildAicPanel();
        aic.setAlignmentX(Component.LEFT_ALIGNMENT);
        aicStack.add(aic);
        JPanel output = createOutputPanel();
        output.setAlignmentX(Component.LEFT_ALIGNMENT);
        aicStack.add(output);
        JScrollPane aicScroll = new JScrollPane(aicStack);
        aicScroll.setBorder(null);
        aicScroll.getViewport().setBackground(AppPanelStyle.getPanelBg());
        aicScroll.getVerticalScrollBar().setUnitIncrement(16);

        xcorrHost = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(xcorrHost);

        corrTabs = new JTabbedPane();
        corrTabs.setBackground(AppPanelStyle.getPanelBg());
        corrTabs.addTab("Functions", functionsScroll);
        corrTabs.addTab("XCorr", xcorrHost);
        corrTabs.setTabComponentAt(0, InfoPopup.tabLabel("Functions", panel, "Functions", FUNCTIONS_INFO));
        corrTabs.setTabComponentAt(1, InfoPopup.tabLabel("XCorr", panel, "XCorr", XCORR_INFO));

        modeCardLayout = new CardLayout();
        modeCards = new JPanel(modeCardLayout);
        AppPanelStyle.setPanelBackground(modeCards);
        modeCards.add(aicScroll, Mode.ABSOLUTE.name());
        modeCards.add(corrTabs, Mode.CORRELATION.name());


        progressHost = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(progressHost);

        panel.add(modeCards, BorderLayout.CENTER);
        panel.add(progressHost, BorderLayout.SOUTH);
        AppPanelStyle.applyThemeTo(panel);
        return panel;
    }

    /** Current Seismic mode (Absolute or Correlation). */
    public Mode getMode() {
        return mode;
    }

    /**
     * Switches the middle pane between Absolute and Correlation. Correlation is read-only on the
     * waveforms, so manual P/S edit is switched off when entering it.
     */
    public void setMode(Mode next) {
        if (next == null) {
            return;
        }
        mode = next;
        if (modeCardLayout != null) {
            modeCardLayout.show(modeCards, next.name());
        }
        if (next == Mode.CORRELATION) {
            setManualEdit(false);
            if (waveformChrome != null) {
                waveformChrome.setToggle("Edit", false);
            }
        }
    }

    private JPanel createBrowserPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(panel);
        panel.setPreferredSize(new Dimension(240, 400));
        panel.setMinimumSize(new Dimension(160, 120));
        browser = new SacDirectoryBrowser();
        browser.setDirectoryListener(this::onDirectorySelected);
        browser.setFileSelectionListener(this::onFilesSelected);
        panel.add(browser, BorderLayout.CENTER);
        AppPanelStyle.applyThemeTo(panel);
        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(panel);

        waveformCanvas = new WaveformCanvas();
        zoomCanvas = new WaveformZoomCanvas();
        wireCanvas();

        chartPanel = new JPanel(new GridLayout(1, 1));
        AppPanelStyle.setPanelBackground(chartPanel);
        chartPanel.add(waveformCanvas);
        ChartImageExport.installChartPopupMenu(chartPanel, panel,
            () -> waveformCanvas.resetView(), "waveform.png");

        zoomChartPanel = new JPanel(new GridLayout(1, 1));
        AppPanelStyle.setPanelBackground(zoomChartPanel);
        zoomChartPanel.setBorder(AppPanelStyle.createTitledSectionBorder("Zoom View"));
        zoomChartPanel.setPreferredSize(new Dimension(0, 150));
        zoomChartPanel.add(zoomCanvas);
        ChartImageExport.installChartPopupMenu(zoomChartPanel, panel,
            () -> {}, "waveform_zoom.png");

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanel, zoomChartPanel);
        split.setResizeWeight(0.7);
        split.setDividerLocation(0.7);
        split.setOneTouchExpandable(true);
        split.setContinuousLayout(true);
        split.setBackground(AppPanelStyle.getPanelBg());
        panel.add(buildWaveformToolbar(), BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);

        JLabel guide = new JLabel(
            "<html><small>Add folders with <b>+</b>. Click a SAC file, or Ctrl-click several folders, "
                + "to stack traces. Drag to slice time; Pan to move; <b>Edit</b> to click-correct P/S.</small></html>");
        guide.setForeground(AppPanelStyle.getMutedTextColor());

        mousePositionLabel = new JLabel("Mouse Position: (0, 0)");
        // The message line lives in the main window's status bar (see addStatusListener).
        statusLabel = new JLabel("No directory selected");
        statusLabel.addPropertyChangeListener("text", e -> {
            String text = String.valueOf(e.getNewValue());
            for (Consumer<String> l : new ArrayList<>(statusListeners)) {
                l.accept(text);
            }
        });
        JPanel statusRow = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(statusRow);
        statusRow.add(mousePositionLabel, BorderLayout.WEST);

        JPanel south = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(south);
        south.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        south.add(guide, BorderLayout.CENTER);
        south.add(statusRow, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);

        waveformChrome = ViewerSideChrome.wrap(panel,
            ViewerSideChrome.Item.toggle("Pan", Map3DIcons.Kind.PAN,
                "Pan: drag to move the time window", this::setPanMode),
            new ViewerSideChrome.Item("Export", Map3DIcons.Kind.EXPORT,
                "Save or copy the waveform view", buildExportCard()),
            new ViewerSideChrome.Item("Color", Map3DIcons.Kind.RANGE,
                "Trace colours by component role", buildColorCard()),
            ViewerSideChrome.Item.toggle("Edit", Map3DIcons.Kind.EDIT,
                "Manual edit ON/OFF — click to correct P/S", this::setManualEdit),
            ViewerSideChrome.Item.action("Reset", Map3DIcons.Kind.RESET,
                "Reset the time window to the full traces", () -> {
                    if (waveformCanvas != null) {
                        waveformCanvas.resetView();
                    }
                }));
        return waveformChrome;
    }

    private static JButton iconButton(String iconKey, String fallbackText, String tooltip) {
        JButton b = new JButton();
        Icon icon = UIManager.getIcon(iconKey);
        if (icon != null) {
            b.setIcon(icon);
        } else {
            b.setText(fallbackText);
        }
        b.setToolTipText(tooltip);
        AppPanelStyle.stylePrimaryButton(b);
        return b;
    }

    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        AppPanelStyle.setPanelBackground(panel);
        panel.setBorder(AppPanelStyle.createSectionBorder("Export"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, AppPanelStyle.GAP, 2, AppPanelStyle.GAP);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        gbc.gridy = 0;
        panel.add(new JLabel("Output"), gbc);
        outputDirField = new JTextField();
        outputDirField.setEditable(false);
        AppPanelStyle.styleTextField(outputDirField);
        if (outputDir != null && !outputDir.isEmpty() && !outputDir.equals(".")) {
            outputDirField.setText(outputDir);
        }
        JButton outBtn = iconButton("FileView.directoryIcon", "📁", "Directory for .obs, WIN pickfile and .dat exports");
        outBtn.addActionListener(e -> selectOutputDirectory());
        JPanel outRow = new JPanel(new BorderLayout(4, 0));
        outRow.setOpaque(false);
        outRow.add(outBtn, BorderLayout.WEST);
        outRow.add(outputDirField, BorderLayout.CENTER);
        gbc.gridy = 1;
        panel.add(outRow, gbc);

        gbc.gridy = 2;
        panel.add(new JLabel("Stations"), gbc);
        stationFileField = new JTextField();
        stationFileField.setEditable(false);
        stationFileField.setToolTipText(
            "Used to limit pair distance for the differential .dat and for XCorr "
                + "(lines: code lat lon [dep pc sc]).");
        AppPanelStyle.styleTextField(stationFileField);
        JButton stBtn = iconButton("FileView.fileIcon", "📄", "Select station file");
        stBtn.addActionListener(e -> selectStationFile());
        JPanel stRow = new JPanel(new BorderLayout(4, 0));
        stRow.setOpaque(false);
        stRow.add(stBtn, BorderLayout.WEST);
        stRow.add(stationFileField, BorderLayout.CENTER);
        gbc.gridy = 3;
        panel.add(stRow, gbc);

        JButton exportButton = new JButton("Export picks");
        exportButton.setToolTipText(
            "Write .obs, a WIN pickfile, and/or a solver .dat (COR hypocenter + pair lags).");
        exportButton.addActionListener(e -> {
            try {
                exportPicks();
            } catch (RuntimeException ex) {
                logger.log(Level.SEVERE, "Export picks failed: " + ex.getMessage(), ex);
                JOptionPane.showMessageDialog(leftPanel,
                    "Export failed: " + ex.getMessage(), "Export picks", JOptionPane.ERROR_MESSAGE);
            }
        });
        AppPanelStyle.styleSuccessButton(exportButton);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        AppPanelStyle.setPanelBackground(actions);
        actions.add(exportButton);
        gbc.gridy = 4;
        panel.add(actions, gbc);
        return panel;
    }

    private JPanel buildAicPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        AppPanelStyle.setPanelBackground(p);
        p.setBorder(AppPanelStyle.createTitledSectionBorder("Auto-pick"));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        autoMethodCombo = new JComboBox<>(new String[] {"AIC", "Kurtosis", "EQTransformer"});
        AppPanelStyle.styleComboBox(autoMethodCombo);
        autoMethodCombo.setToolTipText("AIC: Maeda (1985). Kurtosis: sliding-window kurtosis, median over "
            + "three window lengths. EQTransformer: deep-learning picker (needs E, N and Z at each station).");
        autoMethodCombo.addActionListener(e -> updateAutoMethodUi());
        JPanel methodRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        AppPanelStyle.setPanelBackground(methodRow);
        methodRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        methodRow.add(new JLabel("Method"));
        methodRow.add(autoMethodCombo);
        p.add(methodRow);

        aicPFracField = compactField("0.67", "P is searched in the first fraction of each trace (default 2/3).");
        aicMinPsField = compactField("0.5", "S search starts this many seconds after the P pick.");
        aicMaxPsField = compactField("20", "S search ends this many seconds after the P pick (keeps the picker off the coda).");
        autoOnsetField = compactField("0.3", "Kurtosis: the pick is where the characteristic function has risen "
            + "this fraction of the way to its peak (0 = earliest, 1 = at the peak).");
        classicParams = new JPanel();
        classicParams.setLayout(new BoxLayout(classicParams, BoxLayout.Y_AXIS));
        AppPanelStyle.setPanelBackground(classicParams);
        classicParams.setAlignmentX(Component.LEFT_ALIGNMENT);
        classicParams.add(leftFieldRow("P fraction", aicPFracField));
        classicParams.add(leftFieldRow("Min P\u2013S (s)", aicMinPsField));
        classicParams.add(leftFieldRow("Max P\u2013S (s)", aicMaxPsField));
        autoOnsetRow = leftFieldRow("Onset level", autoOnsetField);
        autoOnsetRow.setVisible(false);
        classicParams.add(autoOnsetRow);
        p.add(classicParams);

        eqtThrPField = compactField("0.1", "A P peak of the network output must reach this probability.");
        eqtThrSField = compactField("0.1", "A S peak of the network output must reach this probability.");
        eqtMergeCombo = new JComboBox<>(new String[] {"mean", "max"});
        AppPanelStyle.styleComboBox(eqtMergeCombo);
        eqtMergeCombo.setToolTipText("How the 60 s windows (50 % overlap) are combined where they overlap.");
        eqtParams = new JPanel();
        eqtParams.setLayout(new BoxLayout(eqtParams, BoxLayout.Y_AXIS));
        AppPanelStyle.setPanelBackground(eqtParams);
        eqtParams.setAlignmentX(Component.LEFT_ALIGNMENT);
        eqtParams.add(leftFieldRow("P threshold", eqtThrPField));
        eqtParams.add(leftFieldRow("S threshold", eqtThrSField));
        JPanel mergeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        AppPanelStyle.setPanelBackground(mergeRow);
        mergeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        mergeRow.add(new JLabel("Overlap"));
        mergeRow.add(eqtMergeCombo);
        eqtParams.add(mergeRow);
        eqtExportButton = new JButton("All peaks \u2192 Pool");
        eqtExportButton.setToolTipText("Pick every peak above the thresholds in the displayed traces, of any "
            + "length (an event or a whole day), and add them to the pick pool for the Associate step. "
            + "'Run' keeps only the best pick per station and phase.");
        eqtExportButton.setFocusPainted(false);
        eqtExportButton.addActionListener(e -> eqtAllToPool());
        JPanel exportRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        AppPanelStyle.setPanelBackground(exportRow);
        exportRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        exportRow.add(eqtExportButton);
        eqtParams.add(exportRow);
        eqtParams.setVisible(false);
        p.add(eqtParams);

        JButton run = new JButton("Run");
        run.setToolTipText("Auto-pick P (vertical) and S (aligned horizontal envelope) on the displayed traces. "
            + "The result is shown dashed on the waveforms and is not a pick until you apply it.");
        AppPanelStyle.styleSuccessButton(run);
        run.addActionListener(e -> runAutoPick());
        autoRunButton = run;
        applyAllButton = new JButton("Apply all");
        applyAllButton.setToolTipText("Make every proposal a pick (one Undo step)");
        applyAllButton.addActionListener(e -> applyAllProposals());
        discardButton = new JButton("Discard");
        discardButton.setToolTipText("Throw the proposals away; the picks are not changed");
        discardButton.addActionListener(e -> pickTable.discardProposals());
        for (JButton b : new JButton[] {applyAllButton, discardButton}) {
            b.setFocusPainted(false);
            b.setEnabled(false);
        }
        JPanel runRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        AppPanelStyle.setPanelBackground(runRow);
        runRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        runRow.add(run);
        runRow.add(applyAllButton);
        runRow.add(discardButton);
        p.add(runRow);

        onlyEmptyCheck = new JCheckBox("Only fill empty picks");
        onlyEmptyCheck.setToolTipText("Apply all keeps picks you already have and fills only the missing ones");
        onlyEmptyCheck.setOpaque(false);
        onlyEmptyCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(onlyEmptyCheck);

        pickTable = new PickTablePanel(pickEditor);
        pickTable.setAlignmentX(Component.LEFT_ALIGNMENT);
        pickTable.addChangeListener(this::syncProposalsToCanvas);
        pickTable.addChangeListener(this::fireState);
        p.add(pickTable);
        return p;
    }

    private static JPanel leftFieldRow(String label, JTextField field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        AppPanelStyle.setPanelBackground(row);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    private JPanel buildWaveformToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        AppPanelStyle.setPanelBackground(bar);
        bar.setBorder(AppPanelStyle.createTitledSectionBorder("Filter"));

        bar.add(new JLabel("Filter (Hz)"));
        lowFreqField = compactField("", "Empty = no filter on this edge. Low only → high-pass. Updates after typing.");
        bar.add(new JLabel("min"));
        bar.add(lowFreqField);
        highFreqField = compactField("", "Empty = no filter on this edge. High only → low-pass. Updates after typing.");
        bar.add(new JLabel("max"));
        bar.add(highFreqField);

        filterPlotDebounceTimer = new Timer(280, e -> applyFilterToPlotIfValid());
        filterPlotDebounceTimer.setRepeats(false);
        DocumentListener doc = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterPlotDebounceTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterPlotDebounceTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterPlotDebounceTimer.restart();
            }
        };
        lowFreqField.getDocument().addDocumentListener(doc);
        highFreqField.getDocument().addDocumentListener(doc);
        return bar;
    }

    private JPanel buildExportCard() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        AppPanelStyle.setPanelBackground(p);
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        JButton save = new JButton("Save PNG…");
        save.setAlignmentX(Component.LEFT_ALIGNMENT);
        save.addActionListener(e -> ChartImageExport.exportPanelToImage(chartPanel, rightPanel, "waveform.png"));
        JButton copy = new JButton("Copy to clipboard");
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.addActionListener(e -> ChartImageExport.copyPanelToClipboard(chartPanel, rightPanel));
        p.add(save);
        p.add(javax.swing.Box.createVerticalStrut(6));
        p.add(copy);
        return p;
    }

    private JPanel buildColorCard() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        AppPanelStyle.setPanelBackground(p);
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        p.add(colorRow("Vertical (UD/Z)", () -> colorVertical, c -> colorVertical = c));
        p.add(colorRow("H1 (N/Ha)", () -> colorH1, c -> colorH1 = c));
        p.add(colorRow("H2 (E/Hb)", () -> colorH2, c -> colorH2 = c));
        p.add(colorRow("Other", () -> colorOther, c -> colorOther = c));
        return p;
    }

    private JPanel colorRow(String label, java.util.function.Supplier<Color> get, Consumer<Color> set) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        AppPanelStyle.setPanelBackground(row);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton swatch = new JButton(" ");
        swatch.setOpaque(true);
        swatch.setBackground(get.get());
        Dimension d = new Dimension(28, 18);
        swatch.setPreferredSize(d);
        swatch.setMinimumSize(d);
        swatch.addActionListener(e -> {
            Color next = JColorChooser.showDialog(rightPanel, label, get.get());
            if (next != null) {
                set.accept(next);
                swatch.setBackground(next);
                if (currentWaveforms != null && currentWaveforms.length > 0) {
                    updateCharts(currentWaveforms);
                }
            }
        });
        row.add(swatch);
        row.add(new JLabel(label));
        return row;
    }

    private static JTextField compactField(String initial, String tooltip) {
        JTextField f = new JTextField(initial, 6);
        f.setColumns(6);
        Dimension d = f.getPreferredSize();
        f.setPreferredSize(new Dimension(Math.max(56, Math.min(d.width, 68)), d.height));
        f.setMaximumSize(new Dimension(72, d.height));
        if (tooltip != null) {
            f.setToolTipText(tooltip);
        }
        AppPanelStyle.styleTextField(f);
        return f;
    }

    private void setManualEdit(boolean on) {
        if (waveformCanvas != null) {
            waveformCanvas.setManualEditEnabled(on);
        }
        if (on && waveformChrome != null) {
            waveformChrome.setToggle("Pan", false);
        }
        if (statusLabel != null) {
            statusLabel.setText(on
                ? "Manual edit ON — click to correct P/S"
                : "Manual edit OFF");
        }
    }

    private void setPanMode(boolean on) {
        if (waveformCanvas != null) {
            waveformCanvas.setPanMode(on);
        }
        if (on && waveformChrome != null) {
            waveformChrome.setToggle("Edit", false);
        }
        if (statusLabel != null) {
            statusLabel.setText(on ? "Pan ON — drag to move time" : "Pan OFF — drag to slice time");
        }
    }

    private void applyManualEditMode() {
        setManualEdit(waveformCanvas != null && waveformCanvas.isManualEditEnabled());
    }

    private void wireCanvas() {
        waveformCanvas.setHoverListener(new WaveformCanvas.HoverListener() {
            @Override
            public void onHover(int seriesIndex, int sampleIndex) {
                updateZoomFromHover(seriesIndex, sampleIndex);
                if (seriesIndex >= 0 && seriesIndex < waveformCanvas.getTraces().size()) {
                    WaveformCanvas.Trace t = waveformCanvas.getTraces().get(seriesIndex);
                    Date d = new Date(t.sampleMillis(sampleIndex));
                    mousePositionLabel.setText("Mouse Position: " + t.label + "  "
                        + new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(d));
                }
            }

            @Override
            public void onLeave() {
                zoomCanvas.clear();
            }
        });
        waveformCanvas.setPickListener((seriesIndex, sampleIndex, phase, time) -> {
            if (seriesIndex < 0 || seriesIndex >= waveformCanvas.getTraces().size()) {
                return;
            }
            WaveformCanvas.Trace t = waveformCanvas.getTraces().get(seriesIndex);
            pickEditor.set(t.station, t.component, phase, time);
            if (statusLabel != null) {
                statusLabel.setText("Picked " + phase + " at " + t.station);
            }
        });
        waveformCanvas.setPhaseResolver((e, keyP, keyS) -> resolvePhase(e, keyP, keyS));
        int menuMask = java.awt.GraphicsEnvironment.isHeadless()
            ? java.awt.event.InputEvent.CTRL_DOWN_MASK
            : java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        waveformCanvas.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, menuMask), "pickUndo");
        waveformCanvas.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, menuMask), "pickRedo");
        waveformCanvas.getActionMap().put("pickUndo", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                pickEditor.undo();
            }
        });
        waveformCanvas.getActionMap().put("pickRedo", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                pickEditor.redo();
            }
        });
        applyPickSettingsToCanvas();
        applyManualEditMode();
    }

    private String resolvePhase(java.awt.event.MouseEvent e, boolean keyP, boolean keyS) {
        AppSettings settings = AppSettingsCache.snapshot();
        int buttonP = mouseButtonFromSetting(settings.getPickingMouseP());
        int buttonS = mouseButtonFromSetting(settings.getPickingMouseS());
        int buttonContext = mouseButtonFromSetting(settings.getPickingMouseContext());
        int keyCodeP = keyCodeFromSetting(settings.getPickingKeyP());
        int keyCodeS = keyCodeFromSetting(settings.getPickingKeyS());
        if (keyCodeP != 0 && keyP) {
            return "P";
        }
        if (keyCodeS != 0 && keyS) {
            return "S";
        }
        int button = e.getButton();
        if (button == buttonContext) {
            return null;
        }
        if (button == buttonP) {
            return "P";
        }
        if (button == buttonS) {
            return "S";
        }
        return null;
    }

    private void applyPickSettingsToCanvas() {
        if (waveformCanvas == null) {
            return;
        }
        AppSettings settings = AppSettingsCache.snapshot();
        waveformCanvas.setPickButtons(
            mouseButtonFromSetting(settings.getPickingMouseP()),
            mouseButtonFromSetting(settings.getPickingMouseS()),
            mouseButtonFromSetting(settings.getPickingMouseContext()));
        waveformCanvas.setPickKeys(
            keyCodeFromSetting(settings.getPickingKeyP()),
            keyCodeFromSetting(settings.getPickingKeyS()));
    }

    private void runAutoPick() {
        if (currentWaveforms == null || currentWaveforms.length == 0) {
            JOptionPane.showMessageDialog(leftPanel, "Load waveforms first.", "Auto-pick",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (isEqtSelected()) {
            runEqt();
            return;
        }
        double pFrac;
        double minPs;
        double maxPs;
        double onset = 0.3;
        try {
            pFrac = Double.parseDouble(aicPFracField.getText().trim());
            minPs = Double.parseDouble(aicMinPsField.getText().trim());
            maxPs = Double.parseDouble(aicMaxPsField.getText().trim());
            if (isKurtosisSelected()) {
                onset = Double.parseDouble(autoOnsetField.getText().trim());
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(leftPanel, "Invalid auto-pick parameter.", "Auto-pick",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        AutoPicker picker;
        if (isKurtosisSelected()) {
            KurtosisPicker.Params kp = new KurtosisPicker.Params();
            kp.pSearchFraction = pFrac;
            kp.minPsSec = minPs;
            kp.maxPsSec = maxPs;
            kp.onsetLevel = onset;
            picker = AutoPicker.kurtosis(kp);
        } else {
            AicPicker.Params ap = new AicPicker.Params();
            ap.pSearchFraction = pFrac;
            ap.minPsSec = minPs;
            ap.maxPsSec = maxPs;
            picker = AutoPicker.aic(ap);
        }
        List<PickProposal> proposals = picker.propose(tracesForAic());
        pickTable.setProposals(proposals);
        statusLabel.setText(picker.name() + " proposed " + proposals.size()
            + " pick(s). Apply to accept them, or Discard.");
    }

    private boolean isEqtSelected() {
        return autoMethodCombo != null && "EQTransformer".equals(autoMethodCombo.getSelectedItem());
    }

    private void updateAutoMethodUi() {
        boolean eqt = isEqtSelected();
        if (classicParams != null) {
            classicParams.setVisible(!eqt);
        }
        if (eqtParams != null) {
            eqtParams.setVisible(eqt);
        }
        if (autoOnsetRow != null) {
            autoOnsetRow.setVisible(isKurtosisSelected());
        }
    }

    /** Settings of the EQTransformer rows, or {@code null} after showing an error. */
    private EqtPicker.Params readEqtParams() {
        EqtPicker.Params p = new EqtPicker.Params();
        try {
            p.thresholdP = Double.parseDouble(eqtThrPField.getText().trim());
            p.thresholdS = Double.parseDouble(eqtThrSField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(leftPanel, "Invalid threshold.", "EQTransformer",
                JOptionPane.ERROR_MESSAGE);
            return null;
        }
        p.mergeMax = "max".equals(eqtMergeCombo.getSelectedItem());
        return p;
    }

    private void setEqtBusy(boolean busy, String message) {
        autoRunButton.setEnabled(!busy);
        eqtExportButton.setEnabled(!busy);
        autoMethodCombo.setEnabled(!busy);
        if (message != null) {
            statusLabel.setText(message);
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getMessage() == null ? c.toString() : c.getMessage();
    }

    private void runEqt() {
        EqtPicker.Params params = readEqtParams();
        if (params == null) {
            return;
        }
        SacTimeSeries[] traces = tracesForAic();
        setEqtBusy(true, "EQTransformer running (the first run loads the model, which takes a few seconds)...");
        new javax.swing.SwingWorker<List<PickProposal>, Void>() {
            @Override
            protected List<PickProposal> doInBackground() {
                return AutoPicker.eqt(params).propose(traces);
            }

            @Override
            protected void done() {
                try {
                    List<PickProposal> proposals = get();
                    pickTable.setProposals(proposals);
                    setEqtBusy(false, "EQTransformer proposed " + proposals.size()
                        + " pick(s). Apply to accept them, or Discard.");
                } catch (Exception ex) {
                    setEqtBusy(false, "EQTransformer failed.");
                    JOptionPane.showMessageDialog(leftPanel, rootMessage(ex), "EQTransformer",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void eqtAllToPool() {
        if (currentWaveforms == null || currentWaveforms.length == 0) {
            JOptionPane.showMessageDialog(leftPanel, "Load waveforms first.", "EQTransformer",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        EqtPicker.Params params = readEqtParams();
        if (params == null) {
            return;
        }
        SacTimeSeries[] traces = tracesForAic();
        setEqtBusy(true, "EQTransformer picking all peaks...");
        new javax.swing.SwingWorker<String, Double>() {
            @Override
            protected String doInBackground() {
                try (EqtPicker picker = new EqtPicker(params)) {
                    List<EqtPicker.Pick> picks = picker.pickAll(traces, f -> publish(f));
                    int added = pickPool.addAll(picks);
                    long nP = picks.stream().filter(x -> "P".equals(x.phase())).count();
                    String msg = picks.size() + " pick(s) (P " + nP + ", S " + (picks.size() - nP) + "), " + added
                        + " new in the pool (" + pickPool.size() + " in total).";
                    return picker.skipped().isEmpty() ? msg
                        : msg + " Skipped: " + String.join("; ", picker.skipped());
                }
            }

            @Override
            protected void process(List<Double> chunks) {
                statusLabel.setText(String.format(Locale.ROOT, "EQTransformer picking... %.0f %%",
                    100 * chunks.get(chunks.size() - 1)));
            }

            @Override
            protected void done() {
                try {
                    setEqtBusy(false, get());
                } catch (Exception ex) {
                    setEqtBusy(false, "EQTransformer failed.");
                    JOptionPane.showMessageDialog(leftPanel, rootMessage(ex), "EQTransformer",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private boolean isKurtosisSelected() {
        return autoMethodCombo != null && "Kurtosis".equals(autoMethodCombo.getSelectedItem());
    }

    private void applyAllProposals() {
        int n = pickTable.applyAll(onlyEmptyCheck.isSelected());
        statusLabel.setText("Applied " + n + " pick(s). Undo reverts them in one step.");
    }

    private void syncProposalsToCanvas() {
        if (waveformCanvas != null && pickTable != null) {
            waveformCanvas.setProposals(pickTable.proposalTimes("P"), pickTable.proposalTimes("S"));
        }
        boolean any = pickTable != null && !pickTable.proposals().isEmpty();
        if (applyAllButton != null) {
            applyAllButton.setEnabled(any);
            discardButton.setEnabled(any);
        }
    }

    private SacTimeSeries[] tracesForAic() {
        if (crossCorr == null || currentWaveforms == null) {
            return currentWaveforms;
        }
        List<SacTimeSeries> keep = new ArrayList<>();
        for (SacTimeSeries wf : currentWaveforms) {
            if (wf == null) {
                continue;
            }
            String station = wf.getHeader().getKstnm() == null ? "" : wf.getHeader().getKstnm().trim().replace(" ", "");
            String component = wf.getHeader().getKcmpnm() == null ? "" : wf.getHeader().getKcmpnm().trim();
            if (crossCorr.isComponentActive(station, component)) {
                keep.add(wf);
            }
        }
        return keep.isEmpty() ? currentWaveforms : keep.toArray(new SacTimeSeries[0]);
    }

    private void refreshOverlays() {
        if (waveformCanvas != null) {
            waveformCanvas.setShowCf(false);
            waveformCanvas.setShowEnvelope(false);
        }
        if (zoomCanvas != null) {
            zoomCanvas.setShowFlags(true, false, false);
        }
    }

    private void syncPicksToCanvas() {
        Map<String, Date> p = new java.util.HashMap<>();
        Map<String, Date> s = new java.util.HashMap<>();
        for (ArrivalTime a : arrivalTimeManager.getArrivalTimeMap().values()) {
            if ("P".equals(a.getPhaseDescriptor())) {
                p.put(a.getStationName(), a.getArrivalTime());
            } else if ("S".equals(a.getPhaseDescriptor())) {
                s.put(a.getStationName(), a.getArrivalTime());
            }
        }
        if (waveformCanvas != null) {
            waveformCanvas.setPicks(p, s);
        }
        if (zoomCanvas != null && waveformCanvas != null
            && waveformCanvas.getHoverSeries() >= 0
            && waveformCanvas.getHoverSeries() < waveformCanvas.getTraces().size()) {
            updateZoomFromHover(waveformCanvas.getHoverSeries(), waveformCanvas.getHoverSample());
        }
    }

    private void updateZoomFromHover(int seriesIndex, int sampleIndex) {
        if (zoomCanvas == null || waveformCanvas == null) {
            return;
        }
        List<WaveformCanvas.Trace> traces = waveformCanvas.getTraces();
        if (seriesIndex < 0 || seriesIndex >= traces.size()) {
            zoomCanvas.clear();
            return;
        }
        WaveformCanvas.Trace t = traces.get(seriesIndex);
        ArrivalTime ap = arrivalTimeManager.getArrivalTime(t.station, "P");
        ArrivalTime as = arrivalTimeManager.getArrivalTime(t.station, "S");
        zoomCanvas.setView(t, sampleIndex, AppSettingsCache.snapshot().getZoomWindowSeconds(),
            ap != null ? ap.getArrivalTime() : null,
            as != null ? as.getArrivalTime() : null);
    }

    // ------------------------------------------------------------------ selection / loading

    private void selectStationFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (stationFilePath != null && !stationFilePath.isEmpty()) {
            File cur = new File(stationFilePath);
            if (cur.getParentFile() != null && cur.getParentFile().isDirectory()) {
                chooser.setCurrentDirectory(cur.getParentFile());
            }
        }
        if (chooser.showOpenDialog(leftPanel) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            stationFilePath = f.getAbsolutePath();
            stationFileField.setText(stationFilePath);
            statusLabel.setText("Station file: " + f.getName());
        }
    }

    private void selectOutputDirectory() {
        File currentDir = null;
        if (outputDir != null && !outputDir.isEmpty() && !outputDir.equals(".")) {
            File cur = new File(outputDir);
            if (cur.exists() && cur.isDirectory()) {
                currentDir = cur;
            }
        }
        File dir = DirectoryChooserHelper.selectDirectory(leftPanel, "Select Output Directory", currentDir);
        if (dir != null) {
            outputDir = dir.getAbsolutePath();
            outputDirField.setText(outputDir);
            statusLabel.setText("Output directory: " + outputDir);
        }
    }

    private void onDirectorySelected(File dir) {
        selectedDir = dir;
        if (pendingEvent == null) {
            linkEventOfFolder(dir);
        }
        if (pickTable != null) {
            pickTable.discardProposals();
        }
        File obs = new File(outputDir, dir.getName() + ".obs");
        if (obs.exists()) {
            pickEditor.loadObs(obs.getAbsolutePath());
            statusLabel.setText("Loaded existing picks from " + obs.getName());
        } else {
            pickEditor.reset();
        }
        int n = browser.getSacFiles().size();
        statusLabel.setText(n == 0
            ? "No SAC files in " + dir.getName()
            : dir.getName() + " — " + n + " SAC file(s). Click a file or Ctrl-click folders to plot.");
        if (crossCorr != null) {
            crossCorr.setWorkingDirectory(dir);
        }
    }

    private void onFilesSelected(List<File> files) {
        plottedFiles = files == null ? List.of() : new ArrayList<>(files);
        if (crossCorr != null) {
            crossCorr.setSelectedSacFiles(plottedFiles);
        }
        if (plottedFiles.isEmpty()) {
            return;
        }
        ViewerStatusBar.startLoading("Loading SAC...");
        try {
            statusLabel.setText("Loading...");
            SacTimeSeries[] waveforms = readWaveforms(files);
            updateCharts(waveforms);
            statusLabel.setText("Loaded " + waveforms.length + " waveform(s), "
                + FrequencySpec.describe(displayFilterFreqMin, displayFilterFreqMax));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error loading waveforms: " + ex.getMessage(), ex);
            JOptionPane.showMessageDialog(leftPanel,
                "Error loading waveforms: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("Error loading waveforms");
        } finally {
            ViewerStatusBar.stopLoading();
        }
    }

    private SacTimeSeries[] readWaveforms(List<File> files) throws Exception {
        SacTimeSeries[] waveforms = new SacTimeSeries[files.size()];
        for (int i = 0; i < waveforms.length; i++) {
            waveforms[i] = WaveProcessor.read(files.get(i).getAbsolutePath());
            WaveProcessor.applyOptionalFrequencyFilter(waveforms[i], displayFilterFreqMin, displayFilterFreqMax);
        }
        return waveforms;
    }

    /** Applies the frequency fields to the plotted traces when they parse; invalid partial input is ignored. */
    private void applyFilterToPlotIfValid() {
        FrequencySpec.Result fr = FrequencySpec.parse(lowFreqField.getText(), highFreqField.getText());
        if (fr.error != null) {
            return;
        }
        displayFilterFreqMin = fr.min;
        displayFilterFreqMax = fr.max;
        if (plottedFiles.isEmpty()) {
            statusLabel.setText("Filter: " + FrequencySpec.describe(fr.min, fr.max) + " (select files to preview)");
            return;
        }
        try {
            statusLabel.setText("Loading...");
            SacTimeSeries[] waveforms = readWaveforms(plottedFiles);
            updateCharts(waveforms);
            statusLabel.setText("Filter (" + FrequencySpec.describe(fr.min, fr.max) + ") — "
                + waveforms.length + " waveform(s)");
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error updating filter: " + ex.getMessage(), ex);
            JOptionPane.showMessageDialog(leftPanel,
                "Error loading waveforms: " + ex.getMessage(),
                "Filter", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------ export

    private static String baseName(Date d) {
        LocalDateTime t = d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            .withSecond(0).withNano(0);
        return BASE_NAME_FORMAT.format(t);
    }

    /**
     * Export dialog: {@code .obs} and/or the differential {@code .dat} (P and S pairs, phase column).
     */
    private void exportPicks() {
        if (outputDir == null || outputDir.isEmpty()) {
            JOptionPane.showMessageDialog(leftPanel, "Please select an output directory first",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (selectedDir == null) {
            JOptionPane.showMessageDialog(leftPanel, "No directory selected",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Date earliestP = arrivalTimeManager.earliestTime("P");
        Date earliestS = arrivalTimeManager.earliestTime("S");
        if (earliestP == null && earliestS == null) {
            JOptionPane.showMessageDialog(leftPanel, "No picks to export. Pick P and/or S first.",
                "Export picks", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int nP = arrivalTimeManager.countStations("P");
        int nS = arrivalTimeManager.countStations("S");

        JCheckBox obsBox = new JCheckBox("Phase picks (.obs)", earliestP != null);
        obsBox.setEnabled(earliestP != null);
        obsBox.setToolTipText("Requires at least one P pick.");
        JCheckBox pickBox = new JCheckBox("WIN pickfile (#p / #s)", true);
        pickBox.setToolTipText("win pickfile: raw picks (#p) and hypomh input (#s).");
        JCheckBox datBox = new JCheckBox("Solver input (.dat: COR + P/S pair lags)",
            nS >= 2 || nP >= 2);
        JTextField maxDistField = new JTextField("", 8);
        maxDistField.setToolTipText("Blank = all pairs. Requires a Station file with coordinates.");

        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        p.add(obsBox, g);
        g.gridy = 1;
        p.add(pickBox, g);
        g.gridy = 2;
        p.add(datBox, g);
        g.gridy = 3; g.gridwidth = 1;
        p.add(new JLabel("   Max pair distance (km):"), g);
        g.gridx = 1;
        p.add(maxDistField, g);
        g.gridx = 0; g.gridy = 4; g.gridwidth = 2;
        p.add(new JLabel("<html><small>Each row is stnk stnl lagt coef P|S. "
            + "lagt = t(stnl) − t(stnk).<br>Picks: P at " + nP + " station(s), S at " + nS
            + " station(s).</small></html>"), g);

        int answer = JOptionPane.showConfirmDialog(leftPanel, p, "Export picks",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        boolean wantObs = obsBox.isSelected();
        boolean wantPick = pickBox.isSelected();
        boolean wantDat = datBox.isSelected();
        if (!wantObs && !wantPick && !wantDat) {
            return;
        }

        String base = baseName(earliestP != null ? earliestP : earliestS);
        StringBuilder msg = new StringBuilder();

        if (wantObs) {
            File obsFile = new File(outputDir, base + ".obs");
            arrivalTimeManager.outputToObs(obsFile.getPath());
            msg.append(".obs → ").append(obsFile.getPath()).append('\n');
        }

        if (wantPick) {
            Map<String, CrossCorrelationPickingService.StationCoord> pickCoords = Collections.emptyMap();
            if (stationFilePath != null && !stationFilePath.isEmpty()) {
                try {
                    pickCoords = CrossCorrelationPickingService.loadCoordsForDistanceFilter(Paths.get(stationFilePath));
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Station file for pickfile: " + stationFilePath, ex);
                }
            }
            java.time.LocalDateTime waveStart = null;
            if (currentWaveforms != null && currentWaveforms.length > 0 && currentWaveforms[0] != null) {
                waveStart = WaveProcessor.getStarttime(currentWaveforms[0]);
            }
            String waveName = selectedDir != null ? selectedDir.getName() : base;
            File pickFile = new File(outputDir, WinPickfileWriter.fileStem(
                arrivalTimeManager.getArrivalTimeMap().values()));
            try {
                WinPickfileWriter.write(pickFile.toPath(), arrivalTimeManager.getArrivalTimeMap().values(),
                    waveName, ".", waveStart, pickCoords);
                msg.append("pickfile → ").append(pickFile.getPath()).append('\n');
            } catch (IOException ex) {
                logger.log(Level.WARNING, "WIN pickfile export failed: " + pickFile, ex);
                JOptionPane.showMessageDialog(leftPanel,
                    "Cannot write pickfile " + pickFile.getPath() + ":\n" + ex.getMessage(),
                    "Export picks", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        if (wantDat) {
            double maxDist = 0;
            String maxRaw = maxDistField.getText().trim();
            if (!maxRaw.isEmpty()) {
                try {
                    maxDist = Double.parseDouble(maxRaw);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(leftPanel, "Invalid max pair distance: " + maxRaw,
                        "Export picks", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            Map<String, CrossCorrelationPickingService.StationCoord> coords = Collections.emptyMap();
            if (maxDist > 0) {
                if (stationFilePath == null || stationFilePath.isEmpty()) {
                    JOptionPane.showMessageDialog(leftPanel,
                        "Select a station file (Input) for the pair-distance limit, or clear the limit.",
                        "Export picks", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                try {
                    coords = CrossCorrelationPickingService.loadCoordsForDistanceFilter(Paths.get(stationFilePath));
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Cannot read station file: " + stationFilePath, ex);
                    JOptionPane.showMessageDialog(leftPanel, "Cannot read station file: " + ex.getMessage(),
                        "Export picks", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (coords.isEmpty()) {
                    JOptionPane.showMessageDialog(leftPanel,
                        "The station file has no coordinates (code lat lon …).",
                        "Export picks", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            File datFile = new File(outputDir, base + ".dat");
            int rows;
            try {
                rows = DifferentialDatWriter.writeFromPicksBoth(datFile.toPath(),
                    arrivalTimeManager.getArrivalTimeMap().values(), coords, maxDist);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Differential .dat export failed: " + datFile, ex);
                JOptionPane.showMessageDialog(leftPanel,
                    "Cannot write " + datFile.getPath() + ":\n" + ex.getMessage(),
                    "Export picks", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (rows > 0) {
                msg.append(".dat (P+S pairs, ").append(rows).append(" rows) → ")
                    .append(datFile.getPath());
            } else {
                msg.append(".dat skipped (need P or S picks at ≥2 stations within the distance limit).");
            }
        }

        statusLabel.setText("Exported: " + base);
        JOptionPane.showMessageDialog(leftPanel, msg.toString().trim(), "Export picks",
            JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------ settings helpers

    private static int mouseButtonFromSetting(String s) {
        if (s == null) return MouseEvent.BUTTON1;
        switch (s.trim().toLowerCase()) {
            case "right": return MouseEvent.BUTTON3;
            case "middle": return MouseEvent.BUTTON2;
            default: return MouseEvent.BUTTON1;
        }
    }

    private static int keyCodeFromSetting(String s) {
        if (s == null || "none".equalsIgnoreCase(s.trim())) return 0;
        switch (s.trim().toUpperCase()) {
            case "P": return KeyEvent.VK_P;
            case "S": return KeyEvent.VK_S;
            case "1": return KeyEvent.VK_1;
            case "2": return KeyEvent.VK_2;
            default: return 0;
        }
    }

    // ------------------------------------------------------------------ waveform display

    private void updateCharts(SacTimeSeries[] waveforms) {
        currentWaveforms = waveforms == null ? new SacTimeSeries[0] : waveforms;
        if (waveformCanvas == null) {
            return;
        }
        applyPickSettingsToCanvas();

        if (currentWaveforms.length == 0) {
            currentFileNames = new String[0];
            waveformCanvas.setTraces(java.util.Collections.emptyList());
            if (zoomCanvas != null) {
                zoomCanvas.clear();
            }
            return;
        }

        List<WaveformCanvas.Trace> traces = new ArrayList<>();
        String[] fileNames = new String[currentWaveforms.length];
        for (int i = 0; i < currentWaveforms.length; i++) {
            SacTimeSeries wf = currentWaveforms[i];
            String station = wf.getHeader().getKstnm().trim().replace(" ", "");
            String component = wf.getHeader().getKcmpnm().trim();
            Color color = colorForComponent(component);
            if (i < plottedFiles.size() && plottedFiles.get(i) != null
                && plottedFiles.get(i).getParentFile() != null) {
                component = plottedFiles.get(i).getParentFile().getName() + "/" + component;
            }
            fileNames[i] = (station + "__" + component);
            long startMillis = WaveProcessor.getStarttime(wf)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            traces.add(new WaveformCanvas.Trace(station, component, color, startMillis,
                wf.getHeader().getDelta(), wf.getY()));
        }
        currentFileNames = fileNames;
        waveformCanvas.setTraces(traces);
        syncPicksToCanvas();
        refreshOverlays();
    }

    private Color colorForComponent(String component) {
        SacComponentClassifier.Role role = SacComponentClassifier.classify(component);
        if (role == SacComponentClassifier.Role.VERTICAL) {
            return colorVertical;
        }
        if (role == SacComponentClassifier.Role.H1) {
            return colorH1;
        }
        if (role == SacComponentClassifier.Role.H2) {
            return colorH2;
        }
        return colorOther;
    }
}
