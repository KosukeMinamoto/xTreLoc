package com.treloc.xtreloc.app.gui.model;

import com.treloc.xtreloc.util.TimeFormatConverter;

public class Hypocenter {
    public final double lat;
    public final double lon;
    public final double depth;
    /** Origin time in ISO-8601 {@code yyyy-MM-dd'T'HH:mm:ss} when parseable; otherwise the raw string. */
    public final String time;
    public final double xerr; // x error (km)
    public final double yerr; // y error (km)
    public final double zerr; // z error (km)
    public final double rms;  // RMS residual
    public final Integer clusterId; // cluster number (optional)
    public final String datFilePath; // relative path of the dat file (optional)
    public final String type; // type (SYN, LMO, GRD, ERR etc.)

    public Hypocenter(String time, double lat, double lon, double depth) {
        this(time, lat, lon, depth, 0.0, 0.0, 0.0, 0.0, null, null, null);
    }

    public Hypocenter(String time, double lat, double lon, double depth, 
                     double xerr, double yerr, double zerr, double rms) {
        this(time, lat, lon, depth, xerr, yerr, zerr, rms, null, null, null);
    }

    public Hypocenter(String time, double lat, double lon, double depth, 
                     double xerr, double yerr, double zerr, double rms, Integer clusterId) {
        this(time, lat, lon, depth, xerr, yerr, zerr, rms, clusterId, null, null);
    }

    public Hypocenter(String time, double lat, double lon, double depth, 
                     double xerr, double yerr, double zerr, double rms, Integer clusterId, String datFilePath) {
        this(time, lat, lon, depth, xerr, yerr, zerr, rms, clusterId, datFilePath, null);
    }

    public Hypocenter(String time, double lat, double lon, double depth, 
                     double xerr, double yerr, double zerr, double rms, Integer clusterId, String datFilePath, String type) {
        // Always store calendar order yyyy-MM-dd (from yymmdd.hhmmss filenames after MCMC, etc.).
        this.time = TimeFormatConverter.toISO8601(time);
        this.lat = lat;
        this.lon = lon;
        this.depth = depth;
        this.xerr = xerr;
        this.yerr = yerr;
        this.zerr = zerr;
        this.rms = rms;
        this.clusterId = clusterId;
        this.datFilePath = datFilePath;
        this.type = type;
    }
}
