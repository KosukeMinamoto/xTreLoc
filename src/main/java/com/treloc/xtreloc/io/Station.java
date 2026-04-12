package com.treloc.xtreloc.io;

public class Station {

    private final String code;
    private final double lat;
    private final double lon;
    private final double dep;
    private final double pc;
    private final double sc;

    public Station(String code, double lat, double lon,
            double dep, double pc, double sc) {

        if (Math.abs(lat) > 90) {
            throw new IllegalArgumentException("Invalid latitude: " + lat);
        }
        if (Math.abs(lon) > 180) {
            throw new IllegalArgumentException("Invalid longitude: " + lon);
        }

        this.code = code;
        this.lat = lat;
        this.lon = lon;
        this.dep = dep;
        this.pc = pc;
        this.sc = sc;
    }

    public String getCode() {
        return code;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public double getDep() {
        return dep;
    }

    public double getPc() {
        return pc;
    }

    public double getSc() {
        return sc;
    }
}
