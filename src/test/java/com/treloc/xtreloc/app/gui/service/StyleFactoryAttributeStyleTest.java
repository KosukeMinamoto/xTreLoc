package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.style.Style;
import org.geotools.data.DataUtilities;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.MapContent;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.renderer.lite.StreamingRenderer;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType;

/** The attribute-driven symbol style must render each feature with its own color and honour opacity. */
public class StyleFactoryAttributeStyleTest {

    @Test
    public void rendersPerFeatureColorAndOpacity() throws Exception {
        System.setProperty("java.awt.headless", "true");
        SimpleFeatureType type = DataUtilities.createType("HypoColor",
            "geom:Point,time:String,depth:Double,color:String,opacity:Double");
        GeometryFactory gf = new GeometryFactory();
        DefaultFeatureCollection fc = new DefaultFeatureCollection("t", type);
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder(type);
        double[][] pts = {{1, 5}, {5, 5}, {9, 5}};
        String[] cols = {"#FF0000", "#00FF00", "#0000FF"};
        double[] ops = {1.0, 1.0, 0.0};
        for (int i = 0; i < 3; i++) {
            fb.add(gf.createPoint(new Coordinate(pts[i][0], pts[i][1])));
            fb.add("t");
            fb.add(1.0);
            fb.add(cols[i]);
            fb.add(ops[i]);
            fc.add(fb.buildFeature(null));
        }
        Style style = StyleFactory.createSymbolStyleByAttribute(SymbolType.CIRCLE, 20);
        MapContent map = new MapContent();
        map.addLayer(new FeatureLayer(fc, style));
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        StreamingRenderer r = new StreamingRenderer();
        r.setMapContent(map);
        r.paint(g, new Rectangle(0, 0, 100, 100),
            new ReferencedEnvelope(0, 10, 0, 10, DefaultGeographicCRS.WGS84));
        g.dispose();
        map.dispose();

        assertEquals(Color.RED.getRGB(), img.getRGB(10, 50));
        assertEquals(Color.GREEN.getRGB(), img.getRGB(50, 50));
        // opacity 0 -> nothing drawn (stays white)
        assertEquals(Color.WHITE.getRGB(), img.getRGB(90, 50));
        assertTrue(true);
    }
}
