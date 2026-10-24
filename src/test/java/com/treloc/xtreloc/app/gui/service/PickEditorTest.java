package com.treloc.xtreloc.app.gui.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class PickEditorTest {

    private static Date t(long ms) {
        return new Date(1_510_000_000_000L + ms);
    }

    private final PickEditor editor = new PickEditor(new ArrivalTimeManager());

    @Test
    public void setReplacesThePickOfTheSameStationAndPhase() {
        assertTrue(editor.set("ST01", "Z", "P", t(1000)));
        assertTrue(editor.set("ST01", "Z", "P", t(1500)));
        assertEquals(1, editor.size());
        assertEquals(t(1500), editor.get("ST01", "P").getArrivalTime());
        assertFalse("same time is not an edit", editor.set("ST01", "Z", "P", t(1500)));
    }

    @Test
    public void undoAndRedoWalkThroughEdits() {
        editor.set("ST01", "Z", "P", t(1000));
        editor.set("ST01", "Z", "P", t(1500));
        editor.remove("ST01", "P");
        assertNull(editor.get("ST01", "P"));

        assertTrue(editor.undo());
        assertEquals(t(1500), editor.get("ST01", "P").getArrivalTime());
        assertTrue(editor.undo());
        assertEquals(t(1000), editor.get("ST01", "P").getArrivalTime());
        assertTrue(editor.undo());
        assertNull(editor.get("ST01", "P"));
        assertFalse(editor.canUndo());

        assertTrue(editor.redo());
        assertEquals(t(1000), editor.get("ST01", "P").getArrivalTime());
        assertTrue(editor.redo());
        assertTrue(editor.redo());
        assertNull(editor.get("ST01", "P"));
        assertFalse(editor.canRedo());
    }

    @Test
    public void aNewEditClearsTheRedoStack() {
        editor.set("ST01", "Z", "P", t(1000));
        editor.undo();
        assertTrue(editor.canRedo());
        editor.set("ST02", "Z", "P", t(2000));
        assertFalse(editor.canRedo());
    }

    @Test
    public void applyingProposalsIsOneUndoStep() {
        editor.set("ST01", "Z", "P", t(1000));
        List<PickProposal> proposals = List.of(
            new PickProposal("ST01", "Z", "P", t(1200), "AIC"),
            new PickProposal("ST01", "N", "S", t(3000), "AIC"),
            new PickProposal("ST02", "Z", "P", t(1100), "AIC"));
        assertEquals(3, editor.apply(proposals, false));
        assertEquals(3, editor.size());
        assertEquals(t(1200), editor.get("ST01", "P").getArrivalTime());

        editor.undo();
        assertEquals(1, editor.size());
        assertEquals(t(1000), editor.get("ST01", "P").getArrivalTime());
        editor.redo();
        assertEquals(3, editor.size());
    }

    @Test
    public void onlyEmptyKeepsExistingPicks() {
        editor.set("ST01", "Z", "P", t(1000));
        int n = editor.apply(List.of(
            new PickProposal("ST01", "Z", "P", t(1200), "AIC"),
            new PickProposal("ST02", "Z", "P", t(1100), "AIC")), true);
        assertEquals(1, n);
        assertEquals(t(1000), editor.get("ST01", "P").getArrivalTime());
        assertEquals(t(1100), editor.get("ST02", "P").getArrivalTime());
    }

    @Test
    public void removeAllIsOneStepAndSkipsMissingPicks() {
        editor.set("ST01", "Z", "P", t(1000));
        editor.set("ST01", "N", "S", t(2000));
        int n = editor.removeAll(List.of(new String[] {"ST01", "P"}, new String[] {"ST01", "S"},
            new String[] {"ST09", "P"}));
        assertEquals(2, n);
        assertEquals(0, editor.size());
        editor.undo();
        assertEquals(2, editor.size());
        assertEquals("N", editor.get("ST01", "S").getComponent());
    }

    @Test
    public void stationNamesAreCleanedLikeInTheManager() {
        editor.set("ST 01", "Z", "P", t(1000));
        assertEquals(t(1000), editor.get("ST01", "P").getArrivalTime());
        assertTrue(editor.remove("ST 01", "P"));
    }

    @Test
    public void resetClearsPicksAndHistoryAndListenersHear() {
        AtomicInteger n = new AtomicInteger();
        editor.addListener(e -> n.incrementAndGet());
        editor.set("ST01", "Z", "P", t(1000));
        editor.reset();
        assertEquals(0, editor.size());
        assertFalse(editor.canUndo());
        assertEquals(2, n.get());
        assertNull(editor.undoLabel());
    }
}
