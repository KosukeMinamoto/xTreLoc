package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.MapLayerStyle;
import com.treloc.xtreloc.app.gui.util.TableHighlightToggle;
import com.treloc.xtreloc.app.gui.util.ToggleAscRowSorter;
import com.treloc.xtreloc.io.Station;
import com.treloc.xtreloc.io.StationRepository;
import com.treloc.xtreloc.solver.grid.NllGridStations;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Scanner;

/**
 * Panel for displaying station data. Supports multiple station files (like Catalog):
 * list of files at top (Visible, Name, Count, File), data table with per-station Use below.
 */
public class StationTablePanel extends JPanel {
    /** Per-file station data (name, file, stations, visible). */
    static final class StationFileInfo {
        String name;
        File file;
        java.util.List<Station> stations;
        boolean visible;
        boolean fromVelocity;
        MapLayerStyle fileStyle = MapLayerStyle.defaultStation();
        /** Station codes marked Use. New loads start with every station checked. */
        final Set<String> usedCodes = new LinkedHashSet<>();

        StationFileInfo(String name, File file, java.util.List<Station> stations, boolean fromVelocity) {
            this.name = name;
            this.file = file;
            this.stations = stations != null ? new ArrayList<>(stations) : new ArrayList<>();
            this.visible = true;
            this.fromVelocity = fromVelocity;
            checkAll();
        }

        MapLayerStyle styleOf() {
            return fileStyle;
        }

        boolean isUsed(Station s) {
            return s != null && s.getCode() != null && usedCodes.contains(s.getCode());
        }

        void setUsed(Station s, boolean used) {
            if (s == null || s.getCode() == null) {
                return;
            }
            if (used) {
                usedCodes.add(s.getCode());
            } else {
                usedCodes.remove(s.getCode());
            }
        }

        java.util.List<Station> usedStations() {
            java.util.List<Station> out = new ArrayList<>();
            for (Station s : stations) {
                if (isUsed(s)) {
                    out.add(s);
                }
            }
            return out;
        }

        boolean allUsed() {
            if (stations.isEmpty()) {
                return false;
            }
            for (Station s : stations) {
                if (!isUsed(s)) {
                    return false;
                }
            }
            return true;
        }

        void checkAll() {
            usedCodes.clear();
            for (Station s : stations) {
                if (s != null && s.getCode() != null) {
                    usedCodes.add(s.getCode());
                }
            }
        }

        void uncheckAll() {
            usedCodes.clear();
        }

