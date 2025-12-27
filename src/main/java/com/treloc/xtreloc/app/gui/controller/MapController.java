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
     * Shapefileを読み込んでレイヤータイトルを返す
     * @param f Shapefile
     * @return レイヤータイトル
     * @throws Exception 読み込みエラー
     */
    public String loadShapefile(File f) throws Exception {
        return view.addShapefile(f);
    }
}
