package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.swing.SwingUtilities;
import com.treloc.xtreloc.app.gui.model.VelocityModelSpec;
import org.junit.Test;

public class VelocityViewerPanelSmokeTest {

    @Test
    public void bundledModelLoadsAndColorBarEditsReachTheRenderer() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final VelocityViewerPanel[] holder = new VelocityViewerPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new VelocityViewerPanel(null);
            holder[0].setModel(VelocityModelSpec.bundledNakajima());
            holder[0].ensureLoaded();
        });
        waitUntil(holder[0]::isModelLoaded);
        assertTrue("model did not load", holder[0].isModelLoaded());
        SwingUtilities.invokeAndWait(() -> {
            holder[0].getColorBar().setRange(-1.5, 1.5, "x");
            holder[0].getColorBar().setPalette(MapView.ColorPalette.VIRIDIS);
        });
        assertEquals(-1.5, holder[0].rendererForTest().vmin, 0.0);
        assertEquals(1.5, holder[0].rendererForTest().vmax, 0.0);
    }

    @Test
    public void oneDimensionalModelBecomesLayerCake_andCardsOpen() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final VelocityViewerPanel[] holder = new VelocityViewerPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance()
                .setGeometry(com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry.defaults());
            holder[0] = new VelocityViewerPanel(null);
            holder[0].setModel(VelocityModelSpec.forModelRef("prem.nd"));
            holder[0].ensureLoaded();
        });
        waitUntil(holder[0]::isModelLoaded);
        assertTrue("1D model did not load", holder[0].isModelLoaded());
        SwingUtilities.invokeAndWait(() -> {
            java.util.List<String> names = holder[0].cardNames();
            assertEquals(java.util.Arrays.asList("View", "Background", "Range", "Style", "Tools", "Export"), names);
            assertTrue(holder[0].tableCardForTest().isBuildEnabledForTest());
            assertTrue(!holder[0].tableCardForTest().fileRootForTest().isEmpty());
            for (String name : names) {
                holder[0].openCardForTest(name);
                assertTrue(name, holder[0].isCardOpenForTest());
            }
            // same card again closes it
            String last = holder[0].cardNames().get(holder[0].cardNames().size() - 1);
            holder[0].openCardForTest(last);
            assertTrue(!holder[0].isCardOpenForTest());
            // unchecking the model clears the view
            holder[0].setModel(null);
        });
        waitUntil(() -> !holder[0].isModelLoaded());
        assertTrue(!holder[0].isModelLoaded());
    }

    private static void waitUntil(java.util.function.BooleanSupplier c) throws Exception {
        long end = System.currentTimeMillis() + 30_000;
        while (!c.getAsBoolean() && System.currentTimeMillis() < end) {
            Thread.sleep(15);
        }
        assertTrue("timeout", c.getAsBoolean());
    }

    @Test
    public void oneDimensionalModelUsesSharedStrikeDipOnLoad() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final VelocityViewerPanel[] holder = new VelocityViewerPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry g =
                com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry.defaults();
            g.strikeDeg = 30;
            g.dipDeg = 20;
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setGeometry(g);
            holder[0] = new VelocityViewerPanel(null);
            holder[0].setModel(VelocityModelSpec.forModelRef("prem.nd"));
            holder[0].ensureLoaded();
        });
        waitUntil(holder[0]::isModelLoaded);
        assertEquals(41, holder[0].volumeForTest().nLon());
        assertTrue(holder[0].volumeForTest().hasChannel(com.treloc.xtreloc.solver.grid.LayerCakeVolumes.CH_VP));
        SwingUtilities.invokeAndWait(() -> com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance()
            .setGeometry(com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry.defaults()));
    }

    @Test
    public void dippingLayerCake_andTravelTimeFieldCanBeShown() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final VelocityViewerPanel[] holder = new VelocityViewerPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance()
                .setGeometry(com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry.defaults());
            holder[0] = new VelocityViewerPanel(null);
            holder[0].setModel(VelocityModelSpec.forModelRef("prem.nd"));
            holder[0].ensureLoaded();
        });
        waitUntil(holder[0]::isModelLoaded);
        // horizontal layer cake has 2 x 2 nodes horizontally; a dip needs a full grid with Vp and Vs
        assertEquals(2, holder[0].volumeForTest().nLon());
        SwingUtilities.invokeAndWait(() -> holder[0].applyStrikeDipForTest("30", "20"));
        assertEquals(41, holder[0].volumeForTest().nLon());
        assertTrue(holder[0].volumeForTest().hasChannel(com.treloc.xtreloc.solver.grid.LayerCakeVolumes.CH_VP));

        // grids written to disk are listed in the Table card and can be shown in the 3D map
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("cardgrids");
        com.treloc.xtreloc.solver.grid.LayerCakeModel m = com.treloc.xtreloc.solver.grid.LayerCakeModel.load("prem.nd");
        com.treloc.xtreloc.solver.grid.NllSimpleTransform tr = new com.treloc.xtreloc.solver.grid.NllSimpleTransform(35, 140);
        double[] ll = tr.toLatLon(0, 0);
        new com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder(m,
            new com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder.Region(35, 140, 10, 10, 10, 2.0), false,
            java.util.EnumSet.of(com.treloc.xtreloc.solver.table.TravelTimeTable.Phase.S))
            .build(java.util.Collections.singletonList(
                new com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder.StationPoint("AAA", ll[0], ll[1], 0.0)), null)
            .write(dir, "prem");
        SwingUtilities.invokeAndWait(() -> {
            holder[0].tableCardForTest().setTableForTest(dir.toString(), null);
            assertEquals(1, holder[0].tableCardForTest().gridCountForTest());
            holder[0].tableCardForTest().showField("S", "AAA");
        });
        waitUntil(() -> holder[0].volumeForTest() != null
            && holder[0].volumeForTest().hasChannel(com.treloc.xtreloc.solver.grid.StationTimeGrid.CH_TIME));
    }

    @Test
    public void afterTravelTime_velocitySourceRebuildsRange() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final VelocityViewerPanel[] holder = new VelocityViewerPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance()
                .setGeometry(com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry.defaults());
            holder[0] = new VelocityViewerPanel(null);
            holder[0].setModel(VelocityModelSpec.forModelRef("prem.nd"));
            holder[0].ensureLoaded();
        });
        waitUntil(holder[0]::isModelLoaded);

        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("srcswitch");
        com.treloc.xtreloc.solver.grid.LayerCakeModel m = com.treloc.xtreloc.solver.grid.LayerCakeModel.load("prem.nd");
        com.treloc.xtreloc.solver.grid.NllSimpleTransform tr = new com.treloc.xtreloc.solver.grid.NllSimpleTransform(35, 140);
        double[] ll = tr.toLatLon(0, 0);
        new com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder(m,
            new com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder.Region(35, 140, 10, 10, 10, 2.0), false,
            java.util.EnumSet.of(com.treloc.xtreloc.solver.table.TravelTimeTable.Phase.S))
            .build(java.util.Collections.singletonList(
                new com.treloc.xtreloc.solver.grid.TravelTimeGridBuilder.StationPoint("AAA", ll[0], ll[1], 0.0)), null)
            .write(dir, "prem");
        SwingUtilities.invokeAndWait(() -> {
            holder[0].setTravelTimeSource(true);
            holder[0].tableCardForTest().setTableForTest(dir.toString(), null);
            holder[0].tableCardForTest().showField("S", "AAA");
        });
        waitUntil(() -> holder[0].volumeForTest() != null
            && holder[0].volumeForTest().hasChannel(com.treloc.xtreloc.solver.grid.StationTimeGrid.CH_TIME));

        SwingUtilities.invokeAndWait(() -> {
            holder[0].setTravelTimeSource(false);
            com.treloc.xtreloc.app.gui.util.SharedFileManager.Geometry g =
                com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().getGeometry();
            g.lonMin = 139.0;
            g.lonMax = 141.0;
            g.latMin = 34.0;
            g.latMax = 36.0;
            g.strikeDeg = 0;
            g.dipDeg = 0;
            com.treloc.xtreloc.app.gui.util.SharedFileManager.getInstance().setGeometry(g);
            holder[0].applyGeometryFromShared();
        });
        assertTrue(holder[0].volumeForTest().hasChannel(com.treloc.xtreloc.solver.grid.LayerCakeVolumes.CH_VP));
        assertTrue(!holder[0].volumeForTest().hasChannel(com.treloc.xtreloc.solver.grid.StationTimeGrid.CH_TIME));
        assertTrue(holder[0].isTravelTimeSource() == false);
    }
}
