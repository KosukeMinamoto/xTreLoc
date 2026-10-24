package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Pins the sign / orientation convention of the manual differential table to the one used by the solver
 * ({@code lagt = t(stnl) - t(stnk)}) and by the cross-correlation output (stnk = later station in sorted order).
 */
public class DifferentialDatWriterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static ArrivalTime pick(String stn, String phase, double sec) {
        return new ArrivalTime(stn, "Z", phase, new Date((long) Math.round(sec * 1000.0)));
    }

    private static List<ArrivalTime> sample() {
        List<ArrivalTime> l = new ArrayList<>();
        l.add(pick("B", "S", 10.0));
        l.add(pick("A", "S", 4.0));
        l.add(pick("C", "S", 7.0));
        l.add(pick("A", "P", 1.0));
        l.add(pick("B", "P", 2.0));
        return l;
    }

    @Test
    public void lagIsTimeOfStnlMinusTimeOfStnk_withStnkTheLaterStation() {
        List<DifferentialDatWriter.Row> rows =
            DifferentialDatWriter.buildPairRows(sample(), "S", null, 0);
        assertEquals(3, rows.size());

        // sorted stations: A, B, C -> (A,B) (A,C) (B,C)
        DifferentialDatWriter.Row ab = rows.get(0);
        assertEquals("B", ab.stnk);
        assertEquals("A", ab.stnl);
        assertEquals(4.0 - 10.0, ab.lagt, 1e-9);

        DifferentialDatWriter.Row ac = rows.get(1);
        assertEquals("C", ac.stnk);
        assertEquals("A", ac.stnl);
        assertEquals(4.0 - 7.0, ac.lagt, 1e-9);

        DifferentialDatWriter.Row bc = rows.get(2);
        assertEquals("C", bc.stnk);
        assertEquals("B", bc.stnl);
        assertEquals(10.0 - 7.0, bc.lagt, 1e-9);

        for (DifferentialDatWriter.Row r : rows) {
            assertEquals(1.0, r.coef, 0.0);
        }
    }

    @Test
    public void onlyRequestedPhaseIsUsed() {
        List<DifferentialDatWriter.Row> rows =
            DifferentialDatWriter.buildPairRows(sample(), "P", null, 0);
        assertEquals(1, rows.size());
        assertEquals("B", rows.get(0).stnk);
        assertEquals("A", rows.get(0).stnl);
        assertEquals(1.0 - 2.0, rows.get(0).lagt, 1e-9);
    }

    @Test
    public void distanceFilterDropsFarAndUnknownStations() {
        Map<String, CrossCorrelationPickingService.StationCoord> coords = new HashMap<>();
        coords.put("A", new CrossCorrelationPickingService.StationCoord("A", 35.0, 135.0, true));
        coords.put("B", new CrossCorrelationPickingService.StationCoord("B", 35.0, 135.1, true));   // ~9 km from A
        coords.put("C", new CrossCorrelationPickingService.StationCoord("C", 35.0, 140.0, true));   // far away

        List<DifferentialDatWriter.Row> rows =
            DifferentialDatWriter.buildPairRows(sample(), "S", coords, 50.0);
        assertEquals(1, rows.size());
        assertEquals("B", rows.get(0).stnk);
        assertEquals("A", rows.get(0).stnl);

        coords.remove("B");
        assertTrue(DifferentialDatWriter.buildPairRows(sample(), "S", coords, 50.0).isEmpty());
    }

    @Test
    public void writesHeaderAndRows() throws Exception {
        Path f = tmp.getRoot().toPath().resolve("out").resolve("t.dat");
        int n = DifferentialDatWriter.writeFromPicks(f, sample(), "S", null, 0);
        assertEquals(3, n);
        List<String> lines = Files.readAllLines(f);
        assertTrue(lines.get(0).endsWith(" COR"));
        assertEquals("999.000 999.000 999.000 999.000", lines.get(1));
        assertEquals("B A -6.000000 1.000000 S", lines.get(2));
        assertEquals(5, lines.size());
    }

    @Test
    public void emptyRowsWriteNothing() throws Exception {
        Path f = tmp.getRoot().toPath().resolve("none.dat");
        List<ArrivalTime> one = new ArrayList<>();
        one.add(pick("A", "S", 1.0));
        assertEquals(0, DifferentialDatWriter.writeFromPicks(f, one, "S", null, 0));
        assertTrue(!Files.exists(f));
    }
}
