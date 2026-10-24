package com.treloc.xtreloc.app.gui.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every pick found by the automatic pickers, waiting for the association step. The pool does not know
 * about events: a long continuous recording and a single event folder both just add picks to it.
 *
 * <p>A pick is identified by station, phase and time, so picking the same data twice does not duplicate it.
 */
public final class PickPool {

    /** Notified after the contents of the pool changed. */
    public interface Listener {
        void poolChanged(PickPool pool);
    }

    private final Map<String, EqtPicker.Pick> picks = new LinkedHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private static String key(EqtPicker.Pick p) {
        return p.station() + '|' + p.phase() + '|' + p.time().getTime();
    }

    /** @return the number of picks that were new to the pool */
    public synchronized int addAll(Collection<EqtPicker.Pick> added) {
        int n = 0;
        for (EqtPicker.Pick p : added) {
            if (picks.putIfAbsent(key(p), p) == null) {
                n++;
            }
        }
        if (n > 0) {
            fire();
        }
        return n;
    }

    public synchronized void clear() {
        if (!picks.isEmpty()) {
            picks.clear();
            fire();
        }
    }

    /** Copy of the picks, in the order they were added. */
    public synchronized List<EqtPicker.Pick> snapshot() {
        return new ArrayList<>(picks.values());
    }

    public synchronized int size() {
        return picks.size();
    }

    public synchronized int count(String phase) {
        int n = 0;
        for (EqtPicker.Pick p : picks.values()) {
            if (phase.equals(p.phase())) {
                n++;
            }
        }
        return n;
    }

    public void addListener(Listener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fire() {
        for (Listener l : listeners) {
            l.poolChanged(this);
        }
    }
}
