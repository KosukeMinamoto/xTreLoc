package com.treloc.xtreloc.solver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.FileUtils;
import com.treloc.xtreloc.io.VelocityModelCatalog;

/**
 * One-dimensional, flat-layered S-wave ray tracer for hypocenter travel times and takeoff/incident angles.
 *
 * <h2>Velocity models</h2>
 * <ul>
 *   <li>TauP-style {@code .nd} and {@code .tvel} files (bundled under {@code velocity-models/} or on disk)</li>
 *   <li>HYPO {@code .aob} / VLSTR {@code struct} files via {@link #loadHypomhStructFile(java.io.File)}</li>
 *   <li>Shorthand tokens {@code prem}, {@code iasp91}, {@code ak135} resolve to bundled tables</li>
 * </ul>
 *
 * <h2>Branch selection</h2>
 * For epicentral distance above 120 km the ray with the smallest positive
 * travel time among generated candidates is taken. At shorter distances the implementation may prefer the
 * candidate whose horizontal distance to the target best matches the requested range, then apply a
 * straight-ray fallback when no branch is numerically trustworthy (see source for thresholds).
 *
 * <h2>System properties</h2>
 * <ul>
 *   <li>{@code -Dxtreloc.raytrace.debug=true} — verbose ray construction on {@code System.err} and FINE logs</li>
 *   <li>{@code -Dxtreloc.raytrace.minlocBranchesOnly=true} — always use minimum positive-time branch (disables
 *       short-distance distance-root preference)</li>
 * </ul>
 *
 * <p>Spherical TauP is not used inside this class; application code may choose TauP via
 * {@link com.treloc.xtreloc.io.AppConfig} in {@link com.treloc.xtreloc.solver.HypoUtils}.</p>
 *
 * @see com.treloc.xtreloc.solver.RaytraceStandalone
 */
public final class Raytrace1D {

    private static final Logger LOGGER = Logger.getLogger(Raytrace1D.class.getName());

    /** Set {@code -Dxtreloc.raytrace.debug=true} for stderr + FINE logs of raytracing steps. */
    private static boolean rayDebug() {
        return Boolean.getBoolean("xtreloc.raytrace.debug");
    }

    private static void rayLog(String msg) {
        if (!rayDebug()) {
            return;
        }
        String line = "[Raytrace1D] " + msg;
        System.err.println(line);
        LOGGER.log(Level.FINE, line);
    }

    /**
     * Minimum absolute velocity gradient magnitude for {@link VelocityGradientMode#ND_AND_TVEL} layers.
     * Avoids division blow-ups in travel-time integrals when a layer is almost constant; HYPO VLSTR files use raw gradients.
     */
    private static final double EPS_SLOPE = 1e-3;

    private enum VelocityGradientMode {
        /** Floor tiny |dv/dz| for discrete global models (.nd / .tvel). */
        ND_AND_TVEL,
        /** HYPO VLSTR struct files: use exact layer gradients (no EPS_SLOPE floor). */
        HYPO_VLSTR
    }
    private static final double EPS_DIV = 1e-10;

    /**
     * Below this epicentral distance (km), branch selection prefers the smallest horizontal-distance residual
     * to the target range before tie-breaking by travel time. Above it, the fastest positive-time branch wins.
     */
    private static final double SHORT_EPICENTRAL_DIST_KM = 120.0;

    /**
     * When epicentral distance is short, distance-root fits on global layered models may be unreliable; large
     * residuals trigger the straight-ray fallback through mean {@code Vs}.
     */
    private static boolean shortRangeDistanceRootNotTrusted(double rrKm, double minXResidualKm) {
        if (!Double.isFinite(minXResidualKm)) {
            return true;
        }
        double tol = Math.max(5.0, 0.15 * rrKm);
        return minXResidualKm > tol;
    }

    private final int n1;
    private final double[] y;
    private final double[] vr;
    private final double[] vlg;
    private final double[] v;

    /**
     * Result of {@link Raytrace1D#solveFastestRay(double, double, double)}: travel time and endpoint ray angles
     * in the flat-layer convention (downward vertical reference).
     */
    public static final class RaySolution {
        /** Geometric ray travel time along the selected branch (s). */
        public final double travelTimeSeconds;
        /**
         * Takeoff angle at the deeper endpoint (rad), measured <strong>from downward vertical</strong> into the ray plane.
         * Not interchangeable with spherical TauP takeoff angles without explicit convention conversion.
         */
        public final double takeoffAngleRad;
        /** Incident angle at the shallower endpoint (rad), from downward vertical. */
        public final double incidentAngleRad;

