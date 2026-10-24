package com.treloc.xtreloc.app.gui.service;

import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.solver.HypoDifferentialEvolution;
import com.treloc.xtreloc.solver.HypoGridSearch;
import com.treloc.xtreloc.solver.HypoMCMC;
import com.treloc.xtreloc.solver.HypoStationPairDiff;
import com.treloc.xtreloc.solver.SolverRunMetricsContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Relocates one event from absolute picks: the picks become station-pair differences (the format the
 * solvers read), the solver starts at the catalog location, and the result is returned as a
 * {@link Hypocenter} with the same origin time label.
 */
public final class EventRelocator {

    /** Solver modes that can relocate a single event from a pair table. */
    public static final List<String> SUPPORTED_MODES = List.of("LMO", "GRD", "MCMC", "DE");

    /** Relocation outcome. */
    public static final class Result {
        public final Hypocenter hypocenter;
        public final int pairRows;
        public final String mode;

        Result(Hypocenter hypocenter, int pairRows, String mode) {
            this.hypocenter = hypocenter;
            this.pairRows = pairRows;
            this.mode = mode;
        }
    }

    private EventRelocator() {
    }

    public static boolean isSupported(String mode) {
        return mode != null && SUPPORTED_MODES.contains(mode);
    }

    /**
     * @param config    solver configuration (stations, velocity model, mode parameters)
     * @param mode      one of {@link #SUPPORTED_MODES}
     * @param initial   starting location (the catalog hypocentre of the event)
     * @param picks     absolute picks, one per station and phase
     * @param coords    station coordinates for the pair-distance limit (may be empty)
     * @param maxDistKm pair distance limit in km; {@code <= 0} disables it
     * @throws IllegalArgumentException when the picks give no station pair or the mode is unsupported
     */
    public static Result relocate(AppConfig config, String mode, Hypocenter initial,
                                  Collection<ArrivalTime> picks,
                                  Map<String, CrossCorrelationPickingService.StationCoord> coords,
                                  double maxDistKm) throws Exception {
        if (!isSupported(mode)) {
            throw new IllegalArgumentException("Solver mode " + mode + " cannot relocate a single event from picks"
                + " (use one of " + SUPPORTED_MODES + ")");
        }
        List<DifferentialDatWriter.Row> rows = new java.util.ArrayList<>();
        rows.addAll(DifferentialDatWriter.buildPairRows(picks, "P", coords, maxDistKm));
        rows.addAll(DifferentialDatWriter.buildPairRows(picks, "S", coords, maxDistKm));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Need P or S picks at two or more stations");
        }
        Path dir = Files.createTempDirectory("xtreloc-relocate");
        try {
            Path in = dir.resolve("event.dat");
            Path out = dir.resolve("event_out.dat");
            DifferentialDatWriter.write(in, rows, initial.lat, initial.lon, initial.depth);
            runSolver(config, mode, in.toString(), out.toString());
            return new Result(readResult(out, initial), rows.size(), mode);
        } finally {
            SolverRunMetricsContext.getAndClear();
            deleteQuietly(dir);
        }
    }

    private static void runSolver(AppConfig cfg, String mode, String in, String out) throws Exception {
        switch (mode) {
            case "LMO" -> new HypoStationPairDiff(cfg).start(in, out);
            case "GRD" -> new HypoGridSearch(cfg).start(in, out);
            case "MCMC" -> new HypoMCMC(cfg).start(in, out);
            case "DE" -> new HypoDifferentialEvolution(cfg).start(in, out);
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    /** Reads a solver output {@code .dat}: line 1 {@code lat lon dep [type]}, line 2 {@code xerr yerr zerr rms}. */
    static Hypocenter readResult(Path out, Hypocenter initial) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(out, StandardCharsets.UTF_8)) {
            String l1 = br.readLine();
            if (l1 == null) {
                throw new IOException("Solver wrote no result: " + out.getFileName());
            }
            String[] p1 = l1.trim().split("\\s+");
            if (p1.length < 3) {
                throw new IOException("Unexpected solver output: " + l1);
            }
            double lat = Double.parseDouble(p1[0]);
            double lon = Double.parseDouble(p1[1]);
            double dep = Double.parseDouble(p1[2]);
            String type = p1.length > 3 ? p1[3] : null;
            double xerr = 0;
            double yerr = 0;
            double zerr = 0;
            double rms = 0;
            String l2 = br.readLine();
            if (l2 != null) {
                String[] p2 = l2.trim().split("\\s+");
                if (p2.length >= 4) {
                    try {
                        xerr = Double.parseDouble(p2[0]);
                        yerr = Double.parseDouble(p2[1]);
                        zerr = Double.parseDouble(p2[2]);
                        rms = Double.parseDouble(p2[3]);
                    } catch (NumberFormatException ignored) {
                        // keep zeros
                    }
                }
            }
            return new Hypocenter(initial.time, lat, lon, dep, xerr, yerr, zerr, rms, initial.clusterId, null, type);
        }
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temporary files only
        }
    }
}
