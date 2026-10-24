package com.treloc.xtreloc.app.gui.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CLI batch: expand a day-folder glob, run CF on every match, then (optionally) cross-correlate
 * every CF directory. Days are independent; one failure does not stop the rest.
 */
public final class CfXcorrPipeline {

    private static final Logger LOG = Logger.getLogger(CfXcorrPipeline.class.getName());

    public enum Phase {
        CF, CC, CFCC
    }

    public static final class Result {
        public final int days;
        public final int cfOk;
        public final int cfFailed;
        public final int cfSkipped;
        public final int ccOk;
        public final int ccFailed;
        public final int ccSkipped;

        Result(int days, int cfOk, int cfFailed, int cfSkipped, int ccOk, int ccFailed, int ccSkipped) {
            this.days = days;
            this.cfOk = cfOk;
            this.cfFailed = cfFailed;
            this.cfSkipped = cfSkipped;
            this.ccOk = ccOk;
            this.ccFailed = ccFailed;
            this.ccSkipped = ccSkipped;
        }

        public boolean failed() {
            return cfFailed > 0 || ccFailed > 0;
        }
    }

    private CfXcorrPipeline() {}

    public static Phase parsePhase(String mode) {
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("Mode is required (CF, CC, CFCC)");
        }
        switch (mode.trim().toUpperCase(Locale.ROOT)) {
            case "CF":
                return Phase.CF;
            case "CC":
                return Phase.CC;
            case "CFCC":
                return Phase.CFCC;
            default:
                throw new IllegalArgumentException("Unknown trace-pipeline mode: " + mode
                    + " (use CF, CC, or CFCC)");
        }
    }

    public static boolean isPipelineMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return false;
        }
        switch (mode.trim().toUpperCase(Locale.ROOT)) {
            case "CF":
            case "CC":
            case "CFCC":
                return true;
            default:
                return false;
        }
    }

    /**
     * CLI entry: {@code spec} is a {@code HypotdBatchConfig} JSON file or a path/glob.
     */
    public static Result runCli(String mode, String spec) throws IOException {
        Phase phase = parsePhase(mode);
        if (spec == null || spec.isBlank()) {
            throw new IOException("Need a pipeline JSON or a path glob (e.g. sac/2007*)");
        }
        Path asPath = Paths.get(spec.trim());
        boolean json = looksLikeJson(asPath);
        HypotdBatchConfig cfg;
        Path base = null;
        if (json) {
            if (!Files.isRegularFile(asPath)) {
                throw new IOException("Pipeline JSON not found: " + spec);
            }
            cfg = HypotdBatchConfig.readJson(asPath);
            base = asPath.toAbsolutePath().getParent();
            if (cfg.getInputRoot() == null || cfg.getInputRoot().isBlank()) {
                throw new IOException("pipeline JSON inputRoot is required");
            }
        } else {
            cfg = new HypotdBatchConfig();
            cfg.setInputRoot(spec.trim());
            cfg.setOutputParent(".");
        }
        return run(phase, cfg, base, stdoutProgress());
    }

    public static Result run(Phase phase, HypotdBatchConfig cfg, Path base,
                             CrossCorrelationPickingService.Progress progress) throws IOException {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(cfg, "cfg");

        String inputSpec = cfg.getInputRoot();
        List<Path> days = DayFolderGlob.expand(inputSpec, base);
        if (days.isEmpty()) {
            throw new IOException("No day folders with .sac/.s matching: " + inputSpec);
        }

        Path outParent = resolveOut(cfg.getOutputParent(), base);
        Files.createDirectories(outParent);
        String cfSub = blankTo(cfg.getCfSubdir(), "CF");
        String corrSub = blankTo(cfg.getCorrelationSubdir(),
            CrossCorrelationPickingService.DEFAULT_CORRELATION_SUBDIR);
        boolean skip = cfg.isSkipExisting();

        CrossCorrelationPickingService.Params params = cfg.toServiceParams();
        Map<String, CrossCorrelationPickingService.StationCoord> coords = loadCoords(cfg, base);

        int cfOk = 0;
        int cfFailed = 0;
        int cfSkipped = 0;
        int ccOk = 0;
        int ccFailed = 0;
        int ccSkipped = 0;

        boolean doCf = phase == Phase.CF || phase == Phase.CFCC;
        boolean doCc = phase == Phase.CC || phase == Phase.CFCC;
        boolean inputIsCfDirs = phase == Phase.CC;

        if (doCf) {
            msg(progress, "CF phase: " + days.size() + " day folder(s) → " + outParent.resolve(cfSub));
            for (int i = 0; i < days.size(); i++) {
                Path day = days.get(i);
                String dayName = dayName(day);
                Path cfDir = outParent.resolve(cfSub).resolve(dayName);
                msg(progress, "CF " + (i + 1) + "/" + days.size() + " " + dayName);
                if (skip && hasCfSac(cfDir)) {
                    cfSkipped++;
                    msg(progress, "  skip existing " + cfDir);
                    continue;
                }
                try {
                    CrossCorrelationPickingService.Params cfParams = copyParams(params);
                    cfParams.treatAsPrepared = false;
                    if (cfParams.signalKind == null) {
                        cfParams.signalKind = CrossCorrelationPickingService.SignalKind.CHARACTERISTIC;
                    }
                    CrossCorrelationPickingService.runDerivedSacToDir(day, cfDir, cfParams, progress);
                    cfOk++;
                    msg(progress, "  wrote " + cfDir);
                } catch (Exception ex) {
                    cfFailed++;
                    LOG.log(Level.WARNING, "CF failed for " + day + ": " + ex.getMessage(), ex);
                    msg(progress, "  FAILED: " + ex.getMessage());
                }
            }
        }

        if (doCc) {
            msg(progress, "CC phase: " + days.size() + " day folder(s) → " + outParent.resolve(corrSub));
            for (int i = 0; i < days.size(); i++) {
                Path day = days.get(i);
                String dayName = dayName(day);
                Path cfDir = inputIsCfDirs ? day : outParent.resolve(cfSub).resolve(dayName);
                Path corrDir = outParent.resolve(corrSub).resolve(dayName);
                msg(progress, "CC " + (i + 1) + "/" + days.size() + " " + dayName);
                if (skip && hasDat(corrDir)) {
                    ccSkipped++;
                    msg(progress, "  skip existing " + corrDir);
                    continue;
                }
                try {
                    List<Path> files = DayFolderGlob.listWaveforms(cfDir);
                    if (files.size() < 2) {
                        throw new IOException("Need at least two CF SAC files in " + cfDir);
                    }
                    CrossCorrelationPickingService.Params ccParams = copyParams(params);
                    ccParams.treatAsPrepared = true;
                    ccParams.signalKind = CrossCorrelationPickingService.SignalKind.RAW;
                    CrossCorrelationPickingService.runCorrelationFromFiles(
                        files, corrDir, ccParams, coords, progress);
                    ccOk++;
                    msg(progress, "  wrote " + corrDir);
                } catch (Exception ex) {
                    ccFailed++;
                    LOG.log(Level.WARNING, "CC failed for " + day + ": " + ex.getMessage(), ex);
                    msg(progress, "  FAILED: " + ex.getMessage());
                }
            }
        }

        Result r = new Result(days.size(), cfOk, cfFailed, cfSkipped, ccOk, ccFailed, ccSkipped);
        msg(progress, summary(phase, r));
        return r;
    }

    public static String summary(Phase phase, Result r) {
        return "Done " + phase + ": days=" + r.days
            + " CF ok/skip/fail=" + r.cfOk + "/" + r.cfSkipped + "/" + r.cfFailed
            + " CC ok/skip/fail=" + r.ccOk + "/" + r.ccSkipped + "/" + r.ccFailed;
    }

    private static CrossCorrelationPickingService.Params copyParams(
            CrossCorrelationPickingService.Params s) {
        CrossCorrelationPickingService.Params p = new CrossCorrelationPickingService.Params();
        p.signalKind = s.signalKind;
        p.treatAsPrepared = s.treatAsPrepared;
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

    private static Map<String, CrossCorrelationPickingService.StationCoord> loadCoords(
            HypotdBatchConfig cfg, Path base) throws IOException {
        String st = firstNonBlank(cfg.getStationFile(), cfg.getStationCoordPath());
        if (st == null) {
            return Collections.emptyMap();
        }
        Path p = Paths.get(st);
        if (!p.isAbsolute() && base != null) {
            p = base.resolve(st).normalize();
        }
        if (!Files.isRegularFile(p)) {
            throw new IOException("stationFile not found: " + p);
        }
        return CrossCorrelationPickingService.loadCoordsForDistanceFilter(p);
    }

    private static Path resolveOut(String outputParent, Path base) {
        String s = outputParent == null || outputParent.isBlank() ? "." : outputParent.trim();
        Path p = Paths.get(s);
        if (!p.isAbsolute() && base != null) {
            return base.resolve(s).normalize();
        }
        return p.toAbsolutePath().normalize();
    }

    private static String dayName(Path day) {
        Path n = day.getFileName();
        return n != null ? n.toString() : "day";
    }

    private static boolean hasCfSac(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String n = p.getFileName() != null ? p.getFileName().toString().toLowerCase(Locale.ROOT) : "";
                if (Files.isRegularFile(p) && n.endsWith("_cf.sac")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private static boolean hasDat(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String n = p.getFileName() != null ? p.getFileName().toString().toLowerCase(Locale.ROOT) : "";
                if (Files.isRegularFile(p) && n.endsWith(".dat")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private static boolean looksLikeJson(Path path) {
        String n = path.getFileName() != null ? path.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        return n.endsWith(".json");
    }

    private static String blankTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static void msg(CrossCorrelationPickingService.Progress progress, String text) {
        if (progress != null && text != null) {
            progress.onMessage(text);
        }
        LOG.info(text);
    }

    private static CrossCorrelationPickingService.Progress stdoutProgress() {
        return message -> System.out.println(message);
    }
}