        public RaySolution(double travelTimeSeconds, double takeoffAngleRad, double incidentAngleRad) {
            this.travelTimeSeconds = travelTimeSeconds;
            this.takeoffAngleRad = takeoffAngleRad;
            this.incidentAngleRad = incidentAngleRad;
        }
    }

    private Raytrace1D(List<Double> depthSamples, List<Double> sVelSamples) {
        this(depthSamples, sVelSamples, VelocityGradientMode.ND_AND_TVEL);
    }

    private Raytrace1D(List<Double> depthSamples, List<Double> sVelSamples, VelocityGradientMode gradientMode) {
        if (depthSamples.size() < 2 || depthSamples.size() != sVelSamples.size()) {
            throw new IllegalArgumentException("Invalid velocity samples.");
        }
        this.n1 = depthSamples.size() - 1;
        this.y = new double[n1 + 1];
        this.vr = new double[n1 + 1];
        this.vlg = new double[n1 + 1];
        this.v = new double[n1 + 1];

        for (int i = 0; i <= n1; i++) {
            y[i] = depthSamples.get(i);
            vr[i] = sVelSamples.get(i);
        }
        if (rayDebug()) {
            rayLog("model built n1=" + n1 + " maxDepthKm=" + y[n1] + " gradientMode=" + gradientMode);
        }
        for (int i = 1; i <= n1; i++) {
            double dz = y[i] - y[i - 1];
            if (dz <= 0) {
                dz = 1e-6;
                y[i] = y[i - 1] + dz;
            }
            double g = (vr[i] - vr[i - 1]) / dz;
            if (gradientMode == VelocityGradientMode.HYPO_VLSTR) {
                vlg[i] = g;
            } else if (Math.abs(g) < EPS_SLOPE) {
                g = (g >= 0.0 ? 1.0 : -1.0) * EPS_SLOPE;
                vlg[i] = g;
            } else {
                vlg[i] = g;
            }
        }
        v[1] = vr[0] / vlg[1];
        for (int i = 2; i <= n1; i++) {
            int j = i - 1;
            v[i] = (vlg[j] * v[j] + (vlg[j] - vlg[i]) * y[j]) / vlg[i];
        }
    }

    /**
     * Loads a velocity model from the classpath, an absolute/relative file path, or a short name
     * ({@code prem.nd}, {@code iasp91.tvel}, token {@code prem}, etc.).
     *
     * @param taupFile resource path, filesystem path, or catalog token
     * @return a ready-to-use model
     * @throws Exception if the file is missing, malformed, or not a supported format
     */
    public static Raytrace1D load(String taupFile) throws Exception {
        String resolved = VelocityModelCatalog.toResourcePath(taupFile);
        InputStream in = Raytrace1D.class.getClassLoader().getResourceAsStream(resolved);
        if (in != null) {
            rayLog("load resource taupFile=" + taupFile + " resolved=" + resolved);
            String extRes = FileUtils.getFileExtension(resolved).toLowerCase();
            try (InputStream resourceIn = in;
                 BufferedReader br = new BufferedReader(new InputStreamReader(resourceIn, StandardCharsets.UTF_8))) {
                if ("tvel".equals(extRes)) {
                    return fromTvelReader(br);
                }
                if ("aob".equals(extRes)) {
                    return fromHypomhVlstrReader(br);
                }
                return fromNdReader(br);
            }
        }
        File f = new File(taupFile);
        if (f.exists() && f.isFile()) {
            rayLog("load file path=" + f.getAbsolutePath());
            String ext = FileUtils.getFileExtension(taupFile).toLowerCase();
            if ("tvel".equals(ext)) {
                return fromTvelFile(f);
            }
            if ("aob".equals(ext)) {
                return loadHypomhStructFile(f);
            }
            return fromNdFile(f);
        }
        return loadByModelToken(taupFile);
    }

