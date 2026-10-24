package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import edu.sc.seis.seisFile.sac.SacTimeSeries;
import java.util.List;
import org.junit.Test;

public class AicPickerTest {

    @Test
    public void picksNearStepOnset() {
        double[] x = new double[400];
        for (int i = 0; i < 400; i++) {
            x[i] = i < 180 ? 0.02 * Math.sin(i * 0.3) : 1.0 + 0.05 * Math.sin(i * 0.7);
        }
        int idx = AicPicker.pickIndex(x);
        assertTrue("onset index " + idx, Math.abs(idx - 180) <= 8);
    }

    @Test
    public void searchWindowLimitsPick() {
        double[] x = new double[300];
        for (int i = 0; i < 300; i++) {
            x[i] = i < 220 ? 0.01 : 2.0;
        }
        int inWindow = AicPicker.pickIndex(x, 0, 250);
        assertTrue(Math.abs(inWindow - 220) <= 6);
        int early = AicPicker.pickIndex(x, 0, 80);
        assertTrue(early < 80);
    }

    @Test
    public void rejectsTinyArrays() {
        assertEquals(-1, AicPicker.pickIndex(new double[4]));
        assertEquals(-1, AicPicker.pickIndex(null));
    }

    @Test
    public void sSearchStartsAfterP() {
        double[] energy = new double[500];
        for (int i = 0; i < 500; i++) {
            if (i < 120) {
                energy[i] = 0.01;
            } else if (i < 260) {
                energy[i] = 0.4;
            } else {
                energy[i] = 2.0;
            }
        }
        int p = AicPicker.pickIndex(energy, 0, 200);
        int s = AicPicker.pickIndex(energy, p + 20, energy.length);
        assertTrue(p > 0 && p < 200);
        assertTrue("S " + s + " should follow P " + p, s > p);
        assertTrue(Math.abs(s - 260) <= 12);
    }

    @Test
    public void sPickStaysInMaxPsWindowNotLateCoda() {
        // 100 Hz, 12 s. P at 1.0 s on Z. S onset at 2.0 s on N/E, huge coda at 8.0 s.
        SacTimeSeries z = synthStep("STA", "HHZ", 1200, 100, 0.9f);
        SacTimeSeries n = synthSWithCoda("STA", "HHN");
        SacTimeSeries e = synthSWithCoda("STA", "HHE");
        AicPicker.Params p = new AicPicker.Params();
        p.minPsSec = 0.3;
        p.maxPsSec = 4.0;
        p.sRefineSec = 0.8;
        p.sSmoothSec = 0.05;
        List<AicPicker.StationPick> picks = AicPicker.pickStations(new SacTimeSeries[] { z, n, e }, p);
        assertEquals(1, picks.size());
        AicPicker.StationPick pick = picks.get(0);
        assertTrue(pick.pTime != null && pick.sTime != null);
        double ps = (pick.sTime.getTime() - pick.pTime.getTime()) / 1000.0;
        assertTrue("S–P was " + ps + " s (expected ~1 s, not the 7 s coda)", ps > 0.5 && ps < 2.0);
    }

    private static SacTimeSeries synthStep(String stn, String cmp, int n, int onset, float amp) {
        float[] y = new float[n];
        for (int i = 0; i < n; i++) {
            y[i] = i < onset ? 0.01f * (float) Math.sin(i * 0.2) : amp + 0.03f * (float) Math.sin(i * 0.4);
        }
        return TestSac.make(stn, cmp, y);
    }

    private static SacTimeSeries synthSWithCoda(String stn, String cmp) {
        int n = 1200;
        float[] y = new float[n];
        for (int i = 0; i < n; i++) {
            if (i < 200) {
                y[i] = 0.01f * (float) Math.sin(i * 0.15);
            } else if (i < 800) {
                y[i] = 0.55f + 0.04f * (float) Math.sin(i * 0.35);
            } else {
                y[i] = 3.2f + 0.05f * (float) Math.sin(i * 0.5);
            }
        }
        return TestSac.make(stn, cmp, y);
    }
}
