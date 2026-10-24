package com.treloc.xtreloc.app.gui.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.app.gui.service.ArrivalTime;
import com.treloc.xtreloc.app.gui.util.WorkingLayer;
import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.StationRepository;
import com.treloc.xtreloc.solver.HypoUtils;
import com.treloc.xtreloc.solver.Point;
import com.treloc.xtreloc.solver.table.TravelTimeTable;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class WorkingRelocationControllerTest {

    private static final double TRUE_LAT = 36.05;
    private static final double TRUE_LON = 129.34;
    private static final double TRUE_DEP = 8.0;

    private AppConfig cfg;
    private List<ArrivalTime> picks;

    private void setUpSynthetic() throws Exception {
        Path sta = Files.createTempFile("ctl-sta", ".tbl");
        Files.writeString(sta,
            "STN  36.00  129.20  -100  0  0\n"
                + "STE  36.00  129.50  -100  0  0\n"
                + "STS  35.80  129.35  -100  0  0\n"
                + "STW  36.00  129.10  -100  0  0\n"
                + "STB  36.20  129.35  -100  0  0\n",
            StandardCharsets.US_ASCII);
        cfg = new AppConfig();
        cfg.taupFile = "prem.nd";
        cfg.stationFile = sta.toString();
        cfg.hypBottom = 50;
        cfg.threshold = 0;
        cfg.minDataCount = 3;
        StationRepository repo = StationRepository.load(sta);
        double[] t = new HypoUtils(cfg).travelTime(repo.getStationTable(), new int[] {0, 1, 2, 3, 4},
            new Point("t", TRUE_LAT, TRUE_LON, TRUE_DEP, 0, 0, 0, 0, "", "", -999), TravelTimeTable.Phase.S);
        picks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            picks.add(new ArrivalTime(repo.getCodes()[i], "U", "S",
                new Date(1_510_000_000_000L + Math.round(t[i] * 1000))));
        }
    }

    private static String run(WorkingRelocationController c, EventKey key, List<ArrivalTime> picks) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> msg = new AtomicReference<>();
        javax.swing.SwingUtilities.invokeAndWait(() -> c.relocate(key, picks, m -> {
            msg.set(m);
            latch.countDown();
        }));
        assertTrue("relocation did not finish", latch.await(120, TimeUnit.SECONDS));
        return msg.get();
    }

    @Test
    public void relocationEndsInTheWorkingLayerAndRelocatingAgainReplacesIt() throws Exception {
        System.setProperty("java.awt.headless", "true");
        setUpSynthetic();
        Hypocenter start = new Hypocenter("2017-11-15T05:20:01", TRUE_LAT + 0.02, TRUE_LON - 0.02, TRUE_DEP + 2.0);
        CatalogInfo cat = new CatalogInfo("Woo", Color.RED, CatalogInfo.SymbolType.CIRCLE, List.of(start), null);
        WorkingLayer layer = new WorkingLayer();
        WorkingRelocationController c = new WorkingRelocationController(() -> cfg, () -> "LMO", () -> List.of(cat), layer);
        EventKey key = EventKey.of(cat, start);

        String msg = run(c, key, picks);

        assertTrue(msg, msg.startsWith("LMO:"));
        WorkingLayer.Entry e = layer.get(key);
        assertNotNull(e);
        assertEquals(TRUE_LAT, e.after.lat, 0.01);
        assertEquals(TRUE_LON, e.after.lon, 0.01);
        assertEquals(start, e.before);

        // Working events can be relocated again; the entry still belongs to the same original.
        EventKey workingKey = EventKey.of(WorkingLayer.CATALOG_NAME, e.after);
        run(c, workingKey, picks);
        assertEquals(1, layer.size());
        assertEquals(start, layer.get(key).before);
    }

    @Test
    public void unsupportedModeAndUnknownEventAreReportedWithoutRunning() throws Exception {
        System.setProperty("java.awt.headless", "true");
        Hypocenter h = new Hypocenter("2017-11-15T05:20:01", 36.0, 129.3, 5.0);
        CatalogInfo cat = new CatalogInfo("Woo", Color.RED, CatalogInfo.SymbolType.CIRCLE, List.of(h), null);
        WorkingLayer layer = new WorkingLayer();
        WorkingRelocationController trd = new WorkingRelocationController(() -> null, () -> "TRD", () -> List.of(cat), layer);
        String msg = run(trd, EventKey.of(cat, h), List.of());
        assertTrue(msg, msg.contains("Select LMO"));
        WorkingRelocationController lmo = new WorkingRelocationController(() -> null, () -> "LMO", () -> List.of(), layer);
        assertTrue(run(lmo, EventKey.of(cat, h), List.of()).contains("not in the loaded catalogs"));
        assertEquals(0, layer.size());
    }
}
