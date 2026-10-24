package com.treloc.xtreloc.solver.eikonal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.treloc.xtreloc.solver.Raytrace1D;
import org.junit.Test;

/** 1-D models shown as a layer cake in the 3D map. */
public class LayerCakeVolumeTest {

    private static final String VS = VelocityVolume3D.CH_VS;

    @Test
    public void interpolatesLinearlyAndKeepsDiscontinuitySharp() {
        double[] z = {0, 10, 10, 40};
        double[] v = {3.0, 3.5, 4.0, 4.5};
        VelocityVolume3D vol = VelocityVolume3D.fromProfile1D("t", z, v, 139, 141, 37, 39, 30);
        assertEquals(30.0, vol.maxDepth(), 1e-9);
        assertEquals(3.25, vol.sample(VS, 140, 38, 5), 1e-6);
        assertEquals(4.0 + 0.5 * 10.0 / 30.0, vol.sample(VS, 140, 38, 20), 1e-3);
        // sharp interface at 10 km (the two samples are 1 m apart)
        assertEquals(3.5, vol.sample(VS, 140, 38, 9.99), 5e-3);
        assertEquals(4.0, vol.sample(VS, 140, 38, 10.01), 5e-3);
    }

    @Test
    public void isHorizontallyUniform() {
        VelocityVolume3D vol = VelocityVolume3D.fromProfile1D("t", new double[] {0, 50}, new double[] {3, 4},
            139, 141, 37, 39, 50);
        double a = vol.sample(VS, 139.1, 37.2, 20);
        double b = vol.sample(VS, 140.9, 38.8, 20);
        assertEquals(a, b, 1e-9);
        assertEquals(3.4, a, 1e-6);
    }

    @Test
    public void extendsFlatBelowTheProfile() {
        VelocityVolume3D vol = VelocityVolume3D.fromProfile1D("t", new double[] {0, 10}, new double[] {3, 4},
            0, 1, 0, 1, 25);
        assertEquals(4.0, vol.sample(VS, 0.5, 0.5, 20), 1e-9);
    }

    @Test
    public void rejectsInvalidInput() {
        try {
            VelocityVolume3D.fromProfile1D("t", new double[] {0, 10}, new double[] {3, 4}, 1, 1, 0, 1, 10);
            fail("empty region");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            VelocityVolume3D.fromProfile1D("t", new double[] {0, 10}, new double[] {3, 0}, 0, 1, 0, 1, 10);
            fail("zero velocity");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void bundledModelBecomesLayerCake() throws Exception {
        for (String name : new String[] {"prem.nd", "iasp91.tvel", "ak135.tvel"}) {
            double[][] p = Raytrace1D.load(name, false).sVelocityProfile();
            assertTrue(name, p[0].length >= 2);
            for (int i = 1; i < p[0].length; i++) {
                assertTrue(name + " depth must not decrease", p[0][i] >= p[0][i - 1]);
            }
            for (double vs : p[1]) {
                assertTrue(name + " vs > 0", vs > 0.0);
            }
            VelocityVolume3D vol = VelocityVolume3D.fromProfile1D(name, p[0], p[1], 138, 144, 35, 41, 100);
            double shallow = vol.sample(VS, 141, 38, 5);
            double deep = vol.sample(VS, 141, 38, 90);
            assertTrue(name + " crust Vs", shallow > 2.5 && shallow < 4.0);
            assertTrue(name + " mantle Vs", deep > shallow);
        }
    }
}
