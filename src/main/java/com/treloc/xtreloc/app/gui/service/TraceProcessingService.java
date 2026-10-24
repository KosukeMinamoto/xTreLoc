package com.treloc.xtreloc.app.gui.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Directory-hierarchy based batch processing of SAC traces (envelope, CF, cross-correlation).
 *
 * <h3>Processing unit</h3>
 * Below the chosen directory {@code D}, every directory that <b>directly contains at least one {@code .sac}</b>
 * is an independent unit (the SAC files are grouped by station inside the unit only; sub-directories are not
 * mixed in).
 *
 * <h3>Output layout</h3>
 * The directory tree below {@code D} is mirrored:
 * <pre>
 *   &lt;outRoot&gt;/&lt;operation dir&gt;/&lt;name of D&gt;/&lt;path of unit relative to D&gt;/...
 * </pre>
 * Envelope / CF write {@code <STN>_ENV.sac} / {@code <STN>_CF.sac}; cross-correlation writes
 * {@code yyMMdd.HHmmss.dat} lag tables.
 */
public final class TraceProcessingService {

    private static final Logger LOG = Logger.getLogger(TraceProcessingService.class.getName());

    /** A calculation that can be run per directory. Each has its own parameter set in the UI. */
    public enum Operation {
        ENVELOPE("Envelope", "ENV"),
        CHARACTERISTIC("Characteristic function (CF)", "CF"),
        CROSS_CORRELATION("Cross-correlation (lag .dat)", CrossCorrelationPickingService.DEFAULT_CORRELATION_SUBDIR);

        private final String label;
        private final String outputDirName;

        Operation(String label, String outputDirName) {
            this.label = label;
            this.outputDirName = outputDirName;
        }

        public String getLabel() {
            return label;
        }

        /** First directory level below the output root. */
        public String getOutputDirName() {
            return outputDirName;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Callbacks (invoked on the calling thread, i.e. the batch thread). */
    public interface UnitListener {
        void onUnitStart(Path unit, int index, int total);

        void onUnitFinished(Path unit, int index, int total, boolean ok, String message);
    }

    public static final class Result {
        public final int total;
        public final int succeeded;
        public final int failed;
        public final boolean cancelled;

        Result(int total, int succeeded, int failed, boolean cancelled) {
            this.total = total;
            this.succeeded = succeeded;
            this.failed = failed;
            this.cancelled = cancelled;
        }
    }

    private TraceProcessingService() {}

    static boolean isSacFile(Path p) {
        Path name = p.getFileName();
        return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(".sac");
    }

    /**
     * Lists processing units: {@code root} and all directories below it that directly contain a {@code .sac}.
     * Sorted by path.
     */
    public static List<Path> findUnitDirectories(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }
        List<Path> dirs;
        try (Stream<Path> walk = Files.walk(root)) {
            dirs = walk.filter(Files::isDirectory).collect(Collectors.toList());
        }
        List<Path> units = new ArrayList<>();
        for (Path d : dirs) {
            if (containsSacDirectly(d)) {
                units.add(d);
            }
        }
        units.sort(Path::compareTo);
        return units;
    }

