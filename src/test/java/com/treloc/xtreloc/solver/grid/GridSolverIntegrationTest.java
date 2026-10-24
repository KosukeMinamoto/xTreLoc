package com.treloc.xtreloc.solver.grid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.StationRepository;
import com.treloc.xtreloc.solver.HypoStationPairDiff;
import com.treloc.xtreloc.solver.HypoUtils;
import com.treloc.xtreloc.solver.Point;
import com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder.Region;
import com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder.StationPoint;
import com.treloc.xtreloc.solver.table.OutOfTableRangeException;
import com.treloc.xtreloc.solver.table.TravelTimeTable;
import com.treloc.xtreloc.solver.table.TravelTimeTable.Phase;
import com.treloc.xtreloc.solver.table.TravelTimeTables;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/** Solvers using station-wise 3-D travel-time grids (taupFile = grid directory). */
public class GridSolverIntegrationTest {

    private static final Path DEMO = Paths.get("demo/locating_example");
    private static Path gridDir;
    private static StationRepository stations;
    private static double[][] stnTable;

    @BeforeClass
    public static void buildGrids() throws Exception {
        assumeTrue(Files.isRegularFile(DEMO.resolve("station.tbl")));
        stations = StationRepository.load(DEMO.resolve("station.tbl"));
        stnTable = stations.getStationTable();
        List<StationPoint> pts = new ArrayList<>();
        for (int i = 0; i < stnTable.length; i++) {
            pts.add(new StationPoint(stations.getCodes()[i], stnTable[i][0], stnTable[i][1], stnTable[i][2]));
        }
        Region region = Region.fitStations(pts, 25.0, 40.0, 3.0);
        // horizontal layers of PREM, spherical Earth: the same model the solver's Raytrace1D uses
        LayerCakeModel m = LayerCakeModel.load("prem.nd");
        gridDir = Files.createTempDirectory("gridsolver");
        new TravelTimeGridBuilder(m, region, true, EnumSet.of(Phase.P, Phase.S)).build(pts, null).write(gridDir, "prem");
    }

    private static AppConfig config(String taup) {
        AppConfig cfg = new AppConfig();
        cfg.taupFile = taup;
        cfg.stationFile = DEMO.resolve("station.tbl").toString();
        cfg.hypBottom = 50;
        cfg.threshold = 0;
        cfg.minDataCount = 3;
        return cfg;
    }

    @Test
    public void gridDirectoryIsRecognisedAsATravelTimeTable() throws Exception {
        assertTrue(TravelTimeTables.isTableSpec(gridDir.toString()));
        assertTrue(TravelTimeTables.isTableSpec(gridDir.resolve("prem.S.ST01.time.hdr").toString()));
        assertFalse(TravelTimeTables.isTableSpec("prem.nd"));
        TravelTimeTable t = TravelTimeTables.load(gridDir.toString());
        assertTrue(t instanceof GridTravelTimeTable);
        assertTrue(t.describe().contains("13 S"));
        assertTrue(t == TravelTimeTables.load(gridDir.toString()));
    }

    @Test
    public void travelTimesAgreeWithTheLayeredRayTracerOfTheSameModel() throws Exception {
        HypoUtils grid = new HypoUtils(config(gridDir.toString()));
        HypoUtils ray = new HypoUtils(config("prem.nd"));
        assertNotNull(grid.getTravelTimeTable());
        assertNull(ray.getTravelTimeTable());
        int[] idx = new int[stnTable.length];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        double maxRel = 0;
        for (double[] ev : new double[][] {{39.5, 142.4, 10}, {39.3, 142.7, 8}, {39.7, 142.9, 20}}) {
            Point p = new Point("t", ev[0], ev[1], ev[2], 0, 0, 0, 0, "", "", -999);
            double[] a = grid.travelTime(stnTable, idx, p, Phase.S);
            double[] b = ray.travelTime(stnTable, idx, p, Phase.S);
            for (int i = 0; i < idx.length; i++) {
                assertTrue(a[i] < 1e6);
                double rel = Math.abs(a[i] - b[i]) / b[i];
                maxRel = Math.max(maxRel, rel);
            }
        }
        System.out.println("grid vs Raytrace1D (PREM): max relative difference " + maxRel);
        assertTrue("max relative difference " + maxRel, maxRel < 0.04);
    }

