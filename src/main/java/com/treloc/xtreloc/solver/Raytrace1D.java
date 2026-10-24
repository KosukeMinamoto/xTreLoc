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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.FileUtils;
import com.treloc.xtreloc.io.VelocityModelCatalog;

/**
 * One-dimensional layered ray tracer (P or S) for hypocenter travel times and takeoff/incident angles.
 *
 * <h2>Velocity models</h2>
 * <ul>
 *   <li>TauP-style {@code .nd} and {@code .tvel} files (bundled under {@code velocity-models/} or on disk)</li>
 *   <li>WIN / hypomh structure files ({@code struct.tbl}, {@code .aob}): discrete P samples with a constant
 *       gradient in each layer (linear interpolation); S = Vp/1.73. See {@link WinHypomhStruct}.</li>
 *   <li>Shorthand tokens {@code prem}, {@code iasp91}, {@code ak135} resolve to bundled tables</li>
 * </ul>
 *
 * <h2>Algorithm</h2>
 * <p>The model is a stack of layers with linear velocity {@code v(z)}. For {@code .nd}/{@code .tvel} models the
 * stack is first mapped to a flat model with the exact Earth-flattening transform
 * ({@code z_f = -R ln(1 - z/R)}, {@code v_f = v R/(R - z)}), so that the travel time and the ray parameter
 * (s/km along the surface) agree with the spherical solution; HYPO {@code .aob} models are used as flat layers.
 * Layers thicker than 50 km are subdivided to keep the flattened velocity piecewise linear to good accuracy.</p>
 *
 * <p>Inside each layer the range and travel time of a ray with horizontal slowness {@code p} are evaluated with
 * the closed-form integrals for a constant-gradient medium (no gradient floor). Candidate rays are</p>
 * <ol>
 *   <li>the direct ray between the two depths (monotone in {@code p}, solved by bisection),</li>
 *   <li>rays turning inside each layer below the deeper endpoint (solved on the turning velocity), and</li>
 *   <li>head waves along constant-velocity layers.</li>
 * </ol>
 * <p>The candidate with the smallest travel time is returned. There is no straight-ray fallback: if no branch
 * exists an {@link IllegalStateException} is thrown.</p>
 *
 * <h2>System properties</h2>
 * <ul>
 *   <li>{@code -Dxtreloc.raytrace.debug=true} — verbose ray construction on {@code System.err} and FINE logs</li>
 *   <li>{@code -Dxtreloc.raytrace.flatEarth=true} — do not apply the Earth-flattening transform to
 *       {@code .nd}/{@code .tvel} models (pure flat-layer model)</li>
 * </ul>
 *
 * <p>Spherical TauP is not used inside this class; application code may choose TauP via
 * {@link com.treloc.xtreloc.io.AppConfig} in {@link com.treloc.xtreloc.solver.HypoUtils}.</p>
 *
 * @see com.treloc.xtreloc.solver.RaytraceStandalone
 */
public final class Raytrace1D {

    private static final Logger LOGGER = Logger.getLogger(Raytrace1D.class.getName());
    private static final ConcurrentHashMap<String, Raytrace1D> LOAD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Dual> DUAL_CACHE = new ConcurrentHashMap<>();

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

    /** Earth radius used by the flattening transform (km); consistent with TauP's reference radius. */
    private static final double R_EARTH = 6371.0;
    /** Layers thicker than this (km, physical depth) are subdivided before flattening. */
    private static final double MAX_SUBLAYER_KM = 50.0;
    /** Depth intervals thinner than this (km) are treated as velocity discontinuities, not layers. */
    private static final double MIN_LAYER_KM = 1e-4;
    /** Samples with S velocity below this (km/s) are fluid; the model is truncated there. */
    private static final double FLUID_VS = 0.1;
    private static final double BIG = 1e30;

    /** Model-coordinate layer arrays (flattened depth/velocity when {@link #flattened}). */
    private final int nl;
    private final double[] zTop;
    private final double[] zBot;
    private final double[] vTop;
    private final double[] vBot;
    /** {@code prefMaxV[j]} = max velocity in layers 0..j (model coordinates). */
    private final double[] prefMaxV;
    private final boolean flattened;

