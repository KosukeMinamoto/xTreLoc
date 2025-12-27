package com.treloc.xtreloc.app.gui.model;

public class Hypocenter {
    public final double lat;
    public final double lon;
    public final double depth;
    public final String time;
    public final double xerr; // x方向誤差 (km)
    public final double yerr; // y方向誤差 (km)
    public final double zerr; // z方向誤差 (km)
    public final double rms;  // RMS残差
    public final Integer clusterId; // クラスタ番号（オプショナル）
    public final String datFilePath; // datファイルの相対パス（オプショナル）
    public final String type; // タイプ（SYN, STD, GRD, ERRなど）

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
        this.time = time;
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
