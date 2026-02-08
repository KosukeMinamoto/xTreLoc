package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.io.Station;
import com.treloc.xtreloc.io.StationRepository;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class StationTablePanel extends JPanel {
    private JTable stationListTable;
    private DefaultTableModel stationListModel;
    
    private JTable dataTable;
    private DefaultTableModel dataTableModel;
    
    private JLabel statusLabel;
    private StationRepository stationRepository;
    private MapView mapView;
    
    private java.util.List<StationFileInfo> stationFileInfos = new java.util.ArrayList<>();
    private java.util.Map<StationFileInfo, File> stationFiles = new java.util.HashMap<>();
    private java.util.Map<StationFileInfo, List<Station>> stationData = new java.util.HashMap<>();
    
    private JButton removeButton;
    
    // Debounce timer for row selection to prevent UI freezing
    private javax.swing.Timer selectionDebounceTimer;
    
    /**
     * Station file information holder
     */
    private static class StationFileInfo {
        String fileName;
        int stationCount;
        
        StationFileInfo(String fileName, int stationCount) {
            this.fileName = fileName;
            this.stationCount = stationCount;
        }
    }

    public StationTablePanel(MapView mapView) {
        this.mapView = mapView;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Station Data"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.3);
        
        // Top: Station file list
        String[] stationListColumnNames = {"Name", "Count", "File"};
        stationListModel = new DefaultTableModel(stationListColumnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        stationListTable = new JTable(stationListModel);
        stationListTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stationListTable.setRowHeight(20);
        stationListTable.getTableHeader().setReorderingAllowed(false);
        
        stationListTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = stationListTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < stationFileInfos.size()) {
                    StationFileInfo selectedFile = stationFileInfos.get(selectedRow);
                    displayStationData(selectedFile);
                }
            }
        });
        
        JScrollPane stationListScrollPane = new JScrollPane(stationListTable);
        stationListScrollPane.setPreferredSize(new Dimension(500, 100));
        splitPane.setTopComponent(stationListScrollPane);
        
        // Bottom: Data display table
        String[] dataColumnNames = {"Code", "Latitude", "Longitude", "Depth (km)", "P Correction", "S Correction"};
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
        
        // Initialize debounce timer for row selection
        selectionDebounceTimer = new javax.swing.Timer(100, e -> {
            // This will be set in the listener
        });
        selectionDebounceTimer.setRepeats(false);
        
        dataTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    // Cancel previous timer
                    selectionDebounceTimer.stop();
                    
                    // Get selected rows
                    final int selectedRow = dataTable.getSelectedRow();
                    final int listRow = stationListTable.getSelectedRow();
                    
                    // Set up debounced action
                    selectionDebounceTimer = new javax.swing.Timer(50, evt -> {
                        // Execute map update asynchronously to prevent UI freezing
                        SwingUtilities.invokeLater(() -> {
                            if (selectedRow >= 0 && listRow >= 0 && listRow < stationFileInfos.size() && mapView != null) {
                                StationFileInfo selectedFile = stationFileInfos.get(listRow);
                                List<Station> stationList = stationData.get(selectedFile);
                                
                                if (stationList != null && selectedRow < stationList.size()) {
                                    Station s = stationList.get(selectedRow);
                                    try {
                                        mapView.highlightPoint(s.getLon(), s.getLat(), "station", null, null);
                                    } catch (Exception ex) {
                                    }
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
        
        dataTable.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int selectedColumn = dataTable.getSelectedColumn();
                    int listRow = stationListTable.getSelectedRow();
                    
                    if (selectedColumn >= 0 && listRow >= 0 && listRow < stationFileInfos.size()) {
                        StationFileInfo selectedFile = stationFileInfos.get(listRow);
                        List<Station> stationList = stationData.get(selectedFile);
                        if (stationList != null && !stationList.isEmpty()) {
                            handleColumnSelection(selectedColumn);
                        }
                    }
                }
            }
        });
        
        JScrollPane dataScrollPane = new JScrollPane(dataTable);
        dataScrollPane.setPreferredSize(new Dimension(500, 300));
        splitPane.setBottomComponent(dataScrollPane);
        
        add(splitPane, BorderLayout.CENTER);

        statusLabel = new JLabel("No station files loaded");
        add(statusLabel, BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton addButton = new JButton("Add Station File");
        addButton.addActionListener(e -> selectStationFile());
        buttonPanel.add(addButton);
        
        removeButton = new JButton("Remove Selected");
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> removeSelectedStationFile());
        buttonPanel.add(removeButton);
        
        add(buttonPanel, BorderLayout.NORTH);
        
        // Register shared file manager listener
        com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().addStationFileListener(file -> {
            if (file != null) {
                loadStationFile(file);
            }
        });
        
        // Load station file if already set
        File existingFile = com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getStationFile();
        if (existingFile != null && existingFile.exists()) {
            loadStationFile(existingFile);
        }
    }

    private void selectStationFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Station File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Station files (*.tbl)", "tbl"));
        
        com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            addStationFile(selectedFile);
            
            // 共有ファイルマネージャーに通知
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setStationFile(selectedFile);
        }
    }
    
    private void addStationFile(File file) {
        try {
            List<Station> stationList = new ArrayList<>();
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
                        stationList.add(station);
                    }
                }
            }
            
            if (stationList.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "No valid station data found in the file.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Create StationFileInfo and add to list
            String fileName = file.getName();
            StationFileInfo fileInfo = new StationFileInfo(fileName, stationList.size());
            stationFileInfos.add(fileInfo);
            stationFiles.put(fileInfo, file);
            stationData.put(fileInfo, stationList);
            
            // Update station list table
            stationListModel.addRow(new Object[]{
                fileName,
                stationList.size(),
                file.getAbsolutePath()
            });
            
            removeButton.setEnabled(true);
            
            updateStatusLabel();
            
            // Display first file automatically
            if (stationFileInfos.size() == 1) {
                stationListTable.setRowSelectionInterval(0, 0);
                displayStationData(fileInfo);
            }
            
            // Plot stations on map
            if (mapView != null && !stationList.isEmpty()) {
                try {
                    mapView.showStations(stationList);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                        "Failed to display on map: " + ex.getMessage(),
                        "Warning", JOptionPane.WARNING_MESSAGE);
                }
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to load station file: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void displayStationData(StationFileInfo fileInfo) {
        List<Station> stationList = stationData.get(fileInfo);
        if (stationList == null) {
            dataTableModel.setRowCount(0);
            return;
        }
        
        dataTableModel.setRowCount(0);
        for (Station station : stationList) {
            double depKm = -station.getDep() / 1000.0;
            Object[] row = {
                station.getCode(),
                String.format("%.3f", station.getLat()),
                String.format("%.3f", station.getLon()),
                String.format("%.3f", depKm),
                String.format("%.3f", station.getPc()),
                String.format("%.3f", station.getSc())
            };
            dataTableModel.addRow(row);
        }
        
        updateStatusLabel();
    }
    
    private void removeSelectedStationFile() {
        int selectedRow = stationListTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        
        StationFileInfo fileInfo = stationFileInfos.get(selectedRow);
        stationFileInfos.remove(selectedRow);
        stationFiles.remove(fileInfo);
        stationData.remove(fileInfo);
        stationListModel.removeRow(selectedRow);
        
        dataTableModel.setRowCount(0);
        
        removeButton.setEnabled(!stationFileInfos.isEmpty());
        updateStatusLabel();
    }
    
    private void updateStatusLabel() {
        if (stationFileInfos.isEmpty()) {
            statusLabel.setText("No station files loaded");
        } else {
            int totalStations = stationData.values().stream().mapToInt(List::size).sum();
            statusLabel.setText(String.format("Files: %d, Total Stations: %d", 
                stationFileInfos.size(), totalStations));
        }
    }

    /**
     * 観測点ファイルを読み込む
     */
    public void loadStationFile(File file) {
        addStationFile(file);
    }

    /**
     * StationRepositoryを取得
     */
    public StationRepository getStationRepository() {
        // Return the repository from first station file if available
        if (!stationFileInfos.isEmpty()) {
            StationFileInfo firstFile = stationFileInfos.get(0);
            List<Station> firstStations = stationData.get(firstFile);
            if (firstStations != null && !firstStations.isEmpty()) {
                return StationRepository.fromList(firstStations);
            }
        }
        return stationRepository;
    }

    /**
     * 観測点ファイルのパスを取得
     */
    public String getStationFilePath() {
        // Current implementation does not keep file path,
        // add if needed
        return null;
    }
    
    /**
     * Handle column selection (apply coloring for numeric columns)
     */
    private void handleColumnSelection(int columnIndex) {
        int listRow = stationListTable.getSelectedRow();
        if (listRow < 0 || listRow >= stationFileInfos.size() || mapView == null) {
            return;
        }
        
        StationFileInfo selectedFile = stationFileInfos.get(listRow);
        List<Station> stationList = stationData.get(selectedFile);
        
        if (stationList == null || stationList.isEmpty()) {
            return;
        }
        
        // 数値列かどうかを判定（緯度、経度、深度、P波補正、S波補正）
        // 0:コード, 1:緯度, 2:経度, 3:深度, 4:P波補正, 5:S波補正
        boolean isNumeric = columnIndex >= 1 && columnIndex <= 5;
        
        if (isNumeric) {
            double[] values = new double[stationList.size()];
            for (int i = 0; i < stationList.size(); i++) {
                Station s = stationList.get(i);
                switch (columnIndex) {
                    case 1: // 緯度
                        values[i] = s.getLat();
                        break;
                    case 2: // 経度
                        values[i] = s.getLon();
                        break;
                    case 3: // 深度
                        // Display is in km, but original data is in m, so be careful
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
                String columnName = dataTableModel.getColumnName(columnIndex);
                mapView.showStations(stationList, columnName, values);
            } catch (Exception e) {
                System.err.println("Failed to apply coloring: " + e.getMessage());
            }
        } else {
            // Normal display for non-numeric columns
            try {
                mapView.showStations(stationList);
            } catch (Exception e) {
                System.err.println("Failed to update display: " + e.getMessage());
            }
        }
    }
}

