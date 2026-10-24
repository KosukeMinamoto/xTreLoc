package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class SacWorkbenchPanelTest {

    @Test
    public void middlePaneHasAbsoluteAndCorrelation() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final SacWorkbenchPanel[] holder = new SacWorkbenchPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new SacWorkbenchPanel(new PickViewerPanel(), new TraceProcessingPanel());
        });
        assertFalse(holder[0].hasModeTabs());
        assertTrue(holder[0].hasSettingsTabs());
        List<String> titles = holder[0].leftSectionTitles();
        assertFalse(titles.contains("Input"));
        assertTrue(titles.contains("Absolute"));
        assertTrue(titles.contains("Correlation"));
        assertEquals(2, titles.size());
    }

    @Test
    public void modeSwitchStartsAbsoluteAndCorrelationTurnsManualEditOff() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final PickViewerPanel[] holder = new PickViewerPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new PickViewerPanel();
            new SacWorkbenchPanel(holder[0], new TraceProcessingPanel());
        });
        PickViewerPanel pick = holder[0];
        assertEquals(PickViewerPanel.Mode.ABSOLUTE, pick.getMode());
        SwingUtilities.invokeAndWait(() -> pick.waveformCanvasForTest().setManualEditEnabled(true));
        assertTrue(pick.isManualEditEnabled());
        SwingUtilities.invokeAndWait(() -> pick.setMode(PickViewerPanel.Mode.CORRELATION));
        assertEquals(PickViewerPanel.Mode.CORRELATION, pick.getMode());
        assertFalse(pick.isManualEditEnabled());
        SwingUtilities.invokeAndWait(() -> pick.setMode(PickViewerPanel.Mode.ABSOLUTE));
        assertEquals(PickViewerPanel.Mode.ABSOLUTE, pick.getMode());
    }

    @org.junit.Rule
    public org.junit.rules.TemporaryFolder tmp = new org.junit.rules.TemporaryFolder();

    private static com.treloc.xtreloc.app.gui.model.CatalogInfo catalogWith(
            com.treloc.xtreloc.app.gui.model.Hypocenter... hs) {
        return new com.treloc.xtreloc.app.gui.model.CatalogInfo("Woo", java.awt.Color.RED,
            com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType.CIRCLE, List.of(hs), null);
    }

    @Test
    public void openEventShowsFolderLinksEventAndEnablesRelocate() throws Exception {
        System.setProperty("java.awt.headless", "true");
        com.treloc.xtreloc.app.gui.model.Hypocenter h =
            new com.treloc.xtreloc.app.gui.model.Hypocenter("2017-11-15T05:20:01", 36.1, 129.3, 5.0);
        com.treloc.xtreloc.app.gui.model.CatalogInfo cat = catalogWith(h);
        com.treloc.xtreloc.app.gui.model.EventKey key = com.treloc.xtreloc.app.gui.model.EventKey.of(cat, h);
        java.io.File dir = tmp.newFolder("171115.052001");
        final PickViewerPanel[] holder = new PickViewerPanel[1];
        final java.util.List<Object> relocated = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new PickViewerPanel();
            new SacWorkbenchPanel(holder[0], new TraceProcessingPanel());
            holder[0].setCatalogsSupplier(() -> List.of(cat));
            holder[0].setRelocateHandler((event, picks, done) -> {
                relocated.add(event);
                done.accept("ok");
            });
            holder[0].openEvent(key, List.of(dir.toPath()), List.of());
        });
        assertEquals(key, holder[0].getCurrentEvent());
        assertEquals(key, com.treloc.xtreloc.app.gui.util.EventSelectionModel.getInstance().focus());
        com.treloc.xtreloc.app.gui.util.EventSelectionModel.getInstance().clear();
    }

    @Test
    public void choosingAFolderNamedLikeAnEventLinksThatEvent() throws Exception {
        System.setProperty("java.awt.headless", "true");
        com.treloc.xtreloc.app.gui.model.Hypocenter h =
            new com.treloc.xtreloc.app.gui.model.Hypocenter("2017-11-15T05:20:01", 36.1, 129.3, 5.0);
        com.treloc.xtreloc.app.gui.model.CatalogInfo cat = catalogWith(h);
        java.io.File linked = tmp.newFolder("171115.052002");
        java.io.File other = tmp.newFolder("sac_misc");
        final PickViewerPanel[] holder = new PickViewerPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new PickViewerPanel();
            new SacWorkbenchPanel(holder[0], new TraceProcessingPanel());
            holder[0].setCatalogsSupplier(() -> List.of(cat));
            holder[0].setInputRoot(linked);
        });
        assertEquals(com.treloc.xtreloc.app.gui.model.EventKey.of(cat, h), holder[0].getCurrentEvent());
        SwingUtilities.invokeAndWait(() -> holder[0].setInputRoot(other));
        org.junit.Assert.assertNull(holder[0].getCurrentEvent());
        com.treloc.xtreloc.app.gui.util.EventSelectionModel.getInstance().clear();
    }

    private static edu.sc.seis.seisFile.sac.SacTimeSeries sac(String stn, String cmp, double onsetSec, long seed) {
        java.util.Random r = new java.util.Random(seed);
        float[] y = new float[6000];
        for (int i = 0; i < y.length; i++) {
            double t = i * 0.01;
            y[i] = (float) r.nextGaussian();
            if (t >= onsetSec) {
                double u = t - onsetSec;
                y[i] += (float) (15 * Math.exp(-u / 1.5) * Math.sin(2 * Math.PI * 5 * u));
            }
        }
        return com.treloc.xtreloc.app.gui.service.TestSac.make(stn, cmp, y);
    }

    @Test
    public void autoPickOnlyProposesUntilAppliedAndUndoRevertsIt() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final PickViewerPanel[] holder = new PickViewerPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new PickViewerPanel();
            new SacWorkbenchPanel(holder[0], new TraceProcessingPanel());
            holder[0].showWaveformsForTest(new edu.sc.seis.seisFile.sac.SacTimeSeries[] {
                sac("ST01", "U", 20.0, 1), sac("ST01", "N", 30.0, 2), sac("ST01", "E", 30.0, 3)});
        });
        PickViewerPanel pick = holder[0];
        for (String method : new String[] {"AIC", "Kurtosis"}) {
            SwingUtilities.invokeAndWait(() -> pick.runAutoPickForTest(method));
            assertEquals(method, 0, pick.pickEditorForTest().size());
            assertFalse(method, pick.pickTableForTest().proposals().isEmpty());
            assertEquals(method, method, pick.pickTableForTest().proposals().get(0).method());

            SwingUtilities.invokeAndWait(pick::applyAllForTest);
            assertTrue(method, pick.pickEditorForTest().size() >= 1);
            assertTrue(method, pick.pickTableForTest().proposals().isEmpty());
            org.junit.Assert.assertNotNull(method, pick.pickEditorForTest().get("ST01", "P"));

            SwingUtilities.invokeAndWait(() -> pick.pickEditorForTest().undo());
            assertEquals(method, 0, pick.pickEditorForTest().size());
        }
    }

    private edu.sc.seis.seisFile.sac.SacTimeSeries[] eqtReferenceTraces() throws Exception {
        float[] in;
        try (java.io.InputStream is = getClass().getResourceAsStream("/eqt/ref_input.f32")) {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(is.readAllBytes())
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            in = new float[bb.remaining() / 4];
            bb.asFloatBuffer().get(in);
        }
        edu.sc.seis.seisFile.sac.SacTimeSeries[] tr = new edu.sc.seis.seisFile.sac.SacTimeSeries[3];
        String[] cmp = {"E", "N", "Z"};
        for (int c = 0; c < 3; c++) {
            edu.sc.seis.seisFile.sac.SacTimeSeries t = sac("ST01", cmp[c], 1e9, 1);
            float[] y = java.util.Arrays.copyOfRange(in, c * 6000, (c + 1) * 6000);
            tr[c] = new edu.sc.seis.seisFile.sac.SacTimeSeries(t.getHeader(), y);
        }
        return tr;
    }

    @Test
    public void eqtMethodSwitchesTheRowsAndProposesAsynchronously() throws Exception {
        System.setProperty("java.awt.headless", "true");
        edu.sc.seis.seisFile.sac.SacTimeSeries[] tr = eqtReferenceTraces();
        final PickViewerPanel[] holder = new PickViewerPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new PickViewerPanel();
            new SacWorkbenchPanel(holder[0], new TraceProcessingPanel());
            holder[0].showWaveformsForTest(tr);
            holder[0].runAutoPickForTest("EQTransformer");
        });
        PickViewerPanel pick = holder[0];
        assertTrue(pick.eqtRowsVisibleForTest());
        long deadline = System.currentTimeMillis() + 120_000;
        while (pick.pickTableForTest().proposals().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertFalse(pick.pickTableForTest().proposals().isEmpty());
        assertEquals("EQTransformer", pick.pickTableForTest().proposals().get(0).method());
        assertEquals(0, pick.pickEditorForTest().size());
        SwingUtilities.invokeAndWait(() -> pick.autoMethodComboForTest().setSelectedItem("AIC"));
        assertFalse(pick.eqtRowsVisibleForTest());
    }

    @Test
    public void allPeaksGoToThePoolAndRepeatingDoesNotDuplicateThem() throws Exception {
        System.setProperty("java.awt.headless", "true");
        edu.sc.seis.seisFile.sac.SacTimeSeries[] tr = eqtReferenceTraces();
        final PickViewerPanel[] holder = new PickViewerPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new PickViewerPanel();
            new SacWorkbenchPanel(holder[0], new TraceProcessingPanel());
            holder[0].showWaveformsForTest(tr);
            holder[0].pickAllToPoolNow();
        });
        PickViewerPanel pick = holder[0];
        waitUntilIdle(pick);
        int first = pick.pickPool().size();
        assertTrue("the reference window has a P and an S peak", first >= 2);
        assertTrue(pick.pickPool().count("P") >= 1);
        assertTrue(pick.pickPool().count("S") >= 1);
        assertEquals("the picks are the pool's, not the pick editor's", 0, pick.pickEditorForTest().size());

        SwingUtilities.invokeAndWait(pick::pickAllToPoolNow);
        waitUntilIdle(pick);
        assertEquals("the same picks again add nothing", first, pick.pickPool().size());
    }

    private static void waitUntilIdle(PickViewerPanel pick) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000;
        Thread.sleep(200);
        while (!pick.autoPickIdleForTest() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertTrue("EQT finished", pick.autoPickIdleForTest());
    }
}
