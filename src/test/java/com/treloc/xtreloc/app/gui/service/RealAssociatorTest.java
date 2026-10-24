package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RealAssociatorTest {

    private int counter;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static Date at(int day, int h, int m, double s) {
        long ms = LocalDateTime.of(2017, 11, day, h, m, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            + Math.round(s * 1000);
        return new Date(ms);
    }

    private static EqtPicker.Pick pick(String sta, String phase, String cmp, Date t) {
        return new EqtPicker.Pick(sta, phase, cmp, t, 0.8);
    }

    /** A stand-in for REAL: records its arguments and writes a fixed phase_sel.txt for the day it was given. */
    private Path fakeReal() throws IOException {
        Path script = tmp.newFile("REAL" + (counter++)).toPath();
        Files.writeString(script, "#!/bin/sh\n"
            + "echo \"$@\" > args.txt\n"
            + "echo running\n"
            + "cat > phase_sel.txt <<'EOF'\n"
            + "    1 2017 11 15 05:00:00.500   18000.500  0.1200 36.1000 129.3000   7.00   1.20 0.300   2   1   3   1  95.00\n"
            + "  XX     ST01     P   18003.0000    2.5000 0.00e+00  0.0100   1.0000   0.0000\n"
            + "  XX     ST01     S   18004.5000    4.0000 0.00e+00 -0.0200   1.0000   0.0000\n"
            + "  XX     ST02     P   18003.2500    2.7500 0.00e+00  0.0300   1.0000   0.0000\n"
            + "    2 2017 11 15 06:30:10.000   23410.000  0.2000 36.2000 129.4000  10.00   0.00 0.000   1   0   1   0 120.00\n"
            + "  XX     ST01     P   23412.0000    2.0000 0.00e+00  0.0000   1.0000   0.0000\n"
            + "EOF\n");
        Assume.assumeTrue("needs a POSIX shell", script.toFile().setExecutable(true) && new File("/bin/sh").exists());
        return script;
    }

    private RealAssociator.Params params() throws IOException {
        RealAssociator.Params p = new RealAssociator.Params();
        p.executable = fakeReal();
        p.stationFile = tmp.newFile("sta" + (counter++) + ".txt").toPath();
        Files.writeString(p.stationFile, "ST01 36.0 129.0\nST02 36.2 129.2\n");
        p.travelTimeTable = tmp.newFile("ttdb" + (counter++) + ".txt").toPath();
        p.workDir = tmp.getRoot().toPath().resolve("work");
        p.network = "XX";
        return p;
    }

    private List<EqtPicker.Pick> picks() {
        return List.of(
            pick("ST01", "P", "HHZ", at(15, 5, 0, 3.0)), pick("ST01", "S", "HHE", at(15, 5, 0, 4.5)),
            pick("ST02", "P", "HHZ", at(15, 5, 0, 3.25)), pick("ST01", "P", "HHZ", at(15, 6, 30, 12.0)));
    }

    @Test
    public void runsRealOncePerDayAndParsesTheEvents() throws Exception {
        List<String> log = new ArrayList<>();
        RealAssociator.Result r = RealAssociator.run(params(), picks(), log::add);

        assertEquals(1, r.days());
        assertEquals(2, r.events().size());
        assertTrue(log.contains("running"));
        RealAssociator.Event e1 = r.events().get(0);
        assertEquals(1, e1.id());
        assertEquals(at(15, 5, 0, 0.5), e1.origin());
        assertEquals(36.1, e1.lat(), 1e-9);
        assertEquals(129.3, e1.lon(), 1e-9);
        assertEquals(7.0, e1.depth(), 1e-9);
        assertEquals(3, e1.phases().size());
        assertEquals(2, e1.np());
        RealAssociator.Phase s = e1.phases().get(1);
        assertEquals("S", s.phase());
        assertEquals(at(15, 5, 0, 4.5), s.time());
        assertEquals("the component comes back from the pool pick", "HHE", s.component());
        assertEquals(-0.02, s.residual(), 1e-9);
        assertEquals(2, r.events().get(1).id());
    }

    @Test
    public void commandLineFollowsRealsUsage() throws Exception {
        RealAssociator.Params p = params();
        RealAssociator.run(p, picks(), null);
        String args = Files.readString(p.workDir.resolve("out/20171115/args.txt")).trim();
        assertTrue(args, args.startsWith("-D2017/11/15/36.1000 -R0.1/20/0.04/2/5 -G1.4/20/0.01/1 -S3/2/12/3/"));
        assertTrue(args, args.contains(" -V6.2/3.3 "));
        assertTrue(args, args.matches(".*ttdb\\d+\\.txt"));
        assertTrue(args, args.contains("station.dat " + p.workDir.toAbsolutePath() + "/picks/20171115 "));
        // REAL's own input layout: lon lat net sta comp elevation
        String dat = Files.readString(p.workDir.resolve("station.dat"));
        assertTrue(dat, dat.contains("129.00000 36.00000 XX ST01 HHZ"));
        assertTrue(Files.isRegularFile(p.workDir.resolve("picks/20171115/XX.ST01.P.txt")));
    }

    @Test
    public void picksOfTwoDaysRunTwice() throws Exception {
        List<EqtPicker.Pick> two = new ArrayList<>(picks());
        two.add(pick("ST01", "P", "HHZ", at(16, 1, 0, 0)));
        RealAssociator.Result r = RealAssociator.run(params(), two, null);
        assertEquals(2, r.days());
        assertEquals(4, r.events().size());
        assertEquals(List.of(1, 2, 3, 4), r.events().stream().map(RealAssociator.Event::id).toList());
    }

    @Test
    public void incompleteInputIsReportedBeforeRunning() throws Exception {
        RealAssociator.Params p = params();
        p.executable = tmp.getRoot().toPath().resolve("missing");
        try {
            RealAssociator.run(p, picks(), null);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("REAL executable"));
        }
        try {
            RealAssociator.run(params(), List.of(), null);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("pool is empty"));
        }
    }

    @Test
    public void failingRealIsReportedWithItsLastOutput() throws Exception {
        RealAssociator.Params p = params();
        Files.writeString(p.executable, "#!/bin/sh\necho boom >&2\nexit 3\n");
        try {
            RealAssociator.run(p, picks(), null);
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("code 3"));
            assertTrue(e.getMessage(), e.getMessage().contains("boom"));
        }
    }

    @Test
    public void writtenRealFilesReadBackAsTheSameEvents() throws Exception {
        RealAssociator.Result r = RealAssociator.run(params(), picks(), null);
        Path out = tmp.getRoot().toPath().resolve("export");
        RealResultWriter.writeRealFiles(out, r.events());

        Map<String, java.util.TreeMap<Long, EqtPicker.Pick>> index = RealAssociator.indexPicks(picks());
        List<RealAssociator.Event> back = RealAssociator.parsePhaseSel(out.resolve("phase_sel.txt"),
            LocalDateTime.of(2017, 11, 15, 0, 0).toLocalDate(), "XX", index);
        assertEquals(r.events().size(), back.size());
        assertEquals(r.events().get(0).origin(), back.get(0).origin());
        assertEquals(r.events().get(0).phases().get(2).time(), back.get(0).phases().get(2).time());
        assertEquals(r.events().get(0).lat(), back.get(0).lat(), 1e-4);
        assertEquals(Files.readAllLines(out.resolve("catalog_sel.txt")).size(), 2);
    }

    @Test
    public void catalogCsvLoadsInTheModelPage() throws Exception {
        RealAssociator.Result r = RealAssociator.run(params(), picks(), null);
        Path csv = tmp.getRoot().toPath().resolve("cat/real.csv");
        Map<Integer, String> dats = new HashMap<>();
        dats.put(1, "171115.050000.dat");
        RealResultWriter.writeCatalogCsv(csv, r.events(), dats);

        var loaded = CatalogLoader.load(csv.toFile());
        assertEquals(2, loaded.size());
        assertEquals("2017-11-15T05:00:00", loaded.get(0).time);
        assertEquals(36.1, loaded.get(0).lat, 1e-4);
        assertEquals(7.0, loaded.get(0).depth, 1e-3);
        assertEquals("REAL", loaded.get(0).type);
        assertEquals("171115.050000.dat", loaded.get(0).datFilePath);
        assertEquals(2, RealResultWriter.toHypocenters(r.events()).size());
    }

    @Test
    public void eventFilesCarryTheAssociatedPicks() throws Exception {
        RealAssociator.Result r = RealAssociator.run(params(), picks(), null);
        Path dir = tmp.getRoot().toPath().resolve("events");
        RealResultWriter.EventFiles opt = new RealResultWriter.EventFiles();
        opt.winPickfile = true;
        Map<Integer, String> dats = new HashMap<>();

        RealResultWriter.Counts c = RealResultWriter.writeEventFiles(dir, r.events(), opt, dats);

        assertEquals(2, c.obs());
        assertEquals(2, c.pickfiles());
        assertEquals("event 1 has P at two stations, event 2 at one", 1, c.dats());
        assertEquals(1, c.skippedDats());
        assertEquals("171115.050000.dat", dats.get(1));
        List<String> dat = Files.readAllLines(dir.resolve("171115.050000.dat"));
        assertTrue(dat.get(0), dat.get(0).startsWith("36.100 129.300 7.000 COR"));
        assertEquals("one P pair of ST01-ST02", 3, dat.size());
        assertTrue(Files.readString(dir.resolve("171115.050000.obs")).contains("ST01"));
    }

    @Test
    public void pickPoolIgnoresDuplicatesAndCountsPhases() {
        PickPool pool = new PickPool();
        int[] heard = {0};
        pool.addListener(p -> heard[0]++);
        assertEquals(4, pool.addAll(picks()));
        assertEquals("the same picks again add nothing", 0, pool.addAll(picks()));
        assertEquals(1, heard[0]);
        assertEquals(3, pool.count("P"));
        assertEquals(1, pool.count("S"));
        pool.clear();
        assertEquals(0, pool.size());
        assertEquals(2, heard[0]);
    }

    @Test
    public void picksCsvRoundTrips() throws Exception {
        Path f = tmp.getRoot().toPath().resolve("picks.csv");
        EqtPickWriter.writeCsv(f, picks());
        List<EqtPicker.Pick> back = EqtPickWriter.readCsv(f);
        assertEquals(4, back.size());
        assertEquals(at(15, 5, 0, 3.0), back.get(0).time());
        assertEquals("sorted by time: the S pick is last", "HHE", back.get(2).component());
        assertEquals(0.8, back.get(0).prob(), 1e-4);
    }
}
