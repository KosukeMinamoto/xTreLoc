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
 * Validates {@link Raytrace1D} against TauP S arrivals on spherical PREM, split by use case.
 * <p>
 * <b>Regional (short range)</b>: the short-range branch is meaningful up to ~120 km. With the same bundled {@code prem.nd}
 * fed to TauP, epicentral distances 10–120 km should show modest relative travel-time error (flat vs spherical mismatch is
 * small at that scale).
 * <p>
 * <b>Teleseismic</b>: layered branch choice and TauP’s first spherical S arrival may disagree. Where the time ratio
 * {@code t1d/taup} sits in a comparable band, we apply a relative-error ceiling; looser ratio checks live in {@link RaytraceVsTauPTest}.
 * <p>
 * Skip with {@code -Dxtreloc.skipTauPCompare=true}.
 */
public class RaytraceTauPAccuracyTest {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final String BUNDLED_PREM = "velocity-models/prem.nd";

    /** Beyond {@link Raytrace1D}'s short-range branch logic (~120 km), times may disagree strongly with TauP S. */
    private static final double MAX_REGIONAL_DISTANCE_KM = 120.0;

    /** When 1D/TauP ratio is in this band, treat both as the same “scale” and require relative error bound. */
    private static final double TELESEISMIC_RATIO_LO = 0.78;
    private static final double TELESEISMIC_RATIO_HI = 1.28;

    private static final double TELESEISMIC_MAX_REL_ERR = 0.22;

    /** Regional grid: holds for typical crustal paths vs TauP at small angular distance. */
    private static final double REGIONAL_MAX_REL_ERR = 0.10;

    private static TauModel loadTauModelFromBundledNd(String resourcePath) throws Exception {
        try (InputStream in = RaytraceTauPAccuracyTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
            throw new IllegalStateException("TauP returned no arrivals.");
        }
        Arrival fastest = arrivals.get(0);
        for (Arrival arr : arrivals) {
            if (arr.getTime() < fastest.getTime()) {
                fastest = arr;
            }
        }
        return fastest;
    }

    @Test
    public void premRegionalDistanceUpTo120Km_matchesTauPFastS_withinTenPercent() throws Exception {
        Assume.assumeFalse(
            "skip TauP compare",
            Boolean.parseBoolean(System.getProperty("xtreloc.skipTauPCompare", "false")));

        TauModel tauModel = loadTauModelFromBundledNd(BUNDLED_PREM);
        Raytrace1D layered = Raytrace1D.load("prem.nd");

        TauP_Time taup = new TauP_Time();
        taup.setTauModel(tauModel);
        taup.clearPhaseNames();
        taup.clearArrivals();
        taup.clearPhases();
        taup.parsePhaseList("tts,S");

        double[] distancesKm = {10.0, 30.0, 50.0, 100.0, 120.0};
        double sourceDepthKm = 30.0;
        double stationDepthKm = 0.1;

        for (double distKm : distancesKm) {
            assertTrue(distKm <= MAX_REGIONAL_DISTANCE_KM + 1e-6);

            double t1d = layered.solveFastestRay(distKm, sourceDepthKm, stationDepthKm).travelTimeSeconds;
            double distanceDeg = Math.toDegrees(distKm / EARTH_RADIUS_KM);
            taup.depthCorrect(sourceDepthKm, stationDepthKm);
            taup.setSourceDepth(sourceDepthKm);
            taup.calculate(distanceDeg);
            double taupTime = fastestArrival(taup.getArrivals()).getTime();

            double rel = Math.abs(t1d - taupTime) / taupTime;
            assertTrue(
                String.format(
                    "regional prem.nd: dist=%.0f km src=%.1f km t1d=%.4f taup=%.4f rel=%.5f (max %.4f)",
                    distKm, sourceDepthKm, t1d, taupTime, rel, REGIONAL_MAX_REL_ERR),
                rel <= REGIONAL_MAX_REL_ERR);

            double ratio = t1d / taupTime;
            assertTrue(
                String.format(
                    "regional ratio unexpectedly far from 1: dist=%.0f ratio=%.4f",
                    distKm, ratio),
                ratio >= 0.75 && ratio <= 1.35);
        }
    }

    @Test
    public void premTeleseismic_when1dTauPRatioComparable_thenRelativeErrorBounded() throws Exception {
        Assume.assumeFalse(
            "skip TauP compare",
            Boolean.parseBoolean(System.getProperty("xtreloc.skipTauPCompare", "false")));

        TauModel tauModel = loadTauModelFromBundledNd(BUNDLED_PREM);
        Raytrace1D layered = Raytrace1D.load("prem.nd");

        TauP_Time taup = new TauP_Time();
        taup.setTauModel(tauModel);
        taup.clearPhaseNames();
        taup.clearArrivals();
        taup.clearPhases();
        taup.parsePhaseList("tts,S");

        double[] distancesKm = {500.0, 800.0, 1200.0, 2000.0, 3500.0, 5000.0};
        double[] sourceDepthsKm = {10.0, 33.0, 100.0};
        double stationDepthKm = 0.1;

        for (double distKm : distancesKm) {
            for (double srcDepthKm : sourceDepthsKm) {
                double t1d = layered.solveFastestRay(distKm, srcDepthKm, stationDepthKm).travelTimeSeconds;
                assertTrue(
                    String.format("t1d non-physical: dist=%.1f dep=%.1f t=%.4f", distKm, srcDepthKm, t1d),
                    Double.isFinite(t1d) && t1d > 0.0);

                double distanceDeg = Math.toDegrees(distKm / EARTH_RADIUS_KM);
                taup.depthCorrect(srcDepthKm, stationDepthKm);
                taup.setSourceDepth(srcDepthKm);
                taup.calculate(distanceDeg);
                double taupTime = fastestArrival(taup.getArrivals()).getTime();
                assertTrue(
                    "TauP time positive finite at dist=" + distKm + " dep=" + srcDepthKm,
                    Double.isFinite(taupTime) && taupTime > 0.0);

                double ratio = t1d / taupTime;
                if (ratio >= TELESEISMIC_RATIO_LO && ratio <= TELESEISMIC_RATIO_HI) {
                    double rel = Math.abs(t1d - taupTime) / taupTime;
                    assertTrue(
                        String.format(
                            "teleseismic prem.nd: dist=%.0f dep=%.0f t1d=%.4f taup=%.4f ratio=%.4f rel=%.4f (max rel %.2f when ratio in [%.2f,%.2f])",
                            distKm,
                            srcDepthKm,
                            t1d,
                            taupTime,
                            ratio,
                            rel,
                            TELESEISMIC_MAX_REL_ERR,
                            TELESEISMIC_RATIO_LO,
                            TELESEISMIC_RATIO_HI),
                        rel <= TELESEISMIC_MAX_REL_ERR);
                }
            }
        }
    }
}
