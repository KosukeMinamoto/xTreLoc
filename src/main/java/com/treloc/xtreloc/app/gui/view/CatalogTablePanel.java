package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.app.gui.service.CatalogLoader;
import com.treloc.xtreloc.app.gui.service.CsvExporter;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;
import com.treloc.xtreloc.app.gui.util.GuiExecutionLog;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Panel for displaying catalog data in an Excel-like table format.
 * Supports multiple catalogs (same structure as ShapefileTablePanel).
 * 
 * @author K.Minamoto
 */
public class CatalogTablePanel extends JPanel {
    private static final Logger logger = Logger.getLogger(CatalogTablePanel.class.getName());
    private static final String[] DATA_COLUMN_NAMES = {"Row", "Time", "Latitude", "Longitude", "Depth (km)", "xerr (km)", "yerr (km)", "zerr (km)", "rms", "Cluster ID", "File Path"};

    private JTable catalogListTable;
    private DefaultTableModel catalogListModel;
    
    private JTable dataTable;
    private DefaultTableModel dataTableModel;
    
    private JLabel statusLabel;
    private MapView mapView;
    private java.util.List<CatalogLoadListener> catalogLoadListeners = new java.util.ArrayList<>();
    private java.util.List<Runnable> onCatalogFileLoadedListeners = new java.util.ArrayList<>();
    private java.util.List<Runnable> screeningChangeListeners = new java.util.ArrayList<>();
    /** Hist/Scatter refresh when map color column or scatter axes change */
    private java.util.List<Runnable> viewerChartChangeListeners = new java.util.ArrayList<>();
    
    private java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> catalogInfos = new java.util.ArrayList<>();
    private java.util.Map<com.treloc.xtreloc.app.gui.model.CatalogInfo, File> catalogFiles = new java.util.HashMap<>();
    
    private List<Hypocenter> hypocenters;
    private File currentCatalogFile;
    
    private JButton removeButton;
    
    private JToggleButton errorEllipseToggleButton;

    private boolean screeningEnabled;
    private JTextField rmsMinField;
    private JTextField rmsMaxField;
    private JTextField depthMinField;
    private JTextField depthMaxField;
    private JTextField latMinField;
    private JTextField latMaxField;
    private JTextField lonMinField;
    private JTextField lonMaxField;
    private JTextField xerrMinField;
    private JTextField xerrMaxField;
    private JTextField yerrMinField;
    private JTextField yerrMaxField;
    private JTextField zerrMinField;
    private JTextField zerrMaxField;
    private JTextField timeMinField;
    private JTextField timeMaxField;
    private JTextField clusterMinField;
    private JTextField clusterMaxField;
    private JCheckBox modeGrdCheck;
    private JCheckBox modeLmoCheck;
    private JCheckBox modeMcmcCheck;
    private JCheckBox modeDeCheck;
    private JCheckBox modeTrdCheck;
    private JCheckBox modeClsCheck;
    private JCheckBox modeSynCheck;
    
    // Debounce timer for row selection to prevent UI freezing
    private javax.swing.Timer selectionDebounceTimer;
    private SwingWorker<?, ?> displayCatalogWorker;

    /** Model index of the column currently used for map color coding (-1 if none). */
    private int colorColumnIndex = -1;

    /** Scatter X/Y: model indices (Latitude..rms = 2..8), chosen from table header context menu. */
    private int scatterXColumnModelIndex = 2;
    private int scatterYColumnModelIndex = 4;

    private static boolean isScatterAxisColumn(int modelIndex) {
        return modelIndex >= 2 && modelIndex <= 8;
    }
    
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

    /**
     * Adds a callback run when a new catalog file has been loaded and added to the UI.
     * Use e.g. to switch the parent tab to "Catalog Data" so the user sees the result.
     */
    public void addOnCatalogFileLoaded(Runnable runnable) {
        if (runnable != null) {
            onCatalogFileLoadedListeners.add(runnable);
        }
    }

    /**
     * Adds a listener that is run when event screening (filter) is applied.
     * Use to refresh Hist/Scatter panels when Apply is pressed.
     */
    public void addScreeningChangeListener(Runnable listener) {
        screeningChangeListeners.add(listener);
    }

    /**
     * Notified when the map color column or scatter X/Y assignment changes (refresh Hist/Scatter).
     */
    public void addViewerChartChangeListener(Runnable listener) {
        if (listener != null) {
            viewerChartChangeListeners.add(listener);
        }
    }

