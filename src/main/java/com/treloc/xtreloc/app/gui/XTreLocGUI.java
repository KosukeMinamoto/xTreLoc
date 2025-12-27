package com.treloc.xtreloc.app.gui;

import com.treloc.xtreloc.app.gui.view.MapView;
import com.treloc.xtreloc.app.gui.controller.MapController;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

import org.geotools.swing.JMapFrame;

public class XTreLocGUI {

    public static void main(String[] args) throws Exception {
        // GUIモードではロゴAAは表示しない
        
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
        
        // GUIモードではログ履歴はGUIのログパネルに表示（後で追加）

        SwingUtilities.invokeLater(() -> {
            try {
                JFrame mainFrame = new JFrame("xTreLoc");
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
                
                // 走時差データを左ペインの下に配置
                JSplitPane leftPanelSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    excelPane, travelTimeDataPanel);
                leftPanelSplit.setResizeWeight(0.7);
                leftPanelSplit.setDividerLocation(400);
                
                // 右側はマップのみ
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
                
                // viewerタブ用の右ペインタブパネルを作成（map, hist, scatter）
                JTabbedPane viewerRightTabs = new JTabbedPane();
                viewerRightTabs.addTab("Map", mapFrame.getContentPane());
                
                // Histタブに複数列選択対応のヒストグラムパネルを作成
                JPanel histogramPanel = createHistogramPanel(catalogPanel);
                viewerRightTabs.addTab("Hist", histogramPanel);
                
                // Scatter plot panelを作成
                JPanel scatterPanel = createScatterPanel(reportPanel);
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
     * Histogram panelを作成（複数列選択対応）
     */
    private static JPanel createHistogramPanel(com.treloc.xtreloc.app.gui.view.CatalogTablePanel catalogPanel) {
        JPanel histogramPanelWrapper = new JPanel(new BorderLayout());
        histogramPanelWrapper.setBorder(javax.swing.BorderFactory.createTitledBorder("ヒストグラム"));
        
        // 列選択チェックボックスパネル
        JPanel columnSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        columnSelectionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("表示する列を選択（複数選択可）"));
        
        String[] columnNames = {"緯度", "経度", "深度 (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms"};
        java.util.List<JCheckBox> columnCheckBoxes = new java.util.ArrayList<>();
        
        for (String columnName : columnNames) {
            JCheckBox checkBox = new JCheckBox(columnName);
            columnCheckBoxes.add(checkBox);
            columnSelectionPanel.add(checkBox);
        }
        
        // ヒストグラム描画パネル
        JPanel histogramPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawHistogram(g, catalogPanel, columnCheckBoxes, columnNames);
            }
        };
        histogramPanel.setPreferredSize(new Dimension(500, 400));
        histogramPanel.setBackground(Color.WHITE);
        
        // チェックボックスの変更時に再描画
        for (JCheckBox checkBox : columnCheckBoxes) {
            checkBox.addActionListener(e -> histogramPanel.repaint());
        }
        
        histogramPanelWrapper.add(columnSelectionPanel, BorderLayout.NORTH);
        histogramPanelWrapper.add(histogramPanel, BorderLayout.CENTER);
        
        return histogramPanelWrapper;
    }
    
