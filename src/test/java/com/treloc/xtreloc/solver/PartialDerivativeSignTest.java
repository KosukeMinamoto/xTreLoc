package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.solver.table.TravelTimeTable;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Checks that {@link HypoUtils#partialDerivativeMatrix} is {@code ∂T/∂(lon,lat,dep)}
 * (not {@code -∂T/∂x}) and that the pair / triple residual convention is internally consistent.
 */
public class PartialDerivativeSignTest {

    private static HypoUtils utils;
    private static final double LAT0 = 36.10;
    private static final double LON0 = 129.38;
    private static final double DEP0 = 4.0;

    @BeforeClass
    public static void loadModel() throws Exception {
        AppConfig cfg = new AppConfig();
        cfg.taupFile = "prem.nd";
        cfg.hypBottom = 50;
        utils = new HypoUtils(cfg);
    }

    @Test
    public void analyticalDerivativesMatchFiniteDifferenceSignAndMagnitude() throws Exception {
        // station to the ENE, another to the north, one nearly above the source
        double[][] stn = {
            {LAT0, LON0 + 0.15, 0.1, 0, 0},
            {LAT0 + 0.12, LON0, 0.1, 0, 0},
            {LAT0 + 0.01, LON0 - 0.01, 0.2, 0, 0},
            {LAT0 - 0.08, LON0 - 0.10, 1.5, 0, 0}
        };
        int[] idx = {0, 1, 2, 3};
        Point p = new Point("t", LAT0, LON0, DEP0, 0, 0, 0, 0, "", "", -999);
        double[][] dtdr = (double[][]) utils.partialDerivativeMatrix(stn, idx, p, TravelTimeTable.Phase.S)[0];

        double dLon = 0.002;
        double dLat = 0.002;
        double dDep = 0.2;
        for (int i : idx) {
            int[] one = {i};
            double fdLon = (t(stn, one, LAT0, LON0 + dLon, DEP0, i) - t(stn, one, LAT0, LON0 - dLon, DEP0, i)) / (2 * dLon);
            double fdLat = (t(stn, one, LAT0 + dLat, LON0, DEP0, i) - t(stn, one, LAT0 - dLat, LON0, DEP0, i)) / (2 * dLat);
            double fdDep = (t(stn, one, LAT0, LON0, DEP0 + dDep, i) - t(stn, one, LAT0, LON0, DEP0 - dDep, i)) / (2 * dDep);
            assertEquals("dT/dlon station " + i, fdLon, dtdr[i][0], 0.20 * Math.abs(fdLon) + 0.05);
            assertEquals("dT/dlat station " + i, fdLat, dtdr[i][1], 0.20 * Math.abs(fdLat) + 0.05);
            assertEquals("dT/ddep station " + i, fdDep, dtdr[i][2], 0.20 * Math.abs(fdDep) + 0.02);
        }

        // cache must not alias sources ~200 m apart (old 0.01° key made T piecewise-constant)
        int[] one = {0};
        double tA = t(stn, one, LAT0, LON0, DEP0, 0);
        double tB = t(stn, one, LAT0, LON0 + 0.002, DEP0, 0);
        assertTrue("travel time must change over 0.002 deg: " + tA + " vs " + tB, Math.abs(tA - tB) > 1e-4);

        // moving the source toward an eastern station must shorten T
        assertTrue("dT/dlon toward an eastern station should be negative: " + dtdr[0][0], dtdr[0][0] < 0);
        assertTrue("dT/dlat toward a northern station should be negative: " + dtdr[1][1], dtdr[1][1] < 0);
        // surface station, source in the crust: deeper usually later
        assertTrue("dT/ddep for a surface station should be positive: " + dtdr[0][2], dtdr[0][2] > 0);
    }

    @Test
    public void pairJacobianIsDcalDhypAndResidualIsObsMinusCal() throws Exception {
        double[][] stn = {
            {LAT0, LON0 + 0.10, 0.1, 0, 0},
            {LAT0, LON0 - 0.08, 0.1, 0, 0}
        };
        int[] idx = {0, 1};
        Point p = new Point("t", LAT0, LON0, DEP0, 0, 0, 0, 0, "", "", -999);
        Object[] tmp = utils.partialDerivativeMatrix(stn, idx, p, TravelTimeTable.Phase.S);
        double[][] dtdr = (double[][]) tmp[0];
        double[] t0 = (double[]) tmp[1];
        double cal = t0[1] - t0[0];
        double gLon = dtdr[1][0] - dtdr[0][0];
        double gLat = dtdr[1][1] - dtdr[0][1];
        double gDep = dtdr[1][2] - dtdr[0][2];

        double stepLon = 0.001;
        Point shifted = new Point("t", LAT0, LON0 + stepLon, DEP0, 0, 0, 0, 0, "", "", -999);
        double[] t1 = utils.travelTime(stn, idx, shifted, TravelTimeTable.Phase.S);
        double calShift = t1[1] - t1[0];
        double predicted = cal + gLon * stepLon;
        assertEquals("pair lag linearization in lon", calShift, predicted, 0.15 * Math.abs(calShift - cal) + 5e-4);

        double[][] lag = {{0, 1, cal + 0.05, 1.0, DatPair.S}};
        double[] r = DatPair.residual(lag, t0, t0);
        assertEquals(0.05, r[0], 1e-12);
    }

    @Test
    public void tripleDifferencePredictedSignMatchesObservedTdTime() {
        // eve0 / eve1 travel times to stations k=0, l=1
        double[] t0 = {10.0, 12.5};
        double[] t1 = {10.2, 12.1};
        double lag0 = t0[1] - t0[0];
        double lag1 = t1[1] - t1[0];
        double tdTime = lag1 - lag0;
        assertEquals(tdTime, DatPair.calcTriple(t0, t1, 0, 1), 1e-12);
        // the old attachResiduals formula (Tk1-Tl1)-(Tk0-Tl0) is exactly -tdTime
        double oldPred = (t1[0] - t1[1]) - (t0[0] - t0[1]);
        assertEquals(-tdTime, oldPred, 1e-12);
    }

    private static double t(double[][] stn, int[] one, double lat, double lon, double dep, int station) {
        Point p = new Point("t", lat, lon, dep, 0, 0, 0, 0, "", "", -999);
        return utils.travelTime(stn, one, p, TravelTimeTable.Phase.S)[station];
    }
}
