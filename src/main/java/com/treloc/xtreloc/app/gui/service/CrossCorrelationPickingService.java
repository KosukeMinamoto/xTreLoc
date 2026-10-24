package com.treloc.xtreloc.app.gui.service;

import com.treloc.xtreloc.io.Station;

import edu.sc.seis.seisFile.sac.SacHeader;
import edu.sc.seis.seisFile.sac.SacTimeSeries;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Sliding-window cross-correlation between station pairs on a processed trace per station: raw
 * (vertical preferred), a horizontal vector envelope (after optional filtering and smoothing), or a
 * characteristic function (CF) following {@code correlation_picker.py} (eigen + STA/LTA + central
 * difference). Writes one solver {@code .dat} per time window (hypocenter = centroid of correlated
 * stations, errors 999, type {@code COR}, then {@code stnk stnl lagt coef} rows).
 */
public final class CrossCorrelationPickingService {

    private static final Logger LOG = Logger.getLogger(CrossCorrelationPickingService.class.getName());
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyMMdd.HHmmss", Locale.US);

    /** Default subdirectory under the output parent for lag-table {@code .dat} files (batch and picking). */
    public static final String DEFAULT_CORRELATION_SUBDIR = "Correlation";

    /** Station code with optional WGS84 coordinates (distance filter and COR hypocenter centroid). */
    public static final class StationCoord {
        public final String code;
        public final double lat;
        public final double lon;
        /** Station depth in km (positive down). {@code NaN} if unknown. */
        public final double depthKm;
        public final boolean hasCoords;

        public StationCoord(String code, double lat, double lon, boolean hasCoords) {
            this(code, lat, lon, Double.NaN, hasCoords);
        }

        public StationCoord(String code, double lat, double lon, double depthKm, boolean hasCoords) {
            this.code = code;
            this.lat = lat;
            this.lon = lon;
            this.depthKm = depthKm;
            this.hasCoords = hasCoords;
        }
    }

    public enum SignalKind {
        /** Preferred vertical (else first component), optionally filtered. */
        RAW,
        /** Smoothed √(N–E energy) on two horizontals. */
        ENVELOPE,
        /** Eigen + recursive STA/LTA + positive central difference. */
        CHARACTERISTIC
    }

    public static final class Params {
        public SignalKind signalKind = SignalKind.CHARACTERISTIC;
        /** When true, each SAC is used as-is (already a CF / envelope); no detrend, filter, or pair CF. */
        public boolean treatAsPrepared = false;
        /** Moving eigen window (CF) or envelope smooth length (envelope), seconds. */
        public double eigWindowSec = 2.0;
        public double staWindowSec = 0.5;
        public double ltaWindowSec = 10.0;
        public double ccWindowSec = 10.0;
        /** Fraction of cc window for step (Python: overlap rate × window length as step). */
        public double ccOverlapRate = 0.25;
        /** Max inter-station distance (km); ignored when no coordinates loaded. */
        public double ccMaxDistKm = 300.0;
        /**
         * Minimum absolute correlation coefficient ({@code |coef|}) for a pair to count toward an event and
         * to be written to the lag table. {@code 0} = include all pairs (no coefficient gate).
         */
        public double ccCoefThreshold = 0.0;
        /**
         * Minimum number of pairs with {@code |coef| ≥ ccCoefThreshold} required to write a time window.
         * {@code null} = no requirement (every window is written, subject to rows meeting the coef gate).
         */
        public Integer ccMinPairCount = null;
        /**
         * Optional filter before processing: both null → none; low only → high-pass; high only → low-pass;
         * both → band-pass.
         */
        public Float filterFreqMin = null;
        public Float filterFreqMax = null;
    }

    public interface Progress {
        void onMessage(String message);

        default void onProgress(int done, int total, String message) {
            onMessage(message);
        }
    }

    /**
     * In-memory result of loading SAC under one event root and building per-station CF / envelope rows.
     */
    public static final class LoadedEventData {
        public final List<String> cfCodes;
        public final List<double[]> cfRows;
        /** Horizontal trace per station (header/time metadata for derived SAC output). */
        public final List<SacTimeSeries> headerTemplatePerStation;
        public final double sfreq;
        public final LocalDateTime windowRefTime;

        public LoadedEventData(List<String> cfCodes, List<double[]> cfRows, List<SacTimeSeries> headerTemplatePerStation,
                              double sfreq, LocalDateTime windowRefTime) {
            this.cfCodes = cfCodes;
            this.cfRows = cfRows;
            this.headerTemplatePerStation = headerTemplatePerStation;
            this.sfreq = sfreq;
            this.windowRefTime = windowRefTime;
        }
    }

    private CrossCorrelationPickingService() {}

