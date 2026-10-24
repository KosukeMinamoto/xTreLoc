package com.treloc.xtreloc.app.gui.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Associates the picks of the {@link PickPool} into events with the external REAL program
 * (Zhang et al., 2019, https://github.com/Dal-mzhang/REAL).
 *
 * <p>REAL works on one day at a time, so the picks are split by (local) day, written in REAL's input
 * layout ({@link EqtPickWriter#writeReal}), and REAL is run once per day. The result files
 * {@code phase_sel.txt} of all days are merged into a list of {@link Event}s.
 *
 * <p>Times are the wall-clock times of the SAC headers, like everywhere else in the GUI.
 */
public final class RealAssociator {

    private RealAssociator() {}

    /** Settings of one association run. */
    public static final class Params {
        /** The REAL executable. */
        public Path executable;
        /** Station file in the xTreLoc layout ({@code code lat lon ...}). */
        public Path stationFile;
        /** Travel-time table made with REAL's {@code taup_tt.py} ({@code ttdb.txt}). */
        public Path travelTimeTable;
        /** Folder for the input and output files; created when missing. */
        public Path workDir;
        /** Network code used for the station names in the pick files. */
        public String network = "XX";
        /** Latitude used to make degrees and km consistent ({@code NaN}: mean latitude of the stations). */
        public double latCenter = Double.NaN;
        /** REAL {@code -R}: rx/rh/tdx/tdh/tint. */
        public String gridR = "0.1/20/0.04/2/5";
        /** REAL {@code -G}: trx/trh/tdx/tdh. */
        public String gridG = "1.4/20/0.01/1";
        /** REAL {@code -V}: vp0/vs0. */
        public String velocityV = "6.2/3.3";
        /** REAL {@code -S}: np0/ns0/nps0/npsboth0/std0/dtps/nrt/drt/nxd/rsel. */
        public String thresholdsS = "3/2/12/3/0.5/0.2/1/0.25/0.2/4";
    }

    /** One associated pick of an event. */
    public record Phase(String network, String station, String phase, String component, Date time,
                        double travelTime, double residual, double weight) {
    }

    /** One event found by REAL. */
    public record Event(int id, Date origin, double lat, double lon, double depth, double std, double mag,
                        int np, int ns, int nps, int npsBoth, double gap, List<Phase> phases) {
    }

    /** All events of a run, sorted by origin time, with consecutive ids. */
    public record Result(List<Event> events, Path workDir, int days) {
    }

    /** The REAL command line for one day. */
    static List<String> command(Params p, LocalDate day, double latCenter, Path stationDat, Path pickDir) {
        List<String> c = new ArrayList<>();
        c.add(p.executable.toAbsolutePath().toString());
        c.add(String.format(Locale.ROOT, "-D%d/%d/%d/%.4f", day.getYear(), day.getMonthValue(),
            day.getDayOfMonth(), latCenter));
        c.add("-R" + p.gridR);
        c.add("-G" + p.gridG);
        c.add("-S" + p.thresholdsS);
        c.add("-V" + p.velocityV);
        c.add(stationDat.toAbsolutePath().toString());
        c.add(pickDir.toAbsolutePath().toString());
        c.add(p.travelTimeTable.toAbsolutePath().toString());
        return c;
    }

    /**
     * Writes the station file in REAL's layout ({@code lon lat net sta comp elevation_km}).
     *
     * @return the mean latitude of the stations
     */
    static double writeStationDat(Path out, Map<String, CrossCorrelationPickingService.StationCoord> coords,
                                  String network) throws IOException {
        double sum = 0;
        int n = 0;
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            for (CrossCorrelationPickingService.StationCoord s : coords.values()) {
                if (!s.hasCoords) {
                    continue;
                }
                double elev = Double.isFinite(s.depthKm) ? -s.depthKm : 0.0;
                w.printf(Locale.ROOT, "%.5f %.5f %s %s HHZ %.4f%n", s.lon, s.lat, network, s.code, elev);
                sum += s.lat;
                n++;
            }
        }
        if (n == 0) {
            throw new IOException("The station file has no station with coordinates.");
        }
        return sum / n;
    }

    private static String validate(Params p, List<EqtPicker.Pick> picks) {
        if (picks == null || picks.isEmpty()) {
            return "The pick pool is empty. Pick first.";
        }
        if (p.executable == null || !Files.isRegularFile(p.executable)) {
            return "The REAL executable is not set or does not exist.";
        }
        if (!Files.isExecutable(p.executable)) {
            return "The REAL file is not executable: " + p.executable;
        }
        if (p.stationFile == null || !Files.isRegularFile(p.stationFile)) {
            return "The station file is not set or does not exist.";
        }
        if (p.travelTimeTable == null || !Files.isRegularFile(p.travelTimeTable)) {
            return "The travel-time table (ttdb.txt) is not set or does not exist.";
        }
        if (p.workDir == null) {
            return "The work folder is not set.";
        }
        return null;
    }

    /**
     * Runs REAL over all days of {@code picks}.
     *
     * @param log receives the output of REAL, line by line (may be {@code null})
     * @throws IOException          the input is incomplete, REAL cannot be started or fails
     * @throws InterruptedException the run was cancelled; the running REAL process is killed
     */
    public static Result run(Params p, List<EqtPicker.Pick> picks, Consumer<String> log)
            throws IOException, InterruptedException {
        String problem = validate(p, picks);
        if (problem != null) {
            throw new IOException(problem);
        }
        Consumer<String> out = log != null ? log : s -> { };
        Map<String, CrossCorrelationPickingService.StationCoord> coords =
            CrossCorrelationPickingService.loadCoordsForDistanceFilter(p.stationFile);
        Files.createDirectories(p.workDir);
        Path stationDat = p.workDir.resolve("station.dat");
        double meanLat = writeStationDat(stationDat, coords, p.network);
        double latCenter = Double.isFinite(p.latCenter) ? p.latCenter : meanLat;

        Path pickRoot = p.workDir.resolve("picks");
        EqtPickWriter.writeReal(pickRoot, p.network, picks);
        TreeSet<LocalDate> days = new TreeSet<>();
        for (EqtPicker.Pick pk : picks) {
            days.add(localDate(pk.time()));
        }

        Map<String, TreeMap<Long, EqtPicker.Pick>> index = indexPicks(picks);
        List<Event> all = new ArrayList<>();
        for (LocalDate day : days) {
            String stamp = String.format(Locale.ROOT, "%04d%02d%02d", day.getYear(), day.getMonthValue(),
                day.getDayOfMonth());
            Path pickDir = pickRoot.resolve(stamp);
            Path outDir = p.workDir.resolve("out").resolve(stamp);
            Files.createDirectories(outDir);
            Files.deleteIfExists(outDir.resolve("phase_sel.txt"));
            Files.deleteIfExists(outDir.resolve("catalog_sel.txt"));
            out.accept("== " + stamp + " ==");
            runOne(command(p, day, latCenter, stationDat, pickDir), outDir, out);
            Path phaseSel = outDir.resolve("phase_sel.txt");
            if (!Files.isRegularFile(phaseSel)) {
                throw new IOException("REAL wrote no phase_sel.txt for " + stamp + " (see the log).");
            }
            List<Event> events = parsePhaseSel(phaseSel, day, p.network, index);
            out.accept(stamp + ": " + events.size() + " event(s)");
            all.addAll(events);
        }
        all.sort(Comparator.comparing(Event::origin));
        List<Event> numbered = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Event e = all.get(i);
            numbered.add(new Event(i + 1, e.origin(), e.lat(), e.lon(), e.depth(), e.std(), e.mag(),
                e.np(), e.ns(), e.nps(), e.npsBoth(), e.gap(), e.phases()));
        }
        return new Result(numbered, p.workDir, days.size());
    }

    private static void runOne(List<String> command, Path dir, Consumer<String> log)
            throws IOException, InterruptedException {
        Process proc = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        List<String> tail = new ArrayList<>();
        try {
            try (BufferedReader r = new BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.accept(line);
                    tail.add(line);
                    if (tail.size() > 5) {
                        tail.remove(0);
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException();
                    }
                }
            }
            int code = proc.waitFor();
            if (code != 0) {
                throw new IOException("REAL exited with code " + code
                    + (tail.isEmpty() ? "" : ": " + String.join(" / ", tail)));
            }
        } catch (InterruptedException e) {
            proc.destroyForcibly();
            throw e;
        } finally {
            if (proc.isAlive()) {
                proc.destroyForcibly();
            }
        }
    }

    // ------------------------------------------------------------------ parsing

    /**
     * Reads REAL's {@code phase_sel.txt}: an event line (id, year, month, day, time, origin seconds, std,
     * lat, lon, depth, mag, mag std, np, ns, nps, npsboth, gap) followed by its phase lines
     * (net, station, phase, arrival seconds of the day, travel time, amplitude, residual, weight, back-azimuth).
     *
     * @param day   the day the file was computed for (arrival seconds count from its 00:00)
     * @param index the pool picks by station and phase, to recover the component of each phase (may be empty)
     */
    static List<Event> parsePhaseSel(Path file, LocalDate day, String network,
                                     Map<String, TreeMap<Long, EqtPicker.Pick>> index) throws IOException {
        List<Event> events = new ArrayList<>();
        Header head = null;
        List<Phase> phases = new ArrayList<>();
        long dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] t = raw.trim().split("\\s+");
            if (t.length >= 17 && t[0].matches("\\d+") && t[1].matches("\\d{4}")) {
                if (head != null) {
                    events.add(head.build(phases));
                }
                head = Header.parse(t);
                phases = new ArrayList<>();
            } else if (head != null && t.length >= 9) {
                try {
                    String sta = t[1];
                    String phase = t[2];
                    long ms = dayStart + Math.round(Double.parseDouble(t[3]) * 1000.0);
                    phases.add(new Phase(t[0].isEmpty() ? network : t[0], sta, phase,
                        componentOf(index, sta, phase, ms), new Date(ms), Double.parseDouble(t[4]),
                        Double.parseDouble(t[6]), Double.parseDouble(t[7])));
                } catch (NumberFormatException ignored) {
                    // a malformed phase line is skipped; the event keeps its other phases
                }
            }
        }
        if (head != null) {
            events.add(head.build(phases));
        }
        return events;
    }

    private record Header(Date origin, double lat, double lon, double depth, double std, double mag,
                          int np, int ns, int nps, int npsBoth, double gap) {

        static Header parse(String[] t) {
            String[] hms = t[4].split(":");
            LocalDateTime ldt = LocalDateTime.of(Integer.parseInt(t[1]), Integer.parseInt(t[2]),
                Integer.parseInt(t[3]), Integer.parseInt(hms[0]), Integer.parseInt(hms[1]), 0);
            long ms = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                + Math.round(Double.parseDouble(hms[2]) * 1000.0);
            return new Header(new Date(ms), Double.parseDouble(t[7]), Double.parseDouble(t[8]),
                Double.parseDouble(t[9]), Double.parseDouble(t[6]), Double.parseDouble(t[10]),
                Integer.parseInt(t[12]), Integer.parseInt(t[13]), Integer.parseInt(t[14]),
                Integer.parseInt(t[15]), Double.parseDouble(t[16]));
        }

        Event build(List<Phase> phases) {
            return new Event(0, origin, lat, lon, depth, std, mag, np, ns, nps, npsBoth, gap, List.copyOf(phases));
        }
    }

    static Map<String, TreeMap<Long, EqtPicker.Pick>> indexPicks(List<EqtPicker.Pick> picks) {
        Map<String, TreeMap<Long, EqtPicker.Pick>> m = new HashMap<>();
        for (EqtPicker.Pick p : picks) {
            m.computeIfAbsent(p.station() + '|' + p.phase(), k -> new TreeMap<>()).put(p.time().getTime(), p);
        }
        return m;
    }

    /** Component of the pool pick within 20 ms of {@code ms}; the vertical for P and a horizontal for S otherwise. */
    private static String componentOf(Map<String, TreeMap<Long, EqtPicker.Pick>> index, String station,
                                      String phase, long ms) {
        TreeMap<Long, EqtPicker.Pick> byTime = index.get(station + '|' + phase);
        if (byTime != null) {
            Map.Entry<Long, EqtPicker.Pick> lo = byTime.floorEntry(ms);
            Map.Entry<Long, EqtPicker.Pick> hi = byTime.ceilingEntry(ms);
            Map.Entry<Long, EqtPicker.Pick> best = lo;
            if (hi != null && (lo == null || hi.getKey() - ms < ms - lo.getKey())) {
                best = hi;
            }
            if (best != null && Math.abs(best.getKey() - ms) <= 20) {
                return best.getValue().component();
            }
        }
        return "P".equals(phase) ? "Z" : "N";
    }

    private static LocalDate localDate(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
