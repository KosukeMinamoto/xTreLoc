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
import java.util.Scanner;

/**
 * Panel for displaying station data. Supports multiple station files (like Catalog):
 * list of files at top (Visible, Name, Count, File), Excel-style data table below.
 */
public class StationTablePanel extends JPanel {
    /** Per-file station data (name, file, stations, visible). */
    private static class StationFileInfo {
        String name;
        File file;
        java.util.List<Station> stations;
        boolean visible;

        StationFileInfo(String name, File file, java.util.List<Station> stations) {
            this.name = name;
            this.file = file;
            this.stations = stations != null ? new ArrayList<>(stations) : new ArrayList<>();
            this.visible = true;
        }
    }

    private JTable stationListTable;
    private DefaultTableModel stationListModel;

    private JTable dataTable;
    private DefaultTableModel dataTableModel;

    private JLabel statusLabel;
    private final java.util.List<StationFileInfo> stationFileInfos = new ArrayList<>();
    private StationRepository stationRepository;
    private java.util.List<Station> currentStations; // stations of the selected file (for table and column selection)
    private MapView mapView;

    private JButton removeButton;

    public StationTablePanel(MapView mapView) {
        this.mapView = mapView;
        setLayout(new BorderLayout());

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.3);

        String[] listColumnNames = {"Visible", "Name", "Count", "File"};
        stationListModel = new DefaultTableModel(listColumnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) return Boolean.class;
                return String.class;
            }
        };
        stationListTable = new JTable(stationListModel);
        stationListTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stationListTable.setRowHeight(20);
        stationListTable.getTableHeader().setReorderingAllowed(false);

        stationListTable.getModel().addTableModelListener(e -> {
            if (e.getColumn() == 0) {
                int row = e.getFirstRow();
                if (row >= 0 && row < stationFileInfos.size()) {
                    StationFileInfo info = stationFileInfos.get(row);
                    Boolean visible = (Boolean) stationListModel.getValueAt(row, 0);
                    info.visible = Boolean.TRUE.equals(visible);
                    updateMapFromVisibleStations();
                    updateStationRepository();
                }
            }
        });

        stationListTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = stationListTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < stationFileInfos.size()) {
                    displayStationData(stationFileInfos.get(selectedRow));
                } else {
                    dataTableModel.setRowCount(0);
                    currentStations = null;
                }
            }
        });

        JScrollPane listScrollPane = new JScrollPane(stationListTable);
        listScrollPane.setPreferredSize(new Dimension(500, 150));
        splitPane.setTopComponent(listScrollPane);

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
                if (columnIndex >= 0 && currentStations != null && !currentStations.isEmpty()) {
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
                    if (selectedRow >= 0 && currentStations != null && selectedRow < currentStations.size() && mapView != null) {
                        Station s = currentStations.get(selectedRow);
                        try {
                            mapView.highlightPoint(s.getLon(), s.getLat(), "station", null, null);
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
                if (!e.getValueIsAdjusting() && dataTable.getSelectedColumn() >= 0 && currentStations != null && !currentStations.isEmpty()) {
                    handleColumnSelection(dataTable.getSelectedColumn());
                }
            }
        });

        JScrollPane dataScrollPane = new JScrollPane(dataTable);
        dataScrollPane.setPreferredSize(new Dimension(500, 300));
        splitPane.setBottomComponent(dataScrollPane);

        add(splitPane, BorderLayout.CENTER);

        statusLabel = new JLabel("No station file loaded");
        add(statusLabel, BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton();
        java.net.URL addUrl = StationTablePanel.class.getResource("/images/Add.png");
        if (addUrl != null) {
            ImageIcon addIcon = new ImageIcon(addUrl);
            Image addImg = addIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            addButton.setIcon(new ImageIcon(addImg));
        }
        addButton.setToolTipText("Add Station File");
        addButton.addActionListener(ev -> selectStationFile());
        buttonPanel.add(addButton);

        removeButton = new JButton();
        java.net.URL removeUrl = StationTablePanel.class.getResource("/images/Remove.png");
        if (removeUrl != null) {
            ImageIcon removeIcon = new ImageIcon(removeUrl);
            Image removeImg = removeIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            removeButton.setIcon(new ImageIcon(removeImg));
        }
        removeButton.setToolTipText("Remove Selected");
        removeButton.addActionListener(ev -> removeSelectedStationFile());
        removeButton.setEnabled(false);
        buttonPanel.add(removeButton);

        JButton refreshButton = new JButton();
        java.net.URL refreshUrl = StationTablePanel.class.getResource("/images/refresh.png");
        if (refreshUrl != null) {
            ImageIcon refreshIcon = new ImageIcon(refreshUrl);
            Image refreshImg = refreshIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            refreshButton.setIcon(new ImageIcon(refreshImg));
        } else {
            refreshButton.setText("↻");
        }
        refreshButton.setToolTipText("Reload selected station file from disk");
        refreshButton.addActionListener(ev -> reloadSelectedStationFile());
        refreshButton.setEnabled(false);
        buttonPanel.add(refreshButton);
        stationListTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int sel = stationListTable.getSelectedRow();
            refreshButton.setEnabled(sel >= 0 && sel < stationFileInfos.size());
        });

        add(buttonPanel, BorderLayout.NORTH);

        com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().addStationFileListener(file -> {
            if (file != null) {
                addStationFile(file);
            }
        });

        File existingFile = com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getStationFile();
        if (existingFile != null && existingFile.exists()) {
            addStationFile(existingFile);
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
            // Only notify shared state; our addStationFileListener will call addStationFile once (avoids double load).
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setStationFile(selectedFile);
        }
    }

    private void addStationFile(File file) {
        java.util.List<Station> stations = loadStationsFromFile(file);
        if (stations == null) return;

        String name = file.getName();
        if (name.endsWith(".tbl")) name = name.substring(0, name.length() - 4);
        StationFileInfo info = new StationFileInfo(name, file, stations);
        stationFileInfos.add(info);

        int row = stationFileInfos.size() - 1;
        stationListModel.addRow(new Object[]{
            info.visible,
            info.name,
            String.valueOf(info.stations.size()),
            file.getPath()
        });

        removeButton.setEnabled(true);
        stationListTable.getSelectionModel().setSelectionInterval(row, row);
        displayStationData(info);
        updateMapFromVisibleStations();
        updateStationRepository();
        updateStatusLabel();
    }

    private void reloadSelectedStationFile() {
        int row = stationListTable.getSelectedRow();
        if (row < 0 || row >= stationFileInfos.size()) return;
        StationFileInfo info = stationFileInfos.get(row);
        File file = info.file;
        if (file == null || !file.exists() || !file.isFile()) return;
        java.util.List<Station> stations = loadStationsFromFile(file);
        if (stations == null) return;
        info.stations = stations;
        stationListModel.setValueAt(String.valueOf(stations.size()), row, 2);
        displayStationData(info);
        updateMapFromVisibleStations();
        updateStationRepository();
        updateStatusLabel();
    }

    private void removeSelectedStationFile() {
        int row = stationListTable.getSelectedRow();
        if (row < 0 || row >= stationFileInfos.size()) return;

        stationFileInfos.remove(row);
        stationListModel.removeRow(row);
        if (stationFileInfos.isEmpty()) {
            removeButton.setEnabled(false);
            dataTableModel.setRowCount(0);
            currentStations = null;
            stationRepository = null;
        } else {
            int newSel = Math.min(row, stationFileInfos.size() - 1);
            stationListTable.getSelectionModel().setSelectionInterval(newSel, newSel);
            displayStationData(stationFileInfos.get(newSel));
        }
        updateMapFromVisibleStations();
        updateStationRepository();
        updateStatusLabel();
    }

    /** Load stations from file; returns null on error. */
    private java.util.List<Station> loadStationsFromFile(File file) {
        try {
            java.util.List<Station> list = new ArrayList<>();
            try (Scanner sc = new Scanner(file)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.isEmpty()) continue;
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 6) {
                        list.add(new Station(
                            parts[0],
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]),
                            Double.parseDouble(parts[4]),
                            Double.parseDouble(parts[5])
                        ));
                    }
                }
            }
            return list;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to load station file: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void displayStationData(StationFileInfo info) {
        currentStations = info.stations;
        dataTableModel.setRowCount(0);
        for (Station s : info.stations) {
            double depKm = -s.getDep() / 1000.0;
            dataTableModel.addRow(new Object[]{
                s.getCode(),
                String.format("%.3f", s.getLat()),
                String.format("%.3f", s.getLon()),
                String.format("%.3f", depKm),
                String.format("%.3f", s.getPc()),
                String.format("%.3f", s.getSc())
            });
        }
    }

    private void updateMapFromVisibleStations() {
        java.util.List<Station> merged = new ArrayList<>();
        for (StationFileInfo info : stationFileInfos) {
            if (info.visible) merged.addAll(info.stations);
        }
        if (mapView != null) {
            try {
                if (merged.isEmpty()) {
                    mapView.showStations(new ArrayList<>());
                } else {
                    mapView.showStations(merged);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to display on map: " + ex.getMessage(),
                    "Warning", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void updateStationRepository() {
        java.util.List<Station> merged = new ArrayList<>();
        for (StationFileInfo info : stationFileInfos) {
            if (info.visible) merged.addAll(info.stations);
        }
        stationRepository = merged.isEmpty() ? null : StationRepository.fromList(merged);
    }

    private void updateStatusLabel() {
        int total = 0;
        for (StationFileInfo info : stationFileInfos) total += info.stations.size();
        if (stationFileInfos.isEmpty()) {
            statusLabel.setText("No station file loaded");
        } else {
            statusLabel.setText(String.format("%d file(s), total %d stations", stationFileInfos.size(), total));
        }
    }

    public StationRepository getStationRepository() {
        return stationRepository;
    }

    public String getStationFilePath() {
        if (stationFileInfos.isEmpty()) return null;
        return stationFileInfos.get(0).file.getPath();
    }

    private void handleColumnSelection(int columnIndex) {
        if (currentStations == null || currentStations.isEmpty() || mapView == null) return;

        String columnName = dataTableModel.getColumnName(columnIndex);
        boolean isNumeric = columnIndex >= 1 && columnIndex <= 5;
        if (isNumeric) {
            double[] values = new double[currentStations.size()];
            for (int i = 0; i < currentStations.size(); i++) {
                Station s = currentStations.get(i);
                switch (columnIndex) {
                    case 1: values[i] = s.getLat(); break;
                    case 2: values[i] = s.getLon(); break;
                    case 3: values[i] = -s.getDep() / 1000.0; break;
                    case 4: values[i] = s.getPc(); break;
                    case 5: values[i] = s.getSc(); break;
                    default: values[i] = -s.getDep() / 1000.0;
                }
            }
            try {
                mapView.showStations(currentStations, columnName, values);
            } catch (Exception e) {
                try {
                    mapView.showStations(currentStations);
                } catch (Exception e2) {
                    // ignore
                }
            }
        } else {
            try {
                mapView.showStations(currentStations);
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
