package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class ChartCatalogColorCardTest {

    @Test
    public void listsCatalogsAndWritesColorBack() throws Exception {
        System.setProperty("java.awt.headless", "true");
        SwingUtilities.invokeAndWait(() -> {
            CatalogTablePanel catalogs = new CatalogTablePanel(null);
            catalogs.addCatalogFromHypocenters(List.of(), "catalog_mcmc_5", null);
            ChartCatalogColorCard card = new ChartCatalogColorCard(catalogs, null, () -> { });
            assertTrue(labelTexts(card).contains("catalog_mcmc_5"));

            CatalogInfo info = catalogs.getCatalogInfos().get(0);
            card.applyColor(info, Color.MAGENTA);
            assertEquals(Color.MAGENTA, info.getColor());
        });
    }

    @Test
    public void map2dViewAndRangeButtonsAreStacked() throws Exception {
        System.setProperty("java.awt.headless", "true");
        SwingUtilities.invokeAndWait(() -> {
            Map2DPanel map = new Map2DPanel(null, null);
            List<String> view = buttonTexts(map.cardBodyForTest("View"));
            List<String> range = buttonTexts(map.cardBodyForTest("Range"));
            assertEquals(List.of("Plan", "Lat–depth", "Lon–depth", "Reset view"), view);
            assertEquals(List.of("Palette and range...", "Auto range"), range);
        });
    }

    @Test
    public void map3dViewIsVerticalAndDropsOblique() throws Exception {
        System.setProperty("java.awt.headless", "true");
        SwingUtilities.invokeAndWait(() -> {
            VelocityViewerPanel viewer = new VelocityViewerPanel(null);
            List<String> view = buttonTexts(viewer.cardBodyForTest("View"));
            assertEquals(List.of("Top", "From S", "From W", "Reset"), view);
            assertFalse(view.contains("Oblique"));
        });
    }

    private static List<String> labelTexts(JComponent root) {
        List<String> out = new ArrayList<>();
        collectLabels(root, out);
        return out;
    }

    private static void collectLabels(Component c, List<String> out) {
        if (c instanceof JLabel label && label.getText() != null) {
            out.add(label.getText());
        }
        if (c instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                collectLabels(child, out);
            }
        }
    }

    private static List<String> buttonTexts(JComponent root) {
        List<String> out = new ArrayList<>();
        collectButtons(root, out);
        return out;
    }

    private static void collectButtons(Component c, List<String> out) {
        if (c instanceof JButton b && b.getText() != null && !b.getText().isBlank()) {
            out.add(b.getText());
        }
        if (c instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                collectButtons(child, out);
            }
        }
    }
}
