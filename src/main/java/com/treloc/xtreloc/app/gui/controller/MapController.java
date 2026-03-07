package com.treloc.xtreloc.app.gui.controller;

import com.treloc.xtreloc.app.gui.view.MapView;
import com.treloc.xtreloc.app.gui.service.CatalogLoader;

import java.io.File;

public class MapController {

    private final MapView view;

    public MapController(MapView view) {
        this.view = view;
    }

    public void loadCatalog(File f) throws Exception {
        view.showHypocenters(CatalogLoader.load(f));
    }

    /**
     * Loads a shapefile using an already-opened FeatureSource (avoids blocking the EDT).
     * @param f shapefile
     * @param featureSource pre-opened FeatureSource
     * @return layer title
     */
    public String loadShapefile(File f, org.geotools.api.data.FeatureSource featureSource) throws Exception {
        return view.addShapefile(f, featureSource);
    }
}
