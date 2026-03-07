package com.treloc.xtreloc.app.gui;

import com.treloc.xtreloc.app.gui.view.MapView;
import com.treloc.xtreloc.app.gui.view.ViewerStatusBar;
import com.treloc.xtreloc.app.gui.controller.MapController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.swing.JMapFrame;

import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.AppSettings;
import com.treloc.xtreloc.app.gui.util.ChartAppearanceSettings;

public class XTreLocGUI {

    private static final Logger logger = Logger.getLogger(XTreLocGUI.class.getName());
    private static final int REPAINT_DEBOUNCE_MS = 100;
    
    /** Returns a runnable that debounces repaint of the given panel (at most once per REPAINT_DEBOUNCE_MS). */
    private static Runnable debouncedRepaint(JPanel panel) {
        final long[] lastTime = { 0 };
        final javax.swing.Timer[] timerRef = { null };
        return () -> {
            long now = System.currentTimeMillis();
            if (now - lastTime[0] >= REPAINT_DEBOUNCE_MS) {
                lastTime[0] = now;
                panel.repaint();
                return;
            }
            if (timerRef[0] == null || !timerRef[0].isRunning()) {
                timerRef[0] = new javax.swing.Timer(REPAINT_DEBOUNCE_MS, e -> {
                    lastTime[0] = System.currentTimeMillis();
                    panel.repaint();
                });
                timerRef[0].setRepeats(false);
                timerRef[0].start();
            }
        };
    }
    
