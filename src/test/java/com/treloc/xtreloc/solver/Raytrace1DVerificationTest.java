package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelLoader;
import edu.sc.seis.TauP.TauP_Time;

/**
 * Verification of {@link Raytrace1D} in three stages, writing a report to
 * {@code build/reports/raytrace1d_verification.md}:
 * <ol>
 *   <li>analytic solutions (homogeneous half-space, constant velocity gradient),</li>
 *   <li>TauP (spherical) vs {@link Raytrace1D} on the raw model (what the application does),</li>
 *   <li>TauP vs {@link Raytrace1D} on the <em>earth-flattened</em> model, which isolates algorithmic error
 *       from the flat-earth approximation.</li>
 * </ol>
 */
public class Raytrace1DVerificationTest {

    private static final double R = 6371.0;
    private static final Path REPORT = Paths.get("build", "reports", "raytrace1d_verification.md");
    private static final StringBuilder OUT = new StringBuilder();

    private static void line(String s) {
        OUT.append(s).append('\n');
        System.out.println(s);
    }

    private static void flush() throws IOException {
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, OUT.toString(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ analytic

    @Test
    public void verifyAll() throws Exception {
        OUT.setLength(0);
        FAILS.clear();
        analytic_homogeneousAndConstantGradient();
        stepModelVsFermat();
        taupComparison_rawAndFlattened();
        flush();
        // Strict by default. Opt out for exploratory runs with -Dxtreloc.strictRaytraceVerification=false
        String strict = System.getProperty("xtreloc.strictRaytraceVerification", "true");
        if (Boolean.parseBoolean(strict)) {
            assertTrue("Raytrace1D verification failures: " + FAILS + " (see " + REPORT + ")", FAILS.isEmpty());
        } else if (!FAILS.isEmpty()) {
            System.out.println("[Raytrace1DVerification] failures (non-strict): " + FAILS);
        }
    }

    private static final List<String> FAILS = new ArrayList<>();

    private void analytic_homogeneousAndConstantGradient() throws Exception {
        Path tmp = Files.createTempDirectory("rt1d-verif");
        tmp.toFile().deleteOnExit();

        // --- homogeneous half-space, Vs = 3.5
        Path homo = tmp.resolve("homo.nd");
        Files.writeString(homo, "0.0 6.0 3.5 2.7\n700.0 6.0 3.5 2.7\n");
        Raytrace1D rtHomo = Raytrace1D.load(homo.toString(), false);

        line("# Raytrace1D verification\n");
        line("## 1a. Homogeneous half-space (Vs = 3.5 km/s), analytic t = hypot(D, dz) / Vs\n");
        line("| D km | zs km | zr km | t_analytic s | t_1d s | err s | rel % |");
        line("|---:|---:|---:|---:|---:|---:|---:|");
        double worstHomo = 0.0;
        for (double d : new double[] {1, 5, 10, 30, 60, 100, 200}) {
            for (double zs : new double[] {0.5, 5, 20, 50}) {
                double zr = 0.0;
                double ta = Math.hypot(d, zs - zr) / 3.5;
                double t1;
                try {
                    t1 = rtHomo.solveFastestRay(d, zs, zr).travelTimeSeconds;
                } catch (RuntimeException e) {
                    line(String.format(Locale.US, "| %.1f | %.1f | %.1f | %.5f | EXCEPTION: %s | | |",
                        d, zs, zr, ta, e.getMessage()));
                    FAILS.add("homogeneous: exception at D=" + d + " zs=" + zs);
                    continue;
                }
                double rel = 100.0 * (t1 - ta) / ta;
                worstHomo = Math.max(worstHomo, Math.abs(rel));
                line(String.format(Locale.US, "| %.1f | %.1f | %.1f | %.5f | %.5f | %+.5f | %+.4f |",
                    d, zs, zr, ta, t1, t1 - ta, rel));
            }
        }
        line(String.format(Locale.US, "\n**worst |rel| homogeneous = %.4f %%**\n", worstHomo));

        // --- constant gradient v = 3 + 0.03 z
        double v0 = 3.0;
        double g = 0.03;
        StringBuilder sb = new StringBuilder();
        for (int z = 0; z <= 300; z += 10) {
            double v = v0 + g * z;
            sb.append(String.format(Locale.US, "%d.0 %.4f %.4f 2.7%n", z, v, v));
        }
        Path grad = tmp.resolve("grad.nd");
        Files.writeString(grad, sb.toString());
        Raytrace1D rtGrad = Raytrace1D.load(grad.toString(), false);

        line("## 1b. Constant gradient v = 3 + 0.03 z, analytic T = acosh(1 + g^2 d^2 / (2 v1 v2)) / g\n");
        line("| D km | zs km | zr km | t_analytic s | t_1d s | err s | rel % |");
        line("|---:|---:|---:|---:|---:|---:|---:|");
        double worstGrad = 0.0;
        for (double d : new double[] {1, 5, 10, 30, 60, 100, 150}) {
            for (double zs : new double[] {0.5, 5, 20, 50}) {
                double zr = 0.0;
                double v1 = v0 + g * zs;
                double v2 = v0 + g * zr;
                double dd = d * d + (zs - zr) * (zs - zr);
                double ta = Math.log(1.0 + g * g * dd / (2.0 * v1 * v2)
                    + Math.sqrt(Math.pow(1.0 + g * g * dd / (2.0 * v1 * v2), 2) - 1.0)) / g;
                double t1;
                try {
                    t1 = rtGrad.solveFastestRay(d, zs, zr).travelTimeSeconds;
                } catch (RuntimeException e) {
                    line(String.format(Locale.US, "| %.1f | %.1f | %.1f | %.5f | EXCEPTION: %s | | |",
                        d, zs, zr, ta, e.getMessage()));
                    FAILS.add("gradient: exception at D=" + d + " zs=" + zs);
                    continue;
                }
                double rel = 100.0 * (t1 - ta) / ta;
                worstGrad = Math.max(worstGrad, Math.abs(rel));
                line(String.format(Locale.US, "| %.1f | %.1f | %.1f | %.5f | %.5f | %+.5f | %+.4f |",
                    d, zs, zr, ta, t1, t1 - ta, rel));
            }
        }
        line(String.format(Locale.US, "\n**worst |rel| constant gradient = %.4f %%**\n", worstGrad));
        flush();

        if (worstHomo >= 0.1) {
            FAILS.add("homogeneous worst rel % = " + worstHomo);
        }
        if (worstGrad >= 0.1) {
            FAILS.add("constant gradient worst rel % = " + worstGrad);
        }
    }

    // ------------------------------------------------------------------ two constant-velocity layers (Fermat reference)

    /** Fastest S time in a two-layer flat model (v1 above depth h, v2 below), station at the surface. */
    private static double fermatTwoLayer(double v1, double v2, double h, double zs, double dist) {
        if (zs >= h) {
            // source in lower layer: refracted path, convex in crossing point x -> ternary search
            double lo = 0.0;
            double hi = dist;
            for (int i = 0; i < 200; i++) {
                double m1 = lo + (hi - lo) / 3.0;
                double m2 = hi - (hi - lo) / 3.0;
                if (f2(v1, v2, h, zs, dist, m1) < f2(v1, v2, h, zs, dist, m2)) {
                    hi = m2;
                } else {
                    lo = m1;
                }
            }
            return f2(v1, v2, h, zs, dist, 0.5 * (lo + hi));
        }
        double best = Math.hypot(dist, zs) / v1;
        if (v2 > v1) {
            double cos = Math.sqrt(1.0 - (v1 / v2) * (v1 / v2));
            double tan = (v1 / v2) / cos;
            double xCrit = ((h - zs) + h) * tan;
            if (dist >= xCrit) {
                double head = dist / v2 + ((h - zs) + h) * cos / v1;
                best = Math.min(best, head);
            }
        }
        return best;
    }

    private static double f2(double v1, double v2, double h, double zs, double dist, double x) {
        return Math.hypot(x, zs - h) / v2 + Math.hypot(dist - x, h) / v1;
    }

    private void stepModelVsFermat() throws Exception {
        Path tmp = Files.createTempDirectory("rt1d-step");
        tmp.toFile().deleteOnExit();
        double v1 = 3.5;
        double v2 = 4.5;
        double h = 20.0;
        Path p = tmp.resolve("step.nd");
        Files.writeString(p, String.format(Locale.US,
            "0.0 6 %.3f 2.7%n%.1f 6 %.3f 2.7%n%.1f 8 %.3f 3.3%n700.0 8 %.3f 3.3%n",
            v1, h, v1, h, v2, v2));
        Raytrace1D rt = Raytrace1D.load(p.toString(), false);

        line("## 1c. Two constant-velocity layers (v1 = 3.5 above 20 km, v2 = 4.5 below), Fermat reference\n");
        line("Reference = minimum over direct/refracted/head-wave paths (independent closed form / 1-D minimisation). "
            + "`bound` = hypot(D, zs) / vmax is a hard physical lower bound; `VIOLATION` means t_1d < bound.\n");
        line("| D km | zs km | t_ref s | t_1d s | err s | rel % | note |");
        line("|---:|---:|---:|---:|---:|---:|:--|");
        int n = 0;
        int bad = 0;
        int violations = 0;
        int exceptions = 0;
        double worstOk = 0.0;
        for (double d : new double[] {2, 5, 10, 20, 30, 50, 80, 100, 150, 200}) {
            for (double zs : new double[] {5, 15, 19.9, 25, 33, 50}) {
                n++;
                double ref = fermatTwoLayer(v1, v2, h, zs, d);
                double bound = Math.hypot(d, zs) / v2;
                double t1;
                try {
                    t1 = rt.solveFastestRay(d, zs, 0.0).travelTimeSeconds;
                } catch (RuntimeException e) {
                    exceptions++;
                    line(String.format(Locale.US, "| %.0f | %.1f | %.4f | EXCEPTION | | | %s |", d, zs, ref, e.getMessage()));
                    continue;
                }
                double rel = 100.0 * (t1 - ref) / ref;
                String note = "";
                if (t1 < bound - 1e-6) {
                    note = "VIOLATION (t < lower bound " + String.format(Locale.US, "%.4f", bound) + ")";
                    violations++;
                }
                if (Math.abs(rel) > 0.5) {
                    bad++;
                } else {
                    worstOk = Math.max(worstOk, Math.abs(rel));
                }
                line(String.format(Locale.US, "| %.0f | %.1f | %.4f | %.4f | %+.4f | %+.3f | %s |",
                    d, zs, ref, t1, t1 - ref, rel, note));
            }
        }
        line(String.format(Locale.US,
            "%n**%d cases: %d with |rel| > 0.5 %%, %d physically impossible (t < lower bound), %d exceptions; "
                + "worst |rel| among the good ones = %.4f %%**%n", n, bad, violations, exceptions, worstOk));
        if (bad > 0 || violations > 0 || exceptions > 0) {
            FAILS.add("two-layer step model: " + bad + " bad, " + violations + " impossible, " + exceptions + " exceptions of " + n);
        }
    }

    // ------------------------------------------------------------------ TauP comparison

    private static final class Model {
        final String name;
        final List<double[]> rows = new ArrayList<>(); // depth, vs

        Model(String name) {
            this.name = name;
        }
    }

    private static Model readModel(String resource) throws IOException {
        Model m = new Model(resource);
        boolean tvel = resource.endsWith(".tvel");
        try (InputStream in = Raytrace1DVerificationTest.class.getClassLoader()
                .getResourceAsStream("velocity-models/" + resource);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String ln;
            int n = 0;
            while ((ln = br.readLine()) != null) {
                n++;
                if (tvel && n <= 2) {
                    continue;
                }
                int c = ln.indexOf('#');
                if (c >= 0) {
                    ln = ln.substring(0, c);
                }
                String[] p = ln.trim().split("\\s+");
                if (p.length < 3) {
                    continue;
                }
                try {
                    m.rows.add(new double[] {Double.parseDouble(p[0]), Double.parseDouble(p[2])});
                } catch (NumberFormatException e) {
                    // discontinuity label
                }
            }
        }
        return m;
    }

    /** Linear-in-depth interpolation within layers; duplicate depths are discontinuities. */
    private static List<double[]> resample(Model m, double maxDepth, double step) {
        List<double[]> out = new ArrayList<>();
        List<double[]> r = m.rows;
        for (int i = 0; i + 1 < r.size(); i++) {
            double z0 = r.get(i)[0];
            double z1 = r.get(i + 1)[0];
            double v0 = r.get(i)[1];
            double v1 = r.get(i + 1)[1];
            if (z0 >= maxDepth) {
                break;
            }
            out.add(new double[] {z0, v0});
            if (z1 > z0) {
                double zEnd = Math.min(z1, maxDepth);
                for (double z = z0 + step; z < zEnd - 1e-9; z += step) {
                    out.add(new double[] {z, v0 + (v1 - v0) * (z - z0) / (z1 - z0)});
                }
                if (z1 > maxDepth) {
                    out.add(new double[] {maxDepth, v0 + (v1 - v0) * (maxDepth - z0) / (z1 - z0)});
                    break;
                }
            }
        }
        return out;
    }

    private static double flatZ(double z) {
        return -R * Math.log((R - z) / R);
    }

    private static Path writeFlattened(Model m, Path dir) throws IOException {
        List<double[]> rs = resample(m, 1200.0, 2.0);
        StringBuilder sb = new StringBuilder();
        for (double[] row : rs) {
            double z = row[0];
            double v = row[1] * R / (R - z);
            sb.append(String.format(Locale.US, "%.6f %.6f %.6f 3.0%n", flatZ(z), v, v));
        }
        Path p = dir.resolve(m.name.replace(".tvel", "").replace(".nd", "") + "_flattened.nd");
        Files.writeString(p, sb.toString());
        return p;
    }

    private static Path copyResource(String resource, Path dir) throws IOException {
        Path p = dir.resolve(resource);
        try (InputStream in = Raytrace1DVerificationTest.class.getClassLoader()
                .getResourceAsStream("velocity-models/" + resource)) {
            Files.copy(in, p, StandardCopyOption.REPLACE_EXISTING);
        }
        return p;
    }

    private static double taupTime(TauP_Time taup, double distKm, double zs, double zr) {
        try {
            taup.depthCorrect(zs, zr);
            taup.setSourceDepth(zs);
            taup.calculate(Math.toDegrees(distKm / R));
            List<Arrival> arr = taup.getArrivals();
            if (arr == null || arr.isEmpty()) {
                return Double.NaN;
            }
            double best = Double.POSITIVE_INFINITY;
            for (Arrival a : arr) {
                best = Math.min(best, a.getTime());
            }
            return best;
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private static double rtTime(Raytrace1D rt, double distKm, double zs, double zr) {
        try {
            return rt.solveFastestRay(distKm, zs, zr).travelTimeSeconds;
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private void taupComparison_rawAndFlattened() throws Exception {
        Path tmp = Files.createTempDirectory("rt1d-taup");
        tmp.toFile().deleteOnExit();

        String[] models = {"prem.nd", "iasp91.tvel"};
        double[] dists = {10, 30, 80, 200, 500};
        double[] depths = {15, 33};
        double zr = 0.0;

        line("## 2/3. TauP (spherical, `tts,S`) vs Raytrace1D\n");
        line("`flat` = Raytrace1D on the raw model (application behaviour, built-in Earth flattening). "
            + "`flattened` = Raytrace1D on the earth-flattened model (z_f = -R ln((R-z)/R), v_f = v R/(R-z)); "
            + "its residual vs TauP is the algorithmic error of Raytrace1D.\n");

        double worstFlatReg = 0, worstFlattenedReg = 0, worstFlatFar = 0, worstFlattenedFar = 0;

        for (String name : models) {
            Model m = readModel(name);
            Path raw = copyResource(name, tmp);
            Path flat = writeFlattened(m, tmp);

            TauP_Time taup = new TauP_Time();
            taup.setTauModel(TauModelLoader.load(raw.toAbsolutePath().toString()));
            taup.clearPhaseNames();
            taup.clearArrivals();
            taup.clearPhases();
            taup.parsePhaseList("tts,S");

            Raytrace1D rtRaw = Raytrace1D.load(raw.toString(), true);
            Raytrace1D rtFlat = Raytrace1D.load(flat.toString(), false);

            line("### " + name + "\n");
            line("| D km | zs km | t_taup s | t_flat s | flat-taup s | flat rel % | t_flattened s | flattened-taup s | flattened rel % |");
            line("|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
            for (double d : dists) {
                for (double zs : depths) {
                    double tt = taupTime(taup, d, zs, zr);
                    double tr = rtTime(rtRaw, d, zs, zr);
                    // flattened model: depths are in flattened coordinates; surface distance is unchanged
                    double tf = rtTime(rtFlat, d, flatZ(zs), flatZ(zr));
                    double relR = 100.0 * (tr - tt) / tt;
                    double relF = 100.0 * (tf - tt) / tt;
                    if (Double.isFinite(relR) && Double.isFinite(relF)) {
                        if (d <= 120) {
                            worstFlatReg = Math.max(worstFlatReg, Math.abs(relR));
                            worstFlattenedReg = Math.max(worstFlattenedReg, Math.abs(relF));
                        } else {
                            worstFlatFar = Math.max(worstFlatFar, Math.abs(relR));
                            worstFlattenedFar = Math.max(worstFlattenedFar, Math.abs(relF));
                        }
                    }
                    line(String.format(Locale.US, "| %.0f | %.0f | %.4f | %.4f | %+.4f | %+.3f | %.4f | %+.4f | %+.3f |",
                        d, zs, tt, tr, tr - tt, relR, tf, tf - tt, relF));
                }
            }
            line("");
        }

        line("## Summary (worst |relative error| vs TauP)\n");
        line(String.format(Locale.US, "- D <= 120 km: flat (app) = %.3f %%, flattened = %.3f %%", worstFlatReg, worstFlattenedReg));
        line(String.format(Locale.US, "- D  > 120 km: flat (app) = %.3f %%, flattened = %.3f %%", worstFlatFar, worstFlattenedFar));
        flush();

        if (worstFlattenedReg >= 1.0) {
            FAILS.add("flattened-vs-TauP regional worst rel % = " + worstFlattenedReg);
        }
    }
}
