/*
 * Decompiled with CFR 0.152.
 */
package com.treloc.xtreloc.app.gui;

import com.treloc.xtreloc.app.gui.controller.MapController;
import com.treloc.xtreloc.app.gui.controller.WorkingRelocationController;
import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.app.gui.service.AssociationModel;
import com.treloc.xtreloc.app.gui.service.RealResultWriter;
import com.treloc.xtreloc.app.gui.service.UpdateChecker;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.AppSettings;
import com.treloc.xtreloc.app.gui.util.AppSettingsCache;
import com.treloc.xtreloc.app.gui.util.BundledImageLoader;
import com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings;
import com.treloc.xtreloc.app.gui.util.ChartImageExport;
import com.treloc.xtreloc.app.gui.util.ChartPlotOptions;
import com.treloc.xtreloc.app.gui.util.ColorScaleUtils;
import com.treloc.xtreloc.app.gui.util.GuiExecutionLog;
import com.treloc.xtreloc.app.gui.util.LogHistoryManager;
import com.treloc.xtreloc.app.gui.util.MainWindowSizePresets;
import com.treloc.xtreloc.app.gui.util.UiFonts;
import com.treloc.xtreloc.app.gui.util.UpdateInfo;
import com.treloc.xtreloc.app.gui.util.WorkingLayer;
import com.treloc.xtreloc.app.gui.view.AbsoluteExportPanel;
import com.treloc.xtreloc.app.gui.view.AppMenuBar;
import com.treloc.xtreloc.app.gui.view.AssociatePanel;
import com.treloc.xtreloc.app.gui.view.AssociationResultPanel;
import com.treloc.xtreloc.app.gui.view.CatalogTablePanel;
import com.treloc.xtreloc.app.gui.view.ChartCatalogColorCard;
import com.treloc.xtreloc.app.gui.view.HypocenterLocationPanel;
import com.treloc.xtreloc.app.gui.view.MainCommands;
import com.treloc.xtreloc.app.gui.view.MainStatusBar;
import com.treloc.xtreloc.app.gui.view.Map2DPanel;
import com.treloc.xtreloc.app.gui.view.MapView;
import com.treloc.xtreloc.app.gui.view.ModelSetupPanel;
import com.treloc.xtreloc.app.gui.view.NavigationPanel;
import com.treloc.xtreloc.app.gui.view.PageToolBar;
import com.treloc.xtreloc.app.gui.view.PickViewerPanel;
import com.treloc.xtreloc.app.gui.view.ReportPanel;
import com.treloc.xtreloc.app.gui.view.SacWorkbenchPanel;
import com.treloc.xtreloc.app.gui.view.SettingsPanel;
import com.treloc.xtreloc.app.gui.view.TraceProcessingPanel;
import com.treloc.xtreloc.app.gui.view.UpdateDialog;
import com.treloc.xtreloc.app.gui.view.VelocityViewerPanel;
import com.treloc.xtreloc.app.gui.view.ViewerSideChrome;
import com.treloc.xtreloc.app.gui.view.ViewerStatusBar;
import com.treloc.xtreloc.app.gui.volume3d.Map3DIcons;
import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.ConfigLoader;
import com.treloc.xtreloc.util.LogInitializer;
import com.treloc.xtreloc.util.TimeFormatConverter;
import com.treloc.xtreloc.util.VersionInfo;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

public class XTreLocGUI {
    private static final Logger logger = Logger.getLogger(XTreLocGUI.class.getName());
    private static final int REPAINT_DEBOUNCE_MS = 100;
    private static final int NAVIGATOR_WIDTH = 170;

    /**
     * Debounces repaint for one or more panels (at most once per {@link #REPAINT_DEBOUNCE_MS}).
     */
    private static Runnable debouncedRepaint(JPanel... panels) {
        if (panels == null || panels.length == 0) {
            throw new IllegalArgumentException("panels");
        }
        final long[] lastTime = { 0 };
        final javax.swing.Timer[] timerRef = { null };
        return () -> {
            long now = System.currentTimeMillis();
            if (now - lastTime[0] >= REPAINT_DEBOUNCE_MS) {
                lastTime[0] = now;
                for (JPanel p : panels) {
                    p.repaint();
                }
                return;
            }
            if (timerRef[0] == null || !timerRef[0].isRunning()) {
                timerRef[0] = new javax.swing.Timer(REPAINT_DEBOUNCE_MS, e -> {
                    lastTime[0] = System.currentTimeMillis();
                    for (JPanel p : panels) {
                        p.repaint();
                    }
                });
                timerRef[0].setRepeats(false);
                timerRef[0].start();
            }
        };
    }

