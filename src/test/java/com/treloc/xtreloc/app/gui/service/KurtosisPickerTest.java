package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import edu.sc.seis.seisFile.sac.SacTimeSeries;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class KurtosisPickerTest {

    private static final double DT = 0.01;

    /** Gaussian noise (sigma 1) plus a decaying 5 Hz wavelet that starts at {@code onsetSec}. */
    private static double[] arrival(int n, double onsetSec, double amp, long seed) {
        Random r = new Random(seed);
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i * DT;
            x[i] = r.nextGaussian();
            if (t >= onsetSec) {
                double u = t - onsetSec;
                x[i] += amp * Math.exp(-u / 1.5) * Math.sin(2 * Math.PI * 5 * u);
            }
        }
        return x;
    }

    @Test
    public void gaussianNoiseHasKurtosisNearThreeAndAnImpulseRaisesIt() {
        Random r = new Random(1);
        double[] x = new double[6000];
        for (int i = 0; i < x.length; i++) {
            x[i] = r.nextGaussian();
        }
        double[] k = KurtosisPicker.kurtosisCf(x, 200);
        assertTrue(Double.isNaN(k[198]));
        assertEquals(3.0, k[3000], 0.8);
        x[3000] += 40;
        double[] kSpike = KurtosisPicker.kurtosisCf(x, 200);
        assertTrue("spike should lift kurtosis, got " + kSpike[3050], kSpike[3050] > 8);
        assertEquals("far from the spike nothing changes", k[5000], kSpike[5000], 1e-9);
    }

    @Test
    public void flatTraceGivesNoPick() {
        assertEquals(-1, KurtosisPicker.pickIndex(new double[3000], 0, 3000, DT, new KurtosisPicker.Params()));
        assertEquals(-1, KurtosisPicker.pickIndex(null, 0, 10, DT, new KurtosisPicker.Params()));
    }

    @Test
    public void picksTheOnsetOfAnImpulsiveArrival() {
        for (long seed = 1; seed <= 5; seed++) {
            double[] x = arrival(4000, 10.0, 12.0, seed);
            int idx = KurtosisPicker.pickIndex(x, 0, 2600, DT, new KurtosisPicker.Params());
            assertTrue("seed " + seed + ": pick at " + idx * DT + " s, onset 10.0 s",
                Math.abs(idx * DT - 10.0) < 0.3);
        }
    }

    @Test
    public void searchWindowKeepsPickInsideIt() {
        double[] x = arrival(4000, 30.0, 12.0, 7);
        int idx = KurtosisPicker.pickIndex(x, 0, 1500, DT, new KurtosisPicker.Params());
        assertTrue(idx < 0 || idx < 1500);
    }

    @Test
    public void picksPAndSPerStation() {
        SacTimeSeries z = TestSac.make("STA", "HHZ", toFloat(arrival(4000, 10.0, 12.0, 11)));
        SacTimeSeries n = TestSac.make("STA", "HHN", toFloat(arrival(4000, 14.0, 12.0, 12)));
        SacTimeSeries e = TestSac.make("STA", "HHE", toFloat(arrival(4000, 14.0, 12.0, 13)));
        KurtosisPicker.Params p = new KurtosisPicker.Params();
        p.minPsSec = 1.0;
        p.maxPsSec = 8.0;
        List<AicPicker.StationPick> picks = KurtosisPicker.pickStations(new SacTimeSeries[] {z, n, e}, p);
        assertEquals(1, picks.size());
        AicPicker.StationPick pick = picks.get(0);
        assertTrue(pick.pTime != null && pick.sTime != null);
        double pSec = (pick.pTime.getTime() - java.time.LocalDateTime.of(2007, 1, 1, 0, 0).atZone(
            java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) / 1000.0;
        assertEquals(10.0, pSec, 0.4);
        double ps = (pick.sTime.getTime() - pick.pTime.getTime()) / 1000.0;
        assertEquals(4.0, ps, 0.8);
    }

    private static float[] toFloat(double[] x) {
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = (float) x[i];
        }
        return y;
    }
}