    /**
     * Loads optional {@code STN lat lon} lines (degrees). Lines starting with # skipped.
     */
    public static Map<String, StationCoord> loadStationCoords(Path path) throws IOException {
        Map<String, StationCoord> map = new LinkedHashMap<>();
        if (path == null || !Files.isRegularFile(path)) {
            return map;
        }
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\\s+");
            if (p.length >= 3) {
                try {
                    double lat = Double.parseDouble(p[1]);
                    double lon = Double.parseDouble(p[2]);
                    map.put(p[0].trim(), new StationCoord(p[0].trim(), lat, lon, true));
                } catch (NumberFormatException ignored) {
                    // channel file line etc.
                }
            }
        }
        return map;
    }

    /**
     * Loads station coordinates for inter-station distance filtering: each line either
     * {@code code lat lon dep pc sc} (six or more columns, solver-style {@link Station}) or
     * {@code code lat lon} (three columns, degrees). Comments and empty lines skipped.
     */
    public static Map<String, StationCoord> loadCoordsForDistanceFilter(Path path) throws IOException {
        Map<String, StationCoord> out = new LinkedHashMap<>();
        if (path == null || !Files.isRegularFile(path)) {
            return out;
        }
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\\s+");
            if (p.length >= 6) {
                try {
                    Station st = new Station(
                        p[0].trim(),
                        Double.parseDouble(p[1]),
                        Double.parseDouble(p[2]),
                        Double.parseDouble(p[3]),
                        Double.parseDouble(p[4]),
                        Double.parseDouble(p[5]));
                    out.put(st.getCode(), new StationCoord(st.getCode(), st.getLat(), st.getLon(),
                        -st.getDep() / 1000.0, true));
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Skip station line (6+ col): " + line + " — " + e.getMessage());
                }
            } else if (p.length >= 3) {
                try {
                    double lat = Double.parseDouble(p[1]);
                    double lon = Double.parseDouble(p[2]);
                    if (Math.abs(lat) <= 90.0 + 1e-6 && Math.abs(lon) <= 180.0 + 1e-6) {
                        String code = p[0].trim();
                        out.put(code, new StationCoord(code, lat, lon, true));
                    }
                } catch (NumberFormatException ignored) {
                    // e.g. channel-mapping line station component number
                }
            }
        }
        return out;
    }

    /**
     * Runs CF + cross-correlation on all {@code .sac} files under {@code sacRoot}, grouped by {@link SacHeader#getKstnm()}.
     */
    public static void run(Path sacRoot, Path outputDir, Params params, Map<String, StationCoord> coords,
                          Progress progress) throws IOException {
        Objects.requireNonNull(sacRoot, "sacRoot");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(params, "params");
        Files.createDirectories(outputDir);
        LoadedEventData ev = loadAndComputeTraces(sacRoot, params, progress);
        writeSlidingWindowCorrelation(ev, outputDir, "", params, coords, progress);
    }

    /**
     * CF or envelope only: writes {@code outputParent/cfSubdir/eventDirName/*.sac} (one derived trace per station).
     */
    public static void runDerivedSacOnly(Path sacRoot, Path outputParent, String eventDirName, Params params,
                                        String cfSubdir, Progress progress) throws IOException {
        Objects.requireNonNull(sacRoot, "sacRoot");
        Objects.requireNonNull(outputParent, "outputParent");
        Objects.requireNonNull(eventDirName, "eventDirName");
        Objects.requireNonNull(params, "params");
        String cfName = cfSubdir == null || cfSubdir.isEmpty() ? "CF" : cfSubdir;
        Path cfDir = outputParent.resolve(cfName).resolve(eventDirName);
        Files.createDirectories(cfDir);
        writeDerivedSacFilesStreaming(sacRoot, cfDir, params, progress, Integer.MAX_VALUE);
    }

    /**
     * CF or envelope for <b>one processing unit</b>: only {@code .sac} files directly inside {@code unitDir}
     * (no recursion) are used; derived traces are written to {@code outDir} (one SAC per station).
     */
    public static void runDerivedSacToDir(Path unitDir, Path outDir, Params params, Progress progress)
            throws IOException {
        Objects.requireNonNull(unitDir, "unitDir");
        Objects.requireNonNull(outDir, "outDir");
        Objects.requireNonNull(params, "params");
        writeDerivedSacFilesStreaming(unitDir, outDir, params, progress, 1);
    }

    /** Station + the two traces used as H1 / H2 for CF or envelope. */
    public static final class StationChannelPair {
        public final String station;
        public final Path first;
        public final Path second;

        public StationChannelPair(String station, Path first, Path second) {
            this.station = station;
            this.first = first;
            this.second = second;
        }
    }

    /**
     * Writes one CF / envelope SAC per pair into {@code outDir}.
     *
     * @return number of station files written
     */
    public static int writeDerivedForStations(List<StationChannelPair> pairs, Path outDir, Params params,
                                             Progress progress) throws IOException {
        Objects.requireNonNull(pairs, "pairs");
        Objects.requireNonNull(outDir, "outDir");
        Objects.requireNonNull(params, "params");
        Files.createDirectories(outDir);
        String tag = SacDerivedTraceWriter.componentSuffix(params.signalKind);
        int total = pairs.size();
        int ok = 0;
        int i = 0;
        for (StationChannelPair pair : pairs) {
            i++;
            if (pair == null || pair.first == null || pair.second == null) {
                continue;
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Cancelled");
            }
            String label = pair.station == null || pair.station.isBlank() ? pair.first.getFileName().toString()
                : pair.station;
            if (progress != null) {
                progress.onProgress(i - 1, total, "CF " + i + "/" + total + " " + label);
            }
            try {
                SacTimeSeries h1 = WaveProcessor.read(pair.first.toString());
                SacTimeSeries h2 = WaveProcessor.read(pair.second.toString());
                double[] row = params.signalKind == SignalKind.ENVELOPE
                    ? computeEnvelope(h1, h2, params)
                    : computeCharacteristic(h1, h2, params);
                String code = SacDerivedTraceWriter.safeFileToken(
                    pair.station == null || pair.station.isBlank()
                        ? h1.getHeader().getKstnm().trim() : pair.station);
                Path out = outDir.resolve(code + "_" + tag + ".sac");
                SacDerivedTraceWriter.write(h1, row, tag, out);
                ok++;
                if (progress != null) {
                    progress.onProgress(i, total, "Wrote " + out.getFileName());
                }
            } catch (IOException ex) {
                if ("Cancelled".equals(ex.getMessage())) {
                    throw ex;
                }
                LOG.log(Level.WARNING, "Skip station " + pair.station + ": " + ex.getMessage(), ex);
                if (progress != null) {
                    progress.onMessage("Skip " + pair.station + ": " + ex.getMessage());
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Skip station " + pair.station + ": " + ex.getMessage(), ex);
                if (progress != null) {
                    progress.onMessage("Skip " + pair.station + ": " + ex.getMessage());
                }
            }
        }
        if (ok == 0) {
            throw new IOException("No CF / envelope traces written");
        }
        return ok;
    }

    /**
     * Sliding-window CC on an explicit list of SAC files (grouped by station).
     * Prepared CF / envelope traces stay as {@code float[]} and only each CC window is copied,
     * so day-long files do not allocate a second full {@code double[]} per station.
     */
    public static void runCorrelationFromFiles(List<Path> sacFiles, Path outDir, Params params,
                                              Map<String, StationCoord> coords, Progress progress)
            throws IOException {
        Objects.requireNonNull(sacFiles, "sacFiles");
        Objects.requireNonNull(outDir, "outDir");
        Objects.requireNonNull(params, "params");
        if (sacFiles.size() < 2) {
            throw new IOException("Select at least two SAC files");
        }
        if (params.treatAsPrepared || params.signalKind == SignalKind.RAW) {
            PreparedEvent ev = loadPreparedStations(sacFiles, params, progress);
            writeSlidingWindows(ev.codes, ev.npts, ev.sfreq, ev.windowRefTime,
                (s, ps, dest) -> copyFloatWindow(ev.rows.get(s), ps, dest),
                outDir, "", params, mergeCoords(coords, ev.headerCoords), progress);
            return;
        }
        Path tmpRoot = Files.createTempDirectory("xtreloc-cc-");
        try {
            int i = 0;
            for (Path src : sacFiles) {
                if (src == null || !Files.isRegularFile(src)) {
                    continue;
                }
                Files.copy(src, tmpRoot.resolve(i++ + "_" + src.getFileName()),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            runCorrelationToDir(tmpRoot, outDir, params, coords, progress);
        } finally {
            try (Stream<Path> walk = Files.walk(tmpRoot)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
        }
    }

    private static final class PreparedEvent {
        final List<String> codes;
        final List<float[]> rows;
        final int npts;
        final double sfreq;
        final LocalDateTime windowRefTime;
        final Map<String, StationCoord> headerCoords;

        PreparedEvent(List<String> codes, List<float[]> rows, int npts, double sfreq, LocalDateTime windowRefTime,
                      Map<String, StationCoord> headerCoords) {
            this.codes = codes;
            this.rows = rows;
            this.npts = npts;
            this.sfreq = sfreq;
            this.windowRefTime = windowRefTime;
            this.headerCoords = headerCoords == null ? Map.of() : headerCoords;
        }
    }

    @FunctionalInterface
    private interface WindowFiller {
        void copy(int stationIndex, int start, double[] dest);
    }

    private static void copyFloatWindow(float[] y, int start, double[] dest) {
        for (int i = 0; i < dest.length; i++) {
            dest[i] = y[start + i];
        }
    }

    private static boolean isWaveformName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".sac") || n.endsWith(".s");
    }

    private static PreparedEvent loadPreparedStations(List<Path> sacFiles, Params params, Progress progress)
            throws IOException {
        Map<String, float[]> rows = new TreeMap<>();
        Map<String, Double> deltas = new TreeMap<>();
        Map<String, LocalDateTime> starts = new TreeMap<>();
        Map<String, StationCoord> headerCoords = new LinkedHashMap<>();
        int total = sacFiles.size();
        int idx = 0;
        for (Path src : sacFiles) {
            idx++;
            if (src == null || !Files.isRegularFile(src) || !isWaveformName(src.getFileName().toString())) {
                continue;
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Cancelled");
            }
            if (progress != null) {
                progress.onProgress(idx - 1, total, "Loading " + idx + "/" + total + " " + src.getFileName());
            }
            try {
                SacTimeSeries sac = WaveProcessor.read(src.toString());
                String st = sac.getHeader().getKstnm() == null ? "" : sac.getHeader().getKstnm().trim().replace(" ", "");
                if (st.isEmpty()) {
                    st = src.getFileName().toString();
                }
                float[] y = sac.getY();
                if (y == null || y.length < 16) {
                    if (progress != null) {
                        progress.onMessage("Skip " + st + ": trace too short");
                    }
                    continue;
                }
                if (params.treatAsPrepared) {
                    rows.put(st, y);
                } else {
                    rows.put(st, toFloat(computeRaw(sac, params)));
                }
                deltas.put(st, (double) sac.getHeader().getDelta());
                starts.put(st, WaveProcessor.getStarttime(sac));
                StationCoord hc = coordFromSac(st, sac.getHeader());
                if (hc != null) {
                    headerCoords.put(st, hc);
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Skip SAC " + src + ": " + ex.getMessage(), ex);
                if (progress != null) {
                    progress.onMessage("Skip " + src.getFileName() + ": " + ex.getMessage());
                }
            }
        }
        if (rows.size() < 2) {
            throw new IOException("Need at least two prepared CF / envelope SAC files; got " + rows.size());
        }
        List<String> codes = new ArrayList<>(rows.keySet());
        List<float[]> series = new ArrayList<>(codes.size());
        int npts = -1;
        double sfreq = -1;
        LocalDateTime t0 = null;
        for (String code : codes) {
            float[] y = rows.get(code);
            if (npts < 0) {
                npts = y.length;
            } else if (y.length != npts) {
                throw new IOException("CF length mismatch: expected " + npts + ", got " + y.length
                    + " (align SAC time ranges before running).");
            }
            series.add(y);
            double dt = deltas.getOrDefault(code, 0.01);
            if (sfreq < 0 && dt > 0) {
                sfreq = 1.0 / dt;
            }
            if (t0 == null) {
                t0 = starts.get(code);
            }
        }
        if (progress != null) {
            progress.onMessage("Loaded " + codes.size() + " CF traces × " + npts + " samples (float)");
        }
        return new PreparedEvent(codes, series, npts, sfreq, t0, headerCoords);
    }

    private static float[] toFloat(double[] y) {
        float[] out = new float[y.length];
        for (int i = 0; i < y.length; i++) {
            out[i] = (float) y[i];
        }
        return out;
    }

    /**
     * Sliding-window cross-correlation for <b>one processing unit</b>: only {@code .sac} files directly inside
     * {@code unitDir} are used; lag tables {@code yyMMdd.HHmmss.dat} are written directly into {@code outDir}.
     */
    public static void runCorrelationToDir(Path unitDir, Path outDir, Params params,
                                           Map<String, StationCoord> coords, Progress progress) throws IOException {
        Objects.requireNonNull(unitDir, "unitDir");
        Objects.requireNonNull(outDir, "outDir");
        Objects.requireNonNull(params, "params");
        Files.createDirectories(outDir);
        LoadedEventData ev = loadAndComputeTraces(unitDir, params, progress, 1);
        writeSlidingWindowCorrelation(ev, outDir, "", params, coords, progress);
    }

    /**
     * Loads SAC (recursive) under {@code sacRoot}, builds CF / envelope rows per station.
     */
    public static LoadedEventData loadAndComputeTraces(Path sacRoot, Params params, Progress progress) throws IOException {
        return loadAndComputeTraces(sacRoot, params, progress, Integer.MAX_VALUE);
    }

    /**
     * Same as {@link #loadAndComputeTraces(Path, Params, Progress)} but limits the directory walk
     * ({@code 1} = only files directly inside {@code sacRoot}).
     */
    public static LoadedEventData loadAndComputeTraces(Path sacRoot, Params params, Progress progress,
                                                       int maxDepth) throws IOException {
        Objects.requireNonNull(sacRoot, "sacRoot");
        Objects.requireNonNull(params, "params");

        Map<String, StationTraceAccumulator> byStation = new TreeMap<>();
        int[] sacCount = new int[] { 0 };
        try (Stream<Path> walk = Files.walk(sacRoot, maxDepth)) {
            walk.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return n.endsWith(".sac");
                })
                .forEach(p -> {
                    try {
                        SacTimeSeries sac = WaveProcessor.read(p.toString());
                        String st = sac.getHeader().getKstnm().trim();
                        if (st.isEmpty()) {
                            st = p.getFileName().toString();
                        }
                        byStation.computeIfAbsent(st, k -> new StationTraceAccumulator()).accept(sac);
                        sacCount[0]++;
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Skip SAC " + p + ": " + e.getMessage(), e);
                    }
                });
        }
        if (sacCount[0] == 0) {
            throw new IOException("No .sac files under " + sacRoot);
        }
        List<String> codeList = new ArrayList<>(byStation.keySet());

        List<double[]> cfRows = new ArrayList<>();
        List<String> cfCodes = new ArrayList<>();
        List<SacTimeSeries> headerTemplates = new ArrayList<>();
        double sfreq = -1;
        LocalDateTime windowRefTime = null;

        for (String code : codeList) {
            StationTraceAccumulator acc = byStation.get(code);
            SacTimeSeries template;
            double[] row;
            if (params.treatAsPrepared || params.signalKind == SignalKind.RAW) {
                SacTimeSeries raw = acc.chooseRaw();
                if (raw == null) {
                    if (progress != null) {
                        progress.onMessage("Skip station " + code + ": no usable trace");
                    }
                    continue;
                }
                row = params.treatAsPrepared ? samplesAsIs(raw) : computeRaw(raw, params);
                template = raw;
                if (sfreq < 0) {
                    sfreq = 1.0 / Math.max(1e-6, raw.getHeader().getDelta());
                }
                if (windowRefTime == null) {
                    windowRefTime = WaveProcessor.getStarttime(raw);
                }
            } else {
                StationPair pair = acc.choosePair();
                if (pair == null) {
                    if (progress != null) {
                        progress.onMessage("Skip station " + code + ": need ≥2 horizontal traces");
                    }
                    continue;
                }
                template = pair.first;
                StationFeature feat = params.signalKind == SignalKind.ENVELOPE
                    ? computeEnvelopeFeature(pair.first, pair.second, params)
                    : computeCharacteristicFeature(pair.first, pair.second, params);
                row = feat.y;
                if (sfreq < 0) {
                    sfreq = 1.0 / feat.deltaSec;
                }
                if (windowRefTime == null) {
                    windowRefTime = java.time.Instant.ofEpochMilli(feat.startMillis)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                }
            }
            cfRows.add(row);
            cfCodes.add(code);
            headerTemplates.add(template);
        }

        if (cfRows.size() < 2) {
            throw new IOException(params.treatAsPrepared
                ? "Need at least two prepared CF / envelope SAC files; got " + cfRows.size()
                : "Need at least two stations with usable traces; got " + cfRows.size());
        }

        int npts = cfRows.get(0).length;
        for (double[] r : cfRows) {
            if (r.length != npts) {
                throw new IOException("CF length mismatch: expected " + npts + ", got " + r.length
                    + " (align SAC time ranges before running).");
            }
        }

        return new LoadedEventData(cfCodes, cfRows, headerTemplates, sfreq, windowRefTime);
    }

    private static final class StationPair {
        private final SacTimeSeries first;
        private final SacTimeSeries second;

        private StationPair(SacTimeSeries first, SacTimeSeries second) {
            this.first = first;
            this.second = second;
        }
    }

    /**
     * Keeps only minimal traces needed for CF/Envelope (at most two horizontals, plus two fallback traces).
     * This prevents retaining every SAC in memory for large event folders.
     */
    private static final class StationTraceAccumulator {
        private final Map<String, SacTimeSeries> horizontalsByCmp = new TreeMap<>();
        private SacTimeSeries vertical;
        private SacTimeSeries fallbackA;
        private SacTimeSeries fallbackB;

        private void accept(SacTimeSeries sac) {
            if (fallbackA == null) {
                fallbackA = sac;
            } else if (fallbackB == null) {
                fallbackB = sac;
            }
            String cmp = sac.getHeader().getKcmpnm().trim().toUpperCase(Locale.ROOT);
            if (cmp.contains("Z") || cmp.contains("U") || cmp.contains("V")) {
                if (vertical == null) {
                    vertical = sac;
                }
            } else {
                horizontalsByCmp.putIfAbsent(cmp, sac);
            }
        }

        private SacTimeSeries chooseRaw() {
            if (vertical != null) {
                return vertical;
            }
            if (!horizontalsByCmp.isEmpty()) {
                return horizontalsByCmp.values().iterator().next();
            }
            return fallbackA;
        }

        private StationPair choosePair() {
            if (horizontalsByCmp.size() >= 2) {
                List<SacTimeSeries> hs = new ArrayList<>(horizontalsByCmp.values());
                return new StationPair(hs.get(0), hs.get(1));
            }
            if (fallbackA != null && fallbackB != null) {
                return new StationPair(fallbackA, fallbackB);
            }
            return null;
        }
    }

    private static final class PathPair {
        private final Path first;
        private final Path second;

        private PathPair(Path first, Path second) {
            this.first = first;
            this.second = second;
        }
    }

    /**
     * Path-only accumulator for CF export: avoids retaining all SAC arrays in memory.
     */
    private static final class StationPathAccumulator {
        private final Map<String, Path> horizontalsByCmp = new TreeMap<>();
        private Path vertical;
        private Path fallbackA;
        private Path fallbackB;

        private void accept(Path path, String cmpUpper) {
            if (fallbackA == null) {
                fallbackA = path;
            } else if (fallbackB == null) {
                fallbackB = path;
            }
            if (cmpUpper.contains("Z") || cmpUpper.contains("U") || cmpUpper.contains("V")) {
                if (vertical == null) {
                    vertical = path;
                }
            } else {
                horizontalsByCmp.putIfAbsent(cmpUpper, path);
            }
        }

        private Path chooseRaw() {
            if (vertical != null) {
                return vertical;
            }
            if (!horizontalsByCmp.isEmpty()) {
                return horizontalsByCmp.values().iterator().next();
            }
            return fallbackA;
        }

        private PathPair choosePair() {
            if (horizontalsByCmp.size() >= 2) {
                List<Path> hs = new ArrayList<>(horizontalsByCmp.values());
                return new PathPair(hs.get(0), hs.get(1));
            }
            if (fallbackA != null && fallbackB != null) {
                return new PathPair(fallbackA, fallbackB);
            }
            return null;
        }
    }

    /**
     * Memory-friendly CF/envelope export for large events.
     * Reads each SAC only as needed per station pair and writes immediately.
     */
    private static void writeDerivedSacFilesStreaming(Path sacRoot, Path cfOutDir, Params params,
                                                      Progress progress, int maxDepth) throws IOException {
        Objects.requireNonNull(sacRoot, "sacRoot");
        Objects.requireNonNull(cfOutDir, "cfOutDir");
        Objects.requireNonNull(params, "params");
        Files.createDirectories(cfOutDir);

        Map<String, StationPathAccumulator> byStation = new TreeMap<>();
        int sacCount = 0;
        try (Stream<Path> walk = Files.walk(sacRoot, maxDepth)) {
            List<Path> sacFiles = walk
                .filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return n.endsWith(".sac");
                })
                .collect(java.util.stream.Collectors.toList());
            sacCount = sacFiles.size();
            for (Path p : sacFiles) {
                try {
                    SacTimeSeries sac = WaveProcessor.read(p.toString());
                    String st = sac.getHeader().getKstnm().trim();
                    if (st.isEmpty()) {
                        st = p.getFileName().toString();
                    }
                    String cmp = sac.getHeader().getKcmpnm().trim().toUpperCase(Locale.ROOT);
                    byStation.computeIfAbsent(st, k -> new StationPathAccumulator()).accept(p, cmp);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Skip SAC " + p + ": " + e.getMessage(), e);
                }
            }
        }
        if (sacCount == 0) {
            throw new IOException("No .sac files under " + sacRoot);
        }

        String tag = SacDerivedTraceWriter.componentSuffix(params.signalKind);
        int ok = 0;
        for (Map.Entry<String, StationPathAccumulator> e : byStation.entrySet()) {
            String code = e.getKey();
            try {
                SacTimeSeries template;
                double[] row;
                if (params.signalKind == SignalKind.RAW) {
                    Path rawPath = e.getValue().chooseRaw();
                    if (rawPath == null) {
                        if (progress != null) {
                            progress.onMessage("Skip station " + code + ": no usable trace");
                        }
                        continue;
                    }
                    template = WaveProcessor.read(rawPath.toString());
                    row = computeRaw(template, params);
                } else {
                    PathPair pair = e.getValue().choosePair();
                    if (pair == null) {
                        if (progress != null) {
                            progress.onMessage("Skip station " + code + ": need ≥2 horizontal traces");
                        }
                        continue;
                    }
                    SacTimeSeries h1 = WaveProcessor.read(pair.first.toString());
                    SacTimeSeries h2 = WaveProcessor.read(pair.second.toString());
                    template = h1;
                    row = params.signalKind == SignalKind.ENVELOPE
                        ? computeEnvelope(h1, h2, params)
                        : computeCharacteristic(h1, h2, params);
                }
                String safeCode = SacDerivedTraceWriter.safeFileToken(code);
                Path out = cfOutDir.resolve(safeCode + "_" + tag + ".sac");
                SacDerivedTraceWriter.write(template, row, tag, out);
                ok++;
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Skip station " + code + " in CF export: " + ex.getMessage(), ex);
            }
        }
        if (ok < 2) {
            throw new IOException("Need at least two stations with two horizontal components; got " + ok);
        }
        if (progress != null) {
            progress.onMessage("CF/envelope export finished: " + ok + " station traces written.");
        }
    }

    /**
     * @param fileNamePrefix prefix before {@code yyMMdd.HHmmss.dat} (e.g. {@code "20071201_"}); may be empty.
     */
    public static void writeSlidingWindowCorrelation(LoadedEventData ev, Path outputDir, String fileNamePrefix,
                                                     Params params, Map<String, StationCoord> coords,
                                                     Progress progress) throws IOException {
        Objects.requireNonNull(ev, "ev");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(params, "params");
        if (coords == null) {
            coords = Collections.emptyMap();
        }
        Files.createDirectories(outputDir);

        List<double[]> cfRows = ev.cfRows;
        int npts = cfRows.get(0).length;
        writeSlidingWindows(ev.cfCodes, npts, ev.sfreq, ev.windowRefTime,
            (s, ps, dest) -> System.arraycopy(cfRows.get(s), ps, dest, 0, dest.length),
            outputDir, fileNamePrefix, params,
            mergeCoords(coords, coordsFromTemplates(ev.cfCodes, ev.headerTemplatePerStation)), progress);
    }

    private static void writeSlidingWindows(List<String> cfCodes, int npts, double sfreq,
                                            LocalDateTime windowRefTime, WindowFiller filler,
                                            Path outputDir, String fileNamePrefix, Params params,
                                            Map<String, StationCoord> coords, Progress progress)
            throws IOException {
        Objects.requireNonNull(cfCodes, "cfCodes");
        Objects.requireNonNull(filler, "filler");
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(params, "params");
        if (coords == null) {
            coords = Collections.emptyMap();
        }
        Files.createDirectories(outputDir);

        int xwinSamp = Math.max(8, (int) Math.round(params.ccWindowSec * sfreq));
        int overSamp = Math.max(1, (int) Math.round(xwinSamp * params.ccOverlapRate));
        int nwin = (npts - xwinSamp) / overSamp + 1;
        if (nwin <= 0) {
            throw new IOException("CC window too long for trace length " + npts + " samples");
        }

        boolean useDist = !coords.isEmpty() && coords.values().stream().anyMatch(c -> c.hasCoords);
        List<int[]> pairIdx = new ArrayList<>();
        for (int i = 0; i < cfCodes.size(); i++) {
            for (int j = i + 1; j < cfCodes.size(); j++) {
                String a = cfCodes.get(i);
                String b = cfCodes.get(j);
                if (useDist) {
                    StationCoord ca = coords.get(a);
                    StationCoord cb = coords.get(b);
                    if (ca == null || cb == null || !ca.hasCoords || !cb.hasCoords) {
                        continue;
                    }
                    GeodesicData g = Geodesic.WGS84.Inverse(ca.lat, ca.lon, cb.lat, cb.lon);
                    if (g.s12 / 1000.0 > params.ccMaxDistKm) {
                        continue;
                    }
                }
                // Python itertools.combinations + codeList.index order: [stnl_idx, stnk_idx] → [j, i] for i<j
                pairIdx.add(new int[] { j, i });
            }
        }
        if (pairIdx.isEmpty()) {
            throw new IOException("No station pairs after filtering (check coordinates and max distance).");
        }

        int reportEvery = Math.max(1, nwin / 200);
        if (progress != null) {
            progress.onProgress(0, nwin, "Cross-correlation: " + cfCodes.size() + " stations, "
                + pairIdx.size() + " pairs, " + nwin + " windows.");
        }

        LocalDateTime strTime = windowRefTime != null ? windowRefTime : LocalDateTime.now();
        strTime = strTime.withSecond(0).withNano(0);
        java.time.Duration del = java.time.Duration.ofNanos(
            (long) (params.ccWindowSec * params.ccOverlapRate * 1_000_000_000L));
        String prefix = fileNamePrefix == null ? "" : fileNamePrefix;
        double[][] cut = new double[cfCodes.size()][xwinSamp];
        int[] lags = ScipySignalCompat.correlationLagsSameEqual(xwinSamp);
        double[] ccBuf = new double[xwinSamp];
        ThreadLocal<double[]> ccLocal = ThreadLocal.withInitial(() -> new double[xwinSamp]);
        boolean parallelPairs = pairIdx.size() >= 8;

        for (int wi = 0; wi < nwin; wi++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Cancelled");
            }
            int ps = overSamp * wi;
            if (ps + xwinSamp > npts) {
                break;
            }
            for (int s = 0; s < cut.length; s++) {
                filler.copy(s, ps, cut[s]);
            }
            normalizeRowsMinMax(cut);

            List<DifferentialDatWriter.Row> rowHits;
            if (parallelPairs) {
                rowHits = pairIdx.parallelStream()
                    .map(ij -> pairRow(cut, ij, lags, sfreq, params.ccCoefThreshold, cfCodes, ccLocal.get()))
                    .filter(row -> row != null)
                    .toList();
            } else {
                rowHits = new ArrayList<>(pairIdx.size());
                for (int[] ij : pairIdx) {
                    DifferentialDatWriter.Row row = pairRow(cut, ij, lags, sfreq, params.ccCoefThreshold, cfCodes, ccBuf);
                    if (row != null) {
                        rowHits.add(row);
                    }
                }
            }
            int goodPairs = rowHits.size();
            if (params.ccMinPairCount != null && goodPairs < params.ccMinPairCount) {
                strTime = strTime.plus(del);
                reportWindow(progress, wi, nwin, reportEvery);
                continue;
            }
            if (goodPairs == 0) {
                strTime = strTime.plus(del);
                reportWindow(progress, wi, nwin, reportEvery);
                continue;
            }
            Path outFile = outputDir.resolve(prefix + FILE_TS.format(strTime) + ".dat");
            DifferentialDatWriter.write(outFile, rowHits, coords);
            strTime = strTime.plus(del);
            reportWindow(progress, wi, nwin, reportEvery);
        }
    }

    private static void reportWindow(Progress progress, int wi, int nwin, int reportEvery) {
        if (progress == null) {
            return;
        }
        int done = wi + 1;
        if (wi < 4 || done == nwin || done % reportEvery == 0) {
            progress.onProgress(done, nwin, "CC window " + done + "/" + nwin);
        }
    }

    private static DifferentialDatWriter.Row pairRow(double[][] cut, int[] ij, int[] lags, double sfreq, double thresh,
                                                   List<String> cfCodes, double[] ccBuf) {
        int i0 = ij[0];
        int i1 = ij[1];
        double[] cf0 = cut[i0];
        double[] cf1 = cut[i1];
        double nrm = Math.sqrt(dot(cf0, cf0) * dot(cf1, cf1)) + Double.MIN_NORMAL;
        int peak = ScipySignalCompat.peakCorrelateSameEqual(cf0, cf1, ccBuf);
        double coef = ccBuf[peak] / nrm;
        if (Math.abs(coef) < thresh) {
            return null;
        }
        double lagSec = -lags[peak] / sfreq;
        return new DifferentialDatWriter.Row(cfCodes.get(i0), cfCodes.get(i1), lagSec, coef);
    }

    /** Station-file coords override SAC-header coords. */
    static Map<String, StationCoord> mergeCoords(Map<String, StationCoord> primary, Map<String, StationCoord> extra) {
        Map<String, StationCoord> out = new LinkedHashMap<>();
        if (extra != null) {
            out.putAll(extra);
        }
        if (primary != null) {
            for (Map.Entry<String, StationCoord> e : primary.entrySet()) {
                if (e.getValue() != null && e.getValue().hasCoords) {
                    out.put(e.getKey(), e.getValue());
                }
            }
        }
        return out;
    }

    static Map<String, StationCoord> coordsFromTemplates(List<String> codes, List<SacTimeSeries> templates) {
        Map<String, StationCoord> out = new LinkedHashMap<>();
        if (codes == null || templates == null) {
            return out;
        }
        int n = Math.min(codes.size(), templates.size());
        for (int i = 0; i < n; i++) {
            SacTimeSeries sac = templates.get(i);
            if (sac == null) {
                continue;
            }
            StationCoord c = coordFromSac(codes.get(i), sac.getHeader());
            if (c != null) {
                out.put(codes.get(i), c);
            }
        }
        return out;
    }

    static StationCoord coordFromSac(String code, SacHeader h) {
        if (h == null) {
            return null;
        }
        float lat = h.getStla();
        float lon = h.getStlo();
        if (!Float.isFinite(lat) || !Float.isFinite(lon) || lat < -90f || lat > 90f || lon < -180f || lon > 180f) {
            return null;
        }
        double dep = Double.NaN;
        float stel = h.getStel();
        if (Float.isFinite(stel) && Math.abs(stel) < 20_000f) {
            dep = -stel / 1000.0;
        }
        return new StationCoord(code, lat, lon, dep, true);
    }

    /**
     * Geographic centroid of the named stations that have coordinates.
     * Depth is the mean station depth in km (0 if unknown).
     */
    public static double[] centroidOf(Iterable<String> codes, Map<String, StationCoord> coords) {
        double slat = 0;
        double slon = 0;
        double sdep = 0;
        int n = 0;
        int nd = 0;
        if (codes != null && coords != null) {
            for (String code : codes) {
                StationCoord c = coords.get(code);
                if (c == null || !c.hasCoords) {
                    continue;
                }
                slat += c.lat;
                slon += c.lon;
                n++;
                if (Double.isFinite(c.depthKm)) {
                    sdep += c.depthKm;
                    nd++;
                }
            }
        }
        if (n == 0) {
            return new double[] {0.0, 0.0, 0.0};
        }
        return new double[] {slat / n, slon / n, nd > 0 ? sdep / nd : 0.0};
    }

    /**
     * Derived trace (CF / envelope / raw) with its own start time and sample interval,
     * so it can be plotted against raw components that have a different {@code delta} or start.
     */
    public static final class StationFeature {
        public final double[] y;
        public final long startMillis;
        public final double deltaSec;

        public StationFeature(double[] y, long startMillis, double deltaSec) {
            this.y = y == null ? new double[0] : y;
            this.startMillis = startMillis;
            this.deltaSec = deltaSec <= 0 ? 0.01 : deltaSec;
        }

        public long sampleMillis(int i) {
            return startMillis + Math.round(i * deltaSec * 1000.0);
        }
    }

    /**
     * In-memory CF / envelope / raw traces keyed by station, from already-loaded waveforms
     * (used to overlay special functions on the pick canvas).
     */
    public static Map<String, StationFeature> computeStationFeatures(SacTimeSeries[] waveforms, SignalKind kind, Params params)
            throws IOException {
        Objects.requireNonNull(waveforms, "waveforms");
        Params p = params == null ? new Params() : params;
        SignalKind k = kind == null ? p.signalKind : kind;
        Map<String, StationTraceAccumulator> byStation = new TreeMap<>();
        for (SacTimeSeries sac : waveforms) {
            if (sac == null) {
                continue;
            }
            String st = sac.getHeader().getKstnm().trim();
            if (st.isEmpty()) {
                st = "UNK";
            }
            byStation.computeIfAbsent(st, key -> new StationTraceAccumulator()).accept(sac);
        }
        Map<String, StationFeature> out = new LinkedHashMap<>();
        for (Map.Entry<String, StationTraceAccumulator> e : byStation.entrySet()) {
            try {
                if (k == SignalKind.RAW) {
                    SacTimeSeries raw = e.getValue().chooseRaw();
                    if (raw != null) {
                        out.put(e.getKey(), new StationFeature(
                            computeRaw(raw, p), WaveProcessor.startEpochMillis(raw), raw.getHeader().getDelta()));
                    }
                    continue;
                }
                StationPair pair = e.getValue().choosePair();
                if (pair == null) {
                    continue;
                }
                out.put(e.getKey(), k == SignalKind.ENVELOPE
                    ? computeEnvelopeFeature(pair.first, pair.second, p)
                    : computeCharacteristicFeature(pair.first, pair.second, p));
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Skip overlay for " + e.getKey() + ": " + ex.getMessage(), ex);
            }
        }
        return out;
    }

    /** Copies SAC amplitudes without detrend or filter (for CC on already-written CF). */
    public static double[] samplesAsIs(SacTimeSeries sac) throws IOException {
        if (sac == null || sac.getY() == null) {
            throw new IOException("empty trace");
        }
        float[] y = sac.getY();
        if (y.length < 16) {
            throw new IOException("trace too short");
        }
        double[] out = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            out[i] = y[i];
        }
        return out;
    }

    /**
     * Single-trace raw series after optional detrend + frequency filter (for CC on raw waveforms).
     */
    public static double[] computeRaw(SacTimeSeries sac, Params p) throws IOException {
        SacTimeSeries s = WaveProcessor.detrend(duplicateForProcessing(sac));
        WaveProcessor.applyOptionalFrequencyFilter(s, p.filterFreqMin, p.filterFreqMax);
        float[] y = s.getY();
        if (y.length < 16) {
            throw new IOException("trace too short");
        }
        double[] out = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            out[i] = y[i];
        }
        return out;
    }

    /**
     * Horizontal vector magnitude √(x²+y²) after optional filtering, then centered moving average
     * with length {@link Params#eigWindowSec}.
     */
    public static double[] computeEnvelope(SacTimeSeries h1, SacTimeSeries h2, Params p) throws IOException {
        return computeEnvelopeFeature(h1, h2, p).y;
    }

    public static StationFeature computeEnvelopeFeature(SacTimeSeries h1, SacTimeSeries h2, Params p)
            throws IOException {
        AlignedPair al = prepareAlignedPair(h1, h2, p);
        double[] env = new double[al.a.length];
        for (int i = 0; i < al.a.length; i++) {
            env[i] = Math.hypot(al.a[i], al.b[i]);
        }
        int winSamp = Math.max(1, (int) Math.round(p.eigWindowSec / al.deltaSec));
        return new StationFeature(movingAverage(env, winSamp), al.startMillis, al.deltaSec);
    }

    private static double[] movingAverage(double[] in, int win) {
        int n = in.length;
        double[] out = new double[n];
        if (win <= 1) {
            System.arraycopy(in, 0, out, 0, n);
            return out;
        }
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - win / 2);
            int b = Math.min(n, i + win / 2 + 1);
            double s = 0;
            for (int j = a; j < b; j++) {
                s += in[j];
            }
            out[i] = s / (b - a);
        }
        return out;
    }

    /**
     * Hendriyana & Tsuji–style CF: two horizontals → eigen (moving window) → STA/LTA → positive central difference.
     */
    public static double[] computeCharacteristic(SacTimeSeries h1, SacTimeSeries h2, Params p) throws IOException {
        return computeCharacteristicFeature(h1, h2, p).y;
    }

    public static StationFeature computeCharacteristicFeature(SacTimeSeries h1, SacTimeSeries h2, Params p)
            throws IOException {
        AlignedPair al = prepareAlignedPair(h1, h2, p);
        int winSamp = Math.max(4, (int) Math.round(p.eigWindowSec / al.deltaSec));
        int nsta = Math.max(2, (int) Math.round(p.staWindowSec / al.deltaSec));
        int nlta = Math.max(nsta + 1, (int) Math.round(p.ltaWindowSec / al.deltaSec));
        double[] eig = maxEigen(al.a, al.b, winSamp);
        double[] slta = recursiveStaLta(eig, nsta, nlta);
        double[] cf = centralDifference(slta);
        for (int i = 0; i < cf.length; i++) {
            if (cf[i] > 1e300 || Double.isNaN(cf[i])) {
                cf[i] = 0;
            }
        }
        if (cf.length > 0) {
            cf[cf.length - 1] = 0;
        }
        return new StationFeature(cf, al.startMillis, al.deltaSec);
    }

    static final class AlignedPair {
        final double[] a;
        final double[] b;
        final long startMillis;
        final double deltaSec;

        AlignedPair(double[] a, double[] b, long startMillis, double deltaSec) {
            this.a = a;
            this.b = b;
            this.startMillis = startMillis;
            this.deltaSec = deltaSec;
        }
    }

    /**
     * Detrend + filter, then resample both traces onto a common time grid
     * (overlap of start/end, finer of the two sample intervals).
     */
    static AlignedPair prepareAlignedPair(SacTimeSeries h1, SacTimeSeries h2, Params p) throws IOException {
        SacTimeSeries s1 = WaveProcessor.detrend(duplicateForProcessing(h1));
        SacTimeSeries s2 = WaveProcessor.detrend(duplicateForProcessing(h2));
        WaveProcessor.applyOptionalFrequencyFilter(s1, p.filterFreqMin, p.filterFreqMax);
        WaveProcessor.applyOptionalFrequencyFilter(s2, p.filterFreqMin, p.filterFreqMax);
        return alignPair(s1, s2);
    }

    static AlignedPair alignPair(SacTimeSeries s1, SacTimeSeries s2) throws IOException {
        float[] x = s1.getY();
        float[] y = s2.getY();
        if (x.length < 16 || y.length < 16) {
            throw new IOException("trace too short");
        }
        long t0a = WaveProcessor.startEpochMillis(s1);
        long t0b = WaveProcessor.startEpochMillis(s2);
        double da = s1.getHeader().getDelta();
        double db = s2.getHeader().getDelta();
        if (da <= 0) {
            da = 0.01;
        }
        if (db <= 0) {
            db = 0.01;
        }
        double dt = Math.min(da, db);
        long t1a = t0a + Math.round((x.length - 1) * da * 1000.0);
        long t1b = t0b + Math.round((y.length - 1) * db * 1000.0);
        long t0 = Math.max(t0a, t0b);
        long t1 = Math.min(t1a, t1b);
        if (t1 <= t0 + Math.round(16 * dt * 1000.0)) {
            throw new IOException("traces do not overlap in time");
        }
        int n = (int) Math.floor((t1 - t0) / (dt * 1000.0)) + 1;
        if (n < 16) {
            throw new IOException("aligned overlap too short");
        }
        return new AlignedPair(
            resample(x, t0a, da, t0, dt, n),
            resample(y, t0b, db, t0, dt, n),
            t0, dt);
    }

    static double[] resample(float[] y, long srcT0, double srcDt, long dstT0, double dstDt, int n) {
        double[] out = new double[n];
        double srcStepMs = srcDt * 1000.0;
        double dstStepMs = dstDt * 1000.0;
        for (int i = 0; i < n; i++) {
            double t = dstT0 + i * dstStepMs;
            double srcIndex = (t - srcT0) / srcStepMs;
            out[i] = interpolate(y, srcIndex);
        }
        return out;
    }

    static double interpolate(float[] y, double index) {
        if (y.length == 0) {
            return 0;
        }
        if (index <= 0) {
            return y[0];
        }
        if (index >= y.length - 1) {
            return y[y.length - 1];
        }
        int i = (int) Math.floor(index);
        double f = index - i;
        return y[i] * (1.0 - f) + y[i + 1] * f;
    }

    private static SacTimeSeries duplicateForProcessing(SacTimeSeries s) {
        float[] y = s.getY();
        return new SacTimeSeries(s.getHeader(), java.util.Arrays.copyOf(y, y.length));
    }

    static double[] maxEigen(double[] x, double[] y, int winSamp) {
        int n = x.length;
        double[] eigArr = new double[n];
        int N = n - winSamp;
        for (int i = 0; i < N - 1; i++) {
            double mx = 0, my = 0;
            for (int k = 0; k < winSamp; k++) {
                mx += x[i + k];
                my += y[i + k];
            }
            mx /= winSamp;
            my /= winSamp;
            double cxx = 0, cyy = 0, cxy = 0;
            for (int k = 0; k < winSamp; k++) {
                double dx = x[i + k] - mx;
                double dy = y[i + k] - my;
                cxx += dx * dx;
                cyy += dy * dy;
                cxy += dx * dy;
            }
            cxx /= (winSamp - 1);
            cyy /= (winSamp - 1);
            cxy /= (winSamp - 1);
            double tr = cxx + cyy;
            double det = cxx * cyy - cxy * cxy;
            double disc = tr * tr / 4.0 - det;
            if (disc < 0) {
                disc = 0;
            }
            double lam1 = tr / 2.0 + Math.sqrt(disc);
            double lam2 = tr / 2.0 - Math.sqrt(disc);
            eigArr[winSamp + i] = Math.max(Math.abs(lam1), Math.abs(lam2));
        }
        return eigArr;
    }

    static double[] recursiveStaLta(double[] data, int nsta, int nlta) {
        int ndat = data.length;
        double[] slta = new double[ndat];
        double csta = 1.0 / nsta;
        double clta = 1.0 / nlta;
        double sta = 0;
        double lta = Double.MIN_NORMAL;
        double icsta = 1 - csta;
        double iclta = 1 - clta;
        for (int i = nsta + 1; i < ndat; i++) {
            sta = csta * data[i] + icsta * sta;
            lta = clta * data[i - nsta - 1] + iclta * lta;
            slta[i] = sta / lta;
        }
        for (int i = 0; i < Math.min(nlta, ndat); i++) {
            slta[i] = 0;
        }
        return slta;
    }

    static double[] centralDifference(double[] X) {
        int n = X.length;
        double[] dX = new double[n];
        int dx = 1;
        for (int i = 1; i < n - 1; i++) {
            dX[i] = (X[i + dx] - X[i - dx]) / (2.0 * dx);
        }
        for (int i = 0; i < n; i++) {
            if (dX[i] < 0) {
                dX[i] = 0;
            }
        }
        return dX;
    }

    static void normalizeRowsMinMax(double[][] rows) {
        for (double[] row : rows) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (double v : row) {
                if (v < min) {
                    min = v;
                }
                if (v > max) {
                    max = v;
                }
            }
            if (max <= min || Double.isInfinite(min) || Double.isNaN(min) || Double.isNaN(max)) {
                java.util.Arrays.fill(row, 0.0);
                continue;
            }
            for (int i = 0; i < row.length; i++) {
                row[i] = (row[i] - min) / (max - min);
            }
        }
    }

    static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }

}
