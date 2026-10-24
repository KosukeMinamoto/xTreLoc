package com.treloc.xtreloc.app.gui.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a WIN pickfile ({@code #p} raw picks and {@code #s} hypomh input). See
 * <a href="https://wwweic.eri.u-tokyo.ac.jp/WIN/man.ja/pickfile.html">pickfile(1W)</a>.
 *
 * <p>{@code #f} (hypomh output) is omitted: this writer is the picking export, not a locator.</p>
 */
public final class WinPickfileWriter {

    public static final String PICKER = "xtreloc";
    public static final double DEFAULT_P_ERR_S = 0.03;
    public static final double DEFAULT_S_ERR_S = 0.06;

    private static final DateTimeFormatter FILE_STAMP =
        DateTimeFormatter.ofPattern("yyMMdd.HHmmss.SSS", Locale.US);

    private WinPickfileWriter() {
    }

    /** WIN pickfile stem {@code YYMMDD.hhmmss.sss} from the earliest P (else S) pick. */
    public static String fileStem(Collection<ArrivalTime> arrivals) {
        Date t = earliest(arrivals, "P");
        if (t == null) {
            t = earliest(arrivals, "S");
        }
        if (t == null) {
            t = new Date();
        }
        return FILE_STAMP.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(t.getTime()), ZoneId.systemDefault()));
    }

    /**
     * @param waveName     waveform file name on the first {@code #p} line (event folder or SAC stem)
     * @param label        operator memo; {@code "."} when empty
     * @param waveStart    first-sample time of the waveform; {@code null} uses the earliest pick
     * @param coords       optional station coordinates for the {@code #s} block
     */
    public static void write(Path file, Collection<ArrivalTime> arrivals, String waveName, String label,
                             LocalDateTime waveStart,
                             Map<String, CrossCorrelationPickingService.StationCoord> coords) throws IOException {
        List<ArrivalTime> list = arrivals == null ? List.of() : new ArrayList<>(arrivals);
        list.removeIf(a -> a == null || a.getArrivalTime() == null);
        if (list.isEmpty()) {
            throw new IOException("No picks to write as a WIN pickfile.");
        }
        list.sort(Comparator
            .comparing((ArrivalTime a) -> a.getStationName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ArrivalTime::getPhaseDescriptor));

        LocalDateTime start = waveStart;
        if (start == null) {
            Date e = earliest(list, "P");
            if (e == null) {
                e = earliest(list, "S");
            }
            start = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.getTime()), ZoneId.systemDefault())
                .withNano(0);
        }
        String wav = (waveName == null || waveName.isBlank()) ? "." : waveName.trim();
        String memo = (label == null || label.isBlank()) ? "." : label.trim();

        Map<String, Integer> chan = new LinkedHashMap<>();
        for (ArrivalTime a : list) {
            chan.putIfAbsent(a.getStationName().trim(), chan.size());
        }

        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "#p %s %s %s", wav, memo, PICKER));
        out.add(String.format(Locale.ROOT, "#p %02d %02d %02d %02d %02d %02d",
            start.getYear() % 100, start.getMonthValue(), start.getDayOfMonth(),
            start.getHour(), start.getMinute(), start.getSecond()));

        for (ArrivalTime a : list) {
            String st = a.getStationName().trim();
            int ch = chan.get(st);
            int kind = "S".equalsIgnoreCase(a.getPhaseDescriptor()) ? 1
                : ("F".equalsIgnoreCase(a.getPhaseDescriptor()) ? 2 : 0);
            double[] span = pickSpanSeconds(a, start);
            int pol = polarityCode(a, kind);
            out.add(String.format(Locale.ROOT, "#p %04X %d %d %03d %d %03d %+d",
                ch & 0xFFFF, kind,
                (int) span[0], (int) span[1],
                (int) span[2], (int) span[3],
                pol));
        }

        LocalDateTime minute = start.withSecond(0).withNano(0);
        DateTimeFormatter made = DateTimeFormatter.ofPattern("yy/MM/dd HH:mm:ss", Locale.US);
        out.add(String.format(Locale.ROOT, "#s %s                   %s",
            DateTimeFormatter.ofPattern("yy/MM/dd HH:mm", Locale.US).format(minute),
            made.format(LocalDateTime.now())));

        Map<String, ArrivalTime> pBy = byPhase(list, "P");
        Map<String, ArrivalTime> sBy = byPhase(list, "S");
        List<String> stations = new ArrayList<>(chan.keySet());
        stations.sort(String.CASE_INSENSITIVE_ORDER);
        for (String st : stations) {
            ArrivalTime p = pBy.get(st);
            ArrivalTime s = sBy.get(st);
            if (p == null && s == null) {
                continue;
            }
            double pSec = p == null ? 0.0 : secondsFrom(minute, p.getArrivalTime());
            double sSec = s == null ? 0.0 : secondsFrom(minute, s.getArrivalTime());
            double pErr = errOf(p, DEFAULT_P_ERR_S);
            double sErr = errOf(s, DEFAULT_S_ERR_S);
            String pol = p == null ? "." : polarityLetter(p);
            double lat = 0.0;
            double lon = 0.0;
            double elevM = 0.0;
            CrossCorrelationPickingService.StationCoord c = coords == null ? null : coords.get(st);
            if (c != null && c.hasCoords) {
                lat = c.lat;
                lon = c.lon;
                if (Double.isFinite(c.depthKm)) {
                    elevM = -c.depthKm * 1000.0;
                }
            }
            out.add(String.format(Locale.US,
                "#s %-4s %1s %7.3f%6.3f %7.3f%6.3f%6.1f%10.2e%11.5f%11.5f%8.0f",
                st.length() > 4 ? st.substring(0, 4) : st,
                pol,
                pSec, pErr, sSec, sErr,
                0.0, 0.0,
                lat, lon, elevM));
        }
        out.add("#s");
        Files.write(file, out, StandardCharsets.US_ASCII);
    }

    static Date earliest(Collection<ArrivalTime> arrivals, String phase) {
        Date best = null;
        if (arrivals == null) {
            return null;
        }
        for (ArrivalTime a : arrivals) {
            if (a == null || a.getArrivalTime() == null || !phase.equalsIgnoreCase(a.getPhaseDescriptor())) {
                continue;
            }
            if (best == null || a.getArrivalTime().before(best)) {
                best = a.getArrivalTime();
            }
        }
        return best;
    }

    /** {startSec, startMs, endSec, endMs} from the waveform origin. */
    static double[] pickSpanSeconds(ArrivalTime a, LocalDateTime waveStart) {
        double t = secondsFrom(waveStart, a.getArrivalTime());
        double half = errOf(a, 0.003);
        double a0 = Math.max(0.0, t - half);
        double a1 = Math.max(a0, t + half);
        return new double[] {
            Math.floor(a0), Math.round((a0 - Math.floor(a0)) * 1000.0),
            Math.floor(a1), Math.round((a1 - Math.floor(a1)) * 1000.0)
        };
    }

    static double secondsFrom(LocalDateTime origin, Date t) {
        long o = origin.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return (t.getTime() - o) / 1000.0;
    }

    static double errOf(ArrivalTime a, double fallback) {
        if (a != null && a.getErrMag() > 0.0f && a.getErrMag() < 10.0f) {
            return a.getErrMag();
        }
        return fallback;
    }

    static int polarityCode(ArrivalTime a, int kind) {
        if (kind != 0) {
            return 0;
        }
        String p = polarityLetter(a);
        if ("U".equals(p)) {
            return 1;
        }
        if ("D".equals(p)) {
            return -1;
        }
        return 0;
    }

    static String polarityLetter(ArrivalTime a) {
        if (a == null) {
            return ".";
        }
        String fm = a.getFirstMotion();
        if (fm == null) {
            return ".";
        }
        String s = fm.trim();
        if (s.isEmpty() || "?".equals(s) || ".".equals(s)) {
            return ".";
        }
        char c = Character.toUpperCase(s.charAt(0));
        if (c == 'U' || c == '+' || c == 'C') {
            return "U";
        }
        if (c == 'D' || c == '-' || c == 'X') {
            return "D";
        }
        return ".";
    }

    private static Map<String, ArrivalTime> byPhase(List<ArrivalTime> list, String phase) {
        Map<String, ArrivalTime> m = new LinkedHashMap<>();
        for (ArrivalTime a : list) {
            if (phase.equalsIgnoreCase(a.getPhaseDescriptor())) {
                m.put(a.getStationName().trim(), a);
            }
        }
        return m;
    }
}
