package com.treloc.xtreloc.solver.eikonal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class VelocityModel3DTest {

    private static Path write(String text) throws IOException {
        Path p = Files.createTempFile("vm3d", ".csv");
        p.toFile().deleteOnExit();
        Files.write(p, text.getBytes(StandardCharsets.UTF_8));
        return p;
    }

    @Test
    public void loadsCsv_withHeaderAndComments_andInterpolates() throws Exception {
        StringBuilder sb = new StringBuilder("# test model\nlon,lat,depth,vs\n");
        // v = 3 + 0.5 (lon-130) + 0.25 (lat-33) + 0.1 depth  (linear => trilinear is exact)
        for (int k = 0; k < 3; k++) {
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    double lon = 130 + i;
                    double lat = 33 + j;
                    double dep = 10 * k;
                    sb.append(lon).append(',').append(lat).append(',').append(dep).append(',')
                      .append(3 + 0.5 * i + 0.25 * j + 0.1 * dep).append('\n');
                }
            }
        }
        VelocityModel3D m = VelocityModel3D.fromCsv(write(sb.toString()));
        assertEquals(3, m.nLon());
        assertEquals(3, m.nLat());
        assertEquals(3, m.nDepth());
        assertEquals(3.0, m.vs(130, 33, 0), 1e-6);
        assertEquals(3 + 0.5 * 2 + 0.25 * 2 + 2.0, m.vs(132, 35, 20), 1e-6);
        assertEquals(3 + 0.5 * 0.3 + 0.25 * 1.7 + 0.1 * 13.0, m.vs(130.3, 34.7, 13.0), 1e-5);
        // outside: nearest edge value
        assertEquals(m.vs(130, 33, 0), m.vs(100, 0, -5), 1e-9);
        assertEquals(m.vs(132, 35, 20), m.vs(200, 80, 500), 1e-9);
    }

    @Test
    public void acceptsShuffledRowsAndWhitespaceSeparators() throws Exception {
        String txt = "130 33 0 3.0\n131 33 0 3.1\n130 34 0 3.2\n131 34 0 3.3\n"
            + "131 34 5 3.7\n130 33 5 3.4\n131 33 5 3.5\n130 34 5 3.6\n";
        VelocityModel3D m = VelocityModel3D.fromCsv(write(txt));
        assertEquals(3.3, m.vs(131, 34, 0), 1e-6);
        assertEquals(3.4, m.vs(130, 33, 5), 1e-6);
        assertEquals(3.7, m.vs(131, 34, 5), 1e-6);
    }

    @Test
    public void rejectsIncompleteGrid() throws Exception {
        String txt = "130 33 0 3.0\n131 33 0 3.1\n130 34 0 3.2\n131 34 0 3.3\n130 33 5 3.4\n";
        try {
            VelocityModel3D.fromCsv(write(txt));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("regular grid"));
        }
    }

    @Test
    public void rejectsNonPositiveVs_andBadLines() throws Exception {
        String neg = "130 33 0 3.0\n131 33 0 3.1\n130 34 0 3.2\n131 34 0 3.3\n"
            + "130 33 5 0\n131 33 5 3.5\n130 34 5 3.6\n131 34 5 3.7\n";
        try {
            VelocityModel3D.fromCsv(write(neg));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Non-positive"));
        }
        try {
            VelocityModel3D.fromCsv(write("130 33 0 3.0\n131 33 abc 3.1\n"));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("non-numeric"));
        }
        try {
            VelocityModel3D.fromCsv(write("# only comments\n"));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no data"));
        }
    }

    @Test
    public void rejectsDuplicateNode() throws Exception {
        // 2x2x2 = 8 rows expected; one node repeated and another missing
        String txt = "130 33 0 3.0\n131 33 0 3.1\n130 34 0 3.2\n131 34 0 3.3\n"
            + "130 33 5 3.4\n130 33 5 3.5\n130 34 5 3.6\n131 34 5 3.7\n";
        try {
            VelocityModel3D.fromCsv(write(txt));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Duplicate"));
        }
    }
}
