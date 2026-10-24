package com.treloc.xtreloc.solver.grid;

import com.treloc.xtreloc.solver.eikonal.EikonalGrid3D;
import com.treloc.xtreloc.solver.eikonal.LocalFrame;
import com.treloc.xtreloc.solver.eikonal.TravelTimeField3D;
import com.treloc.xtreloc.solver.table.TravelTimeTable.Phase;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds station-wise P / S travel-time grids of a {@link LayerCakeModel} (optionally with dipping layers) by
 * solving the eikonal equation from every station (reciprocity) and resampling onto a NonLinLoc-style grid.
 *
 * <p>The output grid has its origin at the region centre, is aligned to the {@link NllSimpleTransform}
 * ({@code TRANSFORM SIMPLE}) and covers {@code x in [-hx dx, hx dx]}, {@code y in [-hy dx, hy dx]},
 * {@code z in [zmin, zmax]} with NonLinLoc {@code dx = dy =} horizontal spacing and {@code dz} depth spacing.
 * Positions outside it are "out of range" for the solver. Stations must lie inside the
 * region ({@link Region#fitStations} gives a region that includes them).</p>
 */
public final class TravelTimeGridBuilder {

    /** Region and sampling of the output grid. */
    public static final class Region {
        public final double centerLat;
        public final double centerLon;
        public final double halfWidthXKm;
        public final double halfWidthYKm;
        public final double minDepthKm;
        public final double maxDepthKm;
        /** Horizontal spacing (km); NonLinLoc {@code dx = dy}. Same as {@link #spacingHorizKm}. */
        public final double spacingKm;
        public final double spacingHorizKm;
        /** Depth spacing (km); NonLinLoc {@code dz}. */
        public final double spacingDepthKm;

        public Region(double centerLat, double centerLon, double halfWidthXKm, double halfWidthYKm,
                      double maxDepthKm, double spacingKm) {
            this(centerLat, centerLon, halfWidthXKm, halfWidthYKm, 0.0, maxDepthKm, spacingKm, spacingKm);
        }

        public Region(double centerLat, double centerLon, double halfWidthXKm, double halfWidthYKm,
                      double minDepthKm, double maxDepthKm, double spacingKm) {
            this(centerLat, centerLon, halfWidthXKm, halfWidthYKm, minDepthKm, maxDepthKm, spacingKm, spacingKm);
        }

        public Region(double centerLat, double centerLon, double halfWidthXKm, double halfWidthYKm,
                      double minDepthKm, double maxDepthKm, double spacingHorizKm, double spacingDepthKm) {
            if (!(halfWidthXKm > 0) || !(halfWidthYKm > 0) || !(maxDepthKm > minDepthKm)
                || !(spacingHorizKm > 0) || !(spacingDepthKm > 0)) {
                throw new IllegalArgumentException("Region extents and spacing must be positive (max depth > min depth)");
            }
            this.centerLat = centerLat;
            this.centerLon = centerLon;
            this.halfWidthXKm = halfWidthXKm;
            this.halfWidthYKm = halfWidthYKm;
            this.minDepthKm = minDepthKm;
            this.maxDepthKm = maxDepthKm;
            this.spacingKm = spacingHorizKm;
            this.spacingHorizKm = spacingHorizKm;
            this.spacingDepthKm = spacingDepthKm;
        }

        /** Region covering a longitude / latitude box (centre = middle of the box). */
        public static Region fromLonLatBox(double lonMin, double lonMax, double latMin, double latMax,
                                           double maxDepthKm, double spacingKm) {
            return fromLonLatBox(lonMin, lonMax, latMin, latMax, 0.0, maxDepthKm, spacingKm, spacingKm);
        }

        public static Region fromLonLatBox(double lonMin, double lonMax, double latMin, double latMax,
                                           double minDepthKm, double maxDepthKm, double spacingKm) {
            return fromLonLatBox(lonMin, lonMax, latMin, latMax, minDepthKm, maxDepthKm, spacingKm, spacingKm);
        }

        public static Region fromLonLatBox(double lonMin, double lonMax, double latMin, double latMax,
                                           double minDepthKm, double maxDepthKm,
                                           double spacingHorizKm, double spacingDepthKm) {
            if (!(lonMax > lonMin) || !(latMax > latMin) || !(maxDepthKm > minDepthKm)) {
                throw new IllegalArgumentException("Region must satisfy min < max");
            }
            double cLat = 0.5 * (latMin + latMax);
            double cLon = 0.5 * (lonMin + lonMax);
            NllSimpleTransform tr = new NllSimpleTransform(cLat, cLon);
            double hx = 0.5 * (lonMax - lonMin) * tr.kmPerDegLon();
            double hy = 0.5 * (latMax - latMin) * NllSimpleTransform.KM_PER_DEG;
            return new Region(cLat, cLon, hx, hy, minDepthKm, maxDepthKm, spacingHorizKm, spacingDepthKm);
        }

        /**
         * Region centred on the bounding box of the stations, extended by {@code marginKm} on every side
         * (at least {@code marginKm} wide).
         */
        public static Region fitStations(List<StationPoint> stations, double marginKm, double maxDepthKm,
                                         double spacingKm) {
            return fitStations(stations, marginKm, maxDepthKm, spacingKm, spacingKm);
        }

        public static Region fitStations(List<StationPoint> stations, double marginKm, double maxDepthKm,
                                         double spacingHorizKm, double spacingDepthKm) {
            if (stations.isEmpty()) {
                throw new IllegalArgumentException("No stations");
            }
            double latMin = Double.POSITIVE_INFINITY;
            double latMax = Double.NEGATIVE_INFINITY;
            double lonMin = Double.POSITIVE_INFINITY;
            double lonMax = Double.NEGATIVE_INFINITY;
            for (StationPoint s : stations) {
                latMin = Math.min(latMin, s.lat);
                latMax = Math.max(latMax, s.lat);
                lonMin = Math.min(lonMin, s.lon);
                lonMax = Math.max(lonMax, s.lon);
            }
            double cLat = 0.5 * (latMin + latMax);
            double cLon = 0.5 * (lonMin + lonMax);
            NllSimpleTransform tr = new NllSimpleTransform(cLat, cLon);
            double hx = marginKm;
            double hy = marginKm;
            for (StationPoint s : stations) {
                double[] xy = tr.toXY(s.lat, s.lon);
                hx = Math.max(hx, Math.abs(xy[0]) + marginKm);
                hy = Math.max(hy, Math.abs(xy[1]) + marginKm);
            }
            return new Region(cLat, cLon, hx, hy, 0.0, maxDepthKm, spacingHorizKm, spacingDepthKm);
        }

        int hx() { return (int) Math.ceil(halfWidthXKm / spacingHorizKm); }
        int hy() { return (int) Math.ceil(halfWidthYKm / spacingHorizKm); }
        int nz() { return (int) Math.ceil((maxDepthKm - minDepthKm) / spacingDepthKm) + 1; }

        public long nodeCount() {
            return (long) (2 * hx() + 1) * (2 * hy() + 1) * nz();
        }
    }

    /** A station position; {@code depthKm} positive down (negative = above sea level, placed at {@code minDepthKm}). */
    public static final class StationPoint {
        public final String label;
        public final double lat;
        public final double lon;
        public final double depthKm;

        public StationPoint(String label, double lat, double lon, double depthKm) {
            this.label = label;
            this.lat = lat;
            this.lon = lon;
            this.depthKm = depthKm;
        }
    }

    /** Progress callback; {@code done} of {@code total} station/phase grids are finished. */
    @FunctionalInterface
    public interface Progress {
        void update(int done, int total, String message);
    }

    private static final long MAX_OUTPUT_NODES = 400_000_000L;

    private final LayerCakeModel model;
    private final Region region;
    private final boolean earthFlattening;
    private final Set<Phase> phases;

    public TravelTimeGridBuilder(LayerCakeModel model, Region region, boolean earthFlattening, Set<Phase> phases) {
        if (phases == null || phases.isEmpty()) {
            throw new IllegalArgumentException("At least one phase is required");
        }
        this.model = model;
        this.region = region;
        this.earthFlattening = earthFlattening;
        this.phases = EnumSet.copyOf(phases);
        if (region.nodeCount() > MAX_OUTPUT_NODES) {
            throw new IllegalArgumentException("Output grid too large: " + region.nodeCount()
                + " nodes. Increase the spacing or reduce the region.");
        }
    }

    /** Number of nodes of one output grid. */
    public long nodesPerGrid() {
        return region.nodeCount();
    }

    public TravelTimeGridSet build(List<StationPoint> stations, Progress progress) throws Exception {
        return build(stations, progress, null);
    }

    /**
     * Same as {@link #build(List, Progress)}; with a {@code sink} every grid is handed over as soon as it is
     * finished (called from worker threads, must be thread-safe) and is not kept in memory, so large sets can be
     * written directly. The returned set is then empty.
     */
    public TravelTimeGridSet build(List<StationPoint> stations, Progress progress,
                                   java.util.function.Consumer<StationTimeGrid> sink) throws Exception {
        if (stations.isEmpty()) {
            throw new IllegalArgumentException("No stations");
        }
        final double hxy = region.spacingHorizKm;
        final double hz = region.spacingDepthKm;
        final int hx = region.hx();
        final int hy = region.hy();
        final int nx = 2 * hx + 1;
        final int ny = 2 * hy + 1;
        final int nz = region.nz();
        final double x0 = -hx * hxy;
        final double y0 = -hy * hxy;
        final double z0 = region.minDepthKm;
        final NllSimpleTransform nll = new NllSimpleTransform(region.centerLat, region.centerLon);
        final LocalFrame frame = new LocalFrame(region.centerLat, region.centerLon);

        // The eikonal grid is padded: its frame (geodesic) differs slightly from the simple output transform.
        double hPad = Math.max(hxy, hz);
        double pad = Math.max(3 * hPad, 0.02 * Math.max(region.halfWidthXKm, region.halfWidthYKm)) + 2 * hPad;
        final EikonalGrid3D.Spec spec = new EikonalGrid3D.Spec(region.centerLat, region.centerLon,
            hx * hxy + pad, hy * hxy + pad, region.maxDepthKm + pad, hxy, hz, earthFlattening);

        // eikonal-frame position of every output column (one geodesic computation per column)
        final double[][] exy = new double[nx * ny][];
        for (int ix = 0; ix < nx; ix++) {
            for (int iy = 0; iy < ny; iy++) {
                double[] ll = nll.toLatLon(x0 + ix * hxy, y0 + iy * hxy);
                exy[ix * ny + iy] = frame.toXY(ll[0], ll[1]);
            }
        }

        // validate stations before the expensive part
        final List<double[]> stationXY = new ArrayList<>();
        final double zMax = z0 + (nz - 1) * hz;
        for (StationPoint s : stations) {
            double[] xy = nll.toXY(s.lat, s.lon);
            if (xy[0] < x0 || xy[0] > x0 + (nx - 1) * hxy || xy[1] < y0 || xy[1] > y0 + (ny - 1) * hxy) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Station %s (%.4f, %.4f, depth %.3f km) is outside the grid region "
                        + "(x %.1f..%.1f km, y %.1f..%.1f km, depth %.1f..%.1f km). Enlarge the region.",
                    s.label, s.lat, s.lon, s.depthKm, x0, x0 + (nx - 1) * hxy, y0, y0 + (ny - 1) * hxy,
                    z0, zMax));
            }
            // Mountain stations (depth < z0, often < 0) sit on the top of the grid.
            double z = Math.min(zMax, Math.max(z0, s.depthKm));
            stationXY.add(new double[] {xy[0], xy[1], z});
        }

        // one eikonal grid (slowness) per phase, shared by all stations
        final List<Phase> phaseList = new ArrayList<>(phases);
        final List<EikonalGrid3D> eg = new ArrayList<>();
        for (Phase ph : phaseList) {
            eg.add(EikonalGrid3D.build(model.velocityFunction(ph), spec));
        }

        final int total = stations.size() * phaseList.size();
        long nodesEik = eg.get(0).nodeCount();
        // transient memory per worker: tau (double) + field (float) + output (float) ~ 20 bytes per node
        long perWorker = Math.max(1L, nodesEik * 20L + (long) nx * ny * nz * 4L);
        int threads = (int) Math.max(1L, Math.min(Runtime.getRuntime().availableProcessors(),
            (long) (Runtime.getRuntime().maxMemory() * 0.6) / perWorker));
        threads = Math.min(threads, total);

        final TravelTimeGridSet set = new TravelTimeGridSet(
            model + ", region " + nx + "x" + ny + "x" + nz + " dx=dy=" + hxy + " dz=" + hz + " km");
        final AtomicInteger done = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<StationTimeGrid>> futures = new ArrayList<>();
            for (int si = 0; si < stations.size(); si++) {
                for (int pi = 0; pi < phaseList.size(); pi++) {
                    final StationPoint st = stations.get(si);
                    final double[] sxy = stationXY.get(si);
                    final Phase ph = phaseList.get(pi);
                    final EikonalGrid3D grid = eg.get(pi);
                    Callable<StationTimeGrid> job = () -> {
                        double[] ll = nll.toLatLon(sxy[0], sxy[1]);
                        TravelTimeField3D field;
                        try {
                            field = grid.solveLatLon(ll[0], ll[1], sxy[2]);
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("Station " + st.label + ": " + e.getMessage(), e);
                        }
                        float[] t = new float[nx * ny * nz];
                        double[] buf = new double[4];
                        for (int ix = 0; ix < nx; ix++) {
                            for (int iy = 0; iy < ny; iy++) {
                                double[] e = exy[ix * ny + iy];
                                int base = (ix * ny + iy) * nz;
                                for (int iz = 0; iz < nz; iz++) {
                                    if (!field.timeAndGradient(e[0], e[1], z0 + iz * hz, buf)) {
                                        throw new IllegalStateException("Output node outside the eikonal grid ("
                                            + ix + "," + iy + "," + iz + ")");
                                    }
                                    t[base + iz] = (float) buf[0];
                                }
                            }
                        }
                        StationTimeGrid g = new StationTimeGrid(st.label, ph.name(), nx, ny, nz,
                            x0, y0, z0, hxy, hxy, hz, sxy[0], sxy[1], sxy[2], nll, t);
                        if (sink != null) {
                            sink.accept(g);
                        }
                        if (progress != null) {
                            progress.update(done.incrementAndGet(), total, ph.name() + " " + st.label);
                        }
                        return sink == null ? g : null;
                    };
                    futures.add(pool.submit(job));
                }
            }
            for (Future<StationTimeGrid> f : futures) {
                try {
                    StationTimeGrid g = f.get();
                    if (g != null) {
                        set.add(g);
                    }
                } catch (ExecutionException e) {
                    Throwable c = e.getCause();
                    if (c instanceof Exception) {
                        throw (Exception) c;
                    }
                    throw e;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return set;
    }
}
