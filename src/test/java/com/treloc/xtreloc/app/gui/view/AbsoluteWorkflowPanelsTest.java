package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.app.gui.service.AssociationModel;
import com.treloc.xtreloc.app.gui.service.EqtPicker;
import com.treloc.xtreloc.app.gui.service.PickPool;
import com.treloc.xtreloc.app.gui.service.RealAssociator;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AbsoluteWorkflowPanelsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static Date at(int h, int m, double s) {
        return new Date(LocalDateTime.of(2017, 11, 15, h, m, 0).atZone(ZoneId.systemDefault()).toInstant()
            .toEpochMilli() + Math.round(s * 1000));
    }

    private static PickPool pool() {
        PickPool p = new PickPool();
        p.addAll(List.of(
            new EqtPicker.Pick("ST01", "P", "HHZ", at(5, 0, 3.0), 0.9),
            new EqtPicker.Pick("ST02", "P", "HHZ", at(5, 0, 3.25), 0.8),
            new EqtPicker.Pick("ST01", "S", "HHE", at(5, 0, 4.5), 0.7)));
        return p;
    }

    private RealAssociator.Params params() throws Exception {
        Path script = tmp.newFile("REAL").toPath();
        Files.writeString(script, "#!/bin/sh\n"
            + "echo associating\n"
            + "cat > phase_sel.txt <<'EOF'\n"
            + "    1 2017 11 15 05:00:00.500   18000.500  0.1200 36.1000 129.3000   7.00   1.20 0.300   2   1   3   1  95.00\n"
            + "  XX     ST01     P   18003.0000    2.5000 0.00e+00  0.0100   1.0000   0.0000\n"
            + "  XX     ST02     P   18003.2500    2.7500 0.00e+00  0.0300   1.0000   0.0000\n"
            + "  XX     ST01     S   18004.5000    4.0000 0.00e+00 -0.0200   1.0000   0.0000\n"
            + "EOF\n");
        Assume.assumeTrue("needs a POSIX shell", script.toFile().setExecutable(true) && new File("/bin/sh").exists());
        RealAssociator.Params p = new RealAssociator.Params();
        p.executable = script;
        p.stationFile = tmp.newFile("sta.txt").toPath();
        Files.writeString(p.stationFile, "ST01 36.0 129.0\nST02 36.2 129.2\n");
        p.travelTimeTable = tmp.newFile("ttdb.txt").toPath();
        p.workDir = tmp.getRoot().toPath().resolve("work");
        return p;
    }

    @Test
    public void associatePanelRunsRealOnThePoolAndFillsTheModel() throws Exception {
        System.setProperty("java.awt.headless", "true");
        PickPool pool = pool();
        AssociationModel model = new AssociationModel();
        AssociatePanel[] panel = new AssociatePanel[1];
        List<String> status = new ArrayList<>();
        int[] busyCalls = {0};
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new AssociatePanel(pool, model, null);
            panel[0].setStatusSink(status::add);
            panel[0].setBusyListener(() -> busyCalls[0]++);
        });
        SwingUtilities.invokeAndWait(() -> {
            try {
                panel[0].applyParams(params());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals("3 picks (P 2, S 1)", panel[0].poolTextForTest());

        SwingUtilities.invokeAndWait(panel[0]::runNow);
        assertTrue(panel[0].waitForIdle(30_000));
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals(1, model.events().size());
        assertEquals(3, model.events().get(0).phases().size());
        assertTrue(panel[0].logTextForTest(), panel[0].logTextForTest().contains("associating"));
        assertTrue(status.toString(), status.get(status.size() - 1).startsWith("1 event(s) from 3 picks in 1 day(s)"));
        assertTrue("the busy listener heard the start and the end", busyCalls[0] >= 2);
        assertTrue(panel[0].runEnabledForTest());
    }

    @Test
    public void incompleteSettingsAreReportedInTheStatus() throws Exception {
        System.setProperty("java.awt.headless", "true");
        PickPool pool = pool();
        AssociationModel model = new AssociationModel();
        AssociatePanel[] panel = new AssociatePanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new AssociatePanel(pool, model, null));
        // the Run button would open a dialog for the error; the service reports it as an IOException
        try {
            RealAssociator.run(panel[0].readParams(), pool.snapshot(), null);
            org.junit.Assert.fail();
        } catch (java.io.IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("REAL executable"));
        }
        assertEquals(0, model.events().size());
    }

    @Test
    public void resultPanelListsEventsAndTheirPicksAndOffersTheMap() throws Exception {
        System.setProperty("java.awt.headless", "true");
        AssociationModel model = new AssociationModel();
        AssociationResultPanel[] panel = new AssociationResultPanel[1];
        List<Integer> mapped = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new AssociationResultPanel(model);
            panel[0].setMapHandler(events -> mapped.add(events.size()));
        });
        assertEquals(0, panel[0].eventRowsForTest());
        assertFalse(panel[0].mapEnabledForTest());

        RealAssociator.Params p = params();
        RealAssociator.Result r = RealAssociator.run(p, pool().snapshot(), null);
        SwingUtilities.invokeAndWait(() -> model.setEvents(r.events()));
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals(1, panel[0].eventRowsForTest());
        assertEquals("the first event is selected, its picks are listed", 3, panel[0].phaseRowsForTest());
        assertEquals("1 event(s)", panel[0].summaryForTest());
        assertTrue(panel[0].mapEnabledForTest());
        SwingUtilities.invokeAndWait(panel[0]::addToMap);
        assertEquals(List.of(1), mapped);
    }

    @Test
    public void exportPanelWritesTheSelectedFormats() throws Exception {
        System.setProperty("java.awt.headless", "true");
        PickPool pool = pool();
        AssociationModel model = new AssociationModel();
        RealAssociator.Result r = RealAssociator.run(params(), pool.snapshot(), null);
        model.setEvents(r.events());
        AbsoluteExportPanel[] panel = new AbsoluteExportPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new AbsoluteExportPanel(pool, model));
        Path out = tmp.getRoot().toPath().resolve("export");
        SwingUtilities.invokeAndWait(() -> {
            panel[0].setOutputForTest(out.toString());
            panel[0].setFormatsForTest(true, true, true, false, true, true);
            panel[0].exportNow();
        });

        assertTrue(Files.isRegularFile(out.resolve("catalog_real.csv")));
        assertTrue(Files.isRegularFile(out.resolve("real/phase_sel.txt")));
        assertTrue(Files.isRegularFile(out.resolve("picks.csv")));
        assertTrue(Files.isRegularFile(out.resolve("events/171115.050000.obs")));
        assertTrue(Files.isRegularFile(out.resolve("events/171115.050000.dat")));
        assertFalse("pickfile was not selected", Files.list(out.resolve("events"))
            .anyMatch(f -> f.getFileName().toString().endsWith(".pickfile")));
        String csv = Files.readString(out.resolve("catalog_real.csv"));
        assertTrue(csv, csv.contains("events/171115.050000.dat"));
        assertTrue(panel[0].statusForTest(), panel[0].statusForTest().startsWith("Exported to export"));
        assertEquals("1 event(s), 3 picks in the pool", panel[0].summaryForTest());
    }
}
