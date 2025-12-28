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

public class ShapefileTablePanel extends JPanel {
    private JTable table;
    private DefaultTableModel tableModel;
    private JTable dataTable;
    private DefaultTableModel dataTableModel;
    private JLabel statusLabel;
    private MapView mapView;
    private MapController mapController;
    private List<File> shapefiles = new ArrayList<>();
    private Map<File, String> shapefileLayerTitles = new HashMap<>();
    private Map<File, org.geotools.api.data.FeatureSource> shapefileFeatureSources = new HashMap<>();

    public ShapefileTablePanel(MapView mapView, MapController mapController) {
        this.mapView = mapView;
        this.mapController = mapController;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Shapefile Data"));

        String[] columnNames = {"Visible", "File Name", "Status"};
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
        
        // 上下に分割
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.3);
        
        // 上部：Shapefileリストテーブル
        JScrollPane listScrollPane = new JScrollPane(table);
        listScrollPane.setPreferredSize(new Dimension(500, 150));
        splitPane.setTopComponent(listScrollPane);
        
        // 下部：Shapefile属性データテーブル
        dataTableModel = new DefaultTableModel(new String[]{"Attribute", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        dataTable = new JTable(dataTableModel);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        dataTable.setFillsViewportHeight(true);
        dataTable.setRowHeight(20);
        dataTable.getTableHeader().setReorderingAllowed(false);
        
        JScrollPane dataScrollPane = new JScrollPane(dataTable);
        dataScrollPane.setPreferredSize(new Dimension(500, 300));
        splitPane.setBottomComponent(dataScrollPane);
        
        add(splitPane, BorderLayout.CENTER);

        statusLabel = new JLabel("No shapefile loaded");
        add(statusLabel, BorderLayout.SOUTH);
        
        // 行選択イベント（Shapefileを選択すると属性データを表示）
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < shapefiles.size()) {
                    File selectedShapefile = shapefiles.get(selectedRow);
                    displayShapefileData(selectedShapefile);
                }
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton addButton = new JButton("Add Shapefile");
        addButton.addActionListener(e -> selectShapefile());
        buttonPanel.add(addButton);
        
        JButton removeButton = new JButton("Remove Selected");
        removeButton.addActionListener(e -> removeSelectedShapefile());
        buttonPanel.add(removeButton);
        
        add(buttonPanel, BorderLayout.NORTH);
    }

    private void selectShapefile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Shapefile");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Shapefile (*.shp)", "shp"));
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            loadShapefile(selectedFile);
        }
    }

    public void loadShapefile(File file) {
        try {
            if (shapefiles.contains(file)) {
                JOptionPane.showMessageDialog(this,
                    "This shapefile is already loaded: " + file.getName(),
                    "Information", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            if (mapView != null && mapController != null) {
                try {
                    // FeatureSourceを取得して保存
                    var store = org.geotools.api.data.FileDataStoreFinder.getDataStore(file);
                    org.geotools.api.data.FeatureSource featureSource = store.getFeatureSource();
                    
                    String layerTitle = mapController.loadShapefile(file);
                    shapefiles.add(file);
                    shapefileLayerTitles.put(file, layerTitle);
                    shapefileFeatureSources.put(file, featureSource);
                    
                    Object[] row = {
                        true,
                        file.getName(),
                        "Loaded"
                    };
                    tableModel.addRow(row);
                    
                    statusLabel.setText(String.format("Shapefile: %d loaded", shapefiles.size()));
                    
                    // 最初のShapefileを選択状態にする
                    if (shapefiles.size() == 1) {
                        table.setRowSelectionInterval(0, 0);
                        displayShapefileData(file);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                        "Failed to display on map: " + ex.getMessage(),
                        "Warning", JOptionPane.WARNING_MESSAGE);
                }
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to load shapefile: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("Load error: " + e.getMessage());
        }
    }
    
    /**
     * 選択されたShapefileの属性データを表示
     */
    private void displayShapefileData(File shapefile) {
        if (shapefile == null) {
            dataTableModel.setRowCount(0);
            return;
        }
        
        org.geotools.api.data.FeatureSource featureSource = shapefileFeatureSources.get(shapefile);
        if (featureSource == null) {
            dataTableModel.setRowCount(0);
            return;
        }
        
        try {
            dataTableModel.setRowCount(0);
            
            // FeatureTypeの属性情報を取得
            org.geotools.api.feature.type.FeatureType featureType = featureSource.getSchema();
            java.util.Collection<org.geotools.api.feature.type.PropertyDescriptor> descriptorsCollection = featureType.getDescriptors();
            java.util.List<org.geotools.api.feature.type.PropertyDescriptor> descriptors = new java.util.ArrayList<>(descriptorsCollection);
            
            // 最初のFeatureを取得して属性値を表示
            // FeatureSourceからFeatureCollectionを取得
            org.geotools.api.data.Query query = org.geotools.api.data.Query.ALL;
            var features = featureSource.getFeatures(query);
            
            // FeatureIteratorを使用してFeatureを取得
            try (var featureIterator = features.features()) {
                if (featureIterator.hasNext()) {
                    var firstFeature = featureIterator.next();
                    
                    // 各属性を表示
                    for (org.geotools.api.feature.type.PropertyDescriptor descriptor : descriptors) {
                        String name = descriptor.getName().toString();
                        var property = firstFeature.getProperty(name);
                        Object value = property != null ? property.getValue() : null;
                        String valueStr = value != null ? value.toString() : "";
                        
                        dataTableModel.addRow(new Object[]{name, valueStr});
                    }
                } else {
                    // Featureがない場合、属性名のみ表示
                    for (org.geotools.api.feature.type.PropertyDescriptor descriptor : descriptors) {
                        String name = descriptor.getName().toString();
                        dataTableModel.addRow(new Object[]{name, ""});
                    }
                }
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to load shapefile attributes: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            dataTableModel.setRowCount(0);
        }
    }
    
    private void removeSelectedShapefile() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= shapefiles.size()) {
            JOptionPane.showMessageDialog(this,
                "Please select a shapefile to remove",
                "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        File fileToRemove = shapefiles.get(selectedRow);
        String layerTitle = shapefileLayerTitles.get(fileToRemove);
        
        if (mapView != null && layerTitle != null) {
            mapView.removeShapefileLayer(layerTitle);
        }
        
        shapefiles.remove(selectedRow);
        shapefileLayerTitles.remove(fileToRemove);
        shapefileFeatureSources.remove(fileToRemove);
        tableModel.removeRow(selectedRow);
        
        if (shapefiles.isEmpty()) {
            dataTableModel.setRowCount(0);
        }
        
        statusLabel.setText(String.format("Shapefile: %d loaded", shapefiles.size()));
    }
    
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

