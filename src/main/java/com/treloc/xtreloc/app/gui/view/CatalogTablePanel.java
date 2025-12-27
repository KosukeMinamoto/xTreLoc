package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.app.gui.service.CatalogLoader;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * CatalogデータをExcel風のテーブルで表示するパネル
 */
public class CatalogTablePanel extends JPanel {
    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private List<Hypocenter> hypocenters;
    private MapView mapView;
    private java.util.List<CatalogLoadListener> catalogLoadListeners = new java.util.ArrayList<>();
    private File currentCatalogFile; // 現在のカタログファイル
    
    /**
     * カタログ読み込みリスナー
     */
    public interface CatalogLoadListener {
        void onCatalogLoaded(List<Hypocenter> hypocenters);
    }
    
    /**
     * カタログ読み込みリスナーを追加
     */
    public void addCatalogLoadListener(CatalogLoadListener listener) {
        catalogLoadListeners.add(listener);
    }

    public CatalogTablePanel(MapView mapView) {
        this.mapView = mapView;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Catalogデータ"));

        // テーブルモデルの作成
        String[] columnNames = {"行", "時刻", "緯度", "経度", "深度 (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms", "クラスタ番号", "ファイルパス"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 読み取り専用
            }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setColumnSelectionAllowed(true); // 列選択を有効化
        table.setCellSelectionEnabled(true); // セル選択を有効化
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFillsViewportHeight(true);
        
        // テーブルの見た目を改善
        table.setRowHeight(20);
        table.getTableHeader().setReorderingAllowed(false);
        
        // 列ヘッダークリックイベントリスナーを追加
        table.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int columnIndex = table.columnAtPoint(e.getPoint());
                if (columnIndex >= 0) {
                    // 列全体を選択
                    table.clearSelection();
                    table.setColumnSelectionInterval(columnIndex, columnIndex);
                    // 列選択処理を実行
                    if (hypocenters != null && !hypocenters.isEmpty()) {
                        handleColumnSelection(columnIndex);
                    }
                }
            }
        });
        
        // 行選択イベントリスナーを追加
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int selectedRow = table.getSelectedRow();
                    
                    // 行選択の場合
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
        table.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int selectedColumn = table.getSelectedColumn();
                    
                    // 列選択の場合（数値列のみ）
                    if (selectedColumn >= 0 && hypocenters != null && !hypocenters.isEmpty()) {
                        handleColumnSelection(selectedColumn);
                    }
                }
            }
        });
        
        // スクロールペインに追加
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(500, 300));
        add(scrollPane, BorderLayout.CENTER);

        // ステータスラベル
        statusLabel = new JLabel("Catalogデータが読み込まれていません");
        add(statusLabel, BorderLayout.SOUTH);

        // ファイル選択ボタン
        JButton selectButton = new JButton("Catalogファイルを選択");
        selectButton.addActionListener(e -> selectCatalogFile());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(selectButton);
        add(buttonPanel, BorderLayout.NORTH);
    }

    /**
     * Catalogファイルを選択して読み込む
     */
    private void selectCatalogFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Catalogファイルを選択");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Catalog files (*.csv)", "csv"));
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            loadCatalogFile(selectedFile);
        }
    }

    /**
     * Catalogファイルを読み込む
     */
    public void loadCatalogFile(File file) {
        try {
            currentCatalogFile = file;
            hypocenters = CatalogLoader.load(file);
            
            // テーブルにデータを表示
            tableModel.setRowCount(0);
            int rowNum = 1;
            for (Hypocenter h : hypocenters) {
                Object[] row = {
                    rowNum++,
                    h.time,
                    String.format("%.6f", h.lat),
                    String.format("%.6f", h.lon),
                    String.format("%.3f", h.depth),
                    String.format("%.3f", h.xerr),
                    String.format("%.3f", h.yerr),
                    String.format("%.3f", h.zerr),
                    String.format("%.4f", h.rms),
                    h.clusterId != null ? String.valueOf(h.clusterId) : "",
                    h.datFilePath != null ? h.datFilePath : ""
                };
                tableModel.addRow(row);
            }
            
            statusLabel.setText(String.format("Catalogデータ数: %d", hypocenters.size()));
            
            // リスナーに通知
            for (CatalogLoadListener listener : catalogLoadListeners) {
                listener.onCatalogLoaded(hypocenters);
            }
            
            // マップにプロット
            if (mapView != null && hypocenters != null && !hypocenters.isEmpty()) {
                try {
                    mapView.showHypocenters(hypocenters);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                        "マップへの表示に失敗しました: " + ex.getMessage(),
                        "警告", JOptionPane.WARNING_MESSAGE);
                }
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Catalogファイルの読み込みに失敗しました: " + e.getMessage(),
                "エラー", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("読み込みエラー: " + e.getMessage());
        }
    }

    /**
     * Catalogデータを取得
     */
    public List<Hypocenter> getHypocenters() {
        return hypocenters;
    }
    
    /**
     * 列選択を処理（数値列の場合、色付けを適用）
     */
    private void handleColumnSelection(int columnIndex) {
        if (hypocenters == null || hypocenters.isEmpty() || mapView == null) {
            return;
        }
        
        // 列名を取得
        String columnName = tableModel.getColumnName(columnIndex);
        
        // ファイルパス列（10）をクリックした場合、走時差データを表示
        if (columnIndex == 10) {
            // ファイルパス列がクリックされた場合、選択された行のファイルパスを取得
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < hypocenters.size()) {
                Hypocenter h = hypocenters.get(selectedRow);
                if (h.datFilePath != null && !h.datFilePath.isEmpty()) {
                    showTravelTimeData(h.datFilePath);
                }
            }
            return;
        }
        
        // 数値列かどうかを判定（行番号、緯度、経度、深度、エラー列、クラスタ番号）
        boolean isNumeric = columnIndex == 0 || // 0:行番号
                           (columnIndex >= 2 && columnIndex <= 4) || // 2:緯度, 3:経度, 4:深度
                           (columnIndex >= 5 && columnIndex <= 8) || // 5:xerr, 6:yerr, 7:zerr, 8:rms
                           columnIndex == 9; // 9:クラスタ番号
        
        if (isNumeric) {
            double[] values = new double[hypocenters.size()];
            for (int i = 0; i < hypocenters.size(); i++) {
                Hypocenter h = hypocenters.get(i);
                switch (columnIndex) {
                    case 0: // 行番号
                        values[i] = i + 1;
                        break;
                    case 2: // 緯度
                        values[i] = h.lat;
                        break;
                    case 3: // 経度
                        values[i] = h.lon;
                        break;
                    case 4: // 深度
                        values[i] = h.depth;
                        break;
                    case 5: // xerr
                        values[i] = h.xerr;
                        break;
                    case 6: // yerr
                        values[i] = h.yerr;
                        break;
                    case 7: // zerr
                        values[i] = h.zerr;
                        break;
                    case 8: // rms
                        values[i] = h.rms;
                        break;
                    case 9: // クラスタ番号
                        values[i] = h.clusterId != null ? h.clusterId : -1;
                        break;
                    default:
                        values[i] = h.depth;
                }
            }
            
            // マップに色付けを適用
            try {
                mapView.showHypocenters(hypocenters, columnName, values);
            } catch (Exception e) {
                System.err.println("色付けの適用に失敗: " + e.getMessage());
            }
        } else {
            // 数値列でない場合は通常の表示
            try {
                mapView.showHypocenters(hypocenters);
            } catch (Exception e) {
                System.err.println("表示の更新に失敗: " + e.getMessage());
            }
        }
    }
    
    /**
     * 走時差データを表示
     */
    private void showTravelTimeData(String datFilePath) {
        try {
            // カタログファイルのディレクトリを基準にdatファイルのパスを解決
            File catalogFile = getCurrentCatalogFile();
            File datFile = null;
            if (catalogFile != null && catalogFile.getParentFile() != null) {
                // 相対パスの場合
                File parentDir = catalogFile.getParentFile();
                datFile = new File(parentDir, datFilePath);
                if (!datFile.exists()) {
                    // 絶対パスの場合
                    datFile = new File(datFilePath);
                }
            } else {
                // 絶対パスの場合
                datFile = new File(datFilePath);
            }
            
            if (!datFile.exists()) {
                JOptionPane.showMessageDialog(this,
                    "ファイルが見つかりません: " + datFilePath,
                    "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 走時差データを読み込む
            com.treloc.xtreloc.solver.PointsHandler handler = new com.treloc.xtreloc.solver.PointsHandler();
            // 観測点ファイルを取得（SharedFileManagerから）
            File stationFile = com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getStationFile();
            if (stationFile == null || !stationFile.exists()) {
                JOptionPane.showMessageDialog(this,
                    "観測点ファイルが設定されていません",
                    "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 観測点コードを読み込む
            String[] codeStrings = loadStationCodes(stationFile);
            if (codeStrings == null || codeStrings.length == 0) {
                JOptionPane.showMessageDialog(this,
                    "観測点コードの読み込みに失敗しました",
                    "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // datファイルを読み込む
            handler.readDatFile(datFile.getAbsolutePath(), codeStrings, 0.0);
            com.treloc.xtreloc.solver.Point point = handler.getMainPoint();
            if (point == null || point.getLagTable() == null) {
                JOptionPane.showMessageDialog(this,
                    "走時差データが見つかりません",
                    "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 走時差データをExcel形式で表示
            showTravelTimeDataTable(point.getLagTable(), codeStrings);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "走時差データの読み込みに失敗しました: " + e.getMessage(),
                "エラー", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    /**
     * 観測点コードを読み込む
     */
    private String[] loadStationCodes(File stationFile) {
        try {
            java.util.List<String> codes = new java.util.ArrayList<>();
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(stationFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length > 0) {
                        codes.add(parts[0]);
                    }
                }
            }
            return codes.toArray(new String[0]);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 走時差データをExcel形式で表示
     */
    private void showTravelTimeDataTable(double[][] lagTable, String[] codeStrings) {
        // 走時差データ表示パネルを取得（XTreLocGUIから設定される）
        if (travelTimeDataPanel != null) {
            travelTimeDataPanel.setData(lagTable, codeStrings);
        } else {
            // パネルが設定されていない場合は別ウィンドウで表示
            JFrame frame = new JFrame("走時差データ: " + (hypocenters != null && table.getSelectedRow() >= 0 ? 
                hypocenters.get(table.getSelectedRow()).datFilePath : ""));
            frame.setSize(600, 400);
            frame.setLocationRelativeTo(this);
            
            // テーブルモデルを作成
            String[] columnNames = {"観測点1", "観測点2", "走時差 (秒)", "重み"};
            DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            
            // データを追加
            for (double[] lag : lagTable) {
                int idx1 = (int) lag[0];
                int idx2 = (int) lag[1];
                double lagTime = lag[2];
                double weight = lag.length > 3 ? lag[3] : 1.0;
                
                String code1 = idx1 < codeStrings.length ? codeStrings[idx1] : "?";
                String code2 = idx2 < codeStrings.length ? codeStrings[idx2] : "?";
                
                model.addRow(new Object[]{
                    code1,
                    code2,
                    String.format("%.3f", lagTime),
                    String.format("%.3f", weight)
                });
            }
            
            JTable table = new JTable(model);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            JScrollPane scrollPane = new JScrollPane(table);
            
            frame.add(scrollPane, BorderLayout.CENTER);
            frame.setVisible(true);
        }
    }
    
    /**
     * 走時差データ表示パネルを設定
     */
    public void setTravelTimeDataPanel(TravelTimeDataPanel panel) {
        this.travelTimeDataPanel = panel;
    }
    
    private TravelTimeDataPanel travelTimeDataPanel;
    
    /**
     * 現在のカタログファイルを取得
     */
    private File getCurrentCatalogFile() {
        return currentCatalogFile;
    }
}

