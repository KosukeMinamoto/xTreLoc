package com.treloc.xtreloc.app.gui.service;

/**
 * Discrete cross-correlation compatible with SciPy / NumPy conventions used in
 * Python reference scripts ({@code scipy.signal.correlate(..., mode="same")} and
 * {@code correlation_lags(..., mode="same")} for equal-length inputs).
 */
public final class ScipySignalCompat {

    /** FFT beats the direct O(n²) loop from about this length. */
    static final int FFT_MIN = 64;

    private ScipySignalCompat() {}

    /**
     * NumPy {@code correlate(a, b, mode="full")} for 1-D real arrays: convolution of {@code a}
     * with time-reversed {@code b}.
     */
    public static double[] numpyCorrelateFull(double[] a, double[] b) {
        int n = a.length;
        int m = b.length;
        int outLen = n + m - 1;
        double[] c = new double[outLen];
        for (int k = 0; k < outLen; k++) {
            double s = 0.0;
            int i0 = Math.max(0, k - m + 1);
            int i1 = Math.min(n - 1, k);
            int jb = m - 1 - k + i0;
            for (int i = i0; i <= i1; i++, jb++) {
                s += a[i] * b[jb];
            }
            c[k] = s;
        }
        return c;
    }

    /**
     * NumPy {@code correlate(a, b, mode="same")} when {@code a} is the first argument (output length = len(a)).
     */
    public static double[] numpyCorrelateSame(double[] a, double[] b) {
        if (a.length != b.length) {
            double[] full = numpyCorrelateFull(a, b);
            int outLen = a.length;
            int start = (full.length - outLen) / 2;
            double[] dest = new double[outLen];
            System.arraycopy(full, start, dest, 0, outLen);
            return dest;
        }
        double[] dest = new double[a.length];
        correlateSameEqual(a, b, dest);
        return dest;
    }

    /**
     * Equal-length {@code mode="same"} correlate into {@code dest} (length {@code a.length}).
     */
    public static void correlateSameEqual(double[] a, double[] b, double[] dest) {
        int n = a.length;
        if (b.length != n || dest.length != n) {
            throw new IllegalArgumentException("equal lengths required");
        }
        if (n >= FFT_MIN) {
            fftCorrelateSame(a, b, dest);
        } else {
            directCorrelateSame(a, b, dest);
        }
    }

    /** Writes {@code mode="same"} into {@code dest} and returns the index of the maximum. */
    public static int peakCorrelateSameEqual(double[] a, double[] b, double[] dest) {
        correlateSameEqual(a, b, dest);
        return argMax(dest);
    }

    static void directCorrelateSame(double[] a, double[] b, double[] dest) {
        int n = a.length;
        int start = (n - 1) / 2;
        for (int t = 0; t < n; t++) {
            int k = start + t;
            int i0 = Math.max(0, k - n + 1);
            int i1 = Math.min(n - 1, k);
            int jb = n - 1 - k + i0;
            double s = 0.0;
            for (int i = i0; i <= i1; i++, jb++) {
                s += a[i] * b[jb];
            }
            dest[t] = s;
        }
    }

    /**
     * SciPy {@code correlation_lags(in1_len, in2_len, mode="same")} for equal lengths ({@code n == n}).
     */
    public static int[] correlationLagsSameEqual(int n) {
        int fullLen = 2 * n - 1;
        int[] full = new int[fullLen];
        for (int k = 0; k < fullLen; k++) {
            full[k] = -n + 1 + k;
        }
        int mid = fullLen / 2;
        int lagBound = n / 2;
        int from;
        int to;
        if ((n & 1) == 0) {
            from = mid - lagBound;
            to = mid + lagBound;
        } else {
            from = mid - lagBound;
            to = mid + lagBound + 1;
        }
        int[] out = new int[to - from];
        System.arraycopy(full, from, out, 0, out.length);
        return out;
    }

    public static int argMax(double[] a) {
        int ix = 0;
        for (int i = 1; i < a.length; i++) {
            if (a[i] > a[ix]) {
                ix = i;
            }
        }
        return ix;
    }

    private static final class FftWork {
        final int L;
        final double[] ar;
        final double[] ai;
        final double[] br;
        final double[] bi;

        FftWork(int L) {
            this.L = L;
            ar = new double[L];
            ai = new double[L];
            br = new double[L];
            bi = new double[L];
        }
    }

    private static final ThreadLocal<FftWork> FFT_WORK = new ThreadLocal<>();

    private static FftWork fftWork(int L) {
        FftWork w = FFT_WORK.get();
        if (w == null || w.L != L) {
            w = new FftWork(L);
            FFT_WORK.set(w);
        }
        return w;
    }

    private static int nextPow2(int n) {
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }

    /** Linear conv(a, reverse(b)) via zero-padded FFT, then the NumPy {@code same} slice. */
    static void fftCorrelateSame(double[] a, double[] b, double[] dest) {
        int n = a.length;
        int L = nextPow2(2 * n - 1);
        FftWork w = fftWork(L);
        java.util.Arrays.fill(w.ar, 0.0);
        java.util.Arrays.fill(w.ai, 0.0);
        java.util.Arrays.fill(w.br, 0.0);
        java.util.Arrays.fill(w.bi, 0.0);
        System.arraycopy(a, 0, w.ar, 0, n);
        for (int i = 0; i < n; i++) {
            w.br[i] = b[n - 1 - i];
        }
        fft(w.ar, w.ai, false);
        fft(w.br, w.bi, false);
        for (int i = 0; i < L; i++) {
            double ar = w.ar[i];
            double ai = w.ai[i];
            double br = w.br[i];
            double bi = w.bi[i];
            w.ar[i] = ar * br - ai * bi;
            w.ai[i] = ar * bi + ai * br;
        }
        fft(w.ar, w.ai, true);
        int start = (n - 1) / 2;
        System.arraycopy(w.ar, start, dest, 0, n);
    }

    /** In-place radix-2 Cooley–Tukey. {@code inverse} applies 1/n. */
    static void fft(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double tr = re[i];
                re[i] = re[j];
                re[j] = tr;
                double ti = im[i];
                im[i] = im[j];
                im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2.0 * Math.PI / len * (inverse ? 1.0 : -1.0);
            double wlenRe = Math.cos(ang);
            double wlenIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double wr = 1.0;
                double wi = 0.0;
                int half = len >> 1;
                for (int j = 0; j < half; j++) {
                    int u = i + j;
                    int v = u + half;
                    double vr = re[v] * wr - im[v] * wi;
                    double vi = re[v] * wi + im[v] * wr;
                    re[v] = re[u] - vr;
                    im[v] = im[u] - vi;
                    re[u] += vr;
                    im[u] += vi;
                    double nwr = wr * wlenRe - wi * wlenIm;
                    wi = wr * wlenIm + wi * wlenRe;
                    wr = nwr;
                }
            }
        }
        if (inverse) {
            double s = 1.0 / n;
            for (int i = 0; i < n; i++) {
                re[i] *= s;
                im[i] *= s;
            }
        }
    }
}
