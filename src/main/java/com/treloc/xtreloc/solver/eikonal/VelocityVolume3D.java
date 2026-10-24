package com.treloc.xtreloc.solver.eikonal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Regular lon / lat / depth volume with one or more named scalar channels (for example absolute Vs and the
 * velocity perturbation in percent). It is the data structure shown by the 3-D model viewer;
 * {@link #toVelocityModel3D()} yields the Vs-only model used for travel-time computation.
 *
 * <p>CSV format (optionally gzip-compressed, {@code .csv.gz}): a header line
 * {@code lon,lat,depth_km,<channel>,<channel>,...} followed by one node per line. The first channel is the
 * primary (Vs, km/s) channel. Lines starting with {@code #} are comments. Nodes must form a complete product grid
 * in any order.</p>
 *
 * <p>Raw files of Nakajima et al. (2001), {@code dvs_vel_JGR2001.out}, have the columns
 * {@code lat lon depth Vs dVs_from_initial(%) dVs_from_mean(%) layer} and are read by
 * {@link #fromNakajimaRaw(Path)}.</p>
 */
public final class VelocityVolume3D {

    /** Channel names used by the bundled model. */
    public static final String CH_VS = "vs_km_s";
    public static final String CH_DVS_INIT = "dvs_init_pct";
    public static final String CH_DVS_MEAN = "dvs_mean_pct";
    public static final String CH_LAYER = "layer";

    private static final Pattern SPLIT = Pattern.compile("[,;\\s]+");
    private static final double AXIS_TOL = 1e-7;

    private final String name;
    private final double[] lons;
    private final double[] lats;
    private final double[] deps;
    private final Map<String, float[]> channels;

    private VelocityVolume3D(String name, double[] lons, double[] lats, double[] deps, Map<String, float[]> channels) {
        this.name = name;
        this.lons = lons;
        this.lats = lats;
        this.deps = deps;
        this.channels = channels;
    }

    // ------------------------------------------------------------------ construction

    /** Builds a volume from explicit axes and channels; every channel array is {@code (k*nLat+j)*nLon+i}. */
    public static VelocityVolume3D of(String name, double[] lons, double[] lats, double[] deps,
                                      Map<String, float[]> channels) {
        checkAxis("lon", lons);
        checkAxis("lat", lats);
        checkAxis("depth", deps);
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("At least one channel is required");
        }
        int n = lons.length * lats.length * deps.length;
        Map<String, float[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> e : channels.entrySet()) {
            if (e.getValue().length != n) {
                throw new IllegalArgumentException("Channel " + e.getKey() + " has wrong length");
            }
            copy.put(e.getKey(), e.getValue().clone());
        }
        return new VelocityVolume3D(name, lons.clone(), lats.clone(), deps.clone(), copy);
    }

    /**
     * "Layer cake": a horizontally uniform 1-D profile extruded over a lon/lat region. Samples with equal depth
     * (a discontinuity) are separated by {@code DISCONTINUITY_EPS_KM} so that the volume stays a strictly increasing
     * grid with a sharp interface.
     *
     * @param depthsKm samples of the profile (non-decreasing)
     * @param vs       velocity at each sample (km/s, positive)
     * @param maxDepthKm lower limit of the volume (the profile is cut / extrapolated flat there)
     */
    public static VelocityVolume3D fromProfile1D(String name, double[] depthsKm, double[] vs,
                                                 double lonMin, double lonMax, double latMin, double latMax,
                                                 double maxDepthKm) {
        if (depthsKm == null || vs == null || depthsKm.length < 2 || depthsKm.length != vs.length) {
            throw new IllegalArgumentException("Profile needs at least 2 samples");
        }
        if (!(lonMax > lonMin) || !(latMax > latMin) || !(maxDepthKm > depthsKm[0])) {
            throw new IllegalArgumentException("Invalid region or depth range");
        }
        List<Double> z = new ArrayList<>();
        List<Double> v = new ArrayList<>();
        for (int i = 0; i < depthsKm.length; i++) {
            double zi = depthsKm[i];
            double vi = vs[i];
            if (!(vi > 0.0) || !Double.isFinite(vi)) {
                throw new IllegalArgumentException("Profile velocity must be positive");
            }
            if (!z.isEmpty() && zi <= z.get(z.size() - 1)) {
                zi = z.get(z.size() - 1) + DISCONTINUITY_EPS_KM;
            }
            if (zi >= maxDepthKm) {
                // interpolate the value at maxDepth between the previous sample and this one
                double z0 = z.isEmpty() ? zi : z.get(z.size() - 1);
                double v0 = v.isEmpty() ? vi : v.get(v.size() - 1);
                double vm = zi > z0 ? v0 + (vi - v0) * (maxDepthKm - z0) / (zi - z0) : vi;
                if (z.isEmpty() || maxDepthKm > z.get(z.size() - 1) + 1e-9) {
                    z.add(maxDepthKm);
                    v.add(vm);
                }
                break;
            }
            z.add(zi);
            v.add(vi);
        }
        if (z.get(z.size() - 1) < maxDepthKm - 1e-9) {
            z.add(maxDepthKm);
            v.add(v.get(v.size() - 1));
        }
        if (z.size() < 2) {
            throw new IllegalArgumentException("Profile does not cover the requested depth range");
        }
        double[] deps = new double[z.size()];
        for (int k = 0; k < deps.length; k++) {
            deps[k] = z.get(k);
        }
        double[] lons = {lonMin, lonMax};
        double[] lats = {latMin, latMax};
        float[] vol = new float[2 * 2 * deps.length];
        for (int k = 0; k < deps.length; k++) {
            for (int jj = 0; jj < 4; jj++) {
                vol[k * 4 + jj] = v.get(k).floatValue();
            }
        }
        Map<String, float[]> ch = new LinkedHashMap<>();
        ch.put(CH_VS, vol);
        return of(name, lons, lats, deps, ch);
    }

    /** Separation (km) given to the two samples of a discontinuity. */
    public static final double DISCONTINUITY_EPS_KM = 0.001;

    /** Reads a volume CSV (plain or {@code .gz}). */
    public static VelocityVolume3D fromCsv(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return fromCsv(in, stripExtensions(file.getFileName().toString()));
        }
    }

    /** Reads a volume CSV from a stream (plain or gzip, detected by the magic number). */
    public static VelocityVolume3D fromCsv(InputStream in, String name) throws IOException {
        java.io.BufferedInputStream bin = new java.io.BufferedInputStream(in, 1 << 16);
        bin.mark(2);
        int b0 = bin.read();
        int b1 = bin.read();
        bin.reset();
        InputStream src = (b0 == 0x1f && b1 == 0x8b) ? new GZIPInputStream(bin, 1 << 16) : bin;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(src, StandardCharsets.UTF_8))) {
            return parse(br, name, new int[] {0, 1, 2}, null);
        }
    }

    /** Reads the raw Nakajima et al. (2001) table {@code lat lon depth Vs dVsInit dVsMean layer}. */
    public static VelocityVolume3D fromNakajimaRaw(Path file) throws IOException {
        String[] names = {"lat", "lon", "depth_km", CH_VS, CH_DVS_INIT, CH_DVS_MEAN, CH_LAYER};
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(br, "nakajima2001_tohoku", new int[] {1, 0, 2}, names);
        }
    }

    private static String stripExtensions(String fileName) {
        String n = fileName;
        if (n.toLowerCase(Locale.ROOT).endsWith(".gz")) {
            n = n.substring(0, n.length() - 3);
        }
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /**
     * @param axisCols    columns of {@code lon, lat, depth}
     * @param fixedNames  column names (all columns) or {@code null} to read them from the header
     */
    private static VelocityVolume3D parse(BufferedReader br, String name, int[] axisCols, String[] fixedNames)
        throws IOException {
        List<double[]> rows = new ArrayList<>(1 << 16);
        String[] headerNames = null;
        int ncol = -1;
        String line;
        int lineNo = 0;
        while ((line = br.readLine()) != null) {
            lineNo++;
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("//")) {
                continue;
            }
            String[] p = SPLIT.split(t);
            double[] r = new double[p.length];
            boolean numeric = true;
            for (int c = 0; c < p.length; c++) {
                try {
                    r[c] = Double.parseDouble(p[c]);
                } catch (NumberFormatException e) {
                    numeric = false;
                    break;
                }
            }
            if (!numeric) {
                if (rows.isEmpty()) {
                    headerNames = p;
                    continue;
                }
                throw new IOException("Line " + lineNo + ": non-numeric value in: " + t);
            }
            if (ncol < 0) {
                ncol = p.length;
                if (ncol < 4) {
                    throw new IOException("Line " + lineNo + ": expected at least 4 columns, got: " + t);
                }
            } else if (p.length != ncol) {
                throw new IOException("Line " + lineNo + ": expected " + ncol + " columns, got " + p.length);
            }
            rows.add(r);
        }
        if (rows.isEmpty()) {
            throw new IOException("Velocity volume has no data rows");
        }
        String[] names = fixedNames;
        if (names == null) {
            names = new String[ncol];
            for (int c = 0; c < ncol; c++) {
                String h = (headerNames != null && headerNames.length == ncol) ? headerNames[c].trim() : "";
                names[c] = h.isEmpty() ? (c == 3 ? CH_VS : "col" + (c + 1)) : h;
            }
        }
        if (names.length != ncol) {
            throw new IOException("Expected " + names.length + " columns, found " + ncol);
        }

        double[] lons = uniqueSorted(rows, axisCols[0]);
        double[] lats = uniqueSorted(rows, axisCols[1]);
        double[] deps = uniqueSorted(rows, axisCols[2]);
        if (lons.length < 2 || lats.length < 2 || deps.length < 2) {
            throw new IOException("Volume needs at least 2 distinct values along lon, lat and depth");
        }
        long expected = (long) lons.length * lats.length * deps.length;
        if (rows.size() != expected) {
            throw new IOException("Volume is not a complete regular grid: " + rows.size() + " rows, expected "
                + lons.length + " x " + lats.length + " x " + deps.length + " = " + expected);
        }
        List<Integer> valueCols = new ArrayList<>();
        for (int c = 0; c < ncol; c++) {
            if (c != axisCols[0] && c != axisCols[1] && c != axisCols[2]) {
                valueCols.add(c);
            }
        }
        float[][] data = new float[valueCols.size()][(int) expected];
        boolean[] seen = new boolean[(int) expected];
        for (double[] r : rows) {
            int i = indexOf(lons, r[axisCols[0]]);
            int j = indexOf(lats, r[axisCols[1]]);
            int k = indexOf(deps, r[axisCols[2]]);
            int idx = (k * lats.length + j) * lons.length + i;
            if (seen[idx]) {
                throw new IOException("Duplicate node at lon=" + r[axisCols[0]] + " lat=" + r[axisCols[1]]
                    + " depth=" + r[axisCols[2]]);
            }
            seen[idx] = true;
            for (int v = 0; v < data.length; v++) {
                data[v][idx] = (float) r[valueCols.get(v)];
            }
        }
        Map<String, float[]> ch = new LinkedHashMap<>();
        for (int v = 0; v < data.length; v++) {
            ch.put(names[valueCols.get(v)], data[v]);
        }
        float[] primary = data[0];
        for (float x : primary) {
            if (!(x > 0f) || !Float.isFinite(x)) {
                throw new IOException("Primary channel (Vs) must be finite and positive");
            }
        }
        return new VelocityVolume3D(name, lons, lats, deps, ch);
    }

    private static double[] uniqueSorted(List<double[]> rows, int col) {
        double[] a = new double[rows.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = rows.get(i)[col];
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
        if (ins >= axis.length) {
            return axis.length - 1;
        }
        if (ins > 0 && Math.abs(axis[ins - 1] - x) < Math.abs(axis[ins] - x)) {
            return ins - 1;
        }
        return ins;
    }

    private static void checkAxis(String nm, double[] a) {
        if (a == null || a.length < 2) {
            throw new IllegalArgumentException(nm + " axis needs at least 2 nodes");
        }
        for (int i = 1; i < a.length; i++) {
            if (!(a[i] > a[i - 1])) {
                throw new IllegalArgumentException(nm + " axis must be strictly increasing");
            }
        }
    }

    // ------------------------------------------------------------------ output

    /**
     * Writes the volume as CSV ({@code .gz} when the file name ends with it), ordered by depth, latitude and
     * longitude. Channel values are written with limited precision to keep the file small.
     */
    public void writeCsv(Path file) throws IOException {
        boolean gz = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz");
        try (OutputStream raw = Files.newOutputStream(file);
             OutputStream os = gz ? new GZIPOutputStream(raw, 1 << 16) : raw;
             BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), 1 << 16)) {
            w.write("# " + name + "\n");
            StringBuilder h = new StringBuilder("lon,lat,depth_km");
            for (String c : channels.keySet()) {
                h.append(',').append(c);
            }
            w.write(h.toString());
            w.write('\n');
            List<float[]> data = new ArrayList<>(channels.values());
            List<String> chNames = new ArrayList<>(channels.keySet());
            StringBuilder sb = new StringBuilder(96);
            for (int k = 0; k < deps.length; k++) {
                for (int j = 0; j < lats.length; j++) {
                    for (int i = 0; i < lons.length; i++) {
                        int idx = (k * lats.length + j) * lons.length + i;
                        sb.setLength(0);
                        sb.append(fmt(lons[i], 4)).append(',').append(fmt(lats[j], 4)).append(',')
                          .append(fmt(deps[k], 3));
                        for (int c = 0; c < data.size(); c++) {
                            float v = data.get(c)[idx];
                            sb.append(',');
                            if (CH_LAYER.equals(chNames.get(c))) {
                                sb.append((int) v);
                            } else {
                                sb.append(fmt(v, 3));
                            }
                        }
                        sb.append('\n');
                        w.write(sb.toString());
                    }
                }
            }
        }
    }

    private static String fmt(double v, int decimals) {
        String s = String.format(Locale.ROOT, "%." + decimals + "f", v);
        if (s.indexOf('.') >= 0) {
            int e = s.length();
            while (e > 0 && s.charAt(e - 1) == '0') {
                e--;
            }
            if (e > 0 && s.charAt(e - 1) == '.') {
                e--;
            }
            s = s.substring(0, e);
        }
        return s.equals("-0") ? "0" : s;
    }

    // ------------------------------------------------------------------ access

    public String name() { return name; }
    public int nLon() { return lons.length; }
    public int nLat() { return lats.length; }
    public int nDepth() { return deps.length; }
    public double[] lons() { return lons.clone(); }
    public double[] lats() { return lats.clone(); }
    public double[] depths() { return deps.clone(); }
    public double lon(int i) { return lons[i]; }
    public double lat(int j) { return lats[j]; }
    public double depth(int k) { return deps[k]; }
    public double minLon() { return lons[0]; }
    public double maxLon() { return lons[lons.length - 1]; }
    public double minLat() { return lats[0]; }
    public double maxLat() { return lats[lats.length - 1]; }
    public double minDepth() { return deps[0]; }
    public double maxDepth() { return deps[deps.length - 1]; }
    public long nodeCount() { return (long) lons.length * lats.length * deps.length; }

    public List<String> channelNames() {
        return new ArrayList<>(channels.keySet());
    }

    public boolean hasChannel(String ch) {
        return channels.containsKey(ch);
    }

    /** Raw channel array ({@code (k*nLat+j)*nLon+i}); not copied, treat as read-only. */
    public float[] channel(String ch) {
        float[] a = channels.get(ch);
        if (a == null) {
            throw new IllegalArgumentException("Unknown channel: " + ch);
        }
        return a;
    }

    public int index(int i, int j, int k) {
        return (k * lats.length + j) * lons.length + i;
    }

    /** {min, max} of a channel. */
    public double[] range(String ch) {
        float[] a = channel(ch);
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (float v : a) {
            if (v < lo) {
                lo = v;
            }
            if (v > hi) {
                hi = v;
            }
        }
        return new double[] {lo, hi};
    }

    /** Value quantile {@code q in [0,1]} of a channel (sampled for large volumes). */
    public double quantile(String ch, double q) {
        float[] a = channel(ch);
        int step = Math.max(1, a.length / 200_000);
        float[] s = new float[(a.length + step - 1) / step];
        for (int i = 0, n = 0; i < a.length; i += step) {
            s[n++] = a[i];
        }
        Arrays.sort(s);
        int idx = (int) Math.round(Math.max(0.0, Math.min(1.0, q)) * (s.length - 1));
        return s[idx];
    }

    /**
     * Trilinear value of a channel (nearest-edge value outside); the {@link #CH_LAYER} channel is sampled with
     * nearest-node lookup because it is categorical.
     */
    public double sample(String ch, double lon, double lat, double depthKm) {
        float[] a = channel(ch);
        int i = cell(lons, lon);
        int j = cell(lats, lat);
        int k = cell(deps, depthKm);
        double fx = frac(lons, i, lon);
        double fy = frac(lats, j, lat);
        double fz = frac(deps, k, depthKm);
        if (CH_LAYER.equals(ch)) {
            int ii = fx >= 0.5 ? i + 1 : i;
            int jj = fy >= 0.5 ? j + 1 : j;
            int kk = fz >= 0.5 ? k + 1 : k;
            return a[index(ii, jj, kk)];
        }
        return trilinear(a, i, j, k, fx, fy, fz);
    }

    /** Trilinear interpolation inside cell {@code (i,j,k)} with fractions {@code fx,fy,fz}. */
    public double trilinear(float[] a, int i, int j, int k, double fx, double fy, double fz) {
        int nx = lons.length;
        int ny = lats.length;
        int b = (k * ny + j) * nx + i;
        int sy = nx;
        int sz = nx * ny;
        double c00 = a[b] + (a[b + 1] - a[b]) * fx;
        double c10 = a[b + sy] + (a[b + sy + 1] - a[b + sy]) * fx;
        double c01 = a[b + sz] + (a[b + sz + 1] - a[b + sz]) * fx;
        double c11 = a[b + sz + sy] + (a[b + sz + sy + 1] - a[b + sz + sy]) * fx;
        double c0 = c00 + (c10 - c00) * fy;
        double c1 = c01 + (c11 - c01) * fy;
        return c0 + (c1 - c0) * fz;
    }

    /** Lower cell index in {@code [0, n-2]}. */
    public static int cell(double[] axis, double x) {
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

    /** Interpolation fraction in {@code [0, 1]} within cell {@code i}. */
    public static double frac(double[] axis, int i, double x) {
        double f = (x - axis[i]) / (axis[i + 1] - axis[i]);
        return f < 0.0 ? 0.0 : (f > 1.0 ? 1.0 : f);
    }

    /** Statistic used when collapsing a volume onto a 2-D plane. {@link #SLICE} is a single plane (see {@link #slice}). */
    public enum Reduce {
        SLICE, MEAN, SUM, VARIANCE, MIN, MAX
    }

    /**
     * Samples one axis of a channel onto a regular 2-D plane at {@code pos}.
     *
     * @param collapse 0 = longitude (lat–depth section), 1 = latitude (lon–depth), 2 = depth (lon–lat map)
     */
    public Plane2D slice(String ch, int collapse, double pos) {
        int axis = collapse < 0 || collapse > 2 ? 2 : collapse;
        int nu;
        int nv;
        double[] u;
        double[] v;
        String uName;
        String vName;
        if (axis == 2) {
            nu = lons.length;
            nv = lats.length;
            u = lons;
            v = lats;
            uName = "lon";
            vName = "lat";
        } else if (axis == 0) {
            nu = lats.length;
            nv = deps.length;
            u = lats;
            v = deps;
            uName = "lat";
            vName = "depth";
        } else {
            nu = lons.length;
            nv = deps.length;
            u = lons;
            v = deps;
            uName = "lon";
            vName = "depth";
        }
        float[] out = new float[nu * nv];
        for (int jv = 0; jv < nv; jv++) {
            for (int iu = 0; iu < nu; iu++) {
                double val;
                if (axis == 2) {
                    val = sample(ch, u[iu], v[jv], pos);
                } else if (axis == 0) {
                    val = sample(ch, pos, u[iu], v[jv]);
                } else {
                    val = sample(ch, u[iu], pos, v[jv]);
                }
                out[jv * nu + iu] = (float) val;
            }
        }
        return new Plane2D(uName, vName, u.clone(), v.clone(), out);
    }

    /**
     * Collapses one axis of a channel onto a regular 2-D plane.
     *
     * @param collapse 0 = longitude (lat–depth section), 1 = latitude (lon–depth), 2 = depth (lon–lat map)
     */
    public Plane2D reduce(String ch, Reduce how, int collapse) {
        float[] a = channel(ch);
        int nx = lons.length;
        int ny = lats.length;
        int nz = deps.length;
        int axis = collapse < 0 || collapse > 2 ? 2 : collapse;
        int nu;
        int nv;
        int nw;
        double[] u;
        double[] v;
        String uName;
        String vName;
        if (axis == 2) {
            nu = nx;
            nv = ny;
            nw = nz;
            u = lons;
            v = lats;
            uName = "lon";
            vName = "lat";
        } else if (axis == 0) {
            nu = ny;
            nv = nz;
            nw = nx;
            u = lats;
            v = deps;
            uName = "lat";
            vName = "depth";
        } else {
            nu = nx;
            nv = nz;
            nw = ny;
            u = lons;
            v = deps;
            uName = "lon";
            vName = "depth";
        }
        float[] out = new float[nu * nv];
        for (int jv = 0; jv < nv; jv++) {
            for (int iu = 0; iu < nu; iu++) {
                double sum = 0;
                double sum2 = 0;
                double mn = Double.POSITIVE_INFINITY;
                double mx = Double.NEGATIVE_INFINITY;
                int n = 0;
                for (int iw = 0; iw < nw; iw++) {
                    int i;
                    int j;
                    int k;
                    if (axis == 2) {
                        i = iu;
                        j = jv;
                        k = iw;
                    } else if (axis == 0) {
                        i = iw;
                        j = iu;
                        k = jv;
                    } else {
                        i = iu;
                        j = iw;
                        k = jv;
                    }
                    float val = a[index(i, j, k)];
                    if (!Float.isFinite(val)) {
                        continue;
                    }
                    sum += val;
                    sum2 += (double) val * val;
                    if (val < mn) {
                        mn = val;
                    }
                    if (val > mx) {
                        mx = val;
                    }
                    n++;
                }
                float r;
                if (n == 0) {
                    r = Float.NaN;
                } else if (how == Reduce.SUM) {
                    r = (float) sum;
                } else if (how == Reduce.MIN) {
                    r = (float) mn;
                } else if (how == Reduce.MAX) {
                    r = (float) mx;
                } else if (how == Reduce.VARIANCE) {
                    double mean = sum / n;
                    r = n < 2 ? 0f : (float) Math.max(0.0, sum2 / n - mean * mean);
                } else {
                    r = (float) (sum / n);
                }
                out[jv * nu + iu] = r;
            }
        }
        return new Plane2D(uName, vName, u.clone(), v.clone(), out);
    }

    /** Regular 2-D field {@code z[jv * nU + iu]} after {@link #reduce}. */
    public static final class Plane2D {
        public final String uName;
        public final String vName;
        public final double[] u;
        public final double[] v;
        public final float[] z;

        public Plane2D(String uName, String vName, double[] u, double[] v, float[] z) {
            this.uName = uName;
            this.vName = vName;
            this.u = u;
            this.v = v;
            this.z = z;
        }

        public int nU() {
            return u.length;
        }

        public int nV() {
            return v.length;
        }

        public double sample(double uu, double vv) {
            int i = cell(u, uu);
            int j = cell(v, vv);
            double fx = frac(u, i, uu);
            double fy = frac(v, j, vv);
            int nu = u.length;
            float z00 = z[j * nu + i];
            float z10 = z[j * nu + i + 1];
            float z01 = z[(j + 1) * nu + i];
            float z11 = z[(j + 1) * nu + i + 1];
            if (!Float.isFinite(z00) || !Float.isFinite(z10) || !Float.isFinite(z01) || !Float.isFinite(z11)) {
                return z00;
            }
            double a = z00 + (z10 - z00) * fx;
            double b = z01 + (z11 - z01) * fx;
            return a + (b - a) * fy;
        }

        /** Nearest node (grid-cell fill). */
        public double sampleNearest(double uu, double vv) {
            return z[nearestIndex(v, vv) * u.length + nearestIndex(u, uu)];
        }

        static int nearestIndex(double[] axis, double x) {
            int n = axis.length;
            if (n <= 1) {
                return 0;
            }
            if (x <= axis[0]) {
                return 0;
            }
            if (x >= axis[n - 1]) {
                return n - 1;
            }
            int i = cell(axis, x);
            return (x - axis[i] <= axis[i + 1] - x) ? i : i + 1;
        }
    }

    /** Vs-only model (first channel) for travel-time computation. */
    public VelocityModel3D toVelocityModel3D() {
        return VelocityModel3D.fromGrid(lons, lats, deps, channels.values().iterator().next());
    }
}
