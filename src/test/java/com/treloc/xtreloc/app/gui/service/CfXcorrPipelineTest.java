package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import edu.sc.seis.seisFile.sac.SacHeader;
import edu.sc.seis.seisFile.sac.SacTimeSeries;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CfXcorrPipelineTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void cfccRunsAllCfThenAllCc_globSkipsOtherMonth() throws Exception {
        Path sac = tmp.newFolder("sac").toPath();
        writeDayHorizontals(sac.resolve("20071201"), 0);
        writeDayHorizontals(sac.resolve("20071202"), 8);
        writeDayHorizontals(sac.resolve("20080101"), 0);
        Path out = tmp.newFolder("out").toPath();

        HypotdBatchConfig cfg = shortWindows();
        cfg.setInputRoot(sac.resolve("200712*").toString());
        cfg.setOutputParent(out.toString());
        cfg.setSkipExisting(true);

        List<String> log = new ArrayList<>();
        CfXcorrPipeline.Result r = CfXcorrPipeline.run(
            CfXcorrPipeline.Phase.CFCC, cfg, null, log::add);

        assertEquals(2, r.days);
        assertEquals(2, r.cfOk);
        assertEquals(2, r.ccOk);
        assertEquals(0, r.cfFailed);
        assertEquals(0, ccFailedOr(r));
        assertTrue("CF phase must finish before CC",
            firstIndex(log, "CC 1/") > firstIndex(log, "CF 2/"));

        assertTrue(Files.exists(out.resolve("CF").resolve("20071201").resolve("AAA_CF.sac")));
        assertTrue(Files.exists(out.resolve("CF").resolve("20071202").resolve("BBB_CF.sac")));
        assertTrue(!Files.exists(out.resolve("CF").resolve("20080101")));
        assertTrue(countDat(out.resolve("Correlation").resolve("20071201")) > 0);
        assertTrue(countDat(out.resolve("Correlation").resolve("20071202")) > 0);
        Path dat = firstDat(out.resolve("Correlation").resolve("20071201"));
        String text = Files.readString(dat);
        assertTrue(text.contains(" COR\n"));
        assertTrue(text.contains("999.000 999.000 999.000 999.000"));
    }

    @Test
    public void resumeSkipsExistingCfAndDat() throws Exception {
        Path sac = tmp.newFolder("raw").toPath();
        writeDayHorizontals(sac.resolve("20071210"), 0);
        Path out = tmp.newFolder("done").toPath();
        HypotdBatchConfig cfg = shortWindows();
        cfg.setInputRoot(sac.toString());
        cfg.setOutputParent(out.toString());

        CfXcorrPipeline.run(CfXcorrPipeline.Phase.CFCC, cfg, null, null);
        Path cf = out.resolve("CF").resolve("20071210").resolve("AAA_CF.sac");
        Path datDir = out.resolve("Correlation").resolve("20071210");
        long cfMtime = Files.getLastModifiedTime(cf).toMillis();
        int dats = countDat(datDir);

        CfXcorrPipeline.Result again = CfXcorrPipeline.run(
            CfXcorrPipeline.Phase.CFCC, cfg, null, null);
        assertEquals(1, again.cfSkipped);
        assertEquals(1, again.ccSkipped);
        assertEquals(0, again.cfOk);
        assertEquals(0, again.ccOk);
        assertEquals(cfMtime, Files.getLastModifiedTime(cf).toMillis());
        assertEquals(dats, countDat(datDir));
    }

    @Test
    public void ccOnlyReadsExistingCfGlob() throws Exception {
        Path cfRoot = tmp.newFolder("CF").toPath();
        writePreparedCf(cfRoot.resolve("20071201"), 0);
        writePreparedCf(cfRoot.resolve("20071202"), 6);
        Path out = tmp.newFolder("ccout").toPath();

        HypotdBatchConfig cfg = shortWindows();
        cfg.setInputRoot(cfRoot.resolve("200712*").toString());
        cfg.setOutputParent(out.toString());

        CfXcorrPipeline.Result r = CfXcorrPipeline.run(
            CfXcorrPipeline.Phase.CC, cfg, null, null);
        assertEquals(2, r.ccOk);
        assertEquals(0, r.cfOk);
        assertTrue(countDat(out.resolve("Correlation").resolve("20071201")) > 0);
    }

    @Test
    public void jsonInputRootGlobResolvesAgainstConfigDir() throws Exception {
        Path work = tmp.newFolder("job").toPath();
        Path sac = work.resolve("sac");
        Files.createDirectories(sac);
        writeDayHorizontals(sac.resolve("20071220"), 0);
        Path out = work.resolve("out");
        HypotdBatchConfig cfg = shortWindows();
        cfg.setInputRoot("sac/200712*");
        cfg.setOutputParent("out");
        Path json = work.resolve("pipeline.json");
        cfg.writeJson(json);

        CfXcorrPipeline.Result r = CfXcorrPipeline.runCli("CFCC", json.toString());
        assertEquals(1, r.cfOk);
        assertEquals(1, r.ccOk);
        assertTrue(Files.isDirectory(out.resolve("CF").resolve("20071220")));
    }

    private static int ccFailedOr(CfXcorrPipeline.Result r) {
        return r.ccFailed;
    }

    private static int firstIndex(List<String> log, String needle) {
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i) != null && log.get(i).contains(needle)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static HypotdBatchConfig shortWindows() {
        HypotdBatchConfig cfg = new HypotdBatchConfig();
        cfg.setEigWindowSec(0.2);
        cfg.setStaWindowSec(0.05);
        cfg.setLtaWindowSec(0.4);
        cfg.setCcWindowSec(0.8);
        cfg.setCcOverlapRate(0.5);
        cfg.setFilterFreqMin(null);
        cfg.setFilterFreqMax(null);
        cfg.setSkipExisting(true);
        return cfg;
    }

    private static void writeDayHorizontals(Path day, int shiftB) throws Exception {
        Files.createDirectories(day);
        writeSac(day.resolve("AAA.N.sac"), "AAA", "HHN", 0);
        writeSac(day.resolve("AAA.E.sac"), "AAA", "HHE", 0);
        writeSac(day.resolve("BBB.N.sac"), "BBB", "HHN", shiftB);
        writeSac(day.resolve("BBB.E.sac"), "BBB", "HHE", shiftB);
    }

    private static void writePreparedCf(Path day, int shiftB) throws Exception {
        Files.createDirectories(day);
        writeSac(day.resolve("AAA_CF.sac"), "AAA", "CF", 0);
        writeSac(day.resolve("BBB_CF.sac"), "BBB", "CF", shiftB);
    }

    private static void writeSac(Path path, String stn, String cmp, int shift) throws Exception {
        int n = 512;
        float[] y = new float[n];
        for (int i = 0; i < n; i++) {
            int k = i - shift;
            y[i] = (float) (k > 40 && k < 80 ? Math.sin((k - 40) * 0.4) : 0.01 * Math.sin(i * 0.05));
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
        new SacTimeSeries(h, y).write(path.toFile());
    }

    private static int countDat(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int n = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.dat")) {
            for (Path ignored : ds) {
                n++;
            }
        }
        return n;
    }

    private static Path firstDat(Path dir) throws Exception {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.dat")) {
            for (Path f : ds) {
                return f;
            }
        }
        return null;
    }
}
