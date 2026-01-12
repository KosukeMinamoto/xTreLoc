package com.treloc.xtreloc.app.gui.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;

import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;

import edu.sc.seis.seisFile.sac.SacHeader;
import edu.sc.seis.seisFile.sac.SacTimeSeries;

/**
 * Utility class for processing SAC waveform data.
 * Provides methods for reading SAC files, applying filters, and converting to time series.
 * 
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class WaveProcessor {
    
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

    /**
     * Gets the start time from a SAC file header.
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
        return time;
    }

    /**
     * Converts a SAC time series to a JFreeChart TimeSeries.
     * 
     * @param sac the SacTimeSeries object
     * @return a TimeSeries for JFreeChart
     */
    public static TimeSeries getTimeSeries(SacTimeSeries sac) {
        SacHeader hdr = sac.getHeader();
        float[] data = sac.getY();
        int npts = hdr.getNpts();
        float delta = hdr.getDelta();

        TimeSeries timeSeries = new TimeSeries("time");
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDateTime startTime = getStarttime(sac);
        for (int i = 0; i < npts; i++) {
            LocalDateTime nowDT = startTime.plusNanos((long) (i * 1e9 * delta));
            Date nowDate = Date.from(nowDT.atZone(zoneId).toInstant());
            timeSeries.add(new Millisecond(nowDate), data[i]);
        }
        return timeSeries;
    }

    /**
     * Removes linear trend from the SAC data.
     * 
     * @param sac the SacTimeSeries object
     * @return the detrended SacTimeSeries
     */
    public static SacTimeSeries detrend(SacTimeSeries sac) {
        try {
            SacHeader hdr = sac.getHeader();
            int npts = hdr.getNpts();
            float[] data = sac.getY();
            double[] trend = new double[npts];
            float[] detrended = new float[npts];

            List<WeightedObservedPoint> points = new ArrayList<>();
            for (int i = 0; i < npts; i++) {
                points.add(new WeightedObservedPoint(1.0, i, data[i]));
            }

            PolynomialCurveFitter fitter = PolynomialCurveFitter.create(1);
            double[] coefficients = fitter.fit(points);
            
            for (int i = 0; i < npts; i++) {
                trend[i] = coefficients[0] + coefficients[1] * i;
                detrended[i] = (float) (data[i] - trend[i]);
            }
            sac.setY(detrended);
        } catch (Exception e) {
            System.err.println("Error in detrend: " + e.getMessage());
            e.printStackTrace();
        }
        return sac;
    }

    /**
     * Applies a simple band-pass filter to the SAC data using a moving average approach.
     * Note: This is a simplified implementation. For production use, consider using
     * a proper signal processing library.
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
            float delta = hdr.getDelta();
            float[] dataIni = sac.getY();
            float[] dataOut = new float[npts];
            
            // Simple high-pass filter (remove DC and low frequencies)
            // Calculate moving average for low-frequency removal
            int windowSize = (int) Math.max(1, Math.round(1.0f / (freqmin * delta)));
            if (windowSize > npts / 2) {
                windowSize = npts / 2;
            }
            
            // High-pass: subtract moving average
            for (int i = 0; i < npts; i++) {
                int start = Math.max(0, i - windowSize / 2);
                int end = Math.min(npts, i + windowSize / 2);
                float sum = 0;
                for (int j = start; j < end; j++) {
                    sum += dataIni[j];
                }
                float avg = sum / (end - start);
                dataOut[i] = dataIni[i] - avg;
            }
            
            // Low-pass: simple moving average for high-frequency removal
            float nyquist = 0.5f / delta;
            if (freqmax < nyquist) {
                int lowPassWindow = (int) Math.max(1, Math.round(nyquist / (freqmax * delta)));
                if (lowPassWindow > 1) {
                    float[] temp = new float[npts];
                    System.arraycopy(dataOut, 0, temp, 0, npts);
                    for (int i = 0; i < npts; i++) {
                        int start = Math.max(0, i - lowPassWindow / 2);
                        int end = Math.min(npts, i + lowPassWindow / 2);
                        float sum = 0;
                        for (int j = start; j < end; j++) {
                            sum += temp[j];
                        }
                        dataOut[i] = sum / (end - start);
                    }
                }
            }
            
            sac.setY(dataOut);
        } catch (Exception e) {
            System.err.println("Error in bandPassFilter: " + e.getMessage());
            e.printStackTrace();
            // Return original data if filtering fails
        }
        return sac;
    }
}
