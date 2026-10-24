package com.treloc.xtreloc.solver.eikonal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class VelocityVolume3DTest {

    private static Path write(String text) throws IOException {
        Path p = Files.createTempFile("vv3d", ".out");
        p.toFile().deleteOnExit();
        Files.write(p, text.getBytes(StandardCharsets.UTF_8));
        return p;
    }

    /** lat lon depth vs dvsInit dvsMean layer, 2 lat x 2 lon x 2 depth. */
    private static final String RAW =
        "  36.000 138.500   0.000   3.000    0.1    0.2  1\n"
      + "  36.000 139.000   0.000   3.100    0.3    0.4  1\n"
      + "  37.000 138.500   0.000   3.200    0.5    0.6  2\n"
      + "  37.000 139.000   0.000   3.300    0.7    0.8  2\n"
      + "  36.000 138.500  10.000   4.000    1.1    1.2  3\n"
      + "  36.000 139.000  10.000   4.100    1.3    1.4  3\n"
      + "  37.000 138.500  10.000   4.200    1.5    1.6  4\n"
      + "  37.000 139.000  10.000   4.300    1.7    1.8  4\n";

    @Test
    public void nakajimaRaw_isParsedWithLatLonOrder() throws Exception {
        VelocityVolume3D v = VelocityVolume3D.fromNakajimaRaw(write(RAW));
        assertEquals(2, v.nLon());
        assertEquals(2, v.nLat());
        assertEquals(2, v.nDepth());
        assertEquals(138.5, v.minLon(), 1e-9);
        assertEquals(37.0, v.maxLat(), 1e-9);
        assertArrayEquals(new String[] {"vs_km_s", "dvs_init_pct", "dvs_mean_pct", "layer"},
            v.channelNames().toArray(new String[0]));
        assertEquals(3.2, v.sample("vs_km_s", 138.5, 37.0, 0), 1e-6);
        assertEquals(1.8, v.sample("dvs_mean_pct", 139.0, 37.0, 10), 1e-6);
        // trilinear at the centre of the cube = mean of the 8 Vs values
        assertEquals(3.65, v.sample("vs_km_s", 138.75, 36.5, 5), 1e-5);
        // layer is nearest-node, not interpolated
        assertEquals(1.0, v.sample("layer", 138.6, 36.2, 1.0), 0.0);
        assertEquals(4.0, v.sample("layer", 138.9, 36.9, 9.0), 0.0);
    }

    @Test
    public void csvRoundTrip_plainAndGzip() throws Exception {
        VelocityVolume3D v = VelocityVolume3D.fromNakajimaRaw(write(RAW));
        for (String ext : new String[] {".csv", ".csv.gz"}) {
            Path out = Files.createTempFile("vv3d_rt", ext);
            out.toFile().deleteOnExit();
            v.writeCsv(out);
            VelocityVolume3D r = VelocityVolume3D.fromCsv(out);
            assertEquals(v.channelNames(), r.channelNames());
            assertEquals(v.nodeCount(), r.nodeCount());
            for (String ch : v.channelNames()) {
                assertArrayEquals(ch + " " + ext, v.channel(ch), r.channel(ch), 1e-3f);
            }
            assertEquals(v.depth(1), r.depth(1), 1e-9);
        }
    }

    @Test
    public void toVelocityModel3D_usesFirstChannel() throws Exception {
        VelocityVolume3D v = VelocityVolume3D.fromNakajimaRaw(write(RAW));
        VelocityModel3D m = v.toVelocityModel3D();
        assertEquals(3.1, m.vs(139.0, 36.0, 0.0), 1e-6);
        assertEquals(4.3, m.vs(139.0, 37.0, 10.0), 1e-6);
    }

    @Test
    public void velocityModel3D_readsGzippedCsv() throws Exception {
        VelocityVolume3D v = VelocityVolume3D.fromNakajimaRaw(write(RAW));
        Path out = Files.createTempFile("vm_gz", ".csv.gz");
        out.toFile().deleteOnExit();
        v.writeCsv(out);
        VelocityModel3D m = VelocityModel3D.fromCsv(out);
        assertEquals(3.3, m.vs(139.0, 37.0, 0.0), 1e-3);
    }

    @Test
    public void incompleteGridIsRejected() throws Exception {
        String bad = RAW.substring(0, RAW.lastIndexOf('\n', RAW.length() - 2) + 1);
        try {
            VelocityVolume3D.fromNakajimaRaw(write(bad));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("regular grid"));
        }
    }

    @Test
    public void bundledTohokuModel_hasExpectedStructure() throws Exception {
        try (InputStream in = getClass().getClassLoader()
            .getResourceAsStream("velocity-models-3d/nakajima2001_tohoku_vs.csv.gz")) {
            assertNotNull("bundled model missing", in);
            VelocityVolume3D v = VelocityVolume3D.fromCsv(in, "tohoku");
            assertEquals(33, v.nLon());
            assertEquals(49, v.nLat());
            assertEquals(201, v.nDepth());
            assertEquals(138.5, v.minLon(), 1e-9);
            assertEquals(142.5, v.maxLon(), 1e-9);
            assertEquals(36.0, v.minLat(), 1e-9);
            assertEquals(42.0, v.maxLat(), 1e-9);
            assertEquals(0.0, v.minDepth(), 1e-9);
            assertEquals(200.0, v.maxDepth(), 1e-9);
            // first line of the raw table: 36.000 138.500 0.000 3.237 0.000 3.594 1
            assertEquals(3.237, v.sample("vs_km_s", 138.5, 36.0, 0.0), 1e-4);
            assertEquals(0.0, v.sample("dvs_init_pct", 138.5, 36.0, 0.0), 1e-4);
            assertEquals(3.594, v.sample("dvs_mean_pct", 138.5, 36.0, 0.0), 1e-4);
            assertEquals(1.0, v.sample("layer", 138.5, 36.0, 0.0), 0.0);
            // raw line: 36.000 141.375 0.000 3.502 0.000 12.067 2
            assertEquals(3.502, v.sample("vs_km_s", 141.375, 36.0, 0.0), 1e-4);
            assertEquals(2.0, v.sample("layer", 141.375, 36.0, 0.0), 0.0);
            double[] r = v.range("vs_km_s");
            assertEquals(2.063, r[0], 1e-4);
            assertEquals(5.3, r[1], 1e-4);
            assertTrue(v.toVelocityModel3D().vs(140.0, 39.0, 30.0) > 2.0);
        }
    }

    @Test
    public void reduceAlongDepth_meanIsAverageOfTheTwoSamples() throws Exception {
        VelocityVolume3D v = VelocityVolume3D.fromNakajimaRaw(write(RAW));
        VelocityVolume3D.Plane2D p = v.reduce(VelocityVolume3D.CH_VS, VelocityVolume3D.Reduce.MEAN, 2);
        assertEquals(2, p.nU());
        assertEquals(2, p.nV());
        assertEquals(3.5, p.sample(138.5, 36.0), 1e-6);
        VelocityVolume3D.Plane2D s = v.reduce(VelocityVolume3D.CH_VS, VelocityVolume3D.Reduce.SUM, 2);
        assertEquals(7.0, s.sample(138.5, 36.0), 1e-6);
        VelocityVolume3D.Plane2D var = v.reduce(VelocityVolume3D.CH_VS, VelocityVolume3D.Reduce.VARIANCE, 2);
        assertEquals(0.25, var.sample(138.5, 36.0), 1e-6);
    }

    @Test
    public void sliceAtDepth_matchesTheNodeValue() throws Exception {
        VelocityVolume3D v = VelocityVolume3D.fromNakajimaRaw(write(RAW));
        VelocityVolume3D.Plane2D plan = v.slice(VelocityVolume3D.CH_VS, 2, 0.0);
        assertEquals(3.0, plan.sample(138.5, 36.0), 1e-6);
        VelocityVolume3D.Plane2D deep = v.slice(VelocityVolume3D.CH_VS, 2, 10.0);
        assertEquals(4.0, deep.sample(138.5, 36.0), 1e-6);
        VelocityVolume3D.Plane2D lonSec = v.slice(VelocityVolume3D.CH_VS, 0, 138.5);
        assertEquals("lat", lonSec.uName);
        assertEquals("depth", lonSec.vName);
        assertEquals(3.0, lonSec.sample(36.0, 0.0), 1e-6);
        assertEquals(4.2, lonSec.sample(37.0, 10.0), 1e-6);
    }
}
