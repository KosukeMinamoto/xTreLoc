package com.treloc.xtreloc.solver;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.FileUtils;
import com.treloc.xtreloc.io.VelocityModelCatalog;
import com.treloc.xtreloc.io.VelocityModelLoadException;
import com.treloc.xtreloc.solver.table.OutOfTableRangeException;
import com.treloc.xtreloc.solver.table.TravelTimeTable;
import com.treloc.xtreloc.solver.table.TravelTimeTables;
import com.treloc.xtreloc.util.SolverLogger;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModel;
import edu.sc.seis.TauP.TauModelLoader;
import edu.sc.seis.TauP.TauP_Time;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

/**
 * HypoUtils — travel times and partial derivatives for hypocenter solvers.
 * Travel times use either {@link Raytrace1D} (P and S, default layered 1-D) or TauP spherical
 * {@code ttp,P} / {@code tts,S} per {@link AppConfig#raytraceMethod}, or NonLinLoc {@code *.time.hdr} grids.
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
    /** Central-difference steps on table travel times (about the table mesh): degrees and km. */
    private static final double FD_TABLE_DEG = 0.02;
    private static final double FD_TABLE_DEP_KM = 1.0;

    /** Maximum number of travel time / partial derivative cache entries (bounded for memory safety) */
    private static final int TRAVEL_TIME_CACHE_MAX_SIZE = 4096;

    private static final ConcurrentHashMap<String, String> BUNDLED_FILE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, TauModel> TAU_MODEL_CACHE = new ConcurrentHashMap<>();

    private final Raytrace1D layeredP;
    private final Raytrace1D layeredS;
    /** Pre-computed travel-time table (replaces the velocity model when {@code taupFile} points to one), or null. */
    private final TravelTimeTable travelTimeTable;
    private final Double locLonMin;
    private final Double locLonMax;
    private final Double locLatMin;
    private final Double locLatMax;
    private final Double locMinDepthKm;
    private final boolean useTauP;
    private final TauModel tauModel;
    private final TauP_Time tauPTimeP;
    private final TauP_Time tauPTimeS;

    /** Cache key for travel time / partial derivative (exact source position + station indices). */
    private static final class TravelTimeCacheKey {
        final double qLat;
        final double qLon;
        final double qDep;
        final int[] usedIdx;
        final TravelTimeTable.Phase phase;
        final int hash;

        TravelTimeCacheKey(double lat, double lon, double dep, int[] usedIdx, TravelTimeTable.Phase phase) {
            this.qLat = lat;
            this.qLon = lon;
            this.qDep = dep;
            this.usedIdx = usedIdx;
            this.phase = phase == null ? TravelTimeTable.Phase.S : phase;
            this.hash = Arrays.hashCode(usedIdx) ^ Double.hashCode(qLat) ^ Double.hashCode(qLon)
                ^ Double.hashCode(qDep) ^ this.phase.hashCode();
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
                && Double.compare(k.qDep, qDep) == 0 && phase == k.phase && Arrays.equals(usedIdx, k.usedIdx);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static class PartialDerivativeCacheEntry {
        double[][] dtdr;
        double[] trvTime;
    }

    /** Bounded travel-time cache (access-order, max size). Synchronized for thread safety. */
    private final Map<TravelTimeCacheKey, double[]> travelTimeCacheMap =
        new LinkedHashMap<TravelTimeCacheKey, double[]>(TRAVEL_TIME_CACHE_MAX_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<TravelTimeCacheKey, double[]> eldest) {
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
        this.locLonMin = config != null ? config.locLonMin : null;
        this.locLonMax = config != null ? config.locLonMax : null;
        this.locLatMin = config != null ? config.locLatMin : null;
        this.locLatMax = config != null ? config.locLatMax : null;
        this.locMinDepthKm = config != null ? config.locMinDepthKm : null;
        String path = config.taupFile;
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Velocity model file path is not specified (config taupFile)");
        }
        if (TravelTimeTables.isTableSpec(path)) {
            try {
                this.travelTimeTable = TravelTimeTables.load(path);
            } catch (Exception e) {
                throw new VelocityModelLoadException("Failed to load travel-time table: " + path, e);
            }
            this.layeredP = null;
            this.layeredS = null;
            this.useTauP = false;
            this.tauModel = null;
            this.tauPTimeP = null;
            this.tauPTimeS = null;
            return;
        }
        this.travelTimeTable = null;
        String modelSpec = path;
        try {
            Raytrace1D.Dual dual = Raytrace1D.loadDual(modelSpec);
            this.layeredP = dual.p;
            this.layeredS = dual.s;
        } catch (Exception e) {
            throw new VelocityModelLoadException("Failed to load P/S velocity model for Raytrace1D: " + modelSpec, e);
        }

        this.useTauP = isTauPMethod(config);
        if (useTauP) {
            try {
                String filePath = resolveBundledModelPath(modelSpec);
                this.tauModel = TAU_MODEL_CACHE.computeIfAbsent(filePath, k -> {
                    try {
                        return TauModelLoader.load(k);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
            } catch (Exception e) {
                Throwable c = e instanceof IllegalStateException && e.getCause() != null ? e.getCause() : e;
                throw new VelocityModelLoadException("Failed to load TauP model from: " + modelSpec, c);
            }
            this.tauPTimeP = new TauP_Time();
            this.tauPTimeP.setTauModel(tauModel);
            this.tauPTimeP.parsePhaseList("ttp,P");
            this.tauPTimeS = new TauP_Time();
            this.tauPTimeS.setTauModel(tauModel);
            this.tauPTimeS.parsePhaseList("tts,S");
            logger.log(Level.INFO, "Travel-time engine: TauP (P ttp + S tts), model={0}", modelSpec);
        } else {
            this.tauModel = null;
            this.tauPTimeP = null;
            this.tauPTimeS = null;
            logger.log(Level.INFO, "Travel-time engine: Raytrace1D (layered 1-D P+S), model={0}", modelSpec);
        }
    }

    private static boolean isTauPMethod(AppConfig config) {
        if (config == null || config.raytraceMethod == null || config.raytraceMethod.isBlank()) {
            return false;
        }
        return "taup".equalsIgnoreCase(config.raytraceMethod.trim());
    }

    /** Extract classpath velocity model to a temp file once (TauP needs a real path). */
    private static String resolveBundledModelPath(String taupFile) throws Exception {
        String candidate = VelocityModelCatalog.toResourcePath(taupFile);
        String cached = BUNDLED_FILE_CACHE.get(candidate);
        if (cached != null) {
            return cached;
        }
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
        String abs = tmp.toAbsolutePath().toString();
        String raced = BUNDLED_FILE_CACHE.putIfAbsent(candidate, abs);
        return raced != null ? raced : abs;
    }

    private Raytrace1D layeredFor(TravelTimeTable.Phase phase) {
        return phase == TravelTimeTable.Phase.P ? layeredP : layeredS;
    }

    private double stationCorrection(double[] stnRow, TravelTimeTable.Phase phase) {
        return phase == TravelTimeTable.Phase.P ? stnRow[3] : stnRow[4];
    }

    private double travelTimeSecondsForEngine(TravelTimeTable.Phase phase, double distKm, double hypDepKm,
                                             double stnDepKm) throws Exception {
        if (!useTauP) {
            return layeredFor(phase).travelTimeSeconds(distKm, hypDepKm, stnDepKm);
        }
        return taupFastestTime(phase, distKm, hypDepKm, stnDepKm);
    }

    private double taupFastestTime(TravelTimeTable.Phase phase, double distKm, double sourceDepthKm,
                                   double stationDepthKm) {
        TauP_Time engine = phase == TravelTimeTable.Phase.P ? tauPTimeP : tauPTimeS;
        synchronized (engine) {
            try {
                engine.depthCorrect(sourceDepthKm, stationDepthKm);
                engine.setSourceDepth(sourceDepthKm);
                double distanceDeg = Math.toDegrees(distKm / EARTH_RADIUS_KM);
                engine.calculate(distanceDeg);
                List<Arrival> arrivals = engine.getArrivals();
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
     * Travel times from the hypocenter to specified stations for one phase.
     * Station correction is {@code pc} for P and {@code sc} for S ({@code stnTable} columns 3 and 4).
     */
    public double[] travelTime(double[][] stnTable, int[] idxList, Point point, TravelTimeTable.Phase phase) {
        TravelTimeTable.Phase ph = phase == null ? TravelTimeTable.Phase.S : phase;
        double[] cached = cachedTravelTime(point, idxList, ph);
        if (cached != null) {
            return cached;
        }
        double[] trvTime = computeTravelTimes(stnTable, idxList, point, ph, null);
        putTravelTime(point, idxList, ph, trvTime);
        return trvTime;
    }

    /**
     * P and/or S travel times. When both phases are needed on a velocity model, the station geodesic
     * is computed once and reused.
     */
    public double[][] travelTimes(double[][] stnTable, int[] idxList, Point point,
                                  boolean needP, boolean needS) {
        double[] tP = null;
        double[] tS = null;
        if (needP) {
            tP = cachedTravelTime(point, idxList, TravelTimeTable.Phase.P);
        }
        if (needS) {
            tS = cachedTravelTime(point, idxList, TravelTimeTable.Phase.S);
        }
        boolean missP = needP && tP == null;
        boolean missS = needS && tS == null;
        if (missP && missS && travelTimeTable == null) {
            double[] distKm = stationDistancesKm(stnTable, idxList, point);
            tP = computeTravelTimes(stnTable, idxList, point, TravelTimeTable.Phase.P, distKm);
            tS = computeTravelTimes(stnTable, idxList, point, TravelTimeTable.Phase.S, distKm);
            putTravelTime(point, idxList, TravelTimeTable.Phase.P, tP);
            putTravelTime(point, idxList, TravelTimeTable.Phase.S, tS);
        } else {
            if (missP) {
                tP = computeTravelTimes(stnTable, idxList, point, TravelTimeTable.Phase.P, null);
                putTravelTime(point, idxList, TravelTimeTable.Phase.P, tP);
            }
            if (missS) {
                tS = computeTravelTimes(stnTable, idxList, point, TravelTimeTable.Phase.S, null);
                putTravelTime(point, idxList, TravelTimeTable.Phase.S, tS);
            }
        }
        return new double[][] { tP, tS };
    }

    private double[] cachedTravelTime(Point point, int[] idxList, TravelTimeTable.Phase ph) {
        TravelTimeCacheKey key = new TravelTimeCacheKey(point.getLat(), point.getLon(), point.getDep(), idxList, ph);
        synchronized (travelTimeCacheMap) {
            double[] hit = travelTimeCacheMap.get(key);
            return hit != null ? hit.clone() : null;
        }
    }

    private void putTravelTime(Point point, int[] idxList, TravelTimeTable.Phase ph, double[] trvTime) {
        TravelTimeCacheKey key = new TravelTimeCacheKey(point.getLat(), point.getLon(), point.getDep(), idxList, ph);
        synchronized (travelTimeCacheMap) {
            travelTimeCacheMap.put(key, trvTime.clone());
        }
    }

    private static double[] stationDistancesKm(double[][] stnTable, int[] idxList, Point point) {
        double hypLat = point.getLat();
        double hypLon = point.getLon();
        double[] distKm = new double[stnTable.length];
        Arrays.fill(distKm, Double.NaN);
        for (int i : idxList) {
            distKm[i] = Geodesic.WGS84.Inverse(hypLat, hypLon, stnTable[i][0], stnTable[i][1]).s12 / 1000.0;
        }
        return distKm;
    }

    private double[] computeTravelTimes(double[][] stnTable, int[] idxList, Point point,
                                        TravelTimeTable.Phase ph, double[] distKmByStation) {
        double hypLon = point.getLon();
        double hypLat = point.getLat();
        double hypDep = point.getDep();
        double[] trvTime = new double[stnTable.length];
        Arrays.fill(trvTime, Double.MAX_VALUE);
        for (int i : idxList) {
            double stnLat = stnTable[i][0];
            double stnLon = stnTable[i][1];
            double stnDep = stnTable[i][2];
            if (travelTimeTable != null) {
                double tt = travelTimeTable.travelTime(ph, hypLat, hypLon, hypDep, stnLat, stnLon, stnDep);
                trvTime[i] = (Double.isFinite(tt) && tt > 0.0) ? tt + stationCorrection(stnTable[i], ph) : Double.MAX_VALUE;
                continue;
            }
            double distKm = distKmByStation != null ? distKmByStation[i]
                : Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon).s12 / 1000.0;
            try {
                double tt = travelTimeSecondsForEngine(ph, distKm, hypDep, stnDep);
                if (!Double.isFinite(tt) || tt <= 0.0) {
                    trvTime[i] = Double.MAX_VALUE;
                } else {
                    trvTime[i] = tt + stationCorrection(stnTable[i], ph);
                }
            } catch (Exception e) {
                logger.warning(String.format(
                    "%s raytrace failed for station %d: dep=(%.3f, %.3f) dist=%.3f km, error=%s",
                    useTauP ? "TauP" : "Raytrace1D",
                    i, hypDep, stnDep, distKm, e.getMessage()));
                trvTime[i] = Double.MAX_VALUE;
            }
        }
        return trvTime;
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
     * @param phase    P or S
     * @return an Object array containing [dtdr (double[][]), trvTime (double[])]
     */
    public Object[] partialDerivativeMatrix(double[][] stnTable, int[] idxList, Point point,
                                            TravelTimeTable.Phase phase) {
        TravelTimeTable.Phase ph = phase == null ? TravelTimeTable.Phase.S : phase;
        double hypLon = point.getLon();
        double hypLat = point.getLat();
        double hypDep = point.getDep();

        TravelTimeCacheKey key = new TravelTimeCacheKey(hypLat, hypLon, hypDep, idxList, ph);
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

        if (travelTimeTable != null) {
            if (!travelTimeTable.containsSource(hypLat, hypLon, hypDep)) {
                throw new OutOfTableRangeException(String.format(java.util.Locale.ROOT,
                    "source (lat=%.4f, lon=%.4f, depth=%.2f km) is outside the travel-time table range",
                    hypLat, hypLon, hypDep));
            }
            tablePartialDerivatives(stnTable, idxList, hypLat, hypLon, hypDep, dtdr, t0, ph);
        } else if (useTauP) {
            for (int i : idxList) {
                double stnLat = stnTable[i][0];
                double stnLon = stnTable[i][1];
                double stnDep = stnTable[i][2];
                GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
                double distKm = g.s12 / 1000.0;
                try {
                    double baseT = taupFastestTime(ph, distKm, hypDep, stnDep);
                    if (!Double.isFinite(baseT) || baseT <= 0.0) {
                        dtdr[i][0] = 0.0;
                        dtdr[i][1] = 0.0;
                        dtdr[i][2] = 0.0;
                        t0[i] = Double.MAX_VALUE;
                        continue;
                    }
                    double distLon = Geodesic.WGS84.Inverse(hypLat, hypLon + FD_DEG, stnLat, stnLon).s12 / 1000.0;
                    double distLat = Geodesic.WGS84.Inverse(hypLat + FD_DEG, hypLon, stnLat, stnLon).s12 / 1000.0;
                    double tLon = taupFastestTime(ph, distLon, hypDep, stnDep);
                    double tLat = taupFastestTime(ph, distLat, hypDep, stnDep);
                    double tDep = taupFastestTime(ph, distKm, hypDep + FD_DEP_KM, stnDep);
                    dtdr[i][0] = (tLon - baseT) / FD_DEG;
                    dtdr[i][1] = (tLat - baseT) / FD_DEG;
                    dtdr[i][2] = (tDep - baseT) / FD_DEP_KM;
                    t0[i] = baseT + stationCorrection(stnTable[i], ph);
                } catch (Exception e) {
                    dtdr[i][0] = 0.0;
                    dtdr[i][1] = 0.0;
                    dtdr[i][2] = 0.0;
                    t0[i] = Double.MAX_VALUE;
                }
            }
        } else {
            Raytrace1D rt = layeredFor(ph);
            double srcVel = rt.sourceSVelocity(hypDep);
            for (int i : idxList) {
                double stnLat = stnTable[i][0];
                double stnLon = stnTable[i][1];
                double stnDep = stnTable[i][2];
                GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
                double azm = Math.toRadians(g.azi1);
                double distKm = g.s12 / 1000.0;
                try {
                    Raytrace1D.RaySolution sol = rt.solveFastestRay(distKm, hypDep, stnDep);
                    double tak = sol.takeoffAngleRad;
                    double p = Double.isFinite(sol.horizontalSlowness)
                        ? sol.horizontalSlowness
                        : Math.sin(tak) / srcVel;
                    // ∂T/∂x_source = −s_ray  (ray leaves the source toward the station;
                    // takeoff from downward vertical; azi1 is hypocenter → station, clockwise from north)
                    dtdr[i][0] = -p * Math.sin(azm) * DEG2KM * Math.cos(Math.toRadians(hypLat));
                    dtdr[i][1] = -p * Math.cos(azm) * DEG2KM;
                    dtdr[i][2] = -Math.cos(tak) / srcVel;
                    t0[i] = sol.travelTimeSeconds + stationCorrection(stnTable[i], ph);
                } catch (Exception e) {
                    dtdr[i][0] = 0.0;
                    dtdr[i][1] = 0.0;
                    dtdr[i][2] = 0.0;
                    t0[i] = Double.MAX_VALUE;
                }
            }
        }

        PartialDerivativeCacheEntry entry = new PartialDerivativeCacheEntry();
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
     * Partial derivatives from the travel-time table: analytic from the take-off angle and source velocity when the
     * table provides them, otherwise central differences of the table travel times.
     */
    private void tablePartialDerivatives(double[][] stnTable, int[] idxList, double hypLat, double hypLon,
                                         double hypDep, double[][] dtdr, double[] t0, TravelTimeTable.Phase ph) {
        double vSrc = travelTimeTable.velocityAtSource(ph, hypLat, hypLon, hypDep);
        for (int i : idxList) {
            double stnLat = stnTable[i][0];
            double stnLon = stnTable[i][1];
            double stnDep = stnTable[i][2];
            double base = travelTimeTable.travelTime(ph, hypLat, hypLon, hypDep, stnLat, stnLon, stnDep);
            if (!Double.isFinite(base) || base <= 0.0) {
                t0[i] = Double.MAX_VALUE;
                continue;
            }
            t0[i] = base + stationCorrection(stnTable[i], ph);
            double[] direct = travelTimeTable.sourceDerivatives(ph, hypLat, hypLon, hypDep, stnLat, stnLon, stnDep);
            if (direct != null && Double.isFinite(direct[0] + direct[1] + direct[2])) {
                dtdr[i][0] = direct[0];
                dtdr[i][1] = direct[1];
                dtdr[i][2] = direct[2];
                continue;
            }
            double tak = travelTimeTable.takeoffAngleRad(ph, hypLat, hypLon, hypDep, stnLat, stnLon, stnDep);
            if (Double.isFinite(tak) && Double.isFinite(vSrc) && vSrc > 0.0) {
                GeodesicData g = Geodesic.WGS84.Inverse(hypLat, hypLon, stnLat, stnLon);
                double azm = Math.toRadians(g.azi1);
                double p = Math.sin(tak) / vSrc;
                dtdr[i][0] = -p * Math.sin(azm) * DEG2KM * Math.cos(Math.toRadians(hypLat));
                dtdr[i][1] = -p * Math.cos(azm) * DEG2KM;
                dtdr[i][2] = -Math.cos(tak) / vSrc;
            } else {
                dtdr[i][0] = tableDerivative(ph, hypLat, hypLon + FD_TABLE_DEG, hypDep, hypLat, hypLon - FD_TABLE_DEG,
                    hypDep, stnLat, stnLon, stnDep, base, FD_TABLE_DEG);
                dtdr[i][1] = tableDerivative(ph, hypLat + FD_TABLE_DEG, hypLon, hypDep, hypLat - FD_TABLE_DEG, hypLon,
                    hypDep, stnLat, stnLon, stnDep, base, FD_TABLE_DEG);
                dtdr[i][2] = tableDerivative(ph, hypLat, hypLon, hypDep + FD_TABLE_DEP_KM, hypLat, hypLon,
                    hypDep - FD_TABLE_DEP_KM, stnLat, stnLon, stnDep, base, FD_TABLE_DEP_KM);
            }
        }
    }

    /** (t(+h) - t(-h)) / 2h, one-sided when one side leaves the table, 0 when both do. */
    private double tableDerivative(TravelTimeTable.Phase ph, double lat1, double lon1, double dep1,
                                   double lat2, double lon2, double dep2,
                                   double stnLat, double stnLon, double stnDep, double base, double h) {
        double t1 = travelTimeTable.travelTime(ph, lat1, lon1, dep1, stnLat, stnLon, stnDep);
        double t2 = travelTimeTable.travelTime(ph, lat2, lon2, dep2, stnLat, stnLon, stnDep);
        boolean ok1 = Double.isFinite(t1);
        boolean ok2 = Double.isFinite(t2);
        if (ok1 && ok2) {
            return (t1 - t2) / (2.0 * h);
        }
        if (ok1) {
            return (t1 - base) / h;
        }
        if (ok2) {
            return (base - t2) / h;
        }
        return 0.0;
    }

    public static final double DEFAULT_P_WEIGHT = 2.0;

    public static double sanitizePWeight(double pWeight) {
        return Double.isFinite(pWeight) && pWeight >= 0.0 ? pWeight : DEFAULT_P_WEIGHT;
    }

    /** P rows use {@code pWeight}; S rows use {@code 1}. */
    public static double phaseWeight(double[] row, double pWeight) {
        return DatPair.isP(row) ? sanitizePWeight(pWeight) : 1.0;
    }

    /** {@code phaseCode < 0.5} is P. */
    public static double phaseWeight(double phaseCode, double pWeight) {
        return phaseCode < 0.5 ? sanitizePWeight(pWeight) : 1.0;
    }

    /**
     * Combined P+S residual: {@code sqrt(Σ w r² / Σ w)} with {@code w = pWeight} for P and {@code 1} for S.
     * Non-finite residuals are skipped.
     */
    public static double weightedRms(double[] residual, double[][] lagTable, double pWeight) {
        if (residual == null || residual.length == 0) {
            return 0.0;
        }
        double wss = 0.0;
        double wsum = 0.0;
        int n = Math.min(residual.length, lagTable == null ? 0 : lagTable.length);
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(residual[i])) {
                continue;
            }
            double w = phaseWeight(lagTable[i], pWeight);
            if (!(w > 0.0)) {
                continue;
            }
            wss += w * residual[i] * residual[i];
            wsum += w;
        }
        if (!(wsum > 0.0)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.sqrt(wss / wsum);
    }

    /** Weighted sum of squared residuals (MCMC likelihood). */
    public static double weightedSumSq(double[] residual, double[][] lagTable, double pWeight) {
        if (residual == null || residual.length == 0) {
            return 0.0;
        }
        double wss = 0.0;
        int n = Math.min(residual.length, lagTable == null ? 0 : lagTable.length);
        boolean any = false;
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(residual[i])) {
                continue;
            }
            double w = phaseWeight(lagTable[i], pWeight);
            if (!(w > 0.0)) {
                continue;
            }
            wss += w * residual[i] * residual[i];
            any = true;
        }
        return any ? wss : Double.POSITIVE_INFINITY;
    }

    /** Observed-minus-calculated pair lags for mixed P/S rows. */
    public double[] pairResiduals(double[][] stnTable, int[] idxList, Point point, double[][] lagTable) {
        boolean[] ph = DatPair.phasesPresent(lagTable);
        double[][] ts = travelTimes(stnTable, idxList, point, ph[0], ph[1]);
        return DatPair.residual(lagTable, ts[0], ts[1]);
    }

    /** The travel-time table in use, or {@code null} when travel times come from a velocity model. */
    public TravelTimeTable getTravelTimeTable() {
        return travelTimeTable;
    }

    /** True if a source here is valid; always true for velocity models, the table range otherwise. */
    public boolean isSourceInRange(double lat, double lon, double depthKm) {
        if (!insideLocationBox(lat, lon, depthKm)) {
            return false;
        }
        return travelTimeTable == null || travelTimeTable.containsSource(lat, lon, depthKm);
    }

    private boolean hasLocationBox() {
        return locLonMin != null && locLonMax != null && locLatMin != null && locLatMax != null
            && locLonMax > locLonMin && locLatMax > locLatMin;
    }

    private boolean insideLocationBox(double lat, double lon, double depthKm) {
        if (!hasLocationBox()) {
            return true;
        }
        if (lat < locLatMin || lat > locLatMax || lon < locLonMin || lon > locLonMax) {
            return false;
        }
        if (locMinDepthKm != null && depthKm < locMinDepthKm) {
            return false;
        }
        return true;
    }

    /** Shrinks a station search box so it stays inside the Model Range, if one is set. */
    public StationSearchBox clipSearchBox(StationSearchBox box) {
        if (box == null || !hasLocationBox()) {
            return box;
        }
        double lat0 = Math.max(locLatMin, box.centerLat - box.latRange);
        double lat1 = Math.min(locLatMax, box.centerLat + box.latRange);
        double lon0 = Math.max(locLonMin, box.centerLon - box.lonRange);
        double lon1 = Math.min(locLonMax, box.centerLon + box.lonRange);
        if (!(lat1 > lat0) || !(lon1 > lon0)) {
            lat0 = locLatMin;
            lat1 = locLatMax;
            lon0 = locLonMin;
            lon1 = locLonMax;
        }
        double cLat = 0.5 * (lat0 + lat1);
        double cLon = 0.5 * (lon0 + lon1);
        double cDep = box.centerDep;
        if (locMinDepthKm != null && cDep < locMinDepthKm) {
            cDep = locMinDepthKm;
        }
        return new StationSearchBox(cLat, cLon, cDep,
            Math.max(0.0, 0.5 * (lat1 - lat0)),
            Math.max(0.0, 0.5 * (lon1 - lon0)),
            box.depRange, box.nStations, box.usedHeaderDepth);
    }

    /**
     * Flags the solution as an outlier ({@code ERR}) when it lies outside the travel-time table range (the range is
     * interpreted like the airquake / depth limit of a velocity model).
     *
     * @return true if the point was flagged
     */
    public boolean markIfOutOfRange(Point point) {
        if (isSourceInRange(point.getLat(), point.getLon(), point.getDep())) {
            return false;
        }
        logger.warning(String.format(java.util.Locale.ROOT,
            "Solution (lat=%.4f, lon=%.4f, depth=%.2f km) is outside the travel-time table range; marked ERR.",
            point.getLat(), point.getLon(), point.getDep()));
        SolverLogger.warning("Solution outside the travel-time table range; marked ERR.");
        point.setType("ERR");
        return true;
    }

    /** Half-width multiplier on the sample std of threshold-passing stations. */
    public static final double DEFAULT_RANGE_SIGMA = 2.0;
    /** Depth used when the .dat header looks like a station/COR centroid (~0 km). */
    public static final double DEFAULT_DEPTH_KM = 5.0;
    /** Default multi-start depths (km) when the header is not a catalog-like event. */
    public static final double[] DEFAULT_DEPTH_SEEDS_KM = { 1.0, 5.0, 10.0 };
    /** Merge seeds closer than this (km). */
    public static final double DEPTH_SEED_DEDUP_KM = 0.25;
    /** Floor on horizontal half-width so a tight cluster still has a searchable box. */
    public static final double MIN_RANGE_DEG = 0.02;
    /** Floor on depth half-width (km). */
    public static final double MIN_DEP_RANGE_KM = 3.0;
    /** Header depths below this are treated as station/COR, not an event. */
    public static final double MIN_EVENT_DEPTH_KM = 0.5;
    /** Legacy fallback when no used stations are available. */
    public static final double LEGACY_LAT_RANGE_DEG = 1.0;
    public static final double LEGACY_LON_RANGE_DEG = 1.0;
    public static final double LEGACY_DEP_RANGE_KM = 10.0;

    /**
     * Search box from the centroid and sample std of stations that passed the
     * correlation / weight threshold ({@code usedIdx}).
     */
    public static final class StationSearchBox {
        public final double centerLat;
        public final double centerLon;
        public final double centerDep;
        public final double latRange;
        public final double lonRange;
        public final double depRange;
        public final int nStations;
        public final boolean usedHeaderDepth;

        public StationSearchBox(double centerLat, double centerLon, double centerDep,
                                double latRange, double lonRange, double depRange,
                                int nStations, boolean usedHeaderDepth) {
            this.centerLat = centerLat;
            this.centerLon = centerLon;
            this.centerDep = centerDep;
            this.latRange = latRange;
            this.lonRange = lonRange;
            this.depRange = depRange;
            this.nStations = nStations;
            this.usedHeaderDepth = usedHeaderDepth;
        }

        public String summary() {
            return String.format(java.util.Locale.ROOT,
                "n=%d center=%.4f,%.4f,%.2f km  ±%.4f° ±%.4f° ±%.2f km  depth=%s",
                nStations, centerLat, centerLon, centerDep, latRange, lonRange, depRange,
                usedHeaderDepth ? "header" : "default");
        }
    }

    public static double sanitizeRangeSigma(double rangeSigma) {
        return Double.isFinite(rangeSigma) && rangeSigma > 0.0 ? rangeSigma : DEFAULT_RANGE_SIGMA;
    }

    public static double sanitizeDefaultDepthKm(double depthKm, double stnBottom, double hypBottom) {
        double v = Double.isFinite(depthKm) && depthKm > 0.0 ? depthKm : DEFAULT_DEPTH_KM;
        double lo = Math.max(stnBottom, 0.1);
        double hi = Math.max(lo, hypBottom);
        return Math.max(lo, Math.min(hi, v));
    }

    public static double readRangeSigma(JsonNode solver) {
        if (solver != null && solver.has("rangeSigma")) {
            return sanitizeRangeSigma(solver.get("rangeSigma").asDouble());
        }
        return DEFAULT_RANGE_SIGMA;
    }

    public static String formatDepthSeeds(double[] seeds) {
        if (seeds == null || seeds.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < seeds.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(java.util.Locale.ROOT, "%.3f", seeds[i]));
        }
        return sb.toString();
    }

    /**
     * Depth multi-start list from {@code depthSeeds} (comma text or JSON array).
     * Falls back to {@code defaultDepthKm}, then {@link #DEFAULT_DEPTH_SEEDS_KM}.
     * Catalog-like header depths are included. Values are clamped to
     * [{@code stnBottom}, {@code hypBottom}] and de-duplicated.
     */
    public static double[] readDepthSeeds(JsonNode solver, double headerDep,
                                          double stnBottom, double hypBottom) {
        List<Double> raw = new ArrayList<>();
        if (solver != null && solver.has("depthSeeds") && !solver.get("depthSeeds").isNull()) {
            JsonNode node = solver.get("depthSeeds");
            if (node.isArray()) {
                for (JsonNode x : node) {
                    if (x != null && x.isNumber()) {
                        raw.add(x.asDouble());
                    } else if (x != null && x.isTextual()) {
                        addDepthSeedTokens(x.asText(), raw);
                    }
                }
            } else {
                addDepthSeedTokens(node.asText(), raw);
            }
        } else if (solver != null && solver.has("defaultDepthKm")) {
            raw.add(solver.get("defaultDepthKm").asDouble());
        } else {
            for (double d : DEFAULT_DEPTH_SEEDS_KM) {
                raw.add(d);
            }
        }
        return sanitizeDepthSeeds(raw, headerDep, stnBottom, hypBottom);
    }

    public static void addDepthSeedTokens(String text, List<Double> out) {
        if (text == null || out == null) {
            return;
        }
        for (String tok : text.split("[,;\\s]+")) {
            if (tok.isEmpty()) {
                continue;
            }
            try {
                out.add(Double.parseDouble(tok));
            } catch (NumberFormatException ignored) {
                // skip invalid tokens
            }
        }
    }

    public static double[] sanitizeDepthSeeds(List<Double> raw, double headerDep,
                                              double stnBottom, double hypBottom) {
        double lo = Math.max(stnBottom, 0.1);
        double hi = Math.max(lo, hypBottom);
        List<Double> clamped = new ArrayList<>();
        if (raw != null) {
            for (Double v : raw) {
                if (v == null || !Double.isFinite(v) || v <= 0.0) {
                    continue;
                }
                clamped.add(Math.max(lo, Math.min(hi, v)));
            }
        }
        if (Double.isFinite(headerDep) && headerDep >= MIN_EVENT_DEPTH_KM && headerDep <= hypBottom) {
            clamped.add(headerDep);
        }
        Collections.sort(clamped);
        List<Double> unique = new ArrayList<>();
        for (double v : clamped) {
            if (unique.isEmpty() || Math.abs(v - unique.get(unique.size() - 1)) > DEPTH_SEED_DEDUP_KM) {
                unique.add(v);
            }
        }
        if (unique.isEmpty()) {
            unique.add(sanitizeDefaultDepthKm(DEFAULT_DEPTH_KM, stnBottom, hypBottom));
        }
        double[] seeds = new double[unique.size()];
        for (int i = 0; i < unique.size(); i++) {
            seeds[i] = unique.get(i);
        }
        return seeds;
    }

    /**
     * Horizontal center = mean of used stations; half-width = {@code rangeSigma} × sample std
     * (floored at {@link #MIN_RANGE_DEG}). Depth center keeps a catalog-like header depth,
     * otherwise {@code defaultDepthKm}. Depth half-width follows the horizontal aperture in km.
     */
    public static StationSearchBox searchBoxFromUsedStations(
            double[][] stationTable, int[] usedIdx,
            double headerLat, double headerLon, double headerDep,
            double rangeSigma, double defaultDepthKm,
            double stnBottom, double hypBottom) {
        double sigma = sanitizeRangeSigma(rangeSigma);
        boolean usedHeaderDepth = Double.isFinite(headerDep)
            && headerDep >= MIN_EVENT_DEPTH_KM
            && headerDep <= hypBottom;
        double centerDep = usedHeaderDepth
            ? headerDep
            : sanitizeDefaultDepthKm(defaultDepthKm, stnBottom, hypBottom);

        int n = 0;
        if (stationTable != null && usedIdx != null) {
            for (int idx : usedIdx) {
                if (idx >= 0 && idx < stationTable.length) {
                    n++;
                }
            }
        }
        if (n == 0) {
            return new StationSearchBox(
                headerLat, headerLon, centerDep,
                LEGACY_LAT_RANGE_DEG, LEGACY_LON_RANGE_DEG, LEGACY_DEP_RANGE_KM,
                0, usedHeaderDepth);
        }

        double sumLat = 0.0;
        double sumLon = 0.0;
        for (int idx : usedIdx) {
            if (idx < 0 || idx >= stationTable.length) {
                continue;
            }
            sumLat += stationTable[idx][0];
            sumLon += stationTable[idx][1];
        }
        double meanLat = sumLat / n;
        double meanLon = sumLon / n;

        double stdLat = 0.0;
        double stdLon = 0.0;
        if (n >= 2) {
            double ssLat = 0.0;
            double ssLon = 0.0;
            for (int idx : usedIdx) {
                if (idx < 0 || idx >= stationTable.length) {
                    continue;
                }
                double dLat = stationTable[idx][0] - meanLat;
                double dLon = stationTable[idx][1] - meanLon;
                ssLat += dLat * dLat;
                ssLon += dLon * dLon;
            }
            stdLat = Math.sqrt(ssLat / (n - 1));
            stdLon = Math.sqrt(ssLon / (n - 1));
        }

        double latRange = Math.max(MIN_RANGE_DEG, sigma * stdLat);
        double lonRange = Math.max(MIN_RANGE_DEG, sigma * stdLon);
        double cosLat = Math.cos(Math.toRadians(meanLat));
        double hStdKm = DEG2KM * Math.hypot(stdLat, stdLon * Math.abs(cosLat));
        double depRange = Math.max(MIN_DEP_RANGE_KM, sigma * hStdKm);

        return new StationSearchBox(
            meanLat, meanLon, centerDep, latRange, lonRange, depRange, n, usedHeaderDepth);
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
