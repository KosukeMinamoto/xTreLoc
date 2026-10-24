package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.DataUtilities;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

public class ShapefileOverlayExtractorTest {

    @Test
    public void clipSegment_insideOutsideAndCrossing() {
        assertArrayEquals(new double[] {1, 1, 2, 2},
            ShapefileOverlayExtractor.clipSegment(1, 1, 2, 2, 0, 10, 0, 10), 1e-12);
        assertNull(ShapefileOverlayExtractor.clipSegment(-5, 20, -1, 30, 0, 10, 0, 10));
        double[] c = ShapefileOverlayExtractor.clipSegment(-5, 5, 15, 5, 0, 10, 0, 10);
        assertNotNull(c);
        assertArrayEquals(new double[] {0, 5, 10, 5}, c, 1e-12);
        double[] d = ShapefileOverlayExtractor.clipSegment(-5, -5, 5, 5, 0, 10, 0, 10);
        assertArrayEquals(new double[] {0, 0, 5, 5}, d, 1e-12);
    }

    @Test
    public void extract_linesAndPolygonsAreClippedToTheRegion() throws Exception {
        SimpleFeatureType type = DataUtilities.createType("T", "geom:Geometry");
        GeometryFactory gf = new GeometryFactory();
        DefaultFeatureCollection fc = new DefaultFeatureCollection("t", type);
        SimpleFeatureBuilder fb = new SimpleFeatureBuilder(type);
        // line crossing the region
        fb.add(gf.createLineString(new Coordinate[] {new Coordinate(-5, 5), new Coordinate(15, 5)}));
        fc.add(fb.buildFeature("a"));
        // polygon fully outside
        fb.add(gf.createPolygon(new Coordinate[] {new Coordinate(20, 20), new Coordinate(30, 20),
            new Coordinate(30, 30), new Coordinate(20, 20)}));
        fc.add(fb.buildFeature("b"));
        // point inside
        fb.add(gf.createPoint(new Coordinate(3, 4)));
        fc.add(fb.buildFeature("c"));

        List<double[]> lines = ShapefileOverlayExtractor.extract(DataUtilities.source(fc), 0, 10, 0, 10);
        assertEquals(2, lines.size());
        double[] line = lines.get(0);
        assertArrayEquals(new double[] {0, 5, 10, 5}, line, 1e-12);
        double[] pt = lines.get(1);
        assertEquals(2, pt.length);
        assertEquals(3.0, pt[0], 0);
        assertEquals(4.0, pt[1], 0);
        assertTrue(ShapefileOverlayExtractor.extract(null, 0, 1, 0, 1).isEmpty());
    }
}
