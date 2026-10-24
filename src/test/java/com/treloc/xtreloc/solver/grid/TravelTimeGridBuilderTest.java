package com.treloc.xtreloc.solver.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder.Region;
import com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder.StationPoint;
import com.treloc.xtreloc.solver.table.TravelTimeTable.Phase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

public class TravelTimeGridBuilderTest {

    private static final double LAT = 35.0;
    private static final double LON = 140.0;

    private static StationPoint station(String label, double dxKm, double dyKm, double depth) {
        NllSimpleTransform tr = new NllSimpleTransform(LAT, LON);
        double[] ll = tr.toLatLon(dxKm, dyKm);
        return new StationPoint(label, ll[0], ll[1], depth);
    }

    @Test
    public void homogeneousModel_givesDistanceOverVelocity_forBothPhases() throws Exception {
        LayerCakeModel m = LayerCakeModel.of("hom", new double[] {0, 100}, new double[] {6, 6}, new double[] {3.5, 3.5});
        Region region = new Region(LAT, LON, 20, 20, 20, 2.0);
        TravelTimeGridSet set = new TravelTimeGridBuilder(m, region, false, EnumSet.of(Phase.P, Phase.S))
            .build(Arrays.asList(station("A", 4, -4, 0.0), station("B", -12, 8, 0.0)), null);
        assertEquals(4, set.size());
        for (String ph : new String[] {"P", "S"}) {
            double v = ph.equals("P") ? 6.0 : 3.5;
            for (String lab : new String[] {"A", "B"}) {
                StationTimeGrid g = set.get(ph, lab);
                // a source on a node has T = 0; off-node the trilinear interpolation of the cone is only good to ~h/v
                assertEquals(0.0, g.timeXYZ(g.sourceX(), g.sourceY(), g.sourceZ()), lab.equals("A") ? 1e-4 : 1.0 / v);
                for (double[] p : new double[][] {{0, 0, 10}, {-15, 12, 3}, {16, -9, 18}, {2, 3, 1}}) {
                    double r = Math.sqrt(Math.pow(p[0] - g.sourceX(), 2) + Math.pow(p[1] - g.sourceY(), 2)
                        + Math.pow(p[2] - g.sourceZ(), 2));
                    assertEquals(ph + lab + Arrays.toString(p), r / v, g.timeXYZ(p[0], p[1], p[2]), 0.02 * r / v + 0.05);
                }
            }
        }
        assertEquals(21, set.get("P", "A").nx());
        assertEquals(11, set.get("P", "A").nz());
    }

    @Test
    public void dippingGradientLayers_matchTheAnalyticConstantGradientTravelTime() throws Exception {
        // v = 4 + 0.3 z' with z' = depth - tan(dip) * s  -> a constant-gradient medium tilted off the vertical
        double g0 = 0.3;
        double v0 = 4.0;
        LayerCakeModel m = LayerCakeModel.of("grad", new double[] {-10, 100},
            new double[] {v0 + g0 * -10, v0 + g0 * 100}, new double[] {v0 + g0 * -10, v0 + g0 * 100})
            .withStrikeDip(30.0, 20.0);
        double tanDip = Math.tan(Math.toRadians(20.0));
        double dipDir = Math.toRadians(120.0);
        double gx = -g0 * tanDip * Math.sin(dipDir);
        double gy = -g0 * tanDip * Math.cos(dipDir);
        double gz = g0;
        double gmag = Math.sqrt(gx * gx + gy * gy + gz * gz);

        Region region = new Region(LAT, LON, 20, 20, 25, 1.0);
        StationPoint st = station("A", 2, -3, 1.0);
        TravelTimeGridSet set = new TravelTimeGridBuilder(m, region, false, EnumSet.of(Phase.S)).build(
            Collections.singletonList(st), null);
        StationTimeGrid g = set.get("S", "A");

        double maxErr = 0.0;
        double maxTiltEffect = 0.0;
        for (double[] p : new double[][] {{0, 0, 10}, {-15, 12, 3}, {16, -9, 18}, {-5, -15, 20}, {12, 14, 8}}) {
            double sx = g.sourceX();
            double sy = g.sourceY();
            double sz = g.sourceZ();
            double va = v0 + gx * sx + gy * sy + g0 * sz - g0 * 0; // value at the station
            double vb = v0 + gx * p[0] + gy * p[1] + gz * p[2];
            double r2 = Math.pow(p[0] - sx, 2) + Math.pow(p[1] - sy, 2) + Math.pow(p[2] - sz, 2);
            double analytic = acosh(1.0 + gmag * gmag * r2 / (2 * va * vb)) / gmag;
            double got = g.timeXYZ(p[0], p[1], p[2]);
            maxErr = Math.max(maxErr, Math.abs(got - analytic) / analytic);
            // the same medium without the tilt would give a clearly different time
            double va0 = v0 + g0 * sz;
            double vb0 = v0 + g0 * p[2];
            double flat = acosh(1.0 + g0 * g0 * r2 / (2 * va0 * vb0)) / g0;
            maxTiltEffect = Math.max(maxTiltEffect, Math.abs(flat - analytic) / analytic);
        }
        System.out.println("dipping gradient: max relative error " + maxErr + ", tilt effect " + maxTiltEffect);
        assertTrue("error " + maxErr, maxErr < 0.015);
        assertTrue("tilt must matter in this test: " + maxTiltEffect, maxTiltEffect > 5 * maxErr);
    }

