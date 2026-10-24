package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.solver.Raytrace1D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RealTravelTimeTableTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final double KM_PER_DEG = Math.toRadians(1.0) * 6371.0;

    private static double[][] read(Path f) throws IOException {
        List<String> lines = Files.readAllLines(f);
        double[][] r = new double[lines.size()][];
        for (int i = 0; i < r.length; i++) {
            String[] t = lines.get(i).trim().split("\\s+");
            assertEquals(lines.get(i), 10, t.length);
            assertEquals("P", t[8]);
            assertEquals("S", t[9]);
            r[i] = new double[8];
            for (int k = 0; k < 8; k++) {
                r[i][k] = Double.parseDouble(t[k]);
            }
        }
        return r;
    }

    @Test
    public void gridParsesAndCountsLikeReal() throws Exception {
        RealTravelTimeTable.Grid g = RealTravelTimeTable.Grid.parse("1.4/20/0.01/1");
        assertEquals(140, g.distanceCount());
        assertEquals(21, g.depthCount());
        try {
            RealTravelTimeTable.Grid.parse("1.4/20/0.01");
            org.junit.Assert.fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("trx/trh/tdx/tdh"));
        }
        try {
            RealTravelTimeTable.Grid.parse("1.4/20/0/1");
            org.junit.Assert.fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("positive"));
        }
    }

    @Test
    public void tableIsADepthMajorGridFromZero() throws Exception {
        Path f = tmp.getRoot().toPath().resolve("sub/ttdb.txt");
        RealTravelTimeTable.Grid g = new RealTravelTimeTable.Grid(0.5, 10, 0.05, 2);
        int rows = RealTravelTimeTable.write(f, "prem", g, null);
        assertEquals(10 * 6, rows);
        double[][] t = read(f);
        assertEquals(60, t.length);
        for (int j = 0; j < 6; j++) {
            for (int i = 0; i < 10; i++) {
                double[] r = t[j * 10 + i];
                assertEquals(i * 0.05, r[0], 1e-9);
                assertEquals(j * 2.0, r[1], 1e-9);
                assertTrue("S is slower than P", r[3] > r[2] || i == 0 && j == 0);
            }
        }
        assertEquals(0.0, t[0][2], 1e-6);
    }

    @Test
    public void realsNodeLookupReproducesTheRayTracer() throws Exception {
        Path f = tmp.newFile("ttdb.txt").toPath();
        RealTravelTimeTable.Grid g = new RealTravelTimeTable.Grid(0.8, 12, 0.01, 1);
        RealTravelTimeTable.write(f, "prem", g, null);
        double[][] t = read(f);
        Raytrace1D.Dual m = Raytrace1D.loadDual("prem");
        int nd = g.distanceCount();
        double[][] cases = {{0.5, 7.3}, {0.123, 5.6}, {0.7, 10.4}, {0.31, 4.2}};
        for (double[] c : cases) {
            double dist = c[0];
            double dep = c[1];
            // the same lookup as REAL.c: ig = rint(dep/tdh)*rint(trx/tdx) + rint(dist/tdx)
            int ig = (int) Math.rint(dep / g.tdh()) * nd + (int) Math.rint(dist / g.tdx());
            double[] r = t[ig];
            double tp = r[2] + (dist - r[0]) * r[4] + (dep - r[1]) * r[6];
            double ts = r[3] + (dist - r[0]) * r[5] + (dep - r[1]) * r[7];
            double refP = m.p.travelTimeSeconds(dist * KM_PER_DEG, dep, 0);
            double refS = m.s.travelTimeSeconds(dist * KM_PER_DEG, dep, 0);
            assertEquals("P at " + dist + "/" + dep, refP, tp, 0.02);
            assertEquals("S at " + dist + "/" + dep, refS, ts, 0.04);
        }
    }

    @Test
    public void errorsAreReported() throws Exception {
        Path f = tmp.getRoot().toPath().resolve("t.txt");
        RealTravelTimeTable.Grid g = new RealTravelTimeTable.Grid(0.5, 4, 0.05, 2);
        try {
            RealTravelTimeTable.write(f, "no-such-model.nd", g, null);
            org.junit.Assert.fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("velocity model"));
        }
        try {
            RealTravelTimeTable.write(f, " ", g, null);
            org.junit.Assert.fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not set"));
        }
        try {
            RealTravelTimeTable.write(f, "prem", g, () -> true);
            org.junit.Assert.fail();
        } catch (IOException e) {
            assertEquals("Cancelled.", e.getMessage());
        }
    }
}
