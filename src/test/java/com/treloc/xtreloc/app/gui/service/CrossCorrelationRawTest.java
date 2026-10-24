package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import edu.sc.seis.seisFile.sac.SacHeader;
import edu.sc.seis.seisFile.sac.SacTimeSeries;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CrossCorrelationRawTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void computeRawKeepsLength() throws Exception {
        SacTimeSeries sac = synthetic("AAA", "HHZ", 0, 0.2);
        double[] y = CrossCorrelationPickingService.computeRaw(sac, new CrossCorrelationPickingService.Params());
        assertEquals(sac.getY().length, y.length);
    }

    @Test
    public void rawCorrelationWritesDat() throws Exception {
        Path unit = tmp.newFolder("ev").toPath();
        writeSac(unit.resolve("aaa.sac"), "AAA", "HHZ", 0);
        writeSac(unit.resolve("bbb.sac"), "BBB", "HHZ", 15);
        Path out = tmp.newFolder("out").toPath();

        CrossCorrelationPickingService.Params p = new CrossCorrelationPickingService.Params();
        p.signalKind = CrossCorrelationPickingService.SignalKind.RAW;
        p.ccWindowSec = 2.0;
        p.ccOverlapRate = 0.5;
        p.filterFreqMin = null;
        p.filterFreqMax = null;

        CrossCorrelationPickingService.runCorrelationToDir(unit, out, p, Collections.emptyMap(), null);

        int dats = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(out, "*.dat")) {
            for (Path f : ds) {
                dats++;
                String text = Files.readString(f);
                assertTrue(text.contains(" COR\n"));
                assertTrue(text.contains("999.000 999.000 999.000 999.000"));
                assertTrue(text.contains("AAA") && text.contains("BBB"));
            }
        }
        assertTrue(dats > 0);
    }

    @Test
    public void preparedCfCorrelationDoesNotCopyToTemp() throws Exception {
        Path a = tmp.newFolder("cf").toPath().resolve("aaa_cf.sac");
        Path b = tmp.newFolder("cf2").toPath().resolve("bbb_cf.sac");
        writeSac(a, "AAA", "CF", 0);
        writeSac(b, "BBB", "CF", 15);
        Path out = tmp.newFolder("cc").toPath();

        CrossCorrelationPickingService.Params p = new CrossCorrelationPickingService.Params();
        p.treatAsPrepared = true;
        p.ccWindowSec = 2.0;
        p.ccOverlapRate = 0.5;

        java.util.List<String> msgs = new java.util.ArrayList<>();
        int[] last = {-1};
        CrossCorrelationPickingService.runCorrelationFromFiles(Arrays.asList(a, b), out, p,
            Collections.emptyMap(), new CrossCorrelationPickingService.Progress() {
                @Override
                public void onMessage(String message) {
                    msgs.add(message);
                }

                @Override
                public void onProgress(int done, int total, String message) {
                    msgs.add(message);
                    last[0] = done;
                }
            });
        assertTrue("progress must leave window 1", last[0] > 1);
        assertTrue(msgs.stream().anyMatch(m -> m.contains("CC window")));

        int dats = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(out, "*.dat")) {
            for (Path f : ds) {
                dats++;
                assertTrue(Files.readString(f).contains("AAA"));
            }
        }
        assertTrue(dats > 0);
    }

    @Test
    public void correlationDatIsSolverInput_centroidAndCorLabel() throws Exception {
        Path a = tmp.newFolder("cfA").toPath().resolve("aaa.sac");
        Path b = tmp.newFolder("cfB").toPath().resolve("bbb.sac");
        writeSac(a, "AAA", "CF", 0);
        writeSac(b, "BBB", "CF", 15);
        Path out = tmp.newFolder("loc").toPath();

        CrossCorrelationPickingService.Params p = new CrossCorrelationPickingService.Params();
        p.treatAsPrepared = true;
        p.ccWindowSec = 2.0;
        p.ccOverlapRate = 0.5;

        java.util.Map<String, CrossCorrelationPickingService.StationCoord> coords = new java.util.HashMap<>();
        coords.put("AAA", new CrossCorrelationPickingService.StationCoord("AAA", 35.0, 135.0, 1.0, true));
        coords.put("BBB", new CrossCorrelationPickingService.StationCoord("BBB", 35.2, 135.4, 3.0, true));

        CrossCorrelationPickingService.runCorrelationFromFiles(java.util.Arrays.asList(a, b), out, p, coords, null);

        Path dat = null;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(out, "*.dat")) {
            for (Path f : ds) {
                dat = f;
                break;
            }
        }
        assertTrue(dat != null);
        com.treloc.xtreloc.solver.PointsHandler h = new com.treloc.xtreloc.solver.PointsHandler();
        h.readDatFile(dat.toString(), new String[] {"AAA", "BBB"}, 0.0);
        com.treloc.xtreloc.solver.Point pt = h.getMainPoint();
        assertEquals("COR", pt.getType());
        assertEquals(35.1, pt.getLat(), 1e-6);
        assertEquals(135.2, pt.getLon(), 1e-6);
        assertEquals(2.0, pt.getDep(), 1e-6);
        assertEquals(999.0, pt.getElat(), 0.0);
        assertEquals(999.0, pt.getElon(), 0.0);
        assertEquals(999.0, pt.getEdep(), 0.0);
        assertEquals(999.0, pt.getRes(), 0.0);
        assertTrue(pt.getLagTable().length > 0);
    }

    private static SacTimeSeries synthetic(String stn, String cmp, int shift, double amp) {
        int n = 512;
        float[] y = new float[n];
        for (int i = 0; i < n; i++) {
            int k = i - shift;
            y[i] = (float) (k > 40 && k < 80 ? amp * Math.sin((k - 40) * 0.4) : 0.01 * Math.sin(i * 0.05));
        }
        SacHeader h = new SacHeader();
        h.setNpts(n);
        h.setDelta(0.01f);
        h.setKstnm(stn);
        h.setKcmpnm(cmp);
        h.setNzyear(2007);
        h.setNzjday(1);
        h.setNzhour(0);
        h.setNzmin(0);
        h.setNzsec(0);
        h.setNzmsec(0);
        return new SacTimeSeries(h, y);
    }

    private static void writeSac(Path path, String stn, String cmp, int shift) throws Exception {
        synthetic(stn, cmp, shift, 1.0).write(path.toFile());
    }
}