    /**
     * Loads a HYPO-style VLSTR {@code struct} (hypomh) file. Line 1 is reference epicenter (lat, lon, depth km);
     * line 2 is layer count and name; then boundary S velocities, thicknesses, and an uncertainty line.
     * Interface depths are not passed through {@code normalizeSamples} so layer boundaries stay aligned with the file.
     *
     * @param file readable {@code .aob} or equivalent struct
     * @return model in {@link VelocityGradientMode#HYPO_VLSTR} mode
     * @throws IOException if the layout does not match the expected record counts
     */
    public static Raytrace1D loadHypomhStructFile(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            return fromHypomhVlstrReader(br);
        }
    }

    private static Raytrace1D fromHypomhVlstrReader(BufferedReader br) throws IOException {
        readNonBlankLine(br);
        String line2 = readNonBlankLine(br);
        String[] t2 = line2.trim().split("\\s+");
        if (t2.length < 2) {
            throw new IOException("hypomh struct line 2: expected NN and name, got: " + line2);
        }
        int nn = Integer.parseInt(t2[0]);
        String name = t2[1].length() > 3 ? t2[1].substring(0, 3) : t2[1];
        double va = 0.0;
        double vb = 0.0;
        if (t2.length >= 4) {
            va = Double.parseDouble(t2[2]);
            vb = Double.parseDouble(t2[3]);
        }
        rayLog("hypomh vlstr header nn=" + nn + " name=" + name + " va=" + va + " vb=" + vb);
        int n1 = nn + 1;
        int nVr = nn + 2;
        List<Double> vrList = readFixedWidthFloatSequence(br, nVr);
        List<Double> thList = readFixedWidthFloatSequence(br, n1);
        readFixedWidthFloatSequence(br, 4);
        if (vrList.size() != nVr || thList.size() != n1) {
            throw new IOException("hypomh struct: expected " + nVr + " VR and " + n1 + " TH values, got "
                + vrList.size() + " and " + thList.size());
        }
        List<Double> dep = new ArrayList<>();
        List<Double> vs = new ArrayList<>();
        dep.add(0.0);
        vs.add(Math.max(0.01, vrList.get(0)));
        double yAcc = 0.0;
        for (int i = 1; i <= n1; i++) {
            yAcc += thList.get(i - 1);
            dep.add(yAcc);
            vs.add(Math.max(0.01, vrList.get(i)));
        }
        return new Raytrace1D(dep, vs, VelocityGradientMode.HYPO_VLSTR);
    }

    private static String readNonBlankLine(BufferedReader br) throws IOException {
        String line;
        do {
            line = br.readLine();
            if (line == null) {
                throw new IOException("Unexpected EOF in hypomh struct file.");
            }
            line = stripInlineComment(line).trim();
        } while (line.isEmpty());
        return line;
    }

    /**
     * Reads {@code count} floats from following non-comment lines (whitespace- or fixed-column separated).
     */
    private static List<Double> readFixedWidthFloatSequence(BufferedReader br, int count) throws IOException {
        List<Double> out = new ArrayList<>(count);
        while (out.size() < count) {
            String line = br.readLine();
            if (line == null) {
                throw new IOException("Unexpected EOF while reading numeric sequence (need " + count + " values).");
            }
            line = stripInlineComment(line).trim();
            if (line.isEmpty()) {
                continue;
            }
            for (String t : line.split("\\s+")) {
                if (t.isEmpty()) {
                    continue;
                }
                out.add(Double.parseDouble(t.replace('D', 'E').replace('d', 'e')));
                if (out.size() >= count) {
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Resolve short names (e.g. {@code prem}) to bundled resources without TauP.
     */
    private static Raytrace1D loadByModelToken(String token) throws IOException {
        String bundled = bundledResourceForToken(token);
        if (bundled == null) {
            throw new IOException(
                "Velocity model not found: " + token
                    + ". Pass a path to .nd/.tvel, a bundled name (e.g. prem.nd), or prem/iasp91/ak135.");
        }
        String path = VelocityModelCatalog.toResourcePath(bundled);
        InputStream in = Raytrace1D.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IOException("Bundled resource missing: " + path);
        }
        rayLog("loadByModelToken token=" + token + " -> " + path);
        try (InputStream resourceIn = in;
             BufferedReader br = new BufferedReader(new InputStreamReader(resourceIn, StandardCharsets.UTF_8))) {
            String ext = FileUtils.getFileExtension(bundled).toLowerCase();
            if ("tvel".equals(ext)) {
                return fromTvelReader(br);
            }
            return fromNdReader(br);
        }
    }

    /** Map CLI-style model token to a filename under {@link VelocityModelCatalog#RESOURCE_DIR}. */
    private static String bundledResourceForToken(String token) {
        if (token == null) {
            return null;
        }
        String t = token.trim();
        if (t.isEmpty()) {
            return null;
        }
        String lower = t.toLowerCase();
        if (VelocityModelCatalog.isBundledModelName(t)) {
            return t;
        }
        if (VelocityModelCatalog.isBundledModelName(lower)) {
            return lower;
        }
        switch (lower) {
            case "prem":
                return "prem.nd";
            case "iasp91":
                return "iasp91.tvel";
            case "ak135":
                return "ak135.tvel";
            default:
                return null;
        }
    }

    private static Raytrace1D fromTvelFile(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return fromTvelReader(br);
        }
    }

    private static Raytrace1D fromTvelReader(BufferedReader br) throws IOException {
        List<Double> dep = new ArrayList<>();
        List<Double> vs = new ArrayList<>();
        br.readLine();
        br.readLine();
        String line;
        while ((line = br.readLine()) != null) {
            String t = stripInlineComment(line).trim();
            if (t.isEmpty()) continue;
            String[] p = t.split("\\s+");
            if (p.length < 3) continue;
            dep.add(Double.parseDouble(p[0]));
            vs.add(Double.parseDouble(p[2]));
        }
        normalizeSamples(dep, vs);
        return new Raytrace1D(dep, vs);
    }

    private static Raytrace1D fromNdFile(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return fromNdReader(br);
        }
    }

    private static Raytrace1D fromNdReader(BufferedReader br) throws IOException {
        List<Double> dep = new ArrayList<>();
        List<Double> vs = new ArrayList<>();
        boolean inBlockComment = false;
        String line;
        while ((line = br.readLine()) != null) {
            String t = line;
            if (inBlockComment) {
                int end = t.indexOf("*/");
                if (end < 0) continue;
                t = t.substring(end + 2);
                inBlockComment = false;
            }
            int blk = t.indexOf("/*");
            if (blk >= 0) {
                int end = t.indexOf("*/", blk + 2);
                if (end >= 0) {
                    t = t.substring(0, blk) + t.substring(end + 2);
                } else {
                    t = t.substring(0, blk);
                    inBlockComment = true;
                }
            }
            t = stripInlineComment(t).trim();
            if (t.isEmpty()) continue;
            String[] p = t.split("\\s+");
            if (p.length < 3) continue; // discontinuity labels etc.
            if (!isNumeric(p[0]) || !isNumeric(p[1]) || !isNumeric(p[2])) continue;
            dep.add(Double.parseDouble(p[0]));
            vs.add(Double.parseDouble(p[2]));
        }
        normalizeSamples(dep, vs);
        return new Raytrace1D(dep, vs);
    }

    private static String stripInlineComment(String line) {
        int hash = line.indexOf('#');
        int slash = line.indexOf("//");
        int cut = -1;
        if (hash >= 0) cut = hash;
        if (slash >= 0 && (cut < 0 || slash < cut)) cut = slash;
        return cut >= 0 ? line.substring(0, cut) : line;
    }

    private static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sorts and cleans depth/velocity samples for global {@code .nd} / {@code .tvel} models: forces a surface
     * sample, removes non-monotonic points, and splits exact duplicate depths with a tiny offset to keep discontinuities.
     */
    private static void normalizeSamples(List<Double> dep, List<Double> vs) {
        if (dep.isEmpty()) {
            throw new IllegalArgumentException("Velocity model has no numeric rows.");
        }
        if (dep.get(0) > 0.0) {
            dep.add(0, 0.0);
            vs.add(0, vs.get(0));
        }
        List<Double> d2 = new ArrayList<>();
        List<Double> v2 = new ArrayList<>();
        for (int i = 0; i < dep.size(); i++) {
            double d = dep.get(i);
            double v = Math.max(0.01, vs.get(i));
            if (d2.isEmpty()) {
                d2.add(d);
                v2.add(v);
                continue;
            }
            double prevD = d2.get(d2.size() - 1);
            if (d < prevD) continue;
            if (Math.abs(d - prevD) < 1e-10) {
                d = prevD + 1e-6;
            }
            d2.add(d);
            v2.add(v);
        }
        dep.clear();
        dep.addAll(d2);
        vs.clear();
        vs.addAll(v2);
    }

    /**
     * Shortest API: S-wave travel time (s) for the fastest selected ray branch.
     *
     * @param distKm        epicentral distance (km, non-negative)
     * @param srcDepthKm    source depth below surface (km)
     * @param stnDepthKm    station depth below surface (km)
     * @return travel time in seconds
     */
    public double travelTimeSeconds(double distKm, double srcDepthKm, double stnDepthKm) {
        return solveFastestRay(distKm, srcDepthKm, stnDepthKm).travelTimeSeconds;
    }

    /**
     * Computes the preferred direct-S ray for the given horizontal distance and endpoint depths, including
     * travel time and takeoff/incident angles in the layer model.
     *
     * @param distKm        epicentral distance (km)
     * @param srcDepthKm    hypocenter depth (km)
     * @param stnDepthKm    station depth (km)
     * @return travel time and angles; see {@link RaySolution} for angle conventions
     * @throws IllegalStateException if no numerically valid branch is found
     */
    public RaySolution solveFastestRay(double distKm, double srcDepthKm, double stnDepthKm) {
        rayLog("solveFastestRay distKm=" + distKm + " srcDepthKm=" + srcDepthKm + " stnDepthKm=" + stnDepthKm);
        double rr = Math.max(0.0, distKm);
        double ya = clampDepth(srcDepthKm);
        double yb = clampDepth(stnDepthKm);

        int l1 = where(Math.max(ya, yb));
        int l2 = where(Math.min(ya, yb));
        rayLog("branch ya=" + ya + " yb=" + yb + " l1=" + l1 + " l2=" + l2 + " rr=" + rr);
        double[] xc = new double[n1 + 2];
        double[] tac = new double[n1 + 2];
        rpcod(Math.max(ya, yb), l1, Math.min(ya, yb), l2, xc, tac);
        if (rayDebug()) {
            int hi = Math.min(n1, l1 + 4);
            StringBuilder sb = new StringBuilder("rpcod xc/tac sample i=");
            for (int ii = l1; ii <= hi; ii++) {
                sb.append(" [").append(ii).append(" xc=").append(xc[ii]).append(" tacRad=").append(tac[ii]).append("]");
            }
            rayLog(sb.toString());
        }

        List<Double> cTrv = new ArrayList<>();
        List<Double> cAng = new ArrayList<>();
        List<Double> cBng = new ArrayList<>();
        List<Double> cXres = new ArrayList<>();
        List<Integer> cLayer = new ArrayList<>();

        for (int i = l1; i <= n1; i++) {
            int j = i + 1;
            double t1 = rr - xc[i];
            double t2 = xc[j] - rr;
            if (t1 * t2 < 0.0) continue;
            double x1 = xc[i];
            double x2 = xc[j];
            double ta1 = tac[i];
            double ta2 = tac[j];
            double x0 = x1;
            double a0 = 0.0;
            double ta0 = 0.0;
            final double eps1 = 1e-8;
            final int lit1 = 30;
            for (int k = 0; k < lit1; k++) {
                double xr = x1 - x2;
                if (rr > 0.0 && Math.abs(xr / rr) < eps1) {
                    break;
                }
                ta0 = (ta1 + ta2) / 2.0;
                double[] xa = rxcod(Math.max(ya, yb), l1, Math.min(ya, yb), l2, ta0);
                x0 = xa[0];
                a0 = xa[1];
                double t0 = rr - x0;
                if (t0 * t1 <= 0.0) {
                    x2 = x0;
                    ta2 = ta0;
                } else {
                    x1 = x0;
                    ta1 = ta0;
                }
            }
            final double bisectionTa0 = ta0;
            final double eps2 = 1e-14;
            final int lit2 = 10;
            double tag = bisectionTa0;
            double dt0 = rr <= 0.0 ? Math.abs(rr - x0) : Math.abs((rr - x0) / rr);
            for (int k = 0; k < lit2; k++) {
                double dta = (rr - x0) / a0;
                tag = bisectionTa0 + dta;
                if (Math.abs(dta) < eps2) {
                    break;
                }
                double[] xa = rxcod(Math.max(ya, yb), l1, Math.min(ya, yb), l2, tag);
                x0 = xa[0];
                a0 = xa[1];
                double dtc = rr <= 0.0 ? Math.abs(rr - x0) : Math.abs((rr - x0) / rr);
                if (dt0 > dtc) {
                    if (dtc < eps2) {
                        break;
                    }
                    continue;
                }
                tag = bisectionTa0;
                break;
            }
            double trvCand = trvel(Math.max(ya, yb), l1, Math.min(ya, yb), l2, tag);
            if (!Double.isFinite(trvCand) || trvCand <= 0.0) {
                continue;
            }
            double[] xaFit = rxcod(Math.max(ya, yb), l1, Math.min(ya, yb), l2, tag);
            double xResidualKm = Math.abs(rr - xaFit[0]);
            double angVal;
            double bngVal;
            if (ya >= yb) {
                angVal = tag;
                double sang = Math.sin(tag) * vlg[l2] * (Math.min(ya, yb) + v[l2])
                    / (vlg[l1] * (Math.max(ya, yb) + v[l1]));
                sang = Math.max(-1.0, Math.min(1.0, sang));
                bngVal = Math.asin(sang);
            } else {
                double sang = Math.sin(tag) * vlg[l2] * (Math.min(ya, yb) + v[l2])
                    / (vlg[l1] * (Math.max(ya, yb) + v[l1]));
                sang = Math.max(-1.0, Math.min(1.0, sang));
                angVal = Math.asin(sang);
                bngVal = tag;
            }
            cTrv.add(trvCand);
            cAng.add(angVal);
            cBng.add(bngVal);
            cXres.add(xResidualKm);
            cLayer.add(i);
            if (rayDebug()) {
                rayLog("candidate layerLoopI=" + i + " tagDeg=" + Math.toDegrees(tag)
                    + " xResKm=" + xResidualKm + " trv=" + trvCand);
            }
        }
        if (cTrv.isEmpty()) {
            if (rr <= SHORT_EPICENTRAL_DIST_KM) {
                if (rayDebug()) {
                    rayLog("short-range straight-ray fallback (no positive-time candidates) rr=" + rr);
                }
                return approximateRegionalDirectRay(rr, ya, yb);
            }
            throw new IllegalStateException("No valid ray path for distance/depth combination.");
        }
        double minXResidual = Double.POSITIVE_INFINITY;
        for (double xr : cXres) {
            if (Double.isFinite(xr)) {
                minXResidual = Math.min(minXResidual, xr);
            }
        }
        if (rr <= SHORT_EPICENTRAL_DIST_KM && shortRangeDistanceRootNotTrusted(rr, minXResidual)) {
            if (rayDebug()) {
                rayLog("short-range straight-ray fallback minXResidualKm=" + minXResidual + " rr=" + rr);
            }
            return approximateRegionalDirectRay(rr, ya, yb);
        }
        if (shouldPreferDistanceRoot(rr)) {
            return pickShortEpicentralBestDistanceRoot(rr, cTrv, cAng, cBng, cLayer, cXres);
        }
        return pickMinlocPositiveTrv(cTrv, cAng, cBng, cLayer, "min-travel-time");
    }

    /** Optional override for tests / parity harness (default: distance-based pick below {@link #SHORT_EPICENTRAL_DIST_KM}). */
    private static boolean shouldPreferDistanceRoot(double rrKm) {
        if (Boolean.getBoolean("xtreloc.raytrace.minlocBranchesOnly")) {
            return false;
        }
        return rrKm <= SHORT_EPICENTRAL_DIST_KM;
    }

    /**
     * Straight ray in a locally uniform medium: path length {@code hypot(Δ, |zs−zr|)} / {@code Vs} at mid-depth.
     * Angles use {@code atan2(horizontal, |Δz|)} from vertical in a uniform wedge — rough fallback only; same caveat as
     * {@link RaySolution#takeoffAngleRad} vs spherical TauP angles.
     */
    private RaySolution approximateRegionalDirectRay(double epicentralKm, double sourceDepthKm, double stationDepthKm) {
        double r = Math.max(0.0, epicentralKm);
        double zs = clampDepth(sourceDepthKm);
        double zr = clampDepth(stationDepthKm);
        double dz = zs - zr;
        double horizDen = Math.abs(dz) < 1e-12 ? 1e-12 : Math.abs(dz);
        double pathLen = Math.hypot(r, dz);
        double vs = sourceSVelocity(0.5 * (zs + zr));
        vs = Math.max(0.5, vs);
        double t = pathLen / vs;
        double ang = Math.atan2(r, horizDen);
        if (!Double.isFinite(ang)) {
            ang = Math.PI / 4.0;
        }
        return new RaySolution(t, ang, ang);
    }

    /**
     * Regional rays: among branches with crustal direct-S–like travel times, minimize epicentral-distance
     * residual to epicentral distance, then travel time. Spurious long-path roots often fit {@code rr}
     * very tightly but sit outside this envelope; excluding them before min-residual avoids picking ~300 s at
     * ~10 km. If no candidate lies in the envelope, retries without the filter (then minimum-time fallback).
     */
    private RaySolution pickShortEpicentralBestDistanceRoot(
        double rr,
        List<Double> cTrv,
        List<Double> cAng,
        List<Double> cBng,
        List<Integer> cLayer,
        List<Double> cXres) {
        RaySolution inEnvelope = selectShortRangeRootByMinDistanceResidual(
            rr, cTrv, cAng, cBng, cLayer, cXres, true);
        if (inEnvelope != null) {
            return inEnvelope;
        }
        RaySolution unconstrained = selectShortRangeRootByMinDistanceResidual(
            rr, cTrv, cAng, cBng, cLayer, cXres, false);
        if (unconstrained != null) {
            return unconstrained;
        }
        return pickMinlocPositiveTrv(cTrv, cAng, cBng, cLayer, "min-travel-time-fallback");
    }

    /**
     * @param travelTimeEnvelope if true, keep only candidates with {@code rr/10 ≤ trv ≤ rr/2 + 80} (seconds).
     */
    private RaySolution selectShortRangeRootByMinDistanceResidual(
        double rr,
        List<Double> cTrv,
        List<Double> cAng,
        List<Double> cBng,
        List<Integer> cLayer,
        List<Double> cXres,
        boolean travelTimeEnvelope) {
        final double tLo = rr / 10.0;
        final double tHi = rr / 2.0 + 80.0;
        final double tolKm = 1e-4 * Math.max(rr, 1.0) + 1e-6;
        int bestI = -1;
        double bestX = Double.POSITIVE_INFINITY;
        double bestT = Double.POSITIVE_INFINITY;
        for (int i = 0; i < cTrv.size(); i++) {
            double t = cTrv.get(i);
            if (!Double.isFinite(t) || t <= 0.0) {
                continue;
            }
            if (travelTimeEnvelope && (t < tLo || t > tHi)) {
                continue;
            }
            double xr = cXres.get(i);
            if (xr < bestX - tolKm) {
                bestX = xr;
                bestT = t;
                bestI = i;
            } else if (Math.abs(xr - bestX) <= tolKm && t < bestT) {
                bestT = t;
                bestI = i;
            }
        }
        if (bestI < 0) {
            return null;
        }
        if (rayDebug()) {
            rayLog("pick tier=short-range-minXResidual envelope=" + travelTimeEnvelope
                + " layerLoopI=" + cLayer.get(bestI) + " xResKm=" + cXres.get(bestI) + " bestTimeSec=" + bestT);
        }
        return new RaySolution(bestT, cAng.get(bestI), cBng.get(bestI));
    }

    /** Picks the candidate with minimum positive travel time among collected branches. */
    private RaySolution pickMinlocPositiveTrv(
        List<Double> cTrv,
        List<Double> cAng,
        List<Double> cBng,
        List<Integer> cLayer,
        String tierLabel) {
        int bestI = -1;
        double bestT = Double.POSITIVE_INFINITY;
        for (int i = 0; i < cTrv.size(); i++) {
            double t = cTrv.get(i);
            if (!Double.isFinite(t) || t <= 0.0) {
                continue;
            }
            if (t < bestT) {
                bestT = t;
                bestI = i;
            }
        }
        if (bestI < 0) {
            throw new IllegalStateException(
                "No ray candidate passed plausibility checks (distance roots / takeoff).");
        }
        if (rayDebug()) {
            rayLog("pick tier=" + tierLabel + " layerLoopI=" + cLayer.get(bestI) + " bestTimeSec=" + bestT);
        }
        return new RaySolution(bestT, cAng.get(bestI), cBng.get(bestI));
    }

    /**
     * Interpolated S velocity at {@code depthKm} (km) for partial derivatives in {@link com.treloc.xtreloc.solver.HypoUtils}.
     */
    double sourceSVelocity(double depthKm) {
        double d = clampDepth(depthKm);
        int i = where(d);
        int i0 = Math.max(0, i - 1);
        double y0 = y[i0];
        double y1 = y[i];
        double v0 = vr[i0];
        double v1 = vr[i];
        if (y1 <= y0 + 1e-12) {
            return Math.max(0.01, v1);
        }
        double t = (d - y0) / (y1 - y0);
        return Math.max(0.01, v0 + t * (v1 - v0));
    }

    private double clampDepth(double dep) {
        double d = Math.max(0.0, dep);
        return Math.min(d, y[n1] - 1e-6);
    }

    private int where(double depth) {
        for (int i = 1; i <= n1; i++) {
            if (y[i] >= depth) return i;
        }
        return n1;
    }

    private void rpcod(double y1, int l1, double y2, int l2, double[] xc, double[] tac) {
        xc[l1] = 0.0;
        tac[l1] = Math.PI;
        for (int i = l1; i <= n1; i++) {
            int j = i + 1;
            double pp = 1.0 / Math.max(vr[i], EPS_DIV);
            double yn = y[i];
            double[] xa = rxinc(0, pp, y1, l1, y2, l2);
            double[] xb = rxinc(1, pp, yn, i, y1, l1);
            xc[j] = xa[0] + 2.0 * xb[0];
            double sc = pp * vlg[l1] * (y1 + v[l1]);
            sc = Math.max(-1.0, Math.min(1.0, sc));
            tac[j] = Math.asin(sc);
        }
    }

    private double[] rxcod(double y1, int l1, double y2, int l2, double ta) {
        double p2 = Math.PI / 2.0;
        double pp = Math.sin(ta) / (vlg[l1] * (y1 + v[l1]));
        double[] xa = rxinc(0, pp, y1, l1, y2, l2);
        double x = xa[0];
        double a = xa[1];
        if (ta >= p2) {
            return new double[] { x, -a };
        }
        double pn = 1.0 / Math.max(pp, EPS_DIV);
        int nl = n1;
        for (int i = 1; i <= n1; i++) {
            if (vr[i] >= pn) {
                nl = i;
                break;
            }
        }
        double yn = pn / vlg[nl] - v[nl];
        double[] xd = rxinc(1, pp, yn, nl, y1, l1);
        x += 2.0 * xd[0];
        a += 2.0 * xd[1];
        return new double[] { x, a };
    }

    /** Layer integral for horizontal distance ({@code id==0}) or auxiliary term ({@code id==1}); stabilizes grazing rays by flooring {@code cn}. */
    private double[] rxinc(int id, double pp, double y1, int l1, double y2, int l2) {
        double x = 0.0;
        double a = 0.0;
        if (y1 == y2) return new double[] { x, a };
        int k1 = l2 - 1;
        int lm = (id == 0) ? l1 + 1 : l2 + 1;
        double[] sn = new double[n1 + 2];
        double[] cn = new double[n1 + 2];
        double[] rn = new double[n1 + 2];
        for (int i = k1; i <= l1; i++) {
            int j = i + 1;
            sn[j] = pp * vr[i];
            if (i == k1) sn[j] = pp * vlg[l2] * (y2 + v[l2]);
            if (i == l1) sn[j] = pp * vlg[l1] * (y1 + v[l1]);
            sn[j] = Math.max(-1.0, Math.min(1.0, sn[j]));
            cn[j] = Math.sqrt(Math.max(0.0, 1.0 - sn[j] * sn[j]));
            if (cn[j] < 1e-10) {
                cn[j] = 1e-10;
            }
        }
        for (int i = k1; i <= l1; i++) {
            int j = i + 1;
            if ((id == 0 && i == l1) || (id == 1 && i == k1)) {
                rn[j] = 1.0;
            } else if (id == 1 && i == l1) {
                rn[j] = 0.0;
            } else {
                rn[j] = cn[lm] / cn[j];
            }
        }
        for (int i = l2; i <= l1; i++) {
            int j = i + 1;
            x += (cn[i] - cn[j]) / vlg[i];
            a += (rn[i] - rn[j]) / vlg[i];
        }
        x /= Math.max(pp, EPS_DIV);
        double snLm = sn[lm];
        if (Math.abs(snLm) < EPS_DIV) {
            snLm = snLm >= 0.0 ? EPS_DIV : -EPS_DIV;
        }
        a = -a / (Math.max(pp, EPS_DIV) * snLm);
        return new double[] { x, a };
    }

    private double trvel(double y1, int l1, double y2, int l2, double ta) {
        double p2 = Math.PI / 2.0;
        double pp = Math.sin(ta) / (vlg[l1] * (y1 + v[l1]));
        double tt = rtinc(pp, y1, l1, y2, l2);
        if (ta >= p2) {
            if (rayDebug()) {
                rayLog("trvel ta>pi/2 branch tt=" + tt + " pp=" + pp);
            }
            return tt;
        }
        double pn = 1.0 / Math.max(pp, EPS_DIV);
        int nl = n1;
        for (int i = 1; i <= n1; i++) {
            if (vr[i] >= pn) {
                nl = i;
                break;
            }
        }
        double yn = pn / vlg[nl] - v[nl];
        double td = rtinc(pp, yn, nl, y1, l1);
        if (rayDebug()) {
            rayLog("trvel ta<pi/2 tt=" + tt + " td=" + td + " sum=" + (tt + 2.0 * td) + " pp=" + pp + " nl=" + nl + " yn=" + yn);
        }
        return tt + 2.0 * td;
    }

    /** Travel-time integral across layers; uses the raw {@code cnr} ratio without clamping the denominator. */
    private double rtinc(double pp, double y1, int l1, double y2, int l2) {
        double tt = 0.0;
        if (y1 == y2) {
            return tt;
        }
        int k1 = l2 - 1;
        double[] sn = new double[n1 + 2];
        double[] cn = new double[n1 + 2];
        for (int i = k1; i <= l1; i++) {
            int j = i + 1;
            sn[j] = pp * vr[i];
            if (i == k1) {
                sn[j] = pp * vlg[l2] * (y2 + v[l2]);
            }
            if (i == l1) {
                sn[j] = pp * vlg[l1] * (y1 + v[l1]);
            }
            sn[j] = Math.max(-1.0, Math.min(1.0, sn[j]));
            cn[j] = Math.sqrt(Math.max(0.0, 1.0 - sn[j] * sn[j]));
        }
        int layerLog = 0;
        final int maxLayerLog = 18;
        for (int i = l2; i <= l1; i++) {
            int j = i + 1;
            double num = (1.0 - cn[j]) * (1.0 + cn[i]);
            double den = (1.0 + cn[j]) * (1.0 - cn[i]);
            double cnr = num / den;
            double term = Math.log(cnr) / (2.0 * vlg[i]);
            tt += term;
            if (rayDebug() && layerLog < maxLayerLog) {
                rayLog("rtinc layer i=" + i + " cnr=" + cnr + " vlg[i]=" + vlg[i] + " term=" + term + " ttAcc=" + tt);
                layerLog++;
            }
        }
        if (rayDebug()) {
            rayLog("rtinc done pp=" + pp + " y1=" + y1 + " l1=" + l1 + " y2=" + y2 + " l2=" + l2 + " ttSum=" + tt);
        }
        return tt;
    }
}
