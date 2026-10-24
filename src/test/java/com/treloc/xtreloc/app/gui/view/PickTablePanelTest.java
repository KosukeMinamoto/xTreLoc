package com.treloc.xtreloc.app.gui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treloc.xtreloc.app.gui.service.ArrivalTimeManager;
import com.treloc.xtreloc.app.gui.service.PickEditor;
import com.treloc.xtreloc.app.gui.service.PickProposal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class PickTablePanelTest {

    private static Date t(long ms) {
        return new Date(1_510_000_000_000L + ms);
    }

    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private final PickEditor editor = new PickEditor(new ArrivalTimeManager());
    private PickTablePanel panel;

    private void build() throws Exception {
        edt(() -> panel = new PickTablePanel(editor));
    }

    @Test
    public void listsPicksAndProposalsWithTheirDifference() throws Exception {
        build();
        edt(() -> {
            editor.set("ST02", "Z", "P", t(1000));
            panel.setProposals(List.of(
                new PickProposal("ST02", "Z", "P", t(1250), "AIC"),
                new PickProposal("ST01", "N", "S", t(3000), "AIC")));
        });
        assertEquals(2, panel.rowCount());
        assertEquals("ST01", panel.cell(0, 0));
        assertEquals("S", panel.cell(0, 1));
        assertEquals("", panel.cell(0, 2));
        assertEquals("ST02", panel.cell(1, 0));
        assertEquals("+0.250", panel.cell(1, 4));
        assertEquals("AIC", panel.cell(1, 5));
    }

    @Test
    public void applyAllMakesProposalsPicksAndClearsThemInOneStep() throws Exception {
        build();
        AtomicInteger changes = new AtomicInteger();
        edt(() -> {
            panel.addChangeListener(changes::incrementAndGet);
            panel.setProposals(List.of(
                new PickProposal("ST01", "Z", "P", t(1000), "AIC"),
                new PickProposal("ST01", "N", "S", t(3000), "AIC")));
            assertEquals(2, panel.applyAll(false));
        });
        assertEquals(2, editor.size());
        assertTrue(panel.proposals().isEmpty());
        assertTrue(changes.get() > 0);
        edt(editor::undo);
        assertEquals(0, editor.size());
    }

    @Test
    public void applySelectedKeepsTheOtherProposals() throws Exception {
        build();
        edt(() -> {
            panel.setProposals(List.of(
                new PickProposal("ST01", "Z", "P", t(1000), "AIC"),
                new PickProposal("ST02", "Z", "P", t(1100), "AIC")));
            panel.selectRows(1);
        });
        assertTrue(panel.applyEnabled());
        edt(() -> panel.applySelected());
        assertNull(editor.get("ST01", "P"));
        assertNotNull(editor.get("ST02", "P"));
        assertEquals(1, panel.proposals().size());
        assertEquals("ST01", panel.proposals().get(0).station());
    }

    @Test
    public void removeAndUndoThroughTheButtons() throws Exception {
        build();
        edt(() -> {
            editor.set("ST01", "Z", "P", t(1000));
            panel.selectRows(0);
        });
        assertTrue(panel.removeEnabled());
        edt(() -> panel.removeSelected());
        assertEquals(0, editor.size());
        assertEquals(0, panel.rowCount());
        assertTrue(panel.undoEnabled());
        edt(panel::clickUndo);
        assertEquals(1, editor.size());
        assertEquals(1, panel.rowCount());
    }

    @Test
    public void discardClearsProposalsOnly() throws Exception {
        build();
        edt(() -> {
            editor.set("ST01", "Z", "P", t(1000));
            panel.setProposals(List.of(new PickProposal("ST01", "Z", "P", t(1100), "AIC")));
            panel.discardProposals();
        });
        assertTrue(panel.proposals().isEmpty());
        assertEquals(1, panel.rowCount());
        assertEquals("", panel.cell(0, 3));
        assertFalse(panel.applyEnabled());
        assertTrue(panel.proposalTimes("P").isEmpty());
    }
}
