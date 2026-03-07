package com.treloc.xtreloc.solver;

import org.apache.commons.math3.ml.distance.DistanceMeasure;

/**
 * Haversine distance calculator for geographical coordinates.
 * Computes the great-circle distance between two points on Earth.
 * 
 * @author K.M.
 * @version 0.1
 * @since 2025-02-22
 */
public class HaversineDistance implements DistanceMeasure {
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Computes the Haversine distance between two points.
     * 
     * @param a the first point [lat, lon] in degrees
     * @param b the second point [lat, lon] in degrees
     * @return the Haversine distance in kilometers
     */
    @Override
    public double compute(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[0]);
        double lon1 = Math.toRadians(a[1]);
        double lat2 = Math.toRadians(b[0]);
        double lon2 = Math.toRadians(b[1]);

        double dlat = lat2 - lat1;
        double dlon = lon2 - lon1;

        double haversine = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                           Math.cos(lat1) * Math.cos(lat2) *
                           Math.sin(dlon / 2) * Math.sin(dlon / 2);

        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(haversine));
    }
}

