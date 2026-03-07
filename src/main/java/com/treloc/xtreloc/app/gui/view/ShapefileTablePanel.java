package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.controller.MapController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShapefileTablePanel extends JPanel {
    private static final Logger logger = Logger.getLogger(ShapefileTablePanel.class.getName());
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

        String[] columnNames = {"Visible", "File Name", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
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
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.setRowHeight(20);
        table.getTableHeader().setReorderingAllowed(false);
        table.getModel().addTableModelListener(e -> {
            if (e.getColumn() == 0) {
                int row = e.getFirstRow();
                if (row >= 0 && row < shapefiles.size()) {
                    File shapefile = shapefiles.get(row);
                    Boolean visible = (Boolean) tableModel.getValueAt(row, 0);
                    toggleShapefileVisibility(shapefile, visible);
                }
            }
        });
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.3);
        JScrollPane listScrollPane = new JScrollPane(table);
        listScrollPane.setPreferredSize(new Dimension(500, 150));
        splitPane.setTopComponent(listScrollPane);
        
        // Bottom: Shapefile attribute data table
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
        
        com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);
        
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

            if (mapView == null || mapController == null) {
                return;
            }

            final File f = file;
            com.treloc.xtreloc.app.gui.view.ViewerStatusBar.startLoading("Loading shapefile...");
            new SwingWorker<ShapefileLoadResult, Void>() {
                @Override
                protected ShapefileLoadResult doInBackground() throws Exception {
                    org.geotools.api.data.DataStore store = org.geotools.api.data.FileDataStoreFinder.getDataStore(f);
                    if (store == null) {
                        throw new Exception("Failed to create DataStore for shapefile. " +
                            "Make sure the shapefile is valid and all required files (.shp, .shx, .dbf) are present.");
                    }
                    String[] typeNames = store.getTypeNames();
                    if (typeNames == null || typeNames.length == 0) {
                        store.dispose();
                        throw new Exception("No type names found in shapefile.");
                    }
                    String typeName = typeNames[0];
                    org.geotools.api.data.FeatureSource featureSource = store.getFeatureSource(typeName);
                    if (featureSource == null) {
                        store.dispose();
                        throw new Exception("Failed to get FeatureSource from shapefile.");
                    }
                    return new ShapefileLoadResult(f, featureSource);
                }
                @Override
                protected void done() {
                    com.treloc.xtreloc.app.gui.view.ViewerStatusBar.stopLoading();
                    try {
                        ShapefileLoadResult result = get();
                        String layerTitle = mapController.loadShapefile(result.file, result.featureSource);
                        shapefiles.add(result.file);
                        shapefileLayerTitles.put(result.file, layerTitle);
                        shapefileFeatureSources.put(result.file, result.featureSource);
                        Object[] row = { true, result.file.getName(), "Loaded" };
                        tableModel.addRow(row);
                        statusLabel.setText(String.format("Shapefile: %d loaded", shapefiles.size()));
                        if (shapefiles.size() == 1) {
                            table.setRowSelectionInterval(0, 0);
                            displayShapefileData(result.file);
                        }
                    } catch (java.util.concurrent.CancellationException e) {
                        // ignore
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, "Shapefile load error: " + f.getAbsolutePath() + " - " + ex.getMessage(), ex);
                        StringBuilder errorMsg = new StringBuilder("Failed to display shapefile on map:\n");
                        errorMsg.append("  File: ").append(f.getAbsolutePath()).append("\n");
                        errorMsg.append("  Error: ").append(ex.getMessage()).append("\n");
                        JOptionPane.showMessageDialog(ShapefileTablePanel.this,
                            errorMsg.toString(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load shapefile: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(this,
                "Failed to load shapefile: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("Load error: " + e.getMessage());
        }
    }

    private static final class ShapefileLoadResult {
        final File file;
        final org.geotools.api.data.FeatureSource featureSource;
        ShapefileLoadResult(File file, org.geotools.api.data.FeatureSource featureSource) {
            this.file = file;
            this.featureSource = featureSource;
        }
    }
    
    /** Displays attribute data for the selected shapefile. */
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
            org.geotools.api.feature.type.FeatureType featureType = featureSource.getSchema();
            java.util.Collection<org.geotools.api.feature.type.PropertyDescriptor> descriptorsCollection = featureType.getDescriptors();
            java.util.List<org.geotools.api.feature.type.PropertyDescriptor> descriptors = new java.util.ArrayList<>(descriptorsCollection);
            org.geotools.api.data.Query query = org.geotools.api.data.Query.ALL;
            var features = featureSource.getFeatures(query);
            try (var featureIterator = features.features()) {
                if (featureIterator.hasNext()) {
                    var firstFeature = featureIterator.next();
                    for (org.geotools.api.feature.type.PropertyDescriptor descriptor : descriptors) {
                        String name = descriptor.getName().toString();
                        var property = firstFeature.getProperty(name);
                        Object value = property != null ? property.getValue() : null;
                        String valueStr = value != null ? value.toString() : "";
                        
                        dataTableModel.addRow(new Object[]{name, valueStr});
                    }
                } else {
                    for (org.geotools.api.feature.type.PropertyDescriptor descriptor : descriptors) {
                        String name = descriptor.getName().toString();
                        dataTableModel.addRow(new Object[]{name, ""});
                    }
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load shapefile attributes: " + e.getMessage(), e);
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

    /** Returns the first selected shapefile (for backward compatibility). */
    public File getSelectedShapefile() {
        return shapefiles.isEmpty() ? null : shapefiles.get(0);
    }

    /** Returns all loaded shapefiles. */
    public List<File> getShapefiles() {
        return new ArrayList<>(shapefiles);
    }
}

