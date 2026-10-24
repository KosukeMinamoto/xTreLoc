package com.treloc.xtreloc.app.gui.map2d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.app.gui.util.EventSelectionModel;
import java.awt.Color;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class Map2DCanvasSelectionTest {

    private Map2DCanvas canvas;
    private EventSelectionModel model;
    private CatalogInfo catalog;
    private Hypocenter h1;
    private Hypocenter h2;
    private Hypocenter h3;

    @Before
    public void setUp() {
        h1 = new Hypocenter("2017-11-15T05:00:01", 36.10, 129.30, 5.0);
        h2 = new Hypocenter("2017-11-15T05:00:02", 36.20, 129.40, 6.0);
        h3 = new Hypocenter("2017-11-15T05:00:03", 36.30, 129.50, 7.0);
        catalog = new CatalogInfo("Cat", Color.RED, CatalogInfo.SymbolType.CIRCLE, List.of(h1, h2, h3), null);
        canvas = new Map2DCanvas();
        canvas.renderer().collapse = 2;
        canvas.renderer().lonMin = 129.0;
        canvas.renderer().lonMax = 130.0;
        canvas.renderer().latMin = 36.0;
        canvas.renderer().latMax = 36.5;
        canvas.renderer().resetView();
        canvas.setBounds(0, 0, 500, 400);
        canvas.setCatalogSource(() -> List.of(catalog));
        model = new EventSelectionModel();
        canvas.setSelectionModel(model);
    }

    private double[] px(Hypocenter h) {
        return canvas.renderer().toPixel(500, 400, h.lon, h.lat);
    }

    @Test
    public void nearestFindsEventUnderPointer() {
        double[] p = px(h2);
        Map2DCanvas.Hit hit = canvas.pickNearest(p[0] + 2, p[1] - 2);
        assertNotNull(hit);
        assertEquals(h2, hit.hypocenter);
        assertNull(canvas.pickNearest(p[0] + 60, p[1] + 60));
    }

    @Test
    public void hiddenCatalogIsNotPickable() {
        catalog.setVisible(false);
        double[] p = px(h1);
        assertNull(canvas.pickNearest(p[0], p[1]));
        assertTrue(canvas.pickInRect(0, 0, 500, 400).isEmpty());
    }

    @Test
    public void rectangleReturnsContainedEventsWithAnyCornerOrder() {
        double[] a = px(h1);
        double[] b = px(h2);
        List<Map2DCanvas.Hit> hits = canvas.pickInRect(b[0] + 5, b[1] - 5, a[0] - 5, a[1] + 5);
        assertEquals(2, hits.size());
    }

    @Test
    public void clickSelectsToggleExtendsAndEmptyClears() {
        double[] a = px(h1);
        double[] b = px(h2);
        canvas.applyClickSelection(a[0], a[1], false);
        assertEquals(EventKey.of(catalog, h1), model.focus());
        canvas.applyClickSelection(b[0], b[1], true);
        assertEquals(2, model.size());
        assertEquals(EventKey.of(catalog, h2), model.focus());
        canvas.applyClickSelection(b[0], b[1], true);
        assertEquals(1, model.size());
        canvas.applyClickSelection(1, 1, true);
        assertEquals(1, model.size());
        canvas.applyClickSelection(1, 1, false);
        assertTrue(model.isEmpty());
    }

    @Test
    public void rectangleSelectionReplacesOrExtends() {
        double[] a = px(h1);
        double[] c = px(h3);
        canvas.applyClickSelection(a[0], a[1], false);
        canvas.applyRectangleSelection(c[0] - 4, c[1] - 4, c[0] + 4, c[1] + 4, true);
        assertEquals(2, model.size());
        assertEquals(EventKey.of(catalog, h1), model.focus());
        canvas.applyRectangleSelection(c[0] - 4, c[1] - 4, c[0] + 4, c[1] + 4, false);
        assertEquals(1, model.size());
        assertEquals(EventKey.of(catalog, h3), model.focus());
        canvas.applyRectangleSelection(0, 0, 3, 3, false);
        assertTrue(model.isEmpty());
    }

    @Test
    public void doubleClickOnAnEventActivatesItAndOnEmptySpaceOnlyResetsTheView() {
        java.util.List<EventKey> activated = new java.util.ArrayList<>();
        canvas.setEventActivatedListener(activated::add);
        double[] p = px(h2);
        canvas.dispatchEvent(click(p[0], p[1], 2));
        assertEquals(java.util.List.of(EventKey.of(catalog, h2)), activated);
        canvas.dispatchEvent(click(1, 1, 2));
        assertEquals(1, activated.size());
    }

    @Test
    public void workingVectorsAreDrawnOnlyWhileTheWorkingCatalogIsVisible() {
        com.treloc.xtreloc.app.gui.util.WorkingLayer layer = new com.treloc.xtreloc.app.gui.util.WorkingLayer();
        Hypocenter moved = new Hypocenter(h1.time, h1.lat + 0.1, h1.lon + 0.2, h1.depth);
        layer.put(EventKey.of(catalog, h1), h1, moved, "LMO", 6);
        CatalogInfo working = new CatalogInfo(com.treloc.xtreloc.app.gui.util.WorkingLayer.CATALOG_NAME,
            Color.BLUE, CatalogInfo.SymbolType.SQUARE, List.of(moved), null);
        canvas.setCatalogSource(() -> List.of(catalog, working));
        canvas.setWorkingLayer(layer);
        canvas.showHypocenters = true;
        java.awt.image.BufferedImage with = canvas.snapshot();
        working.setVisible(false);
        java.awt.image.BufferedImage without = canvas.snapshot();
        double[] a = px(h1);
        double[] b = px(moved);
        int mx = (int) Math.round((a[0] + b[0]) / 2);
        int my = (int) Math.round((a[1] + b[1]) / 2);
        boolean differs = false;
        for (int dx = -2; dx <= 2 && !differs; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (with.getRGB(mx + dx, my + dy) != without.getRGB(mx + dx, my + dy)) {
                    differs = true;
                    break;
                }
            }
        }
        assertTrue("arrow should be visible between old and new position", differs);
    }

    private java.awt.event.MouseEvent click(double x, double y, int count) {
        return new java.awt.event.MouseEvent(canvas, java.awt.event.MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(), 0, (int) Math.round(x), (int) Math.round(y), count, false,
            java.awt.event.MouseEvent.BUTTON1);
    }
}
