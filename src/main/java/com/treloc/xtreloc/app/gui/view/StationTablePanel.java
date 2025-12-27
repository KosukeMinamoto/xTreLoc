package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.io.Station;
import com.treloc.xtreloc.io.StationRepository;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 観測点データをExcel風のテーブルで表示するパネル
 */
public class StationTablePanel extends JPanel {
    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private StationRepository stationRepository;
    private List<Station> stations;
    private MapView mapView;

    public StationTablePanel(MapView mapView) {
        this.mapView = mapView;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("観測点データ"));

        // テーブルモデルの作成
        String[] columnNames = {"コード", "緯度", "経度", "深度 (km)", "P波補正", "S波補正"};
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
                    if (stations != null && !stations.isEmpty()) {
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
                    if (selectedRow >= 0 && selectedRow < stations.size() && mapView != null) {
                        Station s = stations.get(selectedRow);
                        try {
                            mapView.highlightPoint(s.getLon(), s.getLat());
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
                    if (selectedColumn >= 0 && stations != null && !stations.isEmpty()) {
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
        statusLabel = new JLabel("観測点データが読み込まれていません");
        add(statusLabel, BorderLayout.SOUTH);

        // ファイル選択ボタン
        JButton selectButton = new JButton("観測点ファイルを選択");
        selectButton.addActionListener(e -> selectStationFile());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(selectButton);
        add(buttonPanel, BorderLayout.NORTH);
        
        // 共有ファイルマネージャーのリスナーを登録
        com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().addStationFileListener(file -> {
            if (file != null) {
                loadStationFile(file);
            }
        });
        
        // 既に設定されている観測点ファイルがあれば読み込む
        File existingFile = com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getStationFile();
        if (existingFile != null && existingFile.exists()) {
            loadStationFile(existingFile);
        }
    }

    /**
     * 観測点ファイルを選択して読み込む
     */
    private void selectStationFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("観測点ファイルを選択");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Station files (*.tbl)", "tbl"));
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            loadStationFile(selectedFile);
            
            // 共有ファイルマネージャーに通知
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setStationFile(selectedFile);
        }
    }

    /**
     * 観測点ファイルを読み込む
     */
    public void loadStationFile(File file) {
        try {
            stations = new ArrayList<>();
            try (Scanner sc = new Scanner(file)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.isEmpty()) continue;
                    
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 6) {
                        Station station = new Station(
                            parts[0],
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]),
                            Double.parseDouble(parts[4]),
                            Double.parseDouble(parts[5])
                        );
                        stations.add(station);
                    }
                }
            }
            
            // StationRepositoryを作成
            stationRepository = StationRepository.fromList(stations);
            
            // テーブルにデータを表示
            tableModel.setRowCount(0);
            for (Station station : stations) {
                // 深度をメートルからキロメートルに変換
                // 入力: -m (メートル、負の値) → 出力: km (キロメートル、正の値、地下の深度)
                // 1000で割って符号を反転
                double depKm = -station.getDep() / 1000.0;
                Object[] row = {
                    station.getCode(),
                    String.format("%.3f", station.getLat()),
                    String.format("%.3f", station.getLon()),
                    String.format("%.3f", depKm),
                    String.format("%.3f", station.getPc()),
                    String.format("%.3f", station.getSc())
                };
                tableModel.addRow(row);
            }
            
            statusLabel.setText(String.format("観測点数: %d", stations.size()));
            
            // マップにプロット
            if (mapView != null && stations != null && !stations.isEmpty()) {
                try {
                    mapView.showStations(stations);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                        "マップへの表示に失敗しました: " + ex.getMessage(),
                        "警告", JOptionPane.WARNING_MESSAGE);
                }
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "観測点ファイルの読み込みに失敗しました: " + e.getMessage(),
                "エラー", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("読み込みエラー: " + e.getMessage());
        }
    }

    /**
     * StationRepositoryを取得
     */
    public StationRepository getStationRepository() {
        return stationRepository;
    }

    /**
     * 観測点ファイルのパスを取得
     */
    public String getStationFilePath() {
        // 現在の実装では、ファイルパスを保持していないため、
        // 必要に応じて追加する
        return null;
    }
    
    /**
     * 列選択を処理（数値列の場合、色付けを適用）
     */
    private void handleColumnSelection(int columnIndex) {
        if (stations == null || stations.isEmpty() || mapView == null) {
            return;
        }
        
        // 列名を取得
        String columnName = tableModel.getColumnName(columnIndex);
        
        // 数値列かどうかを判定（緯度、経度、深度、P波補正、S波補正）
        // 0:コード, 1:緯度, 2:経度, 3:深度, 4:P波補正, 5:S波補正
        boolean isNumeric = columnIndex >= 1 && columnIndex <= 5;
        
        if (isNumeric) {
            double[] values = new double[stations.size()];
            for (int i = 0; i < stations.size(); i++) {
                Station s = stations.get(i);
                switch (columnIndex) {
                    case 1: // 緯度
                        values[i] = s.getLat();
                        break;
                    case 2: // 経度
                        values[i] = s.getLon();
                        break;
                    case 3: // 深度
                        // 表示はkmだが、元のデータはmなので注意
                        values[i] = -s.getDep() / 1000.0;
                        break;
                    case 4: // P波補正
                        values[i] = s.getPc();
                        break;
                    case 5: // S波補正
                        values[i] = s.getSc();
                        break;
                    default:
                        values[i] = -s.getDep() / 1000.0;
                }
            }
            
            // マップに色付けを適用
            try {
                mapView.showStations(stations, columnName, values);
            } catch (Exception e) {
                System.err.println("色付けの適用に失敗: " + e.getMessage());
            }
        } else {
            // 数値列でない場合は通常の表示
            try {
                mapView.showStations(stations);
            } catch (Exception e) {
                System.err.println("表示の更新に失敗: " + e.getMessage());
            }
        }
    }
}

