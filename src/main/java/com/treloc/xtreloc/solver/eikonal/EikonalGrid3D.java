package com.treloc.xtreloc.solver.eikonal;

/**
 * Cartesian grid carrying the slowness of a 3-D S-wave model, shared by the travel-time fields of all
 * stations. Node {@code (i, j, k)} is at {@code x = xMin + i hx}, {@code y = yMin + j hy} and model depth
 * {@code z = k hz}. Horizontal and depth spacing may differ (NonLinLoc {@code dx dy dz}).
 *
 * <p>With {@code earthFlattening} the vertical axis is the flattened depth
 * {@code z_f = -R ln(1 - z/R)} and the velocity is {@code v_f = v R/(R - z)} (the same transform as
 * {@link com.treloc.xtreloc.solver.Raytrace1D}), so a spherical 1-D model gives the spherical travel times.
 * All public methods take <em>physical</em> depth (km).</p>
 */
public final class EikonalGrid3D {

    /** Earth radius used by the flattening transform (km). */
    public static final double EARTH_RADIUS_KM = 6371.0;

    /** Velocity (km/s) at a point: local east / north (km) and physical depth (km). */
    @FunctionalInterface
    public interface VelocityFunction {
        double vs(double xKm, double yKm, double depthKm);
    }

    /** Geometry of a geographic grid. */
    public static final class Spec {
        public final double centerLat;
        public final double centerLon;
        public final double halfWidthXKm;
        public final double halfWidthYKm;
        public final double maxDepthKm;
        /** Horizontal spacing (km); same as {@link #spacingHorizKm}. */
        public final double spacingKm;
        public final double spacingHorizKm;
        public final double spacingDepthKm;
        public final boolean earthFlattening;

        public Spec(double centerLat, double centerLon, double halfWidthXKm, double halfWidthYKm,
                    double maxDepthKm, double spacingKm, boolean earthFlattening) {
            this(centerLat, centerLon, halfWidthXKm, halfWidthYKm, maxDepthKm, spacingKm, spacingKm, earthFlattening);
        }

        public Spec(double centerLat, double centerLon, double halfWidthXKm, double halfWidthYKm,
                    double maxDepthKm, double spacingHorizKm, double spacingDepthKm, boolean earthFlattening) {
            if (!(spacingHorizKm > 0.0) || !(spacingDepthKm > 0.0)
                || !(halfWidthXKm > 0.0) || !(halfWidthYKm > 0.0) || !(maxDepthKm > 0.0)) {
                throw new IllegalArgumentException("Grid extents and spacing must be positive");
            }
            this.centerLat = centerLat;
            this.centerLon = centerLon;
            this.halfWidthXKm = halfWidthXKm;
            this.halfWidthYKm = halfWidthYKm;
            this.maxDepthKm = maxDepthKm;
            this.spacingKm = spacingHorizKm;
            this.spacingHorizKm = spacingHorizKm;
            this.spacingDepthKm = spacingDepthKm;
            this.earthFlattening = earthFlattening;
        }
    }

    private static final int MAX_SWEEPS = 40;
    private static final double SWEEP_TOL = 1e-6;
    /** Hard limit on the number of nodes of one grid (memory guard). */
    private static final long MAX_NODES = 60_000_000L;

    private final int nx;
    private final int ny;
    private final int nz;
    private final double hx;
    private final double hy;
    private final double hz;
    private final double xMin;
    private final double yMin;
    private final boolean flattened;
    private final float[] slow;
    private final LocalFrame frame;

