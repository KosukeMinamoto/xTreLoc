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
 * Panel for displaying catalog data in an Excel-like table format.
 * Supports multiple catalogs (same structure as ShapefileTablePanel).
 * 
 * @author K.Minamoto
 */
public class CatalogTablePanel extends JPanel {
    private JTable catalogListTable;
    private DefaultTableModel catalogListModel;
    
    private JTable dataTable;
    private DefaultTableModel dataTableModel;
    
    private JLabel statusLabel;
    private MapView mapView;
    private java.util.List<CatalogLoadListener> catalogLoadListeners = new java.util.ArrayList<>();
    
    private java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> catalogInfos = new java.util.ArrayList<>();
    private java.util.Map<com.treloc.xtreloc.app.gui.model.CatalogInfo, File> catalogFiles = new java.util.HashMap<>();
    
    private List<Hypocenter> hypocenters;
    private File currentCatalogFile;
    
    private JButton removeButton;
    
    /**
     * Listener interface for catalog loading events.
     */
    public interface CatalogLoadListener {
        /**
         * Called when a catalog is loaded.
         * 
         * @param hypocenters the list of hypocenters from the loaded catalog
         */
        void onCatalogLoaded(List<Hypocenter> hypocenters);
    }
    
    /**
     * Adds a catalog load listener.
     * 
     * @param listener the listener to add
     */
    public void addCatalogLoadListener(CatalogLoadListener listener) {
        catalogLoadListeners.add(listener);
    }