    /** Debounces repaint of multiple panels as one (at most once per REPAINT_DEBOUNCE_MS). */
    private static Runnable debouncedRepaint(JPanel panel1, JPanel panel2) {
        final long[] lastTime = { 0 };
        final javax.swing.Timer[] timerRef = { null };
        return () -> {
            long now = System.currentTimeMillis();
            if (now - lastTime[0] >= REPAINT_DEBOUNCE_MS) {
                lastTime[0] = now;
                panel1.repaint();
                panel2.repaint();
                return;
            }
            if (timerRef[0] == null || !timerRef[0].isRunning()) {
                timerRef[0] = new javax.swing.Timer(REPAINT_DEBOUNCE_MS, e -> {
                    lastTime[0] = System.currentTimeMillis();
                    panel1.repaint();
                    panel2.repaint();
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
                    File logoFile = com.treloc.xtreloc.app.gui.util.AppDirectoryManager.getLogoFile();
                    if (logoFile.exists()) {
                        ImageIcon logoIcon = new ImageIcon(logoFile.getAbsolutePath());
                        mainFrame.setIconImage(logoIcon.getImage());
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
                
                JSplitPane leftPanelSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    excelPane, travelTimeDataPanel);
                leftPanelSplit.setResizeWeight(0.7);
                leftPanelSplit.setDividerLocation(400);
                
                JSplitPane viewerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    leftPanelSplit, mapFrame.getContentPane());
                viewerSplit.setResizeWeight(0.5);
                viewerSplit.setDividerLocation(400);
                
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
                
                JPanel scatterPanel = createScatterPanel(reportPanel, view);
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
                
                JPanel leftPanelContainer = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(leftPanelContainer);
                leftPanelContainer.add(solverLeftPanel, BorderLayout.CENTER);
                
                JPanel rightPanelContainer = new JPanel(new BorderLayout());
                AppPanelStyle.setPanelBackground(rightPanelContainer);
                rightPanelContainer.add(solverLogPanel, BorderLayout.CENTER);
                rightPanelContainer.setMinimumSize(new java.awt.Dimension(80, 0));
                
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
                // タブごとに左右分割位置を保存 (Solver=0, Viewer=1, Picking=2, Settings=3は復元しない)
                final int[] dividerLocationByTab = { 500, 500, 500 };
                final int[] previousTabIndex = { 0 };

                mainTabbedPane.addChangeListener(e -> {
                    int selectedIndex = mainTabbedPane.getSelectedIndex();
                    String selectedTitle = selectedIndex >= 0 ? mainTabbedPane.getTitleAt(selectedIndex) : "";
                    int prev = previousTabIndex[0];

                    // 離れるタブの分割位置を保存（Settings 以外）
                    if (prev >= 0 && prev <= 2) {
                        dividerLocationByTab[prev] = mainSplit.getDividerLocation();
                    }
                    previousTabIndex[0] = selectedIndex;

                    // 表示切替
                    if ("Settings".equalsIgnoreCase(selectedTitle)) {
                        mainSplit.setDividerLocation(1.0);
                    } else {
                        final int saved = (selectedIndex >= 0 && selectedIndex <= 2) ? dividerLocationByTab[selectedIndex] : 500;
                        SwingUtilities.invokeLater(() -> {
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
                        rightPanelContainer.add(solverLogPanel, BorderLayout.CENTER);
                    } else if ("Viewer".equalsIgnoreCase(selectedTitle)) {
                        rightPanelContainer.add(viewerRightPanel, BorderLayout.CENTER);
                    } else if ("Picking".equalsIgnoreCase(selectedTitle)) {
                        rightPanelContainer.add(pickingPanel.getRightPanel(), BorderLayout.CENTER);
                    } else if ("Settings".equalsIgnoreCase(selectedTitle)) {
                        rightPanelContainer.add(createThemedPanel(), BorderLayout.CENTER);
                    }
                    rightPanelContainer.revalidate();
                    rightPanelContainer.repaint();
                });
                
                mainFrame.add(mainSplit, BorderLayout.CENTER);
                ViewerStatusBar.setRootFrame(mainFrame);

                mainFrame.pack();
                mainFrame.setSize(1800, 850);
                mainFrame.setLocationRelativeTo(null);
                mainFrame.setVisible(true);
                
                SwingUtilities.invokeLater(() -> {
                    viewerSplit.setDividerLocation(400);
                    mainSplit.setDividerLocation(500);
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
        
        JPanel columnSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        com.treloc.xtreloc.app.gui.util.AppPanelStyle.setPanelBackground(columnSelectionPanel);
        columnSelectionPanel.setBorder(com.treloc.xtreloc.app.gui.util.AppPanelStyle.createTitledSectionBorder("Select Parameter (Single Selection)"));
        
        String[] columnNames = {"Latitude", "Longitude", "Depth (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms"};
        java.util.List<JRadioButton> columnRadioButtons = new java.util.ArrayList<>();
        ButtonGroup columnGroup = new ButtonGroup();
        
        for (String columnName : columnNames) {
            JRadioButton radioButton = new JRadioButton(columnName);
            columnRadioButtons.add(radioButton);
            columnGroup.add(radioButton);
            columnSelectionPanel.add(radioButton);
        }
        
        final Double[] zoomMin = { null }, zoomMax = { null };
        final HistChartBounds histBounds = new HistChartBounds();
        final int[] dragStart = { -1, -1 }, dragEnd = { -1, -1 };
        
        JPanel histogramPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawHistogram(g, catalogPanel, columnRadioButtons, columnNames, mapView, zoomMin[0], zoomMax[0], histBounds);
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
        histogramPanel.setBackground(com.treloc.xtreloc.app.gui.util.AppPanelStyle.getContentBg());
        Runnable histRepaint = debouncedRepaint(histogramPanel);
        
        histogramPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
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
        
        for (JRadioButton radioButton : columnRadioButtons) {
            radioButton.addActionListener(e -> histRepaint.run());
        }
        
        JButton saveImageButton = new JButton("Save image");
        saveImageButton.addActionListener(e -> exportPanelToImage(histogramPanel, histogramPanelWrapper, "histogram.png"));
        columnSelectionPanel.add(saveImageButton);
        JButton resetZoomButton = new JButton("Reset zoom");
        resetZoomButton.setToolTipText("Show full range. You can also double-click the chart to reset zoom.");
        resetZoomButton.addActionListener(e -> {
            zoomMin[0] = null;
            zoomMax[0] = null;
            histRepaint.run();
        });
        columnSelectionPanel.add(resetZoomButton);
        
        if (mapView != null) {
            mapView.addCatalogVisibilityChangeListener(histRepaint);
        }
        
        histogramPanelWrapper.add(columnSelectionPanel, BorderLayout.NORTH);
        histogramPanelWrapper.add(histogramPanel, BorderLayout.CENTER);
        
        return histogramPanelWrapper;
    }
    
    private static void exportPanelToImage(JPanel panel, java.awt.Component parent, String defaultName) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(defaultName));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        String path = f.getAbsolutePath();
        if (!path.toLowerCase().endsWith(".png") && !path.toLowerCase().endsWith(".jpg") && !path.toLowerCase().endsWith(".jpeg")) {
            f = new File(path + ".png");
        }
        try {
            int w = panel.getWidth() > 0 ? panel.getWidth() : 600;
            int h = panel.getHeight() > 0 ? panel.getHeight() : 450;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setColor(java.awt.Color.WHITE);
            g2.fillRect(0, 0, w, h);
            panel.paint(g2);
            g2.dispose();
            String ext = f.getName().toLowerCase();
            if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) {
                try {
                    javax.imageio.ImageIO.write(img, "JPEG", f);
                } catch (Exception jpegEx) {
                    File pngFile = new File(f.getParent(), f.getName().replaceFirst("\\.[jJ][pP][eE]?[gG]$", ".png"));
                    javax.imageio.ImageIO.write(img, "PNG", pngFile);
                    f = pngFile;
                }
            } else {
                javax.imageio.ImageIO.write(img, "PNG", f);
            }
            JOptionPane.showMessageDialog(parent, "Saved: " + f.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private static Color parseChartColor(String hex) {
        if (hex == null || hex.isEmpty()) return Color.BLACK;
        try {
            return Color.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (Exception e) {
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
                                     java.util.List<JRadioButton> columnRadioButtons, String[] columnNames,
                                     MapView mapView, Double zoomMin, Double zoomMax, HistChartBounds outBounds) {
        if (outBounds != null) outBounds.chartWidth = 0;
        try {
            ChartAppearanceSettings chart = AppSettings.load().getChartAppearance();
            Font titleFont = chart.getTitleFont();
            Font tickFont = chart.getTickLabelFont();
            Font legendFont = chart.getLegendFont();
            Color bgColor = parseChartColor(chart.getBackgroundColor());
            Color axisColor = parseChartColor(chart.getAxisLineColor());
            
            int selectedColumnIndex = -1;
            String selectedColumnName = null;
            for (int i = 0; i < columnRadioButtons.size() && i < columnNames.length; i++) {
                if (columnRadioButtons.get(i).isSelected()) {
                    selectedColumnIndex = i + 2;
                    selectedColumnName = columnNames[i];
                    break;
                }
            }
            
            if (selectedColumnIndex == -1) {
                g.setColor(axisColor);
                g.setFont(tickFont);
                FontMetrics fm = g.getFontMetrics();
                String message = "Please select a parameter";
                int x = (g.getClipBounds().width - fm.stringWidth(message)) / 2;
                int y = g.getClipBounds().height / 2;
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
                int x = (g.getClipBounds().width - fm.stringWidth(message)) / 2;
                int y = g.getClipBounds().height / 2;
                g.drawString(message, x, y);
                return;
            }
            
            int width = g.getClipBounds().width;
            int height = g.getClipBounds().height;
            
            int margin = 50;
            int chartWidth = width - 2 * margin;
            int chartHeight = height - 2 * margin - 60;
            
            g.setColor(bgColor);
            g.fillRect(0, 0, width, height);
            
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
            
            double globalMin = Double.MAX_VALUE;
            double globalMax = Double.MIN_VALUE;
            java.util.List<java.util.List<Double>> allValues = new java.util.ArrayList<>();
            java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> catalogInfoForValues = new java.util.ArrayList<>();
            
            for (com.treloc.xtreloc.app.gui.model.CatalogInfo info : selectedCatalogs) {
                java.util.List<Double> values = getColumnValuesForHistogram(info.getHypocenters(), selectedColumnIndex);
                if (!values.isEmpty()) {
                    java.util.Collections.sort(values);
                    double min = values.get(0);
                    double max = values.get(values.size() - 1);
                    if (min < globalMin) globalMin = min;
                    if (max > globalMax) globalMax = max;
                    allValues.add(values);
                    catalogInfoForValues.add(info);
                }
            }
            
            if (globalMin == Double.MAX_VALUE) {
                g.setColor(axisColor);
                g.setFont(tickFont);
                FontMetrics fm = g.getFontMetrics();
                String message = "No data available";
                int x = (g.getClipBounds().width - fm.stringWidth(message)) / 2;
                int y = g.getClipBounds().height / 2;
                g.drawString(message, x, y);
                return;
            }
            
            double displayMin = (zoomMin != null) ? zoomMin : globalMin;
            double displayMax = (zoomMax != null) ? zoomMax : globalMax;
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
                for (double value : values) {
                    if (value < displayMin || value > displayMax) continue;
                    int binIndex = (int) Math.min((value - displayMin) / binWidth, numBins - 1);
                    if (binIndex >= 0) bins[binIndex]++;
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
                Color catalogColor = info.getColor();
                
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
                Color catalogColor = info.getColor();
                
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
    
    private static JPanel createScatterPanel(com.treloc.xtreloc.app.gui.view.ReportPanel reportPanel, MapView mapView) {
        JPanel scatterPanel = new JPanel(new BorderLayout());
        com.treloc.xtreloc.app.gui.util.AppPanelStyle.setPanelBackground(scatterPanel);
        scatterPanel.setBorder(com.treloc.xtreloc.app.gui.util.AppPanelStyle.createTitledSectionBorder("Scatter Plot"));
        
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        com.treloc.xtreloc.app.gui.util.AppPanelStyle.setPanelBackground(controlPanel);
        controlPanel.add(new JLabel("X-axis:"));
        JComboBox<String> xAxisCombo = new JComboBox<>(new String[]{"Latitude", "Longitude", "Depth (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms"});
        controlPanel.add(xAxisCombo);
        controlPanel.add(new JLabel("Y-axis:"));
        JComboBox<String> yAxisCombo = new JComboBox<>(new String[]{"Latitude", "Longitude", "Depth (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms"});
        yAxisCombo.setSelectedIndex(2);
        controlPanel.add(yAxisCombo);
        
        final Double[] zoomXMin = { null }, zoomXMax = { null }, zoomYMin = { null }, zoomYMax = { null };
        final ScatterChartBounds scatterBounds = new ScatterChartBounds();
        final int[] dragStart = { -1, -1 }, dragEnd = { -1, -1 };
        
        JPanel plotPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawScatterPlot(g, reportPanel, xAxisCombo, yAxisCombo, mapView,
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
        plotPanel.setBackground(com.treloc.xtreloc.app.gui.util.AppPanelStyle.getContentBg());
        Runnable plotRepaint = debouncedRepaint(plotPanel);
        
        plotPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
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
        
        JButton updateButton = new JButton("Update");
        updateButton.addActionListener(e -> plotRepaint.run());
        controlPanel.add(updateButton);
        JButton saveImageButton = new JButton("Save image");
        saveImageButton.addActionListener(e -> exportPanelToImage(plotPanel, scatterPanel, "scatter.png"));
        controlPanel.add(saveImageButton);
        JButton resetZoomButton = new JButton("Reset zoom");
        resetZoomButton.addActionListener(e -> {
            zoomXMin[0] = null;
            zoomXMax[0] = null;
            zoomYMin[0] = null;
            zoomYMax[0] = null;
            plotRepaint.run();
        });
        controlPanel.add(resetZoomButton);
        
        scatterPanel.add(controlPanel, BorderLayout.NORTH);
        scatterPanel.add(plotPanel, BorderLayout.CENTER);
        
        xAxisCombo.addActionListener(e -> plotRepaint.run());
        yAxisCombo.addActionListener(e -> plotRepaint.run());
        
        if (mapView != null) {
            mapView.addCatalogVisibilityChangeListener(plotRepaint);
        }
        
        return scatterPanel;
    }
    
    private static void drawScatterPlot(Graphics g, com.treloc.xtreloc.app.gui.view.ReportPanel reportPanel, 
                                       JComboBox<String> xAxisCombo, JComboBox<String> yAxisCombo, MapView mapView,
                                       Double zoomXMin, Double zoomXMax, Double zoomYMin, Double zoomYMax,
                                       ScatterChartBounds outBounds) {
        if (outBounds != null) outBounds.chartWidth = 0;
        try {
            ChartAppearanceSettings chart = AppSettings.load().getChartAppearance();
            Font titleFont = chart.getTitleFont();
            Font tickFont = chart.getTickLabelFont();
            Font legendFont = chart.getLegendFont();
            Color bgColor = parseChartColor(chart.getBackgroundColor());
            Color axisColor = parseChartColor(chart.getAxisLineColor());
            
            String xAxisName = (String) xAxisCombo.getSelectedItem();
            String yAxisName = (String) yAxisCombo.getSelectedItem();
            
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
                int x = (g.getClipBounds().width - fm.stringWidth(message)) / 2;
                int y = g.getClipBounds().height / 2;
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
                int x = (g.getClipBounds().width - fm.stringWidth(message)) / 2;
                int y = g.getClipBounds().height / 2;
                g.drawString(message, x, y);
                return;
            }
            
            double xMin = Double.MAX_VALUE;
            double xMax = Double.MIN_VALUE;
            double yMin = Double.MAX_VALUE;
            double yMax = Double.MIN_VALUE;
            
            for (java.util.List<Double> xValues : allXValues) {
                for (double val : xValues) {
                    if (val < xMin) xMin = val;
                    if (val > xMax) xMax = val;
                }
            }
            for (java.util.List<Double> yValues : allYValues) {
                for (double val : yValues) {
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
            
            int width = g.getClipBounds().width;
            int height = g.getClipBounds().height;
            int margin = 60;
            int chartWidth = width - 2 * margin;
            int chartHeight = height - 2 * margin - 60;
            
            g.setColor(bgColor);
            g.fillRect(0, 0, width, height);
            
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
                Color catalogColor = info.getColor();
                
                g.setColor(catalogColor);
                for (int i = 0; i < xValues.size(); i++) {
                    double xVal = xValues.get(i);
                    double yVal = yValues.get(i);
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
                Color catalogColor = info.getColor();
                
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
                java.util.logging.Logger.getLogger("com.treloc.xtreloc")
                    .warning("Error during update check: " + e.getMessage());
            }
        }).start();
    }
}