    private EikonalGrid3D(int nx, int ny, int nz, double hx, double hy, double hz, double xMin, double yMin,
                          boolean flattened, float[] slow, LocalFrame frame) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.hx = hx;
        this.hy = hy;
        this.hz = hz;
        this.xMin = xMin;
        this.yMin = yMin;
        this.flattened = flattened;
        this.slow = slow;
        this.frame = frame;
    }

    // ------------------------------------------------------------------ construction

    /** Samples the velocity of one vertical column (x, y) at the given physical depths. */
    @FunctionalInterface
    private interface ColumnSampler {
        void sample(double xKm, double yKm, double[] depthsKm, double[] outVs);
    }

    /**
     * Grid on local Cartesian coordinates (no geographic frame).
     *
     * @param nz        number of nodes along (model) depth; the grid spans model depth {@code 0 .. (nz-1) h}
     * @param flatten   apply the Earth-flattening transform
     */
    public static EikonalGrid3D cartesian(double xMin, double yMin, int nx, int ny, int nz, double h,
                                          boolean flatten, VelocityFunction f) {
        return build(xMin, yMin, nx, ny, nz, h, h, h, flatten, (x, y, depths, out) -> {
            for (int k = 0; k < depths.length; k++) {
                out[k] = f.vs(x, y, depths[k]);
            }
        }, null);
    }

    /** Grid around {@code spec.center} sampled from a geographic 3-D model. */
    public static EikonalGrid3D build(VelocityModel3D model, Spec spec) {
        final LocalFrame frame = new LocalFrame(spec.centerLat, spec.centerLon);
        int nhx = (int) Math.ceil(spec.halfWidthXKm / spec.spacingHorizKm);
        int nhy = (int) Math.ceil(spec.halfWidthYKm / spec.spacingHorizKm);
        int nx = 2 * nhx + 1;
        int ny = 2 * nhy + 1;
        double zfMax = spec.earthFlattening ? toFlatDepth(spec.maxDepthKm) : spec.maxDepthKm;
        int nz = (int) Math.ceil(zfMax / spec.spacingDepthKm) + 1;
        double xMin = -nhx * spec.spacingHorizKm;
        double yMin = -nhy * spec.spacingHorizKm;
        return build(xMin, yMin, nx, ny, nz, spec.spacingHorizKm, spec.spacingHorizKm, spec.spacingDepthKm,
            spec.earthFlattening, (x, y, depths, out) -> {
            double[] ll = frame.toLatLon(x, y); // one geodesic computation per column
            for (int k = 0; k < depths.length; k++) {
                out[k] = model.vs(ll[1], ll[0], depths[k]);
            }
        }, frame);
    }

    /**
     * Grid around {@code spec.center} sampled from a velocity function of the local Cartesian frame
     * ({@code x} east, {@code y} north, relative to the center; km) and physical depth. Unlike
     * {@link #cartesian} the grid keeps its geographic frame, so geographic positions can be used.
     */
    public static EikonalGrid3D build(VelocityFunction f, Spec spec) {
        final LocalFrame frame = new LocalFrame(spec.centerLat, spec.centerLon);
        int nhx = (int) Math.ceil(spec.halfWidthXKm / spec.spacingHorizKm);
        int nhy = (int) Math.ceil(spec.halfWidthYKm / spec.spacingHorizKm);
        double zfMax = spec.earthFlattening ? toFlatDepth(spec.maxDepthKm) : spec.maxDepthKm;
        int nz = (int) Math.ceil(zfMax / spec.spacingDepthKm) + 1;
        return build(-nhx * spec.spacingHorizKm, -nhy * spec.spacingHorizKm, 2 * nhx + 1, 2 * nhy + 1, nz,
            spec.spacingHorizKm, spec.spacingHorizKm, spec.spacingDepthKm,
            spec.earthFlattening, (x, y, depths, out) -> {
                for (int k = 0; k < depths.length; k++) {
                    out[k] = f.vs(x, y, depths[k]);
                }
            }, frame);
    }

    private static EikonalGrid3D build(double xMin, double yMin, int nx, int ny, int nz,
                                       double hx, double hy, double hz,
                                       boolean flatten, ColumnSampler sampler, LocalFrame frame) {
        if (nx < 3 || ny < 3 || nz < 3 || !(hx > 0.0) || !(hy > 0.0) || !(hz > 0.0)) {
            throw new IllegalArgumentException("Grid needs at least 3 nodes per axis and positive spacing");
        }
        long total = (long) nx * ny * nz;
        if (total > MAX_NODES) {
            throw new IllegalArgumentException("Grid too large: " + total + " nodes (max " + MAX_NODES
                + "). Increase the spacing or reduce the region.");
        }
        float[] slow = new float[(int) total];
        double[] depthPhys = new double[nz];
        double[] vScale = new double[nz];
        for (int k = 0; k < nz; k++) {
            double zm = k * hz;
            depthPhys[k] = flatten ? toPhysicalDepth(zm) : zm;
            vScale[k] = flatten ? Math.exp(zm / EARTH_RADIUS_KM) : 1.0;
        }
        double[] col = new double[nz];
        for (int j = 0; j < ny; j++) {
            double y = yMin + j * hy;
            for (int i = 0; i < nx; i++) {
                double x = xMin + i * hx;
                sampler.sample(x, y, depthPhys, col);
                for (int k = 0; k < nz; k++) {
                    double v = col[k] * vScale[k];
                    if (!(v > 0.0) || !Double.isFinite(v)) {
                        throw new IllegalArgumentException(
                            "Invalid velocity " + v + " at x=" + x + " y=" + y + " depth=" + depthPhys[k]);
                    }
                    slow[(k * ny + j) * nx + i] = (float) (1.0 / v);
                }
            }
        }
        return new EikonalGrid3D(nx, ny, nz, hx, hy, hz, xMin, yMin, flatten, slow, frame);
    }

    // ------------------------------------------------------------------ geometry

    public int nx() { return nx; }
    public int ny() { return ny; }
    public int nz() { return nz; }
    public double hx() { return hx; }
    public double hy() { return hy; }
    public double hz() { return hz; }
    public double xMin() { return xMin; }
    public double yMin() { return yMin; }
    public double xMax() { return xMin + (nx - 1) * hx; }
    public double yMax() { return yMin + (ny - 1) * hy; }
    /** Maximum physical depth covered (km). */
    public double maxDepthKm() { return flattened ? toPhysicalDepth((nz - 1) * hz) : (nz - 1) * hz; }
    public boolean isFlattened() { return flattened; }
    public long nodeCount() { return (long) nx * ny * nz; }
    /** Geographic frame, or {@code null} for a Cartesian grid. */
    public LocalFrame frame() { return frame; }

    public double toModelDepth(double depthKm) {
        return flattened ? toFlatDepth(depthKm) : depthKm;
    }

    /** {@code dz_model / dz_physical}. */
    double dModelDepthdDepth(double depthKm) {
        return flattened ? 1.0 / (1.0 - Math.min(depthKm, EARTH_RADIUS_KM * 0.999999) / EARTH_RADIUS_KM) : 1.0;
    }

    static double toFlatDepth(double depthKm) {
        return -EARTH_RADIUS_KM * Math.log1p(-Math.min(depthKm, EARTH_RADIUS_KM * 0.999999) / EARTH_RADIUS_KM);
    }

    static double toPhysicalDepth(double flatDepthKm) {
        return EARTH_RADIUS_KM * (1.0 - Math.exp(-flatDepthKm / EARTH_RADIUS_KM));
    }

    // ------------------------------------------------------------------ solving

    /**
     * Solves the travel-time field of a source at local position {@code (xKm, yKm)} and physical depth.
     *
     * @throws IllegalArgumentException if the source is outside the grid
     */
    public TravelTimeField3D solve(double xKm, double yKm, double depthKm) {
        double u = xKm - xMin;
        double v = yKm - yMin;
        double w = toModelDepth(depthKm);
        double eps = 1e-9;
        if (u < -eps || v < -eps || w < -eps
            || u > (nx - 1) * hx + eps || v > (ny - 1) * hy + eps || w > (nz - 1) * hz + eps
            || Double.isNaN(u + v + w)) {
            throw new IllegalArgumentException(String.format(
                "Source (x=%.3f, y=%.3f, depth=%.3f km) is outside the travel-time grid", xKm, yKm, depthKm));
        }
        u = Math.max(0.0, u);
        v = Math.max(0.0, v);
        w = Math.max(0.0, w);
        double sSrc = interpolateSlowness(u, v, w);
        double[] tau = FactoredFastSweeping.solveTau(nx, ny, nz, hx, hy, hz, slow, u, v, w, sSrc, MAX_SWEEPS, SWEEP_TOL);
        float[] tauF = new float[tau.length];
        for (int i = 0; i < tau.length; i++) {
            double t = tau[i];
            // unreachable nodes (should not occur) fall back to a homogeneous factor
            tauF[i] = Double.isInfinite(t) ? 1.0f : (float) t;
        }
        return new TravelTimeField3D(this, tauF, u, v, w, sSrc);
    }

    /** Geographic variant of {@link #solve(double, double, double)}; needs a grid built from a model. */
    public TravelTimeField3D solveLatLon(double lat, double lon, double depthKm) {
        if (frame == null) {
            throw new IllegalStateException("Grid has no geographic frame");
        }
        double[] xy = frame.toXY(lat, lon);
        return solve(xy[0], xy[1], depthKm);
    }

    private double interpolateSlowness(double u, double v, double w) {
        int i = Math.max(0, Math.min(nx - 2, (int) Math.floor(u / hx)));
        int j = Math.max(0, Math.min(ny - 2, (int) Math.floor(v / hy)));
        int k = Math.max(0, Math.min(nz - 2, (int) Math.floor(w / hz)));
        double fx = Math.max(0.0, Math.min(1.0, u / hx - i));
        double fy = Math.max(0.0, Math.min(1.0, v / hy - j));
        double fz = Math.max(0.0, Math.min(1.0, w / hz - k));
        int b = (k * ny + j) * nx + i;
        int sy = nx;
        int sz = nx * ny;
        double c00 = slow[b] + (slow[b + 1] - slow[b]) * fx;
        double c10 = slow[b + sy] + (slow[b + sy + 1] - slow[b + sy]) * fx;
        double c01 = slow[b + sz] + (slow[b + sz + 1] - slow[b + sz]) * fx;
        double c11 = slow[b + sz + sy] + (slow[b + sz + sy + 1] - slow[b + sz + sy]) * fx;
        double c0 = c00 + (c10 - c00) * fy;
        double c1 = c01 + (c11 - c01) * fy;
        return c0 + (c1 - c0) * fz;
    }
}
