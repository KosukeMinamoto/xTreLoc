package com.treloc.xtreloc.solver.grid;

import com.treloc.xtreloc.io.VelocityModelCatalog;
import com.treloc.xtreloc.solver.WinHypomhStruct;
import com.treloc.xtreloc.solver.eikonal.EikonalGrid3D;
import com.treloc.xtreloc.solver.table.TravelTimeTable.Phase;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 1-D velocity profile (Vp and Vs versus depth) extruded to 3-D as <em>planar dipping layers</em>.
 *
 * <p>All interfaces share one strike and dip. With the dip direction {@code strike + 90 deg} (azimuth from north,
 * right-hand rule) an interface that is at depth {@code d} below the model centre is at
 * {@code d + tan(dip) * s} at horizontal distance {@code s} from the centre along the dip direction. The velocity
 * at {@code (x, y, depth)} is the profile value at the "layer depth" {@code depth - tan(dip) * s(x, y)}, so a
 * dip of zero gives the plain horizontally uniform layer cake. A negative dip reverses the dip direction (useful
 * for a slab that deepens opposite to {@code strike + 90}). Above / below the profile the first / last value is
 * extended.</p>
 *
 * <p>Instances are immutable.</p>
 */
public final class LayerCakeModel {

    /** Separation (km) given to the two samples of a discontinuity. */
    private static final double DISCONTINUITY_EPS_KM = 1e-6;
    private static final double FLUID_VS = 0.1;
    /** Largest accepted dip (degrees); steeper interfaces would shear the model beyond any use. */
    public static final double MAX_DIP_DEG = 85.0;

    private final String name;
    private final double[] depth;
    private final double[] vp;
    private final double[] vs;
    private final double strikeDeg;
    private final double dipDeg;
    /** {@code tan(dip) sin(dip direction)} and {@code tan(dip) cos(dip direction)}. */
    private final double shiftPerX;
    private final double shiftPerY;

    private LayerCakeModel(String name, double[] depth, double[] vp, double[] vs, double strikeDeg, double dipDeg) {
        if (!(Math.abs(dipDeg) <= MAX_DIP_DEG)) {
            throw new IllegalArgumentException("Dip must be in [-" + MAX_DIP_DEG + ", " + MAX_DIP_DEG + "] degrees: " + dipDeg);
        }
        this.name = name;
        this.depth = depth;
        this.vp = vp;
        this.vs = vs;
        this.strikeDeg = strikeDeg;
        this.dipDeg = dipDeg;
        double dipDir = Math.toRadians(strikeDeg + 90.0);
        double t = Math.tan(Math.toRadians(dipDeg));
        this.shiftPerX = t * Math.sin(dipDir);
        this.shiftPerY = t * Math.cos(dipDir);
    }

    // ------------------------------------------------------------------ construction

    /**
     * Horizontal layer cake from explicit samples (non-decreasing depths; a discontinuity is two samples with the
     * same depth; velocity varies linearly between samples).
     */
    public static LayerCakeModel of(String name, double[] depthKm, double[] vp, double[] vs) {
        if (depthKm == null || vp == null || vs == null || depthKm.length < 2
            || depthKm.length != vp.length || depthKm.length != vs.length) {
            throw new IllegalArgumentException("Profile needs at least 2 samples with equal array lengths");
        }
        List<Double> d = new ArrayList<>();
        List<Double> a = new ArrayList<>();
        List<Double> b = new ArrayList<>();
        for (int i = 0; i < depthKm.length; i++) {
            if (!(vp[i] > 0.0) || !(vs[i] > 0.0) || !Double.isFinite(vp[i] + vs[i] + depthKm[i])) {
                throw new IllegalArgumentException("Profile velocities must be positive and finite (sample " + i + ")");
            }
            double z = depthKm[i];
            if (!d.isEmpty()) {
                double prev = d.get(d.size() - 1);
                if (z < prev) {
                    throw new IllegalArgumentException("Profile depths must not decrease (sample " + i + ")");
                }
                if (z - prev < DISCONTINUITY_EPS_KM) {
                    z = prev + DISCONTINUITY_EPS_KM;
                }
            }
            d.add(z);
            a.add(vp[i]);
            b.add(vs[i]);
        }
        return new LayerCakeModel(name, toArray(d), toArray(a), toArray(b), 0.0, 0.0);
    }

