package com.treloc.xtreloc.app.gui;

import com.treloc.xtreloc.app.gui.view.MapView;
import com.treloc.xtreloc.app.gui.view.ViewerStatusBar;
import com.treloc.xtreloc.app.gui.controller.MapController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.swing.JMapFrame;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.GuiExecutionLog;
import com.treloc.xtreloc.app.gui.util.AppSettings;
import com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings;
import com.treloc.xtreloc.app.gui.util.ChartImageExport;
import com.treloc.xtreloc.app.gui.util.ColorScaleUtils;

public class XTreLocGUI {

    private static final Logger logger = Logger.getLogger(XTreLocGUI.class.getName());
    private static final int REPAINT_DEBOUNCE_MS = 100;
    /** Right pane background on Solver tab (matches convergence log dock; avoids a light strip under the log). */
    private static final Color SOLVER_RIGHT_PANE_BG = new Color(20, 20, 30);

    private static boolean isMainViewerTabSelected(JTabbedPane mainTabs) {
        int i = mainTabs.getSelectedIndex();
        return i >= 0 && "Viewer".equalsIgnoreCase(mainTabs.getTitleAt(i));
    }

    private static boolean isViewerSynMapOnly(JTabbedPane mainTabs,
            com.treloc.xtreloc.app.gui.view.HypocenterLocationPanel locPanel) {
        if (!isMainViewerTabSelected(mainTabs)) {
            return false;
        }
        return locPanel != null && "SYN".equals(locPanel.getSolverModeAbbreviation());
    }

    /**
     * When Viewer is active and solver mode is SYN (Create Synthetic data), hides catalog/shapefile pane
     * and Hist/Scatter so only the map remains. Restores when leaving that state.
     */
    private static void updateViewerSynMapOnlyUi(
            com.treloc.xtreloc.app.gui.view.HypocenterLocationPanel locationPanel,
            JTabbedPane mainTabbedPane,
            JPanel leftPanelContainer,
            JComponent excelPane,
            JTabbedPane viewerRightTabs,
            JPanel histogramPanel,
            JPanel scatterPanel,
            JSplitPane mainSplit,
            int[] dividerLocationByTab,
            int[] synMainSplitBackup,
            int[] defaultMainSplitDividerSize) {
        boolean want = isViewerSynMapOnly(mainTabbedPane, locationPanel);
        if (!want) {
            if (viewerRightTabs != null && viewerRightTabs.getTabCount() == 1
                    && histogramPanel != null && scatterPanel != null) {
                viewerRightTabs.addTab("Hist", histogramPanel);
                viewerRightTabs.addTab("Scatter", scatterPanel);
            }
            if (leftPanelContainer != null && excelPane != null && isMainViewerTabSelected(mainTabbedPane)) {
                if (leftPanelContainer.getComponentCount() != 1
                        || leftPanelContainer.getComponent(0) != excelPane) {
                    leftPanelContainer.removeAll();
                    leftPanelContainer.add(excelPane, BorderLayout.CENTER);
                    leftPanelContainer.revalidate();
                    leftPanelContainer.repaint();
                }
            }
            if (mainSplit != null) {
                int divSz = defaultMainSplitDividerSize[0] > 0 ? defaultMainSplitDividerSize[0] : 5;
                mainSplit.setDividerSize(divSz);
            }
            if (synMainSplitBackup[0] >= 50) {
                dividerLocationByTab[1] = synMainSplitBackup[0];
                synMainSplitBackup[0] = -1;
            }
            return;
        }
        if (leftPanelContainer != null) {
            if (leftPanelContainer.getComponentCount() > 0) {
                leftPanelContainer.removeAll();
                leftPanelContainer.revalidate();
                leftPanelContainer.repaint();
            }
        }
        if (viewerRightTabs != null) {
            while (viewerRightTabs.getTabCount() > 1) {
                viewerRightTabs.removeTabAt(viewerRightTabs.getTabCount() - 1);
            }
            viewerRightTabs.setSelectedIndex(0);
        }
        if (mainSplit != null) {
            if (mainSplit.getDividerSize() > 0) {
                defaultMainSplitDividerSize[0] = mainSplit.getDividerSize();
            }
            int loc = mainSplit.getDividerLocation();
            if (loc > 30) {
                synMainSplitBackup[0] = loc;
            }
            mainSplit.setDividerSize(0);
            mainSplit.setDividerLocation(0);
        }
    }
    
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
        com.treloc.xtreloc.app.gui.util.AppSettings appSettings = null;
        try {
            appSettings = com.treloc.xtreloc.app.gui.util.AppSettings.load();
        } catch (Exception e) {
            logger.log(Level.FINE, "Could not load settings, using defaults: " + e.getMessage());
        }
        try {
            File logFile = com.treloc.xtreloc.app.gui.util.LogHistoryManager.getLogFile();
            if (appSettings != null) {
                com.treloc.xtreloc.util.LogInitializer.setup(
                    logFile.getAbsolutePath(),
                    java.util.logging.Level.INFO,
                    appSettings.getLogLimitBytes(),
                    appSettings.getLogCount()
                );
            } else {
                com.treloc.xtreloc.util.LogInitializer.setup(
                    logFile.getAbsolutePath(),
                    java.util.logging.Level.INFO
                );
            }
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger("com.treloc.xtreloc");
            logger.info("========================================");
            logger.info("xTreLoc application started");
            logger.info("Log file: " + logFile.getAbsolutePath());
            logger.info("========================================");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize logging: " + e.getMessage(), e);
        }
        
