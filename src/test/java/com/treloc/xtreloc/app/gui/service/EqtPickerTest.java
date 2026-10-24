package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import edu.sc.seis.seisFile.sac.SacTimeSeries;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Uses a 60 s window and the PyTorch output saved when the model was exported to ONNX: the Java
 * pipeline (windowing, merge, segmentation, peak picking) must find the same P and S peaks.
 */
public class EqtPickerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static float[] floats(String resource) throws IOException {
        try (InputStream in = EqtPickerTest.class.getResourceAsStream(resource)) {
            ByteBuffer bb = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            float[] f = new float[bb.remaining() / 4];
            bb.asFloatBuffer().get(f);
            return f;
        }
    }

    /** E, N, Z traces made of {@code repeat} copies of the reference window. */
    private static SacTimeSeries[] traces(int repeat) throws IOException {
        float[] in = floats("/eqt/ref_input.f32");
        SacTimeSeries[] out = new SacTimeSeries[3];
        String[] cmp = {"E", "N", "Z"};
        for (int c = 0; c < 3; c++) {
            float[] y = new float[6000 * repeat];
            for (int r = 0; r < repeat; r++) {
                System.arraycopy(in, c * 6000, y, r * 6000, 6000);
            }
            out[c] = TestSac.make("ST01", cmp[c], y, 2017, 319, 5, 20);
        }
        return out;
    }

    private static int argmax(float[] a, int row) {
        int best = 0;
        for (int i = 1; i < 6000; i++) {
            if (a[row * 6000 + i] > a[row * 6000 + best]) {
                best = i;
            }
        }
        return best;
    }

    private static EqtPicker.Params noFilter() {
        EqtPicker.Params p = new EqtPicker.Params();
        p.filter = false;
        return p;
    }

    @Test
    public void findsTheSamePeaksAsTheReferenceModel() throws Exception {
        float[] ref = floats("/eqt/ref_output.f32");
        long t0 = WaveProcessor.startEpochMillis(traces(1)[0]);
        try (EqtPicker picker = new EqtPicker(noFilter())) {
            List<EqtPicker.Pick> best = EqtPicker.best(picker.pickAll(traces(1), null));
            assertEquals(2, best.size());
            for (EqtPicker.Pick p : best) {
                int row = p.phase().equals("P") ? 1 : 2;
                int idx = argmax(ref, row);
                assertEquals(p.phase(), idx * 10.0, p.time().getTime() - t0, 25.0);
                assertEquals(p.phase(), ref[row * 6000 + idx], p.prob(), 0.02);
            }
        }
    }

    @Test
    public void longRecordsGiveEveryPeakOnceAcrossSegments() throws Exception {
        float[] ref = floats("/eqt/ref_output.f32");
        EqtPicker.Params params = noFilter();
        params.segmentSec = 60; // 6000-sample segments: every picks crosses a segment boundary
        SacTimeSeries[] tr = traces(3);
        long t0 = WaveProcessor.startEpochMillis(tr[0]);
        try (EqtPicker picker = new EqtPicker(params)) {
            List<EqtPicker.Pick> all = picker.pickAll(tr, null);
            for (String phase : new String[] {"P", "S"}) {
                int idx = argmax(ref, phase.equals("P") ? 1 : 2);
                for (int r = 0; r < 3; r++) {
                    double expected = (r * 6000 + idx) * 10.0;
                    long n = all.stream()
                        .filter(p -> p.phase().equals(phase))
                        .filter(p -> Math.abs(p.time().getTime() - t0 - expected) < 500)
                        .count();
                    assertEquals(phase + " in copy " + r, 1, n);
                }
            }
            for (int i = 1; i < all.size(); i++) {
                assertTrue("sorted by time", !all.get(i).time().before(all.get(i - 1).time()));
            }
        }
    }

    @Test
    public void stationWithoutThreeComponentsIsSkippedWithAReason() throws Exception {
        SacTimeSeries[] tr = traces(1);
        try (EqtPicker picker = new EqtPicker(noFilter())) {
            List<EqtPicker.Pick> out = picker.pickAll(new SacTimeSeries[] {tr[0], tr[2]}, null);
            assertTrue(out.isEmpty());
            assertEquals(1, picker.skipped().size());
            assertTrue(picker.skipped().get(0).startsWith("ST01"));
        }
    }

    @Test
    public void autoPickerProposesOnePickPerPhaseWithProbability() throws Exception {
        List<PickProposal> proposals = AutoPicker.eqt(noFilter()).propose(traces(2));
        assertEquals(2, proposals.size());
        for (PickProposal p : proposals) {
            assertEquals("EQTransformer", p.method());
            assertTrue(p.score() > 0.1 && p.score() <= 1.0);
            assertEquals(p.phase().equals("P") ? "Z" : "E", p.component());
        }
    }

    @Test
    public void writesCsvAndRealFiles() throws Exception {
        long base = WaveProcessor.startEpochMillis(traces(1)[0]);
        List<EqtPicker.Pick> picks = List.of(
            new EqtPicker.Pick("ST01", "S", "E", new java.util.Date(base + 20_000), 0.5),
            new EqtPicker.Pick("ST01", "P", "Z", new java.util.Date(base + 10_500), 0.9));
        Path dir = tmp.newFolder("real").toPath();
        assertEquals(2, EqtPickWriter.writeReal(dir, "KG", picks));
        // 2017 day 319 = 2017-11-15, station time 05:20:10.500 -> 19210.500 s since midnight
        List<String> lines = Files.readAllLines(dir.resolve("20171115").resolve("KG.ST01.P.txt"));
        assertEquals(List.of("19210.500 0.9000 0.0"), lines);
        assertEquals(List.of("19220.000 0.5000 0.0"),
            Files.readAllLines(dir.resolve("20171115").resolve("KG.ST01.S.txt")));

        Path csv = dir.resolve("picks.csv");
        EqtPickWriter.writeCsv(csv, picks);
        List<String> c = Files.readAllLines(csv);
        assertEquals("station,phase,component,time,prob", c.get(0));
        assertEquals("ST01,P,Z,2017-11-15T05:20:10.500,0.9000", c.get(1));
    }
}
