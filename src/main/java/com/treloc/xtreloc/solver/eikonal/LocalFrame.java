package com.treloc.xtreloc.solver.eikonal;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

/**
 * Local azimuthal-equidistant frame around a center point: {@code x} = east, {@code y} = north (km).
 * Distances from the center are exact geodesic distances on the WGS84 ellipsoid.
 */
public final class LocalFrame {

    private final double lat0;
    private final double lon0;

    public LocalFrame(double lat0, double lon0) {
        this.lat0 = lat0;
        this.lon0 = lon0;
    }

    public double centerLat() { return lat0; }
    public double centerLon() { return lon0; }

    /** @return {@code {x_km, y_km}} */
    public double[] toXY(double lat, double lon) {
        GeodesicData g = Geodesic.WGS84.Inverse(lat0, lon0, lat, lon);
        double s = g.s12 / 1000.0;
        double az = Math.toRadians(g.azi1);
        return new double[] {s * Math.sin(az), s * Math.cos(az)};
    }

    /** @return {@code {lat, lon}} */
    public double[] toLatLon(double xKm, double yKm) {
        double s = Math.hypot(xKm, yKm);
        if (s == 0.0) {
            return new double[] {lat0, lon0};
        }
        double az = Math.toDegrees(Math.atan2(xKm, yKm));
        GeodesicData g = Geodesic.WGS84.Direct(lat0, lon0, az, s * 1000.0);
        return new double[] {g.lat2, g.lon2};
    }
}
