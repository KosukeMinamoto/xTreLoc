package com.treloc.xtreloc.solver.grid;

import java.util.Locale;

/**
 * NonLinLoc {@code TRANSFORM SIMPLE} (rotation 0): {@code x = (lon - lon0) cos(lat0) K}, {@code y = (lat - lat0) K}
 * with {@code K = 111.19492664455873} km per degree; {@code x} east, {@code y} north. Immutable.
 */
public final class NllSimpleTransform implements NllGeographicTransform {

    public static final double KM_PER_DEG = 111.19492664455873;

    private final double lat0;
    private final double lon0;
    private final double kmPerDegLon;

    public NllSimpleTransform(double lat0, double lon0) {
        if (Math.abs(lat0) >= 89.0) {
            throw new IllegalArgumentException("Latitude of the grid origin too close to the pole: " + lat0);
        }
        this.lat0 = lat0;
        this.lon0 = lon0;
        this.kmPerDegLon = KM_PER_DEG * Math.cos(Math.toRadians(lat0));
    }

    @Override
    public double lat0() { return lat0; }
    @Override
    public double lon0() { return lon0; }
    /** Length (km) of one degree of longitude on the grid (constant, at the origin latitude). */
    @Override
    public double kmPerDegLon() { return kmPerDegLon; }
    @Override
    public double kmPerDegLat() { return KM_PER_DEG; }

    /** @return {@code {x_km, y_km}} */
    @Override
    public double[] toXY(double lat, double lon) {
        double dlon = lon - lon0;
        while (dlon > 180.0) {
            dlon -= 360.0;
        }
        while (dlon < -180.0) {
            dlon += 360.0;
        }
        return new double[] {dlon * kmPerDegLon, (lat - lat0) * KM_PER_DEG};
    }

    /** @return {@code {lat, lon}} */
    @Override
    public double[] toLatLon(double xKm, double yKm) {
        return new double[] {lat0 + yKm / KM_PER_DEG, lon0 + xKm / kmPerDegLon};
    }

    /** Header line of an NLL grid file. */
    @Override
    public String headerLine() {
        return String.format(Locale.ROOT, "TRANSFORM  SIMPLE  LatOrig  %.6f  LongOrig  %.6f  RotAngle  0.000000", lat0, lon0);
    }
}