    /** Physical (unflattened, truncated) samples used for {@link #sourceSVelocity(double)}. */
    private final double[] physZ;
    private final double[] physV;
    private final double zMaxPhys;

    /**
     * Result of {@link Raytrace1D#solveFastestRay(double, double, double)}: travel time, endpoint ray angles and
     * horizontal slowness.
     */
    public static final class RaySolution {
        /** Geometric ray travel time along the selected branch (s). */
        public final double travelTimeSeconds;
        /**
         * Takeoff angle at the source (rad), measured <strong>from downward vertical</strong> into the ray plane
         * (0 = straight down, {@code pi} = straight up).
         */
        public final double takeoffAngleRad;
        /** Incident angle at the receiver (rad), from downward vertical (acute; the ray arrives from below). */
        public final double incidentAngleRad;
        /** Horizontal slowness (ray parameter) {@code p = dT/dX} along the surface (s/km). */
        public final double horizontalSlowness;

        public RaySolution(double travelTimeSeconds, double takeoffAngleRad, double incidentAngleRad) {
            this(travelTimeSeconds, takeoffAngleRad, incidentAngleRad, Double.NaN);
        }

        public RaySolution(double travelTimeSeconds, double takeoffAngleRad, double incidentAngleRad,
                           double horizontalSlowness) {
            this.travelTimeSeconds = travelTimeSeconds;
            this.takeoffAngleRad = takeoffAngleRad;
            this.incidentAngleRad = incidentAngleRad;
            this.horizontalSlowness = horizontalSlowness;
        }
    }

    private static boolean defaultFlattening() {
        return !Boolean.getBoolean("xtreloc.raytrace.flatEarth");
    }

