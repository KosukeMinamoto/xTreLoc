package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.app.gui.service.CatalogLoader;
import com.treloc.xtreloc.app.gui.service.CsvExporter;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReportPanel extends JPanel {
    private JPanel histogramPanel;
    private JButton loadCatalogButton;
    private JButton exportReportButton;
    private JButton exportCatalogButton;
    private JButton exportHistogramButton;
    private List<Hypocenter> hypocenters;
    private String[] columnNames = {"Time", "Latitude", "Longitude", "Depth (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms", "Cluster ID"};
    private java.util.Set<Integer> selectedColumns = new java.util.HashSet<>();
    private JTable catalogTable;
    private javax.swing.table.DefaultTableModel catalogTableModel;
    private MapView mapView;
    private JPanel excelTablePanel; // Excelテーブルパネル
    private JPanel histogramPanelWrapper; // ヒストグラムパネル
    
    // Debounce timer for row selection to prevent UI freezing
    private javax.swing.Timer selectionDebounceTimer;
    
    public ReportPanel(MapView mapView) {
        this.mapView = mapView;
        initComponents();
    }
    
    public ReportPanel() {
        this(null);
    }
    
    /**
     * Excelテーブルパネルを取得
     */
    public JPanel getExcelTablePanel() {
        return excelTablePanel;
    }
    
    /**
     * ヒストグラムパネルを取得
     */
    public JPanel getHistogramPanel() {
        return histogramPanelWrapper;
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("Report Generation"));
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        loadCatalogButton = new JButton("Load Catalog File");
        loadCatalogButton.addActionListener(e -> loadCatalogFile());
        topPanel.add(loadCatalogButton);
        
        JButton selectDirButton = new JButton();
        try {
            Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
            if (folderIcon != null) {
                selectDirButton.setIcon(folderIcon);
            } else {
                selectDirButton.setText("📁");
            }
        } catch (Exception e) {
            selectDirButton.setText("📁");
        }
        selectDirButton.setToolTipText("Scan directory for .dat files and generate catalog");
        selectDirButton.addActionListener(e -> generateCatalogFromDirectory());
        topPanel.add(selectDirButton);
        
        exportCatalogButton = new JButton("Export Catalog to CSV");
        exportCatalogButton.setEnabled(false);
        exportCatalogButton.addActionListener(e -> exportCatalog());
        topPanel.add(exportCatalogButton);
        
        exportReportButton = new JButton("Export Report");
        exportReportButton.setEnabled(false);
        exportReportButton.addActionListener(e -> exportReport());
        topPanel.add(exportReportButton);
        
        exportHistogramButton = new JButton("Export Histogram Image");
        exportHistogramButton.setEnabled(false);
        exportHistogramButton.addActionListener(e -> exportHistogramImage());
        topPanel.add(exportHistogramButton);
        
        add(topPanel, BorderLayout.NORTH);
        
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        
        JPanel leftPanel = new JPanel(new BorderLayout());
        
        catalogTableModel = new javax.swing.table.DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        catalogTable = new JTable(catalogTableModel);
        catalogTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        catalogTable.setColumnSelectionAllowed(true);
        catalogTable.setCellSelectionEnabled(true);
        catalogTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        catalogTable.setFillsViewportHeight(true);
        catalogTable.setRowHeight(20);
        catalogTable.getTableHeader().setReorderingAllowed(false);
        
        catalogTable.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int columnIndex = catalogTable.columnAtPoint(e.getPoint());
                if (columnIndex >= 0) {
                    catalogTable.clearSelection();
                    catalogTable.setColumnSelectionInterval(columnIndex, columnIndex);
                    // 列選択処理を実行
                    if (hypocenters != null && !hypocenters.isEmpty()) {
                        handleColumnSelection(columnIndex);
                    }
                }
            }
        });
        
        // Initialize debounce timer for row selection
        selectionDebounceTimer = new javax.swing.Timer(100, e -> {
            // This will be set in the listener
        });
        selectionDebounceTimer.setRepeats(false);
        
        // Add row selection event listener
        catalogTable.getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            @Override
            public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    // Cancel previous timer
                    selectionDebounceTimer.stop();
                    
                    // Get selected row
                    final int selectedRow = catalogTable.getSelectedRow();
                    
                    // Set up debounced action
                    selectionDebounceTimer = new javax.swing.Timer(50, evt -> {
                        // Execute map update asynchronously to prevent UI freezing
                        SwingUtilities.invokeLater(() -> {
                            // 行選択の場合、マップでハイライト
                            if (selectedRow >= 0 && selectedRow < hypocenters.size() && mapView != null) {
                                Hypocenter h = hypocenters.get(selectedRow);
                                try {
                                    com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo = mapView.findCatalogForHypocenter(h);
                                    if (catalogInfo != null) {
                                        mapView.highlightPoint(h.lon, h.lat, null, 
                                            catalogInfo.getSymbolType(), catalogInfo.getColor());
                                    } else {
                                        mapView.highlightPoint(h.lon, h.lat);
                                    }
                                } catch (Exception ex) {
                                }
                            } else if (mapView != null) {
                                mapView.clearHighlight();
                            }
                        });
                    });
                    selectionDebounceTimer.setRepeats(false);
                    selectionDebounceTimer.start();
                }
            }
        });
        
        // Add column selection event listener
        catalogTable.getColumnModel().getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            @Override
            public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int selectedColumn = catalogTable.getSelectedColumn();
                    if (selectedColumn >= 0 && hypocenters != null && !hypocenters.isEmpty()) {
                        handleColumnSelection(selectedColumn);
                    }
                }
            }
        });
        
        JScrollPane tableScroll = new JScrollPane(catalogTable);
        tableScroll.setPreferredSize(new Dimension(500, 300));
        leftPanel.add(tableScroll, BorderLayout.CENTER);
        excelTablePanel = leftPanel; // Excelテーブルパネルを保存
        
        // 右パネル: ヒストグラム
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(new TitledBorder("Histogram"));
        histogramPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (hypocenters != null && !hypocenters.isEmpty() && !selectedColumns.isEmpty()) {
                    drawHistogram(g);
                }
            }
        };
        histogramPanel.setPreferredSize(new Dimension(500, 400));
        histogramPanel.setBackground(Color.WHITE);
        rightPanel.add(histogramPanel, BorderLayout.CENTER);
        histogramPanelWrapper = rightPanel;
        
        centerSplit.setLeftComponent(leftPanel);
        centerSplit.setRightComponent(rightPanel);
        centerSplit.setResizeWeight(0.4);
        centerSplit.setDividerLocation(400);
        
        add(centerSplit, BorderLayout.CENTER);
    }
    
    private void loadCatalogFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Catalog File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Catalog files (*.csv)", "csv"));
        
        com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                hypocenters = CatalogLoader.load(selectedFile);
                setHypocenters(hypocenters);
                JOptionPane.showMessageDialog(this,
                    String.format("Catalog file loaded: %d entries", hypocenters.size()),
                    "Information", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to load catalog file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void updateSelectedColumns() {
        selectedColumns.clear();
        int selectedColumn = catalogTable.getSelectedColumn();
        if (selectedColumn >= 0) {
            selectedColumns.add(selectedColumn);
        }
    }
    
    /**
     * 列選択を処理（数値列の場合、色付けとヒストグラムを適用）
     */
    private void handleColumnSelection(int columnIndex) {
        if (hypocenters == null || hypocenters.isEmpty()) {
            return;
        }
        
        // 列名を取得
        String columnName = catalogTableModel.getColumnName(columnIndex);
        
        // 数値列かどうかを判定
        boolean isNumeric = (columnIndex >= 1 && columnIndex <= 3) || // 1:緯度, 2:経度, 3:深度
                           (columnIndex >= 4 && columnIndex <= 7) || // 4:xerr, 5:yerr, 6:zerr, 7:rms
                           columnIndex == 8; // 8: Cluster ID
        
        if (isNumeric && mapView != null) {
            double[] values = new double[hypocenters.size()];
            for (int i = 0; i < hypocenters.size(); i++) {
                Hypocenter h = hypocenters.get(i);
                switch (columnIndex) {
                    case 1: // 緯度
                        values[i] = h.lat;
                        break;
                    case 2: // 経度
                        values[i] = h.lon;
                        break;
                    case 3: // 深度
                        values[i] = h.depth;
                        break;
                    case 4: // xerr
                        values[i] = h.xerr;
                        break;
                    case 5: // yerr
                        values[i] = h.yerr;
                        break;
                    case 6: // zerr
                        values[i] = h.zerr;
                        break;
                    case 7: // rms
                        values[i] = h.rms;
                        break;
                    case 8: // Cluster ID
                        values[i] = h.clusterId != null ? h.clusterId : -1;
                        break;
                    default:
                        values[i] = h.depth;
                }
            }
            
            // マップに色付けを適用（Swingイベントディスパッチスレッドで実行）
            SwingUtilities.invokeLater(() -> {
                try {
                    mapView.showHypocenters(hypocenters, columnName, values);
                } catch (Exception e) {
                    // Suppress GeoTools rendering errors (NullPointerException, etc.)
                    if (e instanceof NullPointerException && 
                        e.getMessage() != null && 
                        e.getMessage().contains("loops")) {
                        // GeoToolsの既知のバグを無視
                        return;
                    }
                    System.err.println("Failed to apply coloring: " + e.getMessage());
                }
            });
        } else if (mapView != null) {
            // Normal display for non-numeric columns (run on Swing event dispatch thread)
            SwingUtilities.invokeLater(() -> {
                try {
                    mapView.showHypocenters(hypocenters);
                } catch (Exception e) {
                    // Suppress GeoTools rendering errors (NullPointerException, etc.)
                    if (e instanceof NullPointerException && 
                        e.getMessage() != null && 
                        e.getMessage().contains("loops")) {
                        // GeoToolsの既知のバグを無視
                        return;
                    }
                    System.err.println("Failed to update display: " + e.getMessage());
                }
            });
        }
        
        // ヒストグラムを更新
        updateSelectedColumns();
        updateStatisticsAndHistogram();
    }
    
    private void updateStatisticsAndHistogram() {
        if (hypocenters == null || hypocenters.isEmpty()) {
            histogramPanel.repaint();
            return;
        }
        
        if (selectedColumns.isEmpty()) {
            histogramPanel.repaint();
            return;
        }
        
        // ヒストグラムを更新
        histogramPanel.repaint();
    }
    
    private List<Double> getColumnValues(int columnIndex) {
        List<Double> values = new ArrayList<>();
        for (Hypocenter h : hypocenters) {
            double value = 0.0;
            switch (columnIndex) {
                case 0: // 時刻はスキップ
                    continue;
                case 1: // 緯度
                    value = h.lat;
                    break;
                case 2: // 経度
                    value = h.lon;
                    break;
                case 3: // 深度
                    value = h.depth;
                    break;
                case 4: // xerr
                    value = h.xerr;
                    break;
                case 5: // yerr
                    value = h.yerr;
                    break;
                case 6: // zerr
                    value = h.zerr;
                    break;
                case 7: // rms
                    value = h.rms;
                    break;
                case 8: // Cluster ID
                    value = h.clusterId != null ? h.clusterId : -1;
                    break;
                default:
                    continue;
            }
            values.add(value);
        }
        return values;
    }
    
    private String calculateStatistics(List<Double> values, String columnName) {
        if (values.isEmpty()) {
            return "No data available";
        }
        
        Collections.sort(values);
        
        double sum = 0.0;
        double min = values.get(0);
        double max = values.get(values.size() - 1);
        
        for (double v : values) {
            sum += v;
        }
        
        double mean = sum / values.size();
        
        // 標準偏差
        double variance = 0.0;
        for (double v : values) {
            variance += (v - mean) * (v - mean);
        }
        double stdDev = Math.sqrt(variance / values.size());
        
        // 中央値
        double median;
        int size = values.size();
        if (size % 2 == 0) {
            median = (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
        } else {
            median = values.get(size / 2);
        }
        
        // 四分位数
        double q1 = values.get(size / 4);
        double q3 = values.get(size * 3 / 4);
        
        DecimalFormat df = new DecimalFormat("#.######");
        
        StringBuilder sb = new StringBuilder();
        sb.append("Column: ").append(columnName).append("\n");
        sb.append("Data count: ").append(values.size()).append("\n\n");
        sb.append("Min: ").append(df.format(min)).append("\n");
        sb.append("Max: ").append(df.format(max)).append("\n");
        sb.append("Mean: ").append(df.format(mean)).append("\n");
        sb.append("Median: ").append(df.format(median)).append("\n");
        sb.append("Std Dev: ").append(df.format(stdDev)).append("\n");
        sb.append("Q1: ").append(df.format(q1)).append("\n");
        sb.append("Q3: ").append(df.format(q3)).append("\n");
        sb.append("IQR: ").append(df.format(q3 - q1)).append("\n");
        
        return sb.toString();
    }
    
    private void drawHistogram(Graphics g) {
        if (hypocenters == null || hypocenters.isEmpty() || selectedColumns.isEmpty()) {
            return;
        }
        
        int width = histogramPanel.getWidth();
        int height = histogramPanel.getHeight();
        
        // 余白
        int margin = 50;
        int chartWidth = width - 2 * margin;
        int chartHeight = height - 2 * margin;
        
        // 背景をクリア
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        
        // Calculate data range for all selected columns
        double globalMin = Double.MAX_VALUE;
        double globalMax = Double.MIN_VALUE;
        java.util.List<java.util.List<Double>> allValues = new ArrayList<>();
        
        for (int columnIndex : selectedColumns) {
            List<Double> values = getColumnValues(columnIndex);
            if (!values.isEmpty()) {
                Collections.sort(values);
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
        for (List<Double> values : allValues) {
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
        java.util.List<int[]> allBins = new ArrayList<>();
        double binWidth = range / numBins;
        
        for (List<Double> values : allValues) {
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
        
        // Color array (display multiple columns in different colors)
        Color[] colors = {Color.BLUE, Color.RED, Color.GREEN, Color.ORANGE, Color.MAGENTA, Color.CYAN, Color.PINK};
        
        // 各列のヒストグラムを重ね書き
        int colorIndex = 0;
        java.util.List<Integer> columnIndices = new ArrayList<>(selectedColumns);
        
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
        
        // Title and legend
        StringBuilder title = new StringBuilder();
        for (int idx : selectedColumns) {
            if (title.length() > 0) title.append(", ");
            title.append(columnNames[idx]);
        }
        title.append(" Histogram");
        
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
            g.drawString(columnNames[idx], margin + chartWidth - 80, legendY + 8);
            legendY += 15;
        }
    }
    
    private void exportCatalog() {
        if (hypocenters == null || hypocenters.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No catalog data available",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Catalog to CSV");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "CSV files (*.csv)", "csv"));
        fileChooser.setSelectedFile(new File("catalog.csv"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File outputFile = fileChooser.getSelectedFile();
            try {
                CsvExporter.exportHypocenters(hypocenters, outputFile);
                JOptionPane.showMessageDialog(this,
                    "Catalog exported: " + outputFile.getAbsolutePath(),
                    "Information", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to export catalog: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exportReport() {
        if (hypocenters == null || hypocenters.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No catalog data available",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Report");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Text files (*.txt)", "txt"));
        com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);
        fileChooser.setSelectedFile(new File("report.txt"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File outputFile = fileChooser.getSelectedFile();
            try {
                writeReport(outputFile);
                JOptionPane.showMessageDialog(this,
                    "Report exported: " + outputFile.getAbsolutePath(),
                    "Information", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to export report: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void writeReport(File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("=== xTreLoc Catalog Report ===\n\n");
            writer.write("Number of data: " + hypocenters.size() + "\n\n");
            
            // 各列の統計情報
            for (int i = 0; i < columnNames.length; i++) {
                List<Double> values = getColumnValues(i);
                if (!values.isEmpty()) {
                    writer.write("--- " + columnNames[i] + " ---\n");
                    writer.write(calculateStatistics(values, columnNames[i]));
                    writer.write("\n");
                }
            }
        }
    }
    
    public void setHypocenters(List<Hypocenter> hypocenters) {
        this.hypocenters = hypocenters;
        
        // Display data in table
        if (catalogTableModel != null) {
            catalogTableModel.setRowCount(0);
            if (hypocenters != null) {
                for (Hypocenter h : hypocenters) {
                    Object[] row = {
                        h.time,
                        String.format("%.6f", h.lat),
                        String.format("%.6f", h.lon),
                        String.format("%.3f", h.depth),
                        String.format("%.3f", h.xerr),
                        String.format("%.3f", h.yerr),
                        String.format("%.3f", h.zerr),
                        String.format("%.4f", h.rms),
                        h.clusterId != null ? String.valueOf(h.clusterId) : ""
                    };
                    catalogTableModel.addRow(row);
                }
            }
        }
        
        if (hypocenters != null && !hypocenters.isEmpty()) {
            exportCatalogButton.setEnabled(true);
            exportReportButton.setEnabled(true);
            if (exportHistogramButton != null) {
                exportHistogramButton.setEnabled(true);
            }
            updateStatisticsAndHistogram();
        } else {
            exportCatalogButton.setEnabled(false);
            exportReportButton.setEnabled(false);
            if (exportHistogramButton != null) {
                exportHistogramButton.setEnabled(false);
            }
        }
    }
    
    /**
     * ディレクトリから.datファイルを走査してカタログを生成
     */
    private void generateCatalogFromDirectory() {
        File selectedDir = com.treloc.xtreloc.app.gui.util.DirectoryChooserHelper.selectDirectory(
            this, "Select Directory (.dat files will be scanned)",
            new File(System.getProperty("user.dir")));
        
        if (selectedDir == null) {
            return;
        }
        
        JFileChooser saveChooser = new JFileChooser();
        saveChooser.setDialogTitle("Export Catalog to CSV");
        saveChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "CSV files (*.csv)", "csv"));
        com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(saveChooser);
        saveChooser.setSelectedFile(new File(selectedDir, "catalog.csv"));
        
        int saveResult = saveChooser.showSaveDialog(this);
        if (saveResult != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File outputFile = saveChooser.getSelectedFile();
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("Starting catalog generation...");
                
                List<File> datFiles = findDatFiles(selectedDir);
                if (datFiles.isEmpty()) {
                    publish("Error: No .dat files found in the selected directory.");
                    return null;
                }
                
                publish("Found .dat files: " + datFiles.size());
                
                List<Hypocenter> allHypocenters = new ArrayList<>();
                int processedCount = 0;
                int errorCount = 0;
                
                for (File datFile : datFiles) {
                    try {
                        publish("Loading: " + datFile.getName() + " (" + (processedCount + errorCount + 1) + "/" + datFiles.size() + ")");
                        List<Hypocenter> hypocenters = loadHypocentersFromDatFile(datFile);
                        allHypocenters.addAll(hypocenters);
                        processedCount++;
                    } catch (Exception e) {
                        errorCount++;
                        publish("Error: Failed to load " + datFile.getName() + ": " + e.getMessage());
                    }
                }
                
                if (!allHypocenters.isEmpty()) {
                    try {
                        CsvExporter.exportHypocenters(allHypocenters, outputFile);
                        publish("Catalog exported: " + outputFile.getAbsolutePath() + " (" + allHypocenters.size() + " entries)");
                        SwingUtilities.invokeLater(() -> {
                            setHypocenters(allHypocenters);
                        });
                    } catch (Exception e) {
                        publish("Warning: Failed to export catalog: " + e.getMessage());
                    }
                } else {
                    publish("Warning: No hypocenter data loaded");
                }
                
                publish("Catalog generation complete: " + processedCount + " files succeeded, " + errorCount + " files failed");
                
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    // Display in log area if available (ReportPanel doesn't have a log area, so use JOptionPane or add later)
                    System.out.println(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    get(); // Throw exception if any
                    JOptionPane.showMessageDialog(ReportPanel.this,
                        "Catalog generation completed",
                        "Information", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ReportPanel.this,
                        "Error occurred: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        worker.execute();
    }
    
    /**
     * Recursively search for .dat files
     */
    private List<File> findDatFiles(File directory) {
        List<File> datFiles = new ArrayList<>();
        if (!directory.isDirectory()) {
            return datFiles;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    datFiles.addAll(findDatFiles(file));
                } else if (file.getName().toLowerCase().endsWith(".dat")) {
                    datFiles.add(file);
                }
            }
        }
        return datFiles;
    }
    
    /**
     * Load hypocenter data from .dat file
     */
    private List<Hypocenter> loadHypocentersFromDatFile(File datFile) {
        List<Hypocenter> hypocenters = new ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(datFile))) {
            // Line 1: latitude longitude depth type
            String line1 = br.readLine();
            if (line1 != null) {
                String[] parts1 = line1.trim().split("\\s+");
                if (parts1.length >= 3) {
                    double lat = Double.parseDouble(parts1[0]);
                    double lon = Double.parseDouble(parts1[1]);
                    double depth = Double.parseDouble(parts1[2]);
                    // Extract time from filename (e.g., 071201.000030.dat → 071201.000030)
                    String time = datFile.getName().replace(".dat", "");
                    
                    // 2行目: xerr in km, yerr in km, zerr in km, rms residual
                    double xerr = 0.0;
                    double yerr = 0.0;
                    double zerr = 0.0;
                    double rms = 0.0;
                    
                    String line2 = br.readLine();
                    if (line2 != null && !line2.trim().isEmpty()) {
                        String[] parts2 = line2.trim().split("\\s+");
                        try {
                            Double.parseDouble(parts2[0]);
                            // 数値のみの場合（エラー情報行）
                            if (parts2.length >= 4) {
                                xerr = Double.parseDouble(parts2[0]);
                                yerr = Double.parseDouble(parts2[1]);
                                zerr = Double.parseDouble(parts2[2]);
                                rms = Double.parseDouble(parts2[3]);
                            }
                        } catch (NumberFormatException e) {
                            // Line 2 is station pair (format without error information line)
                        }
                    }
                    
                    // Calculate relative path from catalog file base directory
                    String datFilePath = datFile.getName(); // Default is filename only
                    String type = parts1.length > 3 ? parts1[3] : null;
                    hypocenters.add(new Hypocenter(time, lat, lon, depth, xerr, yerr, zerr, rms, null, datFilePath, type));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read dat file: " + e.getMessage(), e);
        }
        return hypocenters;
    }
    
    /**
     * Exports the histogram as an image file.
     */
    private void exportHistogramImage() {
        if (hypocenters == null || hypocenters.isEmpty() || selectedColumns.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No data available to display histogram",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Histogram as Image");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "PNG files (*.png)", "png"));
        fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "JPEG files (*.jpg, *.jpeg)", "jpg", "jpeg"));
        com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);
        fileChooser.setSelectedFile(new File("histogram.png"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File outputFile = fileChooser.getSelectedFile();
            try {
                exportHistogramImageToFile(outputFile);
                JOptionPane.showMessageDialog(this,
                    "Histogram exported: " + outputFile.getAbsolutePath(),
                    "Information", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to export image: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Exports the histogram panel as an image file.
     * 
     * @param outputFile the output file (PNG or JPEG)
     * @throws Exception if export fails
     */
    private void exportHistogramImageToFile(File outputFile) throws Exception {
        int width = histogramPanel.getWidth();
        int height = histogramPanel.getHeight();
        
        if (width <= 0 || height <= 0) {
            width = 800;
            height = 600;
        }
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 背景を白で塗りつぶし
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // ヒストグラムを描画
        histogramPanel.paint(g2d);
        g2d.dispose();
        
        String extension = getFileExtension(outputFile.getName()).toLowerCase();
        if ("png".equals(extension)) {
            javax.imageio.ImageIO.write(image, "PNG", outputFile);
        } else if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            javax.imageio.ImageIO.write(image, "JPEG", outputFile);
        } else {
            throw new IllegalArgumentException("Unsupported image format. Use PNG or JPEG.");
        }
    }
    
    /**
     * Gets the file extension from a filename.
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf(".");
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }
}

