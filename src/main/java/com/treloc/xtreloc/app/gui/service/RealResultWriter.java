package com.treloc.xtreloc.app.gui.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Writes the events of an association run in the formats the other steps read. */
public final class RealResultWriter {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter STEM = DateTimeFormatter.ofPattern("yyMMdd.HHmmss");

    private RealResultWriter() {}

    /** What {@link #writeEventFiles} writes for each event. */
    public static final class EventFiles {
        public boolean obs = true;
        public boolean winPickfile;
        public boolean pairDat = true;
        /** Largest station distance of a pair for the {@code .dat} (km, {@code <= 0}: all pairs). */
        public double maxPairDistKm;
        /** Station coordinates for the pair-distance limit and the pickfile (may be empty). */
        public Map<String, CrossCorrelationPickingService.StationCoord> coords = Collections.emptyMap();
    }

    /** Number of files written by {@link #writeEventFiles}. */
    public record Counts(int obs, int pickfiles, int dats, int skippedDats) {
    }

    private static LocalDateTime local(Date d) {
        return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
    }

    /** {@code yymmdd.hhmmss} of the origin time, the name stem of the per-event files. */
    public static String stem(Date origin) {
        return STEM.format(local(origin));
    }

    /**
     * Catalog in the CSV layout of the Model page ({@code time,latitude,longitude,depth,xerr,yerr,zerr,rms,
     * dat,type}); REAL gives no errors, so they are 0, and the RMS column holds REAL's residual std.
     *
     * @param datFiles event id -&gt; path of its pair {@code .dat} (relative to the catalog), may be empty
     */
    public static void writeCatalogCsv(Path file, List<RealAssociator.Event> events,
                                       Map<Integer, String> datFiles) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            w.println("time,latitude,longitude,depth,xerr,yerr,zerr,rms,dat_file,type");
            for (RealAssociator.Event e : events) {
                w.printf(Locale.ROOT, "%s,%.5f,%.5f,%.3f,0,0,0,%.4f,%s,REAL%n", ISO.format(local(e.origin())),
                    e.lat(), e.lon(), e.depth(), e.std(), datFiles.getOrDefault(e.id(), ""));
            }
        }
    }

    /** Hypocenters of the events, for showing them as a catalog layer. */
    public static List<com.treloc.xtreloc.app.gui.model.Hypocenter> toHypocenters(
            List<RealAssociator.Event> events) {
        List<com.treloc.xtreloc.app.gui.model.Hypocenter> out = new ArrayList<>();
        for (RealAssociator.Event e : events) {
            out.add(new com.treloc.xtreloc.app.gui.model.Hypocenter(ISO.format(local(e.origin())), e.lat(), e.lon(),
                e.depth(), 0.0, 0.0, 0.0, e.std(), null, null, "REAL"));
        }
        return out;
    }

    /** {@code catalog_sel.txt} and {@code phase_sel.txt} in REAL's own layout (ids renumbered from 1). */
    public static void writeRealFiles(Path dir, List<RealAssociator.Event> events) throws IOException {
        Files.createDirectories(dir);
        try (PrintWriter cat = new PrintWriter(Files.newBufferedWriter(dir.resolve("catalog_sel.txt"),
                StandardCharsets.UTF_8));
             PrintWriter pha = new PrintWriter(Files.newBufferedWriter(dir.resolve("phase_sel.txt"),
                StandardCharsets.UTF_8))) {
            int id = 0;
            for (RealAssociator.Event e : events) {
                id++;
                LocalDateTime t = local(e.origin());
                double sec = t.getSecond() + t.getNano() / 1e9;
                String head = String.format(Locale.ROOT,
                    "%5d %04d %02d %02d %02d:%02d:%06.3f %12.3f %7.4f %7.4f %8.4f %6.2f %6.3f %5.3f %3d %3d %3d %3d %6.2f",
                    id, t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute(), sec,
                    t.toLocalTime().toNanoOfDay() / 1e9, e.std(), e.lat(), e.lon(), e.depth(), e.mag(), 0.0,
                    e.np(), e.ns(), e.nps(), e.npsBoth(), e.gap());
                cat.println(head);
                pha.println(head);
                for (RealAssociator.Phase p : e.phases()) {
                    double abs = local(p.time()).toLocalTime().toNanoOfDay() / 1e9;
                    pha.printf(Locale.ROOT, "%5s %8s %5s %12.4f %9.4f %5.2e %7.4f %8.4f %8.4f%n", p.network(),
                        p.station(), p.phase(), abs, p.travelTime(), 0.0, p.residual(), p.weight(), 0.0);
                }
            }
        }
    }

    /** The picks of {@code event} as the pick model of the Absolute page. */
    public static ArrivalTimeManager arrivals(RealAssociator.Event event) {
        ArrivalTimeManager m = new ArrivalTimeManager();
        for (RealAssociator.Phase p : event.phases()) {
            m.setArrivalTime(p.station(), p.component(), p.phase(), p.time());
        }
        return m;
    }

    /**
     * Writes the per-event files into {@code dir}, named by the origin time. The ids of the written
     * {@code .dat} files are added to {@code datFilesOut} (name relative to {@code dir}).
     */
    public static Counts writeEventFiles(Path dir, List<RealAssociator.Event> events, EventFiles opt,
                                         Map<Integer, String> datFilesOut) throws IOException {
        Files.createDirectories(dir);
        Set<String> used = new HashSet<>();
        int obs = 0;
        int picks = 0;
        int dats = 0;
        int skipped = 0;
        for (RealAssociator.Event e : events) {
            String stem = stem(e.origin());
            if (!used.add(stem)) {
                stem = stem + "_" + e.id();
                used.add(stem);
            }
            ArrivalTimeManager atm = arrivals(e);
            if (opt.obs && atm.countStations("P") > 0) {
                atm.outputToObs(dir.resolve(stem + ".obs").toString());
                obs++;
            }
            if (opt.winPickfile && !atm.getArrivalTimeMap().isEmpty()) {
                WinPickfileWriter.write(dir.resolve(WinPickfileWriter.fileStem(atm.getArrivalTimeMap().values())),
                    atm.getArrivalTimeMap().values(), stem, ".", null, opt.coords);
                picks++;
            }
            if (opt.pairDat) {
                List<DifferentialDatWriter.Row> rows = new ArrayList<>();
                rows.addAll(DifferentialDatWriter.buildPairRows(atm.getArrivalTimeMap().values(), "P", opt.coords,
                    opt.maxPairDistKm));
                rows.addAll(DifferentialDatWriter.buildPairRows(atm.getArrivalTimeMap().values(), "S", opt.coords,
                    opt.maxPairDistKm));
                if (DifferentialDatWriter.write(dir.resolve(stem + ".dat"), rows, e.lat(), e.lon(), e.depth()) > 0) {
                    dats++;
                    if (datFilesOut != null) {
                        datFilesOut.put(e.id(), stem + ".dat");
                    }
                } else {
                    skipped++;
                }
            }
        }
        return new Counts(obs, picks, dats, skipped);
    }
}
