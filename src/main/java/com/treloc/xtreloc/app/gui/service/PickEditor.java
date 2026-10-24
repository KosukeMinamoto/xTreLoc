package com.treloc.xtreloc.app.gui.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Edits the absolute picks of an {@link ArrivalTimeManager} with Undo / Redo. There is one pick per
 * station and phase; setting a pick replaces the old one. Every public edit is one undo step, so
 * "apply all proposals" is undone with a single Undo.
 */
public final class PickEditor {

    /** Notified after the picks changed (edit, undo, redo, clear, load). */
    public interface Listener {
        void picksChanged(PickEditor editor);
    }

    private static final int MAX_HISTORY = 200;

    /** One pick before and after an edit; {@code null} time means "no pick". */
    private record Change(String station, String phase,
                          String componentBefore, Date before, String componentAfter, Date after) {
    }

    private record Step(String label, List<Change> changes) {
    }

    private final ArrivalTimeManager manager;
    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public PickEditor(ArrivalTimeManager manager) {
        this.manager = manager;
    }

    public ArrivalTimeManager manager() {
        return manager;
    }

    public void addListener(Listener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    /** The pick of {@code station} and {@code phase}, or {@code null}. */
    public ArrivalTime get(String station, String phase) {
        return manager.getArrivalTime(clean(station), phase);
    }

    /** All picks (a copy). */
    public List<ArrivalTime> picks() {
        return new ArrayList<>(manager.getArrivalTimeMap().values());
    }

    public int size() {
        return manager.getArrivalTimeMap().size();
    }

    /** Sets (or replaces) one pick. Returns {@code false} when it already had that time. */
    public boolean set(String station, String component, String phase, Date time) {
        Change c = changeFor(clean(station), component, phase, time);
        if (c == null) {
            return false;
        }
        commit("Pick " + phase + " " + clean(station), List.of(c));
        return true;
    }

    /** Removes one pick. Returns {@code false} when there was none. */
    public boolean remove(String station, String phase) {
        return removeAll(java.util.Collections.singletonList(new String[] {station, phase})) > 0;
    }

    /** Removes several picks ({@code {station, phase}} pairs) as one step; returns how many existed. */
    public int removeAll(Collection<String[]> keys) {
        List<Change> changes = new ArrayList<>();
        for (String[] k : keys) {
            String st = clean(k[0]);
            ArrivalTime old = manager.getArrivalTime(st, k[1]);
            if (old != null) {
                changes.add(new Change(st, k[1], old.getComponent(), old.getArrivalTime(), null, null));
            }
        }
        if (changes.isEmpty()) {
            return 0;
        }
        commit(changes.size() == 1 ? "Remove " + changes.get(0).phase() + " " + changes.get(0).station()
            : "Remove " + changes.size() + " picks", changes);
        return changes.size();
    }

    /**
     * Applies proposals as one step.
     *
     * @param onlyEmpty keep picks that already exist and fill only the missing ones
     * @return number of picks that changed
     */
    public int apply(Collection<PickProposal> proposals, boolean onlyEmpty) {
        List<Change> changes = new ArrayList<>();
        for (PickProposal p : proposals) {
            String st = clean(p.station());
            if (onlyEmpty && manager.getArrivalTime(st, p.phase()) != null) {
                continue;
            }
            Change c = changeFor(st, p.component(), p.phase(), p.time());
            if (c != null) {
                changes.add(c);
            }
        }
        if (changes.isEmpty()) {
            return 0;
        }
        String method = proposals.isEmpty() ? "" : proposals.iterator().next().method();
        commit("Apply " + changes.size() + " " + method + " pick(s)", changes);
        return changes.size();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Label of the step Undo would revert, or {@code null}. */
    public String undoLabel() {
        return undo.isEmpty() ? null : undo.peek().label();
    }

    public String redoLabel() {
        return redo.isEmpty() ? null : redo.peek().label();
    }

    public boolean undo() {
        if (undo.isEmpty()) {
            return false;
        }
        Step s = undo.pop();
        for (int i = s.changes().size() - 1; i >= 0; i--) {
            Change c = s.changes().get(i);
            restore(c.station(), c.phase(), c.componentBefore(), c.before());
        }
        redo.push(s);
        fire();
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) {
            return false;
        }
        Step s = redo.pop();
        for (Change c : s.changes()) {
            restore(c.station(), c.phase(), c.componentAfter(), c.after());
        }
        undo.push(s);
        fire();
        return true;
    }

    /** Drops all picks and the history (a different event or folder was opened). */
    public void reset() {
        manager.clearArrivalTimes();
        undo.clear();
        redo.clear();
        fire();
    }

    /** Replaces the picks by those of an {@code .obs} file; the history starts empty. */
    public void loadObs(String fileName) {
        manager.clearArrivalTimes();
        manager.readFromObs(fileName);
        undo.clear();
        redo.clear();
        fire();
    }

    private Change changeFor(String station, String component, String phase, Date time) {
        ArrivalTime old = manager.getArrivalTime(station, phase);
        if (old != null && old.getArrivalTime().getTime() == time.getTime()) {
            return null;
        }
        return new Change(station, phase,
            old == null ? null : old.getComponent(), old == null ? null : old.getArrivalTime(),
            component, time);
    }

    private void commit(String label, List<Change> changes) {
        for (Change c : changes) {
            restore(c.station(), c.phase(), c.componentAfter(), c.after());
        }
        undo.push(new Step(label, changes));
        while (undo.size() > MAX_HISTORY) {
            undo.removeLast();
        }
        redo.clear();
        fire();
    }

    private void restore(String station, String phase, String component, Date time) {
        if (time == null) {
            manager.removeArrivalTime(station, component, phase);
        } else {
            manager.setArrivalTime(station, component, phase, time);
        }
    }

    private void fire() {
        for (Listener l : listeners) {
            l.picksChanged(this);
        }
    }

    private static String clean(String station) {
        return station == null ? "" : station.replace(" ", "");
    }
}