    private void fireViewerChartChanged() {
        for (Runnable r : new java.util.ArrayList<>(viewerChartChangeListeners)) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    /** Model column for scatter X (2..8). */
    public int getScatterXColumnModelIndex() {
        return scatterXColumnModelIndex;
    }

    /** Model column for scatter Y (2..8). */
    public int getScatterYColumnModelIndex() {
        return scatterYColumnModelIndex;
    }

    /** Map color column model index (for histogram when in 2..9, excluding non-numeric columns). */
    public int getColorColumnModelIndex() {
        return colorColumnIndex;
    }

    public String getDataColumnName(int modelIndex) {
        if (modelIndex < 0 || modelIndex >= dataTableModel.getColumnCount()) {
            return null;
        }
        return dataTableModel.getColumnName(modelIndex);
    }

    public CatalogTablePanel(MapView mapView) {
        this.mapView = mapView;
        setLayout(new BorderLayout());
        AppPanelStyle.setPanelBackground(this);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.3);
        splitPane.setBackground(AppPanelStyle.getPanelBg());
        
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
        AppPanelStyle.styleTable(catalogListTable);
        catalogListTable.setSelectionBackground(AppPanelStyle.getPrimaryButtonColor());
        catalogListTable.setSelectionForeground(Color.WHITE);

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
                    displayCatalogDataAsync(selectedCatalog);
                    updateErrorEllipseCheckboxForSelection();
                } else {
                    updateErrorEllipseCheckboxForSelection();
                }
            }
        });
        
        JScrollPane catalogListScrollPane = new JScrollPane(catalogListTable);
        catalogListScrollPane.setPreferredSize(new Dimension(500, 150));
        AppPanelStyle.styleScrollPane(catalogListScrollPane);
        JPanel catalogListWrapper = new JPanel(new BorderLayout());
        AppPanelStyle.setPanelBackground(catalogListWrapper);
        catalogListWrapper.add(catalogListScrollPane, BorderLayout.CENTER);
        splitPane.setTopComponent(catalogListWrapper);
        
        // Bottom: Data display table (Excel-style)
        dataTableModel = new DefaultTableModel(DATA_COLUMN_NAMES, 0) {
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
        AppPanelStyle.styleTable(dataTable);
        dataTable.setSelectionBackground(AppPanelStyle.getPrimaryButtonColor());
        dataTable.setSelectionForeground(Color.WHITE);

        // Renderer: show hatch on the column used for map coloring
        dataTable.setDefaultRenderer(Object.class, new ColorColumnHatchRenderer());

        dataTable.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    return;
                }
                int viewCol = dataTable.getTableHeader().columnAtPoint(e.getPoint());
                if (viewCol >= 0) {
                    dataTable.clearSelection();
                    dataTable.setColumnSelectionInterval(viewCol, viewCol);
                    int modelCol = dataTable.convertColumnIndexToModel(viewCol);
                    handleColumnSelection(modelCol);
                }
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShowScatterAxisMenu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeShowScatterAxisMenu(e);
            }

            private void maybeShowScatterAxisMenu(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int viewCol = dataTable.getTableHeader().columnAtPoint(e.getPoint());
                if (viewCol < 0) {
                    return;
                }
                int modelCol = dataTable.convertColumnIndexToModel(viewCol);
                JPopupMenu menu = new JPopupMenu();
                JMenuItem miX = new JMenuItem("Scatter: set as X axis");
                JMenuItem miY = new JMenuItem("Scatter: set as Y axis");
                boolean ok = isScatterAxisColumn(modelCol);
                miX.setEnabled(ok);
                miY.setEnabled(ok);
                miX.addActionListener(ev -> {
                    scatterXColumnModelIndex = modelCol;
                    fireViewerChartChanged();
                    dataTable.repaint();
                });
                miY.addActionListener(ev -> {
                    scatterYColumnModelIndex = modelCol;
                    fireViewerChartChanged();
                    dataTable.repaint();
                });
                menu.add(miX);
                menu.add(miY);
                menu.show(e.getComponent(), e.getX(), e.getY());
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
                    
                    final int selectedRow = dataTable.getSelectedRow();
                    
                    selectionDebounceTimer = new javax.swing.Timer(50, evt -> {
                        // Execute map update asynchronously to prevent UI freezing
                        SwingUtilities.invokeLater(() -> {
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
                    if (selectedColumn >= 0 && getCurrentHypocenters() != null && !getCurrentHypocenters().isEmpty()) {
                        handleColumnSelection(dataTable.convertColumnIndexToModel(selectedColumn));
                    }
                }
            }
        });
        
        JScrollPane dataScrollPane = new JScrollPane(dataTable);
        dataScrollPane.setPreferredSize(new Dimension(500, 300));
        AppPanelStyle.styleScrollPane(dataScrollPane);
        splitPane.setBottomComponent(dataScrollPane);
        
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                refreshAllViews();
            }
        });
        
        add(splitPane, BorderLayout.CENTER);

        statusLabel = new JLabel("No catalog loaded");
        statusLabel.setForeground(AppPanelStyle.getContentTextColor());
        add(statusLabel, BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        AppPanelStyle.setPanelBackground(buttonPanel);
        
        JButton addButton = new JButton();
        java.net.URL addUrl = CatalogTablePanel.class.getResource("/images/Add.png");
        if (addUrl != null) {
            ImageIcon addIcon = new ImageIcon(addUrl);
            Image addImg = addIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            addButton.setIcon(new ImageIcon(addImg));
        }
        addButton.setToolTipText("Add Catalog");
        addButton.addActionListener(e -> selectCatalogFile());
        buttonPanel.add(addButton);
        
        removeButton = new JButton();
        java.net.URL removeUrl = CatalogTablePanel.class.getResource("/images/Remove.png");
        if (removeUrl != null) {
            ImageIcon removeIcon = new ImageIcon(removeUrl);
            Image removeImg = removeIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            removeButton.setIcon(new ImageIcon(removeImg));
        }
        removeButton.setToolTipText("Remove Selected");
        removeButton.addActionListener(e -> removeSelectedCatalog());
        removeButton.setEnabled(false);
        buttonPanel.add(removeButton);
        
        JButton refreshCatalogButton = new JButton();
        java.net.URL refreshUrl = CatalogTablePanel.class.getResource("/images/Refresh.png");
        if (refreshUrl != null) {
            ImageIcon refreshIcon = new ImageIcon(refreshUrl);
            Image refreshImg = refreshIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            refreshCatalogButton.setIcon(new ImageIcon(refreshImg));
        } else {
            refreshCatalogButton.setText("↻");
        }
        refreshCatalogButton.setToolTipText("Reload selected catalog from file");
        refreshCatalogButton.addActionListener(e -> {
            int sel = catalogListTable.getSelectedRow();
            if (sel >= 0 && sel < catalogInfos.size()) {
                com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfos.get(sel);
                File src = info.getSourceFile();
                if (src != null) {
                    reloadCatalogFile(src);
                }
            }
        });
        refreshCatalogButton.setEnabled(false);
        buttonPanel.add(refreshCatalogButton);
        catalogListTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int sel = catalogListTable.getSelectedRow();
            refreshCatalogButton.setEnabled(sel >= 0 && sel < catalogInfos.size() && catalogInfos.get(sel).getSourceFile() != null);
        });
        
        JButton screeningButton = new JButton();
        java.net.URL screeningUrl = CatalogTablePanel.class.getResource("/images/Screening.png");
        if (screeningUrl != null) {
            ImageIcon screeningIcon = new ImageIcon(screeningUrl);
            Image screeningImg = screeningIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            screeningButton.setIcon(new ImageIcon(screeningImg));
        }
        screeningButton.setToolTipText("Event screening...");
        screeningButton.addActionListener(e -> showScreeningDialog());
        buttonPanel.add(screeningButton);
        
        final ImageIcon errorIconOn;
        final ImageIcon errorIconOff;
        java.net.URL errorUrl = CatalogTablePanel.class.getResource("/images/Error.png");
        if (errorUrl != null) {
            ImageIcon errorIcon = new ImageIcon(errorUrl);
            Image errorImg = errorIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            errorIconOn = new ImageIcon(errorImg);
            Image greyed = javax.swing.GrayFilter.createDisabledImage(errorImg);
            errorIconOff = new ImageIcon(greyed);
        } else {
            errorIconOn = null;
            errorIconOff = null;
        }
        errorEllipseToggleButton = new JToggleButton(errorIconOff);
        errorEllipseToggleButton.setSelectedIcon(errorIconOn);
        if (errorIconOn != null) errorEllipseToggleButton.putClientProperty("errorEllipse.iconOn", errorIconOn);
        if (errorIconOff != null) errorEllipseToggleButton.putClientProperty("errorEllipse.iconOff", errorIconOff);
        errorEllipseToggleButton.setToolTipText("Error ellipse: Show/hide confidence ellipses for the selected catalog on the map");
        if (errorIconOn != null && errorIconOff != null) {
            errorEllipseToggleButton.setIcon(errorIconOff);
        }
        errorEllipseToggleButton.addActionListener(e -> {
            int row = catalogListTable.getSelectedRow();
            if (row >= 0 && row < catalogInfos.size() && mapView != null) {
                com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfos.get(row);
                boolean selected = errorEllipseToggleButton.isSelected();
                info.setShowErrorEllipse(selected);
                errorEllipseToggleButton.setIcon(selected ? errorIconOn : errorIconOff);
                mapView.updateMultipleCatalogsDisplay();
            }
        });
        buttonPanel.add(errorEllipseToggleButton);
        
        add(buttonPanel, BorderLayout.NORTH);
    }
    
    private static final int RANGE_FIELD_COLUMNS = 8;
    private static final Color DISABLED_PANEL_BG = new Color(220, 220, 220);

    private void showScreeningDialog() {
        JDialog d = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Event screening", true);
        d.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        JCheckBox enableCheck = new JCheckBox("Enable screening", screeningEnabled);
        JTextField rmsMin = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField rmsMax = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField depthMin = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField depthMax = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField latMin = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField latMax = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField lonMin = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField lonMax = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField xerrMin = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField xerrMax = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField yerrMin = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField yerrMax = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField zerrMin = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField zerrMax = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField timeMin = new JTextField(14);
        JTextField timeMax = new JTextField(14);
        JTextField clusterMin = new JTextField(RANGE_FIELD_COLUMNS);
        JTextField clusterMax = new JTextField(RANGE_FIELD_COLUMNS);
        JCheckBox grd = new JCheckBox("GRD", false);
        JCheckBox lmo = new JCheckBox("LMO", false);
        JCheckBox mcmc = new JCheckBox("MCMC", false);
        JCheckBox de = new JCheckBox("DE", false);
        JCheckBox trd = new JCheckBox("TRD", false);
        JCheckBox cls = new JCheckBox("CLS", false);
        JCheckBox syn = new JCheckBox("SYN", false);
        
        JPanel paramsPanel = new JPanel(new GridBagLayout());
        paramsPanel.setOpaque(true);
        GridBagConstraints pgbc = new GridBagConstraints();
        pgbc.insets = new Insets(4, 0, 4, 8);
        pgbc.anchor = GridBagConstraints.WEST;
        int pr = 0;
        pgbc.gridx = 0; pgbc.gridy = pr; pgbc.gridwidth = 1;
        paramsPanel.add(new JLabel("RMS"), pgbc);
        pgbc.gridx = 1; paramsPanel.add(rmsMin, pgbc);
        pgbc.gridx = 2; paramsPanel.add(new JLabel("–"), pgbc);
        pgbc.gridx = 3; paramsPanel.add(rmsMax, pgbc);
        pr++;
        pgbc.gridx = 0; pgbc.gridy = pr;
        paramsPanel.add(new JLabel("Depth (km)"), pgbc);
        pgbc.gridx = 1; paramsPanel.add(depthMin, pgbc);
        pgbc.gridx = 2; paramsPanel.add(new JLabel("–"), pgbc);
        pgbc.gridx = 3; paramsPanel.add(depthMax, pgbc);
        pr++;
        pgbc.gridx = 0; pgbc.gridy = pr;
        paramsPanel.add(new JLabel("Lat"), pgbc);
        pgbc.gridx = 1; paramsPanel.add(latMin, pgbc);
        pgbc.gridx = 2; paramsPanel.add(new JLabel("–"), pgbc);
        pgbc.gridx = 3; paramsPanel.add(latMax, pgbc);
        pr++;
        pgbc.gridx = 0; pgbc.gridy = pr;
        paramsPanel.add(new JLabel("Lon"), pgbc);
        pgbc.gridx = 1; paramsPanel.add(lonMin, pgbc);
        pgbc.gridx = 2; paramsPanel.add(new JLabel("–"), pgbc);
        pgbc.gridx = 3; paramsPanel.add(lonMax, pgbc);
        pr++;
        pgbc.gridx = 0; pgbc.gridy = pr;
        paramsPanel.add(new JLabel("xerr (km)"), pgbc);
        pgbc.gridx = 1; paramsPanel.add(xerrMin, pgbc);
        pgbc.gridx = 2; paramsPanel.add(new JLabel("–"), pgbc);
        pgbc.gridx = 3; paramsPanel.add(xerrMax, pgbc);
        pr++;
        pgbc.gridx = 0; pgbc.gridy = pr;
        paramsPanel.add(new JLabel("yerr (km)"), pgbc);
        pgbc.gridx = 1; paramsPanel.add(yerrMin, pgbc);
        pgbc.gridx = 2; paramsPanel.add(new JLabel("–"), pgbc);
        pgbc.gridx = 3; paramsPanel.add(yerrMax, pgbc);
        pr++;
        pgbc.gridx = 0; pgbc.gridy = pr;
        paramsPanel.add(new JLabel("zerr (km)"), pgbc);
        pgbc.gridx = 1; paramsPanel.add(zerrMin, pgbc);
        pgbc.gridx = 2; paramsPanel.add(new JLabel("–"), pgbc);
        pgbc.gridx = 3; paramsPanel.add(zerrMax, pgbc);
        pr++;
        pgbc.gridx = 0; pgbc.gridy = pr;
        paramsPanel.add(new JLabel("Time"), pgbc);
        pgbc.gridx = 1; paramsPanel.add(timeMin, pgbc);
        pgbc.gridx = 2; paramsPanel.add(new JLabel("–"), pgbc);
        pgbc.gridx = 3; paramsPanel.add(timeMax, pgbc);
        pr++;
        pgbc.gridx = 0; pgbc.gridy = pr;
        paramsPanel.add(new JLabel("Cluster num"), pgbc);
        pgbc.gridx = 1; paramsPanel.add(clusterMin, pgbc);
        pgbc.gridx = 2; paramsPanel.add(new JLabel("–"), pgbc);
        pgbc.gridx = 3; paramsPanel.add(clusterMax, pgbc);
        pr++;
        pgbc.gridx = 0; pgbc.gridy = pr; pgbc.gridwidth = 4;
        paramsPanel.add(new JLabel("Mode (leave all unchecked = no filter):"), pgbc);
        pr++;
        pgbc.gridwidth = 1;
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        modePanel.add(grd); modePanel.add(lmo); modePanel.add(mcmc); modePanel.add(de);
        modePanel.add(trd); modePanel.add(cls); modePanel.add(syn);
        pgbc.gridx = 0; pgbc.gridy = pr; pgbc.gridwidth = 4;
        paramsPanel.add(modePanel, pgbc);
        
        java.util.List<Component> paramComponents = new ArrayList<>();
        paramComponents.add(rmsMin); paramComponents.add(rmsMax);
        paramComponents.add(depthMin); paramComponents.add(depthMax);
        paramComponents.add(latMin); paramComponents.add(latMax);
        paramComponents.add(lonMin); paramComponents.add(lonMax);
        paramComponents.add(xerrMin); paramComponents.add(xerrMax);
        paramComponents.add(yerrMin); paramComponents.add(yerrMax);
        paramComponents.add(zerrMin); paramComponents.add(zerrMax);
        paramComponents.add(timeMin); paramComponents.add(timeMax);
        paramComponents.add(clusterMin); paramComponents.add(clusterMax);
        paramComponents.add(grd); paramComponents.add(lmo); paramComponents.add(mcmc);
        paramComponents.add(de); paramComponents.add(trd); paramComponents.add(cls); paramComponents.add(syn);
        
        Runnable updateParamEnabled = () -> {
            boolean on = enableCheck.isSelected();
            for (Component c : paramComponents) c.setEnabled(on);
            if (on) {
                paramsPanel.setOpaque(false);
                paramsPanel.setBackground(null);
            } else {
                paramsPanel.setOpaque(true);
                paramsPanel.setBackground(DISABLED_PANEL_BG);
            }
        };
        enableCheck.addActionListener(e -> updateParamEnabled.run());
        updateParamEnabled.run();
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        d.add(enableCheck, gbc);
        gbc.gridy = 1; gbc.gridwidth = 2;
        d.add(paramsPanel, gbc);
        gbc.gridy = 2;
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(e2 -> {
            screeningEnabled = enableCheck.isSelected();
            syncDialogToPanel(rmsMin, rmsMax, depthMin, depthMax, latMin, latMax, lonMin, lonMax,
                xerrMin, xerrMax, yerrMin, yerrMax, zerrMin, zerrMax,
                timeMin, timeMax, clusterMin, clusterMax,
                grd, lmo, mcmc, de, trd, cls, syn);
            applyScreeningFilter();
            // Keep dialog open; map is updated by applyScreeningFilter() -> refreshAfterFilter() -> mapView.repaintMap()
        });
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e2 -> {
            rmsMin.setText(""); rmsMax.setText(""); depthMin.setText(""); depthMax.setText("");
            latMin.setText(""); latMax.setText(""); lonMin.setText(""); lonMax.setText("");
            xerrMin.setText(""); xerrMax.setText(""); yerrMin.setText(""); yerrMax.setText("");
            zerrMin.setText(""); zerrMax.setText("");
            timeMin.setText(""); timeMax.setText(""); clusterMin.setText(""); clusterMax.setText("");
            grd.setSelected(false); lmo.setSelected(false); mcmc.setSelected(false); de.setSelected(false);
            trd.setSelected(false); cls.setSelected(false); syn.setSelected(false);
            clearScreeningFilter();
        });
        JButton exportBtn = new JButton("Export");
        exportBtn.setToolTipText("Save the currently displayed (screened) catalog to CSV");
        exportBtn.addActionListener(e2 -> exportScreenedCatalog(d));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e2 -> d.dispose());
        buttons.add(applyBtn); buttons.add(clearBtn); buttons.add(exportBtn); buttons.add(closeBtn);
        d.add(buttons, gbc);
        
        syncPanelToDialog(rmsMin, rmsMax, depthMin, depthMax, latMin, latMax, lonMin, lonMax,
            xerrMin, xerrMax, yerrMin, yerrMax, zerrMin, zerrMax,
            timeMin, timeMax, clusterMin, clusterMax,
            grd, lmo, mcmc, de, trd, cls, syn);
        enableCheck.setSelected(screeningEnabled);
        updateParamEnabled.run();
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }
    
    private void syncPanelToDialog(JTextField rmsMin, JTextField rmsMax, JTextField depthMin, JTextField depthMax,
                                    JTextField latMin, JTextField latMax, JTextField lonMin, JTextField lonMax,
                                    JTextField xerrMin, JTextField xerrMax, JTextField yerrMin, JTextField yerrMax,
                                    JTextField zerrMin, JTextField zerrMax,
                                    JTextField timeMin, JTextField timeMax, JTextField clusterMin, JTextField clusterMax,
                                    JCheckBox grd, JCheckBox lmo, JCheckBox mcmc, JCheckBox de, JCheckBox trd, JCheckBox cls, JCheckBox syn) {
        if (rmsMinField != null) { rmsMin.setText(rmsMinField.getText()); rmsMax.setText(rmsMaxField.getText()); }
        if (depthMinField != null) { depthMin.setText(depthMinField.getText()); depthMax.setText(depthMaxField.getText()); }
        if (latMinField != null) { latMin.setText(latMinField.getText()); latMax.setText(latMaxField.getText()); }
        if (lonMinField != null) { lonMin.setText(lonMinField.getText()); lonMax.setText(lonMaxField.getText()); }
        if (xerrMinField != null) { xerrMin.setText(xerrMinField.getText()); xerrMax.setText(xerrMaxField.getText()); }
        if (yerrMinField != null) { yerrMin.setText(yerrMinField.getText()); yerrMax.setText(yerrMaxField.getText()); }
        if (zerrMinField != null) { zerrMin.setText(zerrMinField.getText()); zerrMax.setText(zerrMaxField.getText()); }
        if (timeMinField != null) { timeMin.setText(timeMinField.getText()); timeMax.setText(timeMaxField.getText()); }
        if (clusterMinField != null) { clusterMin.setText(clusterMinField.getText()); clusterMax.setText(clusterMaxField.getText()); }
        if (modeGrdCheck != null) grd.setSelected(modeGrdCheck.isSelected());
        if (modeLmoCheck != null) lmo.setSelected(modeLmoCheck.isSelected());
        if (modeMcmcCheck != null) mcmc.setSelected(modeMcmcCheck.isSelected());
        if (modeDeCheck != null) de.setSelected(modeDeCheck.isSelected());
        if (modeTrdCheck != null) trd.setSelected(modeTrdCheck.isSelected());
        if (modeClsCheck != null) cls.setSelected(modeClsCheck.isSelected());
        if (modeSynCheck != null) syn.setSelected(modeSynCheck.isSelected());
    }
    
    private void syncDialogToPanel(JTextField rmsMin, JTextField rmsMax, JTextField depthMin, JTextField depthMax,
                                   JTextField latMin, JTextField latMax, JTextField lonMin, JTextField lonMax,
                                   JTextField xerrMin, JTextField xerrMax, JTextField yerrMin, JTextField yerrMax,
                                   JTextField zerrMin, JTextField zerrMax,
                                   JTextField timeMin, JTextField timeMax, JTextField clusterMin, JTextField clusterMax,
                                   JCheckBox grd, JCheckBox lmo, JCheckBox mcmc, JCheckBox de, JCheckBox trd, JCheckBox cls, JCheckBox syn) {
        ensureScreeningFields();
        rmsMinField.setText(rmsMin.getText()); rmsMaxField.setText(rmsMax.getText());
        depthMinField.setText(depthMin.getText()); depthMaxField.setText(depthMax.getText());
        latMinField.setText(latMin.getText()); latMaxField.setText(latMax.getText());
        lonMinField.setText(lonMin.getText()); lonMaxField.setText(lonMax.getText());
        xerrMinField.setText(xerrMin.getText()); xerrMaxField.setText(xerrMax.getText());
        yerrMinField.setText(yerrMin.getText()); yerrMaxField.setText(yerrMax.getText());
        zerrMinField.setText(zerrMin.getText()); zerrMaxField.setText(zerrMax.getText());
        timeMinField.setText(timeMin.getText()); timeMaxField.setText(timeMax.getText());
        clusterMinField.setText(clusterMin.getText()); clusterMaxField.setText(clusterMax.getText());
        modeGrdCheck.setSelected(grd.isSelected()); modeLmoCheck.setSelected(lmo.isSelected());
        modeMcmcCheck.setSelected(mcmc.isSelected()); modeDeCheck.setSelected(de.isSelected());
        modeTrdCheck.setSelected(trd.isSelected()); modeClsCheck.setSelected(cls.isSelected());
        modeSynCheck.setSelected(syn.isSelected());
    }
    
    private void ensureScreeningFields() {
        if (rmsMinField != null) return;
        rmsMinField = new JTextField(5); rmsMaxField = new JTextField(5);
        depthMinField = new JTextField(5); depthMaxField = new JTextField(5);
        latMinField = new JTextField(5); latMaxField = new JTextField(5);
        lonMinField = new JTextField(5); lonMaxField = new JTextField(5);
        xerrMinField = new JTextField(5); xerrMaxField = new JTextField(5);
        yerrMinField = new JTextField(5); yerrMaxField = new JTextField(5);
        zerrMinField = new JTextField(5); zerrMaxField = new JTextField(5);
        timeMinField = new JTextField(12); timeMaxField = new JTextField(12);
        clusterMinField = new JTextField(5); clusterMaxField = new JTextField(5);
        modeGrdCheck = new JCheckBox("GRD", false);
        modeLmoCheck = new JCheckBox("LMO", false);
        modeMcmcCheck = new JCheckBox("MCMC", false);
        modeDeCheck = new JCheckBox("DE", false);
        modeTrdCheck = new JCheckBox("TRD", false);
        modeClsCheck = new JCheckBox("CLS", false);
        modeSynCheck = new JCheckBox("SYN", false);
    }
    
    private void applyScreeningFilter() {
        if (!screeningEnabled) {
            for (com.treloc.xtreloc.app.gui.model.CatalogInfo info : catalogInfos) {
                info.clearDisplayFilter();
            }
            refreshAfterFilter();
            return;
        }
        ensureScreeningFields();
        Double rmsMin = parseDoubleOrNull(rmsMinField.getText().trim());
        Double rmsMax = parseDoubleOrNull(rmsMaxField.getText().trim());
        Double depthMin = parseDoubleOrNull(depthMinField.getText().trim());
        Double depthMax = parseDoubleOrNull(depthMaxField.getText().trim());
        Double latMin = parseDoubleOrNull(latMinField.getText().trim());
        Double latMax = parseDoubleOrNull(latMaxField.getText().trim());
        Double lonMin = parseDoubleOrNull(lonMinField.getText().trim());
        Double lonMax = parseDoubleOrNull(lonMaxField.getText().trim());
        Double xerrMin = parseDoubleOrNull(xerrMinField.getText().trim());
        Double xerrMax = parseDoubleOrNull(xerrMaxField.getText().trim());
        Double yerrMin = parseDoubleOrNull(yerrMinField.getText().trim());
        Double yerrMax = parseDoubleOrNull(yerrMaxField.getText().trim());
        Double zerrMin = parseDoubleOrNull(zerrMinField.getText().trim());
        Double zerrMax = parseDoubleOrNull(zerrMaxField.getText().trim());
        String timeMinStr = timeMinField.getText().trim();
        String timeMaxStr = timeMaxField.getText().trim();
        boolean filterByTime = !timeMinStr.isEmpty() || !timeMaxStr.isEmpty();
        Integer clusterMin = parseIntegerOrNull(clusterMinField.getText().trim());
        Integer clusterMax = parseIntegerOrNull(clusterMaxField.getText().trim());
        Set<String> allowedModes = new HashSet<>();
        if (modeGrdCheck.isSelected()) allowedModes.add("GRD");
        if (modeLmoCheck.isSelected()) allowedModes.add("LMO");
        if (modeMcmcCheck.isSelected()) allowedModes.add("MCMC");
        if (modeDeCheck.isSelected()) allowedModes.add("DE");
        if (modeTrdCheck.isSelected()) allowedModes.add("TRD");
        if (modeClsCheck.isSelected()) allowedModes.add("CLS");
        if (modeSynCheck.isSelected()) allowedModes.add("SYN");
        boolean filterByMode = !allowedModes.isEmpty();
        
        boolean hasFilter = (rmsMin != null || rmsMax != null || depthMin != null || depthMax != null
            || latMin != null || latMax != null || lonMin != null || lonMax != null
            || xerrMin != null || xerrMax != null || yerrMin != null || yerrMax != null
            || zerrMin != null || zerrMax != null || filterByTime || clusterMin != null || clusterMax != null || filterByMode);
        
        for (com.treloc.xtreloc.app.gui.model.CatalogInfo info : catalogInfos) {
            List<Hypocenter> full = info.getHypocentersFull();
            if (full == null) continue;
            if (!hasFilter) {
                info.clearDisplayFilter();
                continue;
            }
            List<Hypocenter> filtered = new ArrayList<>();
            for (Hypocenter h : full) {
                if (rmsMin != null && h.rms < rmsMin) continue;
                if (rmsMax != null && h.rms > rmsMax) continue;
                if (depthMin != null && h.depth < depthMin) continue;
                if (depthMax != null && h.depth > depthMax) continue;
                if (latMin != null && h.lat < latMin) continue;
                if (latMax != null && h.lat > latMax) continue;
                if (lonMin != null && h.lon < lonMin) continue;
                if (lonMax != null && h.lon > lonMax) continue;
                if (xerrMin != null && h.xerr < xerrMin) continue;
                if (xerrMax != null && h.xerr > xerrMax) continue;
                if (yerrMin != null && h.yerr < yerrMin) continue;
                if (yerrMax != null && h.yerr > yerrMax) continue;
                if (zerrMin != null && h.zerr < zerrMin) continue;
                if (zerrMax != null && h.zerr > zerrMax) continue;
                if (filterByTime) {
                    String t = h.time != null ? h.time.trim() : "";
                    if (!timeMinStr.isEmpty() && (t.isEmpty() || t.compareTo(timeMinStr) < 0)) continue;
                    if (!timeMaxStr.isEmpty() && (t.isEmpty() || t.compareTo(timeMaxStr) > 0)) continue;
                }
                if (clusterMin != null && (h.clusterId == null || h.clusterId < clusterMin)) continue;
                if (clusterMax != null && (h.clusterId == null || h.clusterId > clusterMax)) continue;
                if (filterByMode) {
                    String type = h.type != null ? h.type.trim().toUpperCase() : "";
                    if (!allowedModes.contains(type)) continue;
                }
                filtered.add(h);
            }
            info.setDisplayHypocenters(filtered);
        }
        
        refreshAfterFilter();
    }
    
    private void clearScreeningFilter() {
        ensureScreeningFields();
        rmsMinField.setText(""); rmsMaxField.setText("");
        depthMinField.setText(""); depthMaxField.setText("");
        latMinField.setText(""); latMaxField.setText("");
        lonMinField.setText(""); lonMaxField.setText("");
        xerrMinField.setText(""); xerrMaxField.setText("");
        yerrMinField.setText(""); yerrMaxField.setText("");
        zerrMinField.setText(""); zerrMaxField.setText("");
        if (timeMinField != null) { timeMinField.setText(""); timeMaxField.setText(""); }
        if (clusterMinField != null) { clusterMinField.setText(""); clusterMaxField.setText(""); }
        if (modeGrdCheck != null) modeGrdCheck.setSelected(false);
        if (modeLmoCheck != null) modeLmoCheck.setSelected(false);
        if (modeMcmcCheck != null) modeMcmcCheck.setSelected(false);
        if (modeDeCheck != null) modeDeCheck.setSelected(false);
        if (modeTrdCheck != null) modeTrdCheck.setSelected(false);
        if (modeClsCheck != null) modeClsCheck.setSelected(false);
        if (modeSynCheck != null) modeSynCheck.setSelected(false);
        for (com.treloc.xtreloc.app.gui.model.CatalogInfo info : catalogInfos) {
            info.clearDisplayFilter();
        }
        refreshAfterFilter();
    }
    
    /**
     * Exports the currently displayed (screened) catalog to CSV. Called from Event screening dialog Export button.
     */
    private void exportScreenedCatalog(Component parent) {
        List<Hypocenter> toExport = getCurrentHypocenters();
        if (toExport == null || toExport.isEmpty()) {
            GuiExecutionLog.warning("Viewer: export screened catalog skipped — no events to export");
            JOptionPane.showMessageDialog(parent,
                "No catalog selected or no events to export.\nSelect a catalog and ensure it has events (Apply screening if needed).",
                "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export screened catalog (CSV)");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        chooser.setSelectedFile(new File("catalog_screened.csv"));
        com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(chooser);
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file == null) return;
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getAbsolutePath() + ".csv");
        }
        try {
            CsvExporter.exportHypocenters(toExport, file);
            GuiExecutionLog.info(String.format("Viewer: exported %d events to \"%s\"", toExport.size(), file.getName()));
            JOptionPane.showMessageDialog(parent,
                String.format("Exported %d events to %s", toExport.size(), file.getName()),
                "Export", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            GuiExecutionLog.severe("Viewer: export screened catalog failed — " + ex.getMessage());
            JOptionPane.showMessageDialog(parent,
                "Failed to export: " + ex.getMessage(),
                "Export", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private static java.awt.Color parseHexColor(String hex) {
        if (hex == null || hex.isEmpty()) return java.awt.Color.BLACK;
        try {
            return java.awt.Color.decode(hex.startsWith("#") ? hex : "#" + hex);
        } catch (Exception e) {
            return java.awt.Color.BLACK;
        }
    }
    
    private Double parseDoubleOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntegerOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private void refreshAfterFilter() {
        int selectedRow = catalogListTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < catalogInfos.size()) {
            com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfos.get(selectedRow);
            displayCatalogDataAsync(info);
            int displayCount = info.getHypocenters().size();
            int fullCount = info.getHypocentersFull() == null ? 0 : info.getHypocentersFull().size();
            catalogListModel.setValueAt(displayCount + (displayCount != fullCount ? " (" + fullCount + ")" : ""), selectedRow, 2);
        }
        for (int r = 0; r < catalogInfos.size(); r++) {
            com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfos.get(r);
            int displayCount = info.getHypocenters().size();
            int fullCount = info.getHypocentersFull() == null ? 0 : info.getHypocentersFull().size();
            catalogListModel.setValueAt(displayCount + (displayCount != fullCount ? " (" + fullCount + ")" : ""), r, 2);
        }
        if (mapView != null) {
            mapView.repaintMap();
        }
        refreshCatalogListView();
        for (Runnable r : screeningChangeListeners) {
            r.run();
        }
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
     * Displays catalog in the table. For large catalogs, builds table rows in background to avoid freezing the UI.
     */
    private void displayCatalogDataAsync(com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo) {
        if (catalogInfo == null || catalogInfo.getHypocenters() == null) {
            displayCatalogData(catalogInfo, null);
            return;
        }
        final int size = catalogInfo.getHypocenters().size();
        if (size <= 5000) {
            displayCatalogData(catalogInfo, null);
            return;
        }
        if (displayCatalogWorker != null) {
            displayCatalogWorker.cancel(true);
        }
        final com.treloc.xtreloc.app.gui.model.CatalogInfo target = catalogInfo;
        ViewerStatusBar.startLoading("Preparing table...");
        displayCatalogWorker = new SwingWorker<Object[][], Void>() {
            @Override
            protected Object[][] doInBackground() {
                return buildTableRows(target.getHypocenters());
            }
            @Override
            protected void done() {
                ViewerStatusBar.stopLoading();
                if (isCancelled()) return;
                int selectedRow = catalogListTable.getSelectedRow();
                if (selectedRow < 0 || selectedRow >= catalogInfos.size() || catalogInfos.get(selectedRow) != target) {
                    return;
                }
                try {
                    Object[][] rows = get();
                    displayCatalogData(target, rows);
                } catch (Exception ignored) {
                    displayCatalogData(target, null);
                }
                displayCatalogWorker = null;
            }
        };
        displayCatalogWorker.execute();
    }

    /**
     * Displays the data for the selected catalog.
     * If prebuiltRows is non-null (e.g. after load), uses setDataVector for one-shot update to avoid EDT freeze.
     *
     * @param catalogInfo the catalog information to display
     * @param prebuiltRows optional pre-built table rows from background; if null, rows are built on EDT (can be slow for large catalogs)
     */
    private void displayCatalogData(com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo, Object[][] prebuiltRows) {
        if (catalogInfo == null || catalogInfo.getHypocenters() == null) {
            dataTableModel.setRowCount(0);
            hypocenters = null;
            return;
        }

        List<Hypocenter> hypocenters = catalogInfo.getHypocenters();
        if (prebuiltRows != null) {
            dataTableModel.setDataVector(prebuiltRows, DATA_COLUMN_NAMES);
        } else {
            final int size = hypocenters.size();
            if (size > 5000) {
                displayCatalogDataAsync(catalogInfo);
                return;
            }
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
        }

        this.hypocenters = hypocenters;

        for (CatalogLoadListener listener : catalogLoadListeners) {
            listener.onCatalogLoaded(hypocenters);
        }
        refreshDataTableView();
    }

    /**
     * Forces the data table and its container to revalidate and repaint.
     * Call after updating the table model so the view updates immediately even when
     * the Catalog Data tab was not visible during the update.
     */
    private void refreshDataTableView() {
        if (dataTable != null) {
            dataTable.revalidate();
            dataTable.repaint();
            if (dataTable.getParent() != null) {
                dataTable.getParent().revalidate();
                dataTable.getParent().repaint();
            }
        }
        revalidate();
        repaint();
    }

    /**
     * Forces the catalog list table (top) and its container to revalidate and repaint.
     * Call after adding/removing rows so the view updates immediately.
     */
    private void refreshCatalogListView() {
        if (catalogListTable != null) {
            catalogListTable.revalidate();
            catalogListTable.repaint();
            if (catalogListTable.getParent() != null) {
                catalogListTable.getParent().revalidate();
                catalogListTable.getParent().repaint();
            }
        }
    }

    /**
     * Forces both the catalog list and the data table to refresh their views from the model.
     * Called when this panel becomes visible (tab shown) so the loaded catalog list is always up to date.
     */
    public void refreshAllViews() {
        refreshCatalogListView();
        refreshDataTableView();
        updateErrorEllipseCheckboxForSelection();
    }

    /**
     * Updates the Error ellipse checkbox to reflect the selected catalog's state.
     * Disables the checkbox when no catalog is selected.
     */
    private void updateErrorEllipseCheckboxForSelection() {
        if (errorEllipseToggleButton == null) return;
        int row = catalogListTable.getSelectedRow();
        if (row >= 0 && row < catalogInfos.size()) {
            com.treloc.xtreloc.app.gui.model.CatalogInfo info = catalogInfos.get(row);
            errorEllipseToggleButton.setEnabled(true);
            boolean on = info.isShowErrorEllipse();
            errorEllipseToggleButton.setSelected(on);
            updateErrorEllipseToggleIcon(on);
        } else {
            errorEllipseToggleButton.setEnabled(false);
            errorEllipseToggleButton.setSelected(false);
            updateErrorEllipseToggleIcon(false);
        }
    }

    /** Updates the Error ellipse toggle icon (ON = normal, OFF = greyed). Icon refs are stored in the button's client properties. */
    private void updateErrorEllipseToggleIcon(boolean on) {
        if (errorEllipseToggleButton == null) return;
        ImageIcon onIcon = (ImageIcon) errorEllipseToggleButton.getClientProperty("errorEllipse.iconOn");
        ImageIcon offIcon = (ImageIcon) errorEllipseToggleButton.getClientProperty("errorEllipse.iconOff");
        if (onIcon != null && offIcon != null) {
            errorEllipseToggleButton.setIcon(on ? onIcon : offIcon);
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
        String removedName = info.getName();
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
        
        refreshCatalogListView();
        updateStatusLabel();
        updateErrorEllipseCheckboxForSelection();
        GuiExecutionLog.info("Viewer: removed catalog \"" + removedName + "\"");
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
     * Result of loading a catalog: hypocenters and pre-built table rows to avoid blocking EDT.
     */
    private static class CatalogLoadResult {
        final List<Hypocenter> hypocenters;
        final Object[][] tableRows;

        CatalogLoadResult(List<Hypocenter> hypocenters, Object[][] tableRows) {
            this.hypocenters = hypocenters;
            this.tableRows = tableRows;
        }
    }

    /**
     * Builds table row data from hypocenters (for use off EDT).
     */
    private static Object[][] buildTableRows(List<Hypocenter> hypocenters) {
        if (hypocenters == null || hypocenters.isEmpty()) {
            return new Object[0][];
        }
        Object[][] rows = new Object[hypocenters.size()][DATA_COLUMN_NAMES.length];
        int rowNum = 1;
        for (int i = 0; i < hypocenters.size(); i++) {
            Hypocenter h = hypocenters.get(i);
            rows[i] = new Object[]{
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
        }
        return rows;
    }

    /**
     * Reloads a catalog file from disk and updates the existing catalog entry.
     * If the file is not currently loaded, does nothing. Runs in background.
     *
     * @param file the catalog file to reload (path will be resolved to canonical)
     */
    public void reloadCatalogFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return;
        }
        com.treloc.xtreloc.app.gui.model.CatalogInfo target = null;
        try {
            String canonical = file.getCanonicalPath();
            for (java.util.Map.Entry<com.treloc.xtreloc.app.gui.model.CatalogInfo, File> e : catalogFiles.entrySet()) {
                if (e.getValue() != null && canonical.equals(e.getValue().getCanonicalPath())) {
                    target = e.getKey();
                    break;
                }
            }
        } catch (java.io.IOException ex) {
            logger.warning("Could not resolve path for reload: " + ex.getMessage());
            return;
        }
        if (target == null) {
            return;
        }
        final com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo = target;
        final File f = file;
        ViewerStatusBar.startLoading("Reloading catalog...");
        new SwingWorker<CatalogLoadResult, Void>() {
            @Override
            protected CatalogLoadResult doInBackground() throws Exception {
                List<Hypocenter> hypocenters = CatalogLoader.load(f);
                Object[][] tableRows = buildTableRows(hypocenters);
                return new CatalogLoadResult(hypocenters, tableRows);
            }
            @Override
            protected void done() {
                ViewerStatusBar.stopLoading();
                try {
                    CatalogLoadResult result = get();
                    catalogInfo.setHypocenters(result.hypocenters);
                    int idx = catalogInfos.indexOf(catalogInfo);
                    if (idx >= 0 && idx < catalogListModel.getRowCount()) {
                        int count = result.hypocenters.size();
                        catalogListModel.setValueAt(String.valueOf(count), idx, 2);
                    }
                    int selectedRow = catalogListTable.getSelectedRow();
                    if (selectedRow >= 0 && selectedRow < catalogInfos.size() && catalogInfos.get(selectedRow) == catalogInfo) {
                        displayCatalogData(catalogInfo, result.tableRows);
                    }
                    if (mapView != null) {
                        mapView.repaintMap();
                    }
                    updateStatusLabel();
                    GuiExecutionLog.info(String.format("Viewer: reloaded catalog \"%s\" (%d events)",
                        f.getName(), result.hypocenters.size()));
                } catch (java.util.concurrent.CancellationException e) {
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Catalog reload error: " + f.getAbsolutePath() + " - " + e.getMessage(), e);
                    GuiExecutionLog.severe("Viewer: catalog reload failed — " + f.getName() + ": " + e.getMessage());
                    JOptionPane.showMessageDialog(CatalogTablePanel.this,
                        "Failed to reload catalog:\n  " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Loads a catalog file and adds it to the list of catalogs.
     * Parsing and table row building run in background to avoid freezing the UI.
     *
     * @param file the catalog file to load
     */
    public void loadCatalogFile(File file) {
        if (catalogFiles.containsValue(file)) {
            GuiExecutionLog.warning("Viewer: catalog already loaded — " + file.getName());
            JOptionPane.showMessageDialog(this,
                "This catalog is already loaded: " + file.getName(),
                "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final File f = file;
        ViewerStatusBar.startLoading("Loading catalog...");
        new SwingWorker<CatalogLoadResult, Void>() {
            @Override
            protected CatalogLoadResult doInBackground() throws Exception {
                List<Hypocenter> hypocenters = CatalogLoader.load(f);
                Object[][] tableRows = buildTableRows(hypocenters);
                return new CatalogLoadResult(hypocenters, tableRows);
            }
            @Override
            protected void done() {
                ViewerStatusBar.stopLoading();
                try {
                    CatalogLoadResult result = get();
                    addLoadedCatalogToUI(f, result);
                    updateStatusLabel();
                } catch (java.util.concurrent.CancellationException e) {
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Catalog load error: " + f.getAbsolutePath() + " - " + e.getMessage(), e);
                    GuiExecutionLog.severe("Viewer: catalog load failed — " + f.getName() + ": " + e.getMessage());
                    StringBuilder errorMsg = new StringBuilder("Failed to load catalog file:\n");
                    errorMsg.append("  File: ").append(f.getAbsolutePath()).append("\n");
                    errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
                    JOptionPane.showMessageDialog(CatalogTablePanel.this,
                        errorMsg.toString(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Adds already-loaded catalog data to the list and map (called on EDT after load).
     * Uses pre-built table rows so the table is updated in one setDataVector call.
     */
    private void addLoadedCatalogToUI(File file, CatalogLoadResult result) {
        List<Hypocenter> hypocenters = result.hypocenters;
        String name = file.getName();
        if (name.endsWith(".csv")) {
            name = name.substring(0, name.length() - 4);
        }
        java.awt.Color[] defaultColors = {
            java.awt.Color.BLUE, java.awt.Color.RED, java.awt.Color.GREEN,
            java.awt.Color.ORANGE, java.awt.Color.MAGENTA, java.awt.Color.CYAN,
            java.awt.Color.YELLOW, java.awt.Color.PINK
        };
        java.awt.Color color;
        if (catalogInfos.isEmpty()) {
            String hex = com.treloc.xtreloc.app.gui.util.AppSettingsCache.snapshot().getDefaultSymbolColor();
            color = parseHexColor(hex);
        } else {
            color = defaultColors[catalogInfos.size() % defaultColors.length];
        }
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
            displayCatalogData(catalogInfo, result.tableRows);
        }
        if (removeButton != null) {
            removeButton.setEnabled(true);
        }
        updateStatusLabel();
        GuiExecutionLog.info(String.format("Viewer: loaded catalog \"%s\" (%d events)", file.getName(), hypocenters.size()));
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
     * Adds hypocenters as a new catalog to the list (e.g. after Solver execution).
     * Updates the list table, map, and selects the new row. Call on EDT.
     *
     * @param hypocenters list of hypocenters (not null, may be empty)
     * @param catalogName display name for the catalog (e.g. output filename "catalog_lmo.csv" or "Solver result (LMO)")
     * @param file optional file (e.g. exported catalog path); may be null
     */
    public void addCatalogFromHypocenters(List<Hypocenter> hypocenters, String catalogName, File file) {
        if (hypocenters == null) return;
        String name = catalogName != null && !catalogName.isEmpty() ? catalogName : "Solver result";
        java.awt.Color[] defaultColors = {
            java.awt.Color.BLUE, java.awt.Color.RED, java.awt.Color.GREEN,
            java.awt.Color.ORANGE, java.awt.Color.MAGENTA, java.awt.Color.CYAN,
            java.awt.Color.YELLOW, java.awt.Color.PINK
        };
        java.awt.Color color;
        if (catalogInfos.isEmpty()) {
            try {
                String hex = com.treloc.xtreloc.app.gui.util.AppSettingsCache.snapshot().getDefaultSymbolColor();
                color = parseHexColor(hex);
            } catch (Exception e) {
                color = defaultColors[0];
            }
        } else {
            color = defaultColors[catalogInfos.size() % defaultColors.length];
        }
        com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType =
            com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType.values()[
                catalogInfos.size() % com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType.values().length];
        com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo =
            new com.treloc.xtreloc.app.gui.model.CatalogInfo(name, color, symbolType,
                new java.util.ArrayList<>(hypocenters), file);
        catalogInfos.add(catalogInfo);
        if (file != null) {
            catalogFiles.put(catalogInfo, file);
        }
        Object[] row = {
            true,
            name,
            String.valueOf(hypocenters.size()),
            file != null ? file.getName() : ""
        };
        catalogListModel.addRow(row);
        if (mapView != null) {
            mapView.addCatalog(catalogInfo);
        }
        int newIndex = catalogInfos.size() - 1;
        catalogListTable.setRowSelectionInterval(newIndex, newIndex);
        Object[][] tableRows = buildTableRows(hypocenters);
        displayCatalogData(catalogInfo, tableRows);
        if (removeButton != null) {
            removeButton.setEnabled(true);
        }
        updateStatusLabel();
        refreshCatalogListView();
        for (CatalogLoadListener listener : catalogLoadListeners) {
            listener.onCatalogLoaded(hypocenters);
        }
        GuiExecutionLog.info(String.format("Viewer: added catalog \"%s\" (%d events)%s",
            name, hypocenters.size(), file != null ? " — " + file.getName() : ""));
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
            colorColumnIndex = columnIndex;
            final String colName = columnName;
            final double[] valuesSnapshot = values;
            final List<Hypocenter> hyposSnapshot = currentHypocenters;
            final int nCatalogs = catalogInfos.size();
            final int selectedCatalogRow = catalogListTable.getSelectedRow();
            final com.treloc.xtreloc.app.gui.model.CatalogInfo catalogSnapshot =
                (nCatalogs > 1 && selectedCatalogRow >= 0 && selectedCatalogRow < catalogInfos.size())
                    ? catalogInfos.get(selectedCatalogRow) : null;
            ViewerStatusBar.startLoading("Applying colors...");
            SwingUtilities.invokeLater(() -> {
                try {
                    if (nCatalogs > 1) {
                        if (catalogSnapshot != null) {
                            mapView.applyColorMapToCatalog(catalogSnapshot, colName, valuesSnapshot);
                        }
                    } else {
                        try {
                            mapView.showHypocenters(hyposSnapshot, colName, valuesSnapshot);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Failed to apply coloring: " + ex.getMessage(), ex);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to apply coloring: " + e.getMessage(), e);
                } finally {
                    ViewerStatusBar.stopLoading();
                }
                dataTable.repaint();
                fireViewerChartChanged();
            });
        } else {
            colorColumnIndex = -1;
            final List<Hypocenter> hyposSnapshot = currentHypocenters;
            final int nCatalogs = catalogInfos.size();
            ViewerStatusBar.startLoading("Updating map...");
            SwingUtilities.invokeLater(() -> {
                try {
                    if (nCatalogs > 1) {
                        mapView.clearColorMap();
                        mapView.updateMultipleCatalogsDisplay();
                    } else {
                        try {
                            mapView.showHypocenters(hyposSnapshot);
                        } catch (Exception ex) {
                            logger.log(Level.WARNING, "Failed to update display: " + ex.getMessage(), ex);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to update display: " + e.getMessage(), e);
                } finally {
                    ViewerStatusBar.stopLoading();
                }
                dataTable.repaint();
                fireViewerChartChanged();
            });
        }
    }

    /**
     * Renders table cells with a diagonal hatch on the column used for map color coding.
     */
    private class ColorColumnHatchRenderer extends DefaultTableCellRenderer {
        private static final int HATCH_SPACING = 6;
        private static final int HATCH_SPACING_ALT = 5;
        private static final Color HATCH_COLOR_MAP = new Color(255, 220, 0, 100);
        private static final Color HATCH_COLOR_SCATTER_X = new Color(160, 100, 255, 115);
        private static final Color HATCH_COLOR_SCATTER_Y = new Color(0, 185, 220, 115);

        /** Model column for the cell last prepared; avoids convertColumnIndexToModel during paint. */
        private int lastModelColumn = -1;
        private JTable tableRef;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            tableRef = table;
            try {
                lastModelColumn = table.convertColumnIndexToModel(column);
            } catch (IllegalArgumentException ex) {
                lastModelColumn = -1;
            }
            boolean rowSelected = (table.getSelectedRow() == row);
            Component c = super.getTableCellRendererComponent(table, value, isSelected || rowSelected, hasFocus, row, column);
            setOpaque(true);
            return c;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (tableRef == null || lastModelColumn < 0) {
                return;
            }
            int modelCol = lastModelColumn;
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                if (colorColumnIndex >= 0 && modelCol == colorColumnIndex) {
                    drawForwardHatch(g2, w, h, HATCH_COLOR_MAP, HATCH_SPACING, 0);
                }
                if (scatterXColumnModelIndex >= 0 && modelCol == scatterXColumnModelIndex) {
                    drawForwardHatch(g2, w, h, HATCH_COLOR_SCATTER_X, HATCH_SPACING_ALT, 2);
                }
                if (scatterYColumnModelIndex >= 0 && modelCol == scatterYColumnModelIndex) {
                    drawBackwardHatch(g2, w, h, HATCH_COLOR_SCATTER_Y, HATCH_SPACING_ALT, 1);
                }
            } finally {
                g2.dispose();
            }
        }

        /** Same diagonal sense as the original map hatch overlay. */
        private void drawForwardHatch(Graphics2D g2, int w, int h, Color color, int spacing, int phase) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1f));
            for (int i = -h + phase; i < w + h; i += spacing) {
                g2.drawLine(i, -h, i + h + w, h + w);
            }
        }

        /** Opposite diagonal for scatter Y (distinguishable when overlapping scatter X). */
        private void drawBackwardHatch(Graphics2D g2, int w, int h, Color color, int spacing, int phase) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1f));
            for (int i = -h + phase; i < w + 2 * h; i += spacing) {
                g2.drawLine(i + w, -h, i - h, h + w);
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
            logger.log(Level.SEVERE, "Failed to load travel time data: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(this,
                "Failed to load travel time data: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
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
            logger.log(Level.WARNING, "Could not load station codes from " + stationFile + ": " + e.getMessage(), e);
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
     * @return the current catalog file, or null if no file is loaded
     */
    private File getCurrentCatalogFile() {
        return currentCatalogFile;
    }
}