    private static boolean containsSacDirectly(Path dir) {
        try (Stream<Path> list = Files.list(dir)) {
            return list.anyMatch(p -> Files.isRegularFile(p) && isSacFile(p));
        } catch (IOException e) {
            LOG.log(Level.FINE, "Cannot list " + dir + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Output directory for one unit: {@code outRoot/<op dir>/<name of root>/<unit relative to root>}.
     */
    public static Path outputDirFor(Operation op, Path outRoot, Path root, Path unit) {
        Path r = root.toAbsolutePath().normalize();
        Path u = unit.toAbsolutePath().normalize();
        Path rootName = r.getFileName();
        Path base = outRoot.resolve(op.getOutputDirName())
            .resolve(rootName != null ? rootName.toString() : "root");
        Path rel = r.relativize(u);
        return rel.toString().isEmpty() ? base : base.resolve(rel.toString());
    }

    private static CrossCorrelationPickingService.Params copy(CrossCorrelationPickingService.Params s) {
        CrossCorrelationPickingService.Params p = new CrossCorrelationPickingService.Params();
        p.signalKind = s.signalKind;
        p.eigWindowSec = s.eigWindowSec;
        p.staWindowSec = s.staWindowSec;
        p.ltaWindowSec = s.ltaWindowSec;
        p.ccWindowSec = s.ccWindowSec;
        p.ccOverlapRate = s.ccOverlapRate;
        p.ccMaxDistKm = s.ccMaxDistKm;
        p.ccCoefThreshold = s.ccCoefThreshold;
        p.ccMinPairCount = s.ccMinPairCount;
        p.filterFreqMin = s.filterFreqMin;
        p.filterFreqMax = s.filterFreqMax;
        return p;
    }

    /**
     * Runs {@code op} on every unit below {@code root}. A failing unit is reported and skipped; the batch continues.
     *
     * @param params      parameter set of this operation (for ENVELOPE/CF the signal kind is forced accordingly)
     * @param coords      station coordinates (cross-correlation pair-distance filter); may be {@code null}
     * @param cancelled   polled between units; may be {@code null}
     */
    public static Result run(Operation op, Path root, Path outRoot,
                             CrossCorrelationPickingService.Params params,
                             Map<String, CrossCorrelationPickingService.StationCoord> coords,
                             CrossCorrelationPickingService.Progress progress,
                             UnitListener listener, BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(outRoot, "outRoot");
        Objects.requireNonNull(params, "params");

        List<Path> units = findUnitDirectories(root);
        if (units.isEmpty()) {
            throw new IOException("No directory containing .sac files under " + root);
        }

        CrossCorrelationPickingService.Params p = copy(params);
        if (op == Operation.ENVELOPE) {
            p.signalKind = CrossCorrelationPickingService.SignalKind.ENVELOPE;
        } else if (op == Operation.CHARACTERISTIC) {
            p.signalKind = CrossCorrelationPickingService.SignalKind.CHARACTERISTIC;
        }

        LOG.info("Trace processing started: " + op.getLabel() + ", input=" + root + ", output=" + outRoot
            + ", units=" + units.size());
        int ok = 0;
        int failed = 0;
        boolean wasCancelled = false;
        for (int i = 0; i < units.size(); i++) {
            if ((cancelled != null && cancelled.getAsBoolean()) || Thread.currentThread().isInterrupted()) {
                wasCancelled = true;
                LOG.info("Trace processing cancelled before unit " + (i + 1) + "/" + units.size());
                break;
            }
            Path unit = units.get(i);
            if (listener != null) {
                listener.onUnitStart(unit, i, units.size());
            }
            Path outDir = outputDirFor(op, outRoot, root, unit);
            try {
                switch (op) {
                    case ENVELOPE:
                    case CHARACTERISTIC:
                        CrossCorrelationPickingService.runDerivedSacToDir(unit, outDir, p, progress);
                        break;
                    case CROSS_CORRELATION:
                        CrossCorrelationPickingService.runCorrelationToDir(unit, outDir, p, coords, progress);
                        break;
                    default:
                        throw new IOException("Unsupported operation: " + op);
                }
                ok++;
                LOG.info(op + " done [" + (i + 1) + "/" + units.size() + "] " + unit + " -> " + outDir);
                if (listener != null) {
                    listener.onUnitFinished(unit, i, units.size(), true, outDir.toString());
                }
            } catch (Exception ex) {
                failed++;
                LOG.log(Level.WARNING, op + " failed [" + (i + 1) + "/" + units.size() + "] for " + unit
                    + ": " + ex.getMessage(), ex);
                if (listener != null) {
                    listener.onUnitFinished(unit, i, units.size(), false,
                        ex.getMessage() != null ? ex.getMessage() : ex.toString());
                }
            }
        }
        LOG.info("Trace processing finished: " + op.getLabel() + " — " + ok + " succeeded, " + failed
            + " failed of " + units.size() + " unit(s)" + (wasCancelled ? " (cancelled)" : ""));
        return new Result(units.size(), ok, failed, wasCancelled);
    }
}
