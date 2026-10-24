package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.treloc.xtreloc.app.gui.model.CatalogInfo;
import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import com.treloc.xtreloc.app.gui.util.WorkingLayer;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.After;
import org.junit.Test;

public class CatalogTablePanelWorkingTest {

    @After
    public void cleanUp() {
        WorkingLayer.getInstance().clear();
    }

    private static CatalogInfo find(CatalogTablePanel p, String name) {
        for (CatalogInfo c : p.getCatalogInfos()) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    @Test
    public void workingCatalogIsCreatedOnFirstRelocationAndUpdatedInPlace() throws Exception {
        System.setProperty("java.awt.headless", "true");
        final CatalogTablePanel[] holder = new CatalogTablePanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new CatalogTablePanel(null));
        CatalogTablePanel panel = holder[0];
        Hypocenter before = new Hypocenter("2017-11-15T05:20:01", 36.0, 129.3, 5.0);
        Hypocenter a1 = new Hypocenter("2017-11-15T05:20:01", 36.01, 129.3, 6.0);
        Hypocenter a2 = new Hypocenter("2017-11-15T05:20:01", 36.02, 129.3, 7.0);
        EventKey key = EventKey.of("Woo", before);

        SwingUtilities.invokeAndWait(() -> {
            WorkingLayer.getInstance().put(key, before, a1, "LMO", 6);
            panel.syncWorkingCatalog();
        });
        CatalogInfo working = find(panel, WorkingLayer.CATALOG_NAME);
        assertEquals(List.of(a1), working.getHypocenters());
        assertEquals(1, panel.getCatalogInfos().size());

        SwingUtilities.invokeAndWait(() -> {
            WorkingLayer.getInstance().put(key, before, a2, "LMO", 8);
            panel.syncWorkingCatalog();
        });
        assertEquals("same catalog object is reused", 1, panel.getCatalogInfos().size());
        assertSame(working, find(panel, WorkingLayer.CATALOG_NAME));
        assertEquals(List.of(a2), working.getHypocenters());
    }
}
