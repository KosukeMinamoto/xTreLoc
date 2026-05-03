package com.treloc.xtreloc.solver;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.FileUtils;
import com.treloc.xtreloc.io.VelocityModelCatalog;
import com.treloc.xtreloc.io.VelocityModelLoadException;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModel;
import edu.sc.seis.TauP.TauModelLoader;
import edu.sc.seis.TauP.TauP_Time;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

/**
 * HypoUtils — travel times and partial derivatives for hypocenter solvers.
 * Travel times use either {@link Raytrace1D} (default, layered 1D S) or TauP spherical {@code tts,S} per
 * {@link AppConfig#raytraceMethod}.
 *
 * @version 0.1
 * @since 2025-02-22
 * @author K.M.
 */
public class HypoUtils {
    private static final Logger logger = Logger.getLogger(HypoUtils.class.getName());
    /** Conversion factor from degrees to kilometers (approximately 111.32 km per degree) */
    private static final double DEG2KM = 111.32;

    /** Great-circle distance to degrees for TauP (matches TauP convention). */
    private static final double EARTH_RADIUS_KM = 6371.0;

    /** Forward-difference step for TauP ∂t/∂lon, ∂t/∂lat [deg] */
    private static final double FD_DEG = 0.01;
    /** Forward-difference step for TauP ∂t/∂depth [km] */
    private static final double FD_DEP_KM = 0.01;

    /** Distance threshold for cache reuse (0.01 degrees ≈ 1.1 km) */
    private static final double CACHE_DISTANCE_THRESHOLD = 0.01;

    /** Maximum number of travel time / partial derivative cache entries (bounded for memory safety) */
    private static final int TRAVEL_TIME_CACHE_MAX_SIZE = 256;

    /** Quantization step for cache key: ~0.01 deg ≈ 1.1 km */
    private static final double CACHE_QUANTIZE_DEG = 0.01;
    /** Quantization step for depth: 0.1 km */
    private static final double CACHE_QUANTIZE_DEP_KM = 0.1;

    private final Raytrace1D layeredRaytrace;
    private final boolean useTauP;
    private final TauModel tauModel;
    private final TauP_Time tauPTime;

    /** Cache key for travel time / partial derivative (quantized position + station indices). Thread-safe access via synchronized map. */
    private static final class TravelTimeCacheKey {
        final double qLat;
        final double qLon;
        final double qDep;
        final int[] usedIdx;
        final int hash;

