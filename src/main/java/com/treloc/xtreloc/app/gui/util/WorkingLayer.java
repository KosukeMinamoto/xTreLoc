package com.treloc.xtreloc.app.gui.util;

import com.treloc.xtreloc.app.gui.model.EventKey;
import com.treloc.xtreloc.app.gui.model.Hypocenter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Events relocated from edited picks. Each entry remembers the catalog event it replaces, so the map
 * can show the "Working" catalog together with a vector from the old to the new position.
 *
 * <p>Call from the EDT. At most one entry exists per original event; relocating again replaces it.</p>
 */
public final class WorkingLayer {

    /** Name of the catalog that shows the relocated events. */
    public static final String CATALOG_NAME = "Working";

    /** One relocated event. */
    public static final class Entry {
        public final EventKey original;
        public final Hypocenter before;
        public final Hypocenter after;
        public final String mode;
        public final int pairRows;

        Entry(EventKey original, Hypocenter before, Hypocenter after, String mode, int pairRows) {
            this.original = original;
            this.before = before;
            this.after = after;
            this.mode = mode;
            this.pairRows = pairRows;
        }

        /** Horizontal shift in km (small-angle approximation). */
        public double horizontalShiftKm() {
            double dLat = (after.lat - before.lat) * 111.19;
            double dLon = (after.lon - before.lon) * 111.19 * Math.cos(Math.toRadians(before.lat));
            return Math.hypot(dLat, dLon);
        }

        public double depthShiftKm() {
            return after.depth - before.depth;
        }
    }

    /** Notified after the set of entries changed. */
    public interface Listener {
        void workingChanged(WorkingLayer layer);
    }

    private static final WorkingLayer INSTANCE = new WorkingLayer();

    private final Map<EventKey, Entry> entries = new LinkedHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public WorkingLayer() {
    }

    public static WorkingLayer getInstance() {
        return INSTANCE;
    }

    public void addListener(Listener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public Entry put(EventKey original, Hypocenter before, Hypocenter after, String mode, int pairRows) {
        Entry e = new Entry(original, before, after, mode, pairRows);
        entries.put(original, e);
        fire();
        return e;
    }

    public void remove(EventKey original) {
        if (entries.remove(original) != null) {
            fire();
        }
    }

    public void clear() {
        if (!entries.isEmpty()) {
            entries.clear();
            fire();
        }
    }

    public Entry get(EventKey original) {
        return entries.get(original);
    }

    public List<Entry> entries() {
        return new ArrayList<>(entries.values());
    }

    public int size() {
        return entries.size();
    }

    /** Relocated hypocentres in the order they were first added (the content of the "Working" catalog). */
    public List<Hypocenter> hypocenters() {
        List<Hypocenter> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            out.add(e.after);
        }
        return out;
    }

    private void fire() {
        for (Listener l : listeners) {
            l.workingChanged(this);
        }
    }
}
