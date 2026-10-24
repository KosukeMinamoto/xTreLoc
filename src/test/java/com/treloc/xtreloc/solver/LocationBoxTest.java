package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.io.AppConfig;
import org.junit.BeforeClass;
import org.junit.Test;

public class LocationBoxTest {

    private static HypoUtils boxed;

    @BeforeClass
    public static void loadModel() throws Exception {
        AppConfig cfg = new AppConfig();
        cfg.taupFile = "prem.nd";
        cfg.hypBottom = 20.0;
        cfg.locLonMin = 129.0;
        cfg.locLonMax = 129.5;
        cfg.locLatMin = 36.0;
        cfg.locLatMax = 36.5;
        cfg.locMinDepthKm = 2.0;
        boxed = new HypoUtils(cfg);
    }

    @Test
    public void sourceMustStayInsideTheModelRange() {
        assertTrue(boxed.isSourceInRange(36.2, 129.2, 5.0));
        assertFalse(boxed.isSourceInRange(36.2, 128.9, 5.0));
        assertFalse(boxed.isSourceInRange(36.2, 129.6, 5.0));
        assertFalse(boxed.isSourceInRange(35.9, 129.2, 5.0));
        assertFalse(boxed.isSourceInRange(36.2, 129.2, 1.0));
    }

    @Test
    public void searchBoxIsClippedToTheModelRange() {
        HypoUtils.StationSearchBox wide = new HypoUtils.StationSearchBox(
            36.25, 129.25, 1.0, 1.0, 1.0, 10.0, 3, false);
        HypoUtils.StationSearchBox clipped = boxed.clipSearchBox(wide);
        assertTrue(clipped.centerLat - clipped.latRange >= 36.0 - 1e-12);
        assertTrue(clipped.centerLat + clipped.latRange <= 36.5 + 1e-12);
        assertTrue(clipped.centerLon - clipped.lonRange >= 129.0 - 1e-12);
        assertTrue(clipped.centerLon + clipped.lonRange <= 129.5 + 1e-12);
        assertEquals(2.0, clipped.centerDep, 1e-12);
    }
}
