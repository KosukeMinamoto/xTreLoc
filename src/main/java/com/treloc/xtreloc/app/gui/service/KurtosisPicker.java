package com.treloc.xtreloc.app.gui.service;

import edu.sc.seis.seisFile.sac.SacTimeSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Kurtosis picker (Baillard et al., 2014, simplified). The characteristic function is the kurtosis of a
 * sliding window; it rises when the first impulsive samples of an arrival enter the window. The onset
 * is taken where the rise starts (just before the peak), for several window lengths, and the median of
 * those onsets is the pick.
 *
 * <p>Station handling (P on the vertical, S on the aligned horizontal envelope after P) matches
 * {@link AicPicker}, so the two methods can be compared on the same traces.</p>
 */
public final class KurtosisPicker {

    private KurtosisPicker() {}

    public static final class Params {
        /** Window lengths (seconds) of the kurtosis functions; the pick is the median over them. */
        public double[] windowsSec = {0.5, 1.0, 2.0};
        /** Fraction of the rise (from its start to the peak) that marks the onset. */
        public double onsetLevel = 0.3;
        /** Fraction of the trace searched for P (from the start). */
        public double pSearchFraction = 2.0 / 3.0;
        public double minPsSec = 0.5;
        public double maxPsSec = 20.0;
        /** Moving-average length applied to the aligned √(N²+E²) envelope used for S. */
        public double sSmoothSec = 0.2;
    }

    /**
     * Kurtosis of the window of {@code win} samples ending at each index (index {@code i} covers
     * {@code [i-win+1, i]}); entries before the first full window are {@code NaN}.
     */
    public static double[] kurtosisCf(double[] x, int win) {
        int n = x.length;
        double[] out = new double[n];
        Arrays.fill(out, Double.NaN);
        if (win < 4 || n < win) {
            return out;
        }
        double mean = 0;
        for (double v : x) {
            mean += v;
        }
        mean /= n;
        double var = 0;
        for (double v : x) {
            var += (v - mean) * (v - mean);
        }
        double scale = Math.sqrt(var / n);
        if (!(scale > 0)) {
            return out;
        }
        double s1 = 0;
        double s2 = 0;
        double s3 = 0;
        double s4 = 0;
        for (int i = 0; i < n; i++) {
            double v = clean((x[i] - mean) / scale);
            s1 += v;
            s2 += v * v;
            s3 += v * v * v;
            s4 += v * v * v * v;
            if (i >= win) {
                double o = clean((x[i - win] - mean) / scale);
                s1 -= o;
                s2 -= o * o;
                s3 -= o * o * o;
                s4 -= o * o * o * o;
            }
            if (i >= win - 1) {
                double m = s1 / win;
                double m2 = s2 / win - m * m;
                if (m2 > 1e-12) {
                    double m4 = s4 / win - 4 * m * (s3 / win) + 6 * m * m * (s2 / win) - 3 * m * m * m * m;
                    out[i] = m4 / (m2 * m2);
                } else {
                    out[i] = 3.0;
                }
            }
        }
        return out;
    }

    private static double clean(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? 0 : v;
    }

    /**
     * Onset index in {@code [from, to)} for one window length, or {@code -1}. The kurtosis function of the
     * window is searched for its peak; the onset is where the preceding rise passes {@code level} of its height.
     */
    static int onsetForWindow(double[] x, int from, int to, int win, double level) {
        int start = Math.max(0, from - win);
        int end = Math.min(x.length, to);
        if (end - start < win + 8) {
            return -1;
        }
        double[] seg = Arrays.copyOfRange(x, start, end);
        double[] k = kurtosisCf(seg, win);
        int smooth = Math.max(1, win / 10);
        double[] ks = movingAverage(k, smooth);
        int first = Math.max(win - 1, from - start);
        int peak = -1;
        double best = Double.NEGATIVE_INFINITY;
        for (int i = first; i < ks.length; i++) {
            if (!Double.isNaN(ks[i]) && ks[i] > best) {
                best = ks[i];
                peak = i;
            }
        }
        if (peak < 0) {
            return -1;
        }
        double low = best;
        int lookBack = Math.max(first, peak - 3 * win);
        for (int i = lookBack; i <= peak; i++) {
            if (!Double.isNaN(ks[i]) && ks[i] < low) {
                low = ks[i];
            }
        }
        if (best - low < 1e-9) {
            return -1;
        }
        double threshold = low + level * (best - low);
        int onset = peak;
        while (onset > lookBack && !Double.isNaN(ks[onset - 1]) && ks[onset - 1] > threshold) {
            onset--;
        }
        // the trailing moving average delays the curve by half its length
        return start + Math.max(0, onset - (smooth - 1) / 2);
    }

