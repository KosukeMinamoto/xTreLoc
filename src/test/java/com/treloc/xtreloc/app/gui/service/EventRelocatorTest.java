package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.StationRepository;
import com.treloc.xtreloc.solver.HypoUtils;
import com.treloc.xtreloc.solver.Point;
import com.treloc.xtreloc.solver.table.TravelTimeTable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.Test;

public class EventRelocatorTest {

    private static AppConfig config(Path sta) {
        AppConfig cfg = new AppConfig();
        cfg.taupFile = "prem.nd";
        cfg.stationFile = sta.toString();
        cfg.hypBottom = 50;
        cfg.threshold = 0;
        cfg.minDataCount = 3;
        return cfg;
    }

    private static Path stations() throws Exception {
        Path sta = Files.createTempFile("reloc-sta", ".tbl");
        Files.writeString(sta,
            "STN  36.00  129.20  -100  0  0\n"
                + "STE  36.00  129.50  -100  0  0\n"
                + "STS  35.80  129.35  -100  0  0\n"
                + "STW  36.00  129.10  -100  0  0\n"
                + "STB  36.20  129.35  -100  0  0\n",
            StandardCharsets.US_ASCII);
        return sta;
    }

    @Test
    public void picksPullTheEventToTheTruthFromTheCatalogStart() throws Exception {
        Path sta = stations();
        AppConfig cfg = config(sta);
        StationRepository repo = StationRepository.load(sta);
        double[][] stn = repo.getStationTable();
        String[] codes = repo.getCodes();
        int[] idx = {0, 1, 2, 3, 4};
        double trueLat = 36.05;
        double trueLon = 129.34;
        double trueDep = 8.0;
        double[] t = new HypoUtils(cfg).travelTime(stn, idx,
            new Point("t", trueLat, trueLon, trueDep, 0, 0, 0, 0, "", "", -999), TravelTimeTable.Phase.S);

        long t0 = 1_510_000_000_000L;
        List<ArrivalTime> picks = new ArrayList<>();
        for (int i = 0; i < idx.length; i++) {
            picks.add(new ArrivalTime(codes[i], "U", "S", new Date(t0 + Math.round(t[i] * 1000))));
        }
        Hypocenter catalog = new Hypocenter("2017-11-15T05:20:01", trueLat + 0.02, trueLon - 0.02, trueDep + 2.0);

        EventRelocator.Result r = EventRelocator.relocate(cfg, "LMO", catalog, picks, Collections.emptyMap(), 0);

        assertEquals(10, r.pairRows);
        assertEquals("time label is kept", catalog.time, r.hypocenter.time);
        assertEquals(trueLat, r.hypocenter.lat, 0.01);
        assertEquals(trueLon, r.hypocenter.lon, 0.01);
        assertEquals(trueDep, r.hypocenter.depth, 2.5);
    }

    @Test
    public void needsTwoStationsAndASupportedMode() throws Exception {
        AppConfig cfg = config(stations());
        Hypocenter h = new Hypocenter("2017-11-15T05:20:01", 36.0, 129.3, 5.0);
        List<ArrivalTime> one = List.of(new ArrivalTime("STN", "U", "P", new Date()));
        try {
            EventRelocator.relocate(cfg, "LMO", h, one, Collections.emptyMap(), 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("two or more stations"));
        }
        try {
            EventRelocator.relocate(cfg, "TRD", h, one, Collections.emptyMap(), 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("TRD"));
        }
    }
}
