package com.treloc.xtreloc.app.gui.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.treloc.xtreloc.solver.SolverRunMetrics;

/**
 * Builds a tab-separated summary of per-event (.dat) solver runs for the Execution log
 * (paste into Excel / LibreOffice as columns).
 */
public final class SolverBatchSummary {

    private SolverBatchSummary() {
    }

    /**
     * Escapes a field for TSV (tabs and line breaks would break columns).
     */
    public static String tsvEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    public static final class Row {
        public final String solver;
        public final String event;
        public final String status;
        public final int hypoCount;
        /** LM iterations / MCMC samples / DE generations / GRD grid points; null if unknown. */
        public final Integer iterations;
        /** Function evaluations when meaningful; null if unknown. */
        public final Integer evaluations;
        /** Wall-clock solver time (ms), excluding output catalog merge. */
        public final Long elapsedMs;
        /** Final travel-time RMS residual (s). */
        public final Double finalRms;
        public final String note;

        public Row(String solver, String event, String status, int hypoCount, String note) {
            this(solver, event, status, hypoCount, null, null, null, null, note);
        }

        public Row(String solver, String event, String status, int hypoCount,
                   Integer iterations, Integer evaluations, Long elapsedMs, Double finalRms, String note) {
            this.solver = solver != null ? solver : "";
            this.event = event != null ? event : "";
            this.status = status != null ? status : "";
            this.hypoCount = hypoCount;
            this.iterations = iterations;
            this.evaluations = evaluations;
            this.elapsedMs = elapsedMs;
            this.finalRms = finalRms;
            this.note = note != null ? note : "";
        }

        /** Convenience when metrics were collected on the solver thread. */
        public static Row fromMetrics(String solver, String event, String status, int hypoCount,
                                      SolverRunMetrics metrics, String note) {
            if (metrics == null) {
                return new Row(solver, event, status, hypoCount, note != null ? note : "");
            }
            String merged = mergeMetricNotes(metrics.note, note);
            merged = truncateNote(merged, 2000);
            return new Row(solver, event, status, hypoCount,
                metrics.iterations, metrics.evaluations, metrics.wallTimeMs, metrics.finalRms, merged);
        }

        private static String mergeMetricNotes(String fromMetrics, String fromCaller) {
            String a = fromMetrics != null ? fromMetrics.trim() : "";
            String b = fromCaller != null ? fromCaller.trim() : "";
            if (a.isEmpty()) {
                return b;
            }
            if (b.isEmpty()) {
                return a;
            }
            return a + "; " + b;
        }

        String toTsvDataLine() {
            String itStr = iterations != null ? String.valueOf(iterations) : "";
            String evStr = evaluations != null ? String.valueOf(evaluations) : "";
            String msStr = elapsedMs != null ? String.valueOf(elapsedMs) : "";
            String rmsStr = "";
            if (finalRms != null) {
                rmsStr = String.format(Locale.US, "%.6f", finalRms);
            }
            return String.join("\t",
                tsvEscape(solver),
                tsvEscape(event),
                tsvEscape(status),
                String.valueOf(hypoCount),
                itStr,
                evStr,
                msStr,
                rmsStr,
                tsvEscape(note));
        }
    }

    private static final String HEADER = String.join("\t",
        "solver", "event", "status", "hypocenters",
        "iterations", "evaluations", "elapsed_ms", "final_rms", "note");

    /**
     * Header row plus one data row per file in {@code datFileOrder} (missing keys become SKIPPED).
     */
    public static List<String> formatTsvLines(String mode, List<File> datFileOrder, Map<String, Row> byEventName) {
        List<String> out = new ArrayList<>(1 + (datFileOrder != null ? datFileOrder.size() : 0));
        out.add(HEADER);
        if (datFileOrder == null) {
            return out;
        }
        for (File f : datFileOrder) {
            String name = f.getName();
            Row r = byEventName != null ? byEventName.get(name) : null;
            if (r == null) {
                r = new Row(mode, name, "SKIPPED", 0, "No result recorded");
            }
            out.add(r.toTsvDataLine());
        }
        return out;
    }

    /**
     * Truncates an exception or long message for the note column.
     */
    public static String truncateNote(String msg, int maxLen) {
        if (msg == null) {
            return "";
        }
        String t = tsvEscape(msg);
        if (t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen) + "...";
    }
}
