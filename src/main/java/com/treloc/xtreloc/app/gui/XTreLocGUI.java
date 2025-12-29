package com.treloc.xtreloc.app.gui;

import com.treloc.xtreloc.app.gui.view.MapView;
import com.treloc.xtreloc.app.gui.controller.MapController;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import org.geotools.swing.JMapFrame;

public class XTreLocGUI {

    public static void main(String[] args) throws Exception {
        try {
            File logFile = com.treloc.xtreloc.app.gui.util.LogHistoryManager.getLogFile();
            com.treloc.xtreloc.util.LogInitializer.setup(
                logFile.getAbsolutePath(), 
                java.util.logging.Level.INFO
            );
            
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger("com.treloc.xtreloc");
            logger.info("========================================");
            logger.info("xTreLoc application started");
            logger.info("Log file: " + logFile.getAbsolutePath());
            logger.info("========================================");
        } catch (Exception e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
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
                    System.err.println("Failed to load logo: " + e.getMessage());
                }
                
                MapView view = new MapView();
                MapController ctrl = new MapController(view);
                
                com.treloc.xtreloc.app.gui.util.AppSettings appSettings = 
                    com.treloc.xtreloc.app.gui.util.AppSettings.load();
                applyAppSettings(appSettings, mainFrame, view);
                
                checkForUpdatesAsync(appSettings, mainFrame);
                
                com.treloc.xtreloc.io.AppConfig config = null;
                try {
                    com.treloc.xtreloc.io.ConfigLoader loader = 
                        new com.treloc.xtreloc.io.ConfigLoader("config.json");
                    config = loader.getConfig();
                } catch (Exception e) {
                    System.err.println("Failed to load configuration file: " + e.getMessage());
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
                
                catalogPanel.addCatalogLoadListener(hypocenters -> {
                    reportPanel.setHypocenters(hypocenters);
                });
                
                JTabbedPane viewerRightTabs = new JTabbedPane();
                viewerRightTabs.addTab("Map", mapFrame.getContentPane());
                
                JPanel histogramPanel = createHistogramPanel(catalogPanel, view);
                viewerRightTabs.addTab("Hist", histogramPanel);
                
                JPanel scatterPanel = createScatterPanel(reportPanel, view);
                viewerRightTabs.addTab("Scatter", scatterPanel);
                
                com.treloc.xtreloc.app.gui.view.MicrosoftStyleTabbedPane mainTabbedPane = 
                    new com.treloc.xtreloc.app.gui.view.MicrosoftStyleTabbedPane();
                
                JPanel solverLogPanel = locationPanel.getLogPanel();
                
                JPanel leftPanelContainer = new JPanel(new BorderLayout());
                leftPanelContainer.add(solverLeftPanel, BorderLayout.CENTER);
                
                JPanel rightPanelContainer = new JPanel(new BorderLayout());
                rightPanelContainer.add(solverLogPanel, BorderLayout.CENTER);
                
                mainTabbedPane.addTab("solver", new JPanel());
                mainTabbedPane.addTab("viewer", new JPanel());
                mainTabbedPane.addTab("settings", new JPanel());
                
                mainTabbedPane.addChangeListener(e -> {
                    int selectedIndex = mainTabbedPane.getSelectedIndex();
                    String selectedTitle = selectedIndex >= 0 ? mainTabbedPane.getTitleAt(selectedIndex) : "";
                    
                    leftPanelContainer.removeAll();
                    if ("solver".equals(selectedTitle)) {
                        leftPanelContainer.add(solverLeftPanel, BorderLayout.CENTER);
                    } else if ("viewer".equals(selectedTitle)) {
                        leftPanelContainer.add(excelPane, BorderLayout.CENTER);
                    } else if ("settings".equals(selectedTitle)) {
                        JPanel settingsWrapper = new JPanel(new BorderLayout());
                        settingsWrapper.add(settingsPanel, BorderLayout.NORTH);
                        leftPanelContainer.add(settingsWrapper, BorderLayout.CENTER);
                    }
                    leftPanelContainer.revalidate();
                    leftPanelContainer.repaint();
                    
                    rightPanelContainer.removeAll();
                    if ("solver".equals(selectedTitle)) {
                        rightPanelContainer.add(solverLogPanel, BorderLayout.CENTER);
                    } else if ("viewer".equals(selectedTitle)) {
                        rightPanelContainer.add(viewerRightTabs, BorderLayout.CENTER);
                    } else if ("settings".equals(selectedTitle)) {
                        rightPanelContainer.add(new JPanel(), BorderLayout.CENTER);
                    }
                    rightPanelContainer.revalidate();
                    rightPanelContainer.repaint();
                });
                
                JPanel leftSidePanel = new JPanel(new BorderLayout());
                leftSidePanel.add(mainTabbedPane, BorderLayout.NORTH);
                leftSidePanel.add(leftPanelContainer, BorderLayout.CENTER);
                
                JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    leftSidePanel, rightPanelContainer);
                mainSplit.setResizeWeight(0.3);
                mainSplit.setDividerLocation(500);
                
                mainFrame.add(mainSplit, BorderLayout.CENTER);
                
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
                e.printStackTrace();
                System.err.println("Error initializing GUI: " + e.getMessage());
                JOptionPane.showMessageDialog(null,
                    "An error occurred: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    
    /**
     * Histogram panelを作成（カタログ間比較対応）
     */
    private static JPanel createHistogramPanel(com.treloc.xtreloc.app.gui.view.CatalogTablePanel catalogPanel, MapView mapView) {
        JPanel histogramPanelWrapper = new JPanel(new BorderLayout());
        histogramPanelWrapper.setBorder(javax.swing.BorderFactory.createTitledBorder("Histogram"));
        
        JPanel columnSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        columnSelectionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Select Parameter (Single Selection)"));
        
        String[] columnNames = {"Latitude", "Longitude", "Depth (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms"};
        java.util.List<JRadioButton> columnRadioButtons = new java.util.ArrayList<>();
        ButtonGroup columnGroup = new ButtonGroup();
        
        for (String columnName : columnNames) {
            JRadioButton radioButton = new JRadioButton(columnName);
            columnRadioButtons.add(radioButton);
            columnGroup.add(radioButton);
            columnSelectionPanel.add(radioButton);
        }
        
        JPanel histogramPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawHistogram(g, catalogPanel, columnRadioButtons, columnNames, mapView);
            }
        };
        histogramPanel.setPreferredSize(new Dimension(500, 400));
        histogramPanel.setBackground(Color.WHITE);
        
        for (JRadioButton radioButton : columnRadioButtons) {
            radioButton.addActionListener(e -> histogramPanel.repaint());
        }
        
        if (mapView != null) {
            mapView.addCatalogVisibilityChangeListener(() -> histogramPanel.repaint());
        }
        
        histogramPanelWrapper.add(columnSelectionPanel, BorderLayout.NORTH);
        histogramPanelWrapper.add(histogramPanel, BorderLayout.CENTER);
        
        return histogramPanelWrapper;
    }
    
