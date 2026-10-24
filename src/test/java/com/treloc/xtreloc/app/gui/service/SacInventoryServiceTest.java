package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import edu.sc.seis.seisFile.sac.SacHeader;
import edu.sc.seis.seisFile.sac.SacTimeSeries;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SacInventoryServiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void groupsComponentsAndPicksH1H2() throws Exception {
        Path dir = tmp.newFolder("ev").toPath();
        write(dir.resolve("a.ud.sac"), "AAA", "UD", 0.01f, 64, 0f, 0.63f);
        write(dir.resolve("a.n.sac"), "AAA", "N", 0.01f, 64, 0f, 0.63f);
        write(dir.resolve("a.e.sac"), "AAA", "E", 0.01f, 64, 0f, 0.63f);
        write(dir.resolve("b.h1.sac"), "BBB", "H1", 0.02f, 64, 0f, 1.26f);
        write(dir.resolve("b.h2.sac"), "BBB", "HA", 0.02f, 64, 0f, 1.26f);

        List<SacInventoryService.StationRow> rows = SacInventoryService.scan(dir);
        assertEquals(2, rows.size());
        SacInventoryService.StationRow a = rows.stream().filter(r -> "AAA".equals(r.station)).findFirst().orElseThrow();
        assertTrue(a.componentList().contains("UD"));
        assertEquals("UD", a.ud);
        assertEquals("N", a.h1);
        assertEquals("E", a.h2);
        assertTrue(a.selected.contains("UD"));
        assertTrue(a.use);
        assertEquals(List.of("UD", "N", "E"), SacInventoryService.uniqueComponentNames(List.of(a)));
        assertFalse(a.mixedRate());
        assertEquals("100.000 Hz", a.rateText());
    }

    @Test
    public void readsHaHbAndFlagsHeaderLengthMismatch() throws Exception {
        Path dir = tmp.newFolder("hinet").toPath();
        write(dir.resolve("ml19.ha.sac"), "ML19", "Ha", 0.005f, 64, 0f, 0.315f);
        write(dir.resolve("ml19.hb.sac"), "ML19", "Hb", 0.005f, 64, 0f, 0.315f);
        write(dir.resolve("ml19.ud.sac"), "ML19", "UD", 0.005f, 64, 0f, 10f);

        List<SacInventoryService.StationRow> rows = SacInventoryService.scan(dir);
        assertEquals(1, rows.size());
        SacInventoryService.StationRow r = rows.get(0);
        assertEquals("ML19", r.station);
        assertEquals("UD", r.ud);
        assertEquals("Ha", r.h1);
        assertEquals("Hb", r.h2);
        assertTrue(r.use);
        assertTrue(r.usesComponent("UD", java.util.Set.of()));
        assertFalse(r.usesComponent("UD", java.util.Set.of("UD")));
        assertEquals(List.of("UD", "Ha", "Hb"), SacInventoryService.uniqueComponentNames(rows));
        assertEquals(SacComponentClassifier.Role.H1, r.components.stream()
            .filter(c -> "Ha".equals(c.name)).findFirst().orElseThrow().role);
        assertEquals(SacComponentClassifier.Role.H2, r.components.stream()
            .filter(c -> "Hb".equals(c.name)).findFirst().orElseThrow().role);
        assertEquals("200.000 Hz", r.rateText());
    }

    @Test
    public void headerLengthMismatchUsesBE() {
        SacHeader h = new SacHeader();
        h.setB(0f);
        h.setE(86399.995f);
        assertTrue(SacInventoryService.headerLengthMismatch(h, 100, 0.005));
        assertFalse(SacInventoryService.headerLengthMismatch(h, 17280000, 0.005));
    }

    private static void write(Path path, String stn, String cmp, float delta, int n, float b, float e)
            throws Exception {
        float[] y = new float[n];
        for (int i = 0; i < n; i++) {
            y[i] = 0.2f * (float) Math.sin(i * 0.3);
        }
        SacHeader h = new SacHeader();
        h.setNpts(n);
        h.setDelta(delta);
        h.setB(b);
        h.setE(e);
        h.setKstnm(stn);
        h.setKcmpnm(cmp);
        h.setNzyear(2007);
        h.setNzjday(1);
        h.setNzhour(0);
        h.setNzmin(0);
        h.setNzsec(0);
        h.setNzmsec(0);
        new SacTimeSeries(h, y).write(path.toFile());
    }
}
