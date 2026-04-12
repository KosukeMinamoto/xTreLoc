package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.controller.MapController;
import com.treloc.xtreloc.app.gui.util.AppPanelStyle;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for displaying various data views in tabs.
 * 
 * @author K.Minamoto
 */
public class DataViewPanel extends JPanel {
    private StationTablePanel stationPanel;
    private CatalogTablePanel catalogPanel;
    private ShapefileTablePanel shapefilePanel;
    private HypocenterLocationPanel locationPanel;
    private JTabbedPane tabbedPane;

    public DataViewPanel(MapView mapView, MapController mapController) {
        setLayout(new BorderLayout());
        AppPanelStyle.setPanelBackground(this);
        
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(AppPanelStyle.getPanelBg());
        tabbedPane.setForeground(AppPanelStyle.getContentTextColor());
        
        stationPanel = new StationTablePanel(mapView);
        tabbedPane.addTab("Station Data", stationPanel);
        
        catalogPanel = new CatalogTablePanel(mapView);
        tabbedPane.addTab("Catalog Data", catalogPanel);
        
        shapefilePanel = new ShapefileTablePanel(mapView, mapController);
        tabbedPane.addTab("Shapefile", shapefilePanel);
        
        locationPanel = new HypocenterLocationPanel(mapView);
        
        add(tabbedPane, BorderLayout.CENTER);
        AppPanelStyle.applyThemeTo(this);
    }

    /**
     * Gets the station data panel.
     * 
     * @return the station data panel
     */
    public StationTablePanel getStationPanel() {
        return stationPanel;
    }

    /**
     * Gets the catalog data panel.
     * 
     * @return the catalog data panel
     */
    public CatalogTablePanel getCatalogPanel() {
        return catalogPanel;
    }
    
    /**
     * Gets the hypocenter location panel.
     * 
     * @return the hypocenter location panel
     */
    public HypocenterLocationPanel getLocationPanel() {
        return locationPanel;
    }
    
    /**
     * Gets the tabbed pane.
     * 
     * @return the tabbed pane
     */
    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }
    
    /**
     * Gets the shapefile panel.
     * 
     * @return the shapefile panel
     */
    public ShapefileTablePanel getShapefilePanel() {
        return shapefilePanel;
    }
}

