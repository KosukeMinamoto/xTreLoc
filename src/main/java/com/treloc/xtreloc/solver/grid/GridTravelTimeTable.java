package com.treloc.xtreloc.solver.grid;

import com.treloc.xtreloc.solver.table.TravelTimeTable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link TravelTimeTable} backed by station-wise 3-D travel-time grids ({@link TravelTimeGridSet}, NonLinLoc
 * {@code TIME} format). Stations are identified by position: the source position stored in each grid header is
 * matched with the station coordinates passed by the solver (horizontal distance within about 200 m, depth
 * within 0.5 km after clamping to the surface), so no station codes are needed.
 *
 * <p>Range semantics: a source outside the grids (laterally, above the surface or below the grid bottom) is out of
 * range ({@link #containsSource} false; travel times NaN). Mixed-geometry folders use the union of station grids
 * so a point inside any station grid is in range; a station whose own grid misses the point still returns NaN
 * (failed ray). A station without a matching grid gets NaN, i.e. it is treated as a failed ray (outlier station).</p>
 *
 * <p>Thread-safe.</p>
 */
public final class GridTravelTimeTable implements TravelTimeTable {

    private static final Logger logger = Logger.getLogger(GridTravelTimeTable.class.getName());
    private static final double MATCH_DEG = 0.002;
    private static final double MATCH_DEPTH_KM = 0.5;

    private static final class Entry {
        final StationTimeGrid grid;
        final double lat;
        final double lon;
        final double depth;

        Entry(StationTimeGrid g) {
            this.grid = g;
            double[] ll = g.transform().toLatLon(g.sourceX(), g.sourceY());
            this.lat = ll[0];
            this.lon = ll[1];
            this.depth = g.sourceZ();
        }
    }

    private static final class Key {
        final boolean s;
        final double lat;
        final double lon;
        final double dep;

        Key(boolean s, double lat, double lon, double dep) {
            this.s = s;
            this.lat = lat;
            this.lon = lon;
            this.dep = dep;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) {
                return false;
            }
            Key k = (Key) o;
            return s == k.s && lat == k.lat && lon == k.lon && dep == k.dep;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(s, lat, lon, dep);
        }
    }

    private final String name;
    private final List<Entry> pEntries = new ArrayList<>();
    private final List<Entry> sEntries = new ArrayList<>();
    private final Map<Key, Object> cache = new ConcurrentHashMap<>();
    private static final Object NONE = new Object();
    private final boolean uniform;

    public GridTravelTimeTable(String name, TravelTimeGridSet set) {
        this.name = name;
        for (String ph : set.phases()) {
            for (String label : set.labels(ph)) {
                StationTimeGrid g = set.get(ph, label);
                if (g.transform() == null) {
                    throw new IllegalArgumentException("Grid " + label + "." + ph
                        + " has no geographic transform (TRANSFORM SIMPLE or LAMBERT is required)");
                }
                if ("P".equals(ph)) {
                    pEntries.add(new Entry(g));
                } else if ("S".equals(ph)) {
                    sEntries.add(new Entry(g));
                }
            }
        }
        if (pEntries.isEmpty() && sEntries.isEmpty()) {
            throw new IllegalArgumentException("Travel-time grids must include P or S");
        }
        this.uniform = sameGeometry(rangeEntries());
    }

    private static boolean sameGeometry(List<Entry> es) {
        StationTimeGrid a = es.get(0).grid;
        for (Entry e : es) {
            StationTimeGrid b = e.grid;
            if (a.nx() != b.nx() || a.ny() != b.ny() || a.nz() != b.nz()
                || a.x0() != b.x0() || a.y0() != b.y0() || a.z0() != b.z0()
                || a.dx() != b.dx() || a.dy() != b.dy() || a.dz() != b.dz()
                || a.transform().lat0() != b.transform().lat0() || a.transform().lon0() != b.transform().lon0()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ detection / loading

    /**
     * True if {@code spec} is a directory with {@code *.time.hdr} grids or one such header file.
     */
    public static boolean isGridSpec(String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            return false;
        }
        try {
            Path p = Paths.get(spec.trim());
            if (Files.isDirectory(p)) {
                return hasTimeHeaders(p) || hasTimeHeaders(p.resolve("time"));
            }
            return Files.isRegularFile(p) && p.getFileName().toString().endsWith(".time.hdr");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Loads the grids of a directory, or of the file-name root of a given {@code .time.hdr} file. A directory with
     * the grids of several roots is ambiguous and rejected.
     */
    public static GridTravelTimeTable load(String spec) throws IOException {
        Path p = Paths.get(spec.trim());
        Path dir;
        String root = null;
        if (Files.isDirectory(p)) {
            dir = resolveGridDir(p);
            TreeSet<String> roots = new TreeSet<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(p, "*.time.hdr")) {
                for (Path h : ds) {
                    if (!StationTimeGrid.isComplete(h)) {
                        continue;
                    }
                    String r = rootOf(h.getFileName().toString());
                    if (r != null) {
                        roots.add(r);
                    }
                }
            }
            if (roots.size() > 1) {
                throw new IOException("Several grid sets in " + p + " " + roots
                    + "; give one of the .time.hdr files to select the set");
            }
        } else {
            dir = p.toAbsolutePath().getParent();
            root = rootOf(p.getFileName().toString());
            if (root == null) {
                throw new IOException("Not a travel-time grid header (<root>.<PHASE>.<station>.time.hdr): " + spec);
            }
        }
        TravelTimeGridSet set = TravelTimeGridSet.read(dir, root, null);
        GridTravelTimeTable t = new GridTravelTimeTable(dir.getFileName() + (root == null ? "" : "/" + root), set);
        logger.log(Level.INFO, "Travel-time engine: {0}", t.describe());
        return t;
    }

    static boolean hasTimeHeaders(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.time.hdr")) {
            return ds.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    static Path resolveGridDir(Path dir) {
        if (hasTimeHeaders(dir)) {
            return dir;
        }
        Path time = dir.resolve("time");
        return hasTimeHeaders(time) ? time : dir;
    }

    /** {@code <root>} of {@code <root>.<PHASE>.<label>.time.hdr}, or null. */
    public static String rootOf(String fileName) {
        if (!fileName.endsWith(".time.hdr")) {
            return null;
        }
        String base = fileName.substring(0, fileName.length() - ".time.hdr".length());
        int a = base.lastIndexOf('.');
        if (a <= 0) {
            return null;
        }
        int b = base.lastIndexOf('.', a - 1);
        return b <= 0 ? null : base.substring(0, b);
    }

    // ------------------------------------------------------------------ lookups

    private Entry find(Phase phase, double stnLat, double stnLon, double stnDep) {
        boolean s = phase == Phase.S;
        Key k = new Key(s, stnLat, stnLon, stnDep);
        Object o = cache.get(k);
        if (o == null) {
            o = match(s ? sEntries : pEntries, stnLat, stnLon, stnDep);
            if (o == null) {
                o = NONE;
                logger.warning(String.format(Locale.ROOT,
                    "No %s travel-time grid for the station at lat=%.5f lon=%.5f depth=%.3f km; "
                        + "it is treated as an outlier.", phase, stnLat, stnLon, stnDep));
            }
            cache.put(k, o);
        }
        return o == NONE ? null : (Entry) o;
    }

    private static Entry match(List<Entry> es, double lat, double lon, double dep) {
        Entry best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double cosLat = Math.cos(Math.toRadians(lat));
        for (Entry e : es) {
            double dlat = Math.abs(e.lat - lat);
            double dlon = Math.abs(e.lon - lon) * cosLat;
            double dz = Math.abs(Math.max(0.0, e.depth) - Math.max(0.0, dep));
            if (dlat > MATCH_DEG || dlon > MATCH_DEG || dz > MATCH_DEPTH_KM) {
                continue;
            }
            double score = Math.hypot(dlat, dlon) * 111.19 + dz;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ TravelTimeTable

    private List<Entry> rangeEntries() {
        if (!sEntries.isEmpty() && !pEntries.isEmpty()) {
            List<Entry> all = new ArrayList<>(sEntries.size() + pEntries.size());
            all.addAll(sEntries);
            all.addAll(pEntries);
            return all;
        }
        return sEntries.isEmpty() ? pEntries : sEntries;
    }

    @Override
    public boolean containsSource(double lat, double lon, double depthKm) {
        if (!(depthKm >= 0.0) || Double.isNaN(lat + lon)) {
            return false;
        }
        List<Entry> es = rangeEntries();
        if (uniform) {
            return es.get(0).grid.contains(lat, lon, depthKm);
        }
        for (Entry e : es) {
            if (e.grid.contains(lat, lon, depthKm)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double travelTime(Phase phase, double srcLat, double srcLon, double srcDepKm,
                             double stnLat, double stnLon, double stnDepKm) {
        Entry e = find(phase, stnLat, stnLon, stnDepKm);
        return e == null ? Double.NaN : e.grid.time(srcLat, srcLon, srcDepKm);
    }

    @Override
    public double[] sourceDerivatives(Phase phase, double srcLat, double srcLon, double srcDepKm,
                                      double stnLat, double stnLon, double stnDepKm) {
        Entry e = find(phase, stnLat, stnLon, stnDepKm);
        if (e == null) {
            return null;
        }
        double[] xy = e.grid.transform().toXY(srcLat, srcLon);
        double[] out = new double[4];
        if (!e.grid.evaluate(xy[0], xy[1], srcDepKm, out)) {
            return null;
        }
        return new double[] {out[1] * e.grid.transform().kmPerDegLon(), out[2] * e.grid.transform().kmPerDegLat(), out[3]};
    }

    @Override
    public double takeoffAngleRad(Phase phase, double srcLat, double srcLon, double srcDepKm,
                                  double stnLat, double stnLon, double stnDepKm) {
        Entry e = find(phase, stnLat, stnLon, stnDepKm);
        if (e == null) {
            return Double.NaN;
        }
        double[] xy = e.grid.transform().toXY(srcLat, srcLon);
        double[] out = new double[4];
        if (!e.grid.evaluate(xy[0], xy[1], srcDepKm, out)) {
            return Double.NaN;
        }
        double g = Math.sqrt(out[1] * out[1] + out[2] * out[2] + out[3] * out[3]);
        if (!(g > 0.0)) {
            return Double.NaN;
        }
        // the ray leaves the source against the travel-time gradient; angle from the downward vertical
        return Math.acos(Math.max(-1.0, Math.min(1.0, -out[3] / g)));
    }

    /** Not station independent: always NaN (use {@link #sourceDerivatives}). */
    @Override
    public double velocityAtSource(Phase phase, double lat, double lon, double depthKm) {
        return Double.NaN;
    }

    @Override
    public String describe() {
        StationTimeGrid g = rangeEntries().get(0).grid;
        return String.format(Locale.ROOT,
            "%s travel-time grids %s (%d S / %d P stations, %dx%dx%d nodes, dx=%.2f dz=%.2f km, x %.1f..%.1f y %.1f..%.1f depth %.1f..%.1f km%s)",
            g.time2d() ? "2-D" : "3-D", name,
            sEntries.size(), pEntries.size(),
            g.nx(), g.ny(), g.nz(), g.dx(), g.dz(), g.x0(), g.xMax(), g.y0(), g.yMax(),
            g.z0(), g.zMax(),
            uniform ? "" : ", per-station geometry");
    }
}
