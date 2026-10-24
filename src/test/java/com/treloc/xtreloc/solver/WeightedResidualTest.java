package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeightedResidualTest {

    @Test
    public void pPairsCountTwiceWhenPWeightIsTwo() {
        double[] residual = { 0.2, 0.4 };
        double[][] lag = {
            { 0, 1, 0, 1, DatPair.P },
            { 0, 1, 0, 1, DatPair.S }
        };
        double rms = HypoUtils.weightedRms(residual, lag, 2.0);
        double expected = Math.sqrt((2.0 * 0.04 + 1.0 * 0.16) / 3.0);
        assertEquals(expected, rms, 1e-12);
    }

    @Test
    public void onlySEqualsUnweightedRms() {
        double[] residual = { 0.3, -0.3 };
        double[][] lag = {
            { 0, 1, 0, 1, DatPair.S },
            { 1, 2, 0, 1, DatPair.S }
        };
        double rms = HypoUtils.weightedRms(residual, lag, 2.0);
        assertEquals(0.3, rms, 1e-12);
    }
}
