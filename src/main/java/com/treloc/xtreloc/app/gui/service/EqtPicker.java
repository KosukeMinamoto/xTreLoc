package com.treloc.xtreloc.app.gui.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import edu.sc.seis.seisFile.sac.SacTimeSeries;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * EQTransformer (Mousavi et al., 2020) P/S picker running on ONNX Runtime (CPU).
 *
 * <p>Each station needs three components (E, N, Z). The traces are demeaned, band-passed, brought to
 * 100 Hz and cut into 60 s windows with 50 % overlap; the overlapping probabilities are merged and the
 * P and S probability series are searched for peaks. The traces may be of any length (an event or a
 * continuous day): they are processed in segments, so memory does not grow with the length. Every peak
 * above the threshold is returned, which is what a following association step (for example REAL)
 * needs; {@link #best(List)} reduces them to one pick per station and phase for a single event.
 *
 * <p>Instances hold an ONNX session; use try-with-resources.
 */
public final class EqtPicker implements AutoCloseable {

    /** Window length of the network, in samples at 100 Hz. */
    public static final int WINDOW = 6000;
    /** Sampling rate the network expects. */
    public static final double FS = 100.0;
    /** Bundled model (SeisBench "original_nonconservative", exported to ONNX). */
    public static final String DEFAULT_MODEL = "/models/eqt/original_nonconservative.onnx";

    private static final int BATCH = 32;

    /** Settings; the defaults are the ones evaluated on the Pohang data. */
    public static final class Params {
        public double thresholdP = 0.1;
        public double thresholdS = 0.1;
        /** {@code true}: take the maximum of overlapping windows; {@code false}: the mean. */
        public boolean mergeMax = false;
        public boolean filter = true;
        public double fLowHz = 1.0;
        public double fHighHz = 45.0;
        /** Samples between window starts (at 100 Hz). */
        public int stride = 3000;
        /** Peaks closer than this are merged into the higher one (samples at 100 Hz). */
        public int minDistSamples = 50;
        /** Length processed at once; the result does not depend on it except at window edges. */
        public int segmentSec = 3600;
        /** Classpath resource of the ONNX model. */
        public String modelResource = DEFAULT_MODEL;
    }

    /** One probability peak. */
    public record Pick(String station, String phase, String component, Date time, double prob) {
    }

    /** Raised when the model cannot be loaded or run. */
    public static final class EqtException extends RuntimeException {
        public EqtException(String message, Throwable cause) {
            super(message, cause);
        }

        public EqtException(String message) {
            super(message);
        }
    }

    private final Params params;
    private final OrtEnvironment env;
    private final OrtSession session;
    private final List<String> skipped = new ArrayList<>();

    public EqtPicker(Params params) {
        this.params = params;
        try (InputStream in = EqtPicker.class.getResourceAsStream(params.modelResource)) {
            if (in == null) {
                throw new EqtException("EQTransformer model not found: " + params.modelResource);
            }
            byte[] bytes = in.readAllBytes();
            env = OrtEnvironment.getEnvironment(ai.onnxruntime.OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING, "xtreloc");
            session = env.createSession(bytes, new OrtSession.SessionOptions());
        } catch (IOException | ai.onnxruntime.OrtException | UnsatisfiedLinkError e) {
            throw new EqtException("Cannot start ONNX Runtime: " + e.getMessage(), e);
        }
    }

    /** Stations of the last {@link #pickAll} call that were not processed, with the reason. */
    public List<String> skipped() {
        return new ArrayList<>(skipped);
    }

    /**
     * Picks all stations of {@code traces}.
     *
     * @param progress called with the fraction of stations finished (may be {@code null})
     * @return all peaks above the thresholds, sorted by time
     */
    public List<Pick> pickAll(SacTimeSeries[] traces, DoubleConsumer progress) {
        skipped.clear();
        Map<String, Map<String, SacTimeSeries>> byStation = new LinkedHashMap<>();
        for (SacTimeSeries t : traces) {
            if (t == null) {
                continue;
            }
            String station = AicPicker.stationOf(t);
            SacComponentClassifier.Role role = SacComponentClassifier.classify(t.getHeader().getKcmpnm());
            String key = switch (role) {
                case VERTICAL -> "Z";
                case H1 -> "N";
                case H2 -> "E";
                default -> null;
            };
            if (key != null) {
                byStation.computeIfAbsent(station, k -> new LinkedHashMap<>()).putIfAbsent(key, t);
            }
        }
        List<Pick> out = new ArrayList<>();
        int done = 0;
        for (Map.Entry<String, Map<String, SacTimeSeries>> e : byStation.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException();
            }
            Map<String, SacTimeSeries> c = e.getValue();
            if (c.size() < 3) {
                skipped.add(e.getKey() + ": needs E, N and Z (has " + String.join("", c.keySet()) + ")");
            } else {
                try {
                    out.addAll(pickStation(e.getKey(), c.get("E"), c.get("N"), c.get("Z")));
                } catch (IllegalArgumentException ex) {
                    skipped.add(e.getKey() + ": " + ex.getMessage());
                }
            }
            done++;
            if (progress != null) {
                progress.accept(done / (double) byStation.size());
            }
        }
        out.sort((a, b) -> a.time().compareTo(b.time()));
        return out;
    }

    /** One pick per station and phase: the one with the highest probability. */
    public static List<Pick> best(List<Pick> picks) {
        Map<String, Pick> best = new LinkedHashMap<>();
        for (Pick p : picks) {
            best.merge(p.station() + "|" + p.phase(), p, (a, b) -> b.prob() > a.prob() ? b : a);
        }
        return new ArrayList<>(best.values());
    }

    // ------------------------------------------------------------------ one station

    private List<Pick> pickStation(String station, SacTimeSeries e, SacTimeSeries n, SacTimeSeries z) {
        SacTimeSeries[] trs = {e, n, z};
        float[][] x = new float[3][];
        long[] t0 = new long[3];
        for (int c = 0; c < 3; c++) {
            float[] d = trs[c].getY().clone();
            double dt = trs[c].getHeader().getDelta();
            demean(d);
            if (params.filter) {
                biquad(d, params.fLowHz, 1 / dt, true);
                biquad(d, Math.min(params.fHighHz, 0.45 / dt), 1 / dt, false);
            }
            double sr = 1 / dt;
            if (Math.abs(sr - FS) > 1e-3) {
                int q = (int) Math.round(sr / FS);
                if (q < 2 || Math.abs(sr / FS - q) > 1e-6) {
                    throw new IllegalArgumentException("cannot bring " + sr + " Hz to 100 Hz");
                }
                d = decimate(d, q);
            }
            x[c] = d;
            t0[c] = WaveProcessor.startEpochMillis(trs[c]);
        }
        long start = Math.max(t0[0], Math.max(t0[1], t0[2]));
        int len = Integer.MAX_VALUE;
        int[] off = new int[3];
        for (int c = 0; c < 3; c++) {
            off[c] = (int) Math.round((start - t0[c]) * FS / 1000.0);
            len = Math.min(len, x[c].length - off[c]);
        }
        if (len < 100) {
            throw new IllegalArgumentException("the three components do not overlap");
        }
        float[][] data = new float[3][len];
        for (int c = 0; c < 3; c++) {
            System.arraycopy(x[c], off[c], data[c], 0, len);
        }
        String vertical = AicPicker.trimCmp(z);
        String horizontal = AicPicker.trimCmp(e);
        List<Pick> picks = new ArrayList<>();
        int segLen = Math.max(1, (int) Math.ceil(params.segmentSec * FS / params.stride)) * params.stride;
        for (int segStart = 0; ; segStart += segLen) {
            int segEnd = Math.min(len, segStart + segLen + WINDOW);
            boolean last = segEnd == len;
            float[][] prob = infer(data, segStart, segEnd);
            int keepEnd = last ? segEnd - segStart : segLen;
            for (int k = 1; k <= 2; k++) {
                String phase = k == 1 ? "P" : "S";
                double thr = k == 1 ? params.thresholdP : params.thresholdS;
                for (int i : peaks(prob[k], thr, params.minDistSamples)) {
                    if (i >= keepEnd) {
                        continue;
                    }
                    double sample = segStart + refine(prob[k], i);
                    picks.add(new Pick(station, phase, k == 1 ? vertical : horizontal,
                        new Date(start + Math.round(sample * 1000.0 / FS)), prob[k][i]));
                }
            }
            if (last) {
                break;
            }
        }
        return picks;
    }

    /** Probabilities {detection, P, S} for {@code data[*][from, to)}, windows merged. */
    private float[][] infer(float[][] data, int from, int to) {
        int n = to - from;
        List<Integer> starts = new ArrayList<>();
        for (int s = 0; s + WINDOW <= n; s += params.stride) {
            starts.add(s);
        }
        if (starts.isEmpty() || starts.get(starts.size() - 1) + WINDOW < n) {
            starts.add(Math.max(0, n - WINDOW));
        }
        float[][] merged = new float[3][n];
        float[] count = new float[n];
        for (int b = 0; b < starts.size(); b += BATCH) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException();
            }
            int nw = Math.min(BATCH, starts.size() - b);
            float[] in = new float[nw * 3 * WINDOW];
            for (int w = 0; w < nw; w++) {
                fillWindow(in, w, data, from + starts.get(b + w), Math.min(WINDOW, n - starts.get(b + w)));
            }
            try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(in), new long[] {nw, 3, WINDOW});
                 OrtSession.Result r = session.run(Map.of("waveform", t))) {
                String[] names = {"detection", "p", "s"};
                for (int k = 0; k < 3; k++) {
                    float[][] o = (float[][]) r.get(names[k]).orElseThrow().getValue();
                    for (int w = 0; w < nw; w++) {
                        int s = starts.get(b + w);
                        int len = Math.min(WINDOW, n - s);
                        for (int i = 0; i < len; i++) {
                            float v = o[w][i];
                            if (params.mergeMax) {
                                merged[k][s + i] = Math.max(merged[k][s + i], v);
                            } else {
                                merged[k][s + i] += v;
                            }
                        }
                    }
                }
                if (!params.mergeMax) {
                    for (int w = 0; w < nw; w++) {
                        int s = starts.get(b + w);
                        int len = Math.min(WINDOW, n - s);
                        for (int i = 0; i < len; i++) {
                            count[s + i] += 1;
                        }
                    }
                }
            } catch (ai.onnxruntime.OrtException ex) {
                throw new EqtException("EQTransformer inference failed: " + ex.getMessage(), ex);
            }
        }
        if (!params.mergeMax) {
            for (int k = 0; k < 3; k++) {
                for (int i = 0; i < n; i++) {
                    if (count[i] > 0) {
                        merged[k][i] /= count[i];
                    }
                }
            }
        }
        return merged;
    }

    /** Copies one window to {@code in[w]}, demeaned and divided by one standard deviation over the 3 channels. */
    private static void fillWindow(float[] in, int w, float[][] data, int s, int len) {
        float[] mean = new float[3];
        for (int c = 0; c < 3; c++) {
            double m = 0;
            for (int i = 0; i < len; i++) {
                m += data[c][s + i];
            }
            mean[c] = (float) (m / len);
        }
        double sum2 = 0;
        for (int c = 0; c < 3; c++) {
            for (int i = 0; i < len; i++) {
                double v = data[c][s + i] - mean[c];
                sum2 += v * v;
            }
        }
        double std = Math.sqrt(sum2 / Math.max(1, 3L * len)) + 1e-10;
        for (int c = 0; c < 3; c++) {
            for (int i = 0; i < len; i++) {
                in[(w * 3 + c) * WINDOW + i] = (float) ((data[c][s + i] - mean[c]) / std);
            }
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (ai.onnxruntime.OrtException ignored) {
            // nothing left to release
        }
    }

    // ------------------------------------------------------------------ peaks

    /** Local maxima >= {@code thr}, at least {@code minDist} samples apart (the higher one wins). */
    static List<Integer> peaks(float[] p, double thr, int minDist) {
        List<Integer> cand = new ArrayList<>();
        for (int i = 1; i < p.length - 1; i++) {
            if (p[i] >= thr && p[i] > p[i - 1] && p[i] >= p[i + 1]) {
                cand.add(i);
            }
        }
        cand.sort((a, b) -> Float.compare(p[b], p[a]));
        List<Integer> keep = new ArrayList<>();
        for (int c : cand) {
            boolean ok = true;
            for (int k : keep) {
                if (Math.abs(k - c) < minDist) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                keep.add(c);
            }
        }
        keep.sort(Integer::compare);
        return keep;
    }

    /** Parabolic refinement of a peak position, in samples. */
    static double refine(float[] p, int i) {
        if (i <= 0 || i >= p.length - 1) {
            return i;
        }
        double a = p[i - 1];
        double b = p[i];
        double c = p[i + 1];
        double d = a - 2 * b + c;
        return d == 0 ? i : i + 0.5 * (a - c) / d;
    }

    // ------------------------------------------------------------------ DSP

    static void demean(float[] x) {
        double s = 0;
        for (float v : x) {
            s += v;
        }
        float m = (float) (s / Math.max(1, x.length));
        for (int i = 0; i < x.length; i++) {
            x[i] -= m;
        }
    }

    /** Second-order Butterworth biquad (RBJ), causal. */
    static void biquad(float[] x, double fc, double fs, boolean highPass) {
        double w0 = 2 * Math.PI * fc / fs;
        double cs = Math.cos(w0);
        double al = Math.sin(w0) / (2 * Math.sqrt(0.5));
        double b0;
        double b1;
        if (highPass) {
            b0 = (1 + cs) / 2;
            b1 = -(1 + cs);
        } else {
            b0 = (1 - cs) / 2;
            b1 = 1 - cs;
        }
        double b2 = b0;
        double a0 = 1 + al;
        double a1 = -2 * cs;
        double a2 = 1 - al;
        b0 /= a0;
        b1 /= a0;
        b2 /= a0;
        a1 /= a0;
        a2 /= a0;
        double x1 = 0;
        double x2 = 0;
        double y1 = 0;
        double y2 = 0;
        for (int i = 0; i < x.length; i++) {
            double xi = x[i];
            double y = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = xi;
            y2 = y1;
            y1 = y;
            x[i] = (float) y;
        }
    }

    /** Integer decimation with a Hamming-windowed sinc anti-alias FIR (zero phase). */
    static float[] decimate(float[] x, int q) {
        int half = 8 * q;
        double[] h = new double[2 * half + 1];
        double sum = 0;
        for (int k = -half; k <= half; k++) {
            double sinc = k == 0 ? 1.0 : Math.sin(Math.PI * k / q) / (Math.PI * k / q);
            h[k + half] = sinc / q * (0.54 + 0.46 * Math.cos(Math.PI * k / half));
            sum += h[k + half];
        }
        for (int i = 0; i < h.length; i++) {
            h[i] /= sum;
        }
        float[] y = new float[x.length / q];
        for (int i = 0; i < y.length; i++) {
            double acc = 0;
            int c = i * q;
            for (int k = -half; k <= half; k++) {
                int j = c + k;
                if (j >= 0 && j < x.length) {
                    acc += h[k + half] * x[j];
                }
            }
            y[i] = (float) acc;
        }
        return y;
    }
}
