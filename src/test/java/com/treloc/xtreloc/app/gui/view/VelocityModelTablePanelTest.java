package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.app.gui.model.VelocityModelSpec;
import com.treloc.xtreloc.app.gui.util.SharedFileManager;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class VelocityModelTablePanelTest {

    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    @Test
    public void comboSelectsBundledModel_andApplyToSolverWritesTaup() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final VelocityModelTablePanel[] p = new VelocityModelTablePanel[1];
        final List<VelocityModelSpec> events = new ArrayList<>();
        edt(() -> {
            SharedFileManager.getInstance().setTaupFile(null);
            p[0] = new VelocityModelTablePanel();
            p[0].addVisualizeListener(events::add);
            p[0].setApplyToSolver(true);
            p[0].selectModel("ak135.tvel");
        });
        assertNotNull(p[0].getVisualized());
        assertEquals("ak135.tvel", p[0].getVisualized().ref);
        assertEquals(VelocityModelSpec.Kind.MODEL_1D, p[0].getVisualized().kind);
        assertEquals("ak135.tvel", SharedFileManager.getInstance().getTaupFile());
        assertEquals(1, events.size());

        edt(() -> p[0].selectModel("ak135.tvel"));
        assertEquals("same model again does not re-notify", 1, events.size());
        edt(() -> p[0].selectModel("Select from file..."));
        assertEquals("ak135.tvel", p[0].getVisualized().ref);
    }

    @Test
    public void travelTimeModeDoesNotBindVelocityChangesToSolver() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final VelocityModelTablePanel[] p = new VelocityModelTablePanel[1];
        edt(() -> {
            p[0] = new VelocityModelTablePanel();
            p[0].setApplyToSolver(true);
            p[0].selectModel("prem.nd");
        });
        assertEquals("prem.nd", SharedFileManager.getInstance().getTaupFile());
        edt(() -> {
            p[0].setApplyToSolver(false);
            p[0].selectModel("ak135.tvel");
        });
        assertEquals("ak135.tvel", p[0].getVisualized().ref);
        assertEquals("travel-time source does not rebind velocity to the solver",
            "prem.nd", SharedFileManager.getInstance().getTaupFile());
        edt(() -> p[0].comboForTest().setSelectedItem(VelocityModelTablePanel.NAKAJIMA_ITEM));
        assertEquals(VelocityModelSpec.Kind.BUNDLED_3D, p[0].getVisualized().kind);
        assertEquals("prem.nd", SharedFileManager.getInstance().getTaupFile());
    }

    @Test
    public void defaultHasNoModelAndDoesNotWriteSolver() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final VelocityModelTablePanel[] p = new VelocityModelTablePanel[1];
        edt(() -> {
            SharedFileManager.getInstance().setTaupFile("keep-me");
            p[0] = new VelocityModelTablePanel();
        });
        assertEquals(null, p[0].getVisualized());
        assertFalse(p[0].isApplyToSolver());
        assertEquals("keep-me", SharedFileManager.getInstance().getTaupFile());
        edt(() -> p[0].setApplyToSolver(true));
        assertEquals("keep-me", SharedFileManager.getInstance().getTaupFile());
    }

    @Test
    public void stationPathNotifiesListener() throws Exception {
        System.setProperty("java.awt.headless", "true");
        File tbl = Files.createTempFile("vel-station", ".tbl").toFile();
        Files.writeString(tbl.toPath(), "AAA 35.0 140.0 0.0 0.0 0.0\n");
        final VelocityModelTablePanel[] p = new VelocityModelTablePanel[1];
        final File[] got = new File[1];
        edt(() -> {
            p[0] = new VelocityModelTablePanel();
            p[0].addStationFileListener(f -> got[0] = f);
            p[0].applyStationFile(tbl);
        });
        assertNotNull(got[0]);
        assertEquals(tbl.getAbsolutePath(), got[0].getAbsolutePath());
        assertEquals(tbl.getAbsolutePath(), p[0].getStationPath());
        tbl.delete();
    }

    @Test
    public void specFromFile_detectsGridsByExtension() {
        assertTrue(VelocityModelSpec.fromFile(new java.io.File("/tmp/m.csv.gz")).is3D());
        assertTrue(VelocityModelSpec.fromFile(new java.io.File("/tmp/m.csv")).is3D());
        assertEquals(VelocityModelSpec.Kind.MODEL_1D, VelocityModelSpec.fromFile(new java.io.File("/tmp/m.nd")).kind);
    }
}
