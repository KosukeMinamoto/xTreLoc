package com.treloc.xtreloc.solver.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class StationTimeGridTest {

    /** T = 1 + 0.3 x + 0.2 y + 0.5 z on a small grid with origin (-4, -6, 0). */
    private static StationTimeGrid linearGrid(String label, String phase) {
        int nx = 5;
        int ny = 7;
        int nz = 4;
        float[] t = new float[nx * ny * nz];
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                for (int k = 0; k < nz; k++) {
                    t[(i * ny + j) * nz + k] = (float) (10 + 0.3 * (-4 + 2 * i) + 0.2 * (-6 + 2 * j) + 0.5 * (1.5 * k));
                }
            }
        }
        return new StationTimeGrid(label, phase, nx, ny, nz, -4, -6, 0, 2, 2, 1.5, 1.0, 2.0, 0.0,
            new NllSimpleTransform(35.0, 140.0), t);
    }

    @Test
    public void trilinearInterpolationReproducesALinearFieldAndItsGradient() {
        StationTimeGrid g = linearGrid("ST1", "P");
        double[] out = new double[4];
        assertTrue(g.evaluate(0.7, -1.3, 2.2, out));
        assertEquals(10 + 0.3 * 0.7 + 0.2 * -1.3 + 0.5 * 2.2, out[0], 1e-5);
        assertEquals(0.3, out[1], 1e-5);
        assertEquals(0.2, out[2], 1e-5);
        assertEquals(0.5, out[3], 1e-5);
        assertTrue(Double.isNaN(g.timeXYZ(4.5, 0, 1)));
        assertTrue(Double.isNaN(g.timeXYZ(0, 0, 4.6)));
        assertTrue(Double.isNaN(g.timeXYZ(0, 0, -0.1)));
        assertEquals(g.timeXYZ(8, 6, 4.5), g.timeXYZ(8, 6, 4.5), 0.0); // corner is inside
    }

    @Test
    public void geographicAccessGoesThroughTheSimpleTransform() {
        StationTimeGrid g = linearGrid("ST1", "P");
        double[] ll = g.transform().toLatLon(3.0, 2.0);
        assertEquals(g.timeXYZ(3.0, 2.0, 1.0), g.time(ll[0], ll[1], 1.0), 1e-9);
        assertTrue(g.contains(ll[0], ll[1], 1.0));
        assertFalse(g.contains(ll[0] + 1.0, ll[1], 1.0));
        double[] back = g.transform().toXY(ll[0], ll[1]);
        assertEquals(3.0, back[0], 1e-9);
        assertEquals(2.0, back[1], 1e-9);
    }

    @Test
    public void nllFilesRoundTrip_bothByteOrders_andHeaderFormat() throws Exception {
        Path dir = Files.createTempDirectory("nll");
        for (ByteOrder order : new ByteOrder[] {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN}) {
            StationTimeGrid g = linearGrid("ML01", "S");
            g.write(dir, "demo", order);
            List<String> hdr = Files.readAllLines(dir.resolve("demo.S.ML01.time.hdr"));
            assertEquals("5 7 4  -4.000000 -6.000000 0.000000  2.000000 2.000000 1.500000 TIME FLOAT", hdr.get(0));
            assertEquals("ML01 1.000000 2.000000 0.000000", hdr.get(1));
            assertEquals("TRANSFORM  SIMPLE  LatOrig  35.000000  LongOrig  140.000000  RotAngle  0.000000", hdr.get(2));
            assertEquals(5 * 7 * 4 * 4, Files.size(dir.resolve("demo.S.ML01.time.buf")));

            StationTimeGrid r = StationTimeGrid.read(dir.resolve("demo.S.ML01.time.hdr"));
            assertEquals("ML01", r.label());
            assertEquals("S", r.phase());
            assertEquals(g.nx(), r.nx());
            assertEquals(g.nz(), r.nz());
            assertEquals(1.0, r.sourceX(), 1e-9);
            assertEquals(35.0, r.transform().lat0(), 1e-9);
            for (int i = 0; i < g.nx(); i++) {
                for (int j = 0; j < g.ny(); j++) {
                    for (int k = 0; k < g.nz(); k++) {
                        assertEquals(g.value(i, j, k), r.value(i, j, k), 0.0f);
                    }
                }
            }
        }
    }

    @Test
    public void setWritesAndReadsADirectory() throws Exception {
        Path dir = Files.createTempDirectory("nllset");
        TravelTimeGridSet set = new TravelTimeGridSet("test");
        set.add(linearGrid("A", "P"));
        set.add(linearGrid("A", "S"));
        set.add(linearGrid("B", "P"));
        set.write(dir, "r");
        TravelTimeGridSet back = TravelTimeGridSet.read(dir, "r");
        assertEquals(3, back.size());
        assertEquals(2, back.phases().size());
        assertEquals(2, back.labels("P").size());
        assertTrue(back.get("S", "A") != null);
        assertTrue(back.get("S", "B") == null);
        try {
            TravelTimeGridSet.read(dir, "other");
            org.junit.Assert.fail();
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("No *.time.hdr"));
        }
    }

    @Test
    public void incompleteBufIsSkippedWhenListingAndReading() throws Exception {
        Path dir = Files.createTempDirectory("nllbad");
        linearGrid("OK", "S").write(dir, "r");
        Path badHdr = dir.resolve("r.P.BAD.time.hdr");
        Files.writeString(badHdr, "259 279 51  0 0 0  1 1 1 TIME FLOAT\nBAD 0 0 0\n");
        Files.write(dir.resolve("r.P.BAD.time.buf"), new byte[0]);
        assertFalse(StationTimeGrid.isComplete(badHdr));
        assertEquals(1, TravelTimeGridSet.list(dir, "r").size());
        assertEquals("OK", TravelTimeGridSet.list(dir, "r").get(0).label);
        TravelTimeGridSet set = TravelTimeGridSet.read(dir, "r");
        assertEquals(1, set.size());
        assertTrue(set.get("S", "OK") != null);
    }

    @Test
    public void solverLoadSkipsPGrids() throws Exception {
        Path dir = Files.createTempDirectory("nllsonly");
        linearGrid("A", "P").write(dir, "r");
        linearGrid("A", "S").write(dir, "r");
        TravelTimeGridSet sOnly = TravelTimeGridSet.read(dir, "r", "S");
        assertEquals(1, sOnly.size());
        assertTrue(sOnly.get("S", "A") != null);
        assertTrue(sOnly.get("P", "A") == null);
        GridTravelTimeTable t = GridTravelTimeTable.load(dir.toString());
        assertTrue(t.describe().contains("1 S"));
        assertTrue(t.describe().contains("1 P"));
    }
}