    @Test
    public void mountainStationIsClampedToGridTopWhenMinDepthIsSeaLevel() throws Exception {
        LayerCakeModel m = LayerCakeModel.of("hom", new double[] {0, 100}, new double[] {6, 6}, new double[] {3.5, 3.5});
        Region region = new Region(LAT, LON, 10, 10, 0.0, 10.0, 2.0);
        StationPoint mountain = station("MT", 2, -2, -1.5);
        TravelTimeGridSet set = new TravelTimeGridBuilder(m, region, false, EnumSet.of(Phase.S))
            .build(Collections.singletonList(mountain), null);
        StationTimeGrid g = set.get("S", "MT");
        assertEquals(0.0, g.sourceZ(), 1e-9);
        assertEquals(0.0, g.timeXYZ(g.sourceX(), g.sourceY(), g.sourceZ()), 1e-3);
    }

    @Test
    public void stationOutsideTheRegionIsRejected() {
        LayerCakeModel m = LayerCakeModel.of("hom", new double[] {0, 100}, new double[] {6, 6}, new double[] {3.5, 3.5});
        Region region = new Region(LAT, LON, 10, 10, 10, 1.0);
        try {
            new TravelTimeGridBuilder(m, region, false, EnumSet.of(Phase.S))
                .build(Collections.singletonList(station("FAR", 30, 0, 0)), null);
            org.junit.Assert.fail("expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().contains("FAR") && e.getMessage().contains("outside the grid region"));
        }
    }

    @Test
    public void fitStationsIncludesAllStations() {
        List<StationPoint> st = Arrays.asList(station("A", -40, 10, 0), station("B", 25, -35, 0));
        Region r = Region.fitStations(st, 10.0, 50.0, 2.0);
        NllSimpleTransform tr = new NllSimpleTransform(r.centerLat, r.centerLon);
        for (StationPoint s : st) {
            double[] xy = tr.toXY(s.lat, s.lon);
            assertTrue(Math.abs(xy[0]) <= r.halfWidthXKm - 10.0 + 1e-6);
            assertTrue(Math.abs(xy[1]) <= r.halfWidthYKm - 10.0 + 1e-6);
        }
    }

    @Test
    public void builtGridsSurviveExportAndImport() throws Exception {
        LayerCakeModel m = LayerCakeModel.load("prem.nd").withStrikeDip(45, 10);
        Region region = new Region(LAT, LON, 15, 15, 15, 2.0);
        TravelTimeGridSet set = new TravelTimeGridBuilder(m, region, true, EnumSet.of(Phase.P, Phase.S))
            .build(Collections.singletonList(station("ML01", 3, 4, 0.0)), null);
        Path dir = Files.createTempDirectory("ttgrid");
        set.write(dir, "prem");
        TravelTimeGridSet back = TravelTimeGridSet.read(dir, "prem");
        for (String ph : new String[] {"P", "S"}) {
            StationTimeGrid a = set.get(ph, "ML01");
            StationTimeGrid b = back.get(ph, "ML01");
            double[] ll = a.transform().toLatLon(7.3, -2.2);
            assertEquals(a.time(ll[0], ll[1], 6.4), b.time(ll[0], ll[1], 6.4), 0.0);
            assertTrue(b.time(ll[0], ll[1], 6.4) > 0);
        }
        // P is faster than S
        double[] ll = set.get("P", "ML01").transform().toLatLon(7.3, -2.2);
        assertTrue(set.get("P", "ML01").time(ll[0], ll[1], 6.4) < set.get("S", "ML01").time(ll[0], ll[1], 6.4));
    }

    @Test
    public void anisotropicSpacing_writesDistinctDxDzInNllHeader() throws Exception {
        LayerCakeModel m = LayerCakeModel.of("hom", new double[] {0, 100}, new double[] {6, 6}, new double[] {3.5, 3.5});
        Region region = new Region(LAT, LON, 12, 12, 0.0, 10.0, 2.0, 0.5);
        TravelTimeGridSet set = new TravelTimeGridBuilder(m, region, false, EnumSet.of(Phase.S))
            .build(Collections.singletonList(station("A", 2, -2, 0.0)), null);
        StationTimeGrid g = set.get("S", "A");
        assertEquals(2.0, g.dx(), 1e-12);
        assertEquals(2.0, g.dy(), 1e-12);
        assertEquals(0.5, g.dz(), 1e-12);
        assertEquals(21, g.nz());
        Path dir = Files.createTempDirectory("ttgrid-aniso");
        set.write(dir, "aniso");
        StationTimeGrid back = StationTimeGrid.read(dir.resolve("aniso.S.A.time.hdr"));
        assertEquals(2.0, back.dx(), 1e-9);
        assertEquals(0.5, back.dz(), 1e-9);
        List<String> hdr = Files.readAllLines(dir.resolve("aniso.S.A.time.hdr"));
        assertTrue(hdr.get(0), hdr.get(0).contains("2.000000 2.000000 0.500000"));
    }

    @Test
    public void layerCakeGridAgreesWithRaytrace1D() throws Exception {
        LayerCakeModel m = LayerCakeModel.load("prem.nd");
        Region region = new Region(LAT, LON, 50, 50, 40, 2.0);
        StationPoint st = station("A", 0, 0, 0.0);
        TravelTimeGridSet set = new TravelTimeGridBuilder(m, region, true, EnumSet.of(Phase.P, Phase.S))
            .build(Collections.singletonList(st), null);
        com.treloc.xtreloc.solver.Raytrace1D.Dual dual = com.treloc.xtreloc.solver.Raytrace1D.loadDual("prem.nd");
        double maxRel = 0;
        for (Phase ph : Phase.values()) {
            StationTimeGrid g = set.get(ph.name(), "A");
            com.treloc.xtreloc.solver.Raytrace1D rt = dual.forPhase(ph);
            for (double[] p : new double[][] {{30, 0, 10}, {0, -45, 20}, {-40, 40, 35}, {20, 15, 5}}) {
                double[] ll = g.transform().toLatLon(p[0], p[1]);
                double got = g.time(ll[0], ll[1], p[2]);
                net.sf.geographiclib.GeodesicData gd = net.sf.geographiclib.Geodesic.WGS84.Inverse(
                    ll[0], ll[1], st.lat, st.lon);
                double ref = rt.travelTimeSeconds(gd.s12 / 1000.0, p[2], 0.0);
                maxRel = Math.max(maxRel, Math.abs(got - ref) / ref);
            }
        }
        assertTrue("deviation " + maxRel, maxRel < 0.05);
    }

    private static double acosh(double x) {
        return Math.log(x + Math.sqrt(x * x - 1.0));
    }
}