        TravelTimeCacheKey(double lat, double lon, double dep, int[] usedIdx) {
            this.qLat = Math.round(lat / CACHE_QUANTIZE_DEG) * CACHE_QUANTIZE_DEG;
            this.qLon = Math.round(lon / CACHE_QUANTIZE_DEG) * CACHE_QUANTIZE_DEG;
            this.qDep = Math.round(dep / CACHE_QUANTIZE_DEP_KM) * CACHE_QUANTIZE_DEP_KM;
            this.usedIdx = usedIdx;
            this.hash = Arrays.hashCode(usedIdx) ^ Double.hashCode(qLat) ^ Double.hashCode(qLon) ^ Double.hashCode(qDep);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TravelTimeCacheKey)) {
                return false;
            }
            TravelTimeCacheKey k = (TravelTimeCacheKey) o;
            return Double.compare(k.qLat, qLat) == 0 && Double.compare(k.qLon, qLon) == 0
                && Double.compare(k.qDep, qDep) == 0 && Arrays.equals(usedIdx, k.usedIdx);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static class TravelTimeCacheEntry {
        double lon;
        double lat;
        double dep;
        int[] usedIdx;
        double[] trvTime;

        boolean matches(double lon, double lat, double dep, int[] usedIdx) {
            if (this.trvTime == null || this.usedIdx == null) {
                return false;
            }
            if (this.usedIdx.length != usedIdx.length) {
                return false;
            }
            GeodesicData g = Geodesic.WGS84.Inverse(this.lat, this.lon, lat, lon);
            double distanceDeg = Math.toDegrees(g.s12 / 1000.0 / 6371.0);
            double depthDiff = Math.abs(this.dep - dep);
            if (distanceDeg > CACHE_DISTANCE_THRESHOLD || depthDiff > 0.1) {
                return false;
            }
            for (int i = 0; i < usedIdx.length; i++) {
                if (this.usedIdx[i] != usedIdx[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class PartialDerivativeCacheEntry {
        double lon;
        double lat;
        double dep;
        int[] usedIdx;
        double[][] dtdr;
        double[] trvTime;

        boolean matches(double lon, double lat, double dep, int[] usedIdx) {
            if (this.dtdr == null || this.trvTime == null || this.usedIdx == null) {
                return false;
            }
            if (this.usedIdx.length != usedIdx.length) {
                return false;
            }
            GeodesicData g = Geodesic.WGS84.Inverse(this.lat, this.lon, lat, lon);
            double distanceDeg = Math.toDegrees(g.s12 / 1000.0 / 6371.0);
            double depthDiff = Math.abs(this.dep - dep);
            if (distanceDeg > CACHE_DISTANCE_THRESHOLD || depthDiff > 0.1) {
                return false;
            }
            for (int i = 0; i < usedIdx.length; i++) {
                if (this.usedIdx[i] != usedIdx[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Bounded travel-time cache (access-order, max size). Synchronized for thread safety. */
    private final Map<TravelTimeCacheKey, TravelTimeCacheEntry> travelTimeCacheMap =
        new LinkedHashMap<TravelTimeCacheKey, TravelTimeCacheEntry>(TRAVEL_TIME_CACHE_MAX_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<TravelTimeCacheKey, TravelTimeCacheEntry> eldest) {
                return size() > TRAVEL_TIME_CACHE_MAX_SIZE;
            }
        };

    /** Bounded partial-derivative cache. Synchronized for thread safety. */
    private final Map<TravelTimeCacheKey, PartialDerivativeCacheEntry> partialDerivativeCacheMap =
        new LinkedHashMap<TravelTimeCacheKey, PartialDerivativeCacheEntry>(TRAVEL_TIME_CACHE_MAX_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<TravelTimeCacheKey, PartialDerivativeCacheEntry> eldest) {
                return size() > TRAVEL_TIME_CACHE_MAX_SIZE;
            }
        };

    /**
     * Constructs HypoUtils and loads {@link Raytrace1D} from {@link AppConfig#taupFile}
     * (path or bundled name such as {@code prem.nd}). Optionally loads TauP when {@code raytraceMethod} is {@code taup}.
     */
    public HypoUtils(AppConfig config) throws VelocityModelLoadException {
        String path = config.taupFile;
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Velocity model file path is not specified (config taupFile)");
        }
        try {
            path = resolveBundledModelPath(path);
        } catch (Exception e) {
            throw new VelocityModelLoadException("Failed to resolve velocity model input: " + path, e);
        }
        try {
            this.layeredRaytrace = Raytrace1D.load(path);
        } catch (Exception e) {
            throw new VelocityModelLoadException("Failed to load velocity model for Raytrace1D: " + path, e);
        }

        this.useTauP = isTauPMethod(config);
        if (useTauP) {
            try {
                this.tauModel = TauModelLoader.load(path);
            } catch (Exception e) {
                throw new VelocityModelLoadException("Failed to load TauP model from: " + path, e);
            }
            this.tauPTime = new TauP_Time();
            this.tauPTime.setTauModel(tauModel);
            this.tauPTime.parsePhaseList("tts,S");
            logger.log(Level.INFO, "Travel-time engine: TauP (S tts), model={0}", path);
        } else {
            this.tauModel = null;
            this.tauPTime = null;
            logger.log(Level.INFO, "Travel-time engine: Raytrace1D (layered 1D S-wave), model={0}", path);
        }
    }

    private static boolean isTauPMethod(AppConfig config) {
        if (config == null || config.raytraceMethod == null || config.raytraceMethod.isBlank()) {
            return false;
        }
        return "taup".equalsIgnoreCase(config.raytraceMethod.trim());
    }

    /** Extract classpath velocity model to a temp file when needed (same resource layout as before). */
    private static String resolveBundledModelPath(String taupFile) throws Exception {
        String candidate = VelocityModelCatalog.toResourcePath(taupFile);
        InputStream in = HypoUtils.class.getClassLoader().getResourceAsStream(candidate);
        if (in == null) {
            return taupFile;
        }
        String ext = FileUtils.getFileExtension(candidate);
        Path tmp = Files.createTempFile("xtreloc-velmodel-", ext.isEmpty() ? ".nd" : "." + ext);
        tmp.toFile().deleteOnExit();
        try (InputStream src = in) {
            Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp.toAbsolutePath().toString();
    }

    private double travelTimeSecondsForEngine(double distKm, double hypDepKm, double stnDepKm) throws Exception {
        if (!useTauP) {
            return layeredRaytrace.travelTimeSeconds(distKm, hypDepKm, stnDepKm);
        }
        return taupFastestSTime(distKm, hypDepKm, stnDepKm); // NaN on TauP failure
    }

    private double taupFastestSTime(double distKm, double sourceDepthKm, double stationDepthKm) {
        synchronized (tauPTime) {
            try {
                tauPTime.depthCorrect(sourceDepthKm, stationDepthKm);
                tauPTime.setSourceDepth(sourceDepthKm);
                double distanceDeg = Math.toDegrees(distKm / EARTH_RADIUS_KM);
                tauPTime.calculate(distanceDeg);
                List<Arrival> arrivals = tauPTime.getArrivals();
                if (arrivals == null || arrivals.isEmpty()) {
                    return Double.NaN;
                }
                Arrival fastest = arrivals.get(0);
                for (Arrival a : arrivals) {
                    if (a.getTime() < fastest.getTime()) {
                        fastest = a;
                    }
                }
                return fastest.getTime();
            } catch (Exception e) {
                logger.log(Level.FINE, "TauP calculate failed: {0}", e.getMessage());
                return Double.NaN;
            }
        }
    }

    /**
     * Calculates S-wave travel times from the hypocenter to specified stations.
     *
     * @param stnTable the station table with columns [lat, lon, dep, pc, sc]
     *                  where dep is in kilometers (positive, depth below surface)
     * @param idxList  the index list of stations to use
     * @param point    the hypocenter coordinates
     * @return an array of travel times in seconds ({@link Double#MAX_VALUE} for stations not in {@code idxList} or on ray failure)
     */
    public double[] travelTime(double[][] stnTable, int[] idxList, Point point) {
        double hypLon = point.getLon();
        double hypLat = point.getLat();
        double hypDep = point.getDep();

        TravelTimeCacheKey key = new TravelTimeCacheKey(hypLat, hypLon, hypDep, idxList);
        synchronized (travelTimeCacheMap) {
            TravelTimeCacheEntry hit = travelTimeCacheMap.get(key);
            if (hit != null) {
                return hit.trvTime.clone();
            }
        }

        double[] trvTime = new double[stnTable.length];
        Arrays.fill(trvTime, Double.MAX_VALUE);
        for (int i : idxList) {
            double stnLat = stnTable[i][0];
            double stnLon = stnTable[i][1];
            double stnDep = stnTable[i][2];
            GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
            double distKm = g.s12 / 1000.0;
            try {
                double tt = travelTimeSecondsForEngine(distKm, hypDep, stnDep);
                if (!Double.isFinite(tt) || tt <= 0.0) {
                    trvTime[i] = Double.MAX_VALUE;
                } else {
                    trvTime[i] = tt + stnTable[i][4];
                }
            } catch (Exception e) {
                logger.warning(String.format(
                    "%s raytrace failed for station %d: dep=(%.3f, %.3f) dist=%.3f km, error=%s",
                    useTauP ? "TauP" : "Raytrace1D",
                    i, hypDep, stnDep, distKm, e.getMessage()));
                trvTime[i] = Double.MAX_VALUE;
            }
        }
        TravelTimeCacheEntry entry = new TravelTimeCacheEntry();
        entry.lon = hypLon;
        entry.lat = hypLat;
        entry.dep = hypDep;
        entry.usedIdx = idxList.clone();
        entry.trvTime = trvTime.clone();
        synchronized (travelTimeCacheMap) {
            travelTimeCacheMap.put(key, entry);
        }
        return trvTime.clone();
    }

    /**
     * Converts residuals to weights for a given array of residuals.
     * Uses the inverse of absolute residual values as weights.
     *
     * @param resDiffTime the residuals as an array
     * @return an array of weights (inverse of absolute residuals)
     */
    public static double[] residual2weight(double[] resDiffTime) {
        double[] weight = new double[resDiffTime.length];
        for (int i = 0; i < resDiffTime.length; i++) {
            double w = Math.abs(1.0 / (resDiffTime[i] + 1e-10));
            weight[i] = w;
        }
        return weight;
    }

    /**
     * Calculates the partial derivative matrix for travel time with respect to hypocenter coordinates.
     * Layered 1D mode: analytical (take-off angle). TauP mode: forward differences on TauP travel times.
     * Units: dtdr[i][0] = dt/dlon [s/deg], dtdr[i][1] = dt/dlat [s/deg], dtdr[i][2] = dt/ddep [s/km]
     *
     * @param stnTable the station table with columns [lat, lon, dep, pc, sc]
     * @param idxList  the index list of stations to use
     * @param point    the hypocenter coordinates
     * @return an Object array containing [dtdr (double[][]), trvTime (double[])]
     */
    public Object[] partialDerivativeMatrix(double[][] stnTable, int[] idxList, Point point) {
        double hypLon = point.getLon();
        double hypLat = point.getLat();
        double hypDep = point.getDep();

        TravelTimeCacheKey key = new TravelTimeCacheKey(hypLat, hypLon, hypDep, idxList);
        synchronized (partialDerivativeCacheMap) {
            PartialDerivativeCacheEntry hit = partialDerivativeCacheMap.get(key);
            if (hit != null) {
                double[][] dtdrCopy = new double[hit.dtdr.length][];
                for (int i = 0; i < hit.dtdr.length; i++) {
                    dtdrCopy[i] = hit.dtdr[i].clone();
                }
                return new Object[] { dtdrCopy, hit.trvTime.clone() };
            }
        }

        double[][] dtdr = new double[stnTable.length][3];
        double[] t0 = new double[stnTable.length];
        Arrays.fill(t0, Double.MAX_VALUE);

        if (useTauP) {
            for (int i : idxList) {
                double stnLat = stnTable[i][0];
                double stnLon = stnTable[i][1];
                double stnDep = stnTable[i][2];
                GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
                double distKm = g.s12 / 1000.0;
                try {
                    double baseT = taupFastestSTime(distKm, hypDep, stnDep);
                    if (!Double.isFinite(baseT) || baseT <= 0.0) {
                        dtdr[i][0] = 0.0;
                        dtdr[i][1] = 0.0;
                        dtdr[i][2] = 0.0;
                        t0[i] = Double.MAX_VALUE;
                        continue;
                    }
                    double distLon = Geodesic.WGS84.Inverse(hypLat, hypLon + FD_DEG, stnLat, stnLon).s12 / 1000.0;
                    double distLat = Geodesic.WGS84.Inverse(hypLat + FD_DEG, hypLon, stnLat, stnLon).s12 / 1000.0;
                    double tLon = taupFastestSTime(distLon, hypDep, stnDep);
                    double tLat = taupFastestSTime(distLat, hypDep, stnDep);
                    double tDep = taupFastestSTime(distKm, hypDep + FD_DEP_KM, stnDep);
                    dtdr[i][0] = (tLon - baseT) / FD_DEG;
                    dtdr[i][1] = (tLat - baseT) / FD_DEG;
                    dtdr[i][2] = (tDep - baseT) / FD_DEP_KM;
                    t0[i] = baseT + stnTable[i][4];
                } catch (Exception e) {
                    dtdr[i][0] = 0.0;
                    dtdr[i][1] = 0.0;
                    dtdr[i][2] = 0.0;
                    t0[i] = Double.MAX_VALUE;
                }
            }
        } else {
            double sVel = layeredRaytrace.sourceSVelocity(hypDep);
            for (int i : idxList) {
                double stnLat = stnTable[i][0];
                double stnLon = stnTable[i][1];
                double stnDep = stnTable[i][2];
                GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
                double azm = Math.toRadians(g.azi1);
                double distKm = g.s12 / 1000.0;
                try {
                    Raytrace1D.RaySolution sol = layeredRaytrace.solveFastestRay(distKm, hypDep, stnDep);
                    double tak = sol.takeoffAngleRad;
                    dtdr[i][0] = -Math.sin(tak) * Math.sin(azm) / sVel * DEG2KM * Math.cos(Math.toRadians(hypLat));
                    dtdr[i][1] = -Math.sin(tak) * Math.cos(azm) / sVel * DEG2KM;
                    dtdr[i][2] = -Math.cos(tak) / sVel;
                    t0[i] = sol.travelTimeSeconds + stnTable[i][4];
                } catch (Exception e) {
                    dtdr[i][0] = 0.0;
                    dtdr[i][1] = 0.0;
                    dtdr[i][2] = 0.0;
                    t0[i] = Double.MAX_VALUE;
                }
            }
        }

        PartialDerivativeCacheEntry entry = new PartialDerivativeCacheEntry();
        entry.lon = hypLon;
        entry.lat = hypLat;
        entry.dep = hypDep;
        entry.usedIdx = idxList.clone();
        entry.dtdr = dtdr;
        entry.trvTime = t0;
        synchronized (partialDerivativeCacheMap) {
            partialDerivativeCacheMap.put(key, entry);
        }
        double[][] dtdrCopy = new double[dtdr.length][];
        for (int i = 0; i < dtdr.length; i++) {
            dtdrCopy[i] = dtdr[i].clone();
        }
        return new Object[] { dtdrCopy, t0.clone() };
    }

    /**
     * Calculates the standard deviation of an array of values.
     *
     * @param data the array of values (full length used)
     * @return the standard deviation, or 0.0 if data is empty
     */
    public static double standardDeviation(double[] data) {
        if (data == null || data.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        int length = data.length;
        for (double num : data) {
            sum += num;
        }
        double mean = sum / length;
        double variance = 0.0;
        for (double num : data) {
            variance += Math.pow(num - mean, 2);
        }
        return Math.sqrt(variance / length);
    }

    /**
     * Gets the conversion factor from degrees to kilometers.
     *
     * @return the DEG2KM constant (approximately 111.32 km per degree)
     */
    public static double getDeg2Km() {
        return DEG2KM;
    }
}
