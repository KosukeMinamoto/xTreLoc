package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.controller.MapController;

import javax.swing.*;
import java.awt.*;

/**
 * 観測点データ、Catalogデータ、Shapefileデータをタブで切り替えて表示するパネル
 */
public class DataViewPanel extends JPanel {
    private StationTablePanel stationPanel;
    private CatalogTablePanel catalogPanel;
    private ShapefileTablePanel shapefilePanel;
    private HypocenterLocationPanel locationPanel;
    private JTabbedPane tabbedPane;

    public DataViewPanel(MapView mapView, MapController mapController) {
        setLayout(new BorderLayout());
        
        // タブパネルの作成
        tabbedPane = new JTabbedPane();
        
        // 観測点データタブ
        stationPanel = new StationTablePanel(mapView);
        tabbedPane.addTab("観測点データ", stationPanel);
        
        // Catalogデータタブ
        catalogPanel = new CatalogTablePanel(mapView);
        tabbedPane.addTab("Catalogデータ", catalogPanel);
        
        // Shapefileデータタブ
        shapefilePanel = new ShapefileTablePanel(mapView, mapController);
        tabbedPane.addTab("Shapefile", shapefilePanel);
        
        // 震源決定パネル（外部で使用されるため保持）
        locationPanel = new HypocenterLocationPanel(mapView);
        
        // このパネルはタブパネルのみを表示（震源決定パネルは外部で配置）
        add(tabbedPane, BorderLayout.CENTER);
    }

    /**
     * 観測点データパネルを取得
     */
    public StationTablePanel getStationPanel() {
        return stationPanel;
    }

    /**
     * Catalogデータパネルを取得
     */
    public CatalogTablePanel getCatalogPanel() {
        return catalogPanel;
    }
    
    /**
     * 震源決定パネルを取得
     */
    public HypocenterLocationPanel getLocationPanel() {
        return locationPanel;
    }
    
    /**
     * タブパネルを取得
     */
    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }
    
    /**
     * Shapefileパネルを取得
     */
    public ShapefileTablePanel getShapefilePanel() {
        return shapefilePanel;
    }
}

