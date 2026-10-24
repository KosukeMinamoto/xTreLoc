package com.treloc.xtreloc.solver.eikonal;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 3-D S-wave velocity model on a rectilinear (not necessarily uniform) longitude / latitude / depth grid.
 *
 * <p>Input text/CSV format: one node per line with at least four columns
 * {@code lon lat depth_km vs_km_s} separated by commas, semicolons, tabs or spaces. Empty lines, lines starting
 * with {@code #} or {@code //} and a non-numeric header line are ignored; extra columns are ignored. The nodes must
 * form a complete lon x lat x depth product grid (any order).</p>
 *
 * <p>{@link #vs(double, double, double)} interpolates trilinearly and uses the nearest edge value outside the grid.</p>
 */
public final class VelocityModel3D {

    private static final Pattern SPLIT = Pattern.compile("[,;\\s]+");
    private static final double AXIS_TOL = 1e-7;

    private final double[] lons;
    private final double[] lats;
    private final double[] deps;
    /** Index {@code (k * nLat + j) * nLon + i}. */
    private final float[] vs;

    private VelocityModel3D(double[] lons, double[] lats, double[] deps, float[] vs) {
        this.lons = lons;
        this.lats = lats;
        this.deps = deps;
        this.vs = vs;
    }

    /**
     * Builds a model from explicit axes and values ({@code vs[(k * lats.length + j) * lons.length + i]}).
     *
     * @throws IllegalArgumentException if axes are not strictly increasing, too short, or Vs is not positive
     */
    public static VelocityModel3D fromGrid(double[] lons, double[] lats, double[] deps, float[] vs) {
        checkAxis("lon", lons);
        checkAxis("lat", lats);
        checkAxis("depth", deps);
        if (vs == null || vs.length != lons.length * lats.length * deps.length) {
            throw new IllegalArgumentException("vs length must be nLon*nLat*nDepth");
        }
        for (float v : vs) {
            if (!(v > 0f) || !Float.isFinite(v)) {
                throw new IllegalArgumentException("Vs must be finite and positive");
            }
        }
        return new VelocityModel3D(lons.clone(), lats.clone(), deps.clone(), vs.clone());
    }

    private static void checkAxis(String name, double[] a) {
        if (a == null || a.length < 2) {
            throw new IllegalArgumentException(name + " axis needs at least 2 nodes");
        }
        for (int i = 1; i < a.length; i++) {
            if (!(a[i] > a[i - 1])) {
                throw new IllegalArgumentException(name + " axis must be strictly increasing");
            }
        }
    }

    /** Loads the model from a text/CSV file, optionally gzip-compressed (see class description). */
    public static VelocityModel3D fromCsv(Path file) throws IOException {
        double[][] rows;
        try (java.io.InputStream raw = Files.newInputStream(file);
             java.io.BufferedInputStream bin = new java.io.BufferedInputStream(raw, 1 << 16)) {
            bin.mark(2);
            int b0 = bin.read();
            int b1 = bin.read();
            bin.reset();
            java.io.InputStream src = (b0 == 0x1f && b1 == 0x8b) ? new java.util.zip.GZIPInputStream(bin) : bin;
            try (BufferedReader br = new BufferedReader(new java.io.InputStreamReader(src, StandardCharsets.UTF_8))) {
                rows = readRows(br);
            }
        }
        return fromRows(rows);
    }

    static double[][] readRows(BufferedReader br) throws IOException {
        java.util.List<double[]> rows = new java.util.ArrayList<>();
        String line;
        int lineNo = 0;
        while ((line = br.readLine()) != null) {
            lineNo++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("//")) {
                continue;
            }
            String[] p = SPLIT.split(t);
            if (p.length < 4) {
                if (rows.isEmpty()) {
                    continue; // header with fewer tokens
                }
                throw new IOException("Line " + lineNo + ": expected lon lat depth vs, got: " + t);
            }
            double[] r = new double[4];
            try {
                for (int c = 0; c < 4; c++) {
                    r[c] = Double.parseDouble(p[c]);
                }
            } catch (NumberFormatException e) {
                if (rows.isEmpty()) {
                    continue; // header line
                }
                throw new IOException("Line " + lineNo + ": non-numeric value in: " + t);
            }
            rows.add(r);
        }
        return rows.toArray(new double[0][]);
    }

    static VelocityModel3D fromRows(double[][] rows) throws IOException {
        if (rows.length == 0) {
            throw new IOException("Velocity model has no data rows");
        }
        double[] lons = uniqueSorted(rows, 0);
        double[] lats = uniqueSorted(rows, 1);
        double[] deps = uniqueSorted(rows, 2);
        if (lons.length < 2 || lats.length < 2 || deps.length < 2) {
            throw new IOException("Velocity model needs at least 2 distinct values along lon, lat and depth");
        }
        long expected = (long) lons.length * lats.length * deps.length;
        if (rows.length != expected) {
            throw new IOException("Velocity model is not a complete regular grid: " + rows.length + " rows, expected "
                + lons.length + " x " + lats.length + " x " + deps.length + " = " + expected);
        }
        float[] v = new float[(int) expected];
        boolean[] seen = new boolean[(int) expected];
        for (double[] r : rows) {
            int i = indexOf(lons, r[0]);
            int j = indexOf(lats, r[1]);
            int k = indexOf(deps, r[2]);
            int idx = (k * lats.length + j) * lons.length + i;
            if (seen[idx]) {
                throw new IOException("Duplicate node at lon=" + r[0] + " lat=" + r[1] + " depth=" + r[2]);
            }
            if (!(r[3] > 0.0) || !Double.isFinite(r[3])) {
                throw new IOException("Non-positive Vs at lon=" + r[0] + " lat=" + r[1] + " depth=" + r[2]);
            }
            seen[idx] = true;
            v[idx] = (float) r[3];
        }
        return new VelocityModel3D(lons, lats, deps, v);
    }

    private static double[] uniqueSorted(double[][] rows, int col) {
        double[] a = new double[rows.length];
        for (int i = 0; i < rows.length; i++) {
            a[i] = rows[i][col];
        }
        Arrays.sort(a);
        double[] u = new double[a.length];
        int n = 0;
        for (double x : a) {
            if (n == 0 || x - u[n - 1] > AXIS_TOL) {
                u[n++] = x;
            }
        }
        return Arrays.copyOf(u, n);
    }

    private static int indexOf(double[] axis, double x) {
        int i = Arrays.binarySearch(axis, x);
        if (i >= 0) {
            return i;
        }
        int ins = -i - 1;
        int best = ins;
        if (ins >= axis.length || (ins > 0 && Math.abs(axis[ins - 1] - x) < Math.abs(axis[ins] - x))) {
            best = ins - 1;
        }
        return best;
    }

    public int nLon() { return lons.length; }
    public int nLat() { return lats.length; }
    public int nDepth() { return deps.length; }
    public double minLon() { return lons[0]; }
    public double maxLon() { return lons[lons.length - 1]; }
    public double minLat() { return lats[0]; }
    public double maxLat() { return lats[lats.length - 1]; }
    public double minDepth() { return deps[0]; }
    public double maxDepth() { return deps[deps.length - 1]; }

    /** S velocity (km/s) at the given point (trilinear; nearest edge value outside the grid). */
    public double vs(double lon, double lat, double depthKm) {
        int i = cell(lons, lon);
        int j = cell(lats, lat);
        int k = cell(deps, depthKm);
        double fx = frac(lons, i, lon);
        double fy = frac(lats, j, lat);
        double fz = frac(deps, k, depthKm);
        int nx = lons.length;
        int ny = lats.length;
        double c000 = vs[(k * ny + j) * nx + i];
        double c100 = vs[(k * ny + j) * nx + i + 1];
        double c010 = vs[(k * ny + j + 1) * nx + i];
        double c110 = vs[(k * ny + j + 1) * nx + i + 1];
        double c001 = vs[((k + 1) * ny + j) * nx + i];
        double c101 = vs[((k + 1) * ny + j) * nx + i + 1];
        double c011 = vs[((k + 1) * ny + j + 1) * nx + i];
        double c111 = vs[((k + 1) * ny + j + 1) * nx + i + 1];
        double c00 = c000 + (c100 - c000) * fx;
        double c10 = c010 + (c110 - c010) * fx;
        double c01 = c001 + (c101 - c001) * fx;
        double c11 = c011 + (c111 - c011) * fx;
        double c0 = c00 + (c10 - c00) * fy;
        double c1 = c01 + (c11 - c01) * fy;
        return c0 + (c1 - c0) * fz;
    }

    /** Lower cell index in {@code [0, n-2]} for {@code x}. */
    private static int cell(double[] axis, double x) {
        int n = axis.length;
        if (x <= axis[0]) {
            return 0;
        }
        if (x >= axis[n - 1]) {
            return n - 2;
        }
        int i = Arrays.binarySearch(axis, x);
        if (i < 0) {
            i = -i - 2;
        }
        return Math.min(i, n - 2);
    }

    /** Interpolation fraction in {@code [0, 1]} (clamped, i.e. nearest edge value outside). */
    private static double frac(double[] axis, int i, double x) {
        double f = (x - axis[i]) / (axis[i + 1] - axis[i]);
        return f < 0.0 ? 0.0 : (f > 1.0 ? 1.0 : f);
    }
}
