package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.controller.MapController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ShapefileデータをExcel風のテーブルで表示するパネル
 * 複数のshapefileを読み込み、チェックボックスで表示/非表示を切り替え可能
 */
public class ShapefileTablePanel extends JPanel {
    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private MapView mapView;
    private MapController mapController;
    private List<File> shapefiles = new ArrayList<>();
    private Map<File, String> shapefileLayerTitles = new HashMap<>(); // File -> Layer Title

    public ShapefileTablePanel(MapView mapView, MapController mapController) {
        this.mapView = mapView;
        this.mapController = mapController;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Shapefileデータ"));

        // テーブルモデルの作成（チェックボックス、ファイル名、状態）
        String[] columnNames = {"表示", "ファイル名", "状態"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0; // チェックボックス列のみ編集可能
            }
            
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) {
                    return Boolean.class; // チェックボックス列
                }
                return String.class;
            }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFillsViewportHeight(true);
        
        // チェックボックス列の幅を設定
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        
        // テーブルの見た目を改善
        table.setRowHeight(20);
        table.getTableHeader().setReorderingAllowed(false);
        
        // チェックボックスの変更を監視
        table.getModel().addTableModelListener(e -> {
            if (e.getColumn() == 0) { // チェックボックス列
                int row = e.getFirstRow();
                if (row >= 0 && row < shapefiles.size()) {
                    File shapefile = shapefiles.get(row);
                    Boolean visible = (Boolean) tableModel.getValueAt(row, 0);
                    toggleShapefileVisibility(shapefile, visible);
                }
            }
        });
        
        // スクロールペインに追加
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(500, 300));
        add(scrollPane, BorderLayout.CENTER);

        // ステータスラベル
        statusLabel = new JLabel("Shapefileが読み込まれていません");
        add(statusLabel, BorderLayout.SOUTH);

        // ボタンパネル
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        // ファイル追加ボタン
        JButton addButton = new JButton("Shapefileを追加");
        addButton.addActionListener(e -> selectShapefile());
        buttonPanel.add(addButton);
        
        // 削除ボタン
        JButton removeButton = new JButton("選択を削除");
        removeButton.addActionListener(e -> removeSelectedShapefile());
        buttonPanel.add(removeButton);
        
        add(buttonPanel, BorderLayout.NORTH);
    }

    /**
     * Shapefileを選択して読み込む
     */
    private void selectShapefile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Shapefileを選択");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Shapefile (*.shp)", "shp"));
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            loadShapefile(selectedFile);
        }
    }

    /**
     * Shapefileを読み込む（複数対応）
     */
    public void loadShapefile(File file) {
        try {
            // 既に読み込まれているかチェック
            if (shapefiles.contains(file)) {
                JOptionPane.showMessageDialog(this,
                    "このShapefileは既に読み込まれています: " + file.getName(),
                    "情報", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            // マップにプロット
            if (mapView != null && mapController != null) {
                try {
                    String layerTitle = mapController.loadShapefile(file);
                    shapefiles.add(file);
                    shapefileLayerTitles.put(file, layerTitle);
                    
                    // テーブルに行を追加（チェックボックスはデフォルトでON）
                    Object[] row = {
                        true, // 表示
                        file.getName(),
                        "読み込み済み"
                    };
                    tableModel.addRow(row);
                    
                    statusLabel.setText(String.format("Shapefile: %d件読み込み済み", shapefiles.size()));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                        "マップへの表示に失敗しました: " + ex.getMessage(),
                        "警告", JOptionPane.WARNING_MESSAGE);
                }
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Shapefileの読み込みに失敗しました: " + e.getMessage(),
                "エラー", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("読み込みエラー: " + e.getMessage());
        }
    }
    
    /**
     * 選択されたShapefileを削除
     */
    private void removeSelectedShapefile() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= shapefiles.size()) {
            JOptionPane.showMessageDialog(this,
                "削除するShapefileを選択してください",
                "情報", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        File fileToRemove = shapefiles.get(selectedRow);
        String layerTitle = shapefileLayerTitles.get(fileToRemove);
        
        // マップからレイヤーを削除
        if (mapView != null && layerTitle != null) {
            mapView.removeShapefileLayer(layerTitle);
        }
        
        // リストから削除
        shapefiles.remove(selectedRow);
        shapefileLayerTitles.remove(fileToRemove);
        tableModel.removeRow(selectedRow);
        
        statusLabel.setText(String.format("Shapefile: %d件読み込み済み", shapefiles.size()));
    }
    
    /**
     * Shapefileの表示/非表示を切り替え
     */
    private void toggleShapefileVisibility(File file, boolean visible) {
        String layerTitle = shapefileLayerTitles.get(file);
        if (layerTitle != null && mapView != null) {
            mapView.setShapefileLayerVisibility(layerTitle, visible);
        }
    }

    /**
     * 選択されたShapefileを取得（最初の1つ、後方互換性のため）
     */
    public File getSelectedShapefile() {
        return shapefiles.isEmpty() ? null : shapefiles.get(0);
    }
    
    /**
     * 読み込まれているすべてのShapefileを取得
     */
    public List<File> getShapefiles() {
        return new ArrayList<>(shapefiles);
    }
}

