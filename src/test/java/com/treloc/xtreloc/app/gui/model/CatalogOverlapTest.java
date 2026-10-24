package com.treloc.xtreloc.app.gui.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class CatalogOverlapTest {

    @Test
    public void uniqueNameAppendsIndex() {
        assertEquals("woo", CatalogOverlap.uniqueName("woo", Collections.emptyList()));
        assertEquals("woo (2)", CatalogOverlap.uniqueName("woo", List.of("woo")));
        assertEquals("woo (3)", CatalogOverlap.uniqueName("woo", List.of("woo", "woo (2)")));
    }

    @Test
    public void sameFileUsesCanonicalPath() {
        File a = new File("catalog.csv");
        File b = new File("./catalog.csv");
        assertTrue(CatalogOverlap.sameFile(a, b));
        assertFalse(CatalogOverlap.sameFile(a, new File("other.csv")));
        assertFalse(CatalogOverlap.sameFile(a, null));
    }

    @Test
    public void overlapRatioUsesTimeAndRoundedLocation() {
        Hypocenter a = hypo("20171115.143222", 36.11, 129.37);
        Hypocenter b = hypo("20171115.143222", 36.11004, 129.37002);
        Hypocenter c = hypo("20171115.150000", 36.20, 129.40);
        assertEquals(1.0, CatalogOverlap.overlapRatio(List.of(a), List.of(b)), 1e-9);
        assertEquals(0.0, CatalogOverlap.overlapRatio(List.of(a), List.of(c)), 1e-9);
        assertEquals(0.5, CatalogOverlap.overlapRatio(List.of(a), Arrays.asList(a, c)), 1e-9);
    }

    private static Hypocenter hypo(String time, double lat, double lon) {
        return new Hypocenter(time, lat, lon, 5);
    }
}