    /** Median of the per-window onsets in {@code [from, to)}, or {@code -1} when no window gives one. */
    public static int pickIndex(double[] x, int from, int to, double deltaSec, Params p) {
        if (x == null || deltaSec <= 0) {
            return -1;
        }
        List<Integer> onsets = new ArrayList<>();
        for (double w : p.windowsSec) {
            int win = (int) Math.round(w / deltaSec);
            int o = onsetForWindow(x, from, to, win, p.onsetLevel);
            if (o >= 0) {
                onsets.add(o);
            }
        }
        if (onsets.isEmpty()) {
            return -1;
        }
        onsets.sort(Integer::compare);
        return onsets.get(onsets.size() / 2);
    }

    /** Picks P and S for every station in {@code waveforms}. */
    public static List<AicPicker.StationPick> pickStations(SacTimeSeries[] waveforms, Params params) {
        Objects.requireNonNull(waveforms, "waveforms");
        Params p = params == null ? new Params() : params;
        Map<String, List<SacTimeSeries>> byStation = new LinkedHashMap<>();
        for (SacTimeSeries sac : waveforms) {
            if (sac == null) {
                continue;
            }
            String st = sac.getHeader().getKstnm().trim().replace(" ", "");
            byStation.computeIfAbsent(st.isEmpty() ? "UNK" : st, k -> new ArrayList<>()).add(sac);
        }
        List<AicPicker.StationPick> out = new ArrayList<>();
        for (Map.Entry<String, List<SacTimeSeries>> e : byStation.entrySet()) {
            AicPicker.StationPick pick = pickOneStation(e.getKey(), e.getValue(), p);
            if (pick != null) {
                out.add(pick);
            }
        }
        return out;
    }

    private static AicPicker.StationPick pickOneStation(String station, List<SacTimeSeries> traces, Params p) {
        SacTimeSeries pTrace = null;
        for (SacTimeSeries s : traces) {
            if (AicPicker.isVertical(s.getHeader().getKcmpnm())) {
                pTrace = s;
                break;
            }
        }
        if (pTrace == null) {
            pTrace = traces.get(0);
        }
        double[] pData = AicPicker.toDoubles(pTrace.getY());
        double dt = pTrace.getHeader().getDelta();
        int pEnd = Math.max(16, (int) Math.round(pData.length * Math.min(0.95, Math.max(0.15, p.pSearchFraction))));
        int pIdx = pickIndex(pData, 0, pEnd, dt, p);
        if (pIdx < 0) {
            return null;
        }
        Date pTime = AicPicker.sampleTime(pTrace, pIdx);

        List<SacTimeSeries> horizontals = new ArrayList<>();
        for (SacTimeSeries s : traces) {
            if (AicPicker.isHorizontal(s.getHeader().getKcmpnm())) {
                horizontals.add(s);
            }
        }
        AicPicker.Params ap = new AicPicker.Params();
        ap.sSmoothSec = p.sSmoothSec;
        AicPicker.SSeries ss = AicPicker.buildSSeries(horizontals, pTrace, ap);
        int sIdx = pickS(ss, pTime.getTime(), p);
        Date sTime = sIdx >= 0 ? ss.timeAt(sIdx) : null;
        return new AicPicker.StationPick(station, AicPicker.trimCmp(pTrace), ss.component, pIdx, sIdx, pTime, sTime);
    }

    private static int pickS(AicPicker.SSeries ss, long pEpochMillis, Params p) {
        if (ss == null || ss.y.length < 16) {
            return -1;
        }
        double minPs = Math.max(0, p.minPsSec);
        double maxPs = p.maxPsSec > minPs ? p.maxPsSec : minPs + 8;
        int from = ss.indexAt(pEpochMillis + Math.round(minPs * 1000.0));
        int to = ss.indexAt(pEpochMillis + Math.round(maxPs * 1000.0));
        from = Math.max(0, Math.min(from, ss.y.length - 16));
        to = Math.max(from + 16, Math.min(to, ss.y.length));
        return pickIndex(ss.y, from, to, ss.deltaSec, p);
    }

    private static double[] movingAverage(double[] in, int win) {
        if (win <= 1) {
            return in;
        }
        double[] out = new double[in.length];
        for (int i = 0; i < in.length; i++) {
            int a = Math.max(0, i - win + 1);
            double sum = 0;
            int cnt = 0;
            for (int j = a; j <= i; j++) {
                if (!Double.isNaN(in[j])) {
                    sum += in[j];
                    cnt++;
                }
            }
            out[i] = cnt == 0 ? Double.NaN : sum / cnt;
        }
        return out;
    }
}
