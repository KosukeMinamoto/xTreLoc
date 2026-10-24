package com.treloc.xtreloc.app.gui.service;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the waveform folder of a catalog event, and the event of a waveform folder.
 *
 * <p>The folder comes from a user pattern in which the origin time fills placeholders:
 * {@code {yyyy} {yy} {MM} {dd} {HH} {mm} {ss}}, the composites {@code {yyyyMMdd}} and
 * {@code {yyMMdd.HHmmss}}, and {@code {catalog}}. {@code *} and {@code ?} in the last path segment
 * are expanded like in the Seismic tab (see {@link DayFolderGlob}). Without a pattern, a folder
 * named like the event's {@code .dat} file (next to it) is used.</p>
 */
public final class EventWaveformResolver {

    /** Folders found for one event, or the reason why none was found. */
    public static final class Resolution {
        private final List<Path> folders;
        private final String reason;

        Resolution(List<Path> folders, String reason) {
            this.folders = List.copyOf(folders);
            this.reason = reason;
        }

        public List<Path> folders() {
            return folders;
        }

        public boolean found() {
            return !folders.isEmpty();
        }

        /** First folder, or {@code null}. */
        public Path first() {
            return folders.isEmpty() ? null : folders.get(0);
        }

        /** Why nothing was found (empty when found). */
        public String reason() {
            return reason;
        }
    }

    private static final DateTimeFormatter COMPACT_STEM = DateTimeFormatter.ofPattern("yyMMdd.HHmmss", Locale.ROOT);
    private static final Pattern FOLDER_TIME = Pattern.compile("(\\d{6})\\.(\\d{6})");

    private EventWaveformResolver() {
    }

