package com.treloc.xtreloc.app.gui.service;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Writes station-pair differential {@code .dat} files in the solver layout (same as cross-correlation):
 * hypocenter line ({@code lat lon dep COR}), error line ({@code 999 999 999 999}), then
 * {@code stnk stnl lagt coef PHASE} rows.
 *
 * <h3>Conventions (identical to the cross-correlation output and to what the solver expects)</h3>
 * <ul>
 *   <li>Stations are ordered case-insensitively by code; for the pair {@code (i < j)} in that order,
 *       {@code stnk} is the <b>later</b> station ({@code j}) and {@code stnl} the <b>earlier</b> one ({@code i}),
 *       exactly like {@link CrossCorrelationPickingService#writeSlidingWindowCorrelation}.</li>
 *   <li>{@code lagt = t(stnl) − t(stnk)} in seconds. The solver (e.g. {@code HypoStationPairDiff}) compares
 *       {@code lagt} with {@code trvTime[stnl] − trvTime[stnk]}.</li>
 *   <li>{@code coef} is {@code 1.0} for manual picks.</li>
 * </ul>
 */
public final class DifferentialDatWriter {

    private static final Logger LOG = Logger.getLogger(DifferentialDatWriter.class.getName());

    public static final String LOCATION_TYPE = "COR";
    public static final double UNKNOWN_ERROR = 999.0;

    /** One line of the table. */
    public static final class Row {
        public final String stnk;
        public final String stnl;
        public final double lagt;
        public final double coef;
        public final String phase;

        public Row(String stnk, String stnl, double lagt, double coef) {
            this(stnk, stnl, lagt, coef, "S");
        }

        public Row(String stnk, String stnl, double lagt, double coef, String phase) {
            this.stnk = stnk;
            this.stnl = stnl;
            this.lagt = lagt;
            this.coef = coef;
            this.phase = phase == null || phase.isBlank() ? "S" : phase.trim().toUpperCase();
        }
    }

    private DifferentialDatWriter() {}

    /**
     * Builds all unordered station pairs for one phase.
     *
     * @param arrivals    picks (any phase; only {@code phase} is used)
     * @param phase       {@code "P"} or {@code "S"}
     * @param coords      optional station coordinates; used only when non-empty and {@code maxDistKm} is positive
     * @param maxDistKm   maximum inter-station distance (km); {@code <= 0} or NaN disables the filter
     * @return rows in deterministic order (sorted by station code)
     */
    public static List<Row> buildPairRows(Collection<ArrivalTime> arrivals, String phase,
                                          Map<String, CrossCorrelationPickingService.StationCoord> coords,
                                          double maxDistKm) {
        List<ArrivalTime> sel = new ArrayList<>();
        if (arrivals != null) {
            for (ArrivalTime a : arrivals) {
                if (a != null && phase.equals(a.getPhaseDescriptor())) {
                    sel.add(a);
                }
            }
        }
        sel.sort(Comparator.comparing((ArrivalTime a) -> a.getStationName().trim(), String.CASE_INSENSITIVE_ORDER));

        boolean useDist = coords != null && !coords.isEmpty() && maxDistKm > 0 && !Double.isNaN(maxDistKm)
            && !Double.isInfinite(maxDistKm);

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < sel.size(); i++) {
            ArrivalTime earlier = sel.get(i);
            for (int j = i + 1; j < sel.size(); j++) {
                ArrivalTime later = sel.get(j);
                String stnl = earlier.getStationName().trim();
                String stnk = later.getStationName().trim();
                if (useDist && !withinDistance(coords, stnk, stnl, maxDistKm)) {
                    continue;
                }
                double lagt = (earlier.getArrivalTime().getTime() - later.getArrivalTime().getTime()) / 1000.0;
                rows.add(new Row(stnk, stnl, lagt, 1.0, phase));
            }
        }
        return rows;
    }

    private static boolean withinDistance(Map<String, CrossCorrelationPickingService.StationCoord> coords,
                                          String a, String b, double maxDistKm) {
        CrossCorrelationPickingService.StationCoord ca = coords.get(a);
        CrossCorrelationPickingService.StationCoord cb = coords.get(b);
        if (ca == null || cb == null || !ca.hasCoords || !cb.hasCoords) {
            return false;
        }
        GeodesicData g = Geodesic.WGS84.Inverse(ca.lat, ca.lon, cb.lat, cb.lon);
        return g.s12 / 1000.0 <= maxDistKm;
    }

    /** Formats one pair line (without line terminator). */
    public static String formatRow(Row r) {
        return String.format(Locale.US, "%s %s %.6f %.6f %s", r.stnk, r.stnl, r.lagt, r.coef, r.phase);
    }

    /**
     * Writes a solver-ready {@code .dat}. Does nothing and returns 0 when {@code rows} is empty.
     *
     * @return number of data rows written
     */
    public static int write(Path file, List<Row> rows) throws IOException {
        return write(file, rows, null);
    }

    /**
     * Writes a solver-ready {@code .dat}: hypocenter at the centroid of stations that appear in
     * {@code rows} (0 0 0 if no coordinates), errors 999, type {@code COR}.
     */
    public static int write(Path file, List<Row> rows,
                            Map<String, CrossCorrelationPickingService.StationCoord> coords) throws IOException {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        java.util.LinkedHashSet<String> used = new java.util.LinkedHashSet<>();
        for (Row r : rows) {
            used.add(r.stnk);
            used.add(r.stnl);
        }
        double[] c = CrossCorrelationPickingService.centroidOf(used, coords);
        writeWithHeader(file, rows, c[0], c[1], c[2]);
        return rows.size();
    }

    /**
     * Writes a solver-ready {@code .dat} whose first line is the given starting hypocentre (the solver
     * begins its search there), for example the catalog location of an event that is being relocated.
     */
    public static int write(Path file, List<Row> rows, double lat, double lon, double depth) throws IOException {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        writeWithHeader(file, rows, lat, lon, depth);
        return rows.size();
    }

    private static void writeWithHeader(Path file, List<Row> rows, double lat, double lon, double depth)
            throws IOException {
        double[] c = {lat, lon, depth};
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(String.format(Locale.US, "%.3f %.3f %.3f %s%n", c[0], c[1], c[2], LOCATION_TYPE));
            w.write(String.format(Locale.US, "%.3f %.3f %.3f %.3f%n",
                UNKNOWN_ERROR, UNKNOWN_ERROR, UNKNOWN_ERROR, UNKNOWN_ERROR));
            for (Row r : rows) {
                w.write(formatRow(r));
                w.write("\n");
            }
        }
        LOG.info("Differential .dat written (" + rows.size() + " pairs): " + file.toAbsolutePath());
    }

    /** Convenience: build + write. */
    public static int writeFromPicks(Path file, Collection<ArrivalTime> arrivals, String phase,
                                     Map<String, CrossCorrelationPickingService.StationCoord> coords,
                                     double maxDistKm) throws IOException {
        List<Row> rows = buildPairRows(arrivals, phase,
            coords == null ? Collections.emptyMap() : coords, maxDistKm);
        return write(file, rows, coords);
    }

    /** Writes P pairs then S pairs into one {@code .dat}. */
    public static int writeFromPicksBoth(Path file, Collection<ArrivalTime> arrivals,
                                         Map<String, CrossCorrelationPickingService.StationCoord> coords,
                                         double maxDistKm) throws IOException {
        Map<String, CrossCorrelationPickingService.StationCoord> c =
            coords == null ? Collections.emptyMap() : coords;
        List<Row> rows = new ArrayList<>();
        rows.addAll(buildPairRows(arrivals, "P", c, maxDistKm));
        rows.addAll(buildPairRows(arrivals, "S", c, maxDistKm));
        return write(file, rows, coords);
    }
}
