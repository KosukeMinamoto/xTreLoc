package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TrdCentroidConstraintTest {

    @Test
    public void removeMeanShiftKeepsRelativeOffsetsAndZeroMean() {
        double[] dLon = { 0.02, 0.00, 0.01 };
        double[] dLat = { 0.00, 0.03, 0.00 };
        double[] dDep = { 1.0, 1.0, 4.0 };
        boolean[] used = { true, true, true };
        double[] mean = HypoTripleDiff.removeMeanShift(dLon, dLat, dDep, used);
        assertArrayEquals(new double[] { 0.01, 0.01, 2.0 }, mean, 1e-12);
        assertEquals(0.0, dLon[0] + dLon[1] + dLon[2], 1e-12);
        assertEquals(0.0, dLat[0] + dLat[1] + dLat[2], 1e-12);
        assertEquals(0.0, dDep[0] + dDep[1] + dDep[2], 1e-12);
        assertEquals(0.02, dLon[0] - dLon[1], 1e-12);
    }

    @Test
    public void unusedEventsAreIgnored() {
        double[] dLon = { 0.10, 0.00 };
        double[] dLat = { 0.00, 0.00 };
        double[] dDep = { 0.00, 0.00 };
        boolean[] used = { true, false };
        double[] mean = HypoTripleDiff.removeMeanShift(dLon, dLat, dDep, used);
        assertArrayEquals(new double[] { 0.10, 0.00, 0.00 }, mean, 1e-12);
        assertEquals(0.0, dLon[0], 1e-12);
        assertEquals(0.00, dLon[1], 1e-12);
    }

    @Test
    public void emptyUpdateReturnsNull() {
        assertNull(HypoTripleDiff.removeMeanShift(
            new double[1], new double[1], new double[1], new boolean[] { false }));
    }

    @Test
    public void pinKeepsLastThreeWeightsAtOne() {
        double[] w = { 0.2, 0.0, 0.4, 0.0, 0.1 };
        HypoTripleDiff.pinCentroidConstraintWeights(w);
        assertEquals(1.0, w[2], 0.0);
        assertEquals(1.0, w[3], 0.0);
        assertEquals(1.0, w[4], 0.0);
        assertEquals(0.2, w[0], 0.0);
    }
}
