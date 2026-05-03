package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Assume;
import org.junit.Test;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModel;
import edu.sc.seis.TauP.TauModelLoader;
import edu.sc.seis.TauP.TauP_Time;

/**
 * Regression against TauP on bundled {@code velocity-models/prem.nd} (same file TauP's loader accepts).
 * <p>
 * <b>Important:</b> {@link Raytrace1D} picks a fastest branch in a <em>flat</em> layered model; TauP uses
 * spherical tau-p and typically reports the <em>first</em> S arrival.
 * Travel times therefore need not match closely; this test checks finite behaviour and a loose time ratio envelope,
 * plus optional strict tolerances when {@code -Dxtreloc.strictTauPCompare=true}.
 * <p>
 * Skip entirely with {@code -Dxtreloc.skipTauPCompare=true}.
 */
public class RaytraceVsTauPTest {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final String BUNDLED_PREM = "velocity-models/prem.nd";

    private static final boolean STRICT =
        Boolean.parseBoolean(System.getProperty("xtreloc.strictTauPCompare", "false"));

    @Test
    public void premNdLoadsForBothEnginesAndTimesStayInLooseEnvelopeVsTauP() throws Exception {
        Assume.assumeFalse("skip TauP compare",
            Boolean.parseBoolean(System.getProperty("xtreloc.skipTauPCompare", "false")));

        TauModel tauModel = loadTauModelFromBundledNd(BUNDLED_PREM);
        Raytrace1D layered = Raytrace1D.load("prem.nd");

        TauP_Time taup = new TauP_Time();
        taup.setTauModel(tauModel);
        taup.clearPhaseNames();
        taup.clearArrivals();
        taup.clearPhases();
        taup.parsePhaseList("tts,S");

        // Prefer regional–teleseismic distances; very short epicentral distance often differs by branch choice.
        double[] distancesKm = {500.0, 800.0, 1200.0, 2000.0, 3500.0, 5000.0};
        double[] sourceDepthsKm = {10.0, 33.0, 100.0};
        double stationDepthKm = 0.1;

        for (double distKm : distancesKm) {
            for (double srcDepthKm : sourceDepthsKm) {
                Raytrace1D.RaySolution layeredSol = layered.solveFastestRay(distKm, srcDepthKm, stationDepthKm);
                double layeredTime = layeredSol.travelTimeSeconds;

                assertTrue(
                    String.format("Raytrace1D non-physical: dist=%.1f km dep=%.1f km t=%.4f",
                        distKm, srcDepthKm, layeredTime),
                    Double.isFinite(layeredTime) && layeredTime > 0.0 && layeredTime < 1e6);

                double distanceDeg = Math.toDegrees(distKm / EARTH_RADIUS_KM);
                taup.depthCorrect(srcDepthKm, stationDepthKm);
                taup.setSourceDepth(srcDepthKm);
                taup.calculate(distanceDeg);
                List<Arrival> arrivals = taup.getArrivals();
                Arrival fastest = fastestArrival(arrivals);

                double taupTime = fastest.getTime();
                assertTrue("TauP time positive finite", Double.isFinite(taupTime) && taupTime > 0.0);

                double ratio = layeredTime / taupTime;
                assertTrue(
                    String.format(
                        "1D/TauP ratio out of loose envelope [0.12, 6.0]: dist=%.1f dep=%.1f t1d=%.4f taup=%.4f ratio=%.4f",
                        distKm, srcDepthKm, layeredTime, taupTime, ratio),
                    ratio >= 0.12 && ratio <= 6.0);

                if (STRICT) {
                    double dt = Math.abs(layeredTime - taupTime);
                    double allowedDt = Math.max(5.0, taupTime * 0.18);
                    assertTrue(
                        String.format(
                            "strict travel time: dist=%.1f dep=%.1f t1d=%.4f taup=%.4f diff=%.4f (allowed %.4f)",
                            distKm, srcDepthKm, layeredTime, taupTime, dt, allowedDt),
                        dt <= allowedDt);

                    double taupTakeoffDeg = normalizeTaupTakeoffDeg(fastest.getTakeoffAngle());
                    double layeredTakeoffDeg = normalizeDeg(Math.toDegrees(layeredSol.takeoffAngleRad));
                    double dTakeoff = circularDiffDeg(layeredTakeoffDeg, taupTakeoffDeg);
                    assertTrue(
                        String.format(
                            "strict takeoff: dist=%.1f dep=%.1f t1d=%.2f taup=%.2f diff=%.2f",
                            distKm, srcDepthKm, layeredTakeoffDeg, taupTakeoffDeg, dTakeoff),
                        dTakeoff <= 18.0);
                }
            }
        }
    }

    private static TauModel loadTauModelFromBundledNd(String resourcePath) throws Exception {
        try (InputStream in = RaytraceVsTauPTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Path tmp = Files.createTempFile("xtreloc-taup-prem-", ".nd");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return TauModelLoader.load(tmp.toAbsolutePath().toString());
        }
    }

    private static Arrival fastestArrival(List<Arrival> arrivals) {
        if (arrivals == null || arrivals.isEmpty()) {
            throw new IllegalStateException("TauP returned no arrivals for prem.nd at this distance/depth.");
        }
        Arrival fastest = arrivals.get(0);
        for (Arrival arr : arrivals) {
            if (arr.getTime() < fastest.getTime()) {
                fastest = arr;
            }
        }
        return fastest;
    }

    private static double normalizeTaupTakeoffDeg(double takeoffDeg) {
        if (Double.isNaN(takeoffDeg)) {
            throw new IllegalStateException("TauP takeoff angle is NaN.");
        }
        double d = takeoffDeg;
        if (d < 0.0) {
            d += 180.0;
        }
        return normalizeDeg(d);
    }

    private static double normalizeDeg(double d) {
        double r = d % 360.0;
        return r < 0 ? r + 360.0 : r;
    }

    private static double circularDiffDeg(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }
}
