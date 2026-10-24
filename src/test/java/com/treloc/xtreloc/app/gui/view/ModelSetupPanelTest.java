package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.app.gui.util.SharedFileManager;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class ModelSetupPanelTest {

    @Test
    public void layerTreeHasModelStationCatalogShapefile_andStartsBlank() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final ModelSetupPanel[] holder = new ModelSetupPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            SharedFileManager.getInstance().setTaupFile(null);
            SharedFileManager.getInstance().setGeometry(SharedFileManager.Geometry.unset());
            VelocityViewerPanel viewer = new VelocityViewerPanel(null);
            holder[0] = new ModelSetupPanel(null, null, viewer);
        });
        assertEquals(java.util.List.of("Model", "Stations", "Hypocenters", "Shapefiles"),
            holder[0].layerNodeTitles());
        SwingUtilities.invokeAndWait(() -> {
            JPanel dummy = new JPanel();
            holder[0].insertSolverTab(dummy);
        });
        assertEquals(java.util.List.of("Model", "Solver", "Stations", "Hypocenters", "Shapefiles"),
            holder[0].layerNodeTitles());
        assertEquals(null, holder[0].getVelocityModelPanel().getVisualized());
        String taup = SharedFileManager.getInstance().getTaupFile();
        assertTrue("no default velocity", taup == null || taup.isEmpty());
        assertTrue(!SharedFileManager.getInstance().getGeometry().hasRegion());
    }

    @Test
    public void loadingStationsAutoFillsRange() throws Exception {
        System.setProperty("java.awt.headless", "true");
        java.io.File tbl = java.nio.file.Files.createTempFile("auto-range", ".tbl").toFile();
        java.nio.file.Files.writeString(tbl.toPath(), "AAA 35.0 140.0 0.0 0.0 0.0\nBBB 36.0 141.0 0.0 0.0 0.0\n");
        final ModelSetupPanel[] holder = new ModelSetupPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            SharedFileManager.getInstance().setTaupFile(null);
            SharedFileManager.getInstance().setGeometry(SharedFileManager.Geometry.unset());
            VelocityViewerPanel viewer = new VelocityViewerPanel(null);
            holder[0] = new ModelSetupPanel(null, null, viewer);
            holder[0].getStationPanel().setLinkedStationFile(tbl);
        });
        SharedFileManager.Geometry g = SharedFileManager.getInstance().getGeometry();
        assertTrue(g.hasRegion());
        assertTrue(g.lonMin < 140.0 && g.lonMax > 141.0);
        assertTrue(g.latMin < 35.0 && g.latMax > 36.0);
        tbl.delete();
    }

    @Test
    public void smallerRangeThanTableIsAllowed_widerIsRejected() {
        SharedFileManager.Geometry table = new SharedFileManager.Geometry();
        table.lonMin = 138.0;
        table.lonMax = 145.0;
        table.latMin = 34.0;
        table.latMax = 42.0;
        table.minDepthKm = 0.0;
        table.maxDepthKm = 80.0;
        SharedFileManager.Geometry smaller = table.copy();
        smaller.lonMin = 139.0;
        smaller.lonMax = 143.0;
        smaller.latMin = 35.0;
        smaller.latMax = 40.0;
        smaller.minDepthKm = 5.0;
        smaller.maxDepthKm = 40.0;
        org.junit.Assert.assertFalse(ModelSetupPanel.exceedsTableRange(smaller, table));
        org.junit.Assert.assertFalse(ModelSetupPanel.exceedsTableRange(table, table));
        SharedFileManager.Geometry widerLon = table.copy();
        widerLon.lonMin = 137.0;
        org.junit.Assert.assertTrue(ModelSetupPanel.exceedsTableRange(widerLon, table));
        SharedFileManager.Geometry deeper = table.copy();
        deeper.maxDepthKm = 100.0;
        org.junit.Assert.assertTrue(ModelSetupPanel.exceedsTableRange(deeper, table));
    }

    @Test
    public void clipLonLatDoesNotExtendPastTheSourceGrid() {
        double[] box = {137.0, 150.0, 30.0, 45.0};
        double[] grid = {138.0, 145.0, 34.0, 42.0};
        ModelSetupPanel.clipLonLat(box, grid);
        assertEquals(138.0, box[0], 1e-12);
        assertEquals(145.0, box[1], 1e-12);
        assertEquals(34.0, box[2], 1e-12);
        assertEquals(42.0, box[3], 1e-12);
    }

    @Test
    public void clipLonLatFallsBackToGridWhenStationsMissIt() {
        double[] box = {120.0, 121.0, 20.0, 21.0};
        double[] grid = {138.0, 145.0, 34.0, 42.0};
        ModelSetupPanel.clipLonLat(box, grid);
        assertEquals(138.0, box[0], 1e-12);
        assertEquals(145.0, box[1], 1e-12);
        assertEquals(34.0, box[2], 1e-12);
        assertEquals(42.0, box[3], 1e-12);
    }

    @Test
    public void catalogAddedToTableAppearsInTreeAndCheckboxDrivesVisibility() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final ModelSetupPanel[] holder = new ModelSetupPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            SharedFileManager.getInstance().setTaupFile(null);
            SharedFileManager.getInstance().setGeometry(SharedFileManager.Geometry.unset());
            holder[0] = new ModelSetupPanel(null, null, new VelocityViewerPanel(null));
            holder[0].getCatalogPanel().addCatalogFromHypocenters(
                java.util.List.of(new com.treloc.xtreloc.app.gui.model.Hypocenter(
                    "2017-11-15T05:29:31.00", 36.11, 129.37, 4.0)),
                "demo", null);
        });
        LayerTreePanel tree = holder[0].layerTreeForTest();
        assertEquals(java.util.List.of("demo"), tree.childTitles(LayerTreePanel.Kind.CATALOGS));
        assertTrue(tree.isChildVisible(LayerTreePanel.Kind.CATALOG, 0));
        assertTrue(holder[0].getCatalogPanel().isCatalogVisibleAt(0));
        SwingUtilities.invokeAndWait(() -> tree.clickCheckbox(LayerTreePanel.Kind.CATALOG, 0));
        assertTrue(!holder[0].getCatalogPanel().isCatalogVisibleAt(0));
        assertTrue(!tree.isChildVisible(LayerTreePanel.Kind.CATALOG, 0));
    }
}
