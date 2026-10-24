package com.treloc.xtreloc.solver.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.solver.eikonal.VelocityVolume3D;
import com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder.Region;
import com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder.StationPoint;
import com.treloc.xtreloc.solver.table.TravelTimeTable.Phase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class LayerCakeVolumesTest {

    private static LayerCakeModel twoLayers() {
        return LayerCakeModel.of("two", new double[] {0, 10, 10, 100}, new double[] {5, 5, 8, 8}, new double[] {3, 3, 4.5, 4.5});
    }

    @Test
    public void horizontalLayerCake_keepsTheProfileSamples_andBothChannels() {
        VelocityVolume3D v = LayerCakeVolumes.build(twoLayers(), "t", 140, 141, 35, 36, 50, 41, 41);
        assertEquals(2, v.nLon());
        assertEquals(2, v.nLat());
        assertEquals(0.0, v.minDepth(), 0.0);
        assertEquals(50.0, v.maxDepth(), 1e-9);
        assertTrue(v.hasChannel(VelocityVolume3D.CH_VS));
        assertTrue(v.hasChannel(LayerCakeVolumes.CH_VP));
        assertEquals(3.0, v.sample(VelocityVolume3D.CH_VS, 140.5, 35.5, 5.0), 1e-6);
        assertEquals(8.0, v.sample(LayerCakeVolumes.CH_VP, 140.5, 35.5, 30.0), 1e-6);
    }

    @Test
    public void dippingLayerCake_isSampledAroundTheRegionCentre() {
        // strike north, dip 45: interfaces deepen toward the east; the interface is at 10 km below the centre
        LayerCakeModel m = twoLayers().withStrikeDip(0.0, 45.0);
        VelocityVolume3D v = LayerCakeVolumes.build(m, "t", 140, 141, 35, 36, 60, 41, 61);
        assertEquals(41, v.nLon());
        assertEquals(61, v.nDepth());
        // centre column: S velocity jumps at 10 km
        assertEquals(3.0, v.sample(VelocityVolume3D.CH_VS, 140.5, 35.5, 8.0), 0.05);
        assertEquals(4.5, v.sample(VelocityVolume3D.CH_VS, 140.5, 35.5, 12.0), 0.05);
        // 0.25 deg east (about 22.7 km at 35.5 N) the interface is ~22.7 km deeper
        assertEquals(3.0, v.sample(VelocityVolume3D.CH_VS, 140.75, 35.5, 25.0), 0.05);
        assertEquals(4.5, v.sample(VelocityVolume3D.CH_VS, 140.75, 35.5, 36.0), 0.05);
        // west: shallower, below the interface already at 5 km
        assertEquals(4.5, v.sample(VelocityVolume3D.CH_VS, 140.25, 35.5, 5.0), 0.05);
    }

    @Test
    public void minDepthIsHonoured() {
        VelocityVolume3D v = LayerCakeVolumes.build(twoLayers(), "t", 140, 141, 35, 36, 10, 50, 41, 41);
        assertEquals(10.0, v.minDepth(), 0.0);
        assertEquals(50.0, v.maxDepth(), 1e-9);
        assertEquals(4.5, v.sample(VelocityVolume3D.CH_VS, 140.5, 35.5, 20.0), 1e-6);
    }

    @Test
    public void regionFromLonLatBoxMatchesTheBox() {
        Region r = Region.fromLonLatBox(140.0, 142.0, 35.0, 36.0, 50.0, 2.0);
        assertEquals(141.0, r.centerLon, 1e-12);
        assertEquals(35.5, r.centerLat, 1e-12);
        assertEquals(NllSimpleTransform.KM_PER_DEG * 0.5, r.halfWidthYKm, 1e-9);
        assertEquals(NllSimpleTransform.KM_PER_DEG * Math.cos(Math.toRadians(35.5)), r.halfWidthXKm, 1e-9);
    }

    @Test
    public void builderSinkReceivesGridsWithoutKeepingThem_andTheDirectoryCanBeListed() throws Exception {
        LayerCakeModel m = LayerCakeModel.of("hom", new double[] {0, 100}, new double[] {6, 6}, new double[] {3.5, 3.5});
        Region region = new Region(35, 140, 10, 10, 10, 2.0);
        NllSimpleTransform tr = new NllSimpleTransform(35, 140);
        List<StationPoint> st = new ArrayList<>();
        for (String c : new String[] {"A", "B"}) {
            double[] ll = tr.toLatLon(c.equals("A") ? -3 : 4, 2);
            st.add(new StationPoint(c, ll[0], ll[1], 0.0));
        }
        Path dir = Files.createTempDirectory("sink");
        AtomicInteger n = new AtomicInteger();
        TravelTimeGridSet set = new TravelTimeGridBuilder(m, region, false, EnumSet.of(Phase.P, Phase.S))
            .build(st, null, g -> {
                try {
                    g.write(dir, "hom");
                    n.incrementAndGet();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        assertEquals(0, set.size());
        assertEquals(4, n.get());
        List<TravelTimeGridSet.GridRef> refs = TravelTimeGridSet.list(dir, null);
        assertEquals(4, refs.size());
        assertEquals("P", refs.get(0).phase);
        assertEquals("A", refs.get(0).label);
        assertEquals(Arrays.asList("A", "B"), Arrays.asList(refs.get(0).label, refs.get(1).label));
        assertEquals(Collections.emptyList(), TravelTimeGridSet.list(dir, "other"));
    }

    @Test
    public void stationGridCanBeShownAsAVolume() throws Exception {
        LayerCakeModel m = LayerCakeModel.of("hom", new double[] {0, 100}, new double[] {6, 6}, new double[] {3.5, 3.5});
        Region region = new Region(35, 140, 10, 12, 10, 2.0);
        NllSimpleTransform tr = new NllSimpleTransform(35, 140);
        double[] ll = tr.toLatLon(0, 0);
        TravelTimeGridSet set = new TravelTimeGridBuilder(m, region, false, EnumSet.of(Phase.S))
            .build(Collections.singletonList(new StationPoint("A", ll[0], ll[1], 0.0)), null);
        StationTimeGrid g = set.get("S", "A");
        VelocityVolume3D v = g.toVolume("tt");
        assertEquals(g.nx(), v.nLon());
        assertEquals(g.ny(), v.nLat());
        assertEquals(g.nz(), v.nDepth());
        double[] p = tr.toLatLon(4.0, -6.0);
        assertEquals(g.time(p[0], p[1], 4.0), v.sample(StationTimeGrid.CH_TIME, p[1], p[0], 4.0), 0.02);
    }
}