        final com.treloc.xtreloc.app.gui.util.AppSettings appSettingsForUI =
            (appSettings != null) ? appSettings : com.treloc.xtreloc.app.gui.util.AppSettings.load();
        String theme = appSettingsForUI.getTheme();
        if (theme != null) {
            applyTheme(theme);
        }

        SwingUtilities.invokeLater(() -> {
            try {
                String version = com.treloc.xtreloc.app.gui.util.VersionInfo.getVersion();
                JFrame mainFrame = new JFrame("xTreLoc " + version);
                mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                mainFrame.setLayout(new BorderLayout());
                
                try {
                    java.awt.Image logoImg = com.treloc.xtreloc.app.gui.util.BundledImageLoader.loadImage("logo.png");
                    if (logoImg != null) {
                        mainFrame.setIconImage(logoImg);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to load logo: " + e.getMessage(), e);
                }
                
                MapView view = new MapView();
                MapController ctrl = new MapController(view);
                
                applyAppSettings(appSettingsForUI, mainFrame, view);
                checkForUpdatesAsync(appSettingsForUI, mainFrame);
                
                com.treloc.xtreloc.io.AppConfig config = null;
                try {
                    com.treloc.xtreloc.io.ConfigLoader loader = 
                        new com.treloc.xtreloc.io.ConfigLoader("config.json");
                    config = loader.getConfig();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to load configuration file: " + e.getMessage(), e);
                    String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                    if (msg.contains("No such file") || msg.contains("missing or invalid") || msg.length() > 150) {
                        final String message = msg;
                        SwingUtilities.invokeLater(() -> {
                            JTextArea area = new JTextArea(message, 14, 70);
                            area.setLineWrap(true);
                            area.setWrapStyleWord(true);
                            area.setEditable(false);
                            JOptionPane.showMessageDialog(mainFrame,
                                new JScrollPane(area),
                                "Config load failed", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }
                
                com.treloc.xtreloc.app.gui.view.HypocenterLocationPanel locationPanel = 
                    new com.treloc.xtreloc.app.gui.view.HypocenterLocationPanel(view);
                GuiExecutionLog.setSink(locationPanel::appendExecutionLog);
                if (config != null) {
                    locationPanel.setConfig(config);
                }
                javax.swing.JComponent solverLeftPanel = locationPanel.getLeftPanel();
                
                com.treloc.xtreloc.app.gui.view.DataViewPanel dataViewPanel = 
                    new com.treloc.xtreloc.app.gui.view.DataViewPanel(view, ctrl);
                JTabbedPane excelPane = dataViewPanel.getTabbedPane();
                JMapFrame mapFrame = view.getFrame();
                
                com.treloc.xtreloc.app.gui.view.TravelTimeDataPanel travelTimeDataPanel = 
                    new com.treloc.xtreloc.app.gui.view.TravelTimeDataPanel();
                
                com.treloc.xtreloc.app.gui.view.CatalogTablePanel catalogPanel = 
                    dataViewPanel.getCatalogPanel();
                catalogPanel.setTravelTimeDataPanel(travelTimeDataPanel);
                locationPanel.setSolverResultCatalogPanel(catalogPanel);
                
                com.treloc.xtreloc.app.gui.view.ReportPanel reportPanel = 
                    new com.treloc.xtreloc.app.gui.view.ReportPanel(view);
                
                com.treloc.xtreloc.app.gui.view.SettingsPanel settingsPanel =
                    new com.treloc.xtreloc.app.gui.view.SettingsPanel(view, mainFrame);
                
                com.treloc.xtreloc.app.gui.view.WaveformPickingPanel pickingPanel = 
                    new com.treloc.xtreloc.app.gui.view.WaveformPickingPanel();
                
                catalogPanel.addCatalogLoadListener(hypocenters -> {
                    reportPanel.setHypocenters(hypocenters);
                });
                
                JTabbedPane viewerRightTabs = new JTabbedPane();
                viewerRightTabs.setBackground(AppPanelStyle.getPanelBg());
                viewerRightTabs.setForeground(AppPanelStyle.getContentTextColor());
                viewerRightTabs.setMinimumSize(new java.awt.Dimension(0, 0));
                JPanel mapTabContent = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(mapTabContent);
                mapTabContent.add(view.getMapTopPanel(), BorderLayout.NORTH);
                mapTabContent.add(mapFrame.getContentPane(), BorderLayout.CENTER);
                viewerRightTabs.addTab("Map", mapTabContent);
                
                JPanel histogramPanel = createHistogramPanel(catalogPanel, view);
                viewerRightTabs.addTab("Hist", histogramPanel);
                
                JPanel scatterPanel = createScatterPanel(catalogPanel, view);
                viewerRightTabs.addTab("Scatter", scatterPanel);
                
                Runnable screeningRepaint = debouncedRepaint(histogramPanel, scatterPanel);
                catalogPanel.addScreeningChangeListener(screeningRepaint);
                
                JPanel viewerRightPanel = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(viewerRightPanel);
                viewerRightPanel.add(viewerRightTabs, BorderLayout.CENTER);
                viewerRightPanel.setMinimumSize(new java.awt.Dimension(0, 0));
                
                com.treloc.xtreloc.app.gui.view.MicrosoftStyleTabbedPane mainTabbedPane = 
                    new com.treloc.xtreloc.app.gui.view.MicrosoftStyleTabbedPane();
                
                JPanel solverLogPanel = locationPanel.getLogPanel();
                JPanel solverConvergencePanel = locationPanel.getConvergenceDockPanel();
                
                JPanel executionDock = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(executionDock);
                executionDock.add(solverLogPanel, BorderLayout.CENTER);
                executionDock.setPreferredSize(new java.awt.Dimension(0, 220));
                
                JPanel leftPanelContainer = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(leftPanelContainer);
                leftPanelContainer.add(solverLeftPanel, BorderLayout.CENTER);
                
                JPanel rightPanelContainer = new JPanel(new BorderLayout());
                rightPanelContainer.setOpaque(true);
                rightPanelContainer.setBackground(SOLVER_RIGHT_PANE_BG);
                rightPanelContainer.add(solverConvergencePanel, BorderLayout.CENTER);
                rightPanelContainer.setMinimumSize(new java.awt.Dimension(280, 0));
                
                mainTabbedPane.addTab("Solver", new JPanel());
                mainTabbedPane.addTab("Viewer", new JPanel());
                mainTabbedPane.addTab("Picking", new JPanel());
                mainTabbedPane.addTab("Settings", new JPanel());
                
                JPanel leftSidePanel = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(leftSidePanel);
                leftSidePanel.add(mainTabbedPane, BorderLayout.NORTH);
                leftSidePanel.add(leftPanelContainer, BorderLayout.CENTER);
                leftSidePanel.setMinimumSize(new java.awt.Dimension(200, 0));
                
                JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    leftSidePanel, rightPanelContainer);
                mainSplit.setResizeWeight(0.3);
                mainSplit.setOneTouchExpandable(true);
                mainSplit.setDividerLocation(500);
                mainSplit.setBackground(AppPanelStyle.getPanelBg());
                
                final JSplitPane rootVertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainSplit, executionDock);
                rootVertical.setResizeWeight(0.82);
                rootVertical.setOneTouchExpandable(true);
                rootVertical.setBackground(AppPanelStyle.getPanelBg());
                // タブごとに左右分割位置を保存 (Solver=0, Viewer=1, Picking=2, Settings=3は復元しない)
                final int[] dividerLocationByTab = { 500, 500, 500 };
                final int[] previousTabIndex = { 0 };
                final int[] synMainSplitBackup = { -1 };
                final int[] defaultMainSplitDividerSize = { 5 };
                {
                    int ds0 = UIManager.getInt("SplitPane.dividerSize");
                    if (ds0 > 0) {
                        defaultMainSplitDividerSize[0] = ds0;
                    }
                }

                mainTabbedPane.addChangeListener(e -> {
                    int selectedIndex = mainTabbedPane.getSelectedIndex();
                    String selectedTitle = selectedIndex >= 0 ? mainTabbedPane.getTitleAt(selectedIndex) : "";
                    int prev = previousTabIndex[0];

                    // 離れるタブの分割位置を保存（Settings 以外）
                    if (prev >= 0 && prev <= 2) {
                        if (prev == 1) {
                            int loc = mainSplit.getDividerLocation();
                            if (loc >= 50) {
                                dividerLocationByTab[1] = loc;
                            }
                        } else {
                            dividerLocationByTab[prev] = mainSplit.getDividerLocation();
                        }
                    }
                    previousTabIndex[0] = selectedIndex;

                    // 表示切替
                    if ("Settings".equalsIgnoreCase(selectedTitle)) {
                        mainSplit.setDividerLocation(1.0);
                    } else {
                        final int saved = (selectedIndex >= 0 && selectedIndex <= 2) ? dividerLocationByTab[selectedIndex] : 500;
                        SwingUtilities.invokeLater(() -> {
                            updateViewerSynMapOnlyUi(locationPanel, mainTabbedPane, leftPanelContainer, excelPane,
                                viewerRightTabs, histogramPanel, scatterPanel, mainSplit, dividerLocationByTab,
                                synMainSplitBackup, defaultMainSplitDividerSize);
                            if (isViewerSynMapOnly(mainTabbedPane, locationPanel)) {
                                return;
                            }
                            int maxLoc = Math.max(200, mainSplit.getSize().width - 50);
                            mainSplit.setDividerLocation(Math.min(Math.max(200, saved), maxLoc));
                        });
                    }

                    leftPanelContainer.removeAll();
                    if ("Solver".equalsIgnoreCase(selectedTitle)) {
                        leftPanelContainer.add(solverLeftPanel, BorderLayout.CENTER);
                    } else if ("Viewer".equalsIgnoreCase(selectedTitle)) {
                        leftPanelContainer.add(excelPane, BorderLayout.CENTER);
                    } else if ("Picking".equalsIgnoreCase(selectedTitle)) {
                        leftPanelContainer.add(pickingPanel.getLeftPanel(), BorderLayout.CENTER);
                    } else if ("Settings".equalsIgnoreCase(selectedTitle)) {
                        JPanel settingsWrapper = new JPanel(new BorderLayout());
                        AppPanelStyle.setPanelBackground(settingsWrapper);
                        settingsWrapper.add(settingsPanel, BorderLayout.CENTER);
                        leftPanelContainer.add(settingsWrapper, BorderLayout.CENTER);
                    }
                    leftPanelContainer.revalidate();
                    leftPanelContainer.repaint();
                    
                    rightPanelContainer.removeAll();
                    if ("Solver".equalsIgnoreCase(selectedTitle)) {
                        rightPanelContainer.setOpaque(true);
                        rightPanelContainer.setBackground(SOLVER_RIGHT_PANE_BG);
                        rightPanelContainer.add(solverConvergencePanel, BorderLayout.CENTER);
                    } else if ("Viewer".equalsIgnoreCase(selectedTitle)) {
                        AppPanelStyle.setPanelBackground(rightPanelContainer);
                        rightPanelContainer.add(viewerRightPanel, BorderLayout.CENTER);
                    } else if ("Picking".equalsIgnoreCase(selectedTitle)) {
                        AppPanelStyle.setPanelBackground(rightPanelContainer);
                        rightPanelContainer.add(pickingPanel.getRightPanel(), BorderLayout.CENTER);
                    } else if ("Settings".equalsIgnoreCase(selectedTitle)) {
                        AppPanelStyle.setPanelBackground(rightPanelContainer);
                        rightPanelContainer.add(createThemedPanel(), BorderLayout.CENTER);
                    }
                    rightPanelContainer.revalidate();
                    rightPanelContainer.repaint();
                });

                locationPanel.addSolverModeChangeListener(() -> {
                    if (!isMainViewerTabSelected(mainTabbedPane)) {
                        return;
                    }
                    SwingUtilities.invokeLater(() -> {
                        updateViewerSynMapOnlyUi(locationPanel, mainTabbedPane, leftPanelContainer, excelPane,
                            viewerRightTabs, histogramPanel, scatterPanel, mainSplit, dividerLocationByTab,
                            synMainSplitBackup, defaultMainSplitDividerSize);
                        if (isViewerSynMapOnly(mainTabbedPane, locationPanel)) {
                            return;
                        }
                        int maxLoc = Math.max(200, mainSplit.getSize().width - 50);
                        int saved = dividerLocationByTab[1];
                        mainSplit.setDividerLocation(Math.min(Math.max(200, saved), maxLoc));
                    });
                });
                
                mainFrame.add(rootVertical, BorderLayout.CENTER);
                ViewerStatusBar.setRootFrame(mainFrame);

                mainFrame.pack();
                mainFrame.setSize(1800, 850);
                mainFrame.setLocationRelativeTo(null);
                mainFrame.setVisible(true);
                
                SwingUtilities.invokeLater(() -> {
                    mainSplit.setDividerLocation(500);
                    int rh = rootVertical.getHeight();
                    if (rh > 100) {
                        rootVertical.setDividerLocation((int) (rh * 0.78));
                    }
                });
                
                mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error initializing GUI: " + e.getMessage(), e);
                JOptionPane.showMessageDialog(null,
                    "An error occurred: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    
    /**
     * Creates histogram panel (supports catalog comparison)
     */
    private static JPanel createHistogramPanel(com.treloc.xtreloc.app.gui.view.CatalogTablePanel catalogPanel, MapView mapView) {
        JPanel histogramPanelWrapper = new JPanel(new BorderLayout());
        com.treloc.xtreloc.app.gui.util.AppPanelStyle.setPanelBackground(histogramPanelWrapper);
        histogramPanelWrapper.setBorder(com.treloc.xtreloc.app.gui.util.AppPanelStyle.createTitledSectionBorder("Histogram"));
        
        final Double[] zoomMin = { null }, zoomMax = { null };
        final HistChartBounds histBounds = new HistChartBounds();
        final int[] dragStart = { -1, -1 }, dragEnd = { -1, -1 };
        
        JPanel histogramPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawHistogram(g, catalogPanel, mapView, zoomMin[0], zoomMax[0], histBounds);
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
        
        return histogramPanelWrapper;
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
                                     MapView mapView, Double zoomMin, Double zoomMax, HistChartBounds outBounds) {
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
            if (selectedColumnIndex < 2 || selectedColumnIndex > 8) {
                g.setColor(axisColor);
                g.setFont(tickFont);
                FontMetrics fm = g.getFontMetrics();
                String message = "Select a numeric column (Latitude … rms) in the catalog table header";
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
            if (g instanceof Graphics2D) {
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
                String message = "No data available";
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
                String label = String.format("%.2f", value);
                int x = margin + (int) (chartWidth * i / 5) - fm.stringWidth(label) / 2;
                g.drawString(label, x, margin + chartHeight + 20);
            }
            for (int i = 0; i <= 5; i++) {
                int freq = maxFreq * i / 5;
                String label = String.valueOf(freq);
                int y = margin + chartHeight - (chartHeight * i / 5) + fm.getAscent() / 2;
                g.drawString(label, margin - fm.stringWidth(label) - 5, y);
            }
            
            String title = selectedColumnName + " Histogram";
            g.setFont(titleFont);
            FontMetrics titleFm = g.getFontMetrics();
            int titleWidth = titleFm.stringWidth(title);
            g.drawString(title, (width - titleWidth) / 2, 20);
            
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
        
        final Double[] zoomXMin = { null }, zoomXMax = { null }, zoomYMin = { null }, zoomYMax = { null };
        final ScatterChartBounds scatterBounds = new ScatterChartBounds();
        final int[] dragStart = { -1, -1 }, dragEnd = { -1, -1 };
        
        JPanel plotPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawScatterPlot(g, catalogPanel, mapView,
                    zoomXMin[0], zoomXMax[0], zoomYMin[0], zoomYMax[0], scatterBounds);
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
        
        return scatterPanel;
    }
    
    private static void drawScatterPlot(Graphics g, com.treloc.xtreloc.app.gui.view.CatalogTablePanel catalogPanel,
                                       MapView mapView,
                                       Double zoomXMin, Double zoomXMax, Double zoomYMin, Double zoomYMax,
                                       ScatterChartBounds outBounds) {
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
            if (xAxisName == null || yAxisName == null || xCol < 2 || xCol > 8 || yCol < 2 || yCol > 8) {
                g.setColor(axisColor);
                g.setFont(tickFont);
                FontMetrics fm = g.getFontMetrics();
                String message = "Right-click a catalog column header: set Scatter X and Y (Latitude … rms)";
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
            int margin = 60;
            int chartWidth = width - 2 * margin;
            int chartHeight = height - 2 * margin - 60;
            
            Color gridColor = parseChartColor(chart.getGridlineColor());
            if (g instanceof Graphics2D) {
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
                String label = String.format("%.2f", value);
                int x = margin + (int) (chartWidth * i / 5) - fm.stringWidth(label) / 2;
                g.drawString(label, x, margin + chartHeight + 20);
            }
            
            for (int i = 0; i <= 5; i++) {
                double value = displayYMin + (displayYRange * i / 5);
                String label = String.format("%.2f", value);
                int y = margin + chartHeight - (int) (chartHeight * i / 5) + fm.getAscent() / 2;
                g.drawString(label, margin - fm.stringWidth(label) - 5, y);
            }
            
            g.setFont(titleFont);
            String title = yAxisName + " vs " + xAxisName;
            FontMetrics titleFm = g.getFontMetrics();
            int titleWidth = titleFm.stringWidth(title);
            g.drawString(title, (width - titleWidth) / 2, 20);
            
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
        // Theme is already applied in main() before UI creation
        
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
        Font selectedFont;
        switch (font) {
            case "Sans Serif":
                selectedFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
                break;
            case "Serif":
                selectedFont = new Font(Font.SERIF, Font.PLAIN, 12);
                break;
            case "Monospaced":
                selectedFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
                break;
            default:
                selectedFont = UIManager.getFont("Label.font");
        }
        UIManager.put("Label.font", selectedFont);
        UIManager.put("Button.font", selectedFont);
        UIManager.put("TextField.font", selectedFont);
        UIManager.put("ComboBox.font", selectedFont);
    }
    
    private static JPanel createThemedPanel() {
        JPanel p = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(p);
        return p;
    }
    
    /**
     * Applies the selected UI theme to the application.
     * 
     * @param themeName the theme name to apply
     */
    private static void applyTheme(String themeName) {
        try {
            // Standard Look and Feel themes
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

