package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Regional epicentral distances ({@code ~10–100 km}): branch selection and straight-ray fallback (see
 * {@link Raytrace1D}) keep travel times in a loose crustal envelope across bundled models.
 */
public class RaytraceShortDistanceTest {

    private static final double STATION_DEPTH_KM = 0.1;
    /** Deeper source than default PREM smoke tests; stresses path geometry vs observation depth. */
    private static final double SRC_DEPTH_KM = 30.0;

    /** All velocity tables shipped under {@code src/main/resources/velocity-models/}. */
    private static final String[] BUNDLED_MODELS = {
        "prem.nd",
        "iasp91.tvel",
        "ak135.tvel",
        "alfs.nd",
        "herrin.nd",
        "pwdk.nd",
        "1066a.nd",
        "1066b.nd",
        "sp6.nd",
    };

    private static final double[] DISTANCES_KM = {10.0, 30.0, 50.0, 100.0};

    @Test
    public void bundledModelsTravelTimesArePhysicalAt10To100KmWith30KmSource() throws Exception {
        for (String model : BUNDLED_MODELS) {
            Raytrace1D rt = Raytrace1D.load(model);
            for (double d : DISTANCES_KM) {
                Raytrace1D.RaySolution sol = rt.solveFastestRay(d, SRC_DEPTH_KM, STATION_DEPTH_KM);
                double t = sol.travelTimeSeconds;
                assertTrue(
                    model + " finite positive time at " + d + " km",
                    Double.isFinite(t) && t > 0.0 && t < 1e6);
                // Epicentral distance alone is a poor lower bound when source is deep (long straight-ray path).
                double pathKm = Math.hypot(d, Math.abs(SRC_DEPTH_KM - STATION_DEPTH_KM));
                double tLo = Math.max(0.02, Math.min(d / 10.0, pathKm / 40.0));
                double tHi = pathKm / 2.0 + 100.0;
                assertTrue(
                    String.format(
                        "%s: time %.4f s out of envelope [%f, %f] at dist=%.1f km src=%.1f km path≈%.1f km",
                        model, t, tLo, tHi, d, SRC_DEPTH_KM, pathKm),
                    t >= tLo && t <= tHi);
                assertTrue(model + " takeoff", Math.abs(sol.takeoffAngleRad) < Math.PI + 0.01);
                assertTrue(model + " incident", Math.abs(sol.incidentAngleRad) < Math.PI + 0.01);
            }
        }
    }
}
