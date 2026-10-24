package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class LayerTreePanelTest {

    private static LayerTreePanel create() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final LayerTreePanel[] holder = new LayerTreePanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new LayerTreePanel());
        return holder[0];
    }

    @Test
    public void topLevelNodesAndSolverInsertion() throws Exception {
        LayerTreePanel tree = create();
        assertEquals(List.of("Model", "Stations", "Hypocenters", "Shapefiles"), tree.topLevelTitles());
        SwingUtilities.invokeAndWait(() -> tree.setSolverAvailable(true));
        assertEquals(List.of("Model", "Solver", "Stations", "Hypocenters", "Shapefiles"), tree.topLevelTitles());
    }

    @Test
    public void catalogChildrenFollowSetCatalogs() throws Exception {
        LayerTreePanel tree = create();
        SwingUtilities.invokeAndWait(() ->
            tree.setCatalogs(List.of("woo2019", "real"), List.of(true, false)));
        assertEquals(List.of("woo2019", "real"), tree.childTitles(LayerTreePanel.Kind.CATALOGS));
        assertTrue(tree.isChildVisible(LayerTreePanel.Kind.CATALOG, 0));
        assertFalse(tree.isChildVisible(LayerTreePanel.Kind.CATALOG, 1));
    }

    @Test
    public void checkboxClickReportsToggleAndSelectionReportsKind() throws Exception {
        LayerTreePanel tree = create();
        List<String> events = new ArrayList<>();
        tree.setListener(new LayerTreePanel.Listener() {
            @Override
            public void onSelect(LayerTreePanel.Kind kind, int index) {
                events.add("select:" + kind + ":" + index);
            }

            @Override
            public void onToggle(LayerTreePanel.Kind kind, int index, boolean visible) {
                events.add("toggle:" + kind + ":" + index + ":" + visible);
            }
        });
        SwingUtilities.invokeAndWait(() -> {
            tree.setShapefiles(List.of("coast.shp"), List.of(true));
            tree.clickCheckbox(LayerTreePanel.Kind.SHAPEFILE, 0);
        });
        assertEquals(List.of("toggle:SHAPEFILE:0:false"), events);
        assertFalse(tree.isChildVisible(LayerTreePanel.Kind.SHAPEFILE, 0));
    }

    @Test
    public void programmaticSelectDoesNotNotify() throws Exception {
        LayerTreePanel tree = create();
        List<String> events = new ArrayList<>();
        tree.setListener(new LayerTreePanel.Listener() {
            @Override
            public void onSelect(LayerTreePanel.Kind kind, int index) {
                events.add("select");
            }

            @Override
            public void onToggle(LayerTreePanel.Kind kind, int index, boolean visible) {
                events.add("toggle");
            }
        });
        SwingUtilities.invokeAndWait(() -> tree.select(LayerTreePanel.Kind.STATIONS, -1));
        assertTrue(events.isEmpty());
    }
}
