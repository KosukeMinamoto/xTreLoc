package com.treloc.xtreloc.solver.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.treloc.xtreloc.solver.table.TravelTimeTable.Phase;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class LayerCakeModelTest {

    private static LayerCakeModel twoLayers() {
        // 0-10 km: P 5 / S 3; interface at 10 km; below: P 8 / S 4.5
        return LayerCakeModel.of("two", new double[] {0, 10, 10, 100}, new double[] {5, 5, 8, 8}, new double[] {3, 3, 4.5, 4.5});
    }

    @Test
    public void horizontalLayerCake_hasSharpInterfaceAndExtendsBeyondTheProfile() {
        LayerCakeModel m = twoLayers();
        assertEquals(3.0, m.velocity(Phase.S, 12, -7, 9.99), 1e-9);
        assertEquals(4.5, m.velocity(Phase.S, 12, -7, 10.01), 1e-5);
        assertEquals(5.0, m.velocity(Phase.P, 0, 0, 5), 1e-9);
        assertEquals(8.0, m.velocity(Phase.P, 0, 0, 500), 1e-9);
        assertEquals(5.0, m.velocity(Phase.P, 0, 0, -3), 1e-9);
    }

    @Test
    public void dippingLayers_followStrikeAndDipConvention() {
        // strike north (0), dip 45: dip direction east, interfaces deepen toward the east
        LayerCakeModel m = twoLayers().withStrikeDip(0.0, 45.0);
        assertEquals(10.0, m.layerShiftKm(10, 0), 1e-9);
        assertEquals(0.0, m.layerShiftKm(0, 25), 1e-9);
        // interface is at 10 km below the centre and at 20 km below x = +10 km
        assertEquals(3.0, m.velocity(Phase.S, 10, 0, 19.9), 1e-9);
        assertEquals(4.5, m.velocity(Phase.S, 10, 0, 20.1), 1e-5);
        // to the west it is shallower: interface at 5 km below x = -5
        assertEquals(3.0, m.velocity(Phase.S, -5, 0, 4.9), 1e-9);
        assertEquals(4.5, m.velocity(Phase.S, -5, 0, 5.1), 1e-5);

        // strike east (90): dip direction south
        LayerCakeModel s = twoLayers().withStrikeDip(90.0, 30.0);
        assertEquals(-10.0 * Math.tan(Math.toRadians(30)), s.layerShiftKm(0, 10), 1e-9);
        assertEquals(10.0 * Math.tan(Math.toRadians(30)), s.layerShiftKm(0, -10), 1e-9);
        assertEquals(0.0, s.layerShiftKm(10, 0), 1e-9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tooSteepDipIsRejected() {
        twoLayers().withStrikeDip(0, 89);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tooSteepNegativeDipIsRejected() {
        twoLayers().withStrikeDip(0, -89);
    }

    @Test
    public void negativeDipReversesDipDirection() {
        LayerCakeModel m = twoLayers().withStrikeDip(0.0, -45.0);
        assertEquals(-10.0, m.layerShiftKm(10, 0), 1e-9);
        assertEquals(4.5, m.velocity(Phase.S, 10, 0, 1.0), 1e-5);
        assertEquals(3.0, m.velocity(Phase.S, -10, 0, 19.9), 1e-9);
        assertEquals(4.5, m.velocity(Phase.S, -10, 0, 20.1), 1e-5);
    }

    @Test
    public void loadsBundledModels_withBothWaveTypes_andDropsFluidLayers() throws Exception {
        LayerCakeModel prem = LayerCakeModel.load("prem.nd");
        assertEquals(5.8, prem.velocity(Phase.P, 0, 0, 1), 1e-9);
        assertEquals(3.2, prem.velocity(Phase.S, 0, 0, 1), 1e-9);
        assertTrue(prem.profileBottomKm() <= 2891.0);
        LayerCakeModel iasp = LayerCakeModel.load("iasp91");
        assertTrue(iasp.velocity(Phase.P, 0, 0, 100) > iasp.velocity(Phase.S, 0, 0, 100));
    }

    @Test
    public void convertedVjmaNdKeepsDepthVpVsOrder() throws Exception {
        assumeTrue(Files.isRegularFile(Paths.get("tohoku2007/vjma2020a.nd")));
        LayerCakeModel m = LayerCakeModel.load("tohoku2007/vjma2020a.nd");
        assertEquals(4.8, m.velocity(Phase.P, 0, 0, 0), 1e-9);
        assertEquals(2.796, m.velocity(Phase.S, 0, 0, 0), 1e-9);
        assertTrue(m.velocity(Phase.P, 0, 0, 200) > 7.0);
    }

}
