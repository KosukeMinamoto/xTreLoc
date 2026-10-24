package com.treloc.xtreloc.app.gui.service;

import edu.sc.seis.seisFile.sac.SacTimeSeries;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Maeda (1985) variance AIC picker. P is taken on the vertical (or first) component;
 * S is taken after P on the time-aligned horizontal envelope, inside a limited P–S window,
 * then refined in a short window around the coarse pick.
 */
public final class AicPicker {

    private static final double VAR_FLOOR = 1e-20;

    private AicPicker() {}

    public static final class Params {
        /** Fraction of the trace used as the P search window (from the start). */
        public double pSearchFraction = 2.0 / 3.0;
        /** Minimum P–S gap in seconds. */
        public double minPsSec = 0.5;
        /** Maximum P–S gap in seconds (keeps AIC off the late S coda). */
        public double maxPsSec = 20.0;
        /** Half-width of the second-pass AIC window around the coarse S pick. */
        public double sRefineSec = 1.5;
        /** Moving-average length applied to the aligned √(N²+E²) envelope. */
        public double sSmoothSec = 0.2;
    }

    public static final class StationPick {
        public final String station;
        public final String pComponent;
        public final String sComponent;
        public final int pIndex;
        public final int sIndex;
        public final Date pTime;
        public final Date sTime;

        StationPick(String station, String pComponent, String sComponent,
                    int pIndex, int sIndex, Date pTime, Date sTime) {
            this.station = station;
            this.pComponent = pComponent;
            this.sComponent = sComponent;
            this.pIndex = pIndex;
            this.sIndex = sIndex;
            this.pTime = pTime;
            this.sTime = sTime;
        }
    }