    @Test
    // the grid gradient is piecewise constant (trilinear cells of 2 km), so near stations it differs from a
    // central difference over several cells by tens of percent
    public void partialDerivativesFromTheGridGradientMatchFiniteDifferences() throws Exception {
        HypoUtils grid = new HypoUtils(config(gridDir.toString()));
        int[] idx = {0, 3, 6, 9, 12};
        Point p = new Point("t", 39.5, 142.4, 12.3, 0, 0, 0, 0, "", "", -999);
        double[][] dtdr = (double[][]) grid.partialDerivativeMatrix(stnTable, idx, p, Phase.S)[0];
        double dl = 0.02;
        double dz = 1.0;
        for (int i : idx) {
            int[] one = {i};
            double dLon = (t(grid, one, 39.5, 142.4 + dl, 12.3, i) - t(grid, one, 39.5, 142.4 - dl, 12.3, i)) / (2 * dl);
            double dLat = (t(grid, one, 39.5 + dl, 142.4, 12.3, i) - t(grid, one, 39.5 - dl, 142.4, 12.3, i)) / (2 * dl);
            double dDep = (t(grid, one, 39.5, 142.4, 12.3 + dz, i) - t(grid, one, 39.5, 142.4, 12.3 - dz, i)) / (2 * dz);
            assertEquals("dlon " + i, dLon, dtdr[i][0], 0.25 * Math.abs(dLon) + 0.5);
            assertEquals("dlat " + i, dLat, dtdr[i][1], 0.25 * Math.abs(dLat) + 0.5);
            assertEquals("ddep " + i, dDep, dtdr[i][2], 0.25 * Math.abs(dDep) + 0.05);
        }
    }

    private static double t(HypoUtils u, int[] one, double lat, double lon, double dep, int station) {
        Point p = new Point("t", lat, lon, dep, 0, 0, 0, 0, "", "", -999);
        return u.travelTime(stnTable, one, p, Phase.S)[station];
    }

    @Test
    public void takeoffAngleIsDownwardForDistantStationsAndUpwardForDeepSourcesBelowStations() throws Exception {
        TravelTimeTable t = TravelTimeTables.load(gridDir.toString());
        // station ST05 (39.5, 142.53), 1 km deep. Source 8 km deep right below it -> ray leaves upward
        double up = Math.toDegrees(t.takeoffAngleRad(Phase.S, 39.5, 142.53, 8.0, 39.5, 142.53, stnTable[4][2]));
        assertTrue("take-off " + up, up > 150);
        // shallow source 50 km away -> ray leaves almost horizontally downward (refracted) or horizontal
        double far = Math.toDegrees(t.takeoffAngleRad(Phase.S, 39.5, 142.0, 3.0, 39.5, 142.53, stnTable[4][2]));
        assertTrue("take-off " + far, far > 40 && far < 140);
    }

    @Test
    public void outsideTheGridIsOutOfRange() throws Exception {
        HypoUtils grid = new HypoUtils(config(gridDir.toString()));
        Point far = new Point("t", 42.0, 142.4, 10.0, 0, 0, 0, 0, "", "GRD", -999);
        Point deep = new Point("t", 39.5, 142.4, 80.0, 0, 0, 0, 0, "", "GRD", -999);
        Point air = new Point("t", 39.5, 142.4, -2.0, 0, 0, 0, 0, "", "GRD", -999);
        Point in = new Point("t", 39.5, 142.4, 10.0, 0, 0, 0, 0, "", "GRD", -999);
        for (Point p : new Point[] {far, deep, air}) {
            assertTrue(grid.markIfOutOfRange(p));
            assertEquals("ERR", p.getType());
        }
        assertFalse(grid.markIfOutOfRange(in));
        int[] idx = {0, 1, 2};
        for (double v : grid.travelTime(stnTable, idx, far, Phase.S)) {
            assertEquals(Double.MAX_VALUE, v, 0.0);
        }
        try {
            grid.partialDerivativeMatrix(stnTable, idx, deep, Phase.S);
            org.junit.Assert.fail("expected OutOfTableRangeException");
        } catch (OutOfTableRangeException e) {
            assertTrue(e.getMessage().contains("Depth may be outside model range"));
        }
    }

    @Test
    public void unknownStationIsAnOutlierStation() throws Exception {
        HypoUtils grid = new HypoUtils(config(gridDir.toString()));
        double[][] withExtra = new double[stnTable.length + 1][];
        System.arraycopy(stnTable, 0, withExtra, 0, stnTable.length);
        withExtra[stnTable.length] = new double[] {39.51, 142.41, 1.0, 0, 0}; // no grid at this position
        int[] idx = {0, stnTable.length};
        Point p = new Point("t", 39.5, 142.4, 10.0, 0, 0, 0, 0, "", "", -999);
        double[] tt = grid.travelTime(withExtra, idx, p, Phase.S);
        assertTrue(tt[0] < 1e6);
        assertEquals(Double.MAX_VALUE, tt[stnTable.length], 0.0);
    }

