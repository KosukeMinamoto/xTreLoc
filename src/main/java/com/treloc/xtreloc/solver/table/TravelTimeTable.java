package com.treloc.xtreloc.solver.table;

/**
 * Pre-computed travel-time (and optionally take-off angle) table used by the solvers instead of an on-the-fly
 * velocity model. A table is valid only inside its range (for example epicentral distance and depth limits);
 * outside the range the methods return {@link Double#NaN} and {@link #containsSource} is {@code false}, which the
 * solvers treat as an outlier (the event is flagged {@code ERR}).
 *
 * <p>Implementation: {@link com.treloc.xtreloc.solver.grid.GridTravelTimeTable} (NonLinLoc {@code TIME} grids).</p>
 */
public interface TravelTimeTable {

    enum Phase { P, S }

    /** True if a source at this position lies inside the validity range of the table. */
    boolean containsSource(double lat, double lon, double depthKm);

    /**
     * Travel time in seconds, or {@link Double#NaN} if the source / receiver geometry is outside the table range.
     * Depths are in km below the surface (positive down).
     */
    double travelTime(Phase phase, double srcLat, double srcLon, double srcDepKm,
                      double stnLat, double stnLon, double stnDepKm);

    /**
     * Take-off angle at the source measured from the downward vertical (radians), or {@link Double#NaN} if the
     * table has no take-off angles or the geometry is out of range.
     */
    double takeoffAngleRad(Phase phase, double srcLat, double srcLon, double srcDepKm,
                           double stnLat, double stnLon, double stnDepKm);

    /**
     * Partial derivatives of the travel time with respect to the source position, when the table can provide them
     * directly (for example from the gradient of a 3-D grid): {@code {dT/dlon [s/deg], dT/dlat [s/deg],
     * dT/ddepth [s/km]}}, or {@code null} if not available (the caller then uses the take-off angle or finite
     * differences).
     */
    default double[] sourceDerivatives(Phase phase, double srcLat, double srcLon, double srcDepKm,
                                       double stnLat, double stnLon, double stnDepKm) {
        return null;
    }

    /** Velocity at the source (km/s) or {@link Double#NaN} if unknown. */
    double velocityAtSource(Phase phase, double lat, double lon, double depthKm);

    /** One-line description for logs. */
    String describe();
}