    /**
     * ヒストグラムを描画（複数列対応）
     */
    private static void drawHistogram(Graphics g, com.treloc.xtreloc.app.gui.view.CatalogTablePanel catalogPanel,
                                     java.util.List<JCheckBox> columnCheckBoxes, String[] columnNames) {
        try {
            // CatalogTablePanelからhypocentersを取得
            java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters = catalogPanel.getHypocenters();
            
            if (hypocenters == null || hypocenters.isEmpty()) {
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                FontMetrics fm = g.getFontMetrics();
                String message = "データがありません";
                int x = (g.getClipBounds().width - fm.stringWidth(message)) / 2;
                int y = g.getClipBounds().height / 2;
                g.drawString(message, x, y);
                return;
            }
            
            // 選択された列のインデックスを取得
            java.util.Set<Integer> selectedColumns = new java.util.HashSet<>();
            for (int i = 0; i < columnCheckBoxes.size() && i < columnNames.length; i++) {
                if (columnCheckBoxes.get(i).isSelected()) {
                    // CatalogTablePanelの列インデックスに変換（行番号=0, 時刻=1をスキップ）
                    selectedColumns.add(i + 2); // 2=緯度列のインデックス
                }
            }
            
            if (selectedColumns.isEmpty()) {
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                FontMetrics fm = g.getFontMetrics();
                String message = "表示する列を選択してください";
                int x = (g.getClipBounds().width - fm.stringWidth(message)) / 2;
                int y = g.getClipBounds().height / 2;
                g.drawString(message, x, y);
                return;
            }
            
            int width = g.getClipBounds().width;
            int height = g.getClipBounds().height;
            
            // 余白
            int margin = 50;
            int chartWidth = width - 2 * margin;
            int chartHeight = height - 2 * margin;
            
            // 背景をクリア
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            
            // 全選択列のデータ範囲を計算
            double globalMin = Double.MAX_VALUE;
            double globalMax = Double.MIN_VALUE;
            java.util.List<java.util.List<Double>> allValues = new java.util.ArrayList<>();
            
            for (int columnIndex : selectedColumns) {
                java.util.List<Double> values = getColumnValuesForHistogram(hypocenters, columnIndex);
                if (!values.isEmpty()) {
                    java.util.Collections.sort(values);
                    double min = values.get(0);
                    double max = values.get(values.size() - 1);
                    if (min < globalMin) globalMin = min;
                    if (max > globalMax) globalMax = max;
                    allValues.add(values);
                }
            }
            
            if (globalMin == Double.MAX_VALUE) {
                return;
            }
            
            double range = globalMax - globalMin;
            if (range == 0) {
                range = 1.0;
            }
            
            // ビンの数を決定（Sturgesの公式）
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
            
            // 各列のビンを作成
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
            
            // 最大頻度を取得
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
            
            // 色の配列（複数の列を異なる色で表示）
            Color[] colors = {Color.BLUE, Color.RED, Color.GREEN, Color.ORANGE, Color.MAGENTA, Color.CYAN, Color.PINK};
            
            // 各列のヒストグラムを重ね書き
            int colorIndex = 0;
            
            for (int i = 0; i < allBins.size(); i++) {
                int[] bins = allBins.get(i);
                Color color = colors[colorIndex % colors.length];
                colorIndex++;
                
                // 半透明で描画（重ね書きを視認しやすくする）
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
                double barWidth = (double) chartWidth / numBins;
                
                for (int j = 0; j < numBins; j++) {
                    int barHeight = (int) ((double) bins[j] / maxFreq * chartHeight);
                    int x = margin + (int) (j * barWidth);
                    int y = margin + chartHeight - barHeight;
                    g.fillRect(x, y, (int) barWidth - 1, barHeight);
                }
            }
            
            // 軸を描画
            g.setColor(Color.BLACK);
            // X軸
            g.drawLine(margin, margin + chartHeight, margin + chartWidth, margin + chartHeight);
            // Y軸
            g.drawLine(margin, margin, margin, margin + chartHeight);
            
            // ラベルを描画
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            FontMetrics fm = g.getFontMetrics();
            
            // X軸のラベル
            for (int i = 0; i <= 5; i++) {
                double value = globalMin + (range * i / 5);
                String label = String.format("%.2f", value);
                int x = margin + (int) (chartWidth * i / 5) - fm.stringWidth(label) / 2;
                g.drawString(label, x, margin + chartHeight + 20);
            }
            
            // Y軸のラベル
            for (int i = 0; i <= 5; i++) {
                int freq = maxFreq * i / 5;
                String label = String.valueOf(freq);
                int y = margin + chartHeight - (chartHeight * i / 5) + fm.getAscent() / 2;
                g.drawString(label, margin - fm.stringWidth(label) - 5, y);
            }
            
            // タイトルと凡例
            StringBuilder title = new StringBuilder();
            for (int idx : selectedColumns) {
                if (title.length() > 0) title.append(", ");
                int checkBoxIndex = idx - 2; // CatalogTablePanelの列インデックスからチェックボックスのインデックスに変換
                if (checkBoxIndex >= 0 && checkBoxIndex < columnNames.length) {
                    title.append(columnNames[checkBoxIndex]);
                }
            }
            title.append(" のヒストグラム");
            
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            int titleWidth = fm.stringWidth(title.toString());
            g.drawString(title.toString(), (width - titleWidth) / 2, 20);
            
            // 凡例を描画
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            int legendY = 35;
            colorIndex = 0;
            for (int idx : selectedColumns) {
                Color color = colors[colorIndex % colors.length];
                colorIndex++;
                g.setColor(color);
                g.fillRect(margin + chartWidth - 100, legendY, 15, 10);
                g.setColor(Color.BLACK);
                int checkBoxIndex = idx - 2;
                if (checkBoxIndex >= 0 && checkBoxIndex < columnNames.length) {
                    g.drawString(columnNames[checkBoxIndex], margin + chartWidth - 80, legendY + 8);
                }
                legendY += 15;
            }
            
        } catch (Exception e) {
            System.err.println("Failed to draw histogram: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 列インデックスから値を取得（ヒストグラム用）
     */
    private static java.util.List<Double> getColumnValuesForHistogram(
            java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters, int columnIndex) {
        java.util.List<Double> values = new java.util.ArrayList<>();
        for (com.treloc.xtreloc.app.gui.model.Hypocenter h : hypocenters) {
            double value = 0.0;
            // CatalogTablePanelの列インデックス: 0=行, 1=時刻, 2=緯度, 3=経度, 4=深度, 5=xerr, 6=yerr, 7=zerr, 8=rms
            switch (columnIndex) {
                case 2: // 緯度
                    value = h.lat;
                    break;
                case 3: // 経度
                    value = h.lon;
                    break;
                case 4: // 深度
                    value = h.depth;
                    break;
                case 5: // xerr
                    value = h.xerr;
                    break;
                case 6: // yerr
                    value = h.yerr;
                    break;
                case 7: // zerr
                    value = h.zerr;
                    break;
                case 8: // rms
                    value = h.rms;
                    break;
                default:
                    continue;
            }
            values.add(value);
        }
        return values;
    }
    
    /**
     * Scatter plot panelを作成
     */
    private static JPanel createScatterPanel(com.treloc.xtreloc.app.gui.view.ReportPanel reportPanel) {
        JPanel scatterPanel = new JPanel(new BorderLayout());
        scatterPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("散布図"));
        
        // X軸とY軸の選択コンボボックス
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(new JLabel("X軸:"));
        JComboBox<String> xAxisCombo = new JComboBox<>(new String[]{"緯度", "経度", "深度 (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms"});
        controlPanel.add(xAxisCombo);
        controlPanel.add(new JLabel("Y軸:"));
        JComboBox<String> yAxisCombo = new JComboBox<>(new String[]{"緯度", "経度", "深度 (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms"});
        yAxisCombo.setSelectedIndex(2); // デフォルトで深度
        controlPanel.add(yAxisCombo);
        
        // 散布図描画パネル
        JPanel plotPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (reportPanel != null) {
                    drawScatterPlot(g, reportPanel, xAxisCombo, yAxisCombo);
                }
            }
        };
        plotPanel.setPreferredSize(new Dimension(500, 400));
        plotPanel.setBackground(Color.WHITE);
        
        // 更新ボタン
        JButton updateButton = new JButton("更新");
        updateButton.addActionListener(e -> plotPanel.repaint());
        controlPanel.add(updateButton);
        
        scatterPanel.add(controlPanel, BorderLayout.NORTH);
        scatterPanel.add(plotPanel, BorderLayout.CENTER);
        
        // コンボボックスの変更時に再描画
        xAxisCombo.addActionListener(e -> plotPanel.repaint());
        yAxisCombo.addActionListener(e -> plotPanel.repaint());
        
        return scatterPanel;
    }
    
