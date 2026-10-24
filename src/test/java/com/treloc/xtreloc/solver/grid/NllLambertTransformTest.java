package com.treloc.xtreloc.solver.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.io.Station;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class NllLambertTransformTest {

    /** Vinti / nlloc_sample: TRANS LAMBERT Clarke-1880 43.75 7.5 43.1993 44.9961 0. */
    private static final NllLambertTransform VINTI = NllLambertTransform.of(
        "Clarke-1880", 43.75, 7.5, 43.1993, 44.9961, 0.0);

    @Test
    public void originMapsToZeroAndRoundTrips() {
        double[] xy = VINTI.toXY(43.75, 7.5);
        assertEquals(0.0, xy[0], 1e-4);
        assertEquals(0.0, xy[1], 1e-4);
        double[] ll = VINTI.toLatLon(0.0, 0.0);
        assertEquals(43.75, ll[0], 1e-6);
        assertEquals(7.5, ll[1], 1e-6);
    }

    @Test
    public void roneMatchesNllocSampleHeaderAndGtsrce() {
        // GTSRCE RONE LATLONDM 43 52.88 N  7 35.89 E ; hdr: RONE 7.888686 14.595167
        double lat = 43.0 + 52.88 / 60.0;
        double lon = 7.0 + 35.89 / 60.0;
        double[] xy = VINTI.toXY(lat, lon);
        assertEquals(7.888686, xy[0], 0.02);
        assertEquals(14.595167, xy[1], 0.02);
        double[] back = VINTI.toLatLon(7.888686, 14.595167);
        assertEquals(lat, back[0], 2e-4);
        assertEquals(lon, back[1], 2e-4);
    }

    @Test
    public void parseAcceptsHeaderKeywordsAndLambartTypo() throws Exception {
        NllGeographicTransform tr = NllTransforms.parse(
            "TRANSFORM  LAMBERT RefEllipsoid Clarke-1880  LatOrig 43.750000  LongOrig 7.500000"
                + "  FirstStdParal 43.199300  SecondStdParal 44.996100  RotCW 0.000000");
        assertTrue(tr instanceof NllLambertTransform);
        assertEquals(43.75, tr.lat0(), 1e-9);
        NllGeographicTransform typo = NllTransforms.parse(
            "TRANSFORM  LAMBART RefEllipsoid WGS-84  LatOrig 0  LongOrig 0  FirstStdParal -10  SecondStdParal 10  RotCW 0");
        assertTrue(typo instanceof NllLambertTransform);
    }

    @Test
    public void time2dHeaderIsAcceptedAndStationsAreRead() throws Exception {
        Path dir = Files.createTempDirectory("nll2d");
        String hdr = ""
            + "1 5 4  0.000000 0.000000 -3.000000  1.000000 1.000000 1.000000 TIME2D FLOAT\n"
            + "RONE 7.888686 14.595167 -0.300000\n"
            + "TRANSFORM  LAMBERT RefEllipsoid Clarke-1880  LatOrig 43.750000  LongOrig 7.500000"
            + "  FirstStdParal 43.199300  SecondStdParal 44.996100  RotCW 0.000000\n";
        Path h = dir.resolve("layer.P.RONE.time.hdr");
        Files.writeString(h, hdr);
        float[] t = new float[1 * 5 * 4];
        for (int i = 0; i < t.length; i++) {
            t[i] = 1.0f + 0.01f * i;
        }
        Files.write(dir.resolve("layer.P.RONE.time.buf"), floatsLe(t));

        StationTimeGrid.Header parsed = StationTimeGrid.parseHeader(h);
        assertEquals("TIME2D", parsed.gridType);
        assertEquals(1, parsed.nx);
        Station st = parsed.toStation();
        assertNotNull(st);
        assertEquals("RONE", st.getCode());
        assertEquals(43.0 + 52.88 / 60.0, st.getLat(), 2e-4);
        assertEquals(7.0 + 35.89 / 60.0, st.getLon(), 2e-4);
        assertEquals(300.0, st.getDep(), 1e-3);

        StationTimeGrid g = StationTimeGrid.read(h);
        assertTrue(g.time2d());
        assertTrue(g.containsXYZ(99.0, 2.0, 0.0));
        assertTrue(Double.isFinite(g.timeXYZ(0.0, 2.0, 0.0)));

        List<Station> stations = NllGridStations.read(dir);
        assertEquals(1, stations.size());
        assertEquals("RONE", stations.get(0).getCode());
        assertEquals(NllGridStations.setKey(dir), NllGridStations.setKey(h));
    }

    @Test
    public void time2dInterpolatesTheCollapsedAxis() {
        int nx = 1;
        int ny = 5;
        int nz = 4;
        float[] t = new float[nx * ny * nz];
        for (int j = 0; j < ny; j++) {
            for (int k = 0; k < nz; k++) {
                t[j * nz + k] = (float) (1.0 + 0.2 * (2 * j) + 0.5 * k);
            }
        }
        StationTimeGrid g = new StationTimeGrid("S1", "P", nx, ny, nz, 0, 0, 0, 1, 2, 1,
            0, 0, 0, new NllSimpleTransform(35, 140), t);
        double[] out = new double[4];
        assertTrue(g.evaluate(0.0, 3.0, 1.5, out));
        assertEquals(1.0 + 0.2 * 3.0 + 0.5 * 1.5, out[0], 1e-5);
        assertEquals(0.0, out[1], 1e-9);
        assertEquals(0.2, out[2], 1e-5);
        assertEquals(0.5, out[3], 1e-5);
    }

    private static byte[] floatsLe(float[] v) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(v.length * 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (float x : v) {
            b.putFloat(x);
        }
        return b.array();
    }
}
