package com.treloc.xtreloc.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.treloc.xtreloc.io.AppConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;

/**
 * LMO must walk toward a synthetic truth and must not fly when started at a good Pohang location.
 * MCMC/GRD do not use the Jacobian; a good GRD start that LMO then ejects is an implementation bug.
 */
public class LmoRecoveryTest {

    @Test
    public void recoversASyntheticEventFromANearbyStart() throws Exception {
        Path sta = Files.createTempFile("lmo-sta", ".tbl");
        Files.writeString(sta,
            "STN  36.00  129.20  -100  0  0\n"
                + "STE  36.00  129.50  -100  0  0\n"
                + "STS  35.80  129.35  -100  0  0\n"
                + "STW  36.00  129.10  -100  0  0\n"
                + "STB  36.20  129.35  -100  0  0\n",
            StandardCharsets.US_ASCII);
        AppConfig cfg = config(sta.toString());
        HypoUtils utils = new HypoUtils(cfg);
        com.treloc.xtreloc.io.StationRepository repo =
            com.treloc.xtreloc.io.StationRepository.load(sta);
        double[][] stn = repo.getStationTable();
        int[] idx = {0, 1, 2, 3, 4};
        double trueLat = 36.05;
        double trueLon = 129.34;
        double trueDep = 8.0;
        double[] t = utils.travelTime(stn, idx,
            new Point("t", trueLat, trueLon, trueDep, 0, 0, 0, 0, "", "", -999),
            com.treloc.xtreloc.solver.table.TravelTimeTable.Phase.S);
        String[] codes = repo.getCodes();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.ROOT, "%.5f %.5f %.3f SYN%n",
            trueLat + 0.02, trueLon - 0.02, trueDep + 2.0));
        sb.append("0.000 0.000 0.000 1.000\n");
        for (int k = 0; k < idx.length; k++) {
            for (int l = k + 1; l < idx.length; l++) {
                sb.append(String.format(java.util.Locale.ROOT, "%s %s %.5f 1.000 S%n",
                    codes[k], codes[l], t[l] - t[k]));
            }
        }
        Path dat = Files.createTempFile("lmo-syn", ".dat");
        Files.writeString(dat, sb.toString(), StandardCharsets.US_ASCII);
        double[] got = locate(cfg, dat);
        assertEquals("lat", trueLat, got[0], 0.01);
        assertEquals("lon", trueLon, got[1], 0.01);
        assertEquals("dep", trueDep, got[2], 2.5);
    }

    @Test
    public void pohangLmoFromCatalogHeaderDoesNotFly() throws Exception {
        Path demo = Paths.get("demo/pohang2017");
        Path dat = demo.resolve("dat/171115.052931.dat");
        Path sta = demo.resolve("station.tbl");
        assumeTrue(Files.isRegularFile(dat) && Files.isRegularFile(sta));
        List<String> lines = Files.readAllLines(dat);
        String[] head = lines.get(0).trim().split("\\s+");
        double lat0 = Double.parseDouble(head[0]);
        double lon0 = Double.parseDouble(head[1]);
        double dep0 = Double.parseDouble(head[2]);
        AppConfig cfg = config(sta.toString());
        cfg.hypBottom = 12;
        cfg.taupFile = demo.resolve("pohang.nd").toString();
        if (!Files.isRegularFile(Paths.get(cfg.taupFile))) {
            cfg.taupFile = "prem.nd";
        }
        double[] got = locate(cfg, dat);
        double dLonKm = (got[1] - lon0) * 111.32 * Math.cos(Math.toRadians(lat0));
        double dLatKm = (got[0] - lat0) * 111.32;
        double dDep = got[2] - dep0;
        double shift = Math.sqrt(dLonKm * dLonKm + dLatKm * dLatKm + dDep * dDep);
        assertTrue("LMO flew " + shift + " km from GRD/catalog start " + lat0 + " " + lon0 + " " + dep0
                + " -> " + got[0] + " " + got[1] + " " + got[2],
            shift < 8.0);
    }

    private static AppConfig config(String stationFile) {
        AppConfig cfg = new AppConfig();
        cfg.taupFile = "prem.nd";
        cfg.stationFile = stationFile;
        cfg.hypBottom = 50;
        cfg.threshold = 0;
        cfg.minDataCount = 3;
        return cfg;
    }

    private static double[] locate(AppConfig cfg, Path dat) throws Exception {
        HypoStationPairDiff solver = new HypoStationPairDiff(cfg);
        Path out = Files.createTempFile("lmo-out", ".dat");
        solver.start(dat.toString(), out.toString());
        String[] f = Files.readAllLines(out).get(0).trim().split("\\s+");
        return new double[] {
            Double.parseDouble(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2])
        };
    }
}