    /**
     * 散布図を描画
     */
    private static void drawScatterPlot(Graphics g, com.treloc.xtreloc.app.gui.view.ReportPanel reportPanel, 
                                       JComboBox<String> xAxisCombo, JComboBox<String> yAxisCombo) {
        try {
            // ReportPanelからhypocentersを取得（リフレクションを使用）
            java.lang.reflect.Field hypocentersField = com.treloc.xtreloc.app.gui.view.ReportPanel.class.getDeclaredField("hypocenters");
            hypocentersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter> hypocenters = 
                (java.util.List<com.treloc.xtreloc.app.gui.model.Hypocenter>) hypocentersField.get(reportPanel);
            
            if (hypocenters == null || hypocenters.isEmpty()) {
                g.setColor(Color.BLACK);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                FontMetrics fm = g.getFontMetrics();
                String message = "データがありません";
                int x = (g.getClipBounds().width - fm.stringWidth(message)) / 2;
                int y = g.getClipBounds().height / 2;
                g.drawString(message, x, y);
                return;
            }
            
            String xAxisName = (String) xAxisCombo.getSelectedItem();
            String yAxisName = (String) yAxisCombo.getSelectedItem();
            
            // データを取得
            java.util.List<Double> xValues = new java.util.ArrayList<>();
            java.util.List<Double> yValues = new java.util.ArrayList<>();
            
            for (com.treloc.xtreloc.app.gui.model.Hypocenter h : hypocenters) {
                double xVal = getValueForColumn(h, xAxisName);
                double yVal = getValueForColumn(h, yAxisName);
                if (!Double.isNaN(xVal) && !Double.isNaN(yVal)) {
                    xValues.add(xVal);
                    yValues.add(yVal);
                }
            }
            
            if (xValues.isEmpty()) {
                return;
            }
            
            // データ範囲を計算
            double xMin = java.util.Collections.min(xValues);
            double xMax = java.util.Collections.max(xValues);
            double yMin = java.util.Collections.min(yValues);
            double yMax = java.util.Collections.max(yValues);
            
            double xRange = xMax - xMin;
            double yRange = yMax - yMin;
            if (xRange == 0) xRange = 1.0;
            if (yRange == 0) yRange = 1.0;
            
            int width = g.getClipBounds().width;
            int height = g.getClipBounds().height;
            int margin = 60;
            int chartWidth = width - 2 * margin;
            int chartHeight = height - 2 * margin;
            
            // 背景をクリア
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            
            // 軸を描画
            g.setColor(Color.BLACK);
            g.drawLine(margin, margin + chartHeight, margin + chartWidth, margin + chartHeight); // X軸
            g.drawLine(margin, margin, margin, margin + chartHeight); // Y軸
            
            // データポイントを描画
            g.setColor(Color.BLUE);
            for (int i = 0; i < xValues.size(); i++) {
                double xVal = xValues.get(i);
                double yVal = yValues.get(i);
                
                int x = margin + (int) ((xVal - xMin) / xRange * chartWidth);
                int y = margin + chartHeight - (int) ((yVal - yMin) / yRange * chartHeight);
                
                g.fillOval(x - 2, y - 2, 4, 4);
            }
            
            // ラベルを描画
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            FontMetrics fm = g.getFontMetrics();
            
            // X軸ラベル
            for (int i = 0; i <= 5; i++) {
                double value = xMin + (xRange * i / 5);
                String label = String.format("%.2f", value);
                int x = margin + (int) (chartWidth * i / 5) - fm.stringWidth(label) / 2;
                g.drawString(label, x, margin + chartHeight + 20);
            }
            
            // Y軸ラベル
            for (int i = 0; i <= 5; i++) {
                double value = yMin + (yRange * i / 5);
                String label = String.format("%.2f", value);
                int y = margin + chartHeight - (int) (chartHeight * i / 5) + fm.getAscent() / 2;
                g.drawString(label, margin - fm.stringWidth(label) - 5, y);
            }
            
            // タイトル
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            String title = yAxisName + " vs " + xAxisName;
            int titleWidth = fm.stringWidth(title);
            g.drawString(title, (width - titleWidth) / 2, 20);
            
        } catch (Exception e) {
            System.err.println("Failed to draw scatter plot: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 列名から値を取得
     */
    private static double getValueForColumn(com.treloc.xtreloc.app.gui.model.Hypocenter h, String columnName) {
        switch (columnName) {
            case "緯度":
                return h.lat;
            case "経度":
                return h.lon;
            case "深度 (km)":
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

    private static File selectShapefile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Shapefile");
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            "Shapefile (*.shp)", "shp"));
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        }
        return null;
    }

    private static File selectCatalogFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Catalog File");
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            "Catalog files (*.dat)", "dat"));
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile();
        }
        return null;
    }
    
    private static void selectAndLoadShapefileRelatedFiles(File shapefile, MapController ctrl) {
        String baseName = shapefile.getName().replaceFirst("[.][^.]+$", "");
        File parentDir = shapefile.getParentFile();
        if (parentDir == null) {
            parentDir = new File(System.getProperty("user.dir"));
        }
        
        String[] relatedExtensions = {".dbf", ".shx", ".prj", ".shp.xml", ".cpg", ".sbn", ".sbx"};
        
        java.util.List<File> existingFiles = new java.util.ArrayList<>();
        for (String ext : relatedExtensions) {
            File relatedFile = new File(parentDir, baseName + ext);
            if (relatedFile.exists()) {
                existingFiles.add(relatedFile);
            }
        }
        
        if (!existingFiles.isEmpty()) {
            String[] options = new String[existingFiles.size() + 1];
            options[0] = "Load All";
            for (int i = 0; i < existingFiles.size(); i++) {
                options[i + 1] = existingFiles.get(i).getName();
            }
            
            int choice = JOptionPane.showOptionDialog(null,
                "Related files for the Shapefile were found. Please select files to load:",
                "Select Related Files",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
            
            if (choice == 0) {
                System.out.println("Loading related files automatically");
            } else if (choice > 0 && choice <= existingFiles.size()) {
                File selectedFile = existingFiles.get(choice - 1);
                System.out.println("Selected file: " + selectedFile.getName());
            }
        }
    }
}

