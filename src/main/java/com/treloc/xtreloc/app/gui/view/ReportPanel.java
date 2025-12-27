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

/**
 * レポート生成パネル
 */
public class ReportPanel extends JPanel {
    private JPanel histogramPanel;
    private JButton loadCatalogButton;
    private JButton exportReportButton;
    private JButton exportCatalogButton;
    private JButton exportHistogramButton;
    private List<Hypocenter> hypocenters;
    private String[] columnNames = {"時刻", "緯度", "経度", "深度 (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms", "クラスタ番号"};
    private java.util.Set<Integer> selectedColumns = new java.util.HashSet<>();
    private JTable catalogTable;
    private javax.swing.table.DefaultTableModel catalogTableModel;
    private MapView mapView;
    private JPanel excelTablePanel; // Excelテーブルパネル
    private JPanel histogramPanelWrapper; // ヒストグラムパネル
    
    public ReportPanel(MapView mapView) {
        this.mapView = mapView;
        initComponents();
    }
    
    public ReportPanel() {
        this(null); // 後方互換性のため
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
        setBorder(new TitledBorder("レポート生成"));
        
        // 上部パネル: カタログ読み込みと出力
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        loadCatalogButton = new JButton("カタログファイルを読み込み");
        loadCatalogButton.addActionListener(e -> loadCatalogFile());
        topPanel.add(loadCatalogButton);
        
        // フォルダアイコンを左側に配置
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
        selectDirButton.setToolTipText("ディレクトリから.datファイルを走査してカタログを生成");
        selectDirButton.addActionListener(e -> generateCatalogFromDirectory());
        topPanel.add(selectDirButton);
        
        exportCatalogButton = new JButton("カタログをCSV出力");
        exportCatalogButton.setEnabled(false);
        exportCatalogButton.addActionListener(e -> exportCatalog());
        topPanel.add(exportCatalogButton);
        
        exportReportButton = new JButton("レポートを出力");
        exportReportButton.setEnabled(false);
        exportReportButton.addActionListener(e -> exportReport());
        topPanel.add(exportReportButton);
        
        exportHistogramButton = new JButton("ヒストグラム画像出力");
        exportHistogramButton.setEnabled(false);
        exportHistogramButton.addActionListener(e -> exportHistogramImage());
        topPanel.add(exportHistogramButton);
        
        add(topPanel, BorderLayout.NORTH);
        
        // 中央パネル: 左側にExcelテーブル、右側にヒストグラム
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        
        // 左パネル: Excelテーブル（CatalogTablePanelと同じ形式）
        JPanel leftPanel = new JPanel(new BorderLayout());
        
        // カタログテーブルを作成
        catalogTableModel = new javax.swing.table.DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 読み取り専用
            }
        };
        catalogTable = new JTable(catalogTableModel);
        catalogTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        catalogTable.setColumnSelectionAllowed(true); // 列選択を有効化
        catalogTable.setCellSelectionEnabled(true); // セル選択を有効化
        catalogTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        catalogTable.setFillsViewportHeight(true);
        catalogTable.setRowHeight(20);
        catalogTable.getTableHeader().setReorderingAllowed(false);
        
        // 列ヘッダークリックイベントリスナーを追加
        catalogTable.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int columnIndex = catalogTable.columnAtPoint(e.getPoint());
                if (columnIndex >= 0) {
                    // 列全体を選択
                    catalogTable.clearSelection();
                    catalogTable.setColumnSelectionInterval(columnIndex, columnIndex);
                    // 列選択処理を実行
                    if (hypocenters != null && !hypocenters.isEmpty()) {
                        handleColumnSelection(columnIndex);
                    }
                }
            }
        });
        
        // 行選択イベントリスナーを追加
        catalogTable.getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            @Override
            public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int selectedRow = catalogTable.getSelectedRow();
                    // 行選択の場合、マップでハイライト
                    if (selectedRow >= 0 && selectedRow < hypocenters.size() && mapView != null) {
                        Hypocenter h = hypocenters.get(selectedRow);
                        try {
                            mapView.highlightPoint(h.lon, h.lat);
                        } catch (Exception ex) {
                            // エラーは無視
                        }
                    } else if (mapView != null) {
                        mapView.clearHighlight();
                    }
                }
            }
        });
        
        // 列選択イベントリスナーを追加
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
        rightPanel.setBorder(new TitledBorder("ヒストグラム"));
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
        histogramPanelWrapper = rightPanel; // ヒストグラムパネルを保存
        
        centerSplit.setLeftComponent(leftPanel);
        centerSplit.setRightComponent(rightPanel);
        centerSplit.setResizeWeight(0.4);
        centerSplit.setDividerLocation(400);
        
        add(centerSplit, BorderLayout.CENTER);
    }
    
    private void loadCatalogFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("カタログファイルを選択");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Catalog files (*.csv)", "csv"));
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                hypocenters = CatalogLoader.load(selectedFile);
                setHypocenters(hypocenters); // テーブル更新も含む
                JOptionPane.showMessageDialog(this,
                    String.format("カタログファイルを読み込みました: %d件", hypocenters.size()),
                    "情報", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "カタログファイルの読み込みに失敗しました: " + e.getMessage(),
                    "エラー", JOptionPane.ERROR_MESSAGE);
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
                           columnIndex == 8; // 8:クラスタ番号
        
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
                    case 8: // クラスタ番号
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
                    // GeoToolsのレンダリングエラーを抑制（NullPointerExceptionなど）
                    if (e instanceof NullPointerException && 
                        e.getMessage() != null && 
                        e.getMessage().contains("loops")) {
                        // GeoToolsの既知のバグを無視
                        return;
                    }
                    System.err.println("色付けの適用に失敗: " + e.getMessage());
                }
            });
        } else if (mapView != null) {
            // 数値列でない場合は通常の表示（Swingイベントディスパッチスレッドで実行）
            SwingUtilities.invokeLater(() -> {
                try {
                    mapView.showHypocenters(hypocenters);
                } catch (Exception e) {
                    // GeoToolsのレンダリングエラーを抑制（NullPointerExceptionなど）
                    if (e instanceof NullPointerException && 
                        e.getMessage() != null && 
                        e.getMessage().contains("loops")) {
                        // GeoToolsの既知のバグを無視
                        return;
                    }
                    System.err.println("表示の更新に失敗: " + e.getMessage());
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
                case 8: // クラスタ番号
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
            return "データがありません";
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
        sb.append("列: ").append(columnName).append("\n");
        sb.append("データ数: ").append(values.size()).append("\n\n");
        sb.append("最小値: ").append(df.format(min)).append("\n");
        sb.append("最大値: ").append(df.format(max)).append("\n");
        sb.append("平均値: ").append(df.format(mean)).append("\n");
        sb.append("中央値: ").append(df.format(median)).append("\n");
        sb.append("標準偏差: ").append(df.format(stdDev)).append("\n");
        sb.append("第1四分位数: ").append(df.format(q1)).append("\n");
        sb.append("第3四分位数: ").append(df.format(q3)).append("\n");
        sb.append("四分位範囲: ").append(df.format(q3 - q1)).append("\n");
        
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
        
        // 全選択列のデータ範囲を計算
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
        
        // 色の配列（複数の列を異なる色で表示）
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
        
        // タイトルと凡例
        StringBuilder title = new StringBuilder();
        for (int idx : selectedColumns) {
            if (title.length() > 0) title.append(", ");
            title.append(columnNames[idx]);
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
            g.drawString(columnNames[idx], margin + chartWidth - 80, legendY + 8);
            legendY += 15;
        }
    }
    
    private void exportCatalog() {
        if (hypocenters == null || hypocenters.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "カタログデータがありません",
                "エラー", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("カタログをCSV出力");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "CSV files (*.csv)", "csv"));
        fileChooser.setSelectedFile(new File("catalog.csv"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File outputFile = fileChooser.getSelectedFile();
            try {
                CsvExporter.exportHypocenters(hypocenters, outputFile);
                JOptionPane.showMessageDialog(this,
                    "カタログを出力しました: " + outputFile.getAbsolutePath(),
                    "情報", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "カタログの出力に失敗しました: " + e.getMessage(),
                    "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exportReport() {
        if (hypocenters == null || hypocenters.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "カタログデータがありません",
                "エラー", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("レポートを出力");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Text files (*.txt)", "txt"));
        fileChooser.setSelectedFile(new File("report.txt"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File outputFile = fileChooser.getSelectedFile();
            try {
                writeReport(outputFile);
                JOptionPane.showMessageDialog(this,
                    "レポートを出力しました: " + outputFile.getAbsolutePath(),
                    "情報", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "レポートの出力に失敗しました: " + e.getMessage(),
                    "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void writeReport(File outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("=== xTreLoc カタログレポート ===\n\n");
            writer.write("データ数: " + hypocenters.size() + "\n\n");
            
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
    
    /**
     * カタログデータを設定（外部から呼び出し可能）
     */
    public void setHypocenters(List<Hypocenter> hypocenters) {
        this.hypocenters = hypocenters;
        
        // テーブルにデータを表示
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
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("ディレクトリを選択（.datファイルを走査）");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        
        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File selectedDir = fileChooser.getSelectedFile();
        
        // 出力ファイルを選択
        JFileChooser saveChooser = new JFileChooser();
        saveChooser.setDialogTitle("カタログをCSV出力");
        saveChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "CSV files (*.csv)", "csv"));
        saveChooser.setSelectedFile(new File(selectedDir, "catalog.csv"));
        
        int saveResult = saveChooser.showSaveDialog(this);
        if (saveResult != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File outputFile = saveChooser.getSelectedFile();
        
        // カタログ生成を実行
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("カタログ生成開始...");
                
                // .datファイルを走査
                List<File> datFiles = findDatFiles(selectedDir);
                if (datFiles.isEmpty()) {
                    publish("エラー: 選択したディレクトリに.datファイルが見つかりませんでした。");
                    return null;
                }
                
                publish("見つかった.datファイル数: " + datFiles.size());
                
                List<Hypocenter> allHypocenters = new ArrayList<>();
                int processedCount = 0;
                int errorCount = 0;
                
                for (File datFile : datFiles) {
                    try {
                        publish("読み込み中: " + datFile.getName() + " (" + (processedCount + errorCount + 1) + "/" + datFiles.size() + ")");
                        List<Hypocenter> hypocenters = loadHypocentersFromDatFile(datFile);
                        allHypocenters.addAll(hypocenters);
                        processedCount++;
                    } catch (Exception e) {
                        errorCount++;
                        publish("エラー: " + datFile.getName() + " の読み込みに失敗: " + e.getMessage());
                    }
                }
                
                // カタログを出力
                if (!allHypocenters.isEmpty()) {
                    try {
                        CsvExporter.exportHypocenters(allHypocenters, outputFile);
                        publish("カタログを出力しました: " + outputFile.getAbsolutePath() + " (" + allHypocenters.size() + "件)");
                        // ReportPanelにデータを設定
                        SwingUtilities.invokeLater(() -> {
                            setHypocenters(allHypocenters);
                        });
                    } catch (Exception e) {
                        publish("警告: カタログの出力に失敗: " + e.getMessage());
                    }
                } else {
                    publish("警告: 読み込まれた震源データがありません");
                }
                
                publish("カタログ生成完了: " + processedCount + "ファイル成功, " + errorCount + "ファイルエラー");
                
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    // ログエリアがあれば表示（ReportPanelにはログエリアがないので、後で追加するか、JOptionPaneで表示）
                    System.out.println(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    get(); // 例外があればスロー
                    JOptionPane.showMessageDialog(ReportPanel.this,
                        "カタログの生成が完了しました",
                        "情報", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ReportPanel.this,
                        "エラーが発生しました: " + e.getMessage(),
                        "エラー", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        worker.execute();
    }
    
    /**
     * .datファイルを再帰的に検索
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
     * .datファイルから震源データを読み込む
     */
    private List<Hypocenter> loadHypocentersFromDatFile(File datFile) {
        List<Hypocenter> hypocenters = new ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(datFile))) {
            // 1行目: 緯度 経度 深度 タイプ
            String line1 = br.readLine();
            if (line1 != null) {
                String[] parts1 = line1.trim().split("\\s+");
                if (parts1.length >= 3) {
                    double lat = Double.parseDouble(parts1[0]);
                    double lon = Double.parseDouble(parts1[1]);
                    double depth = Double.parseDouble(parts1[2]);
                    // ファイル名から時刻を取得（例: 071201.000030.dat → 071201.000030）
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
                            // 2行目が観測点ペアの場合（エラー情報行がない形式）
                        }
                    }
                    
                    // カタログファイルの基準ディレクトリからの相対パスを計算
                    String datFilePath = datFile.getName(); // デフォルトはファイル名のみ
                    String type = parts1.length > 3 ? parts1[3] : null;
                    hypocenters.add(new Hypocenter(time, lat, lon, depth, xerr, yerr, zerr, rms, null, datFilePath, type));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("datファイルの読み込みに失敗: " + e.getMessage(), e);
        }
        return hypocenters;
    }
    
    /**
     * Exports the histogram as an image file.
     */
    private void exportHistogramImage() {
        if (hypocenters == null || hypocenters.isEmpty() || selectedColumns.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "ヒストグラムを表示するデータがありません",
                "エラー", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("ヒストグラムを画像として出力");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "PNG files (*.png)", "png"));
        fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "JPEG files (*.jpg, *.jpeg)", "jpg", "jpeg"));
        fileChooser.setSelectedFile(new File("histogram.png"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File outputFile = fileChooser.getSelectedFile();
            try {
                exportHistogramImageToFile(outputFile);
                JOptionPane.showMessageDialog(this,
                    "ヒストグラムを画像として出力しました: " + outputFile.getAbsolutePath(),
                    "情報", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "画像の出力に失敗しました: " + e.getMessage(),
                    "エラー", JOptionPane.ERROR_MESSAGE);
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

