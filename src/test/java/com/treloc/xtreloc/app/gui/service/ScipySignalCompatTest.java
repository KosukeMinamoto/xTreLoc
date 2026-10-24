package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;
import org.junit.Test;

public class ScipySignalCompatTest {

    @Test
    public void sameModeMatchesFullSlice_oddAndEven() {
        for (int n : new int[] {3, 8, 15, 16, 63, 64, 100, 200}) {
            double[] a = ramp(n, 1.0);
            double[] b = ramp(n, 0.3);
            double[] full = naiveFull(a, b);
            int start = (full.length - n) / 2;
            double[] expect = Arrays.copyOfRange(full, start, start + n);
            double[] got = ScipySignalCompat.numpyCorrelateSame(a, b);
            assertEquals("len " + n, expect.length, got.length);
            assertArrayEquals("n=" + n, expect, got, 1e-9 * (1 + maxAbs(expect)));
            assertEquals("peak n=" + n, argmax(expect), ScipySignalCompat.argMax(got));
        }
    }

    @Test
    public void fftAndDirectAgree() {
        Random rnd = new Random(1);
        for (int n : new int[] {64, 65, 128, 200, 512}) {
            double[] a = new double[n];
            double[] b = new double[n];
            for (int i = 0; i < n; i++) {
                a[i] = rnd.nextGaussian();
                b[i] = rnd.nextGaussian();
            }
            double[] d = new double[n];
            double[] f = new double[n];
            ScipySignalCompat.directCorrelateSame(a, b, d);
            ScipySignalCompat.fftCorrelateSame(a, b, f);
            assertArrayEquals("n=" + n, d, f, 1e-9 * (1 + maxAbs(d)));
        }
    }

    @Test
    public void delayedImpulsePeakMatchesLag() {
        int n = 128;
        double[] a = new double[n];
        double[] b = new double[n];
        a[40] = 1.0;
        b[48] = 1.0;
        double[] dest = new double[n];
        int peak = ScipySignalCompat.peakCorrelateSameEqual(a, b, dest);
        int[] lags = ScipySignalCompat.correlationLagsSameEqual(n);
        assertEquals(-8, lags[peak]);
    }

    private static double[] ramp(int n, double s) {
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = s * (i + 1);
        }
        return y;
    }

    private static double[] naiveFull(double[] a, double[] b) {
        int n = a.length;
        int m = b.length;
        double[] c = new double[n + m - 1];
        for (int k = 0; k < c.length; k++) {
            double s = 0;
            for (int i = 0; i < n; i++) {
                int jb = m - 1 - k + i;
                if (jb >= 0 && jb < m) {
                    s += a[i] * b[jb];
                }
            }
            c[k] = s;
        }
        return c;
    }

    private static int argmax(double[] y) {
        int ix = 0;
        for (int i = 1; i < y.length; i++) {
            if (y[i] > y[ix]) {
                ix = i;
            }
        }
        return ix;
    }

    private static double maxAbs(double[] y) {
        double m = 0;
        for (double v : y) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }
}
