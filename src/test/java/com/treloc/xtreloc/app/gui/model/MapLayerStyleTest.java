package com.treloc.xtreloc.app.gui.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.Color;
import org.junit.Test;

public class MapLayerStyleTest {

    @Test
    public void scatterOpacityAndCopy() {
        MapLayerStyle s = MapLayerStyle.scatter(Color.RED, CatalogInfo.SymbolType.SQUARE, 12, 0.4f);
        assertEquals(102, s.alpha());
        assertEquals(Color.RED, s.getColor());
        MapLayerStyle copy = s.copy();
        copy.setOpacity(1f);
        assertEquals(0.4f, s.getOpacity(), 1e-5);
        assertEquals(1f, copy.getOpacity(), 1e-5);
    }

    @Test
    public void lineDashArrays() {
        assertNull(MapLayerStyle.line(Color.BLUE, MapLayerStyle.LineStyle.SOLID, 2, 1).dashArray());
        assertNotNull(MapLayerStyle.line(Color.BLUE, MapLayerStyle.LineStyle.DASH, 2, 1).dashArray());
        assertNotNull(MapLayerStyle.line(Color.BLUE, MapLayerStyle.LineStyle.DOT, 2, 1).stroke());
    }
}
