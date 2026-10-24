package com.treloc.xtreloc.app.gui.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.sc.seis.seisFile.sac.SacHeader;
import edu.sc.seis.seisFile.sac.SacTimeSeries;
import uk.me.berndporr.iirj.Butterworth;

/**
 * Utility class for processing SAC waveform data.
 * Provides methods for reading SAC files, applying filters, and converting to time series.
 * <p>
 * Detrend matches ObsPy {@code stream.detrend("linear")} (line through first and last sample).
 * Band-pass / high-pass / low-pass use Butterworth IIR + forward–reverse filtering (filtfilt),
 * aligned with ObsPy {@code zerophase=True} band-pass processing in spirit (SciPy-style).
 * </p>
 *
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class WaveProcessor {

    private static final Logger LOG = Logger.getLogger(WaveProcessor.class.getName());

    /** Butterworth order for band-pass (ObsPy default {@code corners=4}). */
    private static final int BP_ORDER = 4;
    /** Butterworth order for dedicated high-pass / low-pass (ObsPy default {@code corners=4}). */
    private static final int HP_LP_ORDER = 4;

    /**
     * Reads a SAC file from the specified path.
     *
     * @param sacFilePath the path to the SAC file
     * @return the SacTimeSeries object
     * @throws Exception if there is an error reading the file
     */
    public static SacTimeSeries read(String sacFilePath) throws Exception {
        return SacTimeSeries.read(sacFilePath);
    }

    /** Header only (632 bytes). Safe for day-long continuous SAC. */
    public static SacHeader readHeader(String sacFilePath) throws IOException {
        return new SacHeader(new java.io.File(sacFilePath));
    }

    /**
     * Gets the first-sample time from a SAC file header (NZ reference + {@code B} seconds).
     *
     * @param sac the SacTimeSeries object
     * @return the start time as LocalDateTime
     */
    public static LocalDateTime getStarttime(SacTimeSeries sac) {
        SacHeader hdr = sac.getHeader();
        LocalDateTime time = LocalDateTime.of(
            hdr.getNzyear(),
            1,
            1,
            hdr.getNzhour(),
            hdr.getNzmin(),
            hdr.getNzsec(),
            hdr.getNzmsec() * 1_000_000); // Convert milliseconds to nanoseconds
        time = time.plusDays(hdr.getNzjday() - 1);
        float b = hdr.getB();
        if (!Float.isNaN(b) && Math.abs(b + 12345f) > 1f) {
            time = time.plusNanos(Math.round((double) b * 1_000_000_000.0));
        }
        return time;
    }

    /** Epoch milliseconds of the first sample ({@link #getStarttime} in the system zone). */
    public static long startEpochMillis(SacTimeSeries sac) {
        return getStarttime(sac).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Linear detrend matching ObsPy {@code detrend("linear")}: subtract the straight line through
     * the first and last samples (not a least-squares fit over all points).
     *
     * @param sac the SacTimeSeries object
     * @return the detrended SacTimeSeries
     */
    public static SacTimeSeries detrend(SacTimeSeries sac) {
        try {
            SacHeader hdr = sac.getHeader();
            int npts = hdr.getNpts();
            float[] data = sac.getY();
            if (npts < 2) {
                return sac;
            }
            double y0 = data[0];
            double y1 = data[npts - 1];
            double slope = (y1 - y0) / (npts - 1);
            float[] out = new float[npts];
            for (int i = 0; i < npts; i++) {
                double trend = y0 + slope * i;
                out[i] = (float) (data[i] - trend);
            }
            sac.setY(out);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in detrend", e);
        }
        return sac;
    }

    /**
     * Applies frequency filtering only for non-null corners: both null → no change;
     * low only → high-pass; high only → low-pass; both → band-pass (same as {@link #bandPassFilter}).
     */
    public static SacTimeSeries applyOptionalFrequencyFilter(SacTimeSeries sac, Float freqMinHz, Float freqMaxHz) {
        if (freqMinHz == null && freqMaxHz == null) {
            return sac;
        }
        if (freqMinHz != null && freqMaxHz != null) {
            return bandPassFilter(sac, freqMinHz, freqMaxHz);
        }
        if (freqMinHz != null) {
            return highPassFilter(sac, freqMinHz);
        }
        return lowPassFilter(sac, java.util.Objects.requireNonNull(freqMaxHz, "freqMaxHz"));
    }

    /**
     * High-pass: Butterworth + filtfilt (zero-phase), ObsPy-style {@code corners=4}.
     */
    public static SacTimeSeries highPassFilter(SacTimeSeries sac, float freqmin) {
        try {
            SacHeader hdr = sac.getHeader();
            int npts = hdr.getNpts();
            if (npts < 4) {
                return sac;
            }
            float delta = hdr.getDelta();
            double fs = 1.0 / delta;
            float[] dataIni = sac.getY();
            double[] xd = floatsToDoubles(dataIni);
            double[] yd = filtfiltHighPass(fs, freqmin, xd);
            doublesToFloats(yd, dataIni);
            sac.setY(dataIni);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in highPassFilter", e);
        }
        return sac;
    }

    /**
     * Low-pass: Butterworth + filtfilt (zero-phase), ObsPy-style {@code corners=4}.
     */
    public static SacTimeSeries lowPassFilter(SacTimeSeries sac, float freqmax) {
        try {
            SacHeader hdr = sac.getHeader();
            int npts = hdr.getNpts();
            if (npts < 4) {
                return sac;
            }
            float delta = hdr.getDelta();
            double fs = 1.0 / delta;
            float nyquist = (float) (0.5 * fs);
            if (freqmax >= nyquist) {
                return sac;
            }
            float[] dataIni = sac.getY();
            double[] xd = floatsToDoubles(dataIni);
            double[] yd = filtfiltLowPass(fs, freqmax, xd);
            doublesToFloats(yd, dataIni);
            sac.setY(dataIni);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in lowPassFilter", e);
        }
        return sac;
    }

    /**
     * Band-pass: Butterworth + filtfilt (zero-phase), ObsPy-style {@code corners=4}.
     *
     * @param sac the SacTimeSeries object
     * @param freqmin minimum frequency in Hz
     * @param freqmax maximum frequency in Hz
     * @return the filtered SacTimeSeries
     */
    public static SacTimeSeries bandPassFilter(SacTimeSeries sac, float freqmin, float freqmax) {
        try {
            SacHeader hdr = sac.getHeader();
            int npts = hdr.getNpts();
            if (npts < 8) {
                return sac;
            }
            float delta = hdr.getDelta();
            double fs = 1.0 / delta;
            double nyq = 0.5 * fs;
            if (freqmax >= nyq) {
                return highPassFilter(sac, freqmin);
            }
            if (freqmin <= 0) {
                return lowPassFilter(sac, freqmax);
            }
            if (freqmin >= freqmax) {
                return sac;
            }
            float[] dataIni = sac.getY();
            double[] xd = floatsToDoubles(dataIni);
            double center = 0.5 * (freqmin + freqmax);
            double width = Math.max(freqmax - freqmin, 1e-6);
            double[] yd = filtfiltBandPass(fs, center, width, xd);
            doublesToFloats(yd, dataIni);
            sac.setY(dataIni);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error in bandPassFilter", e);
        }
        return sac;
    }

    private static double[] floatsToDoubles(float[] f) {
        double[] d = new double[f.length];
        for (int i = 0; i < f.length; i++) {
            d[i] = f[i];
        }
        return d;
    }

    private static void doublesToFloats(double[] d, float[] f) {
        for (int i = 0; i < d.length && i < f.length; i++) {
            f[i] = (float) d[i];
        }
    }

    private static void reverseInPlace(double[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            double t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    private static double[] forwardFilter(Butterworth bw, double[] x) {
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = bw.filter(x[i]);
        }
        return y;
    }

    private static double[] filtfiltHighPass(double sampleRate, double cutoffHz, double[] x) {
        if (cutoffHz <= 0 || sampleRate <= 0 || x.length == 0) {
            return x.clone();
        }
        double nyq = sampleRate / 2.0;
        if (cutoffHz >= nyq * 0.999) {
            return x.clone();
        }
        Butterworth b1 = new Butterworth();
        b1.highPass(HP_LP_ORDER, sampleRate, cutoffHz);
        double[] y = forwardFilter(b1, x);
        reverseInPlace(y);
        Butterworth b2 = new Butterworth();
        b2.highPass(HP_LP_ORDER, sampleRate, cutoffHz);
        y = forwardFilter(b2, y);
        reverseInPlace(y);
        return y;
    }

    private static double[] filtfiltLowPass(double sampleRate, double cutoffHz, double[] x) {
        if (cutoffHz <= 0 || sampleRate <= 0 || x.length == 0) {
            return x.clone();
        }
        double nyq = sampleRate / 2.0;
        if (cutoffHz >= nyq * 0.999) {
            return x.clone();
        }
        Butterworth b1 = new Butterworth();
        b1.lowPass(HP_LP_ORDER, sampleRate, cutoffHz);
        double[] y = forwardFilter(b1, x);
        reverseInPlace(y);
        Butterworth b2 = new Butterworth();
        b2.lowPass(HP_LP_ORDER, sampleRate, cutoffHz);
        y = forwardFilter(b2, y);
        reverseInPlace(y);
        return y;
    }

    private static double[] filtfiltBandPass(double sampleRate, double centerHz, double widthHz, double[] x) {
        if (sampleRate <= 0 || x.length == 0) {
            return x.clone();
        }
        double nyq = sampleRate / 2.0;
        double half = 0.5 * widthHz;
        double lo = centerHz - half;
        double hi = centerHz + half;
        if (lo <= 0) {
            return filtfiltLowPass(sampleRate, Math.min(hi, nyq * 0.999), x);
        }
        if (hi >= nyq * 0.999) {
            return filtfiltHighPass(sampleRate, lo, x);
        }
        Butterworth b1 = new Butterworth();
        b1.bandPass(BP_ORDER, sampleRate, centerHz, widthHz);
        double[] y = forwardFilter(b1, x);
        reverseInPlace(y);
        Butterworth b2 = new Butterworth();
        b2.bandPass(BP_ORDER, sampleRate, centerHz, widthHz);
        y = forwardFilter(b2, y);
        reverseInPlace(y);
        return y;
    }
}
