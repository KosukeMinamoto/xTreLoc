package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import edu.sc.seis.seisFile.sac.SacHeader;
import edu.sc.seis.seisFile.sac.SacTimeSeries;
import org.junit.Test;

public class CrossCorrelationAlignTest {

    @Test
    public void startTimeIncludesSacBeginOffset() {
        SacTimeSeries withB = impulse("STA", "HHN", 0.01f, 64, 0, 0.25f, 10);
        SacTimeSeries noB = impulse("STA", "HHE", 0.01f, 64, 0, 0f, 10);
        long dt = WaveProcessor.startEpochMillis(withB) - WaveProcessor.startEpochMillis(noB);
        assertEquals(250L, dt);
    }

    @Test
    public void envelopeAlignsDifferentDeltaAndStart() throws Exception {
        // Impulse at absolute t = 1.00 s on both horizontals.
        // N: 100 Hz, B = 0, peak at sample 100.
        // E: 50 Hz, B = 0.20 s, peak at sample 40 (0.20 + 0.80).
        SacTimeSeries n = impulse("STA", "HHN", 0.01f, 300, 0, 0f, 100);
        SacTimeSeries e = impulse("STA", "HHE", 0.02f, 200, 0, 0.20f, 40);

        CrossCorrelationPickingService.Params p = new CrossCorrelationPickingService.Params();
        p.eigWindowSec = 0.01;
        p.filterFreqMin = null;
        p.filterFreqMax = null;

        CrossCorrelationPickingService.StationFeature env =
            CrossCorrelationPickingService.computeEnvelopeFeature(n, e, p);

        assertEquals(0.01, env.deltaSec, 1e-8);
        assertEquals(WaveProcessor.startEpochMillis(e), env.startMillis);

        int peak = argmax(env.y);
        // Overlap starts at 0.20 s; peak at 1.00 s → index 80.
        assertEquals(80, peak);
        assertTrue(env.y[peak] > 0);
    }

    @Test
    public void resampleLinearlyInterpolatesCoarserTrace() {
        float[] y = { 0f, 10f, 20f };
        double[] out = CrossCorrelationPickingService.resample(y, 0L, 0.02, 0L, 0.01, 5);
        assertEquals(5, out.length);
        assertEquals(0.0, out[0], 1e-9);
        assertEquals(5.0, out[1], 1e-9);
        assertEquals(10.0, out[2], 1e-9);
        assertEquals(15.0, out[3], 1e-9);
        assertEquals(20.0, out[4], 1e-9);
    }

    private static int argmax(double[] y) {
        int iMax = 0;
        double vMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < y.length; i++) {
            if (y[i] > vMax) {
                vMax = y[i];
                iMax = i;
            }
        }
        return iMax;
    }

    private static SacTimeSeries impulse(String stn, String cmp, float delta, int n,
                                        int nzmsec, float b, int peakIndex) {
        float[] y = new float[n];
        if (peakIndex >= 0 && peakIndex < n) {
            y[peakIndex] = 1f;
        }
        SacHeader h = new SacHeader();
        h.setNpts(n);
        h.setDelta(delta);
        h.setB(b);
        h.setKstnm(stn);
        h.setKcmpnm(cmp);
        h.setNzyear(2007);
        h.setNzjday(1);
        h.setNzhour(0);
        h.setNzmin(0);
        h.setNzsec(0);
        h.setNzmsec(nzmsec);
        return new SacTimeSeries(h, y);
    }
}
