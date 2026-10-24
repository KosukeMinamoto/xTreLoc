package com.treloc.xtreloc.app.gui.service;

import edu.sc.seis.seisFile.sac.SacHeader;
import edu.sc.seis.seisFile.sac.SacTimeSeries;

/** Builds in-memory 100 Hz SAC traces for tests. */
public final class TestSac {

    private TestSac() {
    }

    /** Trace starting at 2007-001 00:00:00. */
    public static SacTimeSeries make(String station, String component, float[] y) {
        return make(station, component, y, 2007, 1, 0, 0);
    }

    public static SacTimeSeries make(String station, String component, float[] y,
                                     int year, int jday, int hour, int min) {
        SacHeader h = new SacHeader();
        h.setNpts(y.length);
        h.setDelta(0.01f);
        h.setB(0f);
        h.setKstnm(station);
        h.setKcmpnm(component);
        h.setNzyear(year);
        h.setNzjday(jday);
        h.setNzhour(hour);
        h.setNzmin(min);
        h.setNzsec(0);
        h.setNzmsec(0);
        return new SacTimeSeries(h, y);
    }
}