    public CatalogTablePanel(MapView mapView) {
        this.mapView = mapView;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Catalog Data"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.3);
        
        String[] catalogListColumnNames = {"Visible", "Name", "Count", "File"};
        catalogListModel = new DefaultTableModel(catalogListColumnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
            
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) {
                    return Boolean.class;
                }
                return String.class;
            }
        };
        catalogListTable = new JTable(catalogListModel);
        catalogListTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        catalogListTable.setRowHeight(20);
        catalogListTable.getTableHeader().setReorderingAllowed(false);
        
        catalogListTable.getModel().addTableModelListener(e -> {
            if (e.getColumn() == 0) {
                int row = e.getFirstRow();
                if (row >= 0 && row < catalogInfos.size()) {
                    com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfos.get(row);
                    Boolean visible = (Boolean) catalogListModel.getValueAt(row, 0);
                    info.setVisible(visible);
                    if (mapView != null) {
                        mapView.setCatalogVisible(info, visible);
                    }
                }
            }
        });
        
        catalogListTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = catalogListTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < catalogInfos.size()) {
                    com.treloc.xtreloc.app.gui.model.CatalogInfo selectedCatalog = catalogInfos.get(selectedRow);
                    displayCatalogData(selectedCatalog);
                }
            }
        });
        
        JScrollPane catalogListScrollPane = new JScrollPane(catalogListTable);
        catalogListScrollPane.setPreferredSize(new Dimension(500, 150));
        splitPane.setTopComponent(catalogListScrollPane);
        
        // 下部：データ表示テーブル（Excel風）
        String[] dataColumnNames = {"Row", "Time", "Latitude", "Longitude", "Depth (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms", "Cluster ID", "File Path"};
        dataTableModel = new DefaultTableModel(dataColumnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        dataTable = new JTable(dataTableModel);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataTable.setColumnSelectionAllowed(true);
        dataTable.setCellSelectionEnabled(true);
        dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        dataTable.setFillsViewportHeight(true);
        dataTable.setRowHeight(20);
        dataTable.getTableHeader().setReorderingAllowed(false);
        
        dataTable.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int columnIndex = dataTable.columnAtPoint(e.getPoint());
                if (columnIndex >= 0) {
                    dataTable.clearSelection();
                    dataTable.setColumnSelectionInterval(columnIndex, columnIndex);
                    handleColumnSelection(columnIndex);
                }
            }
        });
        
        dataTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int selectedRow = dataTable.getSelectedRow();
                    if (selectedRow >= 0 && getCurrentHypocenters() != null && 
                        selectedRow < getCurrentHypocenters().size() && mapView != null) {
                        Hypocenter h = getCurrentHypocenters().get(selectedRow);
                        try {
                            int catalogRow = catalogListTable.getSelectedRow();
                            if (catalogRow >= 0 && catalogRow < catalogInfos.size()) {
                                com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo = catalogInfos.get(catalogRow);
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
                }
            }
        });
        
        dataTable.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int selectedColumn = dataTable.getSelectedColumn();
                    if (selectedColumn >= 0 && getCurrentHypocenters() != null && !getCurrentHypocenters().isEmpty()) {
                        handleColumnSelection(selectedColumn);
                    }
                }
            }
        });
        
        JScrollPane dataScrollPane = new JScrollPane(dataTable);
        dataScrollPane.setPreferredSize(new Dimension(500, 300));
        splitPane.setBottomComponent(dataScrollPane);
        
        add(splitPane, BorderLayout.CENTER);

        // ステータスラベル
        statusLabel = new JLabel("No catalog loaded");
        add(statusLabel, BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton addButton = new JButton("Add Catalog");
        addButton.addActionListener(e -> selectCatalogFile());
        buttonPanel.add(addButton);
        
        removeButton = new JButton("Remove Selected");
        removeButton.addActionListener(e -> removeSelectedCatalog());
        removeButton.setEnabled(false);
        buttonPanel.add(removeButton);
        
        JCheckBox showConnectionsCheckBox = new JCheckBox("Show Connections");
        showConnectionsCheckBox.addActionListener(e -> {
            if (mapView != null) {
                mapView.setShowConnections(showConnectionsCheckBox.isSelected());
            }
        });
        buttonPanel.add(showConnectionsCheckBox);
        
        add(buttonPanel, BorderLayout.NORTH);
    }
    
    /**
     * Gets the hypocenters from the currently selected catalog.
     * 
     * @return the list of hypocenters, or null if no catalog is selected
     */
    private List<Hypocenter> getCurrentHypocenters() {
        int selectedRow = catalogListTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < catalogInfos.size()) {
            return catalogInfos.get(selectedRow).getHypocenters();
        }
        return hypocenters;
    }
    
    /**
     * Displays the data for the selected catalog.
     * 
     * @param catalogInfo the catalog information to display
     */
    private void displayCatalogData(com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo) {
        if (catalogInfo == null || catalogInfo.getHypocenters() == null) {
            dataTableModel.setRowCount(0);
            hypocenters = null;
            return;
        }
        
        List<Hypocenter> hypocenters = catalogInfo.getHypocenters();
        dataTableModel.setRowCount(0);
        
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
            dataTableModel.addRow(row);
        }
        
        this.hypocenters = hypocenters;
        
        for (CatalogLoadListener listener : catalogLoadListeners) {
            listener.onCatalogLoaded(hypocenters);
        }
    }
    
    /**
     * Removes the selected catalog from the list.
     */
    private void removeSelectedCatalog() {
        int selectedRow = catalogListTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= catalogInfos.size()) {
            JOptionPane.showMessageDialog(this,
                "Please select a catalog to remove",
                "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfos.get(selectedRow);
        if (mapView != null) {
            mapView.removeCatalog(info);
        }
        catalogInfos.remove(selectedRow);
        catalogFiles.remove(info);
        catalogListModel.removeRow(selectedRow);
        
        if (catalogInfos.isEmpty()) {
            dataTableModel.setRowCount(0);
            hypocenters = null;
            if (removeButton != null) {
                removeButton.setEnabled(false);
            }
        }
        
        updateStatusLabel();
    }
    
    /**
     * Updates the status label with the current catalog count.
     */
    private void updateStatusLabel() {
        if (catalogInfos.isEmpty()) {
            statusLabel.setText("No catalog loaded");
        } else {
            statusLabel.setText(String.format("Catalogs: %d loaded", catalogInfos.size()));
        }
    }

    /**
     * Opens a file chooser to select and load a catalog file.
     */
    private void selectCatalogFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Catalog File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Catalog files (*.csv)", "csv"));
        
        com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            loadCatalogFile(selectedFile);
        }
    }

    /**
     * Loads a catalog file and adds it to the list of catalogs.
     * 
     * @param file the catalog file to load
     */
    public void loadCatalogFile(File file) {
        try {
            if (catalogFiles.containsValue(file)) {
                JOptionPane.showMessageDialog(this,
                    "This catalog is already loaded: " + file.getName(),
                    "Information", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            List<Hypocenter> hypocenters = CatalogLoader.load(file);
            
            String name = file.getName();
            if (name.endsWith(".csv")) {
                name = name.substring(0, name.length() - 4);
            }
            
            java.awt.Color[] defaultColors = {
                java.awt.Color.BLUE, java.awt.Color.RED, java.awt.Color.GREEN,
                java.awt.Color.ORANGE, java.awt.Color.MAGENTA, java.awt.Color.CYAN,
                java.awt.Color.YELLOW, java.awt.Color.PINK
            };
            java.awt.Color color = defaultColors[catalogInfos.size() % defaultColors.length];
            
            com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType = 
                com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType.values()[
                    catalogInfos.size() % com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType.values().length];
            
            com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo = 
                new com.treloc.xtreloc.app.gui.model.CatalogInfo(name, color, symbolType, hypocenters, file);
            catalogInfos.add(catalogInfo);
            catalogFiles.put(catalogInfo, file);
            
            Object[] row = {
                true,
                name,
                String.valueOf(hypocenters.size()),
                file.getName()
            };
            catalogListModel.addRow(row);
            
            if (mapView != null) {
                mapView.addCatalog(catalogInfo);
            }
            
            for (CatalogLoadListener listener : catalogLoadListeners) {
                listener.onCatalogLoaded(hypocenters);
            }
            
            currentCatalogFile = file;
            this.hypocenters = hypocenters;
            
            if (catalogInfos.size() == 1) {
                catalogListTable.setRowSelectionInterval(0, 0);
                displayCatalogData(catalogInfo);
            }
            
            if (removeButton != null) {
                removeButton.setEnabled(true);
            }
            
            updateStatusLabel();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to load catalog file: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            updateStatusLabel();
        }
    }

    /**
     * Gets the hypocenters from the currently selected catalog.
     * 
     * @return the list of hypocenters, or null if no catalog is selected
     */
    public List<Hypocenter> getHypocenters() {
        return getCurrentHypocenters();
    }
    
    /**
     * Gets all catalog information.
     * 
     * @return the list of all catalog information
     */
    public java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> getCatalogInfos() {
        return new java.util.ArrayList<>(catalogInfos);
    }
    
    /**
     * Handles column selection and applies color coding for numeric columns.
     * 
     * @param columnIndex the index of the selected column
     */
    private void handleColumnSelection(int columnIndex) {
        List<Hypocenter> currentHypocenters = getCurrentHypocenters();
        if (currentHypocenters == null || currentHypocenters.isEmpty() || mapView == null) {
            return;
        }
        
        String columnName = dataTableModel.getColumnName(columnIndex);
        
        if (columnIndex == 10) {
            int selectedRow = dataTable.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < currentHypocenters.size()) {
                Hypocenter h = currentHypocenters.get(selectedRow);
                if (h.datFilePath != null && !h.datFilePath.isEmpty()) {
                    showTravelTimeData(h.datFilePath);
                }
            }
            return;
        }
        
        boolean isNumeric = columnIndex == 0 ||
                           (columnIndex >= 2 && columnIndex <= 4) ||
                           (columnIndex >= 5 && columnIndex <= 8) ||
                           columnIndex == 9;
        
        if (isNumeric) {
            double[] values = new double[currentHypocenters.size()];
            for (int i = 0; i < currentHypocenters.size(); i++) {
                Hypocenter h = currentHypocenters.get(i);
                switch (columnIndex) {
                    case 0:
                        values[i] = i + 1;
                        break;
                    case 2:
                        values[i] = h.lat;
                        break;
                    case 3:
                        values[i] = h.lon;
                        break;
                    case 4:
                        values[i] = h.depth;
                        break;
                    case 5:
                        values[i] = h.xerr;
                        break;
                    case 6:
                        values[i] = h.yerr;
                        break;
                    case 7:
                        values[i] = h.zerr;
                        break;
                    case 8:
                        values[i] = h.rms;
                        break;
                    case 9:
                        values[i] = h.clusterId != null ? h.clusterId : -1;
                        break;
                    default:
                        values[i] = h.depth;
                }
            }
            
            try {
                if (catalogInfos.size() > 1) {
                    int selectedCatalogRow = catalogListTable.getSelectedRow();
                    if (selectedCatalogRow >= 0 && selectedCatalogRow < catalogInfos.size()) {
                        com.treloc.xtreloc.app.gui.model.CatalogInfo selectedCatalog = catalogInfos.get(selectedCatalogRow);
                        mapView.applyColorMapToCatalog(selectedCatalog, columnName, values);
                    }
                } else {
                    mapView.showHypocenters(currentHypocenters, columnName, values);
                }
            } catch (Exception e) {
                System.err.println("Failed to apply coloring: " + e.getMessage());
            }
        } else {
            try {
                if (catalogInfos.size() > 1) {
                    mapView.clearColorMap();
                    mapView.updateMultipleCatalogsDisplay();
                } else {
                    mapView.showHypocenters(currentHypocenters);
                }
            } catch (Exception e) {
                System.err.println("Failed to update display: " + e.getMessage());
            }
        }
    }
    
    /**
     * Displays travel time data for the selected hypocenter.
     * 
     * @param datFilePath the path to the dat file containing travel time data
     */
    private void showTravelTimeData(String datFilePath) {
        try {
            File catalogFile = getCurrentCatalogFile();
            File datFile = null;
            if (catalogFile != null && catalogFile.getParentFile() != null) {
                File parentDir = catalogFile.getParentFile();
                datFile = new File(parentDir, datFilePath);
                if (!datFile.exists()) {
                    datFile = new File(datFilePath);
                }
            } else {
                datFile = new File(datFilePath);
            }
            
            if (!datFile.exists()) {
                JOptionPane.showMessageDialog(this,
                    "File not found: " + datFilePath,
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            com.treloc.xtreloc.solver.PointsHandler handler = new com.treloc.xtreloc.solver.PointsHandler();
            File stationFile = com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getStationFile();
            if (stationFile == null || !stationFile.exists()) {
                JOptionPane.showMessageDialog(this,
                    "Station file is not set",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            String[] codeStrings = loadStationCodes(stationFile);
            if (codeStrings == null || codeStrings.length == 0) {
                JOptionPane.showMessageDialog(this,
                    "Failed to load station codes",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            handler.readDatFile(datFile.getAbsolutePath(), codeStrings, 0.0);
            com.treloc.xtreloc.solver.Point point = handler.getMainPoint();
            if (point == null || point.getLagTable() == null) {
                JOptionPane.showMessageDialog(this,
                    "Travel time data not found",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            showTravelTimeDataTable(point.getLagTable(), codeStrings);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to load travel time data: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    /**
     * Loads station codes from a station file.
     * 
     * @param stationFile the station file to read
     * @return an array of station codes, or null if an error occurs
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
     * Displays travel time data in an Excel-like table format.
     * 
     * @param lagTable the travel time data table
     * @param codeStrings the station codes
     */
    private void showTravelTimeDataTable(double[][] lagTable, String[] codeStrings) {
        if (travelTimeDataPanel != null) {
            travelTimeDataPanel.setData(lagTable, codeStrings);
        } else {
            JFrame frame = new JFrame("Travel Time Data: " + (hypocenters != null && dataTable.getSelectedRow() >= 0 ? 
                hypocenters.get(dataTable.getSelectedRow()).datFilePath : ""));
            frame.setSize(600, 400);
            frame.setLocationRelativeTo(this);
            
            String[] columnNames = {"Station 1", "Station 2", "Travel Time Difference (sec)", "Weight"};
            DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            
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
     * Sets the travel time data panel for displaying travel time data.
     * 
     * @param panel the travel time data panel
     */
    public void setTravelTimeDataPanel(TravelTimeDataPanel panel) {
        this.travelTimeDataPanel = panel;
    }
    
    private TravelTimeDataPanel travelTimeDataPanel;
    
    /**
     * Gets the current catalog file.
     * 
     * @return the current catalog file, or null if no file is loaded
     */
    private File getCurrentCatalogFile() {
        return currentCatalogFile;
    }
}