    @Test
    public void locationWithGridsMatchesLocationWithTheVelocityModel() throws Exception {
        Path dat = DEMO.resolve("dat/000101.000000.dat");
        assumeTrue(Files.isRegularFile(dat));
        double[] a = locate(config(gridDir.toString()), dat, "grid");
        double[] b = locate(config("prem.nd"), dat, "prem");
        System.out.printf("LMO grid: %.4f %.4f %.2f   prem: %.4f %.4f %.2f%n", a[0], a[1], a[2], b[0], b[1], b[2]);
        assertEquals("lat", b[0], a[0], 0.03);
        assertEquals("lon", b[1], a[1], 0.03);
        // Pair-difference LMO constrains depth weakly. The eikonal grid is 3 km; Raytrace1D is continuous.
        assertEquals("depth km", b[2], a[2], 6.0);
    }

    /**
     * Synthetic event in a dipping layer cake: data are generated with the dipping grids; locating them with the
     * dipping grids recovers the event, while the horizontal-layer grids give a clearly worse location.
     */
    @Test
    public void dippingLayerGridsRecoverASyntheticEventBetterThanHorizontalLayers() throws Exception {
        int[] pick = {1, 4, 7, 8, 12}; // ST02, ST05, ST08, ST09, CA00
        List<StationPoint> pts = new ArrayList<>();
        double[][] sub = new double[pick.length][];
        String[] codes = stations.getCodes();
        String[] subCodes = new String[pick.length];
        for (int i = 0; i < pick.length; i++) {
            int k = pick[i];
            sub[i] = stnTable[k];
            subCodes[i] = codes[k];
            pts.add(new StationPoint(codes[k], stnTable[k][0], stnTable[k][1], stnTable[k][2]));
        }
        Region region = Region.fitStations(pts, 20.0, 35.0, 3.0);
        Path dipDir = Files.createTempDirectory("gridsolver-dip");
        Path horDir = Files.createTempDirectory("gridsolver-hor-sub");
        new TravelTimeGridBuilder(LayerCakeModel.load("prem.nd").withStrikeDip(0.0, 25.0), region, true,
            EnumSet.of(Phase.P, Phase.S)).build(pts, null).write(dipDir, "dip");
        new TravelTimeGridBuilder(LayerCakeModel.load("prem.nd"), region, true,
            EnumSet.of(Phase.P, Phase.S)).build(pts, null).write(horDir, "hor");

        double trueLat = 39.45;
        double trueLon = 142.75;
        double trueDep = 22.0;
        HypoUtils dipUtils = new HypoUtils(config(dipDir.toString()));
        int[] all = new int[sub.length];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        double[] tt = dipUtils.travelTime(sub, all,
            new Point("t", trueLat, trueLon, trueDep, 0, 0, 0, 0, "", "", -999), Phase.S);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f SYN%n", trueLat + 0.04, trueLon - 0.05, trueDep - 4));
        sb.append("0.000 0.000 0.000 1.000\n");
        for (int k = 0; k < all.length; k++) {
            for (int l = k + 1; l < all.length; l++) {
                sb.append(String.format(java.util.Locale.ROOT, "%s %s %.4f 1.000%n", subCodes[k], subCodes[l], tt[l] - tt[k]));
            }
        }
        Path dat = Files.createTempFile("synthetic", ".dat");
        Files.write(dat, sb.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        double[] withDip = locate(config(dipDir.toString()), dat, "dip");
        double[] horizontal = locate(config(horDir.toString()), dat, "hor");
        double errDip = misfitKm(withDip, trueLat, trueLon, trueDep);
        double errHor = misfitKm(horizontal, trueLat, trueLon, trueDep);
        System.out.printf("synthetic event: dipping-grid error %.2f km, horizontal-grid error %.2f km%n", errDip, errHor);
        assertTrue("dipping grids error " + errDip, errDip < 2.0);
        assertTrue("horizontal grids should be worse: " + errHor + " vs " + errDip, errHor > 2.0 * errDip);
    }

    private static double misfitKm(double[] p, double lat, double lon, double dep) {
        double dx = (p[1] - lon) * 111.19 * Math.cos(Math.toRadians(lat));
        double dy = (p[0] - lat) * 111.19;
        return Math.sqrt(dx * dx + dy * dy + (p[2] - dep) * (p[2] - dep));
    }

    private static double[] locate(AppConfig cfg, Path dat, String tag) throws Exception {
        HypoStationPairDiff solver = new HypoStationPairDiff(cfg);
        Path out = Files.createTempFile("lmo-" + tag, ".dat");
        solver.start(dat.toString(), out.toString());
        String[] f = Files.readAllLines(out).get(0).trim().split("\\s+");
        assertFalse("flagged as outlier: " + String.join(" ", f), String.join(" ", f).contains("ERR"));
        return new double[] {Double.parseDouble(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2])};
    }
}