    /**
     * Variance AIC on {@code x[from, to)}. Returns the split index in the original array, or {@code -1}.
     */
    public static int pickIndex(double[] x, int from, int to) {
        if (x == null) {
            return -1;
        }
        int start = Math.max(0, from);
        int end = Math.min(x.length, to);
        int n = end - start;
        if (n < 8) {
            return -1;
        }
        double[] cs = new double[n + 1];
        double[] cq = new double[n + 1];
        for (int i = 0; i < n; i++) {
            double v = x[start + i];
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                v = 0;
            }
            cs[i + 1] = cs[i] + v;
            cq[i + 1] = cq[i] + v * v;
        }
        double best = Double.POSITIVE_INFINITY;
        int bestK = -1;
        for (int k = 2; k <= n - 2; k++) {
            double var1 = variance(cs, cq, 0, k);
            double var2 = variance(cs, cq, k, n);
            if (var1 < VAR_FLOOR) {
                var1 = VAR_FLOOR;
            }
            if (var2 < VAR_FLOOR) {
                var2 = VAR_FLOOR;
            }
            double aic = k * Math.log(var1) + (n - k - 1) * Math.log(var2);
            if (aic < best) {
                best = aic;
                bestK = k;
            }
        }
        return bestK < 0 ? -1 : start + bestK;
    }

    public static int pickIndex(double[] x) {
        return x == null ? -1 : pickIndex(x, 0, x.length);
    }

    /**
     * Picks P and S for every station present in {@code waveforms}.
     */
    public static List<StationPick> pickStations(SacTimeSeries[] waveforms, Params params) {
        Objects.requireNonNull(waveforms, "waveforms");
        Params p = params == null ? new Params() : params;
        Map<String, List<SacTimeSeries>> byStation = new LinkedHashMap<>();
        for (SacTimeSeries sac : waveforms) {
            if (sac == null) {
                continue;
            }
            String st = stationOf(sac);
            byStation.computeIfAbsent(st, k -> new ArrayList<>()).add(sac);
        }
        List<StationPick> out = new ArrayList<>();
        for (Map.Entry<String, List<SacTimeSeries>> e : byStation.entrySet()) {
            StationPick pick = pickOneStation(e.getKey(), e.getValue(), p);
            if (pick != null) {
                out.add(pick);
            }
        }
        return out;
    }

    static boolean isVertical(String component) {
        String c = component == null ? "" : component.toUpperCase(Locale.ROOT);
        return c.contains("Z") || c.contains("U") || c.contains("V");
    }

    static boolean isHorizontal(String component) {
        if (isVertical(component)) {
            return false;
        }
        String c = component == null ? "" : component.toUpperCase(Locale.ROOT);
        return c.contains("N") || c.contains("E") || c.contains("X") || c.contains("Y") || c.contains("H");
    }

    private static StationPick pickOneStation(String station, List<SacTimeSeries> traces, Params params) {
        SacTimeSeries pTrace = null;
        for (SacTimeSeries s : traces) {
            if (isVertical(s.getHeader().getKcmpnm())) {
                pTrace = s;
                break;
            }
        }
        if (pTrace == null) {
            pTrace = traces.get(0);
        }
        double[] pData = toDoubles(pTrace.getY());
        int pEnd = Math.max(8, (int) Math.round(pData.length * clamp01(params.pSearchFraction)));
        int pIdx = pickIndex(pData, 0, pEnd);
        if (pIdx < 0) {
            return null;
        }

        Date pTime = sampleTime(pTrace, pIdx);

        List<SacTimeSeries> horizontals = new ArrayList<>();
        for (SacTimeSeries s : traces) {
            if (isHorizontal(s.getHeader().getKcmpnm())) {
                horizontals.add(s);
            }
        }
        SSeries sSeries = buildSSeries(horizontals, pTrace, params);
        int sIdx = pickS(sSeries, pTime.getTime(), params);
        Date sTime = sIdx >= 0 ? sSeries.timeAt(sIdx) : null;
        return new StationPick(station, trimCmp(pTrace), sSeries.component, pIdx, sIdx, pTime, sTime);
    }

    static SSeries buildSSeries(List<SacTimeSeries> horizontals, SacTimeSeries pTrace, Params params) {
        if (horizontals.size() >= 2) {
            try {
                CrossCorrelationPickingService.AlignedPair al =
                    CrossCorrelationPickingService.alignPair(horizontals.get(0), horizontals.get(1));
                double[] env = new double[al.a.length];
                for (int i = 0; i < al.a.length; i++) {
                    env[i] = Math.hypot(al.a[i], al.b[i]);
                }
                int win = Math.max(1, (int) Math.round(Math.max(0.02, params.sSmoothSec) / al.deltaSec));
                return new SSeries(smooth(env, win), al.startMillis, al.deltaSec, trimCmp(horizontals.get(0)));
            } catch (IOException ex) {
                // fall through to a single-trace fallback
            }
        }
        SacTimeSeries src = horizontals.isEmpty() ? pTrace : horizontals.get(0);
        return SSeries.fromTrace(src);
    }

    /**
     * Coarse AIC in {@code [P+minPs, P+maxPs]}, then a short refinement pass.
     * A long post-P window lets Maeda AIC lock onto the S coda instead of the onset.
     */
    static int pickS(SSeries sSeries, long pEpochMillis, Params params) {
        if (sSeries == null || sSeries.y.length < 8) {
            return -1;
        }
        double minPs = Math.max(0, params.minPsSec);
        double maxPs = params.maxPsSec > minPs ? params.maxPsSec : minPs + 8;
        int sStart = sSeries.indexAt(pEpochMillis + Math.round(minPs * 1000.0));
        int sEnd = sSeries.indexAt(pEpochMillis + Math.round(maxPs * 1000.0));
        sStart = clamp(sStart, 0, sSeries.y.length - 8);
        sEnd = clamp(sEnd, sStart + 8, sSeries.y.length);
        int coarse = pickIndex(sSeries.y, sStart, sEnd);
        if (coarse < 0) {
            return -1;
        }
        int half = Math.max(4, (int) Math.round(Math.max(0.2, params.sRefineSec) / sSeries.deltaSec));
        int f0 = clamp(coarse - half, sStart, sEnd - 8);
        int f1 = clamp(coarse + half, f0 + 8, sEnd);
        int fine = pickIndex(sSeries.y, f0, f1);
        return fine >= 0 ? fine : coarse;
    }

    static final class SSeries {
        final double[] y;
        final long startMillis;
        final double deltaSec;
        final String component;

        SSeries(double[] y, long startMillis, double deltaSec, String component) {
            this.y = y;
            this.startMillis = startMillis;
            this.deltaSec = deltaSec <= 0 ? 0.01 : deltaSec;
            this.component = component == null ? "?" : component;
        }

        static SSeries fromTrace(SacTimeSeries sac) {
            return new SSeries(toDoubles(sac.getY()),
                WaveProcessor.startEpochMillis(sac),
                sac.getHeader().getDelta(),
                trimCmp(sac));
        }

        int indexAt(long epochMillis) {
            return (int) Math.round((epochMillis - startMillis) / (deltaSec * 1000.0));
        }

        Date timeAt(int index) {
            return Date.from(Instant.ofEpochMilli(
                startMillis + Math.round(index * deltaSec * 1000.0)));
        }
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) {
            return lo;
        }
        if (v > hi) {
            return hi;
        }
        return v;
    }

    static Date sampleTime(SacTimeSeries sac, int index) {
        java.time.LocalDateTime t0 = WaveProcessor.getStarttime(sac);
        double delta = sac.getHeader().getDelta();
        java.time.LocalDateTime t = t0.plusNanos((long) (index * delta * 1_000_000_000L));
        return Date.from(t.atZone(ZoneId.systemDefault()).toInstant());
    }

    /** Station code of a trace without blanks ({@code "UNK"} when empty). */
    static String stationOf(SacTimeSeries sac) {
        String k = sac.getHeader().getKstnm();
        String st = k == null ? "" : k.trim().replace(" ", "");
        return st.isEmpty() ? "UNK" : st;
    }

    static String trimCmp(SacTimeSeries s) {
        String c = s.getHeader().getKcmpnm();
        return c == null ? "?" : c.trim();
    }

    static double[] toDoubles(float[] y) {
        double[] d = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            d[i] = y[i];
        }
        return d;
    }

    private static double[] smooth(double[] in, int win) {
        int n = in.length;
        double[] out = new double[n];
        if (win <= 1) {
            System.arraycopy(in, 0, out, 0, n);
            return out;
        }
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - win / 2);
            int b = Math.min(n, i + win / 2 + 1);
            double s = 0;
            for (int j = a; j < b; j++) {
                s += in[j];
            }
            out[i] = s / (b - a);
        }
        return out;
    }

    private static double variance(double[] cs, double[] cq, int a, int b) {
        int m = b - a;
        if (m < 1) {
            return 0;
        }
        double sum = cs[b] - cs[a];
        double sum2 = cq[b] - cq[a];
        double mean = sum / m;
        return Math.max(0, sum2 / m - mean * mean);
    }

    private static double clamp01(double v) {
        if (v < 0.15) {
            return 0.15;
        }
        if (v > 0.95) {
            return 0.95;
        }
        return v;
    }
}
