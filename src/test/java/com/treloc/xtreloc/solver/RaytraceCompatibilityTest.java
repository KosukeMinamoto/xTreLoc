package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Sanity checks for {@link Raytrace1D} on bundled {@code prem.nd}.
 * TauP cross-check: {@link RaytraceVsTauPTest} (TauP is a {@code test}-scoped dependency).
 */
public class RaytraceCompatibilityTest {

    @Test
    public void raytrace1dLoadsPremNdAndReturnsFinitePositiveTravelTime() throws Exception {
        Raytrace1D rt = Raytrace1D.load("prem.nd");
        double tt = rt.travelTimeSeconds(100.0, 10.0, 0.1);
        assertTrue("travel time finite positive", Double.isFinite(tt) && tt > 0.0);
        Raytrace1D.RaySolution sol = rt.solveFastestRay(100.0, 10.0, 0.1);
        assertTrue(Double.isFinite(sol.takeoffAngleRad));
        assertTrue(Double.isFinite(sol.incidentAngleRad));
    }
}
