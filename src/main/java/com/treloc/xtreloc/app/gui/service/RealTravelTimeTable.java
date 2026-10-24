package com.treloc.xtreloc.app.gui.service;

import com.treloc.xtreloc.solver.Raytrace1D;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Writes REAL's travel-time table ({@code ttdb.txt}) from a velocity model of the solvers
 * ({@link Raytrace1D}: bundled {@code .nd}/{@code .tvel} models, or a model file), so that REAL
 * associates with the same velocity structure as the relocation.
 *
 * <p>REAL looks the table up as {@code T = t + (dist - d)*p + (depth - z)*q} at the grid node
 * {@code (d, z)} nearest to the trial source ({@code ig = rint(depth/tdh)*rint(trx/tdx) + rint(dist/tdx)}),
 * so the file must be a regular grid, depth-major, distance from 0 with {@code rint(trx/tdx)} nodes per depth,
 * depth from 0 in steps of {@code tdh}. Each row is
 * {@code dist[deg] depth[km] tP tS pP[s/deg] pS[s/deg] qP[s/km] qS[s/km] P S}, with
 * {@code p = dT/d(dist)} and {@code q = dT/d(source depth)} (negative for a ray leaving downward).</p>
 */
public final class RealTravelTimeTable {

    private RealTravelTimeTable() {}

    /** Earth radius of the ray tracer ({@code HypoUtils}); one degree of arc is this many km / 57.2958. */
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double KM_PER_DEG = Math.toRadians(1.0) * EARTH_RADIUS_KM;

    /** The range and interval of the table: {@code -G trx/trh/tdx/tdh} of REAL. */
    public record Grid(double trx, double trh, double tdx, double tdh) {

        /** Number of distance nodes per depth ({@code rint(trx/tdx)}, as REAL computes it). */
        public int distanceCount() {
            return (int) Math.rint(trx / tdx);
        }

        /** Number of depth nodes (0, tdh, ..., trh). */
        public int depthCount() {
            return (int) Math.rint(trh / tdh) + 1;
        }

        /** Parses {@code trx/trh/tdx/tdh}. */
        public static Grid parse(String g) throws IOException {
            String[] t = g == null ? new String[0] : g.trim().split("/");
            if (t.length < 4) {
                throw new IOException("The -G setting must be trx/trh/tdx/tdh (deg/km/deg/km): " + g);
            }
            try {
                Grid grid = new Grid(Double.parseDouble(t[0]), Double.parseDouble(t[1]),
                    Double.parseDouble(t[2]), Double.parseDouble(t[3]));
                if (!(grid.trx > 0 && grid.trh >= 0 && grid.tdx > 0 && grid.tdh > 0)
                    || grid.distanceCount() < 2) {
                    throw new IOException("The -G ranges and intervals must be positive: " + g);
                }
                return grid;
            } catch (NumberFormatException e) {
                throw new IOException("The -G setting must be numbers: " + g);
            }
        }
    }

    /**
     * Writes the table.
     *
     * @param velocityModel a bundled model name ({@code prem}, {@code ak135.tvel}, ...) or a model file
     * @param cancelled     polled between depths; when it returns true the write stops with an exception
     * @return the number of rows written
     */
    public static int write(Path file, String velocityModel, Grid grid, BooleanSupplier cancelled)
        throws IOException {
        if (velocityModel == null || velocityModel.isBlank()) {
            throw new IOException("The velocity model is not set.");
        }
        Raytrace1D.Dual model;
        try {
            model = Raytrace1D.loadDual(velocityModel.trim());
        } catch (Exception e) {
            throw new IOException("Cannot read the velocity model " + velocityModel + ": " + e.getMessage(), e);
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        int rows = 0;
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (int j = 0; j < grid.depthCount(); j++) {
                double depth = j * grid.tdh();
                for (int i = 0; i < grid.distanceCount(); i++) {
                    if (cancelled != null && cancelled.getAsBoolean()) {
                        throw new IOException("Cancelled.");
                    }
                    double dist = i * grid.tdx();
                    double[] p = node(model.p, dist, depth);
                    double[] s = node(model.s, dist, depth);
                    w.write(String.format(Locale.ROOT, "%.5f %.5f %.5f %.5f %.6f %.6f %.6f %.6f P S%n",
                        dist, depth, p[0], s[0], p[1], s[1], p[2], s[2]));
                    rows++;
                }
            }
        }
        return rows;
    }

    /** Travel time (s) of one phase between a surface station and a source. */
    static double time(Raytrace1D ray, double distDeg, double depthKm) {
        try {
            return ray.travelTimeSeconds(distDeg * KM_PER_DEG, depthKm, 0.0);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    /** {time, dT/d(dist) [s/deg], dT/d(depth) [s/km]} at one node. */
    private static double[] node(Raytrace1D ray, double distDeg, double depthKm) throws IOException {
        double t = time(ray, distDeg, depthKm);
        if (!Double.isFinite(t)) {
            throw new IOException(String.format(Locale.ROOT,
                "No ray in the velocity model for distance %.3f deg, depth %.1f km.", distDeg, depthKm));
        }
        double p;
        try {
            Raytrace1D.RaySolution sol = ray.solveFastestRay(distDeg * KM_PER_DEG, depthKm, 0.0);
            p = sol.horizontalSlowness * KM_PER_DEG;
        } catch (RuntimeException e) {
            p = Double.NaN;
        }
        if (!Double.isFinite(p)) {
            double h = 1e-3;
            p = (time(ray, distDeg + h, depthKm) - t) / h;
        }
        double hz = 0.05;
        double lo = Math.max(0.0, depthKm - hz);
        double hi = depthKm + hz;
        double q = (time(ray, distDeg, hi) - time(ray, distDeg, lo)) / (hi - lo);
        if (!Double.isFinite(p) || !Double.isFinite(q)) {
            throw new IOException(String.format(Locale.ROOT,
                "The ray derivatives are undefined at distance %.3f deg, depth %.1f km.", distDeg, depthKm));
        }
        return new double[] {t, p, q};
    }
}