    public static void main(String[] args) throws Exception {
        AppSettings appSettings = null;
        try {
            appSettings = AppSettings.load();
        }
        catch (Exception e) {
            logger.log(Level.FINE, "Could not load settings, using defaults: " + e.getMessage());
        }
        try {
            File logFile = LogHistoryManager.getLogFile();
            if (appSettings != null) {
                LogInitializer.setup(logFile.getAbsolutePath(), Level.INFO, appSettings.getLogLimitBytes(), appSettings.getLogCount());
            } else {
                LogInitializer.setup(logFile.getAbsolutePath(), Level.INFO);
            }
            Logger logger = Logger.getLogger("com.treloc.xtreloc");
            logger.info("========================================");
            logger.info("xTreLoc application started");
            logger.info("Log file: " + logFile.getAbsolutePath());
            logger.info("========================================");
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize logging: " + e.getMessage(), e);
        }
        AppSettings appSettingsForUI = appSettings != null ? appSettings : AppSettings.load();
        String theme = appSettingsForUI.getTheme();
        if (theme != null) {
            XTreLocGUI.applyTheme(theme);
        }
        SwingUtilities.invokeLater(() -> {
            try {
                String oneLine;
                String version = VersionInfo.getVersion();
                final JFrame mainFrame = new JFrame("xTreLoc " + version);
                mainFrame.setDefaultCloseOperation(0);
                mainFrame.setLayout(new BorderLayout());
                try {
                    Image logoImg = BundledImageLoader.loadImage("logo.png");
                    if (logoImg != null) {
                        mainFrame.setIconImage(logoImg);
                    }
                }
                catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to load logo: " + e.getMessage(), e);
                }
                MapView view = new MapView();
                MapController ctrl = new MapController(view);
                XTreLocGUI.applyAppSettings(appSettingsForUI, mainFrame, view);
                XTreLocGUI.checkForUpdatesAsync(appSettingsForUI, mainFrame);
                AppConfig config = null;
                Exception startupConfigLoadFailure = null;
                try {
                    ConfigLoader loader = new ConfigLoader("config.json");
                    config = loader.getConfig();
                }
                catch (Exception e) {
                    startupConfigLoadFailure = e;
                    logger.log(Level.FINE, "Startup config.json not loaded (GUI continues without default file).", e);
                    oneLine = e.getMessage() != null ? e.getMessage().replace('\n', ' ').trim() : e.toString();
                    logger.log(Level.INFO, "Startup: could not load config.json \u2014 " + oneLine);
                }
                final HypocenterLocationPanel locationPanel = new HypocenterLocationPanel(view);
                GuiExecutionLog.setSink(locationPanel::appendExecutionLog);
                if (startupConfigLoadFailure != null) {
                    oneLine = startupConfigLoadFailure.getMessage() != null ? startupConfigLoadFailure.getMessage().replace('\n', ' ').trim() : startupConfigLoadFailure.toString();
                    GuiExecutionLog.warning("Startup: default config.json was not loaded. " + oneLine);
                }
                if (config != null) {
                    locationPanel.setConfig(config);
                }
                JComponent solverLeftPanel = locationPanel.getLeftPanel();
                ReportPanel reportPanel = new ReportPanel(view);
                final TraceProcessingPanel traceProcessingPanel = new TraceProcessingPanel();
                PickViewerPanel pickViewerPanel = new PickViewerPanel();
                SacWorkbenchPanel sacWorkbenchPanel = new SacWorkbenchPanel(pickViewerPanel, traceProcessingPanel, false);
                AssociationModel associationModel = new AssociationModel();
                AssociatePanel associatePanel = new AssociatePanel(pickViewerPanel.pickPool(), associationModel, Preferences.userNodeForPackage(AssociatePanel.class));
                AssociationResultPanel resultPanel = new AssociationResultPanel(associationModel);
                AbsoluteExportPanel exportPanel = new AbsoluteExportPanel(pickViewerPanel.pickPool(), associationModel);
                VelocityViewerPanel velocityViewerPanel = new VelocityViewerPanel(view);
                ModelSetupPanel modelSetupPanel = new ModelSetupPanel(view, ctrl, velocityViewerPanel);
                CatalogTablePanel catalogPanel = modelSetupPanel.getCatalogPanel();
                locationPanel.setSolverResultCatalogPanel(catalogPanel);
                catalogPanel.addCatalogLoadListener(hypocenters -> reportPanel.setHypocenters(hypocenters));
                modelSetupPanel.insertSolverTab(solverLeftPanel);
                Map2DPanel map2dPanel = new Map2DPanel(view, velocityViewerPanel);
                velocityViewerPanel.addVolumeListener(map2dPanel::syncVolume);
                JTabbedPane viewerRightTabs = new JTabbedPane();
                viewerRightTabs.setBackground(AppPanelStyle.getPanelBg());
                viewerRightTabs.setForeground(AppPanelStyle.getContentTextColor());
                viewerRightTabs.setMinimumSize(new Dimension(0, 0));
                viewerRightTabs.addTab("2D map", map2dPanel.getComponent());
                viewerRightTabs.addTab("3D map", velocityViewerPanel.getComponent());
                viewerRightTabs.addChangeListener(ev -> {
                    Component sel = viewerRightTabs.getSelectedComponent();
                    if (sel == velocityViewerPanel.getComponent() || sel == map2dPanel.getComponent()) {
                        velocityViewerPanel.ensureLoaded();
                    }
                });
                JPanel histogramPanel = XTreLocGUI.createHistogramPanel(catalogPanel, view);
                viewerRightTabs.addTab("Hist", histogramPanel);
                JPanel scatterPanel = XTreLocGUI.createScatterPanel(catalogPanel, view);
                viewerRightTabs.addTab("Scatter", scatterPanel);
                JPanel solverLogPanel = locationPanel.getLogPanel();
                JPanel solverConvergencePanel = locationPanel.getConvergenceDockPanel();
                JComponent residualChrome = XTreLocGUI.wrapResidualChrome(solverConvergencePanel, locationPanel);
                viewerRightTabs.addTab("Residual", residualChrome);
                Runnable screeningRepaint = XTreLocGUI.debouncedRepaint(histogramPanel, scatterPanel);
                catalogPanel.addScreeningChangeListener(screeningRepaint);
                JPanel viewerRightPanel = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(viewerRightPanel);
                viewerRightPanel.add((Component)viewerRightTabs, "Center");
                viewerRightPanel.setMinimumSize(new Dimension(0, 0));
                JPanel executionDock = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(executionDock);
                executionDock.add((Component)solverLogPanel, "Center");
                executionDock.setPreferredSize(new Dimension(0, 220));
                JPanel leftPanelContainer = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(leftPanelContainer);
                leftPanelContainer.add((Component)modelSetupPanel, "Center");
                JPanel rightPanelContainer = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(rightPanelContainer);
                rightPanelContainer.add((Component)viewerRightPanel, "Center");
                rightPanelContainer.setMinimumSize(new Dimension(280, 0));
                JPanel leftSidePanel = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(leftSidePanel);
                leftSidePanel.add((Component)leftPanelContainer, "Center");
                leftSidePanel.setMinimumSize(new Dimension(200, 0));
                JSplitPane mainSplit = new JSplitPane(1, leftSidePanel, rightPanelContainer);
                mainSplit.setResizeWeight(0.3);
                mainSplit.setOneTouchExpandable(true);
                mainSplit.setDividerLocation(500);
                mainSplit.setBackground(AppPanelStyle.getPanelBg());
                NavigationPanel navigator = new NavigationPanel();
                for (String waveformPage : NavigationPanel.PAGES) {
                    if (!NavigationPanel.isWaveformPage(waveformPage)) continue;
                    navigator.setContext(waveformPage, pickViewerPanel.getBrowserPanel());
                }
                JSplitPane navSplit = new JSplitPane(1, navigator, mainSplit);
                navSplit.setResizeWeight(0.0);
                navSplit.setContinuousLayout(true);
                navSplit.setOneTouchExpandable(true);
                navSplit.setBackground(AppPanelStyle.getPanelBg());
                JSplitPane rootVertical = new JSplitPane(0, navSplit, executionDock);
                rootVertical.setResizeWeight(0.82);
                rootVertical.setOneTouchExpandable(true);
                rootVertical.setBackground(AppPanelStyle.getPanelBg());
                MainCommands commands = new MainCommands();
                PageToolBar pageToolBar = new PageToolBar();
                MainStatusBar statusBar = new MainStatusBar();
                JPanel centerHost = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(centerHost);
                int[] layoutState = new int[]{170, -1};
                boolean[] visible = new boolean[]{true, true};
                Runnable rebuildCenter = () -> {
                    JSplitPane upper;
                    if (navSplit.getDividerLocation() > 20 && visible[0]) {
                        layoutState[0] = navSplit.getDividerLocation();
                    }
                    if (visible[1] && rootVertical.getHeight() > 100) {
                        layoutState[1] = rootVertical.getDividerLocation();
                    }
                    centerHost.removeAll();
                    if (visible[0]) {
                        navSplit.setLeftComponent(navigator);
                        navSplit.setRightComponent(mainSplit);
                        upper = navSplit;
                    } else {
                        navSplit.remove(mainSplit);
                        upper = mainSplit;
                    }
                    if (visible[1]) {
                        rootVertical.setTopComponent(upper);
                        rootVertical.setBottomComponent(executionDock);
                        centerHost.add((Component)rootVertical, "Center");
                    } else {
                        rootVertical.remove(upper);
                        centerHost.add((Component)upper, "Center");
                    }
                    centerHost.revalidate();
                    centerHost.repaint();
                    SwingUtilities.invokeLater(() -> {
                        if (visible[0]) {
                            navSplit.setDividerLocation(layoutState[0]);
                        }
                        if (visible[1] && layoutState[1] > 0) {
                            rootVertical.setDividerLocation(layoutState[1]);
                        }
                    });
                };
                Supplier<List<CatalogInfo>> loadedCatalogs = catalogPanel::getCatalogInfos;
                pickViewerPanel.setCatalogsSupplier(loadedCatalogs);
                pickViewerPanel.setRelocateHandler(new WorkingRelocationController(locationPanel::buildSolverConfig, locationPanel::getSolverModeAbbreviation, loadedCatalogs, WorkingLayer.getInstance()));
                centerHost.add((Component)rootVertical, "Center");
                mainFrame.setJMenuBar(AppMenuBar.create(commands));
                mainFrame.add((Component)pageToolBar, "North");
                mainFrame.add((Component)centerHost, "Center");
                mainFrame.add((Component)statusBar, "South");
                ViewerStatusBar.setRootFrame(mainFrame);
                String modelPage = "Model";
                String pickPage = "Pick";
                pageToolBar.addButton(modelPage, commands.openCatalog);
                pageToolBar.addButton(pickPage, commands.openSacFolder);
                pageToolBar.addButton(pickPage, commands.exportPicks);
                pageToolBar.addSeparator(pickPage);
                JComboBox<String> toolbarMethod = new JComboBox<String>(pickViewerPanel.autoMethodModel());
                toolbarMethod.setFocusable(false);
                toolbarMethod.setToolTipText("Auto-pick method");
                pageToolBar.addLabeledCombo(pickPage, "Auto-pick", toolbarMethod);
                pageToolBar.addButton(pickPage, commands.runAutoPick);
                pageToolBar.addButton(pickPage, commands.pickAllToPool);
                pageToolBar.addButton(pickPage, commands.applyAll);
                pageToolBar.addButton(pickPage, commands.discard);
                pageToolBar.addSeparator(pickPage);
                pageToolBar.addButton(pickPage, commands.undo);
                pageToolBar.addButton(pickPage, commands.redo);
                pageToolBar.addSeparator(pickPage);
                pageToolBar.addButton(pickPage, commands.relocate);
                pageToolBar.addButton("Associate", commands.runAssociate);
                pageToolBar.addButton("Associate", commands.addToMap);
                pageToolBar.addButton("Export", commands.exportAbsolute);
                pageToolBar.addButton("Function", commands.openSacFolder);
                pageToolBar.addButton("Xcorr", commands.openSacFolder);
                pageToolBar.addGlobalButton(commands.openSettings);
                String[] currentPage = new String[]{modelPage};
                HashMap<String, Integer> dividerOfPage = new HashMap<>();
                Consumer<String> showPage = page -> {
                    JPanel left;
                    dividerOfPage.put(currentPage[0], mainSplit.getDividerLocation());
                    currentPage[0] = page;
                    int saved = dividerOfPage.getOrDefault(page, 500);
                    SwingUtilities.invokeLater(() -> {
                        int maxLoc = Math.max(200, mainSplit.getSize().width - 50);
                        mainSplit.setDividerLocation(Math.min(Math.max(200, saved), maxLoc));
                    });
                    JPanel right = switch (page) {
                        case "Pick" -> {
                            pickViewerPanel.setMode(PickViewerPanel.Mode.ABSOLUTE);
                            left = sacWorkbenchPanel;
                            yield pickViewerPanel.getRightPanel();
                        }
                        case "Function", "Xcorr" -> {
                            pickViewerPanel.setMode(PickViewerPanel.Mode.CORRELATION);
                            pickViewerPanel.showCorrelationTab("Xcorr".equals(page));
                            left = sacWorkbenchPanel;
                            yield pickViewerPanel.getRightPanel();
                        }
                        case "Associate" -> {
                            left = associatePanel;
                            yield resultPanel;
                        }
                        case "Export" -> {
                            left = exportPanel;
                            yield resultPanel;
                        }
                        default -> {
                            left = modelSetupPanel;
                            yield viewerRightPanel;
                        }
                    };
                    leftPanelContainer.removeAll();
                    leftPanelContainer.add((Component)left, "Center");
                    leftPanelContainer.revalidate();
                    leftPanelContainer.repaint();
                    rightPanelContainer.removeAll();
                    rightPanelContainer.add((Component)right, "Center");
                    rightPanelContainer.revalidate();
                    rightPanelContainer.repaint();
                    String group = NavigationPanel.groupOf(page);
                    navigator.selectPage((String)page);
                    commands.showPage((String)page, pickViewerPanel);
                    pageToolBar.showPage((String)page);
                    statusBar.setPage((String)(group == null ? page : group + " \u203a " + page));
                };
                navigator.setPageListener(showPage);
                map2dPanel.setSeismicOpener((focus, folders, others) -> {
                    showPage.accept("Pick");
                    pickViewerPanel.openEvent(focus, folders, others);
                });
                Runnable syncPickState = () -> {
                    commands.showPage(currentPage[0], pickViewerPanel);
                    int[] n = pickViewerPanel.pickCounts();
                    statusBar.setPicks(n[0], n[1]);
                    EventKey ev = pickViewerPanel.getCurrentEvent();
                    statusBar.setEvent(ev == null ? null : ev.time());
                };
                pickViewerPanel.addStateListener(syncPickState);
                pickViewerPanel.addStatusListener(statusBar::setMessage);
                commands.pages.forEach((name, command) -> command.on(() -> showPage.accept((String)name)));
                associatePanel.setStatusSink(statusBar::setMessage);
                exportPanel.setStatusSink(statusBar::setMessage);
                associatePanel.setBusyListener(() -> SwingUtilities.invokeLater(() -> commands.runAssociate.setEnabled(!associatePanel.isBusy())));
                commands.runAssociate.on(associatePanel::runNow);
                commands.exportAbsolute.on(exportPanel::exportNow);
                commands.addToMap.on(resultPanel::addToMap);
                resultPanel.setMapHandler(events -> {
                    catalogPanel.addCatalogFromHypocenters(RealResultWriter.toHypocenters(events), "REAL", null);
                    showPage.accept(modelPage);
                });
                associationModel.addListener(m -> SwingUtilities.invokeLater(() -> commands.addToMap.setEnabled(!m.events().isEmpty())));
                JDialog[] settingsWindow = new JDialog[]{null};
                commands.openSettings.on(() -> {
                    if (settingsWindow[0] == null) {
                        JDialog d = new JDialog((Frame)mainFrame, "Settings");
                        d.setContentPane(new SettingsPanel(view));
                        d.setDefaultCloseOperation(1);
                        d.pack();
                        d.setSize(Math.max(d.getWidth(), 820), Math.max(d.getHeight(), 600));
                        d.setLocationRelativeTo(mainFrame);
                        settingsWindow[0] = d;
                    }
                    settingsWindow[0].setVisible(true);
                    settingsWindow[0].toFront();
                });
                WorkingLayer workingLayer = WorkingLayer.getInstance();
                commands.clearWorking.on(workingLayer::clear);
                workingLayer.addListener(layer -> SwingUtilities.invokeLater(() -> commands.clearWorking.setEnabled(layer.size() > 0)));
                commands.clearWorking.setEnabled(workingLayer.size() > 0);
                commands.openSacFolder.on(pickViewerPanel::chooseInputRoot);
                commands.openCatalog.on(() -> {
                    showPage.accept(modelPage);
                    catalogPanel.selectCatalogFile();
                });
                commands.exportPicks.on(pickViewerPanel::exportPicksNow);
                commands.exit.on(() -> mainFrame.dispatchEvent(new WindowEvent(mainFrame, 201)));
                commands.undo.on(pickViewerPanel::undoPick);
                commands.redo.on(pickViewerPanel::redoPick);
                commands.applyAll.on(pickViewerPanel::applyAllProposalsNow);
                commands.discard.on(pickViewerPanel::discardProposalsNow);
                commands.runAutoPick.on(pickViewerPanel::runAutoPickNow);
                commands.pickAllToPool.on(pickViewerPanel::pickAllToPoolNow);
                commands.relocate.on(pickViewerPanel::relocateNow);
                commands.toggleNavigator.on(() -> {
                    visible[0] = Boolean.TRUE.equals(commands.toggleNavigator.getValue("SwingSelectedKey"));
                    rebuildCenter.run();
                });
                commands.toggleOutput.on(() -> {
                    visible[1] = Boolean.TRUE.equals(commands.toggleOutput.getValue("SwingSelectedKey"));
                    rebuildCenter.run();
                });
                commands.toggleStatusBar.on(() -> statusBar.setVisible(Boolean.TRUE.equals(commands.toggleStatusBar.getValue("SwingSelectedKey"))));
                commands.resetLayout.on(() -> {
                    visible[0] = true;
                    visible[1] = true;
                    layoutState[0] = 170;
                    layoutState[1] = -1;
                    commands.toggleNavigator.putValue("SwingSelectedKey", Boolean.TRUE);
                    commands.toggleOutput.putValue("SwingSelectedKey", Boolean.TRUE);
                    commands.toggleStatusBar.putValue("SwingSelectedKey", Boolean.TRUE);
                    statusBar.setVisible(true);
                    rebuildCenter.run();
                    SwingUtilities.invokeLater(() -> {
                        mainSplit.setDividerLocation(500);
                        int rh = rootVertical.getHeight();
                        if (rh > 100) {
                            rootVertical.setDividerLocation((int)((double)rh * 0.78));
                        }
                    });
                });
                commands.checkUpdates.on(() -> XTreLocGUI.checkForUpdatesNow(mainFrame));
                commands.about.on(() -> JOptionPane.showMessageDialog(mainFrame, "xTreLoc " + VersionInfo.getVersion(), "About xTreLoc", 1));
                syncPickState.run();
                showPage.accept(modelPage);
                mainFrame.pack();
                mainFrame.setSize(MainWindowSizePresets.clampWidth(appSettingsForUI.getMainWindowWidth()), MainWindowSizePresets.clampHeight(appSettingsForUI.getMainWindowHeight()));
                mainFrame.setLocationRelativeTo(null);
                mainFrame.setVisible(true);
                SwingUtilities.invokeLater(() -> {
                    navSplit.setDividerLocation(170);
                    mainSplit.setDividerLocation(500);
                    int rh = rootVertical.getHeight();
                    if (rh > 100) {
                        rootVertical.setDividerLocation((int)((double)rh * 0.78));
                    }
                });
                mainFrame.setDefaultCloseOperation(0);
                mainFrame.addWindowListener(new WindowAdapter(){

                    @Override
                    public void windowClosing(WindowEvent e) {
                        int choice;
                        ArrayList<String> running = new ArrayList<String>();
                        if (locationPanel.isBusy()) {
                            running.add("Solver");
                        }
                        if (traceProcessingPanel.isBusy()) {
                            running.add("Cross-corr pick");
                        }
                        if (!running.isEmpty() && (choice = JOptionPane.showConfirmDialog(mainFrame, "A process is still running (" + String.join((CharSequence)", ", running) + ").\nClosing the window will abort it. Do you want to quit anyway?", "Confirm Exit", 0, 2)) != 0) {
                            return;
                        }
                        mainFrame.dispose();
                        System.exit(0);
                    }
                });
            }
            catch (Exception e) {
                logger.log(Level.SEVERE, "Error initializing GUI: " + e.getMessage(), e);
                JOptionPane.showMessageDialog(null, "An error occurred: " + e.getMessage(), "Error", 0);
            }
        });
    }

    private static JPanel chartViewCard(ChartPlotOptions opt, Runnable repaint) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(AppPanelStyle.getPanelBg());
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        JCheckBox title = new JCheckBox("Title", opt.showTitle);
        JCheckBox grid = new JCheckBox("Grid", opt.showGrid);
        for (JCheckBox cb : new JCheckBox[] {title, grid}) {
            cb.setBackground(AppPanelStyle.getPanelBg());
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(cb);
        }
        title.addActionListener(e -> { opt.showTitle = title.isSelected(); repaint.run(); });
        grid.addActionListener(e -> { opt.showGrid = grid.isSelected(); repaint.run(); });
        return p;
    }

    private static JPanel chartToolsCard(ChartPlotOptions opt, Runnable repaint) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(AppPanelStyle.getPanelBg());
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        JCheckBox legend = new JCheckBox("Legend", opt.showLegend);
        legend.setBackground(AppPanelStyle.getPanelBg());
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);
        legend.addActionListener(e -> { opt.showLegend = legend.isSelected(); repaint.run(); });
        p.add(legend);
        return p;
    }

    private static JPanel chartExportCard(JPanel plot, JPanel dialogParent, Runnable resetZoom, String fileName) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        p.setBackground(AppPanelStyle.getPanelBg());
        JButton save = new JButton("Save PNG...");
        save.addActionListener(e -> ChartImageExport.exportPanelToImage(plot, dialogParent, fileName));
        JButton copy = new JButton("Copy");
        copy.addActionListener(e -> ChartImageExport.copyPanelToClipboard(plot, dialogParent));
        JButton reset = new JButton("Reset zoom");
        reset.addActionListener(e -> resetZoom.run());
        p.add(save);
        p.add(copy);
        p.add(reset);
        return p;
    }

    private static JComponent wrapResidualChrome(JPanel residualHost,
            com.treloc.xtreloc.app.gui.view.HypocenterLocationPanel locationPanel) {
        JPanel view = new JPanel();
        view.setLayout(new BoxLayout(view, BoxLayout.Y_AXIS));
        view.setBackground(AppPanelStyle.getPanelBg());
        view.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        JCheckBox auto = new JCheckBox("Auto scale", true);
        auto.setBackground(AppPanelStyle.getPanelBg());
        auto.addActionListener(e -> {
            if (locationPanel.getResidualPlotPanel() != null) {
                locationPanel.getResidualPlotPanel().setAutoScale(auto.isSelected());
            }
        });
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> {
            if (locationPanel.getResidualPlotPanel() != null) {
                locationPanel.getResidualPlotPanel().clearData();
            }
        });
        view.add(auto);
        view.add(Box.createVerticalStrut(6));
        view.add(clear);
        JPanel tools = new JPanel();
        tools.setLayout(new BoxLayout(tools, BoxLayout.Y_AXIS));
        tools.setBackground(AppPanelStyle.getPanelBg());
        tools.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        JCheckBox legend = new JCheckBox("Legend", true);
        legend.setBackground(AppPanelStyle.getPanelBg());
        legend.addActionListener(e -> {
            if (locationPanel.getResidualPlotPanel() != null) {
                locationPanel.getResidualPlotPanel().setShowLegend(legend.isSelected());
            }
        });
        tools.add(legend);
        JPanel export = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        export.setBackground(AppPanelStyle.getPanelBg());
        JButton save = new JButton("Save PNG...");
        save.addActionListener(e -> {
            if (locationPanel.getResidualPlotPanel() != null) {
                locationPanel.getResidualPlotPanel().exportChartImage();
            }
        });
        export.add(save);
        return com.treloc.xtreloc.app.gui.view.ViewerSideChrome.wrap(residualHost,
            new com.treloc.xtreloc.app.gui.view.ViewerSideChrome.Item(
                "View", com.treloc.xtreloc.app.gui.volume3d.Map3DIcons.Kind.VIEW, "Scale and clear", view),
            new com.treloc.xtreloc.app.gui.view.ViewerSideChrome.Item(
                "Tools", com.treloc.xtreloc.app.gui.volume3d.Map3DIcons.Kind.TOOLS, "Legend", tools),
            new com.treloc.xtreloc.app.gui.view.ViewerSideChrome.Item(
                "Export", com.treloc.xtreloc.app.gui.volume3d.Map3DIcons.Kind.EXPORT, "Export the plot", export));
    }

    /**
     * Creates histogram panel (supports catalog comparison)
     */
    private static JPanel createHistogramPanel(com.treloc.xtreloc.app.gui.view.CatalogTablePanel catalogPanel, MapView mapView) {
        JPanel histogramPanelWrapper = new JPanel(new BorderLayout());
        com.treloc.xtreloc.app.gui.util.AppPanelStyle.setPanelBackground(histogramPanelWrapper);
        histogramPanelWrapper.setBorder(com.treloc.xtreloc.app.gui.util.AppPanelStyle.createTitledSectionBorder("Histogram"));
        
        final ChartPlotOptions plotOpt = new ChartPlotOptions();
        final Double[] zoomMin = { null }, zoomMax = { null };
        final HistChartBounds histBounds = new HistChartBounds();
        final int[] dragStart = { -1, -1 }, dragEnd = { -1, -1 };
        
        JPanel histogramPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ChartAppearanceSettings chart = AppSettingsCache.chartAppearance();
                g.setColor(histScatterBackground(chart));
                g.fillRect(0, 0, getWidth(), getHeight());
                drawHistogram(g, catalogPanel, mapView, zoomMin[0], zoomMax[0], histBounds, plotOpt);
                if (dragStart[0] >= 0 && dragEnd[0] >= 0 && g instanceof Graphics2D) {
                    Graphics2D g2 = (Graphics2D) g;
                    int x1 = Math.min(dragStart[0], dragEnd[0]);
                    int x2 = Math.max(dragStart[0], dragEnd[0]);
                    int y1 = histBounds.chartTop;
                    int y2 = histBounds.chartTop + histBounds.chartHeight;
                    g2.setColor(new Color(0, 120, 215, 80));
                    g2.fillRect(x1, y1, x2 - x1, y2 - y1);
                    g2.setColor(new Color(0, 90, 180));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(x1, y1, x2 - x1, y2 - y1);
                }
            }
        };
        histogramPanel.setPreferredSize(new Dimension(500, 400));
        histogramPanel.setOpaque(false);
        Runnable histRepaint = debouncedRepaint(histogramPanel);
        
        Runnable resetHistZoom = () -> {
            zoomMin[0] = null;
            zoomMax[0] = null;
            histRepaint.run();
        };
        ChartImageExport.installChartPopupMenu(histogramPanel, histogramPanelWrapper, resetHistZoom, "histogram.png");

        histogramPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (histBounds.chartWidth <= 0) return;
                int x = e.getX(), y = e.getY();
                if (x >= histBounds.chartLeft && x <= histBounds.chartLeft + histBounds.chartWidth
                    && y >= histBounds.chartTop && y <= histBounds.chartTop + histBounds.chartHeight) {
                    dragStart[0] = x;
                    dragStart[1] = y;
                    dragEnd[0] = x;
                    dragEnd[1] = y;
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (dragStart[0] < 0) return;
                dragEnd[0] = e.getX();
                dragEnd[1] = e.getY();
                int x1 = Math.min(dragStart[0], dragEnd[0]);
                int x2 = Math.max(dragStart[0], dragEnd[0]);
                int w = x2 - x1;
                if (histBounds.chartWidth > 0 && w > 4) {
                    double v1 = histBounds.dataMin + (double) (x1 - histBounds.chartLeft) / histBounds.chartWidth * (histBounds.dataMax - histBounds.dataMin);
                    double v2 = histBounds.dataMin + (double) (x2 - histBounds.chartLeft) / histBounds.chartWidth * (histBounds.dataMax - histBounds.dataMin);
                    zoomMin[0] = Math.min(v1, v2);
                    zoomMax[0] = Math.max(v1, v2);
                }
                dragStart[0] = -1;
                dragEnd[0] = -1;
                histRepaint.run();
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (e.getClickCount() == 2 && histBounds.chartWidth > 0) {
                    int x = e.getX(), y = e.getY();
                    if (x >= histBounds.chartLeft && x <= histBounds.chartLeft + histBounds.chartWidth
                        && y >= histBounds.chartTop && y <= histBounds.chartTop + histBounds.chartHeight) {
                        zoomMin[0] = null;
                        zoomMax[0] = null;
                        histRepaint.run();
                    }
                }
            }
        });
        histogramPanel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart[0] >= 0) {
                    dragEnd[0] = e.getX();
                    dragEnd[1] = e.getY();
                    histRepaint.run();
                }
            }
        });
        
        if (mapView != null) {
            mapView.addCatalogVisibilityChangeListener(histRepaint);
        }
        catalogPanel.addViewerChartChangeListener(histRepaint);

        histogramPanelWrapper.add(histogramPanel, BorderLayout.CENTER);
        return (JPanel) com.treloc.xtreloc.app.gui.view.ViewerSideChrome.wrap(histogramPanelWrapper,
            new com.treloc.xtreloc.app.gui.view.ViewerSideChrome.Item(
                "View", com.treloc.xtreloc.app.gui.volume3d.Map3DIcons.Kind.VIEW, "Title and grid",
                chartViewCard(plotOpt, histRepaint)),
            new com.treloc.xtreloc.app.gui.view.ViewerSideChrome.Item(
                "Style", com.treloc.xtreloc.app.gui.volume3d.Map3DIcons.Kind.TABLE, "Catalog colours",
                new com.treloc.xtreloc.app.gui.view.ChartCatalogColorCard(catalogPanel, mapView, histRepaint)),
            new com.treloc.xtreloc.app.gui.view.ViewerSideChrome.Item(
                "Tools", com.treloc.xtreloc.app.gui.volume3d.Map3DIcons.Kind.TOOLS, "Legend",
                chartToolsCard(plotOpt, histRepaint)),
            new com.treloc.xtreloc.app.gui.view.ViewerSideChrome.Item(
                "Export", com.treloc.xtreloc.app.gui.volume3d.Map3DIcons.Kind.EXPORT, "Save or copy",
                chartExportCard(histogramPanel, histogramPanelWrapper, resetHistZoom, "histogram.png")));
    }
    
    /** Draws a vertical (90° CCW) axis label centered at (cx, cy). */
    private static void drawRotatedYAxisLabel(Graphics2D g2, String text, Font font, Color color, int cx, int cy) {
        Font prevF = g2.getFont();
        Color prevC = g2.getColor();
        AffineTransform prevTx = g2.getTransform();
        try {
            g2.setFont(font);
            g2.setColor(color);
            FontMetrics fm = g2.getFontMetrics();
            g2.translate(cx, cy);
            g2.rotate(-Math.PI / 2);
            int tw = fm.stringWidth(text);
            g2.drawString(text, -tw / 2, fm.getAscent() / 2);
        } finally {
            g2.setTransform(prevTx);
            g2.setFont(prevF);
            g2.setColor(prevC);
        }
    }

    private static Color parseChartColor(String hex) {
        if (hex == null || hex.isEmpty()) return Color.BLACK;
        try {
            return Color.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (Exception e) {
            logger.log(Level.FINE, "Invalid chart color hex, using black: " + hex, e);
            return Color.BLACK;
        }
    }

    /** Theme panel background unless the user set a custom Hist/Scatter plot color. */
    private static Color histScatterBackground(ChartAppearanceSettings chart) {
        if (chart == null) {
            return AppPanelStyle.getPanelBg();
        }
        String hex = chart.getBackgroundColor();
        if (hex == null) {
            return AppPanelStyle.getPanelBg();
        }
        String t = hex.trim();
        if (t.isEmpty() || "#FFFFFF".equalsIgnoreCase(t) || "#FFF".equalsIgnoreCase(t)) {
            return AppPanelStyle.getPanelBg();
        }
        return parseChartColor(t);
    }
    
    private static java.awt.Stroke gridStroke(ChartAppearanceSettings chart) {
        float w = chart.getGridlineWidth();
        String style = chart.getGridlineStyle();
        if (style == null) style = "solid";
        switch (style.toLowerCase()) {
            case "dash":
                return new java.awt.BasicStroke(w, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10f, new float[]{8f, 4f}, 0f);
            case "dot":
                return new java.awt.BasicStroke(w, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_MITER, 10f, new float[]{2f, 4f}, 0f);
            default:
                return new java.awt.BasicStroke(w);
        }
    }
    
    /** Bounds filled by drawHistogram for pixel-to-value conversion (drag zoom). */
    private static class HistChartBounds {
        double dataMin = 0, dataMax = 0;
        int chartLeft = 0, chartTop = 0, chartWidth = 0, chartHeight = 0;
    }
    
    /** Bounds filled by drawScatterPlot for pixel-to-value conversion (drag zoom). */
    private static class ScatterChartBounds {
        double dataXMin = 0, dataXMax = 0, dataYMin = 0, dataYMax = 0;
        int chartLeft = 0, chartTop = 0, chartWidth = 0, chartHeight = 0;
    }
    
    private static void drawHistogram(Graphics g, com.treloc.xtreloc.app.gui.view.CatalogTablePanel catalogPanel,
                                     MapView mapView, Double zoomMin, Double zoomMax, HistChartBounds outBounds,
                                     ChartPlotOptions plotOpt) {
        if (outBounds != null) outBounds.chartWidth = 0;
        try {
            java.awt.Rectangle clip0 = g.getClipBounds();
            final int panelW = clip0 != null ? clip0.width : 400;
            final int panelH = clip0 != null ? clip0.height : 300;
            ChartAppearanceSettings chart = com.treloc.xtreloc.app.gui.util.AppSettingsCache.chartAppearance();
            Font titleFont = chart.getTitleFont();
            Font tickFont = chart.getTickLabelFont();
            Font legendFont = chart.getLegendFont();
            Color axisColor = parseChartColor(chart.getAxisLineColor());
            
            int selectedColumnIndex = catalogPanel != null ? catalogPanel.getColorColumnModelIndex() : -1;
            String selectedColumnName = (catalogPanel != null && selectedColumnIndex >= 0)
                ? catalogPanel.getDataColumnName(selectedColumnIndex) : null;
            if (selectedColumnIndex < 2 || selectedColumnIndex > 9) {
                g.setColor(axisColor);
                g.setFont(tickFont);
                FontMetrics fm = g.getFontMetrics();
                String message = "Select a numeric column (Latitude … Cluster ID) in the catalog table header";
                int x = Math.max(8, (panelW - fm.stringWidth(message)) / 2);
                int y = panelH / 2;
                g.drawString(message, x, y);
                return;
            }
            
            java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> selectedCatalogs = new java.util.ArrayList<>();
            if (mapView != null) {
                java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> allCatalogs = mapView.getCatalogInfos();
                for (com.treloc.xtreloc.app.gui.model.CatalogInfo info : allCatalogs) {
                    if (info.isVisible() && info.getHypocenters() != null && !info.getHypocenters().isEmpty()) {
                        selectedCatalogs.add(info);
                    }
                }
            }
            
            if (selectedCatalogs.isEmpty()) {
                g.setColor(axisColor);
                g.setFont(tickFont);
                FontMetrics fm = g.getFontMetrics();
                String message = "Please select at least one catalog";
                int x = (panelW - fm.stringWidth(message)) / 2;
                int y = panelH / 2;
                g.drawString(message, x, y);
                return;
            }
            
            int width = panelW;
            int height = panelH;
            
            int margin = 50;
            int chartWidth = width - 2 * margin;
            int chartHeight = height - 2 * margin - 60;
            
            Color gridColor = parseChartColor(chart.getGridlineColor());
            if (plotOpt != null && plotOpt.showGrid && g instanceof Graphics2D) {
                Graphics2D g2 = (Graphics2D) g;
                java.awt.Stroke prev = g2.getStroke();
                g2.setStroke(gridStroke(chart));
                g2.setColor(gridColor);
                for (int i = 1; i <= 4; i++) {
                    int vx = margin + (int) (chartWidth * i / 5);
                    g2.drawLine(vx, margin, vx, margin + chartHeight);
                    int hy = margin + (int) (chartHeight * i / 5);
                    g2.drawLine(margin, hy, margin + chartWidth, hy);
                }
                g2.setStroke(prev);
            }
            
            java.util.List<java.util.List<Double>> allValues = new java.util.ArrayList<>();
            java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> catalogInfoForValues = new java.util.ArrayList<>();
            
            for (com.treloc.xtreloc.app.gui.model.CatalogInfo info : selectedCatalogs) {
                java.util.List<Double> values = getColumnValuesForHistogram(info.getHypocenters(), selectedColumnIndex);
                if (!values.isEmpty()) {
                    java.util.Collections.sort(values);
                    allValues.add(values);
                    catalogInfoForValues.add(info);
                }
            }
            
            if (allValues.isEmpty()) {
                g.setColor(axisColor);
                g.setFont(tickFont);
                FontMetrics fm = g.getFontMetrics();
                String message = selectedColumnIndex == 9
                    ? "No cluster ID values (all empty or not set in catalog)"
                    : "No data available";
                int x = (panelW - fm.stringWidth(message)) / 2;
                int y = panelH / 2;
                g.drawString(message, x, y);
                return;
            }
            
            int mergedCap = 0;
            for (java.util.List<Double> v : allValues) {
                mergedCap += v.size();
            }
            double[] mergedFinite = new double[mergedCap];
            int mergedLen = 0;
            for (java.util.List<Double> v : allValues) {
                for (Double d : v) {
                    if (d != null && Double.isFinite(d)) {
                        mergedFinite[mergedLen++] = d;
                    }
                }
            }
            if (mergedLen == 0) {
                g.setColor(axisColor);
                g.setFont(tickFont);
                FontMetrics fm = g.getFontMetrics();
                String message = "No finite values for histogram";
                int x = (panelW - fm.stringWidth(message)) / 2;
                int y = panelH / 2;
                g.drawString(message, x, y);
                return;
            }
            if (mergedLen < mergedFinite.length) {
                mergedFinite = Arrays.copyOf(mergedFinite, mergedLen);
            }
            double[] auto = ColorScaleUtils.computeAutoColorRange(mergedFinite, 0.02, 0.98);
            double globalMin = auto[0];
            double globalMax = auto[1];
            
            boolean zoomed = zoomMin != null && zoomMax != null;
            double displayMin = zoomed ? zoomMin : globalMin;
            double displayMax = zoomed ? zoomMax : globalMax;
            double range = displayMax - displayMin;
            if (range == 0) {
                range = 1.0;
            }
            
            int totalSize = 0;
            for (java.util.List<Double> values : allValues) {
                totalSize += values.size();
            }
            int numBins = (int) Math.ceil(1 + Math.log10(totalSize) / Math.log10(2));
            if (numBins > 30) {
                numBins = 30;
            }
            if (numBins < 5) {
                numBins = 5;
            }
            java.util.List<int[]> allBins = new java.util.ArrayList<>();
            double binWidth = range / numBins;
            
            for (java.util.List<Double> values : allValues) {
                int[] bins = new int[numBins];
                for (Double boxed : values) {
                    if (boxed == null || !Double.isFinite(boxed)) {
                        continue;
                    }
                    double value = boxed;
                    double vBin = value;
                    if (zoomed) {
                        if (value < displayMin || value > displayMax) {
                            continue;
                        }
                    } else {
                        vBin = Math.max(displayMin, Math.min(displayMax, value));
                    }
                    int binIndex = (int) Math.min((vBin - displayMin) / binWidth, numBins - 1);
                    if (binIndex >= 0) {
                        bins[binIndex]++;
                    }
                }
                allBins.add(bins);
            }
            
            int maxFreq = 0;
            for (int[] bins : allBins) {
                for (int freq : bins) {
                    if (freq > maxFreq) {
                        maxFreq = freq;
                    }
                }
            }
            if (maxFreq == 0) {
                maxFreq = 1;
            }
            
            for (int i = 0; i < allBins.size(); i++) {
                int[] bins = allBins.get(i);
                com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfoForValues.get(i);
                Color catalogColor = catalogDrawColor(info);
                
                g.setColor(new Color(catalogColor.getRed(), catalogColor.getGreen(), catalogColor.getBlue(), 150));
                double barWidth = (double) chartWidth / numBins;
                
                for (int j = 0; j < numBins; j++) {
                    int barHeight = (int) ((double) bins[j] / maxFreq * chartHeight);
                    int x = margin + (int) (j * barWidth);
                    int y = margin + chartHeight - barHeight;
                    g.fillRect(x, y, (int) barWidth - 1, barHeight);
                }
            }
            
            g.setColor(axisColor);
            g.drawLine(margin, margin + chartHeight, margin + chartWidth, margin + chartHeight);
            g.drawLine(margin, margin, margin, margin + chartHeight);
            
            g.setFont(tickFont);
            FontMetrics fm = g.getFontMetrics();
            
            for (int i = 0; i <= 5; i++) {
                double value = displayMin + (range * i / 5);
                String label = selectedColumnIndex == 9
                    ? String.format("%.0f", value)
                    : String.format("%.2f", value);
                int x = margin + (int) (chartWidth * i / 5) - fm.stringWidth(label) / 2;
                g.drawString(label, x, margin + chartHeight + 20);
            }
            for (int i = 0; i <= 5; i++) {
                int freq = maxFreq * i / 5;
                String label = String.valueOf(freq);
                int y = margin + chartHeight - (chartHeight * i / 5) + fm.getAscent() / 2;
                g.drawString(label, margin - fm.stringWidth(label) - 5, y);
            }

            Font axisLabelFont = chart.getAxisLabelFont();
            g.setFont(axisLabelFont);
            g.setColor(axisColor);
            FontMetrics axisFm = g.getFontMetrics();
            String xAxisLabel = selectedColumnName != null ? selectedColumnName : "Value";
            String yAxisLabel = "Count";
            int xLabW = axisFm.stringWidth(xAxisLabel);
            g.drawString(xAxisLabel, (width - xLabW) / 2, margin + chartHeight + 42);
            if (g instanceof Graphics2D) {
                drawRotatedYAxisLabel((Graphics2D) g, yAxisLabel, axisLabelFont, axisColor,
                    Math.max(14, margin / 3), margin + chartHeight / 2);
            }
            
            if (plotOpt == null || plotOpt.showTitle) {
                String title = selectedColumnName + " Histogram";
                g.setFont(titleFont);
                FontMetrics titleFm = g.getFontMetrics();
                int titleWidth = titleFm.stringWidth(title);
                g.drawString(title, (width - titleWidth) / 2, 20);
            }

            if (plotOpt == null || plotOpt.showLegend) {
                g.setFont(legendFont);
                int legendY = margin + chartHeight + 50;
                int legendX = margin;
                int legendItemHeight = 15;

                for (int i = 0; i < catalogInfoForValues.size(); i++) {
                    com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfoForValues.get(i);
                    Color catalogColor = catalogDrawColor(info);

                    g.setColor(catalogColor);
                    g.fillRect(legendX, legendY, 15, 10);

                    g.setColor(axisColor);
                    g.drawString(info.getName(), legendX + 20, legendY + 8);

                    legendX += 120;
                    if (legendX + 120 > width - margin) {
                        legendX = margin;
                        legendY += legendItemHeight;
                    }
                }
            }
            
            if (outBounds != null) {
                outBounds.dataMin = displayMin;
                outBounds.dataMax = displayMax;
                outBounds.chartLeft = margin;
                outBounds.chartTop = margin;
                outBounds.chartWidth = chartWidth;
                outBounds.chartHeight = chartHeight;
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to draw histogram: " + e.getMessage(), e);
        }
    }
    
    private static java.util.List<Double> getColumnValuesForHistogram(
            java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters, int columnIndex) {
        java.util.List<Double> values = new java.util.ArrayList<>();
        for (com.treloc.xtreloc.app.gui.model.Hypocenter h : hypocenters) {
            double value = 0.0;
            switch (columnIndex) {
                case 2:
                    value = h.lat;
                    break;
                case 3:
                    value = h.lon;
                    break;
                case 4:
                    value = h.depth;
                    break;
                case 5:
                    value = h.xerr;
                    break;
                case 6:
                    value = h.yerr;
                    break;
                case 7:
                    value = h.zerr;
                    break;
                case 8:
                    value = h.rms;
                    break;
                case 9:
                    if (h.clusterId == null) {
                        continue;
                    }
                    value = h.clusterId;
                    break;
                default:
                    continue;
            }
            values.add(value);
        }
        return values;
    }
    
    private static JPanel createScatterPanel(com.treloc.xtreloc.app.gui.view.CatalogTablePanel catalogPanel, MapView mapView) {
        JPanel scatterPanel = new JPanel(new BorderLayout());
        com.treloc.xtreloc.app.gui.util.AppPanelStyle.setPanelBackground(scatterPanel);
        scatterPanel.setBorder(com.treloc.xtreloc.app.gui.util.AppPanelStyle.createTitledSectionBorder("Scatter Plot"));
        
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        com.treloc.xtreloc.app.gui.util.AppPanelStyle.setPanelBackground(controlPanel);
        JLabel scatterHint = new JLabel("X/Y: right-click catalog column header");
        scatterHint.setForeground(com.treloc.xtreloc.app.gui.util.AppPanelStyle.getContentTextColor());
        controlPanel.add(scatterHint);
        
        final ChartPlotOptions plotOpt = new ChartPlotOptions();
        final Double[] zoomXMin = { null }, zoomXMax = { null }, zoomYMin = { null }, zoomYMax = { null };
        final ScatterChartBounds scatterBounds = new ScatterChartBounds();
        final int[] dragStart = { -1, -1 }, dragEnd = { -1, -1 };
        
        JPanel plotPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ChartAppearanceSettings chart = AppSettingsCache.chartAppearance();
                g.setColor(histScatterBackground(chart));
                g.fillRect(0, 0, getWidth(), getHeight());
                drawScatterPlot(g, catalogPanel, mapView,
                    zoomXMin[0], zoomXMax[0], zoomYMin[0], zoomYMax[0], scatterBounds, plotOpt);
                if (dragStart[0] >= 0 && dragEnd[0] >= 0 && g instanceof Graphics2D) {
                    Graphics2D g2 = (Graphics2D) g;
                    int x1 = Math.min(dragStart[0], dragEnd[0]);
                    int x2 = Math.max(dragStart[0], dragEnd[0]);
                    int y1 = Math.min(dragStart[1], dragEnd[1]);
                    int y2 = Math.max(dragStart[1], dragEnd[1]);
                    g2.setColor(new Color(0, 120, 215, 80));
                    g2.fillRect(x1, y1, x2 - x1, y2 - y1);
                    g2.setColor(new Color(0, 90, 180));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(x1, y1, x2 - x1, y2 - y1);
                }
            }
        };
        plotPanel.setPreferredSize(new Dimension(500, 400));
        plotPanel.setOpaque(false);
        Runnable plotRepaint = debouncedRepaint(plotPanel);

        Runnable resetScatterZoom = () -> {
            zoomXMin[0] = null;
            zoomXMax[0] = null;
            zoomYMin[0] = null;
            zoomYMax[0] = null;
            plotRepaint.run();
        };
        ChartImageExport.installChartPopupMenu(plotPanel, scatterPanel, resetScatterZoom, "scatter.png");
        
        plotPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (scatterBounds.chartWidth <= 0) return;
                int x = e.getX(), y = e.getY();
                if (x >= scatterBounds.chartLeft && x <= scatterBounds.chartLeft + scatterBounds.chartWidth
                    && y >= scatterBounds.chartTop && y <= scatterBounds.chartTop + scatterBounds.chartHeight) {
                    dragStart[0] = x;
                    dragStart[1] = y;
                    dragEnd[0] = x;
                    dragEnd[1] = y;
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (dragStart[0] < 0) return;
                dragEnd[0] = e.getX();
                dragEnd[1] = e.getY();
                int x1 = Math.min(dragStart[0], dragEnd[0]);
                int x2 = Math.max(dragStart[0], dragEnd[0]);
                int y1 = Math.min(dragStart[1], dragEnd[1]);
                int y2 = Math.max(dragStart[1], dragEnd[1]);
                int w = x2 - x1, h = y2 - y1;
                if (scatterBounds.chartWidth > 0 && scatterBounds.chartHeight > 0 && w > 4 && h > 4) {
                    double vx1 = scatterBounds.dataXMin + (double) (x1 - scatterBounds.chartLeft) / scatterBounds.chartWidth * (scatterBounds.dataXMax - scatterBounds.dataXMin);
                    double vx2 = scatterBounds.dataXMin + (double) (x2 - scatterBounds.chartLeft) / scatterBounds.chartWidth * (scatterBounds.dataXMax - scatterBounds.dataXMin);
                    double vy1 = scatterBounds.dataYMax - (double) (y1 - scatterBounds.chartTop) / scatterBounds.chartHeight * (scatterBounds.dataYMax - scatterBounds.dataYMin);
                    double vy2 = scatterBounds.dataYMax - (double) (y2 - scatterBounds.chartTop) / scatterBounds.chartHeight * (scatterBounds.dataYMax - scatterBounds.dataYMin);
                    zoomXMin[0] = Math.min(vx1, vx2);
                    zoomXMax[0] = Math.max(vx1, vx2);
                    zoomYMin[0] = Math.min(vy1, vy2);
                    zoomYMax[0] = Math.max(vy1, vy2);
                }
                dragStart[0] = -1;
                dragEnd[0] = -1;
                plotRepaint.run();
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (e.getClickCount() == 2 && scatterBounds.chartWidth > 0 && scatterBounds.chartHeight > 0) {
                    int x = e.getX(), y = e.getY();
                    if (x >= scatterBounds.chartLeft && x <= scatterBounds.chartLeft + scatterBounds.chartWidth
                        && y >= scatterBounds.chartTop && y <= scatterBounds.chartTop + scatterBounds.chartHeight) {
                        resetScatterZoom.run();
                    }
                }
            }
        });
        plotPanel.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart[0] >= 0) {
                    dragEnd[0] = e.getX();
                    dragEnd[1] = e.getY();
                    plotRepaint.run();
                }
            }
        });
        
        scatterPanel.add(controlPanel, BorderLayout.NORTH);
        scatterPanel.add(plotPanel, BorderLayout.CENTER);
        
        if (mapView != null) {
            mapView.addCatalogVisibilityChangeListener(plotRepaint);
        }
        catalogPanel.addViewerChartChangeListener(plotRepaint);

        return (JPanel) com.treloc.xtreloc.app.gui.view.ViewerSideChrome.wrap(scatterPanel,
            new com.treloc.xtreloc.app.gui.view.ViewerSideChrome.Item(
                "View", com.treloc.xtreloc.app.gui.volume3d.Map3DIcons.Kind.VIEW, "Title and grid",
                chartViewCard(plotOpt, plotRepaint)),
            new com.treloc.xtreloc.app.gui.view.ViewerSideChrome.Item(
                "Style", com.treloc.xtreloc.app.gui.volume3d.Map3DIcons.Kind.TABLE, "Catalog colours",
                new com.treloc.xtreloc.app.gui.view.ChartCatalogColorCard(catalogPanel, mapView, plotRepaint)),
            new com.treloc.xtreloc.app.gui.view.ViewerSideChrome.Item(
                "Tools", com.treloc.xtreloc.app.gui.volume3d.Map3DIcons.Kind.TOOLS, "Legend",
                chartToolsCard(plotOpt, plotRepaint)),
            new com.treloc.xtreloc.app.gui.view.ViewerSideChrome.Item(
                "Export", com.treloc.xtreloc.app.gui.volume3d.Map3DIcons.Kind.EXPORT, "Save or copy",
                chartExportCard(plotPanel, scatterPanel, resetScatterZoom, "scatter.png")));
    }
    
    private static void drawScatterPlot(Graphics g, com.treloc.xtreloc.app.gui.view.CatalogTablePanel catalogPanel,
                                       MapView mapView,
                                       Double zoomXMin, Double zoomXMax, Double zoomYMin, Double zoomYMax,
                                       ScatterChartBounds outBounds, ChartPlotOptions plotOpt) {
        if (outBounds != null) outBounds.chartWidth = 0;
        try {
            java.awt.Rectangle clip0 = g.getClipBounds();
            final int panelW = clip0 != null ? clip0.width : 400;
            final int panelH = clip0 != null ? clip0.height : 300;
            ChartAppearanceSettings chart = com.treloc.xtreloc.app.gui.util.AppSettingsCache.chartAppearance();
            Font titleFont = chart.getTitleFont();
            Font tickFont = chart.getTickLabelFont();
            Font legendFont = chart.getLegendFont();
            Color axisColor = parseChartColor(chart.getAxisLineColor());
            
            int xCol = catalogPanel != null ? catalogPanel.getScatterXColumnModelIndex() : -1;
            int yCol = catalogPanel != null ? catalogPanel.getScatterYColumnModelIndex() : -1;
            String xAxisName = (catalogPanel != null && xCol >= 0) ? catalogPanel.getDataColumnName(xCol) : null;
            String yAxisName = (catalogPanel != null && yCol >= 0) ? catalogPanel.getDataColumnName(yCol) : null;
            if (xAxisName == null || yAxisName == null || xCol < 1 || xCol > 8 || yCol < 2 || yCol > 8) {
                g.setColor(axisColor);
                g.setFont(tickFont);
                FontMetrics fm = g.getFontMetrics();
                String message = "Right-click a catalog column header: set Scatter X and Y (Time, Latitude … rms)";
                int x = Math.max(8, (panelW - fm.stringWidth(message)) / 2);
                int y = panelH / 2;
                g.drawString(message, x, y);
                return;
            }
            
            java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> selectedCatalogs = new java.util.ArrayList<>();
            if (mapView != null) {
                java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> allCatalogs = mapView.getCatalogInfos();
                for (com.treloc.xtreloc.app.gui.model.CatalogInfo info : allCatalogs) {
                    if (info.isVisible() && info.getHypocenters() != null && !info.getHypocenters().isEmpty()) {
                        selectedCatalogs.add(info);
                    }
                }
            }
            
            if (selectedCatalogs.isEmpty()) {
                g.setColor(axisColor);
                g.setFont(tickFont);
                FontMetrics fm = g.getFontMetrics();
                String message = "Please select at least one catalog in Map";
                int x = (panelW - fm.stringWidth(message)) / 2;
                int y = panelH / 2;
                g.drawString(message, x, y);
                return;
            }
            
            java.util.List<java.util.List<Double>> allXValues = new java.util.ArrayList<>();
            java.util.List<java.util.List<Double>> allYValues = new java.util.ArrayList<>();
            java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> catalogInfoForValues = new java.util.ArrayList<>();
            
            for (com.treloc.xtreloc.app.gui.model.CatalogInfo info : selectedCatalogs) {
                java.util.List<Double> xValues = new java.util.ArrayList<>();
                java.util.List<Double> yValues = new java.util.ArrayList<>();
                
                for (com.treloc.xtreloc.app.gui.model.Hypocenter h : info.getHypocenters()) {
                    double xVal = getValueForColumn(h, xAxisName);
                    double yVal = getValueForColumn(h, yAxisName);
                    if (!Double.isNaN(xVal) && !Double.isNaN(yVal)) {
                        xValues.add(xVal);
                        yValues.add(yVal);
                    }
                }
                
                if (!xValues.isEmpty()) {
                    allXValues.add(xValues);
                    allYValues.add(yValues);
                    catalogInfoForValues.add(info);
                }
            }
            
            if (allXValues.isEmpty()) {
                g.setColor(axisColor);
                g.setFont(tickFont);
                FontMetrics fm = g.getFontMetrics();
                String message = "No data available";
                int x = (panelW - fm.stringWidth(message)) / 2;
                int y = panelH / 2;
                g.drawString(message, x, y);
                return;
            }
            
            double xMin = Double.MAX_VALUE;
            double xMax = Double.MIN_VALUE;
            double yMin = Double.MAX_VALUE;
            double yMax = Double.MIN_VALUE;
            
            for (java.util.List<Double> xValues : allXValues) {
                for (Double boxed : xValues) {
                    if (boxed == null || !Double.isFinite(boxed)) {
                        continue;
                    }
                    double val = boxed;
                    if (val < xMin) xMin = val;
                    if (val > xMax) xMax = val;
                }
            }
            for (java.util.List<Double> yValues : allYValues) {
                for (Double boxed : yValues) {
                    if (boxed == null || !Double.isFinite(boxed)) {
                        continue;
                    }
                    double val = boxed;
                    if (val < yMin) yMin = val;
                    if (val > yMax) yMax = val;
                }
            }
            
            double xRange = xMax - xMin;
            double yRange = yMax - yMin;
            if (xRange == 0) xRange = 1.0;
            if (yRange == 0) yRange = 1.0;
            
            double displayXMin = (zoomXMin != null) ? zoomXMin : xMin;
            double displayXMax = (zoomXMax != null) ? zoomXMax : xMax;
            double displayYMin = (zoomYMin != null) ? zoomYMin : yMin;
            double displayYMax = (zoomYMax != null) ? zoomYMax : yMax;
            double displayXRange = displayXMax - displayXMin;
            double displayYRange = displayYMax - displayYMin;
            if (displayXRange == 0) displayXRange = 1.0;
            if (displayYRange == 0) displayYRange = 1.0;
            
            int width = panelW;
            int height = panelH;
            boolean timeX = "Time".equals(xAxisName);
            int margin = timeX ? 72 : 60;
            int chartWidth = width - 2 * margin;
            int chartHeight = height - 2 * margin - (timeX ? 80 : 60);
            
            Color gridColor = parseChartColor(chart.getGridlineColor());
            if (plotOpt != null && plotOpt.showGrid && g instanceof Graphics2D) {
                Graphics2D g2 = (Graphics2D) g;
                java.awt.Stroke prev = g2.getStroke();
                g2.setStroke(gridStroke(chart));
                g2.setColor(gridColor);
                for (int i = 1; i <= 4; i++) {
                    int vx = margin + (int) (chartWidth * i / 5);
                    g2.drawLine(vx, margin, vx, margin + chartHeight);
                    int hy = margin + (int) (chartHeight * i / 5);
                    g2.drawLine(margin, hy, margin + chartWidth, hy);
                }
                g2.setStroke(prev);
            }
            
            g.setColor(axisColor);
            g.drawLine(margin, margin + chartHeight, margin + chartWidth, margin + chartHeight);
            g.drawLine(margin, margin, margin, margin + chartHeight);
            
            for (int catalogIdx = 0; catalogIdx < allXValues.size(); catalogIdx++) {
                java.util.List<Double> xValues = allXValues.get(catalogIdx);
                java.util.List<Double> yValues = allYValues.get(catalogIdx);
                com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfoForValues.get(catalogIdx);
                Color catalogColor = catalogDrawColor(info);
                
                g.setColor(catalogColor);
                for (int i = 0; i < xValues.size(); i++) {
                    Double xBox = xValues.get(i);
                    Double yBox = yValues.get(i);
                    if (xBox == null || yBox == null || !Double.isFinite(xBox) || !Double.isFinite(yBox)) {
                        continue;
                    }
                    double xVal = xBox;
                    double yVal = yBox;
                    if (xVal < displayXMin || xVal > displayXMax || yVal < displayYMin || yVal > displayYMax) continue;
                    
                    int x = margin + (int) ((xVal - displayXMin) / displayXRange * chartWidth);
                    int y = margin + chartHeight - (int) ((yVal - displayYMin) / displayYRange * chartHeight);
                    
                    g.fillOval(x - 2, y - 2, 4, 4);
                }
            }
            
            g.setFont(tickFont);
            FontMetrics fm = g.getFontMetrics();
            
            for (int i = 0; i <= 5; i++) {
                double value = displayXMin + (displayXRange * i / 5);
                String label = timeX
                    ? com.treloc.xtreloc.util.TimeFormatConverter.formatColorBarTime(value, displayXMin, displayXMax)
                    : String.format("%.2f", value);
                int x = margin + (int) (chartWidth * i / 5) - fm.stringWidth(label) / 2;
                g.drawString(label, x, margin + chartHeight + 20);
            }
            
            for (int i = 0; i <= 5; i++) {
                double value = displayYMin + (displayYRange * i / 5);
                String label = String.format("%.2f", value);
                int y = margin + chartHeight - (int) (chartHeight * i / 5) + fm.getAscent() / 2;
                g.drawString(label, margin - fm.stringWidth(label) - 5, y);
            }

            Font axisLabelFont = chart.getAxisLabelFont();
            g.setFont(axisLabelFont);
            g.setColor(axisColor);
            FontMetrics axisFm = g.getFontMetrics();
            String xAxisLabel = xAxisName != null ? xAxisName : "X";
            String yAxisLabel = yAxisName != null ? yAxisName : "Y";
            int xLabW = axisFm.stringWidth(xAxisLabel);
            g.drawString(xAxisLabel, (width - xLabW) / 2, margin + chartHeight + 42);
            if (g instanceof Graphics2D) {
                drawRotatedYAxisLabel((Graphics2D) g, yAxisLabel, axisLabelFont, axisColor,
                    Math.max(18, margin / 3), margin + chartHeight / 2);
            }
            
            if (plotOpt == null || plotOpt.showTitle) {
                g.setFont(titleFont);
                String title = yAxisName + " vs " + xAxisName;
                FontMetrics titleFm = g.getFontMetrics();
                int titleWidth = titleFm.stringWidth(title);
                g.drawString(title, (width - titleWidth) / 2, 20);
            }

            if (plotOpt == null || plotOpt.showLegend) {
                g.setFont(legendFont);
                int legendY = margin + chartHeight + 50;
                int legendX = margin;
                int legendItemHeight = 15;

                for (int i = 0; i < catalogInfoForValues.size(); i++) {
                    com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfoForValues.get(i);
                    Color catalogColor = catalogDrawColor(info);

                    g.setColor(catalogColor);
                    g.fillOval(legendX, legendY, 8, 8);

                    g.setColor(axisColor);
                    g.drawString(info.getName(), legendX + 12, legendY + 8);

                    legendX += 120;
                    if (legendX + 120 > width - margin) {
                        legendX = margin;
                        legendY += legendItemHeight;
                    }
                }
            }
            
            if (outBounds != null) {
                outBounds.dataXMin = displayXMin;
                outBounds.dataXMax = displayXMax;
                outBounds.dataYMin = displayYMin;
                outBounds.dataYMax = displayYMax;
                outBounds.chartLeft = margin;
                outBounds.chartTop = margin;
                outBounds.chartWidth = chartWidth;
                outBounds.chartHeight = chartHeight;
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to draw scatter plot: " + e.getMessage(), e);
        }
    }
    
    /** Catalog color for chart drawing; avoids NPE when unset. */
    private static Color catalogDrawColor(com.treloc.xtreloc.app.gui.model.CatalogInfo info) {
        Color c = info.getColor();
        return c != null ? c : new Color(96, 96, 96);
    }
    
    private static double getValueForColumn(com.treloc.xtreloc.app.gui.model.Hypocenter h, String columnName) {
        switch (columnName) {
            case "Time":
                Long ms = com.treloc.xtreloc.util.TimeFormatConverter.toEpochMillis(h.time);
                return ms == null ? Double.NaN : ms.doubleValue();
            case "Latitude":
                return h.lat;
            case "Longitude":
                return h.lon;
            case "Depth (km)":
                return h.depth;
            case "xerr (km)":
                return h.xerr;
            case "yerr (km)":
                return h.yerr;
            case "zerr (km)":
                return h.zerr;
            case "rms":
                return h.rms;
            default:
                return Double.NaN;
        }
    }
    
    /**
     * Applies application settings to the UI and map view.
     * 
     * @param settings the application settings to apply
     * @param frame the main frame to update
     * @param mapView the map view to configure (may be null)
     */
    private static void applyAppSettings(com.treloc.xtreloc.app.gui.util.AppSettings settings, 
                                        JFrame frame, MapView mapView) {
        String font = settings.getFont();
        if (font != null && !font.equals("default")) {
            applyFont(font);
        }
        
        if (mapView != null) {
            String defaultPalette = settings.getDefaultPalette();
            if (defaultPalette != null) {
                MapView.ColorPalette palette = MapView.ColorPalette.BLUE_TO_RED;
                for (MapView.ColorPalette p : MapView.ColorPalette.values()) {
                    if (p.toString().equals(defaultPalette)) {
                        palette = p;
                        break;
                    }
                }
                mapView.setDefaultPalette(palette);
            }
            
            int symbolSize = settings.getSymbolSize();
            if (symbolSize > 0) {
                mapView.setSymbolSize(symbolSize);
            }
        }
        
        settings.applyTimeDisplayFormat();
        
        String logLevel = settings.getLogLevel();
        if (logLevel != null) {
            java.util.logging.Level level;
            switch (logLevel) {
                case "DEBUG":
                    level = java.util.logging.Level.FINE;
                    break;
                case "WARNING":
                    level = java.util.logging.Level.WARNING;
                    break;
                case "SEVERE":
                    level = java.util.logging.Level.SEVERE;
                    break;
                case "INFO":
                default:
                    level = java.util.logging.Level.INFO;
                    break;
            }
            java.util.logging.Logger.getLogger("").setLevel(level);
        }
        
        if (frame != null) {
            SwingUtilities.updateComponentTreeUI(frame);
        }
    }
    
    private static void applyFont(String font) {
        UiFonts.applyToUIManager(font);
    }
    
    
    /**
     * Applies the selected UI theme to the application.
     * 
     * @param themeName the theme name to apply
     */
    private static void applyTheme(String themeName) {
        try {
            String lafClassName = getLookAndFeelClassName(themeName);
            if (lafClassName != null) {
                UIManager.setLookAndFeel(lafClassName);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to apply theme: " + themeName + " - " + e.getMessage(), e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to apply system theme: " + ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Gets the Look and Feel class name for the given theme name.
     * 
     * @param themeName the theme name
     * @return the Look and Feel class name, or null if not found
     */
    private static String getLookAndFeelClassName(String themeName) {
        if (themeName == null) {
            return UIManager.getSystemLookAndFeelClassName();
        }
        
        switch (themeName) {
            case "System":
                return UIManager.getSystemLookAndFeelClassName();
            case "FlatLaf Light":
                return "com.formdev.flatlaf.FlatLightLaf";
            case "FlatLaf Dark":
                return "com.formdev.flatlaf.FlatDarkLaf";
            case "FlatLaf IntelliJ":
                return "com.formdev.flatlaf.FlatIntelliJLaf";
            case "FlatLaf Darcula":
                return "com.formdev.flatlaf.FlatDarculaLaf";
            case "FlatLaf macOS Light":
                return "com.formdev.flatlaf.themes.FlatMacLightLaf";
            case "FlatLaf macOS Dark":
                return "com.formdev.flatlaf.themes.FlatMacDarkLaf";
            case "Metal":
                return UIManager.getCrossPlatformLookAndFeelClassName();
            case "Nimbus":
                return "javax.swing.plaf.nimbus.NimbusLookAndFeel";
            case "Windows":
                return "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
            case "Windows Classic":
                return "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel";
            case "Mac OS X":
                return "com.apple.laf.AquaLookAndFeel";
            case "GTK+":
                return "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
            default:
                return UIManager.getSystemLookAndFeelClassName();
        }
    }

    private static void checkForUpdatesNow(JFrame mainFrame) {
        new Thread(() -> {
            try {
                UpdateInfo info = UpdateChecker.checkForUpdates();
                SwingUtilities.invokeLater(() -> {
                    if (info != null && info.isUpdateAvailable()) {
                        new UpdateDialog(mainFrame, info).setVisible(true);
                    } else if (info != null) {
                        JOptionPane.showMessageDialog(mainFrame, "xTreLoc is up to date.", "Check for Updates", 1);
                    } else {
                        JOptionPane.showMessageDialog(mainFrame, "Could not check for updates.", "Check for Updates", 2);
                    }
                });
            }
            catch (Exception e) {
                logger.log(Level.WARNING, "Update check failed", e);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, "Could not check for updates: " + e.getMessage(), "Check for Updates", 2));
            }
        }, "update-check").start();
    }

    private static void checkForUpdatesAsync(
            com.treloc.xtreloc.app.gui.util.AppSettings appSettings, 
            JFrame mainFrame) {
        
        if (!appSettings.isAutoUpdateEnabled()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long lastCheck = appSettings.getLastUpdateCheck();
        long oneDayInMillis = 24 * 60 * 60 * 1000;
        
        if (currentTime - lastCheck < oneDayInMillis) {
            return;
        }
        
        new Thread(() -> {
            try {
                com.treloc.xtreloc.app.gui.util.UpdateInfo updateInfo = 
                    com.treloc.xtreloc.app.gui.service.UpdateChecker.checkForUpdates();
                
                appSettings.setLastUpdateCheck(System.currentTimeMillis());
                appSettings.save();
                
                if (updateInfo != null && updateInfo.isUpdateAvailable()) {
                    SwingUtilities.invokeLater(() -> {
                        com.treloc.xtreloc.app.gui.view.UpdateDialog dialog = 
                            new com.treloc.xtreloc.app.gui.view.UpdateDialog(mainFrame, updateInfo);
                        dialog.setVisible(true);
                    });
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during automatic update check", e);
            }
        }).start();
    }
}

