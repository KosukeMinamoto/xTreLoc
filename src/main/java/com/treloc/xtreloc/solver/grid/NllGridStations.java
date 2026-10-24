package com.treloc.xtreloc.solver.grid;

import com.treloc.xtreloc.io.Station;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Station list from NonLinLoc travel-time headers ({@code &lt;root&gt;.&lt;PHASE&gt;.&lt;label&gt;.time.hdr}).
 * The source line is converted with {@code TRANSFORM SIMPLE} or {@code LAMBERT}.
 */
public final class NllGridStations {

    private NllGridStations() {
    }

    public static boolean isNllStationSource(Path spec) {
        if (spec == null) {
            return false;
        }
        try {
            return !headersOf(spec).isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Stable identity of one NonLinLoc grid set (directory + sorted roots).
     * Rebuilds of the same output directory keep this key so stations are not added twice.
     */
    public static String setKey(Path spec) {
        if (spec == null || !isNllStationSource(spec)) {
            return null;
        }
        try {
            List<Path> hdrs = headersOf(spec);
            if (hdrs.isEmpty()) {
                return null;
            }
            Path dir = hdrs.get(0).toAbsolutePath().getParent();
            TreeSet<String> roots = new TreeSet<>();
            for (Path h : hdrs) {
                String root = GridTravelTimeTable.rootOf(h.getFileName().toString());
                if (root != null && !root.isBlank()) {
                    roots.add(root);
                }
            }
            return (dir == null ? "" : dir.toString()) + "|" + String.join(",", roots);
        } catch (IOException e) {
            return spec.toAbsolutePath().toString();
        }
    }

    public static List<Station> read(Path spec) throws IOException {
        List<Path> hdrs = headersOf(spec);
        if (hdrs.isEmpty()) {
            throw new IOException("No NonLinLoc *.time.hdr files at " + spec);
        }
        Map<String, Station> byCode = new LinkedHashMap<>();
        IOException last = null;
        for (Path h : hdrs) {
            try {
                StationTimeGrid.Header head = StationTimeGrid.parseHeader(h);
                Station s = head.toStation();
                if (s != null) {
                    byCode.putIfAbsent(s.getCode().toUpperCase(Locale.ROOT), s);
                }
            } catch (IOException e) {
                last = e;
            }
        }
        if (byCode.isEmpty()) {
            throw last != null ? last : new IOException("No stations with a geographic TRANSFORM in " + spec);
        }
        List<Station> out = new ArrayList<>(byCode.values());
        out.sort(Comparator.comparing(Station::getCode, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static List<Path> headersOf(Path spec) throws IOException {
        List<Path> out = new ArrayList<>();
        if (spec == null || !Files.exists(spec)) {
            return out;
        }
        if (Files.isRegularFile(spec)) {
            String name = spec.getFileName().toString();
            if (name.endsWith(".time.hdr")) {
                Path dir = spec.toAbsolutePath().getParent();
                String root = GridTravelTimeTable.rootOf(name);
                collect(dir, root, out);
                if (out.isEmpty()) {
                    out.add(spec);
                }
                return out;
            }
            if (name.endsWith(".hdr")) {
                Path parent = spec.toAbsolutePath().getParent();
                if (parent != null) {
                    collect(parent.resolve("time"), null, out);
                    if (out.isEmpty() && parent.getParent() != null) {
                        collect(parent.getParent().resolve("time"), null, out);
                    }
                }
                return out;
            }
            return out;
        }
        if (Files.isDirectory(spec)) {
            collect(spec, null, out);
            if (out.isEmpty()) {
                collect(spec.resolve("time"), null, out);
            }
        }
        return out;
    }

    private static void collect(Path dir, String root, List<Path> out) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.time.hdr")) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (root == null || name.startsWith(root + ".")) {
                    out.add(p);
                }
            }
        }
    }
}
