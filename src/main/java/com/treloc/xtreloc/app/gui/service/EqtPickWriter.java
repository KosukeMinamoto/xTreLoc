package com.treloc.xtreloc.app.gui.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes EQTransformer picks for a later association step.
 *
 * <p>Times are the wall-clock times of the SAC headers (the same convention as the rest of the GUI).
 */
public final class EqtPickWriter {

    private static final DateTimeFormatter ISO_MS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private EqtPickWriter() {}

    /** CSV with the header {@code station,phase,component,time,prob}, sorted by time. */
    public static void writeCsv(Path file, List<EqtPicker.Pick> picks) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            w.println("station,phase,component,time,prob");
            for (EqtPicker.Pick p : sorted(picks)) {
                w.printf(Locale.ROOT, "%s,%s,%s,%s,%.4f%n", p.station(), p.phase(), p.component(),
                    local(p.time()).format(ISO_MS), p.prob());
            }
        }
    }

    /**
     * REAL input: {@code dir/YYYYMMDD/NET.STA.P.txt} and {@code ...S.txt}, one line per pick:
     * seconds since 00:00:00 of that day, probability, amplitude (0).
     *
     * @return number of files written
     */
    public static int writeReal(Path dir, String network, List<EqtPicker.Pick> picks) throws IOException {
        Map<Path, List<EqtPicker.Pick>> files = new TreeMap<>();
        for (EqtPicker.Pick p : sorted(picks)) {
            String day = local(p.time()).format(DAY);
            Path f = dir.resolve(day).resolve(network + "." + p.station() + "." + p.phase() + ".txt");
            files.computeIfAbsent(f, k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<Path, List<EqtPicker.Pick>> e : files.entrySet()) {
            Files.createDirectories(e.getKey().getParent());
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(e.getKey()))) {
                for (EqtPicker.Pick p : e.getValue()) {
                    LocalDateTime t = local(p.time());
                    double sec = t.toLocalTime().toNanoOfDay() / 1e9;
                    w.printf(Locale.ROOT, "%.3f %.4f 0.0%n", sec, p.prob());
                }
            }
        }
        return files.size();
    }

    /** Reads a file made by {@link #writeCsv}. */
    public static List<EqtPicker.Pick> readCsv(Path file) throws IOException {
        List<EqtPicker.Pick> out = new ArrayList<>();
        int lineNo = 0;
        for (String line : Files.readAllLines(file)) {
            lineNo++;
            if (line.isBlank() || line.startsWith("station")) {
                continue;
            }
            String[] f = line.trim().split(",");
            if (f.length < 5) {
                throw new IOException(file.getFileName() + ":" + lineNo + ": expected station,phase,component,time,prob");
            }
            try {
                Date t = Date.from(LocalDateTime.parse(f[3].trim(), ISO_MS).atZone(ZoneId.systemDefault()).toInstant());
                out.add(new EqtPicker.Pick(f[0].trim(), f[1].trim(), f[2].trim(), t, Double.parseDouble(f[4].trim())));
            } catch (RuntimeException e) {
                throw new IOException(file.getFileName() + ":" + lineNo + ": " + e.getMessage(), e);
            }
        }
        return out;
    }

    private static List<EqtPicker.Pick> sorted(List<EqtPicker.Pick> picks) {
        List<EqtPicker.Pick> s = new ArrayList<>(picks);
        s.sort(Comparator.comparing(EqtPicker.Pick::time));
        return s;
    }

    private static LocalDateTime local(Date d) {
        return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
    }
}