    private static void drawHistogram(Graphics g, com.treloc.xtreloc.app.gui.view.CatalogTablePanel catalogPanel,
                                     java.util.List<JRadioButton> columnRadioButtons, String[] columnNames,
                                     MapView mapView) {
        try {
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
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
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
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
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
            
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
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
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                FontMetrics fm = g.getFontMetrics();
                String message = "No data available";
                int x = (g.getClipBounds().width - fm.stringWidth(message)) / 2;
                int y = g.getClipBounds().height / 2;
                g.drawString(message, x, y);
                return;
            }
            
            double range = globalMax - globalMin;
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
                    int binIndex = (int) Math.min((value - globalMin) / binWidth, numBins - 1);
                    bins[binIndex]++;
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
            
            g.setColor(Color.BLACK);
            g.drawLine(margin, margin + chartHeight, margin + chartWidth, margin + chartHeight);
            g.drawLine(margin, margin, margin, margin + chartHeight);
            
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            FontMetrics fm = g.getFontMetrics();
            
            for (int i = 0; i <= 5; i++) {
                double value = globalMin + (range * i / 5);
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
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            int titleWidth = fm.stringWidth(title);
            g.drawString(title, (width - titleWidth) / 2, 20);
            
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            int legendY = margin + chartHeight + 50;
            int legendX = margin;
            int legendItemHeight = 15;
            
            for (int i = 0; i < catalogInfoForValues.size(); i++) {
                com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfoForValues.get(i);
                Color catalogColor = info.getColor();
                
                g.setColor(catalogColor);
                g.fillRect(legendX, legendY, 15, 10);
                
                g.setColor(Color.BLACK);
                g.drawString(info.getName(), legendX + 20, legendY + 8);
                
                legendX += 120;
                if (legendX + 120 > width - margin) {
                    legendX = margin;
                    legendY += legendItemHeight;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Failed to draw histogram: " + e.getMessage());
            e.printStackTrace();
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
        scatterPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Scatter Plot"));
        
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(new JLabel("X-axis:"));
        JComboBox<String> xAxisCombo = new JComboBox<>(new String[]{"Latitude", "Longitude", "Depth (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms"});
        controlPanel.add(xAxisCombo);
        controlPanel.add(new JLabel("Y-axis:"));
        JComboBox<String> yAxisCombo = new JComboBox<>(new String[]{"Latitude", "Longitude", "Depth (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms"});
        yAxisCombo.setSelectedIndex(2);
        controlPanel.add(yAxisCombo);
        
        JPanel plotPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawScatterPlot(g, reportPanel, xAxisCombo, yAxisCombo, mapView);
            }
        };
        plotPanel.setPreferredSize(new Dimension(500, 400));
        plotPanel.setBackground(Color.WHITE);
        
        JButton updateButton = new JButton("Update");
        updateButton.addActionListener(e -> plotPanel.repaint());
        controlPanel.add(updateButton);
        
        scatterPanel.add(controlPanel, BorderLayout.NORTH);
        scatterPanel.add(plotPanel, BorderLayout.CENTER);
        
        xAxisCombo.addActionListener(e -> plotPanel.repaint());
        yAxisCombo.addActionListener(e -> plotPanel.repaint());
        
        if (mapView != null) {
            mapView.addCatalogVisibilityChangeListener(() -> plotPanel.repaint());
        }
        
        return scatterPanel;
    }
    
    private static void drawScatterPlot(Graphics g, com.treloc.xtreloc.app.gui.view.ReportPanel reportPanel, 
                                       JComboBox<String> xAxisCombo, JComboBox<String> yAxisCombo, MapView mapView) {
        try {
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
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
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
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
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
            
            int width = g.getClipBounds().width;
            int height = g.getClipBounds().height;
            int margin = 60;
            int chartWidth = width - 2 * margin;
            int chartHeight = height - 2 * margin - 60;
            
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            
            g.setColor(Color.BLACK);
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
                    
                    int x = margin + (int) ((xVal - xMin) / xRange * chartWidth);
                    int y = margin + chartHeight - (int) ((yVal - yMin) / yRange * chartHeight);
                    
                    g.fillOval(x - 2, y - 2, 4, 4);
                }
            }
            
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            FontMetrics fm = g.getFontMetrics();
            
            for (int i = 0; i <= 5; i++) {
                double value = xMin + (xRange * i / 5);
                String label = String.format("%.2f", value);
                int x = margin + (int) (chartWidth * i / 5) - fm.stringWidth(label) / 2;
                g.drawString(label, x, margin + chartHeight + 20);
            }
            
            for (int i = 0; i <= 5; i++) {
                double value = yMin + (yRange * i / 5);
                String label = String.format("%.2f", value);
                int y = margin + chartHeight - (int) (chartHeight * i / 5) + fm.getAscent() / 2;
                g.drawString(label, margin - fm.stringWidth(label) - 5, y);
            }
            
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            String title = yAxisName + " vs " + xAxisName;
            int titleWidth = fm.stringWidth(title);
            g.drawString(title, (width - titleWidth) / 2, 20);
            
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            int legendY = margin + chartHeight + 50;
            int legendX = margin;
            int legendItemHeight = 15;
            
            for (int i = 0; i < catalogInfoForValues.size(); i++) {
                com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfoForValues.get(i);
                Color catalogColor = info.getColor();
                
                g.setColor(catalogColor);
                g.fillOval(legendX, legendY, 8, 8);
                
                g.setColor(Color.BLACK);
                g.drawString(info.getName(), legendX + 12, legendY + 8);
                
                legendX += 120;
                if (legendX + 120 > width - margin) {
                    legendX = margin;
                    legendY += legendItemHeight;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Failed to draw scatter plot: " + e.getMessage());
            e.printStackTrace();
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