    /** Same profile with dipping layers ({@code strikeDeg}: azimuth from north; {@code dipDeg}: 0 .. 85). */
    public LayerCakeModel withStrikeDip(double strikeDeg, double dipDeg) {
        return new LayerCakeModel(name, depth, vp, vs, strikeDeg, dipDeg);
    }

    /**
     * Loads a profile: bundled name ({@code prem.nd}, {@code iasp91.tvel}, {@code prem}, ...) or a TauP
     * {@code .nd} / {@code .tvel} file (Depth Vp Vs). Fluid layers (Vs &lt; 0.1 km/s) and everything below are dropped.
     */
    public static LayerCakeModel load(String spec) throws IOException {
        if (spec == null || spec.trim().isEmpty()) {
            throw new IOException("No velocity model given");
        }
        String s = spec.trim();
        File f = new File(s);
        String lowerName = f.getName().toLowerCase(Locale.ROOT);
        String resolved = VelocityModelCatalog.toResourcePath(s);
        InputStream res = LayerCakeModel.class.getClassLoader().getResourceAsStream(resolved);
        if (res == null && !f.isFile()) {
            String token = tokenToBundled(s.toLowerCase(Locale.ROOT));
            if (token != null) {
                resolved = VelocityModelCatalog.toResourcePath(token);
                res = LayerCakeModel.class.getClassLoader().getResourceAsStream(resolved);
                lowerName = token;
            }
        } else if (res != null) {
            lowerName = resolved.toLowerCase(Locale.ROOT);
        }
        BufferedReader br;
        if (res != null) {
            br = new BufferedReader(new InputStreamReader(res, StandardCharsets.UTF_8));
        } else if (f.isFile()) {
            br = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8);
        } else {
            throw new IOException("Velocity model not found: " + spec);
        }
        try (BufferedReader r = br) {
            List<String> raw = new ArrayList<>();
            String rawLine;
            while ((rawLine = r.readLine()) != null) {
                raw.add(rawLine);
            }
            String modelName = f.isFile() && res == null ? f.getName() : lowerName;
            modelName = modelName.substring(modelName.lastIndexOf('/') + 1);
            if (WinHypomhStruct.isWinFileName(s) || WinHypomhStruct.isWinFileName(modelName)
                || WinHypomhStruct.looksLike(raw)) {
                WinHypomhStruct w = WinHypomhStruct.parse(raw);
                return of(modelName, w.depthKm, w.vp, w.vs);
            }
            List<double[]> rows = new ArrayList<>(); // {depth, vp, vs}
            boolean tvel = lowerName.endsWith(".tvel");
            boolean inBlock = false;
            int skip = tvel ? 2 : 0;
            for (String line : raw) {
                if (skip > 0) {
                    skip--;
                    continue;
                }
                String t = line;
                if (inBlock) {
                    int end = t.indexOf("*/");
                    if (end < 0) {
                        continue;
                    }
                    t = t.substring(end + 2);
                    inBlock = false;
                }
                int blk = t.indexOf("/*");
                if (blk >= 0) {
                    int end = t.indexOf("*/", blk + 2);
                    if (end >= 0) {
                        t = t.substring(0, blk) + t.substring(end + 2);
                    } else {
                        t = t.substring(0, blk);
                        inBlock = true;
                    }
                }
                int cut = indexOfComment(t);
                if (cut >= 0) {
                    t = t.substring(0, cut);
                }
                t = t.trim();
                if (t.isEmpty()) {
                    continue;
                }
                String[] p = t.split("\\s+");
                if (p.length < 3 || !isNumeric(p[0]) || !isNumeric(p[1]) || !isNumeric(p[2])) {
                    continue;
                }
                double a = Double.parseDouble(p[0]);
                double b = Double.parseDouble(p[1]);
                double c = Double.parseDouble(p[2]);
                rows.add(new double[] {a, b, c});
            }
            return fromRows(modelName, rows);
        }
    }

    private static LayerCakeModel fromRows(String name, List<double[]> rows) {
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Velocity model has no numeric rows");
        }
        List<Double> d = new ArrayList<>();
        List<Double> a = new ArrayList<>();
        List<Double> b = new ArrayList<>();
        if (rows.get(0)[0] > 0.0) {
            d.add(0.0);
            a.add(rows.get(0)[1]);
            b.add(rows.get(0)[2]);
        }
        for (double[] r : rows) {
            if (r[2] < FLUID_VS) {
                break;
            }
            if (!d.isEmpty() && r[0] < d.get(d.size() - 1)) {
                continue;
            }
            d.add(r[0]);
            a.add(r[1]);
            b.add(r[2]);
        }
        if (d.size() < 2) {
            throw new IllegalArgumentException("Velocity model has no solid layer starting at the surface");
        }
        return of(name, toArray(d), toArray(a), toArray(b));
    }

    private static String tokenToBundled(String t) {
        switch (t) {
            case "prem":
                return "prem.nd";
            case "iasp91":
                return "iasp91.tvel";
            case "ak135":
                return "ak135.tvel";
            default:
                return VelocityModelCatalog.isBundledModelName(t) ? t : null;
        }
    }

    private static int indexOfComment(String line) {
        int hash = line.indexOf('#');
        int slash = line.indexOf("//");
        if (hash >= 0 && (slash < 0 || hash < slash)) {
            return hash;
        }
        return slash;
    }

    private static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double[] toArray(List<Double> l) {
        double[] o = new double[l.size()];
        for (int i = 0; i < o.length; i++) {
            o[i] = l.get(i);
        }
        return o;
    }

    // ------------------------------------------------------------------ queries

    public String name() { return name; }
    public double strikeDeg() { return strikeDeg; }
    public double dipDeg() { return dipDeg; }
    public int sampleCount() { return depth.length; }
    public double profileBottomKm() { return depth[depth.length - 1]; }
    /** Depth samples of the profile (km); a discontinuity appears as two samples 1e-6 km apart. */
    public double[] depthSamples() { return depth.clone(); }

    /**
     * Vertical shift (km) of the layering at the horizontal position {@code (x, y)} (km east / north of the model
     * centre): the interface that is at depth {@code d} at the centre is at {@code d + shift} here.
     */
    public double layerShiftKm(double xKm, double yKm) {
        return shiftPerX * xKm + shiftPerY * yKm;
    }

    /** Velocity (km/s) at local position {@code (x, y)} km from the model centre and physical depth (km). */
    public double velocity(Phase phase, double xKm, double yKm, double depthKm) {
        return profileVelocity(phase, depthKm - layerShiftKm(xKm, yKm));
    }

    /** Velocity of the un-dipped profile at "layer depth" {@code z} (km). */
    public double profileVelocity(Phase phase, double z) {
        double[] v = phase == Phase.P ? vp : vs;
        int n = depth.length;
        if (z <= depth[0]) {
            if (z < depth[0] && n >= 2 && depth[1] > depth[0]) {
                double f = (z - depth[0]) / (depth[1] - depth[0]);
                return v[0] + f * (v[1] - v[0]);
            }
            return v[0];
        }
        if (z >= depth[n - 1]) {
            return v[n - 1];
        }
        int lo = 0;
        int hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (depth[mid] <= z) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double f = (z - depth[lo]) / (depth[hi] - depth[lo]);
        return v[lo] + f * (v[hi] - v[lo]);
    }

    /** The velocity as a function of the eikonal grid's local frame. */
    public EikonalGrid3D.VelocityFunction velocityFunction(Phase phase) {
        return (x, y, z) -> velocity(phase, x, y, z);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "LayerCake[%s, %d samples, strike=%.1f dip=%.1f]",
            name, depth.length, strikeDeg, dipDeg);
    }
}
