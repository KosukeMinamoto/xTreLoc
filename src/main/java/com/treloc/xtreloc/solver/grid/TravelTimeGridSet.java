package com.treloc.xtreloc.solver.grid;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Station-wise travel-time grids of one or more phases ({@code phase -> station label -> grid}). */
public final class TravelTimeGridSet {

    private final Map<String, Map<String, StationTimeGrid>> grids = new LinkedHashMap<>();
    private final String description;

    public TravelTimeGridSet(String description) {
        this.description = description == null ? "" : description;
    }

    public void add(StationTimeGrid g) {
        grids.computeIfAbsent(g.phase(), k -> new LinkedHashMap<>()).put(g.label(), g);
    }

    /** Grid of the station and phase, or {@code null}. */
    public StationTimeGrid get(String phase, String label) {
        Map<String, StationTimeGrid> m = grids.get(phase);
        return m == null ? null : m.get(StationTimeGrid.sanitize(label));
    }

    public List<String> phases() {
        return new ArrayList<>(grids.keySet());
    }

    public List<String> labels(String phase) {
        Map<String, StationTimeGrid> m = grids.get(phase);
        return m == null ? Collections.emptyList() : new ArrayList<>(m.keySet());
    }

    public int size() {
        int n = 0;
        for (Map<String, StationTimeGrid> m : grids.values()) {
            n += m.size();
        }
        return n;
    }

    public String description() {
        return description;
    }

    /** Writes every grid as {@code <root>.<PHASE>.<label>.time.hdr/.buf}. */
    public void write(Path dir, String root, ByteOrder order) throws IOException {
        for (Map<String, StationTimeGrid> m : grids.values()) {
            for (StationTimeGrid g : m.values()) {
                g.write(dir, root, order);
            }
        }
    }

    public void write(Path dir, String root) throws IOException {
        write(dir, root, ByteOrder.LITTLE_ENDIAN);
    }

    /** A grid file of a directory (header only, no data read). */
    public static final class GridRef {
        public final String phase;
        public final String label;
        public final Path header;

        GridRef(String phase, String label, Path header) {
            this.phase = phase;
            this.label = label;
            this.header = header;
        }
    }

    /** Lists the grids of a directory (optionally of one root) without reading their data, sorted by name. */
    public static List<GridRef> list(Path dir, String root) throws IOException {
        dir = GridTravelTimeTable.resolveGridDir(dir);
        TreeSet<Path> hdrs = new TreeSet<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.time.hdr")) {
            for (Path p : ds) {
                if (root == null || p.getFileName().toString().startsWith(root + ".")) {
                    hdrs.add(p);
                }
            }
        }
        List<GridRef> out = new ArrayList<>();
        for (Path h : hdrs) {
            String name = h.getFileName().toString();
            String r = GridTravelTimeTable.rootOf(name);
            if (r == null) {
                continue;
            }
            String rest = name.substring(r.length() + 1, name.length() - ".time.hdr".length()); // PHASE.label
            int dot = rest.indexOf('.');
            if (dot > 0 && StationTimeGrid.isComplete(h)) {
                out.add(new GridRef(rest.substring(0, dot), rest.substring(dot + 1), h));
            }
        }
        return out;
    }

    /**
     * Reads all {@code *.time.hdr} grids of a directory.
     *
     * @param root only files starting with {@code root + "."}; {@code null} reads every grid
     */
    public static TravelTimeGridSet read(Path dir, String root) throws IOException {
        return read(dir, root, null);
    }

    /**
     * Reads grids of a directory. When {@code phaseOnly} is non-null (e.g. {@code "S"}), other phases are skipped
     * so solvers that use S only do not keep P volumes in memory.
     */
    public static TravelTimeGridSet read(Path dir, String root, String phaseOnly) throws IOException {
        if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }
        dir = GridTravelTimeTable.resolveGridDir(dir);
        TreeSet<Path> hdrs = new TreeSet<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.time.hdr")) {
            for (Path p : ds) {
                if (root == null || p.getFileName().toString().startsWith(root + ".")) {
                    hdrs.add(p);
                }
            }
        }
        if (hdrs.isEmpty()) {
            throw new IOException("No *.time.hdr travel-time grids found in " + dir
                + (root == null ? "" : " for root '" + root + "'"));
        }
        String want = phaseOnly == null || phaseOnly.isBlank()
            ? null : phaseOnly.trim().toUpperCase();
        TravelTimeGridSet set = new TravelTimeGridSet(dir.toString());
        int skipped = 0;
        for (Path h : hdrs) {
            if (!StationTimeGrid.isComplete(h)) {
                skipped++;
                continue;
            }
            if (want != null) {
                String ph = phaseOf(h.getFileName().toString(), root);
                if (ph != null && !want.equalsIgnoreCase(ph)) {
                    continue;
                }
            }
            set.add(StationTimeGrid.read(h));
        }
        if (set.size() == 0) {
            throw new IOException("No complete travel-time grids in " + dir
                + (root == null ? "" : " for root '" + root + "'")
                + (skipped > 0 ? " (" + skipped + " incomplete .hdr/.buf pair(s) skipped)" : ""));
        }
        return set;
    }

    static String phaseOf(String fileName, String root) {
        String r = GridTravelTimeTable.rootOf(fileName);
        if (r == null) {
            return null;
        }
        if (root != null && !r.equals(root)) {
            return null;
        }
        String rest = fileName.substring(r.length() + 1, fileName.length() - ".time.hdr".length());
        int dot = rest.indexOf('.');
        return dot > 0 ? rest.substring(0, dot) : null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TravelTimeGridSet[");
        for (Map.Entry<String, Map<String, StationTimeGrid>> e : grids.entrySet()) {
            sb.append(e.getKey()).append(':').append(e.getValue().size()).append(' ');
        }
        return sb.toString().trim() + "]";
    }
}
