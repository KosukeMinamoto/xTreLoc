package com.treloc.xtreloc.solver.grid;

/**
 * Geographic transform stored in a NonLinLoc grid header ({@code TRANSFORM SIMPLE} or {@code LAMBERT}).
 * Working coordinates are kilometres, x east and y north (after any clockwise rotation).
 */
public interface NllGeographicTransform {

    double lat0();

    double lon0();

    /** Length (km) of one degree of longitude at the origin. */
    double kmPerDegLon();

    /** Length (km) of one degree of latitude at the origin. */
    double kmPerDegLat();

    /** @return {@code {x_km, y_km}} */
    double[] toXY(double lat, double lon);

    /** @return {@code {lat, lon}} */
    double[] toLatLon(double xKm, double yKm);

    String headerLine();
}
