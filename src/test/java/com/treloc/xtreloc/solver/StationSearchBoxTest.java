package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StationSearchBoxTest {

    @Test
    public void centroidAndSampleStdFromUsedStations() {
        double[][] stn = {
            { 36.00, 129.00, 0.0 },
            { 36.10, 129.00, 0.0 },
            { 36.00, 129.10, 0.0 }
        };
        int[] used = { 0, 1, 2 };
        HypoUtils.StationSearchBox box = HypoUtils.searchBoxFromUsedStations(
            stn, used, 36.2, 129.4, 0.0, 2.0, 5.0, 0.0, 100.0);

        assertEquals(3, box.nStations);
        assertEquals((36.00 + 36.10 + 36.00) / 3.0, box.centerLat, 1e-12);
        assertEquals((129.00 + 129.00 + 129.10) / 3.0, box.centerLon, 1e-12);
        assertFalse(box.usedHeaderDepth);
        assertEquals(5.0, box.centerDep, 1e-12);

        double meanLat = box.centerLat;
        double meanLon = box.centerLon;
        double ssLat = 0.0;
        double ssLon = 0.0;
        for (double[] row : stn) {
            ssLat += (row[0] - meanLat) * (row[0] - meanLat);
            ssLon += (row[1] - meanLon) * (row[1] - meanLon);
        }
        double stdLat = Math.sqrt(ssLat / 2.0);
        double stdLon = Math.sqrt(ssLon / 2.0);
        assertEquals(2.0 * stdLat, box.latRange, 1e-12);
        assertEquals(2.0 * stdLon, box.lonRange, 1e-12);

        double hStdKm = HypoUtils.getDeg2Km() * Math.hypot(stdLat, stdLon * Math.cos(Math.toRadians(meanLat)));
        assertEquals(2.0 * hStdKm, box.depRange, 1e-12);
    }

    @Test
    public void catalogLikeHeaderDepthIsKept() {
        double[][] stn = { { 36.0, 129.0, 0.0 }, { 36.1, 129.1, 0.0 } };
        HypoUtils.StationSearchBox box = HypoUtils.searchBoxFromUsedStations(
            stn, new int[] { 0, 1 }, 36.2, 129.4, 4.2, 2.0, 5.0, 0.0, 100.0);
        assertTrue(box.usedHeaderDepth);
        assertEquals(4.2, box.centerDep, 1e-12);
    }

    @Test
    public void singleStationUsesMinimumHorizontalRange() {
        double[][] stn = { { 36.1, 129.3, 0.0 } };
        HypoUtils.StationSearchBox box = HypoUtils.searchBoxFromUsedStations(
            stn, new int[] { 0 }, 36.1, 129.3, 0.0, 2.0, 5.0, 0.0, 100.0);
        assertEquals(1, box.nStations);
        assertEquals(36.1, box.centerLat, 1e-12);
        assertEquals(129.3, box.centerLon, 1e-12);
        assertEquals(HypoUtils.MIN_RANGE_DEG, box.latRange, 1e-12);
        assertEquals(HypoUtils.MIN_RANGE_DEG, box.lonRange, 1e-12);
        assertEquals(HypoUtils.MIN_DEP_RANGE_KM, box.depRange, 1e-12);
    }

    @Test
    public void emptyUsedIdxFallsBackToLegacyBoxAroundHeader() {
        HypoUtils.StationSearchBox box = HypoUtils.searchBoxFromUsedStations(
            new double[0][0], new int[0], 36.2, 129.4, 4.0, 2.0, 5.0, 0.0, 100.0);
        assertEquals(0, box.nStations);
        assertEquals(36.2, box.centerLat, 1e-12);
        assertEquals(129.4, box.centerLon, 1e-12);
        assertEquals(HypoUtils.LEGACY_LAT_RANGE_DEG, box.latRange, 1e-12);
        assertEquals(HypoUtils.LEGACY_DEP_RANGE_KM, box.depRange, 1e-12);
        assertTrue(box.usedHeaderDepth);
        assertEquals(4.0, box.centerDep, 1e-12);
    }
}