        String selectedCountLabel() {
            return usedCodes.size() + "/" + stations.size();
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
    private boolean updatingUseColumn;
    private final java.util.List<Runnable> stationsChangedListeners = new ArrayList<>();

    public StationTablePanel(MapView mapView) {
        this.mapView = mapView;
        setLayout(new BorderLayout());
        if (mapView != null) {
            mapView.setStationStyleSink(this::applyStationStyle);
        }

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.3);

        String[] listColumnNames = {"Visible", "Name", "Selected", "File"};
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
                if (TableHighlightToggle.isClearing(stationListTable)) {
                    return;
                }
                int selectedRow = stationListTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < stationFileInfos.size()) {
                    displayStationData(stationFileInfos.get(selectedRow));
                } else {
                    dataTableModel.setRowCount(0);
                    currentStations = null;
                    if (mapView != null) {
                        mapView.clearHighlight();
                    }
                }
            }
        });
        TableHighlightToggle.install(stationListTable, () -> {
            if (mapView != null) {
                mapView.clearHighlight();
            }
        });

        JScrollPane listScrollPane = new JScrollPane(stationListTable);
        listScrollPane.setPreferredSize(new Dimension(500, 150));
        splitPane.setTopComponent(listScrollPane);

        String[] dataColumnNames = {"Use", "Code", "Latitude", "Longitude", "Depth (km)", "P Correction", "S Correction"};
        dataTableModel = new DefaultTableModel(dataColumnNames, 0) {
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
        dataTable = new JTable(dataTableModel);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        dataTable.setFillsViewportHeight(true);
        dataTable.setRowHeight(20);
        dataTable.getTableHeader().setReorderingAllowed(false);
        dataTable.getColumnModel().getColumn(0).setPreferredWidth(44);
        dataTable.getColumnModel().getColumn(0).setMaxWidth(56);
        dataTable.getTableHeader().setToolTipText(
            "Click Use to check or uncheck all. Other headers: ascending, descending, original order");
        ToggleAscRowSorter<DefaultTableModel> stationSorter = new ToggleAscRowSorter<>(dataTableModel);
        stationSorter.setSortable(0, false);
        stationSorter.textColumns(1);
        stationSorter.numericColumns(2, 3, 4, 5, 6);
        dataTable.setRowSorter(stationSorter);
        dataTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = dataTable.columnAtPoint(e.getPoint());
                if (viewCol < 0) {
                    return;
                }
                int modelCol = dataTable.convertColumnIndexToModel(viewCol);
                if (modelCol == 0) {
                    StationFileInfo info = selectedFileInfo();
                    if (info != null) {
                        setAllUsed(!info.allUsed());
                    }
                }
            }
        });

        dataTableModel.addTableModelListener(e -> {
            if (updatingUseColumn || e.getColumn() != 0) {
                return;
            }
            int row = e.getFirstRow();
            StationFileInfo info = selectedFileInfo();
            if (info == null || currentStations == null || row < 0 || row >= currentStations.size()) {
                return;
            }
            Boolean used = (Boolean) dataTableModel.getValueAt(row, 0);
            info.setUsed(currentStations.get(row), Boolean.TRUE.equals(used));
            updateMapFromVisibleStations();
            updateStationRepository();
            updateStatusLabel();
        });

        dataTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    if (TableHighlightToggle.isClearing(dataTable)) {
                        return;
                    }
                    int selectedRow = ToggleAscRowSorter.modelRow(dataTable, dataTable.getSelectedRow());
                    if (selectedRow >= 0 && currentStations != null && selectedRow < currentStations.size() && mapView != null) {
                        Station s = currentStations.get(selectedRow);
                        MapLayerStyle st = styleOf(s);
                        try {
                            mapView.highlightPoint(s.getLon(), s.getLat(), "station",
                                st != null ? st.getSymbol() : CatalogInfo.SymbolType.TRIANGLE,
                                st != null ? st.colorWithOpacity() : Color.BLACK,
                                "station|" + s.getCode());
                        } catch (Exception ex) {
                        }
                    } else if (mapView != null) {
                        mapView.clearHighlight();
                    }
                }
            }
        });
        TableHighlightToggle.install(dataTable, () -> {
            if (mapView != null) {
                mapView.clearHighlight();
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
        java.net.URL refreshUrl = StationTablePanel.class.getResource("/images/Refresh.png");
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
        com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().addTaupFileListener(spec -> {
            if (spec == null || spec.isBlank()) {
                return;
            }
            File f = new File(spec);
            if (NllGridStations.isNllStationSource(f.toPath())) {
                replaceVelocityStations(f);
            }
        });
        com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().addPlotFilterListener(this::updateMapFromVisibleStations);

        File existingFile = com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getStationFile();
        if (existingFile != null && existingFile.exists()) {
            addStationFile(existingFile);
        }
        String taup = com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getTaupFile();
        if (taup != null && !taup.isBlank()) {
            File f = new File(taup);
            if (NllGridStations.isNllStationSource(f.toPath())) {
                replaceVelocityStations(f);
            }
        }
    }

    /**
     * Station chosen with a velocity model: replace the previous velocity-linked set, add it to this
     * tab, draw it, and publish it as the Solver station file.
     */
    public void setLinkedStationFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        for (int i = stationFileInfos.size() - 1; i >= 0; i--) {
            if (stationFileInfos.get(i).fromVelocity) {
                stationFileInfos.remove(i);
                stationListModel.removeRow(i);
            }
        }
        addStationFile(file, true);
        com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setStationFile(file);
    }

    /**
     * Travel-time rebuilds must replace the previous NLL-derived station set, not append another copy.
     * A hand-loaded {@code station.tbl} already has the same sites, so those grids are not added.
     */
    private void replaceVelocityStations(File file) {
        if (file == null) {
            return;
        }
        for (int i = stationFileInfos.size() - 1; i >= 0; i--) {
            StationFileInfo info = stationFileInfos.get(i);
            boolean nll = info.fromVelocity
                || (info.file != null && NllGridStations.isNllStationSource(info.file.toPath()));
            if (nll) {
                stationFileInfos.remove(i);
                stationListModel.removeRow(i);
            }
        }
        boolean hasManual = false;
        for (StationFileInfo info : stationFileInfos) {
            if (!info.fromVelocity) {
                hasManual = true;
                break;
            }
        }
        if (hasManual) {
            if (stationFileInfos.isEmpty()) {
                removeButton.setEnabled(false);
                dataTableModel.setRowCount(0);
                currentStations = null;
            }
            updateMapFromVisibleStations();
            updateStationRepository();
            updateStatusLabel();
            return;
        }
        addStationFile(file, true);
    }

    private void selectStationFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Station File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Station / NonLinLoc (*.tbl, *.hdr)", "tbl", "hdr"));
        com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            // Only notify shared state; our addStationFileListener will call addStationFile once (avoids double load).
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setStationFile(selectedFile);
        }
    }

    private void addStationFile(File file) {
        addStationFile(file, false);
    }

    private void addStationFile(File file, boolean fromVelocity) {
        if (file == null || alreadyLoaded(file)) {
            return;
        }
        java.util.List<Station> stations = loadStationsFromFile(file);
        if (stations == null) return;

        String name = file.getName();
        if (name.endsWith(".tbl")) name = name.substring(0, name.length() - 4);
        if (name.endsWith(".time.hdr")) name = name.substring(0, name.length() - 9);
        if (name.endsWith(".hdr")) name = name.substring(0, name.length() - 4);
        StationFileInfo info = new StationFileInfo(name, file, stations, fromVelocity);
        stationFileInfos.add(info);

        int row = stationFileInfos.size() - 1;
        stationListModel.addRow(new Object[]{
            info.visible,
            info.name,
            info.selectedCountLabel(),
            file.getPath()
        });

        removeButton.setEnabled(true);
        stationListTable.getSelectionModel().setSelectionInterval(row, row);
        displayStationData(info);
        updateMapFromVisibleStations();
        updateStationRepository();
        updateStatusLabel();
        if (NllGridStations.isNllStationSource(file.toPath())) {
            com.treloc.xtreloc.app.gui.util.GuiExecutionLog.info(
                "Stations from NonLinLoc header: " + stations.size() + " at " + file.getPath());
        }
        fireStationsChanged();
    }

    /** Called after a station file is added, reloaded, or removed. */
    public void addStationsChangedListener(Runnable listener) {
        if (listener != null) {
            stationsChangedListeners.add(listener);
        }
    }

    private void fireStationsChanged() {
        for (Runnable r : new ArrayList<>(stationsChangedListeners)) {
            r.run();
        }
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
        info.checkAll();
        stationListModel.setValueAt(info.selectedCountLabel(), row, 2);
        displayStationData(info);
        updateMapFromVisibleStations();
        updateStationRepository();
        updateStatusLabel();
        fireStationsChanged();
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
        fireStationsChanged();
    }

    private boolean alreadyLoaded(File file) {
        String path = file.getAbsolutePath();
        String setKey = NllGridStations.setKey(file.toPath());
        for (StationFileInfo info : stationFileInfos) {
            if (info.file != null && path.equals(info.file.getAbsolutePath())) {
                return true;
            }
            if (setKey != null && setKey.equals(NllGridStations.setKey(info.file == null ? null : info.file.toPath()))) {
                return true;
            }
        }
        return false;
    }

    /** Load stations from file; returns null on error. */
    private java.util.List<Station> loadStationsFromFile(File file) {
        try {
            if (NllGridStations.isNllStationSource(file.toPath())) {
                return NllGridStations.read(file.toPath());
            }
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
        updatingUseColumn = true;
        try {
            dataTableModel.setRowCount(0);
            for (Station s : info.stations) {
                double depKm = -s.getDep() / 1000.0;
                dataTableModel.addRow(new Object[]{
                    info.isUsed(s),
                    s.getCode(),
                    String.format("%.3f", s.getLat()),
                    String.format("%.3f", s.getLon()),
                    String.format("%.3f", depKm),
                    String.format("%.3f", s.getPc()),
                    String.format("%.3f", s.getSc())
                });
            }
        } finally {
            updatingUseColumn = false;
        }
        dataTable.clearSelection();
    }

    private StationFileInfo selectedFileInfo() {
        int row = stationListTable.getSelectedRow();
        if (row < 0 || row >= stationFileInfos.size()) {
            return null;
        }
        return stationFileInfos.get(row);
    }

    private void setAllUsed(boolean used) {
        StationFileInfo info = selectedFileInfo();
        if (info == null) {
            return;
        }
        if (used) {
            info.checkAll();
        } else {
            info.uncheckAll();
        }
        if (currentStations == info.stations) {
            updatingUseColumn = true;
            try {
                for (int i = 0; i < dataTableModel.getRowCount(); i++) {
                    dataTableModel.setValueAt(used, i, 0);
                }
            } finally {
                updatingUseColumn = false;
            }
        }
        updateMapFromVisibleStations();
        updateStationRepository();
        updateStatusLabel();
    }

    private void updateMapFromVisibleStations() {
        java.util.List<Station> displayed = filterForPlot(displayedStationsMerged());
        if (mapView != null) {
            mapView.setStationStyleEntries(visibleStyleEntries());
            mapView.setFilledStations(filterForPlot(usedStationsMerged()));
            try {
                mapView.showStations(displayed);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to display on map: " + ex.getMessage(),
                    "Warning", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    /** Stations of every Visible file (checked and unchecked). Shown on the map. */
    private java.util.List<Station> displayedStationsMerged() {
        java.util.List<Station> merged = new ArrayList<>();
        for (StationFileInfo info : stationFileInfos) {
            if (info.visible) {
                merged.addAll(info.stations);
            }
        }
        return merged;
    }

    private static java.util.List<Station> filterForPlot(java.util.List<Station> src) {
        if (src == null || src.isEmpty()) {
            return src;
        }
        com.treloc.xtreloc.app.gui.util.SharedFileManager sm =
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance();
        java.util.List<Station> out = new ArrayList<>();
        for (Station s : src) {
            if (s != null && sm.shouldPlotStation(s.getCode())) {
                out.add(s);
            }
        }
        return out;
    }

    /** Use-checked stations of every Visible file (solver / travel-time source). */
    private java.util.List<Station> usedStationsMerged() {
        java.util.List<Station> merged = new ArrayList<>();
        for (StationFileInfo info : stationFileInfos) {
            if (info.visible) {
                merged.addAll(info.usedStations());
            }
        }
        return merged;
    }

    private java.util.List<MapView.StationStyleEntry> visibleStyleEntries() {
        java.util.List<MapView.StationStyleEntry> out = new ArrayList<>();
        for (StationFileInfo info : stationFileInfos) {
            if (!info.visible) {
                continue;
            }
            out.add(new MapView.StationStyleEntry("file:" + info.name, "Stations: " + info.name,
                filterForPlot(new ArrayList<>(info.stations)), info.fileStyle, true));
        }
        return out;
    }

    public void applyStationStyle(String id, MapLayerStyle style) {
        if (id == null || style == null) {
            return;
        }
        if (id.startsWith("file:")) {
            String name = id.substring("file:".length());
            for (StationFileInfo info : stationFileInfos) {
                if (name.equals(info.name)) {
                    info.fileStyle = style.copy();
                }
            }
        }
        updateMapFromVisibleStations();
    }

    MapLayerStyle styleOf(Station s) {
        if (s == null) {
            return MapLayerStyle.defaultStation();
        }
        for (StationFileInfo info : stationFileInfos) {
            for (Station st : info.stations) {
                if (st == s || (st.getCode() != null && st.getCode().equals(s.getCode()))) {
                    return info.styleOf();
                }
            }
        }
        return MapLayerStyle.defaultStation();
    }

    private void updateStationRepository() {
        java.util.List<Station> merged = usedStationsMerged();
        stationRepository = merged.isEmpty() ? null : StationRepository.fromList(merged);
    }

    private void updateStatusLabel() {
        int total = 0;
        int used = 0;
        for (StationFileInfo info : stationFileInfos) {
            total += info.stations.size();
            used += info.usedCodes.size();
        }
        for (int i = 0; i < stationFileInfos.size(); i++) {
            stationListModel.setValueAt(stationFileInfos.get(i).selectedCountLabel(), i, 2);
        }
        if (stationFileInfos.isEmpty()) {
            statusLabel.setText("No station file loaded");
        } else {
            statusLabel.setText(String.format("%d file(s), %d / %d stations used",
                stationFileInfos.size(), used, total));
        }
    }

    /** Stations marked Use in every file currently checked as visible. */
    public java.util.List<Station> getVisibleStations() {
        return usedStationsMerged();
    }

}