    private Raytrace1D(List<Double> depthSamples, List<Double> sVelSamples, boolean flatten) {
        if (depthSamples.size() < 2 || depthSamples.size() != sVelSamples.size()) {
            throw new IllegalArgumentException("Invalid velocity samples.");
        }
        this.flattened = flatten;

        // 1) truncate at the first fluid sample
        List<Double> zs = new ArrayList<>();
        List<Double> vs = new ArrayList<>();
        for (int i = 0; i < depthSamples.size(); i++) {
            double v = sVelSamples.get(i);
            if (v < FLUID_VS) {
                break;
            }
            zs.add(depthSamples.get(i));
            vs.add(v);
        }
        if (zs.size() < 2) {
            throw new IllegalArgumentException("Velocity model has no solid layer starting at the surface.");
        }
        this.physZ = new double[zs.size()];
        this.physV = new double[zs.size()];
        for (int i = 0; i < physZ.length; i++) {
            physZ[i] = zs.get(i);
            physV[i] = vs.get(i);
        }
        this.zMaxPhys = physZ[physZ.length - 1];

        // 2) layers (skip near-zero thickness = discontinuity), subdivide, flatten
        List<double[]> layers = new ArrayList<>();
        for (int i = 1; i < physZ.length; i++) {
            double za = physZ[i - 1];
            double zb = physZ[i];
            double va = physV[i - 1];
            double vb = physV[i];
            double dz = zb - za;
            if (dz < MIN_LAYER_KM) {
                continue;
            }
            int nsub = flatten ? Math.max(1, (int) Math.ceil(dz / MAX_SUBLAYER_KM)) : 1;
            for (int k = 0; k < nsub; k++) {
                double f0 = (double) k / nsub;
                double f1 = (double) (k + 1) / nsub;
                double z0 = za + f0 * dz;
                double z1 = za + f1 * dz;
                double v0 = va + f0 * (vb - va);
                double v1 = va + f1 * (vb - va);
                layers.add(new double[] {
                    toModelDepth(z0, flatten), toModelDepth(z1, flatten),
                    toModelVelocity(v0, z0, flatten), toModelVelocity(v1, z1, flatten)});
            }
        }
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("Velocity model has no layers.");
        }
        this.nl = layers.size();
        this.zTop = new double[nl];
        this.zBot = new double[nl];
        this.vTop = new double[nl];
        this.vBot = new double[nl];
        this.prefMaxV = new double[nl];
        double run = 0.0;
        for (int j = 0; j < nl; j++) {
            double[] L = layers.get(j);
            zTop[j] = L[0];
            zBot[j] = L[1];
            vTop[j] = L[2];
            vBot[j] = L[3];
            run = Math.max(run, Math.max(vTop[j], vBot[j]));
            prefMaxV[j] = run;
        }
        rayLog("model built layers=" + nl + " maxDepthKm=" + zMaxPhys + " flattened=" + flatten);
    }

    private static double toModelDepth(double z, boolean flatten) {
        if (!flatten) {
            return z;
        }
        return -R_EARTH * Math.log1p(-Math.min(z, R_EARTH * 0.999999) / R_EARTH);
    }

    private static double toModelVelocity(double v, double z, boolean flatten) {
        if (!flatten) {
            return v;
        }
        return v * R_EARTH / (R_EARTH - Math.min(z, R_EARTH * 0.999999));
    }

    // ------------------------------------------------------------------ loading

    /**
     * Loads a velocity model from the classpath, an absolute/relative file path, or a short name
     * ({@code prem.nd}, {@code iasp91.tvel}, token {@code prem}, etc.).
     *
     * @param taupFile resource path, filesystem path, or catalog token
     * @return a ready-to-use model
     * @throws Exception if the file is missing, malformed, or not a supported format
     */
    /**
     * P and S tracers from the same {@code .nd}/{@code .tvel} or WIN structure model. Both Vp and Vs are required
     * ({@code .nd}/{@code .tvel} must list them; WIN derives S as Vp/1.73).
     */
    public static final class Dual {
        public final Raytrace1D p;
        public final Raytrace1D s;

        Dual(Raytrace1D p, Raytrace1D s) {
            this.p = p;
            this.s = s;
        }

        public Raytrace1D forPhase(com.treloc.xtreloc.solver.table.TravelTimeTable.Phase phase) {
            return phase == com.treloc.xtreloc.solver.table.TravelTimeTable.Phase.P ? p : s;
        }
    }

    public static Raytrace1D load(String taupFile) throws Exception {
        return load(taupFile, defaultFlattening());
    }

    public static Dual loadDual(String taupFile) throws Exception {
        return loadDual(taupFile, defaultFlattening());
    }

    public static Dual loadDual(String taupFile, boolean earthFlattening) throws Exception {
        String cacheKey = "dual|" + taupFile + '|' + earthFlattening;
        Dual cached = DUAL_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Dual loaded = loadDualUncached(taupFile, earthFlattening);
        Dual raced = DUAL_CACHE.putIfAbsent(cacheKey, loaded);
        return raced != null ? raced : loaded;
    }

    /**
     * Same as {@link #load(String)} with explicit control of the Earth-flattening transform
     * (ignored for HYPO {@code .aob} models, which are always flat).
     *
     * @param earthFlattening {@code true}: treat the model as a spherical Earth (matches TauP);
     *                        {@code false}: pure flat layers
     */
    public static Raytrace1D load(String taupFile, boolean earthFlattening) throws Exception {
        boolean flat = earthFlattening;
        String cacheKey = String.valueOf(taupFile) + '|' + flat;
        Raytrace1D cached = LOAD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Raytrace1D loaded = loadUncached(taupFile, flat);
        Raytrace1D raced = LOAD_CACHE.putIfAbsent(cacheKey, loaded);
        return raced != null ? raced : loaded;
    }

    private static Dual loadDualUncached(String taupFile, boolean earthFlattening) throws Exception {
        String resolved = VelocityModelCatalog.toResourcePath(taupFile);
        InputStream in = Raytrace1D.class.getClassLoader().getResourceAsStream(resolved);
        String ext;
        BufferedReader br;
        if (in != null) {
            ext = FileUtils.getFileExtension(resolved).toLowerCase();
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } else {
            File f = new File(taupFile);
            if (!f.exists() || !f.isFile()) {
                String bundled = bundledResourceForToken(taupFile);
                if (bundled == null) {
                    throw new IOException(
                        "Velocity model not found: " + taupFile
                            + ". Pass a path to .nd/.tvel with Vp and Vs (e.g. prem.nd).");
                }
                String path = VelocityModelCatalog.toResourcePath(bundled);
                in = Raytrace1D.class.getClassLoader().getResourceAsStream(path);
                if (in == null) {
                    throw new IOException("Bundled resource missing: " + path);
                }
                ext = FileUtils.getFileExtension(bundled).toLowerCase();
                br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            } else {
                ext = FileUtils.getFileExtension(taupFile).toLowerCase();
                br = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8));
            }
        }
        try (BufferedReader r = br) {
            if ("tvel".equals(ext)) {
                return dualFromSamples(readTvelSamples(r), earthFlattening);
            }
            List<String> lines = readAllLines(r);
            if ("aob".equals(ext) || WinHypomhStruct.isWinFileName(taupFile) || WinHypomhStruct.looksLike(lines)) {
                return dualFromWin(WinHypomhStruct.parse(lines));
            }
            return dualFromSamples(readNdSamples(lines), earthFlattening);
        }
    }

    private static final class VelSamples {
        final List<Double> dep = new ArrayList<>();
        final List<Double> vp = new ArrayList<>();
        final List<Double> vs = new ArrayList<>();
    }

    private static Dual dualFromSamples(VelSamples s, boolean flat) throws IOException {
        if (s.dep.isEmpty() || s.dep.size() != s.vp.size() || s.dep.size() != s.vs.size()) {
            throw new IOException("Velocity model has no Depth / Vp / Vs rows.");
        }
        boolean anyP = false;
        boolean anyS = false;
        for (int i = 0; i < s.vp.size(); i++) {
            if (s.vp.get(i) >= 0.5) {
                anyP = true;
            }
            if (s.vs.get(i) >= FLUID_VS) {
                anyS = true;
            }
        }
        if (!anyP || !anyS) {
            throw new IOException("Velocity model must provide both P and S velocities.");
        }
        List<Double> dP = new ArrayList<>(s.dep);
        List<Double> vP = new ArrayList<>(s.vp);
        List<Double> dS = new ArrayList<>(s.dep);
        List<Double> vS = new ArrayList<>(s.vs);
        normalizeSamples(dP, vP);
        normalizeSamples(dS, vS);
        return new Dual(new Raytrace1D(dP, vP, flat), new Raytrace1D(dS, vS, flat));
    }

    private static Dual dualFromWin(WinHypomhStruct w) throws IOException {
        VelSamples s = new VelSamples();
        for (int i = 0; i < w.depthKm.length; i++) {
            s.dep.add(w.depthKm[i]);
            s.vp.add(w.vp[i]);
            s.vs.add(w.vs[i]);
        }
        return dualFromSamples(s, false);
    }

    private static List<String> readAllLines(BufferedReader br) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            lines.add(line);
        }
        return lines;
    }

    private static VelSamples readNdSamples(BufferedReader br) throws IOException {
        return readNdSamples(readAllLines(br));
    }

    private static VelSamples readNdSamples(List<String> raw) throws IOException {
        VelSamples out = new VelSamples();
        boolean inBlockComment = false;
        for (String line : raw) {
            String t = line;
            if (inBlockComment) {
                int end = t.indexOf("*/");
                if (end < 0) {
                    continue;
                }
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
            if (t.isEmpty()) {
                continue;
            }
            String[] p = t.split("\\s+");
            if (p.length < 3 || !isNumeric(p[0]) || !isNumeric(p[1]) || !isNumeric(p[2])) {
                continue;
            }
            out.dep.add(Double.parseDouble(p[0]));
            out.vp.add(Double.parseDouble(p[1]));
            out.vs.add(Double.parseDouble(p[2]));
        }
        return out;
    }

    private static VelSamples readTvelSamples(BufferedReader br) throws IOException {
        br.readLine();
        br.readLine();
        VelSamples out = new VelSamples();
        String line;
        while ((line = br.readLine()) != null) {
            String t = stripInlineComment(line).trim();
            if (t.isEmpty()) {
                continue;
            }
            String[] p = t.split("\\s+");
            if (p.length < 3) {
                continue;
            }
            out.dep.add(Double.parseDouble(p[0]));
            out.vp.add(Double.parseDouble(p[1]));
            out.vs.add(Double.parseDouble(p[2]));
        }
        return out;
    }

    private static Raytrace1D loadUncached(String taupFile, boolean earthFlattening) throws Exception {
        boolean flat = earthFlattening;
        String resolved = VelocityModelCatalog.toResourcePath(taupFile);
        InputStream in = Raytrace1D.class.getClassLoader().getResourceAsStream(resolved);
        if (in != null) {
            rayLog("load resource taupFile=" + taupFile + " resolved=" + resolved);
            String extRes = FileUtils.getFileExtension(resolved).toLowerCase();
            try (InputStream resourceIn = in;
                 BufferedReader br = new BufferedReader(new InputStreamReader(resourceIn, StandardCharsets.UTF_8))) {
                if ("tvel".equals(extRes)) {
                    return fromTvelReader(br, flat);
                }
                if ("aob".equals(extRes)) {
                    return fromHypomhVlstrReader(br);
                }
                return fromNdReader(br, flat);
            }
        }
        File f = new File(taupFile);
        if (f.exists() && f.isFile()) {
            rayLog("load file path=" + f.getAbsolutePath());
            String ext = FileUtils.getFileExtension(taupFile).toLowerCase();
            if ("tvel".equals(ext)) {
                try (BufferedReader br = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
                    return fromTvelReader(br, flat);
                }
            }
            if ("aob".equals(ext) || WinHypomhStruct.isWinFileName(taupFile)) {
                return loadHypomhStructFile(f);
            }
            try (BufferedReader br = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
                return fromNdReader(br, flat);
            }
        }
        return loadByModelToken(taupFile, flat);
    }

    /**
     * Loads a WIN / hypomh structure file. P velocities at layer tops are linearly interpolated
     * (constant gradient, continuous at interfaces); S = Vp/1.73. Flat layers (no Earth flattening).
     *
     * @param file readable {@code .aob} or equivalent struct
     * @return flat-layer model
     * @throws IOException if the layout does not match the expected record counts
     */
    public static Raytrace1D loadHypomhStructFile(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            return fromHypomhVlstrReader(br);
        }
    }

    private static Raytrace1D fromHypomhVlstrReader(BufferedReader br) throws IOException {
        WinHypomhStruct w = WinHypomhStruct.read(br);
        rayLog("WIN/hypomh struct name=" + w.name + " layers=" + w.nLayers);
        List<Double> dep = new ArrayList<>();
        List<Double> vs = new ArrayList<>();
        for (int i = 0; i < w.depthKm.length; i++) {
            dep.add(w.depthKm[i]);
            vs.add(w.vs[i]);
        }
        return new Raytrace1D(dep, vs, false);
    }

    /**
     * Resolve short names (e.g. {@code prem}) to bundled resources without TauP.
     */
    private static Raytrace1D loadByModelToken(String token, boolean flat) throws IOException {
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
                return fromTvelReader(br, flat);
            }
            return fromNdReader(br, flat);
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

    private static Raytrace1D fromTvelReader(BufferedReader br, boolean flat) throws IOException {
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
        return new Raytrace1D(dep, vs, flat);
    }

    private static Raytrace1D fromNdReader(BufferedReader br, boolean flat) throws IOException {
        List<String> raw = readAllLines(br);
        if (WinHypomhStruct.looksLike(raw)) {
            return fromHypomhVlstrReader(new BufferedReader(new java.io.StringReader(String.join("\n", raw))));
        }
        List<Double> dep = new ArrayList<>();
        List<Double> vs = new ArrayList<>();
        boolean inBlockComment = false;
        for (String line : raw) {
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
        return new Raytrace1D(dep, vs, flat);
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

    // ------------------------------------------------------------------ public API

    /**
     * Shortest API: S-wave travel time (s) for the fastest ray.
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
     * Physical (unflattened) S-velocity samples of the model, fluid layers removed:
     * {@code {depthsKm, vsKmPerSec}}. A discontinuity appears as two samples at the same depth; velocity is linear
     * between consecutive samples.
     */
    public double[][] sVelocityProfile() {
        return new double[][] {physZ.clone(), physV.clone()};
    }

    /**
     * Physical (not flattened) S velocity at the given depth (km/s), linearly interpolated in the model.
     * Used for the vertical slowness at the source.
     */
    public double sourceSVelocity(double depthKm) {
        double z = Math.max(0.0, Math.min(depthKm, zMaxPhys));
        int lo = 0;
        int hi = physZ.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (physZ[mid] <= z) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double dz = physZ[hi] - physZ[lo];
        if (dz < MIN_LAYER_KM) {
            return physV[hi];
        }
        double t = (z - physZ[lo]) / dz;
        return physV[lo] + t * (physV[hi] - physV[lo]);
    }

    /**
     * Computes the fastest ray for the given horizontal distance and endpoint depths, including travel time,
     * takeoff/incident angles and the horizontal slowness.
     *
     * @param distKm        epicentral distance (km)
     * @param srcDepthKm    hypocenter depth (km)
     * @param stnDepthKm    station depth (km)
     * @return travel time and angles; see {@link RaySolution} for angle conventions
     * @throws IllegalStateException if no valid ray is found
     */
    public RaySolution solveFastestRay(double distKm, double srcDepthKm, double stnDepthKm) {
        final double rr = Math.max(0.0, distKm);
        final double zs = toModelDepth(clampPhysDepth(srcDepthKm), flattened);
        final double zr = toModelDepth(clampPhysDepth(stnDepthKm), flattened);
        final int jsrc = layerOf(zs);
        final int jstn = layerOf(zr);
        final double vSrc = vel(jsrc, zs);
        final double vStn = vel(jstn, zr);
        final boolean srcDeeper = zs >= zr;
        final double zSh = Math.min(zs, zr);
        final double zDp = Math.max(zs, zr);
        final int js = srcDeeper ? jstn : jsrc;
        final int jd = srcDeeper ? jsrc : jstn;
        rayLog("solve rr=" + rr + " zs=" + zs + " zr=" + zr + " js=" + js + " jd=" + jd);

        // maximum velocity on [zSh, zDp]
        double mBase = vel(js, zSh);
        for (int j = js; j < jd; j++) {
            mBase = Math.max(mBase, Math.max(vBot[j], vTop[j + 1]));
        }
        mBase = Math.max(mBase, vel(jd, zDp));

        final Best best = new Best();
        final double[] acc = new double[2];

        // -------- direct family
        final double dzDiff = zDp - zSh;
        if (dzDiff > 1e-9) {
            double pHi = (1.0 / mBase) * (1.0 - 1e-13);
            if (rr < 1e-9) {
                acc[0] = 0;
                acc[1] = 0;
                addRange(0.0, js, zSh, jd, zDp, acc);
                best.offer(acc[1], 0.0, srcDeeper);
            } else {
                acc[0] = 0;
                acc[1] = 0;
                addRange(pHi, js, zSh, jd, zDp, acc);
                if (acc[0] >= rr) {
                    double lo = 0.0;
                    double hi = pHi;
                    for (int it = 0; it < 200; it++) {
                        double mid = 0.5 * (lo + hi);
                        acc[0] = 0;
                        acc[1] = 0;
                        addRange(mid, js, zSh, jd, zDp, acc);
                        if (acc[0] < rr) {
                            lo = mid;
                        } else {
                            hi = mid;
                        }
                        if (hi - lo <= 1e-15 * hi) {
                            break;
                        }
                    }
                    double p = 0.5 * (lo + hi);
                    acc[0] = 0;
                    acc[1] = 0;
                    addRange(p, js, zSh, jd, zDp, acc);
                    if (Math.abs(acc[0] - rr) <= 1e-6 * Math.max(1.0, rr)) {
                        best.offer(acc[1], p, srcDeeper);
                    }
                }
            }
        } else if (rr > 0.0) {
            // same depth: a horizontal path is always a valid (upper-bound) path
            best.offer(rr / vSrc, 1.0 / vSrc, false);
        } else {
            best.offer(0.0, 0.0, false);
        }

        // -------- turning families and head waves
        if (rr >= 1e-9) {
            double mRun = mBase;
            for (int jt = jd; jt < nl; jt++) {
                double lo;
                double hi = vBot[jt];
                if (jt == jd) {
                    lo = mBase;
                } else {
                    mRun = Math.max(mRun, vBot[jt - 1]);
                    lo = Math.max(mRun, vTop[jt]);
                }
                if (jt > jd && best.t < BIG) {
                    double bound = 2.0 * (zTop[jt] - zDp) / prefMaxV[jt];
                    if (bound > best.t) {
                        continue;
                    }
                }
                double dzL = zBot[jt] - zTop[jt];
                double g = (vBot[jt] - vTop[jt]) / dzL;
                rayLog("family jt=" + jt + " lo=" + lo + " hi=" + hi + " g=" + g);

                // head wave along a (nearly) constant-velocity layer
                if (jt > jd && Math.abs(g) < 1e-6 && vTop[jt] > mRun * (1.0 + 1e-12)) {
                    double p = 1.0 / vTop[jt];
                    acc[0] = 0;
                    acc[1] = 0;
                    addLeg(p, zs, jsrc, jt, vTop[jt], acc);
                    addLeg(p, zr, jstn, jt, vTop[jt], acc);
                    if (acc[0] <= rr) {
                        best.offer(acc[1] + (rr - acc[0]) / vTop[jt], p, false);
                    }
                }

                if (!(hi > lo * (1.0 + 1e-12)) || g <= 0.0) {
                    continue;
                }
                // root of X(vt) - rr, vt = lo + (hi - lo) s^2, s in (0, 1]
                final int ns = 8;
                double sPrev = 1e-7;
                double fPrev = turnX(sPrev, lo, hi, zs, jsrc, zr, jstn, jt, acc) - rr;
                for (int k = 1; k <= ns; k++) {
                    double sCur = (double) k / ns;
                    double fCur = turnX(sCur, lo, hi, zs, jsrc, zr, jstn, jt, acc) - rr;
                    if ((fPrev <= 0.0 && fCur >= 0.0) || (fPrev >= 0.0 && fCur <= 0.0)) {
                        double a = sPrev;
                        double b = sCur;
                        double fa = fPrev;
                        for (int it = 0; it < 80; it++) {
                            double m = 0.5 * (a + b);
                            double fm = turnX(m, lo, hi, zs, jsrc, zr, jstn, jt, acc) - rr;
                            if ((fa <= 0.0) == (fm <= 0.0)) {
                                a = m;
                                fa = fm;
                            } else {
                                b = m;
                            }
                            if (b - a < 1e-14) {
                                break;
                            }
                        }
                        double s = 0.5 * (a + b);
                        double vt = lo + (hi - lo) * s * s;
                        double p = 1.0 / vt;
                        acc[0] = 0;
                        acc[1] = 0;
                        addLeg(p, zs, jsrc, jt, vt, acc);
                        addLeg(p, zr, jstn, jt, vt, acc);
                        if (Math.abs(acc[0] - rr) <= 1e-6 * Math.max(1.0, rr)) {
                            best.offer(acc[1], p, false);
                        }
                    }
                    sPrev = sCur;
                    fPrev = fCur;
                }
            }
        }

        if (!(best.t < BIG)) {
            throw new IllegalStateException("No valid ray path for distKm=" + distKm + " srcDepthKm="
                + srcDepthKm + " stnDepthKm=" + stnDepthKm);
        }
        double p = best.p;
        double sinTake = Math.min(1.0, p * vSrc);
        double sinInc = Math.min(1.0, p * vStn);
        double take;
        if (best.upgoingDirect && srcDeeper) {
            take = Math.PI - Math.asin(sinTake);
        } else {
            take = Math.asin(sinTake);
        }
        double inc = Math.asin(sinInc);
        rayLog("solution T=" + best.t + " p=" + p + " takeoff=" + take + " incident=" + inc);
        return new RaySolution(best.t, take, inc, p);
    }

    // ------------------------------------------------------------------ internals

    private static final class Best {
        double t = BIG;
        double p = 0.0;
        boolean upgoingDirect = false;

        void offer(double time, double slowness, boolean upgoing) {
            rayLog("candidate T=" + time + " p=" + slowness + " up=" + upgoing);
            if (time >= 0.0 && time < t) {
                t = time;
                p = slowness;
                upgoingDirect = upgoing;
            }
        }
    }

    private double clampPhysDepth(double z) {
        if (Double.isNaN(z)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(z, zMaxPhys - 1e-6));
    }

    /** Layer index containing model depth {@code zm} (upper layer on a boundary). */
    private int layerOf(double zm) {
        int lo = 0;
        int hi = nl - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (zBot[mid] >= zm - 1e-12) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    private double vel(int j, double zm) {
        double dz = zBot[j] - zTop[j];
        double t = (zm - zTop[j]) / dz;
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        return vTop[j] + t * (vBot[j] - vTop[j]);
    }

    /** Sum of X and T of a ray with slowness {@code p} between two depths (no turning). */
    private void addRange(double p, int jA, double zA, int jB, double zB, double[] out) {
        for (int j = jA; j <= jB; j++) {
            double za = (j == jA) ? zA : zTop[j];
            double zb = (j == jB) ? zB : zBot[j];
            double va = (j == jA) ? vel(j, zA) : vTop[j];
            double vb = (j == jB) ? vel(j, zB) : vBot[j];
            addLayer(va, vb, zb - za, p, out);
        }
    }

    /**
     * Adds the range/time of one leg: from depth {@code zStart} (layer {@code jStart}) down to the turning point
     * with velocity {@code vt} inside layer {@code jt}.
     */
    private void addLeg(double p, double zStart, int jStart, int jt, double vt, double[] out) {
        for (int j = jStart; j < jt; j++) {
            double za = (j == jStart) ? zStart : zTop[j];
            double va = (j == jStart) ? vel(j, zStart) : vTop[j];
            addLayer(va, vBot[j], zBot[j] - za, p, out);
        }
        double va = (jt == jStart) ? vel(jt, zStart) : vTop[jt];
        if (vt > va) {
            double g = (vBot[jt] - vTop[jt]) / (zBot[jt] - zTop[jt]);
            addLayer(va, vt, (vt - va) / g, p, true, out);
        }
    }

    private double turnX(double s, double lo, double hi, double zs, int jsrc, double zr, int jstn, int jt,
                         double[] acc) {
        double vt = lo + (hi - lo) * s * s;
        double p = 1.0 / vt;
        acc[0] = 0;
        acc[1] = 0;
        addLeg(p, zs, jsrc, jt, vt, acc);
        addLeg(p, zr, jstn, jt, vt, acc);
        return acc[0];
    }

    /**
     * Adds range and time of a ray with slowness {@code p} crossing a constant-gradient layer from velocity
     * {@code va} to {@code vb} over depth {@code dz} (the ray must satisfy {@code p v <= 1}).
     */
    private static void addLayer(double va, double vb, double dz, double p, double[] out) {
        addLayer(va, vb, dz, p, false, out);
    }

    /**
     * @param turningEnd if true, {@code vb = 1/p} is the turning velocity and its cosine is set to exactly zero
     *                   (avoids rounding noise of order sqrt(eps) in {@code 1 - p^2 vb^2})
     */
    private static void addLayer(double va, double vb, double dz, double p, boolean turningEnd, double[] out) {
        if (dz <= 0.0) {
            return;
        }
        double ca = Math.sqrt(Math.max(0.0, 1.0 - p * p * va * va));
        double cb = turningEnd ? 0.0 : Math.sqrt(Math.max(0.0, 1.0 - p * p * vb * vb));
        double csum = ca + cb;
        if (csum < 1e-14) {
            out[0] += BIG;
            out[1] += BIG;
            return;
        }
        double x = p * dz * (va + vb) / csum;
        double dv = vb - va;
        double t;
        if (Math.abs(dv) <= 1e-9 * Math.max(va, vb)) {
            t = dz / (0.25 * (va + vb) * csum);
        } else {
            double l1 = Math.log1p(dv / va);
            double l2 = Math.log1p((p * p * (vb * vb - va * va) / csum) / (1.0 + cb));
            t = dz * (l1 + l2) / dv;
        }
        out[0] += x;
        out[1] += t;
    }
}
