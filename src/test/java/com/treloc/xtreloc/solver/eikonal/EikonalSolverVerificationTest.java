package com.treloc.xtreloc.solver.eikonal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.solver.Raytrace1D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Accuracy checks of the factored fast-sweeping eikonal solver. Writes a summary to
 * {@code build/reports/eikonal3d_verification.md}.
 */
public class EikonalSolverVerificationTest {

    private static final List<String> REPORT = new ArrayList<>();

    private static void line(String s) {
        REPORT.add(s);
        System.out.println(s);
    }

    @AfterClass
    public static void writeReport() throws IOException {
        Path out = Paths.get("build", "reports", "eikonal3d_verification.md");
        Files.createDirectories(out.getParent());
        Files.write(out, REPORT, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ homogeneous

    @Test
    public void homogeneousMedium_matchesDistanceOverVelocity() {
        final double v = 3.5;
        EikonalGrid3D g = EikonalGrid3D.cartesian(-20, -20, 41, 41, 41, 1.0, false, (x, y, z) -> v);
        double sx = 3.3, sy = -4.1, sz = 7.7; // off-node source
        TravelTimeField3D f = g.solve(sx, sy, sz);
        Random rnd = new Random(1);
        double worst = 0;
        for (int n = 0; n < 500; n++) {
            double x = -19 + 38 * rnd.nextDouble();
            double y = -19 + 38 * rnd.nextDouble();
            double z = 1 + 38 * rnd.nextDouble();
            double ex = Math.sqrt((x - sx) * (x - sx) + (y - sy) * (y - sy) + (z - sz) * (z - sz)) / v;
            double t = f.time(x, y, z);
            worst = Math.max(worst, Math.abs(t - ex) / ex);
        }
        line("- homogeneous (v=3.5, h=1 km, off-node source): worst relative error = "
            + String.format("%.2e", worst));
        assertTrue("homogeneous error " + worst, worst < 1e-4);
        assertEquals(0.0, f.time(sx, sy, sz), 1e-9);
    }

    // ------------------------------------------------------------------ linear gradient

    @Test
    public void linearGradient_matchesAnalyticSolution() {
        final double v0 = 3.0, grad = 0.03;
        for (double h : new double[] {2.0}) {
            int n = (int) Math.round(60 / h) + 1;
            int nz = (int) Math.round(40 / h) + 1;
            EikonalGrid3D g = EikonalGrid3D.cartesian(-30, -30, n, n, nz, h, false, (x, y, z) -> v0 + grad * z);
            double sz = 10;
            TravelTimeField3D f = g.solve(0, 0, sz);
            Random rnd = new Random(2);
            double worst = 0;
            double sum = 0;
            int cnt = 0;
            for (int i = 0; i < 400; i++) {
                double x = -25 + 50 * rnd.nextDouble();
                double y = -25 + 50 * rnd.nextDouble();
                double z = 2 + 30 * rnd.nextDouble();
                double r2 = x * x + y * y + (z - sz) * (z - sz);
                if (r2 < 4.0) {
                    continue;
                }
                double va = v0 + grad * sz;
                double vb = v0 + grad * z;
                double ex = acosh(1 + grad * grad * r2 / (2 * va * vb)) / grad;
                double t = f.time(x, y, z);
                double rel = Math.abs(t - ex) / ex;
                worst = Math.max(worst, rel);
                sum += rel;
                cnt++;
            }
            line(String.format("- linear gradient v=3+0.03z, h=%.0f km: worst = %.3f %%, mean = %.3f %%",
                h, worst * 100, sum / cnt * 100));
            assertTrue("gradient model error " + worst + " at h=" + h, worst < (h > 1.5 ? 0.008 : 0.005));
        }
    }

    private static double acosh(double x) {
        return Math.log(x + Math.sqrt(x * x - 1));
    }

    // ------------------------------------------------------------------ PREM vs Raytrace1D

    @Test
    public void premLayered_matchesRaytrace1D() throws Exception {
        Raytrace1D rt = Raytrace1D.load("prem.nd", true);
        final double h = 2.0;
        int nx = 81; // x in [-80, 80]
        int ny = 5;
        int nz = 51; // model depth up to 100 km
        EikonalGrid3D g = EikonalGrid3D.cartesian(-80, -4, nx, ny, nz, h, true,
            (x, y, z) -> rt.sourceSVelocity(z));
        double[] depths = {10, 20, 33};
        double[] dists = {10, 20, 50, 80};
        line("");
        line("### PREM (S), eikonal (h=2 km, flattened) vs Raytrace1D (receiver depth 0)\n");
        line("| zs km | D km | t_ray s | t_eik s | rel % |");
        line("|---:|---:|---:|---:|---:|");
        double worst = 0;
        double worstFar = 0;
        for (double zs : depths) {
            TravelTimeField3D f = g.solve(-70, 0, zs);
            for (double d : dists) {
                if (-70 + d > 79) {
                    continue;
                }
                double tr = rt.travelTimeSeconds(d, zs, 0.0);
                double te = f.time(-70 + d, 0, 0.0);
                double rel = (te - tr) / tr;
                line(String.format("| %.0f | %.0f | %.3f | %.3f | %+.3f |", zs, d, tr, te, rel * 100));
                if (d >= 20) {
                    worstFar = Math.max(worstFar, Math.abs(rel));
                }
                worst = Math.max(worst, Math.abs(rel));
            }
        }
        line(String.format("%n- PREM worst |rel| = %.3f %% (all), %.3f %% (D >= 20 km)", worst * 100, worstFar * 100));
        // PREM has first-order discontinuities (15, 24.4, 80 km); a grid with h = 1 km smears them, which mainly
        // affects head waves near the critical distance. Smooth models are checked far more tightly above.
        assertTrue("PREM worst error " + worst, worst < 0.04);
    }

    // ------------------------------------------------------------------ reciprocity in 3-D

    @Test
    public void reciprocity_inHeterogeneousModel() {
        EikonalGrid3D.VelocityFunction vf = (x, y, z) ->
            (3.4 + 0.03 * z) * (1.0 + 0.12 * Math.sin(x / 7.0) * Math.cos(y / 9.0) * Math.cos(z / 6.0));
        EikonalGrid3D g = EikonalGrid3D.cartesian(-30, -30, 31, 31, 21, 2.0, false, vf);
        double[][] pts = {{-12, 8, 6}, {15, -10, 18}, {5, 14, 3}, {-18, -16, 25}, {20, 18, 12}};
        TravelTimeField3D[] fields = new TravelTimeField3D[pts.length];
        for (int a = 0; a < pts.length; a++) {
            fields[a] = g.solve(pts[a][0], pts[a][1], pts[a][2]);
        }
        double worst = 0;
        for (int a = 0; a < pts.length; a++) {
            for (int b = a + 1; b < pts.length; b++) {
                double tab = fields[a].time(pts[b][0], pts[b][1], pts[b][2]);
                double tba = fields[b].time(pts[a][0], pts[a][1], pts[a][2]);
                worst = Math.max(worst, Math.abs(tab - tba) / (0.5 * (tab + tba)));
            }
        }
        line(String.format("- reciprocity (random 3-D model, 12%% perturbation, h=2 km): worst = %.3f %%", worst * 100));
        assertTrue("reciprocity " + worst, worst < 0.005);
    }

    // ------------------------------------------------------------------ gradient

    @Test
    public void gradient_matchesFiniteDifferences() {
        EikonalGrid3D.VelocityFunction vf = (x, y, z) -> (3.4 + 0.03 * z) * (1.0 + 0.08 * Math.sin(x / 9.0));
        for (boolean flat : new boolean[] {false}) {
            EikonalGrid3D g = EikonalGrid3D.cartesian(-30, -30, 31, 31, 21, 2.0, flat, vf);
            TravelTimeField3D f = g.solve(2.2, -3.7, 9.4);
            double[][] pts = {{10.3, 7.1, 14.2}, {-12.6, 5.2, 4.8}, {4.4, -15.5, 20.3}};
            double worst = 0;
            double[] o = new double[4];
            for (double[] p : pts) {
                assertTrue(f.timeAndGradient(p[0], p[1], p[2], o));
                double d = 0.05;
                double[] fd = {
                    (f.time(p[0] + d, p[1], p[2]) - f.time(p[0] - d, p[1], p[2])) / (2 * d),
                    (f.time(p[0], p[1] + d, p[2]) - f.time(p[0], p[1] - d, p[2])) / (2 * d),
                    (f.time(p[0], p[1], p[2] + d) - f.time(p[0], p[1], p[2] - d)) / (2 * d)};
                for (int c = 0; c < 3; c++) {
                    worst = Math.max(worst, Math.abs(o[c + 1] - fd[c]) / Math.max(Math.abs(fd[c]), 0.05));
                }
                // eikonal property: |grad T| = local slowness (physical depth gradient, no flattening scaling issue
                // for the unflattened grid)
                if (!flat) {
                    double gn = Math.sqrt(o[1] * o[1] + o[2] * o[2] + o[3] * o[3]);
                    double s = 1.0 / vf.vs(p[0], p[1], p[2]);
                    assertEquals("|grad T| vs slowness", s, gn, 0.03 * s);
                }
            }
            line(String.format("- gradient vs finite difference (flatten=%b): worst = %.3f %%", flat, worst * 100));
            assertTrue("gradient fd mismatch " + worst, worst < 0.02);
        }
    }

    // ------------------------------------------------------------------ geographic pipeline

    @Test
    public void geographicPipeline_matchesRaytrace1D() throws Exception {
        Raytrace1D rt = Raytrace1D.load("prem.nd", true);
        // CSV-like 1-D layered model on a lon/lat grid around (35N, 135E)
        double[] lons = {134.0, 134.5, 135.0, 135.5, 136.0};
        double[] lats = {34.0, 34.5, 35.0, 35.5, 36.0};
        double[] deps = new double[16];
        for (int k = 0; k < deps.length; k++) {
            deps[k] = 2.0 * k;
        }
        float[] vs = new float[lons.length * lats.length * deps.length];
        for (int k = 0; k < deps.length; k++) {
            for (int j = 0; j < lats.length; j++) {
                for (int i = 0; i < lons.length; i++) {
                    vs[(k * lats.length + j) * lons.length + i] = (float) rt.sourceSVelocity(deps[k]);
                }
            }
        }
        VelocityModel3D m = VelocityModel3D.fromGrid(lons, lats, deps, vs);
        EikonalGrid3D g = EikonalGrid3D.build(m, new EikonalGrid3D.Spec(35.0, 135.0, 35, 35, 30, 2.0, true));
        double[][] stn = {{35.15, 135.2, 0}, {35.05, 135.0, 0}};
        double[][] hyp = {{35.0, 135.0, 10}, {35.1, 135.1, 20}};
        double worst = 0;
        line("");
        line("### geographic pipeline (1-D PREM stored as 3-D grid, h=2 km)\n");
        for (double[] s : stn) {
            TravelTimeField3D f = g.solveLatLon(s[0], s[1], s[2]);
            for (double[] h : hyp) {
                double dist = net.sf.geographiclib.Geodesic.WGS84.Inverse(s[0], s[1], h[0], h[1]).s12 / 1000.0;
                double tr = rt.travelTimeSeconds(dist, h[2], s[2]);
                double te = f.timeLatLon(h[0], h[1], h[2]);
                double rel = Math.abs(te - tr) / tr;
                worst = Math.max(worst, rel);
                line(String.format("- D=%.1f km zs=%.0f: ray %.3f s, eik %.3f s (%.3f %%)", dist, h[2], tr, te, rel * 100));
            }
        }
        assertTrue("geographic pipeline error " + worst, worst < 0.03);
    }
}