    /** Parses {@link Hypocenter#time} (ISO, fractional seconds allowed); {@code null} if unparseable. */
    public static LocalDateTime parseTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        String t = time.trim().replace(' ', 'T');
        int dot = t.indexOf('.');
        if (dot > 0) {
            t = t.substring(0, dot);
        }
        try {
            return LocalDateTime.parse(t);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Fills the placeholders of {@code pattern} for an event; the time must be parseable. */
    public static String expandPattern(String pattern, String catalogName, LocalDateTime t) {
        String s = pattern;
        s = s.replace("{yyMMdd.HHmmss}", t.format(COMPACT_STEM));
        s = s.replace("{yyyyMMdd}", String.format(Locale.ROOT, "%04d%02d%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth()));
        s = s.replace("{yyyy}", String.format(Locale.ROOT, "%04d", t.getYear()));
        s = s.replace("{yy}", String.format(Locale.ROOT, "%02d", t.getYear() % 100));
        s = s.replace("{MM}", String.format(Locale.ROOT, "%02d", t.getMonthValue()));
        s = s.replace("{dd}", String.format(Locale.ROOT, "%02d", t.getDayOfMonth()));
        s = s.replace("{HH}", String.format(Locale.ROOT, "%02d", t.getHour()));
        s = s.replace("{mm}", String.format(Locale.ROOT, "%02d", t.getMinute()));
        s = s.replace("{ss}", String.format(Locale.ROOT, "%02d", t.getSecond()));
        s = s.replace("{catalog}", catalogName == null ? "" : catalogName);
        return s;
    }

    /**
     * @param pattern folder pattern (may be blank to use only the {@code .dat} neighbour)
     * @param catalog catalog the event belongs to (its source file anchors relative {@code .dat} paths)
     * @param h       the event
     */
    public static Resolution resolve(String pattern, CatalogInfo catalog, Hypocenter h) {
        if (h == null) {
            return new Resolution(List.of(), "No event");
        }
        String why = "";
        if (pattern != null && !pattern.isBlank()) {
            LocalDateTime t = parseTime(h.time);
            if (t == null) {
                why = "Origin time \"" + h.time + "\" cannot be parsed";
            } else {
                String spec = expandPattern(pattern.trim(), catalog == null ? "" : catalog.getName(), t);
                try {
                    List<Path> dirs = DayFolderGlob.expand(spec);
                    if (!dirs.isEmpty()) {
                        return new Resolution(dirs, "");
                    }
                    why = "No waveform folder at " + spec;
                } catch (IOException e) {
                    why = e.getMessage() == null ? "No waveform folder at " + spec : e.getMessage();
                }
            }
        }
        Path viaDat = folderNextToDat(catalog, h);
        if (viaDat != null) {
            return new Resolution(List.of(viaDat), "");
        }
        if (why.isEmpty()) {
            why = "Set the waveform folder pattern in Settings (e.g. /data/sac/{yyMMdd.HHmmss})";
        }
        return new Resolution(List.of(), why);
    }

    /** Same as {@link #resolve(String, CatalogInfo, Hypocenter)} for an event given by its key. */
    public static Resolution resolve(String pattern, List<CatalogInfo> catalogs, EventKey key) {
        if (key != null && catalogs != null) {
            for (CatalogInfo c : catalogs) {
                if (!key.catalog().equals(c.getName()) || c.getHypocenters() == null) {
                    continue;
                }
                for (Hypocenter h : c.getHypocenters()) {
                    if (key.matches(c.getName(), h)) {
                        return resolve(pattern, c, h);
                    }
                }
            }
        }
        return new Resolution(List.of(), "Event is not in the loaded catalogs");
    }

    private static Path folderNextToDat(CatalogInfo catalog, Hypocenter h) {
        if (h.datFilePath == null || h.datFilePath.isBlank()) {
            return null;
        }
        Path dat = Paths.get(h.datFilePath);
        if (!dat.isAbsolute()) {
            File src = catalog == null ? null : catalog.getSourceFile();
            Path base = src == null ? null : src.toPath().toAbsolutePath().getParent();
            if (base == null) {
                return null;
            }
            dat = base.resolve(dat);
        }
        String name = dat.getFileName() == null ? "" : dat.getFileName().toString();
        String stem = name.toLowerCase(Locale.ROOT).endsWith(".dat") ? name.substring(0, name.length() - 4) : name;
        Path dir = dat.toAbsolutePath().normalize().resolveSibling(stem);
        return Files.isDirectory(dir) && DayFolderGlob.containsWaveform(dir) ? dir : null;
    }

    /** Origin time encoded in a folder name like {@code 171115.052001}, or {@code null}. */
    public static LocalDateTime timeFromFolderName(String name) {
        if (name == null) {
            return null;
        }
        Matcher m = FOLDER_TIME.matcher(name);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDateTime.parse(m.group(1) + m.group(2), DateTimeFormatter.ofPattern("yyMMddHHmmss", Locale.ROOT));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * The event of the visible catalogs closest in origin time to {@code t} within {@code tolSeconds}
     * (earlier catalogs win ties), or {@code null}.
     */
    public static EventKey findEvent(List<CatalogInfo> catalogs, LocalDateTime t, double tolSeconds) {
        if (t == null || catalogs == null) {
            return null;
        }
        EventKey best = null;
        double bestDt = Double.MAX_VALUE;
        for (CatalogInfo c : catalogs) {
            if (!c.isVisible() || c.getHypocenters() == null) {
                continue;
            }
            for (Hypocenter h : c.getHypocenters()) {
                LocalDateTime ht = parseTime(h.time);
                if (ht == null) {
                    continue;
                }
                double dt = Math.abs(Duration.between(ht, t).toMillis()) / 1000.0;
                if (dt <= tolSeconds && dt < bestDt) {
                    bestDt = dt;
                    best = EventKey.of(c, h);
                }
            }
        }
        return best;
    }

    /** Every folder of {@code r} for all events; helper for "open all selected". */
    public static List<Path> allFolders(List<Resolution> rs) {
        List<Path> out = new ArrayList<>();
        for (Resolution r : rs) {
            for (Path p : r.folders()) {
                if (!out.contains(p)) {
                    out.add(p);
                }
            }
        }
        return out;
    }
}
