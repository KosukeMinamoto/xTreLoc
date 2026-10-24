package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.solver.grid.LayerCakeModel;
import com.treloc.xtreloc.solver.table.TravelTimeTable.Phase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WinHypomhStructTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** WIN manpage example: 6 named layers, n+2 P velocities, n+1 thicknesses. */
    private static final String WIN = ""
        + "35.5 139.5 30.0\n"
        + "    6 ABC\n"
        + "5.50 5.51 6.10 6.11 6.70 6.71 8.00\n"
        + "8.20\n"
        + "4.00 0.01 10.60 0.01 16.90 0.01 600.0\n"
        + "5.0 100.0 100.0 30.0\n";

    @Test
    public void looksLikeWinAndNotNd() {
        assertTrue(WinHypomhStruct.looksLike(Arrays.asList(WIN.split("\n"))));
        assertTrue(!WinHypomhStruct.looksLike(Arrays.asList(
            "0.000 5.50 3.18",
            "4.000 5.50 3.18",
            "4.000 6.10 3.53")));
    }

    @Test
    public void discretePSamplesAreLinearlyInterpolated_sIsVpOver173() throws Exception {
        WinHypomhStruct w = WinHypomhStruct.parse(Arrays.asList(WIN.split("\n")));
        assertEquals("ABC", w.name);
        assertEquals(6, w.nLayers);
        assertEquals(8, w.depthKm.length);
        assertEquals(0.0, w.depthKm[0], 0.0);
        assertEquals(4.0, w.depthKm[1], 1e-12);
        assertEquals(5.50, w.vp[0], 1e-12);
        assertEquals(5.51, w.vp[1], 1e-12);
        assertEquals(5.50 / 1.73, w.vs[0], 1e-12);

        Path f = tmp.newFile("struct.tbl").toPath();
        Files.write(f, WIN.getBytes(StandardCharsets.US_ASCII));
        LayerCakeModel m = LayerCakeModel.load(f.toString());
        assertEquals(5.50, m.velocity(Phase.P, 0, 0, 0.0), 1e-9);
        assertEquals(5.51, m.velocity(Phase.P, 0, 0, 4.0), 1e-9);
        assertEquals(0.5 * (5.50 + 5.51), m.velocity(Phase.P, 0, 0, 2.0), 1e-9);
        assertEquals(5.50 / 1.73, m.velocity(Phase.S, 0, 0, 0.0), 1e-9);

        Raytrace1D.Dual dual = Raytrace1D.loadDual(f.toString());
        assertEquals(5.50, dual.p.sourceSVelocity(0.0), 1e-9);
        assertEquals(0.5 * (5.50 + 5.51), dual.p.sourceSVelocity(2.0), 1e-9);
        assertEquals(5.50 / 1.73, dual.s.sourceSVelocity(0.0), 1e-9);
    }
}
